package fi.oph.viestinvalitus.vastaanotto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@SpringBootApplication
@EnableWebMvc
public class DevApp {
  public static void main(String[] args) {
    System.setProperty("spring.profiles.active", "dev");

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

    // swagger
    System.setProperty("springdoc.api-docs.path", "/openapi/v3/api-docs");
    System.setProperty("springdoc.swagger-ui.path", "/openapi/index.html");

    SpringApplication.run(App.class, args);
  }
}
