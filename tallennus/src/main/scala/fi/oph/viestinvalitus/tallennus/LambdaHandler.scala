package fi.oph.viestinvalitus.tallennus

import com.amazonaws.serverless.exceptions.ContainerInitializationException
import com.amazonaws.serverless.proxy.model.{AwsProxyResponse, HttpApiV2ProxyRequest}
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler, RequestStreamHandler}
import fi.oph.viestinvalitus.tallennus.App
import org.slf4j.{Logger, LoggerFactory}
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext

object LambdaHandler {
  val context: ConfigurableApplicationContext = SpringApplication.run(classOf[App])
}
import org.springframework.context.ConfigurableApplicationContext

class LambdaHandler extends RequestHandler[SQSEvent, Void] {

  private[viestinvalitus] val LOG = LoggerFactory.getLogger(classOf[RequestStreamHandler])

  override def handleRequest(event: SQSEvent, context: Context): Void = {
    System.out.println("jeejee")
    null
  }
}
