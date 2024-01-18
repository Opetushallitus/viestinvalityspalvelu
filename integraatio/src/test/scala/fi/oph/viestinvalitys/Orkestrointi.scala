package fi.oph.viestinvalitys

import com.amazonaws.services.lambda.runtime.events.{SNSEvent, SQSEvent}
import com.amazonaws.services.lambda.runtime.events.SNSEvent.SNSRecord
import com.fasterxml.jackson.databind.ObjectMapper
import fi.oph.viestinvalitys.business.LiitteenTila
import fi.oph.viestinvalitys.lahetys
import fi.oph.viestinvalitys.skannaus.{BucketAVViesti, SqsViesti}
import fi.oph.viestinvalitys.util.{AwsUtil, ConfigurationUtil, DbUtil}
import org.apache.http.client.utils.URIBuilder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.PostgresProfile.api.*
import software.amazon.awssdk.services.ses.model.{ConfigurationSet, CreateConfigurationSetEventDestinationRequest, CreateConfigurationSetRequest, EventDestination, EventType, NotificationType, SNSDestination, SetIdentityNotificationTopicRequest, VerifyDomainIdentityRequest, VerifyEmailAddressRequest}
import software.amazon.awssdk.services.sns.model.{CreateTopicRequest, SubscribeRequest}
import software.amazon.awssdk.services.sqs.model.{CreateQueueRequest, DeleteMessageBatchRequest, DeleteMessageBatchRequestEntry, DeleteMessageRequest, GetQueueAttributesRequest, ListQueuesRequest, QueueAttributeName, ReceiveMessageRequest, ReceiveMessageResponse, SendMessageRequest}

import java.time.Instant
import scala.jdk.CollectionConverters.*
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import scala.util.control.Breaks

/**
 * Testikoodia jonka avulla simuloidaan AWS:n toiminnallisuutta lokaaliympäristössä.
 */
@Component
class Orkestrointi {

  @Autowired
  var objectMapper: ObjectMapper = null

  val LOG = LoggerFactory.getLogger(classOf[Orkestrointi]);
  val sqsClient = AwsUtil.sqsClient
  val sesQueueUrl = LocalUtil.getQueueUrl(LocalUtil.LOCAL_SES_MONITOROINTI_QUEUE_NAME).get
  val skannausQueueUrl = LocalUtil.getQueueUrl(LocalUtil.LOCAL_SKANNAUS_QUEUE_NAME).get
  val ajastusQueueUrl = LocalUtil.getQueueUrl(LocalUtil.LOCAL_AJASTUS_QUEUE_NAME).get

  def convertToSqsEvent(response: ReceiveMessageResponse): SQSEvent =
    val sqsEvent = new SQSEvent
    sqsEvent.setRecords(response.messages().asScala.map(message => {
      val sqsMessage = new SQSEvent.SQSMessage
      sqsMessage.setBody(message.body())
      sqsMessage.setReceiptHandle(message.receiptHandle())
      sqsMessage
    }).asJava)
    sqsEvent

  def createSqsEvent(queueUrl: String, payload: String): SQSEvent =
    // luodaan viesti jonoon jotta handler voi poistaa sen
    sqsClient.sendMessage(SendMessageRequest.builder()
      .queueUrl(queueUrl)
      .messageBody(payload)
      .build())
    // haetaan viesti
    val response = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
      .queueUrl(queueUrl)
      .waitTimeSeconds(5)
      .build())
    convertToSqsEvent(response)

  @Scheduled(fixedRate = 2000)
  def orkestroiLahetys(): Unit =
    new lahetys.LambdaHandler().handleRequest(createSqsEvent(ajastusQueueUrl, Instant.now.toString), null)

  @Scheduled(fixedRate = 2000)
  def orkestroiMonitorointi(): Unit =
    val breaks = new Breaks
    breaks.breakable {
      while (true) {
        val response = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
          .queueUrl(this.sesQueueUrl)
          .build())

        if (!response.messages().isEmpty())
        // ajetaan lambda-handleri (handleri poistaa viestit jonosta)
          new fi.oph.viestinvalitys.tilapaivitys.LambdaHandler().handleRequest(convertToSqsEvent(response), null)
        else
          breaks.break()
      }
    }

  @Scheduled(fixedRate = 5000)
  def orkestroiSkannaus(): Unit =
    val liiteTunnisteet = Await.result(DbUtil.database.run(
      sql"""
           SELECT tunniste
           FROM liitteet
           WHERE tila=${LiitteenTila.SKANNAUS.toString}
         """.as[String]), 5.seconds)

    liiteTunnisteet.foreach(tunniste => {
      LOG.info(s"Merkitään liite ${tunniste} puhtaaksi")
      val payload = objectMapper.writeValueAsString(SqsViesti(objectMapper.writeValueAsString(BucketAVViesti(
        bucket = LocalUtil.LOCAL_ATTACHMENTS_BUCKET_NAME, key = tunniste, status = "clean"
      ))))
      new fi.oph.viestinvalitys.skannaus.LambdaHandler().handleRequest(createSqsEvent(skannausQueueUrl, payload), null)
    })

  @Scheduled(fixedRate = 10000)
  def orkestroiSiivous(): Unit =
    new fi.oph.viestinvalitys.siivous.LambdaHandler().handleRequest(null, null)

}
