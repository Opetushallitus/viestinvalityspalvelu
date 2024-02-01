package fi.oph.viestinvalitys.vastaanotto.model

import fi.oph.viestinvalitys.vastaanotto.model.Lahetys.*
import fi.oph.viestinvalitys.vastaanotto.model.LahetysImpl.*
import org.apache.commons.validator.routines.EmailValidator

import java.util.{List, Optional}
import scala.jdk.CollectionConverters.*
import scala.util.matching.Regex

/**
 * Validoi järjestelmään syötetyn lähetyksen kentät. Validaattorin virheilmoitukset eivät saa sisältää sensitiivistä
 * tietoa koska ne menevät mm. lokeille.
 */
object LahetysValidator:

  final val VALIDATION_OPH_OID_PREFIX                 = "1.2.246.562"
  final val VALIDATION_OPH_DOMAIN                     = "@opintopolku.fi"

  final val VALIDATION_OTSIKKO_TYHJA                  = "otsikko: Kenttä on pakollinen"
  final val VALIDATION_OTSIKKO_LIIAN_PITKA            = "otsikko: Otsikko ei voi pidempi kuin " + OTSIKKO_MAX_PITUUS + " merkkiä"

  final val VALIDATION_LAHETTAVA_PALVELU_TYHJA        = "lahettavaPalvelu: Kenttä on pakollinen"
  final val VALIDATION_LAHETTAVA_PALVELU_LIIAN_PITKA  = "lahettavaPalvelu: Kentän pituus voi olla korkeintaan " + LAHETTAVAPALVELU_MAX_PITUUS + " merkkiä"
  final val VALIDATION_LAHETTAVA_PALVELU_INVALID      = "lahettavaPalvelu: Arvo ei ole validi käännösavain"

  final val VALIDATION_LAHETTAJAN_OID_INVALID         = "lähettäjänOid: Oid ei ole validi (1.2.246.562-alkuinen) oph-oid"

  final val VALIDATION_LAHETTAJAN_OID_PITUUS          = "lähettäjänOid-kentän suurin sallittu pituus on " + VIRKAILIJAN_OID_MAX_PITUUS + " merkkiä"

  final val VALIDATION_LAHETTAJA_TYHJA                = "lähettäjä: Kenttä on pakollinen"
  final val VALIDATION_LAHETTAJA_NIMI_LIIAN_PITKA     = "lähettäjä: nimi-kenttä voi maksimissaan olla " + LAHETTAJA_NIMI_MAX_PITUUS + " merkkiä pitkä"
  final val VALIDATION_LAHETTAJAN_OSOITE_TYHJA        = "lähettäjä: Lähettäjän sähköpostiosoite -kenttä on pakollinen"
  final val VALIDATION_LAHETTAJAN_OSOITE_INVALID      = "lähettäjä: Lähettäjän sähköpostiosoite ei ole validi sähköpostiosoite"
  final val VALIDATION_LAHETTAJAN_OSOITE_DOMAIN       = "lähettäjä: Lähettäjän sähköpostiosoite ei ole opintopolku.fi -domainissa"

  final val VALIDATION_REPLYTO_INVALID                = "replyTo: arvo ei ole validi sähköpostiosoite"

  final val VALIDATION_PRIORITEETTI                   = "prioriteetti: Prioriteetti täytyy olla joko \"" + LAHETYS_PRIORITEETTI_NORMAALI + "\" tai \"" + LAHETYS_PRIORITEETTI_KORKEA + "\""

  final val VALIDATION_SAILYTYSAIKA_TYHJA             = "sailytysAika: Kenttä on pakollinen"
  final val VALIDATION_SAILYTYSAIKA                   = "sailytysAika: Säilytysajan tulee olla " + SAILYTYSAIKA_MIN_PITUUS + "-" + SAILYTYSAIKA_MAX_PITUUS + " päivää"

  def validateOtsikko(otsikko: Optional[String]): Set[String] =
    if (otsikko.isEmpty || otsikko.get.length == 0)
      Set(VALIDATION_OTSIKKO_TYHJA)
    else if (otsikko.get.length > OTSIKKO_MAX_PITUUS)
      Set(VALIDATION_OTSIKKO_LIIAN_PITKA)
    else
      Set.empty

  val kaannosAvainPattern: Regex = "[a-zA-Z0-9]+".r
  def validateLahettavaPalvelu(lahettavaPalvelu: Optional[String]): Set[String] =
    if (lahettavaPalvelu.isEmpty || lahettavaPalvelu.get.isEmpty)
      Set(VALIDATION_LAHETTAVA_PALVELU_TYHJA)
    else
      Some(Set.empty.asInstanceOf[Set[String]])
        .map(virheet =>
          if (lahettavaPalvelu.get.length > LAHETTAVAPALVELU_MAX_PITUUS)
            virheet.incl(VALIDATION_LAHETTAVA_PALVELU_LIIAN_PITKA) else virheet)
        .map(virheet =>
          if (!kaannosAvainPattern.matches(lahettavaPalvelu.get))
            virheet.incl(VALIDATION_LAHETTAVA_PALVELU_INVALID) else virheet).get

  val ophOidPattern: Regex = (VALIDATION_OPH_OID_PREFIX + "(\\.[0-9]+)+").r
  def validateLahettavanVirkailijanOID(oid: Optional[String]): Set[String] =
    if (oid.isEmpty)
      Set.empty
    else
      Some(Set.empty.asInstanceOf[Set[String]])
        .map(virheet =>
          if (!ophOidPattern.matches(oid.get()))
            virheet.incl(VALIDATION_LAHETTAJAN_OID_INVALID) else virheet)
        .map(virheet =>
          if (oid.get.length > VIRKAILIJAN_OID_MAX_PITUUS)
            virheet.incl(VALIDATION_LAHETTAJAN_OID_PITUUS) else virheet).get

  def validateLahettaja(lahettaja: Optional[Lahettaja]): Set[String] =
    if (lahettaja.isEmpty)
      Set(VALIDATION_LAHETTAJA_TYHJA)
    else
      Some(Set.empty.asInstanceOf[Set[String]])
        .map(virheet =>
          if (lahettaja.get.getNimi.isPresent && lahettaja.get.getNimi.get.length > LAHETTAJA_NIMI_MAX_PITUUS)
            virheet.incl(VALIDATION_LAHETTAJA_NIMI_LIIAN_PITKA) else virheet)
        .map(virheet =>
          if (lahettaja.get.getSahkopostiOsoite.isEmpty || lahettaja.get.getSahkopostiOsoite.get.length == 0)
            virheet.incl(VALIDATION_LAHETTAJAN_OSOITE_TYHJA)
          else if (!EmailValidator.getInstance(false).isValid(lahettaja.get.getSahkopostiOsoite.get))
            virheet.incl(VALIDATION_LAHETTAJAN_OSOITE_INVALID)
          else if (!lahettaja.get.getSahkopostiOsoite.get.endsWith(VALIDATION_OPH_DOMAIN))
            virheet.incl(VALIDATION_LAHETTAJAN_OSOITE_DOMAIN)
          else virheet).get

  def validateReplyTo(replyTo: Optional[String]): Set[String] =
    if (replyTo.isEmpty)
      Set.empty
    else if (!EmailValidator.getInstance(false).isValid(replyTo.get))
      Set(VALIDATION_REPLYTO_INVALID)
    else
      Set.empty

  def validatePrioriteetti(prioriteetti: Optional[String]): Set[String] =
    if (prioriteetti.isEmpty || (!prioriteetti.get.equals(LAHETYS_PRIORITEETTI_KORKEA) && !prioriteetti.get.equals(LAHETYS_PRIORITEETTI_NORMAALI)))
      Set(VALIDATION_PRIORITEETTI)
    else
      Set.empty

  def validateSailytysAika(sailytysAika: Optional[Integer]): Set[String] =
    if (sailytysAika.isEmpty)
      Set(VALIDATION_SAILYTYSAIKA_TYHJA)
    else if (sailytysAika.get < SAILYTYSAIKA_MIN_PITUUS || sailytysAika.get > SAILYTYSAIKA_MAX_PITUUS)
      Set(VALIDATION_SAILYTYSAIKA)
    else
      Set.empty

  def validateLahetys(lahetys: Lahetys): Set[String] =
    Set(
      validateOtsikko(lahetys.getOtsikko),
      validateLahettavaPalvelu(lahetys.getLahettavaPalvelu),
      validateLahettavanVirkailijanOID(lahetys.getLahettavanVirkailijanOid),
      validateLahettaja(lahetys.getLahettaja),
      validateReplyTo(lahetys.getReplyTo),
      validatePrioriteetti(lahetys.getPrioriteetti),
      validateSailytysAika(lahetys.getSailytysaika),
    ).flatten
  
end LahetysValidator

