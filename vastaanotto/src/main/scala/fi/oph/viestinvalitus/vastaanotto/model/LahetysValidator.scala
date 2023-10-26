package fi.oph.viestinvalitus.vastaanotto.model

/**
 * Validoi järjestelmään syötetyn lähetyksen kentät
 */
object LahetysValidator:

  final val VALIDATION_OTSIKKO_TYHJA                      = "otsikko: Kenttä on pakollinen"
  final val VALIDATION_OTSIKKO_LIIAN_PITKA                = "otsikko: Otsikko ei voi pidempi kuin " + Lahetys.OTSIKKO_MAX_PITUUS + " merkkiä"

  def validateOtsikko(otsikko: String): Set[String] =
    var errors: Set[String] = Set.empty

    if(otsikko==null || otsikko.length==0)
      errors = errors.incl(VALIDATION_OTSIKKO_TYHJA)
    else if(otsikko.length > Viesti.OTSIKKO_MAX_PITUUS)
      errors = errors.incl(VALIDATION_OTSIKKO_LIIAN_PITKA)

    errors

end LahetysValidator

