package fi.oph.viestinvalitus

import com.amazonaws.serverless.exceptions.ContainerInitializationException
import com.amazonaws.serverless.proxy.model.{AwsProxyResponse, HttpApiV2ProxyRequest}
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler
import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import com.amazonaws.services.lambda.runtime.RequestHandler
import org.slf4j.{Logger, LoggerFactory}

object LambdaHandler {
  val handler: SpringBootLambdaContainerHandler[HttpApiV2ProxyRequest, AwsProxyResponse] = SpringBootLambdaContainerHandler.getHttpApiV2ProxyHandler(classOf[App])
}

class LambdaHandler extends RequestHandler[HttpApiV2ProxyRequest, AnyRef] {

  private[viestinvalitus] val LOG = LoggerFactory.getLogger(classOf[RequestStreamHandler])

  override def handleRequest(request: HttpApiV2ProxyRequest, context: Context): AnyRef = {
    LambdaHandler.handler.proxy(request, context)
  }
}
