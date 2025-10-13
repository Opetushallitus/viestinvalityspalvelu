package fi.oph.viestinvalitys.lahetys

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, SerializationFeature}
import fi.oph.viestinvalitys.business.*
import fi.oph.viestinvalitys.lahetys.LambdaHandler.*
import fi.oph.viestinvalitys.security.AuditLog
import fi.oph.viestinvalitys.security.AuditOperation
import fi.oph.viestinvalitys.util.*
import fi.vm.sade.auditlog.Changes
import org.crac.{Core, Resource}
import org.simplejavamail.api.email.{ContentTransferEncoding, Email, EmailPopulatingBuilder}
import org.simplejavamail.converter.EmailConverter
import org.simplejavamail.email.EmailBuilder
import org.slf4j.LoggerFactory
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.cloudwatch.model.{Dimension, MetricDatum, PutMetricDataRequest, StandardUnit}
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.ses.model.{RawMessage, SendRawEmailRequest, SesException}
import org.apache.commons.validator.routines.EmailValidator

import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util
import java.util.UUID
import scala.jdk.CollectionConverters.{CollectionHasAsScala, SeqHasAsJava}

object LambdaHandler {
  val SAHKOPOSTIOSOITE_EI_VALIDI_ERROR = "Sähköpostiosoite ei validi"

  val LOG = LoggerFactory.getLogger(classOf[LambdaHandler]);
  val queueUrl = ConfigurationUtil.getConfigurationItem(ConfigurationUtil.AJASTUS_QUEUE_URL_KEY).get;
  val kantaOperaatiot = new KantaOperaatiot(DbUtil.database)

  val bucketName = ConfigurationUtil.getConfigurationItem("ATTACHMENTS_BUCKET_NAME").get
  val configurationSetName = ConfigurationUtil.getConfigurationItem("CONFIGURATION_SET_NAME").get
  val fromEmailAddress = sys.env.getOrElse("FROM_EMAIL_ADDRESS", s"noreply@${ConfigurationUtil.opintopolkuDomain}")
  val namespace = sys.env.getOrElse("METRIC_DATA_NAMESPACE", s"${ConfigurationUtil.environment}-viestinvalitys")
  val mode = ConfigurationUtil.getMode()

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
      LambdaHandler.LOG.info(builder.to(vastaanottaja.kontakti.sahkoposti).buildEmail().toString)
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
            LOG.info(s"Lähetetään viestiä vastaanottajalle ${vastaanottaja.tunniste.toString}")

            if(!EmailValidator.getInstance().isValid(vastaanottaja.kontakti.sahkoposti))
              LOG.warn(s"Vastaanottajan ${vastaanottaja.tunniste.toString} sähköposti ei ole validi, siirretään suoraan virhetilaan")
              val changes: Changes = new Changes.Builder()
                .added("lisatiedot", SAHKOPOSTIOSOITE_EI_VALIDI_ERROR)
                .updated("vastaanottajanTila", vastaanottaja.tila.toString, VastaanottajanTila.VIRHE.toString)
                .build()
              AuditLog.logChanges(AuditLog.getAuditUserForLambda(), Map("vastaanottaja" -> vastaanottaja.tunniste.toString), AuditOperation.UpdateVastaanottajanTila, changes)
              kantaOperaatiot.paivitaVastaanottajaVirhetilaan(vastaanottaja.tunniste, SAHKOPOSTIOSOITE_EI_VALIDI_ERROR)
            else
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
                  sendTestEmail(vastaanottaja, builder.from(viesti.lahettaja.nimi.getOrElse(null), fromEmailAddress))
              }
              val changes: Changes = new Changes.Builder()
                .added("sesTunniste", sesTunniste)
                .updated("vastaanottajanTila",vastaanottaja.tila.toString, VastaanottajanTila.LAHETETTY.toString)
                .build()
              AuditLog.logChanges(AuditLog.getAuditUserForLambda(), Map("vastaanottaja" -> vastaanottaja.tunniste.toString), AuditOperation.SendEmail, changes)
              LOG.info(s"Lähetetty viesti vastaanottajalle ${vastaanottaja.tunniste.toString}")
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
            case e: SesException if e.isThrottlingException =>
              LOG.error(s"Kuristus lähettäessä viestiä vastaanottajalle ${vastaanottaja.tunniste.toString}, lähetystä kokeillaan myöhemmin uudestaan", e)
              val changes: Changes = new Changes.Builder()
                .added("lisatiedot", e.getMessage)
                .updated("vastaanottajanTila", vastaanottaja.tila.toString, VastaanottajanTila.ODOTTAA.toString)
                .build()
              AuditLog.logChanges(AuditLog.getAuditUserForLambda(), Map("vastaanottaja" -> vastaanottaja.tunniste.toString), AuditOperation.UpdateVastaanottajanTila, changes)
              kantaOperaatiot.paivitaVastaanottajaOdottaaTilaan(vastaanottaja.tunniste, e.getMessage)
            case e: Exception =>
              LOG.error(s"Virhe lähetettäessä viestiä vastaanottajalle ${vastaanottaja.tunniste.toString}", e)
              val changes: Changes = new Changes.Builder()
                .added("lisatiedot", e.getMessage)
                .updated("vastaanottajanTila", vastaanottaja.tila.toString, VastaanottajanTila.VIRHE.toString)
                .build()
              AuditLog.logChanges(AuditLog.getAuditUserForLambda(), Map("vastaanottaja" -> vastaanottaja.tunniste.toString), AuditOperation.UpdateVastaanottajanTila, changes)
              kantaOperaatiot.paivitaVastaanottajaVirhetilaan(vastaanottaja.tunniste, e.getMessage)
          }
        })
      })

      if(!metricDatums.isEmpty)
        AwsUtil.cloudWatchClient.putMetricData(PutMetricDataRequest.builder()
          .namespace(namespace)
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
