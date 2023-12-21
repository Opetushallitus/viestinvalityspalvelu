package fi.oph.viestinvalitys.vastaanotto.resource

import com.fasterxml.jackson.databind.ObjectMapper
import fi.oph.viestinvalitys.aws.AwsUtil
import fi.oph.viestinvalitys.business.{Kieli, Kontakti, LahetysOperaatiot, Prioriteetti, SisallonTyyppi, VastaanottajanTila}
import fi.oph.viestinvalitys.db.{ConfigurationUtil, DbUtil, Mode}
import fi.oph.viestinvalitys.vastaanotto.model
import fi.oph.viestinvalitys.vastaanotto.model.{Lahetys, LahetysMetadata, LiiteMetadata, Viesti, ViestiValidator}
import fi.oph.viestinvalitys.vastaanotto.resource.APIConstants.{GET_VIESTI_PATH, LUO_VIESTI_PATH, VIESTITUNNISTE_PARAM_NAME, VIESTIT_PATH}
import fi.oph.viestinvalitys.vastaanotto.security.{SecurityConstants, SecurityOperaatiot}
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.{HttpStatus, MediaType, ResponseEntity}
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import slick.dbio.DBIO
import slick.jdbc.JdbcBackend.Database
import slick.lifted.TableQuery
import slick.jdbc.PostgresProfile.api.*
import software.amazon.awssdk.services.cloudwatch.model.{Dimension, MetricDatum, PutMetricDataRequest, StandardUnit}

import java.time.Instant
import java.util.{Collections, UUID}
import java.util.stream.Collectors
import scala.annotation.meta.field
import scala.beans.BeanProperty
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

class LuoViestiResponse() {}

