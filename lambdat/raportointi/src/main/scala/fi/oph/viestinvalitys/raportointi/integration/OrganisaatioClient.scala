package fi.oph.viestinvalitys.raportointi.integration

import com.google.common.cache.*
import fi.oph.viestinvalitys.raportointi.security.SecurityOperaatiot
import org.slf4j.LoggerFactory
import sttp.client4.Response
import upickle.default.*

import java.util.concurrent.TimeUnit

object OrganisaatioClient extends OrganisaatioClient
class OrganisaatioClient {

  val LOG = LoggerFactory.getLogger(classOf[OrganisaatioClient])

  def getAllChildOidsFlat(oid: String): Set[String] =
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
    val foo = securityOperaatiot.getCasOrganisaatiot()
    val response: Response[String] = OrganisaatioCache.orgHierarkiaCache.get(securityOperaatiot.getCasOrganisaatiot())
    response.code.code match
      case 200 =>
        val orgs = read[OrganisaatioHierarkia](response.body)
        read[OrganisaatioHierarkia](response.body).organisaatiot
      case _ =>
        LOG.error(s"organisaatioiden haku epäonnistui, status ${response.code.code} error ${response.statusText}")
        throw new RuntimeException(s"Organisaatioiden haku epäonnistui: ${response.statusText}")
}
