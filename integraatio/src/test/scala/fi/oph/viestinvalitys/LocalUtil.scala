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
import fi.oph.viestinvalitys.business.{KantaOperaatiot, Kayttooikeus, Kieli, Kontakti, Prioriteetti, SisallonTyyppi, VastaanottajanTila}
import fi.oph.viestinvalitys.vastaanotto.security.SecurityConstants
import org.slf4j.LoggerFactory

import java.util.UUID
import scala.Range
import scala.beans.BeanProperty
import scala.collection.immutable.Range

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

  val LOG = LoggerFactory.getLogger(classOf[LocalUtil])

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
    val kayttooikeus = Kayttooikeus("OIKEUS", Option.apply("1.2.246.562.10.240484683010"))
    val kantaOperaatiot = new KantaOperaatiot(DbUtil.database)
    val lahetyksia = kantaOperaatiot.getLahetykset(Option.empty, Option.apply(20), Set(kayttooikeus))
    if(lahetyksia.isEmpty || lahetyksia.length < 3) {
      // lähetyksiä massaviestillä jossa samalla viestillä useita vastaanottajia
      Range(0, 20).map(counter => {
        val lahetys2 = kantaOperaatiot.tallennaLahetys(
          "Massalähetysotsikko "+counter,
          "omistaja",
          "hakemuspalvelu",
          Option.apply("0.1.2.3"),
          Kontakti(Option.apply("Joku Virkailija"), "hakemuspalvelu@opintopolku.fi"),
          Option.apply("noreply@opintopolku.fi"),
          Prioriteetti.NORMAALI,
          365
        )
        kantaOperaatiot.tallennaViesti("Massaviestin " + counter +" testiotsikko",
          "Massaviestin sisältö",
          SisallonTyyppi.TEXT,
          Set(Kieli.FI),
          Map.empty, // maskit
          Option.empty, // läh. oid
          Option.empty, // lähettäjä
          Option.empty, // replyto
          Range(0, 20).map(suffix => Kontakti(Option.apply("Joku Vastaanottaja" + suffix), "vastaanottaja" + suffix + "@example.com")),
          Seq.empty,
          Option.empty,
          Option.apply(lahetys2.tunniste),
          Option.apply(Prioriteetti.NORMAALI),
          Option.apply(365),
          Set(kayttooikeus),
          Map("avain" -> Seq("arvo")),
          "omistaja")
      })
      // lähetyksiä joissa räätälöity viesti useilla vastaanottajilla
      Range(0, 6).map(counter => {
        val lahetys = kantaOperaatiot.tallennaLahetys(
          "Räätälöidyn massaviestin " + counter + " testiotsikko",
          "omistaja",
          "hakemuspalvelu",
          Option.apply("0.1.2.3"),
          Kontakti(Option.apply("Testi Virkailija" + counter), "noreply@opintopolku.fi"),
          Option.apply("noreply@opintopolku.fi"),
          Prioriteetti.NORMAALI,
          365
        )
        // räätälöidyt viestit lähetystunnuksella, yksi vastaanottaja per viesti
        Range(0, 25).map(viestinro => {
          val (viesti, vastaanottajat) = kantaOperaatiot.tallennaViesti("Viestin testiotsikko " + viestinro,
            "Viestin sisältö " + viestinro,
            SisallonTyyppi.TEXT,
            Set(Kieli.FI),
            Map.empty,
            Option.empty,
            Option.empty,
            Option.empty,
            Seq(Kontakti(Option.apply("Testi Vastaanottaja " + viestinro), "testi.vastaanottaja" + viestinro + "@example.com")),
            Seq.empty,
            Option.empty,
            Option.apply(lahetys.tunniste),
            Option.empty,
            Option.apply(365),
            Set(kayttooikeus),
            Map("avain" -> Seq("arvo")),
            "omistaja")
          if (counter == 1) {
            kantaOperaatiot.paivitaVastaanottajaLahetetyksi(vastaanottajat.head.tunniste, "ses-tunniste")
            kantaOperaatiot.paivitaVastaanotonTila("ses-tunniste", VastaanottajanTila.DELIVERY, Option.empty)
          } else {
            if (viestinro <= 10) {
              kantaOperaatiot.paivitaVastaanottajaLahetetyksi(vastaanottajat.head.tunniste, "ses-tunniste")
              kantaOperaatiot.paivitaVastaanotonTila("ses-tunniste", VastaanottajanTila.DELIVERY, Option.empty)
            }
            if (viestinro > 10 && viestinro < 15)
              kantaOperaatiot.paivitaVastaanottajaVirhetilaan(vastaanottajat.head.tunniste, "lisätiedot virheestä")
          }
        })
      })
      // tyhjä lähetys
      val lahetys3 = kantaOperaatiot.tallennaLahetys(
        "Orpo lähetys",
        "omistaja",
        "osoitepalvelu",
        Option.apply("0.1.2.3"),
        Kontakti(Option.apply("Testi Virkailija"), "osoitepalvelu@opintopolku.fi"),
        Option.apply("noreply@opintopolku.fi"),
        Prioriteetti.NORMAALI,
        365
      )
      // viesti ilman lähetystunnusta
      kantaOperaatiot.tallennaViesti("Yksittäinen viesti",
        "Tämä on yksittäinen viesti muutamalla vastaanottajalla",
        SisallonTyyppi.TEXT,
        Set(Kieli.FI),
        Map.empty,
        Option.apply("0.1.2.3"),
        Option.apply(Kontakti(Option.apply("Testi Virkailija"), "testipalvelu@opintopolku.fi")),
        Option.apply("noreply@opintopolku.fi"),
        Range(0, 3).map(suffix => Kontakti(Option.apply("Testi Vastaanottaja" + suffix), "testi.vastaanottaja" + suffix + "@example.com")),
        Seq.empty,
        Option.apply("testipalvelu"),
        Option.empty,
        Option.apply(Prioriteetti.NORMAALI),
        Option.apply(365),
        Set(kayttooikeus),
        Map("avain" -> Seq("arvo")),
        "omistaja")
      kantaOperaatiot.tallennaViesti("Kuopio yhteiskunta- ja kauppatieteet viesti",
        "Tämä on viesti käyttöoikeushierarkian todentamiseen",
        SisallonTyyppi.TEXT,
        Set(Kieli.FI),
        Map.empty,
        Option.apply("0.1.2.3"),
        Option.apply(Kontakti(Option.apply("Testi Virkailija"), "hakemuspalvelu@opintopolku.fi")),
        Option.apply("noreply@opintopolku.fi"),
        Range(0, 3).map(suffix => Kontakti(Option.apply("Testi Vastaanottaja" + suffix), "testi.vastaanottaja" + suffix + "@example.com")),
        Seq.empty,
        Option.apply("hakemuspalvelu"),
        Option.empty,
        Option.apply(Prioriteetti.NORMAALI),
        Option.apply(365),
        Set(Kayttooikeus("HAKEMUS_CRUD", Some("1.2.246.562.10.2014041814455745619200"))),
        Map("avain" -> Seq("arvo")),
        "omistaja")
      kantaOperaatiot.tallennaViesti("Viesti ilman organisaatiota",
        "Tämä on viesti käyttöoikeustarkistuksen todentamiseen ilman organisaatiorajausta",
        SisallonTyyppi.TEXT,
        Set(Kieli.FI),
        Map.empty,
        Option.apply("0.1.2.3"),
        Option.apply(Kontakti(Option.apply("Testi Virkailija"), "hakemuspalvelu@opintopolku.fi")),
        Option.apply("noreply@opintopolku.fi"),
        Range(0, 3).map(suffix => Kontakti(Option.apply("Testi Vastaanottaja" + suffix), "testi.vastaanottaja" + suffix + "@example.com")),
        Seq.empty,
        Option.apply("hakemuspalvelu"),
        Option.empty,
        Option.apply(Prioriteetti.NORMAALI),
        Option.apply(365),
        Set(Kayttooikeus("OIKEUS", None)),
        Map("avain" -> Seq("arvo")),
        "omistaja")
    }

}

class LocalUtil {}
