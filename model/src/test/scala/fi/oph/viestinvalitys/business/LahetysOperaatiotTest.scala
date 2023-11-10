package fi.oph.viestinvalitys.business

import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import fi.oph.viestinvalitys.business.{LahetysOperaatiot, ViestinTila}
import fi.oph.viestinvalitys.db.{DbUtil, Viestit}
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.postgresql.ds.PGSimpleDataSource
import org.slf4j.LoggerFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.PostgresProfile.api.*

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class OphPostgresContainer(dockerImageName: String) extends PostgreSQLContainer[OphPostgresContainer](dockerImageName) {
}

@TestInstance(Lifecycle.PER_CLASS)
class OrkestraattoriTest {

  val LOG = LoggerFactory.getLogger(classOf[OrkestraattoriTest])

  @Container var postgres: OphPostgresContainer = new OphPostgresContainer("postgres:15.4")
    .withDatabaseName("viestinvalitys")
    .withUsername("viestinvalitys")
    .withPassword("viestinvalitys")
    //.withExposedPorts(5432)
    .withLogConsumer(frame => LOG.info(frame.getUtf8StringWithoutLineEnding))

  def getDatasource() =
    val ds: PGSimpleDataSource = new PGSimpleDataSource()
    ds.setServerNames(Array("localhost"))
    ds.setDatabaseName("viestinvalitys")
    ds.setPortNumbers(Array(postgres.getMappedPort(5432)))
    ds.setUser("viestinvalitys")
    ds.setPassword("viestinvalitys")
    ds

  def getDatabase(): JdbcBackend.JdbcDatabaseDef =
    Database.forDataSource(getDatasource(), Option.empty)

  @BeforeAll def setup(): Unit = {
    postgres.start()
  }

  @AfterAll def teardown(): Unit = {
    postgres.stop()
  }

  @BeforeEach def setupTest(): Unit = {
    val flyway = Flyway.configure()
      .dataSource(getDatasource())
      .outOfOrder(true)
      .locations("flyway")
      .load()
    flyway.migrate()
  }

  @AfterEach def teardownTest(): Unit = {
    Await.result(getDatabase().run(sqlu"""DROP TABLE viestit; DROP TABLE viestiryhmat_liitteet; DROP TABLE viestiryhmat; DROP TABLE lahetykset; DROP TABLE liitteet; DROP TABLE metadata_avaimet; DROP TABLE metadata; DROP TABLE flyway_schema_history;"""), 5.seconds)
  }

  @Test def testLisaaViestiRyhma(): Unit =
    val lahetysOperaatiot = LahetysOperaatiot(getDatabase())

    val viestiTunniste = DbUtil.getUUID();
    val tallennetutViestit = lahetysOperaatiot.tallennaViestiRyhma(
      "otsikko",
      "sisältö",
      SisallonTyyppi.TEXT,
      Set(Kieli.FI),
      Option.empty,
      Lahettaja("Lasse Lahettaja", "lasse.lahettaja@oph.fi"),
      Seq(
        Vastaanottaja("Vallu Vastaanottaja", "vallu.vastaanottaja@example.com"),
        Vastaanottaja("Virpi Vastaanottaja", "vallu.vastaanottaja@example.com")
      ),
      Seq.empty,
      "lahettavapalvelu",
      Option.empty,
      Prioriteetti.NORMAALI,
      10,
      Set.empty,
      Map("avain" -> "arvo"),
      "omistaja"
    )

    val viestit = lahetysOperaatiot.getViestit(tallennetutViestit.map(viesti => viesti.tunniste))


  @Test def testGetLahetettavatViestit(): Unit =
    val db = getDatabase();
    val VIESTITUNNISTE = UUID.fromString("699eb156-c631-4e84-af0a-657a3ca03406")

    val insertViestiryhma = sqlu"""INSERT INTO viestiryhmat VALUES('42ddcdd1-98a0-4202-9ed2-52924379a732', 'Otsikko', 'Sisältö', 'TEXT', false, false, false, '1.2.3', 'Lasse Lähettäjä', 'lasse.lahettaja@oph.fi', 'palvelu', 'NORMAALI')"""
    Await.result(db.run(insertViestiryhma), 5.seconds)

    val insertViesti = sqlu"""INSERT INTO viestit VALUES('#${VIESTITUNNISTE.toString}', '42ddcdd1-98a0-4202-9ed2-52924379a732', '3fa85f64-5717-4562-b3fc-2c963f66afa6', 'Vallu Vastaanottaja', 'vallu.vastaanottaja@example.com', 'ODOTTAA')"""
    Await.result(db.run(insertViesti), 5.seconds)

    val lahetettavat = new LahetysOperaatiot(getDatabase()).getLahettavatViestit(10)

    val tila = Await.result(db.run(TableQuery[Viestit].filter(viesti => viesti.tunniste===VIESTITUNNISTE).map(_.tila).result.head), 5.seconds)
    Assertions.assertEquals(ViestinTila.LAHETYKSESSA.toString, tila)

    // TODO: testaa tilanne jossa ei lähetettäviä viestejä

  @Test def testPaivitaViestinTila(): Unit =
    val db = getDatabase();

    val insertViestiryhma = sqlu"""INSERT INTO viestiryhmat VALUES('42ddcdd1-98a0-4202-9ed2-52924379a732', 'Otsikko', 'Sisältö', 'TEXT', false, false, false, '1.2.3', 'Lasse Lähettäjä', 'lasse.lahettaja@oph.fi', 'palvelu', 'NORMAALI')"""
    Await.result(db.run(insertViestiryhma), 5.seconds)

    val VIESTITUNNISTE = UUID.randomUUID()
    val insertViesti = sqlu"""INSERT INTO viestit VALUES('#${VIESTITUNNISTE.toString}', '42ddcdd1-98a0-4202-9ed2-52924379a732', '3fa85f64-5717-4562-b3fc-2c963f66afa6', 'Vallu Vastaanottaja', 'vallu.vastaanottaja@example.com', 'ODOTTAA')"""
    Await.result(db.run(insertViesti), 5.seconds)

    new LahetysOperaatiot(db).paivitaViestinTila(VIESTITUNNISTE, ViestinTila.LAHETETTY)

    val tila = Await.result(db.run(TableQuery[Viestit].filter(viesti => viesti.tunniste===VIESTITUNNISTE).map(_.tila).result.head), 5.seconds)
    Assertions.assertEquals(ViestinTila.LAHETETTY.toString, tila)


}
