package fi.oph.viestinvalitys.vastaanotto.resource

import com.fasterxml.jackson.databind.ObjectMapper
import fi.oph.viestinvalitys.business.{KantaOperaatiot, Kieli, Kontakti, Prioriteetti, SisallonTyyppi, VastaanottajanTila}
import fi.oph.viestinvalitys.util.{AwsUtil, ConfigurationUtil, DbUtil, Mode}
import fi.oph.viestinvalitys.vastaanotto.model
import fi.oph.viestinvalitys.vastaanotto.model.{LahetysMetadata, LiiteMetadata, LuoViestiSuccessResponse, ViestiImpl, ViestiValidator}
import fi.oph.viestinvalitys.vastaanotto.resource.LahetysAPIConstants.*
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

  private def validoiViesti(viesti: ViestiImpl, kantaOperaatiot: KantaOperaatiot): Seq[String] =
    val securityOperaatiot = new SecurityOperaatiot
    val identiteetti = securityOperaatiot.getIdentiteetti()

    val liiteMetadatat = kantaOperaatiot.getLiitteet(ParametriUtil.validUUIDs(viesti.liitteidenTunnisteet))
      .map(liite => liite.tunniste -> LiiteMetadata(liite.omistaja, liite.koko))
      // hyväksytään esimerkkitunniste kaikille käyttäjille jotta swaggerin testitoimintoa voi käyttää
      .appended(UUID.fromString(LahetysAPIConstants.ESIMERKKI_LIITETUNNISTE) -> LiiteMetadata(identiteetti, 0))
      .toMap

    val lahetysMetadata =
      ParametriUtil.asUUID(viesti.lahetysTunniste)
        .map(lahetysTunniste => kantaOperaatiot.getLahetys(lahetysTunniste)
          .map(lahetys => LahetysMetadata(lahetys.omistaja, lahetys.prioriteetti.equals(Prioriteetti.KORKEA))))
        .getOrElse(Option.empty)

    ViestiValidator.validateViesti(viesti, lahetysMetadata, liiteMetadatat, identiteetti)

  private def tallennaMetriikat(vastaanottajienMaara: Int, prioriteetti: Prioriteetti): Unit =
    AwsUtil.cloudWatchClient.putMetricData(PutMetricDataRequest.builder()
      .namespace("Viestinvalitys")
      .metricData(MetricDatum.builder()
        .metricName("VastaanottojenMaara")
        .value(vastaanottajienMaara.toDouble)
        .storageResolution(1)
        .dimensions(Seq(Dimension.builder()
          .name("Prioriteetti")
          .value(prioriteetti.toString)
          .build()).asJava)
        .timestamp(Instant.now())
        .unit(StandardUnit.COUNT)
        .build())
      .build())

  final val ENDPOINT_LISAAVIESTI_DESCRIPTION = "Huomioita:\n" +
    "- mikäli lähetystunnusta ei ole määritelty, se luodaan automaattisesti ja tunnuksen otsikkona on viestin otsikko\n" +
    "- käyttöoikeusrajoitukset rajaavat ketkä voivat nähdä viestejä lähetys tai raportointirajapinnan kautta, niiden " +
    "täytyy olla organisaatiorajoitettuja, ts. niiden täytyy päättyä _ + oidiin (ks. esimerkki)\n" +
    "- viestin sisällön ja liitteiden koko voi olla yhteensä korkeintaan " + ViestiImpl.VIESTI_MAX_SIZE_MB_STR + " megatavua, " +
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
        content = Array(new Content(schema = new Schema(implementation = classOf[ViestiImpl])))),
    responses = Array(
      new ApiResponse(responseCode = "200", description="Pyyntö vastaanotettu, palauttaa lähetettävän viestin tunnisteen", content = Array(new Content(schema = new Schema(implementation = classOf[LuoViestiSuccessResponseImpl])))),
      new ApiResponse(responseCode = "400", description=LahetysAPIConstants.RESPONSE_400_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[LuoViestiFailureResponseImpl])))),
      new ApiResponse(responseCode = "403", description=LahetysAPIConstants.LAHETYS_RESPONSE_403_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void])))),
      new ApiResponse(responseCode = "429", description=LahetysAPIConstants.VIESTI_RATELIMIT_VIRHE, content = Array(new Content(schema = new Schema(implementation = classOf[LuoViestiRateLimitResponseImpl])))),
    ))
  def lisaaViesti(@RequestBody viestiBytes: Array[Byte], @Hidden @RequestParam(name = "disableRateLimiter", defaultValue = "false") disableRateLimiter: Boolean): ResponseEntity[LuoViestiResponse] =
    try
      val securityOperaatiot = new SecurityOperaatiot
      val kantaOperaatiot = KantaOperaatiot(DbUtil.database)

      Right(None)
        .flatMap(_ =>
            // tarkastetaan lähetysoikeus
          if (!securityOperaatiot.onOikeusLahettaa())
            Left(ResponseEntity.status(HttpStatus.FORBIDDEN).build())
          else
            Right(None))
        .flatMap(_ =>
          // deserialisoidaan
          try
            Right(mapper.readValue(viestiBytes, classOf[ViestiImpl]))
          catch
            case e: Exception => Left(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(LuoViestiFailureResponseImpl(java.util.List.of(LahetysAPIConstants.VIRHEELLINEN_VIESTI_JSON_VIRHE)))))
        .flatMap(viesti =>
          // tarkastetaan rate limit
          if ((mode == Mode.PRODUCTION || !disableRateLimiter) &&
            (viesti.prioriteetti.isPresent && Prioriteetti.KORKEA.toString.equals(viesti.prioriteetti.get.toUpperCase)) &&
            kantaOperaatiot.getKorkeanPrioriteetinViestienMaaraSince(securityOperaatiot.getIdentiteetti(),
              LahetysAPIConstants.PRIORITEETTI_KORKEA_RATELIMIT_AIKAIKKUNA_SEKUNTIA) + 1 >
              LahetysAPIConstants.PRIORITEETTI_KORKEA_RATELIMIT_VIESTEJA_AIKAIKKUNASSA)
            Left(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(LuoViestiRateLimitResponseImpl(java.util.List.of(LahetysAPIConstants.VIESTI_RATELIMIT_VIRHE))))
          else
            Right(viesti))
        .flatMap(viesti =>
          // validoidaan viesti
          val validointiVirheet = validoiViesti(viesti, kantaOperaatiot)
          if (!validointiVirheet.isEmpty)
            Left(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(LuoViestiFailureResponseImpl(validointiVirheet.asJava)))
          else
            Right(viesti))
        .map(viesti =>
          // tallennetaan viesti
          val (viestiEntiteetti, vastaanottajaEntiteetit) = kantaOperaatiot.tallennaViesti(
            otsikko = viesti.otsikko.get,
            sisalto = viesti.sisalto.get,
            sisallonTyyppi = SisallonTyyppi.valueOf(viesti.sisallonTyyppi.get.toUpperCase),
            kielet = viesti.kielet.map(kielet => kielet.asScala.map(kieli => Kieli.valueOf(kieli.toUpperCase)).toSet).orElse(Set.empty),
            maskit = viesti.maskit.map(maskit => maskit.asScala.map(maski => maski.getSalaisuus.get -> maski.getMaski.toScala).toMap).orElse(Map.empty),
            lahettavanVirkailijanOID = viesti.lahettavanVirkailijanOid.toScala,
            lahettaja = viesti.lahettaja.map(l => Kontakti(l.getNimi.toScala, l.getSahkopostiOsoite.get)).toScala,
            replyTo = viesti.replyTo.toScala,
            vastaanottajat = viesti.vastaanottajat.get.asScala.map(vastaanottaja => Kontakti(vastaanottaja.getNimi.toScala, vastaanottaja.getSahkopostiOsoite.get)).toSeq,
            liiteTunnisteet = viesti.liitteidenTunnisteet.orElse(Collections.emptyList()).asScala.map(tunniste => UUID.fromString(tunniste)).toSeq,
            lahettavaPalvelu = viesti.lahettavaPalvelu.toScala,
            lahetysTunniste = ParametriUtil.asUUID(viesti.lahetysTunniste),
            prioriteetti = viesti.prioriteetti.map(p => Prioriteetti.valueOf(p.toUpperCase)).toScala,
            sailytysAika = viesti.sailytysaika.map(s => s.asInstanceOf[Int]).toScala,
            kayttooikeusRajoitukset = viesti.kayttooikeusRajoitukset.toScala.map(r => r.asScala.toSet).getOrElse(Set.empty),
            metadata = viesti.metadata.toScala.map(m => m.asScala.map(entry => entry._1 -> entry._2.asScala.toSeq).toMap).getOrElse(Map.empty),
            omistaja = securityOperaatiot.getIdentiteetti()
          )
          tallennaMetriikat(vastaanottajaEntiteetit.size, viestiEntiteetti.prioriteetti)
          viestiEntiteetti)
        .map(viestiEntiteetti =>
          ResponseEntity.status(HttpStatus.OK)
            .body(LuoViestiSuccessResponseImpl(viestiEntiteetti.tunniste, viestiEntiteetti.lahetys_tunniste))
            .asInstanceOf[ResponseEntity[LuoViestiResponse]])
        .fold(e => e, r => r)
    catch
        case e: Exception =>
          LOG.error("Viestin luonti epäonnistui", e)
          ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(LuoViestiFailureResponseImpl(Seq(LahetysAPIConstants.VIESTIN_LUONTI_EPAONNISTUI).asJava))

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
      new ApiResponse(responseCode = "400", description = LahetysAPIConstants.RESPONSE_400_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[PalautaViestiFailureResponse])))),
      new ApiResponse(responseCode = "403", description = LahetysAPIConstants.KATSELU_RESPONSE_403_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void])))),
      new ApiResponse(responseCode = "410", description = LahetysAPIConstants.KATSELU_RESPONSE_410_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void]))))
    ))
  def lueViesti(@PathVariable(VIESTITUNNISTE_PARAM_NAME) viestiTunniste: String): ResponseEntity[PalautaViestiResponse] =
    val securityOperaatiot = new SecurityOperaatiot
    if(!securityOperaatiot.onOikeusKatsella())
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build()

    val uuid = ParametriUtil.asUUID(viestiTunniste)
    if (uuid.isEmpty)
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(PalautaViestiFailureResponse(LahetysAPIConstants.VIESTITUNNISTE_INVALID))

    val kantaOperaatiot = new KantaOperaatiot(DbUtil.database)
    val viesti = kantaOperaatiot.getViestit(Seq(uuid.get)).find(v => true)
    if (viesti.isEmpty)
      return ResponseEntity.status(HttpStatus.GONE).build()

    val onLukuOikeudet    = securityOperaatiot.onOikeusKatsellaEntiteetti(viesti.get.omistaja)
    if (!onLukuOikeudet)
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build()

    ResponseEntity.status(HttpStatus.OK).body(PalautaViestiSuccessResponse(viesti.get.tunniste, viesti.get.otsikko))
}