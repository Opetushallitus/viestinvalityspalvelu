package fi.vm.sade.viestinvalitys.lahetys.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import fi.vm.sade.viestinvalitys.lahetys.audit.AuditLogService;
import fi.vm.sade.viestinvalitys.lahetys.email.EmailSender;
import fi.vm.sade.viestinvalitys.lahetys.model.*;
import fi.vm.sade.viestinvalitys.lahetys.repository.LahetysSendRepository;
import fi.vm.sade.viestinvalitys.lahetys.attachments.AttachmentDownloader;
import fi.vm.sade.viestinvalitys.lahetys.service.cloudwatch.MetricService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.routines.EmailValidator;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ses.model.SesException;

/**
 * Based on Scala lahetys lambda's {@code laheta(maara)}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "viestinvalitys.lahetys.enabled", havingValue = "true")
public class LahetysSendService {

  public static final String SAHKOPOSTIOSOITE_EI_VALIDI_ERROR = "Sähköpostiosoite ei validi";

  private final LahetysSendRepository repository;
  private final AttachmentDownloader attachmentDownloader;
  private final EmailSender emailSender;
  private final AuditLogService auditLog;
  private final MetricService metricService;

  @Value("${viestinvalitys.lahetys.polling-interval-seconds:2}")
  private int pollingIntervalSeconds;

  @Value("${viestinvalitys.lahetys.sending-quota-per-second:65}")
  private int sendingQuotaPerSecond;

  public int batchSize() {
    return pollingIntervalSeconds * sendingQuotaPerSecond;
  }

  public void laheta() {
    laheta(batchSize());
  }

  public void laheta(int maara) {
    List<UUID> tunnisteet = repository.getLahetettavatVastaanottajat(maara);
    if (tunnisteet.isEmpty()) {
      return;
    }
    log.info("Haetaan vastaanottajien tiedot {} tunnisteelle", tunnisteet.size());
    List<Vastaanottaja> vastaanottajat = repository.getVastaanottajat(tunnisteet);
    List<UUID> viestiTunnisteet =
            vastaanottajat.stream().map(Vastaanottaja::viestiTunniste).distinct().toList();
    Map<UUID, Viesti> viestit = repository.getViestit(viestiTunnisteet);
    Map<UUID, List<Liite>> liitteet = repository.getViestinLiitteet(viestiTunnisteet);
    Map<UUID, List<Attachment>> ladatutLiitteet = new HashMap<>();

    List<Prioriteetti> lahetetyt = new ArrayList<>();
    for (Vastaanottaja vastaanottaja : vastaanottajat) {
      MDC.put("vastaanottajaTunniste", vastaanottaja.tunniste().toString());
      MDC.put("viestiTunniste", vastaanottaja.viestiTunniste().toString());
      try {
        lahetaYhdelle(vastaanottaja, viestit, liitteet, ladatutLiitteet, lahetetyt);
      } finally {
        MDC.remove("vastaanottajaTunniste");
        MDC.remove("viestiTunniste");
      }
    }
    metricService.recordLahetykset(lahetetyt);
  }

  private void lahetaYhdelle(
          Vastaanottaja vastaanottaja,
          Map<UUID, Viesti> viestit,
          Map<UUID, List<Liite>> liitteet,
          Map<UUID, List<Attachment>> ladatutLiitteet,
          List<Prioriteetti> lahetetyt) {
    UUID tunniste = vastaanottaja.tunniste();
    try {
      if (!EmailValidator.getInstance().isValid(vastaanottaja.kontakti().sahkoposti())) {
        log.warn("Vastaanottajan {} sähköposti ei ole validi, siirretään virhetilaan", tunniste);
        auditLog.logStateChange(
                tunniste, vastaanottaja.tila(), VastaanottajanTila.VIRHE, SAHKOPOSTIOSOITE_EI_VALIDI_ERROR);
        repository.paivitaVastaanottajaVirhetilaan(tunniste, SAHKOPOSTIOSOITE_EI_VALIDI_ERROR);
        return;
      }

      Viesti viesti = viestit.get(vastaanottaja.viestiTunniste());
      List<Attachment> attachments =
              ladatutLiitteet.computeIfAbsent(
                      viesti.tunniste(),
                      k ->
                              liitteet.getOrDefault(k, List.of()).stream()
                                      .map(
                                              l ->
                                                      new Attachment(
                                                              l.nimi(), l.contentType(), attachmentDownloader.download(l.tunniste())))
                                      .toList());

      String sesTunniste = emailSender.send(viesti, vastaanottaja, attachments);
      auditLog.logSendEmail(
              tunniste, sesTunniste, vastaanottaja.tila(), VastaanottajanTila.LAHETETTY);
      repository.paivitaVastaanottajaLahetetyksi(tunniste, sesTunniste);
      lahetetyt.add(viesti.prioriteetti());
      log.info("Lähetetty viesti vastaanottajalle {}", tunniste);
    } catch (SesException e) {
      if (e.isThrottlingException()) {
        log.error("Kuristus lähettäessä vastaanottajalle {}, kokeillaan myöhemmin uudestaan", tunniste, e);
        auditLog.logStateChange(
                tunniste, vastaanottaja.tila(), VastaanottajanTila.ODOTTAA, e.getMessage());
        repository.paivitaVastaanottajaOdottaaTilaan(tunniste, e.getMessage());
      } else {
        log.error("Virhe lähetettäessä vastaanottajalle {}", tunniste, e);
        auditLog.logStateChange(
                tunniste, vastaanottaja.tila(), VastaanottajanTila.VIRHE, e.getMessage());
        repository.paivitaVastaanottajaVirhetilaan(tunniste, e.getMessage());
      }
    } catch (Exception e) {
      log.error("Virhe lähetettäessä vastaanottajalle {}", tunniste, e);
      auditLog.logStateChange(
              tunniste, vastaanottaja.tila(), VastaanottajanTila.VIRHE, e.getMessage());
      repository.paivitaVastaanottajaVirhetilaan(tunniste, e.getMessage());
    }
  }
}
