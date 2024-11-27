package fi.oph.viestinvalitys.raportointi.integration
import fi.oph.viestinvalitys.raportointi.App
import fi.oph.viestinvalitys.util.ConfigurationUtil
import fi.vm.sade.javautils.nio.cas.{CasClient, CasClientBuilder}
import org.asynchttpclient.RequestBuilder
import org.slf4j.LoggerFactory

import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.jdk.javaapi.FutureConverters.asScala
import scala.concurrent.ExecutionContext.Implicits.global

trait ONRService {
  def haeAsiointikieli(personOid: String): Either[Throwable, String]
}
object ONRService {
  def apply(): ONRService = new RealONRService()
}
class RealONRService() extends ONRService {

  val LOG = LoggerFactory.getLogger(classOf[RealONRService])

  val opintopolkuDomain = ConfigurationUtil.opintopolkuDomain

  val casPassword = ConfigurationUtil.casPassword

  private val client: CasClient = CasClientBuilder.build(ScalaCasConfig(
    "viestinvalityspalvelu",
    casPassword,
    s"https://virkailija.$opintopolkuDomain/cas",
    s"https://virkailija.$opintopolkuDomain/oppijanumerorekisteri-service",
    App.CALLER_ID,
    App.CALLER_ID,
    "/j_spring_cas_security_check",
    "JSESSIONID"))

  def haeAsiointikieli(personOid: String): Either[Throwable, String] =
    LOG.info("Haetaan tiedot oppijanumerorekisteristä")
    val url = s"https://virkailija.$opintopolkuDomain/oppijanumerorekisteri-service/henkilo/$personOid/asiointiKieli"
    fetch(url) match
      case Left(e) => Left(e)
      case Right(o) => Right(o)

  private def fetch(url: String): Either[Throwable, String] =
    val req = new RequestBuilder()
      .setMethod("GET")
      .setUrl(url)
      .build()
    try
      val result = asScala(client.execute(req)).map {
        case r if r.getStatusCode() == 200  =>
          Right(r.getResponseBody())
        case r =>
          LOG.error(s"Kutsu oppijanumerorekisteriin epäonnistui: ${r.getStatusCode()} ${r.getStatusText()} ${r.getResponseBody()}")
          Left(new RuntimeException("Failed to fetch asiointikieli: " + r.getResponseBody()))
      }
      Await.result(result, Duration(10, TimeUnit.SECONDS))
    catch
      case e: Throwable => Left(e)

}
