package fi.oph.viestinvalitys.orkestraattori;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class App {

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

  public static ConfigurableApplicationContext start(String[] args) {
    return new SpringApplicationBuilder(App.class)
        .web(WebApplicationType.NONE)
        .run(args);
  }

  public static void main(String[] args) {
    System.setProperty("postgres.host", "abc");
    System.setProperty("postgres.port", "5432");
    System.setProperty("postgres.username", "abc");
    System.setProperty("postgres.password", "abc");
    System.setProperty("queueurl", "abc");
    start(args);
  }
}
