package fi.oph.viestinvalitys.kello

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.{SendMessageBatchRequest, SendMessageBatchRequestEntry}

import java.time.Instant

import scala.jdk.CollectionConverters.SeqHasAsJava

class LambdaHandler extends RequestHandler[Any, Void] {

  val LOG = LoggerFactory.getLogger(classOf[LambdaHandler]);

  override def handleRequest(event: Any, context: Context): Void = {
    val sqsClient = SqsClient.create()
    LOG.info("Ajastetaan orkestraattorilambda")
    val timestamp = Instant.now()
    Range(0, 30).map(v => v * 2).map(delay => SendMessageBatchRequestEntry.builder()
      .id(java.util.UUID.randomUUID().toString)
      .messageBody(timestamp.plusSeconds(delay).toString)
      .delaySeconds(delay)
      .build())
      .grouped(10)
      .foreach(entries => sqsClient.sendMessageBatch(SendMessageBatchRequest.builder()
        .queueUrl("https://sqs.eu-west-1.amazonaws.com/153563371259/hahtuva-viestinvalityspalvelu-timing")
        .entries(entries.asJava)
        .build())
      )
    null
  }
}
