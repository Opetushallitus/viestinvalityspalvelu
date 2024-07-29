package fi.oph.viestinvalitys

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.servlet.config.annotation.EnableWebMvc

@SpringBootApplication
@EnableWebMvc
@EnableScheduling
class DevApp {}

object DevApp {

  @main
  def mainMethod(args: String*): Unit =
    main(args.toArray)
  def main(args: Array[String]): Unit =
    // cas-configuraatio
    System.setProperty("cas-service.service", "https://localhost:8443")
    System.setProperty("cas-service.sendRenew", "false")
    System.setProperty("cas-service.key", "viestinvalityspalvelu")
    System.setProperty("web.url.cas", "https://virkailija.testiopintopolku.fi/cas")
    if(!"localtest".equals(System.getProperty("ENVIRONMENT_NAME"))) {
      System.setProperty("ENVIRONMENT_NAME","local")
      System.setProperty("DEV_OPINTOPOLKU_DOMAIN", "testiopintopolku.fi")
    }
    System.setProperty("kayttooikeus-service.userDetails.byUsername", "https://virkailija.testiopintopolku.fi/kayttooikeus-service/userDetails/$1")

    System.setProperty("host.virkailija", "virkailija.testiopintopolku.fi")
    // swagger
    System.setProperty("springdoc.api-docs.path", "/openapi/v3/api-docs")
    System.setProperty("springdoc.swagger-ui.path", "/static/swagger-ui/index.html")
    System.setProperty("springdoc.swagger-ui.tagsSorter", "alpha")

    LocalUtil.setupLocal()

    SpringApplication.run(classOf[DevApp], args:_*)
}
