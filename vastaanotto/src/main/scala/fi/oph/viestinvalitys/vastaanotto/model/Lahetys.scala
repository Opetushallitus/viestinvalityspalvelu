package fi.oph.viestinvalitys.vastaanotto.model

import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode

import java.util.UUID
import scala.annotation.meta.field
import scala.beans.BeanProperty

object Lahetys {
  final val OTSIKKO_MAX_PITUUS = 255
}

/**
 * Lähetyksen otsikko
 *
 * @param otsikko
 */
case class Lahetys(
  @(Schema @field)(example = "Lähetyksen Otsikko", requiredMode=RequiredMode.REQUIRED, maxLength = Lahetys.OTSIKKO_MAX_PITUUS)
  @BeanProperty otsikko: String,

  @(Schema@field)(requiredMode = RequiredMode.REQUIRED, example = "[\"APP_ATARU_HAKEMUS_CRUD_1.2.246.562.00.00000000000000006666\"]")
  @BeanProperty kayttooikeusRajoitukset: java.util.List[String],
                  ) {
  /**
   * Tyhjä konstruktori Jacksonia varten
   */
  def this() = {
    this(null, null)
  }
}
