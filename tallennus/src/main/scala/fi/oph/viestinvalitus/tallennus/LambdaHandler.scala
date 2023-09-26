package fi.oph.viestinvalitus.tallennus

import com.amazonaws.serverless.exceptions.ContainerInitializationException
import com.amazonaws.serverless.proxy.model.{AwsProxyResponse, HttpApiV2ProxyRequest}
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler, RequestStreamHandler}
import fi.oph.viestinvalitus.tallennus.App
import org.postgresql.ds.PGSimpleDataSource
import org.slf4j.{Logger, LoggerFactory}
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import slick.jdbc.PostgresProfile.api.*

import java.util.stream.Collectors
import scala.concurrent.Await
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.concurrent.ExecutionContext.Implicits.global
import concurrent.duration.DurationInt


object LambdaHandler {
  val context: ConfigurableApplicationContext = App.start(Array())
}

class LambdaHandler extends RequestHandler[SQSEvent, Void] {

  private[viestinvalitus] val LOG = LoggerFactory.getLogger(classOf[RequestStreamHandler])

  override def handleRequest(event: SQSEvent, context: Context): Void = {
    val ds = LambdaHandler.context.getBean(classOf[PGSimpleDataSource])

    val viestit = TableQuery[Viestit]
    val db = Database.forDataSource(ds, Option.empty)

    val insertAction: DBIO[Option[Int]] = viestit ++= event.getRecords.stream.map(record => (0, record.getBody)).collect(Collectors.toList).asScala
    val result = Await.result(db.run(insertAction), 5.seconds)

    System.out.println("jeejee")
    null
  }
}
