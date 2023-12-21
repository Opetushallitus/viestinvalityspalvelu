package fi.oph.viestinvalitys.business

import com.github.f4b6a3.uuid.UuidCreator
import fi.oph.viestinvalitys.business.VastaanottajanTila
import org.slf4j.LoggerFactory
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.PostgresProfile.api.*

import java.time.Instant
import java.util.UUID
import java.util.concurrent.{Executor, Executors}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.DurationInt

object LahetysOperaatiot {
  val executionContext: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(8))
}

/**
 * Lähetykseen liittyvät business-operaatiot
 *
 * @param db  oletuskannan sijaan käytettävä tietokanta (testejä varten)
 */
class LahetysOperaatiot(db: JdbcBackend.JdbcDatabaseDef) {

  implicit val executionContext: ExecutionContext = LahetysOperaatiot.executionContext

  val LOG = LoggerFactory.getLogger(classOf[LahetysOperaatiot]);

  def getUUID(): UUID =
    // käytetään aikaperustaisia UUID:tä kahdesta syystä:
    // - sivuttavissa endpointeissa entiteetit voidaan järjestää luomisjärjestykseen UUID:n perusteella
    // - indeksien päivittäminen on kevyempi operaatio kun avaimen arvot eivät ole satunnaisia
    UuidCreator.getTimeOrderedEpoch()

  /**
   * Tallentaa uuden lähetyksen.
   *
   * @param otsikko                 lähetyksen otsikko
   * @param omistaja                lähetyksen omistaja (luoja), vain sama omistaja voi liittää lähetykseen viestejä
   * @param kayttooikeusRajoitukset oikeudet joista jokin käyttäjällä pitää olla lähetykset katselemiseksi
   *
   * @return          tallennettu lähetys
   */
  def tallennaLahetys(otsikko: String, kayttooikeusRajoitukset: Set[String], omistaja: String): Lahetys = {
    val lahetysTunniste = this.getUUID()
    val lahetysInsertAction = sqlu"""INSERT INTO lahetykset VALUES(${lahetysTunniste.toString}::uuid, ${otsikko}, ${omistaja}, now())"""

    val kayttooikeusInsertActions = DBIO.sequence(kayttooikeusRajoitukset.map(kayttooikeus => {
      val tunniste = this.getUUID()
      sqlu"""
            INSERT INTO lahetykset_kayttooikeudet VALUES(${lahetysTunniste.toString}::uuid, ${kayttooikeus})
          """
    }))

    Await.result(db.run(DBIO.sequence(Seq(lahetysInsertAction, kayttooikeusInsertActions)).transactionally), 5.seconds)
    Lahetys(lahetysTunniste, otsikko, omistaja)
  }

  /**
   * Palauttaa lähetyksen
   *
   * @param tunniste  lähetyksen tunniste
   * @return          tunnistetta vastaava lähetys
   */
  def getLahetys(tunniste: UUID): Option[Lahetys] =
    Await.result(db.run(
      sql"""
            SELECT tunniste, otsikko, omistaja
            FROM lahetykset
            WHERE tunniste=${tunniste.toString}::uuid
         """.as[(String, String, String)].headOption), 5.seconds)
      .map((tunniste, otsikko, omistaja) => Lahetys(UUID.fromString(tunniste), otsikko, omistaja))

  /**
   * Palauttaa lähetyksen katseluun vaadittavat käyttöoikeudet
   *
   * @param lahetysTunniste lähetyksen tunniste
   * @return                vaadittavat käyttöoikeudet
   */
  def getLahetyksenKayttooikeudet(lahetysTunniste: UUID): Set[String] =
    val kayttooikeudetQuery =
      sql"""
            SELECT kayttooikeus
            FROM lahetykset_kayttooikeudet
            WHERE lahetys_tunniste =${lahetysTunniste.toString}::uuid
         """.as[String]

    Await.result(db.run(kayttooikeudetQuery), 5.seconds).toSet

