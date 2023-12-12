package fi.oph.viestinvalitys.sesmonitorointi

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler, RequestStreamHandler}
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, SerializationFeature}
import fi.oph.viestinvalitys.aws.AwsUtil
import fi.oph.viestinvalitys.business.{LahetysOperaatiot, LiitteenTila, VastaanottajanTila}
import fi.oph.viestinvalitys.db.{ConfigurationUtil, DbUtil}
import fi.oph.viestinvalitys.sesmonitorointi.LambdaHandler.lahetysOperaatiot
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

  val lahetysOperaatiot = LahetysOperaatiot(DbUtil.database)
}

class LambdaHandler extends RequestHandler[SQSEvent, Void], Resource {
  Core.getGlobalContext.register(this)

  val queueUrl = ConfigurationUtil.getConfigurationItem("SES_MONITOROINTI_QUEUE_URL").get;
  val LOG = LoggerFactory.getLogger(classOf[LambdaHandler]);

  override def handleRequest(event: SQSEvent, context: Context): Void = {
    event.getRecords.asScala.foreach(sqsMessage => {
      LOG.info("SQS Message: " + sqsMessage.getBody)
      val message = Deserialisoija.deserialisoiSqsViesti(sqsMessage.getBody)
      if(message.isEmpty)
        LOG.warn("Message is not a SES message")
      else
        val messageId = message.get.mail.messageId
        val siirtyma = message.get.asVastaanottajanSiirtyma()
        if(siirtyma.isDefined)
          val (vastaanottajanTila, lisatiedot) = siirtyma.get
          lahetysOperaatiot.paivitaVastaanotonTila(messageId, vastaanottajanTila, lisatiedot)
      AwsUtil.deleteMessages(java.util.List.of(sqsMessage), queueUrl)
    })
    null
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
