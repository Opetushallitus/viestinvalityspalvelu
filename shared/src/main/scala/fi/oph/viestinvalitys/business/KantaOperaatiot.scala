package fi.oph.viestinvalitys.business

import com.github.f4b6a3.uuid.UuidCreator
import fi.oph.viestinvalitys.util.queryUtil
import org.slf4j.LoggerFactory
import slick.jdbc.JdbcBackend
import slick.jdbc.SetParameter
import slick.jdbc.PostgresProfile.api.*

import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}
import com.github.tminglei.slickpg.utils.PlainSQLUtils.mkArraySetParameter
import org.jsoup.Jsoup

implicit val setStringArray: SetParameter[Seq[String]] = mkArraySetParameter[String]("varchar")

object KantaOperaatiot {
  val executionContext: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(8))
}

/**
 * Lähetykseen liittyvät business-operaatiot
 *
 * @param db  oletuskannan sijaan käytettävä tietokanta (testejä varten)
 */
class KantaOperaatiot(db: JdbcBackend.JdbcDatabaseDef) {

  implicit val executionContext: ExecutionContext = KantaOperaatiot.executionContext

  final val DB_TIMEOUT = 15.seconds
  val LOG = LoggerFactory.getLogger(classOf[KantaOperaatiot])

  def getUUID(): UUID =
    // käytetään aikaperustaisia UUID:tä kahdesta syystä:
    // - sivuttavissa endpointeissa entiteetit voidaan järjestää luomisjärjestykseen UUID:n perusteella
    // - indeksien päivittäminen on kevyempi operaatio kun avaimen arvot eivät ole satunnaisia
    UuidCreator.getTimeOrderedEpoch()

  /**
   * Hakee session CAS-tiketin tunnisteella
   */
  def getSessionIdByMappingId(mappingId: String, serviceName: String): Option[String] =
    val action =
      sql"""
            SELECT #${queryUtil.sessionIdAttributeName(serviceName)}
            FROM #${queryUtil.sessionTableName(serviceName)}
            WHERE mapped_ticket_id = $mappingId
          """.as[String]
    Await.result(db.run(action), DB_TIMEOUT).find(v => true)


  /**
   * Poistaa CAS-sessiomappauksen sessio id:n perusteella
   */
  def deleteCasMappingBySessionId(sessionId: String, serviceName: String): Unit =
    val action =
      sqlu"""
            DELETE
            FROM #${queryUtil.sessionTableName(serviceName)}
            WHERE #${queryUtil.sessionIdAttributeName(serviceName)} = $sessionId
          """
    Await.result(db.run(action), DB_TIMEOUT)

  /**
   *
   * Lisää kantaan mappauksen palvelun sessiosta CAS-sessioon
   */
  def addMappingForSessionId(mappingId: String, sessionId: String, serviceName: String): Unit = {
    val insertAction =
      sqlu"""INSERT INTO  #${queryUtil.sessionTableName(serviceName)} (mapped_ticket_id, raportointi_session_id) VALUES ($mappingId, $sessionId)
             ON CONFLICT (mapped_ticket_id) DO NOTHING"""
    Await.result(db.run(insertAction), DB_TIMEOUT)
  }

  /**
   * scheduled cleanup job for expired sessions
   */
  def cleanSessionMappings(): Unit = {
    val action =
      sqlu"""
            DELETE
            FROM raportointi_cas_client_session
            WHERE raportointi_session_id NOT IN = (SELECT session_id FROM raportointi_session)
          """
    Await.result(db.run(action), DB_TIMEOUT)
  }
  /**
   * Tallentaa uuden lähetyksen.
   *
   * @param otsikko                 lähetyksen otsikko
   * @param omistaja                lähetyksen omistaja (luoja), vain sama omistaja voi liittää lähetykseen viestejä
   * @param lahettavanVirkailijanOID lähetyksen tehneen virkailijan OID
   * @param lahettaja               lähetyksen tehneen virkailijan nimi ja sähköpostiosoite
   * @param replyTo                 lähetyksen vastausosoite
   * @param prioriteetti            lähetyksen prioriteetti
   * @param sailytysAika            lähetyksen säilytysaika vuorokausina
   *
   * @return          tallennettu lähetys
   */
  def tallennaLahetys(otsikko: String,
                      omistaja: String, lahettavaPalvelu: String,
                      lahettavanVirkailijanOID: Option[String],
                      lahettaja: Kontakti,
                      replyTo: Option[String],
                      prioriteetti: Prioriteetti,
                      sailytysAika: Int
                     ): Lahetys = {
    val lahetysTunniste = this.getUUID()
    val luontiaika = Instant.now
    val lahetysInsertAction =
      sqlu"""INSERT INTO lahetykset VALUES(${lahetysTunniste.toString}::uuid, ${otsikko},
        ${lahettavaPalvelu}, ${lahettavanVirkailijanOID}, ${lahettaja.nimi}, ${lahettaja.sahkoposti}, ${replyTo},
        ${prioriteetti.toString}::prioriteetti, ${omistaja}, ${luontiaika.toString}::timestamptz, ${Instant.now.plusSeconds(60*60*24*sailytysAika).toString}::timestamptz)"""

    Await.result(db.run(DBIO.sequence(Seq(lahetysInsertAction)).transactionally), DB_TIMEOUT)
    this.getLahetys(lahetysTunniste).get
  }

  /**
   * Palauttaa lähetyksen tekemättä käyttöoikeusrajausta
   *
   * @param tunniste  lähetyksen tunniste
   * @return          tunnistetta vastaava lähetys
   */
  def getLahetys(tunniste: UUID): Option[Lahetys] =
    Await.result(db.run(
      sql"""
            SELECT tunniste, otsikko, omistaja, lahettavapalvelu, lahettavanvirkailijanoid, lahettajannimi, lahettajansahkoposti, replyto, prioriteetti, to_json(luotu::timestamptz)#>>'{}'
            FROM lahetykset
            WHERE tunniste=${tunniste.toString}::uuid
         """.as[(String, String, String, String, String, String, String, String, String, String)].headOption), DB_TIMEOUT)
      .map((tunniste, otsikko, omistaja, lahettavapalvelu, lahettavanVirkailijanOid, lahettajanNimi, lahettajanSahkoposti, replyto, prioriteetti, luotu) =>
        Lahetys(UUID.fromString(tunniste), otsikko, omistaja, lahettavapalvelu, Option.apply(lahettavanVirkailijanOid),
          Kontakti(Option.apply(lahettajanNimi), lahettajanSahkoposti), Option.apply(replyto), Prioriteetti.valueOf(prioriteetti), Instant.parse(luotu)))

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
    Await.result(db.run(insertAction), DB_TIMEOUT)
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

