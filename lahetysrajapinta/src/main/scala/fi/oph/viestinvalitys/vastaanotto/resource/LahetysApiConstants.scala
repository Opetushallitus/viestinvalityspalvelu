package fi.oph.viestinvalitys.vastaanotto.resource

import fi.oph.viestinvalitys.vastaanotto.model.ViestiValidator
import fi.oph.viestinvalitys.vastaanotto.security.SecurityConstants

import java.util.UUID
import scala.jdk.CollectionConverters.*

object LahetysAPIConstants {

  /**
   * Lähetys-API:n endpointtien polkuihin liittyvät vakiot
   */
  final val LAHETYS_API_PREFIX                = "/lahetys"
  final val VERSIONED_LAHETYS_API_PREFIX      = LAHETYS_API_PREFIX + "/v1"

  final val LAHETYKSET_PATH                   = VERSIONED_LAHETYS_API_PREFIX + "/lahetykset"
  final val LUO_LAHETYS_PATH                  = LAHETYKSET_PATH
  final val LAHETYSTUNNISTE_PARAM_NAME        = "lahetysTunniste"
  final val LAHETYSTUNNISTE_PARAM_PLACEHOLDER = "{" + LAHETYSTUNNISTE_PARAM_NAME + "}"
  final val GET_LAHETYS_PATH                  = LAHETYKSET_PATH + "/" + LAHETYSTUNNISTE_PARAM_PLACEHOLDER

  final val GET_VASTAANOTTAJAT_PATH           = GET_LAHETYS_PATH + "/vastaanottajat"
  final val ALKAEN_PARAM_NAME                 = "alkaen"
  final val ENINTAAN_PARAM_NAME               = "enintaan"

  final val LIITTEET_PATH                     = VERSIONED_LAHETYS_API_PREFIX + "/liitteet"
  final val LUO_LIITE_PATH                    = LIITTEET_PATH

  final val VIESTIT_PATH                      = VERSIONED_LAHETYS_API_PREFIX + "/viestit"
  final val LUO_VIESTI_PATH                   = VIESTIT_PATH
  final val VIESTITUNNISTE_PARAM_NAME         = "viestiTunniste"
  final val VIESTITUNNISTE_PARAM_PLACEHOLDER  = "{" + VIESTITUNNISTE_PARAM_NAME + "}"
  final val GET_VIESTI_PATH                   = VIESTIT_PATH + "/" + VIESTITUNNISTE_PARAM_PLACEHOLDER

  final val LOGIN_PATH                        = LAHETYS_API_PREFIX + "/login"
  final val HEALTHCHECK_PATH                  = VERSIONED_LAHETYS_API_PREFIX + "/healthcheck"

  /**
   * Swagger-kuvauksiin liittyvät vakiot
   */
  final val RESPONSE_400_DESCRIPTION          = "Pyyntö virheellinen, palauttaa listan pyynnössä olevista virheistä"
  final val LAHETYS_RESPONSE_403_DESCRIPTION  = "Käyttäjällä ei ole " + SecurityConstants.SECURITY_ROOLI_LAHETYS + "-oikeutta"
  final val KATSELU_RESPONSE_403_DESCRIPTION  = "Käyttäjällä ei ole oikeutta lukea entiteettiä. Oikeus on jos:\n" +
    "- Käyttäjällä on " + SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA + "-oikeus, tai\n" +
    "- Käyttäjä luonut entiteetin"
  final val KATSELU_RESPONSE_410_DESCRIPTION  = "Entiteettiä ei löytynyt, tunniste on virheellinen tai entiteetti on poistettu säilytysajan päätyttyä"

  final val EXAMPLE_OTSIKKO_VALIDOINTIVIRHE   = "[ \"" + ViestiValidator.VALIDATION_OTSIKKO_TYHJA + "\" ]"

  final val EXAMPLE_LAHETYSTUNNISTE_VALIDOINTIVIRHE = "[ \"" + LAHETYSTUNNISTE_INVALID + "\" ]"

  final val ESIMERKKI_LAHETYSTUNNISTE         = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
  final val ESIMERKKI_LIITETUNNISTE           = "3fa85f64-5717-4562-b3fc-2c963f66afa6"

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
  final val VIRHEELLINEN_LAHETYS_JSON_VIRHE   = "Lähetyksen json-deserialisointi epäonnistui"
  final val VIRHEELLINEN_VIESTI_JSON_VIRHE    = "Viestin json-deserialisointi epäonnistui"

  final val LIITE_VIRHE_LIITE_PUUTTUU         = "Pyynnöstä puuttuu liite-niminen multipart-osio"
  final val LIITE_VIRHE_JARJESTELMAVIRHE      = "Järjestelmävirhe, jos virhe toistuu ole yhteydessä palvelun ylläpitoon."

  final val ALKAEN_TUNNISTE_INVALID           = ALKAEN_PARAM_NAME + "-parametri: Tunniste ei ole muodoltaan validi uuid"
  final val ENINTAAN_INVALID                  = ENINTAAN_PARAM_NAME + "-parametri: Arvon pitää olla numero väliltä " + VASTAANOTTAJAT_ENINTAAN_MIN_STR + "-" + VASTAANOTTAJAT_ENINTAAN_MAX_STR

  /**
   * Korkean prioriteetin viestien ratelimitteriin liittyvät vakiot
   */
  final val VIESTI_RATELIMIT_VIRHE = "Liikaa korkean prioriteetin lähetyspyyntöjä"

  final val PRIORITEETTI_KORKEA_RATELIMIT_VIESTIA_SEKUNNISSA      = 1
  final val PRIORITEETTI_KORKEA_RATELIMIT_AIKAIKKUNA_SEKUNTIA     = 5
  final val PRIORITEETTI_KORKEA_RATELIMIT_VIESTEJA_AIKAIKKUNASSA  =
    PRIORITEETTI_KORKEA_RATELIMIT_VIESTIA_SEKUNNISSA * PRIORITEETTI_KORKEA_RATELIMIT_AIKAIKKUNA_SEKUNTIA
}
