package fi.oph.viestinvalitys.util

import fi.oph.viestinvalitys.business.Kayttooikeus
import fi.oph.viestinvalitys.business.*
import org.slf4j.LoggerFactory

object queryUtil {

  val LOG = LoggerFactory.getLogger(classOf[KantaOperaatiot])
  private def isPaakayttaja(kayttooikeudet: Set[Kayttooikeus]): Boolean =
    kayttooikeudet.map(ko => ko.oikeus)("VIESTINVALITYS_OPH_PAAKAYTTAJA")
  def lahetyksenKayttooikeudetJoin(kayttooikeudet: Set[Kayttooikeus]): String =
    if isPaakayttaja(kayttooikeudet)
      then ""
    else
      """ JOIN lahetykset_kayttooikeudet ON lahetykset_kayttooikeudet.lahetys_tunniste=lahetykset.tunniste JOIN kayttooikeudet ON lahetykset_kayttooikeudet.kayttooikeus_tunniste=kayttooikeudet.tunniste"""

  def viestinKayttooikeudetJoin(kayttooikeudet: Set[Kayttooikeus]): String =
    if isPaakayttaja(kayttooikeudet) then ""
    else
      """ JOIN viestit_kayttooikeudet ON viestit_kayttooikeudet.viesti_tunniste=viestit.tunniste JOIN kayttooikeudet ON viestit_kayttooikeudet.kayttooikeus_tunniste=kayttooikeudet.tunniste"""

  def kayttooikeudetWhere(kayttooikeudet: Set[Kayttooikeus]): String =
    if isPaakayttaja(kayttooikeudet) then ""
    else {
      // (HAKEMUS_CRUD_1.2.246.562.10.93252924118, VIESTINVALITYS_KATSELU_1.2.246.562.10.93252924118)
      val organisaatioOikeudet = kayttooikeudet.filter(kayttooikeus => kayttooikeus.organisaatio.isDefined)
        .map(oikeus => "'" + oikeus.oikeus + "_" + oikeus.organisaatio.get + "'").mkString(",")
      // (HAKEMUS_CRUD, VIESTINVALITYS_KATSELU), käyttäjälle tulee aina myös pelkät roolit
      val oikeudetIlmanOrganisaatiota = kayttooikeudet.filter(kayttooikeus => kayttooikeus.organisaatio.isEmpty)
        .map(oikeus => "'" + oikeus.oikeus + "'").mkString(",")
      val loppuehto = if (oikeudetIlmanOrganisaatiota.isEmpty) "" else s""" OR (kayttooikeudet.organisaatio IS NULL AND kayttooikeudet.oikeus IN ($oikeudetIlmanOrganisaatiota))"""
      s"""AND ((kayttooikeudet.organisaatio IS NOT NULL AND kayttooikeudet.oikeus || '_' || kayttooikeudet.organisaatio IN ($organisaatioOikeudet)) $loppuehto)""".stripMargin
    }

  def vastaanottajanTilaWhere(raportointiTila: Option[String]): String =
    raportointiTila match
      case Some("epaonnistui") => s""" AND vastaanottajat.tila IN (${raportointiTilat.epaonnistuneet.map(tila => "'"+tila.toString+"'").mkString(",")})"""
      case Some("kesken") => s""" AND vastaanottajat.tila IN (${raportointiTilat.kesken.map(tila => "'"+tila.toString+"'").mkString(",")})"""
      case Some("valmis") => s""" AND vastaanottajat.tila IN (${raportointiTilat.valmiit.map(tila => "'"+tila.toString+"'").mkString(",")})"""
      case None => ""

  def sessionTableName(serviceName: String): String =
    s"${serviceName}_cas_client_session"

  def sessionIdAttributeName(serviceName: String): String =
    s"${serviceName}_session_id"
}