    Await.result(db.run(DBIO.sequence(Seq(lukitseLiitteetAction, paivitaVastaanottajienTilaAction, paivitaLiitteenTilaAction)).transactionally), DB_TIMEOUT)

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
    Await.result(db.run(liiteQuery), DB_TIMEOUT)
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
                      lahettaja: Option[Kontakti],
                      replyTo: Option[String],
                      vastaanottajat: Seq[Kontakti],
                      liiteTunnisteet: Seq[UUID],
                      lahettavaPalvelu: Option[String],
                      lahetysTunniste: Option[UUID],
                      prioriteetti: Option[Prioriteetti],
                      sailytysAika: Option[Int],
                      kayttooikeusRajoitukset: Set[Kayttooikeus],
                      metadata: Map[String, Seq[String]],
                      omistaja: String,
                      idempotencyKey: Option[String]
                         ): (Viesti, Seq[Vastaanottaja]) = {

    val viestiTunniste = this.getUUID()
    val lahetys = lahetysTunniste.map(tunniste => this.getLahetys(tunniste).get)
    val finalPrioriteetti = lahetys.map(l => l.prioriteetti).getOrElse(prioriteetti.get)
    val finalLahetysTunniste = lahetys.map(l => l.tunniste).getOrElse(viestiTunniste)
    val finalLahettaja = lahetys.map(l => l.lahettaja).getOrElse(lahettaja.get)
    val finalLahettavaPalvelu = lahetys.map(l => l.lahettavaPalvelu).getOrElse(lahettavaPalvelu.get)
    val lahetysLuotu = lahetys.map(l => l.luotu).getOrElse(Instant.now)

    val lahetysInsertAction = {
      if(lahetysTunniste.isDefined) sql"""SELECT 1""".as[Int] // NOP jos lähetys on jo olemassa
      else
        sqlu"""INSERT INTO lahetykset VALUES(${finalLahetysTunniste.toString}::uuid, ${otsikko}, ${lahettavaPalvelu},
          ${lahettavanVirkailijanOID}, ${lahettaja.get.nimi}, ${lahettaja.get.sahkoposti}, ${replyTo},
          ${finalPrioriteetti.toString}::prioriteetti, ${omistaja}, ${lahetysLuotu.toString}::timestamptz, ${Instant.now.plusSeconds(60*60*24*sailytysAika.get).toString}::timestamptz)"""
    }

    // luodaan käyttöoikeudet
    val kayttooikeusCreateActions = {
      DBIO.sequence(kayttooikeusRajoitukset.map(kayttooikeus => {
        sql"""
              WITH lisays AS (
                INSERT INTO kayttooikeudet (organisaatio, oikeus) VALUES(${kayttooikeus.organisaatio.getOrElse(null)}, ${kayttooikeus.oikeus}) ON CONFLICT DO NOTHING RETURNING tunniste
              )
              SELECT tunniste FROM kayttooikeudet WHERE organisaatio IS NOT DISTINCT FROM ${kayttooikeus.organisaatio.getOrElse(null)} AND oikeus=${kayttooikeus.oikeus} UNION SELECT tunniste FROM lisays
            """.as[Int]
      })).map(oikeudet => oikeudet.flatten)
    }

    val kayttooikeusRelatedInsertActions = kayttooikeusCreateActions.flatMap(oikeudet => {
      // generoidaan kielikohtaiset otsikot
      val otsikko_fi = if (kielet.contains(Kieli.FI)) otsikko else ""
      val otsikko_sv = if (kielet.contains(Kieli.SV)) otsikko else ""
      val otsikko_en = if (kielet.contains(Kieli.EN)) otsikko else ""
      val otsikko_simple = if (kielet.isEmpty) otsikko else ""

      // poistetaan sisällöstä salaisuudet
      val sisalto_sanitized = {
        var s = sisalto
        maskit.foreach(maski => s = s.replace(maski._1, ""))
        s
      }

      // poistetaan sisällöstä mahdollinen markup, myös postgresin to_tsvector-funktio ottaa tägit pois
      // mutta varmuuden vuoksi käytetään tähän tarkoitettua kirjastoa
      val sisalto_text = if(sisallonTyyppi==SisallonTyyppi.HTML) Jsoup.parse(sisalto_sanitized).text() else sisalto_sanitized

      // generoidaan kielikohtaiset sisällöt
      val sisalto_fi = if (kielet.contains(Kieli.FI)) sisalto_text else ""
      val sisalto_sv = if (kielet.contains(Kieli.SV)) sisalto_text else ""
      val sisalto_en = if (kielet.contains(Kieli.EN)) sisalto_text else ""
      val sisalto_simple = if (kielet.isEmpty) sisalto else ""

      // tallennetaan viesti
      val viestiInsertAction =
        sqlu"""
             INSERT INTO viestit (tunniste, lahetys_tunniste, otsikko, sisalto, sisallontyyppi, kielet_fi, kielet_sv,
                                  kielet_en, prioriteetti, omistaja, luotu, idempotency_key, haku_otsikko, haku_sisalto,
                                  haku_kayttooikeudet, haku_vastaanottajat, haku_lahettaja, haku_metadata,
                                  haku_lahettavapalvelu)
             VALUES(${viestiTunniste.toString}::uuid,
                    ${finalLahetysTunniste.toString}::uuid,
                    ${otsikko},
                    ${sisalto},
                    ${sisallonTyyppi.toString},
                    ${kielet.contains(Kieli.FI)}, ${kielet.contains(Kieli.SV)}, ${kielet.contains(Kieli.EN)},
                    ${finalPrioriteetti.toString}::prioriteetti,
                    ${omistaja},
                    ${Instant.now.toString}::timestamptz,
                    ${idempotencyKey.getOrElse(null)},
                    to_tsvector('finnish', ${otsikko_fi}) || to_tsvector('swedish', ${otsikko_sv}) || to_tsvector('english', ${otsikko_en}) || to_tsvector('simple', ${otsikko_simple}),
                    to_tsvector('finnish', ${sisalto_fi}) || to_tsvector('swedish', ${sisalto_sv}) || to_tsvector('english', ${sisalto_en}) || to_tsvector('simple', ${sisalto_simple}),
                    ARRAY[#${oikeudet.mkString(",")}]::integer[],
                    ARRAY[${vastaanottajat.map(v => v.sahkoposti.toLowerCase)}]::varchar[],
                    ${finalLahettaja.sahkoposti},
                    ${metadata.map((avain, arvot) => arvot.map(arvo => avain + ":" + arvo)).flatten.toSeq},
                    ${finalLahettavaPalvelu})
          """

      // tallennetaan viestin ja lähetyksen oikeudet
      val viestiKayttooikeusInsertActions = oikeudet.map(kayttooikeusTunniste => {
        sqlu"""
             INSERT INTO viestit_kayttooikeudet (viesti_tunniste, kayttooikeus_tunniste) VALUES(${viestiTunniste.toString}::uuid, ${kayttooikeusTunniste})
              """
      })
      val lahetysKayttooikeusInsertActions = oikeudet.map(kayttooikeusTunniste => {
        sqlu"""
             INSERT INTO lahetykset_kayttooikeudet (lahetys_tunniste, kayttooikeus_tunniste)
             VALUES(${finalLahetysTunniste.toString}::uuid, ${kayttooikeusTunniste})
             ON CONFLICT DO NOTHING
              """
      })

      DBIO.sequence(Seq(viestiInsertAction).concat(viestiKayttooikeusInsertActions).concat(lahetysKayttooikeusInsertActions))
    })

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
      vastaanottajaEntiteetit = vastaanottajat.map(vastaanottaja => Vastaanottaja(this.getUUID(), viestiTunniste, vastaanottaja, tila, finalPrioriteetti, Option.empty))
      val vastaanottajaInsertActions = DBIO.sequence(vastaanottajaEntiteetit.map(vastaanottaja => {
        sqlu"""
               INSERT INTO vastaanottajat
               VALUES(${vastaanottaja.tunniste.toString}::uuid, ${viestiTunniste.toString}::uuid, ${vastaanottaja.kontakti.nimi},
                ${vastaanottaja.kontakti.sahkoposti}, ${vastaanottaja.tila.toString}, now(), ${finalPrioriteetti.toString}::prioriteetti)
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

    Await.result(db.run(DBIO.sequence(Seq(lahetysInsertAction, kayttooikeusRelatedInsertActions, metadataInsertActions, maskitInsertActions, liiteRelatedInsertActions)).transactionally), DB_TIMEOUT)
    (this.getViestit(Seq(viestiTunniste)).find(v => true).get, vastaanottajaEntiteetit)
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
    Await.result(db.run(maaraAction), DB_TIMEOUT).find(i => true).get

  def getVastaanottajat(vastaanottajaTunnisteet: Seq[UUID]): Seq[Vastaanottaja] =
    if(vastaanottajaTunnisteet.isEmpty) return Seq.empty

    val vastaanottajatQuery =
      sql"""
          SELECT tunniste, viesti_tunniste, nimi, sahkopostiosoite, tila, prioriteetti, ses_tunniste
          FROM vastaanottajat
          WHERE tunniste IN (#${vastaanottajaTunnisteet.map(tunniste => "'" + tunniste + "'").mkString(",")})
       """
        .as[(String, String, String, String, String, String, String)]
    Await.result(db.run(vastaanottajatQuery), DB_TIMEOUT)
      .map((tunniste, viestiTunniste, nimi, sahkopostiOsoite, tila, prioriteetti, sesTunniste)
      => Vastaanottaja(UUID.fromString(tunniste), UUID.fromString(viestiTunniste), Kontakti(Option.apply(nimi), sahkopostiOsoite), VastaanottajanTila.valueOf(tila), Prioriteetti.valueOf(prioriteetti), Option.apply(sesTunniste)))

  private def toKielet(kieletFi: Boolean, kieletSv: Boolean, kieletEn: Boolean): Set[Kieli] =
    var kielet: Seq[Kieli] = Seq.empty
    if (kieletFi) kielet = kielet.appended(Kieli.FI)
    if (kieletSv) kielet = kielet.appended(Kieli.SV)
    if (kieletEn) kielet = kielet.appended(Kieli.EN)
    kielet.toSet

  def getViestit(viestiTunnisteet: Seq[UUID]): Seq[Viesti] =
    if(viestiTunnisteet.isEmpty)
      Seq.empty
    else
      val viestitQuery =
        sql"""
            SELECT viestit.tunniste, lahetys_tunniste, viestit.otsikko, sisalto, sisallontyyppi, kielet_fi, kielet_sv, kielet_en, replyto, viestit.omistaja, viestit.prioriteetti,
              lahettavapalvelu, lahettavanvirkailijanoid, lahettajannimi, lahettajansahkoposti
            FROM viestit
            JOIN lahetykset ON viestit.lahetys_tunniste=lahetykset.tunniste
            WHERE viestit.tunniste IN (#${viestiTunnisteet.map(tunniste => "'" + tunniste + "'").mkString(",")})
         """
          .as[(String, String, String, String, String, Boolean, Boolean, Boolean, String, String, String, String, String, String, String)]

      val maskitQuery =
        sql"""
            SELECT viesti_tunniste, salaisuus, maski
            FROM maskit
            WHERE viesti_tunniste IN (#${viestiTunnisteet.map(tunniste => "'" + tunniste + "'").mkString(",")})
        """
          .as[(String, String, String)]
      val maskit: Map[String, Map[String, Option[String]]] = Await.result(db.run(maskitQuery), DB_TIMEOUT)
        .groupBy((viestiTunniste, salaisuus, maski) => viestiTunniste)
        .map((viestiTunniste, maskit) => viestiTunniste -> maskit.map((viestiTunniste, salaisuus, maski) => salaisuus -> Option.apply(maski)).toMap)

      Await.result(db.run(viestitQuery), DB_TIMEOUT)
        .map((tunniste, lahetysTunniste, otsikko, sisalto, sisallonTyyppi, kieletFi, kieletSv, kieletEn, replyTo, omistaja, prioriteetti, lahettavapalvelu, lahettavanvirkailijanoid, lahettajannimi, lahettajansahkoposti)
        => Viesti(
            tunniste = UUID.fromString(tunniste),
            lahetysTunniste = UUID.fromString(lahetysTunniste),
            otsikko = otsikko,
            sisalto = sisalto,
            sisallonTyyppi = SisallonTyyppi.valueOf(sisallonTyyppi),
            kielet = toKielet(kieletFi, kieletSv, kieletEn),
            maskit = maskit.get(tunniste).getOrElse(Map.empty),
            lahettavaPalvelu = lahettavapalvelu,
            lahettavanVirkailijanOID = Option.apply(lahettavanvirkailijanoid),
            lahettaja = Kontakti(Option.apply(lahettajannimi), lahettajansahkoposti),
            replyTo = Option.apply(replyTo),
            omistaja = omistaja,
            prioriteetti = Prioriteetti.valueOf(prioriteetti)))

  /**
   * Palauttaa mahdollisen olemassaolevan viestin idempotency-avaimen perusteella
   *
   * @param omistaja        käyttäjä jonka luoma viesti palautetaan
   * @param idempotencyKey  avain jonka perusteella viestiä haetaan
   * @return                mahdollinen aikaisemmin luotu sama viesti
   */
  def getExistingViesti(omistaja: String, idempotencyKey: String): Option[Viesti] =
    val query = sql"""SELECT tunniste FROM viestit WHERE omistaja=${omistaja} AND idempotency_key=${idempotencyKey}""".as[String]
    Await.result(db.run(query), DB_TIMEOUT).map(tunniste => UUID.fromString(tunniste))
      .headOption
      .map(tunniste => getViestit(Seq(tunniste)).find(_ => true).headOption.get)

  def getViestinLiitteet(viestiTunnisteet: Seq[UUID]): Map[UUID, Seq[Liite]] =
    if(viestiTunnisteet.isEmpty)
      Map.empty
    else
      val liitteetQuery =
        sql"""
            SELECT viestit_liitteet.viesti_tunniste, liitteet.tunniste, liitteet.nimi, liitteet.contenttype,
              liitteet.koko, liitteet.omistaja, liitteet.tila
            FROM viestit_liitteet JOIN liitteet ON viestit_liitteet.liite_tunniste=liitteet.tunniste
            WHERE viestit_liitteet.viesti_tunniste IN (#${viestiTunnisteet.map(tunniste => "'" + tunniste + "'").mkString(",")})
            ORDER BY indeksi ASC
         """
          .as[(String, String, String, String, Int, String, String)]
      Await.result(db.run(liitteetQuery), DB_TIMEOUT)
        .map((viestiTunniste, liiteTunniste, nimi, contentType, koko, omistaja, tila) =>
          UUID.fromString(viestiTunniste) -> Liite(UUID.fromString(liiteTunniste), nimi, contentType, koko, omistaja, LiitteenTila.valueOf(tila)))
        .groupMap((uuid, liite) => uuid)((uuid, liite) => liite)

  def getViestinKayttooikeudet(viestiTunnisteet: Seq[UUID]): Map[UUID, Set[Kayttooikeus]] =
    if(viestiTunnisteet.isEmpty)
      Map.empty
    else
      val kayttooikeudetQuery =
        sql"""
              SELECT viesti_tunniste, oikeus, organisaatio
              FROM viestit_kayttooikeudet
              JOIN kayttooikeudet ON viestit_kayttooikeudet.kayttooikeus_tunniste=kayttooikeudet.tunniste
              WHERE viesti_tunniste IN (#${viestiTunnisteet.map(t => "'" + t.toString + "'").mkString(",")})
           """.as[(String, String, String)]

      Await.result(db.run(kayttooikeudetQuery), DB_TIMEOUT)
        .groupMap((viestiTunniste, oikeus, organisaatio) => UUID.fromString(viestiTunniste))((viestiTunniste, oikeus, organisaatio)
          => Kayttooikeus(oikeus, Option.apply(organisaatio)))
        .view.mapValues(oikeudet => oikeudet.toSet).toMap

  /**
   * Hakee lähetyksen vastaanottajat
   *
   * @param lahetysTunniste lähetyksen tunniste
   * @param alkaen          sivutuksen ensimmäinen vastaanottaja
   * @param enintaan        sivutuksen sivukoko
   * @return lähetyksen vastaanottajat
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
    Await.result(db.run(vastaanottajatQuery), DB_TIMEOUT)
      .map((tunniste, viestiTunniste, nimi, sahkopostiOsoite, tila, prioriteetti, sesTunniste)
      => Vastaanottaja(UUID.fromString(tunniste), UUID.fromString(viestiTunniste), Kontakti(Option.apply(nimi), sahkopostiOsoite), VastaanottajanTila.valueOf(tila), Prioriteetti.valueOf(prioriteetti), Option.apply(sesTunniste)))

  def getLahetystenKayttooikeudet(lahetysTunnisteet: Seq[UUID]): Map[UUID, Set[Kayttooikeus]] =
    if (lahetysTunnisteet.isEmpty)
      Map.empty
    else
      val kayttooikeudetQuery =
        sql"""
              SELECT lahetys_tunniste, oikeus, organisaatio
              FROM lahetykset_kayttooikeudet
              JOIN kayttooikeudet ON lahetykset_kayttooikeudet.kayttooikeus_tunniste=kayttooikeudet.tunniste
              WHERE lahetys_tunniste IN (#${lahetysTunnisteet.map(t => "'" + t.toString + "'").mkString(",")})
           """.as[(String, String, String)]

      Await.result(db.run(kayttooikeudetQuery), DB_TIMEOUT)
        .groupMap((lahetysTunniste, oikeus, organisaatio)
        => UUID.fromString(lahetysTunniste))((lahetysTunniste, oikeus, organisaatio) => Kayttooikeus(oikeus, Option.apply(organisaatio)))
        .view.mapValues(oikeudet => oikeudet.toSet).toMap

  /**
   * Hakee lähetettäväksi uuden joukon vastaanottajia ja merkitsee ne "LAHETYKSESSA"-tilaan.
   *
   * @param maara maksimimäärä kerralla lähetettäviä vastaanottajia, tämän avulla on throttlataan lähetettäviä viestijä,
   *              esim. jos kerran kahdessa sekunnissa haetaan mask. 50 viestiä on lähetysnopeus maksimissaan 100 viestiä/s.
   * @return lähetettävien vastaanottajien tunnisteet
   */
  def getLahetettavatVastaanottajat(maara: Int): Seq[UUID] =
    if(maara<=0)
      Seq.empty
    else
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

    Await.result(db.run(DBIO.sequence(Seq(paivitaAction, siirtymaAction)).transactionally), DB_TIMEOUT)

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

    Await.result(db.run(DBIO.sequence(Seq(paivitaAction, siirtymaAction)).transactionally), DB_TIMEOUT)

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
    Await.result(db.run(paivitaAction.transactionally), DB_TIMEOUT)

  def getVastaanottajanSiirtymat(tunniste: UUID): Seq[VastaanottajanSiirtyma] =
    val action =
      sql"""
            SELECT to_json(aika::timestamptz)#>>'{}', tila, lisatiedot
            FROM vastaanottaja_siirtymat
            WHERE vastaanottaja_tunniste=${tunniste.toString}::uuid
            ORDER BY aika DESC
         """.as[(String, String, String)]

    Await.result(db.run(action), DB_TIMEOUT)
        .map((aika, tila, lisatiedot) => VastaanottajanSiirtyma(Instant.parse(aika), VastaanottajanTila.valueOf(tila), lisatiedot))

  /**
   * Poistaa lähetykset joiden säilytysaika on kulunut umpeen
   */
  def poistaPoistettavatLahetykset(): Unit =
    val action =
      sqlu"""
            DELETE
            FROM lahetykset
            WHERE lahetykset.poistettava<${Instant.now.toString}::timestamptz
          """
    Await.result(db.run(action), 60.seconds)

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
   * Poistaa idempotency-avaimet viestiestä jotka luotu ennen määriteltyä ajankohtaa
   *
   * @param luotuEnnen  poistetaan avaimet viesteistä jotka luotu ennen annettua päivämäärää
   */
  def poistaIdempotencyKeys(luotuEnnen: Instant): Int =
    val action = sql"""
          UPDATE viestit
          SET idempotency_key=null
          WHERE idempotency_key IS NOT null AND luotu<${luotuEnnen.toString}::timestamptz
        """.as[Int]
    Await.result(db.run(action), 60.seconds).headOption.get

/* Raportointikäyttöliittymää varten tehdyt haut */

  /**
   * Palauttaa tunnisteet joukolle käyttöoikeuksia (niille joille on tunniste järjestelmässä).
   *
   * @param kayttooikeudet  käyttöoikeudet joilla haetaan tunnisteita ja joilla on tunniste järjestelmässä
   *
   * @return tunnisteet
   */
  def getKayttooikeusTunnisteet(kayttooikeudet: Seq[Kayttooikeus]): Set[Int] =
    kayttooikeudet.sliding(1024, 1024).map(kayttooikeudet => Await.result(db.run((
        sql"""SELECT tunniste FROM kayttooikeudet WHERE (organisaatio, oikeus) IN (""" concat {
          var statement = sql""""""
          kayttooikeudet.zipWithIndex.foreach((kayttooikeus: Kayttooikeus, index: Int) => {
            if(index>0) statement = statement concat sql""","""
            statement = statement concat sql"""(${kayttooikeus.organisaatio}, ${kayttooikeus.oikeus})"""
          })
          statement
        } concat sql""")""").as[Int]), DB_TIMEOUT).toSet).flatten.toSet

  private def getLahetyksetJoihinOikeudetLausekkeet(alkaen: Option[UUID], kayttooikeudet: Option[Set[Int]]) =
    if(kayttooikeudet.isEmpty)
      // pääkäyttäjän tapauksessa lähdetään liikkeelle kaikista lähetyksistä
      sql"""
          SELECT tunniste AS lahetys_tunniste FROM lahetykset
          WHERE (${alkaen.isEmpty} OR tunniste<${alkaen.map(a => a.toString).getOrElse(null)}::uuid) ORDER BY lahetys_tunniste DESC
         """
    else
      // Normaalin käyttäjän tapauksessa lähetykset ovat unioni kaikista yksittäisten käyttöoikeuksien kautta
      // löytyvistä lähetyksistä (saattaa tässä vaiheessa sisältää duplikaatteja). Tällä tavoin Postgres hakee
      // MergeAppend-strategialla unionin yksittäisistä osista uusimpia lähetyksiä kunnes tarvittava määrä on
      // täynnä ja näin vältytään potentiaalisesti hyvin suurten lähetysmäärien sorttaamiselta.
      //
      // Suunnitteluoletus on että vaikka jollain käyttäjillä voi olla kymmenittäin tai sadoittain yksittäisiä
      // oikeuksia, niiden oikeuksien määrä joita käytetään (ja joilla näin ollen on tunniste) viestinvälitys-
      // palvelussa on loppupeleissä pieni osajoukko, jolloin union-lausekkeiden määrä pysyy järkevänä.
      var lahetykset = sql""""""
      kayttooikeudet.get.zipWithIndex.foreach((kayttooikeus, index) => {
        if(index>0) lahetykset = lahetykset concat sql"""UNION ALL"""
        lahetykset = lahetykset concat
          sql"""
              (SELECT lahetys_tunniste FROM lahetykset_kayttooikeudet
              WHERE kayttooikeus_tunniste=${kayttooikeus}
              AND (${alkaen.isEmpty} OR lahetys_tunniste<${alkaen.map(a => a.toString).getOrElse(null)}::uuid)
              ORDER BY lahetys_tunniste DESC)
             """
      })
      lahetykset

  /**
   * Hakee lähetykset järjestettynä luontipäivämäärän mukaan (uusin ensin) perustuen käyttöoikeuksiin.
   *
   * @param alkaen                    palautetaan lähetyksen alkaen tämän lähetyksen jälkeen
   * @param enintaan                  palautetaan enintään näin monta lähetystä
   * @param kayttooikeusTunnisteet    käyttäjän käyttöoikeuksien tunnisteet (Option.Empty tarkoittaa pääkäyttäjää)
   *
   * @return                          kriteereihin sopivat lähetykset järjestettynä uusimmasta vanhimpaan
   */
  def getLahetykset(alkaen: Option[UUID], enintaan: Int, kayttooikeusTunnisteet: Option[Set[Int]]): (Seq[Lahetys], Boolean) =
    if(kayttooikeusTunnisteet.isDefined && kayttooikeusTunnisteet.get.isEmpty)
      // käyttäjällä ei oikeuksia yhteenkään viestiin
      (Seq.empty, false)
    else
      val lahetykset = Await.result(db.run(
          (sql"""
              SELECT tunniste, otsikko, omistaja, lahettavapalvelu, lahettavanvirkailijanoid, lahettajannimi, lahettajansahkoposti, replyto, prioriteetti, to_json(lahetykset.luotu::timestamptz)#>>'{}' from (
                SELECT lahetys_tunniste FROM (
                  SELECT lahetys_tunniste, (lag(lahetys_tunniste, 1) OVER ()=lahetys_tunniste) AS duplicate FROM ("""
                    concat getLahetyksetJoihinOikeudetLausekkeet(alkaen, kayttooikeusTunnisteet) concat
           sql""" ) AS lahetykset_joihin_oikeudet
                  ORDER BY lahetys_tunniste DESC
                ) AS duplikaatteja
                WHERE duplicate=false OR duplicate IS NULL
                limit ${enintaan+1}
              ) AS lahetys_tunnisteet LEFT JOIN lahetykset
              ON lahetys_tunnisteet.lahetys_tunniste=lahetykset.tunniste
              ORDER BY lahetys_tunnisteet.lahetys_tunniste DESC;
              """).as[(String, String, String, String, String, String, String, String, String, String)]), DB_TIMEOUT)
        .map((tunniste, otsikko, omistaja, lahettavapalvelu, lahettavanVirkailijanOid, lahettajanNimi, lahettajanSahkoposti, replyto, prioriteetti, luotu) =>
          Lahetys(UUID.fromString(tunniste), otsikko, omistaja, lahettavapalvelu, Option.apply(lahettavanVirkailijanOid),
            Kontakti(Option.apply(lahettajanNimi), lahettajanSahkoposti), Option.apply(replyto), Prioriteetti.valueOf(prioriteetti), Instant.parse(luotu)))
      if(lahetykset.size<=enintaan)
        (lahetykset, false)
      else
        (lahetykset.take(lahetykset.length-1), true)

  def getViestienHakuLausekkeet(lahetysTunniste: Option[UUID], kayttooikeusTunnisteet: Option[Set[Int]],
                                otsikkoHakuLauseke: Option[String], sisaltoHakuLauseke: Option[String],
                                vastaanottajaHakuLauseke: Option[String], lahettajaHakuLauseke: Option[String],
                                metadataHakuLausekkeet: Option[Map[String, Seq[String]]], lahettavaPalveluHakuLauseke: Option[String]) =
    sql"""
        ((${kayttooikeusTunnisteet.isEmpty} OR
          haku_kayttooikeudet && ARRAY[#${kayttooikeusTunnisteet.map(o => o.mkString(",")).getOrElse("")}]::integer[])
        AND
        (${vastaanottajaHakuLauseke.isEmpty} OR haku_vastaanottajat @> (
          ${vastaanottajaHakuLauseke.map(l => Seq(l)).getOrElse(Seq(""))}
        ))
        AND
        (${lahettajaHakuLauseke.isEmpty} OR
          haku_lahettaja = ${lahettajaHakuLauseke.getOrElse("")})
        AND
        (${otsikkoHakuLauseke.isEmpty} OR haku_otsikko @@ (
          websearch_to_tsquery('finnish', ${otsikkoHakuLauseke.getOrElse("")}) ||
          websearch_to_tsquery('swedish', ${otsikkoHakuLauseke.getOrElse("")}) ||
          websearch_to_tsquery('english', ${otsikkoHakuLauseke.getOrElse("")})
        ))
        AND
        (${sisaltoHakuLauseke.isEmpty} OR haku_sisalto @@ (
          websearch_to_tsquery('finnish', ${sisaltoHakuLauseke.getOrElse("")}) ||
          websearch_to_tsquery('swedish', ${sisaltoHakuLauseke.getOrElse("")}) ||
          websearch_to_tsquery('english', ${sisaltoHakuLauseke.getOrElse("")})
        ))
        AND
        (${metadataHakuLausekkeet.isEmpty} OR haku_metadata @> (
          ${metadataHakuLausekkeet.map(m => m.map((avain, arvot) => arvot.map(arvo => avain + ":" + arvo)).flatten.toSeq).getOrElse(Seq(""))}
        ))
        AND
        (${lahettavaPalveluHakuLauseke.isEmpty} OR
          haku_lahettavapalvelu = ${lahettavaPalveluHakuLauseke.getOrElse("")})
        AND
        (${lahetysTunniste.isEmpty} OR
          lahetys_tunniste = ${lahetysTunniste.map(t => t.toString).getOrElse("")}::uuid))
      """

  /**
   * Hakee lähetykset järjestettynä luontipäivämäärän mukaan (uusin ensin) perustuen annettuihin hakukriteereihin.
   *
   * Lähetysten hakuun käytetään kahta vaihtoehtoista strategiaa:
   * 1) Jos hakukriteereitä (otsikko, sisältö, vastaanottaja) ei ole määritelty, haetaan lähetykset lähtien liikkeelle
   *    käyttäjän oikeuksista (pääkäyttäjällä kaikista lähetyksistä)
   * 2) Jos hakukriteereitä on määritelty, haetaan lähetykset lähtien viesteistä (viestit-tauluun on denormalisoitu
   *    lähettäjä, vastaanottajat ja käyttöoikeudet GIN-indeksoinnin mahdollistamiseksi)
   *
   * @param alkaen                    palautetaan lähetykset alkaen tämän lähetyksen jälkeen
   * @param enintaan                  palautetaan enintään näin monta lähetystä
   * @param kayttooikeusTunnisteet    käyttäjän käyttöoikeuksien tunnisteet (Option.Empty tarkoittaa pääkäyttäjää)
   * @param otsikkoHakuLauseke        lauseke jonka perusteella haetaan lähetyksiä perustuen jonkin sen viestin otsikkoon
   * @param sisaltoHakuLauseke        lauseke jonka perusteella haetaan lähetyksia perustuen jonkin sen viestin sisältöön
   * @param vastaanottajaHakuLauseke  lauseke jonka perusteella haetaan lähetyksia perustuen jonkin sen viestin vastaanottajaan
   * @param lahettajaHakuLauseke      lauseke jonka perusteella haetaan lähetyksia perustuen jonkin sen viestin lähettäjään
   * @param metadataHakuLauseke       lauseke jonka perusteella haetaan lähetyksia perustuen jonkin sen viestin metadataan
   *
   * Haku perustuu Postgresin GIN-indeksiin, sekä tekstikenttien osalta tsvector-tietotyyppiin ja websearch_to_tsquery-
   * funktioon (https://www.postgresql.org/docs/current/textsearch-controls.html).
   *
   * @return                          tuple jossa maksimissaan enintään-parametrin määrä hakukriteereihin sopivat lähetykset
   *                                  järjestettynä uusimmasta vanhimpaan ja tieto siitä onko kriteereihin sopiviä lähetyksiä
   *                                  lisää
   */
  def searchLahetykset(alkaen: Option[UUID] = Option.empty,
                       enintaan: Int = 65535,
                       kayttooikeusTunnisteet: Option[Set[Int]] = Option.empty,
                       otsikkoHakuLauseke: Option[String] = Option.empty,
                       sisaltoHakuLauseke: Option[String] = Option.empty,
                       vastaanottajaHakuLauseke: Option[String] = Option.empty,
                       lahettajaHakuLauseke: Option[String] = Option.empty,
                       metadataHakuLausekkeet: Option[Map[String, Seq[String]]] = Option.empty,
                       lahettavaPalveluHakuLauseke: Option[String] = Option.empty): (Seq[Lahetys], Boolean) =
    if(otsikkoHakuLauseke.isEmpty && sisaltoHakuLauseke.isEmpty && vastaanottajaHakuLauseke.isEmpty && lahettajaHakuLauseke.isEmpty
      && metadataHakuLausekkeet.isEmpty && lahettavaPalveluHakuLauseke.isEmpty)
      getLahetykset(alkaen, enintaan, kayttooikeusTunnisteet)
    else
      val lahetykset = Await.result(db.run(
          (sql"""
            SELECT tunniste, otsikko, omistaja, lahettavapalvelu, lahettavanvirkailijanoid, lahettajannimi, lahettajansahkoposti, replyto, prioriteetti, to_json(luotu::timestamptz)#>>'{}'
            FROM lahetykset
            WHERE tunniste IN (
              SELECT DISTINCT lahetys_tunniste FROM viestit
              WHERE
              """
              concat getViestienHakuLausekkeet(Option.empty, kayttooikeusTunnisteet, otsikkoHakuLauseke, sisaltoHakuLauseke,
                      vastaanottajaHakuLauseke, lahettajaHakuLauseke, metadataHakuLausekkeet, lahettavaPalveluHakuLauseke) concat
           sql"""
            ) AND (${alkaen.isEmpty} OR tunniste<${alkaen.map(a => a.toString).getOrElse(null)}::uuid)
            ORDER BY tunniste DESC LIMIT ${enintaan}
           """).as[(String, String, String, String, String, String, String, String, String, String)]), DB_TIMEOUT)
              .map((tunniste, otsikko, omistaja, lahettavapalvelu, lahettavanVirkailijanOid, lahettajanNimi, lahettajanSahkoposti, replyto, prioriteetti, luotu) =>
                Lahetys(UUID.fromString(tunniste), otsikko, omistaja, lahettavapalvelu, Option.apply(lahettavanVirkailijanOid),
                  Kontakti(Option.apply(lahettajanNimi), lahettajanSahkoposti), Option.apply(replyto), Prioriteetti.valueOf(prioriteetti), Instant.parse(luotu)))
      if (lahetykset.size <= enintaan)
        (lahetykset, false)
      else
        (lahetykset.take(lahetykset.length - 1), true)

  def getVastaanottajanTilaLausekkeet(raportointiTila: Option[RaportointiTila]) =
    raportointiTila match
      case Some(RaportointiTila.epaonnistui) => sql"""vastaanottajat.tila IN (#${raportointiTilat.epaonnistuneet.map(tila => "'"+tila.toString+"'").mkString(",")})"""
      case Some(RaportointiTila.kesken) => sql"""vastaanottajat.tila IN (#${raportointiTilat.kesken.map(tila => "'"+tila.toString+"'").mkString(",")})"""
      case Some(RaportointiTila.valmis) => sql"""vastaanottajat.tila IN (#${raportointiTilat.valmiit.map(tila => "'"+tila.toString+"'").mkString(",")})"""
      case None => sql"""true"""

  /**
   * Haetaan lähetyksen vastaanottajat perustuen annettuihin hakukriteereihin järjestettynä vastaanottajatunnuksen mukaan.
   *
   * @param lahetysTunniste           lähetyksen tunniste
   * @param alkaen                    alkaen tästä vastaanottajasta
   * @param enintaan                  enintään näin monta vastaanottajaa
   * @param kayttooikeusTunnisteet    käyttäjän käyttöoikeuksien tunnisteet
   * @param otsikkoHakuLauseke        lauseke jonka perusteella haetaan lähetyksiä perustuen jonkin sen viestin otsikkoon
   * @param sisaltoHakuLauseke        lauseke jonka perusteella haetaan lähetyksia perustuen jonkin sen viestin sisältöön
   * @param vastaanottajaHakuLauseke  lauseke jonka perusteella haetaan lähetyksia perustuen jonkin sen viestin vastaanottajaan
   * @param metadataHakuLauseke       lauseke jonka perusteella haetaan lähetyksia perustuen jonkin sen viestin metadataan
   * @param raportointiTila           vastaanottajan tila (kesken, valmis, virhe)
   *
   * @return                          maksimissaan enintään-parametrin määrittämä määrä lähetyksen vastaanottajia jotka
   *                                  sopivat hakukriteereihin
   */
  def searchVastaanottajat(lahetysTunniste: UUID,
                           alkaen: Option[UUID] = Option.empty,
                           enintaan: Option[Int] = Option.empty,
                           kayttooikeusTunnisteet: Option[Set[Int]] = Option.empty,
                           otsikkoHakuLauseke: Option[String] = Option.empty,
                           sisaltoHakuLauseke: Option[String] = Option.empty,
                           vastaanottajaHakuLauseke: Option[String] = Option.empty,
                           metadataHakuLausekkeet: Option[Map[String, Seq[String]]] = Option.empty,
                           raportointiTila: Option[RaportointiTila] = Option.empty): Seq[Vastaanottaja] =
    Await.result(db.run(
      (sql"""
        SELECT vastaanottajat.tunniste, vastaanottajat.viesti_tunniste, vastaanottajat.nimi, vastaanottajat.sahkopostiosoite, vastaanottajat.tila, vastaanottajat.prioriteetti, vastaanottajat.ses_tunniste
        FROM viestit JOIN vastaanottajat ON viestit.tunniste=vastaanottajat.viesti_tunniste
        WHERE
        """
        concat getViestienHakuLausekkeet(Option.apply(lahetysTunniste), kayttooikeusTunnisteet, otsikkoHakuLauseke,
        sisaltoHakuLauseke, vastaanottajaHakuLauseke, Option.empty, metadataHakuLausekkeet, Option.empty) concat
        sql"""
        AND (${vastaanottajaHakuLauseke.isEmpty} OR vastaanottajat.sahkopostiosoite=${vastaanottajaHakuLauseke.getOrElse("")})
        AND (""" concat getVastaanottajanTilaLausekkeet(raportointiTila) concat sql""")
        AND (${alkaen.isEmpty} OR vastaanottajat.tunniste<${alkaen.map(a => a.toString).getOrElse("")}::uuid)
        ORDER BY vastaanottajat.tunniste DESC
        LIMIT ${enintaan.getOrElse(65535)}
        """).as[(String, String, String, String, String, String, String)]), DB_TIMEOUT).map((tunniste, viestiTunniste, nimi, sahkopostiOsoite, tila, prioriteetti, sesTunniste)
    => Vastaanottaja(UUID.fromString(tunniste), UUID.fromString(viestiTunniste), Kontakti(Option.apply(nimi), sahkopostiOsoite),
        VastaanottajanTila.valueOf(tila), Prioriteetti.valueOf(prioriteetti), Option.apply(sesTunniste)))

  /**
   * Palauttaa lähetyksen jos käyttäjällä on siihen oikeudet
   *
   * @param tunniste       lähetyksen tunniste
   * @param kayttooikeudet lista käyttäjän käyttöoikeuksista
   * @return tunnistetta vastaava lähetys, jos käyttäjällä on siihen oikeudet
   */
  def getLahetysKayttooikeusrajauksilla(tunniste: UUID, kayttooikeudet: Set[Kayttooikeus]): Option[Lahetys] =
    if (kayttooikeudet.isEmpty)
      Option.empty
    else
      Await.result(db.run(
          sql"""
          SELECT lahetykset.tunniste, otsikko, omistaja, lahettavapalvelu, lahettavanvirkailijanoid, lahettajannimi, lahettajansahkoposti, replyto, prioriteetti, to_json(lahetykset.luotu::timestamptz)#>>'{}'
          FROM lahetykset
          #${queryUtil.lahetyksenKayttooikeudetJoin(kayttooikeudet)}
          WHERE lahetykset.tunniste=${tunniste.toString}::uuid
          #${queryUtil.kayttooikeudetWhere(kayttooikeudet)}
       """.as[(String, String, String, String, String, String, String, String, String, String)].headOption), DB_TIMEOUT)
        .map((tunniste, otsikko, omistaja, lahettavapalvelu, lahettavanVirkailijanOid, lahettajanNimi, lahettajanSahkoposti, replyto, prioriteetti, luotu) =>
          Lahetys(UUID.fromString(tunniste), otsikko, omistaja, lahettavapalvelu, Option.apply(lahettavanVirkailijanOid),
            Kontakti(Option.apply(lahettajanNimi), lahettajanSahkoposti), Option.apply(replyto), Prioriteetti.valueOf(prioriteetti), Instant.parse(luotu)))

  /**
   * Palauttaa listan lähetyksiä hakuehdoilla rajattuna käyttöoikeuksien mukaan
   *
   * @param alkaen         aikaleima, jonka jälkeen luodut haetaan (sivutus)
   * @param enintaan       palautettavan lähetysjoukon maksimikoko (sivutus)
   * @param kayttooikeudet käyttäjän käyttöoikeudet
   * @param vastaanottajanEmail
   * @return hakuehtoja vastaavat lähetykset
   */
  def getLahetykset(alkaen: Option[Instant], enintaan: Option[Int], kayttooikeudet: Set[Kayttooikeus], vastaanottajanEmail: String = ""): Seq[Lahetys] =
    if (kayttooikeudet.isEmpty)
      Seq.empty
    else
      val selectLahetyksetSql =
        """SELECT lahetykset.tunniste, lahetykset.otsikko, lahetykset.omistaja, lahettavapalvelu, lahettavanVirkailijanOid, lahettajanNimi, lahettajanSahkoposti, replyto, lahetykset.prioriteetti, to_json(lahetykset.luotu::timestamptz)#>>'{}' FROM lahetykset"""
      val vastaanottajatJoin = if vastaanottajanEmail.isEmpty() then ""
        else " JOIN viestit ON lahetykset.tunniste=viestit.lahetys_tunniste JOIN vastaanottajat ON vastaanottajat.viesti_tunniste=viestit.tunniste "
      val vastaanottajatWhere = if vastaanottajanEmail.isEmpty() then ""
        else s" AND vastaanottajat.sahkopostiosoite ='$vastaanottajanEmail'"

      val lahetyksetQuery = sql"""#$selectLahetyksetSql
        #${queryUtil.lahetyksenKayttooikeudetJoin(kayttooikeudet)}
        #$vastaanottajatJoin
        WHERE lahetykset.luotu<${alkaen.getOrElse(Instant.now()).toString}::timestamptz
        #${queryUtil.kayttooikeudetWhere(kayttooikeudet)}
        #$vastaanottajatWhere
        GROUP BY lahetykset.tunniste
        ORDER BY lahetykset.luotu DESC
        LIMIT ${enintaan.getOrElse(256)}
        """.as[(String, String, String, String, String, String, String, String, String, String)]

      Await.result(db.run(lahetyksetQuery), DB_TIMEOUT)
        .map((tunniste, otsikko, omistaja, lahettavapalvelu, lahettavanVirkailijanOid, lahettajanNimi, lahettajanSahkoposti, replyTo, prioriteetti, luotu) =>
          Lahetys(UUID.fromString(tunniste), otsikko, omistaja, lahettavapalvelu, Option.apply(lahettavanVirkailijanOid), Kontakti(Option.apply(lahettajanNimi), lahettajanSahkoposti), Option.apply(replyTo), Prioriteetti.valueOf(prioriteetti), Instant.parse(luotu)))

  /**
   * Hakee yhteenvedon lähetyksien vastaanottajien lukumääristä tiloittain
   *
   * @param lahetysTunnisteet lista lähetysten tunnisteita
   * @param kayttooikeudet  käyttäjän oikeudet
   * @return lähetyksen vastaanottotilat mapattuna lähetystunnuksiin
   */
  def getLahetystenVastaanottotilat(lahetysTunnisteet: Seq[UUID], kayttooikeudet: Set[Kayttooikeus]): Map[UUID, Seq[(String, Int)]] =
    if (lahetysTunnisteet.isEmpty) return Map.empty
    
    val vastaanottajaTilatQuery = sql"""
       SELECT viestit.lahetys_tunniste, vastaanottajat.tila, count(*) as vastaanottajia
       FROM vastaanottajat JOIN viestit ON vastaanottajat.viesti_tunniste=viestit.tunniste
       #${queryUtil.viestinKayttooikeudetJoin(kayttooikeudet)}
       WHERE viestit.lahetys_tunniste IN (#${lahetysTunnisteet.map(tunniste => "'" + tunniste + "'").mkString(",")})
       #${queryUtil.kayttooikeudetWhere(kayttooikeudet)}
       GROUP BY viestit.lahetys_tunniste, vastaanottajat.tila
       ORDER BY viestit.lahetys_tunniste, vastaanottajat.tila
     """.as[(String, String, Int)]

    Await.result(db.run(vastaanottajaTilatQuery), DB_TIMEOUT)
      .groupMap((lahetysTunniste, tila, vastaanottajalkm) => UUID.fromString(lahetysTunniste))((lahetysTunniste, tila, vastaanottajalkm) => (tila, vastaanottajalkm))

  /**
   * Hakee lähetyksiin liittyvät maskit
   *
   * @param lahetysTunnisteet lista lähetysten tunnisteita
   * @param kayttooikeudet    käyttäjän oikeudet
   * @return lähetyksen viestien maskit mapattuna lähetystunnuksiin
   */
  def getLahetystenMaskit(lahetysTunnisteet: Seq[UUID], kayttooikeudet: Set[Kayttooikeus]): Map[UUID, Map[String, Option[String]]] =
    if (lahetysTunnisteet.isEmpty) return Map.empty

    val maskitQuery =
      sql"""
        SELECT lahetys_tunniste, salaisuus, maski
        FROM maskit JOIN viestit ON maskit.viesti_tunniste=viestit.tunniste
        #${queryUtil.viestinKayttooikeudetJoin(kayttooikeudet)}
        WHERE viestit.lahetys_tunniste IN (#${lahetysTunnisteet.map(tunniste => "'" + tunniste + "'").mkString(",")})
        """
        .as[(String, String, String)]

    Await.result(db.run(maskitQuery), DB_TIMEOUT)
      .groupBy((lahetysTunniste, salaisuus, maski) => lahetysTunniste)
      .map((lahetysTunniste, maskit) => UUID.fromString(lahetysTunniste) -> maskit.map((lahetysTunniste, salaisuus, maski) => salaisuus -> Option.apply(maski)).toMap)

  /**
   * Hakee lähetyksen viestin lukumäärän
   *
   * @param lahetysTunniste lähetyksen tunniste
   * @return lähetyksen viestien määrä
   */
  def getLahetyksenViestiLkm(lahetysTunniste: UUID): Int =
    val viestiCount = sql"""
        SELECT count(1) FROM viestit WHERE lahetys_tunniste=${lahetysTunniste.toString}::uuid
         """.as[Int]
    Await.result(db.run(viestiCount), DB_TIMEOUT).find(i => true).get

  /**
   * Hakee yksittäisen viestin lähetystunnisteella (massaviesti) tai viestitunnisteella
   *
   * @param lahetysTunniste lähetyksen tunniste
   * @param kayttooikeudet  käyttäjän oikeudet
   * @return lähetyksen vastaanottajat
   */
  def getMassaViestiLahetystunnisteella(lahetysTunniste: UUID, kayttooikeudet: Set[Kayttooikeus]): Option[RaportointiViesti] =
    if (kayttooikeudet.isEmpty)
      Option.empty
    else
      val viestiQuery =
        sql"""
                SELECT viestit.tunniste, lahetys_tunniste, otsikko, sisalto, sisallontyyppi, kielet_fi, kielet_sv, kielet_en, omistaja, prioriteetti
                FROM viestit
                #${queryUtil.viestinKayttooikeudetJoin(kayttooikeudet)}
                WHERE viestit.lahetys_tunniste=${lahetysTunniste.toString}::uuid
                #${queryUtil.kayttooikeudetWhere(kayttooikeudet)}
             """
          .as[(String, String, String, String, String, Boolean, Boolean, Boolean, String, String)]

      val maskitQuery =
        sql"""
          SELECT viesti_tunniste, salaisuus, maski
          FROM maskit JOIN viestit ON maskit.viesti_tunniste=viestit.tunniste
          WHERE viestit.lahetys_tunniste = ${lahetysTunniste.toString}::uuid
          """
          .as[(String, String, String)]

      val maskit: Map[String, Map[String, Option[String]]] = Await.result(db.run(maskitQuery), DB_TIMEOUT)
        .groupBy((viestiTunniste, salaisuus, maski) => viestiTunniste)
        .map((viestiTunniste, maskit) => viestiTunniste -> maskit.map((viestiTunniste, salaisuus, maski) => salaisuus -> Option.apply(maski)).toMap)

      Await.result(db.run(viestiQuery), DB_TIMEOUT)
        .map((tunniste, lahetysTunniste, otsikko, sisalto, sisallonTyyppi, kieletFi, kieletSv, kieletEn, omistaja, prioriteetti)
        => RaportointiViesti(
            tunniste = UUID.fromString(tunniste),
            lahetysTunniste = UUID.fromString(lahetysTunniste),
            otsikko = otsikko,
            sisalto = sisalto,
            sisallonTyyppi = SisallonTyyppi.valueOf(sisallonTyyppi),
            kielet = toKielet(kieletFi, kieletSv, kieletEn),
            maskit = maskit.get(tunniste).getOrElse(Map.empty),
            omistaja = omistaja,
            prioriteetti = Prioriteetti.valueOf(prioriteetti))).headOption

  /**
   * Hakee yksittäisen viestin lähetystunnisteella (massaviesti) tai viestitunnisteella
   *
   * @param lahetysTunniste lähetyksen tunniste
   * @param viestiTunniste  viestin tunniste
   * @param kayttooikeudet  käyttäjän oikeudet
   * @return lähetyksen vastaanottajat
   */
  def getRaportointiViestiTunnisteella(viestiTunniste: UUID, kayttooikeudet: Set[Kayttooikeus]): Option[RaportointiViesti] =
    if (kayttooikeudet.isEmpty)
      Option.empty
    else
      val viestiQuery =
        sql"""
        SELECT viestit.tunniste, lahetys_tunniste, otsikko, sisalto, sisallontyyppi, kielet_fi, kielet_sv, kielet_en, omistaja, prioriteetti
        FROM viestit
        #${queryUtil.viestinKayttooikeudetJoin(kayttooikeudet)}
        WHERE viestit.tunniste=${viestiTunniste.toString}::uuid
        #${queryUtil.kayttooikeudetWhere(kayttooikeudet)}
        """
        .as[(String, String, String, String, String, Boolean, Boolean, Boolean, String, String)]

      val maskitQuery =
        sql"""
            SELECT viesti_tunniste, salaisuus, maski
            FROM maskit JOIN viestit ON maskit.viesti_tunniste=viestit.tunniste
            WHERE viestit.tunniste = ${viestiTunniste.toString}::uuid
        """
          .as[(String, String, String)]

      val maskit: Map[String, Map[String, Option[String]]] = Await.result(db.run(maskitQuery), DB_TIMEOUT)
        .groupBy((viestiTunniste, salaisuus, maski) => viestiTunniste)
        .map((viestiTunniste, maskit) => viestiTunniste -> maskit.map((viestiTunniste, salaisuus, maski) => salaisuus -> Option.apply(maski)).toMap)

      Await.result(db.run(viestiQuery), DB_TIMEOUT)
        .map((tunniste, lahetysTunniste, otsikko, sisalto, sisallonTyyppi, kieletFi, kieletSv, kieletEn, omistaja, prioriteetti)
        => RaportointiViesti(
            tunniste = UUID.fromString(tunniste),
            lahetysTunniste = UUID.fromString(lahetysTunniste),
            otsikko = otsikko,
            sisalto = sisalto,
            sisallonTyyppi = SisallonTyyppi.valueOf(sisallonTyyppi),
            kielet = toKielet(kieletFi, kieletSv, kieletEn),
            maskit = maskit.get(tunniste).getOrElse(Map.empty),
            omistaja = omistaja,
            prioriteetti = Prioriteetti.valueOf(prioriteetti))).headOption

  private def getViestiLahetystunnisteellaQuery(lahetysTunniste: String, kayttooikeudet: Set[Kayttooikeus]) = {
    sql"""
            SELECT viestit.tunniste, lahetys_tunniste, otsikko, sisalto, sisallontyyppi, kielet_fi, kielet_sv, kielet_en, omistaja, prioriteetti
            FROM viestit
            #${queryUtil.viestinKayttooikeudetJoin(kayttooikeudet)}
            WHERE viestit.lahetys_tunniste=${lahetysTunniste}::uuid
            #${queryUtil.kayttooikeudetWhere(kayttooikeudet)}
         """
      .as[(String, String, String, String, String, Boolean, Boolean, Boolean, String, String)]
  }

  private def getViestiTunnisteellaQuery(viestiTunniste: String, kayttooikeudet: Set[Kayttooikeus]) = {
    sql"""
            SELECT viestit.tunniste, lahetys_tunniste, otsikko, sisalto, sisallontyyppi, kielet_fi, kielet_sv, kielet_en, omistaja, prioriteetti
            FROM viestit
            #${queryUtil.viestinKayttooikeudetJoin(kayttooikeudet)}
            WHERE viestit.tunniste=${viestiTunniste}::uuid
            #${queryUtil.kayttooikeudetWhere(kayttooikeudet)}
         """
      .as[(String, String, String, String, String, Boolean, Boolean, Boolean, String, String)]
  }
}
