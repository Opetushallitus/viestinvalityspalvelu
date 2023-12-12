package fi.oph.viestinvalitys.vastaanotto

import fi.oph.viestinvalitys.aws.AwsUtil
import fi.oph.viestinvalitys.db.ConfigurationUtil
import fi.oph.viestinvalitys.vastaanotto.resource.APIConstants
import fi.oph.viestinvalitys.flyway.LambdaHandler
import org.apache.commons.io.IOUtils
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import software.amazon.awssdk.services.s3.model.{CreateBucketRequest, ListObjectsRequest, PutObjectRequest}
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.ses.model.{ConfigurationSet, CreateConfigurationSetEventDestinationRequest, CreateConfigurationSetRequest, EventDestination, EventType, SNSDestination, VerifyDomainIdentityRequest}
import software.amazon.awssdk.services.sns.model.{CreateTopicRequest, SubscribeRequest}
import software.amazon.awssdk.services.sqs.model.{CreateQueueRequest, ListQueuesRequest}

@SpringBootApplication
@EnableWebMvc
@EnableScheduling
class DevApp {}

object DevApp {

  final val LOCAL_ATTACHMENTS_BUCKET_NAME = "local-viestinvalityspalvelu-attachments";

  final val LOCAL_SKANNAUS_QUEUE_NAME = "local-viestinvalityspalvelu-skannaus"
  final val LOCAL_AJASTUS_QUEUE_NAME = "local-viestinvalityspalvelu-lahetys"
  final val LOCAL_SES_MONITOROINTI_QUEUE_NAME = "local-viestinvalityspalvelu-ses-monitorointi"
  final val LOCAL_SES_CONFIGURATION_SET_NAME = "viestinvalitys-local"

  def setupS3(): Unit =
    // luodaan bucket liitetiedostoille jos ei olemassa
    val s3Client = AwsUtil.s3Client
    if (s3Client.listBuckets().buckets().stream().filter(b => b.name().equals(LOCAL_ATTACHMENTS_BUCKET_NAME)).findFirst().isEmpty())
      s3Client.createBucket(CreateBucketRequest.builder()
        .bucket(LOCAL_ATTACHMENTS_BUCKET_NAME)
        .build())

    // tallennetaan esimerkkiliite jos ei olemassa
    if (s3Client.listObjects(ListObjectsRequest.builder()
      .bucket(LOCAL_ATTACHMENTS_BUCKET_NAME)
      .build()).contents().stream().filter(o => o.key().equals(APIConstants.ESIMERKKI_LIITETUNNISTE)).findFirst().isEmpty())
      try
        s3Client.putObject(PutObjectRequest
          .builder()
          .bucket(LOCAL_ATTACHMENTS_BUCKET_NAME)
          .key(APIConstants.ESIMERKKI_LIITETUNNISTE)
          .contentType("image/png")
          .build(), RequestBody.fromBytes(IOUtils.toByteArray(classOf[DevApp].getClassLoader().getResourceAsStream("screenshot.png")
        )))
      catch
        case e: Exception => throw new RuntimeException(e)

  def getQueueUrl(queueName: String): Option[String] =
    val sqsClient = AwsUtil.sqsClient;
    val existingQueueUrls = sqsClient.listQueues(ListQueuesRequest.builder()
      .queueNamePrefix(queueName)
      .build()).queueUrls()
    if (!existingQueueUrls.isEmpty)
      Option.apply(existingQueueUrls.get(0))
    else
      Option.empty

  def setupSkannaus(): Unit =
  // katsotaan onko konfigurointi jo tehty
    if (getQueueUrl(LOCAL_SKANNAUS_QUEUE_NAME).isDefined)
      return

    val createQueueResponse = AwsUtil.sqsClient.createQueue(CreateQueueRequest.builder()
      .queueName(LOCAL_SKANNAUS_QUEUE_NAME)
      .build())

  def setupLahetys(): Unit =
    // katsotaan onko konfigurointi jo tehty
    if (getQueueUrl(LOCAL_AJASTUS_QUEUE_NAME).isDefined)
      return

    val createQueueResponse = AwsUtil.sqsClient.createQueue(CreateQueueRequest.builder()
      .queueName(LOCAL_AJASTUS_QUEUE_NAME)
      .build())

