package fi.oph.viestinvalitys.vastaanotto.resource

import com.fasterxml.jackson.databind.ObjectMapper
import fi.oph.viestinvalitys.db.dbUtil
import fi.oph.viestinvalitys.model.Lahetykset
import fi.oph.viestinvalitys.vastaanotto.model
import fi.oph.viestinvalitys.vastaanotto.model.{Lahetys, LahetysTunnisteIdentityProvider, LahetysValidator, LiiteTunnisteIdentityProvider, Viesti, ViestiValidator}
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.{HttpStatus, MediaType, ResponseEntity}
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

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

  final val EXAMPLE_LAHETYS_VALIDOINTIVIRHE = "[ \"" + LahetysValidator.VALIDATION_OTSIKKO_TYHJA + "\" ]"
}

class LahetysResponse() {
}

case class LahetysSuccessResponse(
   @(Schema@field)(example = LahetysConstants.ESIMERKKI_LAHETYSTUNNISTE)
   @BeanProperty lahetysTunniste: String) extends LahetysResponse {
}

case class LahetysFailureResponse(
   @(Schema@field)(example = LahetysConstants.EXAMPLE_LAHETYS_VALIDOINTIVIRHE)
   @BeanProperty validointiVirheet: java.util.List[String]) extends LahetysResponse {
}

@RequestMapping(path = Array("/v2/resource/lahetys"))
@RestController
@Tag("1. Lähetys")
class LahetysResource {

  @PutMapping(
    path = Array(""),
    consumes = Array(MediaType.APPLICATION_JSON_VALUE),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  @Operation(
    summary = "Luo uuden lähetyksen",
    description = "",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Pyyntö vastaanotettu, palauttaa lähetystunnisteen", content = Array(new Content(schema = new Schema(implementation = classOf[LahetysSuccessResponse])))),
      new ApiResponse(responseCode = "400", description = "Pyyntö virheellinen, palauttaa listan pyynnössä olevista virheistä", content = Array(new Content(schema = new Schema(implementation = classOf[LahetysFailureResponse])))),
    ))
  def lisaaLahetys(@RequestBody lahetys: Lahetys): ResponseEntity[LahetysResponse] = {
    val validointiVirheet = Seq(
      LahetysValidator.validateOtsikko(lahetys.otsikko)).flatten

    if(!validointiVirheet.isEmpty)
      ResponseEntity.status(HttpStatus.BAD_REQUEST).body(LahetysFailureResponse(validointiVirheet.asJava))
    else
      val tunniste = dbUtil.getUUID()
      val omistaja = SecurityContextHolder.getContext.getAuthentication.getName()
      val lahetysInsertAction: DBIO[Option[Int]] = TableQuery[Lahetykset] ++= List((tunniste, lahetys.otsikko, omistaja))
      Await.result(dbUtil.getDatabase().run(lahetysInsertAction), 5.seconds)

      ResponseEntity.status(HttpStatus.OK).body(LahetysSuccessResponse(tunniste.toString))
  }
}