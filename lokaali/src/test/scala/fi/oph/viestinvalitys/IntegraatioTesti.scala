package fi.oph.viestinvalitys

import com.fasterxml.jackson.databind.ObjectMapper
import com.nimbusds.jose.util.StandardCharset
import fi.oph.viestinvalitys.util.{AwsUtil, DbUtil}
import fi.oph.viestinvalitys.business.{Kieli, Prioriteetti, SisallonTyyppi, VastaanottajanTila}
import fi.oph.viestinvalitys.vastaanotto.model.Viesti.Vastaanottaja
import fi.oph.viestinvalitys.vastaanotto.model.{LahettajaImpl, LahetysImpl, VastaanottajaImpl, ViestiImpl, ViestiValidator}
import fi.oph.viestinvalitys.vastaanotto.resource.{APIConstants, HealthcheckResource, LuoLahetysFailureResponseImpl, LuoLahetysSuccessResponseImpl, LuoLiiteFailureResponseImpl, LuoLiiteSuccessResponseImpl, LuoViestiFailureResponseImpl, LuoViestiSuccessResponseImpl, PalautaLahetysSuccessResponse, PalautaViestiSuccessResponse, VastaanottajatSuccessResponse}
import fi.oph.viestinvalitys.vastaanotto.security.SecurityConstants
import org.junit.Before
import org.junit.jupiter.api.*
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.{UseMainMethod, WebEnvironment}
import org.springframework.http.MediaType
import org.springframework.mock.web.{MockHttpServletRequest, MockMultipartFile}
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.test.context.support.{WithAnonymousUser, WithMockUser}
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.{DefaultMockMvcBuilder, MockMvcBuilders, MockMvcConfigurer}
import org.springframework.web.context.WebApplicationContext
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.localstack.LocalStackContainer.Service
import org.testcontainers.utility.DockerImageName
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.request.{MockHttpServletRequestBuilder, MockMvcRequestBuilders}
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import java.util.{Optional, UUID}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import concurrent.ExecutionContext.Implicits.global
import scala.jdk.CollectionConverters.*

/**
 * Lähetysapin integraatiotestit. Testeissä on pyritty kattamaan kaikkien endpointtien kaikki eri paluuarvoihin
 * johtavat skenaariot. Eri variaatiot näiden skenaarioiden sisällä (esim. erityyppiset validointiongelmat) testataan
 * yksikkötasolla.
 */
class IntegraatioTesti extends BaseIntegraatioTesti {

  @Autowired private val objectMapper: ObjectMapper = null
  @Autowired private val context: WebApplicationContext = null

  private var mvc: MockMvc = null

  @BeforeAll def setup(): Unit = {
    val configurer: MockMvcConfigurer = SecurityMockMvcConfigurers.springSecurity()
    val intermediate: DefaultMockMvcBuilder = MockMvcBuilders.webAppContextSetup(context).apply(configurer)
    mvc = intermediate.build()
  }

  def getViesti(vastaanottajat: java.util.List[Vastaanottaja] = java.util.List.of(VastaanottajaImpl(Optional.empty(), Optional.of("vallu.vastaanottaja+success@example.com"))),
                liitteidenTunnisteet: Optional[java.util.List[String]] = Optional.empty(),
                prioriteetti: String = Prioriteetti.NORMAALI.toString.toLowerCase): ViestiImpl =
    ViestiImpl(
      otsikko = Optional.of("Otsikko"),
      sisalto = Optional.of("Sisalto"),
      sisallonTyyppi = Optional.of(SisallonTyyppi.TEXT.toString.toLowerCase),
      kielet = Optional.of(java.util.List.of("fi")),
      maskit = Optional.empty(),
      lahettavanVirkailijanOid = Optional.empty(),
      lahettaja = Optional.of(LahettajaImpl(Optional.empty(), Optional.of("noreply@opintopolku.fi"))),
      replyTo = Optional.empty,
      vastaanottajat = Optional.of(vastaanottajat),
      liitteidenTunnisteet = liitteidenTunnisteet,
      lahettavaPalvelu = Optional.of("hakemuspalvelu"),
      lahetysTunniste = Optional.empty(),
      prioriteetti = Optional.of(prioriteetti),
      sailytysAika = Optional.of(1),
      kayttooikeusRajoitukset = Optional.empty(),
      metadata = Optional.empty()
    )

