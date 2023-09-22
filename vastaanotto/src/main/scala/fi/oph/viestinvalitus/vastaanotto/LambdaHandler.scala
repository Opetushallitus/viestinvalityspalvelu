package fi.oph.viestinvalitus.vastaanotto

import com.amazonaws.serverless.exceptions.ContainerInitializationException
import com.amazonaws.serverless.proxy.model.{AwsProxyResponse, HttpApiV2ProxyRequest}
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler, RequestStreamHandler}
import fi.oph.viestinvalitus.vastaanotto.{App, LambdaHandler}
import org.slf4j.{Logger, LoggerFactory}

object LambdaHandler {
  val handler: SpringBootLambdaContainerHandler[HttpApiV2ProxyRequest, AwsProxyResponse] = SpringBootLambdaContainerHandler.getHttpApiV2ProxyHandler(classOf[App])
}

class LambdaHandler extends RequestHandler[HttpApiV2ProxyRequest, AwsProxyResponse] {

  private[viestinvalitus] val LOG = LoggerFactory.getLogger(classOf[RequestStreamHandler])

  override def handleRequest(request: HttpApiV2ProxyRequest, context: Context): AwsProxyResponse = {
    LambdaHandler.handler.proxy(request, context)
  }
}
