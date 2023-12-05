package fi.oph.viestinvalitys.orkestraattori

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, SerializationFeature}
import fi.oph.viestinvalitys.aws.AwsUtil
import fi.oph.viestinvalitys.business.LahetysOperaatiot
import fi.oph.viestinvalitys.db.{ConfigurationUtil, DbUtil}
import org.postgresql.ds.PGSimpleDataSource
import org.slf4j.{Logger, LoggerFactory}
import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.{DeleteMessageBatchRequest, DeleteMessageBatchRequestEntry}
import software.amazon.awssdk.services.lambda.LambdaClient
import software.amazon.awssdk.services.lambda.model.InvokeRequest

import java.nio.charset.{Charset, StandardCharsets}
import java.time.Instant
import java.util
import java.util.UUID
import java.util.stream.Collectors
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.jdk.CollectionConverters.SeqHasAsJava
import org.crac.{Core, Resource}

object LambdaHandler {
  val LOG = LoggerFactory.getLogger(classOf[LambdaHandler]);
  val queueUrl = System.getenv("clock_queue_url");
  val lahetysFunctionName = System.getenv("lahetys_function_name");
  val lahetysOperaatiot = new LahetysOperaatiot(DbUtil.getDatabase())

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

  def laheta(maara: Int, lambdaAina: Boolean): Unit = {
    LambdaHandler.LOG.debug("Haetaan lähetettävät viestit")
    val lahetettavat = LambdaHandler.lahetysOperaatiot.getLahetettavatVastaanottajat(maara)
    if (!lahetettavat.isEmpty || lambdaAina)
      LambdaHandler.LOG.info("Lähetetään seuraavat viestit: " + lahetettavat.mkString(","))
      val lambdaClient = LambdaClient.builder()
        .credentialsProvider(ContainerCredentialsProvider.builder().build())
        .build();

      lambdaClient.invoke(InvokeRequest.builder()
        .functionName(LambdaHandler.lahetysFunctionName)
        .payload(SdkBytes.fromString(LambdaHandler.mapper.writeValueAsString(lahetettavat.asJava), StandardCharsets.UTF_8))
        .build())
      LambdaHandler.LOG.debug("Lähetetty seuraavat viestit: " + lahetettavat.mkString(","))
  }

  override def handleRequest(event: SQSEvent, context: Context): Void = {
    LambdaHandler.LOG.debug("Poistetaan viestit")
    AwsUtil.deleteMessages(event.getRecords, LambdaHandler.queueUrl)

    val now = Instant.now
    event.getRecords.asScala.foreach(message => {
      val viestiTimestamp = Instant.parse(message.getBody)
      val sqsViive = now.toEpochMilli - viestiTimestamp.toEpochMilli
      if(sqsViive>1000)
        LambdaHandler.LOG.info("Ohitetaan vanha viesti: " + viestiTimestamp)
      else
        LambdaHandler.LOG.info("Ajetaan orkestraattori: " + viestiTimestamp)
        laheta(130, false)
    })
    null
  }

  @throws[Exception]
  def beforeCheckpoint(context: org.crac.Context[_ <: Resource]): Unit =
    LambdaHandler.LOG.info("Before checkpoint")
    AwsUtil.getSqsClient()
    laheta(0, true)

  @throws[Exception]
  def afterRestore(context: org.crac.Context[_ <: Resource]): Unit =
    LambdaHandler.LOG.info("After restore")


}