case class LuoViestiSuccessResponse(
  @(Schema @field)(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
  @BeanProperty viestiTunniste: String,
  @(Schema@field)(example = "5b4501ec-3298-4064-8868-262b55fdce9a")
  @BeanProperty lahetysTunniste: String
) extends LuoViestiResponse

case class LuoViestiFailureResponse(
  @(Schema @field)(example = APIConstants.EXAMPLE_OTSIKKO_VALIDOINTIVIRHE)
  @BeanProperty validointiVirheet: java.util.List[String]
) extends LuoViestiResponse

case class LuoViestiRateLimitResponse(
  @(Schema@field)(example = APIConstants.VIESTI_RATELIMIT_VIRHE)
  @BeanProperty virhe: java.util.List[String]
) extends LuoViestiResponse

class PalautaViestiResponse() {}

case class PalautaViestiSuccessResponse(
  @(Schema@field)(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
  @BeanProperty viestiTunniste: String,
  @(Schema@field)(example = "Onnistunut otsikko")
  @BeanProperty otsikko: String
) extends PalautaViestiResponse

case class PalautaViestiFailureResponse(
  @(Schema@field)(example = APIConstants.VIESTITUNNISTE_INVALID)
  @BeanProperty virhe: String,
) extends PalautaViestiResponse

@RequestMapping(path = Array(""))
@RestController
@Tag(
  name = "3. Viestit",
  description = "Lähetettävät viestit. Viestit lähetetään yksi vastaanottaja kerrallaan.")
class ViestiResource {

  val LOG = LoggerFactory.getLogger(classOf[ViestiResource])

  @Autowired var mapper: ObjectMapper = null
  val mode = ConfigurationUtil.getMode()

  private def nullAsEmpty[A](list: java.util.List[A]): java.util.List[A] =
    Option.apply(list).getOrElse(java.util.Collections.emptyList())

  private def nullAsEmpty[A, B](map: java.util.Map[A, B]): java.util.Map[A, B] =
    Option.apply(map).getOrElse(java.util.Collections.emptyMap())

  private def validoiViesti(viesti: Viesti, lahetysOperaatiot: LahetysOperaatiot): Seq[String] =
    val securityOperaatiot = new SecurityOperaatiot
    val identiteetti = securityOperaatiot.getIdentiteetti()

    val liiteMetadatat = lahetysOperaatiot.getLiitteet(ParametriUtil.validUUIDs(viesti.liitteidenTunnisteet))
      .map(liite => liite.tunniste -> LiiteMetadata(liite.omistaja, liite.koko))
      // hyväksytään esimerkkitunniste kaikille käyttäjille jotta swaggerin testitoimintoa voi käyttää
      .appended(UUID.fromString(APIConstants.ESIMERKKI_LIITETUNNISTE) -> LiiteMetadata(identiteetti, 0))
      .toMap

    val lahetysMetadata =
      ParametriUtil.asUUID(viesti.lahetysTunniste)
        .map(lahetysTunniste => lahetysOperaatiot.getLahetys(lahetysTunniste)
          .map(lahetys => LahetysMetadata(lahetys.omistaja)))
        .getOrElse(Option.empty)

    ViestiValidator.validateViesti(viesti, lahetysMetadata, liiteMetadatat, identiteetti)

  final val ENDPOINT_LISAAVIESTI_DESCRIPTION = "Huomioita:\n" +
    "- mikäli lähetystunnusta ei ole määritelty, se luodaan automaattisesti ja tunnuksen otsikkona on viestin otsikko\n" +
    "- käyttöoikeusrajoitukset rajaavat ketkä voivat nähdä viestejä lähetys tai raportointirajapinnan kautta, niiden " +
    "täytyy olla organisaatiorajoitettuja, ts. niiden täytyy päättyä _ + oidiin (ks. esimerkki)\n" +
    "- viestin sisällön ja liitteiden koko voi olla yhteensä korkeintaan " + ViestiValidator.VIESTI_MAX_SIZE_MB_STR + " megatavua, " +
    "suurempi koko johtaa 400-virheeseen\n" +
    "- korkean prioriteetin viesteillä voi olla vain yksi vastaanottaja\n" +
    "- yksittäinen järjestelmä voi lähettää vain yhden korkean prioriteetin pyynnön sekunnissa, " +
    "nopeampi lähetystahti voi johtaa 429-vastaukseen"
  @PostMapping(
    path = Array(LUO_VIESTI_PATH),
    consumes = Array(MediaType.APPLICATION_JSON_VALUE),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  @Operation(
    summary = "Luo uuden viestin",
    description = ENDPOINT_LISAAVIESTI_DESCRIPTION,
    requestBody =
      new io.swagger.v3.oas.annotations.parameters.RequestBody(
        content = Array(new Content(schema = new Schema(implementation = classOf[Viesti])))),
    responses = Array(
      new ApiResponse(responseCode = "200", description="Pyyntö vastaanotettu, palauttaa lähetettävän viestin tunnisteen", content = Array(new Content(schema = new Schema(implementation = classOf[LuoViestiSuccessResponse])))),
      new ApiResponse(responseCode = "400", description=APIConstants.RESPONSE_400_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[LuoViestiFailureResponse])))),
      new ApiResponse(responseCode = "403", description=APIConstants.LAHETYS_RESPONSE_403_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void])))),
      new ApiResponse(responseCode = "429", description=APIConstants.VIESTI_RATELIMIT_VIRHE, content = Array(new Content(schema = new Schema(implementation = classOf[LuoViestiRateLimitResponse])))),
    ))
  def lisaaViesti(@RequestBody viestiBytes: Array[Byte], @Hidden @RequestParam(name = "disableRateLimiter", defaultValue = "false") disableRateLimiter: Boolean): ResponseEntity[LuoViestiResponse] =
    val securityOperaatiot = new SecurityOperaatiot
    if(!securityOperaatiot.onOikeusLahettaa())
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build()

    val viesti: Viesti =
      try
        mapper.readValue(viestiBytes, classOf[Viesti])
      catch
        case e: Exception => return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(LuoViestiFailureResponse(java.util.List.of(APIConstants.VIRHEELLINEN_VIESTI_JSON_VIRHE)))
    val lahetysOperaatiot = LahetysOperaatiot(DbUtil.database)

    if((mode==Mode.PRODUCTION || !disableRateLimiter) &&
      (viesti.prioriteetti.isPresent && Prioriteetti.KORKEA.toString.equals(viesti.prioriteetti.get.toUpperCase)) &&
      lahetysOperaatiot.getKorkeanPrioriteetinViestienMaaraSince(securityOperaatiot.getIdentiteetti(),
        APIConstants.PRIORITEETTI_KORKEA_RATELIMIT_AIKAIKKUNA_SEKUNTIA) + 1>
        APIConstants.PRIORITEETTI_KORKEA_RATELIMIT_VIESTEJA_AIKAIKKUNASSA)
          return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(LuoViestiRateLimitResponse(java.util.List.of(APIConstants.VIESTI_RATELIMIT_VIRHE)))

    val validointiVirheet = validoiViesti(viesti, lahetysOperaatiot)
    if(!validointiVirheet.isEmpty)
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(LuoViestiFailureResponse(validointiVirheet.asJava))

    val (viestiEntiteetti, vastaanottajaEntiteetit) = lahetysOperaatiot.tallennaViesti(
      otsikko                   = viesti.otsikko.get,
      sisalto                   = viesti.sisalto.get,
      sisallonTyyppi            = SisallonTyyppi.valueOf(viesti.sisallonTyyppi.get.toUpperCase),
      kielet                    = viesti.kielet.get.asScala.map(kieli => Kieli.valueOf(kieli.toUpperCase)).toSet,
      maskit                    = viesti.maskit.map(maskit => maskit.asScala.map(maski => maski.salaisuus.get -> maski.maski.toScala).toMap).orElse(Map.empty),
      lahettavanVirkailijanOID  = viesti.lahettavanVirkailijanOid.toScala,
      lahettaja                 = Kontakti(viesti.lahettaja.get.nimi.toScala, viesti.lahettaja.get.sahkopostiOsoite.get),
      replyTo                   = viesti.replyTo.toScala,
      vastaanottajat            = viesti.vastaanottajat.get.asScala.map(vastaanottaja => Kontakti(vastaanottaja.nimi.toScala, vastaanottaja.sahkopostiOsoite.get)).toSeq,
      liiteTunnisteet           = viesti.liitteidenTunnisteet.orElse(Collections.emptyList()).asScala.map(tunniste => UUID.fromString(tunniste)).toSeq,
      lahettavaPalvelu          = viesti.lahettavaPalvelu.toScala,
      lahetysTunniste           = ParametriUtil.asUUID(viesti.lahetysTunniste),
      prioriteetti              = Prioriteetti.valueOf(viesti.prioriteetti.get.toUpperCase),
      sailytysAika              = viesti.sailytysAika.get,
      kayttooikeusRajoitukset   = viesti.kayttooikeusRajoitukset.toScala.map(r => r.asScala.toSet).getOrElse(Set.empty),
      metadata                  = viesti.metadata.toScala.map(m => m.asScala.map(entry => entry._1 -> entry._2.asScala.toSeq).toMap).getOrElse(Map.empty),
      omistaja                  = securityOperaatiot.getIdentiteetti()
    )

    AwsUtil.cloudWatchClient.putMetricData(PutMetricDataRequest.builder()
      .namespace("Viestinvalitys")
      .metricData(MetricDatum.builder()
        .metricName("VastaanottojenMaara")
        .value(viesti.vastaanottajat.get.size().toDouble)
        .storageResolution(1)
        .dimensions(Seq(Dimension.builder()
          .name("Prioriteetti")
          .value(viesti.prioriteetti.get.toUpperCase)
          .build()).asJava)
        .timestamp(Instant.now())
        .unit(StandardUnit.COUNT)
        .build())
      .build())

    ResponseEntity.status(HttpStatus.OK).body(LuoViestiSuccessResponse(viestiEntiteetti.tunniste.toString,
      viestiEntiteetti.lahetys_tunniste.toString))

  final val ENDPOINT_LUEVIESTI_DESCRIPTION = "Huomioita:\n" +
    "- Palauttaa viestin ja yhteenvedon lähetyksen tilasta\n"
  @GetMapping(
    path = Array(GET_VIESTI_PATH),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  @Operation(
    summary = "Palauttaa viestin",
    description = ENDPOINT_LUEVIESTI_DESCRIPTION,
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Palauttaa viestin", content = Array(new Content(schema = new Schema(implementation = classOf[PalautaViestiSuccessResponse])))),
      new ApiResponse(responseCode = "400", description = APIConstants.RESPONSE_400_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[PalautaViestiFailureResponse])))),
      new ApiResponse(responseCode = "403", description = APIConstants.KATSELU_RESPONSE_403_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void])))),
      new ApiResponse(responseCode = "410", description = APIConstants.KATSELU_RESPONSE_410_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void]))))
    ))
  def lueViesti(@PathVariable(VIESTITUNNISTE_PARAM_NAME) viestiTunniste: String): ResponseEntity[PalautaViestiResponse] =
    val securityOperaatiot = new SecurityOperaatiot
    if(!securityOperaatiot.onOikeusKatsella())
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build()

    val uuid = ParametriUtil.asUUID(viestiTunniste)
    if (uuid.isEmpty)
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(PalautaViestiFailureResponse(APIConstants.VIESTITUNNISTE_INVALID))

    val lahetysOperaatiot = new LahetysOperaatiot(DbUtil.database)
    val viesti = lahetysOperaatiot.getViestit(Seq(uuid.get)).find(v => true)
    if (viesti.isEmpty)
      return ResponseEntity.status(HttpStatus.GONE).build()

    val viestinOikeudet   = lahetysOperaatiot.getViestinKayttooikeudet(Seq(viesti.get.tunniste)).get(viesti.get.tunniste).getOrElse(Set.empty)
    val onLukuOikeudet    = securityOperaatiot.onOikeusKatsellaEntiteetti(viesti.get.omistaja, viestinOikeudet)
    if (!onLukuOikeudet)
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build()

    ResponseEntity.status(HttpStatus.OK).body(PalautaViestiSuccessResponse(viesti.get.tunniste.toString, viesti.get.otsikko))
}