package fi.oph.viestinvalitys.raportointi.resource

import fi.oph.viestinvalitys.raportointi.security.SecurityConstants


object RaportointiAPIConstants {

  /**
   * Lähetys-API:n endpointtien polkuihin liittyvät vakiot
   */
  final val RAPORTOINTI_API_PREFIX            = "/raportointi"
  final val VERSIONED_RAPORTOINTI_API_PREFIX  = RAPORTOINTI_API_PREFIX + "/v1"

  final val LAHETYKSET_PATH                   = VERSIONED_RAPORTOINTI_API_PREFIX + "/lahetykset"
  final val LAHETYSTUNNISTE_PARAM_NAME        = "lahetysTunniste"
  final val LAHETYSTUNNISTE_PARAM_PLACEHOLDER = "{" + LAHETYSTUNNISTE_PARAM_NAME + "}"
  final val GET_LAHETYS_PATH                  = LAHETYKSET_PATH + "/" + LAHETYSTUNNISTE_PARAM_PLACEHOLDER
  final val GET_LAHETYKSET_LISTA_PATH         = LAHETYKSET_PATH + "/" + "lista"

  final val GET_VASTAANOTTAJAT_PATH           = GET_LAHETYS_PATH + "/vastaanottajat"
  final val ALKAEN_PARAM_NAME                 = "alkaen"
  final val SIVUTUS_TILA_PARAM_NAME           = "sivutustila"
  final val ENINTAAN_PARAM_NAME               = "enintaan"
  final val TILA_PARAM_NAME                   = "tila"
  final val VASTAANOTTAJA_PARAM_NAME          = "vastaanottaja"
  final val ORGANISAATIO_PARAM_NAME           = "organisaatio"

  final val VIESTI_PATH                      = VERSIONED_RAPORTOINTI_API_PREFIX + "/viesti"
  final val VIESTITUNNISTE_PARAM_NAME         = "viestiTunniste"
  final val VIESTITUNNISTE_PARAM_PLACEHOLDER  = "{" + VIESTITUNNISTE_PARAM_NAME + "}"
  final val GET_VIESTI_PATH                   = VIESTI_PATH + "/" + VIESTITUNNISTE_PARAM_PLACEHOLDER
  final val GET_VIESTI_LAHETYSTUNNISTEELLA_PATH  = VERSIONED_RAPORTOINTI_API_PREFIX + "/massaviesti" + "/" + LAHETYSTUNNISTE_PARAM_PLACEHOLDER

  final val LOGIN_PATH                        = RAPORTOINTI_API_PREFIX + "/login"
  final val HEALTHCHECK_PATH                  = VERSIONED_RAPORTOINTI_API_PREFIX + "/healthcheck"
  final val ORGANISAATIOT_PATH                = VERSIONED_RAPORTOINTI_API_PREFIX + "/organisaatiot"
  final val ORGANISAATIOT_OIKEUDET_PATH       = ORGANISAATIOT_PATH + "/oikeudet"
  final val OMAT_TIEDOT_PATH                  = VERSIONED_RAPORTOINTI_API_PREFIX + "/omattiedot"

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
  final val LAHETYKSET_ENINTAAN_MIN_STR = "1"
  final val LAHETYKSET_ENINTAAN_MAX_STR = "512"
  final val LAHETYKSET_ENINTAAN_MIN = VASTAANOTTAJAT_ENINTAAN_MIN_STR.toInt
  final val LAHETYKSET_ENINTAAN_MAX = VASTAANOTTAJAT_ENINTAAN_MAX_STR.toInt
  final val LAHETYKSET_ENINTAAN_DEFAULT = VASTAANOTTAJAT_ENINTAAN_DEFAULT_STR.toInt
  final val LAHETYKSET_ENINTAAN_DEFAULT_STR = "256"
  final val VASTAANOTTAJAT_ENINTAAN_MIN_STR   = "1"
  final val VASTAANOTTAJAT_ENINTAAN_MAX_STR   = "512"
  final val VASTAANOTTAJAT_ENINTAAN_MIN       = VASTAANOTTAJAT_ENINTAAN_MIN_STR.toInt
  final val VASTAANOTTAJAT_ENINTAAN_MAX       = VASTAANOTTAJAT_ENINTAAN_MAX_STR.toInt
  final val VASTAANOTTAJAT_ENINTAAN_DEFAULT   = VASTAANOTTAJAT_ENINTAAN_DEFAULT_STR.toInt
  final val VASTAANOTTAJAT_ENINTAAN_DEFAULT_STR  = "256"
  final val emailRegex = "^[^\\s,@]+@(([a-zA-Z\\-0-9])+\\.)+([a-zA-Z\\-0-9]){2,}$".r

  /**
   * Virhetilanteisiin liittyvät vakiot
   */
  final val LAHETYS_LUKEMINEN_EPAONNISTUI     = "Lähetyksen lukeminen epäonnistui"
  final val LAHETYKSET_LUKEMINEN_EPAONNISTUI  = "Lähetysten lukeminen epäonnistui"
  final val VIESTI_LUKEMINEN_EPAONNISTUI      = "Viestin lukeminen epäonnistui"
  final val VASTAANOTTAJAT_LUKEMINEN_EPAONNISTUI = "Vastaanottajien lukeminen epäonnistui"

  final val VIESTITUNNISTE_INVALID            = "Tunniste ei ole muodoltaan validi uuid"
  final val LAHETYSTUNNISTE_INVALID           = "Lähetystunniste ei ole muodoltaan validi uuid"
  final val ORGANISAATIO_INVALID               = "Organisaation oid ei ole validi"

  final val ALKAEN_EMAIL_TUNNISTE_INVALID     = ALKAEN_PARAM_NAME + "-parametri: Tunniste ei ole muodoltaan validi sähköpostiosoite"
  final val VASTAANOTTAJAT_ENINTAAN_INVALID   = ENINTAAN_PARAM_NAME + "-parametri: Arvon pitää olla numero väliltä " + VASTAANOTTAJAT_ENINTAAN_MIN_STR + "-" + VASTAANOTTAJAT_ENINTAAN_MAX_STR
  final val ALKAEN_AIKA_TUNNISTE_INVALID      = ALKAEN_PARAM_NAME + "-parametri: Tunniste ei ole muodoltaan validi aikaleima"
  final val LAHETYKSET_ENINTAAN_INVALID       = ENINTAAN_PARAM_NAME + "-parametri: Arvon pitää olla numero väliltä " + LAHETYKSET_ENINTAAN_MIN_STR + "-" + LAHETYKSET_ENINTAAN_MAX_STR
  final val VASTAANOTTAJA_INVALID             = VASTAANOTTAJA_PARAM_NAME + "-parametri: Tunniste ei ole validi sähköpostiosoite"
  final val TILA_INVALID                      = TILA_PARAM_NAME + "-parametri: Tunniste ei ole validi vastaanoton tila"
  final val SIVUTUS_TILA_INVALID              = SIVUTUS_TILA_PARAM_NAME + "-parametri: Tunniste ei ole validi vastaanoton tila"

}
