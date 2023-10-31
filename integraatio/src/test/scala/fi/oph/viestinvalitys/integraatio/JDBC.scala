package fi.oph.viestinvalitys.integraatio

import fi.oph.viestinvalitys.model.Viestipohjat
import org.assertj.core.api.recursive.comparison.DualValue
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.{Test, TestInstance}
import org.junit.runner.RunWith
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.annotation.Configuration
import org.springframework.context.{ApplicationContextInitializer, ConfigurableApplicationContext}
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.utility.DockerImageName
import slick.jdbc.PostgresProfile.api.*
import java.util.UUID

import java.lang.NoSuchMethodException
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, DurationInt}
@Test
@TestInstance(Lifecycle.PER_CLASS)
class JDBC {

  @Container var postgres: OphPostgresContainer = new OphPostgresContainer("postgres:15.4")
    .withDatabaseName("viestinvalitys")
    .withUsername("viestinvalitys")
    .withPassword("viestinvalitys")

  val driver = "org.postgresql.Driver"

  def createDb(host: String, port: Int, dbName: String, user: String, pwd: String) = {
    val onlyHostNoDbUrl = s"jdbc:postgresql://$host:$port/"
    val ds: Database = Database.forURL(onlyHostNoDbUrl, user = user, password = pwd, driver = driver)
    Await.result(ds.run(sqlu"CREATE DATABASE #$dbName"), Duration(5, TimeUnit.SECONDS))
  }

  @Test def testJDBC(): Unit = {

    postgres.start();
    createDb(postgres.getHost, postgres.getMappedPort(5432), "test", "viestinvalitys", "viestinvalitys")

    val ds: PGSimpleDataSource = new PGSimpleDataSource()
    ds.setServerName(postgres.getHost)
    ds.setDatabaseName("test")
    ds.setPortNumber(postgres.getMappedPort(5432))
    ds.setUser(postgres.getUsername)
    ds.setPassword(postgres.getPassword)
    val db = Database.forDataSource(ds, Option.empty)

    val viestipohjat = TableQuery[Viestipohjat]

    val setup = DBIO.seq(
      // Create the tables, including primary and foreign keys
      (viestipohjat.schema).create,

      // Insert some suppliers
      viestipohjat += (UUID.randomUUID(), "Testiviesti 1"),
      viestipohjat += (UUID.randomUUID(), "Testiviesti 2")
    )
    Await.ready(db.run(setup).map(Unit => {
      db.run(viestipohjat.result).map(_.foreach {
        case(id, heading) =>
          println("  " + id + "\t" + heading)
      })
    }), 5.seconds)

    Thread.sleep(1000)
  }
}
