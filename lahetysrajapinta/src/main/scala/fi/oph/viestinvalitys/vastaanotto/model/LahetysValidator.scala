package fi.oph.viestinvalitys.vastaanotto.model

import fi.oph.viestinvalitys.vastaanotto.model.Lahetys.*
import fi.oph.viestinvalitys.vastaanotto.model.LahetysImpl.{LAHETYS_PRIORITEETTI_KORKEA, LAHETYS_PRIORITEETTI_NORMAALI, SAILYTYSAIKA_MAX_PITUUS, SAILYTYSAIKA_MIN_PITUUS}
import org.apache.commons.validator.routines.EmailValidator

import java.util.{List, Optional}
import scala.jdk.CollectionConverters.*
import scala.util.matching.Regex

/**
 * Validoi järjestelmään syötetyn lähetyksen kentät
 */
object LahetysValidator:

  final val VALIDATION_OTSIKKO_TYHJA                  = "otsikko: Kenttä on pakollinen"
  final val VALIDATION_OTSIKKO_LIIAN_PITKA            = "otsikko: Otsikko ei voi pidempi kuin " + OTSIKKO_MAX_PITUUS + " merkkiä"

  final val VALIDATION_LAHETTAVA_PALVELU_TYHJA        = "lahettavaPalvelu: Kenttä on pakollinen"
  final val VALIDATION_LAHETTAVA_PALVELU_LIIAN_PITKA  = "lahettavaPalvelu: Kentän pituus voi olla korkeintaan " + LAHETTAVAPALVELU_MAX_PITUUS + " merkkiä"
  final val VALIDATION_LAHETTAVA_PALVELU_INVALID      = "lahettavaPalvelu: Arvo ei ole validi käännösavain"

  final val VALIDATION_LAHETTAJAN_OID_INVALID         = "lähettäjänOid: Oid ei ole validi (1.2.246.562-alkuinen) oph-oid"

  final val VALIDATION_LAHETTAJAN_OID_PITUUS          = "lähettäjänOid-kentän suurin sallittu pituus on " + VIRKAILIJAN_OID_MAX_PITUUS + " merkkiä"

  final val VALIDATION_OPH_OID_PREFIX                 = "1.2.246.562"

  final val VALIDATION_LAHETTAJA_TYHJA                = "lähettäjä: Kenttä on pakollinen"
  final val VALIDATION_LAHETTAJA_NIMI_LIIAN_PITKA     = "lähettäjä: nimi-kenttä voi maksimissaan olla " + LAHETTAJA_NIMI_MAX_PITUUS + " merkkiä pitkä"
  final val VALIDATION_LAHETTAJAN_OSOITE_TYHJA        = "lähettäjä: Lähettäjän sähköpostiosoite -kenttä on pakollinen"
  final val VALIDATION_LAHETTAJAN_OSOITE_INVALID      = "lähettäjä: Lähettäjän sähköpostiosoite ei ole validi sähköpostiosoite"
  final val VALIDATION_LAHETTAJAN_OSOITE_DOMAIN       = "lähettäjä: Lähettäjän sähköpostiosoite ei ole opintopolku.fi -domainissa"

  final val VALIDATION_REPLYTO_INVALID                = "replyTo: arvo ei ole validi sähköpostiosoite"

  final val VALIDATION_PRIORITEETTI                   = "prioriteetti: Prioriteetti täytyy olla joko \"" + LAHETYS_PRIORITEETTI_NORMAALI + "\" tai \"" + LAHETYS_PRIORITEETTI_KORKEA + "\""

  final val VALIDATION_SAILYTYSAIKA_TYHJA             = "sailytysAika: Kenttä on pakollinen"
  final val VALIDATION_SAILYTYSAIKA                   = "sailytysAika: Säilytysajan tulee olla " + SAILYTYSAIKA_MIN_PITUUS + "-" + SAILYTYSAIKA_MAX_PITUUS + " päivää"

  final val VALIDATION_KAYTTOOIKEUSRAJOITUS_NULL      = "kayttooikeusRajoitukset: Kenttä sisältää null-arvoja"
  final val VALIDATION_KAYTTOOIKEUSRAJOITUS_DUPLICATE = "kayttooikeusRajoitukset: Kentässä on duplikaatteja: "
  final val VALIDATION_KAYTTOOIKEUSRAJOITUS_INVALID   = "käyttöoikeusrajoitus ei ole organisaatiorajoitettu (ts. ei pääty _<oid>)"

  def validateOtsikko(otsikko: Optional[String]): Set[String] =
    var errors: Set[String] = Set.empty
  
    if (otsikko.isEmpty || otsikko.get.length == 0)
      errors = errors.incl(VALIDATION_OTSIKKO_TYHJA)
    else if (otsikko.get.length > OTSIKKO_MAX_PITUUS)
      errors = errors.incl(VALIDATION_OTSIKKO_LIIAN_PITKA)
  
    errors

  val kaannosAvainPattern: Regex = "[a-zA-Z0-9]+".r
  def validateLahettavaPalvelu(lahettavaPalvelu: Optional[String]): Set[String] =
    if (lahettavaPalvelu.isEmpty || lahettavaPalvelu.get.isEmpty) return Set(VALIDATION_LAHETTAVA_PALVELU_TYHJA)

    var virheet: Set[String] = Set.empty
    if (lahettavaPalvelu.get.length > LAHETTAVAPALVELU_MAX_PITUUS)
      virheet = virheet.incl(VALIDATION_LAHETTAVA_PALVELU_LIIAN_PITKA)
    if (!kaannosAvainPattern.matches(lahettavaPalvelu.get))
      virheet = virheet.incl(VALIDATION_LAHETTAVA_PALVELU_INVALID)

    virheet

  val oidPattern: Regex = (VALIDATION_OPH_OID_PREFIX + "(\\.[0-9]+)+").r
  def validateLahettavanVirkailijanOID(oid: Optional[String]): Set[String] =
    if (oid.isEmpty) return Set.empty

    var virheet: Set[String] = Set.empty

    if (!oidPattern.matches(oid.get()))
      virheet = virheet.incl(VALIDATION_LAHETTAJAN_OID_INVALID)

    if (oid.get.length > VIRKAILIJAN_OID_MAX_PITUUS)
      virheet = virheet.incl(VALIDATION_LAHETTAJAN_OID_PITUUS)

    virheet

  def validateLahettaja(lahettaja: Optional[Lahettaja]): Set[String] =
    if (lahettaja.isEmpty) return Set(VALIDATION_LAHETTAJA_TYHJA)

    var virheet: Set[String] = Set.empty
    if (lahettaja.get.getNimi.isPresent && lahettaja.get.getNimi.get.length > LAHETTAJA_NIMI_MAX_PITUUS)
      virheet = virheet.incl(VALIDATION_LAHETTAJA_NIMI_LIIAN_PITKA)
    if (lahettaja.get.getSahkopostiOsoite.isEmpty || lahettaja.get.getSahkopostiOsoite.get.length == 0)
      virheet = virheet.incl(VALIDATION_LAHETTAJAN_OSOITE_TYHJA)
    else if (!EmailValidator.getInstance(false).isValid(lahettaja.get.getSahkopostiOsoite.get))
      virheet = virheet.incl(VALIDATION_LAHETTAJAN_OSOITE_INVALID)
    else if (!lahettaja.get.getSahkopostiOsoite.get.endsWith("@opintopolku.fi"))
      virheet = virheet.incl(VALIDATION_LAHETTAJAN_OSOITE_DOMAIN)

    virheet

  def validateReplyTo(replyTo: Optional[String]): Set[String] =
    if (replyTo.isEmpty) return Set.empty

    if (!EmailValidator.getInstance(false).isValid(replyTo.get))
      return Set(VALIDATION_REPLYTO_INVALID)

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

  val kayttooikeusPattern: Regex = ("^.*_[0-9]+(\\.[0-9]+)+$").r
  def validateKayttooikeusRajoitukset(kayttooikeusRajoitukset: Optional[List[String]]): Set[String] =
    var virheet: Set[String] = Set.empty

    // on ok jos käyttöoikeusrajoituksia ei määritelty
    if (kayttooikeusRajoitukset.isEmpty)
      return virheet

    // tarkastetaan onko käyttöoikeusrajoituslistalla null-arvoja
    if (kayttooikeusRajoitukset.get.stream().filter(tunniste => tunniste == null).count() > 0)
      virheet = virheet.incl(VALIDATION_KAYTTOOIKEUSRAJOITUS_NULL)

    // validoidaan yksittäiset käyttöoikeudet
    kayttooikeusRajoitukset.get.asScala.toSet.map(rajoitus => {
      if (rajoitus != null) {
        var rajoitusVirheet: Set[String] = Set.empty

        if (!kayttooikeusPattern.matches(rajoitus))
          rajoitusVirheet = rajoitusVirheet.incl(VALIDATION_KAYTTOOIKEUSRAJOITUS_INVALID)

        if (!rajoitusVirheet.isEmpty)
          virheet = virheet.incl("Käyttöoikeusrajoitus \"" + rajoitus + "\": " +
            rajoitusVirheet.mkString(","))
      }
    })

    // tutkitaan onko käyttöoikeusrajoituslistalla duplikaatteja
    val duplikaattiRajoitukset = kayttooikeusRajoitukset.get.asScala
      .filter(rajoitus => rajoitus != null)
      .groupBy(rajoitus => rajoitus)
      .filter(rajoitusByRajoitus => rajoitusByRajoitus._2.size > 1)
      .map(rajoitusByRajoitus => rajoitusByRajoitus._1)
    if (!duplikaattiRajoitukset.isEmpty)
      virheet = virheet.incl(VALIDATION_KAYTTOOIKEUSRAJOITUS_DUPLICATE + duplikaattiRajoitukset.mkString(","))

    virheet

  def validateLahetys(lahetys: Lahetys): Set[String] =
    Set(
      validateOtsikko(lahetys.getOtsikko),
      validateLahettavaPalvelu(lahetys.getLahettavaPalvelu),
      validateLahettavanVirkailijanOID(lahetys.getLahettavanVirkailijanOid),
      validateLahettaja(lahetys.getLahettaja),
      validateReplyTo(lahetys.getReplyTo),
      validatePrioriteetti(lahetys.getPrioriteetti),
      validateSailytysAika(lahetys.getSailytysaika),
      validateKayttooikeusRajoitukset(lahetys.getKayttooikeusRajoitukset)
    ).flatten
  
end LahetysValidator

