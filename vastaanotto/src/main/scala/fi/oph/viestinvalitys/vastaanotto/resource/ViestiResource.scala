package fi.oph.viestinvalitys.vastaanotto.resource

import com.fasterxml.jackson.databind.ObjectMapper
import fi.oph.viestinvalitys.business.{Kieli, Kontakti, LahetysOperaatiot, Prioriteetti, SisallonTyyppi, VastaanottajanTila}
import fi.oph.viestinvalitys.db.DbUtil
import fi.oph.viestinvalitys.vastaanotto.model
import fi.oph.viestinvalitys.vastaanotto.model.{Lahetys, LahetysMetadata, LiiteMetadata, Viesti, ViestiValidator}
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.{HttpStatus, MediaType, ResponseEntity}
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

  final val EXAMPLE_VIESTI_VALIDOINTIVIRHE = "[ \"" + ViestiValidator.VALIDATION_OTSIKKO_TYHJA + "\" ]"
}

class ViestiResponse() {}

case class ViestiSuccessResponse(
  @(Schema @field)(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
  @BeanProperty viestiTunniste: String,
) extends ViestiResponse

case class ViestiFailureResponse(
  @(Schema @field)(example = ViestiConstants.EXAMPLE_VIESTI_VALIDOINTIVIRHE)
  @BeanProperty validointiVirheet: java.util.List[String]
) extends ViestiResponse

case class ViestiRateLimitResponse(
  @(Schema@field)(example = ViestiConstants.VIESTI_RATELIMIT_VIRHE)
  @BeanProperty virhe: java.util.List[String]
) extends ViestiResponse

@RequestMapping(path = Array("/v2/resource"))
@RestController
@Tag("3. Viesti")
class ViestiResource {

  @Autowired var mapper: ObjectMapper = null

  private def asUUID(tunniste: String): Option[UUID] =
    try
      Option.apply(UUID.fromString(tunniste))
    catch
      case e: Exception => Option.empty

  private def validUUIDs(tunnisteet: Seq[String]): Seq[UUID] =
    tunnisteet
      .map(asUUID)
      .filter(tunniste => tunniste.isDefined)
      .map(tunniste => tunniste.get)

  private def validoiViesti(viesti: Viesti, lahetysOperaatiot: LahetysOperaatiot): Seq[String] =
    val identiteetti = SecurityContextHolder.getContext.getAuthentication.getName()

    val liiteMetadatat = lahetysOperaatiot.getLiitteet(validUUIDs(viesti.liitteidenTunnisteet.asScala.toSeq))
      .map(liite => liite.tunniste -> LiiteMetadata(liite.omistaja, liite.koko))
      // hyväksytään esimerkkitunniste kaikille käyttäjille jotta swaggerin testitoimintoa voi käyttää
      .appended(UUID.fromString(LiiteConstants.ESIMERKKI_LIITETUNNISTE) -> LiiteMetadata(identiteetti, 0))
      .toMap

    val lahetysMetadata =
      if(LahetysConstants.ESIMERKKI_LAHETYSTUNNISTE.equals(viesti.lahetysTunniste))
        // hyväksytään esimerkkilähetys kaikille käyttäjille jotta swaggerin testitoimintoa voi käyttää
        Option.apply(LahetysMetadata(identiteetti))
      else
        lahetysOperaatiot.getLahetys(asUUID(viesti.lahetysTunniste).get)
          .map(lahetys => LahetysMetadata(lahetys.omistaja))
          .orElse(Option.empty)

    ViestiValidator.validateViesti(viesti, lahetysMetadata, liiteMetadatat, identiteetti)

  final val ENDPOINT_VIESTI_DESCRIPTION = "Huomioita:\n" +
    "- mikäli lähetystunnusta ei ole määritelty, se luodaan automaattisesti ja tunnuksen otsikkona on viestin otsikko\n" +
    "- käyttöoikeusrajoitusten täytyy olla organisaatiorajoitettuja, ts. niiden täytyy päättyä _ + oidiin (ks. esimerkki)\n" +
    "- viestin sisällön ja liitteiden koko voi olla yhteensä korkeintaan " + ViestiConstants.VIESTI_MAX_SIZE_MB_STR + " megatavua, " +
    "suurempi koko johtaa 400-virheeseen\n" +
    "- korkean prioriteetin viesteillä voi olla vain yksi vastaanottaja\n" +
    "- yksittäinen järjestelmä voi lähettää vain yhden korkean prioriteetin pyynnön joka viides sekunti, " +
    "nopeampi lähetystahti voi johtaa 429-vastaukseen"
  @PutMapping(
    path = Array("/viesti"),
    consumes = Array(MediaType.APPLICATION_JSON_VALUE),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  @Operation(
    summary = "Luo uuden viestin",
    description = ENDPOINT_VIESTI_DESCRIPTION,
    requestBody =
      new io.swagger.v3.oas.annotations.parameters.RequestBody(
        content = Array(new Content(schema = new Schema(implementation = classOf[Viesti])))),
    responses = Array(
      new ApiResponse(responseCode = "200", description="Pyyntö vastaanotettu, palauttaa lähetettävän viestin tunnisteen", content = Array(new Content(schema = new Schema(implementation = classOf[ViestiSuccessResponse])))),
      new ApiResponse(responseCode = "400", description="Pyyntö virheellinen, palauttaa listan pyynnössä olevista virheistä", content = Array(new Content(schema = new Schema(implementation = classOf[ViestiFailureResponse])))),
      new ApiResponse(responseCode = "429", description="Liikaa korkean prioriteetin lähetyspyyntöjä", content = Array(new Content(schema = new Schema(implementation = classOf[ViestiRateLimitResponse])))),
    ))
  def lisaaViesti(@RequestBody viestiBytes: Array[Byte]): ResponseEntity[ViestiResponse] = {
    val viesti: Viesti = mapper.readValue(viestiBytes, classOf[Viesti])
    val lahetysOperaatiot = LahetysOperaatiot(DbUtil.getDatabase())

    val validointiVirheet = validoiViesti(viesti, lahetysOperaatiot)
    if(!validointiVirheet.isEmpty) {
      ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ViestiFailureResponse(validointiVirheet.asJava))
    } else {
      val omistaja = SecurityContextHolder.getContext.getAuthentication.getName()
      val lahetysTunniste = {
        if(viesti.lahetysTunniste==null) Option.empty
        else Option.apply(UUID.fromString(viesti.lahetysTunniste))
      }
      val viestiEntiteetti = new LahetysOperaatiot(DbUtil.getDatabase()).tallennaViesti(
        viesti.otsikko,
        viesti.sisalto,
        SisallonTyyppi.valueOf(viesti.sisallonTyyppi.toUpperCase),
        viesti.kielet.asScala.map(kieli => Kieli.valueOf(kieli.toUpperCase)).toSet,
        viesti.lahettavanVirkailijanOid.map(oid => Option.apply(oid)).orElse(Option.empty),
        Kontakti(viesti.lahettaja.nimi, viesti.lahettaja.sahkopostiOsoite),
        viesti.vastaanottajat.asScala.map(vastaanottaja => Kontakti(vastaanottaja.nimi, vastaanottaja.sahkopostiOsoite)).toSeq,
        viesti.liitteidenTunnisteet.asScala.map(tunniste => UUID.fromString(tunniste)).toSeq,
        viesti.lahettavaPalvelu,
        Option.apply(viesti.lahetysTunniste).map(tunniste => UUID.fromString(tunniste)),
        Prioriteetti.valueOf(viesti.prioriteetti.toUpperCase),
        viesti.sailytysAika,
        viesti.kayttooikeusRajoitukset.asScala.toSet,
        viesti.metadata.asScala.toMap,
        omistaja
      )

      ResponseEntity.status(HttpStatus.OK).body(ViestiSuccessResponse(viestiEntiteetti.tunniste.toString))
    }
  }
}