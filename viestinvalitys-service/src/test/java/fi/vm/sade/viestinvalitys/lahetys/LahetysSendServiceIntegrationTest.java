package fi.vm.sade.viestinvalitys.lahetys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fi.vm.sade.viestinvalitys.ViestinvalitysServiceApiTest;

import java.util.List;
import java.util.UUID;

import fi.vm.sade.viestinvalitys.lahetys.email.EmailSender;
import fi.vm.sade.viestinvalitys.lahetys.repository.LahetysSendRepository;
import fi.vm.sade.viestinvalitys.lahetys.service.LahetysSendService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendRawEmailRequest;
import software.amazon.awssdk.services.ses.model.SendRawEmailResponse;
import software.amazon.awssdk.services.ses.model.SesException;

@TestPropertySource(
        properties = {
                "viestinvalitys.lahetys.enabled=true",
                "viestinvalitys.mode=PRODUCTION",
                "db-scheduler.enabled=false",
                "viestinvalitys.ses.configuration-set-name=test-config-set",
                "viestinvalitys.ses.from-email-address=noreply@example.com",
                "viestinvalitys.metrics.namespace=test-viestinvalitys",
                "attachments.bucket.name=test-bucket"
        })
class LahetysSendServiceIntegrationTest extends ViestinvalitysServiceApiTest {

  @Autowired
  private LahetysSendService lahetysSendService;

  @MockitoBean
  private SesClient sesClient;
  @MockitoBean
  private CloudWatchClient cloudWatchClient;
  @MockitoBean
  private S3Client s3Client;

  @BeforeEach
  void setup() {
    clearDatabase();
  }

  @Test
  void sendsWaitingRecipientAndMarksLahetetty() {
    stubSesSuccess("ses-msg-1");
    UUID vastaanottaja = insertValmisViesti("vastaanottaja@example.com", "NORMAALI");

    lahetysSendService.laheta(10);

    assertEquals("LAHETETTY", tilaOf(vastaanottaja));
    assertEquals("ses-msg-1", sesTunnisteOf(vastaanottaja));
    // ODOTTAA (initial) → LAHETYKSESSA (claim) → LAHETETTY (sent)
    assertEquals(List.of("LAHETYKSESSA", "LAHETETTY"), siirtymaTilatOf(vastaanottaja));
    verify(sesClient, times(1)).sendRawEmail(any(SendRawEmailRequest.class));
  }

  @Test
  void invalidEmailMovesRecipientToVirheWithoutCallingSes() {
    UUID vastaanottaja = insertValmisViesti("not-an-email", "NORMAALI");

    lahetysSendService.laheta(10);

    assertEquals("VIRHE", tilaOf(vastaanottaja));
    assertEquals(
            LahetysSendService.SAHKOPOSTIOSOITE_EI_VALIDI_ERROR, viimeisinLisatietoOf(vastaanottaja));
    verify(sesClient, never()).sendRawEmail(any(SendRawEmailRequest.class));
  }

  @Test
  void onlyOdottaaRecipientsAreProcessed() {
    stubSesSuccess("ses-msg-1");
    UUID odottaa = insertValmisViesti("odottaa@example.com", "NORMAALI");
    UUID joLahetetty = insertVastaanottajaWithTila("lahetetty@example.com", "LAHETETTY");

    lahetysSendService.laheta(10);

    assertEquals("LAHETETTY", tilaOf(odottaa));
    assertEquals("LAHETETTY", tilaOf(joLahetetty)); // unchanged
    // only the ODOTTAA one produced transitions
    assertTrue(siirtymaTilatOf(joLahetetty).isEmpty());
    verify(sesClient, times(1)).sendRawEmail(any(SendRawEmailRequest.class));
  }

