package fi.oph.viestinvalitys

import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import fi.oph.viestinvalitys
import fi.oph.viestinvalitys.ViestinvalitysClient.*
import fi.oph.viestinvalitys.vastaanotto.model.{Lahetys, Liite, LuoLahetysSuccessResponse, LuoLiiteSuccessResponse, LuoViestiSuccessResponse, VastaanottajaResponse, Viesti}
import fi.oph.viestinvalitys.vastaanotto.resource.{LahetysAPIConstants, LuoLahetysFailureResponseImpl, LuoLahetysSuccessResponseImpl, LuoLiiteFailureResponseImpl, LuoLiiteSuccessResponseImpl, LuoViestiFailureResponseImpl, LuoViestiSuccessResponseImpl, VastaanottajaResponseImpl, VastaanottajatFailureResponse, VastaanottajatResponse, VastaanottajatSuccessResponse}
import fi.vm.sade.javautils.nio.cas.impl.{CasClientImpl, CasSessionFetcher}
import fi.vm.sade.javautils.nio.cas.{CasClient, CasClientBuilder, CasConfig}
import org.asynchttpclient.request.body.multipart.ByteArrayPart
import org.asynchttpclient.{Dsl, Request, RequestBuilder}

import java.util.concurrent.{CompletableFuture, TimeUnit}
import java.util.{Optional, UUID}
import scala.jdk.CollectionConverters.*
import java.util

class ViestinvalitysClientImpl(casClient: CasClient, endpoint: String, callerId: String) extends ViestinvalitysClient {

