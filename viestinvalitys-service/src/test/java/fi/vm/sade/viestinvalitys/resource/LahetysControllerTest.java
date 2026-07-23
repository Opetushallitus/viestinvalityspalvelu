package fi.vm.sade.viestinvalitys.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fi.vm.sade.viestinvalitys.ViestinvalitysServiceApiTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class LahetysControllerTest extends ViestinvalitysServiceApiTest {

  @BeforeEach
  void setup() {
    clearDatabase();
  }

  private String insertLahetys(String otsikko, String palvelu) {
    var tunniste = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "INSERT INTO lahetykset "
            + "(tunniste, otsikko, lahettavapalvelu, lahettajansahkoposti, prioriteetti, omistaja, luotu, poistettava) "
            + "VALUES (?::uuid, ?, ?, ?, ?::prioriteetti, ?, now(), '2040-01-01 00:00:00'::timestamp)",
        tunniste,
        otsikko,
        palvelu,
        "testi.lahettaja@opintopolku.fi",
        "NORMAALI",
        TEST_KAYTTAJA_OID);
    return tunniste;
  }

  @Test
  void listingServicesRequiresAuthentication() throws Exception {
    mvc.perform(get("/v1/palvelut")).andExpect(status().is3xxRedirection());
  }

  @Test
  @UserKatselijaRaportoija
  void listingServicesReturnsEmptyArrayWhenDatabaseIsEmpty() throws Exception {
    mvc.perform(get("/v1/palvelut"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @UserKatselijaRaportoija
  void listingServicesReturnsDistinctPalveluFromDatabase() throws Exception {
    insertLahetys("Otsikko A", "Palvelu-X");
    insertLahetys("Otsikko B", "Palvelu-X");

    mvc.perform(get("/v1/palvelut"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0]").value("Palvelu-X"));
  }

  @Test
  @UserPaakayttaja
  void paakayttajaSeesAllLahetykset() throws Exception {
    insertLahetys("Pääkäyttäjän näkymä", "Palvelu-Y");

    mvc.perform(get("/v1/lahetykset/lista"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lahetykset.length()").value(1))
        .andExpect(jsonPath("$.lahetykset[0].otsikko").value("Pääkäyttäjän näkymä"))
        .andExpect(jsonPath("$.lahetykset[0].lahettavaPalvelu").value("Palvelu-Y"));
  }

  @Test
  @UserKatselijaRaportoija
  void malformedLahetysTunnisteYieldsBadRequest() throws Exception {
    mvc.perform(get("/v1/lahetykset/{tunniste}", "not-a-uuid"))
        .andExpect(status().isBadRequest());
  }

  private static final String VALID_LAHETYS_JSON =
      """
      {
        "otsikko": "E2E lahetys",
        "lahettavaPalvelu": "e2e-test",
        "lahettaja": { "nimi": "E2E Tester", "sahkopostiOsoite": "noreply@opintopolku.fi" },
        "prioriteetti": "normaali",
        "sailytysaika": 10
      }
      """;

  @Test
  void creatingLahetysRequiresAuthentication() throws Exception {
    mvc.perform(post("/v1/lahetykset").contentType(MediaType.APPLICATION_JSON).content(VALID_LAHETYS_JSON))
        .andExpect(status().is3xxRedirection());
  }

  @Test
  @UserKatselijaRaportoija
  void creatingLahetysWithoutSendRightsIsForbidden() throws Exception {
    mvc.perform(post("/v1/lahetykset").contentType(MediaType.APPLICATION_JSON).content(VALID_LAHETYS_JSON))
        .andExpect(status().isForbidden());
  }

  @Test
  @UserLahettaja
  void creatingLahetysPersistsAndReturnsTunniste() throws Exception {
    mvc.perform(post("/v1/lahetykset").contentType(MediaType.APPLICATION_JSON).content(VALID_LAHETYS_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lahetysTunniste").isString());

    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM lahetykset "
                + "WHERE otsikko = ? AND lahettavapalvelu = ? AND omistaja = ? AND prioriteetti = 'NORMAALI'::prioriteetti",
            Integer.class,
            "E2E lahetys",
            "e2e-test",
            TEST_KAYTTAJA_OID);
    assertEquals(1, count);
  }

  @Test
  @UserLahettaja
  void creatingInvalidLahetysYieldsBadRequest() throws Exception {
    // missing otsikko, lahettaja, prioriteetti and sailytysaika
    mvc.perform(
            post("/v1/lahetykset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"lahettavaPalvelu\": \"e2e-test\" }"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.validointiVirheet").isArray());
  }

  @Test
  void creatingViestiRequiresAuthentication() throws Exception {
    mvc.perform(post("/v1/viestit").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().is3xxRedirection());
  }

  @Test
  @UserKatselijaRaportoija
  void creatingViestiWithoutSendRightsIsForbidden() throws Exception {
    mvc.perform(post("/v1/viestit").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @UserLahettaja
  void creatingViestiForExistingLahetysPersistsAndReturnsTunnisteet() throws Exception {
    String lahetysTunniste = insertLahetys("E2E lahetys", "e2e-test");
    String viestiJson =
        """
        {
          "otsikko": "E2E viesti",
          "sisalto": "Tämä on E2E-testiviesti.",
          "sisallonTyyppi": "text",
          "vastaanottajat": [ { "nimi": "Vastaan Ottaja", "sahkopostiOsoite": "vastaanottaja@example.com" } ],
          "lahetysTunniste": "%s",
          "kayttooikeusRajoitukset": [ { "oikeus": "APP_OIKEUS", "organisaatio": "%s" } ]
        }
        """
            .formatted(lahetysTunniste, OPH_ORGANISAATIO_OID);

    mvc.perform(post("/v1/viestit").contentType(MediaType.APPLICATION_JSON).content(viestiJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.viestiTunniste").isString())
        .andExpect(jsonPath("$.lahetysTunniste").value(lahetysTunniste));

    Integer viestit =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM viestit WHERE lahetys_tunniste = ?::uuid AND otsikko = ? AND sisallontyyppi = 'TEXT'",
            Integer.class,
            lahetysTunniste,
            "E2E viesti");
    assertEquals(1, viestit);
    Integer vastaanottajat =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM vastaanottajat WHERE sahkopostiosoite = ? AND tila = 'ODOTTAA'",
            Integer.class,
            "vastaanottaja@example.com");
    assertEquals(1, vastaanottajat);
  }

  @Test
  @UserLahettaja
  void creatingViestiWithoutLahetysCreatesOwnLahetys() throws Exception {
    String viestiJson =
        """
        {
          "otsikko": "Standalone viesti",
          "sisalto": "Sisältö",
          "sisallonTyyppi": "text",
          "vastaanottajat": [ { "nimi": "Vastaan Ottaja", "sahkopostiOsoite": "vo@example.com" } ],
          "lahettavaPalvelu": "e2e-test",
          "lahettaja": { "nimi": "Tester", "sahkopostiOsoite": "noreply@opintopolku.fi" },
          "prioriteetti": "normaali",
          "sailytysaika": 10,
          "kayttooikeusRajoitukset": [ { "oikeus": "APP_OIKEUS", "organisaatio": "%s" } ]
        }
        """
            .formatted(OPH_ORGANISAATIO_OID);

    mvc.perform(post("/v1/viestit").contentType(MediaType.APPLICATION_JSON).content(viestiJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.viestiTunniste").isString())
        .andExpect(jsonPath("$.lahetysTunniste").isString());

    Integer lahetykset =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM lahetykset WHERE otsikko = ? AND lahettavapalvelu = ? AND omistaja = ?",
            Integer.class,
            "Standalone viesti",
            "e2e-test",
            TEST_KAYTTAJA_OID);
    assertEquals(1, lahetykset);
  }

  @Test
  @UserLahettaja
  void creatingInvalidViestiYieldsBadRequest() throws Exception {
    // missing otsikko, sisalto, sisallonTyyppi and vastaanottajat
    mvc.perform(
            post("/v1/viestit")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"lahettavaPalvelu\": \"e2e-test\" }"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.validointiVirheet").isArray());
  }

  @Test
  @UserLahettaja
  void repeatedIdempotencyKeyReturnsExistingViestiWithoutDuplicating() throws Exception {
    String key = "e2e-idem-" + UUID.randomUUID();
    String viestiJson =
        """
        {
          "otsikko": "Idempotentti viesti",
          "sisalto": "Sisältö",
          "sisallonTyyppi": "text",
          "vastaanottajat": [ { "nimi": "Vastaan Ottaja", "sahkopostiOsoite": "idem@example.com" } ],
          "lahettavaPalvelu": "e2e-test",
          "lahettaja": { "nimi": "Tester", "sahkopostiOsoite": "noreply@opintopolku.fi" },
          "prioriteetti": "normaali",
          "sailytysaika": 10,
          "kayttooikeusRajoitukset": [ { "oikeus": "APP_OIKEUS", "organisaatio": "%s" } ],
          "idempotencyKey": "%s"
        }
        """
            .formatted(OPH_ORGANISAATIO_OID, key);

    mvc.perform(post("/v1/viestit").contentType(MediaType.APPLICATION_JSON).content(viestiJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.viestiTunniste").isString());
    // repeat with the same key
    mvc.perform(post("/v1/viestit").contentType(MediaType.APPLICATION_JSON).content(viestiJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.viestiTunniste").isString());

    // only one Viesti (and one Vastaanottaja) was created despite two requests
    Integer viestit =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM viestit WHERE idempotency_key = ?", Integer.class, key);
    assertEquals(1, viestit);
    Integer vastaanottajat =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM vastaanottajat WHERE sahkopostiosoite = ?",
            Integer.class,
            "idem@example.com");
    assertEquals(1, vastaanottajat);
  }

  private static String korkeaPrioriteettiViestiJson(String osoite) {
    return """
        {
          "otsikko": "Korkean prioriteetin viesti",
          "sisalto": "Sisältö",
          "sisallonTyyppi": "text",
          "vastaanottajat": [ { "nimi": "Vastaan Ottaja", "sahkopostiOsoite": "%s" } ],
          "lahettavaPalvelu": "e2e-test",
          "lahettaja": { "nimi": "Tester", "sahkopostiOsoite": "noreply@opintopolku.fi" },
          "prioriteetti": "korkea",
          "sailytysaika": 10,
          "kayttooikeusRajoitukset": [ { "oikeus": "APP_OIKEUS", "organisaatio": "%s" } ]
        }
        """
        .formatted(osoite, OPH_ORGANISAATIO_OID);
  }

  @Test
  @UserLahettaja
  void tooManyHighPriorityViestitYieldsTooManyRequests() throws Exception {
    // limit is PRIORITEETTI_KORKEA_RATELIMIT_VIESTEJA_AIKAIKKUNASSA (=5) within the 5s window
    for (int i = 0; i < 5; i++) {
      mvc.perform(
              post("/v1/viestit")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(korkeaPrioriteettiViestiJson("korkea" + i + "@example.com")))
          .andExpect(status().isOk());
    }
    mvc.perform(
            post("/v1/viestit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(korkeaPrioriteettiViestiJson("korkea-yli@example.com")))
        .andExpect(status().isTooManyRequests());
  }
}
