package fi.vm.sade.viestinvalitys.lahetys.audit;

import fi.vm.sade.auditlog.Operation;

public enum LahetysAuditOperation implements Operation {
  /**
   * An email was sent to a recipient.
   */
  SAHKOPOSTIN_LAHETYS_VASTAANOTTAJALLE,
  /**
   * A recipient's state changed.
   */
  VASTAANOTTAJAN_TILAN_PAIVITYS
}
