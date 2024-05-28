package fi.oph.viestinvalitys.business

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.postgresql.ds.PGSimpleDataSource
import org.slf4j.LoggerFactory
import org.testcontainers.containers.PostgreSQLContainer
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.PostgresProfile.api.*

import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Random

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
class KantaOperaatiotTest {

  implicit val executionContext: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(64))

  val LOG = LoggerFactory.getLogger(classOf[KantaOperaatiotTest])

  var postgres: OphPostgresContainer = new OphPostgresContainer("postgres:15.4")
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

  val rand = Random
  var kantaOperaatiot: KantaOperaatiot = null
  var database: Database = null
  @BeforeAll def setup(): Unit = {
    postgres.start()
    database = Database.forDataSource(getHikariDatasource(), Option.empty)
    kantaOperaatiot = KantaOperaatiot(database)
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
    Await.result(database.run(
      sqlu"""
            DROP TABLE RAPORTOINTI_CAS_CLIENT_SESSION;
            DROP TABLE LAHETYS_SESSION_ATTRIBUTES;
            DROP TABLE LAHETYS_SESSION;
            DROP TABLE RAPORTOINTI_SESSION_ATTRIBUTES;
            DROP TABLE RAPORTOINTI_SESSION;
            DROP TABLE vastaanottaja_siirtymat;
            DROP TABLE vastaanottajat;
            DROP TABLE metadata_avaimet;
            DROP TABLE metadata;
            DROP TABLE viestit_liitteet;
            DROP TABLE maskit;
            DROP TABLE viestit_kayttooikeudet;
            DROP TABLE lahetykset_kayttooikeudet;
            DROP TABLE kayttooikeudet;
            DROP TABLE viestit;
            DROP TABLE lahetykset;
            DROP TABLE liitteet;
            DROP TYPE prioriteetti;
            DROP TABLE flyway_schema_history;
          """), 5.seconds)
  }

  // apumetodi lähetyksen tallennuksen yms. testaamiseen
  private def tallennaLahetys(sailytysaika: Int = 10): Lahetys =
    kantaOperaatiot.tallennaLahetys(
      "otsikko",
      "omistaja",
      "lahettavapalvelu",
      Some("0.1.2.3"),
      Kontakti(Some("Lasse Lähettäjä"), "lasse.lahettaja@opintopolku.fi"),
      Option.empty,
      Prioriteetti.NORMAALI,
      sailytysaika
    )

  private val ORGANISAATIO1 = "1.2.246.562.10.16790925842"
  private val ORGANISAATIO2 = "1.2.246.562.10.16790925843"
  private val Oikeus1Organisaatio1 = Kayttooikeus("OIKEUS1", Some(ORGANISAATIO1))
  private val kayttooikeudetOik1org1: Set[Kayttooikeus] = Set(Oikeus1Organisaatio1)

  // apumetodi viestien tallennuksen ja lähetyksen priorisoinnin yms. testaamiseen
  private def tallennaViesti(vastaanottajat: Int, prioriteetti: Prioriteetti = Prioriteetti.NORMAALI,
                             lahetysTunniste: UUID = null, liitteet: Seq[Liite] = Seq.empty, sailytysAika: Int = 10,
                             kayttooikeudet: Set[Kayttooikeus] = Set(Oikeus1Organisaatio1,
                               Kayttooikeus("OIKEUS2", Some(ORGANISAATIO1))),
                             omistaja: String = "omistaja",
                             lahettavaPalvelu: String = "palvelu",
                             maskit: Map[String, Option[String]] = Map("ö" -> Some("*"))): (Viesti, Seq[Vastaanottaja]) =
    kantaOperaatiot.tallennaViesti(
      "otsikko",
      "sisältö",
      SisallonTyyppi.TEXT,
      Set(Kieli.FI),
      maskit,
      Option.empty,
      Some(Kontakti(Some("Lasse Lahettaja"), "lasse.lahettaja@oph.fi")),
      Option.empty,
      Range(0, vastaanottajat).map(suffix => Kontakti(Some("Vastaanottaja" + suffix), "vastaanottaja" + suffix + "@example.com")),
      liitteet.map(liite => liite.tunniste),
      Option.apply(lahettavaPalvelu),
      Option.apply(lahetysTunniste),
      Option.apply(prioriteetti),
      Option.apply(sailytysAika),
      kayttooikeudet,
      Map("avain" -> Seq("arvo")),
      omistaja
    )

  private def tallennaRaataloityViesti(vastaanottajat: Seq[Kontakti], otsikko: String = "otsikko", sisalto: String = "sisältö",
                             lahetysTunniste: UUID = null,
                             kayttoOikeudet: Set[Kayttooikeus] = Set(Oikeus1Organisaatio1,
                               Kayttooikeus("OIKEUS2", Some(ORGANISAATIO1))),
                             omistaja: String = "omistaja",
                             lahettavaPalvelu: String = "palvelu",
                             maskit: Map[String, Option[String]] = Map.empty) =
    kantaOperaatiot.tallennaViesti(
      otsikko,
      sisalto,
      SisallonTyyppi.TEXT,
      Set(Kieli.FI),
      maskit,
      Option.empty,
      Some(Kontakti(Some("Lasse Lahettaja"), "lasse.lahettaja@oph.fi")),
      Option.empty,
      vastaanottajat,
      Seq.empty,
      Some(lahettavaPalvelu),
      Some(lahetysTunniste),
      Some(Prioriteetti.NORMAALI),
      Some(10),
      kayttoOikeudet,
      Map("avain" -> Seq("arvo")),
      omistaja
    )
  /**
   * Testataan lähetyksien tallennus ja luku
   */
  @Test def testLahetysRoundtrip(): Unit =
    // tallennetaan lähetys
    val lahetys = this.tallennaLahetys()

    // varmistetaan että palautettu entiteetti sisältää mitä pitää
    Assertions.assertEquals("otsikko", lahetys.otsikko)
    Assertions.assertEquals("lahettavapalvelu", lahetys.lahettavaPalvelu)
    Assertions.assertEquals("omistaja", lahetys.omistaja)

    // varmistetaan että luettu entiteetti vastaa tallennettua
    Assertions.assertEquals(lahetys, kantaOperaatiot.getLahetys(lahetys.tunniste).get)

  /**
   * Testataan että myös tyhjän joukon lähtyksia voi lukea
   */
  @Test def testGetLiitteetEmpty(): Unit =
    // operaatio ei saa räjähtää jos kysytään liitteitä tyhjällä joukolla tunnisteita
    Assertions.assertEquals(Seq.empty, kantaOperaatiot.getLiitteet(Seq.empty))

  /**
   * Testataan liitteen tallennus ja luku
   */
  @Test def testLiiteRoundtrip(): Unit =
    // tallennetaan liite
    val liite = kantaOperaatiot.tallennaLiite("testiliite", "application/png", 1024, "omistaja")

    // varmistetaan että palautettu entiteetti sisältää mitä pitää
    Assertions.assertEquals("testiliite", liite.nimi)
    Assertions.assertEquals("application/png", liite.contentType)
    Assertions.assertEquals(1024, liite.koko)
    Assertions.assertEquals("omistaja", liite.omistaja)
    Assertions.assertEquals(LiitteenTila.SKANNAUS, liite.tila)

    // varmistetaan että luettu entiteetti vastaa tallennettua
    Assertions.assertEquals(Seq(liite), kantaOperaatiot.getLiitteet(Seq(liite.tunniste)))

  /**
   * Testaa että viimeisin vastaanottan siirtymä on mitä oletettiin
   *
   * @param vastaanottajaTunniste vastaanottajan tunniste
   * @param tila                  siirtymän kohdetila
   * @param lisatiedot            lisätiedot
   */
  private def assertViimeinenSiirtyma(vastaanottajaTunniste: UUID, tila: VastaanottajanTila, lisatiedot: Option[String]): Unit =
    val siirtymat = kantaOperaatiot.getVastaanottajanSiirtymat(vastaanottajaTunniste)
    val viimeinen = siirtymat(0)

    Assertions.assertTrue(Instant.now().isAfter(viimeinen.aika))
    Assertions.assertTrue(Instant.now().minusSeconds(1).isBefore(viimeinen.aika))
    Assertions.assertEquals(tila, viimeinen.tila)
    Assertions.assertEquals(lisatiedot, Option.apply(viimeinen.lisatiedot))


  /**
   * Testataan että myös tyhjän joukon viestejä voi lukea
   */
  @Test def testGetViestitEmpty(): Unit =
    // operaatio ei saa räjähtää jos kysytään viestejä tyhjällä joukolla tunnisteita
    Assertions.assertEquals(Seq.empty, kantaOperaatiot.getViestit(Seq.empty))

  /**
   * Testataan että myös tyhjän joukon vastaanottajia voi lukea
   */
  @Test def testGetVastaanottajatEmpty(): Unit =
    // operaatio ei saa räjähtää jos kysytään vastaanottajia tyhjällä joukolla tunnisteita
    Assertions.assertEquals(Seq.empty, kantaOperaatiot.getVastaanottajat(Seq.empty))

  /**
   * Testataan että myös tyhjän joukon viestien liitteitä voi lukea
   */
  @Test def testGetViestinLiitteetEmpty(): Unit =
    // operaatio ei saa räjähtää jos kysytään liitteitä tyhjällä joukolla tunnisteita
    Assertions.assertEquals(Map.empty, kantaOperaatiot.getViestinLiitteet(Seq.empty))

  /**
   * Testataan viestien tallennus ja luku
   */
  @Test def testViestiRoundtrip(): Unit =
    // tallennetaan viesti
    val liitteet = Range(0, 100).map(i => kantaOperaatiot.tallennaLiite(s"testiliite${i}", "application/png", 1024, "omistaja"))
    val maskit: Map[String, Option[String]] = Map("salaisuus1" -> Some("peitetty1"), "salaisuus2" -> Some("peitetty2"))
    val (viesti, vastaanottajat) = tallennaViesti(3, liitteet = liitteet, maskit = maskit)

    // varmistetaan että luetut entiteetit sisältävät mitä tallennettiin
    // HUOM! liitteiden järjestys on olennainen asia
    val tallennettuLahetys = kantaOperaatiot.getLahetys(viesti.lahetysTunniste).get
    Assertions.assertEquals(viesti.lahetysTunniste, tallennettuLahetys.tunniste)
    Assertions.assertEquals(viesti.otsikko, tallennettuLahetys.otsikko)
    Assertions.assertEquals("omistaja", tallennettuLahetys.omistaja)
    Assertions.assertEquals("palvelu", tallennettuLahetys.lahettavaPalvelu)
    Assertions.assertEquals(vastaanottajat, kantaOperaatiot.getVastaanottajat(vastaanottajat.map(v => v.tunniste)))
    Assertions.assertEquals(viesti, kantaOperaatiot.getViestit(Seq(viesti.tunniste)).find(v => true).get)
    Assertions.assertEquals(liitteet, kantaOperaatiot.getViestinLiitteet(Seq(viesti.tunniste)).get(viesti.tunniste).get)
    Assertions.assertEquals(viesti.maskit, kantaOperaatiot.getViestit(Seq(viesti.tunniste)).find(v => true).get.maskit)

    vastaanottajat.foreach(vastaanottaja => {
      this.assertViimeinenSiirtyma(vastaanottaja.tunniste, VastaanottajanTila.SKANNAUS, Option.empty)
    })

  /**
   * Testataan korkean prioriteetin viestien määrän lukeminen
   */
  @Test def testGetKorkeanPrioriteetinViestienMaaraSince(): Unit =
    // Luodaan korkean prioriteetin viestejä
    tallennaViesti(1, omistaja = "omistaja1", prioriteetti = Prioriteetti.KORKEA)
    tallennaViesti(1, omistaja = "omistaja1", prioriteetti = Prioriteetti.KORKEA)

    // Odotetaan jotta luodut viestit menevät pois aikaikkunasta
    Thread.sleep(1000)

    // Luodaan 2 korkean prioriteetin viestiä jotka aikaikkunan sisällä
    tallennaViesti(1, omistaja = "omistaja1", prioriteetti = Prioriteetti.KORKEA)
    tallennaViesti(3, omistaja = "omistaja1", prioriteetti = Prioriteetti.NORMAALI)
    tallennaViesti(1, omistaja = "omistaja1", prioriteetti = Prioriteetti.KORKEA)

    // Luodaan korkean prioriteetin viestejä toiselle omistajalle
    tallennaViesti(1, omistaja = "omistaja2", prioriteetti = Prioriteetti.KORKEA)

    // Omistaja1:llä kaksi korkean prioriteetin viestiä aikaikkunan sisällä
    Assertions.assertEquals(2, kantaOperaatiot.getKorkeanPrioriteetinViestienMaaraSince("omistaja1", 1))

  /**
   * Testataan viestin käyttöoikeudet
   */
  @Test def testGetViestinKayttooikeudet(): Unit =
    // tallennetaan viestit oikeuksilla (jolloin luodaan lähetys johon oikeudet tallennetaan)
    val (viesti1, vastaanottajat1) = tallennaViesti(1, lahetysTunniste = null,
      kayttooikeudet = kayttooikeudetOik1org1)
    val (viesti2, vastaanottajat2) = tallennaViesti(1, lahetysTunniste = null,
      kayttooikeudet = Set(Kayttooikeus("OIKEUS2", Some(ORGANISAATIO1)), Kayttooikeus("OIKEUS3", Some(ORGANISAATIO1))))

    // luetut käyttöoikeudet vastaavat tallennettuja
    Assertions.assertEquals(
      Seq(viesti1.tunniste -> kayttooikeudetOik1org1,
        viesti2.tunniste -> Set(Kayttooikeus("OIKEUS2", Some(ORGANISAATIO1)), Kayttooikeus("OIKEUS3", Some(ORGANISAATIO1)))).toMap,
      kantaOperaatiot.getViestinKayttooikeudet(Seq(viesti1.tunniste, viesti2.tunniste)))

  /**
   * Testataan viestin yhteydessä luodun lähetyksen käyttöoikeudet
   */
  @Test def testGetViestinLahetyksenKayttooikeudet(): Unit =
    // tallennetaan viestit oikeuksilla (jolloin luodaan lähetys johon oikeudet tallennetaan)
    val (viesti1, vastaanottajat1) = tallennaViesti(1, lahetysTunniste = null,
      kayttooikeudet = Set(Oikeus1Organisaatio1, Kayttooikeus("OIKEUS2", Some(ORGANISAATIO1))))
    val (viesti2, vastaanottajat2) = tallennaViesti(1, lahetysTunniste = null,
      kayttooikeudet = Set(Kayttooikeus("OIKEUS2", Some(ORGANISAATIO1))))

    // luetut käyttöoikeudet vastaavat tallennettuja
    Assertions.assertEquals(
      Seq(viesti1.lahetysTunniste -> Set(Oikeus1Organisaatio1, Kayttooikeus("OIKEUS2", Some(ORGANISAATIO1))),
        viesti2.lahetysTunniste -> Set(Kayttooikeus("OIKEUS2", Some(ORGANISAATIO1)))).toMap,
      kantaOperaatiot.getLahetystenKayttooikeudet(Seq(viesti1.lahetysTunniste, viesti2.lahetysTunniste)))

  /**
   * Testataan aikaisemmin luodun lähetyksen käyttöoikeudet
   */
  @Test def testGetLahetyksenKayttooikeudet(): Unit =
    val lahetys1 = this.tallennaLahetys()
    val lahetys2 = this.tallennaLahetys()
    val lahetys3 = this.tallennaLahetys()

    // tallennetaan viestit oikeuksilla (jolloin luodaan lähetys johon oikeudet tallennetaan)
    tallennaViesti(1, lahetysTunniste = lahetys1.tunniste,
      kayttooikeudet = kayttooikeudetOik1org1)
    tallennaViesti(1, lahetysTunniste = lahetys2.tunniste,
      kayttooikeudet = Set(Oikeus1Organisaatio1, Kayttooikeus("OIKEUS2", Some(ORGANISAATIO1))))
    tallennaViesti(1, lahetysTunniste = lahetys3.tunniste,
    kayttooikeudet = Set(Kayttooikeus("OIKEUS1", Some(ORGANISAATIO2))))


  // luetut käyttöoikeudet vastaavat tallennettuja
    Assertions.assertEquals(
      Seq(lahetys1.tunniste -> kayttooikeudetOik1org1,
        lahetys2.tunniste -> Set(Oikeus1Organisaatio1, Kayttooikeus("OIKEUS2", Some(ORGANISAATIO1)))).toMap,
      kantaOperaatiot.getLahetystenKayttooikeudet(Seq(lahetys1.tunniste, lahetys2.tunniste)))
    Assertions.assertEquals(
      Seq(lahetys3.tunniste -> Set(Kayttooikeus("OIKEUS1", Some(ORGANISAATIO2)))).toMap,
    kantaOperaatiot.getLahetystenKayttooikeudet(Seq(lahetys3.tunniste)))
    // lisätään uusi viesti uudella oikeudella
    tallennaViesti(1, lahetysTunniste = lahetys2.tunniste,
      kayttooikeudet = Set(Kayttooikeus("OIKEUS3", Some(ORGANISAATIO2))))

    // jolloin uusi oikeus tulee lähetykselle
    Assertions.assertEquals(
      Seq(lahetys2.tunniste -> Set(
        Kayttooikeus("OIKEUS1", Some(ORGANISAATIO1)),
        Kayttooikeus("OIKEUS2", Some(ORGANISAATIO1)),
        Kayttooikeus("OIKEUS3", Some(ORGANISAATIO2)))).toMap,
      kantaOperaatiot.getLahetystenKayttooikeudet(Seq(lahetys2.tunniste)))

  /**
   * Testataan lähetyksen haku käyttöoikeusrajauksilla
   */
  @Test def testGetLahetysKayttooikeusrajauksilla(): Unit =
    val lahetys1 = this.tallennaLahetys()

    // tallennetaan viestit oikeuksilla (jolloin lähetyksen oikeudet tallennetaan)
    tallennaViesti(1, lahetysTunniste = lahetys1.tunniste,
      kayttooikeudet = kayttooikeudetOik1org1)

    Assertions.assertEquals(
    Seq(lahetys1.tunniste -> kayttooikeudetOik1org1).toMap,
    kantaOperaatiot.getLahetystenKayttooikeudet(Seq(lahetys1.tunniste)))
    Assertions.assertEquals(kantaOperaatiot.getLahetysKayttooikeusrajauksilla(
      lahetys1.tunniste, kayttooikeudetOik1org1).get.tunniste,lahetys1.tunniste)
    Assertions.assertEquals(kantaOperaatiot.getLahetysKayttooikeusrajauksilla(
      lahetys1.tunniste, Set(Kayttooikeus("OIKEUS2", Some(ORGANISAATIO1)))), Option.empty)
    Assertions.assertEquals(kantaOperaatiot.getLahetysKayttooikeusrajauksilla(
      lahetys1.tunniste, Set(Oikeus1Organisaatio1, Kayttooikeus("OIKEUS2", Some(ORGANISAATIO1)))).get.tunniste, lahetys1.tunniste)


  /**
   * Testataan että viestiin voi liittää erikseen luodun lähetykset
   */
  @Test def testViestiOlemassaOlevaLahetys(): Unit =
    // luodaan uusi lähetys ja viesti tähän lähetykseen
    val lahetys = this.tallennaLahetys()
    val (viesti, vastaanottajat) = tallennaViesti(3, lahetysTunniste = lahetys.tunniste)

    // kannasta luetun viestin lähetystunniste täsmää
    Assertions.assertEquals(lahetys.tunniste, kantaOperaatiot.getViestit(Seq(viesti.tunniste)).find(v => true).get.lahetysTunniste)

  /**
   * Testataan että [[KantaOperaatiot.getLahetettavatVastaanottajat()]] palauttaa halutun määrän vastaanottajia
   */
  @Test def testGetLahetettavatViestitMaara(): Unit =
    // tallennetaan viestit
    val (viesti1, vastaanottajat1) = tallennaViesti(2)
    val (viesti2, vastaanottajat2) = tallennaViesti(4, Prioriteetti.KORKEA)
    val tallennetutVastaanottajat = vastaanottajat1.concat(vastaanottajat2)

    // haetaan lähetettäväksi viisi vastaanottajaa
    val lahetettavatVastaanottajat = kantaOperaatiot.getLahetettavatVastaanottajat(5)

    // tuloksena viisi vastaanottajaa
    Assertions.assertEquals(5, lahetettavatVastaanottajat.size)

  /**
   * Testataan että [[KantaOperaatiot.getLahetettavatVastaanottajat()]] palauttaa saman vastaanottajan vain kerran
   */
  @Test def testGetLahetettavatYksiVastaanottajaVainKerran(): Unit =
    // tallennetaan iso joukko vastaanottajia rinnakkaisesti, 200*25=5000
    val tallennetutVastaanottajat: Seq[UUID] = Await.result(Future.sequence(Range(0, 200).map(i => Future {
        tallennaViesti(25)._2.map(v => v.tunniste)
      })), 10.seconds).flatten

    // haetaan sama määrä vastaanottajia rinnakkaisesti, 2500*2=5000
    val haetutVastaanottajat = Await.result(Future.sequence(Range(0, 2500).map(i => Future {
        kantaOperaatiot.getLahetettavatVastaanottajat(2)
      })), 10.seconds).flatten

    // joukot samoja jolloin kaikki vastaanottajat haettu kerran
    Assertions.assertEquals(tallennetutVastaanottajat.toSet, haetutVastaanottajat.toSet)

  /**
   * Testataan että [[KantaOperaatiot.getLahetettavatVastaanottajat()]] muuttaa palauttamansa vastaanottajat tilaan
   * [[VastaanottajanTila.LAHETYKSESSA]]
   */
  @Test def testGetLahetettavatViestitTilamuutos(): Unit =
    // tallennetaan viestit
    val (viesti1, vastaanottajat1) = tallennaViesti(2)
    val (viesti2, vastaanottajat2) = tallennaViesti(4, Prioriteetti.KORKEA)
    val tallennetutVastaanottajat = vastaanottajat1.concat(vastaanottajat2)

    // haetaan lähetettäväksi viisi vastaanottajaa
    val lahetettavatVastaanottajat = kantaOperaatiot.getLahetettavatVastaanottajat(5)
      .map(t => tallennetutVastaanottajat.find(v => v.tunniste.equals(t)).get)

    // odottavien tila ei muuttunut
    val odottavatVastaanottajat = tallennetutVastaanottajat.filter(v => !lahetettavatVastaanottajat.contains(v))
    Assertions.assertEquals(odottavatVastaanottajat.toSet, kantaOperaatiot.getVastaanottajat(odottavatVastaanottajat.map(v => v.tunniste)).toSet)

    // lähetettävien tila on lähetyksessä
    Assertions.assertEquals(lahetettavatVastaanottajat.map(v => v.copy(tila = VastaanottajanTila.LAHETYKSESSA)).toSet,
      kantaOperaatiot.getVastaanottajat(lahetettavatVastaanottajat.map(v => v.tunniste)).toSet)

  /**
   * Testataan että [[KantaOperaatiot.getLahetettavatVastaanottajat()]] lisää palauttamillensa vastaanottajille
   * tilasiirtymän tilaan [[VastaanottajanTila.LAHETYKSESSA]]
   */
  @Test def testGetLahetettavatViestitTilasiirtymä(): Unit =
    // tallennetaan viestit
    val (viesti1, vastaanottajat) = tallennaViesti(5)

    // haetaan lähetettäväksi kaksi vastaanottajaa
    val lahetettavatVastaanottajat = kantaOperaatiot.getLahetettavatVastaanottajat(2)
      .map(t => vastaanottajat.find(v => v.tunniste.equals(t)).get)

    // yhä odottaville ei tullut siirtymää
    val odottavatVastaanottajat = vastaanottajat.filter(v => !lahetettavatVastaanottajat.contains(v))
    odottavatVastaanottajat.foreach(vastaanottaja => {
      this.assertViimeinenSiirtyma(vastaanottaja.tunniste, VastaanottajanTila.ODOTTAA, Option.empty)
    })

    // lähetettäville tullut tilasiirtymä
    lahetettavatVastaanottajat.foreach(vastaanottaja => {
      this.assertViimeinenSiirtyma(vastaanottaja.tunniste, VastaanottajanTila.LAHETYKSESSA, Option.empty)
    })

  /**
   * Testataan että [[KantaOperaatiot.getLahetettavatVastaanottajat()]] palauttaa (saman prioriteetin) vastaanottajia
   * siinä järjestyksessä kun ne on luotu
   */
  @Test def testGetLahetettavatViestitAikaJarjestys(): Unit =
    // tallennetaan viestit
    val (viesti1, vastaanottajat1) = tallennaViesti(2)
    val (viesti2, vastaanottajat2) = tallennaViesti(10)
    val tallennetutVastaanottajat = vastaanottajat1.concat(vastaanottajat2)

    // haetaan lähetettäväksi viisi vastaanottajaa
    val lahetettavatVastaanottajat = kantaOperaatiot.getLahetettavatVastaanottajat(5)
      .map(t => tallennetutVastaanottajat.find(v => v.tunniste.equals(t)).get)

    // joista 2 ensimmäisen viestin ja 3 toisen
    Assertions.assertEquals(2, vastaanottajat1.intersect(lahetettavatVastaanottajat).size)
    Assertions.assertEquals(3, vastaanottajat2.intersect(lahetettavatVastaanottajat).size)

  /**
   * Testataan että [[KantaOperaatiot.getLahetettavatVastaanottajat()]] palauttaa ensin korkean prioriteetin viestien
   * vastaanottajat
   */
  @Test def testGetLahetettavatViestitPrioriteettiJarjestys(): Unit =
    // tallennetaan joukko viestejä, korkean prioriteetin viesti ei jonon kärjessä
    val (viesti1, vastaanottajatNormaali1) = tallennaViesti(5)
    val (viesti2, vastaanottajatNormaali2) = tallennaViesti(5)
    val (viesti3, vastaanottajatKorkea) = tallennaViesti(2, prioriteetti = Prioriteetti.KORKEA)
    val tallennetutVastaanottajat = vastaanottajatNormaali1.concat(vastaanottajatNormaali2).concat(vastaanottajatKorkea)

    // haetaan lähetettäväksi neljä vastaanottajaa
    val lahetettavatVastaanottajat = kantaOperaatiot.getLahetettavatVastaanottajat(4)
      .map(t => tallennetutVastaanottajat.find(v => v.tunniste.equals(t)).get)

    // joista 2 korkean prioriteetit viestin ja 2 ensimmäisen normaalin prioriteetin viestin
    Assertions.assertEquals(2, vastaanottajatKorkea.intersect(lahetettavatVastaanottajat).size)
    Assertions.assertEquals(2, vastaanottajatNormaali1.intersect(lahetettavatVastaanottajat).size)

  /**
   * Testataan että [[KantaOperaatiot.getLahetettavatVastaanottajat()]] ei palauta niiden viestien vastaaanottajia
   * joiden jokin liite ei ole tilassa [[LiitteenTila.PUHDAS]]
   */
  @Test def testJosLiiteSkannauksessaEiLaheteta(): Unit =
    // tallennataan viesti jolla kaksi liitettä (jotka menevät SKANNAUS-tilaan)
    val liite1 = kantaOperaatiot.tallennaLiite("testiliite1", "application/png", 1024, "omistaja")
    val liite2 = kantaOperaatiot.tallennaLiite("testiliite2", "application/png", 1024, "omistaja")
    val (viesti, vastaanottajat) = tallennaViesti(2, liitteet = Seq(liite1, liite2))

    // vain liite1 skannattu
    kantaOperaatiot.paivitaLiitteenTila(liite1.tunniste, LiitteenTila.PUHDAS)

    // viestejä ei voida lähettää vastaanottajille koska liite2 ei skannattu
    val lahetettavatVastaanottajat = kantaOperaatiot.getLahetettavatVastaanottajat(15)
    Assertions.assertEquals(0, lahetettavatVastaanottajat.size)

  /**
   * Testataan että [[KantaOperaatiot.getLahetettavatVastaanottajat()]] palauttaa niiden viestien vastaanottajat joiden
   * liitteen ovat siirtyneet tilaan [[LiitteenTila.PUHDAS]]
   */
  @Test def testJosLiitteetSkannattuLahetetaan(): Unit =
    // tallennataan viesti jolla kaksi liitettä (jotka menevät SKANNAUS-tilaan)
    val liite1 = kantaOperaatiot.tallennaLiite("testiliite1", "application/png", 1024, "omistaja")
    val liite2 = kantaOperaatiot.tallennaLiite("testiliite2", "application/png", 1024, "omistaja")
    val (viesti, vastaanottajat) = tallennaViesti(2, liitteet = Seq(liite1, liite2))

    // kumpikin liite skannattu
    kantaOperaatiot.paivitaLiitteenTila(liite1.tunniste, LiitteenTila.PUHDAS)
    kantaOperaatiot.paivitaLiitteenTila(liite2.tunniste, LiitteenTila.PUHDAS)

    // viestit voidaan lähettää vastaanottajille
    val lahetettavatVastaanottajat = kantaOperaatiot.getLahetettavatVastaanottajat(15)
    Assertions.assertEquals(vastaanottajat.map(v => v.tunniste), lahetettavatVastaanottajat)

  /**
   * Testataan että [[KantaOperaatiot.getLahetettavatVastaanottajat()]] palauttaa kaikkien niiden viestien
   * vastaanottajat joiden liitteen ovat siirtyneet tilaan [[LiitteenTila.PUHDAS]], vaikka skannaus tapahtuu
   * rinnakkaisesti samaan aikaan kun liitteitä liitetään uusiin viesteihin.
   *
   * Katso [[KantaOperaatiot.tallennaViesti()]] ja [[KantaOperaatiot.paivitaLiitteenTila()]] -metodien kommentit
   * siitä miksi tämä ei ole triviaalia
   */
  @Test def testKaikkiJoidenLiitteetSkannattuLahetetaan(): Unit =
    // luodaan joukko liitteitä
    val liitteet = Range(0, 1000).map(i => kantaOperaatiot.tallennaLiite(s"testiliite${i}", "application/png", 1024, "omistaja")).toVector

    // merkitään liitteitä skannatuksi ja luodaan liitteitä sisältäviä viestejä lomittain,
    // palautetaan luotujen viestien vastaanottajien tunnisteet
    val skannausOperaatiot: Seq[() => Seq[UUID]] = liitteet.map(liite => () => {
      kantaOperaatiot.paivitaLiitteenTila(liite.tunniste, LiitteenTila.PUHDAS)
      Seq.empty
    })
    val viestinLuontiOperaatiot: Seq[() => Seq[UUID]] = Range(0, 500).map(i => () => {
      val viestinLiitteet = Range(0, 15).map(i => liitteet(rand.nextInt(liitteet.size))).toSet.toSeq
      tallennaViesti(3, liitteet = viestinLiitteet)._2.map(v => v.tunniste)
    })
    val lomitetutOperaatiot = Random.shuffle(skannausOperaatiot.concat(viestinLuontiOperaatiot)).map(op => Future { op() })
    val tallennetutVastaanottajat = Await.result(Future.sequence(lomitetutOperaatiot), 20.seconds).flatten

    // kaikki vastaanottajat ovat lähetysvalmiita kun kaikki liitteet on skannattu
    Assertions.assertEquals(tallennetutVastaanottajat.toSet, kantaOperaatiot.getLahetettavatVastaanottajat(10000).toSet)

  /**
   * Testataan että vastaanottajan tila päivittyy
   */
  @Test def testPaivitaVastaanottajanTilaLahetetyksi(): Unit =
    // tallennetaan viesti
    val (viesti, vastaanottajat) = tallennaViesti(2)
    val vastaanottajanTunniste = vastaanottajat.find(v => true).map(v => v.tunniste).get

    // vastaanottaja odottaa-tilassa
    Assertions.assertEquals(VastaanottajanTila.ODOTTAA, kantaOperaatiot.getVastaanottajat(Seq(vastaanottajanTunniste)).find(v => true).map(v => v.tila).get)

    // päivitetään vastaanottajan tila lähetetyksi
    kantaOperaatiot.paivitaVastaanottajaLahetetyksi(vastaanottajanTunniste, "ses-tunniste")

    // katsotaan että a) tila lähetetty, b) ses-tunniste oletettu, ja c) tilasiirtymä tallentuu
    Assertions.assertEquals(VastaanottajanTila.LAHETETTY, kantaOperaatiot.getVastaanottajat(Seq(vastaanottajanTunniste)).find(v => true).map(v => v.tila).get)
    Assertions.assertEquals("ses-tunniste", kantaOperaatiot.getVastaanottajat(Seq(vastaanottajanTunniste)).find(v => true).map(v => v.sesTunniste.get).get)
    this.assertViimeinenSiirtyma(vastaanottajanTunniste, VastaanottajanTila.LAHETETTY, Option.empty)

  /**
   * Testataan että vastaanottajan tila päivittyy oikein virhetilaan
   */
  @Test def testPaivitaVastaanottajanTilaVirhetilaan(): Unit =
    // tallennetaan viesti
    val (viesti, vastaanottajat) = tallennaViesti(2)
    val vastaanottajanTunniste = vastaanottajat.find(v => true).map(v => v.tunniste).get

    // vastaanottaja odottaa-tilassa
    Assertions.assertEquals(VastaanottajanTila.ODOTTAA, kantaOperaatiot.getVastaanottajat(Seq(vastaanottajanTunniste)).find(v => true).map(v => v.tila).get)

    // päivitetään vastaanottajan tila virhetilaan
    kantaOperaatiot.paivitaVastaanottajaVirhetilaan(vastaanottajanTunniste, "lisätiedot")

    // katsotaan että a) vastaanottaja virhetilassa, b) siirtymä lisätietoineen tallentunut
    Assertions.assertEquals(VastaanottajanTila.VIRHE, kantaOperaatiot.getVastaanottajat(Seq(vastaanottajanTunniste)).find(v => true).map(v => v.tila).get)
    this.assertViimeinenSiirtyma(vastaanottajanTunniste, VastaanottajanTila.VIRHE, Some("lisätiedot"))

  /**
   * Testataan että vastaanottajan tila päivittyy
   */
  @Test def testPaivitaVastaanotonTila(): Unit =
    // tallennetaan viesti
    val (viesti, vastaanottajat) = tallennaViesti(2)
    val vastaanottajanTunniste = vastaanottajat.find(v => true).map(v => v.tunniste).get

    // päivitetään vastaanottajan tila lähetetty-tilaan
    kantaOperaatiot.paivitaVastaanottajaLahetetyksi(vastaanottajanTunniste, "ses-tunniste")

    // katsotaan että uusi päivitys menee läpi ja tilasiirtymä tallentuu
    kantaOperaatiot.paivitaVastaanotonTila("ses-tunniste", VastaanottajanTila.BOUNCE, Some("mailbox full"))
    Assertions.assertEquals(VastaanottajanTila.BOUNCE, kantaOperaatiot.getVastaanottajat(Seq(vastaanottajanTunniste)).find(v => true).map(v => v.tila).get)
    this.assertViimeinenSiirtyma(vastaanottajanTunniste, VastaanottajanTila.BOUNCE, Some("mailbox full"))

  /**
   * Testataan että vanhojen lähetysten (ja sitä kautta viestien yms.) siivous toimii
   */
  @Test def testPoistaPoistettavatLahetykset(): Unit =
    val lahetys1 = this.tallennaLahetys(sailytysaika = 0)
    val lahetys2 = this.tallennaLahetys(sailytysaika = 1)

    // tallennetaan viestit eri tallennusajoilla (viesti1 0pv, viesti2 1pv)
    val liite = kantaOperaatiot.tallennaLiite("testiliite", "application/png", 1024, "omistaja")
    val (viesti1, vastaanottajat1) = tallennaViesti(1, lahetysTunniste = lahetys1.tunniste, liitteet = Seq(liite))
    val (viesti2, vastaanottajat2) = tallennaViesti(1, lahetysTunniste = lahetys2.tunniste, liitteet = Seq(liite))

    // poistetaan viestit jotka määritelty poistetaviksi
    kantaOperaatiot.poistaPoistettavatLahetykset()

    // viesti1:n liitelinkitys, vastaanottaja, tilasiirtymät ja itse viesti poistuneet
    Assertions.assertEquals(None, kantaOperaatiot.getLahetys(lahetys1.tunniste))
    Assertions.assertEquals(Seq.empty, kantaOperaatiot.getViestit(Seq(viesti1.tunniste)))
    Assertions.assertEquals(Seq.empty, kantaOperaatiot.getVastaanottajat(vastaanottajat1.map(v => v.tunniste)))
    Assertions.assertEquals(Map.empty, kantaOperaatiot.getViestinLiitteet(Seq(viesti1.tunniste)))
    vastaanottajat1.foreach(vastaanottaja => Assertions.assertEquals(Seq.empty, kantaOperaatiot.getVastaanottajanSiirtymat(vastaanottaja.tunniste)))

    // viesti2:n liitelinkitys, vastaanottaja, tilasiirtymät ja itse viesti jäljellä
    Assertions.assertEquals(Some(lahetys2), kantaOperaatiot.getLahetys(lahetys2.tunniste))

    Assertions.assertEquals(Seq(viesti2), kantaOperaatiot.getViestit(Seq(viesti2.tunniste)))
    Assertions.assertEquals(vastaanottajat2, kantaOperaatiot.getVastaanottajat(vastaanottajat2.map(v => v.tunniste)))
    Assertions.assertEquals(Seq(viesti2.tunniste -> Seq(liite)).toMap, kantaOperaatiot.getViestinLiitteet(Seq(viesti1.tunniste, viesti2.tunniste)))
    vastaanottajat2.foreach(vastaanottaja => this.assertViimeinenSiirtyma(vastaanottaja.tunniste, VastaanottajanTila.SKANNAUS, Option.empty))

  /**
   * Testataan että vanhojen liitteiden siivous toimii
   */
  @Test def testPoistaPoistettavatLiitteet(): Unit =
    val liite1 = kantaOperaatiot.tallennaLiite("testiliite1", "application/png", 1024, "omistaja")
    val liite2 = kantaOperaatiot.tallennaLiite("testiliite2", "application/png", 1024, "omistaja")
    val (viesti, vastaanottajat) = tallennaViesti(1, sailytysAika = 0, liitteet = Seq(liite1))

    // poistetaan liitteet jotka luotu ennen nykyhetkeä ja joilla ei linkityksiä
    Assertions.assertEquals(Seq(liite2.tunniste), kantaOperaatiot.poistaPoistettavatLiitteet(Instant.now))

    // liite1 edelleen olemassa (koska linkitetty viestiin), liite2 poistettu (koska ei linkityksiä)
    Assertions.assertEquals(Seq(liite1), kantaOperaatiot.getLiitteet(Seq(liite1.tunniste, liite2.tunniste)))

    // poistetaan viesti ja siihen liittyvät liitelinkitykset, sekä uudestaan turhat liitteet
    kantaOperaatiot.poistaPoistettavatLahetykset()
    kantaOperaatiot.poistaPoistettavatLiitteet(Instant.now)

    // myös liite1 poistunut
    Assertions.assertEquals(Seq.empty, kantaOperaatiot.getLiitteet(Seq(liite1.tunniste, liite2.tunniste)))

