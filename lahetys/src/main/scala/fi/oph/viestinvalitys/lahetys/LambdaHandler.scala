package fi.oph.viestinvalitys.lahetys

import com.amazonaws.serverless.exceptions.ContainerInitializationException
import com.amazonaws.serverless.proxy.model.{AwsProxyResponse, HttpApiV2ProxyRequest}
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler
import com.amazonaws.services.lambda.runtime.events.{SNSEvent, SQSEvent}
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler, RequestStreamHandler}
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, SerializationFeature}
import fi.oph.viestinvalitys.aws.AwsUtil
import fi.oph.viestinvalitys.business.{LahetysOperaatiot, LiitteenTila, SisallonTyyppi, VastaanottajanTila}
import fi.oph.viestinvalitys.db.DbUtil
import jakarta.mail.Message.RecipientType
import org.postgresql.ds.PGSimpleDataSource
import org.simplejavamail.api.email.{ContentTransferEncoding, Recipient}
import org.simplejavamail.api.mailer.config.TransportStrategy
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

class LambdaHandler extends RequestHandler[java.util.List[UUID], Void] {

  val LOG = LoggerFactory.getLogger(classOf[LambdaHandler]);

  override def handleRequest(viestiTunnisteet: java.util.List[UUID], context: Context): Void = {
    val lahetysOperaatiot = new LahetysOperaatiot(DbUtil.getDatabase())
    val (vastaanottajat, viestit, viestinLiitteet) = lahetysOperaatiot.getLahetysData(viestiTunnisteet.asScala.toSeq)

    val mailer = MailerBuilder
      .withSMTPServerHost("fakemailer-1.fakemailer.hahtuvaopintopolku.fi")
      .withSMTPServerPort(1025)
      .withTransportStrategy(TransportStrategy.SMTP)
      .withSessionTimeout(10 * 1000).buildMailer()

    vastaanottajat.foreach(vastaanottaja => {
      LOG.info("Lähetetään viestiä: " + vastaanottaja.tunniste)

      val viesti = viestit.get(vastaanottaja.viestiTunniste).get
      var builder = EmailBuilder.startingBlank()
        .to(vastaanottaja.kontakti.nimi, vastaanottaja.kontakti.sahkoposti)
        .from(viesti.lahettaja.nimi, viesti.lahettaja.sahkoposti)
        .withContentTransferEncoding(ContentTransferEncoding.BASE_64)
        .withSubject(viesti.otsikko)

      viesti.sisallonTyyppi match {
        case SisallonTyyppi.TEXT => builder = builder.withPlainText(viesti.sisalto)
        case SisallonTyyppi.HTML => builder = builder.withHTMLText(viesti.sisalto)
      }

      viestinLiitteet.get(viesti.tunniste).get.foreach(liite => {
        // TODO: varmista että liitteet oikeassa järjestyksessä
        val getObjectResponse = AwsUtil.getS3Client().getObject(GetObjectRequest
          .builder()
          .bucket("hahtuva-viestinvalityspalvelu-attachments")
          .key(liite.tunniste.toString)
          .build())
        builder = builder.withAttachment(liite.nimi, getObjectResponse.readAllBytes(), liite.contentType)
      })

      try {
        val email = builder.buildEmail()
        mailer.sendMail(email)
        LOG.info("Lähetetty viesti: " + email.getId)
        lahetysOperaatiot.paivitaVastaanottajanTila(vastaanottaja.tunniste, VastaanottajanTila.LAHETETTY)
      } catch {
        case e: Exception => lahetysOperaatiot.paivitaVastaanottajanTila(vastaanottaja.tunniste, VastaanottajanTila.VIRHE)
      }
    })
    null
  }
}
