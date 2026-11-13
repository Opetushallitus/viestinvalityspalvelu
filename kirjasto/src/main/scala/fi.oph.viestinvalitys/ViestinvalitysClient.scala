package fi.oph.viestinvalitys

import tools.jackson.databind.{DeserializationFeature, ObjectMapper, SerializationFeature}
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.scala.DefaultScalaModule
import fi.oph.viestinvalitys.ViestinvalitysClient.*
import fi.oph.viestinvalitys.vastaanotto.model.{Lahetys, Liite, LuoLahetysSuccessResponse, LuoLiiteSuccessResponse, LuoViestiSuccessResponse, VastaanottajaResponse, Viesti}
import fi.oph.viestinvalitys.vastaanotto.resource.{LahetysAPIConstants, LuoLahetysFailureResponseImpl, LuoLahetysSuccessResponseImpl, LuoLiiteFailureResponseImpl, LuoLiiteSuccessResponseImpl, LuoViestiFailureResponseImpl, LuoViestiSuccessResponseImpl, VastaanottajatFailureResponse, VastaanottajatSuccessResponse}
import io.netty.handler.codec.http.cookie.DefaultCookie
import org.asynchttpclient.request.body.multipart.ByteArrayPart
import org.asynchttpclient.{AsyncHttpClient, Dsl, Request, RequestBuilder, Response}
import org.slf4j.LoggerFactory

import java.util
import java.util.concurrent.{CompletableFuture, CompletionException, ExecutionException, TimeUnit}
import java.util.{Optional, UUID}
import scala.jdk.CollectionConverters.*

