package fi.oph.viestinvalitys

import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import fi.oph.viestinvalitys.ViestinvalitysClient.*
import fi.oph.viestinvalitys.vastaanotto.model.{Lahetys, Liite, LuoLahetysSuccessResponse, LuoLiiteSuccessResponse, LuoViestiSuccessResponse, VastaanottajaResponse, Viesti}
import fi.oph.viestinvalitys.vastaanotto.resource.{LahetysAPIConstants, LuoLahetysFailureResponseImpl, LuoLahetysSuccessResponseImpl, LuoLiiteFailureResponseImpl, LuoLiiteSuccessResponseImpl, LuoViestiFailureResponseImpl, LuoViestiSuccessResponseImpl, VastaanottajatFailureResponse, VastaanottajatSuccessResponse}
import org.asynchttpclient.request.body.multipart.ByteArrayPart
import org.asynchttpclient.{AsyncHttpClient, Dsl, Request, RequestBuilder, Response}

import java.util.concurrent.atomic.AtomicReference
import java.util.{Optional, UUID}
import scala.jdk.CollectionConverters.*
import java.util

class ViestinvalitysClientImpl(
  httpClient: AsyncHttpClient,
  endpoint: String,
  callerId: String,
  sessionProvider: SessionProvider,
) extends ViestinvalitysClient {

  private val objectMapper = {
    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)
    mapper.registerModule(new Jdk8Module())
    mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
    mapper.configure(SerializationFeature.INDENT_OUTPUT, true)
    mapper
  }

  override def luoLahetys(lahetys: Lahetys): LuoLahetysSuccessResponse =
    val response = executeAndRetryWithCleanSessionOnStatusCodes(
      sessionId => getJsonPostRequest(LahetysAPIConstants.LAHETYKSET_PATH, lahetys, sessionId),
      Set(401),
    )
    response.getStatusCode match
      case 200 => objectMapper.readValue(response.getResponseBody, classOf[LuoLahetysSuccessResponseImpl])
      case 403 => throw new ViestinvalitysClientException(Set.empty.asJava, 403)
      case _ =>
        val failureResponse = objectMapper.readValue(response.getResponseBody, classOf[LuoLahetysFailureResponseImpl])
        throw new ViestinvalitysClientException(failureResponse.validointiVirheet.asScala.toSet.asJava, response.getStatusCode)

  override def getVastaanottajat(lahetysTunniste: UUID, enintaan: Optional[java.lang.Integer]): util.Iterator[util.List[VastaanottajaResponse]] =
    new util.Iterator[util.List[VastaanottajaResponse]]:

      def getNextVastaanottajat(seuraavatUrl: Optional[String], enintaan: Optional[java.lang.Integer]): VastaanottajatSuccessResponse =
        val url =
          if seuraavatUrl.isPresent then
            seuraavatUrl.get
          else
            endpoint + LahetysAPIConstants.GET_VASTAANOTTAJAT_PATH
              .replace(LahetysAPIConstants.LAHETYSTUNNISTE_PARAM_PLACEHOLDER, lahetysTunniste.toString) +
              enintaan.map(v => s"?${LahetysAPIConstants.ENINTAAN_PARAM_NAME}=$v").orElse("")

        val response = executeAndRetryWithCleanSessionOnStatusCodes(
          sessionId => getJsonGetRequest(url, sessionId),
          Set(401),
        )
        response.getStatusCode match
          case 200 => objectMapper.readValue(response.getResponseBody, classOf[VastaanottajatSuccessResponse])
          case 403 => throw new ViestinvalitysClientException(Set.empty.asJava, 403)
          case _ =>
            val failureResponse = objectMapper.readValue(response.getResponseBody, classOf[VastaanottajatFailureResponse])
            throw new ViestinvalitysClientException(failureResponse.virheet.asScala.toSet.asJava, response.getStatusCode)

      private var end = false
      private var vastaanottajatResponse = this.getNextVastaanottajat(Optional.empty, enintaan)

      override def hasNext: Boolean =
        this.vastaanottajatResponse.seuraavat.isPresent

      override def next(): util.List[VastaanottajaResponse] =
        if end then
          throw new NoSuchElementException

        val vastaanottajat = this.vastaanottajatResponse.vastaanottajat
        if this.vastaanottajatResponse.seuraavat.isEmpty then
          end = true
        else
          this.vastaanottajatResponse = getNextVastaanottajat(this.vastaanottajatResponse.seuraavat, Optional.empty)

        vastaanottajat

  override def luoLiite(liite: Liite): LuoLiiteSuccessResponse =
    val response = executeAndRetryWithCleanSessionOnStatusCodes(
      sessionId => getMultipartPostRequest(LahetysAPIConstants.LIITTEET_PATH, liite, sessionId),
      Set(401),
    )
    response.getStatusCode match
      case 200 => objectMapper.readValue(response.getResponseBody, classOf[LuoLiiteSuccessResponseImpl])
      case 403 => throw new ViestinvalitysClientException(Set.empty.asJava, 403)
      case _ =>
        val failureResponse = objectMapper.readValue(response.getResponseBody, classOf[LuoLiiteFailureResponseImpl])
        throw new ViestinvalitysClientException(failureResponse.virheet.asScala.toSet.asJava, response.getStatusCode)

  override def luoViesti(viesti: Viesti): LuoViestiSuccessResponse =
    val response = executeAndRetryWithCleanSessionOnStatusCodes(
      sessionId => getJsonPostRequest(LahetysAPIConstants.VIESTIT_PATH, viesti, sessionId),
      Set(401),
    )
    response.getStatusCode match
      case 200 => objectMapper.readValue(response.getResponseBody, classOf[LuoViestiSuccessResponseImpl])
      case 403 => throw new ViestinvalitysClientException(Set.empty.asJava, 403)
      case _ =>
        val failureResponse = objectMapper.readValue(response.getResponseBody, classOf[LuoViestiFailureResponseImpl])
        throw new ViestinvalitysClientException(failureResponse.validointiVirheet.asScala.toSet.asJava, response.getStatusCode)

  private def executeAndRetryWithCleanSessionOnStatusCodes(
    requestBuilder: String => Request,
    statusCodesToRetry: Set[Int],
  ): Response =
    var response = execute(requestBuilder(sessionProvider.getSessionId()))
    if statusCodesToRetry.contains(response.getStatusCode) then
      sessionProvider.refreshSessionId().foreach(newSessionId =>
        response = execute(requestBuilder(newSessionId)),
      )
    response

  private def execute(request: Request): Response =
    httpClient.executeRequest(request).get()

  private def commonRequestBuilder(url: String, sessionId: String): RequestBuilder =
    new RequestBuilder()
      .setUrl(url)
      .setRequestTimeout(10000)
      .addHeader("Caller-Id", callerId)
      .addHeader("Accept", "application/json")
      .addHeader("CSRF", callerId)
      .addCookie(new io.netty.handler.codec.http.cookie.DefaultCookie("CSRF", callerId))
      .addCookie(new io.netty.handler.codec.http.cookie.DefaultCookie("JSESSIONID", sessionId))

  private def getJsonGetRequest(url: String, sessionId: String): Request =
    commonRequestBuilder(url, sessionId)
      .setMethod("GET")
      .build()

  private def getJsonPostRequest(path: String, body: Any, sessionId: String): Request =
    commonRequestBuilder(endpoint + path, sessionId)
      .setMethod("POST")
      .setBody(objectMapper.writeValueAsString(body))
      .addHeader("Content-Type", "application/json")
      .build()

  private def getMultipartPostRequest(path: String, liite: Liite, sessionId: String): Request =
    commonRequestBuilder(endpoint + path, sessionId)
      .setMethod("POST")
      .addBodyPart(new ByteArrayPart("liite", liite.getBytes(), liite.getSisaltoTyyppi, null, liite.getTiedostoNimi))
      .addHeader("Content-Type", "multipart/form-data")
      .build()
}

