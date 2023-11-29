package fi.oph.viestinvalitys.vastaanotto;

import fi.oph.viestinvalitys.aws.AwsUtil;
import fi.oph.viestinvalitys.flyway.LambdaHandler;
import fi.oph.viestinvalitys.vastaanotto.resource.APIConstants;
import org.apache.commons.io.IOUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.CreateTopicResponse;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;

@SpringBootApplication
@EnableWebMvc
@EnableScheduling
public class DevApp {

  public static final String LOCAL_ATTACHMENTS_BUCKET_NAME = "local-viestinvalityspalvelu-attachments";
  public static void main(String[] args) {
    System.setProperty("spring.profiles.active", "dev");

    // ssl-konfiguraatio
    System.setProperty("server.ssl.key-store-type", "PKCS12");
    System.setProperty("server.ssl.key-store", "classpath:viestinvalitys.p12");
    System.setProperty("server.ssl.key-store-password", "password");
    System.setProperty("server.ssl.key-alias", "viestinvalitys");
    System.setProperty("server.ssl.enabled", "true");
    System.setProperty("server.port", "8443");

    // cas-configuraatio
    System.setProperty("cas-service.service", "https://localhost:8443");
    System.setProperty("cas-service.sendRenew", "false");
    System.setProperty("cas-service.key", "viestinvalityspalvelu");
    System.setProperty("web.url.cas", "https://virkailija.hahtuvaopintopolku.fi/cas");

    System.setProperty("kayttooikeus-service.userDetails.byUsername", "https://virkailija.hahtuvaopintopolku.fi/kayttooikeus-service/userDetails/$1");
    
    System.setProperty("host.virkailija", "virkailija.hahtuvaopintopolku.fi");

    // swagger
    System.setProperty("springdoc.api-docs.path", "/openapi/v3/api-docs");
    System.setProperty("springdoc.swagger-ui.path", "/static/swagger-ui/index.html");
    System.setProperty("springdoc.swagger-ui.tagsSorter", "alpha");

    // lokaalispesifit smtp- ja s3-konfiguraatiot
    System.setProperty("MODE", "LOCAL");
    System.setProperty("FAKEMAILER_HOST", "localhost");
    System.setProperty("FAKEMAILER_PORT", "1025");
    System.setProperty("aws.accessKeyId", "localstack");
    System.setProperty("aws.secretAccessKey", "localstack");
    System.setProperty("ATTACHMENTS_BUCKET_NAME", LOCAL_ATTACHMENTS_BUCKET_NAME);

    // luodaan bucket liitetiedostoille jos ei olemassa
    S3Client s3Client = AwsUtil.getS3Client();
    if(s3Client.listBuckets().buckets().stream().filter(b -> b.name().equals(LOCAL_ATTACHMENTS_BUCKET_NAME)).findFirst().isEmpty()) {
      s3Client.createBucket(CreateBucketRequest.builder()
          .bucket(LOCAL_ATTACHMENTS_BUCKET_NAME)
          .build());
    }

    // tallennetaan esimerkkiliite jos ei olemassa
    if(s3Client.listObjects(ListObjectsRequest.builder()
        .bucket(LOCAL_ATTACHMENTS_BUCKET_NAME)
        .build()).contents().stream().filter(o -> o.key().equals(APIConstants.ESIMERKKI_LIITETUNNISTE())).findFirst().isEmpty()) {
      try {
        s3Client.putObject(PutObjectRequest
            .builder()
            .bucket(LOCAL_ATTACHMENTS_BUCKET_NAME)
            .key(APIConstants.ESIMERKKI_LIITETUNNISTE())
            .contentType("image/png")
            .build(), RequestBody.fromBytes(IOUtils.toByteArray(DevApp.class.getClassLoader().getResourceAsStream("screenshot.png"))));
      } catch(Exception e) {
        throw new RuntimeException(e);
      }
    }

    // ajetaan migraatiolambdan koodi
    new LambdaHandler().handleRequest(null, null);

    SpringApplication.run(DevApp.class, args);
  }
}
