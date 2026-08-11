package fi.vm.sade.viestinvalitys.resource;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import fi.vm.sade.viestinvalitys.ViestinvalitysServiceApiTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class LahetysAuthorizationTest extends ViestinvalitysServiceApiTest {

  @RegisterExtension
  static WireMockExtension virkailija =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("host.virkailija", virkailija::baseUrl);
    registry.add("viestinvalitys.features.downloadViesti.enabled", () -> "true");
    registry.add("aws.s3.endpoint", () -> "http://localhost:4566");
    registry.add("aws.s3.path-style-access", () -> "true");
    registry.add("aws.region", () -> "us-east-1");
    registry.add("attachments.bucket.name", () -> "attachments");
  }

  private static final String TOISEN_VIRKAILIJAN_OID = "1.2.246.562.24.11111111111";
  private static final String RAJATTU_ORGANISAATIO_OID = "1.2.246.562.10.44444444444";
  private static final String PARENT_ORGANISAATIO_OID = "1.2.246.562.10.55555555555";
  private static final String CHILD_ORGANISAATIO_OID = "1.2.246.562.10.66666666666";
  private static final String RAJATTU_OIKEUS = "APP_HAKEMUS_CRUD";

  @BeforeEach
  void setup() {
    clearDatabase();
  }

  private int insertKayttooikeus(String oikeus, String organisaatio) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO kayttooikeudet (organisaatio, oikeus) VALUES (?, ?) "
            + "ON CONFLICT (organisaatio, oikeus) DO UPDATE SET organisaatio = EXCLUDED.organisaatio "
            + "RETURNING tunniste",
        Integer.class,
        organisaatio,
        oikeus);
  }

  private String insertLahetys(String otsikko, String omistaja) {
    var tunniste = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "INSERT INTO lahetykset "
            + "(tunniste, otsikko, lahettavapalvelu, lahettajansahkoposti, prioriteetti, omistaja, luotu, poistettava) "
            + "VALUES (?::uuid, ?, ?, ?, ?::prioriteetti, ?, now(), '2040-01-01 00:00:00'::timestamp)",
        tunniste,
        otsikko,
        "authtesti-palvelu",
        "testi.lahettaja@opintopolku.fi",
        "NORMAALI",
        omistaja);
    return tunniste;
  }

  private String insertViesti(String lahetysTunniste, String omistaja, int kayttooikeusTunniste) {
    var tunniste = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "INSERT INTO viestit (tunniste, lahetys_tunniste, otsikko, sisalto, sisallontyyppi, "
            + "kielet_fi, kielet_sv, kielet_en, prioriteetti, omistaja, luotu, "
            + "haku_otsikko, haku_sisalto, haku_kayttooikeudet, haku_vastaanottajat, "
            + "haku_lahettaja, haku_metadata, haku_lahettavapalvelu, haku_organisaatiot) "
            + "VALUES (?::uuid, ?::uuid, 'Otsikko', 'Sisältö', 'TEXT', true, false, false, "
            + "'NORMAALI'::prioriteetti, ?, now(), to_tsvector('simple', 'Otsikko'), "
            + "to_tsvector('simple', 'Sisältö'), ARRAY[?]::integer[], '{}'::varchar[], "
            + "null, '{}'::varchar[], 'authtesti-palvelu', '{}'::varchar[])",
        tunniste,
        lahetysTunniste,
        omistaja,
        kayttooikeusTunniste);
    jdbcTemplate.update(
        "INSERT INTO viestit_kayttooikeudet (viesti_tunniste, kayttooikeus_tunniste) VALUES (?::uuid, ?)",
        tunniste,
        kayttooikeusTunniste);
    jdbcTemplate.update(
        "INSERT INTO lahetykset_kayttooikeudet (lahetys_tunniste, kayttooikeus_tunniste) "
            + "VALUES (?::uuid, ?) ON CONFLICT DO NOTHING",
        lahetysTunniste,
        kayttooikeusTunniste);
    return tunniste;
  }

  private void insertVastaanottaja(String viestiTunniste, String sahkoposti) {
    jdbcTemplate.update(
        "INSERT INTO vastaanottajat (tunniste, viesti_tunniste, nimi, sahkopostiosoite, tila, luotu, prioriteetti) "
            + "VALUES (?::uuid, ?::uuid, 'Vastaan Ottaja', ?, 'DELIVERY', now(), 'NORMAALI'::prioriteetti)",
        UUID.randomUUID().toString(),
        viestiTunniste,
        sahkoposti);
  }

  private String lahetysRestrictedToAnotherOrganisation() {
    int oikeus = insertKayttooikeus(RAJATTU_OIKEUS, RAJATTU_ORGANISAATIO_OID);
    String lahetysTunniste = insertLahetys("Rajattu lähetys", TOISEN_VIRKAILIJAN_OID);
    String viestiTunniste = insertViesti(lahetysTunniste, TOISEN_VIRKAILIJAN_OID, oikeus);
    insertVastaanottaja(viestiTunniste, "rajattu@example.com");
    return lahetysTunniste;
  }

  @Test
  @UserKatselijaRaportoija
  void katselijaCannotViewLahetysRestrictedToAnotherOrganisation() throws Exception {
    String lahetysTunniste = lahetysRestrictedToAnotherOrganisation();

    mvc.perform(get("/v1/lahetykset/{tunniste}", lahetysTunniste))
        .andExpect(status().isForbidden());
  }

  @Test
  @UserKatselijaRaportoija
  void katselijaCannotViewMassaviestiRestrictedToAnotherOrganisation() throws Exception {
    String lahetysTunniste = lahetysRestrictedToAnotherOrganisation();

    mvc.perform(get("/v1/massaviesti/{tunniste}", lahetysTunniste))
        .andExpect(status().isForbidden());
  }

  @Test
  @UserKatselijaRaportoija
  void katselijaCannotViewViestiRestrictedToAnotherOrganisation() throws Exception {
    int oikeus = insertKayttooikeus(RAJATTU_OIKEUS, RAJATTU_ORGANISAATIO_OID);
    String lahetysTunniste = insertLahetys("Rajattu lähetys", TOISEN_VIRKAILIJAN_OID);
    String viestiTunniste = insertViesti(lahetysTunniste, TOISEN_VIRKAILIJAN_OID, oikeus);

    mvc.perform(get("/v1/viesti/{tunniste}", viestiTunniste))
        .andExpect(status().isForbidden());
  }

  @Test
  @UserKatselijaRaportoija
  void katselijaCannotListVastaanottajatOfLahetysRestrictedToAnotherOrganisation() throws Exception {
    String lahetysTunniste = lahetysRestrictedToAnotherOrganisation();

    mvc.perform(get("/v1/lahetykset/{tunniste}/vastaanottajat", lahetysTunniste))
        .andExpect(status().isForbidden());
  }

  @Test
  @UserKatselijaRaportoija
  void katselijaCannotDownloadViestiRestrictedToAnotherOrganisation() throws Exception {
    int oikeus = insertKayttooikeus(RAJATTU_OIKEUS, RAJATTU_ORGANISAATIO_OID);
    String lahetysTunniste = insertLahetys("Rajattu lähetys", TOISEN_VIRKAILIJAN_OID);
    String viestiTunniste = insertViesti(lahetysTunniste, TOISEN_VIRKAILIJAN_OID, oikeus);

    mvc.perform(get("/v1/download/viesti").param("viestiTunniste", viestiTunniste))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(
      username = TEST_KAYTTAJA_OID,
      authorities = {"APP_VIESTINVALITYS_KATSELU"})
  void katselijaWithoutOrganisaatioSeesNoLahetyksetInSearch() throws Exception {
    lahetysRestrictedToAnotherOrganisation();

    mvc.perform(get("/v1/lahetykset/lista"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lahetykset.length()").value(0));
  }

  @Test
  @UserKatselijaRaportoija
  void katselijaDoesNotSeeLahetysRestrictedToAnotherOrganisationInSearch() throws Exception {
    lahetysRestrictedToAnotherOrganisation();

    mvc.perform(get("/v1/lahetykset/lista"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lahetykset.length()").value(0));
  }

  @Test
  @UserKatselijaRaportoija
  void vastaanottajatContainsOnlyViestitUserHasRightsTo() throws Exception {
    int omaOikeus = insertKayttooikeus("APP_VIESTINVALITYS_KATSELU", OPH_ORGANISAATIO_OID);
    int vierasOikeus = insertKayttooikeus(RAJATTU_OIKEUS, RAJATTU_ORGANISAATIO_OID);
    String lahetysTunniste = insertLahetys("Sekalähetys", TOISEN_VIRKAILIJAN_OID);
    String omaViesti = insertViesti(lahetysTunniste, TOISEN_VIRKAILIJAN_OID, omaOikeus);
    String vierasViesti = insertViesti(lahetysTunniste, TOISEN_VIRKAILIJAN_OID, vierasOikeus);
    insertVastaanottaja(omaViesti, "oma@example.com");
    insertVastaanottaja(vierasViesti, "vieras@example.com");

    mvc.perform(get("/v1/lahetykset/{tunniste}/vastaanottajat", lahetysTunniste))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.vastaanottajat.length()").value(1))
        .andExpect(jsonPath("$.vastaanottajat[0].sahkoposti").value("oma@example.com"));
  }

  @Test
  @UserKatselijaRaportoija
  void omistajaCanViewOwnLahetysWithoutMatchingKayttooikeus() throws Exception {
    int oikeus = insertKayttooikeus(RAJATTU_OIKEUS, RAJATTU_ORGANISAATIO_OID);
    String lahetysTunniste = insertLahetys("Oma lähetys", TEST_KAYTTAJA_OID);
    insertViesti(lahetysTunniste, TEST_KAYTTAJA_OID, oikeus);

    mvc.perform(get("/v1/lahetykset/{tunniste}", lahetysTunniste))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.otsikko").value("Oma lähetys"));
  }

  @Test
  @UserKatselijaRaportoija
  void katselijaWithMatchingKayttooikeusCanViewLahetys() throws Exception {
    int oikeus = insertKayttooikeus("APP_VIESTINVALITYS_KATSELU", OPH_ORGANISAATIO_OID);
    String lahetysTunniste = insertLahetys("Sallittu lähetys", TOISEN_VIRKAILIJAN_OID);
    insertViesti(lahetysTunniste, TOISEN_VIRKAILIJAN_OID, oikeus);

    mvc.perform(get("/v1/lahetykset/{tunniste}", lahetysTunniste))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.otsikko").value("Sallittu lähetys"));
  }

  @Test
  @WithMockUser(
      username = TEST_KAYTTAJA_OID,
      authorities = {
        "APP_VIESTINVALITYS_KATSELU_" + PARENT_ORGANISAATIO_OID,
        RAJATTU_OIKEUS + "_" + PARENT_ORGANISAATIO_OID
      })
  void parentOrganisaatioRightGrantsAccessToChildRestrictedLahetys() throws Exception {
    virkailija.stubFor(
        com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/organisaatio-service/api/" + CHILD_ORGANISAATIO_OID + "/parentoids"))
            .willReturn(okJson("[\"" + PARENT_ORGANISAATIO_OID + "\"]")));
    int oikeus = insertKayttooikeus(RAJATTU_OIKEUS, CHILD_ORGANISAATIO_OID);
    String lahetysTunniste = insertLahetys("Lapsiorganisaation lähetys", TOISEN_VIRKAILIJAN_OID);
    insertViesti(lahetysTunniste, TOISEN_VIRKAILIJAN_OID, oikeus);

    mvc.perform(get("/v1/lahetykset/{tunniste}", lahetysTunniste))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.otsikko").value("Lapsiorganisaation lähetys"));
  }

  @Test
  @UserPaakayttaja
  void paakayttajaCanViewLahetysRestrictedToAnotherOrganisation() throws Exception {
    String lahetysTunniste = lahetysRestrictedToAnotherOrganisation();

    mvc.perform(get("/v1/lahetykset/{tunniste}", lahetysTunniste))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.otsikko").value("Rajattu lähetys"));
  }
}
