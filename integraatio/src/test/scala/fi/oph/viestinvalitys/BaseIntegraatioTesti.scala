package fi.oph.viestinvalitys

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.dockerjava.api.model.{ExposedPort, HostConfig, PortBinding, Ports}
import com.nimbusds.jose.util.StandardCharset
import fi.oph.viestinvalitys.BaseIntegraatioTesti.*
import fi.oph.viestinvalitys.business.{KantaOperaatiot, Kieli, Prioriteetti, SisallonTyyppi, VastaanottajanTila}
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
import org.postgresql.ds.PGSimpleDataSource
import slick.jdbc.JdbcBackend.Database

import java.util.{Optional, UUID}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.jdk.CollectionConverters.*

class OphPostgresContainer(dockerImageName: String) extends PostgreSQLContainer[OphPostgresContainer](dockerImageName) {}

object BaseIntegraatioTesti {

  // Vakioidaan portit testien suorituksen ajaksi. Tämä on tarpeen koska koodissa on lazy val -konfiguraatioarvoja jotka
  // eivät resetoidu testien välissä
  lazy val localstackPort = TestSocketUtils.findAvailableTcpPort
  lazy val postgresPort = TestSocketUtils.findAvailableTcpPort
}

/**
 * Integraatiotestien base-luokka. Käynnistää ennen testejä Localstacking, Postgresin ja Rediksen. Lisäksi konfiguroi
 * [[KantaOperaatiot]]-instanssin, jonka avulla voidaan validoida kannan tila.
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

  private def getDatasource() =
    val ds: PGSimpleDataSource = new PGSimpleDataSource()
    ds.setServerNames(Array("localhost"))
    ds.setDatabaseName("viestinvalitys")
    ds.setPortNumbers(Array(postgres.getMappedPort(5432)))
    ds.setUser("app")
    ds.setPassword("app")
    ds

  var kantaOperaatiot: KantaOperaatiot = null

  // kontteja ei voi käynnistää vasta @BeforeAll-metodissa koska spring-konteksti rakennetaan ennen sitä
  val setupDone = {
    localstack.start()
    postgres.start()
    System.setProperty(AwsUtil.LOCALSTACK_HOST_KEY, "http://localhost:" + localstack.getMappedPort(4566).toString)
    System.setProperty(DbUtil.LOCAL_POSTGRES_PORT_KEY, postgres.getMappedPort(5432).toString)
    System.setProperty("LOCAL_OPINTOPOLKU_DOMAIN", "localopintopolku.fi")

    LocalUtil.setupLocal()

    val database = Database.forDataSource(getDatasource(), Option.empty)
    kantaOperaatiot = KantaOperaatiot(database)
    true
  }

  @AfterAll def teardown(): Unit = {
    postgres.stop()
    localstack.stop()
  }
}
