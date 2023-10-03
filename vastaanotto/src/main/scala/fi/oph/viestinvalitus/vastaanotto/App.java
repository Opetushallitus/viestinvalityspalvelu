package fi.oph.viestinvalitus.vastaanotto;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@SpringBootApplication
@EnableWebMvc
public class App {

  public static final String CALLER_ID = "1.2.246.562.10.00000000001.sijoittelu.sijoittelu-service";

  static class Ctx implements Context {
    @Override
    public String getAwsRequestId() {
      return "";
    }

    @Override
    public String getLogGroupName() {
      return "";
    }

    @Override
    public String getLogStreamName() {
      return "";
    }

    @Override
    public String getFunctionName() {
      return "";
    }

    @Override
    public String getFunctionVersion() {
      return "";
    }

    @Override
    public String getInvokedFunctionArn() {
      return "";
    }

    @Override
    public CognitoIdentity getIdentity() {
      return null;
    }

    @Override
    public ClientContext getClientContext() {
      return null;
    }

    @Override
    public int getRemainingTimeInMillis() {
      return 0;
    }

    @Override
    public int getMemoryLimitInMB() {
      return 0;
    }

    @Override
    public LambdaLogger getLogger() {
      return null;
    }
  }

/*
  @Bean
  public SQSService getSQSService(@Value("${localstack.endpoint}") String endpoint, @Value("${localstack.region}") String region, @Value("${localstack.accessKey}") String accessKey, @Value("${localstack.secretKey}") String secretKey, @Value("${localstack.queueUrl}") String queueUrl, @Value("${LOCALSTACK_HOSTNAME}") String localstackHostname) {
    try {
      queueUrl = new URIBuilder(queueUrl).setHost(localstackHostname).setPort(4566).build().toString();
      endpoint = new URIBuilder(endpoint).setHost(localstackHostname).setPort(4566).build().toString();

      return new SQSService(queueUrl, endpoint, region, accessKey, secretKey);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

  }
*/

  public static void main(String[] args) {
    // ssl-konfiguraatio
    System.setProperty("server.ssl.key-store-type", "PKCS12");
    System.setProperty("server.ssl.key-store", "classpath:sijoittelu.p12");
    System.setProperty("server.ssl.key-store-password", "password");
    System.setProperty("server.ssl.key-alias", "sijoittelu");
    System.setProperty("server.ssl.enabled", "true");
    System.setProperty("server.port", "8443");

    // cas-configuraatio
    System.setProperty("cas-service.service", "https://localhost:8443/viestinvalituspalvelu");
    System.setProperty("cas-service.sendRenew", "false");
    System.setProperty("cas-service.key", "viestinvalituspalvelu");
    System.setProperty("web.url.cas", "https://virkailija.hahtuvaopintopolku.fi/cas");

    System.setProperty("kayttooikeus-service.userDetails.byUsername", "https://virkailija.hahtuvaopintopolku.fi/kayttooikeus-service/userDetails/$1");
    
    System.setProperty("host.virkailija", "virkailija.hahtuvaopintopolku.fi");

    // redis
    System.setProperty("spring.redis.host", "localhost");
    System.setProperty("spring.redis.port", "6379");

    System.setProperty("server.servlet.context-path", "/viestinvalituspalvelu");

    System.setProperty("logging.level.root", "TRACE");

    SpringApplication.run(App.class, args);
  }
}