  /**
   * Tallentaa uuden liitteen
   *
   * @param nimi        liitteen tiedostonimi
   * @param contentType liitteen tiedostotyyppi
   * @param koko        tiedoston koko (tavua)
   * @param omistaja    liitteen omistaja (luoja), vain sama omistaja voi liittää liitteen viesteihin
   * @return            tallennettu liite
   */
  def tallennaLiite(nimi: String, contentType: String, koko: Int, omistaja: String): Liite =
    val tunniste = this.getUUID()
    val insertAction =
      sqlu"""
            INSERT INTO liitteet
            VALUES(${tunniste.toString}::uuid, ${nimi}, ${contentType}, ${koko}, ${omistaja}, ${LiitteenTila.SKANNAUS.toString}, now())"""
    Await.result(db.run(insertAction), 5.seconds)
    Liite(tunniste, nimi, contentType, koko, omistaja, LiitteenTila.SKANNAUS)

  /**
   * Päivittää liitteen tilan. Tätä käytetään virusskannauksen tuloksen päivittämiseen liitteelle.
   * Mikäli liite päivitetään tilaan PUHDAS, päivitetään liitteen sisältävien viestien vastaanottajat
   * odottamaan lähetystä, mikäli viestillä ei ole muita ei PUHDAS-tilassa olevia liitteitä.
   *
   * @param tunniste  päivitettävän liitteen tunniste
   * @param tila      uusi tila
   */
  def paivitaLiitteenTila(tunniste: UUID, tila: LiitteenTila): Unit =
    // lukitaan kaikki ei puhtaat liitteet, tämä siksi ettei kaksi rinnakkaista saman viestin liitteen
    // päivitystä ei näe toisiaan ei-puhtaina, jolloin viestin vastaanottajia ei päivitettäisi
    val lukitseLiitteetAction =
      sql"""
            SELECT tunniste
            FROM liitteet WHERE tila<>${LiitteenTila.PUHDAS.toString}
            ORDER BY tunniste -- lukot pitää hakea aina samassa järjestykessä, muuten voi tulla deadlock
            FOR UPDATE
         """.as[String]

    val paivitaVastaanottajienTilaAction = {
      if(tila!=LiitteenTila.PUHDAS)
        sql"""SELECT 0""".as[Int]
      else
        sqlu"""
              -- etsitään viestit joilla tasan yksi ei puhdas liite, eli liite jonka tilaa ollaan nyt päivittämässä
              WITH muutettavat_viestit AS (
                SELECT liite.viesti_tunniste AS tunniste
                FROM viestit_liitteet AS liite
                JOIN viestit_liitteet AS muut_liitteet ON liite.viesti_tunniste=muut_liitteet.viesti_tunniste
                JOIN liitteet ON muut_liitteet.liite_tunniste=liitteet.tunniste
                WHERE liite.liite_tunniste=${tunniste.toString}::uuid
                AND liitteet.tila<>${LiitteenTila.PUHDAS.toString}
                GROUP BY liite.viesti_tunniste
                HAVING count(1)=1
              ),
              -- viestien perusteella haetaan vastaanottajat
              muutettavat_vastaanottajat AS (
                SELECT vastaanottajat.tunniste AS tunniste
                FROM muutettavat_viestit
                JOIN vastaanottajat ON muutettavat_viestit.tunniste=vastaanottajat.viesti_tunniste
              )
              -- jotka päivitetään odottamaan lähetystä
              UPDATE vastaanottajat SET tila=${VastaanottajanTila.ODOTTAA.toString}
              FROM muutettavat_vastaanottajat
              WHERE vastaanottajat.tunniste=muutettavat_vastaanottajat.tunniste
            """
    }

    val paivitaLiitteenTilaAction = sqlu"""UPDATE liitteet SET tila=${tila.toString} WHERE tunniste=${tunniste.toString}::uuid"""

    Await.result(db.run(DBIO.sequence(Seq(lukitseLiitteetAction, paivitaVastaanottajienTilaAction, paivitaLiitteenTilaAction)).transactionally), 5.seconds)

  /**
   * Hakee liitteitä. Tätä käytetään luotavien viestien validointiin (liitteet olemassa, viestin koko sallituissa rajoissa)
   *
   * @param tunnisteet  haettavien liitteiden tunnisteet
   * @return            löydetyt liitteet
   */
  def getLiitteet(tunnisteet: Seq[UUID]): Seq[Liite] =
    if(tunnisteet.isEmpty) return Seq.empty