class ViestinvalitysClientImpl(
  httpClient: AsyncHttpClient,
  endpoint: String,
  callerId: String,
  sessionProvider: SessionProvider,
  requestTimeoutMs: Long,
  numberOfRetries: Int = 1,
) extends ViestinvalitysClient {

  private val log = LoggerFactory.getLogger(classOf[ViestinvalitysClientImpl])

  private val objectMapper: ObjectMapper = JsonMapper.builder()
    .addModule(DefaultScalaModule)
    .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
    .enable(SerializationFeature.INDENT_OUTPUT)
    .build()

  private case class Either[T](value: T, throwable: Throwable)

  override def luoLahetys(lahetys: Lahetys): LuoLahetysSuccessResponse =
    val response = executeAndRetryWithCleanSessionOnStatusCodes(
      getJsonPostRequest(LahetysAPIConstants.LAHETYKSET_PATH, lahetys),
      Set(401),
    ).get()
    response.getStatusCode match
      case 200 => objectMapper.readValue(response.getResponseBody, classOf[LuoLahetysSuccessResponseImpl])
      case 403 => throw new ViestinvalitysClientException(Set.empty.asJava, 403)
      case _ =>
        val failureResponse = objectMapper.readValue(response.getResponseBody, classOf[LuoLahetysFailureResponseImpl])
        throw new ViestinvalitysClientException(failureResponse.validointiVirheet.asScala.toSet.asJava, response.getStatusCode)

  override def getVastaanottajat(lahetysTunniste: UUID, enintaan: Optional[java.lang.Integer]): util.Iterator[util.List[VastaanottajaResponse]] =
    new util.Iterator[util.List[VastaanottajaResponse]]:
      def getNextVastaanottajat(seuraavatUrl: Optional[String], enintaan: Optional[java.lang.Integer]): VastaanottajatSuccessResponse =
        val request =
          if seuraavatUrl.isPresent then
            getJsonGetRequest(seuraavatUrl.get)
          else
            val enintaanQuery = enintaan.map(v => s"?${LahetysAPIConstants.ENINTAAN_PARAM_NAME}=$v").orElse("")
            getJsonGetRequest(
              endpoint + LahetysAPIConstants.GET_VASTAANOTTAJAT_PATH
                .replace(LahetysAPIConstants.LAHETYSTUNNISTE_PARAM_PLACEHOLDER, lahetysTunniste.toString) + enintaanQuery,
            )

        val response = executeAndRetryWithCleanSessionOnStatusCodes(request, Set(401)).get()
        response.getStatusCode match
          case 200 => objectMapper.readValue(response.getResponseBody, classOf[VastaanottajatSuccessResponse])
          case 403 => throw new ViestinvalitysClientException(Set.empty.asJava, 403)
          case _ =>
            val failureResponse = objectMapper.readValue(response.getResponseBody, classOf[VastaanottajatFailureResponse])
            throw new ViestinvalitysClientException(failureResponse.virheet.asScala.toSet.asJava, response.getStatusCode)

      private var end = false
      private var vastaanottajatResponse = this.getNextVastaanottajat(Optional.empty, enintaan)

      override def hasNext: Boolean = this.vastaanottajatResponse.seuraavat.isPresent

      override def next(): util.List[VastaanottajaResponse] =
        if end then throw new NoSuchElementException
        val vastaanottajat = this.vastaanottajatResponse.vastaanottajat
        if this.vastaanottajatResponse.seuraavat.isEmpty then
          end = true
        else
          this.vastaanottajatResponse = getNextVastaanottajat(this.vastaanottajatResponse.seuraavat, Optional.empty)
        vastaanottajat

  override def luoLiite(liite: Liite): LuoLiiteSuccessResponse =
    val response = executeAndRetryWithCleanSessionOnStatusCodes(getMultipartPostRequest(LahetysAPIConstants.LIITTEET_PATH, liite), Set(401)).get()
    response.getStatusCode match
      case 200 => objectMapper.readValue(response.getResponseBody, classOf[LuoLiiteSuccessResponseImpl])
      case 403 => throw new ViestinvalitysClientException(Set.empty.asJava, 403)
      case _ =>
        val failureResponse = objectMapper.readValue(response.getResponseBody, classOf[LuoLiiteFailureResponseImpl])
        throw new ViestinvalitysClientException(failureResponse.virheet.asScala.toSet.asJava, response.getStatusCode)

  override def luoViesti(viesti: Viesti): LuoViestiSuccessResponse =
    val response = executeAndRetryWithCleanSessionOnStatusCodes(getJsonPostRequest(LahetysAPIConstants.VIESTIT_PATH, viesti), Set(401)).get()
    response.getStatusCode match
      case 200 => objectMapper.readValue(response.getResponseBody, classOf[LuoViestiSuccessResponseImpl])
      case 403 => throw new ViestinvalitysClientException(Set.empty.asJava, 403)
      case _ =>
        val failureResponse = objectMapper.readValue(response.getResponseBody, classOf[LuoViestiFailureResponseImpl])
        throw new ViestinvalitysClientException(failureResponse.validointiVirheet.asScala.toSet.asJava, response.getStatusCode)

  private def executeAndRetryWithCleanSessionOnStatusCodes(request: Request, statusCodesToRetry: Set[Int]): CompletableFuture[Response] =
    executeWithRetries(request, numberOfRetries, statusCodesToRetry)

  private def executeWithRetries(request: Request, retriesLeft: Int, statusCodesToRetry: Set[Int]): CompletableFuture[Response] =
    val execution = executeWithSession(request, retrySessionFetch = true)
    if retriesLeft < 1 then
      execution
    else
      execution
        .handle((response: Response, throwable: Throwable) => Either(response, throwable))
        .thenCompose(entry =>
          if entry.throwable != null then
            log.warn(s"Retrying request ${request.getUrl} on exception", entry.throwable)
            sessionProvider.clearSessionStore()
            sessionProvider.clearTgtStore()
            executeWithRetries(request, retriesLeft - 1, statusCodesToRetry)
          else if statusCodesToRetry.contains(entry.value.getStatusCode) then
            log.warn(s"Retrying request ${request.getUrl} (response status code = ${entry.value.getStatusCode})")
            sessionProvider.clearSessionStore()
            sessionProvider.clearTgtStore()
            executeWithRetries(request, retriesLeft - 1, statusCodesToRetry)
          else
            CompletableFuture.completedFuture(entry.value),
        )

  private def executeWithSession(request: Request, retrySessionFetch: Boolean): CompletableFuture[Response] =
    sessionProvider.fetchSessionToken()
      .handle((session: String, throwable: Throwable) => Either(session, throwable))
      .thenCompose(sessionResult =>
        val cause = unwrap(sessionResult.throwable)
        if retrySessionFetch && (cause.isInstanceOf[ServiceTicketException] || cause.isInstanceOf[TicketGrantingTicketException]) then
          log.warn("Clearing stores and retrying executeWithSession", cause)
          sessionProvider.clearTgtStore()
          sessionProvider.clearSessionStore()
          executeWithSession(request, retrySessionFetch = false)
        else if cause != null then
          CompletableFuture.failedFuture(cause)
        else
          httpClient.getConfig.getCookieStore.clear()
          val requestWithSession = withCallerIdAndCsrfHeader(request.toBuilder)
            .addOrReplaceCookie(new DefaultCookie("JSESSIONID", sessionResult.value))
            .build()
          httpClient.executeRequest(requestWithSession).toCompletableFuture,
      )

  private def withCallerIdAndCsrfHeader(requestBuilder: RequestBuilder): RequestBuilder =
    requestBuilder
      .setHeader("Caller-Id", callerId)
      .setHeader("CSRF", callerId)
      .addOrReplaceCookie(new DefaultCookie("CSRF", callerId))

  private def getJsonGetRequest(url: String): Request =
    withCallerIdAndCsrfHeader(
      withRequestTimeout(new RequestBuilder()
        .setUrl(url)
        .setMethod("GET")
        .addHeader("Accept", "application/json")),
    ).build()

  private def getJsonPostRequest(path: String, body: Any): Request =
    withCallerIdAndCsrfHeader(
      withRequestTimeout(new RequestBuilder()
        .setUrl(endpoint + path)
        .setMethod("POST")
        .setBody(objectMapper.writeValueAsString(body))
        .addHeader("Content-Type", "application/json")
        .addHeader("Accept", "application/json")),
    ).build()

  private def getMultipartPostRequest(path: String, liite: Liite): Request =
    withCallerIdAndCsrfHeader(
      withRequestTimeout(new RequestBuilder()
        .setUrl(endpoint + path)
        .setMethod("POST")
        .addBodyPart(new ByteArrayPart("liite", liite.getBytes(), liite.getSisaltoTyyppi, null, liite.getTiedostoNimi))
        .addHeader("Content-Type", "multipart/form-data")
        .addHeader("Accept", "application/json")),
    ).build()

  private def withRequestTimeout(requestBuilder: RequestBuilder): RequestBuilder =
    requestBuilder.setRequestTimeout(requestTimeoutMs.toInt)

  private def unwrap(t: Throwable): Throwable =
    t match
      case null => null
      case ce: CompletionException if ce.getCause != null && ce.getCause != ce => unwrap(ce.getCause)
      case ee: ExecutionException if ee.getCause != null && ee.getCause != ee => unwrap(ee.getCause)
      case other => other
}

