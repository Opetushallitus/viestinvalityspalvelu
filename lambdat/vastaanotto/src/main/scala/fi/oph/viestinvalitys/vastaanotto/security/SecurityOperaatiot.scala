package fi.oph.viestinvalitys.vastaanotto.security

import org.springframework.security.core.context.SecurityContextHolder

import scala.jdk.CollectionConverters.*

class SecurityOperaatiot(
  getOikeudet: () => Set[String] = () => SecurityContextHolder.getContext.getAuthentication.getAuthorities.asScala.map(a => a.getAuthority).toSet,
  getUsername: () => String = () => SecurityContextHolder.getContext.getAuthentication.getName())  {

  private lazy val kayttajanOikeudet = getOikeudet()

  val identiteetti = getUsername()

  def getIdentiteetti(): String =
    identiteetti

  def onOikeusKatsellaEntiteetti(omistaja: String): Boolean =
    identiteetti.equals(omistaja) || kayttajanOikeudet.contains(SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA_FULL + "_" + SecurityConstants.OPH_ORGANISAATIO_OID)

  def onOikeusLahettaa(): Boolean =
    SecurityConstants.LAHETYS_ROLES.intersect(kayttajanOikeudet).size>0

  def onOikeusKatsella(): Boolean =
    SecurityConstants.KATSELU_ROLES.intersect(kayttajanOikeudet).size>0

}
