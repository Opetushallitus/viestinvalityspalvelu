package fi.vm.sade.viestinvalitys.resource;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import fi.vm.sade.viestinvalitys.ViestinvalitysServiceApiTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class LahetysSearchControllerTest extends ViestinvalitysServiceApiTest {

  @RegisterExtension
  static WireMockExtension virkailija =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @DynamicPropertySource
  static void overrideVirkailijaUrl(DynamicPropertyRegistry registry) {
    registry.add("host.virkailija", virkailija::baseUrl);
  }

  private static final String PARENT_ORGANISAATIO_OID = "1.2.246.562.10.11111111111";
  private static final String CHILD_ORGANISAATIO_OID = "1.2.246.562.10.22222222222";
  private static final String TOINEN_ORGANISAATIO_OID = "1.2.246.562.10.33333333333";
  private static final String LAHETTAJA_OID = "1.2.246.562.24.99999999999";

  @BeforeEach
  void setup() {
    clearDatabase();
  }

  private String insertLahetys(String otsikko) {
    var tunniste = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "INSERT INTO lahetykset "
            + "(tunniste, otsikko, lahettavapalvelu, lahettajansahkoposti, prioriteetti, omistaja, luotu, poistettava) "
            + "VALUES (?::uuid, ?, ?, ?, ?::prioriteetti, ?, now(), '2040-01-01 00:00:00'::timestamp)",
        tunniste,
        otsikko,
        "hakutesti-palvelu",
        "testi.lahettaja@opintopolku.fi",
        "NORMAALI",
        TEST_KAYTTAJA_OID);
    return tunniste;
  }

  private void insertViesti(
      String lahetysTunniste,
      String otsikko,
      String sisalto,
      String vastaanottajanSahkoposti,
      String lahettajanOid,
      String organisaatioOid) {
    jdbcTemplate.update(
        "INSERT INTO viestit (tunniste, lahetys_tunniste, otsikko, sisalto, sisallontyyppi, "
            + "kielet_fi, kielet_sv, kielet_en, prioriteetti, omistaja, luotu, "
            + "haku_otsikko, haku_sisalto, haku_kayttooikeudet, haku_vastaanottajat, "
            + "haku_lahettaja, haku_metadata, haku_lahettavapalvelu, haku_organisaatiot) "
            + "VALUES (?::uuid, ?::uuid, ?, ?, 'TEXT', true, false, false, 'NORMAALI'::prioriteetti, ?, now(), "
            + "to_tsvector('simple', ?), to_tsvector('simple', ?), '{}'::integer[], string_to_array(?, ','), "
            + "?, '{}'::varchar[], 'hakutesti-palvelu', string_to_array(?, ','))",
        UUID.randomUUID().toString(),
        lahetysTunniste,
        otsikko,
        sisalto,
        TEST_KAYTTAJA_OID,
        otsikko,
        sisalto,
        vastaanottajanSahkoposti,
        lahettajanOid,
        organisaatioOid);
  }

  private String insertLahetysWithViesti(
      String otsikko,
      String sisalto,
      String vastaanottajanSahkoposti,
      String lahettajanOid,
      String organisaatioOid) {
    String lahetysTunniste = insertLahetys(otsikko);
    insertViesti(
        lahetysTunniste, otsikko, sisalto, vastaanottajanSahkoposti, lahettajanOid, organisaatioOid);
    return lahetysTunniste;
  }

  @Test
  @UserKatselijaRaportoija
  void vastaanottajaFilterReturnsOnlyLahetyksetWithMatchingRecipient() throws Exception {
    insertLahetysWithViesti(
        "Osuma", "Sisältö", "matti.meikalainen@example.com", LAHETTAJA_OID, TOINEN_ORGANISAATIO_OID);
    insertLahetysWithViesti(
        "Ei osumaa", "Sisältö", "maija.mallikas@example.com", LAHETTAJA_OID, TOINEN_ORGANISAATIO_OID);

    mvc.perform(
            MockMvcRequestBuilders.get("/v1/lahetykset/lista")
                .param("vastaanottaja", "matti.meikalainen@example.com"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lahetykset.length()").value(1))
        .andExpect(jsonPath("$.lahetykset[0].otsikko").value("Osuma"));
  }

  @Test
  @UserKatselijaRaportoija
  void vastaanottajaFilterMatchesOnlyCompleteEmailAddress() throws Exception {
    insertLahetysWithViesti(
        "Osittainen", "Sisältö", "matti.meikalainen@example.com", LAHETTAJA_OID, TOINEN_ORGANISAATIO_OID);

    mvc.perform(
            MockMvcRequestBuilders.get("/v1/lahetykset/lista").param("vastaanottaja", "matti"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lahetykset.length()").value(0));
  }

  @Test
  @UserKatselijaRaportoija
  void viestiFilterMatchesSisaltoAndOtsikkoByWordPrefix() throws Exception {
    insertLahetysWithViesti(
        "Kissakerhon tiedote",
        "Kokous pidetään maanantaina",
        "a@example.com",
        LAHETTAJA_OID,
        TOINEN_ORGANISAATIO_OID);
    insertLahetysWithViesti(
        "Koirakerhon tiedote",
        "Ulkoilutus tiistaina",
        "b@example.com",
        LAHETTAJA_OID,
        TOINEN_ORGANISAATIO_OID);

    mvc.perform(MockMvcRequestBuilders.get("/v1/lahetykset/lista").param("viesti", "kissakerho"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lahetykset.length()").value(1))
        .andExpect(jsonPath("$.lahetykset[0].otsikko").value("Kissakerhon tiedote"));

    mvc.perform(MockMvcRequestBuilders.get("/v1/lahetykset/lista").param("viesti", "kokous"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lahetykset.length()").value(1))
        .andExpect(jsonPath("$.lahetykset[0].otsikko").value("Kissakerhon tiedote"));

    mvc.perform(MockMvcRequestBuilders.get("/v1/lahetykset/lista").param("viesti", "hiirenloukku"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lahetykset.length()").value(0));
  }

  @Test
  @UserKatselijaRaportoija
  void lahettajaFilterReturnsOnlyLahetyksetFromMatchingVirkailija() throws Exception {
    insertLahetysWithViesti(
        "Oma", "Sisältö", "a@example.com", LAHETTAJA_OID, TOINEN_ORGANISAATIO_OID);
    insertLahetysWithViesti(
        "Vieras", "Sisältö", "b@example.com", "1.2.246.562.24.88888888888", TOINEN_ORGANISAATIO_OID);

    mvc.perform(MockMvcRequestBuilders.get("/v1/lahetykset/lista").param("lahettaja", LAHETTAJA_OID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lahetykset.length()").value(1))
        .andExpect(jsonPath("$.lahetykset[0].otsikko").value("Oma"));
  }

  @Test
  @UserKatselijaRaportoija
  void organisaatioFilterIncludesChildOrganisationsFromOrganisaatioService() throws Exception {
    virkailija.stubFor(
        get(urlPathEqualTo("/organisaatio-service/api/" + PARENT_ORGANISAATIO_OID + "/childoids"))
            .willReturn(okJson("[\"" + CHILD_ORGANISAATIO_OID + "\"]")));
    insertLahetysWithViesti(
        "Lapsiorganisaation viesti", "Sisältö", "a@example.com", LAHETTAJA_OID, CHILD_ORGANISAATIO_OID);
    insertLahetysWithViesti(
        "Toisen organisaation viesti", "Sisältö", "b@example.com", LAHETTAJA_OID, TOINEN_ORGANISAATIO_OID);

    mvc.perform(
            MockMvcRequestBuilders.get("/v1/lahetykset/lista")
                .param("organisaatio", PARENT_ORGANISAATIO_OID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lahetykset.length()").value(1))
        .andExpect(jsonPath("$.lahetykset[0].otsikko").value("Lapsiorganisaation viesti"));
  }

  @Test
  @UserKatselijaRaportoija
  void viestiFilterSupportsTermsContainingApostrophe() throws Exception {
    insertLahetysWithViesti(
        "Tuotetiedote",
        "L'Oreal tuotteiden tilaus",
        "a@example.com",
        LAHETTAJA_OID,
        TOINEN_ORGANISAATIO_OID);

    mvc.perform(MockMvcRequestBuilders.get("/v1/lahetykset/lista").param("viesti", "l'oreal"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lahetykset.length()").value(1))
        .andExpect(jsonPath("$.lahetykset[0].otsikko").value("Tuotetiedote"));
  }

  @Test
  @UserKatselijaRaportoija
  void malformedOrganisaatioOidYieldsBadRequest() throws Exception {
    mvc.perform(
            MockMvcRequestBuilders.get("/v1/lahetykset/lista")
                .param("organisaatio", "not-an-oid/../../evil"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @UserKatselijaRaportoija
  void combinedFiltersRequireAllToMatchTheSameViesti() throws Exception {
    insertLahetysWithViesti(
        "Yhdistelmäosuma", "Sisältö", "matti@example.com", LAHETTAJA_OID, TOINEN_ORGANISAATIO_OID);
    insertLahetysWithViesti(
        "Vain vastaanottaja täsmää",
        "Sisältö",
        "matti@example.com",
        "1.2.246.562.24.88888888888",
        TOINEN_ORGANISAATIO_OID);

    mvc.perform(
            MockMvcRequestBuilders.get("/v1/lahetykset/lista")
                .param("vastaanottaja", "matti@example.com")
                .param("lahettaja", LAHETTAJA_OID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lahetykset.length()").value(1))
        .andExpect(jsonPath("$.lahetykset[0].otsikko").value("Yhdistelmäosuma"));
  }
}