    val liiteQuery =
      sql"""
            SELECT tunniste, nimi, contenttype, koko, omistaja, tila
            FROM liitteet
            WHERE tunniste IN (#${tunnisteet.map(tunniste => "'" + tunniste + "'").mkString(",")})
         """
        .as[(String, String, String, Int, String, String)]
    Await.result(db.run(liiteQuery), 5.seconds)
      .map((tunniste, nimi, contentType, koko, omistaja, tila)
      => Liite(UUID.fromString(tunniste), nimi, contentType, koko, omistaja, LiitteenTila.valueOf(tila)))

  /**
   * Tallentaa uuden viestin
   *
   * @return  tallennettu viesti
   */
  def tallennaViesti(
                      otsikko: String,
                      sisalto: String,
                      sisallonTyyppi: SisallonTyyppi,
                      kielet: Set[Kieli],
                      maskit: Map[String, Option[String]],
                      lahettavanVirkailijanOID: Option[String],
                      lahettaja: Kontakti,
                      replyTo: Option[String],
                      vastaanottajat: Seq[Kontakti],
                      liiteTunnisteet: Seq[UUID],
                      lahettavaPalvelu: Option[String],
                      lahetysTunniste: Option[UUID],
                      prioriteetti: Prioriteetti,
                      sailytysAika: Int,
                      kayttooikeusRajoitukset: Set[String],
                      metadata: Map[String, Seq[String]],
                      omistaja: String
                         ): (Viesti, Seq[Vastaanottaja]) = {

    // luodaan lähetystunniste mikäli ei valmiiksi annettu
    val finalLahetysTunniste = {
      if(lahetysTunniste.isDefined) lahetysTunniste.get
      else this.getUUID()
    }
    val lahetysInsertAction = {
      if(lahetysTunniste.isDefined) sql"""SELECT 1""".as[Int]
      else sqlu"""INSERT INTO lahetykset VALUES(${finalLahetysTunniste.toString}::uuid, ${otsikko}, ${omistaja}, now())"""
    }
    val kayttooikeusInsertActions = {
      if(lahetysTunniste.isDefined) sql"""SELECT 1""".as[Int]
      else DBIO.sequence(kayttooikeusRajoitukset.map(kayttooikeus => {
        val tunniste = this.getUUID()
        sqlu"""
        INSERT INTO lahetykset_kayttooikeudet VALUES(${finalLahetysTunniste.toString}::uuid, ${kayttooikeus})
      """
      }))
    }

    // tallennetaan viesti
    val viestiTunniste = this.getUUID()
    val viestiInsertAction =
      sqlu"""
             INSERT INTO viestit
             VALUES(${viestiTunniste.toString}::uuid, ${finalLahetysTunniste.toString}::uuid,
                    ${otsikko}, ${sisalto}, ${sisallonTyyppi.toString}, ${kielet.contains(Kieli.FI)},
                    ${kielet.contains(Kieli.SV)}, ${kielet.contains(Kieli.EN)}, ${lahettavanVirkailijanOID},
                    ${lahettaja.nimi}, ${lahettaja.sahkoposti}, ${replyTo}, ${lahettavaPalvelu}, ${prioriteetti.toString}::prioriteetti, ${omistaja},
                    ${Instant.now.toString}::timestamptz, ${Instant.now.plusSeconds(60*60*24*sailytysAika).toString}::timestamptz
                    )
          """

    // tallennetaan metadata
    val metadataInsertActions = DBIO.sequence(metadata.map((avain, arvot) => {
      DBIO.sequence(arvot.map(arvo => {
        DBIO.sequence(Seq(
          sqlu"""INSERT INTO metadata_avaimet VALUES(${avain}) ON CONFLICT (avain) DO NOTHING""",
          sqlu"""
                 INSERT INTO metadata VALUES(${avain}, ${arvo}, ${viestiTunniste.toString}::uuid)
              """
        ))
      }))
    }))

    // tallennetaan maskit
    val maskitInsertActions = DBIO.sequence(maskit.map((salaisuus, maski) => {
      DBIO.sequence(Seq(
        sqlu"""
           INSERT INTO maskit VALUES(${viestiTunniste.toString}::uuid, ${salaisuus}, ${maski})
        """
      ))
    }))

    // lukitaan viestin liitteet, tämä on pakko tehdä kahdesta syystä:
    //  - viestit_liitteet taulun foreign key liitteet tauluun johtaa siihen että lisättäessä viesteihin liiteitä
    //    liitteet-taulun vastavat rivit lukitaan, jos lukkoja ei haeta aina samassa (tunniste-) järjestyksessä
    //    seuraa deadlockeja
    //  - kun liite päivitetään PUHDAS-tilaan ja sen seurauksena vastaanottajat päivitetään lähestyvalmiiksi, täytyy
    //    varmistua ettei samaan aikaan olla lisäämässä uusia samoista liitteistä riippuvaisia vastaanottajia jotka
    //    voisivat jäädä päivittämättä
    var vastaanottajaEntiteetit: Seq[Vastaanottaja] = null
    val lukitseLiitteetAction = {
      if(liiteTunnisteet.size==0)
        sql"""SELECT 1 WHERE false"""
      else
        sql"""
             SELECT tunniste, tila
             FROM liitteet
             WHERE tunniste IN (#${liiteTunnisteet.map(tunniste => "'" + tunniste + "'").mkString(",")})
             -- lukitaan liitteet aina samassa järjestyksessä ettei tule deadlockia
             ORDER BY tunniste ASC
             FOR UPDATE
           """
    }.as[(String, String)]
    val liiteRelatedInsertActions = lukitseLiitteetAction.flatMap(liitteet => {
      // linkataan viestin liitteet
      val viestitLiitteetInsertActions = DBIO.sequence(liiteTunnisteet.zip(0 until liiteTunnisteet.size).map((liiteTunniste, index) => {
        sqlu"""
               INSERT INTO viestit_liitteet VALUES(${viestiTunniste.toString}::uuid, ${liiteTunniste.toString}::uuid, ${index})
            """
      }))

      val eiPuhtaatLiitteet = liitteet.filter((tunniste, tila) => LiitteenTila.valueOf(tila) != LiitteenTila.PUHDAS)
      val tila = {
        if(eiPuhtaatLiitteet.size==0) VastaanottajanTila.ODOTTAA
        else VastaanottajanTila.SKANNAUS
      }

      // tallennetaan vastaanottajat
      vastaanottajaEntiteetit = vastaanottajat.map(vastaanottaja => Vastaanottaja(this.getUUID(), viestiTunniste, vastaanottaja, tila, prioriteetti, Option.empty))
      val vastaanottajaInsertActions = DBIO.sequence(vastaanottajaEntiteetit.map(vastaanottaja => {
        sqlu"""
               INSERT INTO vastaanottajat
               VALUES(${vastaanottaja.tunniste.toString}::uuid, ${viestiTunniste.toString}::uuid, ${vastaanottaja.kontakti.nimi},
                ${vastaanottaja.kontakti.sahkoposti}, ${vastaanottaja.tila.toString}, now(), ${prioriteetti.toString}::prioriteetti)
            """
      }))

      // tallennetaan vastaanottajien tilasiirtymä
      val vastaanottajanSiirtymaActions = DBIO.sequence(vastaanottajaEntiteetit.map(vastaanottaja => {
        sqlu"""
               INSERT INTO vastaanottaja_siirtymat
               VALUES(${vastaanottaja.tunniste.toString}::uuid, now(), ${vastaanottaja.tila.toString}, null)
            """
      }))

      DBIO.sequence(Seq(viestitLiitteetInsertActions, vastaanottajaInsertActions, vastaanottajanSiirtymaActions))
    })

    Await.result(db.run(DBIO.sequence(Seq(lahetysInsertAction, kayttooikeusInsertActions, viestiInsertAction, metadataInsertActions, maskitInsertActions, liiteRelatedInsertActions)).transactionally), 5.seconds)
    (Viesti(viestiTunniste, finalLahetysTunniste, otsikko, sisalto, sisallonTyyppi, kielet, maskit, lahettavanVirkailijanOID, lahettaja, replyTo, lahettavaPalvelu, omistaja, prioriteetti), vastaanottajaEntiteetit)
  }

