package fi.oph.viestinvalitys

import fi.oph.viestinvalitys.vastaanotto.resource.LahetysAPIConstants
import fi.oph.viestinvalitys.migraatio.LambdaHandler
import fi.oph.viestinvalitys.util.{AwsUtil, ConfigurationUtil, DbUtil}
import org.apache.commons.io.IOUtils
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.model.{CreateBucketRequest, ListObjectsRequest, PutObjectRequest}
import software.amazon.awssdk.services.ses.model.{ConfigurationSet, CreateConfigurationSetEventDestinationRequest, CreateConfigurationSetRequest, EventDestination, EventType, SNSDestination, VerifyDomainIdentityRequest}
import software.amazon.awssdk.services.sns.model.{CreateTopicRequest, SubscribeRequest}
import software.amazon.awssdk.services.sqs.model.{CreateQueueRequest, ListQueuesRequest}
import com.amazonaws.services.lambda.runtime.{ClientContext, CognitoIdentity, Context, LambdaLogger}
import fi.oph.viestinvalitys.business.{KantaOperaatiot, Kontakti, Prioriteetti}

import java.util.UUID
import scala.beans.BeanProperty

case class TestAwsContext(
  @BeanProperty awsRequestId: String,
  @BeanProperty logGroupName: String,
  @BeanProperty logStreamName: String,
  @BeanProperty functionName: String,
  @BeanProperty functionVersion: String,
  @BeanProperty invokedFunctionArn: String,
  @BeanProperty identity: CognitoIdentity,
  @BeanProperty clientContext: ClientContext,
  @BeanProperty remainingTimeInMillis: Int,
  @BeanProperty memoryLimitInMB: Int,
  @BeanProperty logger: LambdaLogger
) extends Context {

  def this(functionName: String) =
    this(UUID.randomUUID().toString, null, null, functionName, null, null, null, null, 0, 0, null)
}

/**
 * Konfiguroi Localstacking lokaalia ympäristöä varten
 */
