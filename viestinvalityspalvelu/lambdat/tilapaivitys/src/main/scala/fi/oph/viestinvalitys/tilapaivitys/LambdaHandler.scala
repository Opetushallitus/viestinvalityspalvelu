package fi.oph.viestinvalitys.tilapaivitys

import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import com.amazonaws.services.lambda.runtime.events.{SQSBatchResponse, SQSEvent}
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import fi.oph.viestinvalitys.business.{KantaOperaatiot, VastaanottajanTila}
import fi.oph.viestinvalitys.security.{AuditLog, AuditOperation}
import fi.oph.viestinvalitys.tilapaivitys.LambdaHandler.kantaOperaatiot
import fi.oph.viestinvalitys.util.{AwsUtil, ConfigurationUtil, DbUtil, LogContext}
import fi.vm.sade.auditlog.Changes
import org.crac.{Core, Resource}
import org.slf4j.LoggerFactory
import slick.jdbc.JdbcBackend
import slick.jdbc.PostgresProfile.api.*

import java.util
import scala.jdk.CollectionConverters.*

object LambdaHandler {

  val kantaOperaatiot = KantaOperaatiot(DbUtil.database)
}

class LambdaHandler extends RequestHandler[SQSEvent, SQSBatchResponse], Resource {
  Core.getGlobalContext.register(this)

  val queueUrl = ConfigurationUtil.getConfigurationItem(ConfigurationUtil.SESMONITOROINTI_QUEUE_URL_KEY).get;
  val LOG = LoggerFactory.getLogger(classOf[LambdaHandler]);

  override def handleRequest(event: SQSEvent, context: Context): SQSBatchResponse = {
    LogContext(requestId = context.getAwsRequestId, functionName = context.getFunctionName)(() => {
      LOG.info("Prosessoidaan SES-viesti")
      val failures = event.getRecords.asScala.flatMap(processSingleMessage)
      SQSBatchResponse.builder().withBatchItemFailures(failures.asJava).build()
    })
  }

  def processSingleMessage(sqsMessage: SQSMessage): Option[SQSBatchResponse.BatchItemFailure] = {
    try
      val message = Deserialisoija.deserialisoiSqsViesti(sqsMessage.getBody)
      if (message.isEmpty)
        LOG.warn("SES-viesti on tyhjä")
      else
        val messageId = message.get.mail.messageId
        val siirtyma = message.get.asVastaanottajanSiirtyma()
        if (siirtyma.isDefined)
          val (vastaanottajanTila, lisatiedot) = siirtyma.get
          val changes: Changes = new Changes.Builder()
            .added("lisatiedot", lisatiedot.getOrElse(""))
            .added("vastaanottajanTila", vastaanottajanTila.toString)
            .build()
          AuditLog.logChanges(AuditLog.getAuditUserForLambda(), Map("sesTunniste" -> messageId), AuditOperation.UpdateVastaanottajanTila, changes)
          LOG.info("Siirretään viesti " + messageId + " tilaan " + vastaanottajanTila.toString)
          kantaOperaatiot.paivitaVastaanotonTila(messageId, vastaanottajanTila, lisatiedot)
        else
          LOG.info("Viestin " + messageId + " tilalle ei ole määritelty siirtymää")
      None
    catch
      case e: Exception =>
        LOG.error("Virhe prosessoitaessa SES-viestiä", e)
        Some(SQSBatchResponse.BatchItemFailure.builder()
          .withItemIdentifier(sqsMessage.getMessageId)
          .build())
  }

  @throws[Exception]
  def beforeCheckpoint(context: org.crac.Context[_ <: Resource]): Unit = {
    System.out.println("Before checkpoint")
    AwsUtil.sqsClient
  }

  @throws[Exception]
  def afterRestore(context: org.crac.Context[_ <: Resource]): Unit = {
    System.out.println("After restore")
    DbUtil.flushDataSource()
  }
}
