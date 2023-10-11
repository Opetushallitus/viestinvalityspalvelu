package fi.oph.viestinvalitus.vastaanotto;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.List;

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

  /**
   * Päivitetään serverin osoite jotta swagger-ui:sta tehdyt kutsut menevät oikeaan paikkaan
   */
  @Bean
  public OpenAPI customOpenAPI() {
    Server server = new Server();
    server.setUrl("https://viestinvalitus.hahtuvaopintopolku.fi");
    return new OpenAPI().servers(List.of(server));
  }

  /**
   * Käytetään Jedistä Lettucen sijaan koska yhteyden saaminen ylös näyttää olevan huomattavasti nopeampaa
   */
  @Bean
  public JedisConnectionFactory redisConnectionFactory() {
    RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
    config.setHostName(System.getenv("spring_redis_host"));
    config.setPort(Integer.parseInt(System.getenv("spring_redis_port")));

    JedisConnectionFactory connectionFactory = new JedisConnectionFactory(config);
    connectionFactory.setUsePool(false);
    return connectionFactory;
  }
  
  public static void main(String[] args) {
    // ssl-konfiguraatio
    System.setProperty("server.ssl.key-store-type", "PKCS12");
    System.setProperty("server.ssl.key-store", "classpath:sijoittelu.p12");
    System.setProperty("server.ssl.key-store-password", "password");
    System.setProperty("server.ssl.key-alias", "sijoittelu");
    System.setProperty("server.ssl.enabled", "true");
    System.setProperty("server.port", "8443");

    // cas-configuraatio
    System.setProperty("cas-service.service", "https://localhost:8443");
    System.setProperty("cas-service.sendRenew", "false");
    System.setProperty("cas-service.key", "viestinvalituspalvelu");
    System.setProperty("web.url.cas", "https://virkailija.hahtuvaopintopolku.fi/cas");

    System.setProperty("kayttooikeus-service.userDetails.byUsername", "https://virkailija.hahtuvaopintopolku.fi/kayttooikeus-service/userDetails/$1");
    
    System.setProperty("host.virkailija", "virkailija.hahtuvaopintopolku.fi");

    // redis
    System.setProperty("spring.redis.host", "localhost");
    System.setProperty("spring.redis.port", "6379");

    // swagger
    System.setProperty("springdoc.api-docs.path", "/openapi/v3/api-docs");
    System.setProperty("springdoc.swagger-ui.path", "/openapi/index.html");

    SpringApplication.run(App.class, args);
  }
}
