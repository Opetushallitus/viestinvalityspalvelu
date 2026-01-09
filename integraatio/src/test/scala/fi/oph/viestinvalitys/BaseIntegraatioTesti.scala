package fi.oph.viestinvalitys

import com.github.dockerjava.api.model.{ExposedPort, HostConfig, PortBinding, Ports}
import fi.oph.viestinvalitys.BaseIntegraatioTesti.*
import fi.oph.viestinvalitys.business.{KantaOperaatiot}
import fi.oph.viestinvalitys.util.{AwsUtil, DbUtil}
import org.junit.jupiter.api.*
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.{UseMainMethod, WebEnvironment}
import org.springframework.test.util.TestSocketUtils
import org.testcontainers.containers.{PostgreSQLContainer}
import org.testcontainers.utility.DockerImageName
import org.postgresql.ds.PGSimpleDataSource
import slick.jdbc.JdbcBackend.Database

import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.builder.ImageFromDockerfile
import java.nio.file.Paths

class OphPostgresContainer(dockerImageName: String) extends PostgreSQLContainer[OphPostgresContainer](dockerImageName) {}

class OphLocalStackContainer(image: ImageFromDockerfile) extends GenericContainer[OphLocalStackContainer](image) {
  withExposedPorts(4566)
}

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

  val localstack: OphLocalStackContainer = {
    val userDir = System.getProperty("user.dir")
    val dockerfilePath = if (userDir.endsWith("/integraatio")) {
      Paths.get(userDir + "/docker/localstack/Dockerfile")
    } else {
      Paths.get(userDir + "/integraatio/docker/localstack/Dockerfile")
    }
    LOG.info(s"Using Dockerfile from: ${dockerfilePath.toAbsolutePath}")
    new OphLocalStackContainer(new ImageFromDockerfile()
      .withDockerfile(dockerfilePath))
      .withLogConsumer(frame => LOG.info(frame.getUtf8StringWithoutLineEnding))
      .withCreateContainerCmdModifier(m => m.withHostConfig(new HostConfig()
        .withPortBindings(new PortBinding(Ports.Binding.bindPort(localstackPort), new ExposedPort(4566)))))
  }

  val postgres: OphPostgresContainer = new OphPostgresContainer("postgres:15.4")
    .withDatabaseName("viestinvalityspalvelu")
    .withUsername("app")
    .withPassword("app")
    .withLogConsumer(frame => LOG.info(frame.getUtf8StringWithoutLineEnding))
    .withExposedPorts(5432)
    .withCreateContainerCmdModifier(m => m.withHostConfig(new HostConfig()
      .withPortBindings(new PortBinding(Ports.Binding.bindPort(postgresPort), new ExposedPort(5432)))))

  private def getDatasource() =
    val ds: PGSimpleDataSource = new PGSimpleDataSource()
    ds.setServerNames(Array("localhost"))
    ds.setDatabaseName("viestinvalityspalvelu")
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
    System.setProperty("ENVIRONMENT_NAME", "localtest")
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
