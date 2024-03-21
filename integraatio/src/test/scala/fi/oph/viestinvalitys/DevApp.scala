package fi.oph.viestinvalitys

import fi.oph.viestinvalitys.util.{AwsUtil, ConfigurationUtil}
import fi.oph.viestinvalitys.vastaanotto.resource.LahetysAPIConstants
import org.apache.commons.io.IOUtils
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import software.amazon.awssdk.services.s3.model.{CreateBucketRequest, ListObjectsRequest, PutObjectRequest}
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.ses.model.{ConfigurationSet, CreateConfigurationSetEventDestinationRequest, CreateConfigurationSetRequest, EventDestination, EventType, SNSDestination, VerifyDomainIdentityRequest}
import software.amazon.awssdk.services.sns.model.{CreateTopicRequest, SubscribeRequest}
import software.amazon.awssdk.services.sqs.model.{CreateQueueRequest, ListQueuesRequest}

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
    System.setProperty("web.url.cas", "https://virkailija.hahtuvaopintopolku.fi/cas")

    System.setProperty("kayttooikeus-service.userDetails.byUsername", "https://virkailija.hahtuvaopintopolku.fi/kayttooikeus-service/userDetails/$1")

    System.setProperty("host.virkailija", "virkailija.hahtuvaopintopolku.fi")
    // swagger
    System.setProperty("springdoc.api-docs.path", "/openapi/v3/api-docs")
    System.setProperty("springdoc.swagger-ui.path", "/static/swagger-ui/index.html")
    System.setProperty("springdoc.swagger-ui.tagsSorter", "alpha")

    LocalUtil.setupLocal()

    SpringApplication.run(classOf[DevApp], args:_*)
}
