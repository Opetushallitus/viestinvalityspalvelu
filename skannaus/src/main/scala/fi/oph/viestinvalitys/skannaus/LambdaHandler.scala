package fi.oph.viestinvalitys.skannaus

import com.amazonaws.serverless.exceptions.ContainerInitializationException
import com.amazonaws.serverless.proxy.model.{AwsProxyResponse, HttpApiV2ProxyRequest}
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler
import com.amazonaws.services.lambda.runtime.events.{SNSEvent, SQSEvent}
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler, RequestStreamHandler}
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, SerializationFeature}
import fi.oph.viestinvalitys.db.dbUtil.getParameter
import fi.oph.viestinvalitys.db.{awsUtil, dbUtil}
import fi.oph.viestinvalitys.model.{Lahetykset, LiitteenTila, Liitteet, Viestipohjat}
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

case class BucketAVMessage(@BeanProperty bucket: String, @BeanProperty key: String, @BeanProperty status: String) {
  def this() = {
    this(null, null, null)
  }
}

class LambdaHandler extends RequestHandler[SNSEvent, Void] {

  val LOG = LoggerFactory.getLogger(classOf[LambdaHandler]);

  val mapper = {
    val mapper = new ObjectMapper()
    mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    mapper.configure(SerializationFeature.INDENT_OUTPUT, true)
    mapper
  }

  override def handleRequest(event: SNSEvent, context: Context): Void = {
    event.getRecords.asScala.foreach(notification => {
      LOG.info("SNS Message: " + notification.getSNS.getMessage)
      val message: BucketAVMessage = mapper.readValue(notification.getSNS.getMessage, classOf[BucketAVMessage])
      val uusiTila = message.status match {
        case "clean"    => LiitteenTila.PUHDAS.toString
        case "infected" => LiitteenTila.SAASTUNUT.toString
        case _          => LiitteenTila.VIRHE.toString
      }

      val tila = for { liite <- TableQuery[Liitteet] if liite.tunniste === UUID.fromString(message.key) } yield liite.tila
      val updateAction = tila.update(uusiTila)
      Await.result(dbUtil.getDatabase().run(updateAction), 5.seconds)
    })
    null
  }
}
