package fi.oph.viestinvalitys.raportointi.integration

import scala.util.matching.Regex

object OrganisaatioOid {

  val organisaatioOidPattern: Regex = "^1\\.2\\.246\\.562\\.(10|99)\\.\\d+$".r
  def isValid(oid: String): Boolean = organisaatioOidPattern.matches(oid)
}

case class Organisaatio(oid: String,
                        parentOidPath: String,
                        oppilaitostyyppi: Option[String] = None,
                        nimi: Map[String, String],
                        status: String,
                        kotipaikkaUri: Option[String] = None,
                        children: List[Organisaatio] = List(),
                        organisaatiotyypit: List[String] = List(),
                        tyypit: List[String] = List()) {

}

case class OrganisaatioHierarkia(organisaatiot: List[Organisaatio])
