package fi.oph.viestinvalitys.business

import fi.oph.viestinvalitys.business.ViestinTila
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.PostgresProfile.api.*

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class LahetysOperaatiot(db: JdbcBackend.JdbcDatabaseDef) {

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
