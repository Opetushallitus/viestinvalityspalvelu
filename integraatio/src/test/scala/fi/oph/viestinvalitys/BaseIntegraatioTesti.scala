package fi.oph.viestinvalitys

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.dockerjava.api.model.{ExposedPort, HostConfig, PortBinding, Ports}
import com.nimbusds.jose.util.StandardCharset
import fi.oph.viestinvalitys.BaseIntegraatioTesti.*
import fi.oph.viestinvalitys.business.{Kieli, Prioriteetti, SisallonTyyppi, VastaanottajanTila}
import fi.oph.viestinvalitys.util.{AwsUtil, DbUtil}
import fi.oph.viestinvalitys.vastaanotto.model.Viesti.Vastaanottaja
import fi.oph.viestinvalitys.vastaanotto.model.*
import fi.oph.viestinvalitys.vastaanotto.resource.*
import fi.oph.viestinvalitys.vastaanotto.security.SecurityConstants
import org.junit.Before
import org.junit.jupiter.api.*
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.{UseMainMethod, WebEnvironment}
import org.springframework.http.MediaType
import org.springframework.mock.web.{MockHttpServletRequest, MockMultipartFile}
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.test.context.support.{WithAnonymousUser, WithMockUser}
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.util.TestSocketUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.{MockHttpServletRequestBuilder, MockMvcRequestBuilders}
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.{DefaultMockMvcBuilder, MockMvcBuilders, MockMvcConfigurer}
import org.springframework.web.context.WebApplicationContext
import org.testcontainers.containers.{GenericContainer, PostgreSQLContainer}
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.localstack.LocalStackContainer.Service
import org.testcontainers.utility.DockerImageName

import java.util.{Optional, UUID}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.jdk.CollectionConverters.*

class OphPostgresContainer(dockerImageName: String) extends PostgreSQLContainer[OphPostgresContainer](dockerImageName) {}

class RedisContainer(dockerImageName: String) extends GenericContainer[RedisContainer](dockerImageName) {}

object BaseIntegraatioTesti {

  lazy val localstackPort = TestSocketUtils.findAvailableTcpPort
  lazy val postgresPort = TestSocketUtils.findAvailableTcpPort
  lazy val redisPort = TestSocketUtils.findAvailableTcpPort
}

/**
 * Lähetysapin integraatiotestit. Testeissä on pyritty kattamaan kaikkien endpointtien kaikki eri paluuarvoihin
 * johtavat skenaariot. Eri variaatiot näiden skenaarioiden sisällä (esim. erityyppiset validointiongelmat) testataan
 * yksikkötasolla.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, useMainMethod = UseMainMethod.ALWAYS, classes = Array(classOf[DevApp]))
@TestInstance(Lifecycle.PER_CLASS)
class BaseIntegraatioTesti {

  val LOG = LoggerFactory.getLogger(this.getClass)

  val localstack: LocalStackContainer = new LocalStackContainer(new DockerImageName("localstack/localstack:2.2.0"))
    .withServices(Service.SQS, Service.SES, Service.CLOUDWATCH)
    .withLogConsumer(frame => LOG.info(frame.getUtf8StringWithoutLineEnding))
    .withExposedPorts(4566)
    .withCreateContainerCmdModifier(m => m.withHostConfig(new HostConfig()
      .withPortBindings(new PortBinding(Ports.Binding.bindPort(localstackPort), new ExposedPort(4566)))))

  val postgres: OphPostgresContainer = new OphPostgresContainer("postgres:15.4")
    .withDatabaseName("viestinvalitys")
    .withUsername("app")
    .withPassword("app")
    .withLogConsumer(frame => LOG.info(frame.getUtf8StringWithoutLineEnding))
    .withExposedPorts(5432)
    .withCreateContainerCmdModifier(m => m.withHostConfig(new HostConfig()
      .withPortBindings(new PortBinding(Ports.Binding.bindPort(postgresPort), new ExposedPort(5432)))))

  val redis: RedisContainer = new RedisContainer("redis:7.2.3-alpine")
    .withExposedPorts(6379)
    .withCreateContainerCmdModifier(m => m.withHostConfig(new HostConfig()
      .withPortBindings(new PortBinding(Ports.Binding.bindPort(redisPort), new ExposedPort(6379)))))

  // kontteja ei voi käynnistää vasta @BeforeAll-metodissa koska spring-konteksti rakennetaan ennen sitä
  val setupDone = {
    localstack.start()
    postgres.start()
    redis.start()
    System.setProperty(AwsUtil.LOCALSTACK_HOST_KEY, "http://localhost:" + localstack.getMappedPort(4566).toString)
    System.setProperty(DbUtil.LOCAL_POSTGRES_PORT_KEY, postgres.getMappedPort(5432).toString)
    System.setProperty("spring.data.redis.host", "localhost")
    System.setProperty("spring.data.redis.port", redis.getMappedPort(6379).toString)

    LocalUtil.setupLocal()
    true
  }

  @AfterAll def teardown(): Unit = {
    postgres.stop()
    localstack.stop()
    redis.stop()
  }
}
