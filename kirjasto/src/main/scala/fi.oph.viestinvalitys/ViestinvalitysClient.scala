package fi.oph.viestinvalitys

import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import fi.oph.viestinvalitys
import fi.oph.viestinvalitys.ViestinvalitysClient.*
import fi.oph.viestinvalitys.vastaanotto.model.{Lahetys, Viesti}
import fi.oph.viestinvalitys.vastaanotto.resource.{APIConstants, LuoLahetysSuccessResponse, LuoViestiSuccessResponse}
import fi.vm.sade.javautils.nio.cas.impl.{CasClientImpl, CasSessionFetcher}
import fi.vm.sade.javautils.nio.cas.{CasClient, CasClientBuilder, CasConfig}
import org.asynchttpclient.{Dsl, Request, RequestBuilder}

import java.util.concurrent.CompletableFuture
import java.util.{Set, UUID}

class ViestinvalitysClientImpl(casClient: CasClient, endpoint: String, callerId: String) extends ViestinvalitysClient {

  final val CSRF_VALUE = "CSRF";

  val objectMapper = {
    val mapper = new ObjectMapper()
    mapper.registerModule(new Jdk8Module())
    mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
    mapper.configure(SerializationFeature.INDENT_OUTPUT, true)
    mapper
  }

  private def getRequest(path: String, body: Any): Request =
    new RequestBuilder()
      .setUrl(this.endpoint + path)
      .setMethod("POST")
      .setBody(objectMapper.writeValueAsString(body))
      .setRequestTimeout(10000)
      .addHeader("Caller-Id", this.callerId)
      .addHeader("CSRF", CSRF_VALUE)
      .addHeader("Content-Type", "application/json")
      .addHeader("Accept", "application/json")
      .addHeader("Cookie", String.format("CSRF=%s;", CSRF_VALUE)).build()

  override def luoLahetys(lahetys: Lahetys): UUID =
    val response = casClient.executeAndRetryWithCleanSessionOnStatusCodes(getRequest(APIConstants.LAHETYKSET_PATH, lahetys), Set.of(302, 401)).get()
    val successResponse = objectMapper.readValue(response.getResponseBody, classOf[LuoLahetysSuccessResponse])
    UUID.fromString(successResponse.lahetysTunniste)

  override def luoViesti(viesti: Viesti): UUID =
    val response = casClient.executeAndRetryWithCleanSessionOnStatusCodes(getRequest(APIConstants.VIESTIT_PATH, viesti), Set.of(302, 401)).get()
    val successResponse = objectMapper.readValue(response.getResponseBody, classOf[LuoViestiSuccessResponse])
    UUID.fromString(successResponse.viestiTunniste)

}

case class ViestinvalitysClientConfig(username: String, password: String, callerId: String, endpoint: Option[String], sessionId: Option[String])

class ViestinvalitysClientBuilderImpl() extends AuthenticationBuilder, PasswordBuilder, CallerIdBuilder, ViestinValitysClientBuilder {

  var config = ViestinvalitysClientConfig(null, null, null, Option.empty, Option.empty)

  override def withSessionId(sessionId: String): CallerIdBuilder =
    config = config.copy(sessionId = Option.apply(sessionId))
    this

  override def withUsername(username: String): PasswordBuilder =
    config = config.copy(username = username)
    this

  override def withPassword(password: String): CallerIdBuilder =
    config = config.copy(password = password)
    this

  override def withCallerId(callerId: String): ViestinValitysClientBuilder =
    config = config.copy(callerId = callerId)
    this

  override def withEndpoint(endpoint: String): ViestinValitysClientBuilder =
    config = config.copy(endpoint = Option.apply(endpoint))
    this

  override def build(): ViestinvalitysClient =
    val casConfig = new CasConfig.CasConfigBuilder(config.username, config.password, "https://virkailija.hahtuvaopintopolku.fi/cas", "https://viestinvalitys.hahtuvaopintopolku.fi/j_spring_cas_security_check", "CSRF", config.callerId, "")
      .setJsessionName("JSESSIONID")
      .build();
    val casClient = {
      if(config.sessionId.isEmpty)
        CasClientBuilder.build(casConfig)
      else
        new CasClientImpl(casConfig, Dsl.asyncHttpClient(), new CasSessionFetcher(null, null, null, null) {
          override def clearSessionStore(): Unit = {}
          override def clearTgtStore(): Unit = {}
          override def fetchSessionToken(): CompletableFuture[String] =
            CompletableFuture.completedFuture(config.sessionId.get)
        })
    };
    ViestinvalitysClientImpl(casClient, config.endpoint.get, config.callerId)

}
