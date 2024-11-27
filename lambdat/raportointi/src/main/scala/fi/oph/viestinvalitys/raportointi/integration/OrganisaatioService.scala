package fi.oph.viestinvalitys.raportointi.integration

import fi.oph.viestinvalitys.raportointi.security.SecurityOperaatiot
import org.slf4j.LoggerFactory
import sttp.client4.Response
import upickle.default.*

object OrganisaatioService extends OrganisaatioService
class OrganisaatioService {

  val LOG = LoggerFactory.getLogger(classOf[OrganisaatioService])

  def getAllChildOidsFlat(oid: String): Set[String] =
    LOG.info(s"Haetaan lapsiorganisaatiot cachesta oidille $oid")
    if (!OrganisaatioOid.isValid(oid))
      LOG.error(s"Organisaation oid $oid on virheellinen")
      throw new RuntimeException(s"Organisaation oid $oid on virheellinen")
    val response: Response[String] = OrganisaatioCache.childOidsCache.get(oid)
    response.code.code match
      case 200 => read[List[String]](response.body).toSet
      case _ =>
        LOG.error(s"organisaatioiden haku epäonnistui, status ${response.code.code} error ${response.statusText}")
        throw new RuntimeException(s"Organisaatioiden haku epäonnistui: ${response.statusText}")

  def getOrganisaatioHierarkia(): List[Organisaatio] =
    val securityOperaatiot = new SecurityOperaatiot
    val response: Response[String] = OrganisaatioCache.orgHierarkiaCache.get(securityOperaatiot.getCasOrganisaatiot())
    response.code.code match
      case 200 =>
        val orgs = read[OrganisaatioHierarkia](response.body)
        read[OrganisaatioHierarkia](response.body).organisaatiot
      case _ =>
        LOG.error(s"organisaatioiden haku epäonnistui, status ${response.code.code} error ${response.statusText}")
        throw new RuntimeException(s"Organisaatioiden haku epäonnistui: ${response.statusText}")
}
