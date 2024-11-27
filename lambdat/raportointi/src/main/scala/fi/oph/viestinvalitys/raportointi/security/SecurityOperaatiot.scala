package fi.oph.viestinvalitys.raportointi.security

import fi.oph.viestinvalitys.business.{KantaOperaatiot, Kayttooikeus}
import fi.oph.viestinvalitys.raportointi.integration.OrganisaatioService
import fi.oph.viestinvalitys.raportointi.security.SecurityConstants.OPH_ORGANISAATIO_OID
import fi.oph.viestinvalitys.util.DbUtil
import jakarta.servlet.http.HttpSession
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder

import scala.jdk.CollectionConverters.*
import scala.util.matching.Regex

object SecurityConstants {

  final val OPH_ORGANISAATIO_OID = "1.2.246.562.10.00000000001";

  final val KAYTTOOIKEUSPATTERN: Regex = ("^(.*)_([0-9]+(\\.[0-9]+)+)$").r

  final val SECURITY_ROOLI_LAHETYS = "APP_VIESTINVALITYS_LAHETYS"
  final val SECURITY_ROOLI_KATSELU = "APP_VIESTINVALITYS_KATSELU"
  final val SECURITY_ROOLI_PAAKAYTTAJA = "APP_VIESTINVALITYS_OPH_PAAKAYTTAJA"
  final val SECURITY_ROOLI_LAHETYS_OIKEUS = Kayttooikeus(SECURITY_ROOLI_LAHETYS, Option.empty)
  final val SECURITY_ROOLI_KATSELU_OIKEUS = Kayttooikeus(SECURITY_ROOLI_KATSELU, Option.empty)
  final val SECURITY_ROOLI_PAAKAYTTAJA_OIKEUS = Kayttooikeus(SECURITY_ROOLI_PAAKAYTTAJA, Option.empty)

  final val LAHETYS_ROLES = Set(SECURITY_ROOLI_LAHETYS_OIKEUS, SECURITY_ROOLI_PAAKAYTTAJA_OIKEUS)
  final val KATSELU_ROLES = Set(SECURITY_ROOLI_KATSELU_OIKEUS, SECURITY_ROOLI_PAAKAYTTAJA_OIKEUS)
}

class SecurityOperaatiot(
                          getOikeudet: () => Seq[String] = () => SecurityContextHolder.getContext.getAuthentication.getAuthorities.asScala.map(a => a.getAuthority).toSeq,
                          getUsername: () => String = () => SecurityContextHolder.getContext.getAuthentication.getName(),
                          organisaatioClient: OrganisaatioService = OrganisaatioService,
                          optionalHttpSession: Option[HttpSession] = None) {

  val LOG = LoggerFactory.getLogger(classOf[SecurityOperaatiot])
  val kantaOperaatiot = new KantaOperaatiot(DbUtil.database)
  final val SECURITY_ROOLI_PREFIX_PATTERN = "^ROLE_"
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
      optionalHttpSession match {
        case Some(n) if optionalHttpSession.get.getAttribute("kayttooikeudet") != null =>
          LOG.warn("oikat sessiosta!")
          getSetOfKayttooikeusFromSession(optionalHttpSession.get, "kayttooikeudet").getOrElse(Set.empty)
        case _ =>
          LOG.warn("parsitaan oikat!")
          LOG.info("Haetaan käyttöoikeuksien organisaatioiden aliorganisaatiot")
          val lapsioikeudet = kayttajanCasOikeudet
            .filter(kayttajanOikeus => kayttajanOikeus.organisaatio.isDefined)
            .map(kayttajanOikeus =>
              organisaatioClient.getAllChildOidsFlat(kayttajanOikeus.organisaatio.get)
                .map(o => Kayttooikeus(kayttajanOikeus.oikeus, Some(o)))).flatten
          if(optionalHttpSession.isDefined)
            optionalHttpSession.get.setAttribute("kayttooikeudet", kayttajanCasOikeudet ++ lapsioikeudet)
          kayttajanCasOikeudet ++ lapsioikeudet
      }
  }

  private lazy val kayttajanKayttooikeustunnisteet: Option[Set[Int]] = {
    val pk = onPaakayttaja()
    if (onPaakayttaja())
      Option.empty
    else
      optionalHttpSession match {
        case None =>
          LOG.warn("ei sessiota, oikkatunnisteet kannasta!")
          Option.apply(kantaOperaatiot.getKayttooikeusTunnisteet(kayttajanOikeudet.toSeq))
        case Some(n) if optionalHttpSession.get.getAttribute("kayttooikeustunnisteet") != null =>
          LOG.warn("oikkatunnisteet sessiosta!")
          getSetOfIntsFromSession(optionalHttpSession.get, "kayttooikeustunnisteet")
        case _ =>
          LOG.warn("oikkatunnisteet kannasta ja laitetaan sessioon!")
          val tunnisteet = kantaOperaatiot.getKayttooikeusTunnisteet(kayttajanOikeudet.toSeq)
          optionalHttpSession.get.setAttribute("kayttooikeustunnisteet", tunnisteet)
          Some(tunnisteet)
      }
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

  def getKayttajanKayttooikeustunnisteet(): Option[Set[Int]] = kayttajanKayttooikeustunnisteet

  /**
   * Palauttaa käyttäjän käyttöoikeuksien organisaatiot ilman lapsihierarkiaa
   */
  def getCasOrganisaatiot(): Set[String] =
    kayttajanCasOikeudet.filter(kayttooikeus => kayttooikeus.organisaatio.isDefined).map(ko => ko.organisaatio.get)

  def getSetOfIntsFromSession(session: HttpSession, attributeName: String): Option[Set[Int]] = {
    Option(session.getAttribute(attributeName)) match {
      case Some(value: Set[_]) =>
        // Safely cast elements to Int, filter out invalid ones
        Some(value.collect { case i: Int => i })
      case _ =>
        None
    }
  }

  def getSetOfKayttooikeusFromSession(session: HttpSession, attributeName: String): Option[Set[Kayttooikeus]] = {
    Option(session.getAttribute(attributeName)) match {
      case Some(value: Set[_]) =>
        // Safely cast elements to Kayttooikeus, filter out invalid ones
        Some(value.collect { case i: Kayttooikeus => i })
      case _ =>
        None
    }
  }
}