  /**
   * Palauttaa käyttäjän luomien korkean prioriteetin viestien määrän annetun aikaikkunan sisällä. Tätä käytetään
   * korkean prioriteetin viestien määrän rajoittamiseen.
   *
   * @param omistaja  käyttäjä jonka luomien viestien määrä palautetaan
   * @param sekuntia  aikaikkuna nykyhetkestä taaksepäin
   * @return          käyttäjän luomien korkean prioriteetin viestien määrä aikaikkunassa
   */
  def getKorkeanPrioriteetinViestienMaaraSince(omistaja: String, sekuntia: Int): Int =
    val maaraAction = sql"""
          SELECT count(1)
          FROM viestit
          WHERE prioriteetti=${Prioriteetti.KORKEA.toString}::prioriteetti
          AND omistaja=${omistaja}
          AND luotu>${Instant.now.minusSeconds(sekuntia).toString}::timestamptz
       """.as[Int]
    Await.result(db.run(maaraAction), 5.seconds).find(i => true).get

  def getVastaanottajat(vastaanottajaTunnisteet: Seq[UUID]): Seq[Vastaanottaja] =
    if(vastaanottajaTunnisteet.isEmpty) return Seq.empty

    val vastaanottajatQuery =
      sql"""
          SELECT tunniste, viesti_tunniste, nimi, sahkopostiosoite, tila, prioriteetti, ses_tunniste
          FROM vastaanottajat
          WHERE tunniste IN (#${vastaanottajaTunnisteet.map(tunniste => "'" + tunniste + "'").mkString(",")})
       """
        .as[(String, String, String, String, String, String, String)]
    Await.result(db.run(vastaanottajatQuery), 5.seconds)
      .map((tunniste, viestiTunniste, nimi, sahkopostiOsoite, tila, prioriteetti, sesTunniste)
      => Vastaanottaja(UUID.fromString(tunniste), UUID.fromString(viestiTunniste), Kontakti(Option.apply(nimi), sahkopostiOsoite), VastaanottajanTila.valueOf(tila), Prioriteetti.valueOf(prioriteetti), Option.apply(sesTunniste)))

