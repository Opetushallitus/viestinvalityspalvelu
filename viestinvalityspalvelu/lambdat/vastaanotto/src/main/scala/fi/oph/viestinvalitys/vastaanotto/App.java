package fi.oph.viestinvalitys.vastaanotto;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@SpringBootApplication
@EnableWebMvc
@Profile("default")
public class App {

  public static final String CALLER_ID = "1.2.246.562.10.00000000001.viestinvalityspalvelu";

}
