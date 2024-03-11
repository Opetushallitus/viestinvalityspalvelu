package fi.oph.viestinvalitys.raportointi.integration

import scala.util.matching.Regex
import upickle.default.*

object OrganisaatioOid {

  val organisaatioOidPattern: Regex = "^1\\.2\\.246\\.562\\.(10|99)\\.\\d+$".r
  def isValid(oid: String): Boolean = organisaatioOidPattern.matches(oid)
}

case class Organisaatio(oid: String,
                        parentOid: String,
                        parentOidPath: String,
                        nimi: Map[String, String],
                        status: String,
                        children: List[Organisaatio] = List()) derives ReadWriter {

}

case class OrganisaatioHierarkia(organisaatiot: List[Organisaatio]) derives ReadWriter

