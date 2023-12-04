package fi.oph.viestinvalitys.vastaanotto

import com.amazonaws.services.lambda.runtime.events.{SNSEvent, SQSEvent}
import com.amazonaws.services.lambda.runtime.events.SNSEvent.SNSRecord
import com.fasterxml.jackson.databind.ObjectMapper
import fi.oph.viestinvalitys.aws.AwsUtil
import fi.oph.viestinvalitys.business.{LahetysOperaatiot, LiitteenTila}
import fi.oph.viestinvalitys.db.{ConfigurationUtil, DbUtil}
import fi.oph.viestinvalitys.skannaus.{BucketAVViesti, SqsViesti}
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

import scala.jdk.CollectionConverters.*
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.DurationInt

@Component
class Orkestrointi {

  @Autowired
  var objectMapper: ObjectMapper = null

  val LOG = LoggerFactory.getLogger(classOf[Orkestrointi]);
  val sqsClient = AwsUtil.getSqsClient()
  val sesQueueUrl = ConfigurationUtil.getConfigurationItem("SES_MONITOROINTI_QUEUE_URL").get
  val skannausQueueUrl = ConfigurationUtil.getConfigurationItem("SKANNAUS_QUEUE_URL").get

  @Scheduled(fixedRate = 2000)
  def orkestroiLahetys(): Unit =
    LOG.info("Ajetaan orkestrointi")
    val lahetysOperaatiot = new LahetysOperaatiot(DbUtil.getDatabase())
    val lahetettavat = lahetysOperaatiot.getLahetettavatVastaanottajat(10)
    new fi.oph.viestinvalitys.lahetys.LambdaHandler().handleRequest(lahetettavat.asJava, null)

  def convertToSqsEvent(response: ReceiveMessageResponse): SQSEvent =
    val sqsEvent = new SQSEvent
    sqsEvent.setRecords(response.messages().asScala.map(message => {
      val sqsMessage = new SQSEvent.SQSMessage
      sqsMessage.setBody(message.body())
      sqsMessage.setReceiptHandle(message.receiptHandle())
      sqsMessage
    }).asJava)
    sqsEvent

  @Scheduled(fixedRate = 2000)
  def orkestroiMonitorointi(): Unit =
    LOG.info("Ajetaan ses-monitorointi")
    val response = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
      .queueUrl(this.sesQueueUrl)
      .build())

    if(!response.messages().isEmpty())
      // ajetaan lambda-handleri (handleri poistaa viestit jonosta)
      new fi.oph.viestinvalitys.sesmonitorointi.LambdaHandler().handleRequest(convertToSqsEvent(response), null)

  @Scheduled(fixedRate = 5000)
  def orkestroiSkannaus(): Unit =
    LOG.info("Simuloidaan skannausta")
    val liiteTunnisteet = Await.result(DbUtil.getDatabase().run(
      sql"""
           SELECT tunniste
           FROM liitteet
           WHERE tila=${LiitteenTila.SKANNAUS.toString}
         """.as[String]), 5.seconds)

      liiteTunnisteet.foreach(tunniste => {
        LOG.info(s"Merkitään liite ${tunniste} puhtaaksi")
        // luodaan viesti jonoon jotta handler voi poistaa sen
        sqsClient.sendMessage(SendMessageRequest.builder()
          .queueUrl(skannausQueueUrl)
          .messageBody(objectMapper.writeValueAsString(SqsViesti(objectMapper.writeValueAsString(BucketAVViesti(
            bucket = DevApp.LOCAL_ATTACHMENTS_BUCKET_NAME, key = tunniste, status = "clean"
          )))))
          .build())

        // haetaan viesti ja välitetään handlerille
        val response = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
          .queueUrl(skannausQueueUrl)
          .waitTimeSeconds(5)
          .build())
        new fi.oph.viestinvalitys.skannaus.LambdaHandler().handleRequest(convertToSqsEvent(response), null)
      })

  @Scheduled(fixedRate = 10000)
  def orkestroiSiivous(): Unit =
    LOG.info("Simuloidaan siivousta")
    new fi.oph.viestinvalitys.siivous.LambdaHandler().handleRequest(null, null)

}
