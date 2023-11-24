package fi.oph.viestinvalitys.vastaanotto.resource

import com.fasterxml.jackson.databind.ObjectMapper
import fi.oph.viestinvalitys.aws.AwsUtil
import fi.oph.viestinvalitys.business.{LahetysOperaatiot, LiitteenTila}
import fi.oph.viestinvalitys.db.DbUtil
import fi.oph.viestinvalitys.vastaanotto.security.{SecurityConstants, SecurityOperaatiot}
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import jakarta.servlet.http.HttpServletResponse
import org.apache.commons.fileupload2.core.DiskFileItemFactory
import org.apache.commons.fileupload2.jakarta.JakartaServletDiskFileUpload
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.{HttpStatus, MediaType, ResponseEntity}
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.{PostMapping, RequestMapping, RequestParam, RestController}
import org.springframework.web.multipart.MultipartFile
import slick.dbio.DBIO
import slick.lifted.TableQuery
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.auth.credentials.{ContainerCredentialsProvider, DefaultCredentialsProvider}
import slick.dbio.DBIO
import slick.jdbc.JdbcBackend.Database
import slick.lifted.TableQuery
import slick.jdbc.PostgresProfile.api.*

import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.stream.Collectors
import scala.annotation.meta.field
import scala.beans.BeanProperty
import scala.jdk.CollectionConverters.*
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

object LiiteConstants {
  final val ESIMERKKI_LIITETUNNISTE = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
}

class LiiteResponse() {}

case class LiiteSuccessResponse(
  @(Schema @field)(example = LiiteConstants.ESIMERKKI_LIITETUNNISTE)
  @BeanProperty liiteTunniste: String) extends LiiteResponse {}

case class LiiteFailureResponse(
  @(Schema@field)(example = "{ virhe: Liitteen koko on liian suuri }") // TODO: miten ilmoitetaan kokovirhe yhdessä muiden virheiden kanssa
  @BeanProperty virhe: String) extends LiiteResponse {}

@RequestMapping(path = Array("/v2/resource/liite"))
@RestController
@Tag("2. Liite")
class LiiteResource {

  val LOG = LoggerFactory.getLogger(classOf[LiiteResource]);

  @PostMapping(
    path = Array(""),
    consumes = Array(MediaType.MULTIPART_FORM_DATA_VALUE),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  @Operation(
    summary = "Lataa uuden liitetiedoston",
    description = "Huomioita:\n" +
      "- liitteen maksimikoko on 4,5 megatavua",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Liite vastaanotettu, palauttaa liitetunnisteen", content = Array(new Content(schema = new Schema(implementation = classOf[LiiteSuccessResponse])))),
      new ApiResponse(responseCode = "400", description = "Pyyntö on virheellinen", content = Array(new Content(schema = new Schema(implementation = classOf[Void])))),
      new ApiResponse(responseCode = "403", description = SecurityConstants.LAHETYS_RESPONSE_403_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void]))))
    ))
  def lisaaLiite(@RequestParam("liite") liite: MultipartFile): ResponseEntity[LiiteResponse] = {
    val securityOperaatiot = new SecurityOperaatiot
    if(!securityOperaatiot.onOikeusLahettaa())
      ResponseEntity.status(HttpStatus.FORBIDDEN).build()

    try
      val identiteetti = securityOperaatiot.getIdentiteetti()
      val tallennettu = LahetysOperaatiot(DbUtil.getDatabase()).tallennaLiite(liite.getOriginalFilename, liite.getContentType, liite.getSize.toInt, identiteetti)
      val putObjectResponse = AwsUtil.getS3Client().putObject(PutObjectRequest
        .builder()
        .bucket("hahtuva-viestinvalityspalvelu-attachments")
        .key(tallennettu.tunniste.toString)
        .contentType(liite.getContentType)
        .build(), RequestBody.fromBytes(liite.getBytes))

      ResponseEntity.status(HttpStatus.OK).body(LiiteSuccessResponse(tallennettu.tunniste.toString))
    catch
      case e: Exception =>
        LOG.error("Liitteen lataus epäonnistui: ", e)
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(LiiteFailureResponse("Järjestelmävirhe, jos virhe toistuu ole yhteydessä palvelun ylläpitoon."))
  }
}