  private def toKielet(kieletFi: Boolean, kieletSv: Boolean, kieletEn: Boolean): Set[Kieli] =
    var kielet: Seq[Kieli] = Seq.empty
    if (kieletFi) kielet = kielet.appended(Kieli.FI)
    if (kieletSv) kielet = kielet.appended(Kieli.SV)
    if (kieletEn) kielet = kielet.appended(Kieli.EN)
    kielet.toSet

  def getViestit(viestiTunnisteet: Seq[UUID]): Seq[Viesti] =
    if(viestiTunnisteet.isEmpty) return Seq.empty

    val viestitQuery =
      sql"""
          SELECT tunniste, lahetys_tunniste, otsikko, sisalto, sisallontyyppi, kielet_fi, kielet_sv, kielet_en, lahettavanvirkailijanoid,
                lahettajannimi, lahettajansahkoposti, replyto, lahettavapalvelu, omistaja, prioriteetti
          FROM viestit
          WHERE tunniste IN (#${viestiTunnisteet.map(tunniste => "'" + tunniste + "'").mkString(",")})
       """
        .as[(String, String, String, String, String, Boolean, Boolean, Boolean, String, String, String, String, String, String, String)]

    val maskitQuery =
      sql"""
          SELECT viesti_tunniste, salaisuus, maski
          FROM maskit
          WHERE viesti_tunniste IN (#${viestiTunnisteet.map(tunniste => "'" + tunniste + "'").mkString(",")})
      """
        .as[(String, String, String)]
    val maskit: Map[String, Map[String, Option[String]]] = Await.result(db.run(maskitQuery), 5.seconds)
      .groupBy((viestiTunniste, salaisuus, maski) => viestiTunniste)
      .map((viestiTunniste, maskit) => viestiTunniste -> maskit.map((viestiTunniste, salaisuus, maski) => salaisuus -> Option.apply(maski)).toMap)

    Await.result(db.run(viestitQuery), 5.seconds)
      .map((tunniste, lahetysTunniste, otsikko, sisalto, sisallonTyyppi, kieletFi, kieletSv, kieletEn, lahettavanVirkailijanOid,
            lahettajanNimi, lahettajanSahkoposti, replyTo, lahettavaPalvelu, omistaja, prioriteetti)
      => Viesti(UUID.fromString(tunniste), UUID.fromString(lahetysTunniste), otsikko, sisalto, SisallonTyyppi.valueOf(sisallonTyyppi),
          toKielet(kieletFi, kieletSv, kieletEn), maskit.get(tunniste).getOrElse(Map.empty), Option.apply(lahettavanVirkailijanOid), Kontakti(Option.apply(lahettajanNimi), lahettajanSahkoposti),
          Option.apply(replyTo), Option.apply(lahettavaPalvelu), omistaja, Prioriteetti.valueOf(prioriteetti)))

