package fi.oph.viestinvalitys.vastaanotto

import fi.oph.viestinvalitys.aws.AwsUtil
import fi.oph.viestinvalitys.vastaanotto.resource.APIConstants
import fi.oph.viestinvalitys.flyway.LambdaHandler
import org.apache.commons.io.IOUtils
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import software.amazon.awssdk.services.s3.model.{CreateBucketRequest, ListObjectsRequest, PutObjectRequest}
import software.amazon.awssdk.core.sync.RequestBody

@SpringBootApplication
@EnableWebMvc
@EnableScheduling
class DevApp {}

object DevApp {

  final val LOCAL_ATTACHMENTS_BUCKET_NAME = "local-viestinvalityspalvelu-attachments";

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

    // luodaan bucket liitetiedostoille jos ei olemassa
    val s3Client = AwsUtil.getS3Client()
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

    // ajetaan migraatiolambdan koodi
    new LambdaHandler().handleRequest(null, null)

    SpringApplication.run(classOf[DevApp], args:_*)
}
