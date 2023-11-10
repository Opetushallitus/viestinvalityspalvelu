package fi.oph.viestinvalitys.orkestraattori

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, SerializationFeature}
import fi.oph.viestinvalitys.business.LahetysOperaatiot
import fi.oph.viestinvalitys.db.DbUtil
import org.apache.commons.io.Charsets
import org.flywaydb.core.Flyway
import org.postgresql.ds.PGSimpleDataSource
import org.slf4j.{Logger, LoggerFactory}
import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.{DeleteMessageBatchRequest, DeleteMessageBatchRequestEntry}
import software.amazon.awssdk.services.lambda.LambdaClient
import software.amazon.awssdk.services.lambda.model.InvokeRequest

import java.nio.charset.Charset
import java.time.Instant
import java.util
import java.util.UUID
import java.util.stream.Collectors
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.jdk.CollectionConverters.SeqHasAsJava

class LambdaHandler extends RequestHandler[SQSEvent, Void] {

  val LOG = LoggerFactory.getLogger(classOf[LambdaHandler]);
  val queueUrl = System.getenv("clock_queue_url");
  val lahetysFunctionName = System.getenv("lahetys_function_name");

  val mapper = {
    val mapper = new ObjectMapper()
    mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    mapper.configure(SerializationFeature.INDENT_OUTPUT, true)
    mapper
  }

  def deleteMessages(event: SQSEvent): Unit =
    val sqsClient = SqsClient.builder()
      .credentialsProvider(ContainerCredentialsProvider.builder().build())
      .build()

    // deletoidaan viestit jonosta
    val entries: util.Collection[DeleteMessageBatchRequestEntry] = event.getRecords.asScala.map(event => DeleteMessageBatchRequestEntry.builder()
      .id(event.getMessageId)
      .receiptHandle(event.getReceiptHandle)
      .build()).toSeq.asJava
    sqsClient.deleteMessageBatch(DeleteMessageBatchRequest.builder()
      .queueUrl(queueUrl)
      .entries(entries)
      .build())

  def laheta(): Unit = {
    val lahetysOperaatiot = new LahetysOperaatiot(DbUtil.getDatabase())
    val lahetettavat = lahetysOperaatiot.getLahetettavatVastaanottajat(10)
    if (!lahetettavat.isEmpty)
      LOG.info("Lähetetään seuraavat viestit: " + lahetettavat.mkString(","))
      val lambdaClient = LambdaClient.builder()
        .credentialsProvider(ContainerCredentialsProvider.builder().build())
        .build();

      lambdaClient.invoke(InvokeRequest.builder()
        .functionName(lahetysFunctionName)
        .payload(SdkBytes.fromString(mapper.writeValueAsString(lahetettavat.asJava), Charsets.UTF_8))
        .build())
      LOG.info("Lähetetty seuraavat viestit: " + lahetettavat.mkString(","))
  }

  var migrated = false
  def migrate(): Unit =
    if (migrated) return
    migrated = true

    val flyway = Flyway.configure()
      .dataSource(DbUtil.getDatasource())
      .outOfOrder(true)
      .locations("flyway")
      .load()
    flyway.migrate()


  override def handleRequest(event: SQSEvent, context: Context): Void = {
    deleteMessages(event)

    val now = Instant.now
    event.getRecords.asScala.foreach(message => {
      val viestiTimestamp = Instant.parse(message.getBody)
      val sqsViive = now.toEpochMilli - viestiTimestamp.toEpochMilli
      if(sqsViive>10000)
        LOG.info("Ohitetaan vanha viesti: " + viestiTimestamp)
      else
        LOG.info("Ajetaan orkestraattori: " + viestiTimestamp)
        migrate()
        laheta()
    })
    null
  }
}
