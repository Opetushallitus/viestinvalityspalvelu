package fi.oph.viestinvalitys.vastaanotto.resource

import fi.oph.viestinvalitys.business.KantaOperaatiot
import fi.oph.viestinvalitys.security.{AuditLog, AuditOperation}
import fi.oph.viestinvalitys.util.{AwsUtil, ConfigurationUtil, DbUtil, LogContext}
import fi.oph.viestinvalitys.vastaanotto.model.Liite
import fi.oph.viestinvalitys.vastaanotto.resource.LahetysAPIConstants.*
import fi.oph.viestinvalitys.vastaanotto.security.SecurityOperaatiot
import fi.oph.viestinvalitys.vastaanotto.validation.LiiteValidator
import fi.vm.sade.auditlog.Changes
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.{HttpStatus, MediaType, ResponseEntity}
import org.springframework.web.bind.annotation.{PostMapping, RequestMapping, RequestParam, RestController}
import org.springframework.web.context.request.{RequestContextHolder, ServletRequestAttributes}
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.model.PutObjectRequest

import java.util.Optional
import scala.jdk.CollectionConverters.*

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
      new ApiResponse(responseCode = "200", description = "Liite vastaanotettu, palauttaa liitetunnisteen", content = Array(new Content(schema = new Schema(implementation = classOf[LuoLiiteSuccessResponseImpl])))),
      new ApiResponse(responseCode = "400", description = "Pyyntö on virheellinen", content = Array(new Content(schema = new Schema(implementation = classOf[LuoLiiteFailureResponseImpl])))),
      new ApiResponse(responseCode = "403", description = LAHETYS_RESPONSE_403_DESCRIPTION, content = Array(new Content(schema = new Schema(implementation = classOf[Void]))))
    ))
  def lisaaLiite(@RequestParam("liite", required=false) liite: Optional[MultipartFile]): ResponseEntity[LuoLiiteResponse] =
    val securityOperaatiot = new SecurityOperaatiot
    LogContext(path = LUO_LIITE_PATH, identiteetti = securityOperaatiot.getIdentiteetti())(() =>
      try
        Right(None)
          .flatMap(_ =>
            if (!securityOperaatiot.onOikeusLahettaa())
              LOG.warn("Lähetysoikeus puuttuu")
              Left(ResponseEntity.status(HttpStatus.FORBIDDEN).build())
            else
              Right(None))
          .flatMap(_ =>
            if (liite.isEmpty)
              LOG.warn("Liite puuttuu")
              Left(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(LuoLiiteFailureResponseImpl(Seq(LIITE_VIRHE_LIITE_PUUTTUU).asJava)))
            else
              Right(None))
          .flatMap(_ =>
            val validointiVirheet = LiiteValidator.validateLiite(Liite.builder().withFileName(liite.get.getOriginalFilename)
              .withBytes(liite.get.getBytes).withContentType(liite.get.getContentType).build())
            if (!validointiVirheet.isEmpty)
              LOG.warn("Liitteessä on validointivirheitä: " + validointiVirheet.mkString(","))
              Left(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(LuoLiiteFailureResponseImpl(validointiVirheet.toSeq.asJava)))
            else
              Right(None))
          .map(_ =>
            val identiteetti = securityOperaatiot.getIdentiteetti()
            val tallennettu = KantaOperaatiot(DbUtil.database).tallennaLiite(liite.get.getOriginalFilename, liite.get.getContentType, liite.get.getSize.toInt, identiteetti)
            LogContext(liiteTunniste = tallennettu.tunniste.toString)(() =>
              LOG.info("Tallennettu liite kantaan")
              val user = AuditLog.getUser(RequestContextHolder.getRequestAttributes.asInstanceOf[ServletRequestAttributes].getRequest)
              AuditLog.logCreate(user, Map("liiteTunniste" -> tallennettu.tunniste.toString), AuditOperation.CreateLiite, tallennettu)
              val putObjectResponse = AwsUtil.s3Client.putObject(PutObjectRequest
                .builder()
                .bucket(BUCKET_NAME)
                .key(tallennettu.tunniste.toString)
                .contentType(liite.get.getContentType)
                .build(), RequestBody.fromBytes(liite.get.getBytes))
              LOG.info("Tallennettu liite S3:een")
              ResponseEntity.status(HttpStatus.OK).body(LuoLiiteSuccessResponseImpl(tallennettu.tunniste))))
          .fold(e => e, r => r).asInstanceOf[ResponseEntity[LuoLiiteResponse]]
      catch
        case e: Exception =>
          LOG.error("Liitteen lisääminen epäonnistui", e)
          ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(LuoLiiteFailureResponseImpl(Seq(LahetysAPIConstants.LIITTEEN_LUONTI_EPAONNISTUI).asJava)))
}