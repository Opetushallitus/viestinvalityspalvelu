package fi.oph.viestinvalitys.vastaanotto.model

/**
 * Validoi järjestelmään syötetyn lähetyksen kentät
 */
object LahetysValidator:

  def validateOtsikko(otsikko: String): Set[String] =
    ViestiValidator.validateOtsikko(otsikko)
  def validateKayttooikeudet(kayttooikeudet: java.util.List[String]): Set[String] =
    ViestiValidator.validateKayttooikeusRajoitukset(kayttooikeudet)

end LahetysValidator

