package fi.oph.viestinvalitys.vastaanotto.resource

import com.fasterxml.jackson.databind.ObjectMapper
import fi.oph.viestinvalitys.business.{Kieli, Kontakti, LahetysOperaatiot, Prioriteetti, SisallonTyyppi, VastaanottajanTila}
import fi.oph.viestinvalitys.db.DbUtil
import fi.oph.viestinvalitys.vastaanotto.model
import fi.oph.viestinvalitys.vastaanotto.model.{Lahetys, LahetysMetadata, LiiteMetadata, Viesti, ViestiValidator}
import fi.oph.viestinvalitys.vastaanotto.security.{SecurityConstants, SecurityOperaatiot}
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import jakarta.servlet.http.HttpServletResponse
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

import java.util.UUID
import java.util.stream.Collectors
import scala.annotation.meta.field
import scala.beans.BeanProperty
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.*


object ViestiConstants {
  final val VIESTI_MAX_SIZE = VIESTI_MAX_SIZE_MB_STR.toInt * 1024 * 1024
  final val VIESTI_MAX_SIZE_MB_STR = "8"

  final val VIESTI_RATELIMIT_VIRHE = "virhe: Liikaa korkean prioriteetin lähetyspyyntöjä"
  final val VIESTI_TUNNISTE_VIRHE = "virhe: viestiTunniste ei ole muodoltaan validi viestitunniste"

  final val EXAMPLE_VIESTI_VALIDOINTIVIRHE = "[ \"" + ViestiValidator.VALIDATION_OTSIKKO_TYHJA + "\" ]"
}

class LuoViestiResponse() {}

