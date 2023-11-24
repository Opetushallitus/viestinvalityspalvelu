package fi.oph.viestinvalitys.vastaanotto.security

import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.security.core.context.SecurityContextHolder

import java.util.UUID
import scala.jdk.CollectionConverters.*

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

class SecurityOperaatiot {

  private lazy val kayttajanOikeudet = SecurityContextHolder.getContext.getAuthentication.getAuthorities.asScala.map(a => a.getAuthority).toSet
  val identiteetti = SecurityContextHolder.getContext.getAuthentication.getName()

  def getIdentiteetti(): String =
    identiteetti

  def onOikeusKatsellaEntiteetti(omistaja: String, entiteetinOikeudet: Set[String]): Boolean =
    if (identiteetti.equals(omistaja) || kayttajanOikeudet.contains(SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA_FULL))
      return true
    entiteetinOikeudet.intersect(kayttajanOikeudet).size > 0

  def onOikeusLahettaa(): Boolean =
    SecurityConstants.LAHETYS_ROLES.intersect(kayttajanOikeudet).size>0

  def onOikeusKatsella(): Boolean =
    SecurityConstants.KATSELU_ROLES.intersect(kayttajanOikeudet).size>0

}
