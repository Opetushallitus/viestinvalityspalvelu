package fi.oph.viestinvalitys.orkestraattori

import com.amazonaws.serverless.exceptions.ContainerInitializationException
import com.amazonaws.serverless.proxy.model.{AwsProxyResponse, HttpApiV2ProxyRequest}
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler, RequestStreamHandler}
import fi.oph.viestinvalitys.db.dbUtil
import fi.oph.viestinvalitys.model.Viestipohjat
import org.flywaydb.core.Flyway
import org.postgresql.ds.PGSimpleDataSource
import org.slf4j.{Logger, LoggerFactory}
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import slick.jdbc.PostgresProfile.api.*
import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParameterRequest

import java.util.stream.Collectors
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.CollectionHasAsScala


object LambdaHandler {
  val context: ConfigurableApplicationContext = App.start(Array())

  System.setProperty("logging.level.root", "DEBUG")

  val LOG = LoggerFactory.getLogger(classOf[LambdaHandler]);

  var migrated = false
  def migrate(): Unit =
    if (migrated) return
    migrated = true

    val flyway = Flyway.configure()
      .dataSource(dbUtil.getDatasource())
      .outOfOrder(true)
      .locations("flyway")
      .load()
    flyway.migrate()
}

class LambdaHandler extends RequestHandler[Any, Void] {

  override def handleRequest(event: Any, context: Context): Void = {
/*
    val ds = LambdaHandler.context.getBean(classOf[PGSimpleDataSource])

    val viestit = TableQuery[Viestit]
    val db = Database.forDataSource(ds, Option.empty)

    val insertAction: DBIO[Option[Int]] = viestit ++= event.getRecords.stream.map(record => (0, record.getBody)).collect(Collectors.toList).asScala
    val result = Await.result(db.run(insertAction), 5.seconds)

    System.out.println("jeejee")
*/

    LambdaHandler.migrate()

    LambdaHandler.LOG.info("Running orkestraattori, event is of type: " + event.getClass.getName)

    null
  }
}