case class LuoViestiSuccessResponse(
  @(Schema @field)(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
  @BeanProperty viestiTunniste: String,
) extends LuoViestiResponse

case class LuoViestiFailureResponse(
  @(Schema @field)(example = ViestiConstants.EXAMPLE_VIESTI_VALIDOINTIVIRHE)
  @BeanProperty validointiVirheet: java.util.List[String]
) extends LuoViestiResponse

case class LuoViestiRateLimitResponse(
  @(Schema@field)(example = ViestiConstants.VIESTI_RATELIMIT_VIRHE)
  @BeanProperty virhe: java.util.List[String]
) extends LuoViestiResponse

@RequestMapping(path = Array("/v2/resource"))
@RestController
@Tag("3. Viesti")
class ViestiResource {

  @Autowired var mapper: ObjectMapper = null

  private def validoiViesti(viesti: Viesti, lahetysOperaatiot: LahetysOperaatiot): Seq[String] =
    val securityOperaatiot = new SecurityOperaatiot
    val identiteetti = securityOperaatiot.getIdentiteetti()

    val liiteMetadatat = lahetysOperaatiot.getLiitteet(UUIDUtil.validUUIDs(viesti.liitteidenTunnisteet.asScala.toSeq))
      .map(liite => liite.tunniste -> LiiteMetadata(liite.omistaja, liite.koko))
      // hyväksytään esimerkkitunniste kaikille käyttäjille jotta swaggerin testitoimintoa voi käyttää
      .appended(UUID.fromString(LiiteConstants.ESIMERKKI_LIITETUNNISTE) -> LiiteMetadata(identiteetti, 0))
      .toMap

    val lahetysMetadata =
      if(LahetysConstants.ESIMERKKI_LAHETYSTUNNISTE.equals(viesti.lahetysTunniste))
        // hyväksytään esimerkkilähetys kaikille käyttäjille jotta swaggerin testitoimintoa voi käyttää
        Option.apply(LahetysMetadata(identiteetti))
      else
        lahetysOperaatiot.getLahetys(UUIDUtil.asUUID(viesti.lahetysTunniste).get)
          .map(lahetys => LahetysMetadata(lahetys.omistaja))
          .orElse(Option.empty)

    ViestiValidator.validateViesti(viesti, lahetysMetadata, liiteMetadatat, identiteetti)

  final val ENDPOINT_LISAAVIESTI_DESCRIPTION = "Huomioita:\n" +
    "- mikäli lähetystunnusta ei ole määritelty, se luodaan automaattisesti ja tunnuksen otsikkona on viestin otsikko\n" +
    "- käyttöoikeusrajoitusten täytyy olla organisaatiorajoitettuja, ts. niiden täytyy päättyä _ + oidiin (ks. esimerkki)\n" +
    "- viestin sisällön ja liitteiden koko voi olla yhteensä korkeintaan " + ViestiConstants.VIESTI_MAX_SIZE_MB_STR + " megatavua, " +
    "suurempi koko johtaa 400-virheeseen\n" +
    "- korkean prioriteetin viesteillä voi olla vain yksi vastaanottaja\n" +
    "- yksittäinen järjestelmä voi lähettää vain yhden korkean prioriteetin pyynnön sekunnissa, " +
    "nopeampi lähetystahti voi johtaa 429-vastaukseen"
  @PutMapping(
    path = Array("/viesti"),
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
      new ApiResponse(responseCode = "400", description="Pyyntö virheellinen, palauttaa listan pyynnössä olevista virheistä", content = Array(new Content(schema = new Schema(implementation = classOf[LuoViestiFailureResponse])))),
      new ApiResponse(responseCode = "403", description=SecurityConstants.LAHETYS_RESPONSE_403_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void])))),
      new ApiResponse(responseCode = "429", description="Liikaa korkean prioriteetin lähetyspyyntöjä", content = Array(new Content(schema = new Schema(implementation = classOf[LuoViestiRateLimitResponse])))),
    ))
  def lisaaViesti(@RequestBody viestiBytes: Array[Byte]): ResponseEntity[LuoViestiResponse] =
    val securityOperaatiot = new SecurityOperaatiot
    if(!securityOperaatiot.onOikeusLahettaa())
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build()

    val viesti: Viesti = mapper.readValue(viestiBytes, classOf[Viesti])
    val lahetysOperaatiot = LahetysOperaatiot(DbUtil.getDatabase())
    val validointiVirheet = validoiViesti(viesti, lahetysOperaatiot)
    if(!validointiVirheet.isEmpty)
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(LuoViestiFailureResponse(validointiVirheet.asJava))

    val (viestiEntiteetti, vastaanottajaEntiteetit) = lahetysOperaatiot.tallennaViesti(
      otsikko                   = viesti.otsikko,
      sisalto                   = viesti.sisalto,
      sisallonTyyppi            = SisallonTyyppi.valueOf(viesti.sisallonTyyppi.toUpperCase),
      kielet                    = viesti.kielet.asScala.map(kieli => Kieli.valueOf(kieli.toUpperCase)).toSet,
      lahettavanVirkailijanOID  = viesti.lahettavanVirkailijanOid.map(oid => Option.apply(oid)).orElse(Option.empty),
      lahettaja                 = Kontakti(viesti.lahettaja.nimi, viesti.lahettaja.sahkopostiOsoite),
      vastaanottajat            = viesti.vastaanottajat.asScala.map(vastaanottaja => Kontakti(vastaanottaja.nimi, vastaanottaja.sahkopostiOsoite)).toSeq,
      liiteTunnisteet           = viesti.liitteidenTunnisteet.asScala.map(tunniste => UUID.fromString(tunniste)).toSeq,
      lahettavaPalvelu          = viesti.lahettavaPalvelu,
      oLahetysTunniste          = UUIDUtil.asUUID(viesti.lahetysTunniste),
      prioriteetti              = Prioriteetti.valueOf(viesti.prioriteetti.toUpperCase),
      sailytysAika              = viesti.sailytysAika,
      kayttooikeusRajoitukset   = viesti.kayttooikeusRajoitukset.asScala.toSet,
      metadata                  = viesti.metadata.asScala.toMap,
      omistaja                  = securityOperaatiot.getIdentiteetti()
    )

    ResponseEntity.status(HttpStatus.OK).body(LuoViestiSuccessResponse(viestiEntiteetti.tunniste.toString))

  class PalautaViestiResponse() {}

  case class PalautaViestiSuccessResponse(
    @(Schema @field)(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    @BeanProperty viestiTunniste: String,
    @(Schema @field)(example = "Onnistunut otsikko")
    @BeanProperty otsikko: String
  ) extends PalautaViestiResponse

  case class PalautaViestiFailureResponse(
    @BeanProperty virhe: String,
  ) extends PalautaViestiResponse

  final val ENDPOINT_LUEVIESTI_DESCRIPTION = "Huomioita:\n" +
    "- Palauttaa viestin ja yhteenvedon lähetyksen tilasta\n"
  final val LUEVIESTI_RESPONSE_403_DESCRIPTION = "Käyttäjällä ei ole oikeutta lukea viestiä. Oikeus on jos:\n" +
    "- Käyttäjällä on " + SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA + "-oikeus, tai\n" +
    "- Käyttäjä luonut viestin, tai\n" +
    "- Käyttäjällä on " + SecurityConstants.SECURITY_ROOLI_KATSELU + "-oikeus, ja lisäksi jokin viestiin luonnin yhteydessä" +
    " liitetyistä vaadituista lukuoikeuksista"
  @GetMapping(
    path = Array("/viesti/{tunniste}"),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  @Operation(
    summary = "Palauttaa viestin",
    description = ENDPOINT_LUEVIESTI_DESCRIPTION,
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Palauttaa viestin", content = Array(new Content(schema = new Schema(implementation = classOf[PalautaViestiSuccessResponse])))),
      new ApiResponse(responseCode = "403", description = LUEVIESTI_RESPONSE_403_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void])))),
      new ApiResponse(responseCode = "410", description = "Viestiä ei löytynyt, tunniste on virheellinen tai viesti on poistettu säilytysajan päätyttyä", content = Array(new Content(schema = new Schema(implementation = classOf[Void]))))
    ))
  def lueViesti(@PathVariable("tunniste") viestiTunniste: String): ResponseEntity[PalautaViestiResponse] =
    val securityOperaatiot = new SecurityOperaatiot
    if(!securityOperaatiot.onOikeusKatsella())
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build()

    val uuid = UUIDUtil.asUUID(viestiTunniste)
    if (uuid.isEmpty)
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(PalautaViestiFailureResponse(ViestiConstants.VIESTI_TUNNISTE_VIRHE))

    val lahetysOperaatiot     = new LahetysOperaatiot(DbUtil.getDatabase())
    val viesti = lahetysOperaatiot.getViestit(Seq(uuid.get)).find(v => true)
    if (viesti.isEmpty)
      return ResponseEntity.status(HttpStatus.GONE).build()

    val viestinOikeudet   = lahetysOperaatiot.getKayttooikeudet(Seq(viesti.get.tunniste)).get(viesti.get.tunniste).getOrElse(Set.empty)
    val onLukuOikeudet    = securityOperaatiot.onOikeusKatsellaViesti(viesti.get, viestinOikeudet)
    if (!onLukuOikeudet)
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build()

    ResponseEntity.status(HttpStatus.OK).body(PalautaViestiSuccessResponse(viesti.get.tunniste.toString, viesti.get.otsikko))
}