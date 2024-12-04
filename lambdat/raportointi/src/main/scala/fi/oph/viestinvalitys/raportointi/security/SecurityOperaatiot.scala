package fi.oph.viestinvalitys.raportointi.security

import fi.oph.viestinvalitys.business.{KantaOperaatiot, Kayttooikeus}
import fi.oph.viestinvalitys.raportointi.integration.OrganisaatioService
import fi.oph.viestinvalitys.raportointi.security.SecurityConstants.{OPH_ORGANISAATIO_OID, SECURITY_ROOLI_PAAKAYTTAJA, SESSION_ATTR_KAYTTOOIKEUDET, SESSION_ATTR_KAYTTOOIKEUSTUNNISTEET, SESSION_ATTR_UUSIN_KAYTTOOIKEUSTUNNISTE}
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
  final val VIESTINVALITYS_ROLES = Set(SECURITY_ROOLI_KATSELU_OIKEUS, SECURITY_ROOLI_LAHETYS_OIKEUS, SECURITY_ROOLI_PAAKAYTTAJA_OIKEUS)

  final val SESSION_ATTR_KAYTTOOIKEUDET = "kayttooikeudet"
  final val SESSION_ATTR_KAYTTOOIKEUSTUNNISTEET = "kayttooikeustunnisteet"
  final val SESSION_ATTR_UUSIN_KAYTTOOIKEUSTUNNISTE = "uusintunniste"
}