  def getViestinLiitteet(viestiTunnisteet: Seq[UUID]): Map[UUID, Seq[Liite]] =
    if(viestiTunnisteet.isEmpty) return Map.empty

    val liitteetQuery =
      sql"""
          SELECT viestit_liitteet.viesti_tunniste, liitteet.tunniste, liitteet.nimi, liitteet.contenttype,
            liitteet.koko, liitteet.omistaja, liitteet.tila
          FROM viestit_liitteet JOIN liitteet ON viestit_liitteet.liite_tunniste=liitteet.tunniste
          WHERE viestit_liitteet.viesti_tunniste IN (#${viestiTunnisteet.map(tunniste => "'" + tunniste + "'").mkString(",")})
          ORDER BY indeksi ASC
       """
        .as[(String, String, String, String, Int, String, String)]
    Await.result(db.run(liitteetQuery), 5.seconds)
      .map((viestiTunniste, liiteTunniste, nimi, contentType, koko, omistaja, tila) =>
        UUID.fromString(viestiTunniste) -> Liite(UUID.fromString(liiteTunniste), nimi, contentType, koko, omistaja, LiitteenTila.valueOf(tila)))
      .groupMap((uuid, liite) => uuid)((uuid, liite) => liite)

  def getViestinKayttooikeudet(viestiTunnisteet: Seq[UUID]): Map[UUID, Set[String]] =
    if(viestiTunnisteet.isEmpty) return Map.empty

    val kayttooikeudetQuery =
      sql"""
            SELECT viestit.tunniste, kayttooikeus
            FROM viestit JOIN lahetykset_kayttooikeudet ON viestit.lahetys_tunniste=lahetykset_kayttooikeudet.lahetys_tunniste
            WHERE viestit.tunniste IN (#${viestiTunnisteet.map(t => "'" + t.toString + "'").mkString(",")})
         """.as[(String, String)]

    Await.result(db.run(kayttooikeudetQuery), 5.seconds)
      .groupMap((viestiTunniste, kayttooikeus) => UUID.fromString(viestiTunniste))((viestiTunniste, kayttooikeus) => kayttooikeus)
      .view.mapValues(oikeudet => oikeudet.toSet).toMap

  /**
   * Hakee lähetyksen vastaanottajat
   *
   * @param lahetysTunniste lähetyksen tunniste
   * @return                lähetyksen vastaanottajat
   */
  def getLahetyksenVastaanottajat(lahetysTunniste: UUID, alkaen: Option[UUID], enintaan: Option[Int]): Seq[Vastaanottaja] =
    val vastaanottajatQuery =
      sql"""
        SELECT vastaanottajat.tunniste, vastaanottajat.viesti_tunniste, vastaanottajat.nimi, vastaanottajat.sahkopostiosoite, vastaanottajat.tila, vastaanottajat.prioriteetti, vastaanottajat.ses_tunniste
        FROM vastaanottajat JOIN viestit ON vastaanottajat.viesti_tunniste=viestit.tunniste
        WHERE viestit.lahetys_tunniste=${lahetysTunniste.toString}::uuid AND vastaanottajat.tunniste>${alkaen.getOrElse(UUID.fromString("00000000-0000-0000-0000-000000000000")).toString}::uuid
        ORDER BY vastaanottajat.tunniste
        LIMIT ${enintaan.getOrElse(256)}
     """
        .as[(String, String, String, String, String, String, String)]
    Await.result(db.run(vastaanottajatQuery), 5.seconds)
      .map((tunniste, viestiTunniste, nimi, sahkopostiOsoite, tila, prioriteetti, sesTunniste)
      => Vastaanottaja(UUID.fromString(tunniste), UUID.fromString(viestiTunniste), Kontakti(Option.apply(nimi), sahkopostiOsoite), VastaanottajanTila.valueOf(tila), Prioriteetti.valueOf(prioriteetti), Option.apply(sesTunniste)))

  def getLahetystenKayttooikeudet(lahetysTunnisteet: Seq[UUID]): Map[UUID, Set[String]] =
    if (lahetysTunnisteet.isEmpty) return Map.empty

