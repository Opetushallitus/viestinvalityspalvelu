package fi.oph.viestinvalitys.sesmonitorointi

import com.amazonaws.serverless.exceptions.ContainerInitializationException
import com.amazonaws.serverless.proxy.model.{AwsProxyResponse, HttpApiV2ProxyRequest}
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler
import com.amazonaws.services.lambda.runtime.events.{SNSEvent, SQSEvent}
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler, RequestStreamHandler}
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, SerializationFeature}
import fi.oph.viestinvalitys.aws.AwsUtil
import fi.oph.viestinvalitys.business.{LahetysOperaatiot, LiitteenTila, VastaanottajanTila}
import fi.oph.viestinvalitys.db.DbUtil
import org.flywaydb.core.Flyway
import org.postgresql.ds.PGSimpleDataSource
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

class LambdaHandler extends RequestHandler[SNSEvent, Void] {

  val LOG = LoggerFactory.getLogger(classOf[LambdaHandler]);
  val deserialisoija = new Deserialisoija

  override def handleRequest(event: SNSEvent, context: Context): Void = {
    event.getRecords.asScala.foreach(notification => {
      LOG.info("SNS Message: " + notification.getSNS.getMessage)
      val message: SesMonitoringMessage = deserialisoija.deserialisoi(notification.getSNS.getMessage)

      if(message.bounce!=null)
        val messageId = message.mail.headers.find(h => MESSAGE_ID_HEADER_NAME.equals(h.name)).get.value
        LahetysOperaatiot(DbUtil.getDatabase()).paivitaVastaanottajanTila(UUID.fromString(messageId), VastaanottajanTila.BOUNCE)
    })
    null
  }
}
