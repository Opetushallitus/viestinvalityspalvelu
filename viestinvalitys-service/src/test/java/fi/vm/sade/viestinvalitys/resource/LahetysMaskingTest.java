package fi.vm.sade.viestinvalitys.resource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fi.vm.sade.viestinvalitys.ViestinvalitysServiceApiTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LahetysMaskingTest extends ViestinvalitysServiceApiTest {

  private static final String TOISEN_VIRKAILIJAN_OID = "1.2.246.562.24.11111111111";
  private static final String SALAISUUS = "https://example.com/kutsu/token-ABC123";
  private static final String MASKI = "<linkki piilotettu>";

  @BeforeEach
  void setup() {
    clearDatabase();
  }

  private int insertKatseluOikeus() {
    return jdbcTemplate.queryForObject(
        "INSERT INTO kayttooikeudet (organisaatio, oikeus) VALUES (?, 'APP_VIESTINVALITYS_KATSELU') "
            + "ON CONFLICT (organisaatio, oikeus) DO UPDATE SET organisaatio = EXCLUDED.organisaatio "
            + "RETURNING tunniste",
        Integer.class,
        OPH_ORGANISAATIO_OID);
  }

  private String insertLahetys(String otsikko) {
    var tunniste = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "INSERT INTO lahetykset "
            + "(tunniste, otsikko, lahettavapalvelu, lahettajansahkoposti, prioriteetti, omistaja, luotu, poistettava) "
            + "VALUES (?::uuid, ?, ?, ?, ?::prioriteetti, ?, now(), '2040-01-01 00:00:00'::timestamp)",
        tunniste,
        otsikko,
        "maskitesti-palvelu",
        "testi.lahettaja@opintopolku.fi",
        "NORMAALI",
        TOISEN_VIRKAILIJAN_OID);
    return tunniste;
  }

  private String insertViesti(String lahetysTunniste, String otsikko, String sisalto, int kayttooikeusTunniste) {
    var tunniste = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "INSERT INTO viestit (tunniste, lahetys_tunniste, otsikko, sisalto, sisallontyyppi, "
            + "kielet_fi, kielet_sv, kielet_en, prioriteetti, omistaja, luotu, "
            + "haku_otsikko, haku_sisalto, haku_kayttooikeudet, haku_vastaanottajat, "
            + "haku_lahettaja, haku_metadata, haku_lahettavapalvelu, haku_organisaatiot) "
            + "VALUES (?::uuid, ?::uuid, ?, ?, 'TEXT', true, false, false, 'NORMAALI'::prioriteetti, ?, now(), "
            + "to_tsvector('simple', ?), to_tsvector('simple', ?), ARRAY[?]::integer[], '{}'::varchar[], "
            + "null, '{}'::varchar[], 'maskitesti-palvelu', '{}'::varchar[])",
        tunniste,
        lahetysTunniste,
        otsikko,
        sisalto,
        TOISEN_VIRKAILIJAN_OID,
        otsikko,
        sisalto,
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

  private void insertMaski(String viestiTunniste, String salaisuus, String maski) {
    jdbcTemplate.update(
        "INSERT INTO maskit (viesti_tunniste, salaisuus, maski) VALUES (?::uuid, ?, ?)",
        viestiTunniste,
        salaisuus,
        maski);
  }

  private String maskattavaLahetys(String maski) {
    int oikeus = insertKatseluOikeus();
    String lahetysTunniste = insertLahetys("Kutsu " + SALAISUUS);
    String viestiTunniste =
        insertViesti(lahetysTunniste, "Kutsu " + SALAISUUS, "Avaa linkki " + SALAISUUS, oikeus);
    insertMaski(viestiTunniste, SALAISUUS, maski);
    return lahetysTunniste;
  }

  @Test
  @UserKatselijaRaportoija
  void lahetysListMasksSalaisuusInOtsikko() throws Exception {
    maskattavaLahetys(MASKI);

    mvc.perform(get("/v1/lahetykset/lista"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lahetykset[0].otsikko").value("Kutsu " + MASKI));
  }

  @Test
  @UserKatselijaRaportoija
  void lahetysDetailMasksSalaisuusInOtsikko() throws Exception {
    String lahetysTunniste = maskattavaLahetys(MASKI);

    mvc.perform(get("/v1/lahetykset/{tunniste}", lahetysTunniste))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.otsikko").value("Kutsu " + MASKI));
  }

  @Test
  @UserKatselijaRaportoija
  void massaviestiMasksSalaisuusInOtsikkoAndSisalto() throws Exception {
    String lahetysTunniste = maskattavaLahetys(MASKI);

    mvc.perform(get("/v1/massaviesti/{tunniste}", lahetysTunniste))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.otsikko").value("Kutsu " + MASKI))
        .andExpect(jsonPath("$.sisalto").value("Avaa linkki " + MASKI));
  }

  @Test
  @UserKatselijaRaportoija
  void viestiMasksSalaisuusInOtsikkoAndSisalto() throws Exception {
    int oikeus = insertKatseluOikeus();
    String lahetysTunniste = insertLahetys("Kutsu " + SALAISUUS);
    String viestiTunniste =
        insertViesti(lahetysTunniste, "Kutsu " + SALAISUUS, "Avaa linkki " + SALAISUUS, oikeus);
    insertMaski(viestiTunniste, SALAISUUS, MASKI);

    mvc.perform(get("/v1/viesti/{tunniste}", viestiTunniste))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.otsikko").value("Kutsu " + MASKI))
        .andExpect(jsonPath("$.sisalto").value("Avaa linkki " + MASKI));
  }

  @Test
  @UserKatselijaRaportoija
  void maskWithoutValueFallsBackToDefault() throws Exception {
    String lahetysTunniste = maskattavaLahetys(null);

    mvc.perform(get("/v1/massaviesti/{tunniste}", lahetysTunniste))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sisalto").value("Avaa linkki xxxxx"));
  }

  @Test
  @UserKatselijaRaportoija
  void viestiWithoutMaskitIsReturnedAsIs() throws Exception {
    int oikeus = insertKatseluOikeus();
    String lahetysTunniste = insertLahetys("Tavallinen otsikko");
    insertViesti(lahetysTunniste, "Tavallinen otsikko", "Tavallinen sisältö", oikeus);

    mvc.perform(get("/v1/massaviesti/{tunniste}", lahetysTunniste))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.otsikko").value("Tavallinen otsikko"))
        .andExpect(jsonPath("$.sisalto").value("Tavallinen sisältö"));
  }
}
