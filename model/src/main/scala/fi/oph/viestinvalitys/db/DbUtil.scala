package fi.oph.viestinvalitys.db

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import org.postgresql.ds.PGSimpleDataSource
import org.slf4j.LoggerFactory
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcBackend.Database
import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParameterRequest

import java.util.UUID

enum Mode:
  case LOCAL, TEST, PRODUCTION

object ConfigurationUtil {

  final val AJASTUS_QUEUE_URL_KEY = "AJASTUS_QUEUE_URL"

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

  val LOG = LoggerFactory.getLogger(classOf[String]);

  val localMode = ConfigurationUtil.getMode() == Mode.LOCAL
  var password = {
    if(localMode)
      "app"
    else
      ConfigurationUtil.getParameter("/hahtuva/postgresqls/viestinvalitys/app-user-password")
  }

  def getLocalModeDataSource(): PGSimpleDataSource =
    val ds: PGSimpleDataSource = new PGSimpleDataSource()
    ds.setServerNames(Array("localhost"))
    ds.setDatabaseName("viestinvalitys")
    ds.setPortNumbers(Array(5432))
    ds.setUser("app")
    ds.setPassword(password)
    ds

  def getDatasource(): PGSimpleDataSource =
    if (localMode)
      return getLocalModeDataSource()

    val ds: PGSimpleDataSource = new PGSimpleDataSource()
    ds.setServerNames(Array("viestinvalitys.db.hahtuvaopintopolku.fi"))
    ds.setDatabaseName("viestinvalitys")
    ds.setPortNumbers(Array(5432))
    ds.setUser("app")
    ds.setPassword(password)
    ds

  private def getHikariDatasource() =
    val config = new HikariConfig()
    config.setMaximumPoolSize(2)
    config.setDataSource(getDatasource())
    new HikariDataSource(config)

  val ds = getHikariDatasource()

  def getDatabase(): JdbcBackend.JdbcDatabaseDef =
    LOG.debug("Getting database")
    Database.forDataSource(ds, Option.empty)

  def flushDataSource(): Unit =
    ds.getHikariPoolMXBean.softEvictConnections()
}
