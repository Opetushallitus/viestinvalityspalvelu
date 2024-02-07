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
      val organisaatioOikeudet = kayttooikeudet.map(oikeus => "'" + oikeus.organisaatio.getOrElse("") + "_" + oikeus.oikeus + "'").mkString(",")
      s"""AND kayttooikeudet.organisaatio || '_' || kayttooikeudet.oikeus IN ($organisaatioOikeudet)"""
    }

  def vastaanottajanTilaWhere(raportointiTila: Option[String]): String =
    raportointiTila match
      case Some("epaonnistui") => s""" AND vastaanottajat.tila IN (${raportointiTilat.epaonnistuneet.map(tila => "'"+tila.toString+"'").mkString(",")})"""
      case Some("kesken") => s""" AND vastaanottajat.tila IN (${raportointiTilat.kesken.map(tila => "'"+tila.toString+"'").mkString(",")})"""
      case Some("valmis") => s""" AND vastaanottajat.tila IN (${raportointiTilat.valmiit.map(tila => "'"+tila.toString+"'").mkString(",")})"""
      case None => ""
}
