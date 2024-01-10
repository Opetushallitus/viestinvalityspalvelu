package fi.oph.viestinvalitys

import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import fi.oph.viestinvalitys
import fi.oph.viestinvalitys.ViestinvalitysClient.*
import fi.oph.viestinvalitys.vastaanotto.model.{Lahetys, Liite, LuoLahetysSuccessResponse, LuoLiiteSuccessResponse, LuoViestiSuccessResponse, Viesti}
import fi.oph.viestinvalitys.vastaanotto.resource.{APIConstants, LuoLahetysFailureResponseImpl, LuoLahetysSuccessResponseImpl, LuoLiiteFailureResponseImpl, LuoLiiteSuccessResponseImpl, LuoViestiFailureResponseImpl, LuoViestiSuccessResponseImpl}
import fi.vm.sade.javautils.nio.cas.impl.{CasClientImpl, CasSessionFetcher}
import fi.vm.sade.javautils.nio.cas.{CasClient, CasClientBuilder, CasConfig}
import org.asynchttpclient.request.body.multipart.ByteArrayPart
import org.asynchttpclient.{Dsl, Request, RequestBuilder}

import java.util.concurrent.CompletableFuture
import java.util.UUID
import scala.jdk.CollectionConverters.*
import java.util

class ViestinvalitysClientImpl(casClient: CasClient, endpoint: String, callerId: String) extends ViestinvalitysClient {

  val objectMapper = {
    val mapper = new ObjectMapper()
    mapper.registerModule(new Jdk8Module())
    mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
    mapper.configure(SerializationFeature.INDENT_OUTPUT, true)
    mapper
  }

  private def getJsonPostRequest(path: String, body: Any): Request =
    new RequestBuilder()
      .setUrl(this.endpoint + path)
      .setMethod("POST")
      .setBody(objectMapper.writeValueAsString(body))
      .setRequestTimeout(10000)
      .addHeader("Caller-Id", this.callerId)
      .addHeader("Content-Type", "application/json")
      .addHeader("Accept", "application/json").build()

  override def luoLahetys(lahetys: Lahetys): LuoLahetysSuccessResponse =
    val response = casClient.executeAndRetryWithCleanSessionOnStatusCodes(getJsonPostRequest(APIConstants.LAHETYKSET_PATH, lahetys), util.Set.of(401)).get()
    if(response.getStatusCode==403)
      throw new ViestinvalitysClientException(Set.empty.asJava, 403)
    if(response.getStatusCode!=200)
      val failureResponse = objectMapper.readValue(response.getResponseBody, classOf[LuoLahetysFailureResponseImpl])
      throw new ViestinvalitysClientException(failureResponse.validointiVirheet.asScala.toSet.asJava, response.getStatusCode)

    val successResponse = objectMapper.readValue(response.getResponseBody, classOf[LuoLahetysSuccessResponseImpl])
    successResponse

  override def luoLiite(liite: Liite): LuoLiiteSuccessResponse =
    val request = new RequestBuilder()
      .setUrl(this.endpoint + APIConstants.LIITTEET_PATH)
      .setMethod("POST")
      .addBodyPart(new ByteArrayPart("liite", liite.getBytes(), liite.getSisaltoTyyppi, null, liite.getTiedostoNimi))
      .setRequestTimeout(10000)
      .addHeader("Caller-Id", this.callerId)
      .addHeader("Content-Type", "multipart/form-data")
      .addHeader("Accept", "application/json").build()
    val response = casClient.executeAndRetryWithCleanSessionOnStatusCodes(request, util.Set.of(401)).get()
    if (response.getStatusCode == 403)
      throw new ViestinvalitysClientException(Set.empty.asJava, 403)
    if (response.getStatusCode != 200)
      val failureResponse = objectMapper.readValue(response.getResponseBody, classOf[LuoLiiteFailureResponseImpl])
      throw new ViestinvalitysClientException(failureResponse.virheet.asScala.toSet.asJava, response.getStatusCode)

    val successResponse = objectMapper.readValue(response.getResponseBody, classOf[LuoLiiteSuccessResponseImpl])
      successResponse

  override def luoViesti(viesti: Viesti): LuoViestiSuccessResponse =
    val response = casClient.executeAndRetryWithCleanSessionOnStatusCodes(getJsonPostRequest(APIConstants.VIESTIT_PATH, viesti), util.Set.of(401)).get()
    if (response.getStatusCode == 403)
      throw new ViestinvalitysClientException(Set.empty.asJava, 403)
    if (response.getStatusCode != 200)
      val failureResponse = objectMapper.readValue(response.getResponseBody, classOf[LuoViestiFailureResponseImpl])
      throw new ViestinvalitysClientException(failureResponse.validointiVirheet.asScala.toSet.asJava, response.getStatusCode)

    val successResponse = objectMapper.readValue(response.getResponseBody, classOf[LuoViestiSuccessResponseImpl])
      successResponse

}

case class ViestinvalitysClientConfig(username: String, password: String, callerId: String, endpoint: String, casEndpoint: String, sessionId: Option[String])

class ViestinvalitysClientBuilderImpl() extends AuthenticationBuilder, PasswordBuilder, CallerIdBuilder, EndpointBuilder, CasEndpointBuilder, ViestinValitysClientBuilder {

  var config = ViestinvalitysClientConfig(null, null, null, null, null, Option.empty)

  override def withEndpoint(endpoint: String): AuthenticationBuilder =
    config = config.copy(endpoint = endpoint)
    this

  override def withSessionId(sessionId: String): ViestinvalitysClientBuilderImpl =
    config = config.copy(sessionId = Option.apply(sessionId))
    this

  override def withUsername(username: String): ViestinvalitysClientBuilderImpl =
    config = config.copy(username = username)
    this

  override def withPassword(password: String): ViestinvalitysClientBuilderImpl =
    config = config.copy(password = password)
    this

  override def withCallerId(callerId: String): ViestinvalitysClientBuilderImpl =
    config = config.copy(callerId = callerId)
    this

  override def withCasEndpoint(casEndpoint: String): ViestinvalitysClientBuilderImpl =
    config = config.copy(casEndpoint = casEndpoint)
    this

  override def build(): ViestinvalitysClient =
    val casConfig = new CasConfig.CasConfigBuilder(config.username, config.password, config.casEndpoint, config.endpoint + "/j_spring_cas_security_check", "CSRF", config.callerId, "")
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
    ViestinvalitysClientImpl(casClient, config.endpoint, config.callerId)

}