object LocalUtil {

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
      .build()).contents().stream().filter(o => o.key().equals(LahetysAPIConstants.ESIMERKKI_LIITETUNNISTE)).findFirst().isEmpty())
      try
        s3Client.putObject(PutObjectRequest
          .builder()
          .bucket(LOCAL_ATTACHMENTS_BUCKET_NAME)
          .key(LahetysAPIConstants.ESIMERKKI_LIITETUNNISTE)
          .contentType("image/png")
          .build(), RequestBody.fromBytes(IOUtils.toByteArray(classOf[LocalUtil].getClassLoader().getResourceAsStream("screenshot.png")
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
  // luodaan skannauseventtien jono jos ei jo luotu
    if (!getQueueUrl(LOCAL_SKANNAUS_QUEUE_NAME).isDefined)
      val createQueueResponse = AwsUtil.sqsClient.createQueue(CreateQueueRequest.builder()
        .queueName(LOCAL_SKANNAUS_QUEUE_NAME)
        .build())

  def setupLahetys(): Unit =
    // luodaan lähetyksen ajastuseventtien jono jos ei jo luotu
    if (!getQueueUrl(LOCAL_AJASTUS_QUEUE_NAME).isDefined)
      val createQueueResponse = AwsUtil.sqsClient.createQueue(CreateQueueRequest.builder()
        .queueName(LOCAL_AJASTUS_QUEUE_NAME)
        .build())

  def setupSesMonitoring(): Unit =
    // katsotaan onko SES-konfigurointi jo tehty
    if (!getQueueUrl(LOCAL_SES_MONITOROINTI_QUEUE_NAME).isDefined)

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
        .domain("localopintopolku.fi")
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

  def setupLocal(): Unit =
    // lokaalispesifit konfiguraatiot
    System.setProperty("MODE", "LOCAL")

    System.setProperty("aws.accessKeyId", "localstack")
    System.setProperty("aws.secretAccessKey", "localstack")

    LocalUtil.setupS3()
    LocalUtil.setupSkannaus()
    LocalUtil.setupLahetys()
    LocalUtil.setupSesMonitoring()

    System.setProperty("ENVIRONMENT_NAME", "local")
    System.setProperty("FAKEMAILER_HOST", "localhost")
    System.setProperty("FAKEMAILER_PORT", "1025")
    System.setProperty("ATTACHMENTS_BUCKET_NAME", LocalUtil.LOCAL_ATTACHMENTS_BUCKET_NAME)
    System.setProperty(ConfigurationUtil.AJASTUS_QUEUE_URL_KEY, LocalUtil.getQueueUrl(LocalUtil.LOCAL_AJASTUS_QUEUE_NAME).get)
    System.setProperty(ConfigurationUtil.SKANNAUS_QUEUE_URL_KEY, LocalUtil.getQueueUrl(LocalUtil.LOCAL_SKANNAUS_QUEUE_NAME).get)
    System.setProperty(ConfigurationUtil.SESMONITOROINTI_QUEUE_URL_KEY, LocalUtil.getQueueUrl(LocalUtil.LOCAL_SES_MONITOROINTI_QUEUE_NAME).get)
    System.setProperty("CONFIGURATION_SET_NAME", LocalUtil.LOCAL_SES_CONFIGURATION_SET_NAME)

    // ajetaan migraatiolambdan koodi
    new LambdaHandler().handleRequest(null, new TestAwsContext("migraatio"))

    // alustetaan data
    val kantaOperaatiot = new KantaOperaatiot(DbUtil.database)
    if(kantaOperaatiot.getLahetykset(Option.empty, Option.apply(20)).isEmpty) {
      // lähetystä joissa useita viestejä
      /*
      otsikko: String,
                            omistaja: String, lahettavaPalvelu: String,
                            lahettavanVirkailijanOID: Option[String],
                            lahettaja: Kontakti,
                            replyTo: Option[String],
                            prioriteetti: Prioriteetti,
                            sailytysAika: Int
       */
      val lahetys = kantaOperaatiot.tallennaLahetys(
        "Testiotsikko",
        "omistaja",
        "hakemuspalvelu",
        Option.apply("0.1.2.3"),
        Kontakti(Option.apply("Testi Virkailija"), "testi.virkailija@oph.fi"),
        Option.apply("no-reply@opintopolku.fi"),
        Prioriteetti.NORMAALI,
        365
      )
      val lahetys2 = kantaOperaatiot.tallennaLahetys(
        "Testiotsikko2",
        "omistaja",
        "hakemuspalvelu",
        Option.apply("0.1.2.3"),
        Kontakti(Option.apply("Testi Virkailija"), "testi.virkailija@oph.fi"),
        Option.apply("no-reply@opintopolku.fi"),
        Prioriteetti.NORMAALI,
        365
      )
      val lahetys3 = kantaOperaatiot.tallennaLahetys(
        "Testiotsikko3",
        "omistaja",
        "osoitepalvelu",
        Option.apply("0.1.2.3"),
        Kontakti(Option.apply("Testi Virkailija"), "testi.virkailija@oph.fi"),
        Option.apply("no-reply@opintopolku.fi"),
        Prioriteetti.NORMAALI,
        365
      )
      /*
      otsikko: String,
      sisalto: String,
      sisallonTyyppi: SisallonTyyppi,
      kielet: Set[Kieli],
      maskit: Map[String, Option[String]],
      lahettavanVirkailijanOID: Option[String],
      lahettaja: Option[Kontakti],
      replyTo: Option[String],
      vastaanottajat: Seq[Kontakti],
      liiteTunnisteet: Seq[UUID],
      lahettavaPalvelu: Option[String],
      lahetysTunniste: Option[UUID],
      prioriteetti: Option[Prioriteetti],
      sailytysAika: Option[Int],
      kayttooikeusRajoitukset: Set[String],
      metadata: Map[String, Seq[String]],
      omistaja: String
       */
      //kantaOperaatiot.tallennaViesti()
      // viesti lähetystunnuksella
      // viesti ilman lähetystunnusta

    }

}

class LocalUtil {}
