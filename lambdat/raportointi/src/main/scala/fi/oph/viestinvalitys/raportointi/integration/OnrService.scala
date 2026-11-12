package fi.oph.viestinvalitys.raportointi.integration

import fi.oph.viestinvalitys.raportointi.App
import fi.oph.viestinvalitys.util.{ConfigurationUtil, SecretsManagerClient}
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.{HttpEntity, HttpHeaders, HttpMethod, MediaType}
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate

import java.net.HttpURLConnection
import java.util.concurrent.atomic.AtomicReference
import scala.jdk.CollectionConverters.*

trait ONRService {
  def haeAsiointikieli(personOid: String): Either[Throwable, String]
}
object ONRService {
  def apply(): ONRService = new RealONRService()
}
class RealONRService() extends ONRService {

  val LOG = LoggerFactory.getLogger(classOf[RealONRService])
  private val callerId = App.CALLER_ID

  val opintopolkuDomain = ConfigurationUtil.opintopolkuDomain
  private val virkailijaUrl = s"https://virkailija.$opintopolkuDomain"

  lazy val casPassword = ConfigurationUtil.casPassword

  private val restTemplate = {
    val factory = new SimpleClientHttpRequestFactory() {
      override protected def prepareConnection(connection: HttpURLConnection, httpMethod: String): Unit = {
        super.prepareConnection(connection, httpMethod)
        connection.setInstanceFollowRedirects(false)
      }
    }
    factory.setConnectTimeout(5000)
    factory.setReadTimeout(10000)
    new RestTemplate(factory)
  }
  private val sessionCookie = new AtomicReference[String]()

  def haeAsiointikieli(personOid: String): Either[Throwable, String] =
    LOG.debug(s"Haetaan tiedot oppijanumerorekisteristä oidille $personOid")
    try
      val session = Option(sessionCookie.get()).getOrElse(login())
      try
        Right(fetchAsiointikieli(personOid, session))
      catch
        case _: SessionExpiredException =>
          Right(fetchAsiointikieli(personOid, login()))
    catch
      case e: Throwable =>
        LOG.warn(s"Asiointikielen haku epäonnistui oidille $personOid: ${e.getMessage}")
        Left(e)

  private def fetchAsiointikieli(personOid: String, session: String): String =
    val url = s"$virkailijaUrl/oppijanumerorekisteri-service/henkilo/$personOid/asiointiKieli"
    val headers = new HttpHeaders()
    headers.set("Caller-Id", callerId)
    headers.set(HttpHeaders.COOKIE, s"JSESSIONID=$session")
    val response = restTemplate.exchange(
      url,
      HttpMethod.GET,
      new HttpEntity[Void](headers),
      new ParameterizedTypeReference[java.util.Map[String, Object]]() {},
    )
    if (response.getStatusCode.is3xxRedirection)
      throw SessionExpiredException()
    val body = response.getBody
    body match
      case null => throw RuntimeException("Asiointikielen haku palautti tyhjän vastauksen")
      case _ =>
        body.get("kieliKoodi") match
          case kieliKoodi: String => kieliKoodi
          case _ => throw RuntimeException(s"Asiointikielen haku palautti virheellisen vastauksen: $body")

  private def login(): String =
    val tgtUrl = createTicketGrantingTicket()
    val serviceTicket = createServiceTicket(tgtUrl)
    val session = createOnrSession(serviceTicket)
    sessionCookie.set(session)
    session

  private def createTicketGrantingTicket(): String =
    val (username, password) = SecretsManagerClient.getCasSecret match
      case Some(secret) => (secret.username, secret.password)
      case _ => ("viestinvalityspalvelu", casPassword)

    val form = new LinkedMultiValueMap[String, String]()
    form.add("username", username)
    form.add("password", password)
    val headers = new HttpHeaders()
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED)
    headers.set("Caller-Id", callerId)
    val response = restTemplate.postForEntity(
      s"$virkailijaUrl/cas/v1/tickets",
      new HttpEntity(form, headers),
      classOf[String],
    )
    val location = response.getHeaders.getFirst(HttpHeaders.LOCATION)
    if (location == null)
      throw IllegalStateException("CAS ticket granting ticket response had no location header")
    location

  private def createServiceTicket(tgtUrl: String): String =
    val form = new LinkedMultiValueMap[String, String]()
    form.add("service", s"$virkailijaUrl/oppijanumerorekisteri-service/j_spring_cas_security_check")
    val headers = new HttpHeaders()
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED)
    headers.set("Caller-Id", callerId)
    val serviceTicket = restTemplate.postForObject(
      tgtUrl,
      new HttpEntity(form, headers),
      classOf[String],
    )
    if (serviceTicket == null || serviceTicket.isBlank)
      throw IllegalStateException("CAS service ticket response was empty")
    serviceTicket

  private def createOnrSession(serviceTicket: String): String =
    val headers = new HttpHeaders()
    headers.set("Caller-Id", callerId)
    val response = restTemplate.exchange(
      s"$virkailijaUrl/oppijanumerorekisteri-service/j_spring_cas_security_check?ticket=$serviceTicket",
      HttpMethod.GET,
      new HttpEntity[Void](headers),
      classOf[String],
    )
    response.getHeaders
      .getOrEmpty(HttpHeaders.SET_COOKIE)
      .asScala
      .find(_.startsWith("JSESSIONID="))
      .map(cookie =>
        val startIndex = "JSESSIONID=".length
        val endIndex = cookie.indexOf(';')
        if (endIndex > startIndex) cookie.substring(startIndex, endIndex) else cookie.substring(startIndex),
      )
      .getOrElse(throw IllegalStateException("CAS security check response had no JSESSIONID cookie"))

  private case class SessionExpiredException() extends RuntimeException

}
