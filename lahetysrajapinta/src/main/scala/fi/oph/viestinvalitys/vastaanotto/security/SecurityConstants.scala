package fi.oph.viestinvalitys.vastaanotto.security

object SecurityConstants {

  final val SECURITY_ROOLI_PREFIX = "ROLE_APP_"
  final val SECURITY_ROOLI_LAHETYS = "VIESTINVALITYS_LAHETYS"
  final val SECURITY_ROOLI_KATSELU = "VIESTINVALITYS_KATSELU"
  final val SECURITY_ROOLI_PAAKAYTTAJA = "VIESTINVALITYS_OPH_PAAKAYTTAJA"
  final val SECURITY_ROOLI_LAHETYS_FULL = SECURITY_ROOLI_PREFIX + SECURITY_ROOLI_LAHETYS
  final val SECURITY_ROOLI_KATSELU_FULL = SECURITY_ROOLI_PREFIX + SECURITY_ROOLI_KATSELU
  final val SECURITY_ROOLI_PAAKAYTTAJA_FULL = SECURITY_ROOLI_PREFIX + SECURITY_ROOLI_PAAKAYTTAJA

  final val LAHETYS_ROLES = Set(SECURITY_ROOLI_LAHETYS_FULL, SECURITY_ROOLI_PAAKAYTTAJA_FULL)
  final val KATSELU_ROLES = Set(SECURITY_ROOLI_KATSELU_FULL, SECURITY_ROOLI_PAAKAYTTAJA_FULL)
}