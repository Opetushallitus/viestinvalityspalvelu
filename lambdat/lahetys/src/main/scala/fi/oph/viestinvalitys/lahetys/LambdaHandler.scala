package fi.oph.viestinvalitys.lahetys

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, SerializationFeature}
import fi.oph.viestinvalitys.business.*
import fi.oph.viestinvalitys.lahetys.LambdaHandler.*
import fi.oph.viestinvalitys.util.*
import org.crac.{Core, Resource}
import org.simplejavamail.api.email.{ContentTransferEncoding, Email, EmailPopulatingBuilder}
import org.simplejavamail.api.mailer.config.TransportStrategy
import org.simplejavamail.converter.EmailConverter
import org.simplejavamail.email.EmailBuilder
import org.simplejavamail.mailer.MailerBuilder
import org.slf4j.LoggerFactory
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.cloudwatch.model.{Dimension, MetricDatum, PutMetricDataRequest, StandardUnit}
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.ses.model.{RawMessage, SendRawEmailRequest}

import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util
import java.util.UUID
import scala.jdk.CollectionConverters.{CollectionHasAsScala, SeqHasAsJava}

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
    if(!vastaanottajaTunnisteet.isEmpty)
      val viestit = new scala.collection.mutable.HashMap[UUID, Viesti]()
      val liitteet = new scala.collection.mutable.HashMap[UUID, Seq[(Liite, Array[Byte])]]()

      LOG.info("Haetaan vastaanottajien tiedot tunnisteille: " + vastaanottajaTunnisteet.mkString(","))
      val vastaanottajat = kantaOperaatiot.getVastaanottajat(vastaanottajaTunnisteet)
      val metricDatums: java.util.Collection[MetricDatum] = new util.ArrayList[MetricDatum]()
      vastaanottajat.foreach(vastaanottaja => {
        LogContext(vastaanottajaTunniste = vastaanottaja.tunniste.toString, viestiTunniste = vastaanottaja.viestiTunniste.toString)(() => {
          try {
            LOG.info("Lähetetään viestiä vastaanottajalle")
            val viesti = viestit.getOrElseUpdate(vastaanottaja.viestiTunniste, kantaOperaatiot.getViestit(Seq(vastaanottaja.viestiTunniste)).find(v => true).get)

            var builder = EmailBuilder.startingBlank()
              .withContentTransferEncoding(ContentTransferEncoding.BASE_64)
              .withSubject(viesti.otsikko)

            if (viesti.replyTo.isDefined)
              builder.withReplyTo(viesti.replyTo.get)

            viesti.sisallonTyyppi match {
              case SisallonTyyppi.TEXT => builder = builder.withPlainText(viesti.sisalto)
              case SisallonTyyppi.HTML => builder = builder.withHTMLText(viesti.sisalto)
            }

            liitteet.getOrElseUpdate(viesti.tunniste, kantaOperaatiot.getViestinLiitteet(Seq(viesti.tunniste))
              .find((viestiTunniste, liitteet) => true).map((viestiTunniste, liitteet) => liitteet.map(liite => {
              val getObjectResponse = AwsUtil.s3Client.getObject(GetObjectRequest
                .builder()
                .bucket(bucketName)
                .key(liite.tunniste.toString)
                .build())
              (liite, getObjectResponse.readAllBytes)
            })).getOrElse(Seq.empty)).foreach((liite, bytes) => {
              builder = builder.withAttachment(liite.nimi, bytes, liite.contentType)
            })

            val sesTunniste = {
              if (mode == Mode.PRODUCTION)
                this.sendSesEmail(builder
                  .from(viesti.lahettaja.nimi.getOrElse(null), viesti.lahettaja.sahkoposti)
                  .to(vastaanottaja.kontakti.nimi.getOrElse(null), vastaanottaja.kontakti.sahkoposti)
                  .buildEmail())
              else
                sendTestEmail(vastaanottaja, builder.from(viesti.lahettaja.nimi.getOrElse(null), s"noreply@${ConfigurationUtil.opintopolkuDomain}"))
            }

            LOG.info("Lähetetty viesti vastaanottajalle")
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
              LOG.error("Virhe lähetettäessä viestiä vastaanottajalle", e)
              kantaOperaatiot.paivitaVastaanottajaVirhetilaan(vastaanottaja.tunniste, e.getMessage)
          }
        })
      })

      if(!metricDatums.isEmpty)
        AwsUtil.cloudWatchClient.putMetricData(PutMetricDataRequest.builder()
          .namespace("Viestinvalitys")
          .metricData(metricDatums)
          .build())

  override def handleRequest(event: SQSEvent, context: Context): Void = {
    LogContext(requestId = context.getAwsRequestId, functionName = context.getFunctionName)(() => {
      LambdaHandler.LOG.debug("Poistetaan ajastusviestit jonosta")
      AwsUtil.deleteMessages(event.getRecords, LambdaHandler.queueUrl)

      val now = Instant.now
      event.getRecords.asScala.foreach(message => {
        val viestiTimestamp = Instant.parse(message.getBody)
        val sqsViive = now.toEpochMilli - viestiTimestamp.toEpochMilli
        if (sqsViive > 1000)
        // tämä on tilanne jossa lähetyslambda on ollut poissa toiminnasta ja ajastusjonoon on kertynyt
        // paljon viestejä
          LambdaHandler.LOG.info("Ohitetaan vanha ajastusviesti: " + viestiTimestamp)
        else
          LambdaHandler.LOG.info("Ajetaan lähetys: " + viestiTimestamp)
          laheta(ConfigurationUtil.AJASTUS_POLLING_INTERVAL_SECONDS * ConfigurationUtil.AJASTUS_SENDING_QUOTA_PER_SECOND)
      })
      null
    })
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
