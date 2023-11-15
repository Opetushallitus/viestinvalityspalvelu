package fi.oph.viestinvalitys.business

import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
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
import java.util.concurrent.{Executor, Executors}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.DurationInt

class OphPostgresContainer(dockerImageName: String) extends PostgreSQLContainer[OphPostgresContainer](dockerImageName) {
}

/**
 * Yksikkötestit viestinvälityspalvelun tietokantakerrokselle.
 *
 * Testit ovat luonteeltaan inkrementaalisia, ts. myöhemmissä testeissä käytetään aikaisemmista testeissä testattuja
 * operaatioita oletuksena että aikaisemmat testit ovat varmistaneet niiden toiminnan oikeellisuuden.
 *
 * Kaikki testit ajetaan tyhjään kantaan johon on ajettu flyway-päivitykset.
 */
@TestInstance(Lifecycle.PER_CLASS)
class LahetysOperaatiotTest {

  implicit val executionContext: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(64))

  val LOG = LoggerFactory.getLogger(classOf[LahetysOperaatiotTest])

  @Container var postgres: OphPostgresContainer = new OphPostgresContainer("postgres:15.4")
    .withDatabaseName("viestinvalitys")
    .withUsername("viestinvalitys")
    .withPassword("viestinvalitys")
    .withLogConsumer(frame => LOG.info(frame.getUtf8StringWithoutLineEnding))

  private def getDatasource() =
    val ds: PGSimpleDataSource = new PGSimpleDataSource()
    ds.setServerNames(Array("localhost"))
    ds.setDatabaseName("viestinvalitys")
    ds.setPortNumbers(Array(postgres.getMappedPort(5432)))
    ds.setUser("viestinvalitys")
    ds.setPassword("viestinvalitys")
    ds

  private def getHikariDatasource() =
    val config = new HikariConfig()
    config.setMaximumPoolSize(64)
    config.setDataSource(getDatasource())
    new HikariDataSource(config)

  var lahetysOperaatiot: LahetysOperaatiot = null
  var database: Database = null
  @BeforeAll def setup(): Unit = {
    postgres.start()
    database = Database.forDataSource(getHikariDatasource(), Option.empty)
    lahetysOperaatiot = LahetysOperaatiot(database)
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
    Await.result(database.run(sqlu"""DROP TABLE vastaanottajat; DROP TABLE viestit_liitteet; DROP TABLE viestit; DROP TABLE lahetykset; DROP TABLE liitteet; DROP TABLE metadata_avaimet; DROP TABLE metadata; DROP TYPE prioriteetti; DROP TABLE flyway_schema_history;"""), 5.seconds)
  }

  @Test def testLahetysRoundtrip(): Unit =
    // tallennetaan lähetys
    val lahetys = lahetysOperaatiot.tallennaLahetys("otsikko", "omistaja")

    // varmistetaan että palautettu entiteetti sisältää mitä pitää
    Assertions.assertEquals("otsikko", lahetys.otsikko)
    Assertions.assertEquals("omistaja", lahetys.omistaja)

    // varmistetaan että luettu entiteetti vastaa tallennettua
    Assertions.assertEquals(lahetys, lahetysOperaatiot.getLahetys(lahetys.tunniste).get)

  @Test def testGetLiitteetEmpty(): Unit =
    // operaatio ei saa räjähtää jos kysytään liitteitä tyhjällä joukolla tunnisteita
    Assertions.assertEquals(Seq.empty, lahetysOperaatiot.getLiitteet(Seq.empty))

  @Test def testLiiteRoundtrip(): Unit =
    // tallennetaan liite
    val liite = lahetysOperaatiot.tallennaLiite("testiliite", "application/png", 1024, "omistaja")

    // varmistetaan että palautettu entiteetti sisältää mitä pitää
    Assertions.assertEquals("testiliite", liite.nimi)
    Assertions.assertEquals("application/png", liite.contentType)
    Assertions.assertEquals(1024, liite.koko)
    Assertions.assertEquals("omistaja", liite.omistaja)
    Assertions.assertEquals(LiitteenTila.SKANNAUS, liite.tila)

    // varmistetaan että luettu entiteetti vastaa tallennettua
    Assertions.assertEquals(Seq(liite), lahetysOperaatiot.getLiitteet(Seq(liite.tunniste)))

  // apumetodi viestien tallennuksen ja lähetyksen priorisoinnin testaamiseen
  private def tallennaViesti(vastaanottajat: Int, prioriteetti: Prioriteetti = Prioriteetti.NORMAALI, lahetysTunniste: UUID = null, liitteet: Seq[Liite] = Seq.empty): (Viesti, Seq[Vastaanottaja]) =
    lahetysOperaatiot.tallennaViesti(
      "otsikko",
      "sisältö",
      SisallonTyyppi.TEXT,
      Set(Kieli.FI),
      Option.empty,
      Kontakti("Lasse Lahettaja", "lasse.lahettaja@oph.fi"),
      Range(0, vastaanottajat).map(suffix => Kontakti("Vastaanottaja" + suffix, "vastaanottaja" + suffix + "@example.com")),
      liitteet.map(liite => liite.tunniste),
      "lahettavapalvelu",
      Option.apply(lahetysTunniste),
      prioriteetti,
      10,
      Set.empty,
      Map("avain" -> "arvo"),
      "omistaja"
    )

  @Test def testGetViestitEmpty(): Unit =
    // operaatio ei saa räjähtää jos kysytään viestejä tyhjällä joukolla tunnisteita
    Assertions.assertEquals(Seq.empty, lahetysOperaatiot.getViestit(Seq.empty))

  @Test def testGetVastaanottajatEmpty(): Unit =
    // operaatio ei saa räjähtää jos kysytään vastaanottajia tyhjällä joukolla tunnisteita
    Assertions.assertEquals(Seq.empty, lahetysOperaatiot.getVastaanottajat(Seq.empty))

  @Test def testGetViestinLiitteetEmpty(): Unit =
    // operaatio ei saa räjähtää jos kysytään liitteitä tyhjällä joukolla tunnisteita
    Assertions.assertEquals(Map.empty, lahetysOperaatiot.getViestinLiitteet(Seq.empty))

  @Test def testViestiRoundtrip(): Unit =
    // tallennetaan viesti
    val liite1 = lahetysOperaatiot.tallennaLiite("testiliite1", "application/png", 1024, "omistaja")
    val liite2 = lahetysOperaatiot.tallennaLiite("testiliite2", "application/png", 1024, "omistaja")
    val (viesti, vastaanottajat) = tallennaViesti(3, liitteet = Seq(liite1, liite2))

    // varmistetaan että luetut entiteetit sisältävät mitä tallennettiin
    Assertions.assertEquals(Lahetys(viesti.lahetys_tunniste, viesti.otsikko, "omistaja"), lahetysOperaatiot.getLahetys(viesti.lahetys_tunniste).get)
    Assertions.assertEquals(vastaanottajat, lahetysOperaatiot.getVastaanottajat(vastaanottajat.map(v => v.tunniste)))
    Assertions.assertEquals(viesti, lahetysOperaatiot.getViestit(Seq(viesti.tunniste)).find(v => true).get)
    Assertions.assertEquals(Seq(liite1, liite2), lahetysOperaatiot.getViestinLiitteet(Seq(viesti.tunniste)).get(viesti.tunniste).get)

  @Test def testGetLahetettavatViestitKoko(): Unit =
    // tallennetaan viestit
    val (viesti1, vastaanottajat1) = tallennaViesti(2)
    val (viesti2, vastaanottajat2) = tallennaViesti(4, Prioriteetti.KORKEA)
    val tallennetutVastaanottajat = vastaanottajat1.concat(vastaanottajat2)

    // haetaan lähetettäväksi viisi vastaanottajaa
    val lahetettavatVastaanottajat = lahetysOperaatiot.getLahetettavatVastaanottajat(5)

    // tuloksena viisi vastaanottajaa
    Assertions.assertEquals(5, lahetettavatVastaanottajat.size)

  @Test def testGetLahetettavatYksiVastaanottajaVainKerran(): Unit =
    // tallennetaan iso joukko vastaanottajia, 200*25=5000
    val tallennetutVastaanottajat = Await.result(Future.foldLeft(Range(0, 200)
      .map(i => Future {
        tallennaViesti(25)._2.map(v => v.tunniste)
      }))(Seq.empty)((l, v) => l.concat(v)), 10.seconds)


    // haetaan sama määrä vastaanottajia, 2500*2=5000
    val haetutVastaanottajat = Await.result(Future.foldLeft(Range(0, 2500)
        .map(i => Future {
          lahetysOperaatiot.getLahetettavatVastaanottajat(2)
        }))(Seq.empty)((s, v) => s.concat(v)), 10.seconds)

    // tarkistetaan että joukot samoja jolloin kaikki vastaanottajat haettu kerran
    Assertions.assertEquals(tallennetutVastaanottajat.toSet, haetutVastaanottajat.toSet)

  @Test def testGetLahetettavatViestitTilamuutos(): Unit =
    // tallennetaan viestit
    val (viesti1, vastaanottajat1) = tallennaViesti(2)
    val (viesti2, vastaanottajat2) = tallennaViesti(4, Prioriteetti.KORKEA)
    val tallennetutVastaanottajat = vastaanottajat1.concat(vastaanottajat2)

    // haetaan lähetettäväksi viisi vastaanottajaa
    val lahetettavatVastaanottajat = lahetysOperaatiot.getLahetettavatVastaanottajat(5)
      .map(t => tallennetutVastaanottajat.find(v => v.tunniste.equals(t)).get)

    // odottavien tila ei muuttunut
    val odottavatVastaanottajat = tallennetutVastaanottajat.filter(v => !lahetettavatVastaanottajat.contains(v))
    Assertions.assertEquals(odottavatVastaanottajat.toSet, lahetysOperaatiot.getVastaanottajat(odottavatVastaanottajat.map(v => v.tunniste)).toSet)

    // lähetettävien tila on lähetyksessä
    Assertions.assertEquals(lahetettavatVastaanottajat.map(v => v.copy(tila = VastaanottajanTila.LAHETYKSESSA)).toSet,
      lahetysOperaatiot.getVastaanottajat(lahetettavatVastaanottajat.map(v => v.tunniste)).toSet)

  @Test def testGetLahetettavatViestitAikaJarjestys(): Unit =
    // tallennetaan viestit
    val (viesti1, vastaanottajat1) = tallennaViesti(2)
    val (viesti2, vastaanottajat2) = tallennaViesti(10)
    val tallennetutVastaanottajat = vastaanottajat1.concat(vastaanottajat2)

    // haetaan lähetettäväksi viisi vastaanottajaa
    val lahetettavatVastaanottajat = lahetysOperaatiot.getLahetettavatVastaanottajat(5)
      .map(t => tallennetutVastaanottajat.find(v => v.tunniste.equals(t)).get)

    // joista 2 ensimmäisen viestin ja 3 toisen
    Assertions.assertEquals(2, vastaanottajat1.intersect(lahetettavatVastaanottajat).size)
    Assertions.assertEquals(3, vastaanottajat2.intersect(lahetettavatVastaanottajat).size)

  @Test def testGetLahetettavatViestitPrioriteettiJarjestys(): Unit =
    // tallennetaan joukko viestejä, korkean prioriteetin viesti ei jonon kärjessä
    val (viesti1, vastaanottajatNormaali1) = tallennaViesti(5)
    val (viesti2, vastaanottajatNormaali2) = tallennaViesti(5)
    val (viesti3, vastaanottajatKorkea) = tallennaViesti(2, prioriteetti = Prioriteetti.KORKEA)
    val tallennetutVastaanottajat = vastaanottajatNormaali1.concat(vastaanottajatNormaali2).concat(vastaanottajatKorkea)

    // haetaan lähetettäväksi neljä vastaanottajaa
    val lahetettavatVastaanottajat = lahetysOperaatiot.getLahetettavatVastaanottajat(4)
      .map(t => tallennetutVastaanottajat.find(v => v.tunniste.equals(t)).get)

    // joista 2 korkean prioriteetit viestin ja 2 ensimmäisen normaalin prioriteetin viestin
    Assertions.assertEquals(2, vastaanottajatKorkea.intersect(lahetettavatVastaanottajat).size)
    Assertions.assertEquals(2, vastaanottajatNormaali1.intersect(lahetettavatVastaanottajat).size)

  @Test def testJosLiiteSkannauksessaEiLaheteta(): Unit =
    // tallennataan viesti jolla kaksi liitettä (jotka menevät SKANNAUS-tilaan)
    val liite1 = lahetysOperaatiot.tallennaLiite("testiliite1", "application/png", 1024, "omistaja")
    val liite2 = lahetysOperaatiot.tallennaLiite("testiliite2", "application/png", 1024, "omistaja")
    val (viesti, vastaanottajat) = tallennaViesti(2, liitteet = Seq(liite1, liite2))

    // vain liite1 skannattu
    lahetysOperaatiot.paivitaLiitteenTila(liite1.tunniste, LiitteenTila.PUHDAS)

    // viestejä ei voida lähettää vastaanottajille koska liite2 ei skannattu
    val lahetettavatVastaanottajat = lahetysOperaatiot.getLahetettavatVastaanottajat(15)
    Assertions.assertEquals(0, lahetettavatVastaanottajat.size)

  @Test def testJosLiitteetSkannattuLahetetaan(): Unit =
    // tallennataan viesti jolla kaksi liitettä (jotka menevät SKANNAUS-tilaan)
    val liite1 = lahetysOperaatiot.tallennaLiite("testiliite1", "application/png", 1024, "omistaja")
    val liite2 = lahetysOperaatiot.tallennaLiite("testiliite2", "application/png", 1024, "omistaja")
    val (viesti, vastaanottajat) = tallennaViesti(2, liitteet = Seq(liite1, liite2))

    // kumpikin liite skannattu
    lahetysOperaatiot.paivitaLiitteenTila(liite1.tunniste, LiitteenTila.PUHDAS)
    lahetysOperaatiot.paivitaLiitteenTila(liite2.tunniste, LiitteenTila.PUHDAS)

    // viestit voidaan lähettää vastaanottajille
    val lahetettavatVastaanottajat = lahetysOperaatiot.getLahetettavatVastaanottajat(15)
    Assertions.assertEquals(vastaanottajat.map(v => v.tunniste), lahetettavatVastaanottajat)

  @Test def testPaivitaVastaanottajanTila(): Unit =
    // tallennetaan viesti
    val (viesti, vastaanottajat) = tallennaViesti(2)
    val vastaanottajanTunniste = vastaanottajat.find(v => true).map(v => v.tunniste).get

    lahetysOperaatiot.paivitaVastaanottajanTila(vastaanottajanTunniste, VastaanottajanTila.LAHETYKSESSA)
    Assertions.assertEquals(VastaanottajanTila.LAHETYKSESSA, lahetysOperaatiot.getVastaanottajat(Seq(vastaanottajanTunniste)).find(v => true).map(v => v.tila).get)

    lahetysOperaatiot.paivitaVastaanottajanTila(vastaanottajanTunniste, VastaanottajanTila.LAHETETTY)
    Assertions.assertEquals(VastaanottajanTila.LAHETETTY, lahetysOperaatiot.getVastaanottajat(Seq(vastaanottajanTunniste)).find(v => true).map(v => v.tila).get)
}