case class ViestinvalitysClientConfig(
  username: String,
  password: String,
  callerId: String,
  endpoint: String,
  casEndpoint: String,
  sessionId: Option[String],
)

private trait SessionProvider {
  def getSessionId(): String
  def refreshSessionId(): Option[String]
}

private class StaticSessionProvider(sessionId: String) extends SessionProvider {
  override def getSessionId(): String = sessionId
  override def refreshSessionId(): Option[String] = None
}

private class CasSessionProvider(config: ViestinvalitysClientConfig, httpClient: AsyncHttpClient) extends SessionProvider {
  private val cachedSessionId = new AtomicReference[String]()

  override def getSessionId(): String =
    Option(cachedSessionId.get()).getOrElse(refreshAndGet())

  override def refreshSessionId(): Option[String] =
    Some(refreshAndGet())

  private def refreshAndGet(): String =
    val session = login()
    cachedSessionId.set(session)
    session

  private def login(): String =
    val tgtUrl = createTicketGrantingTicket()
    val serviceTicket = createServiceTicket(tgtUrl)
    createSession(serviceTicket)

  private def createTicketGrantingTicket(): String =
    requireNonEmpty(config.username, "username")
    requireNonEmpty(config.password, "password")
    requireNonEmpty(config.casEndpoint, "casEndpoint")
    requireNonEmpty(config.callerId, "callerId")
    requireNonEmpty(config.endpoint, "endpoint")

