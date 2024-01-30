package fi.oph.viestinvalitys.raportointi

import com.amazonaws.serverless.exceptions.ContainerInitializationException
import com.amazonaws.serverless.proxy.model.*
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler
import com.amazonaws.services.lambda.runtime.*
import fi.oph.viestinvalitys.raportointi.LambdaHandler.{handler, opintopolkuDomain}
import fi.oph.viestinvalitys.raportointi.priming.PrimingContext
import fi.oph.viestinvalitys.raportointi.resource.RaportointiAPIConstants
import fi.oph.viestinvalitys.util.{ConfigurationUtil, DbUtil, LogContext}
import org.crac.{Core, Resource}
import org.slf4j.{Logger, LoggerFactory}
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.security.core.context.SecurityContextHolder

import java.util
import java.util.stream.Collectors

object LambdaHandler {
  val opintopolkuDomain = ConfigurationUtil.opintopolkuDomain

  // Cas
  System.setProperty("cas-service.service", s"https://viestinvalitys.${opintopolkuDomain}")
  System.setProperty("cas-service.sendRenew", "false")
  System.setProperty("cas-service.key", "viestinvalityspalvelu")
  System.setProperty("web.url.cas", s"https://virkailija.${opintopolkuDomain}/cas")
  System.setProperty("kayttooikeus-service.userDetails.byUsername", "http://alb." + opintopolkuDomain + "/kayttooikeus-service/userDetails/$1")
  System.setProperty("host.virkailija", s"virkailija.${opintopolkuDomain}")
  
  System.setProperty("logging.level.root", "INFO")

  val handler: SpringBootLambdaContainerHandler[HttpApiV2ProxyRequest, AwsProxyResponse] = SpringBootLambdaContainerHandler.getHttpApiV2ProxyHandler(classOf[App])

  val LOG = LoggerFactory.getLogger(classOf[LambdaHandler]);
}

class LambdaHandler extends RequestHandler[HttpApiV2ProxyRequest, AwsProxyResponse], Resource {
  Core.getGlobalContext.register(this)
  private[viestinvalitys] val LOG = LoggerFactory.getLogger(classOf[RequestStreamHandler])

  private def convertResponse(response: AwsProxyResponse): Unit = {
    if(response.getMultiValueHeaders!=null) {
      LOG.debug("Converting multivalue headers")

      val singleHeaders = new util.HashMap[String, String]()
      response.getMultiValueHeaders.entrySet().forEach(h => {
        if (h.getValue.size() == 1) {
          if (h.getKey.equals("Location")) {
            singleHeaders.put(h.getKey, h.getValue.get(0).replaceAll("https://.*\\.on.aws", s"https://viestinvalitys.${opintopolkuDomain}"))
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
  def beforeCheckpoint(context: org.crac.Context[_ <: Resource]): Unit = {
    System.out.println("Before checkpoint")

    // this force spring boot initialization
    LambdaHandler.handler.toString

    // Priming
    try
      val req = new HttpApiV2ProxyRequest()
      req.setRequestContext(new HttpApiV2ProxyRequestContext)
      req.getRequestContext.setHttp(new HttpApiV2HttpContext)
      req.getRequestContext.getHttp.setPath(RaportointiAPIConstants.HEALTHCHECK_PATH)
      req.getRequestContext.getHttp.setMethod("GET")
      val ctx: Context = new PrimingContext()
      Set(0 to 200).foreach(n => LambdaHandler.handler.proxy(req, ctx))
    catch
      case e: Exception => LOG.debug("priming error")
  }

  @throws[Exception]
  def afterRestore(context: org.crac.Context[_ <: Resource]): Unit = {
    System.out.println("After restore")
    DbUtil.flushDataSource()
  }
}
