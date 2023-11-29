package fi.oph.viestinvalitys.vastaanotto

import com.amazonaws.services.lambda.runtime.events.SNSEvent
import com.amazonaws.services.lambda.runtime.events.SNSEvent.SNSRecord
import com.fasterxml.jackson.databind.ObjectMapper
import fi.oph.viestinvalitys.aws.AwsUtil
import fi.oph.viestinvalitys.business.{LahetysOperaatiot, LiitteenTila}
import fi.oph.viestinvalitys.db.DbUtil
import fi.oph.viestinvalitys.skannaus.BucketAVMessage
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
import software.amazon.awssdk.services.sqs.model.{CreateQueueRequest, DeleteMessageBatchRequest, DeleteMessageBatchRequestEntry, DeleteMessageRequest, GetQueueAttributesRequest, QueueAttributeName, ReceiveMessageRequest, ReceiveMessageResponse}

import scala.jdk.CollectionConverters.*
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.DurationInt

@Component
class Orkestrointi {

  @Autowired
  var objectMapper: ObjectMapper = null

  val LOG = LoggerFactory.getLogger(classOf[Orkestrointi]);
  val sqsClient = AwsUtil.getSqsClient()

  final val CONFIGURATION_SET_NAME = "viestinvalitys-local"

  val queueUrl = {
    // luodaan sns-topic ja routataan se sqs-jonoon
    val sqsClient = AwsUtil.getSqsClient();
    val createQueueResponse = sqsClient.createQueue(CreateQueueRequest.builder()
      .queueName("ses-monitorointi")
      .build())
    val snsClient = AwsUtil.getSnsClient();
    val createTopicResponse = snsClient.createTopic(CreateTopicRequest.builder()
      .name("viestinvalitys-monitor")
      .build())
    snsClient.subscribe(SubscribeRequest.builder()
      .topicArn(createTopicResponse.topicArn())
      .protocol("sqs")
      .endpoint("arn:aws:sqs:us-east-1:000000000000:ses-monitorointi")
      .build())

    // verifioidaan ses-identiteetti ja konfiguroidaan eventit
    val sesClient = AwsUtil.getSesClient();
    sesClient.verifyDomainIdentity(VerifyDomainIdentityRequest.builder()
      .domain("knowit.fi")
      .build())
    sesClient.createConfigurationSet(CreateConfigurationSetRequest.builder()
      .configurationSet(ConfigurationSet.builder()
        .name(CONFIGURATION_SET_NAME)
        .build())
      .build())
    sesClient.createConfigurationSetEventDestination(CreateConfigurationSetEventDestinationRequest.builder()
      .configurationSetName(CONFIGURATION_SET_NAME)
      .eventDestination(EventDestination.builder()
        .matchingEventTypes(EventType.BOUNCE, EventType.OPEN, EventType.COMPLAINT, EventType.CLICK, EventType.SEND, EventType.DELIVERY, EventType.REJECT)
        .name("ViestinvalitysMonitor")
        .enabled(true)
        .snsDestination(SNSDestination.builder()
          .topicARN(createTopicResponse.topicArn())
          .build())
        .build())
      .build())

    createQueueResponse.queueUrl()

    /*
    sesClient.verifyEmailAddress(VerifyEmailAddressRequest.builder()
      .emailAddress("santeri.korri@knowit.fi")
      .build())

    sesClient.setIdentityNotificationTopic(SetIdentityNotificationTopicRequest.builder()
      .identity("knowit.fi")
      .snsTopic(createTopicResponse.topicArn())
      .notificationType(NotificationType.BOUNCE)
      .build())
    */
  }

  @Scheduled(fixedRate = 2000)
  def orkestroiLahetys(): Unit =
    LOG.info("Ajetaan orkestrointi")
    val lahetysOperaatiot = new LahetysOperaatiot(DbUtil.getDatabase())
    val lahetettavat = lahetysOperaatiot.getLahetettavatVastaanottajat(10)
    new fi.oph.viestinvalitys.lahetys.LambdaHandler().handleRequest(lahetettavat.asJava, null)

  @Scheduled(fixedRate = 2000)
  def orkestroiMonitorointi(): Unit =
    LOG.info("Ajetaan ses-monitorointi")
    val response = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
      .queueUrl(this.queueUrl)
      .build())

    if(!response.messages().isEmpty())
      // poistetaan viestit jonosta
      response.messages().forEach(message => {
        sqsClient.deleteMessage(DeleteMessageRequest.builder()
          .queueUrl(this.queueUrl)
          .receiptHandle(message.receiptHandle())
          .build())
      })
      // ajetaan labmda-handleri
      new fi.oph.viestinvalitys.sesmonitorointi.LambdaHandler().handleRequest(new SNSEvent().withRecords(response.messages().asScala.map(message => {
        new SNSRecord().withSns(new SNSEvent.SNS().withMessage(message.body()))
      }).asJava), null)


  @Scheduled(fixedRate = 5000)
  def orkestroiSkannaus(): Unit =
    LOG.info("Simuloidaan skannausta")
    val liiteTunnisteet = Await.result(DbUtil.getDatabase().run(
      sql"""
           SELECT tunniste
           FROM liitteet
           WHERE tila=${LiitteenTila.SKANNAUS.toString}
         """.as[String]), 5.seconds)

    val snsEvent = new SNSEvent().withRecords(liiteTunnisteet.map(tunniste => {
      LOG.info(s"Merkitään liite ${tunniste} puhtaaksi")
      new SNSRecord().withSns(new SNSEvent.SNS().withMessage(objectMapper.writeValueAsString(new BucketAVMessage(
        bucket = DevApp.LOCAL_ATTACHMENTS_BUCKET_NAME, key = tunniste, status = "clean"
      ))))
    }).asJava)
    new fi.oph.viestinvalitys.skannaus.LambdaHandler().handleRequest(snsEvent, null)

  @Scheduled(fixedRate = 10000)
  def orkestroiSiivous(): Unit =
    LOG.info("Simuloidaan siivousta")
    new fi.oph.viestinvalitys.siivous.LambdaHandler().handleRequest(null, null)

}
