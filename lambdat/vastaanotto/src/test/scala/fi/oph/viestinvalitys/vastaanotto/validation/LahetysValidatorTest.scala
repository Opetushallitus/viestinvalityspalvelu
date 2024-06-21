package fi.oph.viestinvalitys.vastaanotto.validation

import fi.oph.viestinvalitys.vastaanotto.model.Lahetys.Lahettaja
import fi.oph.viestinvalitys.vastaanotto.model.{LahettajaImpl, LahetysImpl, ViestiImpl}
import org.junit.jupiter.api.{Assertions, Test}

import java.util
import java.util.Optional

@Test
class LahetysValidatorTest {

  @Test def testValidateOtsikko(): Unit = {
    // laillinen otsikko on sallittu
    Assertions.assertEquals(Set.empty, LahetysValidator.validateOtsikko(Optional.of("Tosi hyvä otsikko")))

    // tyhjä otsikko ei ole sallittu
    Assertions.assertEquals(Set(LahetysValidator.VALIDATION_OTSIKKO_TYHJA), LahetysValidator.validateOtsikko(Optional.empty()))
    Assertions.assertEquals(Set(LahetysValidator.VALIDATION_OTSIKKO_TYHJA), LahetysValidator.validateOtsikko(Optional.of("")))

    // liian pitkä otsikko ei ole sallittu
    Assertions.assertEquals(Set(LahetysValidator.VALIDATION_OTSIKKO_LIIAN_PITKA), LahetysValidator.validateOtsikko(Optional.of("x".repeat(ViestiImpl.OTSIKKO_MAX_PITUUS + 1))))
  }

  @Test def testValidateLahettavaPalvelu(): Unit =
    // validin muotoiset avaimet sallittu
    Assertions.assertEquals(Set.empty, LahetysValidator.validateLahettavaPalvelu(Optional.of("kaannosavain1")))
    Assertions.assertEquals(Set.empty, LahetysValidator.validateLahettavaPalvelu(Optional.of("kaannosavain2")))

    // tyhjä käännösavain ei sallittu
    Assertions.assertEquals(Set(LahetysValidator.VALIDATION_LAHETTAVA_PALVELU_TYHJA), LahetysValidator.validateLahettavaPalvelu(Optional.empty))

    // liian pitkä avain ei sallittu
    Assertions.assertEquals(Set(LahetysValidator.VALIDATION_LAHETTAVA_PALVELU_LIIAN_PITKA), LahetysValidator.validateLahettavaPalvelu(Optional.of("x".repeat(LahetysImpl.LAHETTAVAPALVELU_MAX_PITUUS + 1))))

    // väärän muotoinen avain ei sallittu
    Assertions.assertEquals(Set(LahetysValidator.VALIDATION_LAHETTAVA_PALVELU_INVALID), LahetysValidator.validateLahettavaPalvelu(Optional.of("!\\?*")))

    // kaikki virheet kerätään
    Assertions.assertEquals(Set(
      LahetysValidator.VALIDATION_LAHETTAVA_PALVELU_LIIAN_PITKA,
      LahetysValidator.VALIDATION_LAHETTAVA_PALVELU_INVALID
    ), LahetysValidator.validateLahettavaPalvelu(Optional.of("x".repeat(LahetysImpl.LAHETTAVAPALVELU_MAX_PITUUS) + "!\\?*")))

  @Test def testValidateLahettajanOid(): Unit = {
    // oph-oidit ovat sallittuja
    Assertions.assertEquals(Set.empty, LahetysValidator.validateLahettavanVirkailijanOID(Optional.of(LahetysValidator.VALIDATION_OPH_OID_PREFIX + ".123.456.00001")))
    Assertions.assertEquals(Set.empty, LahetysValidator.validateLahettavanVirkailijanOID(Optional.of(LahetysValidator.VALIDATION_OPH_OID_PREFIX + ".789.987.00001")))

    // määrittelemätön oid on sallittu
    Assertions.assertEquals(Set.empty, LahetysValidator.validateLahettavanVirkailijanOID(Optional.empty()))

    // muut kuin oph-oidit eivät ole sallittuja
    Assertions.assertEquals(Set(LahetysValidator.VALIDATION_LAHETTAJAN_OID_INVALID), LahetysValidator.validateLahettavanVirkailijanOID(Optional.of("123.456.789")))

    // liian pitkä oid ei ole sallittu
    Assertions.assertEquals(Set(LahetysValidator.VALIDATION_LAHETTAJAN_OID_PITUUS), LahetysValidator.validateLahettavanVirkailijanOID(Optional.of(LahetysValidator.VALIDATION_OPH_OID_PREFIX + "." + "0".repeat(LahetysImpl.VIRKAILIJAN_OID_MAX_PITUUS))))
  }

