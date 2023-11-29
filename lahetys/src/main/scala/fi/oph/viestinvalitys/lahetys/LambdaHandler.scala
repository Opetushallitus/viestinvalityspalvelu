package fi.oph.viestinvalitys.lahetys

import com.amazonaws.serverless.exceptions.ContainerInitializationException
import com.amazonaws.serverless.proxy.model.{AwsProxyResponse, HttpApiV2ProxyRequest}
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler
import com.amazonaws.services.lambda.runtime.events.{SNSEvent, SQSEvent}
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler, RequestStreamHandler}
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, SerializationFeature}
import com.sun.mail.iap.ByteArray
import fi.oph.viestinvalitys.aws.AwsUtil
import fi.oph.viestinvalitys.business.{LahetysOperaatiot, LiitteenTila, SisallonTyyppi, Vastaanottaja, VastaanottajanTila}
import fi.oph.viestinvalitys.db.{ConfigurationUtil, DbUtil, Mode}
import jakarta.mail.Message.RecipientType
import org.apache.commons.io.IOUtils
import org.apache.commons.io.output.ByteArrayOutputStream
import org.postgresql.ds.PGSimpleDataSource
import org.simplejavamail.api.email.{ContentTransferEncoding, Email, EmailPopulatingBuilder, Recipient}
import org.simplejavamail.api.mailer.config.TransportStrategy
import org.simplejavamail.converter.EmailConverter
import org.simplejavamail.email.EmailBuilder
import org.simplejavamail.mailer.MailerBuilder
import org.simplejavamail.mailer.internal.MailerRegularBuilderImpl
import org.slf4j.{Logger, LoggerFactory}
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ConfigurableApplicationContext
import slick.jdbc.PostgresProfile.api.*
import slick.lifted.TableQuery
import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{GetObjectRequest, GetObjectTaggingRequest}
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParameterRequest

import java.time.Instant
import java.util
import java.util.UUID
import java.util.stream.Collectors
import scala.beans.BeanProperty
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.jdk.CollectionConverters.SeqHasAsJava
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcBackend.Database
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.ses.model.{RawMessage, SendEmailRequest, SendRawEmailRequest}

class LambdaHandler extends RequestHandler[java.util.List[UUID], Void] {

  val BUCKET_NAME = ConfigurationUtil.getConfigurationItem("ATTACHMENTS_BUCKET_NAME").get
  val LOG = LoggerFactory.getLogger(classOf[LambdaHandler]);
  val mode = ConfigurationUtil.getMode()

  val fakemailerHost = ConfigurationUtil.getConfigurationItem("FAKEMAILER_HOST").getOrElse(null)
  val fakemailerPort = ConfigurationUtil.getConfigurationItem("FAKEMAILER_PORT").map(value => value.toInt).getOrElse(-1)
  val fakeMailer = {
    if (mode!=Mode.PRODUCTION)
      MailerBuilder
        .withSMTPServerHost(fakemailerHost)
        .withSMTPServerPort(fakemailerPort)
        .withTransportStrategy(TransportStrategy.SMTP)
        .withSessionTimeout(10 * 1000).buildMailer()
    else
      null
  }

  val sesClient = AwsUtil.getSesClient();
  def sendSesEmail(email: Email): Unit =
    val stream = new ByteArrayOutputStream()
    EmailConverter.emailToMimeMessage(email).writeTo(stream)

    sesClient.sendRawEmail(SendRawEmailRequest.builder()
      .rawMessage(RawMessage.builder()
        .data(SdkBytes.fromByteArray(stream.toByteArray))
        .build())
      .build())

  private def sendTestEmail(vastaanottaja: Vastaanottaja, builder: EmailPopulatingBuilder): Unit =
    LOG.info("Lähetetään viestiä testimoodissa")
    if (vastaanottaja.kontakti.sahkoposti.split("@")(0).endsWith("+bounce"))
      this.sendSesEmail(builder.to("bounce@simulator.amazonses.com").buildEmail())
    else if (vastaanottaja.kontakti.sahkoposti.split("@")(0).endsWith("+success"))
      this.sendSesEmail(builder.to("success@simulator.amazonses.com").buildEmail())
    else
      fakeMailer.sendMail(builder.to(vastaanottaja.kontakti.sahkoposti).buildEmail())

  override def handleRequest(vastaanottajaTunnisteet: java.util.List[UUID], context: Context): Void = {
    val lahetysOperaatiot = new LahetysOperaatiot(DbUtil.getDatabase())
    val vastaanottajat = lahetysOperaatiot.getVastaanottajat(vastaanottajaTunnisteet.asScala.toSeq)
    val viestiTunnisteet = vastaanottajat.map(v => v.viestiTunniste).toSet.toSeq
    val viestit = lahetysOperaatiot.getViestit(viestiTunnisteet).map(v => v.tunniste -> v).toMap
    val viestinLiitteet = lahetysOperaatiot.getViestinLiitteet(viestiTunnisteet)

    vastaanottajat.foreach(vastaanottaja => {
      try {
        LOG.info("Lähetetään viestiä: " + vastaanottaja.tunniste)

        val viesti = viestit.get(vastaanottaja.viestiTunniste).get
        var builder = EmailBuilder.startingBlank()
          .from(viesti.lahettaja.nimi, "santeri.korri@knowit.fi")
          .withContentTransferEncoding(ContentTransferEncoding.BASE_64)
          .withSubject(viesti.otsikko)
          .fixingMessageId(vastaanottaja.tunniste.toString)

        viesti.sisallonTyyppi match {
          case SisallonTyyppi.TEXT => builder = builder.withPlainText(viesti.sisalto)
          case SisallonTyyppi.HTML => builder = builder.withHTMLText(viesti.sisalto)
        }

        viestinLiitteet.get(viesti.tunniste).foreach(liitteet => liitteet.foreach(liite => {
          val getObjectResponse = AwsUtil.getS3Client().getObject(GetObjectRequest
            .builder()
            .bucket(BUCKET_NAME)
            .key(liite.tunniste.toString)
            .build())
          builder = builder.withAttachment(liite.nimi, getObjectResponse.readAllBytes(), liite.contentType)
        }))

        if(mode==Mode.PRODUCTION)
          this.sendSesEmail(builder.buildEmail())
        else
          sendTestEmail(vastaanottaja, builder)

        LOG.info("Lähetetty viesti: " + vastaanottaja.tunniste)
        lahetysOperaatiot.paivitaVastaanottajanTila(vastaanottaja.tunniste, VastaanottajanTila.LAHETETTY, Option.empty)
      } catch {
        case e: Exception =>
          LOG.error("Lähetyksessä tapahtui virhe: " + vastaanottaja.tunniste, e)
          lahetysOperaatiot.paivitaVastaanottajanTila(vastaanottaja.tunniste, VastaanottajanTila.VIRHE, Option.apply(e.getMessage))
      }
    })
    null
  }
}
