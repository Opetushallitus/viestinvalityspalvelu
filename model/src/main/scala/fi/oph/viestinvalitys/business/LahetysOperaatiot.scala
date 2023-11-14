package fi.oph.viestinvalitys.business

import fi.oph.viestinvalitys.business.VastaanottajanTila
import fi.oph.viestinvalitys.db.DbUtil
import org.slf4j.LoggerFactory
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.PostgresProfile.api.*

import java.util.UUID
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

/**
 * Lähetykseen liittyvät business-operaatiot
 *
 * @param db  oletuskannan sijaan käytettävä tietokanta (testejä varten)
 */
class LahetysOperaatiot(db: JdbcBackend.JdbcDatabaseDef) {

  def this() = {
    this(DbUtil.getDatabase())
  }

  val LOG = LoggerFactory.getLogger(classOf[LahetysOperaatiot]);

  /**
   * Tallentaa uuden lähetyksen.
   *
   * @param otsikko   lähetyksen otsikko
   * @param omistaja  lähetyksen omistaja (luoja), vain sama omistaja voi liittää lähetykseen viestejä
   * @return          tallennettu lähetys
   */
  def tallennaLahetys(otsikko: String, omistaja: String): Lahetys = {
    val tunniste = DbUtil.getUUID()
    val insertAction = sqlu"""INSERT INTO lahetykset VALUES(${tunniste.toString}::uuid, ${otsikko}, ${omistaja})"""
    Await.result(db.run(insertAction), 5.seconds)
    Lahetys(tunniste, otsikko, omistaja)
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
            VALUES(${tunniste.toString}::uuid, ${nimi}, ${contentType}, ${koko}, ${omistaja}, ${LiitteenTila.ODOTTAA.toString})"""
    Await.result(db.run(insertAction), 5.seconds)
    Liite(tunniste, nimi, contentType, koko, omistaja, LiitteenTila.ODOTTAA)

  /**
   * Päivittää liitteen tilan. Tätä käytetään virusskannauksen tuloksen päivittämiseen liitteelle.
   *
   * @param tunniste  päivitettävän liitteen tunniste
   * @param tila      uusi tila
   */
  def paivitaLiitteenTila(tunniste: UUID, tila: LiitteenTila): Unit =
    val updateAction = sqlu"""UPDATE liitteet SET tila=${tila.toString} WHERE tunniste=${tunniste.toString}::uuid"""
    Await.result(db.run(updateAction), 5.seconds)

  /**
   * Hakee liitteitä. Tätä käytetään luotavien viestien validointiin (liitteet olemassa, viestin koko sallituissa rajoissa)
   *
   * @param tunnisteet  haettavien liitteiden tunnisteet
   * @return            löydetyt liitteet
   */
  def getLiitteet(tunnisteet: Seq[UUID]): Seq[Liite] =
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
      else sqlu"""INSERT INTO lahetykset VALUES(${lahetysTunniste.toString}::uuid, ${otsikko}, ${omistaja})"""
    }

    // tallennetaan viesti
    val viestiTunniste = DbUtil.getUUID()
    val viestiInsertAction =
      sqlu"""
             INSERT INTO viestit
             VALUES(${viestiTunniste.toString}::uuid, ${lahetysTunniste.toString}::uuid,
                    ${otsikko}, ${sisalto}, ${sisallonTyyppi.toString}, ${kielet.contains(Kieli.FI)},
                    ${kielet.contains(Kieli.SV)}, ${kielet.contains(Kieli.EN)}, ${lahettavanVirkailijanOID},
                    ${lahettaja.nimi}, ${lahettaja.sahkoposti}, ${lahettavaPalvelu}, ${prioriteetti.toString}
                    )
          """

    // tallennetaan liitteet
    val viestitLiitteetInsertActions = DBIO.sequence(liiteTunnisteet.map(liiteTunniste => {
      sqlu"""
             INSERT INTO viestit_liitteet VALUES(${viestiTunniste.toString}::uuid, ${liiteTunniste.toString}::uuid)
          """
    }))

    // tallennetaan metadata
    val metadataInsertActions = DBIO.sequence(metadata.map((avain, arvo) => {
      DBIO.sequence(Seq(
        sqlu"""INSERT INTO metadata_avaimet VALUES(${avain}) ON CONFLICT (avain) DO NOTHING""",
        sqlu"""
               INSERT INTO metadata VALUES(${avain}, ${arvo}, ${viestiTunniste.toString}::uuid)
            """
      ))
    }))

    // tallennetaan vastaanottajat
    val vastaanottajaEntiteetit = vastaanottajat.map(vastaanottaja => Vastaanottaja(DbUtil.getUUID(), viestiTunniste, vastaanottaja, VastaanottajanTila.ODOTTAA))
    val vastaanottajaEntiteettiInsertActions = DBIO.sequence(vastaanottajaEntiteetit.map(vastaanottaja => {
      sqlu"""
             INSERT INTO vastaanottajat
             VALUES(${vastaanottaja.tunniste.toString}::uuid, ${viestiTunniste.toString}::uuid, ${vastaanottaja.kontakti.nimi},
              ${vastaanottaja.kontakti.sahkoposti}, ${vastaanottaja.tila.toString}, now())
          """
    }))

    Await.result(db.run(DBIO.sequence(Seq(lahetysInsertAction, viestiInsertAction, viestitLiitteetInsertActions, metadataInsertActions, vastaanottajaEntiteettiInsertActions)).transactionally), 5.seconds)
    (Viesti(viestiTunniste, lahetysTunniste, otsikko, sisalto, sisallonTyyppi, kielet, lahettavanVirkailijanOID, lahettaja, lahettavaPalvelu, prioriteetti), vastaanottajaEntiteetit)
  }

  def getVastaanottajat(vastaanottajaTunnisteet: Seq[UUID]): Seq[Vastaanottaja] =
    val vastaanottajatQuery =
      sql"""
          SELECT tunniste, viesti_tunniste, vastaanottajat.nimi, vastaanottajat.sahkopostiosoite, vastaanottajat.tila
          FROM vastaanottajat
          WHERE tunniste IN (#${vastaanottajaTunnisteet.map(tunniste => "'" + tunniste + "'").mkString(",")})
       """
        .as[(String, String, String, String, String)]
    Await.result(db.run(vastaanottajatQuery), 5.seconds)
      .map((tunniste, viestiTunniste, nimi, sahkopostiOsoite, tila)
      => Vastaanottaja(UUID.fromString(tunniste), UUID.fromString(viestiTunniste), Kontakti(nimi, sahkopostiOsoite), VastaanottajanTila.valueOf(tila)))

  def getViestit(viestiTunnisteet: Seq[UUID]): Seq[Viesti] =
    val viestitQuery =
      sql"""
          SELECT tunniste, lahetys_tunniste, otsikko, sisalto, sisallontyyppi, kielet_fi, kielet_sv, kielet_en, lahettavanvirkailijanoid,
                lahettajannimi, lahettajansahkoposti, lahettavapalvelu, prioriteetti
          FROM viestit
          WHERE tunniste IN (#${viestiTunnisteet.map(tunniste => "'" + tunniste + "'").mkString(",")})
       """
        .as[(String, String, String, String, String, Boolean, Boolean, Boolean, String, String, String, String, String)]
    Await.result(db.run(viestitQuery), 5.seconds)
      .map((tunniste, lahetysTunniste, otsikko, sisalto, sisallonTyyppi, kieletFi, kieletSv, kieletEn, lahettavanVirkailijanOid,
            lahettajanNimi, lahettajanSahkoposti, lahettavaPalvelu, prioriteetti)
      => Viesti(UUID.fromString(tunniste), UUID.fromString(lahetysTunniste), otsikko, sisalto, SisallonTyyppi.valueOf(sisallonTyyppi),
          toKielet(kieletFi, kieletSv, kieletEn), Option.apply(lahettavanVirkailijanOid), Kontakti(lahettajanNimi, lahettajanSahkoposti),
          lahettavaPalvelu, Prioriteetti.valueOf(prioriteetti)))

  def getViestinLiitteet(viestiTunnisteet: Seq[UUID]): Map[UUID, Seq[Liite]] =
    val liitteetQuery =
      sql"""
          SELECT viestit_liitteet.viesti_tunniste, liitteet.tunniste, liitteet.nimi, liitteet.contenttype,
            liitteet.koko, liitteet.omistaja, liitteet.tila
          FROM viestit_liitteet JOIN liitteet ON viestit_liitteet.liite_tunniste=liitteet.tunniste
          WHERE viestit_liitteet.viesti_tunniste IN (#${viestiTunnisteet.map(tunniste => "'" + tunniste + "'").mkString(",")})
       """
        .as[(String, String, String, String, Int, String, String)]
    Await.result(db.run(liitteetQuery), 5.seconds)
      .map((viestiTunniste, liiteTunniste, nimi, contentType, koko, omistaja, tila) =>
        UUID.fromString(viestiTunniste) -> Liite(UUID.fromString(liiteTunniste), nimi, contentType, koko, omistaja, LiitteenTila.valueOf(tila)))
      .groupMap((uuid, liite) => uuid)((uuid, liite) => liite)

  /**
   * Hakee lähetettäväksi uuden joukon vastaanottajia ja merkitsee ne "LAHETYKSESSA"-tilaan.
   *
   * @param maara maksimimäärä kerralla lähetettäviä vastaanottajia, tämän avulla on throttlataan lähetettäviä viestijö,
   *              esim. jos kerran kahdessa sekunnissa haetaan mask. 50 viestiä on lähetysnopeus maksimissaan 100 viestiä/s.
   * @return      lähetettävien vastaanottajien tunnisteet
   */
  def getLahetettavatVastaanottajat(maara: Int): Seq[UUID] =
    var lahetettavat: Seq[String] = null
    val result =
      sql"""
            SELECT tunniste
            FROM vastaanottajat
            WHERE tila='#${VastaanottajanTila.ODOTTAA.toString}' AND
            NOT EXISTS (SELECT 1
                        FROM liitteet JOIN viestit_liitteet ON liitteet.tunniste=viestit_liitteet.liite_tunniste
                        WHERE
                          viestit_liitteet.viesti_tunniste=vastaanottajat.viesti_tunniste AND
                          liitteet.tila<>'#${LiitteenTila.PUHDAS.toString}')
            ORDER BY aikaisintaan ASC
            FOR UPDATE
            LIMIT ${maara}
      """.as[String].flatMap(tunnisteet => {
        lahetettavat = tunnisteet
        if (tunnisteet.isEmpty)
          sql"""SELECT 1""".as[Int]
        else
          sqlu"""
                UPDATE vastaanottajat SET tila='#${VastaanottajanTila.LAHETYKSESSA.toString}'
                WHERE tunniste IN (#${tunnisteet.map(tunniste => "'" + tunniste + "'").mkString(",")})
              """
      }).transactionally
    Await.result(db.run(result), 5.seconds)
    lahetettavat.map(tunniste => UUID.fromString(tunniste))

  private def toKielet(kieletFi: Boolean, kieletSv: Boolean, kieletEn: Boolean): Set[Kieli] =
    var kielet: Seq[Kieli] = Seq.empty
    if(kieletFi) kielet = kielet.appended(Kieli.FI)
    if(kieletSv) kielet = kielet.appended(Kieli.SV)
    if(kieletEn) kielet = kielet.appended(Kieli.EN)
    kielet.toSet

  /**
   * Palauttaa lähettämistä varten tarvittavan data joukolle vastaanottajia (Vastaanottajata, Viestit, Liitteet)
   *
   * @param vastaanottajaTunnisteet vastaanottajien tunnisteet joille data haetaan
   * @return                        Vastaanottajat, Viestit mapattuna vastaanottajan UUID:n perusteella, Liitteet
   *                                mapattuna viestin UUID:n perusteella
   */
  def getLahetysData(vastaanottajaTunnisteet: Seq[UUID]): (Seq[Vastaanottaja], Map[UUID, Viesti], Map[UUID, Seq[Liite]]) =
    val vastaanottajat = getVastaanottajat(vastaanottajaTunnisteet)

    val viestiTunnisteet = vastaanottajat.map(v => v.viestiTunniste).toSet
    val viestit = getViestit(viestiTunnisteet.toSeq)
    val viestinLiitteet = getViestinLiitteet(viestiTunnisteet.toSeq)

    (vastaanottajat, viestit.map(viesti => viesti.tunniste -> viesti).toMap, viestinLiitteet)

  /**
   * Päivittää vastaanottajan tilan
   *
   * @param tunniste  vastaanottajan tunniste
   * @param tila      uusi tila
   */
  def paivitaVastaanottajanTila(tunniste: UUID, tila: VastaanottajanTila): Unit =
    val updateAction = sqlu"""UPDATE vastaanottajat SET tila='#${tila.toString}' WHERE tunniste='#${tunniste.toString}'"""
    Await.result(db.run(updateAction), 5.seconds)

}
