package fi.oph.viestinvalitys.db

import org.postgresql.ds.PGSimpleDataSource
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcBackend.Database
import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParameterRequest

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
}
