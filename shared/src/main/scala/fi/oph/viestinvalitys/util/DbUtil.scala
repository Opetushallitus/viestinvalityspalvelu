package fi.oph.viestinvalitys.util

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import org.postgresql.ds.PGSimpleDataSource
import org.slf4j.LoggerFactory
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcBackend.Database
import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParameterRequest

import java.util.UUID

object DbUtil {

  final val LOCAL_POSTGRES_PORT_KEY = "POSTGRES_PORT"

  val LOG = LoggerFactory.getLogger(classOf[String]);

  val localMode = ConfigurationUtil.getMode() == Mode.LOCAL
  var password = {
    if(localMode)
      "app"
    else
      ConfigurationUtil.getParameter("/hahtuva/postgresqls/viestinvalitys/app-user-password")
  }

  private def getLocalModeDataSource(): PGSimpleDataSource =
    val ds: PGSimpleDataSource = new PGSimpleDataSource()
    ds.setServerNames(Array("localhost"))
    ds.setDatabaseName("viestinvalitys")
    ds.setPortNumbers(Array(ConfigurationUtil.getConfigurationItem(LOCAL_POSTGRES_PORT_KEY).map(v => v.toInt).getOrElse(5432)))
    ds.setUser("app")
    ds.setPassword(password)
    ds

  private def getDatasource(): PGSimpleDataSource =
    if (localMode)
      return getLocalModeDataSource()

    val ds: PGSimpleDataSource = new PGSimpleDataSource()
    ds.setServerNames(Array("viestinvalitys.db.hahtuvaopintopolku.fi"))
    ds.setDatabaseName("viestinvalitys")
    ds.setPortNumbers(Array(5432))
    ds.setUser("app")
    ds.setPassword(password)
    ds

  lazy val pooledDatasource = {
    val config = new HikariConfig()
    config.setMaximumPoolSize(2)
    config.setDataSource(getDatasource())
    new HikariDataSource(config)
  }

  lazy val database = Database.forDataSource(pooledDatasource, Option.empty)

  def flushDataSource(): Unit =
    pooledDatasource.getHikariPoolMXBean.softEvictConnections()
}
