package fi.oph.viestinvalitys.business

import fi.oph.viestinvalitys.business.ViestinTila
import fi.oph.viestinvalitys.db.{DbUtil, Lahetykset, Liitteet, Metadata, Viestiryhmat, ViestiryhmatLiitteet, Viestit}
import org.slf4j.LoggerFactory
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.PostgresProfile.api.*

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class LahetysOperaatiot(db: JdbcBackend.JdbcDatabaseDef) {

  val LOG = LoggerFactory.getLogger(classOf[LahetysOperaatiot]);

  def tallennaLahetys(otsikko: String, omistaja: String): Lahetys = {
    val tunniste = DbUtil.getUUID();
    val lahetysInsertAction: DBIO[Option[Int]] = TableQuery[Lahetykset] ++= List((tunniste, otsikko, omistaja))
    Await.result(DbUtil.getDatabase().run(lahetysInsertAction), 5.seconds)
    Lahetys(tunniste, otsikko, omistaja)
  }

  def getLahetys(tunniste: UUID): Option[Lahetys] =
    Await.result(DbUtil.getDatabase().run(TableQuery[Lahetykset].filter(_.tunniste === tunniste)
      .result.headOption), 5.seconds)
      .map((tunniste, otsikko, omistaja) => Lahetys(tunniste, otsikko, omistaja))

  def tallennaLiite(nimi: String, contentType: String, koko: Int, omistaja: String): Liite =
    val tunniste = DbUtil.getUUID()
    val liiteInsertAction: DBIO[Option[Int]] = TableQuery[Liitteet] ++= List((tunniste, nimi, contentType, koko, omistaja, LiitteenTila.ODOTTAA.toString))
    Await.result(DbUtil.getDatabase().run(liiteInsertAction), 5.seconds)
    Liite(tunniste, nimi, contentType, koko, omistaja, LiitteenTila.ODOTTAA)

  def getLiitteet(tunnisteet: Seq[UUID]): Seq[Liite] =
    val liiteTunnisteRajoite = tunnisteet.map(tunniste => "'" + tunniste + "'").mkString(",")
    val liiteQuery: DBIO[Seq[(String, String, String, Int, String, String)]] = sql"""SELECT tunniste, nimi, contenttype, koko, omistaja, tila FROM liitteet WHERE tunniste IN (#${liiteTunnisteRajoite})""".as[(String, String, String, Int, String, String)]
    Await.result(DbUtil.getDatabase().run(liiteQuery), 5.seconds)
      .map((tunniste, nimi, contentType, koko, omistaja, tila) => Liite(UUID.fromString(tunniste), nimi, contentType, koko, omistaja, LiitteenTila.valueOf(tila)))

  def tallennaViestiRyhma(
                           otsikko: String,
                           sisalto: String,
                           sisallonTyyppi: SisallonTyyppi,
                           kielet: Set[Kieli],
                           lahettavanVirkailijanOid: Option[String],
                           lahettaja: Lahettaja,
                           vastaanottajat: Seq[Vastaanottaja],
                           liiteTunnisteet: Seq[UUID],
                           lahettavaPalvelu: String,
                           oLahetysTunniste: Option[UUID],
                           prioriteetti: Prioriteetti,
                           sailytysAika: Int,
                           kayttooikeusRajoitukset: Set[String],
                           metadata: Map[String, String],
                           omistaja: String
                         ): Seq[Viesti] = {
    // TODO: make transactional

    // luodaan lähetystunniste mikäli ei valmiiksi annettu
    val lahetysTunniste = {
      if(oLahetysTunniste.isDefined)
        oLahetysTunniste.get
      else
        val lahetysTunniste = DbUtil.getUUID();
        val lahetysInsertAction: DBIO[Option[Int]] = TableQuery[Lahetykset] ++= List((lahetysTunniste, otsikko, omistaja))
        Await.result(db.run(lahetysInsertAction), 5.seconds)
        lahetysTunniste
    }

    // luodaan viestiryhmä ja tallennetaan kaikille viesteille yhteiset kentät
    val viestiRyhmaTunniste = DbUtil.getUUID()
    val viestiRyhmaInsertAction: DBIO[Option[Int]] = TableQuery[Viestiryhmat] ++=
      List((viestiRyhmaTunniste, otsikko, sisalto, sisallonTyyppi.toString, kielet.contains(Kieli.FI), kielet.contains(Kieli.SV), kielet.contains(Kieli.EN), lahettavanVirkailijanOid, lahettaja.nimi, lahettaja.sahkopostiOsoite, lahettavaPalvelu, prioriteetti.toString))
    Await.result(db.run(viestiRyhmaInsertAction), 5.seconds)

    // tallennetaan liitteet
    val viestiRyhmaLiitteetInsertAction: DBIO[Option[Int]] = TableQuery[ViestiryhmatLiitteet] ++= liiteTunnisteet.map(liiteTunniste => (viestiRyhmaTunniste, liiteTunniste))
    Await.result(db.run(viestiRyhmaLiitteetInsertAction), 5.seconds)

    // tallennetaan metadata
    metadata.map((avain, arvo) => {
      Await.result(db.run(sqlu"""INSERT INTO metadata_avaimet VALUES(${avain}) ON CONFLICT (avain) DO NOTHING"""), 5.seconds)
      val metadataInsertAction: DBIO[Option[Int]] = TableQuery[Metadata] ++= List((avain, arvo, viestiRyhmaTunniste))
      Await.result(db.run(metadataInsertAction), 5.seconds)
    })

    // tallennetaan viestit
    val viestiEntiteetit = vastaanottajat.map(vastaanottaja => Viesti(DbUtil.getUUID(), lahetysTunniste, otsikko, sisalto, sisallonTyyppi, kielet, lahettaja, vastaanottaja, liiteTunnisteet, ViestinTila.ODOTTAA))
    val viestiEntiteettiInsertAction: DBIO[Option[Int]] = TableQuery[Viestit] ++= viestiEntiteetit.map(viesti =>
      (viesti.tunniste, viestiRyhmaTunniste, viesti.lahetysTunniste, viesti.vastaanottaja.nimi, viesti.vastaanottaja.sahkopostiOsoite, viesti.tila.toString))
    Await.result(db.run(viestiEntiteettiInsertAction), 5.seconds)

    viestiEntiteetit
  }

  def getLahettavatViestit(maara: Int): Seq[UUID] =
    var lahetettavat: Seq[String] = null
    val result = sql"""SELECT tunniste FROM viestit WHERE tila='#${ViestinTila.ODOTTAA.toString}' FOR UPDATE LIMIT 10""".as[String].flatMap(tunnisteet => {
      lahetettavat = tunnisteet
      if (tunnisteet.isEmpty)
        sql"""SELECT 1""".as[Int]
      else
        sqlu"""UPDATE viestit SET tila='#${ViestinTila.LAHETYKSESSA.toString}' WHERE tunniste IN (#${tunnisteet.map(tunniste => "'" + tunniste + "'").mkString(",")})"""
    }).transactionally
    Await.result(db.run(result), 5.seconds)
    lahetettavat.map(tunniste => UUID.fromString(tunniste))

  def toKielet(kieletFi: Boolean, kieletSv: Boolean, kieletEn: Boolean): Set[Kieli] =
    var kielet: Seq[Kieli] = Seq.empty
    if(kieletFi) kielet = kielet.appended(Kieli.FI)
    if(kieletSv) kielet = kielet.appended(Kieli.SV)
    if(kieletEn) kielet = kielet.appended(Kieli.EN)
    kielet.toSet

  def getViestit(tunnisteet: Seq[UUID]): Seq[Viesti] =
    val result = sql"""SELECT viestit.tunniste, viestit.lahetys_tunniste, viestiryhmat.otsikko, viestiryhmat.sisalto, viestiryhmat.sisallontyyppi, viestiryhmat.kielet_fi, viestiryhmat.kielet_sv, viestiryhmat.kielet_en, viestiryhmat.lahettajannimi, viestiryhmat.lahettajansahkoposti, viestit.nimi, viestit.sahkopostiosoite, viestit.tila FROM viestit JOIN viestiryhmat ON viestit.viestiryhma_tunniste=viestiryhmat.tunniste WHERE viestit.tunniste IN (#${tunnisteet.map(tunniste => "'" + tunniste + "'").mkString(",")})"""
      .as[(String, String, String, String, String, Boolean, Boolean, Boolean, String, String, String, String, String)]
    Await.result(db.run(result), 5.seconds).map((tunniste, lahetysTunniste, otsikko, sisalto, sisallonTyyppi, kieletFi, kieletSv, kieletEn, lahettajanNimi, lahettajanSahkoposti, vastaanottajanNimi, vastaanottajanSahkoposti, tila) =>
      Viesti(UUID.fromString(tunniste), UUID.fromString(lahetysTunniste), otsikko, sisalto, SisallonTyyppi.valueOf(sisallonTyyppi), toKielet(kieletFi, kieletSv, kieletEn), Lahettaja(lahettajanNimi, lahettajanSahkoposti), Vastaanottaja(vastaanottajanNimi, vastaanottajanSahkoposti), Seq.empty, ViestinTila.valueOf(tila)))

//  def getLiitteet(viestiTunnisteet: Seq[UUID]): Seq[Liite] =

  def paivitaViestinTila(tunniste: UUID, tila: ViestinTila): Unit =
    val updateAction = sqlu"""UPDATE viestit SET tila='#${tila.toString}' WHERE tunniste='#${tunniste.toString}'"""
    Await.result(db.run(updateAction), 5.seconds)

}