  @Test
  void throttlingReturnsRecipientToOdottaa() {
    when(sesClient.sendRawEmail(any(SendRawEmailRequest.class)))
            .thenThrow(
                    SesException.builder()
                            .statusCode(429)
                            .awsErrorDetails(
                                    AwsErrorDetails.builder()
                                            .errorCode("Throttling")
                                            .errorMessage("Maximum sending rate exceeded")
                                            .build())
                            .build());
    UUID vastaanottaja = insertValmisViesti("vastaanottaja@example.com", "NORMAALI");

    lahetysSendService.laheta(10);

    assertEquals("ODOTTAA", tilaOf(vastaanottaja)); // requeued for retry
    // ODOTTAA (initial) → LAHETYKSESSA (claim) → ODOTTAA (throttled back)
    assertEquals(List.of("LAHETYKSESSA", "ODOTTAA"), siirtymaTilatOf(vastaanottaja));
  }

  @Test
  void genericSendFailureMovesRecipientToVirhe() {
    when(sesClient.sendRawEmail(any(SendRawEmailRequest.class)))
            .thenThrow(new RuntimeException("SES exploded"));
    UUID vastaanottaja = insertValmisViesti("vastaanottaja@example.com", "NORMAALI");

    lahetysSendService.laheta(10);

    assertEquals("VIRHE", tilaOf(vastaanottaja));
  }

  @Test
  void attachmentsAreFetchedFromS3AndSent() {
    stubSesSuccess("ses-msg-1");
    when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
            .thenReturn(
                    ResponseBytes.fromByteArray(
                            GetObjectResponse.builder().build(), "attachment-bytes".getBytes()));
    UUID viesti = UUID.randomUUID();
    UUID vastaanottaja = insertValmisViesti(viesti, "vastaanottaja@example.com", "NORMAALI");
    UUID liite = insertLiite("liite.pdf", "application/pdf");
    linkLiite(viesti, liite, 0);

    lahetysSendService.laheta(10);

    assertEquals("LAHETETTY", tilaOf(vastaanottaja));
    verify(s3Client, times(1)).getObjectAsBytes(any(GetObjectRequest.class));
    verify(sesClient, times(1)).sendRawEmail(any(SendRawEmailRequest.class));
  }

  @Test
  void recordsOneMetricBatchWhenSomethingWasSent() {
    stubSesSuccess("ses-msg-1");
    insertValmisViesti("a@example.com", "NORMAALI");
    insertValmisViesti("b@example.com", "KORKEA");

    lahetysSendService.laheta(10);

    verify(cloudWatchClient, times(1)).putMetricData(any(PutMetricDataRequest.class));
  }

  @Test
  void noMetricWhenNothingSent() {
    insertValmisViesti("not-an-email", "NORMAALI"); // only an invalid recipient

    lahetysSendService.laheta(10);

    verify(cloudWatchClient, never()).putMetricData(any(PutMetricDataRequest.class));
  }

  @Test
  void doesNothingWhenNoWaitingRecipients() {
    lahetysSendService.laheta(10);

    verify(sesClient, never()).sendRawEmail(any(SendRawEmailRequest.class));
    verify(cloudWatchClient, never()).putMetricData(any(PutMetricDataRequest.class));
  }

  private void stubSesSuccess(String messageId) {
    when(sesClient.sendRawEmail(any(SendRawEmailRequest.class)))
            .thenReturn(SendRawEmailResponse.builder().messageId(messageId).build());
  }

  private UUID insertValmisViesti(String email, String prioriteetti) {
    return insertValmisViesti(UUID.randomUUID(), email, prioriteetti);
  }

  private UUID insertValmisViesti(UUID viesti, String email, String prioriteetti) {
    insertLahetysAndViesti(viesti, prioriteetti);
    return insertVastaanottaja(viesti, email, "ODOTTAA", prioriteetti);
  }