case class ViestinvalitysClientConfig(
  username: String,
  password: String,
  callerId: String,
  endpoint: String,
  casEndpoint: String,
  sessionId: Option[String],
  sessionTicketValidMs: Long,
  ticketGrantingTicketValidMs: Long,
  numberOfRetries: Int,
  requestTimeoutMs: Long,
)

private trait SessionProvider {
  def fetchSessionToken(): CompletableFuture[String]
  def clearSessionStore(): Unit
  def clearTgtStore(): Unit
}

private class StaticSessionProvider(sessionId: String) extends SessionProvider {
  override def fetchSessionToken(): CompletableFuture[String] = CompletableFuture.completedFuture(sessionId)
  override def clearSessionStore(): Unit = ()
  override def clearTgtStore(): Unit = ()
}

private case class CacheEntry(value: CompletableFuture[String], validUntilEpochMs: Long)

private class CasSessionProvider(config: ViestinvalitysClientConfig, httpClient: AsyncHttpClient) extends SessionProvider {
  private val sessionTtlMs = config.sessionTicketValidMs
  private val tgtTtlMs = config.ticketGrantingTicketValidMs

  private var cachedSession: Option[CacheEntry] = None
  private var cachedTgt: Option[CacheEntry] = None

  override def fetchSessionToken(): CompletableFuture[String] = synchronized {
    val now = System.currentTimeMillis()
    cachedSession.filter(_.validUntilEpochMs > now) match
      case Some(entry) => entry.value
      case None =>
        val inFlight = fetchSessionForReal()
        cachedSession = Some(CacheEntry(inFlight, now + sessionTtlMs))
        inFlight.whenComplete((_, throwable) =>
          if throwable != null then clearSessionIfCurrent(inFlight),
        )
        inFlight
  }

