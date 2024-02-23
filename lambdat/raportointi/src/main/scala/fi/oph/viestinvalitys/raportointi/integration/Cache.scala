package fi.oph.viestinvalitys.raportointi.integration

import com.google.common.cache.{CacheBuilder, CacheLoader, LoadingCache}
import org.slf4j.LoggerFactory
import sttp.client4.quick.*
import sttp.client4.Response
import sttp.model.Uri

import java.util.concurrent.TimeUnit

object OrganisaatioCache extends OrganisaatioCache
class OrganisaatioCache {
  val callerId: String = "1.2.246.562.10.00000000001.viestinvalitys-raportointi"
  val headers: Map[String, String] = Map("Caller-Id" -> callerId, "CSRF" -> callerId)
  val queryParams =
    Map("rekursiivisesti" -> "true", "aktiiviset" -> "true", "suunnitellut" -> "false", "lakkautetut" -> "false")

  val LOG = LoggerFactory.getLogger(classOf[OrganisaatioCache])

  val childOidsLoader = new CacheLoader[String, Response[String]] {
    def load(oid: String): Response[String] =
      // TODO url konfiguraatioihin
      val uri: Uri = uri"https://virkailija.testiopintopolku.fi/organisaatio-service/api/$oid/childoids?$queryParams"
      quickRequest
        .headers(headers)
        .cookie("CSRF", callerId)
        .get(uri)
        .send()
  }

  // TODO asetukset konffeihin?
  val childOidsCache: LoadingCache[String, Response[String]] = CacheBuilder.newBuilder()
    .maximumSize(1500)
    .expireAfterAccess(60, TimeUnit.MINUTES)
    .build(childOidsLoader)
}

