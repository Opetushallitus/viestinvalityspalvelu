package fi.oph.viestinvalitys.skannaus

import com.amazonaws.services.lambda.runtime.events.{SNSEvent, SQSEvent}
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler, RequestStreamHandler}
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import fi.oph.viestinvalitys.business.{KantaOperaatiot, LiitteenTila}
import fi.oph.viestinvalitys.util.{AwsUtil, ConfigurationUtil, DbUtil}
import org.crac.Resource
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

class LambdaHandler extends RequestHandler[SQSEvent, Void], Resource {

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

  def deserialisoiSqsViesti(viesti: String): Option[BucketAVViesti] =
    val sqsViesti = mapper.readValue(viesti, classOf[SqsViesti])
    if(sqsViesti.Message==null)
      Option.empty
    else
      Option.apply(mapper.readValue(sqsViesti.Message, classOf[BucketAVViesti]))

  override def handleRequest(event: SQSEvent, context: Context): Void = {
    event.getRecords.asScala.foreach(sqsMessage => {
      LOG.info("SQS Message: " + sqsMessage.getBody)
      val message = deserialisoiSqsViesti(sqsMessage.getBody)
      if(message.isEmpty)
        LOG.warn("No message in SQS message")
      else
        val uusiTila = message.get.status match
          case "clean" => LiitteenTila.PUHDAS
          case "infected" => LiitteenTila.SAASTUNUT
          case _ => LiitteenTila.VIRHE
        KantaOperaatiot(DbUtil.database).paivitaLiitteenTila(UUID.fromString(message.get.key), uusiTila)
      AwsUtil.deleteMessages(java.util.List.of(sqsMessage), queueUrl)
    })
    null
  }

  @throws[Exception]
  def beforeCheckpoint(context: org.crac.Context[_ <: Resource]): Unit = {
    AwsUtil.sqsClient
  }

  @throws[Exception]
  def afterRestore(context: org.crac.Context[_ <: Resource]): Unit = {
  }
}
