package fi.oph.viestinvalitys.vastaanotto.model

import java.util
import java.util.Optional

/**
 * Validoi järjestelmään syötetyn lähetyksen kentät
 */
object LiiteValidator:

  final val VALIDATION_TIEDOSTONIMI_TYHJA         = "tiedostonimi: Kenttä on pakollinen"
  final val VALIDATION_TIEDOSTONIMI_LIIAN_PITKA   = "tiedostonimi: Tiedostonimi ei voi pidempi kuin " + Liite.TIEDOSTONIMI_MAX_PITUUS + " merkkiä"
  final val VALIDATION_TIEDOSTOTYYPPI_KIELLETTY   = "tiedostonimi: Tiedostotyyppi on kielletty: "

  final val VALIDATION_SISALTOTYYPPI_TYHJA        = "sisaltotyyppi: Kenttä on pakollinen"
  final val VALIDATION_SISALTOTYYPPI_LIIAN_PITKA  = "sisaltotyyppi: Sisältötyyppi ei voi pidempi kuin " + Liite.SISALTOTYYPPI_MAX_PITUUS + " merkkiä"

  final val TIEDOSTOTYYPPIPATTERN                 = """\.[0-9A-Za-z]+$""".r

  def validateTiedostoNimi(nimi: Optional[String]): Set[String] =
    var virheet: Set[String] = Set.empty

    if (nimi.isEmpty || nimi.get.length == 0)
      virheet = virheet.incl(VALIDATION_TIEDOSTONIMI_TYHJA)
    else
      if (nimi.get.length > Liite.TIEDOSTONIMI_MAX_PITUUS)
        virheet = virheet.incl(VALIDATION_TIEDOSTONIMI_LIIAN_PITKA)
      val tiedostoTyyppi = TIEDOSTOTYYPPIPATTERN.findFirstIn(nimi.get)
      if(tiedostoTyyppi.map(t => Liite.KIELLETYT_TIEDOSTOTYYPIT.contains(t.toLowerCase)).getOrElse(false))
        virheet = virheet.incl(VALIDATION_TIEDOSTOTYYPPI_KIELLETTY + tiedostoTyyppi.get)

    virheet

  def validateSisaltoTyyppi(sisaltoTyyppi: Optional[String]): Set[String] =
    var virheet: Set[String] = Set.empty

    if (sisaltoTyyppi.isEmpty || sisaltoTyyppi.get.length == 0)
      virheet = virheet.incl(VALIDATION_SISALTOTYYPPI_TYHJA)
    else if (sisaltoTyyppi.get.length > Liite.SISALTOTYYPPI_MAX_PITUUS)
      virheet = virheet.incl(VALIDATION_SISALTOTYYPPI_LIIAN_PITKA)

    virheet

  def validateBytes(bytes: Array[Byte]): Set[String] =
    Set.empty

  def validateLiite(liite: Liite): Set[String] =
    Set(validateTiedostoNimi(Optional.of(liite.getTiedostoNimi)), validateSisaltoTyyppi(Optional.of(liite.getSisaltoTyyppi)), validateBytes(liite.getBytes)).flatten

end LiiteValidator


