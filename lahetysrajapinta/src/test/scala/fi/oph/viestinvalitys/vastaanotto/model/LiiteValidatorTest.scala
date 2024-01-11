package fi.oph.viestinvalitys.vastaanotto.model

import fi.oph.viestinvalitys.vastaanotto.model.Viesti.{Maski, Vastaanottaja}
import org.junit.jupiter.api.{Assertions, Test}

import java.util
import java.util.{Collections, Optional, UUID}
import scala.jdk.CollectionConverters.*

@Test
class LiiteValidatorTest {

  @Test def testValidateTiedostonimi(): Unit = {
    // laillinen tiedostonimi on sallittu
    Assertions.assertEquals(Set.empty, LiiteValidator.validateTiedostoNimi(Optional.of("liitetiedosto.pdf")))

    // tyhjä tiedostonimi ei ole sallittu
    Assertions.assertEquals(Set(LiiteValidator.VALIDATION_TIEDOSTONIMI_TYHJA), LiiteValidator.validateTiedostoNimi(Optional.empty()))
    Assertions.assertEquals(Set(LiiteValidator.VALIDATION_TIEDOSTONIMI_TYHJA), LiiteValidator.validateTiedostoNimi(Optional.of("")))

    // liian pitkä tiedostonimi ei ole sallittu
    Assertions.assertEquals(Set(LiiteValidator.VALIDATION_TIEDOSTONIMI_LIIAN_PITKA), LiiteValidator.validateTiedostoNimi(Optional.of("x".repeat(Liite.TIEDOSTONIMI_MAX_PITUUS + 1))))

    // kielletty tiedostotyyppi ei ole sallittu
    Assertions.assertEquals(Set(LiiteValidator.VALIDATION_TIEDOSTOTYYPPI_KIELLETTY + ".bat"), LiiteValidator.validateTiedostoNimi(Optional.of("pahatiedosto.bat")))

    // kaikki virheet kerätään
    Assertions.assertEquals(Set(LiiteValidator.VALIDATION_TIEDOSTONIMI_LIIAN_PITKA, LiiteValidator.VALIDATION_TIEDOSTOTYYPPI_KIELLETTY + ".bat"), LiiteValidator.validateTiedostoNimi(Optional.of("x".repeat(Liite.TIEDOSTONIMI_MAX_PITUUS + 1) + ".bat")))
  }

  @Test def testValidateSisaltoTyyppi(): Unit = {
    // laillinen sisältötyyppi on sallittu
    Assertions.assertEquals(Set.empty, LiiteValidator.validateSisaltoTyyppi(Optional.of("image/png")))

    // tyhjä sisältötyyppi ei ole sallittu
    Assertions.assertEquals(Set(LiiteValidator.VALIDATION_SISALTOTYYPPI_TYHJA), LiiteValidator.validateSisaltoTyyppi(Optional.empty()))
    Assertions.assertEquals(Set(LiiteValidator.VALIDATION_SISALTOTYYPPI_TYHJA), LiiteValidator.validateSisaltoTyyppi(Optional.of("")))

    // liian pitkä sisältötyyppi ei ole sallittu
    Assertions.assertEquals(Set(LiiteValidator.VALIDATION_SISALTOTYYPPI_LIIAN_PITKA), LiiteValidator.validateSisaltoTyyppi(Optional.of("x".repeat(Liite.SISALTOTYYPPI_MAX_PITUUS + 1))))
  }
}
