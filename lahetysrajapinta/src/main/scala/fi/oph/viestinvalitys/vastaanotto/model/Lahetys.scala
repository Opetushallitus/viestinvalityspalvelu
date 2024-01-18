package fi.oph.viestinvalitys.vastaanotto.model

import fi.oph.viestinvalitys.vastaanotto.model.Lahetys.*
import fi.oph.viestinvalitys.vastaanotto.model.LahetysImpl.*
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode

import java.util
import java.util.{Optional, UUID}
import scala.annotation.meta.field
import scala.beans.BeanProperty
import scala.jdk.CollectionConverters.*

/**
 * Viestin lähettäjä
 *
 * @param nimi
 * @param sahkopostiOsoite
 */
case class LahettajaImpl(
  @(Schema @field)(example = "Opintopolku", maxLength = ViestiImpl.VIESTI_NIMI_MAX_PITUUS)
  @BeanProperty nimi: Optional[String],

  @(Schema @field)(description="Domainin pitää olla opintopolku.fi", example = "noreply@opintopolku.fi", requiredMode=RequiredMode.REQUIRED)
  @BeanProperty sahkopostiOsoite: Optional[String],
) extends Lahettaja {

  /**
   * Tyhjä konstruktori Jacksonia varten
   */
  def this() = {
    this(null, null)
  }
}

object LahetysImpl {

  final val LAHETYS_PRIORITEETTI_KORKEA   = "korkea"
  final val LAHETYS_PRIORITEETTI_NORMAALI = "normaali"

  final val OTSIKKO_MAX_PITUUS            = 255;
  final val LAHETTAVAPALVELU_MAX_PITUUS   = 127;
  final val LAHETTAJA_NIMI_MAX_PITUUS     = 64;
  final val VIRKAILIJAN_OID_MAX_PITUUS    = 64;

  final val SAILYTYSAIKA_MIN_PITUUS       = SAILYTYSAIKA_MIN_PITUUS_STR.toInt
  final val SAILYTYSAIKA_MIN_PITUUS_STR   = "1"
  final val SAILYTYSAIKA_MAX_PITUUS       = SAILYTYSAIKA_MAX_PITUUS_STR.toInt
  final val SAILYTYSAIKA_MAX_PITUUS_STR   = "3650"
}

/**
 * Lähetys
 *
 * @param otsikko
 */
@Schema(name = "Lahetys")
case class LahetysImpl(
  @(Schema @field)(example = "Lähetyksen Otsikko", requiredMode=RequiredMode.REQUIRED, maxLength = OTSIKKO_MAX_PITUUS)
  @BeanProperty otsikko: Optional[String],

  @(Schema@field)(example = "hakemuspalvelu", requiredMode=RequiredMode.REQUIRED, maxLength = LAHETTAVAPALVELU_MAX_PITUUS)
  @BeanProperty lahettavaPalvelu: Optional[String],

  @(Schema@field)(example = "1.2.246.562.00.00000000000000006666", maxLength = VIRKAILIJAN_OID_MAX_PITUUS)
  @BeanProperty lahettavanVirkailijanOid: Optional[String],

  @(Schema@field)(requiredMode = RequiredMode.REQUIRED)
  @BeanProperty lahettaja: Optional[Lahetys.Lahettaja],

  @(Schema@field)(example = "ville.virkamies@oph.fi")
  @BeanProperty replyTo: Optional[String],

  @(Schema@field)(allowableValues = Array(LahetysImpl.LAHETYS_PRIORITEETTI_KORKEA, LahetysImpl.LAHETYS_PRIORITEETTI_NORMAALI), requiredMode = RequiredMode.REQUIRED, example = LahetysImpl.LAHETYS_PRIORITEETTI_NORMAALI)
  @BeanProperty prioriteetti: Optional[String],

  @(Schema@field)(requiredMode = RequiredMode.REQUIRED, minimum = LahetysImpl.SAILYTYSAIKA_MIN_PITUUS_STR, maximum = LahetysImpl.SAILYTYSAIKA_MAX_PITUUS_STR, example = "365")
  @BeanProperty sailytysaika: Optional[Integer],
) extends Lahetys {

  /**
   * Tyhjä konstruktori Jacksonia varten
   */
  def this() = {
    this(null, null, null, null, null, null, null)
  }
}

class LahetysBuilderImpl() extends OtsikkoBuilder, LahettavaPalveluBuilder, LahettajaBuilder, PrioriteettiBuilder, SailytysaikaBuilder, LahetysBuilder {

  var lahetys = new LahetysImpl(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())

  def withOtsikko(otsikko: String): LahetysBuilderImpl =
    lahetys = lahetys.copy(otsikko = Optional.of(otsikko))
    this

  def withLahettavaPalvelu(lahettavaPalvelu: String): LahetysBuilderImpl =
    lahetys = lahetys.copy(lahettavaPalvelu = Optional.of(lahettavaPalvelu))
    this

  override def withLahettaja(nimi: Optional[String], sahkopostiOsoite: String): LahetysBuilderImpl =
    lahetys = lahetys.copy(lahettaja = Optional.of(LahettajaImpl(nimi, Optional.of(sahkopostiOsoite))))
    this

  override def withReplyTo(replyTo: String): LahetysBuilderImpl =
    lahetys = lahetys.copy(replyTo = Optional.of(replyTo))
    this

  override def withLahettavanVirkailijanOid(oid: String): LahetysBuilderImpl =
    lahetys = lahetys.copy(lahettavanVirkailijanOid = Optional.of(oid))
    this

  override def withNormaaliPrioriteetti(): LahetysBuilderImpl =
    lahetys = lahetys.copy(prioriteetti = Optional.of(LahetysImpl.LAHETYS_PRIORITEETTI_NORMAALI.toLowerCase))
    this

  override def withKorkeaPrioriteetti(): LahetysBuilderImpl =
    lahetys = lahetys.copy(prioriteetti = Optional.of(LahetysImpl.LAHETYS_PRIORITEETTI_KORKEA.toLowerCase))
    this

  override def withSailytysaika(sailytysaika: Int): LahetysBuilderImpl =
    lahetys = lahetys.copy(sailytysaika = Optional.of(sailytysaika))
    this

  def build(): LahetysImpl =
    val virheet = LahetysValidator.validateLahetys(lahetys)
    if(!virheet.isEmpty) throw new BuilderException(virheet.asJava)
    lahetys

}