  override def clearSessionStore(): Unit = synchronized {
    cachedSession = None
    httpClient.getConfig.getCookieStore.clear()
  }

  override def clearTgtStore(): Unit = synchronized {
    cachedTgt = None
    httpClient.getConfig.getCookieStore.clear()
  }

  private def clearSessionIfCurrent(expected: CompletableFuture[String]): Unit = synchronized {
    cachedSession match
      case Some(entry) if entry.value eq expected => cachedSession = None
      case _ => ()
  }

  private def clearTgtIfCurrent(expected: CompletableFuture[String]): Unit = synchronized {
    cachedTgt match
      case Some(entry) if entry.value eq expected => cachedTgt = None
      case _ => ()
  }

  private def fetchSessionForReal(): CompletableFuture[String] =
    fetchTicketGrantingTicket()
      .thenCompose(fetchServiceTicketWithTgt)
      .thenCompose(createSession)

  private def fetchTicketGrantingTicket(): CompletableFuture[String] = synchronized {
    val now = System.currentTimeMillis()
    cachedTgt.filter(_.validUntilEpochMs > now) match
      case Some(entry) => entry.value
      case None =>
        val inFlight = fetchTicketGrantingTicketForReal()
        cachedTgt = Some(CacheEntry(inFlight, now + tgtTtlMs))
        inFlight.whenComplete((_, throwable) =>
          if throwable != null then clearTgtIfCurrent(inFlight),
        )
        inFlight
  }

  private def fetchTicketGrantingTicketForReal(): CompletableFuture[String] = {
    requireNonEmpty(config.username, "username")
    requireNonEmpty(config.password, "password")
    requireNonEmpty(config.casEndpoint, "casEndpoint")
    requireNonEmpty(config.callerId, "callerId")
    requireNonEmpty(config.endpoint, "endpoint")

    val request = withCallerIdAndCsrfHeader(withRequestTimeout(
      new RequestBuilder()
        .setUrl(s"${stripTrailingSlash(config.casEndpoint)}/v1/tickets")
        .setMethod("POST")
        .addFormParam("username", config.username)
        .addFormParam("password", config.password)),
      config.callerId,
    ).build()

    httpClient.getConfig.getCookieStore.clear()
    httpClient.executeRequest(request).toCompletableFuture
      .thenApply(response =>
        if response.getStatusCode == 201 then
          val location = response.getHeader("Location")
          if location == null || location.isBlank then
            throw new TicketGrantingTicketException(
              s"Could not parse TGT from CAS response. URL = ${request.getUrl}, location = ${response.getHeader("Location")}, body = ${response.getResponseBody}",
            )
          location.substring(location.lastIndexOf('/') + 1)
        else
          throw new TicketGrantingTicketException(
            s"Couldn't get TGT ticket from CAS! URL = ${request.getUrl}, status = ${response.getStatusCode}, body = ${response.getResponseBody}",
          ),
      )
  }

  private def fetchServiceTicketWithTgt(ticketGrantingTicket: String): CompletableFuture[String] = {
    val serviceUrl = s"${config.endpoint}${LahetysAPIConstants.CAS_TICKET_VALIDATION_PATH}"
    val request = withCallerIdAndCsrfHeader(withRequestTimeout(
      new RequestBuilder()
        .setUrl(s"${stripTrailingSlash(config.casEndpoint)}/v1/tickets/$ticketGrantingTicket")
        .setMethod("POST")
        .addFormParam("service", serviceUrl)),
      config.callerId,
    ).build()

    httpClient.getConfig.getCookieStore.clear()
    httpClient.executeRequest(request).toCompletableFuture
      .thenCompose(response =>
        if response.getStatusCode == 200 then
          val ticket = Option(response.getResponseBody).getOrElse("").trim
          if ticket.isEmpty then
            CompletableFuture.failedFuture(new ServiceTicketException("CAS service ticket response was empty"))
          else
            CompletableFuture.completedFuture(ticket)
        else
          CompletableFuture.failedFuture(
            new ServiceTicketException(
              s"Couldn't get service ticket from CAS! URL = ${request.getUrl}, status = ${response.getStatusCode}, body = ${response.getResponseBody}",
            ),
          ),
      )
  }

