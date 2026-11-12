package fi.oph.viestinvalitys.raportointi

import com.amazonaws.serverless.proxy.model.*
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler
import com.amazonaws.services.lambda.runtime.*
import fi.oph.viestinvalitys.raportointi.LambdaHandler.{handler, opintopolkuDomain, viestinvalitysUrl}
import fi.oph.viestinvalitys.util.{ConfigurationUtil, LogContext}
import org.slf4j.LoggerFactory

import java.util

object LambdaHandler {
  val opintopolkuDomain = ConfigurationUtil.opintopolkuDomain
  val viestinvalitysUrl = sys.env.getOrElse("VIESTINVALITYS_URL", s"https://viestinvalitys.${opintopolkuDomain}")

  // Cas
  System.setProperty("cas-service.service", viestinvalitysUrl)
  System.setProperty("cas-service.sendRenew", "false")
  System.setProperty("cas-service.key", "viestinvalityspalvelu")
  System.setProperty("web.url.cas", s"https://virkailija.${opintopolkuDomain}/cas")
  System.setProperty("host.virkailija", s"virkailija.${opintopolkuDomain}")
  
  System.setProperty("logging.level.root", "INFO")

  val handler: SpringBootLambdaContainerHandler[HttpApiV2ProxyRequest, AwsProxyResponse] = SpringBootLambdaContainerHandler.getHttpApiV2ProxyHandler(classOf[App])

  val LOG = LoggerFactory.getLogger(classOf[LambdaHandler]);
}

class LambdaHandler extends RequestHandler[HttpApiV2ProxyRequest, AwsProxyResponse] {
  private[viestinvalitys] val LOG = LoggerFactory.getLogger(classOf[RequestStreamHandler])

  private def convertResponse(response: AwsProxyResponse): Unit = {
    if(response.getMultiValueHeaders!=null) {
      LOG.debug("Converting multivalue headers")

      val singleHeaders = new util.HashMap[String, String]()
      response.getMultiValueHeaders.entrySet().forEach(h => {
        if (h.getValue.size() == 1) {
          if (h.getKey.equals("Location")) {
            singleHeaders.put(h.getKey, h.getValue.get(0).replaceAll("https://.*\\.on.aws", viestinvalitysUrl))
          } else {
            singleHeaders.put(h.getKey, h.getValue.get(0))
          }
        }
      })
      response.setHeaders(singleHeaders)
    }
  }

  private def logRequestData(request: HttpApiV2ProxyRequest): Unit = {
    LOG.debug("Headers:")
    if (request.getHeaders != null) request.getHeaders.entrySet().forEach(h => LOG.debug("Single value header: " + h.getKey + ": " + h.getValue))

    LOG.debug("Query string:")
    if(request.getQueryStringParameters!=null) request.getQueryStringParameters.entrySet().forEach(p => LOG.debug("SingleParam: " + p.getKey + ": " + p.getValue))
  }

  override def handleRequest(request: HttpApiV2ProxyRequest, context: Context): AwsProxyResponse = {
    LogContext(requestId = context.getAwsRequestId, functionName = context.getFunctionName)(() => {
      if (request.getHeaders.containsKey("content-type-original")) {
        request.getHeaders.put("content-type", request.getHeaders.get("content-type-original"))
        request.setBase64Encoded(true)
      }

      val response = LambdaHandler.handler.proxy(request, context)
      this.convertResponse(response)
      this.logRequestData(request);
      response
    })
  }
}
