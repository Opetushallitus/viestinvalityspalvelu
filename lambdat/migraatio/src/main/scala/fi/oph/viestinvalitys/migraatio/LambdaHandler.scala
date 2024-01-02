package fi.oph.viestinvalitys.migraatio

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import fi.oph.viestinvalitys.util.DbUtil
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory

class LambdaHandler extends RequestHandler[Any, Void] {

  val LOG = LoggerFactory.getLogger(classOf[LambdaHandler]);

  override def handleRequest(event: Any, context: Context): Void = {
    LOG.info("Ajetaan migraatiot")

    val flyway = Flyway.configure()
      .dataSource(DbUtil.pooledDatasource)
      .outOfOrder(true)
      .locations("flyway")
      .load()
    flyway.migrate()
    null
  }
}
