package fi.oph.viestinvalitys.util

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import org.postgresql.ds.PGSimpleDataSource
import org.slf4j.LoggerFactory
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcBackend.Database

object DbUtil {

  final val LOCAL_POSTGRES_PORT_KEY = "POSTGRES_PORT"
  final val ENVIRONMENT_NAME_KEY = "ENVIRONMENT_NAME"
  final val DB_HOST_KEY = "DB_HOST"

  val LOG = LoggerFactory.getLogger(classOf[String]);

  val localMode = ConfigurationUtil.getMode() == Mode.LOCAL
  val password = {
    if(localMode)
      "app"
    else
      val environmentName = ConfigurationUtil.getConfigurationItem(ENVIRONMENT_NAME_KEY).get
      ConfigurationUtil.getParameter(s"/${environmentName}/postgresqls/viestinvalityspalvelu/app-user-password")
  }

  private def getLocalModeDataSource(): PGSimpleDataSource =
    val ds: PGSimpleDataSource = new PGSimpleDataSource()
    ds.setServerNames(Array("localhost"))
    ds.setDatabaseName("viestinvalityspalvelu")
    ds.setPortNumbers(Array(ConfigurationUtil.getConfigurationItem(LOCAL_POSTGRES_PORT_KEY).map(v => v.toInt).getOrElse(5432)))
    ds.setUser("app")
    ds.setPassword(password)
    ds

  private def getDatasource(): PGSimpleDataSource =
    SecretsManagerClient.getDatabaseSecret match {
      case Some(secret) =>
        getDatabaseSecretBasedDataSource(secret)
      case _ =>
        if (localMode)
          getLocalModeDataSource()
        else
          val dbHost = ConfigurationUtil.getConfigurationItem(DB_HOST_KEY).get
          val ds: PGSimpleDataSource = new PGSimpleDataSource()
          ds.setServerNames(Array(dbHost))
          ds.setSslMode("require")
          ds.setDatabaseName("viestinvalityspalvelu")
          ds.setPortNumbers(Array(5432))
          ds.setUser("app")
          ds.setPassword(password)
          ds
    }

  private def getDatabaseSecretBasedDataSource(secret: DatabaseSecret) = {
    val ds: PGSimpleDataSource = new PGSimpleDataSource()
    ds.setServerNames(Array(secret.host))
    ds.setSslMode("require")
    ds.setDatabaseName(secret.dbname)
    ds.setPortNumbers(Array(secret.port))
    ds.setUser(secret.username)
    ds.setPassword(secret.password)
    ds
  }

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
