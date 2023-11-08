package fi.oph.viestinvalitys.business

import fi.oph.viestinvalitys.business.ViestinTila
import fi.oph.viestinvalitys.db.{DbUtil, Lahetykset, Viestipohjat, Viestit}
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.PostgresProfile.api.*

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class LahetysOperaatiot(db: JdbcBackend.JdbcDatabaseDef) {

  def getLiitteet(tunnisteet: Seq[UUID]): Seq[Liite] =
    val liiteTunnisteRajoite = tunnisteet.map(tunniste => "'" + tunniste + "'").mkString(",")
    val liiteQuery: DBIO[Seq[(String, String, String, Int, String, String)]] = sql"""SELECT tunniste, nimi, content_type, koko, omistaja, tila FROM liitteet WHERE tunniste IN (#${liiteTunnisteRajoite})""".as[(String, String, String, Int, String, String)]
    Await.result(DbUtil.getDatabase().run(liiteQuery), 5.seconds)
      .map((tunniste, nimi, contentType, koko, omistaja, tila) => Liite(UUID.fromString(tunniste), nimi, contentType, koko, omistaja, LiitteenTila.valueOf(tila)))

  def getLahetys(tunniste: UUID): Option[Lahetys] =
    Await.result(DbUtil.getDatabase().run(TableQuery[Lahetykset].filter(_.tunniste === tunniste)
      .result.headOption), 5.seconds)
      .map((tunniste, otsikko, omistaja) => Lahetys(tunniste, otsikko, omistaja))

  def tallennaViestiRyhma(viestit: ViestiRyhma): Seq[Viesti] = {
    // TODO: make transactional

    val lahetysTunniste = {
      if(viestit.lahetysTunniste.isDefined)
        viestit.lahetysTunniste.get
      else
        val lahetysTunniste = DbUtil.getUUID();
        val lahetysInsertAction: DBIO[Option[Int]] = TableQuery[Lahetykset] ++= List((lahetysTunniste, viestit.otsikko, viestit.omistaja))
        Await.result(db.run(lahetysInsertAction), 5.seconds)
        lahetysTunniste
    }

    val viestiPohjaTunniste = DbUtil.getUUID()
    val viestiPohjaInsertAction: DBIO[Option[Int]] = TableQuery[Viestipohjat] ++= List((viestiPohjaTunniste, viestit.otsikko))
    Await.result(db.run(viestiPohjaInsertAction), 5.seconds)

    val viestiEntiteetit = viestit.vastaanottajat.map(vastaanottaja => Viesti(DbUtil.getUUID(), viestiPohjaTunniste, lahetysTunniste, vastaanottaja.nimi, vastaanottaja.sahkopostiOsoite, ViestinTila.ODOTTAA))

    val viestiEntiteettiInsertAction: DBIO[Option[Int]] = TableQuery[Viestit] ++= viestiEntiteetit.map(viesti =>
      (viesti.tunniste, viesti.viestipohjaTunniste, viesti.lahetysTunniste, viesti.vastaanottajanSahkoposti, viesti.tila.toString))
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

  def paivitaViestinTila(tunniste: UUID, tila: ViestinTila): Unit =
    val updateAction = sqlu"""UPDATE viestit SET tila='#${tila.toString}' WHERE tunniste='#${tunniste.toString}'"""
    Await.result(db.run(updateAction), 5.seconds)

}
