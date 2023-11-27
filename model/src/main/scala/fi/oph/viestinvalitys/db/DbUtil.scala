package fi.oph.viestinvalitys.db

import org.postgresql.ds.PGSimpleDataSource
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcBackend.Database
import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParameterRequest

import java.util.UUID

enum Mode:
  case LOCAL, TEST, PRODUCTION

object ConfigurationUtil {
  def getConfigurationItem(key: String): Option[String] =
    sys.env.get(key).orElse(sys.props.get(key))

  def getMode(): Mode =
    getConfigurationItem("MODE").map(value => Mode.valueOf(value)).getOrElse(Mode.PRODUCTION)

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
}

object DbUtil {

  val localMode = ConfigurationUtil.getMode() == Mode.LOCAL

  def getLocalModeDataSource(): PGSimpleDataSource =
    val ds: PGSimpleDataSource = new PGSimpleDataSource()
    ds.setServerNames(Array("localhost"))
    ds.setDatabaseName("viestinvalitys")
    ds.setPortNumbers(Array(5432))
    ds.setUser("app")
    ds.setPassword("app")
    ds

  def getDatasource(): PGSimpleDataSource =
    if (localMode)
      return getLocalModeDataSource()

    val password = ConfigurationUtil.getParameter("/hahtuva/postgresqls/viestinvalitys/app-user-password")

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
}
