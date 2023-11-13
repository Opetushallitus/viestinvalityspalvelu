package fi.oph.viestinvalitys.business

import fi.oph.viestinvalitys.business.VastaanottajanTila
import fi.oph.viestinvalitys.db.{DbUtil, Lahetykset, Liitteet, Metadata, Vastaanottajat, Viestit, ViestitLiitteet}
import org.slf4j.LoggerFactory
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.PostgresProfile.api.*

import java.time.Instant
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

  def tallennaViesti(
                           otsikko: String,
                           sisalto: String,
                           sisallonTyyppi: SisallonTyyppi,
                           kielet: Set[Kieli],
                           lahettavanVirkailijanOid: Option[String],
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
                         ): Viesti = {
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

    // tallennetaan viesti
    val viestiTunniste = DbUtil.getUUID()
    val viestiInsertAction: DBIO[Option[Int]] = TableQuery[Viestit] ++=
      List((viestiTunniste, lahetysTunniste, otsikko, sisalto, sisallonTyyppi.toString, kielet.contains(Kieli.FI), kielet.contains(Kieli.SV), kielet.contains(Kieli.EN), lahettavanVirkailijanOid, lahettaja.nimi, lahettaja.sahkoposti, lahettavaPalvelu, prioriteetti.toString))
    Await.result(db.run(viestiInsertAction), 5.seconds)

    // tallennetaan liitteet
    val viestitLiitteetInsertAction: DBIO[Option[Int]] = TableQuery[ViestitLiitteet] ++= liiteTunnisteet.map(liiteTunniste => (viestiTunniste, liiteTunniste))
    Await.result(db.run(viestitLiitteetInsertAction), 5.seconds)

    // tallennetaan metadata
    metadata.map((avain, arvo) => {
      Await.result(db.run(sqlu"""INSERT INTO metadata_avaimet VALUES(${avain}) ON CONFLICT (avain) DO NOTHING"""), 5.seconds)
      val metadataInsertAction: DBIO[Option[Int]] = TableQuery[Metadata] ++= List((avain, arvo, viestiTunniste))
      Await.result(db.run(metadataInsertAction), 5.seconds)
    })

    // tallennetaan vastaanottajat
    val aikaisintaan = Instant.now
    val vastaanottajaEntiteetit = vastaanottajat.map(vastaanottaja => Vastaanottaja(DbUtil.getUUID(), vastaanottaja, VastaanottajanTila.ODOTTAA))
    val vastaanottajaEntiteettiInsertAction: DBIO[Option[Int]] = TableQuery[Vastaanottajat] ++= vastaanottajaEntiteetit.map(vastaanottaja =>
      (vastaanottaja.tunniste, viestiTunniste, vastaanottaja.kontakti.nimi, vastaanottaja.kontakti.sahkoposti, vastaanottaja.tila.toString, aikaisintaan))
    Await.result(db.run(vastaanottajaEntiteettiInsertAction), 5.seconds)

    Viesti(viestiTunniste)
  }

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
          sqlu"""UPDATE vastaanottajat SET tila='#${VastaanottajanTila.LAHETYKSESSA.toString}' WHERE tunniste IN (#${tunnisteet.map(tunniste => "'" + tunniste + "'").mkString(",")})"""
      }).transactionally
    Await.result(db.run(result), 5.seconds)
    lahetettavat.map(tunniste => UUID.fromString(tunniste))

  def toKielet(kieletFi: Boolean, kieletSv: Boolean, kieletEn: Boolean): Set[Kieli] =
    var kielet: Seq[Kieli] = Seq.empty
    if(kieletFi) kielet = kielet.appended(Kieli.FI)
    if(kieletSv) kielet = kielet.appended(Kieli.SV)
    if(kieletEn) kielet = kielet.appended(Kieli.EN)
    kielet.toSet

  def getVastaanottajat(tunnisteet: Seq[UUID]): Seq[Vastaanottaja] =
    val result = sql"""SELECT vastaanottajat.tunniste, viestit.lahetys_tunniste, viestit.otsikko, viestit.sisalto, viestit.sisallontyyppi, viestit.kielet_fi, viestit.kielet_sv, viestit.kielet_en, viestit.lahettajannimi, viestit.lahettajansahkoposti, vastaanottajat.nimi, vastaanottajat.sahkopostiosoite, vastaanottajat.tila FROM vastaanottajat JOIN viestit ON vastaanottajat.viesti_tunniste=viestit.tunniste WHERE vastaanottajat.tunniste IN (#${tunnisteet.map(tunniste => "'" + tunniste + "'").mkString(",")})"""
      .as[(String, String, String, String, String, Boolean, Boolean, Boolean, String, String, String, String, String)]
    Await.result(db.run(result), 5.seconds).map((tunniste, lahetysTunniste, otsikko, sisalto, sisallonTyyppi, kieletFi, kieletSv, kieletEn, lahettajanNimi, lahettajanSahkoposti, vastaanottajanNimi, vastaanottajanSahkoposti, tila) =>
      Vastaanottaja(UUID.fromString(tunniste), Kontakti(vastaanottajanNimi, vastaanottajanSahkoposti), VastaanottajanTila.valueOf(tila)))
      //Vastaanottaja(UUID.fromString(tunniste), UUID.fromString(lahetysTunniste), otsikko, sisalto, SisallonTyyppi.valueOf(sisallonTyyppi), toKielet(kieletFi, kieletSv, kieletEn), Kontakti(lahettajanNimi, lahettajanSahkoposti), Kontakti(vastaanottajanNimi, vastaanottajanSahkoposti), Seq.empty, ViestinTila.valueOf(tila)))


//  def getLiitteet(viestiTunnisteet: Seq[UUID]): Seq[Liite] =

  def paivitaVastaanottajanTila(tunniste: UUID, tila: VastaanottajanTila): Unit =
    val updateAction = sqlu"""UPDATE vastaanottajat SET tila='#${tila.toString}' WHERE tunniste='#${tunniste.toString}'"""
    Await.result(db.run(updateAction), 5.seconds)

}