    val kayttooikeudetQuery =
      sql"""
            SELECT lahetys_tunniste, kayttooikeus
            FROM lahetykset_kayttooikeudet
            WHERE lahetys_tunniste IN (#${lahetysTunnisteet.map(t => "'" + t.toString + "'").mkString(",")})
         """.as[(String, String)]

    Await.result(db.run(kayttooikeudetQuery), 5.seconds)
      .groupMap((lahetysTunniste, kayttooikeus) => UUID.fromString(lahetysTunniste))((lahetysTunniste, kayttooikeus) => kayttooikeus)
      .view.mapValues(oikeudet => oikeudet.toSet).toMap


  /**
   * Hakee lähetettäväksi uuden joukon vastaanottajia ja merkitsee ne "LAHETYKSESSA"-tilaan.
   *
   * @param maara maksimimäärä kerralla lähetettäviä vastaanottajia, tämän avulla on throttlataan lähetettäviä viestijö,
   *              esim. jos kerran kahdessa sekunnissa haetaan mask. 50 viestiä on lähetysnopeus maksimissaan 100 viestiä/s.
   * @return lähetettävien vastaanottajien tunnisteet
   */
  def getLahetettavatVastaanottajat(maara: Int): Seq[UUID] =
    if(maara<=0) return Seq.empty

    var lahetettavat: Seq[String] = null
    val result =
      sql"""
          SELECT tunniste
          FROM vastaanottajat
          WHERE tila='#${VastaanottajanTila.ODOTTAA.toString}'
          ORDER BY prioriteetti, luotu ASC
          FOR UPDATE SKIP LOCKED
          LIMIT ${maara}
      """.as[String].flatMap(tunnisteet => {
          lahetettavat = tunnisteet

          val paivitaTilaAction = if (tunnisteet.isEmpty)
            sql"""SELECT 1""".as[Int]
          else
            sqlu"""
                UPDATE vastaanottajat SET tila='#${VastaanottajanTila.LAHETYKSESSA.toString}'
                WHERE tunniste IN (#${tunnisteet.map(tunniste => "'" + tunniste + "'").mkString(",")})
              """

          val lisaaSiirtymaActions = DBIO.sequence(tunnisteet.map(tunniste =>
            sqlu"""
                  INSERT INTO vastaanottaja_siirtymat VALUES(${tunniste}::uuid, now(), ${VastaanottajanTila.LAHETYKSESSA.toString}, null)
                """))

          DBIO.sequence(Seq(paivitaTilaAction, lisaaSiirtymaActions))
      }).transactionally
    Await.result(db.run(result), 60.seconds)
    lahetettavat.map(tunniste => UUID.fromString(tunniste))

  /**
   * Päivittää vastaanottajan tilan lähetetyksi
   *
   * @param tunniste    vastaanottajan tunniste
   * @param sesTunniste SES-palvelun antama tunniste vastaanottajalle
   */
  def paivitaVastaanottajaLahetetyksi(tunniste: UUID, sesTunniste: String): Unit =
    val paivitaAction = sqlu"""UPDATE vastaanottajat SET tila='#${VastaanottajanTila.LAHETETTY.toString}', ses_tunniste=${sesTunniste} WHERE tunniste='#${tunniste.toString}'"""
    val siirtymaAction =
      sqlu"""
            INSERT INTO vastaanottaja_siirtymat VALUES(${tunniste.toString}::uuid, now(), ${VastaanottajanTila.LAHETETTY.toString}, null)
          """

    Await.result(db.run(DBIO.sequence(Seq(paivitaAction, siirtymaAction)).transactionally), 5.seconds)

  /**
   * Päivittää vastaanottajan tilan virhetilaan lähetyksen epäonnistuttua
   *
   * @param tunniste    vastaanottajan tunniste
   * @param lisatiedot  lisätiedot virheestä
   */
  def paivitaVastaanottajaVirhetilaan(tunniste: UUID, lisatiedot: String): Unit =
    val paivitaAction = sqlu"""UPDATE vastaanottajat SET tila='#${VastaanottajanTila.VIRHE.toString}' WHERE tunniste='#${tunniste.toString}'"""
    val siirtymaAction =
      sqlu"""
            INSERT INTO vastaanottaja_siirtymat VALUES(${tunniste.toString}::uuid, now(), ${VastaanottajanTila.VIRHE.toString}, ${lisatiedot})
          """

