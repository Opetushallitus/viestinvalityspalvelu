package fi.oph.viestinvalitys.business

import fi.oph.viestinvalitys.business.VastaanottajanTila
import fi.oph.viestinvalitys.db.DbUtil
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
}

/**
 * Lähetykseen liittyvät business-operaatiot
 *
 * @param db  oletuskannan sijaan käytettävä tietokanta (testejä varten)
 */
class LahetysOperaatiot(db: JdbcBackend.JdbcDatabaseDef) {

  def this() = {
    this(DbUtil.getDatabase())
  }

  implicit val executionContext: ExecutionContext = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  val LOG = LoggerFactory.getLogger(classOf[LahetysOperaatiot]);

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
    val lahetysTunniste = DbUtil.getUUID()
    val lahetysInsertAction = sqlu"""INSERT INTO lahetykset VALUES(${lahetysTunniste.toString}::uuid, ${otsikko}, ${omistaja}, now())"""

    val kayttooikeusInsertActions = DBIO.sequence(kayttooikeusRajoitukset.map(kayttooikeus => {
      val tunniste = DbUtil.getUUID()
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
    val tunniste = DbUtil.getUUID()
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
                      lahettavanVirkailijanOID: Option[String],
                      lahettaja: Kontakti,
                      vastaanottajat: Seq[Kontakti],
                      liiteTunnisteet: Seq[UUID],
                      lahettavaPalvelu: String,
                      oLahetysTunniste: Option[UUID],
                      prioriteetti: Prioriteetti,
                      sailytysAika: Int,
                      kayttooikeusRajoitukset: Set[String],
                      metadata: Map[String, String],
                      omistaja: String
                         ): (Viesti, Seq[Vastaanottaja]) = {

    // luodaan lähetystunniste mikäli ei valmiiksi annettu
    val lahetysTunniste = {
      if(oLahetysTunniste.isDefined) oLahetysTunniste.get
      else DbUtil.getUUID()
    }
    val lahetysInsertAction = {
      if(oLahetysTunniste.isDefined) sql"""SELECT 1""".as[Int]
      else sqlu"""INSERT INTO lahetykset VALUES(${lahetysTunniste.toString}::uuid, ${otsikko}, ${omistaja}, now())"""
    }

    // tallennetaan viesti
    val viestiTunniste = DbUtil.getUUID()
    val viestiInsertAction =
      sqlu"""
             INSERT INTO viestit
             VALUES(${viestiTunniste.toString}::uuid, ${lahetysTunniste.toString}::uuid,
                    ${otsikko}, ${sisalto}, ${sisallonTyyppi.toString}, ${kielet.contains(Kieli.FI)},
                    ${kielet.contains(Kieli.SV)}, ${kielet.contains(Kieli.EN)}, ${lahettavanVirkailijanOID},
                    ${lahettaja.nimi}, ${lahettaja.sahkoposti}, ${lahettavaPalvelu}, ${omistaja},
                    ${Instant.now.plusSeconds(60*60*24*sailytysAika).toString}::timestamptz
                    )
          """

    // tallennetaan metadata
    val metadataInsertActions = DBIO.sequence(metadata.map((avain, arvo) => {
      DBIO.sequence(Seq(
        sqlu"""INSERT INTO metadata_avaimet VALUES(${avain}) ON CONFLICT (avain) DO NOTHING""",
        sqlu"""
               INSERT INTO metadata VALUES(${avain}, ${arvo}, ${viestiTunniste.toString}::uuid)
            """
      ))
    }))

    val kayttooikeusInsertActions = DBIO.sequence(kayttooikeusRajoitukset.map(kayttooikeus => {
      val tunniste = DbUtil.getUUID()
      sqlu"""
            INSERT INTO viestit_kayttooikeudet VALUES(${viestiTunniste.toString}::uuid, ${kayttooikeus})
         """
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
      vastaanottajaEntiteetit = vastaanottajat.map(vastaanottaja => Vastaanottaja(DbUtil.getUUID(), viestiTunniste, vastaanottaja, tila, prioriteetti))
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

    Await.result(db.run(DBIO.sequence(Seq(lahetysInsertAction, viestiInsertAction, metadataInsertActions, kayttooikeusInsertActions, liiteRelatedInsertActions)).transactionally), 5.seconds)
    (Viesti(viestiTunniste, lahetysTunniste, otsikko, sisalto, sisallonTyyppi, kielet, lahettavanVirkailijanOID, lahettaja, lahettavaPalvelu, omistaja), vastaanottajaEntiteetit)
  }

  def getVastaanottajat(vastaanottajaTunnisteet: Seq[UUID]): Seq[Vastaanottaja] =
    if(vastaanottajaTunnisteet.isEmpty) return Seq.empty

    val vastaanottajatQuery =
      sql"""
          SELECT tunniste, viesti_tunniste, nimi, sahkopostiosoite, tila, prioriteetti
          FROM vastaanottajat
          WHERE tunniste IN (#${vastaanottajaTunnisteet.map(tunniste => "'" + tunniste + "'").mkString(",")})
       """
        .as[(String, String, String, String, String, String)]
    Await.result(db.run(vastaanottajatQuery), 5.seconds)
      .map((tunniste, viestiTunniste, nimi, sahkopostiOsoite, tila, prioriteetti)
      => Vastaanottaja(UUID.fromString(tunniste), UUID.fromString(viestiTunniste), Kontakti(nimi, sahkopostiOsoite), VastaanottajanTila.valueOf(tila), Prioriteetti.valueOf(prioriteetti)))

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
                lahettajannimi, lahettajansahkoposti, lahettavapalvelu, omistaja
          FROM viestit
          WHERE tunniste IN (#${viestiTunnisteet.map(tunniste => "'" + tunniste + "'").mkString(",")})
       """
        .as[(String, String, String, String, String, Boolean, Boolean, Boolean, String, String, String, String, String)]
    Await.result(db.run(viestitQuery), 5.seconds)
      .map((tunniste, lahetysTunniste, otsikko, sisalto, sisallonTyyppi, kieletFi, kieletSv, kieletEn, lahettavanVirkailijanOid,
            lahettajanNimi, lahettajanSahkoposti, lahettavaPalvelu, omistaja)
      => Viesti(UUID.fromString(tunniste), UUID.fromString(lahetysTunniste), otsikko, sisalto, SisallonTyyppi.valueOf(sisallonTyyppi),
          toKielet(kieletFi, kieletSv, kieletEn), Option.apply(lahettavanVirkailijanOid), Kontakti(lahettajanNimi, lahettajanSahkoposti),
          lahettavaPalvelu, omistaja))

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
            SELECT viesti_tunniste, kayttooikeus
            FROM viestit_kayttooikeudet
            WHERE viesti_tunniste IN (#${viestiTunnisteet.map(t => "'" + t.toString + "'").mkString(",")})
         """.as[(String, String)]

    Await.result(db.run(kayttooikeudetQuery), 5.seconds)
      .groupMap((viestiTunniste, kayttooikeus) => UUID.fromString(viestiTunniste))((viestiTunniste, kayttooikeus) => kayttooikeus)
      .view.mapValues(oikeudet => oikeudet.toSet).toMap

  private def getLahetettavatVastaanottajat(maara: Int, prioriteetti: Prioriteetti): Seq[UUID] =
    if(maara<=0) return Seq.empty

    var lahetettavat: Seq[String] = null
    val result =
      sql"""
          SELECT tunniste
          FROM vastaanottajat
          WHERE tila='#${VastaanottajanTila.ODOTTAA.toString}' AND prioriteetti=${prioriteetti.toString}::prioriteetti
          ORDER BY aikaisintaan ASC
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
   * Hakee lähetettäväksi uuden joukon vastaanottajia ja merkitsee ne "LAHETYKSESSA"-tilaan.
   *
   * @param maara maksimimäärä kerralla lähetettäviä vastaanottajia, tämän avulla on throttlataan lähetettäviä viestijö,
   *              esim. jos kerran kahdessa sekunnissa haetaan mask. 50 viestiä on lähetysnopeus maksimissaan 100 viestiä/s.
   * @return      lähetettävien vastaanottajien tunnisteet
   */
  def getLahetettavatVastaanottajat(maara: Int): Seq[UUID] =
    val korkea = getLahetettavatVastaanottajat(maara, Prioriteetti.KORKEA)
    korkea.concat(getLahetettavatVastaanottajat(Math.max(0, maara-korkea.size), Prioriteetti.NORMAALI))

  /**
   * Päivittää vastaanottajan tilan
   *
   * @param tunniste  vastaanottajan tunniste
   * @param tila      uusi tila
   */
  def paivitaVastaanottajanTila(tunniste: UUID, tila: VastaanottajanTila, lisatiedot: Option[String]): Unit =
    val paivitaAction = sqlu"""UPDATE vastaanottajat SET tila='#${tila.toString}' WHERE tunniste='#${tunniste.toString}'"""
    val siirtymaAction =
      sqlu"""
            INSERT INTO vastaanottaja_siirtymat VALUES(${tunniste.toString}::uuid, now(), ${tila.toString}, ${lisatiedot.getOrElse(null)})
          """

    Await.result(db.run(DBIO.sequence(Seq(paivitaAction, siirtymaAction)).transactionally), 5.seconds)

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
