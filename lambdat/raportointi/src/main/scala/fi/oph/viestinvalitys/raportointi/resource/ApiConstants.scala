package fi.oph.viestinvalitys.raportointi.resource

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

  final val HEALTHCHECK_PATH                  = RAPORTOINTI_API_PREFIX + "/healthcheck"

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
