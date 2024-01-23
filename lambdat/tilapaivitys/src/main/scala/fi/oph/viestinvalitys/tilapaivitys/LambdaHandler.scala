package fi.oph.viestinvalitys.tilapaivitys

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler, RequestStreamHandler}
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, SerializationFeature}
import fi.oph.viestinvalitys.business.{KantaOperaatiot, LiitteenTila, VastaanottajanTila}
import fi.oph.viestinvalitys.tilapaivitys.LambdaHandler.kantaOperaatiot
import fi.oph.viestinvalitys.util.{AwsUtil, ConfigurationUtil, DbUtil, LogContext}
import org.crac.{Core, Resource}
import org.flywaydb.core.Flyway
import org.postgresql.ds.PGSimpleDataSource
import org.slf4j.{Logger, LoggerFactory}
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

object LambdaHandler {

  val kantaOperaatiot = KantaOperaatiot(DbUtil.database)
}

class LambdaHandler extends RequestHandler[SQSEvent, Void], Resource {
  Core.getGlobalContext.register(this)

  val queueUrl = ConfigurationUtil.getConfigurationItem(ConfigurationUtil.SESMONITOROINTI_QUEUE_URL_KEY).get;
  val LOG = LoggerFactory.getLogger(classOf[LambdaHandler]);

  override def handleRequest(event: SQSEvent, context: Context): Void = {
    LogContext(requestId = context.getAwsRequestId, functionName = context.getFunctionName)(() => {
      LOG.info("Prosessoidaan SES-viesti")
      event.getRecords.asScala.foreach(sqsMessage => {
        try
          val message = Deserialisoija.deserialisoiSqsViesti(sqsMessage.getBody)
          if (message.isEmpty)
            LOG.warn("SES-viesti on tyhjä")
          else
            val messageId = message.get.mail.messageId
            val siirtyma = message.get.asVastaanottajanSiirtyma()
            if (siirtyma.isDefined)
              val (vastaanottajanTila, lisatiedot) = siirtyma.get
              LOG.info("Siirretään viesti " + messageId + " tilaan " + vastaanottajanTila.toString)
              kantaOperaatiot.paivitaVastaanotonTila(messageId, vastaanottajanTila, lisatiedot)
            else
              LOG.info("Viestin " + messageId + " tilalle ei ole määritelty siirtymää")
          AwsUtil.deleteMessages(java.util.List.of(sqsMessage), queueUrl)
        catch
          case e: Exception => LOG.error("Virhe prosessoitaessa SES-viestiä", e)
      })
      null
    })
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