  @Test def testValidateLahettaja(): Unit = {

    def getLahettaja(nimi: String, sahkoposti: String): Optional[Lahettaja] =
      Optional.of(LahettajaImpl(Optional.ofNullable(nimi), Optional.ofNullable(sahkoposti)))

    // lähettäjät joiden osoite validi ovat sallittuja
    Assertions.assertEquals(Set.empty, LahetysValidator.validateLahettaja(getLahettaja("Opetushallitus", "noreply@opintopolku.fi")))
    Assertions.assertEquals(Set.empty, LahetysValidator.validateLahettaja(getLahettaja(null, "jotain@opintopolku.fi")))

    // määrittelemätön lähettäjä ei ole sallittu
    Assertions.assertEquals(Set(LahetysValidator.VALIDATION_LAHETTAJA_TYHJA), LahetysValidator.validateLahettaja(Optional.empty()))

    // määrittelemätön nimi on sallittu
    Assertions.assertEquals(Set.empty, LahetysValidator.validateLahettaja(getLahettaja(null, "noreply@opintopolku.fi")))

    // liian pitkä nimi ei ole sallittu
    Assertions.assertEquals(Set(LahetysValidator.VALIDATION_LAHETTAJA_NIMI_LIIAN_PITKA),
      LahetysValidator.validateLahettaja(getLahettaja("x".repeat(ViestiImpl.VIESTI_NIMI_MAX_PITUUS + 1), "noreply@opintopolku.fi")))

    // määrittelemätön osoite ei ole sallittu
    Assertions.assertEquals(Set(LahetysValidator.VALIDATION_LAHETTAJAN_OSOITE_TYHJA), LahetysValidator.validateLahettaja(getLahettaja("Opetushallitus", null)))

    // ei validi osoite ei ole sallittu
    Assertions.assertEquals(Set(LahetysValidator.VALIDATION_LAHETTAJAN_OSOITE_INVALID), LahetysValidator.validateLahettaja(getLahettaja("Opetushallitus", "ei validi osoite")))

    // ei opintopolku.fi -domain ei ole sallittu
    Assertions.assertEquals(Set(LahetysValidator.VALIDATION_LAHETTAJAN_OSOITE_DOMAIN), LahetysValidator.validateLahettaja(getLahettaja("Opetushallitus", "noreply@example.com")))
  }

  @Test def testValidateReplyTo(): Unit = {
    // määrittelemätön replyTo on sallittu
    Assertions.assertEquals(Set.empty, LahetysValidator.validateReplyTo(Optional.empty()))

    // validi sähköpostiosoite on sallittu
    Assertions.assertEquals(Set.empty, LahetysValidator.validateReplyTo(Optional.of("ville.virkamies@oph.fi")))

    // ei validi sähköpostiosoite ei ole sallittu
    Assertions.assertEquals(Set(LahetysValidator.VALIDATION_REPLYTO_INVALID), LahetysValidator.validateReplyTo(Optional.of("tämä ei ole sähköpostiosoite")))
  }

  @Test def testValidatePrioriteetti(): Unit =
    // laillinen prioriteetti on sallittu
    Assertions.assertEquals(Set.empty, LahetysValidator.validatePrioriteetti(Optional.of(LahetysImpl.LAHETYS_PRIORITEETTI_KORKEA)))

    Assertions.assertEquals(Set.empty, LahetysValidator.validatePrioriteetti(Optional.of(LahetysImpl.LAHETYS_PRIORITEETTI_NORMAALI)))

    // tyhjä prioriteetti ei ole sallittu
    Assertions.assertEquals(Set(LahetysValidator.VALIDATION_PRIORITEETTI), LahetysValidator.validatePrioriteetti(Optional.empty()))
    Assertions.assertEquals(Set(LahetysValidator.VALIDATION_PRIORITEETTI), LahetysValidator.validatePrioriteetti(Optional.of("")))

    // väärä prioriteetti ei ole sallittu
    Assertions.assertEquals(Set(LahetysValidator.VALIDATION_PRIORITEETTI), LahetysValidator.validatePrioriteetti(Optional.of("jotain hämärää")))

  @Test def testValidateSailytysAika(): Unit =
    // laillinen säilytysaika on sallittu
    Assertions.assertEquals(Set.empty, LahetysValidator.validateSailytysAika(Optional.of(5)))

    Assertions.assertEquals(Set.empty, LahetysValidator.validateSailytysAika(Optional.of(365)))

    // olematon säilytysaika ei ole sallittu
    Assertions.assertEquals(Set(LahetysValidator.VALIDATION_SAILYTYSAIKA_TYHJA), LahetysValidator.validateSailytysAika(Optional.empty()))
    Assertions.assertEquals(Set(LahetysValidator.VALIDATION_SAILYTYSAIKA), LahetysValidator.validateSailytysAika(Optional.of(-3)))
    Assertions.assertEquals(Set(LahetysValidator.VALIDATION_SAILYTYSAIKA), LahetysValidator.validateSailytysAika(Optional.of(0)))
}