  private def createSession(serviceTicket: String): CompletableFuture[String] = {
    val request = withCallerIdAndCsrfHeader(withRequestTimeout(
      new RequestBuilder()
        .setUrl(s"${config.endpoint}${LahetysAPIConstants.CAS_TICKET_VALIDATION_PATH}")
        .setMethod("GET")
        .addQueryParam("ticket", serviceTicket)),
      config.callerId,
    ).build()

    httpClient.executeRequest(request).toCompletableFuture
      .thenCompose(response =>
        response.getCookies.asScala
          .find(_.name() == "JSESSIONID")
          .map(cookie => CompletableFuture.completedFuture(cookie.value()))
          .getOrElse(CompletableFuture.failedFuture(new MissingSessionCookieException("JSESSIONID", response))),
      )
  }

  private def withCallerIdAndCsrfHeader(requestBuilder: RequestBuilder, callerId: String): RequestBuilder =
    requestBuilder
      .setHeader("Caller-Id", callerId)
      .setHeader("CSRF", callerId)
      .addOrReplaceCookie(new DefaultCookie("CSRF", callerId))

  private def withRequestTimeout(requestBuilder: RequestBuilder): RequestBuilder =
    requestBuilder.setRequestTimeout(config.requestTimeoutMs.toInt)

  private def stripTrailingSlash(url: String): String =
    if (url.endsWith("/")) url.dropRight(1) else url

  private def requireNonEmpty(value: String, fieldName: String): Unit =
    if (value == null || value.isBlank)
      throw new IllegalStateException(s"Missing required configuration field: $fieldName")
}

private class ServiceTicketException(message: String) extends Exception(message)
private class TicketGrantingTicketException(message: String) extends RuntimeException(message)
private class MissingSessionCookieException(sessionName: String, response: Response)
  extends Exception(s"Missing session cookie '$sessionName' in response: status=${response.getStatusCode}, url=${response.getUri}")

class ViestinvalitysClientBuilderImpl(config: ViestinvalitysClientConfig)
  extends AuthenticationBuilder,
    PasswordBuilder,
    CallerIdBuilder,
    EndpointBuilder,
    CasEndpointBuilder,
    ViestinValitysClientBuilder {

  def this() =
    this(
      ViestinvalitysClientConfig(
        null,
        null,
        null,
        null,
        null,
        Option.empty,
        TimeUnit.MINUTES.toMillis(15),
        TimeUnit.HOURS.toMillis(7),
        1,
        10000L,
      ),
    )

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

  override def withRequestTimeoutMs(requestTimeoutMs: Long): ViestinValitysClientBuilder =
    ViestinvalitysClientBuilderImpl(config.copy(requestTimeoutMs = requestTimeoutMs))

  override def withNumberOfRetries(numberOfRetries: Int): ViestinValitysClientBuilder =
    ViestinvalitysClientBuilderImpl(config.copy(numberOfRetries = numberOfRetries))

  override def withSessionTicketValidMs(sessionTicketValidMs: Long): ViestinValitysClientBuilder =
    ViestinvalitysClientBuilderImpl(config.copy(sessionTicketValidMs = sessionTicketValidMs))

  override def withTicketGrantingTicketValidMs(ticketGrantingTicketValidMs: Long): ViestinValitysClientBuilder =
    ViestinvalitysClientBuilderImpl(config.copy(ticketGrantingTicketValidMs = ticketGrantingTicketValidMs))

  override def build(): ViestinvalitysClient =
    buildWithHttpClient(Dsl.asyncHttpClient())

  override def buildWithHttpClient(httpClient: AsyncHttpClient): ViestinvalitysClient =
    val sessionProvider =
      if config.sessionId.isDefined then
        new StaticSessionProvider(config.sessionId.get)
      else
        new CasSessionProvider(config, httpClient)

    ViestinvalitysClientImpl(
      httpClient,
      config.endpoint,
      config.callerId,
      sessionProvider,
      config.requestTimeoutMs,
      config.numberOfRetries,
    )
}
