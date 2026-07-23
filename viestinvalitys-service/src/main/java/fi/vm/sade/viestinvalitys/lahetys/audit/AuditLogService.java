package fi.vm.sade.viestinvalitys.lahetys.audit;

import fi.vm.sade.auditlog.ApplicationType;
import fi.vm.sade.auditlog.Audit;
import fi.vm.sade.auditlog.Changes;
import fi.vm.sade.auditlog.Target;
import fi.vm.sade.auditlog.User;
import fi.vm.sade.viestinvalitys.lahetys.model.VastaanottajanTila;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

import fi.vm.sade.viestinvalitys.lahetys.attachments.LahetysAuditOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "viestinvalitys.lahetys.enabled", havingValue = "true")
public class AuditLogService {

  private static final Logger AUDIT_LOGGER = LoggerFactory.getLogger("auditlog");

  private final Audit audit =
          new Audit(AUDIT_LOGGER::info, "viestinvalitys", ApplicationType.VIRKAILIJA);

  public void logSendEmail(
          UUID vastaanottaja, String sesTunniste, VastaanottajanTila from, VastaanottajanTila to) {
    var changes =
            new Changes.Builder()
                    .added("sesTunniste", sesTunniste)
                    .updated("vastaanottajanTila", from.name(), to.name())
                    .build();
    audit.log(
            auditUser(),
            LahetysAuditOperation.SAHKOPOSTIN_LAHETYS_VASTAANOTTAJALLE,
            target(vastaanottaja),
            changes);
  }

  public void logStateChange(
          UUID vastaanottaja, VastaanottajanTila from, VastaanottajanTila to, String lisatiedot) {
    var changes =
            new Changes.Builder()
                    .added("lisatiedot", lisatiedot == null ? "" : lisatiedot)
                    .updated("vastaanottajanTila", from.name(), to.name())
                    .build();
    audit.log(
            auditUser(),
            LahetysAuditOperation.VASTAANOTTAJAN_TILAN_PAIVITYS,
            target(vastaanottaja),
            changes);
  }

  private static Target target(UUID vastaanottaja) {
    return new Target.Builder().setField("vastaanottaja", vastaanottaja.toString()).build();
  }

  private static User auditUser() {
    InetAddress ip;
    try {
      ip = InetAddress.getLocalHost();
    } catch (UnknownHostException e) {
      ip = InetAddress.getLoopbackAddress();
    }
    return new User(ip, "", "");
  }
}
