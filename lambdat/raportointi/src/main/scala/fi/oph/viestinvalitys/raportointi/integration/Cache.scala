package fi.oph.viestinvalitys.raportointi.integration

import com.google.common.cache.{CacheBuilder, CacheLoader, LoadingCache}
import fi.oph.viestinvalitys.raportointi.App
import fi.oph.viestinvalitys.util.ConfigurationUtil
import org.slf4j.LoggerFactory
import scala.concurrent.duration._
import sttp.client4.quick.*
import sttp.client4.Response
import sttp.model.Uri

import java.util.concurrent.TimeUnit

object OrganisaatioCache extends OrganisaatioCache
class OrganisaatioCache {
  val opintopolkuDomain = ConfigurationUtil.opintopolkuDomain
  val headers: Map[String, String] = Map("Caller-Id" -> App.CALLER_ID, "CSRF" -> App.CALLER_ID)
  val queryParams =
    Map("rekursiivisesti" -> "true", "aktiiviset" -> "true", "suunnitellut" -> "false", "lakkautetut" -> "false")
  val hierarkiaQueryParams = Map("aktiiviset" -> "true", "suunnitellut" -> "true", "lakkautetut" -> "false", "skipParents" -> "false")
  val LOG = LoggerFactory.getLogger(classOf[OrganisaatioCache])

  val childOidsLoader = new CacheLoader[String, Response[String]] {
    def load(oid: String): Response[String] =
      val uri: Uri = uri"https://virkailija.$opintopolkuDomain/organisaatio-service/api/$oid/childoids?$queryParams"
      quickRequest
        .headers(headers)
        .cookie("CSRF", App.CALLER_ID)
        .readTimeout(2.minutes)
        .get(uri)
        .send()
  }

  val orgHierarkiaLoader = new CacheLoader[Set[String], Response[String]] {
    def load(oids: Set[String]): Response[String] =
      val uri: Uri = uri"https://virkailija.$opintopolkuDomain/organisaatio-service/api/hierarkia/hae?oidRestrictionList=$oids&$hierarkiaQueryParams"
      quickRequest
        .headers(headers)
        .cookie("CSRF", App.CALLER_ID)
        .get(uri)
        .send()
  }

  val childOidsCache: LoadingCache[String, Response[String]] = CacheBuilder.newBuilder()
    .maximumSize(1500)
    .expireAfterAccess(60, TimeUnit.MINUTES)
    .build(childOidsLoader)

  val orgHierarkiaCache: LoadingCache[Set[String], Response[String]] = CacheBuilder.newBuilder()
    .maximumSize(1500)
    .expireAfterAccess(60, TimeUnit.MINUTES)
    .build(orgHierarkiaLoader)
}

