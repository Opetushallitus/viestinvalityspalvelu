package fi.oph.viestinvalitus.vastaanotto.model

import org.junit.jupiter.api.{Assertions, Test}

import java.util
import java.util.Optional

@Test
class LahetysValidatorTest {

  @Test def testValidateOtsikko(): Unit = {
    // laillinen otsikko on sallittu
    Assertions.assertEquals(Set.empty, LahetysValidator.validateOtsikko("Tosi hyvä otsikko"))

    // tyhjä otsikko ei ole sallittu
    Assertions.assertEquals(Set(LahetysValidator.VALIDATION_OTSIKKO_TYHJA), LahetysValidator.validateOtsikko(null))
    Assertions.assertEquals(Set(LahetysValidator.VALIDATION_OTSIKKO_TYHJA), LahetysValidator.validateOtsikko(""))

    // liian pitkä otsikko ei ole sallittu
    Assertions.assertEquals(Set(LahetysValidator.VALIDATION_OTSIKKO_LIIAN_PITKA), LahetysValidator.validateOtsikko("x".repeat(Lahetys.OTSIKKO_MAX_PITUUS + 1)))
  }
}
