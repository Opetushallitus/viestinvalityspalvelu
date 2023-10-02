package fi.oph.viestinvalitus.vastaanotto

import com.amazonaws.serverless.exceptions.ContainerInitializationException
import com.amazonaws.serverless.proxy.model.{AwsProxyResponse, HttpApiV2HttpContext, HttpApiV2ProxyRequest, HttpApiV2ProxyRequestContext}
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler
import com.amazonaws.services.lambda.runtime.{ClientContext, CognitoIdentity, Context, LambdaLogger, RequestHandler, RequestStreamHandler}
import fi.oph.viestinvalitus.vastaanotto.App.Ctx
import fi.oph.viestinvalitus.vastaanotto.{App, LambdaHandler}
import org.crac.{Core, Resource}
import org.slf4j.{Logger, LoggerFactory}

object LambdaHandler {
  val handler: SpringBootLambdaContainerHandler[HttpApiV2ProxyRequest, AwsProxyResponse] = SpringBootLambdaContainerHandler.getHttpApiV2ProxyHandler(classOf[App])
}

class LambdaHandler extends RequestHandler[HttpApiV2ProxyRequest, AwsProxyResponse], Resource {
  Core.getGlobalContext.register(this)

  private[viestinvalitus] val LOG = LoggerFactory.getLogger(classOf[RequestStreamHandler])

  override def handleRequest(request: HttpApiV2ProxyRequest, context: Context): AwsProxyResponse = {
    LambdaHandler.handler.proxy(request, context)
  }

  @throws[Exception]
  def beforeCheckpoint(context: org.crac.Context[_ <: Resource]): Unit = {
    System.setProperty("cas-service.service", "https://z5jr24i2s4zzgzp3blv4uvgdje0ebbgg.lambda-url.eu-west-1.on.aws/")
    System.setProperty("cas-service.sendRenew", "false")
    System.setProperty("cas-service.key", "viestinvalituspalvelu")
    System.setProperty("web.url.cas", "https://virkailija.hahtuvaopintopolku.fi/cas")
    System.setProperty("kayttooikeus-service.userDetails.byUsername", "https://virkailija.hahtuvaopintopolku.fi/kayttooikeus-service/userDetails/$1")
    System.setProperty("host.virkailija", "virkailija.hahtuvaopintopolku.fi")

    System.out.println("Before checkpoint")
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
