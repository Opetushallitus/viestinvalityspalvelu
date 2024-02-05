package fi.oph.viestinvalitys.raportointi.security

import fi.oph.viestinvalitys.business.Kayttooikeus
import fi.oph.viestinvalitys.vastaanotto.model.ViestiValidator
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.security.core.context.SecurityContextHolder

import java.util.UUID
import scala.jdk.CollectionConverters.*

object SecurityConstants {

  final val SECURITY_ROOLI_LAHETYS = "VIESTINVALITYS_LAHETYS"
  final val SECURITY_ROOLI_KATSELU = "VIESTINVALITYS_KATSELU"
  final val SECURITY_ROOLI_PAAKAYTTAJA = "VIESTINVALITYS_OPH_PAAKAYTTAJA"
  final val SECURITY_ROOLI_LAHETYS_OIKEUS = Kayttooikeus(Option.empty, SECURITY_ROOLI_LAHETYS)
  final val SECURITY_ROOLI_KATSELU_OIKEUS = Kayttooikeus(Option.empty, SECURITY_ROOLI_KATSELU)
  final val SECURITY_ROOLI_PAAKAYTTAJA_OIKEUS = Kayttooikeus(Option.empty, SECURITY_ROOLI_PAAKAYTTAJA)

  final val LAHETYS_ROLES = Set(SECURITY_ROOLI_LAHETYS_OIKEUS, SECURITY_ROOLI_PAAKAYTTAJA_OIKEUS)
  final val KATSELU_ROLES = Set(SECURITY_ROOLI_KATSELU_OIKEUS, SECURITY_ROOLI_PAAKAYTTAJA_OIKEUS)
}

class SecurityOperaatiot(
  getOikeudet: () => Seq[String] = () => SecurityContextHolder.getContext.getAuthentication.getAuthorities.asScala.map(a => a.getAuthority).toSeq,
  getNimi: () => String = () => SecurityContextHolder.getContext.getAuthentication.getName()) {

  final val SECURITY_ROOLI_PREFIX_PATTERN = "^ROLE_APP_"
  private lazy val kayttajanOikeudet = {
    getOikeudet()
      .map(a => a.replaceFirst(SECURITY_ROOLI_PREFIX_PATTERN, ""))
      .map(a => {
        val organisaatioOikeus = ViestiValidator.KAYTTOOIKEUSPATTERN.findFirstMatchIn(a)
        if(organisaatioOikeus.isDefined)
          Kayttooikeus(Option.apply(organisaatioOikeus.get.group(2)), organisaatioOikeus.get.group(1))
        else
          Kayttooikeus(Option.empty, a)
      })
      .toSet
  }
  val identiteetti = getNimi()

  def getIdentiteetti(): String =
    identiteetti

  def onOikeusLahettaaEntiteetti(omistaja: String, entiteetinOikeudet: Set[Kayttooikeus]): Boolean =
    if (identiteetti.equals(omistaja) || onPaakayttaja())
      true
    else
      entiteetinOikeudet.intersect(kayttajanOikeudet).size > 0

  def onOikeusKatsellaEntiteetti(omistaja: String, entiteetinOikeudet: Set[Kayttooikeus]): Boolean =
    if (identiteetti.equals(omistaja) || onPaakayttaja())
      true
    else
      entiteetinOikeudet.intersect(kayttajanOikeudet).size > 0

  def onOikeusLahettaa(): Boolean =
    SecurityConstants.LAHETYS_ROLES.intersect(kayttajanOikeudet.map(ko => Kayttooikeus(Option.empty, ko.oikeus))).size>0

  /**
   * Tarkastelee pelkkää käyttöoikeusroolia, ei huomioi organisaatiorajoituksia
   */
  def onOikeusKatsella(): Boolean =
    SecurityConstants.KATSELU_ROLES.intersect(kayttajanOikeudet.map(ko => Kayttooikeus(Option.empty, ko.oikeus))).size>0

  def onPaakayttaja(): Boolean =
    kayttajanOikeudet.map(ko => ko.oikeus).contains(SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA)

  /**
   * Tarkastelee pelkkää käyttöoikeusroolia, ei huomioi organisaatiorajoituksia
   */
  def getKayttajanOikeudet(): Set[Kayttooikeus] = kayttajanOikeudet
}