class SecurityOperaatiot(
                          getOikeudet: () => Seq[String] = () => SecurityContextHolder.getContext.getAuthentication.getAuthorities.asScala.map(a => a.getAuthority).toSeq,
                          getUsername: () => String = () => SecurityContextHolder.getContext.getAuthentication.getName(),
                          organisaatioClient: OrganisaatioService = OrganisaatioService,
                          kantaOperaatiot: KantaOperaatiot = new KantaOperaatiot(DbUtil.database),
                          httpSession: HttpSession) {

  val LOG = LoggerFactory.getLogger(classOf[SecurityOperaatiot])
  final val SECURITY_ROOLI_PREFIX_PATTERN = "^ROLE_"
  private lazy val kayttajanCasOikeudet: Set[Kayttooikeus] = {
    parseKayttooikeudet(getOikeudet()
      .map(a => a.replaceFirst(SECURITY_ROOLI_PREFIX_PATTERN, "")))
  }

  // säilytetään käyttöoikeudet sessiossa
  // koska niiden parsiminen aliorganisaatiotasolle kestää jos on runsaasti aliorganisaatiotasoja
  private lazy val kayttajanOikeudet: Set[Kayttooikeus] = {
    val pk = onPaakayttaja()
    if(onPaakayttaja())
      kayttajanCasOikeudet // ei tarvitse mapata kaikkia lapsiorganisaatioita
    else
      Option(httpSession.getAttribute(SESSION_ATTR_KAYTTOOIKEUDET)) match {
        case Some(_) =>
          LOG.warn("oikat sessiosta!")
          parseTypedKayttooikeusSetFromSession(httpSession, SESSION_ATTR_KAYTTOOIKEUDET).getOrElse(Set.empty)

        case None =>
          val viestienOikeusrajoitukset = kantaOperaatiot.getKaikkikayttooikeudet()
          val relevantitKayttajanOikeudet = viestienOikeusrajoitukset.intersect(kayttajanCasOikeudet) ++
            kayttajanCasOikeudet.filter(_.oikeus.startsWith("APP_VIESTINVALITYS"))

          val parentOikeudet = viestienOikeusrajoitukset
            .filterNot(kayttajanCasOikeudet)
            .filter(hasOikeusForParentOrg(_, kayttajanCasOikeudet))

          val mergedKayttooikeudet = relevantitKayttajanOikeudet ++ parentOikeudet

          httpSession.setAttribute(SESSION_ATTR_KAYTTOOIKEUDET, mergedKayttooikeudet)
          mergedKayttooikeudet
      }
  }

  // säilytetään käyttöoikeustunnisteet sessiossa
  // ja virkistetään kannasta vain jos on tullut uusia sen jälkeen kun tunnisteet laitettiin sessioon
  private lazy val kayttajanKayttooikeustunnisteet: Option[Set[Int]] = {
    val pk = onPaakayttaja()
    if (onPaakayttaja())
      Option.empty
    else
      if (httpSession.getAttribute(SESSION_ATTR_KAYTTOOIKEUSTUNNISTEET) != null
        && eiUusiaTunnisteitaKannassa(httpSession, kantaOperaatiot))
        LOG.warn("oikkatunnisteet sessiosta!")
        parseTypedTunnisteAttributeFromSession(httpSession, SESSION_ATTR_KAYTTOOIKEUSTUNNISTEET)
      else
        LOG.warn("oikkatunnisteet kannasta ja laitetaan sessioon!")
        val (tunnisteet, uusinTunniste) = kantaOperaatiot.getKayttooikeusTunnisteet(kayttajanOikeudet.toSeq)
        httpSession.setAttribute(SESSION_ATTR_KAYTTOOIKEUSTUNNISTEET, tunnisteet)
        httpSession.setAttribute(SESSION_ATTR_UUSIN_KAYTTOOIKEUSTUNNISTE, uusinTunniste)
        Some(tunnisteet)
  }

  val identiteetti = getUsername()

  def getIdentiteetti(): String =
    identiteetti

  def onOikeusLahettaaEntiteetti(omistaja: String, entiteetinOikeudet: Set[Kayttooikeus]): Boolean =
    if (identiteetti.equals(omistaja) || onPaakayttaja())
      true
    else
      onOikeusLahettaa() && entiteetinOikeudet.intersect(kayttajanOikeudet).nonEmpty

  def onOikeusKatsellaEntiteetti(omistaja: String, entiteetinOikeudet: Set[Kayttooikeus]): Boolean =
    if (identiteetti.equals(omistaja) || onPaakayttaja())
      true
    else
      onOikeusKatsella() && entiteetinOikeudet.intersect(kayttajanOikeudet).nonEmpty

  /**
   * Tarkastelee pelkkää käyttöoikeusroolia, ei huomioi organisaatiorajoituksia
   */
  def onOikeusLahettaa(): Boolean =
    SecurityConstants.LAHETYS_ROLES.intersect(kayttajanCasOikeudet).nonEmpty
  /**
   * Tarkastelee pelkkää käyttöoikeusroolia, ei huomioi organisaatiorajoituksia
   */
  def onOikeusKatsella(): Boolean =
    SecurityConstants.KATSELU_ROLES.intersect(kayttajanCasOikeudet).nonEmpty

  def onPaakayttaja(): Boolean =
    kayttajanCasOikeudet.exists(ko => OPH_ORGANISAATIO_OID.equals(ko.organisaatio.orNull) &&
      SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA.equals(ko.oikeus))

  def getKayttajanOikeudet(): Set[Kayttooikeus] = kayttajanOikeudet

  def getKayttajanKayttooikeustunnisteet(): Option[Set[Int]] = kayttajanKayttooikeustunnisteet

  /**
   * Palauttaa käyttäjän käyttöoikeuksien organisaatiot ilman lapsihierarkiaa
   */
  def getCasOrganisaatiot(): Set[String] =
    kayttajanCasOikeudet.filter(kayttooikeus => kayttooikeus.organisaatio.isDefined).map(ko => ko.organisaatio.get)

  def parseTypedTunnisteAttributeFromSession(session: HttpSession, attributeName: String): Option[Set[Int]] = {
    Option(session.getAttribute(attributeName)) match {
      case Some(value: Set[_]) =>
        // Safely cast elements to Int, filter out invalid ones
        Some(value.collect { case i: Int => i })
      case _ =>
        None
    }
  }

  def parseTypedKayttooikeusSetFromSession(session: HttpSession, attributeName: String): Option[Set[Kayttooikeus]] = {
    Option(session.getAttribute(attributeName)) match {
      case Some(value: Set[_]) =>
        // Safely cast elements to Kayttooikeus, filter out invalid ones
        Some(value.collect { case i: Kayttooikeus => i })
      case _ =>
        None
    }
  }

  def eiUusiaTunnisteitaKannassa(session: HttpSession, kantaOperaatiot: KantaOperaatiot): Boolean =
    val uusin = kantaOperaatiot.getUusinKayttooikeusTunniste()
    uusin == session.getAttribute(SESSION_ATTR_UUSIN_KAYTTOOIKEUSTUNNISTE).asInstanceOf[Int]

  def parseKayttooikeudet(authorities: Seq[String]): Set[Kayttooikeus] =
    authorities.map(a => {
        val organisaatioOikeus = SecurityConstants.KAYTTOOIKEUSPATTERN.findFirstMatchIn(a)
        if (organisaatioOikeus.isDefined)
          Kayttooikeus(organisaatioOikeus.get.group(1), Option.apply(organisaatioOikeus.get.group(2)))
        else
          Kayttooikeus(a, Option.empty)
      })
      .toSet

  def hasOikeusForParentOrg(puuttuvaKayttooikeus: Kayttooikeus, kayttajanOikeudet: Set[Kayttooikeus]): Boolean =
    // on oph-tason oikat tai vastaava oikeus parent-organisaatioon
    kayttajanOikeudet.contains(Kayttooikeus(puuttuvaKayttooikeus.oikeus, Some(OPH_ORGANISAATIO_OID))) ||
    organisaatioClient
      .getParentOids(puuttuvaKayttooikeus.organisaatio.get)
      .exists(parentOid => kayttajanOikeudet.contains(Kayttooikeus(puuttuvaKayttooikeus.oikeus, Some(parentOid))))
}