  def getLahetys(): LahetysImpl =
    LahetysImpl(
      Optional.of("Otsikko"),
      Optional.empty()
    )

  def jsonPost(path: String, body: Any): MockHttpServletRequestBuilder =
    MockMvcRequestBuilders
      .post(path)
      .contentType(MediaType.APPLICATION_JSON_VALUE)
      .accept(MediaType.APPLICATION_JSON_VALUE)
      .content(objectMapper.writeValueAsString(body))

  /**
   * Testataan healthcheck-toiminnallisuus
   */
  @WithAnonymousUser
  @Test def testHealthCheckAnonymous(): Unit =
    // tuntematon käyttäjä ohjataan tunnistautumaan
    mvc.perform(MockMvcRequestBuilders
      .get(APIConstants.HEALTHCHECK_PATH))
      .andExpect(status().isFound)

  @WithMockUser(value = "kayttaja")
  @Test def testHealthCheckOk(): Unit =
    // healthcheck palauttaa aina ok
    mvc.perform(MockMvcRequestBuilders
      .get(APIConstants.HEALTHCHECK_PATH))
      .andExpect(status().isOk())
      .andExpect(MockMvcResultMatchers.content().string("OK"));

  /**
   * Testataan lähetyksen luonti
   */
  @WithAnonymousUser
  @Test def testLuoLahetysAnonymous(): Unit =
    // tuntematon käyttäjä ohjataan tunnistautumaan
    mvc.perform(jsonPost(APIConstants.LUO_LAHETYS_PATH, getLahetys()))
      .andExpect(status().isFound)

