package fi.vm.sade.viestinvalitys.resource;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

  private String insertViesti(
      String lahetysTunniste,
      String otsikko,
      String sisalto,
      String vastaanottajanSahkoposti,
      String lahettajanOid,
      String organisaatioOid) {
    int katseluOikeus =
        jdbcTemplate.queryForObject(
            "INSERT INTO kayttooikeudet (organisaatio, oikeus) VALUES (?, 'APP_VIESTINVALITYS_KATSELU') "
                + "ON CONFLICT (organisaatio, oikeus) DO UPDATE SET organisaatio = EXCLUDED.organisaatio "
                + "RETURNING tunniste",
            Integer.class,
            OPH_ORGANISAATIO_OID);
    var tunniste = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "INSERT INTO viestit (tunniste, lahetys_tunniste, otsikko, sisalto, sisallontyyppi, "
            + "kielet_fi, kielet_sv, kielet_en, prioriteetti, omistaja, luotu, "
            + "haku_otsikko, haku_sisalto, haku_kayttooikeudet, haku_vastaanottajat, "
            + "haku_lahettaja, haku_metadata, haku_lahettavapalvelu, haku_organisaatiot) "
            + "VALUES (?::uuid, ?::uuid, ?, ?, 'TEXT', true, false, false, 'NORMAALI'::prioriteetti, ?, now(), "
            + "to_tsvector('simple', ?), to_tsvector('simple', ?), ARRAY[?]::integer[], string_to_array(?, ','), "
            + "?, '{}'::varchar[], 'hakutesti-palvelu', string_to_array(?, ','))",
        tunniste,
        lahetysTunniste,
        otsikko,
        sisalto,
        TEST_KAYTTAJA_OID,
        otsikko,
        sisalto,
        katseluOikeus,
        vastaanottajanSahkoposti,
        lahettajanOid,
        organisaatioOid);
    jdbcTemplate.update(
        "INSERT INTO viestit_kayttooikeudet (viesti_tunniste, kayttooikeus_tunniste) VALUES (?::uuid, ?)",
        tunniste,
        katseluOikeus);
    jdbcTemplate.update(
        "INSERT INTO lahetykset_kayttooikeudet (lahetys_tunniste, kayttooikeus_tunniste) "
            + "VALUES (?::uuid, ?) ON CONFLICT DO NOTHING",
        lahetysTunniste,
        katseluOikeus);
    return tunniste;
  }

  private void insertVastaanottaja(String viestiTunniste, String sahkoposti, String tila) {
    jdbcTemplate.update(
        "INSERT INTO vastaanottajat (tunniste, viesti_tunniste, nimi, sahkopostiosoite, tila, luotu, prioriteetti) "
            + "VALUES (?::uuid, ?::uuid, ?, ?, ?, now(), 'NORMAALI'::prioriteetti)",
        UUID.randomUUID().toString(),
        viestiTunniste,
        "Vastaan Ottaja",
        sahkoposti,
        tila);
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
  void lahetysPaginationReturnsAllRowsWithoutDuplicates() throws Exception {
    insertLahetysWithViesti("Eka", "Sisältö", "a@example.com", LAHETTAJA_OID, TOINEN_ORGANISAATIO_OID);
    insertLahetysWithViesti("Toka", "Sisältö", "b@example.com", LAHETTAJA_OID, TOINEN_ORGANISAATIO_OID);
    insertLahetysWithViesti("Kolmas", "Sisältö", "c@example.com", LAHETTAJA_OID, TOINEN_ORGANISAATIO_OID);

    var nahdyt = new java.util.HashSet<String>();
    @SuppressWarnings("unchecked")
    var eka =
        (java.util.Map<String, Object>) getJson(java.util.Map.class, "/v1/lahetykset/lista?enintaan=2");
    ((java.util.List<java.util.Map<String, Object>>) eka.get("lahetykset"))
        .forEach(l -> nahdyt.add((String) l.get("otsikko")));
    assertEquals(2, nahdyt.size());

    @SuppressWarnings("unchecked")
    var toka =
        (java.util.Map<String, Object>)
            getJson(
                java.util.Map.class,
                "/v1/lahetykset/lista?enintaan=2&alkaen={alkaen}",
                eka.get("seuraavatAlkaen"));
    ((java.util.List<java.util.Map<String, Object>>) toka.get("lahetykset"))
        .forEach(l -> nahdyt.add((String) l.get("otsikko")));
    assertEquals(3, nahdyt.size());
  }

  @Test
  @UserKatselijaRaportoija
  void tilaFilterMapsRaportointiTilaToVastaanottajanTilat() throws Exception {
    String lahetysTunniste = insertLahetys("Tilatesti");
    String viestiTunniste =
        insertViesti(
            lahetysTunniste, "Tilatesti", "Sisältö", "a@example.com", LAHETTAJA_OID, TOINEN_ORGANISAATIO_OID);
    insertVastaanottaja(viestiTunniste, "bounce@example.com", "BOUNCE");
    insertVastaanottaja(viestiTunniste, "send@example.com", "SEND");
    insertVastaanottaja(viestiTunniste, "delivery@example.com", "DELIVERY");

    mvc.perform(
            MockMvcRequestBuilders.get("/v1/lahetykset/{tunniste}/vastaanottajat", lahetysTunniste)
                .param("tila", "epaonnistui"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.vastaanottajat.length()").value(1))
        .andExpect(jsonPath("$.vastaanottajat[0].sahkoposti").value("bounce@example.com"));

    mvc.perform(
            MockMvcRequestBuilders.get("/v1/lahetykset/{tunniste}/vastaanottajat", lahetysTunniste)
                .param("tila", "kesken"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.vastaanottajat.length()").value(1))
        .andExpect(jsonPath("$.vastaanottajat[0].sahkoposti").value("send@example.com"));

    mvc.perform(
            MockMvcRequestBuilders.get("/v1/lahetykset/{tunniste}/vastaanottajat", lahetysTunniste)
                .param("tila", "valmis"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.vastaanottajat.length()").value(1))
        .andExpect(jsonPath("$.vastaanottajat[0].sahkoposti").value("delivery@example.com"));
  }

  @Test
  @UserKatselijaRaportoija
  void invalidTilaYieldsBadRequest() throws Exception {
    String lahetysTunniste = insertLahetys("Tilatesti");

    mvc.perform(
            MockMvcRequestBuilders.get("/v1/lahetykset/{tunniste}/vastaanottajat", lahetysTunniste)
                .param("tila", "BOUNCE"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @UserKatselijaRaportoija
  void vastaanottajaFilterOnVastaanottajatMatchesOnlyExactEmail() throws Exception {
    String lahetysTunniste = insertLahetys("Sähköpostitesti");
    String viestiTunniste =
        insertViesti(
            lahetysTunniste, "Sähköpostitesti", "Sisältö", "a@example.com", LAHETTAJA_OID, TOINEN_ORGANISAATIO_OID);
    insertVastaanottaja(viestiTunniste, "matti@example.com", "DELIVERY");
    insertVastaanottaja(viestiTunniste, "matti.pitkanimi@example.com", "DELIVERY");

    mvc.perform(
            MockMvcRequestBuilders.get("/v1/lahetykset/{tunniste}/vastaanottajat", lahetysTunniste)
                .param("vastaanottaja", "matti@example.com"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.vastaanottajat.length()").value(1))
        .andExpect(jsonPath("$.vastaanottajat[0].sahkoposti").value("matti@example.com"));
  }

  @Test
  @UserKatselijaRaportoija
  void organisaatioFilterOnVastaanottajatIncludesChildOrganisations() throws Exception {
    virkailija.stubFor(
        get(urlPathEqualTo("/organisaatio-service/api/" + PARENT_ORGANISAATIO_OID + "/childoids"))
            .willReturn(okJson("[\"" + CHILD_ORGANISAATIO_OID + "\"]")));
    String lahetysTunniste = insertLahetys("Organisaatiotesti");
    String lapsiViesti =
        insertViesti(
            lahetysTunniste, "Lapsi", "Sisältö", "a@example.com", LAHETTAJA_OID, CHILD_ORGANISAATIO_OID);
    String toinenViesti =
        insertViesti(
            lahetysTunniste, "Toinen", "Sisältö", "b@example.com", LAHETTAJA_OID, TOINEN_ORGANISAATIO_OID);
    insertVastaanottaja(lapsiViesti, "lapsi@example.com", "DELIVERY");
    insertVastaanottaja(toinenViesti, "toinen@example.com", "DELIVERY");

    mvc.perform(
            MockMvcRequestBuilders.get("/v1/lahetykset/{tunniste}/vastaanottajat", lahetysTunniste)
                .param("organisaatio", PARENT_ORGANISAATIO_OID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.vastaanottajat.length()").value(1))
        .andExpect(jsonPath("$.vastaanottajat[0].sahkoposti").value("lapsi@example.com"));
  }

  @Test
  @UserKatselijaRaportoija
  void vastaanottajatPaginationReturnsAllRowsWithoutDuplicates() throws Exception {
    String lahetysTunniste = insertLahetys("Sivutustesti");
    String viestiTunniste =
        insertViesti(
            lahetysTunniste, "Sivutustesti", "Sisältö", "a@example.com", LAHETTAJA_OID, TOINEN_ORGANISAATIO_OID);
    insertVastaanottaja(viestiTunniste, "eka@example.com", "DELIVERY");
    insertVastaanottaja(viestiTunniste, "toka@example.com", "DELIVERY");
    insertVastaanottaja(viestiTunniste, "kolmas@example.com", "DELIVERY");

    var nahdyt = new java.util.HashSet<String>();
    @SuppressWarnings("unchecked")
    var eka =
        (java.util.Map<String, Object>)
            getJson(
                java.util.Map.class,
                "/v1/lahetykset/{tunniste}/vastaanottajat?enintaan=2",
                lahetysTunniste);
    ((java.util.List<java.util.Map<String, Object>>) eka.get("vastaanottajat"))
        .forEach(v -> nahdyt.add((String) v.get("sahkoposti")));
    assertEquals(2, nahdyt.size());

    @SuppressWarnings("unchecked")
    var toka =
        (java.util.Map<String, Object>)
            getJson(
                java.util.Map.class,
                "/v1/lahetykset/{tunniste}/vastaanottajat?enintaan=2&alkaen={alkaen}",
                lahetysTunniste,
                eka.get("seuraavatAlkaen"));
    ((java.util.List<java.util.Map<String, Object>>) toka.get("vastaanottajat"))
        .forEach(v -> nahdyt.add((String) v.get("sahkoposti")));
    assertEquals(3, nahdyt.size());
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