  private void insertLahetysAndViesti(UUID viesti, String prioriteetti) {
    UUID lahetys = UUID.randomUUID();
    jdbcTemplate.update(
            "INSERT INTO lahetykset "
                    + "(tunniste, otsikko, lahettavapalvelu, lahettajannimi, lahettajansahkoposti, replyto, "
                    + "prioriteetti, omistaja, luotu, poistettava) "
                    + "VALUES (?::uuid, ?, ?, ?, ?, null, ?::prioriteetti, ?, now(), "
                    + "'2040-01-01 00:00:00'::timestamp)",
            lahetys.toString(),
            "Otsikko",
            "Palvelu",
            "Testi Lähettäjä",
            "noreply@opintopolku.fi",
            prioriteetti,
            TEST_KAYTTAJA_OID);
    jdbcTemplate.update(
            "INSERT INTO viestit "
                    + "(tunniste, lahetys_tunniste, otsikko, sisalto, sisallontyyppi, kielet_fi, kielet_sv, "
                    + "kielet_en, prioriteetti, omistaja, luotu, haku_sisalto, haku_otsikko, "
                    + "haku_kayttooikeudet, haku_vastaanottajat, haku_metadata, haku_lahettavapalvelu, "
                    + "haku_organisaatiot) "
                    + "VALUES (?::uuid, ?::uuid, ?, ?, ?, true, false, false, ?::prioriteetti, ?, now(), "
                    + "''::tsvector, ''::tsvector, '{}'::integer[], '{}'::varchar[], '{}'::varchar[], '', "
                    + "'{}'::varchar[])",
            viesti.toString(),
            lahetys.toString(),
            "Otsikko",
            "Viestin sisältö",
            "TEXT",
            prioriteetti,
            TEST_KAYTTAJA_OID);
  }

  private UUID insertVastaanottajaWithTila(String email, String tila) {
    UUID viesti = UUID.randomUUID();
    insertLahetysAndViesti(viesti, "NORMAALI");
    return insertVastaanottaja(viesti, email, tila, "NORMAALI");
  }

  private UUID insertVastaanottaja(UUID viesti, String email, String tila, String prioriteetti) {
    UUID vastaanottaja = UUID.randomUUID();
    jdbcTemplate.update(
            "INSERT INTO vastaanottajat "
                    + "(tunniste, viesti_tunniste, nimi, sahkopostiosoite, tila, luotu, prioriteetti) "
                    + "VALUES (?::uuid, ?::uuid, ?, ?, ?, now(), ?::prioriteetti)",
            vastaanottaja.toString(),
            viesti.toString(),
            "Vastaanottaja",
            email,
            tila,
            prioriteetti);
    return vastaanottaja;
  }

  private UUID insertLiite(String nimi, String contentType) {
    UUID liite = UUID.randomUUID();
    jdbcTemplate.update(
            "INSERT INTO liitteet (tunniste, nimi, contenttype, koko, omistaja, tila, luotu) "
                    + "VALUES (?::uuid, ?, ?, ?, ?, 'PUHDAS', now())",
            liite.toString(),
            nimi,
            contentType,
            123,
            TEST_KAYTTAJA_OID);
    return liite;
  }

  private void linkLiite(UUID viesti, UUID liite, int indeksi) {
    jdbcTemplate.update(
            "INSERT INTO viestit_liitteet (viesti_tunniste, liite_tunniste, indeksi) "
                    + "VALUES (?::uuid, ?::uuid, ?)",
            viesti.toString(),
            liite.toString(),
            indeksi);
  }

  private String tilaOf(UUID vastaanottaja) {
    return jdbcTemplate.queryForObject(
            "SELECT tila FROM vastaanottajat WHERE tunniste = ?::uuid",
            String.class,
            vastaanottaja.toString());
  }

  private String sesTunnisteOf(UUID vastaanottaja) {
    return jdbcTemplate.queryForObject(
            "SELECT ses_tunniste FROM vastaanottajat WHERE tunniste = ?::uuid",
            String.class,
            vastaanottaja.toString());
  }

  private List<String> siirtymaTilatOf(UUID vastaanottaja) {
    return jdbcTemplate.queryForList(
            "SELECT tila FROM vastaanottaja_siirtymat WHERE vastaanottaja_tunniste = ?::uuid "
                    + "ORDER BY aika ASC",
            String.class,
            vastaanottaja.toString());
  }

  private String viimeisinLisatietoOf(UUID vastaanottaja) {
    return jdbcTemplate.queryForObject(
            "SELECT lisatiedot FROM vastaanottaja_siirtymat WHERE vastaanottaja_tunniste = ?::uuid "
                    + "ORDER BY aika DESC LIMIT 1",
            String.class,
            vastaanottaja.toString());
  }
}
