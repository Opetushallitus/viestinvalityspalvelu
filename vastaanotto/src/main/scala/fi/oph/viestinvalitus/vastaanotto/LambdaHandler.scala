package fi.oph.viestinvalitus.vastaanotto

import com.amazonaws.serverless.exceptions.ContainerInitializationException
import com.amazonaws.serverless.proxy.model.{AwsProxyRequest, AwsProxyResponse, HttpApiV2HttpContext, HttpApiV2ProxyRequest, HttpApiV2ProxyRequestContext, MultiValuedTreeMap}
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler
import com.amazonaws.services.lambda.runtime.{ClientContext, CognitoIdentity, Context, LambdaLogger, RequestHandler, RequestStreamHandler}
import fi.oph.viestinvalitus.vastaanotto.App.Ctx
import fi.oph.viestinvalitus.vastaanotto.{App, LambdaHandler}
import org.crac.{Core, Resource}
import org.slf4j.{Logger, LoggerFactory}

import java.util
import java.util.stream.Collectors

object LambdaHandler {
  System.setProperty("cas-service.service", "https://virkailija.hahtuvaopintopolku.fi/viestinvalituspalvelu")
  System.setProperty("cas-service.sendRenew", "false")
  System.setProperty("cas-service.key", "viestinvalituspalvelu")
  System.setProperty("web.url.cas", "https://virkailija.hahtuvaopintopolku.fi/cas")
  System.setProperty("kayttooikeus-service.userDetails.byUsername", "http://alb.hahtuvaopintopolku.fi/kayttooikeus-service/userDetails/$1")
  System.setProperty("host.virkailija", "virkailija.hahtuvaopintopolku.fi")

  System.setProperty("spring.session.store-type", "redis")
  System.setProperty("spring.data.redis.host", "hah-ha-1f5sxjfagjcca.jq0isi.0001.euw1.cache.amazonaws.com")
  System.setProperty("spring.data.redis.port", System.getenv("spring_redis_port"))

  System.setProperty("server.servlet.context-path", "/viestinvalituspalvelu")

  System.setProperty("logging.level.root", "TRACE")

  val handler: SpringBootLambdaContainerHandler[AwsProxyRequest, AwsProxyResponse] = SpringBootLambdaContainerHandler.getAwsProxyHandler(classOf[App])
  val LOG = LoggerFactory.getLogger(classOf[LambdaHandler]);
}

class LambdaHandler extends RequestHandler[AwsProxyRequest, AwsProxyResponse], Resource {
  Core.getGlobalContext.register(this)

  private[viestinvalitus] val LOG = LoggerFactory.getLogger(classOf[RequestStreamHandler])

  override def handleRequest(request: AwsProxyRequest, context: Context): AwsProxyResponse = {
    // convert query string
    val params = new MultiValuedTreeMap[String, String]()
    request.getQueryStringParameters.entrySet().forEach(p => params.putSingle(p.getKey, p.getValue))
    request.setMultiValueQueryStringParameters(params)

    // convert headers
    request.getHeaders.entrySet().forEach(h => request.getMultiValueHeaders.add(h.getKey, h.getValue))

    val response = LambdaHandler.handler.proxy(request, context)

    LOG.debug("Headers: " + request.getQueryString)
    if(request.getHeaders!=null) {
      request.getHeaders.entrySet().forEach(h => LOG.debug("Single value header: " + h.getKey + ": " + h.getValue))
    }
    request.getMultiValueHeaders.entrySet().forEach(h => LOG.debug("Multivalue header: " + h.getKey + ": " + h.getValue.stream().collect(Collectors.joining(", "))))

    LOG.debug("Query string: " + request.getQueryString)
    request.getMultiValueQueryStringParameters.entrySet().forEach(p => LOG.debug("MultiParam: " + p.getKey + ": " + p.getValue.stream().collect(Collectors.joining(", "))))
    request.getQueryStringParameters.entrySet().forEach(p => LOG.debug("SingleParam: " + p.getKey + ": " + p.getValue))

    val singleHeaders = new util.HashMap[String, String]()
    response.getMultiValueHeaders.entrySet().forEach(h => {
      if(h.getValue.size()==1) {
        if(h.getKey.equals("Location")) {
          singleHeaders.put(h.getKey, h.getValue.get(0).replaceAll("http://null\\.execute-api\\.eu-west-1\\.amazonaws\\.com", "https://virkailija.hahtuvaopintopolku.fi"))
        } else {
          singleHeaders.put(h.getKey, h.getValue.get(0))
        }
      }
    })
    response.setHeaders(singleHeaders)

    response
  }

  @throws[Exception]
  def beforeCheckpoint(context: org.crac.Context[_ <: Resource]): Unit = {
    System.out.println("Before checkpoint")
    LambdaHandler.handler.toString

/*
    val req: HttpApiV2ProxyRequest = new HttpApiV2ProxyRequest()
    req.setBody("{\"heading\":\"test1\",\"content\":\"test1\"}")
    req.setRawPath("/v2/resource/viesti")
    req.setRequestContext(new HttpApiV2ProxyRequestContext())
    req.getRequestContext.setHttp(new HttpApiV2HttpContext())
    req.getRequestContext.getHttp.setPath("/v2/resource/viesti")
    req.getRequestContext.getHttp.setMethod("PUT")
    val ctx: Context = new Ctx()
    Set(0 to 200).foreach(n => LambdaHandler.handler.proxy(req, ctx))
*/
  }

  @throws[Exception]
  def afterRestore(context: org.crac.Context[_ <: Resource]): Unit = {
    System.out.println("After restore")
  }
}