  val objectMapper = {
    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)
    mapper.registerModule(new Jdk8Module())
    mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
    mapper.configure(SerializationFeature.INDENT_OUTPUT, true)
    mapper
  }

  private def getJsonGetRequest(path: String): Request =
    new RequestBuilder()
      .setUrl(path)
      .setMethod("GET")
      .setRequestTimeout(10000)
      .addHeader("Caller-Id", this.callerId)
      .addHeader("Accept", "application/json").build()

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
    val response = casClient.executeAndRetryWithCleanSessionOnStatusCodes(getJsonPostRequest(LahetysAPIConstants.LAHETYKSET_PATH, lahetys), util.Set.of(401)).get()
    response.getStatusCode match
      case 200 => objectMapper.readValue(response.getResponseBody, classOf[LuoLahetysSuccessResponseImpl])
      case 403 => throw new ViestinvalitysClientException(Set.empty.asJava, 403)
      case _ =>
        val failureResponse = objectMapper.readValue(response.getResponseBody, classOf[LuoLahetysFailureResponseImpl])
        throw new ViestinvalitysClientException(failureResponse.validointiVirheet.asScala.toSet.asJava, response.getStatusCode)

  override def getVastaanottajat(lahetysTunniste: UUID, enintaan: Optional[java.lang.Integer]): util.Iterator[util.List[VastaanottajaResponse]] =
    new util.Iterator[util.List[VastaanottajaResponse]]:

      def getNextVastaanottajat(seuraavatUrl: Optional[String], enintaan: Optional[java.lang.Integer]): VastaanottajatSuccessResponse =
        val url = {
          if(seuraavatUrl.isPresent)
            seuraavatUrl.get
          else
            endpoint + LahetysAPIConstants.GET_VASTAANOTTAJAT_PATH.replace(LahetysAPIConstants.LAHETYSTUNNISTE_PARAM_PLACEHOLDER, lahetysTunniste.toString) + enintaan.map(v => "?" + LahetysAPIConstants.ENINTAAN_PARAM_NAME + "=" + v.toString).orElse("")
        }
        val response = casClient.executeAndRetryWithCleanSessionOnStatusCodes(getJsonGetRequest(url), util.Set.of(401)).get()
        response.getStatusCode match
          case 200 => objectMapper.readValue(response.getResponseBody, classOf[VastaanottajatSuccessResponse])
          case 403 => throw new ViestinvalitysClientException(Set.empty.asJava, 403)
          case _ =>
            val failureResponse = objectMapper.readValue(response.getResponseBody, classOf[VastaanottajatFailureResponse])
            throw new ViestinvalitysClientException(failureResponse.virheet.asScala.toSet.asJava, response.getStatusCode)

      var end = false;
      var vastaanottajatResponse = this.getNextVastaanottajat(Optional.empty, enintaan)

      override def hasNext: Boolean =
        this.vastaanottajatResponse.seuraavat.isPresent

      override def next(): util.List[VastaanottajaResponse] =
        if(end)
          throw new NoSuchElementException

        val vastaanottajat = this.vastaanottajatResponse.vastaanottajat
        if(this.vastaanottajatResponse.seuraavat.isEmpty)
          end = true
        else
          this.vastaanottajatResponse = getNextVastaanottajat(this.vastaanottajatResponse.seuraavat, Optional.empty)

        vastaanottajat


  override def luoLiite(liite: Liite): LuoLiiteSuccessResponse =
    val request = new RequestBuilder()
      .setUrl(this.endpoint + LahetysAPIConstants.LIITTEET_PATH)
      .setMethod("POST")
      .addBodyPart(new ByteArrayPart("liite", liite.getBytes(), liite.getSisaltoTyyppi, null, liite.getTiedostoNimi))
      .setRequestTimeout(10000)
      .addHeader("Caller-Id", this.callerId)
      .addHeader("Content-Type", "multipart/form-data")
      .addHeader("Accept", "application/json").build()
    val response = casClient.executeAndRetryWithCleanSessionOnStatusCodes(request, util.Set.of(401)).get()
    response.getStatusCode match
      case 200 => objectMapper.readValue(response.getResponseBody, classOf[LuoLiiteSuccessResponseImpl])
      case 403 => throw new ViestinvalitysClientException(Set.empty.asJava, 403)
      case _ =>
        val failureResponse = objectMapper.readValue(response.getResponseBody, classOf[LuoLiiteFailureResponseImpl])
        throw new ViestinvalitysClientException(failureResponse.virheet.asScala.toSet.asJava, response.getStatusCode)

  override def luoViesti(viesti: Viesti): LuoViestiSuccessResponse =
    val response = casClient.executeAndRetryWithCleanSessionOnStatusCodes(getJsonPostRequest(LahetysAPIConstants.VIESTIT_PATH, viesti), util.Set.of(401)).get()
    response.getStatusCode match
      case 200 => objectMapper.readValue(response.getResponseBody, classOf[LuoViestiSuccessResponseImpl])
      case 403 => throw new ViestinvalitysClientException(Set.empty.asJava, 403)
      case _ =>
        val failureResponse = objectMapper.readValue(response.getResponseBody, classOf[LuoViestiFailureResponseImpl])
        throw new ViestinvalitysClientException(failureResponse.validointiVirheet.asScala.toSet.asJava, response.getStatusCode)
}

case class ViestinvalitysClientConfig(username: String, password: String, callerId: String, endpoint: String, casEndpoint: String, sessionId: Option[String])

class ViestinvalitysClientBuilderImpl(config: ViestinvalitysClientConfig) extends AuthenticationBuilder, PasswordBuilder, CallerIdBuilder, EndpointBuilder, CasEndpointBuilder, ViestinValitysClientBuilder {

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
    val casConfig = new CasConfig.CasConfigBuilder(config.username, config.password, config.casEndpoint, config.endpoint + "/lahetys/j_spring_cas_security_check", "CSRF", config.callerId, "")
      .setJsessionName("JSESSIONID")
      .build();
    val casClient = {
      if(config.sessionId.isEmpty)
        CasClientBuilder.build(casConfig)
      else
        new CasClientImpl(casConfig, Dsl.asyncHttpClient(), new CasSessionFetcher(null, null, TimeUnit.HOURS.toMillis(7), TimeUnit.MINUTES.toMillis(15)) {
          override def clearSessionStore(): Unit = {}
          override def clearTgtStore(): Unit = {}
          override def fetchSessionToken(): CompletableFuture[String] =
            CompletableFuture.completedFuture(config.sessionId.get)
        })
    };
    ViestinvalitysClientImpl(casClient, config.endpoint, config.callerId)

}