    val request = new RequestBuilder()
      .setUrl(s"${stripTrailingSlash(config.casEndpoint)}/v1/tickets")
      .setMethod("POST")
      .setRequestTimeout(10000)
      .addHeader("Caller-Id", config.callerId)
      .addHeader("CSRF", config.callerId)
      .addCookie(new io.netty.handler.codec.http.cookie.DefaultCookie("CSRF", config.callerId))
      .addFormParam("username", config.username)
      .addFormParam("password", config.password)
      .build()

    val response = httpClient.executeRequest(request).get()
    val location = response.getHeader("Location")
    if (location == null || location.isBlank)
      throw new IllegalStateException(s"CAS ticket granting ticket response had no Location header (status ${response.getStatusCode})")
    location

  private def createServiceTicket(ticketGrantingTicketUrl: String): String =
    val serviceUrl = config.endpoint + LahetysAPIConstants.CAS_TICKET_VALIDATION_PATH
    val request = new RequestBuilder()
      .setUrl(ticketGrantingTicketUrl)
      .setMethod("POST")
      .setRequestTimeout(10000)
      .addHeader("Caller-Id", config.callerId)
      .addHeader("CSRF", config.callerId)
      .addCookie(new io.netty.handler.codec.http.cookie.DefaultCookie("CSRF", config.callerId))
      .addFormParam("service", serviceUrl)
      .build()

    val response = httpClient.executeRequest(request).get()
    val serviceTicket = Option(response.getResponseBody).getOrElse("").trim
    if (serviceTicket.isEmpty)
      throw new IllegalStateException(s"CAS service ticket response was empty (status ${response.getStatusCode})")
    serviceTicket

  private def createSession(serviceTicket: String): String =
    val serviceValidationUrl = s"${config.endpoint}${LahetysAPIConstants.CAS_TICKET_VALIDATION_PATH}?ticket=$serviceTicket"
    val request = new RequestBuilder()
      .setUrl(serviceValidationUrl)
      .setMethod("GET")
      .setRequestTimeout(10000)
      .addHeader("Caller-Id", config.callerId)
      .addHeader("CSRF", config.callerId)
      .addCookie(new io.netty.handler.codec.http.cookie.DefaultCookie("CSRF", config.callerId))
      .build()

    val response = httpClient.executeRequest(request).get()
    response.getCookies.asScala
      .find(_.name() == "JSESSIONID")
      .map(_.value())
      .getOrElse(throw new IllegalStateException(s"CAS ticket validation response had no JSESSIONID cookie (status ${response.getStatusCode})"))

  private def stripTrailingSlash(url: String): String =
    if (url.endsWith("/")) url.dropRight(1) else url

  private def requireNonEmpty(value: String, fieldName: String): Unit =
    if (value == null || value.isBlank)
      throw new IllegalStateException(s"Missing required configuration field: $fieldName")
}

class ViestinvalitysClientBuilderImpl(config: ViestinvalitysClientConfig)
  extends AuthenticationBuilder,
    PasswordBuilder,
    CallerIdBuilder,
    EndpointBuilder,
    CasEndpointBuilder,
    ViestinValitysClientBuilder {

  def this() =
    this(ViestinvalitysClientConfig(null, null, null, null, null, Option.empty))

  override def withEndpoint(endpoint: String): AuthenticationBuilder =
    ViestinvalitysClientBuilderImpl(config.copy(endpoint = endpoint))

  override def withSessionId(sessionId: String): ViestinvalitysClientBuilderImpl =
    ViestinvalitysClientBuilderImpl(config.copy(sessionId = Option.apply(sessionId)))

  override def withUsername(username: String): ViestinvalitysClientBuilderImpl =
    ViestinvalitysClientBuilderImpl(config.copy(username = username))

  override def withPassword(password: String): ViestinvalitysClientBuilderImpl =
    ViestinvalitysClientBuilderImpl(config.copy(password = password))

  override def withCallerId(callerId: String): ViestinvalitysClientBuilderImpl =
    ViestinvalitysClientBuilderImpl(config.copy(callerId = callerId))

  override def withCasEndpoint(casEndpoint: String): ViestinvalitysClientBuilderImpl =
    ViestinvalitysClientBuilderImpl(config.copy(casEndpoint = casEndpoint))

  override def build(): ViestinvalitysClient =
    buildWithHttpClient(Dsl.asyncHttpClient())

  override def buildWithHttpClient(httpClient: AsyncHttpClient): ViestinvalitysClient =
    val sessionProvider =
      if config.sessionId.isDefined then
        new StaticSessionProvider(config.sessionId.get)
      else
        new CasSessionProvider(config, httpClient)

    ViestinvalitysClientImpl(httpClient, config.endpoint, config.callerId, sessionProvider)
}
