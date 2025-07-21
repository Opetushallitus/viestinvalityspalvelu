package fi.oph.viestinvalitys.raportointi.integration

import org.slf4j.LoggerFactory
import sttp.client4.Response
import upickle.default.*

object OrganisaatioService extends OrganisaatioService
class OrganisaatioService {

  val LOG = LoggerFactory.getLogger(classOf[OrganisaatioService])

  def getAllChildOidsFlat(oid: String): Set[String] =
    LOG.info(s"Haetaan lapsiorganisaatiot cachesta oidille $oid")
    if (!OrganisaatioOid.isValid(oid))
      LOG.error(s"Organisaation oid $oid on virheellinen, ei voitu hakea lapsiorganisaatioita")
      throw new RuntimeException(s"Organisaation oid $oid on virheellinen")
    val response: Response[String] = OrganisaatioCache.childOidsCache.get(oid)
    response.code.code match
      case 200 => read[List[String]](response.body).toSet
      case _ =>
        LOG.error(s"lapsiorganisaatioiden haku epäonnistui, status ${response.code.code} error ${response.statusText}")
        throw new RuntimeException(s"Organisaatioiden haku epäonnistui: ${response.statusText}")

  def getParentOids(oid: String): Set[String] =
    LOG.info(s"Haetaan parent-organisaatiot cachesta oidille $oid")
    if (!OrganisaatioOid.isValid(oid))
      // HUOM! toistaiseksi ei heitetä poikkeusta koska esim testiympäristöjen datassa on
      // käyttöoikeusrajoituksissa epävalideja organisaatio-oideja
      LOG.error(s"Organisaation oid $oid on virheellinen, ei voitu hakea parent-organisaatioita")
      Set.empty
    else
      val response: Response[String] = OrganisaatioCache.parentOidsCache.get(oid)
      response.code.code match
        case 200 => read[List[String]](response.body).toSet
        case _ =>
          LOG.error(s"parent-organisaatioiden haku epäonnistui, status ${response.code.code} error ${response.statusText}")
          throw new RuntimeException(s"Organisaatioiden haku epäonnistui: ${response.statusText}")

}
