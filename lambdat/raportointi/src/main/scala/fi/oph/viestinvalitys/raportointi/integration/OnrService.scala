package fi.oph.viestinvalitys.raportointi.integration
import fi.vm.sade.javautils.nio.cas.CasConfig
import fi.oph.viestinvalitys.raportointi.App
import fi.oph.viestinvalitys.util.ConfigurationUtil
import fi.vm.sade.javautils.nio.cas.{CasClient, CasClientBuilder}
import org.asynchttpclient.RequestBuilder
import org.slf4j.LoggerFactory
import upickle.default.*

import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.jdk.javaapi.FutureConverters.asScala
import scala.concurrent.ExecutionContext.Implicits.global

trait ONRService {
  def haeOmatTiedot(personOid: String): Either[RuntimeException, OmatTiedot]
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

  def haeOmatTiedot(personOid: String): Either[RuntimeException, OmatTiedot] =
    LOG.info("Haetaan tiedot oppijanumerorekisteristä")
    val url = s"https://virkailija.$opintopolkuDomain/oppijanumerorekisteri-service/henkilo/$personOid/omattiedot"
    fetch(url) match
      case Left(e) => Left(new RuntimeException(s"Failed to get omat tiedot for $personOid:  ${e.getMessage}"))
      case Right(o) => Right(o)

  private def fetch(url: String): Either[Throwable, OmatTiedot] =
    val req = new RequestBuilder()
      .setMethod("GET")
      .setUrl(url)
      .build()
    try
      val result = asScala(client.execute(req)).map {
        case r if r.getStatusCode() == 200  =>
          Right(read[OmatTiedot](r.getResponseBody()))
        case r =>
          LOG.error(s"Kutsu oppijanumerorekisteriin epäonnistui: ${r.getStatusCode()} ${r.getStatusText()} ${r.getResponseBody()}")
          Left(new RuntimeException("Failed to fetch omattiedot: " + r.getResponseBody()))
      }
      Await.result(result, Duration(10, TimeUnit.SECONDS))
    catch
      case e: Throwable => Left(e)

}