    Await.result(db.run(DBIO.sequence(Seq(paivitaAction, siirtymaAction)).transactionally), 5.seconds)

  /**
   * Päivittää vastaanottajan tilan
   *
   * @param tunniste    SES-palvelun tunniste vastaanottajalle
   * @param tila        uusi tila
   * @param lisatiedot  tilasiirtymään liittyvät lisätiedot (esim. bouncen syy)
   */
  def paivitaVastaanotonTila(sesTunniste: String, tila: VastaanottajanTila, lisatiedot: Option[String]): Unit =
    val paivitaAction =
      sql"""
            UPDATE vastaanottajat
            SET tila='#${tila.toString}'
            WHERE ses_tunniste='#${sesTunniste}'
            RETURNING tunniste
            """.as[String]
        .flatMap(tunnisteet => {
          DBIO.sequence(tunnisteet.map(tunniste => {
              sqlu"""INSERT INTO vastaanottaja_siirtymat VALUES(${tunniste}::uuid, now(), ${tila.toString}, ${lisatiedot.getOrElse(null)})"""
          }))
        })
    Await.result(db.run(paivitaAction.transactionally), 5.seconds)

  def getVastaanottajanSiirtymat(tunniste: UUID): Seq[VastaanottajanSiirtyma] =
    val action =
      sql"""
            SELECT to_json(aika::timestamptz)#>>'{}', tila, lisatiedot
            FROM vastaanottaja_siirtymat
            WHERE vastaanottaja_tunniste=${tunniste.toString}::uuid
            ORDER BY aika DESC
         """.as[(String, String, String)]

    Await.result(db.run(action), 5.seconds)
        .map((aika, tila, lisatiedot) => VastaanottajanSiirtyma(Instant.parse(aika), VastaanottajanTila.valueOf(tila), lisatiedot))

  /**
   * Poistaa viestit joiden säilytysaika on kulunut umpeen
   */
  def poistaPoistettavatViestit(): Unit =
    val poistaviestit =
      sqlu"""
            DELETE
            FROM viestit
            WHERE viestit.poistettava<${Instant.now.toString}::timestamptz
          """
    Await.result(db.run(poistaviestit), 60.seconds)

  /**
   * Poistaa vanhat liitteet joihin linkitetyt viestit on poistettu
   *
   * @param luotuEnnen  poistetaan vain liitteet jotka luotu ennen annettua päivämäärää
   * @return            poistettujen liitteiden tunnisteet
   */
  def poistaPoistettavatLiitteet(luotuEnnen: Instant): Seq[UUID] =
    val action = sql"""
          WITH ei_linkitetyt_liitteet AS (
            SELECT liitteet.tunniste AS tunniste
            FROM liitteet
            LEFT JOIN viestit_liitteet ON liitteet.tunniste=viestit_liitteet.liite_tunniste
            WHERE viestit_liitteet.liite_tunniste IS null
          )

          DELETE
          FROM liitteet
          USING ei_linkitetyt_liitteet
          WHERE liitteet.tunniste=ei_linkitetyt_liitteet.tunniste
          AND liitteet.luotu<${luotuEnnen.toString}::timestamptz
          RETURNING liitteet.tunniste
        """.as[String]
    Await.result(db.run(action), 60.seconds).map(t => UUID.fromString(t))

  /**
   * Poistaa vanhat liitteet joihin linkitetyt viestit on poistettu
   *
   * @param luotuEnnen poistetaan vain liitteet jotka luotu ennen annettua päivämäärää
   * @return poistettujen liitteiden tunnisteet
   */
  def poistaPoistettavatLahetykset(luotuEnnen: Instant): Unit =
    val action = sqlu"""
          WITH ei_linkitetyt_lahetykset AS (
            SELECT lahetykset.tunniste AS tunniste
            FROM lahetykset
            LEFT JOIN viestit ON lahetykset.tunniste=viestit.lahetys_tunniste
            WHERE viestit.lahetys_tunniste IS null
          )

          DELETE
          FROM lahetykset
          USING ei_linkitetyt_lahetykset
          WHERE lahetykset.tunniste=ei_linkitetyt_lahetykset.tunniste
          AND lahetykset.luotu<${luotuEnnen.toString}::timestamptz
        """
    Await.result(db.run(action), 60.seconds)
}