  @WithMockUser(value = "kayttaja", authorities = Array())
  @Test def testLuoLahetysNotAllowed(): Unit =
    // vaatii VIESTINVALITYS_LAHETYS-oikeuden
    mvc.perform(jsonPost(APIConstants.LUO_LAHETYS_PATH, getLahetys()))
      .andExpect(status().isForbidden())

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL))
  @Test def testLuoLahetysMalformedJson(): Unit =
    // ei validi json ei sallittu
    val result = mvc.perform(jsonPost(APIConstants.LUO_LAHETYS_PATH, "tämä ei ole lähetys-json-objekti"))
      .andExpect(status().isBadRequest).andReturn()

    Assertions.assertEquals(LuoLahetysFailureResponseImpl(java.util.List.of(APIConstants.VIRHEELLINEN_LAHETYS_JSON_VIRHE)),
      objectMapper.readValue(result.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[LuoLahetysFailureResponseImpl]))

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL))
  @Test def testLuoLahetysInvalidRequest(): Unit =
    // tyhjä otsikko ei (esimerkiksi) ole sallittu, muuten validointi testataan yksikkötesteillä
    val result = mvc.perform(jsonPost(APIConstants.LUO_LAHETYS_PATH, getLahetys().copy(otsikko = Optional.empty())))
      .andExpect(status().isBadRequest).andReturn()

    Assertions.assertEquals(LuoLahetysFailureResponseImpl(java.util.List.of(ViestiValidator.VALIDATION_OTSIKKO_TYHJA)),
      objectMapper.readValue(result.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[LuoLahetysFailureResponseImpl]))

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL))
  @Test def testLuoLahetysAllowed(): Unit =
    // käyttäjälle VIESTINVALITYS_LAHETYS-oikeus ja viesti validi, joten lähetys onnistuu
    val result = mvc.perform(jsonPost(APIConstants.LUO_LAHETYS_PATH, getLahetys()))
      .andExpect(status().isOk).andReturn()

    val luoLahetysResponse = objectMapper.readValue(result.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[LuoLahetysSuccessResponseImpl])

  /**
   * Testataan lähetyksen haku
   */
  @WithAnonymousUser
  @Test def testGetLahetysAnonymous(): Unit =
    // tuntematon käyttäjä ohjataan tunnistautumaan
    mvc.perform(MockMvcRequestBuilders
      .get(APIConstants.GET_LAHETYS_PATH.replace(APIConstants.LAHETYSTUNNISTE_PARAM_PLACEHOLDER, UUID.randomUUID().toString))
      .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isFound)

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL))
  @Test def testGetLahetysNotAllowed(): Unit =
    // luodaan lähetys
    val luoResult = mvc.perform(jsonPost(APIConstants.LUO_LAHETYS_PATH, getLahetys()))
      .andExpect(status().isOk).andReturn()
    val luoLahetysResponse = objectMapper.readValue(luoResult.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[LuoLahetysSuccessResponseImpl])

    // käyttäjällä ei katseluoikeutta joten tulee 403
    mvc.perform(MockMvcRequestBuilders
        .get(APIConstants.GET_LAHETYS_PATH.replace(APIConstants.LAHETYSTUNNISTE_PARAM_PLACEHOLDER, luoLahetysResponse.lahetysTunniste.toString))
        .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isForbidden)

  @Test def testGetLahetysNotAllowedEriKayttaja(): Unit =
    // käyttäjällä A oikeus luoda lähetys ja lähetys validi joten luonti onnistuu
    val luoResult = mvc.perform(jsonPost(APIConstants.LUO_LAHETYS_PATH, getLahetys())
        .`with`(user("A").roles(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL.replace("ROLE_", ""))))
      .andExpect(status().isOk).andReturn()
    val luoLahetysResponse = objectMapper.readValue(luoResult.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[LuoLahetysSuccessResponseImpl])

    // käyttäjällä B katseluoikeus, mutta ei oikeuksia tähän lähetykseen joten tulee 403
    mvc.perform(MockMvcRequestBuilders
        .get(APIConstants.GET_LAHETYS_PATH.replace(APIConstants.LAHETYSTUNNISTE_PARAM_PLACEHOLDER, luoLahetysResponse.lahetysTunniste.toString))
        .`with`(user("B").roles(SecurityConstants.SECURITY_ROOLI_KATSELU_FULL.replace("ROLE_", "")))
        .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isForbidden)

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_KATSELU_FULL))
  @Test def testGetLahetysGone(): Unit =
    // tuntematon lähetystunniste johtaa 410-vastaukseen
    mvc.perform(MockMvcRequestBuilders
      .get(APIConstants.GET_LAHETYS_PATH.replace(APIConstants.LAHETYSTUNNISTE_PARAM_PLACEHOLDER, UUID.randomUUID().toString))
      .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isGone)

  @Test def testGetLahetysAllowed(): Unit =
    // luodaan lähetys ja saadaan tunniste
    val luoResult = mvc.perform(jsonPost(APIConstants.LUO_LAHETYS_PATH, getLahetys())
        .`with`(user("kayttaja").roles(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL.replace("ROLE_", ""))))
      .andExpect(status().isOk).andReturn()
    val lahetysTunniste = objectMapper.readValue(luoResult.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[LuoLahetysSuccessResponseImpl]).lahetysTunniste

    // käyttäjällä oikeus katsoa lähetyksia, ja on luonut tämän lähetyksen joten haku onnistuu
    val getResult = mvc.perform(MockMvcRequestBuilders
        .get(APIConstants.GET_LAHETYS_PATH.replace(APIConstants.LAHETYSTUNNISTE_PARAM_PLACEHOLDER, lahetysTunniste.toString))
        .`with`(user("kayttaja").roles(SecurityConstants.SECURITY_ROOLI_KATSELU_FULL.replace("ROLE_", "")))
        .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isOk).andReturn()

    val getLahetysResponse = objectMapper.readValue(getResult.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[PalautaLahetysSuccessResponse])
    Assertions.assertEquals(lahetysTunniste, getLahetysResponse.lahetysTunniste)

  /**
   * Testataan vastaanottajien haku
   */
  @WithAnonymousUser
  @Test def testGetVastaanottajatAnonymous(): Unit =
    // tuntematon käyttäjä ohjataan tunnistautumaan
    mvc.perform(MockMvcRequestBuilders
      .get(APIConstants.GET_VASTAANOTTAJAT_PATH.replace(APIConstants.LAHETYSTUNNISTE_PARAM_PLACEHOLDER, UUID.randomUUID().toString))
      .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isFound)

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL))
  @Test def testGetVastaanottajatNotAllowed(): Unit =
    // luodaan viesti
    val luoResult = mvc.perform(jsonPost(APIConstants.LUO_VIESTI_PATH, getViesti()))
      .andExpect(status().isOk()).andReturn()
    val luoViestiResponse = objectMapper.readValue(luoResult.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[LuoViestiSuccessResponseImpl])

    // mutta käyttäjällä ei katseluoikeutta joten tulee 403
    mvc.perform(MockMvcRequestBuilders
      .get(APIConstants.GET_VASTAANOTTAJAT_PATH.replace(APIConstants.LAHETYSTUNNISTE_PARAM_PLACEHOLDER, luoViestiResponse.lahetysTunniste.toString))
      .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isForbidden)

  @Test def testGetVastaanottajatNotAllowedEriKayttaja(): Unit =
    // luodaan viesti käyttäjän A toimesta
    val luoResult = mvc.perform(jsonPost(APIConstants.LUO_VIESTI_PATH, getViesti())
      .`with`(user("A").roles(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL.replace("ROLE_", ""))))
      .andExpect(status().isOk()).andReturn()
    val luoViestiResponse = objectMapper.readValue(luoResult.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[LuoViestiSuccessResponseImpl])

    // käyttäjällä B katseluoikeus, mutta ei oikeuksia tähän lähetykseen joten tulee 403
    mvc.perform(MockMvcRequestBuilders
      .get(APIConstants.GET_VASTAANOTTAJAT_PATH.replace(APIConstants.LAHETYSTUNNISTE_PARAM_PLACEHOLDER, luoViestiResponse.lahetysTunniste.toString))
      .accept(MediaType.APPLICATION_JSON_VALUE)
      .`with`(user("B").roles(SecurityConstants.SECURITY_ROOLI_KATSELU_FULL.replace("ROLE_", ""))))
      .andExpect(status().isForbidden)

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_KATSELU_FULL))
  @Test def testGetVastaanottajatGone(): Unit =
    // tuntematon lähetystunniste johtaa 410-vastaukseen
    mvc.perform(MockMvcRequestBuilders
      .get(APIConstants.GET_VASTAANOTTAJAT_PATH.replace(APIConstants.LAHETYSTUNNISTE_PARAM_PLACEHOLDER, UUID.randomUUID().toString))
      .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isGone)

  @Test def testGetVastaanottajatAllowed(): Unit =
    // luodaan viesti ja saadaan tunniste
    val viesti = getViesti()
    val luoResult = mvc.perform(jsonPost(APIConstants.LUO_VIESTI_PATH, viesti)
      .`with`(user("kayttaja").roles(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL.replace("ROLE_", ""))))
      .andExpect(status().isOk).andReturn()
    val lahetysTunniste = objectMapper.readValue(luoResult.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[LuoViestiSuccessResponseImpl]).lahetysTunniste

    // käyttäjällä oikeus katsoa lähetyksiä, ja on luonut tämän lähetyksen joten vastaanottajien haku onnistuu
    val getResult = mvc.perform(MockMvcRequestBuilders
      .get(APIConstants.GET_VASTAANOTTAJAT_PATH.replace(APIConstants.LAHETYSTUNNISTE_PARAM_PLACEHOLDER, lahetysTunniste.toString))
      .`with`(user("kayttaja").roles(SecurityConstants.SECURITY_ROOLI_KATSELU_FULL.replace("ROLE_", "")))
      .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isOk).andReturn()

    // tulos vastaa luotua viestiä
    val getVastaanottajatResponse = objectMapper.readValue(getResult.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[VastaanottajatSuccessResponse])
    Assertions.assertEquals(viesti.vastaanottajat.get.asScala.map(v => v.getSahkopostiOsoite.get), getVastaanottajatResponse.vastaanottajat.asScala.map(v => v.sahkoposti))

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL, SecurityConstants.SECURITY_ROOLI_KATSELU_FULL))
  @Test def testGetVastaanottajatSivutus(): Unit =
    val vastaanottajat1 = Seq(
      VastaanottajaImpl(Optional.empty(), Optional.of("vallu.vastaanottaja+success@example.com")),
      VastaanottajaImpl(Optional.empty(), Optional.of("veera.vastaanottaja+success@example.com")))
    val vastaanottajat2 = Seq(
      VastaanottajaImpl(Optional.empty(), Optional.of("ville.vastaanottaja+success@example.com")),
      VastaanottajaImpl(Optional.empty(), Optional.of("veksi.vastaanottaja+success@example.com")))

    // luodaan viesti ja saadaan tunniste
    val viesti = getViesti(vastaanottajat = vastaanottajat1.concat(vastaanottajat2).asJava)
    val luoResult = mvc.perform(jsonPost(APIConstants.LUO_VIESTI_PATH, viesti))
      .andExpect(status().isOk).andReturn()
    val lahetysTunniste = objectMapper.readValue(luoResult.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[LuoViestiSuccessResponseImpl]).lahetysTunniste

    // haetaan max 2 vastaanottajaa, saadaan vastaanottajat1
    val getResult = mvc.perform(MockMvcRequestBuilders
      .get(APIConstants.GET_VASTAANOTTAJAT_PATH.replace(APIConstants.LAHETYSTUNNISTE_PARAM_PLACEHOLDER, lahetysTunniste.toString) + s"?${APIConstants.ENINTAAN_PARAM_NAME}=2")
      .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isOk).andReturn()
    val getVastaanottajatResponse = objectMapper.readValue(getResult.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[VastaanottajatSuccessResponse])
    Assertions.assertEquals(vastaanottajat1.map(v => v.sahkopostiOsoite.get), getVastaanottajatResponse.vastaanottajat.asScala.map(v => v.sahkoposti))

    // haetaan seuraavat vastaanottajat, saadaan vastaanottajat2
    val getSeuraavatResult = mvc.perform(MockMvcRequestBuilders
      .get(getVastaanottajatResponse.seuraavat.get)
      .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isOk).andReturn()
    val getSeuraavatResponse = objectMapper.readValue(getSeuraavatResult.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[VastaanottajatSuccessResponse])
    Assertions.assertEquals(vastaanottajat2.map(v => v.sahkopostiOsoite.get), getSeuraavatResponse.vastaanottajat.asScala.map(v => v.sahkoposti))

    // vastaanottajia ei enää jäljellä
    Assertions.assertEquals(Optional.empty, getSeuraavatResponse.seuraavat)

  /**
   * Testataan liitteen luonti
   */
  @WithAnonymousUser
  @Test def testLuoLiiteAnonymous(): Unit =
    // tuntematon käyttäjä ohjataan tunnistautumaan
    mvc.perform(MockMvcRequestBuilders
        .multipart(APIConstants.LUO_LIITE_PATH)
        .file(MockMultipartFile("liite", "filename.txt", "text/plain", "sisältö".getBytes()))
        .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isFound)

  @WithMockUser(value = "kayttaja", authorities = Array())
  @Test def testLuoLiiteNotAllowed(): Unit =
    // vaatii VIESTINVALITYS_LAHETYS-oikeuden
    mvc.perform(MockMvcRequestBuilders
      .multipart(APIConstants.LUO_LIITE_PATH)
      .file(MockMultipartFile("liite", "filename.txt", "text/plain", "sisältö".getBytes()))
      .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isForbidden)

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL))
  @Test def testLuoLiiteVaaraParametrinNimi(): Unit =
    // käyttäjällä VIESTINVALITUS_LAHETYS-oikeus ja liite validi mutta parametrin nimi väärä
    val result = mvc.perform(MockMvcRequestBuilders
      .multipart(APIConstants.LUO_LIITE_PATH)
      .file(MockMultipartFile("vaaraNimi", "filename.txt", "text/plain", "sisältö".getBytes()))
      .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isBadRequest).andReturn()

    Assertions.assertEquals(LuoLiiteFailureResponseImpl(Seq(APIConstants.LIITE_VIRHE_LIITE_PUUTTUU).asJava),
      objectMapper.readValue(result.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[LuoLiiteFailureResponseImpl]))

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL))
  @Test def testLuoLiiteLiianIso(): Unit =
    // placeholder, tämä toiminnallisuus on toteutettu edge-funktiolla, joten sitä ei voi testata lokaalisti
    null

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL))
  @Test def testLuoLiiteAllowed(): Unit =
    // käyttäjällä VIESTINVALITUS_LAHETYS-oikeus ja liite validi joten luonti onnistuu
    val result = mvc.perform(MockMvcRequestBuilders
      .multipart(APIConstants.LUO_LIITE_PATH)
      .file(MockMultipartFile("liite", "filename.txt", "text/plain", "sisältö".getBytes()))
      .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isOk).andReturn()
    val response = objectMapper.readValue(result.getResponse.getContentAsString, classOf[LuoLiiteSuccessResponseImpl])

  /**
   * Testataan viestin luonti
   */
  @WithAnonymousUser
  @Test def testLuoViestiAnonymous(): Unit =
    // tuntematon käyttäjä ohjataan tunnistautumaan
    mvc.perform(jsonPost(APIConstants.LUO_VIESTI_PATH, getViesti()))
      .andExpect(status().isFound)

  @WithMockUser(value = "kayttaja", authorities = Array())
  @Test def testLuoViestiNotAllowed(): Unit =
    // vaatii VIESTINVALITYS_LAHETYS-oikeuden
    mvc.perform(jsonPost(APIConstants.LUO_VIESTI_PATH, getViesti()))
      .andExpect(status().isForbidden)

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL))
  @Test def testLuoViestiMalformedJson(): Unit =
    // ei validi json ei sallittu
    val result = mvc.perform(jsonPost(APIConstants.LUO_VIESTI_PATH, "tämä ei ole viesti-json-objekti"))
      .andExpect(status().isBadRequest).andReturn()

    Assertions.assertEquals(LuoViestiFailureResponseImpl(java.util.List.of(APIConstants.VIRHEELLINEN_VIESTI_JSON_VIRHE)),
      objectMapper.readValue(result.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[LuoViestiFailureResponseImpl]))

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL))
  @Test def testLuoViestiBadRequest(): Unit =
    // tyhjä otsikko ei (esimerkiksi) ole sallittu, muuten validointi testataan yksikkötesteillä
    val result = mvc.perform(jsonPost(APIConstants.LUO_VIESTI_PATH, getViesti().copy(otsikko = Optional.empty())))
      .andExpect(status().isBadRequest()).andReturn()

    Assertions.assertEquals(LuoViestiFailureResponseImpl(java.util.List.of(ViestiValidator.VALIDATION_OTSIKKO_TYHJA)),
      objectMapper.readValue(result.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[LuoViestiFailureResponseImpl]))

  @WithMockUser(value = "kayttaja", authorities=Array(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL))
  @Test def testLuoViestiAllowed(): Unit =
    // käyttäjällä VIESTINVALITUS_LAHETYS-oikeus ja viesti validi joten luonti onnistuu
    mvc.perform(jsonPost(APIConstants.LUO_VIESTI_PATH, getViesti()))
      .andExpect(status().isOk())

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL))
  @Test def testLuoViestiRateLimiter(): Unit =
    // tehdään korkean prioriteetin luontikutsuja niin että vastaanottajia syntyy yli aikaikkunassa sallittu määrä
    val count = Range.inclusive(1, APIConstants.PRIORITEETTI_KORKEA_RATELIMIT_VIESTEJA_AIKAIKKUNASSA + 1).foreach(count =>
      val request = mvc.perform(jsonPost(APIConstants.LUO_VIESTI_PATH, getViesti(
        vastaanottajat = java.util.List.of(VastaanottajaImpl(Optional.empty(), Optional.of("vallu.vastaanottaja+success@example.com"))),
        prioriteetti = Prioriteetti.KORKEA.toString.toLowerCase)
      ))

      // sallitun määrän puitteissa vastaus on 200
      if(count<=APIConstants.PRIORITEETTI_KORKEA_RATELIMIT_VIESTEJA_AIKAIKKUNASSA) request.andExpect(status().isOk)
      // ja sen jälkeen 429
      else request.andExpect(status().isTooManyRequests)
    )

  /**
   * Testataan viestin haku
   */
  @WithAnonymousUser
  @Test def testGetViestiAnonymous(): Unit =
    // tuntematon käyttäjä ohjataan tunnistautumaan
    mvc.perform(MockMvcRequestBuilders
      .get(APIConstants.GET_VIESTI_PATH.replace(APIConstants.VIESTITUNNISTE_PARAM_PLACEHOLDER, UUID.randomUUID().toString))
      .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isFound)

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL))
  @Test def testGetViestiNotAllowed(): Unit =
    // käyttäjällä oikeus luoda viesti ja viesti validi joten luonti onnistuu
    val luoResult = mvc.perform(jsonPost(APIConstants.LUO_VIESTI_PATH, getViesti()))
      .andExpect(status().isOk).andReturn()
    val luoViestiResponse = objectMapper.readValue(luoResult.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[LuoViestiSuccessResponseImpl])

    // mutta käyttäjällä ei katseluoikeutta joten tulee 403
    mvc.perform(MockMvcRequestBuilders
      .get(APIConstants.GET_VIESTI_PATH.replace(APIConstants.VIESTITUNNISTE_PARAM_PLACEHOLDER, luoViestiResponse.viestiTunniste.toString))
      .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isForbidden)

  @Test def testGetViestiNotAllowedEriKayttaja(): Unit =
    // käyttäjällä A oikeus luoda viesti ja viesti validi joten luonti onnistuu
    val luoResult = mvc.perform(jsonPost(APIConstants.LUO_VIESTI_PATH, getViesti())
      .`with`(user("A").roles(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL.replace("ROLE_", ""))))
      .andExpect(status().isOk).andReturn()
    val luoViestiResponse = objectMapper.readValue(luoResult.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[LuoViestiSuccessResponseImpl])

    // käyttäjällä B katseluoikeus, mutta ei oikeuksia tähän viestiin joten tulee 403
    mvc.perform(MockMvcRequestBuilders
      .get(APIConstants.GET_VIESTI_PATH.replace(APIConstants.VIESTITUNNISTE_PARAM_PLACEHOLDER, luoViestiResponse.viestiTunniste.toString))
      .`with`(user("B").roles(SecurityConstants.SECURITY_ROOLI_KATSELU_FULL.replace("ROLE_", "")))
      .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isForbidden)

  @WithMockUser(value = "kayttaja", authorities = Array(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL, SecurityConstants.SECURITY_ROOLI_KATSELU_FULL))
  @Test def testGetViestiGone(): Unit =
    // tuntematon viestitunniste johtaa 410-vastaukseen
    mvc.perform(MockMvcRequestBuilders
      .get(APIConstants.GET_VIESTI_PATH.replace(APIConstants.VIESTITUNNISTE_PARAM_PLACEHOLDER, UUID.randomUUID().toString))
      .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isGone)

  @Test def testGetViestiAllowed(): Unit =
    // luodaan viesti ja saadaan tunniste
    val luoResult = mvc.perform(jsonPost(APIConstants.LUO_VIESTI_PATH, getViesti())
      .`with`(user("kayttaja").roles(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL.replace("ROLE_", ""))))
      .andExpect(status().isOk).andReturn()
    val viestiTunniste = objectMapper.readValue(luoResult.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[LuoViestiSuccessResponseImpl]).viestiTunniste

    // käyttäjällä oikeus katsoa viestejä, ja on luonut tämän viestiin joten haku onnistuu
    val getResult = mvc.perform(MockMvcRequestBuilders
      .get(APIConstants.GET_VIESTI_PATH.replace(APIConstants.VIESTITUNNISTE_PARAM_PLACEHOLDER, viestiTunniste.toString))
      .`with`(user("kayttaja").roles(SecurityConstants.SECURITY_ROOLI_KATSELU_FULL.replace("ROLE_", "")))
      .accept(MediaType.APPLICATION_JSON_VALUE))
      .andExpect(status().isOk).andReturn()

    val getViestiResponse = objectMapper.readValue(getResult.getResponse.getContentAsString(StandardCharset.UTF_8), classOf[PalautaViestiSuccessResponse])
    Assertions.assertEquals(viestiTunniste, getViestiResponse.viestiTunniste)

  /**
   * Testataan viestin lähetys end-to-end, ts. että vastaanottajan tila päivittyy DELIVERY-tilaan. Tällöin:
   *  - liite on "skannattu" (lokaalista skannausta vain simuloidaan) onnistuneesti ja vastaanottaja siirretty
   *    odottamaan lähetystä (SKANNAUS->ODOTTAA)
   *  - vastaanottajalle on lähetetty viesti Localstackin SES:iin (ODOTTAA->LAHETYKSESSA->LAHETETTY)
   *  - SES:in lähettämä tilapäivitys on prosessoitu onnistuneesti (LAHETETTY->DELIVERY).
   */
  @Test def testLuoViestiEnd2End(): Unit =
    // luodaan liite
    val result = mvc.perform(MockMvcRequestBuilders
      .multipart(APIConstants.LUO_LIITE_PATH)
      .file(MockMultipartFile("liite", "filename.txt", "text/plain", "sisältö".getBytes()))
      .accept(MediaType.APPLICATION_JSON_VALUE)
      .`with`(user("kayttaja").roles(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL.replace("ROLE_", ""))))
      .andExpect(status().isOk).andReturn()
    val liiteTunniste = objectMapper.readValue(result.getResponse.getContentAsString, classOf[LuoLiiteSuccessResponseImpl]).liiteTunniste

    // luodaan viesti
    val luoViestiResult = mvc.perform(jsonPost(APIConstants.LUO_VIESTI_PATH, getViesti(liitteidenTunnisteet = Optional.of(java.util.List.of(liiteTunniste.toString))))
        .`with`(user("kayttaja").roles(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL.replace("ROLE_", ""))))
        .andExpect(status().isOk()).andReturn()
    val response = objectMapper.readValue(luoViestiResult.getResponse.getContentAsString, classOf[LuoViestiSuccessResponseImpl])

    // haetaan viestin ainoan vastaanottajan tila sekunnin välein kunnes tilassa DELIVERY
    try
      Await.ready(Future {
        var tila: String = null
        while (!VastaanottajanTila.DELIVERY.toString.equals(tila))
          Thread.sleep(1000)
          val vastaanottajaResult = mvc.perform(MockMvcRequestBuilders
              .get(APIConstants.GET_VASTAANOTTAJAT_PATH.replace(APIConstants.LAHETYSTUNNISTE_PARAM_PLACEHOLDER, response.lahetysTunniste.toString))
              .`with`(user("kayttaja").roles(SecurityConstants.SECURITY_ROOLI_KATSELU_FULL.replace("ROLE_", ""))))
            .andExpect(status().isOk()).andReturn()
          val vastaanottajaResponse = objectMapper.readValue(vastaanottajaResult.getResponse.getContentAsString, classOf[VastaanottajatSuccessResponse])

          tila = vastaanottajaResponse.vastaanottajat.get(0).tila
      }, 60.seconds)
    catch
      case e: Exception => Assertions.fail("Vastaanottaja ei muuttunut delivery-tilaan sallitussa ajassa")
}
