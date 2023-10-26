package fi.oph.viestinvalitus.vastaanotto.resource

import com.fasterxml.jackson.databind.ObjectMapper
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
import org.springframework.web.bind.annotation.{PostMapping, RequestMapping, RequestParam, RestController}
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.auth.credentials.{ContainerCredentialsProvider, DefaultCredentialsProvider}

import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.stream.Collectors
import scala.annotation.meta.field
import scala.beans.BeanProperty
import scala.jdk.CollectionConverters.*

object LiiteConstants {
}

case class LiiteResponse(
  @(Schema @field)(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
  @BeanProperty liiteTunniste: String) {
}

@RequestMapping(path = Array("/v2/resource/liite"))
@RestController
@Tag("2. Liitteet")
class LiiteResource {

  val LOG = LoggerFactory.getLogger(classOf[LiiteResource]);

  @PostMapping(
    path = Array(""),
    consumes = Array(MediaType.MULTIPART_FORM_DATA_VALUE),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  @Operation(
    summary = "Lataa uuden liitetiedoston",
    description = "",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Liite vastaanotettu, palauttaa liitetunnisteen", content = Array(new Content(schema = new Schema(implementation = classOf[LiiteResponse]))))
    ))
  def lisaaLiite(@RequestParam("liite") liite: MultipartFile): ResponseEntity[LiiteResponse] = {
    LOG.info("Liite: " + liite.getOriginalFilename + ", " + liite.getContentType + ", size: " + liite.getSize)
    LOG.info("Access key id: " + System.getenv("AWS_ACCESS_KEY_ID"))
    LOG.info("Environment: " + System.getenv().entrySet().stream().map(entry => "key: " + entry.getKey + ", value: " + entry.getValue).collect(Collectors.joining(",")))

    val key = UUID.randomUUID().toString
    try
      val s3Client = S3Client.builder()
        .credentialsProvider(ContainerCredentialsProvider.builder().build()) // tämä on SnapStartin takia
        .build()
      val putObjectResponse = s3Client.putObject(PutObjectRequest
        .builder()
        .bucket("hahtuva-viestinvalituspalvelu-attachments")
        .key(key)
        .build(), RequestBody.fromBytes(liite.getBytes))
    catch
      case e: Exception => {
        LOG.error("Liitteen lataus epäonnistui: ", e)
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(LiiteResponse(key))
      }

    ResponseEntity.status(HttpStatus.OK).body(LiiteResponse(key))
  }
}