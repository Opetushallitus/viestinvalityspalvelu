package fi.oph.viestinvalitys.vastaanotto.model

import java.util
import java.util.Optional

/**
 * Validoi järjestelmään syötetyn lähetyksen kentät
 */
object LahetysValidator:

  def validateOtsikko(otsikko: Optional[String]): Set[String] =
    ViestiValidator.validateOtsikko(otsikko)
  def validateKayttooikeudet(kayttooikeudet: Optional[util.List[String]]): Set[String] =
    ViestiValidator.validateKayttooikeusRajoitukset(kayttooikeudet)

  def validateLahetys(lahetys: Lahetys): Set[String] =
    Set(validateOtsikko(lahetys.getOtsikko), validateKayttooikeudet(lahetys.getKayttooikeusRajoitukset)).flatten
  
end LahetysValidator
