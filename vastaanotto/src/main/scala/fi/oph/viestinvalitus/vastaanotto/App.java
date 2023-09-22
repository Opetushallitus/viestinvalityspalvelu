package fi.oph.viestinvalitus.vastaanotto;

import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@SpringBootApplication
@EnableWebMvc
public class App {

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

  public static void main(String[] args) {
    SpringApplication.run(App.class, args);
  }
}
