package fi.oph.viestinvalitys

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.nimbusds.jose.util.StandardCharset
import fi.oph.viestinvalitys.business.{Kayttooikeus, Prioriteetti, SisallonTyyppi}
import fi.oph.viestinvalitys.raportointi.model.{PalautaLahetyksetSuccessResponse, PalautaLahetysSuccessResponse, VastaanottajatTilassa, ViestiSuccessResponse}
import fi.oph.viestinvalitys.raportointi.resource.{RaportointiAPIConstants}
import fi.oph.viestinvalitys.raportointi.security.SecurityConstants.{OPH_ORGANISAATIO_OID}
import fi.oph.viestinvalitys.security.{AuditLog, AuditOperation}
import fi.oph.viestinvalitys.util.AwsUtil
import fi.oph.viestinvalitys.vastaanotto.model.Lahetys.Lahettaja
import fi.oph.viestinvalitys.vastaanotto.model.Viesti.Vastaanottaja
import fi.oph.viestinvalitys.vastaanotto.model.{KayttooikeusImpl, LahettajaImpl, LahetysImpl, MaskiImpl, VastaanottajaImpl, ViestiImpl}
import fi.oph.viestinvalitys.vastaanotto.resource.{LahetysAPIConstants, LuoLahetysSuccessResponseImpl, LuoViestiSuccessResponseImpl}
import fi.oph.viestinvalitys.vastaanotto.security.SecurityConstants
import fi.oph.viestinvalitys.vastaanotto.validation.LahetysValidator
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.{WithAnonymousUser, WithMockUser}
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.{DefaultMockMvcBuilder, MockMvcBuilders, MockMvcConfigurer}
import org.springframework.web.context.WebApplicationContext
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.request.{MockHttpServletRequestBuilder, MockMvcRequestBuilders}
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import software.amazon.awssdk.services.cloudwatchlogs.model.GetLogEventsRequest

import java.time.{Instant}
import java.util.{Optional, UUID}
import scala.jdk.CollectionConverters.*

// Luokat auditlokientryjen deserialisoimiseksi
@JsonIgnoreProperties(ignoreUnknown = true)
case class LueLahetysAuditLogEventTarget(lahetys: String)
@JsonIgnoreProperties(ignoreUnknown = true)
case class LueLahetysAuditLogEvent(operation: String, target: LueLahetysAuditLogEventTarget)
@JsonIgnoreProperties(ignoreUnknown = true)
case class LueViestiAuditLogEventTarget(viesti: String)
@JsonIgnoreProperties(ignoreUnknown = true)
case class LueViestiAuditLogEvent(operation: String, target: LueViestiAuditLogEventTarget)
/**
 * Raportointiapin integraatiotestit. Testeissä on pyritty kattamaan kaikkien endpointtien kaikki eri paluuarvoihin
 * johtavat skenaariot. Eri variaatiot näiden skenaarioiden sisällä (esim. parametrien validointi) testataan yksikkötasolla.
 * Hakutoimintojen paluuarvojen assertioiden suhteen pohjaa LocalUtilin generoimaan dataan joka alustetaan BaseIntegraatioTestissä.
 */
class RaportointiApiIntegraatioTest extends BaseIntegraatioTesti {


  val KATSELUOIKEUS = "APP_VIESTINVALITYS_KATSELU"
  val LAHETYSOIKEUS = "APP_VIESTINVALITYS_LAHETYS"
  val ORGANISAATIO = "1.2.246.562.10.240484683010"
  val LAPSI_ORGANISAATIO = "1.2.246.562.10.2014041814455745619200"
  val ORGANISAATIO2 = "1.2.246.562.10.79559059674"
  val OIKEUS = "APP_HAKEMUS_CRUD"

