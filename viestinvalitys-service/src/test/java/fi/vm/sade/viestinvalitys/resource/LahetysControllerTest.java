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
}
