package fi.oph.viestinvalitus.vastaanotto.model

import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import fi.oph.viestinvalitus.model.*
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
                  ) {
  /**
   * Tyhjä konstruktori Jacksonia varten
   */
  def this() = {
    this(null)
  }
}
