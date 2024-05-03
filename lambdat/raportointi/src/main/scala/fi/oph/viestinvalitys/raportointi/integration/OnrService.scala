package fi.oph.viestinvalitys.raportointi.integration
import fi.vm.sade.javautils.nio.cas.CasConfig
import fi.oph.viestinvalitys.raportointi.App
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
  def haeAsiointikieli(personOid: String): Either[RuntimeException, String]
}
object ONRService {
  def apply(): ONRService = new RealONRService()
}
class RealONRService() extends ONRService {

  val LOG = LoggerFactory.getLogger(classOf[RealONRService])

  /*
  CasClient client = CasClientBuilder.build(
    new CasConfig.CasConfigBuilder(
      username,
      password,
      casServerUrl,
      serviceBaseUrl,
      csrfToken,
      callerIdentifier,
      serviceAuthenticationUrlSuffix)
    .setJsessionName(sessionCookieName)
    .build())
   */

  private val client: CasClient = CasClientBuilder.build(
    new CasConfig.CasConfigBuilder(
    "viestinvalityspalvelu",
    "Testitunnus12345678!",
    "https://virkailija.hahtuvaopintopolku.fi/cas",
    "https://virkailija.hahtuvaopintopolku.fi/oppijanumerorekisteri-service",
    App.CALLER_ID,
      App.CALLER_ID,
    "/auth/cas")
    .setJsessionName("JSESSIONID")
    .build())

  def haeAsiointikieli(personOid: String): Either[RuntimeException, String] =
    LOG.info("Haetaan tiedot oppijanumerorekisteristä")
    val url = s"https://virkailija.hahtuvaopintopolku.fi/oppijanumerorekisteri-service/henkilo/$personOid/omattiedot"
    fetch(url) match
      case Left(e) => Left(new RuntimeException(s"Failed to get omat tiedot for $personOid"))
      case Right(o) => Right(o.asiointikieli)

  private def fetch(url: String): Either[Throwable, OmatTiedot] =
    LOG.warn(s"Calling oppijanumerorekisteri uri: $url")
    val req = new RequestBuilder()
      .setMethod("GET")
      .setUrl(url)
      .build()
    val result = asScala(client.execute(req)).map {
      case r if r.getStatusCode() == 200  =>
        Right(read[OmatTiedot](r.getResponseBody()))
      case r =>
        LOG.error(s"Kutsu oppijanumerorekisteriin epäonnistui: ${r.getStatusCode()} ${r.getStatusText()} ${r.getResponseBody()}")
        Left(new RuntimeException("Failed to fetch omattiedot: " + r.getResponseBody()))
    }
    try
      Await.result(result, Duration(2, TimeUnit.MINUTES))
    catch
      case e: Throwable => Left(e)

}