/* Raportointikälin hakutoiminnot */

  @Test def testLahetyksenKayttooikeusIlmanOrganisaatiota(): Unit =
    val lahetys1 = this.tallennaLahetys()
    tallennaViesti(1, lahetysTunniste = lahetys1.tunniste,
      kayttooikeudet = Set(Kayttooikeus("OIKEUS1", None)))
    Assertions.assertEquals(1, kantaOperaatiot.getLahetykset(Option.empty, Option.empty, Set(Kayttooikeus("OIKEUS1", Some(ORGANISAATIO1)), Kayttooikeus("OIKEUS1", None))).size)


  /**
   * Testataan lähetyksien haku raportointikäyttöliittymälle
   */
  @Test def testGetLahetykset(): Unit =
    val lahetys1 = this.tallennaLahetys()
    val lahetys2 = this.tallennaLahetys()
    val lahetys3 = this.tallennaLahetys()
    val lahetys4 = this.tallennaLahetys()

    // tallennetaan viestit oikeuksilla (jolloin lähetyksen oikeudet tallennetaan)
    tallennaViesti(1, lahetysTunniste = lahetys1.tunniste,
      kayttooikeudet = kayttooikeudetOik1org1)
    tallennaViesti(1, lahetysTunniste = lahetys2.tunniste,
      kayttooikeudet = Set(Kayttooikeus("OIKEUS2", Some(ORGANISAATIO1))))
    tallennaViesti(1, lahetysTunniste = lahetys3.tunniste,
      kayttooikeudet = Set(Oikeus1Organisaatio1, Kayttooikeus("OIKEUS2", Some(ORGANISAATIO1))))
    tallennaViesti(1, lahetysTunniste = lahetys4.tunniste,
      kayttooikeudet = Set(Kayttooikeus("OIKEUS1", Some(ORGANISAATIO2))))
    tallennaViesti(1, lahetysTunniste = lahetys2.tunniste,
      kayttooikeudet = Set(Kayttooikeus("OIKEUS2", None)))

    // käyttöoikeusrajaukset
    Assertions.assertEquals(2, kantaOperaatiot.getLahetykset(Option.empty, Option.empty, kayttooikeudetOik1org1).size)
    Assertions.assertEquals(2, kantaOperaatiot.getLahetykset(Option.empty, Option.empty, Set(Kayttooikeus("OIKEUS2", Some(ORGANISAATIO1)))).size)
    Assertions.assertEquals(3, kantaOperaatiot.getLahetykset(Option.empty, Option.empty,
      Set(Oikeus1Organisaatio1, Kayttooikeus("OIKEUS2", Some(ORGANISAATIO1)))).size)
    Assertions.assertEquals(1, kantaOperaatiot.getLahetykset(Option.empty, Option.empty,
      Set(Kayttooikeus("OIKEUS1", Some(ORGANISAATIO2)))).size)
    Assertions.assertEquals(3, kantaOperaatiot.getLahetykset(Option.empty, Option.empty,
      Set(Oikeus1Organisaatio1, Kayttooikeus("OIKEUS1", Some(ORGANISAATIO2)))).size)
    Assertions.assertEquals(5, kantaOperaatiot.getLahetykset(Option.empty, Option.empty,
      Set(Kayttooikeus("VIESTINVALITYS_OPH_PAAKAYTTAJA", Option.empty))).size)
    // limit & sort desc
    val lahetykset = kantaOperaatiot.getLahetykset(Option.empty, Some(2), Set(Oikeus1Organisaatio1, Kayttooikeus("OIKEUS1", Some(ORGANISAATIO2))))
    Assertions.assertEquals(2, lahetykset.size)
    Assertions.assertEquals(lahetykset.head.luotu, lahetys4.luotu)
    Assertions.assertEquals(lahetykset.last.tunniste, lahetys3.tunniste)
    val lahetyksetSivutus = kantaOperaatiot.getLahetykset(Option.apply(lahetykset.last.luotu), Some(2),
      Set(Oikeus1Organisaatio1, Kayttooikeus("OIKEUS1", Some(ORGANISAATIO2))))
    Assertions.assertEquals(1, lahetyksetSivutus.size)
    Assertions.assertEquals(lahetyksetSivutus.head.luotu, lahetys1.luotu)
    // haku vastaanottajan spostilla
    tallennaRaataloityViesti(Seq.apply(Kontakti(Some("Testi vastaanottaja"), "testi.vastaanottaja@example.org")), lahetysTunniste = lahetys4.tunniste, kayttoOikeudet = kayttooikeudetOik1org1)
    Assertions.assertEquals(1, kantaOperaatiot.getLahetykset(Option.empty, Option.empty, kayttooikeudetOik1org1, "testi.vastaanottaja@example.org").size)
    Assertions.assertEquals(0, kantaOperaatiot.getLahetykset(Option.empty, Option.empty, Set(Kayttooikeus("OIKEUS2", Some(ORGANISAATIO1))), "testi.vastaanottaja@example.org").size)
    Assertions.assertEquals(0, kantaOperaatiot.getLahetykset(Option.empty, Option.empty, kayttooikeudetOik1org1, "test.vastaanottaja@example.org").size)

  /**
   * Testataan lähetyksien vastaanottotilojen haku raportointikäyttöliittymälle
   */
  @Test def testGetLahetystenVastaanottotilat(): Unit =
    // tallennetaan lähetys
    val lahetys1 = this.tallennaLahetys()
    // vastaanottotilat ilman vastaanottajia on tyhjä
    Assertions.assertEquals(Map.empty, kantaOperaatiot.getLahetystenVastaanottotilat(Seq(lahetys1.tunniste), kayttooikeudetOik1org1))
    // tallennetaan viesti
    val (viesti, vastaanottajat) = tallennaViesti(5, lahetysTunniste = lahetys1.tunniste,
      kayttooikeudet = kayttooikeudetOik1org1)

    val vastaanottajanTunniste = vastaanottajat.head.tunniste
    val toisenVastaanottajanTunniste = vastaanottajat.last.tunniste

    // päivitetään yhden vastaanottajan tila lähetetyksi
    kantaOperaatiot.paivitaVastaanottajaLahetetyksi(vastaanottajanTunniste, "ses-tunniste")
    // päivitetään toisen vastaanottajan tila virhetilaan
    kantaOperaatiot.paivitaVastaanottajaVirhetilaan(toisenVastaanottajanTunniste, "lisätiedot")

    val vastaanottotilat = kantaOperaatiot.getLahetystenVastaanottotilat(Seq(lahetys1.tunniste), kayttooikeudetOik1org1)
      .get(lahetys1.tunniste).get
    // tiloista löytyy kesken, lähetetty, ja virhe
    Assertions.assertEquals(3, vastaanottotilat.size)
    Assertions.assertEquals(1, vastaanottotilat.filter(tila => tila._1.equals(VastaanottajanTila.LAHETETTY.toString)).head._2)
    Assertions.assertEquals(1, vastaanottotilat.filter(tila => tila._1.equals(VastaanottajanTila.VIRHE.toString)).head._2)
    Assertions.assertEquals(3, vastaanottotilat.filter(tila => tila._1.equals(VastaanottajanTila.ODOTTAA.toString)).head._2)
    Assertions.assertEquals(Map.empty, kantaOperaatiot.getLahetystenVastaanottotilat(Seq(lahetys1.tunniste), Set(Kayttooikeus("OIKEUS2", Some(ORGANISAATIO1)))))

  /**
   * Testataan lähetyksien maskien haku raportointikäyttöliittymälle
   */
  @Test def testGetLahetystenMaskit(): Unit =
    // tallennetaan lähetys
    val lahetys1 = this.tallennaLahetys()
    // maskit ilman lähetyksen viestiä on tyhjä
    Assertions.assertEquals(Map.empty, kantaOperaatiot.getLahetystenMaskit(Seq(lahetys1.tunniste), kayttooikeudetOik1org1))
    val maskit: Map[String, Option[String]] = Map("salaisuus1" -> Some("peitetty1"), "salaisuus2" -> Some("peitetty2"))
    val maskit2: Map[String, Option[String]] = Map("salaisuus1" -> Some("peitettyx"), "salaisuus3" -> Some("peitetty3"))
    val maskit3: Map[String, Option[String]] = Map("salaisuus" -> Some("peitetty"))
    // tallennetaan viestejä eri maskeilla
    tallennaViesti(5, lahetysTunniste = lahetys1.tunniste,
      kayttooikeudet = kayttooikeudetOik1org1, maskit = maskit)
    tallennaViesti(2, lahetysTunniste = lahetys1.tunniste,
    kayttooikeudet = kayttooikeudetOik1org1, maskit = maskit2)
    val lahetys2 = this.tallennaLahetys()
    tallennaViesti(1, lahetysTunniste = lahetys2.tunniste,
      kayttooikeudet = kayttooikeudetOik1org1, maskit = maskit3)
    // tallennetaan viesti ilman maskeja
    val lahetys3 = this.tallennaLahetys()
    tallennaViesti(1, lahetysTunniste = lahetys3.tunniste,
      kayttooikeudet = Set(Kayttooikeus("OIKEUS2", Some(ORGANISAATIO1))), maskit=Map.empty)

    val lahetystenMaskit = kantaOperaatiot.getLahetystenMaskit(Seq(lahetys1.tunniste, lahetys2.tunniste, lahetys3.tunniste), kayttooikeudetOik1org1)
    // tiloista löytyy maskit tai ei löydy tunnistetta
    Assertions.assertEquals(2, lahetystenMaskit.size)
    Assertions.assertEquals(3, lahetystenMaskit.get(lahetys1.tunniste).get.size)
    Assertions.assertEquals(Map("salaisuus1" -> Some("peitettyx"), "salaisuus2" -> Some("peitetty2"), "salaisuus3" -> Some("peitetty3")), lahetystenMaskit.get(lahetys1.tunniste).get)
    Assertions.assertEquals(1, lahetystenMaskit.get(lahetys2.tunniste).get.size)
    Assertions.assertEquals(Map("salaisuus" -> Some("peitetty")), lahetystenMaskit.get(lahetys2.tunniste).get)
    Assertions.assertEquals(None, lahetystenMaskit.get(lahetys3.tunniste))

  /**
   * Testataan lähetyksen viestien lukumäärän haku
   */
  @Test def testGetLahetyksenViestiLkm(): Unit =
    // lähetys jossa yksi viesti
    val lahetys = this.tallennaLahetys()
    tallennaViesti(1, lahetysTunniste = lahetys.tunniste, kayttooikeudet = kayttooikeudetOik1org1)
    // massalähetys useammalla viestillä
    val lahetys2 = this.tallennaLahetys()
    tallennaViesti(5, lahetysTunniste = lahetys2.tunniste, kayttooikeudet = kayttooikeudetOik1org1)
    tallennaViesti(1, lahetysTunniste = lahetys2.tunniste, kayttooikeudet = Set(Kayttooikeus("OIKEUS2", Some(ORGANISAATIO2))))
    Assertions.assertEquals(1, kantaOperaatiot.getLahetyksenViestiLkm(lahetys.tunniste))
    Assertions.assertEquals(2, kantaOperaatiot.getLahetyksenViestiLkm(lahetys2.tunniste))


  /**
   * Testataan viestin tietojen haku lähetystunnuksella ja viestitunnuksella
   */
  @Test def testMassaViestiLahetystunnuksella(): Unit =
    val kayttajanKayttooikeudet = kayttooikeudetOik1org1
    // lähetys jossa yksi viesti
    val lahetys = this.tallennaLahetys()
    val (viesti, vastaanottajat) = tallennaViesti(5, lahetysTunniste = lahetys.tunniste, kayttooikeudet = kayttooikeudetOik1org1)
    // eri oikeuksilla
    val lahetys2 = this.tallennaLahetys()
    val (viesti2, vastaanottajat2) = tallennaViesti(5, lahetysTunniste = lahetys2.tunniste, kayttooikeudet = Set(Kayttooikeus("OIKEUS2", Some(ORGANISAATIO1))))
    Assertions.assertEquals(
      RaportointiViesti(viesti.tunniste, viesti.lahetysTunniste,viesti.otsikko,viesti.sisalto,viesti.sisallonTyyppi,viesti.kielet,viesti.maskit,viesti.omistaja,viesti.prioriteetti),
      kantaOperaatiot.getMassaViestiLahetystunnisteella(lahetys.tunniste, kayttajanKayttooikeudet).get)
    // ei käyttöoikeuksia
    Assertions.assertEquals(Option.empty, kantaOperaatiot.getMassaViestiLahetystunnisteella(lahetys2.tunniste, kayttajanKayttooikeudet))


  /**
   * Testataan viestin tietojen haku lähetystunnuksella ja viestitunnuksella
   */
  @Test def testRaportointiViestiTunnuksella(): Unit =
    val kayttajanKayttooikeudet = kayttooikeudetOik1org1
    val lahetys = this.tallennaLahetys()
    val (viesti, vastaanottajat) = tallennaViesti(5, lahetysTunniste = lahetys.tunniste, kayttooikeudet = kayttooikeudetOik1org1)
    // eri oikeuksilla
    val lahetys2 = this.tallennaLahetys()
    val (viesti2, vastaanottajat2) = tallennaViesti(5, lahetysTunniste = lahetys2.tunniste, kayttooikeudet = Set(Kayttooikeus("OIKEUS2", Some(ORGANISAATIO1))))
    Assertions.assertEquals(
      RaportointiViesti(viesti.tunniste, viesti.lahetysTunniste, viesti.otsikko, viesti.sisalto, viesti.sisallonTyyppi, viesti.kielet, viesti.maskit, viesti.omistaja, viesti.prioriteetti),
      kantaOperaatiot.getRaportointiViestiTunnisteella(viesti.tunniste, kayttajanKayttooikeudet).get)
    // ei käyttöoikeuksia
    Assertions.assertEquals(Option.empty, kantaOperaatiot.getRaportointiViestiTunnisteella(viesti2.tunniste, kayttajanKayttooikeudet))

  /**
   * Testataan lähetyksen vastaanottajien lukeminen
   */
  @Test def testHaeLahetyksenVastaanottajia(): Unit =
    val kayttooikeudet = kayttooikeudetOik1org1
    // luodaan kaksi settiä vastaanottajia
    val lahetys = this.tallennaLahetys()
    val (viesti1, vastaanottajat1) = tallennaViesti(2, lahetysTunniste = lahetys.tunniste, kayttooikeudet=kayttooikeudet)
    // tällä on rajoituksissa toinenkin käyttöoikeus
    val (viesti2, vastaanottajat2) = tallennaRaataloityViesti(Seq.apply(Kontakti(Some("Eka Vastaanottaja"), "eka.vastaanottaja@example.org")), lahetysTunniste = lahetys.tunniste)

    // kun haetaan kaikki kerralla vastaa luotuja
    Assertions.assertEquals(vastaanottajat1.concat(vastaanottajat2).toSet,
      kantaOperaatiot.haeLahetyksenVastaanottajia(lahetys.tunniste, Option.empty, Option.empty, Option.empty, kayttooikeudet).toSet)

    // kun haetaan kaksi ensimmäistä tulee aakkostettuna ensimmäiset
    Assertions.assertEquals(Set(vastaanottajat2.head, vastaanottajat1.head),
      kantaOperaatiot.haeLahetyksenVastaanottajia(lahetys.tunniste, Option.empty, Some(2), Option.empty, kayttooikeudet).toSet)

    // sivutus: haku palauttaa alkaen-parametrin jälkeiset rivit
    Assertions.assertEquals(Set(vastaanottajat1.last),
      kantaOperaatiot.haeLahetyksenVastaanottajia(lahetys.tunniste, Option.apply(vastaanottajat1.head.kontakti.sahkoposti), Option.empty, Option.empty,kayttooikeudet).toSet)

    // käyttöoikeusrajaus
    Assertions.assertEquals(Set(vastaanottajat2.head),
      kantaOperaatiot.haeLahetyksenVastaanottajia(lahetys.tunniste, Option.empty, Option.empty, Option.empty, Set(Kayttooikeus("OIKEUS2", Some(ORGANISAATIO1)))).toSet)
    // tilarajaus
    Assertions.assertEquals(vastaanottajat1.concat(vastaanottajat2).toSet,
      kantaOperaatiot.haeLahetyksenVastaanottajia(lahetys.tunniste, Option.empty, Option.empty, Some("kesken"), kayttooikeudet).toSet)
    // päivitetään yhden vastaanottajan tila lähetetyksi
    kantaOperaatiot.paivitaVastaanottajaLahetetyksi(vastaanottajat2.head.tunniste, "ses-tunniste")
    kantaOperaatiot.paivitaVastaanotonTila("ses-tunniste", VastaanottajanTila.DELIVERY, Option.empty)
    Assertions.assertEquals(vastaanottajat2.head.tunniste,
    kantaOperaatiot.haeLahetyksenVastaanottajia(lahetys.tunniste, Option.empty, Option.empty, Some("valmis"), kayttooikeudet).head.tunniste)
    Assertions.assertEquals(vastaanottajat1.toSet,
      kantaOperaatiot.haeLahetyksenVastaanottajia(lahetys.tunniste, Option.empty, Option.empty, Some("kesken"), kayttooikeudet).toSet)
    // päivitetään yhden vastaanottajan tila virhetilaan
    kantaOperaatiot.paivitaVastaanottajaVirhetilaan(vastaanottajat1.head.tunniste, "lisätiedot")
    kantaOperaatiot.haeLahetyksenVastaanottajia(lahetys.tunniste, Option.empty, Option.empty, Some("valmis"), kayttooikeudet).head.tunniste
    Assertions.assertEquals(vastaanottajat1.head.tunniste,
      kantaOperaatiot.haeLahetyksenVastaanottajia(lahetys.tunniste, Option.empty, Option.empty, Some("epaonnistui"), kayttooikeudet).head.tunniste)

  /**
   * Testataan lähetyksen vastaanottajien lukeminen
   */
  @Test def testHaeLahetyksenVastaanottajiaKriteereilla(): Unit =
    val kayttooikeudet = kayttooikeudetOik1org1
    // luodaan kaksi settiä vastaanottajia
    val lahetys = this.tallennaLahetys()
    val (viesti1, vastaanottajat1) = tallennaViesti(2, lahetysTunniste = lahetys.tunniste, kayttooikeudet = kayttooikeudet)
    val (viesti2, vastaanottajat2) = tallennaRaataloityViesti(Seq.apply(Kontakti(Some("Eka Vastaanottaja"), "eka.vastaanottaja@example.org")), lahetysTunniste = lahetys.tunniste)

    // kun haetaan sähköpostiosoitteella, lista suodattuu
    Assertions.assertEquals(Set(vastaanottajat2.head),
      kantaOperaatiot.haeLahetyksenVastaanottajia(lahetys.tunniste, Option.empty, Option.empty, Option.empty, kayttooikeudet, "eka.vastaanottaja@example.org").toSet)
    Assertions.assertTrue(kantaOperaatiot.haeLahetyksenVastaanottajia(lahetys.tunniste, Option.empty, Option.empty, Option.empty, kayttooikeudet, "foobar").toSet.isEmpty)

}
