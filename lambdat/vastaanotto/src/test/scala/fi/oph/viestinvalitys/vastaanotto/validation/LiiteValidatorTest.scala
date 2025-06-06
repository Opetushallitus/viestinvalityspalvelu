package fi.oph.viestinvalitys.vastaanotto.validation

import fi.oph.viestinvalitys.vastaanotto.model.Liite
import org.junit.jupiter.api.{Assertions, Test}

import java.util
import java.util.Optional

@Test
class LiiteValidatorTest {

  @Test def testValidateTiedostonimi(): Unit = {
    // laillinen tiedostonimi on sallittu
    Assertions.assertEquals(Set.empty, LiiteValidator.validateTiedostoNimi(Optional.of("liitetiedosto +-_().pdf")))

    // tyhjä tiedostonimi ei ole sallittu
    Assertions.assertEquals(Set(LiiteValidator.VALIDATION_TIEDOSTONIMI_TYHJA), LiiteValidator.validateTiedostoNimi(Optional.empty()))
    Assertions.assertEquals(Set(LiiteValidator.VALIDATION_TIEDOSTONIMI_TYHJA), LiiteValidator.validateTiedostoNimi(Optional.of("")))

    // liian pitkä tiedostonimi ei ole sallittu
    Assertions.assertEquals(Set(LiiteValidator.VALIDATION_TIEDOSTONIMI_LIIAN_PITKA), LiiteValidator.validateTiedostoNimi(Optional.of("x".repeat(Liite.TIEDOSTONIMI_MAX_PITUUS + 1) + ".pdf")))

    // vain tietyt merkit sallittuja nimessä
    Assertions.assertEquals(Set(LiiteValidator.VALIDATION_TIEDOSTONIMI_MERKIT), LiiteValidator.validateTiedostoNimi(Optional.of("/etc/password.pdf")))

    // tiedostotyyppi pitää olla määritelty
    Assertions.assertEquals(Set(LiiteValidator.VALIDATION_EI_TIEDOSTOTYYPPIA + "abc"), LiiteValidator.validateTiedostoNimi(Optional.of("abc")))

    // aws ses -kielletty tiedostotyyppi ei ole sallittu
    Assertions.assertEquals(Set(LiiteValidator.VALIDATION_TIEDOSTOTYYPPI_EI_SALLITTU + ".bat"), LiiteValidator.validateTiedostoNimi(Optional.of("pahatiedosto.bat")))

    // ei-sallittu tiedostotyyppi ei ole sallittu
    Assertions.assertEquals(Set(LiiteValidator.VALIDATION_TIEDOSTOTYYPPI_EI_SALLITTU + ".bmp"), LiiteValidator.validateTiedostoNimi(Optional.of("eitiedetystihyvatiedosto.bmp")))

    // kaikki virheet kerätään
    Assertions.assertEquals(Set(LiiteValidator.VALIDATION_TIEDOSTONIMI_LIIAN_PITKA, LiiteValidator.VALIDATION_TIEDOSTOTYYPPI_EI_SALLITTU + ".bat"), LiiteValidator.validateTiedostoNimi(Optional.of("x".repeat(Liite.TIEDOSTONIMI_MAX_PITUUS + 1) + ".bat")))
  }

  @Test def testValidateSisaltoTyyppi(): Unit = {
    // tiedoston sisältöä vastaava sisältötyyppi on sallittu
    Assertions.assertEquals(Set.empty, LiiteValidator.validateSisaltoTyyppi(Optional.of("application/pdf"), getClass.getResourceAsStream("/sample.pdf")))

    // tyhjä sisältötyyppi ei ole sallittu
    Assertions.assertEquals(Set(LiiteValidator.VALIDATION_SISALTOTYYPPI_TYHJA), LiiteValidator.validateSisaltoTyyppi(Optional.empty(), getClass.getResourceAsStream("/sample.pdf")))
    Assertions.assertEquals(Set(LiiteValidator.VALIDATION_SISALTOTYYPPI_TYHJA), LiiteValidator.validateSisaltoTyyppi(Optional.of(""), getClass.getResourceAsStream("/sample.pdf")))

    // liian pitkä sisältötyyppi ei ole sallittu
    Assertions.assertEquals(Set(LiiteValidator.VALIDATION_SISALTOTYYPPI_LIIAN_PITKA), LiiteValidator.validateSisaltoTyyppi(Optional.of("x".repeat(Liite.SISALTOTYYPPI_MAX_PITUUS + 1)), getClass.getResourceAsStream("/sample.pdf")))

    // sisältötyyppi joka ei vastaa tiedoston sisältöä ei ole sallittu
    Assertions.assertEquals(Set(LiiteValidator.VALIDATION_SISALTOTYYPPI_EI_VASTAA + "application/pdf!=image/jpg"), LiiteValidator.validateSisaltoTyyppi(Optional.of("image/jpg"), getClass.getResourceAsStream("/pdffakingjpg.jpg")))
  }
}
