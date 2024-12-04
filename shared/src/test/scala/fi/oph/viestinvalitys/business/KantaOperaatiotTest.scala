package fi.oph.viestinvalitys.business

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import fi.oph.viestinvalitys.business.SisallonTyyppi.HTML
import fi.oph.viestinvalitys.business.VastaanottajanTila.DELIVERY
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
import java.time.temporal.ChronoUnit
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
    .withDatabaseName("viestinvalityspalvelu")
    .withUsername("app")
    .withPassword("app")
    .withLogConsumer(frame => LOG.info(frame.getUtf8StringWithoutLineEnding))

  private def getDatasource() =
    val ds: PGSimpleDataSource = new PGSimpleDataSource()
    ds.setServerNames(Array("localhost"))
    ds.setDatabaseName("viestinvalityspalvelu")
    ds.setPortNumbers(Array(postgres.getMappedPort(5432)))
    ds.setUser("app")
    ds.setPassword("app")
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
            DROP TABLE raportointi_cas_client_session;
            DROP TABLE lahetys_cas_client_session;
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
  private def tallennaLahetys(sailytysaika: Int = 10,
                              lahettaja: Kontakti = Kontakti(Some("Lasse Lähettäjä"), "lasse.lahettaja@opintopolku.fi"),
                              lahettavaPalvelu: String = "lahettavapalvelu",
                              lahettavanVirkailijanOID: Option[String] = Some(LAHETTAJA_OID1)): Lahetys =
    kantaOperaatiot.tallennaLahetys(
      "otsikko",
      "omistaja",
      lahettavaPalvelu,
      lahettavanVirkailijanOID,
      lahettaja,
      Option.empty,
      Prioriteetti.NORMAALI,
      sailytysaika
    )

  private val ORGANISAATIO1 = "1.2.246.562.10.16790925842"
  private val ORGANISAATIO2 = "1.2.246.562.10.16790925843"
  private val LAHETTAJA_OID1 = "1.2.246.562.24.1"
  private val LAHETTAJA_OID2 = "1.2.246.562.24.2"
  private val Oikeus1Organisaatio1 = Kayttooikeus("OIKEUS1", Some(ORGANISAATIO1))
  private val kayttooikeudetOik1org1: Set[Kayttooikeus] = Set(Oikeus1Organisaatio1)

  // apumetodeja viestien tallennuksen ja lähetyksen priorisoinnin yms. testaamiseen
  private def getVastaanottajat(maara: Int): Seq[Kontakti] =
    Range(1, maara+1).map(suffix => Kontakti(Some("Vastaanottaja" + suffix), "vastaanottaja" + suffix + "@example.com"))

  private def tallennaViesti(vastaanottajat: Seq[Kontakti] = getVastaanottajat(1),
                             prioriteetti: Prioriteetti = Prioriteetti.NORMAALI,
                             otsikko: String = "otsikko",
                             sisalto: String = "sisältö",
                             sisallonTyyppi: SisallonTyyppi = SisallonTyyppi.TEXT,
                             lahetys: Lahetys = null,
                             kielet: Set[Kieli] = Set(Kieli.FI),
                             liitteet: Seq[Liite] = Seq.empty,
                             sailytysAika: Int = 10,
                             kayttooikeudet: Set[Kayttooikeus] = Set(Oikeus1Organisaatio1, Kayttooikeus("OIKEUS2", Some(ORGANISAATIO1))),
                             omistaja: String = "omistaja",
                             lahettavaPalvelu: String = "palvelu",
                             maskit: Map[String, Option[String]] = Map("öö" -> Some("**")),
                             idempotencyKey: String = null,
                             metadata: Map[String, Seq[String]] = Map("avain" -> Seq("arvo"))): (Viesti, Seq[Vastaanottaja]) =
    kantaOperaatiot.tallennaViesti(
      otsikko,
      sisalto,
      sisallonTyyppi,
      kielet,
      maskit,
      Option.empty,
      Some(Kontakti(Some("Lasse Lahettaja"), "lasse.lahettaja@oph.fi")),
      Option.empty,
      vastaanottajat,
      liitteet.map(liite => liite.tunniste),
      Option.apply(lahettavaPalvelu),
      Option.apply(if(lahetys==null) null else lahetys.tunniste),
      Option.apply(prioriteetti),
      Option.apply(sailytysAika),
      kayttooikeudet,
      metadata,
      omistaja,
      Option.apply(idempotencyKey),
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
      omistaja,
      Option.empty
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
    val (viesti, vastaanottajat) = tallennaViesti(liitteet = liitteet, maskit = maskit)

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
   * Testataan että sama omistaja ei voi tallentaa kahta viestiä samalla idempotency-avaimella
   */
  @Test def testTallennaViestiIdempotencyKeyExists(): Unit =
    // Sama omistaja ei voi tallentaa kahta viestiä samalla idempotency-avaimella
    tallennaViesti(omistaja = "omistaja1", idempotencyKey = "avain")
    try
      tallennaViesti(omistaja = "omistaja1", idempotencyKey = "avain")
      Assertions.fail("omistaja ei saa pystyä tallentamaan kahta viestiä samalla idempotency-avaimella")
    catch
      case e: Exception =>

    // Sama omistaja saa käyttää tyhjää avainta vaikka kuinka monta kertaa
    tallennaViesti(omistaja = "omistaja1", idempotencyKey = null)
    tallennaViesti(omistaja = "omistaja1", idempotencyKey = null)

    // Eri omistaja saa käyttää samaa avainta
    tallennaViesti(omistaja = "omistaja2", idempotencyKey = "avain")

  /**
   * Testataan viestin tunnisteen hakeminen idempotency-avaimella
   */
  @Test def testGetViestiWithIdempotencyKey(): Unit =
    // jos viestiä ei löydy palautuu empty
    Assertions.assertEquals(Option.empty, kantaOperaatiot.getExistingViesti("omistaja1", "avain"))

    // jos viesti on olemassa palautuu tunniste
    val (viesti, vastaanottaja) = tallennaViesti(omistaja = "omistaja1", idempotencyKey = "avain")
    Assertions.assertEquals(viesti, kantaOperaatiot.getExistingViesti("omistaja1", "avain").get)

  /**
   * Testataan korkean prioriteetin viestien määrän lukeminen
   */
  @Test def testGetKorkeanPrioriteetinViestienMaaraSince(): Unit =
    // Luodaan korkean prioriteetin viestejä
    tallennaViesti(getVastaanottajat(1), omistaja = "omistaja1", prioriteetti = Prioriteetti.KORKEA)
    tallennaViesti(getVastaanottajat(1), omistaja = "omistaja1", prioriteetti = Prioriteetti.KORKEA)

    // Odotetaan jotta luodut viestit menevät pois aikaikkunasta
    Thread.sleep(1000)

    // Luodaan 2 korkean prioriteetin viestiä jotka aikaikkunan sisällä
    tallennaViesti(getVastaanottajat(1), omistaja = "omistaja1", prioriteetti = Prioriteetti.KORKEA)
    tallennaViesti(getVastaanottajat(3), omistaja = "omistaja1", prioriteetti = Prioriteetti.NORMAALI)
    tallennaViesti(getVastaanottajat(1), omistaja = "omistaja1", prioriteetti = Prioriteetti.KORKEA)

    // Luodaan korkean prioriteetin viestejä toiselle omistajalle
    tallennaViesti(getVastaanottajat(1), omistaja = "omistaja2", prioriteetti = Prioriteetti.KORKEA)

    // Omistaja1:llä kaksi korkean prioriteetin viestiä aikaikkunan sisällä
    Assertions.assertEquals(2, kantaOperaatiot.getKorkeanPrioriteetinViestienMaaraSince("omistaja1", 1))

  /**
   * Testataan viestin käyttöoikeudet
   */
  @Test def testGetViestinKayttooikeudet(): Unit =
    // tallennetaan viestit oikeuksilla (jolloin luodaan lähetys johon oikeudet tallennetaan)
    val (viesti1, _) = tallennaViesti(lahetys = null,
      kayttooikeudet = kayttooikeudetOik1org1)
    val (viesti2, _) = tallennaViesti(lahetys = null,
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
    val (viesti1, _) = tallennaViesti(lahetys = null,
      kayttooikeudet = Set(Oikeus1Organisaatio1, Kayttooikeus("OIKEUS2", Some(ORGANISAATIO1))))
    val (viesti2, _) = tallennaViesti(lahetys = null,
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
    tallennaViesti(lahetys = lahetys1,
      kayttooikeudet = kayttooikeudetOik1org1)
    tallennaViesti(lahetys = lahetys2,
      kayttooikeudet = Set(Oikeus1Organisaatio1, Kayttooikeus("OIKEUS2", Some(ORGANISAATIO1))))
    tallennaViesti(lahetys = lahetys3,
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
    tallennaViesti(lahetys = lahetys2,
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
    val lahetys2 = this.tallennaLahetys()
    // tallennetaan viestit oikeuksilla (jolloin lähetyksen oikeudet tallennetaan)
    tallennaViesti(lahetys = lahetys1,
      kayttooikeudet = kayttooikeudetOik1org1)
    tallennaViesti(lahetys = lahetys2,
      kayttooikeudet = Set(Kayttooikeus("OIKEUS2", Some(ORGANISAATIO1))))
    val kayttooikeusTunnisteet = kantaOperaatiot.getKayttooikeusTunnisteet(kayttooikeudetOik1org1.toSeq)._1
    val kayttooikeusTunnisteet2 = kantaOperaatiot.getKayttooikeusTunnisteet(Seq(Kayttooikeus("OIKEUS2", Some(ORGANISAATIO1))))._1
    Assertions.assertEquals(
    Seq(lahetys1.tunniste -> kayttooikeudetOik1org1).toMap,
    kantaOperaatiot.getLahetystenKayttooikeudet(Seq(lahetys1.tunniste)))
    // pääkäyttäjä
    Assertions.assertEquals(lahetys1.tunniste, kantaOperaatiot.getLahetysKayttooikeusrajauksilla(
      lahetys1.tunniste, Option.empty).get.tunniste)
    // on oikeudet
    Assertions.assertEquals(lahetys1.tunniste, kantaOperaatiot.getLahetysKayttooikeusrajauksilla(
      lahetys1.tunniste,Some(kayttooikeusTunnisteet)).get.tunniste)
    // tyhjä oikeuslista
    Assertions.assertEquals(Option.empty, kantaOperaatiot.getLahetysKayttooikeusrajauksilla(
      lahetys1.tunniste, Some(Set.empty)))
    // väärät oikeudet
    Assertions.assertEquals(Option.empty, kantaOperaatiot.getLahetysKayttooikeusrajauksilla(
      lahetys1.tunniste, Some(kayttooikeusTunnisteet2)))
    // on oikeudet ja muitakin oikkia
    Assertions.assertEquals(lahetys1.tunniste, kantaOperaatiot.getLahetysKayttooikeusrajauksilla(
      lahetys1.tunniste, Some(kayttooikeusTunnisteet ++ kayttooikeusTunnisteet2)).get.tunniste)


  /**
   * Testataan että viestiin voi liittää erikseen luodun lähetykset
   */
  @Test def testViestiOlemassaOlevaLahetys(): Unit =
    // luodaan uusi lähetys ja viesti tähän lähetykseen
    val lahetys = this.tallennaLahetys()
    val (viesti, _) = tallennaViesti(lahetys = lahetys)

    // kannasta luetun viestin lähetystunniste täsmää
    Assertions.assertEquals(lahetys.tunniste, kantaOperaatiot.getViestit(Seq(viesti.tunniste)).find(v => true).get.lahetysTunniste)

  /**
   * Testataan että [[KantaOperaatiot.getLahetettavatVastaanottajat()]] palauttaa halutun määrän vastaanottajia
   */
  @Test def testGetLahetettavatViestitMaara(): Unit =
    // tallennetaan viestit
    val (viesti1, vastaanottajat1) = tallennaViesti(getVastaanottajat(2))
    val (viesti2, vastaanottajat2) = tallennaViesti(getVastaanottajat(4), Prioriteetti.KORKEA)
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
        tallennaViesti(getVastaanottajat(25))._2.map(v => v.tunniste)
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
    val (viesti1, vastaanottajat1) = tallennaViesti(getVastaanottajat(2))
    val (viesti2, vastaanottajat2) = tallennaViesti(getVastaanottajat(4), Prioriteetti.KORKEA)
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
    val (viesti1, vastaanottajat) = tallennaViesti(getVastaanottajat(5))

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
    val (viesti1, vastaanottajat1) = tallennaViesti(getVastaanottajat(2))
    val (viesti2, vastaanottajat2) = tallennaViesti(getVastaanottajat(10))
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
    val (viesti1, vastaanottajatNormaali1) = tallennaViesti(getVastaanottajat(5))
    val (viesti2, vastaanottajatNormaali2) = tallennaViesti(getVastaanottajat(5))
    val (viesti3, vastaanottajatKorkea) = tallennaViesti(getVastaanottajat(2), prioriteetti = Prioriteetti.KORKEA)
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
    val (viesti, vastaanottajat) = tallennaViesti(getVastaanottajat(2), liitteet = Seq(liite1, liite2))

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
    val (viesti, vastaanottajat) = tallennaViesti(getVastaanottajat(2), liitteet = Seq(liite1, liite2))

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
      tallennaViesti(getVastaanottajat(3), liitteet = viestinLiitteet)._2.map(v => v.tunniste)
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
    val (viesti, vastaanottajat) = tallennaViesti(getVastaanottajat(2))
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
    val (viesti, vastaanottajat) = tallennaViesti(getVastaanottajat(2))
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
    val (viesti, vastaanottajat) = tallennaViesti(getVastaanottajat(2))
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
    val (viesti1, vastaanottajat1) = tallennaViesti(getVastaanottajat(1), lahetys = lahetys1, liitteet = Seq(liite))
    val (viesti2, vastaanottajat2) = tallennaViesti(getVastaanottajat(1), lahetys = lahetys2, liitteet = Seq(liite))

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
    val (viesti, _) = tallennaViesti(sailytysAika = 0, liitteet = Seq(liite1))

    // poistetaan liitteet jotka luotu ennen nykyhetkeä ja joilla ei linkityksiä
    Assertions.assertEquals(Seq(liite2.tunniste), kantaOperaatiot.poistaPoistettavatLiitteet(Instant.now))

    // liite1 edelleen olemassa (koska linkitetty viestiin), liite2 poistettu (koska ei linkityksiä)
    Assertions.assertEquals(Seq(liite1), kantaOperaatiot.getLiitteet(Seq(liite1.tunniste, liite2.tunniste)))

    // poistetaan viesti ja siihen liittyvät liitelinkitykset, sekä uudestaan turhat liitteet
    kantaOperaatiot.poistaPoistettavatLahetykset()
    kantaOperaatiot.poistaPoistettavatLiitteet(Instant.now)

    // myös liite1 poistunut
    Assertions.assertEquals(Seq.empty, kantaOperaatiot.getLiitteet(Seq(liite1.tunniste, liite2.tunniste)))

  /**
   * Testataan että idempotency-avainten poistaminen toimii
   */
  @Test def testPoistaPoistettavatIdempotencyKeys(): Unit =
    tallennaViesti(omistaja = "omistaja1", idempotencyKey = "avain")

    // avainta ei poistettu
    Assertions.assertEquals(0, kantaOperaatiot.poistaIdempotencyKeys(Instant.now().minusSeconds(60*60)))
    try
      tallennaViesti(omistaja = "omistaja1", idempotencyKey = "avain")
      Assertions.fail("idempotency-avaimen uudelleenkäyttö sallittiin")
    catch
      case e: Exception =>

    // avain poistettu joten sen voi käyttää uudestaan
    Assertions.assertEquals(1, kantaOperaatiot.poistaIdempotencyKeys(Instant.now().plusSeconds(60*60)))
    tallennaViesti(omistaja = "omistaja1", idempotencyKey = "avain")

/* Raportointikälin hakutoiminnot */
  @Test def testGetKayttooikeusTunnisteet(): Unit =
    val viestinKayttooikeudet = Set(Kayttooikeus("oikeus1", Option.apply("organisaatio1")), Kayttooikeus("oikeus2", Option.apply("organisaatio1")), Kayttooikeus("oikeus2", None))

    // varmistetaan että metodi tukee suurta joukkoa käyttöoikeuksia
    val muutKayttooikeudet = Range(0, 1024*16).map(i => Kayttooikeus("oikeus" + i, Option.apply("organisaatio" + i))).toSet
    val (viesti, _) = tallennaViesti(kayttooikeudet = viestinKayttooikeudet)
    val (tunnisteet, uusinTunniste) = kantaOperaatiot.getKayttooikeusTunnisteet(viestinKayttooikeudet.concat(muutKayttooikeudet).toSeq)
    val uusin = kantaOperaatiot.getUusinKayttooikeusTunniste()

    // kannassa on 2 tunnistetta viestin lisäyksessä käytetyillä käyttöoikeus-organisaatio-yhdistelmillä
    // ilman organisaatiota tallennettu käyttöoikeus jätetään huomioimatta
    Assertions.assertEquals(2, tunnisteet.size)
    // uusin tunniste on 3 kun tunniste on juokseva sekvenssi ja käyttöoikeuksia on lisätty kantaan 3
    Assertions.assertEquals(3, uusinTunniste)
    Assertions.assertEquals(0, kantaOperaatiot.getKayttooikeusTunnisteet(Seq(Kayttooikeus("oikeus2", Option.apply("organisaatio2"))))._1.size)

  @Test def testGetKayttooikeusTunnisteetOikeallaDatalla(): Unit =
    val viestinKayttooikeudet = Set(Kayttooikeus("APP_OSOITE_CRUD", Option.apply("1.2.246.562.10.00000000001")))

    val kayttajanKayttooikeudet = Set(Kayttooikeus("APP_RAPORTOINTI",None), Kayttooikeus("APP_OSOITE_CRUD",Some("1.2.246.562.10.00000000001")), Kayttooikeus("APP_RYHMASAHKOPOSTI_SEND",None), Kayttooikeus("APP_VIRKAILIJANTYOPOYTA_MUUT",None), Kayttooikeus("APP_RAPORTOINTI_READ",None), Kayttooikeus("APP_KAYTTOOIKEUS_VASTUUKAYTTAJAT",None), Kayttooikeus("APP_KAYTTOOIKEUS_VASTUUKAYTTAJAT",Some("1.2.246.562.10.00000000001")), Kayttooikeus("APP_RYHMASAHKOPOSTI_SEND",Some("1.2.246.562.10.00000000001")), Kayttooikeus("APP_VIRKAILIJANTYOPOYTA_MUUT",Some("1.2.246.562.10.00000000001")), Kayttooikeus("APP_KAYTTOOIKEUS",None), Kayttooikeus("APP_VIESTINVALITYS",None), Kayttooikeus("APP_VIESTINVALITYS_KATSELU",Some("1.2.246.562.10.00000000001")), Kayttooikeus("APP_RYHMASAHKOPOSTI",None), Kayttooikeus("APP_RAPORTOINTI_READ",Some("1.2.246.562.10.00000000001")), Kayttooikeus("APP_OSOITE_CRUD",None), Kayttooikeus("APP_OSOITE",None), Kayttooikeus("APP_VIESTINVALITYS_KATSELU",None), Kayttooikeus("APP_VIRKAILIJANTYOPOYTA",None))

    val (viesti, _) = tallennaViesti(kayttooikeudet = viestinKayttooikeudet)
    Assertions.assertEquals(1, kantaOperaatiot.getKayttooikeusTunnisteet(kayttajanKayttooikeudet.toSeq)._1.size);
    Assertions.assertEquals(0, kantaOperaatiot.getKayttooikeusTunnisteet(Seq(Kayttooikeus("oikeus2", Option.apply("organisaatio2"))))._1.size);


  @Test def testHaeKaikistaLahetyksista(): Unit =
    val lahetys1 = tallennaLahetys();
    val lahetys2 = tallennaLahetys();

    val kayttooikeudet1 = Set(Kayttooikeus("oikeus1", Option.apply("organisaatio1")),Kayttooikeus("oikeus2", Option.apply("organisaatio1")))
    val kayttooikeudet2 = Set(Kayttooikeus("oikeus1", Option.apply("organisaatio2")),Kayttooikeus("oikeus2", Option.apply("organisaatio2")))
    val kayttooikeudet3 = Set(Kayttooikeus("oikeus1", Option.apply("organisaatio3")),Kayttooikeus("oikeus2", Option.apply("organisaatio3")))

    val vastaanottaja1 = Kontakti(Option.apply("Vallu Vastaanottaja"), "vallu.vastaanottaja@example.com")
    val vastaanottaja2 = Kontakti(Option.apply("Venla Vastaanottaja"), "venla.vastaanottaja@example.com")

    // tallennetaan lähetyksille useita viestejä jotta voidaan varmistua ettei tämä aiheuta duplikaatteja tuloksiin
    Range(0, 3).map(_ => tallennaViesti(Seq(vastaanottaja1), lahetys=lahetys1, otsikko="Junaillaan junalla", sisalto="Valtion rautatiet on hieno yritys", kielet = Set(Kieli.FI), kayttooikeudet = kayttooikeudet1))
    Range(0, 3).map(_ => tallennaViesti(Seq(vastaanottaja2), lahetys=lahetys2, otsikko="Autoillaan bussilla", sisalto="Onnibus rulettaa", kielet = Set(Kieli.FI), kayttooikeudet = kayttooikeudet2))

    val kayttooikeusTunnisteet1 = kantaOperaatiot.getKayttooikeusTunnisteet(kayttooikeudet1.toSeq)._1
    val kayttooikeusTunnisteet2 = kantaOperaatiot.getKayttooikeusTunnisteet(kayttooikeudet2.toSeq)._1

    // haku pääkäyttäjänä palauttaa kaikki luodut viesti (haetaan vain kaksi ensimmäistä koska kantaan on ladattu myös esimerkkejä)
    Assertions.assertEquals(Seq(lahetys2, lahetys1), kantaOperaatiot.searchLahetykset(enintaan=2)._1)

    // haku kaikista viesteistä ilman oikeuksia ei palauta mitään
    Assertions.assertEquals(Seq.empty, kantaOperaatiot.searchLahetykset(kayttooikeusTunnisteet = Option.apply(Set.empty))._1)

    // haku lähetyksen 1 viestien käyttöoikeuksilla palauttaa lähetyksen 1
    Assertions.assertEquals(Seq(lahetys1), kantaOperaatiot.searchLahetykset(kayttooikeusTunnisteet =  Option.apply(kayttooikeusTunnisteet1))._1)

  @Test def testHaeLahetyksetPaakayttajana(): Unit =
    val lahetys1 = tallennaLahetys(lahettavaPalvelu = "lahettavaPalvelu1", lahettavanVirkailijanOID = Some(LAHETTAJA_OID1));
    val lahetys2 = tallennaLahetys(lahettavaPalvelu = "lahettavaPalvelu2", lahettavanVirkailijanOID = Some(LAHETTAJA_OID2));

    val vastaanottaja1 = Kontakti(Option.apply("Vallu Vastaanottaja"), "vallu.vastaanottaja@example.com")
    val vastaanottaja2 = Kontakti(Option.apply("Venla Vastaanottaja"), "venla.vastaanottaja@example.com")

    val kayttooikeudet1 = Set(Kayttooikeus("oikeus", Option.apply("organisaatio1")))
    val kayttooikeudet2 = Set(Kayttooikeus("oikeus", Option.apply("organisaatio2")))

    // tallennetaan lähetyksille useita viestejä jotta voidaan varmistua ettei tämä aiheuta duplikaatteja tuloksiin
    Range(0, 3).map(_ => tallennaViesti(Seq(vastaanottaja1), lahetys=lahetys1, otsikko="Junaillaan junalla",
      sisalto="Valtion rautatiet on hieno yritys", kielet = Set(Kieli.FI), metadata = Map("avain" -> Seq("arvo11", "arvo12")),
      kayttooikeudet = kayttooikeudet1))
    Range(0, 3).map(_ => tallennaViesti(Seq(vastaanottaja2), lahetys=lahetys2, otsikko="Autoillaan bussilla",
      sisalto="Onnibus rulettaa", kielet = Set(Kieli.FI), metadata = Map("avain" -> Seq("arvo21", "arvo22")),
      kayttooikeudet = kayttooikeudet2))

    // haku ilman kriteerejä palauttaa kaikki luodut viesti (haetaan vain kaksi ensimmäistä koska kantaan on ladattu myös esimerkkejä)
    Assertions.assertEquals(Seq(lahetys2, lahetys1), kantaOperaatiot.searchLahetykset(enintaan = 2)._1)

    // haku lähetyksen 1 viestien oikeuksien organisaatiolla palauttaa lähetyksen 1
    Assertions.assertEquals(Seq(lahetys1), kantaOperaatiot.searchLahetykset(organisaatiot = Option.apply(Set("organisaatio1")))._1)

    // haku lähetyksen 1 viestien otsikon sisällöllä palauttaa lähetyksen 1
    Assertions.assertEquals(Seq(lahetys1), kantaOperaatiot.searchLahetykset(sisaltoHakuLauseke = Option.apply("junaillaan"))._1)

    // haku lähetyksen 1 sisällöllä palauttaa lähetyksen 1
    Assertions.assertEquals(Seq(lahetys1), kantaOperaatiot.searchLahetykset(sisaltoHakuLauseke = Option.apply("valtion"))._1)

    // haku lähetyksen 1 vastaanottajan sähköpostilla palauttaa lähetyksen 1
    Assertions.assertEquals(Seq(lahetys1), kantaOperaatiot.searchLahetykset(vastaanottajaHakuLauseke = Option.apply("vallu.vastaanottaja@example.com"))._1)

    // haku lähetyksen 1 lähettäjän oidilla palauttaa lähetyksen 1
    Assertions.assertEquals(Seq(lahetys1), kantaOperaatiot.searchLahetykset(lahettajaHakuLauseke = Option.apply(LAHETTAJA_OID1))._1)

    // haku lähetyksen 1 viestin metadatalla palauttaa lähetyksen 1
    Assertions.assertEquals(Seq(lahetys1), kantaOperaatiot.searchLahetykset(metadataHakuLausekkeet = Option.apply(Map("avain" -> Seq("arvo11"))))._1)

    // haku lähetyksen 1 lähettävällä palvelulla palauttaa lähetyksen 1
    Assertions.assertEquals(Seq(lahetys1), kantaOperaatiot.searchLahetykset(lahettavaPalveluHakuLauseke = Option.apply("lahettavaPalvelu1"))._1)

    // haku aikarajauksella palauttaa lähetykset aikavälillä
    Assertions.assertEquals(Seq(lahetys2, lahetys1), kantaOperaatiot.searchLahetykset(hakuAlkaen = Option.apply(Instant.now.minus(1, ChronoUnit.HOURS)))._1)

  @Test def testHaeLahetyksetIlmanOikeuksia(): Unit =
    val lahetys1 = tallennaLahetys(lahettavaPalvelu = "lahettavaPalvelu1");
    val lahetys2 = tallennaLahetys(lahettavaPalvelu = "lahettavaPalvelu2");

    val vastaanottaja1 = Kontakti(Option.apply("Vallu Vastaanottaja"), "vallu.vastaanottaja@example.com")
    val vastaanottaja2 = Kontakti(Option.apply("Venla Vastaanottaja"), "venla.vastaanottaja@example.com")

    val kayttooikeudet1 = Set(Kayttooikeus("oikeus", Option.apply("organisaatio1")))
    val kayttooikeudet2 = Set(Kayttooikeus("oikeus", Option.apply("organisaatio2")))

    // tallennetaan lähetyksille useita viestejä jotta voidaan varmistua ettei tämä aiheuta duplikaatteja tuloksiin
    Range(0, 3).map(_ => tallennaViesti(Seq(vastaanottaja1), lahetys=lahetys1, otsikko="Junaillaan junalla",
      sisalto="Valtion rautatiet on hieno yritys", kielet = Set(Kieli.FI),
      metadata = Map("avain" -> Seq("arvo11", "arvo12")), kayttooikeudet = kayttooikeudet1))
    Range(0, 3).map(_ => tallennaViesti(Seq(vastaanottaja2), lahetys=lahetys2, otsikko="Autoillaan bussilla",
      sisalto="Onnibus rulettaa", kielet = Set(Kieli.FI), metadata = Map("avain" -> Seq("arvo21", "arvo22")),
      kayttooikeudet = kayttooikeudet2))

    // haku ilman kriteerejä ei palauta mitään
    Assertions.assertEquals(Seq.empty, kantaOperaatiot.searchLahetykset(kayttooikeusTunnisteet = Option.apply(Set.empty))._1)

    // haku lähetyksen 1 viestien oikeuksien organisaatiolla ei palauta mitään
    Assertions.assertEquals(Seq.empty, kantaOperaatiot.searchLahetykset(kayttooikeusTunnisteet = Option.apply(Set.empty),
      organisaatiot = Option.apply(Set("organisaatio1")))._1)

    // haku lähetyksen 1 viestien otsikolla ei palauta mitään
    Assertions.assertEquals(Seq.empty, kantaOperaatiot.searchLahetykset(kayttooikeusTunnisteet = Option.apply(Set.empty),
      sisaltoHakuLauseke = Option.apply("junaillaan"))._1)

    // haku lähetyksen 1 sisällöllä ei palauta mitään
    Assertions.assertEquals(Seq.empty, kantaOperaatiot.searchLahetykset(kayttooikeusTunnisteet = Option.apply(Set.empty),
      sisaltoHakuLauseke = Option.apply("valtion"))._1)

    // haku lähetyksen 1 vastaanottajan sähköpostilla ei palauta mitään
    Assertions.assertEquals(Seq.empty, kantaOperaatiot.searchLahetykset(kayttooikeusTunnisteet = Option.apply(Set.empty),
      vastaanottajaHakuLauseke = Option.apply("vallu.vastaanottaja@example.com"))._1)

    // haku lähetyksen 1 lähettäjän oidilla ei palauta mitään
    Assertions.assertEquals(Seq.empty, kantaOperaatiot.searchLahetykset(kayttooikeusTunnisteet = Option.apply(Set.empty),
      lahettajaHakuLauseke = Option.apply(LAHETTAJA_OID1))._1)

    // haku lähetyksen 1 viestin metadatalla ei palauta mitään
    Assertions.assertEquals(Seq.empty, kantaOperaatiot.searchLahetykset(kayttooikeusTunnisteet = Option.apply(Set.empty),
      metadataHakuLausekkeet = Option.apply(Map("avain" -> Seq("arvo11"))))._1)

    // haku lähetyksen 1 lähettävällä palvelulla ei palauta mitään
    Assertions.assertEquals(Seq.empty, kantaOperaatiot.searchLahetykset(kayttooikeusTunnisteet = Option.apply(Set.empty),
      lahettavaPalveluHakuLauseke = Option.apply("lahettavaPalvelu1"))._1)

    // haku aikarajauksella ei palauta mitään
    Assertions.assertEquals(Seq.empty, kantaOperaatiot.searchLahetykset(kayttooikeusTunnisteet = Option.apply(Set.empty),
      hakuAlkaen = Option.apply(Instant.now.minus(1, ChronoUnit.HOURS)))._1)


  @Test def testHaeLahetyksetOikeuksilla(): Unit =
    val lahetysOikeudetMatch = tallennaLahetys(lahettavaPalvelu = "lahettavaPalvelu1", lahettavanVirkailijanOID = Some(LAHETTAJA_OID1));
    val lahetysOikeudetEiMatch = tallennaLahetys(lahettavaPalvelu = "lahettavaPalvelu2", lahettavanVirkailijanOID = Some(LAHETTAJA_OID2));
    val lahetysEiOikeuksiaMatch = tallennaLahetys(lahettavaPalvelu = "lahettavaPalvelu1", lahettavanVirkailijanOID = Some(LAHETTAJA_OID1));
    val lahetysEiOikeuksiaEiMatch = tallennaLahetys(lahettavaPalvelu = "lahettavaPalvelu2", lahettavanVirkailijanOID = Some(LAHETTAJA_OID2));

    val kayttooikeudet1 = Set(Kayttooikeus("oikeus1", Option.apply("organisaatio1")),Kayttooikeus("oikeus2", Option.apply("organisaatio1")))
    val kayttooikeudet2 = Set(Kayttooikeus("oikeus1", Option.apply("organisaatio2")),Kayttooikeus("oikeus2", Option.apply("organisaatio2")))

    val vastaanottaja1 = Kontakti(Option.apply("Vallu Vastaanottaja"), "vallu.vastaanottaja@example.com")
    val vastaanottaja2 = Kontakti(Option.apply("Venla Vastaanottaja"), "venla.vastaanottaja@example.com")

    // tallennetaan lähetyksille useita viestejä jotta voidaan varmistua ettei tämä aiheuta duplikaatteja tuloksiin
    Range(0, 3).map(_ => tallennaViesti(Seq(vastaanottaja1), lahetys=lahetysOikeudetMatch, otsikko="Junaillaan junalla",
      sisalto="Valtion rautatiet on älyttömän hieno yritys", kielet = Set(Kieli.FI),
      kayttooikeudet = kayttooikeudet1.incl(Kayttooikeus("oikeus", Option.apply("organisaatioMatch"))),
      metadata = Map("avain" -> Seq("arvo1", "arvo2"))))
    Range(0, 3).map(_ => tallennaViesti(Seq(vastaanottaja2), lahetys=lahetysOikeudetEiMatch, otsikko="Autoillaan bussilla",
      sisalto="Onnibus rulettaa", kielet = Set(Kieli.FI), kayttooikeudet = kayttooikeudet1))
    Range(0, 3).map(_ => tallennaViesti(Seq(vastaanottaja1), lahetys=lahetysEiOikeuksiaMatch, otsikko="Junaillaan junalla",
      sisalto="Valtion rautatiet on älyttömän hieno yritys", kielet = Set(Kieli.FI), kayttooikeudet = kayttooikeudet2,
      metadata = Map("avain" -> Seq("arvo1", "arvo2"))))
    Range(0, 3).map(_ => tallennaViesti(Seq(vastaanottaja2), lahetys=lahetysEiOikeuksiaEiMatch, otsikko="Autoillaan bussilla",
      sisalto="Onnibus rulettaa", kielet = Set(Kieli.FI), kayttooikeudet = kayttooikeudet2))

    val kayttooikeusTunnisteet = kantaOperaatiot.getKayttooikeusTunnisteet(kayttooikeudet1.toSeq)._1

    // haku ilman kriteerejä palauttaa kaikki lähetykset joihin oikeudet
    Assertions.assertEquals(Seq(lahetysOikeudetEiMatch, lahetysOikeudetMatch), kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet))._1)

    // haku organisaatiolla palauttaa lähetyksen johon oikeudet ja jonka organisaatio mätchää
    Assertions.assertEquals(Seq(lahetysOikeudetMatch), kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet),
      organisaatiot = Option.apply(Set("organisaatioMatch", "jokuMuuOrganisaatio")))._1)

    // jos organisaatio ei mätchää ei palauteta mitään
    Assertions.assertEquals(Seq.empty, kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet),
      organisaatiot = Option.apply(Set("jokuMuuOrganisaatio")))._1)

    // haku otsikolla palauttaa lähetyksen johon oikeudet ja jonka otsikko mätchää
    Assertions.assertEquals(Seq(lahetysOikeudetMatch), kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet), sisaltoHakuLauseke = Option.apply("junaillaan"))._1)

    // jos otsikko ei mätchää ei palauteta mitään
    Assertions.assertEquals(Seq.empty, kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet), sisaltoHakuLauseke = Option.apply("veneillään"))._1)

    // haku sisällöllä palauttaa lähetyksen johon oikeudet ja jonka sisältö mätchää
    Assertions.assertEquals(Seq(lahetysOikeudetMatch), kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet), sisaltoHakuLauseke = Option.apply("valtion"))._1)

    // jos sisältö ei mätchää ei palauteta mitään
    Assertions.assertEquals(Seq.empty, kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet), sisaltoHakuLauseke = Option.apply("kunnan"))._1)

    // haku vastaanottajan sähköpostilla palauttaa lähetyksen johon oikeudet ja jonka vastaanottajan sähköposti mätchää
    Assertions.assertEquals(Seq(lahetysOikeudetMatch), kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet), vastaanottajaHakuLauseke = Option.apply("vallu.vastaanottaja@example.com"))._1)

    // jos vastaanottajan sähköposti ei mätchää ei palauteta mitään
    Assertions.assertEquals(Seq.empty, kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet), vastaanottajaHakuLauseke = Option.apply("vili.vastaanottaja@example.com"))._1)

    // haku lähettäjän oidilla palauttaa lähetyksen johon oikeudet ja jonka vastaanottajan sähköposti mätchää
    Assertions.assertEquals(Seq(lahetysOikeudetMatch), kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet), lahettajaHakuLauseke = Option.apply(LAHETTAJA_OID1))._1)

    // jos lähettäjän oid ei mätchää ei palauteta mitään
    Assertions.assertEquals(Seq.empty, kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet), lahettajaHakuLauseke = Option.apply("9.9.9.9"))._1)

    // haku metadatalla palauttaa lähetyksen johon oikeudet ja jonka viestin metadata mätchää
    Assertions.assertEquals(Seq(lahetysOikeudetMatch), kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet), metadataHakuLausekkeet = Option.apply(Map("avain" -> Seq("arvo1"))))._1)

    // haku lähettävällä palvelulla palauttaa lähetyksen johon oikeudet ja jonka lähettävä palvelu mätchää
    Assertions.assertEquals(Seq(lahetysOikeudetMatch), kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet), lahettavaPalveluHakuLauseke = Option.apply("lahettavaPalvelu1"))._1)

    // jos vastaanottajan lähettävä palvelu ei mätchää ei palauteta mitään
    Assertions.assertEquals(Seq.empty, kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet), lahettavaPalveluHakuLauseke = Option.apply("lahettavaPalvelu33"))._1)

    // haku aikavälillä palauttaa lähetykset joihin oikeudet ja jotka osuvat aikavälille
    Assertions.assertEquals(Seq(lahetysOikeudetEiMatch, lahetysOikeudetMatch), kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet),
      hakuAlkaen = Option.apply(Instant.now.minus(1, ChronoUnit.HOURS)), hakuPaattyen = Option.apply(Instant.now))._1)

    // haku aikavälillä jossa ei ole lähetyksiä ei palauta mitään - lasketaan sen varaan että data alustettu yli nanosekuntia aiemmin
    Assertions.assertEquals(Seq.empty, kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet),
      hakuAlkaen = Option.apply(Instant.now.minus(1, ChronoUnit.NANOS)), hakuPaattyen = Option.apply(Instant.now))._1)

    // haku pelkällä alkamissajalla
    Assertions.assertEquals(Seq(lahetysOikeudetEiMatch, lahetysOikeudetMatch), kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet),
      hakuAlkaen = Option.apply(Instant.now.minus(1, ChronoUnit.HOURS)), hakuPaattyen = Option.empty)._1)

    // haku pelkällä päättymisajalla
    Assertions.assertEquals(Seq.empty, kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet),
      hakuAlkaen = Option.empty, hakuPaattyen = Option.apply(Instant.now.minus(1, ChronoUnit.HOURS)))._1)


  /**
   * Tämä testi dokumentoi tekstihaun vaatimukset, päivitetään jos speksi muuttuu
   */
  @Test def testHaeLahetyksiaOtsikollaJaSisallolla(): Unit =
    val lahetys = tallennaLahetys(lahettavaPalvelu = "lahettavaPalvelu1", lahettavanVirkailijanOID = Some(LAHETTAJA_OID1));
    val lahetys2 = tallennaLahetys(lahettavaPalvelu = "lahettavaPalvelu1", lahettavanVirkailijanOID = Some(LAHETTAJA_OID1));

    val kayttooikeudet = Set(Kayttooikeus("oikeus1", Option.apply("organisaatio1")))

    val vastaanottaja = Kontakti(Option.apply("Vallu Vastaanottaja"), "vallu.vastaanottaja@example.com")

    // oikean datan esimerkkejä
    tallennaViesti(Seq(vastaanottaja), lahetys = lahetys, otsikko = "AMOSAA-työkalu poistuu käytöstä syyskuussa",
      sisalto = "Keväällä 2023 otettiin käyttöön uusi TOTSU-työkalu, joka korvasi vanhan AMOSAA -työkalun. Vanha AMOSAA-työkalu tullaan poistamaan käytöstä 29.9.2023. Vanhan AMOSAA -työkalun puolella luodut suunnitelmat näkyvät automaattisesti myös uudessa työkalussa ja uusi TOTSU -työkalu toimii samoilla käyttäjätunnuksilla kuin ennenkin.", kielet = Set(Kieli.FI),
      kayttooikeudet = kayttooikeudet)
    tallennaViesti(Seq(vastaanottaja), lahetys = lahetys2, otsikko = "ePerusteiden etusivua uudistetaan – osallistu testaukseen!",
      sisalto = "ePerusteet-palvelu on laajentunut viime vuosina ja palveluun on lisätty valtakunnallisten perusteiden ja paikallisten suunnitelmien lisäksi muutakin sisältöä (mm. digitaalisen osaamisen kuvaukset, määräyskokoelma). Tämän myötä etusivua on lähdetty uudistamaan erityisesti tiedon löydettävyyden näkökulmasta.", kielet = Set(Kieli.FI), kayttooikeudet = kayttooikeudet)

    val kayttooikeusTunnisteet = kantaOperaatiot.getKayttooikeusTunnisteet(kayttooikeudet.toSeq)._1

    // jos otsikko tai sisältö ei mätchää ei palauteta mitään
    Assertions.assertEquals(Seq.empty, kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet), sisaltoHakuLauseke = Option.apply("eiosumaa"))._1)
    // osaa matchata keskelle otsikkoa
    Assertions.assertEquals(Seq(lahetys2), kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet), sisaltoHakuLauseke = Option.apply("osallistu"))._1)
    // matchaus ei ole case-sensitiivinen
    Assertions.assertEquals(Seq(lahetys2), kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet), sisaltoHakuLauseke = Option.apply("oSaLLisTu"))._1)
    // osaa matchata keskelle sisältöä
    Assertions.assertEquals(Seq(lahetys2), kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet), sisaltoHakuLauseke = Option.apply("valtakunnallisten"))._1)
    // osaa matchata otsikosta löytyvän sanan osalla
    Assertions.assertEquals(Seq(lahetys), kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet), sisaltoHakuLauseke = Option.apply("syysku"))._1)
    // osaa matchata sisällöstä löytyvän sanan osalla
    Assertions.assertEquals(Seq(lahetys2), kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet), sisaltoHakuLauseke = Option.apply("digit"))._1)
    // osaa matchata ääkkösiä
    Assertions.assertEquals(Seq(lahetys), kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet), sisaltoHakuLauseke = Option.apply("käytöstä"))._1)
    // osaa matchata numeroita ja erikoismerkkejä
    Assertions.assertEquals(Seq(lahetys), kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet), sisaltoHakuLauseke = Option.apply("29.9.2023"))._1)
    // osaa matchata useamman otsikosta löytyvän sanan
    Assertions.assertEquals(Seq(lahetys2), kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet), sisaltoHakuLauseke = Option.apply("osallistu testaukseen"))._1)
    // osaa matchata useamman sisällöstä löytyvän sanan
    Assertions.assertEquals(Seq(lahetys), kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet), sisaltoHakuLauseke = Option.apply("tullaan poistamaan"))._1)
    // haku mätchäävällä sanan alkuosalla tuottaa osuman
    Assertions.assertEquals(Seq(lahetys2), kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet), sisaltoHakuLauseke = Option.apply("ePerus"))._1)

    // haku tekstillä jossa on väliviiva palauttaa lähetyksen jonka otsikko tai sisältö mätchää
    Assertions.assertEquals(Seq(lahetys), kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet), sisaltoHakuLauseke = Option.apply("AMOSAA-työkalu"))._1)
    Assertions.assertEquals(Seq(lahetys), kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet), sisaltoHakuLauseke = Option.apply("TOTSU-työkalu"))._1)
    Assertions.assertEquals(Seq(lahetys), kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet), sisaltoHakuLauseke = Option.apply("AMOSAA -työkalun"))._1)

    // useamman sanan haku matchaa vaikka sanojen välissä olisi muita sanoja kunhan sanat ovat halutussa järjestyksessä
    Assertions.assertEquals(Seq.empty, kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet), sisaltoHakuLauseke = Option.apply("syyskuussa poistuu"))._1)
    Assertions.assertEquals(Seq.empty, kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet), sisaltoHakuLauseke = Option.apply("poistuu syyskuussa"))._1)
    Assertions.assertEquals(Seq(lahetys), kantaOperaatiot.searchLahetykset(
      kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet), sisaltoHakuLauseke = Option.apply("poistuu käytöstä syyskuussa"))._1)


  @Test def testSearchLahetyksetAlkaen(): Unit =
    // luodaan kymmenen viestiä, otetaan viisi vanhinta
    val viestit = Range(0, 10).map(i => {
      Thread.sleep(5)
      tallennaViesti(otsikko = "Veneillään")._1
    })

    // haetaan valituille lähetykset ja uusin lähetyksen luontihetki
    val lahetykset = viestit.take(5).map(v => kantaOperaatiot.getLahetys(v.lahetysTunniste).get)
    val alkaen = viestit.take(6).last.lahetysTunniste

    // alkaen-haulla ilman kriteereitä saadaan valitut lähetykset luontijärjestyksessä
    Assertions.assertEquals(lahetykset.reverse, kantaOperaatiot.searchLahetykset(alkaen = Option.apply(alkaen), enintaan = 5)._1)

    // alkaen-haulla otsikko-kriteerillä saadaan valitut lähetykset luontijärjestyksessä
    Assertions.assertEquals(lahetykset.reverse, kantaOperaatiot.searchLahetykset(alkaen = Option.apply(alkaen), enintaan = 5, Option.empty,
      sisaltoHakuLauseke = Option.apply("veneillään"))._1)

    // alkaen-haulla ei-mätchäävällä otsikkokriteerillä ei saada mitään
    Assertions.assertEquals(Seq.empty, kantaOperaatiot.searchLahetykset(alkaen = Option.apply(alkaen), enintaan = 5,
      sisaltoHakuLauseke = Option.apply("lennetään"))._1)

  @Test def testSearchLahetyksetEnintaan(): Unit =
    // luodaan kymmenen viestiä ja haetaan lähetykset
    val viestit = Range(0, 10).map(i => {
      Thread.sleep(5)
      tallennaViesti(otsikko = "veneillään")._1
    })
    val lahetykset = viestit.map(v => kantaOperaatiot.getLahetys(v.lahetysTunniste).get)

    // limit-haku ilman kriteereitä palauttaa viisi uusinta
    Assertions.assertEquals(lahetykset.reverse.take(5), kantaOperaatiot.searchLahetykset(enintaan = 5)._1)

    // limit-haku mätchäävällä otsikkokriteerillä palauttaa viisi uusinta
    Assertions.assertEquals(lahetykset.reverse.take(5), kantaOperaatiot.searchLahetykset(enintaan = 5,
      sisaltoHakuLauseke = Option.apply("veneillään"))._1)

    // limit-haku ei-mätchäävällä otsikkokriteerillä ei palauta mitään
    Assertions.assertEquals(Seq.empty, kantaOperaatiot.searchLahetykset(enintaan = 5,
      sisaltoHakuLauseke = Option.apply("lennetään"))._1)

  @Test def testSearchLahetyksetHakutulosLukumaara(): Unit =
    // luodaan kymmenen viestiä + lähetystä
    val viestit = Range(0, 10).map(i => {
      Thread.sleep(5)
      tallennaViesti(otsikko = "veneillään")._1
    })
    // luodaan 5 viestiä + lähetystä
    val viestit2 = Range(0, 5).map(i => {
      Thread.sleep(5)
      tallennaViesti(otsikko = "autoillaan")._1
    })

    // enintään 5 tuloksen rajauksella saadaan 5 tulosta ja tieto että osumia on yhteensä 15
    val (hakutulos, onSeuraavia, osumienLkm) = kantaOperaatiot.searchLahetykset(enintaan = 5)
    Assertions.assertEquals(5, hakutulos.size)
    Assertions.assertEquals(true, onSeuraavia)
    Assertions.assertEquals(15, osumienLkm)
    // enintään 3 tuloksen rajauksella ja 10 lähetykseen osuvalla otsikkokriteerillä
    // saadaan 3 tulosta ja tieto että osumia on yhteensä 10
    val (hakutulos2, onSeuraavia2, osumienLkm2) = kantaOperaatiot.searchLahetykset(enintaan = 3,
      sisaltoHakuLauseke = Option.apply("veneillään"))
    Assertions.assertEquals(3, hakutulos2.size)
    Assertions.assertEquals(true, onSeuraavia2)
    Assertions.assertEquals(10, osumienLkm2)

    // limit-haku ei-mätchäävällä otsikkokriteerillä ei palauta mitään
    val (hakutulos3, onSeuraavia3, osumienLkm3) = kantaOperaatiot.searchLahetykset(enintaan = 5,
      sisaltoHakuLauseke = Option.apply("lennetään"))
    Assertions.assertEquals(0, hakutulos3.size)
    Assertions.assertEquals(false, onSeuraavia3)
    Assertions.assertEquals(0, osumienLkm3)

  @Test def testSearchLahetyksetSanitized(): Unit =
    // luodaan viesti jossa salaisuus
    val (viesti, _) = tallennaViesti(otsikko="Piilotettu avoin", sisalto="Julkinen salainen", kielet = Set(Kieli.FI), maskit = Map(("salainen", Option.apply("*****")), ("Piilotettu", Option.apply("*****"))))
    val lahetys = kantaOperaatiot.getLahetys(viesti.lahetysTunniste).get

    // salaisuuden perusteella ei voi hakea
    Assertions.assertEquals(Seq.empty, kantaOperaatiot.searchLahetykset(sisaltoHakuLauseke = Option.apply("salainen"))._1)
    Assertions.assertEquals(Seq.empty, kantaOperaatiot.searchLahetykset(sisaltoHakuLauseke = Option.apply("Piilotettu"))._1)

    // julkisen osan perusteella voi hakea
    Assertions.assertEquals(Seq(lahetys), kantaOperaatiot.searchLahetykset(sisaltoHakuLauseke = Option.apply("julkinen"))._1)
    Assertions.assertEquals(Seq(lahetys), kantaOperaatiot.searchLahetykset(sisaltoHakuLauseke = Option.apply("avoin"))._1)

  @Test def testSearchLahetyksetHtml(): Unit =
    val sisalto =
      """
        |<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
        |<html>
        |<head>
        |  <meta http-equiv="Content-Type" content="text/html; charset=utf-8">
        |  <meta http-equiv="Content-Style-Type" content="text/css">
        |  <title></title>
        |  <meta name="Generator" content="Cocoa HTML Writer">
        |  <meta name="CocoaVersion" content="2299.4">
        |  <style type="text/css">
        |    p.p1 {margin: 0.0px 0.0px 0.0px 0.0px; font: 16.0px Arial; color: #161616; -webkit-text-stroke: #161616; background-color: #ffffff}
        |    p.p2 {margin: 0.0px 0.0px 0.0px 0.0px; font: 16.0px Arial; color: #161616; -webkit-text-stroke: #161616; min-height: 18.0px}
        |    li.li4 {margin: 0.0px 0.0px 0.0px 0.0px; font: 16.0px Arial; color: #161616; -webkit-text-stroke: #161616}
        |    span.s1 {font-kerning: none}
        |    span.s2 {background-color: #ffffff; -webkit-text-stroke: 0px #000000}
        |    span.s3 {font-kerning: none; background-color: #ffffff}
        |    ol.ol1 {list-style-type: decimal}
        |  </style>
        |</head>
        |<body>
        |<p class="p1"><span class="s1">Ylioppilastutkinto järjestetään lukio-opintojen päätteeksi, keväällä tai syksyllä. Ylioppilaskirjoitukset pidetään yhtä aikaa kaikissa lukioissa ja lukiokoulutusta järjestävissä oppilaitoksissa.</span></p>
        |<p class="p2"><span class="s1"></span><br></p>
        |<h2 style="margin: 0.0px 0.0px 8.4px 0.0px; font: 24.0px Arial; color: #161616; -webkit-text-stroke: #161616; background-color: #ffffff"><span class="s1"><b>Mistä ylioppilastutkinto koostuu?</b></span></h2>
        |<p class="p1"><span class="s1">Ylioppilastutkinnon valmiiksi saamiseen vaaditaan viisi koetta. Äidinkielen ja kirjallisuuden kokeen suorittaminen vaaditaan kaikilta kokelailta, ja muut vaadittavat neljä koetta tulee valita seuraavista ryhmistä:</span></p>
        |<ol class="ol1">
        |  <li class="li4"><span class="s2"></span><span class="s3">vieras kieli</span></li>
        |  <li class="li4"><span class="s2"></span><span class="s3">toinen kotimainen kieli</span></li>
        |  <li class="li4"><span class="s2"></span><span class="s3">matematiikka</span></li>
        |  <li class="li4"><span class="s2"></span><span class="s3">reaaliaine.</span></li>
        |</ol>
        |</body>
        |</html>
        |""".stripMargin

    // luodaan viesti html-sisällöllä
    val (viesti, _) = tallennaViesti(sisalto=sisalto, sisallonTyyppi=HTML)
    val lahetys = kantaOperaatiot.getLahetys(viesti.lahetysTunniste).get

    // html-tagien perusteella ei voi hakea
    Assertions.assertEquals(Seq.empty, kantaOperaatiot.searchLahetykset(sisaltoHakuLauseke = Option.apply("head"))._1)

    // julkisen osan perusteella voi hakea
    Assertions.assertEquals(Seq(lahetys), kantaOperaatiot.searchLahetykset(sisaltoHakuLauseke = Option.apply("äidinkielen"))._1)

  @Test def testSearchVastaanottajat(): Unit =
    val lahetys = tallennaLahetys();

    val kayttooikeudet1 = Set(Kayttooikeus("oikeus1", Option.apply("organisaatio1")), Kayttooikeus("oikeus2", Option.apply("organisaatio1")))
    val kayttooikeudet2 = Set(Kayttooikeus("oikeus1", Option.apply("organisaatio2")), Kayttooikeus("oikeus2", Option.apply("organisaatio2")))

    val kontakti1 = Kontakti(Option.apply("Vallu Vastaanottaja"), "vallu.vastaanottaja@example.com")
    val kontakti2 = Kontakti(Option.apply("Veera Vastaanottaja"), "veera.vastaanottaja@example.com")
    val kontakti3 = Kontakti(Option.apply("Venla Vastaanottaja"), "venla.vastaanottaja@example.com")

    // tallennetaan lähetyksille useita viestejä jotta voidaan varmistua ettei tämä aiheuta duplikaatteja tuloksiin
    val (_, vastaanottajatOikeudetMatch) = tallennaViesti(Seq(kontakti1), lahetys = lahetys, otsikko = "Junaillaan junalla",
      sisalto = "Valtion rautatiet on hieno yritys", kielet = Set(Kieli.FI), kayttooikeudet = kayttooikeudet1,
      metadata = Map("avain" -> Seq("arvo1", "arvo2")))
    val (_, vastaanottajatOikeudetEiMatch) = tallennaViesti(Seq(kontakti2), lahetys = lahetys, otsikko = "Autoillaan bussilla",
      sisalto = "Onnibus rulettaa", kielet = Set(Kieli.FI), kayttooikeudet = kayttooikeudet1)
    val (_, vastaanottajatEiOikeuksiaMatch) = tallennaViesti(Seq(kontakti3), lahetys = lahetys, otsikko = "Junaillaan junalla",
      sisalto = "Valtion rautatiet on hieno yritys", kielet = Set(Kieli.FI), kayttooikeudet = kayttooikeudet2,
      metadata = Map("avain" -> Seq("arvo1", "arvo2")))

    val kayttooikeusTunnisteet = kantaOperaatiot.getKayttooikeusTunnisteet(kayttooikeudet1.toSeq)._1

    // haku pääkäyttäjänä ilman kriteereitä palauttaa kaikki vastaanottajat
    Assertions.assertEquals(Seq(vastaanottajatEiOikeuksiaMatch, vastaanottajatOikeudetEiMatch, vastaanottajatOikeudetMatch).flatten,
      kantaOperaatiot.searchVastaanottajat(lahetys.tunniste)._1)

    // haku ilman kriteerejä palauttaa kaikki lähetykset joihin oikeudet
    Assertions.assertEquals(Seq(vastaanottajatOikeudetEiMatch, vastaanottajatOikeudetMatch).flatten,
      kantaOperaatiot.searchVastaanottajat(lahetys.tunniste, kayttooikeusTunnisteet=Option.apply(kayttooikeusTunnisteet))._1)

    // haku otsikolla palauttaa lähetyksen johon oikeudet ja jonka otsikko mätchää
    Assertions.assertEquals(vastaanottajatOikeudetMatch,
      kantaOperaatiot.searchVastaanottajat(lahetys.tunniste, kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet),
        sisaltoHakuLauseke = Option.apply("Junaillaan"))._1)

    // jos otsikko ei mätchää ei palauteta mitään
    Assertions.assertEquals(Seq.empty,
      kantaOperaatiot.searchVastaanottajat(lahetys.tunniste, kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet),
        sisaltoHakuLauseke = Option.apply("Veneillään"))._1)

    // haku sisällöllä palauttaa lähetyksen johon oikeudet ja jonka sisältö mätchää
    Assertions.assertEquals(vastaanottajatOikeudetMatch,
      kantaOperaatiot.searchVastaanottajat(lahetys.tunniste, kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet),
        sisaltoHakuLauseke = Option.apply("valtion"))._1)

    // jos sisältö ei mätchää ei palauteta mitään
    Assertions.assertEquals(Seq.empty,
      kantaOperaatiot.searchVastaanottajat(lahetys.tunniste, kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet),
        sisaltoHakuLauseke = Option.apply("kunnan"))._1)

    // haku vastaanottajan sähköpostilla palauttaa lähetyksen johon oikeudet ja jonka vastaanottajan sähköposti mätchää
    Assertions.assertEquals(vastaanottajatOikeudetMatch,
      kantaOperaatiot.searchVastaanottajat(lahetys.tunniste, kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet),
        vastaanottajaHakuLauseke = Option.apply("vallu.vastaanottaja@example.com"))._1)

    // jos vastaanottajan sähköposti ei mätchää ei palauteta mitään
    Assertions.assertEquals(Seq.empty,
      kantaOperaatiot.searchVastaanottajat(lahetys.tunniste, kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet),
        vastaanottajaHakuLauseke = Option.apply("ville.vastaanottaja@example.com"))._1)

    // haku metadatalla palauttaa lähetyksen johon oikeudet ja jonka viestin metadata mätchää
    Assertions.assertEquals(vastaanottajatOikeudetMatch,
      kantaOperaatiot.searchVastaanottajat(lahetys.tunniste, kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet),
        metadataHakuLausekkeet = Option.apply(Map("avain" -> Seq("arvo1"))))._1)

    // jos metadata ei mätchää ei palauteta mitään
    Assertions.assertEquals(Seq.empty,
      kantaOperaatiot.searchVastaanottajat(lahetys.tunniste, kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet),
        metadataHakuLausekkeet = Option.apply(Map("avain" -> Seq("arvo33"))))._1)

    // haku tilalla palauttaa lähetyksen johon oikeudet ja jonka tila mätchää
    vastaanottajatOikeudetEiMatch.foreach(v => {
      kantaOperaatiot.paivitaVastaanottajaLahetetyksi(v.tunniste, "sesTunniste")
      kantaOperaatiot.paivitaVastaanotonTila("sesTunniste", DELIVERY, Option.empty)
    })
    Assertions.assertEquals(vastaanottajatOikeudetMatch,
      kantaOperaatiot.searchVastaanottajat(lahetys.tunniste, kayttooikeusTunnisteet = Option.apply(kayttooikeusTunnisteet),
        raportointiTila = Option.apply(RaportointiTila.kesken))._1)

  @Test def testSearchVastaanottajatAlkaen(): Unit =
    // luodaan viesti jolla kymmenen vastaanottajaa
    val kontaktit = Range(0, 10).map(i => Kontakti(Option.empty, "vastaanottaja" + i + "@oph.fi"))
    val (viesti, vastaanottajat) = tallennaViesti(vastaanottajat = kontaktit)

    val alkaen = vastaanottajat.reverse.take(5).last.tunniste

    // alkaen-haku palauttaa viisi vanhinta
    Assertions.assertEquals(vastaanottajat.take(5).reverse, kantaOperaatiot.searchVastaanottajat(
      lahetysTunniste=viesti.lahetysTunniste, alkaen = Option.apply(alkaen))._1)

  @Test def testSearchVastaanottajatEnintaan(): Unit =
    // luodaan viesti jolla kymmenen vastaanottajaa
    val kontaktit = Range(0, 10).map(i => Kontakti(Option.empty, "vastaanottaja" + i + "@oph.fi"))
    val (viesti, vastaanottajat) = tallennaViesti(vastaanottajat = kontaktit)

    // limit-haku palauttaa viisi uusinta
    Assertions.assertEquals(vastaanottajat.reverse.take(5), kantaOperaatiot.searchVastaanottajat(
      lahetysTunniste = viesti.lahetysTunniste, enintaan = 5)._1)

  /**
   * Testataan lähetyksien vastaanottotilojen haku raportointikäyttöliittymälle
   */
  @Test def testGetLahetystenVastaanottotilat(): Unit =
    // tallennetaan lähetys
    val lahetys1 = this.tallennaLahetys()
    // tallennetaan viesti
    val (viesti, vastaanottajat) = tallennaViesti(getVastaanottajat(5), lahetys = lahetys1,
      kayttooikeudet = kayttooikeudetOik1org1)
    val kayttooikeusTunnisteet = kantaOperaatiot.getKayttooikeusTunnisteet(kayttooikeudetOik1org1.toSeq)._1
    val kayttooikeusTunnisteet2 = kantaOperaatiot.getKayttooikeusTunnisteet(Seq(Kayttooikeus("OIKEUS2", Some(ORGANISAATIO1))))._1
    val vastaanottajanTunniste = vastaanottajat.head.tunniste
    val toisenVastaanottajanTunniste = vastaanottajat.last.tunniste

    // päivitetään yhden vastaanottajan tila lähetetyksi
    kantaOperaatiot.paivitaVastaanottajaLahetetyksi(vastaanottajanTunniste, "ses-tunniste")
    // päivitetään toisen vastaanottajan tila virhetilaan
    kantaOperaatiot.paivitaVastaanottajaVirhetilaan(toisenVastaanottajanTunniste, "lisätiedot")

    val vastaanottotilat = kantaOperaatiot.getLahetystenVastaanottotilat(Seq(lahetys1.tunniste), Some(kayttooikeusTunnisteet))
      .get(lahetys1.tunniste).get
    // tiloista löytyy kesken, lähetetty, ja virhe
    Assertions.assertEquals(3, vastaanottotilat.size)
    Assertions.assertEquals(1, vastaanottotilat.filter(tila => tila._1.equals(VastaanottajanTila.LAHETETTY.toString)).head._2)
    Assertions.assertEquals(1, vastaanottotilat.filter(tila => tila._1.equals(VastaanottajanTila.VIRHE.toString)).head._2)
    Assertions.assertEquals(3, vastaanottotilat.filter(tila => tila._1.equals(VastaanottajanTila.ODOTTAA.toString)).head._2)
    Assertions.assertEquals(Map.empty, kantaOperaatiot.getLahetystenVastaanottotilat(Seq(lahetys1.tunniste), Some(kayttooikeusTunnisteet2)))

  /**
   * Testataan lähetyksien maskien haku raportointikäyttöliittymälle
   */
  @Test def testGetLahetystenMaskit(): Unit =
    // tallennetaan lähetys
    val lahetys1 = this.tallennaLahetys()
    val maskit: Map[String, Option[String]] = Map("salaisuus1" -> Some("peitetty1"), "salaisuus2" -> Some("peitetty2"))
    val maskit2: Map[String, Option[String]] = Map("salaisuus1" -> Some("peitettyx"), "salaisuus3" -> Some("peitetty3"))
    val maskit3: Map[String, Option[String]] = Map("salaisuus" -> Some("peitetty"))
    // tallennetaan viestejä eri maskeilla
    tallennaViesti(getVastaanottajat(5), lahetys = lahetys1,
      kayttooikeudet = kayttooikeudetOik1org1, maskit = maskit)
    tallennaViesti(getVastaanottajat(2), lahetys = lahetys1,
    kayttooikeudet = kayttooikeudetOik1org1, maskit = maskit2)
    val lahetys2 = this.tallennaLahetys()
    tallennaViesti(getVastaanottajat(1), lahetys = lahetys2,
      kayttooikeudet = kayttooikeudetOik1org1, maskit = maskit3)
    // tallennetaan viesti ilman maskeja
    val lahetys3 = this.tallennaLahetys()
    tallennaViesti(getVastaanottajat(1), lahetys = lahetys3,
      kayttooikeudet = Set(Kayttooikeus("OIKEUS2", Some(ORGANISAATIO1))), maskit=Map.empty)

    val kayttooikeusTunnisteet = kantaOperaatiot.getKayttooikeusTunnisteet(kayttooikeudetOik1org1.toSeq)._1
    val kayttooikeusTunnisteet2 = kantaOperaatiot.getKayttooikeusTunnisteet(Seq(Kayttooikeus("OIKEUS2", Some(ORGANISAATIO1))))._1

    val lahetystenMaskit = kantaOperaatiot.getLahetystenMaskit(Seq(lahetys1.tunniste, lahetys2.tunniste, lahetys3.tunniste), Some(kayttooikeusTunnisteet))
    // tiloista löytyy maskit tai ei löydy tunnistetta
    Assertions.assertEquals(2, lahetystenMaskit.size)
    Assertions.assertEquals(3, lahetystenMaskit.get(lahetys1.tunniste).get.size)
    Assertions.assertEquals(Map("salaisuus1" -> Some("peitettyx"), "salaisuus2" -> Some("peitetty2"), "salaisuus3" -> Some("peitetty3")), lahetystenMaskit.get(lahetys1.tunniste).get)
    Assertions.assertEquals(1, lahetystenMaskit.get(lahetys2.tunniste).get.size)
    Assertions.assertEquals(Map("salaisuus" -> Some("peitetty")), lahetystenMaskit.get(lahetys2.tunniste).get)
    Assertions.assertEquals(None, lahetystenMaskit.get(lahetys3.tunniste))
    // pääkäyttäjä
    val lahetystenMaskitAdmin = kantaOperaatiot.getLahetystenMaskit(Seq(lahetys1.tunniste, lahetys2.tunniste, lahetys3.tunniste), Option.empty)
    Assertions.assertEquals(2, lahetystenMaskitAdmin.size)
    // maskit vaan lähetyksiin joihin on oikeudet
    val lahetystenMaskitEiOikkia = kantaOperaatiot.getLahetystenMaskit(Seq(lahetys1.tunniste, lahetys2.tunniste, lahetys3.tunniste), Some(kayttooikeusTunnisteet2))
    Assertions.assertEquals(Map.empty, lahetystenMaskitEiOikkia)

  /**
   * Testataan lähetyksen viestien lukumäärän haku
   */
  @Test def testGetLahetyksenViestiLkm(): Unit =
    // lähetys jossa yksi viesti
    val lahetys = this.tallennaLahetys()
    tallennaViesti(getVastaanottajat(1), lahetys = lahetys, kayttooikeudet = kayttooikeudetOik1org1)
    // massalähetys useammalla viestillä
    val lahetys2 = this.tallennaLahetys()
    tallennaViesti(getVastaanottajat(5), lahetys = lahetys2, kayttooikeudet = kayttooikeudetOik1org1)
    tallennaViesti(getVastaanottajat(1), lahetys = lahetys2, kayttooikeudet = Set(Kayttooikeus("OIKEUS2", Some(ORGANISAATIO2))))
    Assertions.assertEquals(1, kantaOperaatiot.getLahetyksenViestiLkm(lahetys.tunniste))
    Assertions.assertEquals(2, kantaOperaatiot.getLahetyksenViestiLkm(lahetys2.tunniste))

  /**
   * Käyttöoikeustunnisteiden haku käyttöoikeuksilla
   */
  @Test def testGetUusinKayttooikeusTunniste(): Unit =
    val kayttooikeudet1 = Set(Kayttooikeus("oikeus1", Option.apply("organisaatio1")), Kayttooikeus("oikeus2", Option.apply("organisaatio1")))
    val kayttooikeudet2 = Set(Kayttooikeus("oikeus1", Option.apply("organisaatio2")), Kayttooikeus("oikeus2", Option.apply("organisaatio2")))
  
    tallennaViesti(kayttooikeudet = kayttooikeudet1)
    val tunniste = kantaOperaatiot.getUusinKayttooikeusTunniste()
    // jos luodaan viesti samoilla oikeuksilla, ei tule uusia tunnisteita
    tallennaViesti(kayttooikeudet = kayttooikeudet1)
    Assertions.assertEquals(tunniste, kantaOperaatiot.getUusinKayttooikeusTunniste())
    // jos tulee uusi oikeus, tulee myös uudempi tunniste
    tallennaViesti(kayttooikeudet = kayttooikeudet2)
    Assertions.assertTrue(tunniste < kantaOperaatiot.getUusinKayttooikeusTunniste())

  /**
   * Testataan viestin tietojen haku lähetystunnuksella ja viestitunnuksella
   */
  @Test def testMassaViestiLahetystunnuksella(): Unit =
    // lähetys jossa yksi viesti
    val lahetys = this.tallennaLahetys()
    val (viesti, vastaanottajat) = tallennaViesti(getVastaanottajat(5), lahetys = lahetys, kayttooikeudet = kayttooikeudetOik1org1)
    // eri oikeuksilla
    val lahetys2 = this.tallennaLahetys()
    val (viesti2, vastaanottajat2) = tallennaViesti(getVastaanottajat(5), lahetys = lahetys2, kayttooikeudet = Set(Kayttooikeus("OIKEUS2", Some(ORGANISAATIO1))))
    val kayttooikeusTunnisteet = kantaOperaatiot.getKayttooikeusTunnisteet(kayttooikeudetOik1org1.toSeq)._1
    Assertions.assertEquals(
      RaportointiViesti(viesti.tunniste, viesti.lahetysTunniste,viesti.otsikko,viesti.sisalto,viesti.sisallonTyyppi,viesti.kielet,viesti.maskit,viesti.omistaja,viesti.prioriteetti),
      kantaOperaatiot.getMassaViestiLahetystunnisteella(lahetys.tunniste, Some(kayttooikeusTunnisteet)).get)
    // pääkäyttäjä
    Assertions.assertEquals(
      RaportointiViesti(viesti.tunniste, viesti.lahetysTunniste, viesti.otsikko, viesti.sisalto, viesti.sisallonTyyppi, viesti.kielet, viesti.maskit, viesti.omistaja, viesti.prioriteetti),
      kantaOperaatiot.getMassaViestiLahetystunnisteella(lahetys.tunniste, Option.empty).get)
    // ei käyttöoikeuksia
    Assertions.assertEquals(Option.empty, kantaOperaatiot.getMassaViestiLahetystunnisteella(lahetys2.tunniste, Some(kayttooikeusTunnisteet)))


  /**
   * Testataan viestin tietojen haku lähetystunnuksella ja viestitunnuksella
   */
  @Test def testRaportointiViestiTunnuksella(): Unit =
    val lahetys = this.tallennaLahetys()
    val (viesti, vastaanottajat) = tallennaViesti(getVastaanottajat(5), lahetys = lahetys, kayttooikeudet = kayttooikeudetOik1org1)
    val kayttooikeusTunnisteet = kantaOperaatiot.getKayttooikeusTunnisteet(kayttooikeudetOik1org1.toSeq)._1
    // eri oikeuksilla
    val lahetys2 = this.tallennaLahetys()
    val (viesti2, vastaanottajat2) = tallennaViesti(getVastaanottajat(5), lahetys = lahetys2, kayttooikeudet = Set(Kayttooikeus("OIKEUS2", Some(ORGANISAATIO1))))
    Assertions.assertEquals(
      RaportointiViesti(viesti.tunniste, viesti.lahetysTunniste, viesti.otsikko, viesti.sisalto, viesti.sisallonTyyppi, viesti.kielet, viesti.maskit, viesti.omistaja, viesti.prioriteetti),
      kantaOperaatiot.getRaportointiViestiTunnisteella(viesti.tunniste, Some(kayttooikeusTunnisteet)).get)
    // pääkäyttäjä
    Assertions.assertEquals(
      RaportointiViesti(viesti.tunniste, viesti.lahetysTunniste, viesti.otsikko, viesti.sisalto, viesti.sisallonTyyppi, viesti.kielet, viesti.maskit, viesti.omistaja, viesti.prioriteetti),
      kantaOperaatiot.getRaportointiViestiTunnisteella(viesti.tunniste, Option.empty).get)
    // ei käyttöoikeuksia
    Assertions.assertEquals(Option.empty, kantaOperaatiot.getRaportointiViestiTunnisteella(viesti2.tunniste, Some(kayttooikeusTunnisteet)))

  /**
   * Testataan lähettävien palvelujen listan haku
   */
  @Test def testGetLahettavatPalvelut(): Unit =
    // lähetyksiä eri palveluilla
    val lahetys = this.tallennaLahetys(lahettavaPalvelu = "hakemuspalvelu")
    val lahetys2 = this.tallennaLahetys(lahettavaPalvelu = "osoitepalvelu")
    val lahetys3 = this.tallennaLahetys(lahettavaPalvelu = "jokupalvelu")
    // sama palvelu
    val lahetys4 = this.tallennaLahetys(lahettavaPalvelu = "hakemuspalvelu")
    // Esimerkkipalvelu on alustettuna kantaan
    Assertions.assertEquals(4, kantaOperaatiot.getLahettavatPalvelut().size)
    Assertions.assertEquals(List("Esimerkkipalvelu", "hakemuspalvelu", "jokupalvelu", "osoitepalvelu"), kantaOperaatiot.getLahettavatPalvelut())
}
