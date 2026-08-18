package fi.vm.sade.viestinvalitys.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fi.vm.sade.viestinvalitys.ViestinvalitysServiceApiTest;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class LiitteetTest extends ViestinvalitysServiceApiTest {

  private static final String BUCKET = "e2e-liitteet";
  private static final String LOCALSTACK_ENDPOINT = "http://localhost:4566";
  private static final String TOISEN_VIRKAILIJAN_OID = "1.2.246.562.24.11111111111";
  private static final String RAJATTU_ORGANISAATIO_OID = "1.2.246.562.10.44444444444";
  private static final byte[] LIITE_SISALTO = "kissakuva-bytes".getBytes();

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("viestinvalitys.features.downloadViesti.enabled", () -> "true");
    registry.add("aws.s3.endpoint", () -> LOCALSTACK_ENDPOINT);
    registry.add("aws.s3.path-style-access", () -> "true");
    registry.add("aws.region", () -> "us-east-1");
    registry.add("attachments.bucket.name", () -> BUCKET);
  }

  private static S3Client s3;

  @BeforeAll
  static void setupBucket() {
    s3 =
        S3Client.builder()
            .endpointOverride(URI.create(LOCALSTACK_ENDPOINT))
            .region(Region.US_EAST_1)
            .forcePathStyle(true)
            .credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
            .build();
    try {
      s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
    } catch (software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException e) {
      // bucket exists from a previous run
    }
  }

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

  private String insertLahetys() {
    var tunniste = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "INSERT INTO lahetykset "
            + "(tunniste, otsikko, lahettavapalvelu, lahettajansahkoposti, prioriteetti, omistaja, luotu, poistettava) "
            + "VALUES (?::uuid, 'Liitetesti', 'liitetesti-palvelu', 'testi.lahettaja@opintopolku.fi', "
            + "'NORMAALI'::prioriteetti, ?, now(), '2040-01-01 00:00:00'::timestamp)",
        tunniste,
        TOISEN_VIRKAILIJAN_OID);
    return tunniste;
  }

  private String insertViesti(String lahetysTunniste, int kayttooikeusTunniste) {
    var tunniste = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "INSERT INTO viestit (tunniste, lahetys_tunniste, otsikko, sisalto, sisallontyyppi, "
            + "kielet_fi, kielet_sv, kielet_en, prioriteetti, omistaja, luotu, "
            + "haku_otsikko, haku_sisalto, haku_kayttooikeudet, haku_vastaanottajat, "
            + "haku_lahettaja, haku_metadata, haku_lahettavapalvelu, haku_organisaatiot) "
            + "VALUES (?::uuid, ?::uuid, 'Otsikko', 'Sisältö', 'TEXT', true, false, false, "
            + "'NORMAALI'::prioriteetti, ?, now(), to_tsvector('simple', 'Otsikko'), "
            + "to_tsvector('simple', 'Sisältö'), ARRAY[?]::integer[], '{}'::varchar[], "
            + "null, '{}'::varchar[], 'liitetesti-palvelu', '{}'::varchar[])",
        tunniste,
        lahetysTunniste,
        TOISEN_VIRKAILIJAN_OID,
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

  private String insertLiite(String viestiTunniste, String nimi, String contentType, int indeksi) {
    var tunniste = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "INSERT INTO liitteet (tunniste, nimi, contenttype, koko, omistaja, tila, luotu) "
            + "VALUES (?::uuid, ?, ?, ?, ?, 'PUHDAS', now())",
        tunniste,
        nimi,
        contentType,
        LIITE_SISALTO.length,
        TOISEN_VIRKAILIJAN_OID);
    jdbcTemplate.update(
        "INSERT INTO viestit_liitteet (viesti_tunniste, liite_tunniste, indeksi) VALUES (?::uuid, ?::uuid, ?)",
        viestiTunniste,
        tunniste,
        indeksi);
    return tunniste;
  }

  private String katselijalleNakyvaViestiWithLiitteet() {
    int oikeus = insertKayttooikeus("APP_VIESTINVALITYS_KATSELU", OPH_ORGANISAATIO_OID);
    String lahetysTunniste = insertLahetys();
    String viestiTunniste = insertViesti(lahetysTunniste, oikeus);
    insertLiite(viestiTunniste, "raportti.pdf", "application/pdf", 0);
    insertLiite(viestiTunniste, "kuva.png", "image/png", 1);
    return viestiTunniste;
  }

  @Test
  @UserKatselijaRaportoija
  void viestiResponseContainsLiitteetInIndeksiOrder() throws Exception {
    String viestiTunniste = katselijalleNakyvaViestiWithLiitteet();

    mvc.perform(get("/v1/viesti/{tunniste}", viestiTunniste))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.liitteet.length()").value(2))
        .andExpect(jsonPath("$.liitteet[0].nimi").value("raportti.pdf"))
        .andExpect(jsonPath("$.liitteet[0].contentType").value("application/pdf"))
        .andExpect(jsonPath("$.liitteet[1].nimi").value("kuva.png"));
  }

  @Test
  @UserKatselijaRaportoija
  void massaviestiResponseContainsLiitteet() throws Exception {
    int oikeus = insertKayttooikeus("APP_VIESTINVALITYS_KATSELU", OPH_ORGANISAATIO_OID);
    String lahetysTunniste = insertLahetys();
    String viestiTunniste = insertViesti(lahetysTunniste, oikeus);
    insertLiite(viestiTunniste, "raportti.pdf", "application/pdf", 0);

    mvc.perform(get("/v1/massaviesti/{tunniste}", lahetysTunniste))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.liitteet.length()").value(1))
        .andExpect(jsonPath("$.liitteet[0].nimi").value("raportti.pdf"));
  }

  @Test
  @UserKatselijaRaportoija
  void downloadLiiteReturnsAttachmentContent() throws Exception {
    int oikeus = insertKayttooikeus("APP_VIESTINVALITYS_KATSELU", OPH_ORGANISAATIO_OID);
    String lahetysTunniste = insertLahetys();
    String viestiTunniste = insertViesti(lahetysTunniste, oikeus);
    String liiteTunniste = insertLiite(viestiTunniste, "raportti.pdf", "application/pdf", 0);
    s3.putObject(
        PutObjectRequest.builder().bucket(BUCKET).key(liiteTunniste).build(),
        RequestBody.fromBytes(LIITE_SISALTO));

    var response =
        mvc.perform(
                get("/v1/download/liite")
                    .param("viestiTunniste", viestiTunniste)
                    .param("liiteTunniste", liiteTunniste))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    assertThat(response.getContentType()).isEqualTo("application/pdf");
    assertThat(response.getHeader("Content-Disposition")).contains("raportti.pdf");
    assertThat(response.getContentAsByteArray()).isEqualTo(LIITE_SISALTO);
  }

  @Test
  @UserKatselijaRaportoija
  void downloadLiiteRequiresRightsToViesti() throws Exception {
    int oikeus = insertKayttooikeus("APP_HAKEMUS_CRUD", RAJATTU_ORGANISAATIO_OID);
    String lahetysTunniste = insertLahetys();
    String viestiTunniste = insertViesti(lahetysTunniste, oikeus);
    String liiteTunniste = insertLiite(viestiTunniste, "raportti.pdf", "application/pdf", 0);

    mvc.perform(
            get("/v1/download/liite")
                .param("viestiTunniste", viestiTunniste)
                .param("liiteTunniste", liiteTunniste))
        .andExpect(status().isForbidden());
  }

  @Test
  @UserKatselijaRaportoija
  void downloadLiiteNotBelongingToViestiIsNotFound() throws Exception {
    int oikeus = insertKayttooikeus("APP_VIESTINVALITYS_KATSELU", OPH_ORGANISAATIO_OID);
    String lahetysTunniste = insertLahetys();
    String viestiTunniste = insertViesti(lahetysTunniste, oikeus);
    String toinenViesti = insertViesti(insertLahetys(), oikeus);
    String liiteTunniste = insertLiite(toinenViesti, "raportti.pdf", "application/pdf", 0);

    mvc.perform(
            get("/v1/download/liite")
                .param("viestiTunniste", viestiTunniste)
                .param("liiteTunniste", liiteTunniste))
        .andExpect(status().isNotFound());
  }

  @Test
  void downloadLiiteRequiresAuthentication() throws Exception {
    mvc.perform(
            get("/v1/download/liite")
                .param("viestiTunniste", UUID.randomUUID().toString())
                .param("liiteTunniste", UUID.randomUUID().toString()))
        .andExpect(status().is3xxRedirection());
  }
}
