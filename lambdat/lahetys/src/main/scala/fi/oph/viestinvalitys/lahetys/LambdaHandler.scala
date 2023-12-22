package fi.oph.viestinvalitys.lahetys

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, SerializationFeature}
import fi.oph.viestinvalitys.aws.AwsUtil
import fi.oph.viestinvalitys.business.{KantaOperaatiot, SisallonTyyppi, Vastaanottaja}
import fi.oph.viestinvalitys.db.{ConfigurationUtil, DbUtil, Mode}
import LambdaHandler.*
import org.postgresql.ds.PGSimpleDataSource
import org.slf4j.{Logger, LoggerFactory}
import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.{DeleteMessageBatchRequest, DeleteMessageBatchRequestEntry}
import software.amazon.awssdk.services.lambda.LambdaClient
import software.amazon.awssdk.services.lambda.model.InvokeRequest

import java.nio.charset.{Charset, StandardCharsets}
import java.time.Instant
import java.util
import java.util.UUID
import java.util.stream.Collectors
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.jdk.CollectionConverters.SeqHasAsJava
import org.crac.{Core, Resource}
import org.simplejavamail.api.email.{ContentTransferEncoding, Email, EmailPopulatingBuilder}
import org.simplejavamail.api.mailer.config.TransportStrategy
import org.simplejavamail.converter.EmailConverter
import org.simplejavamail.email.EmailBuilder
import org.simplejavamail.mailer.MailerBuilder
import software.amazon.awssdk.services.cloudwatch.model.{Dimension, MetricDatum, PutMetricDataRequest, StandardUnit}
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.ses.model.{RawMessage, SendRawEmailRequest}

import java.io.ByteArrayOutputStream

object LambdaHandler {
  val LOG = LoggerFactory.getLogger(classOf[LambdaHandler]);
  val queueUrl = ConfigurationUtil.getConfigurationItem(ConfigurationUtil.AJASTUS_QUEUE_URL_KEY).get;
  val kantaOperaatiot = new KantaOperaatiot(DbUtil.database)

  val bucketName = ConfigurationUtil.getConfigurationItem("ATTACHMENTS_BUCKET_NAME").get
  val configurationSetName = ConfigurationUtil.getConfigurationItem("CONFIGURATION_SET_NAME").get
  val mode = ConfigurationUtil.getMode()

  val fakemailerHost = ConfigurationUtil.getConfigurationItem("FAKEMAILER_HOST").getOrElse(null)
  val fakemailerPort = ConfigurationUtil.getConfigurationItem("FAKEMAILER_PORT").map(value => value.toInt).getOrElse(-1)
  val fakeMailer = {
    if (mode != Mode.PRODUCTION)
      MailerBuilder
        .withSMTPServerHost(fakemailerHost)
        .withSMTPServerPort(fakemailerPort)
        .withTransportStrategy(TransportStrategy.SMTP)
        .withSessionTimeout(10 * 1000).buildMailer()
    else
      null
  }

  val mapper = {
    val mapper = new ObjectMapper()
    mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    mapper.configure(SerializationFeature.INDENT_OUTPUT, true)
    mapper
  }
}

class LambdaHandler extends RequestHandler[SQSEvent, Void], Resource {
  Core.getGlobalContext.register(this)

  def sendSesEmail(email: Email): String =
    val stream = new ByteArrayOutputStream()
    EmailConverter.emailToMimeMessage(email).writeTo(stream)

    AwsUtil.sesClient.sendRawEmail(SendRawEmailRequest.builder()
      .configurationSetName(configurationSetName)
      .rawMessage(RawMessage.builder()
        .data(SdkBytes.fromByteArray(stream.toByteArray))
        .build())
      .build()).messageId()

  private def sendTestEmail(vastaanottaja: Vastaanottaja, builder: EmailPopulatingBuilder): String =
    LambdaHandler.LOG.info("Lähetetään viestiä testimoodissa")
    if (vastaanottaja.kontakti.sahkoposti.split("@")(0).endsWith("+bounce"))
      this.sendSesEmail(builder.to("bounce@simulator.amazonses.com").buildEmail())
    else if (vastaanottaja.kontakti.sahkoposti.split("@")(0).endsWith("+complaint"))
      this.sendSesEmail(builder.to("complaint@simulator.amazonses.com").buildEmail())
    else if (vastaanottaja.kontakti.sahkoposti.split("@")(0).endsWith("+success"))
      this.sendSesEmail(builder.to("success@simulator.amazonses.com").buildEmail())
    else
      fakeMailer.sendMail(builder.to(vastaanottaja.kontakti.sahkoposti).buildEmail())
      vastaanottaja.tunniste.toString

