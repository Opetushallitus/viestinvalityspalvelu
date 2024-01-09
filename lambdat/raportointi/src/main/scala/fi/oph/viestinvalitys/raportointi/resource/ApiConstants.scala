package fi.oph.viestinvalitys.raportointi.resource

import fi.oph.viestinvalitys.raportointi.security.SecurityConstants

import java.util.UUID
import scala.jdk.CollectionConverters.*

object APIConstants {

  /**
   * Lähetys-API:n endpointtien polkuihin liittyvät vakiot
   */
  final val RAPORTOINTI_API_PREFIX            = "/raportointi/v1"

  final val LAHETYKSET_PATH                   = RAPORTOINTI_API_PREFIX + "/lahetykset"
  final val LAHETYSTUNNISTE_PARAM_NAME        = "lahetysTunniste"
  final val LAHETYSTUNNISTE_PARAM_PLACEHOLDER = "{" + LAHETYSTUNNISTE_PARAM_NAME + "}"
  final val GET_LAHETYS_PATH                  = LAHETYKSET_PATH + "/" + LAHETYSTUNNISTE_PARAM_PLACEHOLDER

  final val GET_VASTAANOTTAJAT_PATH           = GET_LAHETYS_PATH + "/vastaanottajat"
  final val ALKAEN_PARAM_NAME                 = "alkaen"
  final val ENINTAAN_PARAM_NAME               = "enintaan"

  final val VIESTIT_PATH                      = RAPORTOINTI_API_PREFIX + "/viestit"
  final val VIESTITUNNISTE_PARAM_NAME         = "viestiTunniste"
  final val VIESTITUNNISTE_PARAM_PLACEHOLDER  = "{" + VIESTITUNNISTE_PARAM_NAME + "}"
  final val GET_VIESTI_PATH                   = VIESTIT_PATH + "/" + VIESTITUNNISTE_PARAM_PLACEHOLDER
  final val GET_VIESTIT_LAHETYSTUNNISTEELLA_PATH  = VIESTIT_PATH + "/" + LAHETYSTUNNISTE_PARAM_PLACEHOLDER

  final val HEALTHCHECK_PATH                  = RAPORTOINTI_API_PREFIX + "/healthcheck"

  /**
   * Swagger-kuvauksiin liittyvät vakiot
   */
  final val RESPONSE_400_DESCRIPTION = "Pyyntö virheellinen, palauttaa listan pyynnössä olevista virheistä"
  final val LAHETYS_RESPONSE_403_DESCRIPTION = "Käyttäjällä ei ole " + SecurityConstants.SECURITY_ROOLI_LAHETYS + "-oikeutta"
  final val KATSELU_RESPONSE_403_DESCRIPTION = "Käyttäjällä ei ole oikeutta lukea entiteettiä. Oikeus on jos:\n" +
    "- Käyttäjällä on " + SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA + "-oikeus, tai\n" +
    "- Käyttäjä luonut entiteetin, tai\n" +
    "- Käyttäjällä on " + SecurityConstants.SECURITY_ROOLI_KATSELU + "-oikeus, ja lisäksi jokin entiteetin luonnin yhteydessä" +
    " liitetyistä lukuoikeuksista"
  final val KATSELU_RESPONSE_410_DESCRIPTION = "Entiteettiä ei löytynyt, tunniste on virheellinen tai entiteetti on poistettu säilytysajan päätyttyä"

  final val EXAMPLE_LAHETYSTUNNISTE_VALIDOINTIVIRHE = "[ \"" + LAHETYSTUNNISTE_INVALID + "\" ]"

  final val ESIMERKKI_LAHETYSTUNNISTE = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
  final val ESIMERKKI_LIITETUNNISTE = "3fa85f64-5717-4562-b3fc-2c963f66afa6"


  /**
   * Parametreihin liittyvät vakiot
   */
  final val VASTAANOTTAJAT_ENINTAAN_MIN_STR   = "1"
  final val VASTAANOTTAJAT_ENINTAAN_MAX_STR   = "512"
  final val VASTAANOTTAJAT_ENINTAAN_MIN       = VASTAANOTTAJAT_ENINTAAN_MIN_STR.toInt
  final val VASTAANOTTAJAT_ENINTAAN_MAX       = VASTAANOTTAJAT_ENINTAAN_MAX_STR.toInt
  final val VASTAANOTTAJAT_ENINTAAN_DEFAULT   = VASTAANOTTAJAT_ENINTAAN_DEFAULT_STR.toInt
  final val VASTAANOTTAJAT_ENINTAAN_DEFAULT_STR  = "256"

  /**
   * Virhetilanteisiin liittyvät vakiot
   */
  final val VIESTITUNNISTE_INVALID            = "Tunniste ei ole muodoltaan validi uuid"
  final val LAHETYSTUNNISTE_INVALID           = "Lahetystunniste ei ole muodoltaan validi uuid"

  final val ALKAEN_TUNNISTE_INVALID           = ALKAEN_PARAM_NAME + "-parametri: Tunniste ei ole muodoltaan validi uuid"
  final val ENINTAAN_INVALID                  = ENINTAAN_PARAM_NAME + "-parametri: Arvon pitää olla numero väliltä " + VASTAANOTTAJAT_ENINTAAN_MIN_STR + "-" + VASTAANOTTAJAT_ENINTAAN_MAX_STR
}
