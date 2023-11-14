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
    Assertions.assertEquals(LiitteenTila.ODOTTAA, liite.tila)
    Assertions.assertEquals(Seq(liite), lahetysOperaatiot.getLiitteet(Seq(liite.tunniste)))

  @Test def testPaivitaLiitteenTila(): Unit =
    val lahetysOperaatiot = LahetysOperaatiot(getDatabase())
    val liite = lahetysOperaatiot.tallennaLiite("testiliite", "application/png", 1024, "omistaja")

    Assertions.assertEquals(LiitteenTila.ODOTTAA, liite.tila)
    lahetysOperaatiot.paivitaLiitteenTila(liite.tunniste, LiitteenTila.SAASTUNUT)
    Assertions.assertEquals(LiitteenTila.SAASTUNUT, lahetysOperaatiot.getLiitteet(Seq(liite.tunniste)).find(l => true).get.tila)

  private def tallennaViesti(lahetysOperaatiot: LahetysOperaatiot, lahetysTunniste: Option[UUID], liitteet: Seq[Liite]): (Viesti, Seq[Vastaanottaja]) =
    lahetysOperaatiot.tallennaViesti(
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
      liitteet.map(liite => liite.tunniste),
      "lahettavapalvelu",
      lahetysTunniste,
      Prioriteetti.NORMAALI,
      10,
      Set.empty,
      Map("avain" -> "arvo"),
      "omistaja"
    )

  @Test def testViestiRoundtrip(): Unit =
    val lahetysOperaatiot = LahetysOperaatiot(getDatabase())

    val liite1 = lahetysOperaatiot.tallennaLiite("testiliite1", "application/png", 1024, "omistaja")
    val liite2 = lahetysOperaatiot.tallennaLiite("testiliite2", "application/png", 1024, "omistaja")
    val (viesti, vastaanottajat) = tallennaViesti(lahetysOperaatiot, Option.empty, Seq(liite1, liite2))

    Assertions.assertEquals(vastaanottajat, lahetysOperaatiot.getVastaanottajat(vastaanottajat.map(v => v.tunniste)))
    Assertions.assertEquals(viesti, lahetysOperaatiot.getViestit(Seq(viesti.tunniste)).find(v => true).get)
    Assertions.assertEquals(Seq(liite1, liite2), lahetysOperaatiot.getViestinLiitteet(Seq(viesti.tunniste)).get(viesti.tunniste).get)

  @Test def testGetLahetettavatViestit(): Unit =
    val lahetysOperaatiot = LahetysOperaatiot(getDatabase())

    val (viesti, vastaanottajat) = tallennaViesti(lahetysOperaatiot, Option.empty, Seq.empty)
    val lahetettavatVastastaanottajat = lahetysOperaatiot.getLahetettavatVastaanottajat(1000)
    Assertions.assertEquals(vastaanottajat.map(v => v.copy(tila = VastaanottajanTila.LAHETYKSESSA)), lahetysOperaatiot.getVastaanottajat(vastaanottajat.map(v => v.tunniste)))

  @Test def testGetLahetysData(): Unit =
    val lahetysOperaatiot = LahetysOperaatiot(getDatabase())

    val liitteet = Seq(
      lahetysOperaatiot.tallennaLiite("testiliite1", "application/png", 1024, "omistaja"),
      lahetysOperaatiot.tallennaLiite("testiliite2", "application/png", 1024, "omistaja")
    )
    val (viesti, vastaanottajat) = tallennaViesti(lahetysOperaatiot, Option.empty, liitteet)

    val (vastaanottajat2, viestit, liitteet2) = lahetysOperaatiot.getLahetysData(vastaanottajat.map(v => v.tunniste))
    Assertions.assertEquals(vastaanottajat, vastaanottajat2)
    Assertions.assertEquals(Seq(viesti.tunniste -> viesti).toMap, viestit)
    Assertions.assertEquals(Seq(viesti.tunniste -> liitteet).toMap, liitteet2)

  @Test def testPaivitaVastaanottajanTila(): Unit =
    val lahetysOperaatiot = LahetysOperaatiot(getDatabase())

    val (viesti, vastaanottajat) = tallennaViesti(lahetysOperaatiot, Option.empty, Seq.empty)
    val vastaanottajanTunniste = vastaanottajat.find(v => true).map(v => v.tunniste).get

    lahetysOperaatiot.paivitaVastaanottajanTila(vastaanottajanTunniste, VastaanottajanTila.LAHETYKSESSA)
    Assertions.assertEquals(VastaanottajanTila.LAHETYKSESSA, lahetysOperaatiot.getVastaanottajat(Seq(vastaanottajanTunniste)).find(v => true).map(v => v.tila).get)

    lahetysOperaatiot.paivitaVastaanottajanTila(vastaanottajanTunniste, VastaanottajanTila.LAHETETTY)
    Assertions.assertEquals(VastaanottajanTila.LAHETETTY, lahetysOperaatiot.getVastaanottajat(Seq(vastaanottajanTunniste)).find(v => true).map(v => v.tila).get)
}
