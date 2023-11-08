package fi.oph.viestinvalitys.db

import org.postgresql.ds.PGSimpleDataSource
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcBackend.Database
import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParameterRequest

import java.util.UUID
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.PostgresProfile.api.*

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt


object dbUtil {

  def getParameter(name: String): String =
    val ssmClient = SsmClient.builder()
      .credentialsProvider(ContainerCredentialsProvider.builder().build()) // tämä on SnapStartin takia
      .build();

    try {
      val parameterRequest = GetParameterRequest.builder
        .withDecryption(true)
        .name(name).build
      val parameterResponse = ssmClient.getParameter(parameterRequest)
      parameterResponse.parameter.value
    } catch {
      case e: Exception => throw new RuntimeException(e)
    }

  def getDatasource(): PGSimpleDataSource =
    val password = getParameter("/hahtuva/postgresqls/viestinvalitys/app-user-password")

    val ds: PGSimpleDataSource = new PGSimpleDataSource()
    ds.setServerNames(Array("viestinvalitys.db.hahtuvaopintopolku.fi"))
    ds.setDatabaseName("viestinvalitys")
    ds.setPortNumbers(Array(5432))
    ds.setUser("app")
    ds.setPassword(password)
    ds

  def getDatabase(): JdbcBackend.JdbcDatabaseDef =
    Database.forDataSource(getDatasource(), Option.empty)

  def getUUID(): UUID =
    UUID.randomUUID()

  def getLahettavatViestit(maara: Int, db: JdbcBackend.JdbcDatabaseDef): Seq[UUID] =
    var lahetettavat: Seq[String] = null
    val result = sql"""SELECT tunniste FROM viestit WHERE tila='#${ViestinTila.ODOTTAA.toString}' FOR UPDATE LIMIT 10""".as[String].flatMap(tunnisteet => {
      lahetettavat = tunnisteet
      if(tunnisteet.isEmpty)
        sql"""SELECT 1""".as[Int]
      else
        sqlu"""UPDATE viestit SET tila='#${ViestinTila.LAHETYKSESSA.toString}' WHERE tunniste IN (#${tunnisteet.map(tunniste => "'" + tunniste + "'").mkString(",")})"""
    }).transactionally
    Await.result(db.run(result), 5.seconds)
    lahetettavat.map(tunniste => UUID.fromString(tunniste))

  def paivitaViestinTila(tunniste: UUID, tila: ViestinTila, db: JdbcBackend.JdbcDatabaseDef): Unit =
    val updateAction = sqlu"""UPDATE viestit SET tila='#${tila.toString}' WHERE tunniste='#${tunniste.toString}'"""
    Await.result(db.run(updateAction), 5.seconds)
}