  def setupMonitoring(): Unit =
    // katsotaan onko konfigurointi jo tehty
    if(getQueueUrl(LOCAL_SES_MONITOROINTI_QUEUE_NAME).isDefined)
      return

    // luodaan sns-topic ja routataan se sqs-jonoon
    val createQueueResponse = AwsUtil.sqsClient.createQueue(CreateQueueRequest.builder()
      .queueName(LOCAL_SES_MONITOROINTI_QUEUE_NAME)
      .build())
    val createTopicResponse = AwsUtil.snsClient.createTopic(CreateTopicRequest.builder()
      .name("viestinvalitys-monitor")
      .build())
    AwsUtil.snsClient.subscribe(SubscribeRequest.builder()
      .topicArn(createTopicResponse.topicArn())
      .protocol("sqs")
      .endpoint("arn:aws:sqs:us-east-1:000000000000:" + LOCAL_SES_MONITOROINTI_QUEUE_NAME)
      .build())

    // verifioidaan ses-identiteetti ja konfiguroidaan eventit
    val sesClient = AwsUtil.sesClient
    sesClient.verifyDomainIdentity(VerifyDomainIdentityRequest.builder()
      .domain("hahtuvaopintopolku.fi")
      .build())
    sesClient.createConfigurationSet(CreateConfigurationSetRequest.builder()
      .configurationSet(ConfigurationSet.builder()
        .name(LOCAL_SES_CONFIGURATION_SET_NAME)
        .build())
      .build())
    sesClient.createConfigurationSetEventDestination(CreateConfigurationSetEventDestinationRequest.builder()
      .configurationSetName(LOCAL_SES_CONFIGURATION_SET_NAME)
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


  @main
  def main(args: String*): Unit =
    System.setProperty("spring.profiles.active", "dev")

    // ssl-konfiguraatio
    System.setProperty("server.ssl.key-store-type", "PKCS12")
    System.setProperty("server.ssl.key-store", "classpath:viestinvalitys.p12")
    System.setProperty("server.ssl.key-store-password", "password")
    System.setProperty("server.ssl.key-alias", "viestinvalitys")
    System.setProperty("server.ssl.enabled", "true")
    System.setProperty("server.port", "8443")

    // cas-configuraatio
    System.setProperty("cas-service.service", "https://localhost:8443")
    System.setProperty("cas-service.sendRenew", "false")
    System.setProperty("cas-service.key", "viestinvalityspalvelu")
    System.setProperty("web.url.cas", "https://virkailija.hahtuvaopintopolku.fi/cas")

    System.setProperty("kayttooikeus-service.userDetails.byUsername", "https://virkailija.hahtuvaopintopolku.fi/kayttooikeus-service/userDetails/$1")

    System.setProperty("host.virkailija", "virkailija.hahtuvaopintopolku.fi")

    // swagger
    System.setProperty("springdoc.api-docs.path", "/openapi/v3/api-docs")
    System.setProperty("springdoc.swagger-ui.path", "/static/swagger-ui/index.html")
    System.setProperty("springdoc.swagger-ui.tagsSorter", "alpha")

    // lokaalispesifit smtp- ja s3-konfiguraatiot
    System.setProperty("MODE", "LOCAL")
    System.setProperty("FAKEMAILER_HOST", "localhost")
    System.setProperty("FAKEMAILER_PORT", "1025")
    System.setProperty("aws.accessKeyId", "localstack")
    System.setProperty("aws.secretAccessKey", "localstack")
    System.setProperty("ATTACHMENTS_BUCKET_NAME", LOCAL_ATTACHMENTS_BUCKET_NAME)

    setupS3()
    setupSkannaus()
    setupLahetys()
    setupMonitoring()
    System.setProperty(ConfigurationUtil.AJASTUS_QUEUE_URL_KEY, getQueueUrl(LOCAL_AJASTUS_QUEUE_NAME).get)
    System.setProperty("SES_MONITOROINTI_QUEUE_URL", getQueueUrl(LOCAL_SES_MONITOROINTI_QUEUE_NAME).get)
    System.setProperty("CONFIGURATION_SET_NAME", LOCAL_SES_CONFIGURATION_SET_NAME)

    // ajetaan migraatiolambdan koodi
    new LambdaHandler().handleRequest(null, null)

    SpringApplication.run(classOf[DevApp], args:_*)
}
