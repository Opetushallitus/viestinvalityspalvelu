package fi.oph.viestinvalitys.vastaanotto.resource

import com.fasterxml.jackson.databind.ObjectMapper
import fi.oph.viestinvalitys.aws.AwsUtil
import fi.oph.viestinvalitys.business.{LahetysOperaatiot, LiitteenTila}
import fi.oph.viestinvalitys.db.{ConfigurationUtil, DbUtil}
import fi.oph.viestinvalitys.vastaanotto.resource.APIConstants.{ESIMERKKI_LIITETUNNISTE, LAHETYS_RESPONSE_403_DESCRIPTION, LIITE_VIRHE_JARJESTELMAVIRHE, LIITE_VIRHE_LIITE_PUUTTUU, LUO_LIITE_PATH}
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
import java.util.{Optional, UUID}
import java.util.stream.Collectors
import scala.annotation.meta.field
import scala.beans.BeanProperty
import scala.jdk.CollectionConverters.*
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class LuoLiiteResponse() {}

case class LuoLiiteSuccessResponse(
  @(Schema @field)(example = ESIMERKKI_LIITETUNNISTE)
  @BeanProperty liiteTunniste: String) extends LuoLiiteResponse {}

case class LuoLiiteFailureResponse(
  @(Schema@field)(example = "{ virhe: Liitteen koko on liian suuri }") // TODO: miten ilmoitetaan kokovirhe yhdessä muiden virheiden kanssa
  @BeanProperty virhe: String) extends LuoLiiteResponse {}

@RequestMapping(path = Array(""))
@RestController
@Tag(
  name= "2. Liitteet",
  description = "Viestien liitetiedostot")
class LiiteResource {

  val BUCKET_NAME = ConfigurationUtil.getConfigurationItem("ATTACHMENTS_BUCKET_NAME").get
  val LOG = LoggerFactory.getLogger(classOf[LiiteResource]);

  @PostMapping(
    path = Array(LUO_LIITE_PATH),
    consumes = Array(MediaType.MULTIPART_FORM_DATA_VALUE),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  @Operation(
    summary = "Lataa uuden liitetiedoston",
    description = "Huomioita:\n" +
      "- liitteen maksimikoko on 4,5 megatavua",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Liite vastaanotettu, palauttaa liitetunnisteen", content = Array(new Content(schema = new Schema(implementation = classOf[LuoLiiteSuccessResponse])))),
      new ApiResponse(responseCode = "400", description = "Pyyntö on virheellinen", content = Array(new Content(schema = new Schema(implementation = classOf[Void])))),
      new ApiResponse(responseCode = "403", description = LAHETYS_RESPONSE_403_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void]))))
    ))
  def lisaaLiite(@RequestParam("liite", required=false) liite: Optional[MultipartFile]): ResponseEntity[LuoLiiteResponse] = {
    val securityOperaatiot = new SecurityOperaatiot
    if(!securityOperaatiot.onOikeusLahettaa())
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build()

    if(liite.isEmpty)
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(LuoLiiteFailureResponse(LIITE_VIRHE_LIITE_PUUTTUU))

    try
      val identiteetti = securityOperaatiot.getIdentiteetti()
      val tallennettu = LahetysOperaatiot(DbUtil.database).tallennaLiite(liite.get.getOriginalFilename, liite.get.getContentType, liite.get.getSize.toInt, identiteetti)
      val putObjectResponse = AwsUtil.s3Client.putObject(PutObjectRequest
        .builder()
        .bucket(BUCKET_NAME)
        .key(tallennettu.tunniste.toString)
        .contentType(liite.get.getContentType)
        .build(), RequestBody.fromBytes(liite.get.getBytes))

      ResponseEntity.status(HttpStatus.OK).body(LuoLiiteSuccessResponse(tallennettu.tunniste.toString))
    catch
      case e: Exception =>
        LOG.error("Liitteen lataus epäonnistui: ", e)
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(LuoLiiteFailureResponse(LIITE_VIRHE_JARJESTELMAVIRHE))
  }
}