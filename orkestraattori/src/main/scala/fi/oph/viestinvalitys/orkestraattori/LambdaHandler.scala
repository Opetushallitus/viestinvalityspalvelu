package fi.oph.viestinvalitys.orkestraattori

import com.amazonaws.serverless.exceptions.ContainerInitializationException
import com.amazonaws.serverless.proxy.model.{AwsProxyResponse, HttpApiV2ProxyRequest}
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler, RequestStreamHandler}
import fi.oph.viestinvalitys.db.{awsUtil, dbUtil}
import fi.oph.viestinvalitys.model.{Lahetykset, LiitteenTila, Liitteet, Viestipohjat}
import fi.oph.viestinvalitys.orkestraattori.LambdaHandler.LOG
import org.flywaydb.core.Flyway
import org.postgresql.ds.PGSimpleDataSource
import org.slf4j.{Logger, LoggerFactory}
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import slick.jdbc.PostgresProfile.api.*
import slick.lifted.TableQuery
import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{GetObjectRequest, GetObjectTaggingRequest}
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.{DeleteMessageBatchRequest, DeleteMessageBatchRequestEntry}
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParameterRequest

import java.time.Instant
import java.util
import java.util.UUID
import java.util.stream.Collectors
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.jdk.CollectionConverters.SeqHasAsJava


object LambdaHandler {
  //val context: ConfigurableApplicationContext = App.start(Array())

  System.setProperty("logging.level.root", "DEBUG")

  val LOG = LoggerFactory.getLogger(classOf[LambdaHandler]);

  var migrated = false
  def migrate(): Unit =
    if (migrated) return
    migrated = true

    val flyway = Flyway.configure()
      .dataSource(dbUtil.getDatasource())
      .outOfOrder(true)
      .locations("flyway")
      .load()
    flyway.migrate()

  def updateScanned(): Unit =
    LOG.info("Päivitetään virusskannatut")
    val tunnisteet = Await.result(dbUtil.getDatabase().run(TableQuery[Liitteet].filter(_.tila === LiitteenTila.ODOTTAA.toString).map(_.tunniste).result), 5.seconds)
    val s3Client = awsUtil.getS3Client()
    val tilat = tunnisteet.map(tunniste => {
      val r = s3Client.getObjectTagging(GetObjectTaggingRequest.builder()
        .bucket("hahtuva-viestinvalityspalvelu-attachments")
        .key(tunniste.toString)
        .build())
      tunniste -> {
        LOG.info("Tags: " + r.tagSet().asScala.map(tag => tag.key() + ":" + tag.value()).mkString(", "))
        r.tagSet().asScala.find(tag => "bucketav".equals(tag.key()))
      }
    }).toMap
    val puhtaat = tilat.filter((tunniste, tila) => tila.isDefined && "clean".equals(tila.get.value())).map((tunniste, tila) => tunniste)

    if(!puhtaat.isEmpty)
      LOG.info("Seuraavat liitteet todettu puhtaiksi: " + puhtaat.mkString(","))
      val puhtaatRajoite = puhtaat.map(tunniste => "'" + tunniste + "'").mkString(",")
      val puhtaatQuery: DBIO[Int] = sqlu"""UPDATE liitteet SET tila='#${LiitteenTila.PUHDAS.toString}' WHERE tunniste IN (#${puhtaatRajoite})"""
      Await.result(dbUtil.getDatabase().run(puhtaatQuery), 5.seconds)
}

class LambdaHandler extends RequestHandler[SQSEvent, Void] {

  override def handleRequest(event: SQSEvent, context: Context): Void = {
    val sqsClient = SqsClient.builder()
      .credentialsProvider(ContainerCredentialsProvider.builder().build())
      .build()

    // deletoidaan viestit jonosta
    val entries: util.Collection[DeleteMessageBatchRequestEntry] = event.getRecords.asScala.map(event => DeleteMessageBatchRequestEntry.builder()
      .id(event.getMessageId)
      .receiptHandle(event.getReceiptHandle)
      .build()).toSeq.asJava
    sqsClient.deleteMessageBatch(DeleteMessageBatchRequest.builder()
      .queueUrl("https://sqs.eu-west-1.amazonaws.com/153563371259/hahtuva-viestinvalityspalvelu-timing")
      .entries(entries)
      .build())

    val cutoff = Instant.now.minusSeconds(60)
    event.getRecords.asScala.foreach(message => {

      val timestamp = Instant.parse(message.getBody)
      if(timestamp.isBefore(cutoff))
        LOG.info("Skipping old message: " + timestamp)
      else
        LOG.info("Running orchestrator for message: " + timestamp)
        LambdaHandler.updateScanned()
    })

    //LambdaHandler.migrate()
    //LambdaHandler.orkestroi(context)
    null
  }
}
