package fi.oph.viestinvalitys.skannaus

import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import com.amazonaws.services.lambda.runtime.events.{SQSBatchResponse, SQSEvent}
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import fi.oph.viestinvalitys.business.{KantaOperaatiot, LiitteenTila}
import fi.oph.viestinvalitys.security.{AuditLog, AuditOperation}
import fi.oph.viestinvalitys.util.{AwsUtil, ConfigurationUtil, DbUtil, LogContext}
import fi.vm.sade.auditlog.Changes
import org.crac.Resource
import org.slf4j.LoggerFactory
import slick.jdbc.PostgresProfile.api.*

import java.util
import java.util.UUID
import scala.beans.BeanProperty
import scala.jdk.CollectionConverters.*

case class SqsViesti(@BeanProperty Message: String) {
  def this() = {
    this(null)
  }
}

case class BucketAVViesti(@BeanProperty bucket: String, @BeanProperty key: String, @BeanProperty status: String) {
  def this() = {
    this(null, null, null)
  }
}

class LambdaHandler extends RequestHandler[SQSEvent, SQSBatchResponse], Resource {

  val LOG = LoggerFactory.getLogger(classOf[LambdaHandler]);
  val queueUrl = ConfigurationUtil.getConfigurationItem(ConfigurationUtil.SKANNAUS_QUEUE_URL_KEY).get;

  val mapper = {
    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)
    mapper.registerModule(new Jdk8Module()) // tämä on java.util.Optional -kenttiä varten
    mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    mapper
  }

  def deserialisoiBucketAVViesti(viesti: String): Option[BucketAVViesti] =
    val sqsViesti = mapper.readValue(viesti, classOf[SqsViesti])
    if(sqsViesti.Message==null)
      Option.empty
    else
      Option.apply(mapper.readValue(sqsViesti.Message, classOf[BucketAVViesti]))

  override def handleRequest(event: SQSEvent, context: Context): SQSBatchResponse = {
    LogContext(requestId = context.getAwsRequestId, functionName = context.getFunctionName)(() => {
      LOG.info("Prosessoidaan BucketAV-viestit")
      val failures = event.getRecords.asScala.flatMap(processSingleMessage)
      SQSBatchResponse.builder().withBatchItemFailures(failures.asJava).build()
    })
  }

  def processSingleMessage(sqsMessage: SQSMessage): Option[SQSBatchResponse.BatchItemFailure] = {
    try
      val message = deserialisoiBucketAVViesti(sqsMessage.getBody)
      if (message.isEmpty)
        LOG.warn("BucketAV-viesti on tyhjä")
      else
        val tunniste = {
          try
            Option.apply(UUID.fromString(message.get.key))
          catch
            case e: Exception =>
              LOG.info("Tiedostonimi ei UUID-muotoinen")
              Option.empty
        }
        tunniste.foreach(tunniste => {
          LogContext(liiteTunniste = tunniste.toString)(() => {
            val uusiTila = message.get.status match
              case "clean" => LiitteenTila.PUHDAS
              case "infected" => LiitteenTila.SAASTUNUT
              case _ => LiitteenTila.VIRHE
            LOG.info("Päivitetään liitteen tila tilaan: " + uusiTila.toString)
            val changes: Changes = new Changes.Builder()
              .updated("liitteenTila", LiitteenTila.SKANNAUS.toString, uusiTila.toString)
              .build()
            AuditLog.logChanges(AuditLog.getAuditUserForLambda(), Map("liite" -> tunniste.toString), AuditOperation.UpdateLiitteenTila, changes)

            KantaOperaatiot(DbUtil.database).paivitaLiitteenTila(UUID.fromString(message.get.key), uusiTila)
          })
        })
      None
    catch
      case e: Exception =>
        LOG.error("Virhe prosessoitaesssa BucketAV-viestiä", e)
        Some(SQSBatchResponse.BatchItemFailure.builder()
          .withItemIdentifier(sqsMessage.getMessageId)
          .build())
  }

  @throws[Exception]
  def beforeCheckpoint(context: org.crac.Context[_ <: Resource]): Unit = {
    AwsUtil.sqsClient
  }

  @throws[Exception]
  def afterRestore(context: org.crac.Context[_ <: Resource]): Unit = {
  }
}
