package fi.oph.viestinvalitys.security

import fi.vm.sade.auditlog.Operation

trait AuditOperation(val name: String) extends Operation

object AuditOperation {
  case object Login extends AuditOperation("KIRJAUTUMINEN")

  case object ReadLahetys extends AuditOperation("LAHETYKSEN_LUKU")

  case object ReadViesti extends AuditOperation("VIESTIN_LUKU")

  case object ReadVastaanottajat extends AuditOperation("VASTAANOTTAJIEN_LUKU")

  case object CreateLahetys extends AuditOperation("LAHETYKSEN_LUONTI")

  case object CreateViesti extends AuditOperation("VIESTIN_LUONTI")

  case object CreateLiite extends AuditOperation("LIITTEEN_LUONTI")

  case object SendEmail extends AuditOperation("SAHKOPOSTIN_LAHETYS_VASTAANOTTAJALLE")
  case object UpdateVastaanottajanTila extends AuditOperation("VASTAANOTTAJAN_TILAN_PAIVITYS")

  case object UpdateLiitteenTila extends AuditOperation("LIITTEEN_TILAN_PAIVITYS")


}

enum ResourceEntiteetit {
  case LAHETYS, VIESTI, LIITE, VASTAANOTTAJA, VASTAANOTTAJAN_SIIRTYMA
}
