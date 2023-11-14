package fi.oph.viestinvalitys.business

import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import fi.oph.viestinvalitys.business.{LahetysOperaatiot, VastaanottajanTila}
import fi.oph.viestinvalitys.db.DbUtil
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
class LahetysOperaatiotTest {

  val LOG = LoggerFactory.getLogger(classOf[LahetysOperaatiotTest])

  @Container var postgres: OphPostgresContainer = new OphPostgresContainer("postgres:15.4")
    .withDatabaseName("viestinvalitys")
    .withUsername("viestinvalitys")
    .withPassword("viestinvalitys")
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
    Await.result(getDatabase().run(sqlu"""DROP TABLE vastaanottajat; DROP TABLE viestit_liitteet; DROP TABLE viestit; DROP TABLE lahetykset; DROP TABLE liitteet; DROP TABLE metadata_avaimet; DROP TABLE metadata; DROP TABLE flyway_schema_history;"""), 5.seconds)
  }

  @Test def testLahetysRoundtrip(): Unit =
    val lahetysOperaatiot = LahetysOperaatiot(getDatabase())
    val lahetys = lahetysOperaatiot.tallennaLahetys("otsikko", "omistaja")

    Assertions.assertEquals("otsikko", lahetys.otsikko)
    Assertions.assertEquals("omistaja", lahetys.omistaja)
    Assertions.assertEquals(lahetys, lahetysOperaatiot.getLahetys(lahetys.tunniste).get)

  @Test def testLiiteRoundtrip(): Unit =
    val lahetysOperaatiot = LahetysOperaatiot(getDatabase())
    val liite = lahetysOperaatiot.tallennaLiite("testiliite", "application/png", 1024, "omistaja")

    Assertions.assertEquals("testiliite", liite.nimi)
    Assertions.assertEquals("application/png", liite.contentType)
    Assertions.assertEquals(1024, liite.koko)
    Assertions.assertEquals("omistaja", liite.omistaja)
    Assertions.assertEquals(Seq(liite), lahetysOperaatiot.getLiitteet(Seq(liite.tunniste)))


  @Test def testLisaaViesti(): Unit =
    val lahetysOperaatiot = LahetysOperaatiot(getDatabase())

    val viesti = lahetysOperaatiot.tallennaViesti(
      "otsikko",
      "sisältö",
      SisallonTyyppi.TEXT,
      Set(Kieli.FI),
      Option.empty,
      Kontakti("Lasse Lahettaja", "lasse.lahettaja@oph.fi"),
      Seq(
        Kontakti("Vallu Vastaanottaja", "vallu.vastaanottaja@example.com"),
        Kontakti("Virpi Vastaanottaja", "vallu.vastaanottaja@example.com")
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



    val vastaanottajat = lahetysOperaatiot.getLahetettavatVastaanottajat(1)
    val viestit = lahetysOperaatiot.getLahetysData(vastaanottajat)


  @Test def testGetLahetettavatViestit(): Unit =
    val db = getDatabase();
    val VASTAANOTTAJATUNNISTE = UUID.fromString("699eb156-c631-4e84-af0a-657a3ca03406")

    val insertViesti = sqlu"""INSERT INTO viestit VALUES('42ddcdd1-98a0-4202-9ed2-52924379a732', '3fa85f64-5717-4562-b3fc-2c963f66afa6', 'Otsikko', 'Sisältö', 'TEXT', false, false, false, '1.2.3', 'Lasse Lähettäjä', 'lasse.lahettaja@oph.fi', 'palvelu', 'NORMAALI')"""
    Await.result(db.run(insertViesti), 5.seconds)

    val insertVastaanottaja = sqlu"""INSERT INTO vastaanottajat VALUES('#${VASTAANOTTAJATUNNISTE.toString}', '42ddcdd1-98a0-4202-9ed2-52924379a732', 'Vallu Vastaanottaja', 'vallu.vastaanottaja@example.com', 'ODOTTAA', now())"""
    Await.result(db.run(insertVastaanottaja), 5.seconds)

    val lahetysOperaatiot = LahetysOperaatiot(getDatabase())
    val lahetettavat = lahetysOperaatiot.getLahetettavatVastaanottajat(10)

    val vastaanottaja = lahetysOperaatiot.getLahetysData(Seq(VASTAANOTTAJATUNNISTE))._1.find(v => true).get
    Assertions.assertEquals(VastaanottajanTila.LAHETYKSESSA, vastaanottaja.tila)

    // TODO: testaa tilanne jossa ei lähetettäviä viestejä

  @Test def testPaivitaVastaanottajanTila(): Unit =
    val db = getDatabase();

    val insertViesti = sqlu"""INSERT INTO viestit VALUES('42ddcdd1-98a0-4202-9ed2-52924379a732', '3fa85f64-5717-4562-b3fc-2c963f66afa6', 'Otsikko', 'Sisältö', 'TEXT', false, false, false, '1.2.3', 'Lasse Lähettäjä', 'lasse.lahettaja@oph.fi', 'palvelu', 'NORMAALI')"""
    Await.result(db.run(insertViesti), 5.seconds)

    val VASTAANOTTAJATUNNISTE = UUID.randomUUID()
    val insertVastaanottaja = sqlu"""INSERT INTO vastaanottajat VALUES('#${VASTAANOTTAJATUNNISTE.toString}', '42ddcdd1-98a0-4202-9ed2-52924379a732', 'Vallu Vastaanottaja', 'vallu.vastaanottaja@example.com', 'ODOTTAA', now())"""
    Await.result(db.run(insertVastaanottaja), 5.seconds)

    val lahetysOperaatiot = LahetysOperaatiot(getDatabase())
    lahetysOperaatiot.paivitaVastaanottajanTila(VASTAANOTTAJATUNNISTE, VastaanottajanTila.LAHETETTY)

    val vastaanottaja = lahetysOperaatiot.getLahetysData(Seq(VASTAANOTTAJATUNNISTE))._1.find(v => true).get
    Assertions.assertEquals(VastaanottajanTila.LAHETETTY, vastaanottaja.tila)
}
