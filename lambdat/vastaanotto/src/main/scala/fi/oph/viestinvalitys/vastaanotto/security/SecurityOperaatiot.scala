package fi.oph.viestinvalitys.vastaanotto.security

import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.security.core.context.SecurityContextHolder

import java.util.UUID
import scala.jdk.CollectionConverters.*

class SecurityOperaatiot {

  private lazy val kayttajanOikeudet = SecurityContextHolder.getContext.getAuthentication.getAuthorities.asScala.map(a => a.getAuthority).toSet
  val identiteetti = SecurityContextHolder.getContext.getAuthentication.getName()

  def getIdentiteetti(): String =
    identiteetti

  def onOikeusKatsellaEntiteetti(omistaja: String): Boolean =
    identiteetti.equals(omistaja) || kayttajanOikeudet.contains(SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA_FULL)

  def onOikeusLahettaa(): Boolean =
    SecurityConstants.LAHETYS_ROLES.intersect(kayttajanOikeudet).size>0

  def onOikeusKatsella(): Boolean =
    SecurityConstants.KATSELU_ROLES.intersect(kayttajanOikeudet).size>0

}
