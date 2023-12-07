package fi.oph.viestinvalitys.ajastus

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import fi.oph.viestinvalitys.aws.AwsUtil
import fi.oph.viestinvalitys.db.ConfigurationUtil
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.{SendMessageBatchRequest, SendMessageBatchRequestEntry}

import java.time.Instant
import scala.jdk.CollectionConverters.SeqHasAsJava

class LambdaHandler extends RequestHandler[Any, Void] {

  val LOG = LoggerFactory.getLogger(classOf[LambdaHandler]);

  override def handleRequest(event: Any, context: Context): Void = {
    val queueUrl = ConfigurationUtil.getConfigurationItem(ConfigurationUtil.AJASTUS_QUEUE_URL_KEY).get

    val sqsClient = AwsUtil.getSqsClient()
    LOG.info("Ajastetaan lähetyslambda")
    val timestamp = Instant.now()
    Range(0, 30).map(v => v * 2).map(delay => SendMessageBatchRequestEntry.builder()
      .id(java.util.UUID.randomUUID().toString)
      .messageBody(timestamp.plusSeconds(delay).toString)
      .delaySeconds(delay)
      .build())
      .grouped(10)
      .foreach(entries => sqsClient.sendMessageBatch(SendMessageBatchRequest.builder()
        .queueUrl(queueUrl)
        .entries(entries.asJava)
        .build())
      )
    null
  }
}
