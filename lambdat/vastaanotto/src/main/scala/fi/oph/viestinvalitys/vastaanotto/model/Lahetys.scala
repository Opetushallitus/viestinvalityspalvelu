package fi.oph.viestinvalitys.vastaanotto.model

import Lahetys.*
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode

import java.util
import java.util.{Optional, UUID}
import scala.annotation.meta.field
import scala.beans.BeanProperty
import scala.jdk.CollectionConverters.*

/**
 * Lähetys
 *
 * @param otsikko
 */
@Schema(name = "Lahetys")
case class LahetysImpl(
  @(Schema @field)(example = "Lähetyksen Otsikko", requiredMode=RequiredMode.REQUIRED, maxLength = OTSIKKO_MAX_PITUUS)
  @BeanProperty otsikko: Optional[String],

  @(Schema@field)(example = "[\"APP_ATARU_HAKEMUS_CRUD_1.2.246.562.00.00000000000000006666\"]")
  @BeanProperty kayttooikeusRajoitukset: Optional[util.List[String]],
) extends Lahetys {

  /**
   * Tyhjä konstruktori Jacksonia varten
   */
  def this() = {
    this(null, null)
  }
}

class LahetysBuilderImpl() extends OtsikkoBuilder, ILahetysBuilder {

  var lahetys = new LahetysImpl(Optional.empty(), Optional.empty())

  def withOtsikko(otsikko: String): ILahetysBuilder =
    lahetys = lahetys.copy(otsikko = Optional.of(otsikko))
    this

  def withKayttooikeusRajoitukset(kayttooikeusRajoitukset: String*): ILahetysBuilder =
    lahetys = lahetys.copy(kayttooikeusRajoitukset = Optional.of(kayttooikeusRajoitukset.asJava))
    this

  def build(): LahetysImpl =
    val virheet = LahetysValidator.validateLahetys(lahetys)
    if(!virheet.isEmpty) throw new BuilderException(virheet.asJava)
    lahetys

}