  private val objectMapper: ObjectMapper = {
    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)
    mapper.registerModule(new JavaTimeModule())
    mapper.registerModule(new Jdk8Module()) // tämä on java.util.Optional -kenttiä varten
    mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
    mapper.configure(SerializationFeature.INDENT_OUTPUT, true)
    mapper
  }

  @Autowired private val context: WebApplicationContext = null

  private var mvc: MockMvc = null

  @BeforeAll def setup(): Unit = {
    val configurer: MockMvcConfigurer = SecurityMockMvcConfigurers.springSecurity()
    val intermediate: DefaultMockMvcBuilder = MockMvcBuilders.webAppContextSetup(context).apply(configurer)
    mvc = intermediate.build()
  }

  def getLahetys(): LahetysImpl =
    LahetysImpl(
      Optional.of("Otsikko"),
      Optional.of("hakemuspalvelu"),
      Optional.of(LahetysValidator.VALIDATION_OPH_OID_PREFIX + ".111"),
      Optional.of(LahettajaImpl(Optional.empty(), Optional.of("noreply@opintopolku.fi"))),
      Optional.of("replyto@opintopolku.fi"),
      Optional.of(LahetysImpl.LAHETYS_PRIORITEETTI_NORMAALI),
      Optional.of(1)
    )

  def getViesti(otsikko: String = "Otsikko",
                sisalto: String = "Sisalto",
                vastaanottajat: java.util.List[Vastaanottaja] = java.util.List.of(VastaanottajaImpl(Optional.empty(), Optional.of("vallu.vastaanottaja+success@example.com"))),
                liitteidenTunnisteet: Optional[java.util.List[String]] = Optional.empty(),
                prioriteetti: Optional[String] = Optional.of(Prioriteetti.NORMAALI.toString.toLowerCase),
                lahetysTunniste: Optional[String] = Optional.empty,
                lahettavaPalvelu: Optional[String] = Optional.of("hakemuspalvelu"),
                lahettaja: Optional[Lahettaja] = Optional.of(LahettajaImpl(Optional.empty(), Optional.of("noreply@opintopolku.fi"))),
                lahettavanVirkailijanOid: Optional[String] = Optional.of(LahetysValidator.VALIDATION_OPH_OID_PREFIX + ".111"),
                replyTo: Optional[String] = Optional.of("replyto@opintopolku.fi"),
                sailytysAika: Optional[Integer] = Optional.of(1),
                idempotencyKey: String = null): ViestiImpl =
    ViestiImpl(
      otsikko = Optional.of(otsikko),
      sisalto = Optional.of(sisalto),
      sisallonTyyppi = Optional.of(SisallonTyyppi.TEXT.toString.toLowerCase),
      kielet = Optional.of(java.util.List.of("fi")),
      maskit = Optional.of(java.util.List.of(MaskiImpl(Optional.of("salaisuus"), Optional.of("maskattu")))),
      lahettavanVirkailijanOid = lahettavanVirkailijanOid,
      lahettaja = lahettaja,
      replyTo = replyTo,
      vastaanottajat = Optional.of(vastaanottajat),
      liitteidenTunnisteet = liitteidenTunnisteet,
      lahettavaPalvelu = lahettavaPalvelu,
      lahetysTunniste = lahetysTunniste,
      prioriteetti = prioriteetti,
      sailytysaika = sailytysAika,
      kayttooikeusRajoitukset = Optional.of(java.util.List.of(KayttooikeusImpl(Optional.of(OIKEUS), Optional.of(LAPSI_ORGANISAATIO)))),
      metadata = Optional.of(java.util.Map.of("avain", java.util.List.of("arvo1", "arvo2"))),
      idempotencyKey = Optional.ofNullable(idempotencyKey)
    )

  def jsonPost(path: String, body: Any): MockHttpServletRequestBuilder =
    MockMvcRequestBuilders
      .post(path)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .accept(MediaType.APPLICATION_JSON_VALUE)
      .content(objectMapper.writeValueAsString(body))

  def lueAuditLokiEntryt() =
    AwsUtil.cloudWatchLogsClient.getLogEvents(GetLogEventsRequest.builder()
      .logGroupName(AuditLog.auditLogGroupName)
      .logStreamName(AuditLog.auditLogStreamName)
      .build())
  /**
   * Testataan healthcheck-toiminnallisuus
   */
  @WithAnonymousUser
  @Test def testHealthCheckAnonymous(): Unit =
    // tuntematon käyttäjä blokataan
    mvc.perform(MockMvcRequestBuilders
        .get(RaportointiAPIConstants.HEALTHCHECK_PATH))
      .andExpect(status().isUnauthorized)

  @WithMockUser(value = "kayttaja")
  @Test def testHealthCheckOk(): Unit =
    // healthcheck palauttaa aina ok
    mvc.perform(MockMvcRequestBuilders
        .get(RaportointiAPIConstants.HEALTHCHECK_PATH))
      .andExpect(status().isOk())
      .andExpect(MockMvcResultMatchers.content().string("OK"));

  /**
   * Testataan lähetyksien haku eri skenaarioilla ja hakuparametreilla
   */
  @WithAnonymousUser
  @Test def testGetLahetyksetAnonymous(): Unit =
    // tuntematon käyttäjä blokataan
    mvc.perform(MockMvcRequestBuilders
        .get(RaportointiAPIConstants.GET_LAHETYKSET_LISTA_PATH)
        .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isUnauthorized)

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL))
  @Test def testGetLahetyksetNotAllowed(): Unit =
    val sessionAttr: Map[String, Object] = Map("kayttooikeudet" -> Set(Kayttooikeus(OIKEUS, Some(ORGANISAATIO)), Kayttooikeus(SecurityConstants.SECURITY_ROOLI_LAHETYS, Option.empty)))
    // käyttäjällä ei katseluoikeutta joten tulee 403
    mvc.perform(MockMvcRequestBuilders
        .get(RaportointiAPIConstants.GET_LAHETYKSET_LISTA_PATH)
        .sessionAttrs(sessionAttr.asJava)
        .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isForbidden)

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_KATSELU_FULL, "ROLE_APP_HAKEMUS_CRUD"))
  @Test def testGetLahetyksetInvalid(): Unit =
    val sessionAttr: Map[String, Object] = Map("kayttooikeudet" -> Set(Kayttooikeus(OIKEUS, Some(LAPSI_ORGANISAATIO)), Kayttooikeus(SecurityConstants.SECURITY_ROOLI_KATSELU, Option.empty)))
    // epävalidi hakukriteeri joten tulee 400
    mvc.perform(MockMvcRequestBuilders
        .get(RaportointiAPIConstants.GET_LAHETYKSET_LISTA_PATH+"?enintaan=abc")
        .sessionAttrs(sessionAttr.asJava)
        .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isBadRequest)

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL, SecurityConstants.SECURITY_ROOLI_KATSELU_FULL, "ROLE_APP_HAKEMUS_CRUD"))
  @Test def testGetLahetyksetEmptyList(): Unit =
    val sessionAttr: Map[String, Object] = Map("kayttooikeudet" -> Set(Kayttooikeus("FOOBAR_OIKEUS", None), Kayttooikeus(SecurityConstants.SECURITY_ROOLI_KATSELU, Option.empty)))
    // käyttäjän käyttöoikeuksilla ei löydy lähetyksiä, joten palautetaan tyhjä lista
    val result = mvc.perform(MockMvcRequestBuilders
        .get(RaportointiAPIConstants.GET_LAHETYKSET_LISTA_PATH)
        .sessionAttrs(sessionAttr.asJava)
        .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isOk).andReturn()
    val response = objectMapper.readValue(result.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[PalautaLahetyksetSuccessResponse])
    Assertions.assertEquals(List.empty, response.lahetykset.asScala)
    Assertions.assertEquals(Optional.empty, response.seuraavatAlkaen)
    Assertions.assertEquals(0, response.lukumaara)

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA_FULL, "ROLE_APP_HAKEMUS_CRUD"))
  @Test def testGetLahetyksetWithDefaultSivutus(): Unit =
    val sessionAttr: Map[String, Object] = Map("kayttooikeudet" -> Set(Kayttooikeus(SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA, Some(OPH_ORGANISAATIO_OID))))
    val result = mvc.perform(MockMvcRequestBuilders
        .get(RaportointiAPIConstants.GET_LAHETYKSET_LISTA_PATH+"?enintaan=20")
        .`with`(user("kayttaja").roles(SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA, SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA + "_" + OPH_ORGANISAATIO_OID))
        .sessionAttrs(sessionAttr.asJava)
        .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isOk).andReturn()
    val response = objectMapper.readValue(result.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[PalautaLahetyksetSuccessResponse])
    // testidatan lähetyksiä tulee käyttöliittymän oletussivutuksella myös seuraavalle sivulle
    Assertions.assertEquals(20, response.lahetykset.asScala.size)
    Assertions.assertTrue(response.seuraavatAlkaen.isPresent)

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA_FULL, "ROLE_APP_HAKEMUS_CRUD"))
  @Test def testGetLahetyksetAlkuajalla(): Unit =
    val sessionAttr: Map[String, Object] = Map("kayttooikeudet" -> Set(Kayttooikeus(SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA, Some(OPH_ORGANISAATIO_OID))))
    // luodaan viesti aikaleiman jälkeen
    val aikaleima = Instant.now()
    val viesti = getViesti(otsikko = "Uusi viesti")
    mvc.perform(jsonPost(LahetysAPIConstants.LUO_VIESTI_PATH, viesti))
    val result = mvc.perform(MockMvcRequestBuilders
        .get(RaportointiAPIConstants.GET_LAHETYKSET_LISTA_PATH + "?hakuAlkaen=" + aikaleima)
        .`with`(user("kayttaja").roles(SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA, SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA + "_" + OPH_ORGANISAATIO_OID))
        .sessionAttrs(sessionAttr.asJava)
        .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isOk).andReturn()
    val response = objectMapper.readValue(result.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[PalautaLahetyksetSuccessResponse])
    // aikaleiman jälkeen lähetetty viesti palautuu
    Assertions.assertEquals(1, response.lahetykset.asScala.size)
    Assertions.assertEquals("Uusi viesti", response.lahetykset.asScala.head.otsikko)

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA_FULL, "ROLE_APP_HAKEMUS_CRUD"))
  @Test def testGetLahetyksetLahettajaOidilla(): Unit =
    val sessionAttr: Map[String, Object] = Map("kayttooikeudet" -> Set(Kayttooikeus(SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA, Some(OPH_ORGANISAATIO_OID))))
    val result = mvc.perform(MockMvcRequestBuilders
        .get(RaportointiAPIConstants.GET_LAHETYKSET_LISTA_PATH + "?lahettaja=" + "1.2.246.562.24.1") // LocalUtilin testialustuksissa
        .`with`(user("kayttaja").roles(SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA, SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA + "_" + OPH_ORGANISAATIO_OID))
        .sessionAttrs(sessionAttr.asJava)
        .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isOk).andReturn()
    val response = objectMapper.readValue(result.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[PalautaLahetyksetSuccessResponse])
    // lähettäjä-oidilla löytyy LocalUtilissa ko oidilla alustetut lähetykset
    Assertions.assertEquals(27, response.lahetykset.asScala.size)

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA_FULL, "ROLE_APP_HAKEMUS_CRUD"))
  @Test def testGetLahetyksetOtsikolla(): Unit =
    val sessionAttr: Map[String, Object] = Map("kayttooikeudet" -> Set(Kayttooikeus(SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA, Some(OPH_ORGANISAATIO_OID))))
    val result = mvc.perform(MockMvcRequestBuilders
        .get(RaportointiAPIConstants.GET_LAHETYKSET_LISTA_PATH + "?viesti=" + "Yksittäinen viesti") // LocalUtilin testialustuksissa
        .`with`(user("kayttaja").roles(SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA, SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA + "_" + OPH_ORGANISAATIO_OID))
        .sessionAttrs(sessionAttr.asJava)
        .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isOk).andReturn()
    val response = objectMapper.readValue(result.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[PalautaLahetyksetSuccessResponse])
    // yksilöllisellä otsikolla löytyy yksi LocalUtilissa alustettu lähetys
    Assertions.assertEquals(1, response.lahetykset.asScala.size)

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA_FULL, "ROLE_APP_HAKEMUS_CRUD"))
  @Test def testGetLahetyksetSisallolla(): Unit =
    val sessionAttr: Map[String, Object] = Map("kayttooikeudet" -> Set(Kayttooikeus(SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA, Some(OPH_ORGANISAATIO_OID))))
    val result = mvc.perform(MockMvcRequestBuilders
        .get(RaportointiAPIConstants.GET_LAHETYKSET_LISTA_PATH + "?viesti=" + "Tämä on viesti käyttöoikeushierarkian todentamiseen") // LocalUtilin testialustuksissa
        .`with`(user("kayttaja").roles(SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA, SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA + "_" + OPH_ORGANISAATIO_OID))
        .sessionAttrs(sessionAttr.asJava)
        .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isOk).andReturn()
    val response = objectMapper.readValue(result.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[PalautaLahetyksetSuccessResponse])
    // yksilöllisellä viestin sisältötekstillä löytyy yksi LocalUtilissa alustettu lähetys
    Assertions.assertEquals(1, response.lahetykset.asScala.size)

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA_FULL, "ROLE_APP_HAKEMUS_CRUD"))
  @Test def testGetLahetyksetVastaanottajalla(): Unit =
    val sessionAttr: Map[String, Object] = Map("kayttooikeudet" -> Set(Kayttooikeus(SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA, Some(OPH_ORGANISAATIO_OID))))
    val result = mvc.perform(MockMvcRequestBuilders
        .get(RaportointiAPIConstants.GET_LAHETYKSET_LISTA_PATH + "?vastaanottaja=ruotsi.vastaanottaja@example.com") // LocalUtilin testialustuksissa
        .`with`(user("kayttaja").roles(SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA, SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA + "_" + OPH_ORGANISAATIO_OID))
        .sessionAttrs(sessionAttr.asJava)
        .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isOk).andReturn()
    val response = objectMapper.readValue(result.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[PalautaLahetyksetSuccessResponse])
    // yksilöllisellä viestin vastaanottajalla löytyy yksi LocalUtilissa alustettu lähetys
    Assertions.assertEquals(1, response.lahetykset.asScala.size)

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA_FULL, "ROLE_APP_HAKEMUS_CRUD"))
  @Test def testGetLahetyksetPalvelulla(): Unit =
    val sessionAttr: Map[String, Object] = Map("kayttooikeudet" -> Set(Kayttooikeus(SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA, Some(OPH_ORGANISAATIO_OID))))
    val result = mvc.perform(MockMvcRequestBuilders
        .get(RaportointiAPIConstants.GET_LAHETYKSET_LISTA_PATH + "?palvelu=testipalvelu") // LocalUtilin testialustuksissa
        .`with`(user("kayttaja").roles(SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA, SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA + "_" + OPH_ORGANISAATIO_OID))
        .sessionAttrs(sessionAttr.asJava)
        .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isOk).andReturn()
    val response = objectMapper.readValue(result.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[PalautaLahetyksetSuccessResponse])
    val tunnisteet = response.lahetykset.asScala.map(lahetys => lahetys.lahetysTunniste).toSet
    // lähettävällä palvelulla "testipalvelu" löytyy 3 LocalUtilissa alustettua lähetystä
    Assertions.assertEquals(3, response.lahetykset.asScala.size)

    // luetaan auditlokit ja filtteröidään lukueventit
    // useamman lähetyksen katselussa tunnisteet on yhdistetty samaan merkintään pilkulla eroteltuna
    val lahetyksetLogEvent = lueAuditLokiEntryt().events().asScala
      .filter(e => AuditOperation.ReadLahetys.name.equals(objectMapper.readValue(e.message(), classOf[AuditLogEvent]).operation))
      .map(e => objectMapper.readValue(e.message(), classOf[LueLahetysAuditLogEvent]))
      .filter(e => tunnisteet.forall(e.target.lahetys.contains))
    Assertions.assertTrue(lahetyksetLogEvent.size > 1) // muissakin testeissä on katseltu näitä iditä...
  /**
   * Testataan yksittäisen lähetyksen haku tunnisteella
   */
  @WithAnonymousUser
  @Test def testGetLahetysAnonymous(): Unit =
    // tuntematon käyttäjä blokataan
    mvc.perform(MockMvcRequestBuilders
        .get(RaportointiAPIConstants.GET_LAHETYS_PATH.replace(RaportointiAPIConstants.LAHETYSTUNNISTE_PARAM_PLACEHOLDER, UUID.randomUUID().toString))
        .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isUnauthorized)

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL))
  @Test def testGetLahetysNotAllowed(): Unit =
    val sessionAttr: Map[String, Object] = Map("kayttooikeudet" -> Set(Kayttooikeus(OIKEUS, Some(ORGANISAATIO)), Kayttooikeus(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL, Option.empty)))
    // luodaan lähetys
    val luoResult = mvc.perform(jsonPost(LahetysAPIConstants.LUO_LAHETYS_PATH, getLahetys()))
      .andExpect(status().isOk).andReturn()
    val luoLahetysResponse = objectMapper.readValue(luoResult.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[LuoLahetysSuccessResponseImpl])
    // käyttäjällä ei katseluoikeutta joten tulee 403
    mvc.perform(MockMvcRequestBuilders
        .get(RaportointiAPIConstants.GET_LAHETYS_PATH.replace(RaportointiAPIConstants.LAHETYSTUNNISTE_PARAM_PLACEHOLDER, luoLahetysResponse.lahetysTunniste.toString))
        .sessionAttrs(sessionAttr.asJava)
        .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isForbidden)

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL, SecurityConstants.SECURITY_ROOLI_KATSELU_FULL, "ROLE_APP_HAKEMUS_CRUD"))
  @Test def testGetLahetysInvalid(): Unit =
    val sessionAttr: Map[String, Object] = Map("kayttooikeudet" -> Set(Kayttooikeus(OIKEUS, Some(LAPSI_ORGANISAATIO)), Kayttooikeus(SecurityConstants.SECURITY_ROOLI_KATSELU_FULL, Option.empty)))

    // epävalidi lähetystunnus joten tulee 400
    val result = mvc.perform(MockMvcRequestBuilders
        .get(RaportointiAPIConstants.GET_LAHETYS_PATH.replace(RaportointiAPIConstants.LAHETYSTUNNISTE_PARAM_PLACEHOLDER, "1234"))
        .sessionAttrs(sessionAttr.asJava)
        .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isBadRequest)

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL, SecurityConstants.SECURITY_ROOLI_KATSELU_FULL, "ROLE_APP_HAKEMUS_CRUD"))
  @Test def testGetLahetysGone(): Unit =
    val sessionAttr: Map[String, Object] = Map("kayttooikeudet" -> Set(Kayttooikeus(OIKEUS, Some(LAPSI_ORGANISAATIO)), Kayttooikeus(SecurityConstants.SECURITY_ROOLI_KATSELU_FULL, Option.empty)))

    // lähetystunnus jolla ei löydy lähetystä joten tulee 410
    mvc.perform(MockMvcRequestBuilders
        .get(RaportointiAPIConstants.GET_LAHETYS_PATH.replace(RaportointiAPIConstants.LAHETYSTUNNISTE_PARAM_PLACEHOLDER, UUID.randomUUID().toString))
        .sessionAttrs(sessionAttr.asJava)
        .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isGone)

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL, SecurityConstants.SECURITY_ROOLI_KATSELU_FULL, "ROLE_APP_HAKEMUS_CRUD"))
  @Test def testGetLahetysOkJaGetViestiLahetystunnuksellaOk(): Unit =
    val sessionAttr: Map[String, Object] = Map("kayttooikeudet" -> Set(Kayttooikeus(OIKEUS, Some(LAPSI_ORGANISAATIO)), Kayttooikeus(SecurityConstants.SECURITY_ROOLI_KATSELU_FULL, Option.empty)))

    val viesti = getViesti()
    val luoViestiResult = mvc.perform(jsonPost(LahetysAPIConstants.LUO_VIESTI_PATH, viesti))
      .andExpect(status().isOk()).andReturn()
    val luoViestiResponse = objectMapper.readValue(luoViestiResult.getResponse.getContentAsString, classOf[LuoViestiSuccessResponseImpl])
    // haetaan lähetys lähetystunnuksella
    val getLahetysResult = mvc.perform(MockMvcRequestBuilders
        .get(RaportointiAPIConstants.GET_LAHETYS_PATH.replace(RaportointiAPIConstants.LAHETYSTUNNISTE_PARAM_PLACEHOLDER, luoViestiResponse.lahetysTunniste.toString))
        .sessionAttrs(sessionAttr.asJava)
        .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isOk).andReturn()
    val response = objectMapper.readValue(getLahetysResult.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[PalautaLahetysSuccessResponse])
    // palautuu täsmäävän lähetyksen tiedot
    Assertions.assertEquals("Otsikko", response.otsikko)
    Assertions.assertEquals(1, response.viestiLkm)
    Assertions.assertEquals(Seq(VastaanottajatTilassa("ODOTTAA", 1)), response.tilat.asScala)
    // haetaan viesti lähetystunnuksella
    // nämä kutsut tehdään käytännössä client-päässä yhdessä, joten yhdistetty myös testissä
    val viestiResult = mvc.perform(MockMvcRequestBuilders
        .get(RaportointiAPIConstants.GET_VIESTI_LAHETYSTUNNISTEELLA_PATH.replace(RaportointiAPIConstants.LAHETYSTUNNISTE_PARAM_PLACEHOLDER, luoViestiResponse.lahetysTunniste.toString))
        .sessionAttrs(sessionAttr.asJava)
        .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isOk).andReturn()
    // palautuu viestin tiedot
    val viestiResponse = objectMapper.readValue(viestiResult.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[ViestiSuccessResponse])
    Assertions.assertEquals("Otsikko", viestiResponse.otsikko)

    // luetaan auditlokit ja filtteröidään tähän liittyvät eventit
    val lahetysLogEvent = lueAuditLokiEntryt().events().asScala
      .filter(e => AuditOperation.ReadLahetys.name.equals(objectMapper.readValue(e.message(), classOf[AuditLogEvent]).operation))
      .map(e => objectMapper.readValue(e.message(), classOf[LueLahetysAuditLogEvent]))
      .filter(e => luoViestiResponse.lahetysTunniste.toString.equals(e.target.lahetys))
    Assertions.assertEquals(1, lahetysLogEvent.size)
    val viestiLogEvent = lueAuditLokiEntryt().events().asScala
      .filter(e => AuditOperation.ReadViesti.name.equals(objectMapper.readValue(e.message(), classOf[AuditLogEvent]).operation))
      .map(e => objectMapper.readValue(e.message(), classOf[LueViestiAuditLogEvent]))
      .filter(e => luoViestiResponse.viestiTunniste.toString.equals(e.target.viesti))
    // lähetyksen ja viestin katselusta jäi audit-merkintä
    Assertions.assertEquals(1, lahetysLogEvent.size)
    Assertions.assertEquals(1, viestiLogEvent.size)


  /**
   * Viestin haku
   */
  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL, SecurityConstants.SECURITY_ROOLI_KATSELU_FULL, "ROLE_APP_HAKEMUS_CRUD"))
  @Test def testGetViestiLahetystunnuksellaInvalid(): Unit =
    val sessionAttr: Map[String, Object] = Map("kayttooikeudet" -> Set(Kayttooikeus(OIKEUS, Some(LAPSI_ORGANISAATIO)), Kayttooikeus(SecurityConstants.SECURITY_ROOLI_KATSELU_FULL, Option.empty)))

    val viestiResult = mvc.perform(MockMvcRequestBuilders
        .get(RaportointiAPIConstants.GET_VIESTI_LAHETYSTUNNISTEELLA_PATH.replace(RaportointiAPIConstants.LAHETYSTUNNISTE_PARAM_PLACEHOLDER, "foo"))
        .sessionAttrs(sessionAttr.asJava)
        .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isBadRequest)

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL, SecurityConstants.SECURITY_ROOLI_KATSELU_FULL, "ROLE_APP_HAKEMUS_CRUD"))
  @Test def testGetViestiLahetystunnuksellaGone(): Unit =
    val sessionAttr: Map[String, Object] = Map("kayttooikeudet" -> Set(Kayttooikeus(OIKEUS, Some(LAPSI_ORGANISAATIO)), Kayttooikeus(SecurityConstants.SECURITY_ROOLI_KATSELU_FULL, Option.empty)))

    mvc.perform(MockMvcRequestBuilders
        .get(RaportointiAPIConstants.GET_VIESTI_LAHETYSTUNNISTEELLA_PATH.replace(RaportointiAPIConstants.LAHETYSTUNNISTE_PARAM_PLACEHOLDER, UUID.randomUUID().toString))
        .sessionAttrs(sessionAttr.asJava)
        .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isGone)

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL, "ROLE_APP_HAKEMUS_CRUD"))
  @Test def testGetViestiLahetystunnuksellaNotAllowed(): Unit =
    val sessionAttr: Map[String, Object] = Map("kayttooikeudet" -> Set(Kayttooikeus(OIKEUS, Some(LAPSI_ORGANISAATIO)), Kayttooikeus(SecurityConstants.SECURITY_ROOLI_KATSELU_FULL, Option.empty)))

    mvc.perform(MockMvcRequestBuilders
        .get(RaportointiAPIConstants.GET_VIESTI_LAHETYSTUNNISTEELLA_PATH.replace(RaportointiAPIConstants.LAHETYSTUNNISTE_PARAM_PLACEHOLDER, UUID.randomUUID().toString))
        .sessionAttrs(sessionAttr.asJava)
        .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isForbidden)

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL, SecurityConstants.SECURITY_ROOLI_KATSELU_FULL, "ROLE_APP_HAKEMUS_CRUD"))
  @Test def testGetViestiInvalid(): Unit =
    val sessionAttr: Map[String, Object] = Map("kayttooikeudet" -> Set(Kayttooikeus(OIKEUS, Some(LAPSI_ORGANISAATIO)), Kayttooikeus(SecurityConstants.SECURITY_ROOLI_KATSELU_FULL, Option.empty)))

    mvc.perform(MockMvcRequestBuilders
        .get(RaportointiAPIConstants.GET_VIESTI_PATH.replace(RaportointiAPIConstants.VIESTITUNNISTE_PARAM_PLACEHOLDER, "foo"))
        .sessionAttrs(sessionAttr.asJava)
        .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isBadRequest)

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL, SecurityConstants.SECURITY_ROOLI_KATSELU_FULL, "ROLE_APP_HAKEMUS_CRUD"))
  @Test def testGetViestiGone(): Unit =
    val sessionAttr: Map[String, Object] = Map("kayttooikeudet" -> Set(Kayttooikeus(OIKEUS, Some(LAPSI_ORGANISAATIO)), Kayttooikeus(SecurityConstants.SECURITY_ROOLI_KATSELU_FULL, Option.empty)))

    mvc.perform(MockMvcRequestBuilders
        .get(RaportointiAPIConstants.GET_VIESTI_PATH.replace(RaportointiAPIConstants.VIESTITUNNISTE_PARAM_PLACEHOLDER, UUID.randomUUID().toString))
        .sessionAttrs(sessionAttr.asJava)
        .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isGone)

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL, "ROLE_APP_HAKEMUS_CRUD"))
  @Test def testGetViestiNotAllowed(): Unit =
    val sessionAttr: Map[String, Object] = Map("kayttooikeudet" -> Set(Kayttooikeus(OIKEUS, Some(LAPSI_ORGANISAATIO)), Kayttooikeus(SecurityConstants.SECURITY_ROOLI_KATSELU_FULL, Option.empty)))
    // koska käyttäjällä ei ole katseluoikeuksia palautuu 403
    mvc.perform(MockMvcRequestBuilders
        .get(RaportointiAPIConstants.GET_VIESTI_PATH.replace(RaportointiAPIConstants.VIESTITUNNISTE_PARAM_PLACEHOLDER, UUID.randomUUID().toString))
        .sessionAttrs(sessionAttr.asJava)
        .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isForbidden)

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL, SecurityConstants.SECURITY_ROOLI_KATSELU_FULL, "ROLE_APP_HAKEMUS_CRUD"))
  @Test def testGetViestiOk(): Unit =
    val sessionAttr: Map[String, Object] = Map("kayttooikeudet" -> Set(Kayttooikeus(OIKEUS, Some(LAPSI_ORGANISAATIO)), Kayttooikeus(SecurityConstants.SECURITY_ROOLI_KATSELU_FULL, Option.empty)))

    val viesti = getViesti()
    val luoViestiResult = mvc.perform(jsonPost(LahetysAPIConstants.LUO_VIESTI_PATH, viesti))
      .andExpect(status().isOk()).andReturn()
    val luoViestiResponse = objectMapper.readValue(luoViestiResult.getResponse.getContentAsString, classOf[LuoViestiSuccessResponseImpl])

    val viestiResult = mvc.perform(MockMvcRequestBuilders
        .get(RaportointiAPIConstants.GET_VIESTI_PATH.replace(RaportointiAPIConstants.VIESTITUNNISTE_PARAM_PLACEHOLDER, luoViestiResponse.viestiTunniste.toString))
        .sessionAttrs(sessionAttr.asJava)
        .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isOk).andReturn()
    val viestiResponse = objectMapper.readValue(viestiResult.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[ViestiSuccessResponse])
    Assertions.assertEquals("Otsikko", viestiResponse.otsikko)

    // luetaan auditlokit ja filtteröidään viestin katseluun liittyvät eventit
    val viestiLogEvent = lueAuditLokiEntryt().events().asScala
      .filter(e => AuditOperation.ReadViesti.name.equals(objectMapper.readValue(e.message(), classOf[AuditLogEvent]).operation))
      .map(e => objectMapper.readValue(e.message(), classOf[LueViestiAuditLogEvent]))
      .filter(e => luoViestiResponse.viestiTunniste.toString.equals(e.target.viesti))
    // löytyy yksi lokimerkintä
    Assertions.assertEquals(1, viestiLogEvent.size)

  /**
   * Lähettävät palvelut
   */
  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_KATSELU_FULL, SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL))
  @Test def testGetLahettavatPalvelut(): Unit =
    val sessionAttr: Map[String, Object] = Map("kayttooikeudet" -> Set(Kayttooikeus(SecurityConstants.SECURITY_ROOLI_KATSELU, Option.empty)))
    val result = mvc.perform(MockMvcRequestBuilders
        .get(RaportointiAPIConstants.PALVELUT_PATH)
        .sessionAttrs(sessionAttr.asJava)
        .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isOk).andReturn()
    val response = objectMapper.readValue(result.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[Set[String]])
    // apista palautuu integraatiotesteille LocalUtilissa alustetun datan palvelut
    Assertions.assertEquals(Set("hakemuspalvelu", "osoitepalvelu", "testipalvelu"), response)
}
