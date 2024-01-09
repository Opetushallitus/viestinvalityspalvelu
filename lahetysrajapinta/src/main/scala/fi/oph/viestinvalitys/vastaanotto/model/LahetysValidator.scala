package fi.oph.viestinvalitys.vastaanotto.model

import fi.oph.viestinvalitys.vastaanotto.model.Lahetys.*

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
    Set(validateOtsikko(lahetys.getOtsikko), validateLahettavaPalvelu(lahetys.getLahettavaPalvelu),
      validateKayttooikeusRajoitukset(lahetys.getKayttooikeusRajoitukset)).flatten
  
end LahetysValidator

