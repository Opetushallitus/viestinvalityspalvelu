package fi.oph.viestinvalitys.vastaanotto


import com.amazonaws.serverless.proxy.model.*
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler
import com.amazonaws.services.lambda.runtime.*
import fi.oph.viestinvalitys.util.{ConfigurationUtil, DbUtil, LogContext}
import fi.oph.viestinvalitys.vastaanotto.LambdaHandler.opintopolkuDomain
import org.crac.{Core, Resource}
import org.slf4j.{Logger, LoggerFactory}

import java.util

object LambdaHandler {

  val opintopolkuDomain: String = ConfigurationUtil.opintopolkuDomain
  private val viestinvalitysUrl = sys.env.getOrElse("VIESTINVALITYS_URL", s"https://viestinvalitys.${opintopolkuDomain}")

  // Cas
  System.setProperty("cas-service.service", s"https://viestinvalitys.${opintopolkuDomain}")
  System.setProperty("cas-service.service", viestinvalitysUrl)
  System.setProperty("cas-service.sendRenew", "false")
  System.setProperty("cas-service.key", "viestinvalityspalvelu")
  System.setProperty("web.url.cas", s"https://virkailija.${opintopolkuDomain}/cas")
  System.setProperty("host.virkailija", s"virkailija.${opintopolkuDomain}")

  // Swagger configuration
  System.setProperty("springdoc.api-docs.path", "/openapi/v3/api-docs")
  System.setProperty("springdoc.swagger-ui.tagsSorter", "alpha")

  System.setProperty("logging.level.root", "INFO")

  val handler: SpringBootLambdaContainerHandler[HttpApiV2ProxyRequest, AwsProxyResponse] = SpringBootLambdaContainerHandler.getHttpApiV2ProxyHandler(classOf[App])
}

class LambdaHandler extends RequestHandler[HttpApiV2ProxyRequest, AwsProxyResponse], Resource {
  Core.getGlobalContext.register(this)
  val LOG: Logger = LoggerFactory.getLogger(classOf[LambdaHandler]);

  private def convertResponse(response: AwsProxyResponse): Unit = {
    if(response.getMultiValueHeaders!=null) {
      LOG.debug("Converting multivalue headers")

      val singleHeaders = new util.HashMap[String, String]()
      response.getMultiValueHeaders.entrySet().forEach(h => {
        if (h.getValue.size() == 1) {
          if (h.getKey.equals("Location")) {
            singleHeaders.put(h.getKey, h.getValue.get(0).replaceAll("https://.*\\.on.aws", s"https://viestinvalitys.${opintopolkuDomain}"))
            singleHeaders.put(h.getKey, h.getValue.get(0).replaceAll("https://.*\\.on.aws", LambdaHandler.viestinvalitysUrl))
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

  @throws[Exception]
  override def beforeCheckpoint(context: org.crac.Context[_ <: Resource]): Unit = {
    LOG.debug("Before checkpoint")
    //val _ = LambdaHandler.handler // Initialize Spring before checkpointing, without dispatching a request.
  }

  @throws[Exception]
  override def afterRestore(context: org.crac.Context[_ <: Resource]): Unit = {
    LOG.debug("After restore")
    DbUtil.flushDataSource()
  }
}

