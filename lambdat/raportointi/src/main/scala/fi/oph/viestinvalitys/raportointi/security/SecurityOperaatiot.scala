package fi.oph.viestinvalitys.raportointi.security

import fi.oph.viestinvalitys.business.Kayttooikeus
import fi.oph.viestinvalitys.raportointi.integration.OrganisaatioService
import fi.oph.viestinvalitys.raportointi.security.SecurityConstants.OPH_ORGANISAATIO_OID
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder

import scala.jdk.CollectionConverters.*
import scala.util.matching.Regex

object SecurityConstants {

  final val OPH_ORGANISAATIO_OID = "1.2.246.562.10.00000000001";

  final val KAYTTOOIKEUSPATTERN: Regex = ("^(.*)_([0-9]+(\\.[0-9]+)+)$").r

  final val SECURITY_ROOLI_LAHETYS = "VIESTINVALITYS_LAHETYS"
  final val SECURITY_ROOLI_KATSELU = "VIESTINVALITYS_KATSELU"
  final val SECURITY_ROOLI_PAAKAYTTAJA = "VIESTINVALITYS_OPH_PAAKAYTTAJA"
  final val SECURITY_ROOLI_LAHETYS_OIKEUS = Kayttooikeus(SECURITY_ROOLI_LAHETYS, Option.empty)
  final val SECURITY_ROOLI_KATSELU_OIKEUS = Kayttooikeus(SECURITY_ROOLI_KATSELU, Option.empty)
  final val SECURITY_ROOLI_PAAKAYTTAJA_OIKEUS = Kayttooikeus(SECURITY_ROOLI_PAAKAYTTAJA, Option.empty)

  final val LAHETYS_ROLES = Set(SECURITY_ROOLI_LAHETYS_OIKEUS, SECURITY_ROOLI_PAAKAYTTAJA_OIKEUS)
  final val KATSELU_ROLES = Set(SECURITY_ROOLI_KATSELU_OIKEUS, SECURITY_ROOLI_PAAKAYTTAJA_OIKEUS)
}

class SecurityOperaatiot(
  getOikeudet: () => Seq[String] = () => SecurityContextHolder.getContext.getAuthentication.getAuthorities.asScala.map(a => a.getAuthority).toSeq,
  getUsername: () => String = () => SecurityContextHolder.getContext.getAuthentication.getName(),
  organisaatioClient: OrganisaatioService = OrganisaatioService) {

  val LOG = LoggerFactory.getLogger(classOf[SecurityOperaatiot])
  final val SECURITY_ROOLI_PREFIX_PATTERN = "^ROLE_APP_"
  private lazy val kayttajanCasOikeudet: Set[Kayttooikeus] = {
    getOikeudet()
      .map(a => a.replaceFirst(SECURITY_ROOLI_PREFIX_PATTERN, ""))
      .map(a => {
        val organisaatioOikeus = SecurityConstants.KAYTTOOIKEUSPATTERN.findFirstMatchIn(a)
        if (organisaatioOikeus.isDefined)
          Kayttooikeus(organisaatioOikeus.get.group(1), Option.apply(organisaatioOikeus.get.group(2)))
        else
          Kayttooikeus(a, Option.empty)
      })
      .toSet
  }
  private lazy val kayttajanOikeudet: Set[Kayttooikeus] = {
    val pk = onPaakayttaja()
    if(onPaakayttaja())
      kayttajanCasOikeudet // ei tarvitse mapata kaikkia lapsiorganisaatioita
    else
      val lapsioikeudet = kayttajanCasOikeudet
        .filter(kayttajanOikeus => kayttajanOikeus.organisaatio.isDefined)
        .map(kayttajanOikeus =>
          organisaatioClient.getAllChildOidsFlat(kayttajanOikeus.organisaatio.get)
            .map(o => Kayttooikeus(kayttajanOikeus.oikeus, Some(o)))
        ).flatten
      kayttajanCasOikeudet ++ lapsioikeudet
  }
  val identiteetti = getUsername()

  def getIdentiteetti(): String =
    identiteetti

  def onOikeusLahettaaEntiteetti(omistaja: String, entiteetinOikeudet: Set[Kayttooikeus]): Boolean =
    if (identiteetti.equals(omistaja) || onPaakayttaja())
      true
    else
      onOikeusLahettaa() && entiteetinOikeudet.intersect(kayttajanOikeudet).size > 0

  def onOikeusKatsellaEntiteetti(omistaja: String, entiteetinOikeudet: Set[Kayttooikeus]): Boolean =
    if (identiteetti.equals(omistaja) || onPaakayttaja())
      true
    else
      onOikeusKatsella() && entiteetinOikeudet.intersect(kayttajanOikeudet).size > 0

  /**
   * Tarkastelee pelkkää käyttöoikeusroolia, ei huomioi organisaatiorajoituksia
   */
  def onOikeusLahettaa(): Boolean =
    SecurityConstants.LAHETYS_ROLES.intersect(kayttajanOikeudet).size > 0
  /**
   * Tarkastelee pelkkää käyttöoikeusroolia, ei huomioi organisaatiorajoituksia
   */
  def onOikeusKatsella(): Boolean =
    SecurityConstants.KATSELU_ROLES.intersect(kayttajanOikeudet).size > 0

  def onPaakayttaja(): Boolean =
    !kayttajanCasOikeudet
      .filter(ko => OPH_ORGANISAATIO_OID.equals(ko.organisaatio.getOrElse(null)) &&
        SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA.equals(ko.oikeus)).isEmpty

  def getKayttajanOikeudet(): Set[Kayttooikeus] = kayttajanOikeudet

  /**
   * Palauttaa käyttäjän käyttöoikeuksien organisaatiot ilman lapsihierarkiaa
   */
  def getCasOrganisaatiot(): Set[String] =
    kayttajanCasOikeudet.filter(kayttooikeus => kayttooikeus.organisaatio.isDefined).map(ko => ko.organisaatio.get)
}