  def laheta(maara: Int): Unit =
    val vastaanottajaTunnisteet = LambdaHandler.kantaOperaatiot.getLahetettavatVastaanottajat(maara)
    if(vastaanottajaTunnisteet.isEmpty)  return

    LOG.info("Haetaan vastaanottajien tiedot")
    val vastaanottajat = kantaOperaatiot.getVastaanottajat(vastaanottajaTunnisteet)
    val viestiTunnisteet = vastaanottajat.map(v => v.viestiTunniste).toSet.toSeq
    val viestit = kantaOperaatiot.getViestit(viestiTunnisteet).map(v => v.tunniste -> v).toMap
    val viestinLiitteet = kantaOperaatiot.getViestinLiitteet(viestiTunnisteet)
    val metricDatums: java.util.Collection[MetricDatum] = new util.ArrayList[MetricDatum]()
    vastaanottajat.foreach(vastaanottaja => {
      try {
        LOG.info("Lähetetään viestiä: " + vastaanottaja.tunniste)

        val viesti = viestit.get(vastaanottaja.viestiTunniste).get
        var builder = EmailBuilder.startingBlank()
          .withContentTransferEncoding(ContentTransferEncoding.BASE_64)
          .withSubject(viesti.otsikko)

        if(viesti.replyTo.isDefined)
          builder.withReplyTo(viesti.replyTo.get)

        viesti.sisallonTyyppi match {
          case SisallonTyyppi.TEXT => builder = builder.withPlainText(viesti.sisalto)
          case SisallonTyyppi.HTML => builder = builder.withHTMLText(viesti.sisalto)
        }

        viestinLiitteet.get(viesti.tunniste).foreach(liitteet => liitteet.foreach(liite => {
          val getObjectResponse = AwsUtil.s3Client.getObject(GetObjectRequest
            .builder()
            .bucket(bucketName)
            .key(liite.tunniste.toString)
            .build())
          builder = builder.withAttachment(liite.nimi, getObjectResponse.readAllBytes(), liite.contentType)
        }))

        val sesTunniste = {
          if (mode == Mode.PRODUCTION)
            this.sendSesEmail(builder
              .from(viesti.lahettaja.nimi.getOrElse(null), viesti.lahettaja.sahkoposti)
              .to(vastaanottaja.kontakti.nimi.getOrElse(null), vastaanottaja.kontakti.sahkoposti)
              .buildEmail())
          else
            sendTestEmail(vastaanottaja, builder.from(viesti.lahettaja.nimi.getOrElse(null), "noreply@hahtuvaopintopolku.fi"))
        }

        LOG.info("Lähetetty viesti: " + vastaanottaja.tunniste)
        kantaOperaatiot.paivitaVastaanottajaLahetetyksi(vastaanottaja.tunniste, sesTunniste)

        metricDatums.add(MetricDatum.builder()
          .metricName("LahetyksienMaara")
          .value(1)
          .storageResolution(1)
          .dimensions(Seq(Dimension.builder()
            .name("Prioriteetti")
            .value(viesti.prioriteetti.toString)
            .build()).asJava)
          .timestamp(Instant.now())
          .unit(StandardUnit.COUNT)
          .build())
      } catch {
        case e: Exception =>
          LOG.error("Lähetyksessä tapahtui virhe: " + vastaanottaja.tunniste, e)
          kantaOperaatiot.paivitaVastaanottajaVirhetilaan(vastaanottaja.tunniste, e.getMessage)
      }
    })

    if(!metricDatums.isEmpty)
      AwsUtil.cloudWatchClient.putMetricData(PutMetricDataRequest.builder()
        .namespace("Viestinvalitys")
        .metricData(metricDatums)
        .build())

  override def handleRequest(event: SQSEvent, context: Context): Void = {
    LambdaHandler.LOG.debug("Poistetaan viestit")
    AwsUtil.deleteMessages(event.getRecords, LambdaHandler.queueUrl)

    val now = Instant.now
    event.getRecords.asScala.foreach(message => {
      val viestiTimestamp = Instant.parse(message.getBody)
      val sqsViive = now.toEpochMilli - viestiTimestamp.toEpochMilli
      if(sqsViive>1000)
        LambdaHandler.LOG.info("Ohitetaan vanha viesti: " + viestiTimestamp)
      else
        LambdaHandler.LOG.info("Ajetaan lähetys: " + viestiTimestamp)
        laheta(130)
    })
    null
  }

  @throws[Exception]
  def beforeCheckpoint(context: org.crac.Context[_ <: Resource]): Unit =
    LambdaHandler.LOG.info("Before checkpoint")
    AwsUtil.sqsClient
    AwsUtil.sesClient
    laheta(0)

  @throws[Exception]
  def afterRestore(context: org.crac.Context[_ <: Resource]): Unit =
    LambdaHandler.LOG.info("After restore")
    DbUtil.flushDataSource()

}
