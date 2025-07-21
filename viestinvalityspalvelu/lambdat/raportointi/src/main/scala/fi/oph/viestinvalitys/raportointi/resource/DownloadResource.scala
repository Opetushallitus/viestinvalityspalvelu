package fi.oph.viestinvalitys.raportointi.resource

import fi.oph.viestinvalitys.business.{KantaOperaatiot, SisallonTyyppi}
import fi.oph.viestinvalitys.util.{AwsUtil, ConfigurationUtil, DbUtil}
import io.swagger.v3.oas.annotations.Hidden
import org.simplejavamail.api.email.{ContentTransferEncoding, Email}
import org.simplejavamail.converter.EmailConverter
import org.simplejavamail.email.EmailBuilder
import org.springframework.web.bind.annotation.{GetMapping, RequestMapping, RequestParam, RestController}
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import org.springframework.http.{HttpHeaders, ResponseEntity, ContentDisposition, MediaType}
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

import java.util
import scala.jdk.CollectionConverters.*
import java.util.{Base64, UUID}

@Hidden
@RestController("DownloadResource")
@RequestMapping(path = Array("/raportointi/v1/download"))
@ConditionalOnProperty(name = Array("viestinvalitys_features_downloadViesti_enabled"), matchIfMissing = false)
class DownloadResource {
  private val bucketName = ConfigurationUtil.getConfigurationItem("ATTACHMENTS_BUCKET_NAME").get

  @GetMapping(path = Array("/viesti"))
  def generateEml(@RequestParam(name = "viestiTunniste", required = true) viestiTunniste: UUID) = {
    getViesti(viestiTunniste)
      .map(convertToEML)
      .map(eml => {
        val headers = new HttpHeaders()
        headers.setContentType(MediaType.parseMediaType("message/rfc822"))
        headers.setContentLength(eml.length)
        headers.setContentDisposition(ContentDisposition
          .attachment()
          .filename(s"viesti-$viestiTunniste.eml")
          .build())

        ResponseEntity
          .ok()
          .headers(headers)
          .body(eml)
      })
      .getOrElse(ResponseEntity.notFound().build())
  }

  private def base64Encode(s: String) = Base64.getEncoder.encodeToString(s.getBytes)

  private def convertToEML(email: Email) = EmailConverter.emailToEML(email)

  private def getViesti(viestiTunniste: UUID) = {
    val kantaOperaatiot = new KantaOperaatiot(DbUtil.database)
    kantaOperaatiot.getViestit(Seq(viestiTunniste)).map { viesti =>
      var builder = EmailBuilder.startingBlank()
        .withContentTransferEncoding(ContentTransferEncoding.BASE_64)
        .withSubject(viesti.otsikko)

      if (viesti.replyTo.isDefined) {
        builder.withReplyTo(viesti.replyTo.get)
      }

      viesti.sisallonTyyppi match {
        case SisallonTyyppi.TEXT => builder = builder.withPlainText(viesti.sisalto)
        case SisallonTyyppi.HTML => builder = builder.withHTMLText(viesti.sisalto)
      }

      kantaOperaatiot.getViestinLiitteet(Seq(viesti.tunniste))
        .find((viestiTunniste, liitteet) => true)
        .map((viestiTunniste, liitteet) => liitteet.map(liite => {
          val getObjectResponse = AwsUtil.s3Client.getObject(GetObjectRequest
            .builder()
            .bucket(bucketName)
            .key(liite.tunniste.toString)
            .build())
          (liite, getObjectResponse.readAllBytes)
        })).getOrElse(Seq.empty).foreach((liite, bytes) => {
          builder = builder.withAttachment(liite.nimi, bytes, liite.contentType)
      })

      builder.buildEmail();
    }.headOption
}}
