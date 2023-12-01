package fi.oph.viestinvalitys.vastaanotto.resource

import com.fasterxml.jackson.databind.ObjectMapper
import fi.oph.viestinvalitys.business.LahetysOperaatiot
import fi.oph.viestinvalitys.db.DbUtil
import fi.oph.viestinvalitys.vastaanotto.model
import fi.oph.viestinvalitys.vastaanotto.model.{Lahetys, LahetysMetadata, LahetysValidator, Viesti, ViestiValidator}
import fi.oph.viestinvalitys.vastaanotto.security.{SecurityConstants, SecurityOperaatiot}
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.ScanCursor
import org.springframework.http.{HttpStatus, MediaType, ResponseEntity}
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import org.springframework.security.access.prepost.PreAuthorize

import java.util.UUID
import scala.annotation.meta.field
import scala.beans.BeanProperty
import scala.jdk.CollectionConverters.*
import slick.dbio.DBIO
import slick.jdbc.JdbcBackend.Database
import slick.lifted.TableQuery
import slick.jdbc.PostgresProfile.api.*

import java.util.UUID
import java.util.stream.Collectors
import scala.annotation.meta.field
import scala.beans.BeanProperty
import scala.jdk.CollectionConverters.*
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

object LahetysConstants {
  final val ESIMERKKI_LAHETYSTUNNISTE = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
}

class LahetysResponse() {
}

case class LahetysSuccessResponse(
   @(Schema@field)(example = LahetysConstants.ESIMERKKI_LAHETYSTUNNISTE)
   @BeanProperty lahetysTunniste: String) extends LahetysResponse {
}

case class LahetysFailureResponse(
   @(Schema @field)(example = APIConstants.EXAMPLE_OTSIKKO_VALIDOINTIVIRHE)
   @BeanProperty validointiVirheet: java.util.List[String]) extends LahetysResponse {
}

case class LahetysForbiddenResponse(
   @(Schema@field)(example = APIConstants.LAHETYS_RESPONSE_403_DESCRIPTION)
   @BeanProperty virhe: String) extends LahetysResponse {
}

@RequestMapping(path = Array("/lahetys/v1/lahetykset"))
@RestController
@Tag(
  name = "1. Lähetykset",
  description = "Lähetys on joukko viestejä joita voidaan tarkastella yhtenä kokonaisuutena raportoinnissa. Viestit " +
    "voi luomisen yhteydessä liittää lähetykseen.")
class LahetysResource {

  @Autowired var mapper: ObjectMapper = null;

  @PostMapping(
    path = Array(""),
    consumes = Array(MediaType.APPLICATION_JSON_VALUE),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  @Operation(
    summary = "Luo uuden lähetyksen",
    description = "",
    requestBody =
      new io.swagger.v3.oas.annotations.parameters.RequestBody(
        content = Array(new Content(schema = new Schema(implementation = classOf[Lahetys])))),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Pyyntö vastaanotettu, palauttaa lähetystunnisteen", content = Array(new Content(schema = new Schema(implementation = classOf[LahetysSuccessResponse])))),
      new ApiResponse(responseCode = "400", description = APIConstants.RESPONSE_400_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[LahetysFailureResponse])))),
      new ApiResponse(responseCode = "403", description = APIConstants.LAHETYS_RESPONSE_403_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void]))))
    ))
  def lisaaLahetys(@RequestBody lahetysBytes: Array[Byte]): ResponseEntity[LahetysResponse] =
    val securityOperaatiot = new SecurityOperaatiot
    if(!securityOperaatiot.onOikeusLahettaa())
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build()

    val lahetys = mapper.readValue(lahetysBytes, classOf[Lahetys])
    val validointiVirheet = Seq(
      LahetysValidator.validateOtsikko(lahetys.otsikko),
      LahetysValidator.validateKayttooikeudet(lahetys.kayttooikeusRajoitukset)
    ).flatten
    if(!validointiVirheet.isEmpty)
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(LahetysFailureResponse(validointiVirheet.asJava))

    val tunniste = LahetysOperaatiot(DbUtil.getDatabase()).tallennaLahetys(
      otsikko                 = lahetys.otsikko,
      kayttooikeusRajoitukset = lahetys.kayttooikeusRajoitukset.asScala.toSet,
      omistaja                = securityOperaatiot.getIdentiteetti()
    ).tunniste

    ResponseEntity.status(HttpStatus.OK).body(LahetysSuccessResponse(tunniste.toString))

  class PalautaLahetysResponse() {}

  case class PalautaLahetysSuccessResponse(
    @(Schema@field)(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    @BeanProperty lahetysTunniste: String,
    @(Schema@field)(example = "Onnistunut otsikko")
    @BeanProperty otsikko: String
  ) extends PalautaLahetysResponse

  case class PalautaLahetysFailureResponse(
    @(Schema@field)(example = APIConstants.ENTITEETTI_TUNNISTE_INVALID)
    @BeanProperty virhe: String,
  ) extends PalautaLahetysResponse

  @GetMapping(
    path = Array("/{tunniste}"),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  @Operation(
    summary = "Palauttaa lähetyksen",
    description = "Palauttaa lähetyksen ja yhteenvedon sen tilasta",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Palauttaa viestin", content = Array(new Content(schema = new Schema(implementation = classOf[PalautaLahetysSuccessResponse])))),
      new ApiResponse(responseCode = "400", description = APIConstants.RESPONSE_400_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[PalautaLahetysFailureResponse])))),
      new ApiResponse(responseCode = "403", description = APIConstants.KATSELU_RESPONSE_403_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void])))),
      new ApiResponse(responseCode = "410", description = APIConstants.KATSELU_RESPONSE_410_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void]))))
    ))
  def lueLahetys(@PathVariable("tunniste") lahetysTunniste: String): ResponseEntity[PalautaLahetysResponse] =
    val securityOperaatiot = new SecurityOperaatiot
    if (!securityOperaatiot.onOikeusKatsella())
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build()

    val uuid = UUIDUtil.asUUID(lahetysTunniste)
    if (uuid.isEmpty)
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(PalautaLahetysFailureResponse(APIConstants.ENTITEETTI_TUNNISTE_INVALID))

    val lahetysOperaatiot = new LahetysOperaatiot(DbUtil.getDatabase())
    val lahetys = lahetysOperaatiot.getLahetys(uuid.get)
    if (lahetys.isEmpty)
      return ResponseEntity.status(HttpStatus.GONE).build()

    val lahetyksenOikeudet = lahetysOperaatiot.getLahetyksenKayttooikeudet(lahetys.get.tunniste)
    val onLukuOikeudet = securityOperaatiot.onOikeusKatsellaEntiteetti(lahetys.get.omistaja, lahetyksenOikeudet)
    if (!onLukuOikeudet)
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build()

    ResponseEntity.status(HttpStatus.OK).body(PalautaLahetysSuccessResponse(lahetys.get.tunniste.toString, lahetys.get.otsikko))

}