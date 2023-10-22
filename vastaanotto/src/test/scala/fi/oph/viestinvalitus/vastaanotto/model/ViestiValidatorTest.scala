package fi.oph.viestinvalitus.vastaanotto.model

import org.junit.jupiter.api.{Assertions, Test}

import java.util
import java.util.Optional

@Test
class ViestiValidatorTest {

  @Test def testValidateOtsikko(): Unit = {
    // laillinen otsikko on sallittu
    Assertions.assertEquals(Set.empty, ViestiValidator.validateOtsikko("Tosi hyvä otsikko"))

    // tyhjä otsikko ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_OTSIKKO_TYHJA), ViestiValidator.validateOtsikko(null))
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_OTSIKKO_TYHJA), ViestiValidator.validateOtsikko(""))

    // liian pitkä otsikko ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_OTSIKKO_LIIAN_PITKA), ViestiValidator.validateOtsikko("x".repeat(Viesti.OTSIKKO_MAX_PITUUS + 1)))
  }

  @Test def testValidateSisalto(): Unit = {
    // laillinen sisältö on sallittu
    Assertions.assertEquals(Set.empty, ViestiValidator.validateSisalto("Tosi hyvä sisältö"))

    // tyhjä sisältö ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_SISALTO_TYHJA), ViestiValidator.validateSisalto(null))
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_SISALTO_TYHJA), ViestiValidator.validateSisalto(""))

    // liian pitkä sisältö ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_SISALTO_LIIAN_PITKA), ViestiValidator.validateSisalto("x".repeat(Viesti.SISALTO_MAX_PITUUS + 1)))
  }

  @Test def testValidateSisallonTyyppi(): Unit = {
    // laillinen sisällönTyyppi on sallittu
    Assertions.assertEquals(Set.empty, ViestiValidator.validateSisallonTyyppi(Viesti.VIESTI_SISALTOTYYPPI_TEXT))
    Assertions.assertEquals(Set.empty, ViestiValidator.validateSisallonTyyppi(Viesti.VIESTI_SISALTOTYYPPI_HTML))

    // tyhjä sisällönTyyppi ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_SISALLONTYYPPI), ViestiValidator.validateSisallonTyyppi(null))
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_SISALLONTYYPPI), ViestiValidator.validateSisallonTyyppi(""))

    // väärä sisällönTyyppi ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_SISALLONTYYPPI), ViestiValidator.validateSisallonTyyppi("jotain hämärää"))
  }

  @Test def testValidateKielet(): Unit = {
    // lailliset kielet ovat sallittuja
    Assertions.assertEquals(Set.empty, ViestiValidator.validateKielet(java.util.List.of("fi", "sv")))
    Assertions.assertEquals(Set.empty, ViestiValidator.validateKielet(java.util.List.of("sv", "fi")))
    Assertions.assertEquals(Set.empty, ViestiValidator.validateKielet(java.util.List.of("en")))

    // määrittelemätön kieli ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_KIELET_TYHJA), ViestiValidator.validateKielet(null))
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_KIELET_TYHJA), ViestiValidator.validateKielet(java.util.List.of()))

    // ei validit kielit eivät sallittuja
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_KIELI_EI_SALLITTU + "de"), ViestiValidator.validateKielet(util.List.of("de")))

    // null arvot eivät ole sallittuja
    val kielet = util.ArrayList[String]()
    kielet.add("en")
    kielet.add(null)
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_KIELI_NULL), ViestiValidator.validateKielet(kielet))
  }

  @Test def testValidateLahettajanOid(): Unit = {
    // oph-oidit ovat sallittuja
    Assertions.assertEquals(Set.empty, ViestiValidator.validateLahettavanVirkailijanOID(Optional.of(ViestiValidator.VALIDATION_OPH_OID_PREFIX + ".123.456.00001")))
    Assertions.assertEquals(Set.empty, ViestiValidator.validateLahettavanVirkailijanOID(Optional.of(ViestiValidator.VALIDATION_OPH_OID_PREFIX + ".789.987.00001")))

    // määrittelemätön oid on sallittu
    Assertions.assertEquals(Set.empty, ViestiValidator.validateLahettavanVirkailijanOID(Optional.empty()))

    // muut kuin oph-oidit eivät ole sallittuja
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LAHETTAJAN_OID), ViestiValidator.validateLahettavanVirkailijanOID(Optional.of("123.456.789")))
  }

  @Test def testValidateLahettaja(): Unit = {
    // lähettäjät joiden nimi määritelty ja osoite validi ovat sallittuja
    Assertions.assertEquals(Set.empty, ViestiValidator.validateLahettaja(Lahettaja("Opetushallitus", "noreply@opintopolku.fi")))
    Assertions.assertEquals(Set.empty, ViestiValidator.validateLahettaja(Lahettaja("Joku muu", "jotain@opintopolku.fi")))

    // määrittelemätön nimi ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LAHETTAJAN_NIMI_TYHJA), ViestiValidator.validateLahettaja(Lahettaja(null, "noreply@opintopolku.fi")))

    // määrittelemätön osoite ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LAHETTAJAN_OSOITE_TYHJA), ViestiValidator.validateLahettaja(Lahettaja("Opetushallitus", null)))

    // ei validi osoite ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LAHETTAJAN_OSOITE_INVALID), ViestiValidator.validateLahettaja(Lahettaja("Opetushallitus", "ei validi osoite")))

    // ei opintopolku.fi -domain ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LAHETTAJAN_OSOITE_DOMAIN), ViestiValidator.validateLahettaja(Lahettaja("Opetushallitus", "noreply@example.com")))
  }

  @Test def testValidateVastaanottajat(): Unit = {
    // Vastaanottajat joiden nimi määritelty ja osoite validi ovat sallittuja
    Assertions.assertEquals(Set.empty, ViestiValidator.validateVastaanottajat(java.util.List.of(
      Vastaanottaja("Vallu Vastaanottaja", "vallu.vastaanottaja@example.com"),
    )))

    // null-arvot vastaanottajalistassa eivät ole sallittuja
    val vastaanottajat = new util.ArrayList[Vastaanottaja]()
    vastaanottajat.add(Vastaanottaja("Vallu Vastaanottaja", "vallu.vastaanottaja@example.com"))
    vastaanottajat.add(null)
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_VASTAANOTTAJA_NULL), ViestiValidator.validateVastaanottajat(vastaanottajat))

    // määrittelemätön nimi ei ole sallittu
    Assertions.assertEquals(Set("Vastaanottaja (nimi: null, sähköpostiosoite: vallu.vastaanottaja@example.com): " + ViestiValidator.VALIDATION_VASTAANOTTAJAN_NIMI_TYHJA),
      ViestiValidator.validateVastaanottajat(java.util.List.of(Vastaanottaja(null, "vallu.vastaanottaja@example.com"))))

    // määrittelemätön sähköpostiosoite ei ole sallittu
    Assertions.assertEquals(Set("Vastaanottaja (nimi: Vallu Vastaanottaja, sähköpostiosoite: null): " + ViestiValidator.VALIDATION_VASTAANOTTAJAN_OSOITE_TYHJA),
      ViestiValidator.validateVastaanottajat(java.util.List.of(Vastaanottaja("Vallu Vastaanottaja", null))))

    // ei validi sähköpostiosoite ei ole sallittu
    Assertions.assertEquals(Set("Vastaanottaja (nimi: Vallu Vastaanottaja, sähköpostiosoite: ei validi osoite): " + ViestiValidator.VALIDATION_VASTAANOTTAJAN_OSOITE_INVALID),
      ViestiValidator.validateVastaanottajat(java.util.List.of(Vastaanottaja("Vallu Vastaanottaja", "ei validi osoite"))))

    // kaikki virheet kerätään
    Assertions.assertEquals(Set(
      "Vastaanottaja (nimi: Vallu Vastaanottaja, sähköpostiosoite: null): " + ViestiValidator.VALIDATION_VASTAANOTTAJAN_OSOITE_TYHJA,
      "Vastaanottaja (nimi: Vallu Vastaanottaja, sähköpostiosoite: ei validi osoite): " + ViestiValidator.VALIDATION_VASTAANOTTAJAN_OSOITE_INVALID),
      ViestiValidator.validateVastaanottajat(java.util.List.of(
        Vastaanottaja("Vallu Vastaanottaja", null),
        Vastaanottaja("Vallu Vastaanottaja", "ei validi osoite")
      )))

    // duplikaattiosoitteet eivät ole sallittuja
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_VASTAANOTTAJA_OSOITE_DUPLICATE + "vallu.vastaanottaja@example.com"),
      ViestiValidator.validateVastaanottajat(java.util.List.of(
        Vastaanottaja("Vallu Vastaanottaja", "vallu.vastaanottaja@example.com"),
        Vastaanottaja("Vallu Vastaanottaja", "vallu.vastaanottaja@example.com")
      )))
  }

  @Test def testValidateLiiteTunnisteet(): Unit =
    val VALIDI_LIITETUNNISTE1 = "3fa85f64-5717-4562-b3fc-2c963f66afa6";
    val IDENTITEETTI1 = "jarjestelma1"
    val VALIDI_LIITETUNNISTE2 = "4fa85f64-5717-4562-b3fc-2c963f66afa6";
    val IDENTITEETTI2 = "jarjestelma2"
    val testValidator: LiiteTunnisteIdentityProvider = liiteTunniste => {
        if(VALIDI_LIITETUNNISTE1.equals(liiteTunniste))
          Option.apply(IDENTITEETTI1)
        else if(VALIDI_LIITETUNNISTE2.equals(liiteTunniste))
          Option.apply(IDENTITEETTI2)
        else
          Option.empty
      }

    // validit liitetunnisteet ovat sallittuja tunnisteet ladanneelle identiteetille
    Assertions.assertEquals(Set.empty, ViestiValidator.validateLiitteidenTunnisteet(util.List.of(VALIDI_LIITETUNNISTE1), testValidator, IDENTITEETTI1))

    // null-arvot liitetunnistelistassa eivät ole sallittuja
    val tunnisteet = new util.ArrayList[String]()
    tunnisteet.add(VALIDI_LIITETUNNISTE1)
    tunnisteet.add(null)
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LIITETUNNISTE_NULL), ViestiValidator.validateLiitteidenTunnisteet(tunnisteet, testValidator, IDENTITEETTI1))

    // väärän muotoinen liitetunniste ei ole sallittu
    val EI_UUID_MUOTOINEN_TUNNISTE = "ei uuid-muotoinen tunniste"
    Assertions.assertEquals(Set("Liitetunniste \"" + EI_UUID_MUOTOINEN_TUNNISTE + "\": " + ViestiValidator.VALIDATION_LIITETUNNISTE_INVALID),
      ViestiValidator.validateLiitteidenTunnisteet(util.List.of(EI_UUID_MUOTOINEN_TUNNISTE), testValidator, IDENTITEETTI1))

    // oikean muotoinen liitetunniste jolla ei liitettä ei sallittu
    val EI_TARJOLLA_OLEVA_LIITETUNNISTE = "4fa85f64-5717-4562-b3fc-2c963f66afa6";
    Assertions.assertEquals(Set("Liitetunniste \"" + EI_TARJOLLA_OLEVA_LIITETUNNISTE + "\": " + ViestiValidator.VALIDATION_LIITETUNNISTE_EI_TARJOLLA),
      ViestiValidator.validateLiitteidenTunnisteet(util.List.of(EI_TARJOLLA_OLEVA_LIITETUNNISTE), testValidator, IDENTITEETTI1))

    // toisen identiteetin lataama tunniste ei sallittu (VALIDI_LIITETUNNISTE2, IDENTITEETTI1)
    Assertions.assertEquals(Set("Liitetunniste \"" + VALIDI_LIITETUNNISTE2 + "\": " + ViestiValidator.VALIDATION_LIITETUNNISTE_EI_TARJOLLA),
      ViestiValidator.validateLiitteidenTunnisteet(util.List.of(VALIDI_LIITETUNNISTE2), testValidator, IDENTITEETTI1))

    // kaikki virheet kerätään
    Assertions.assertEquals(Set(
      "Liitetunniste \"" + EI_UUID_MUOTOINEN_TUNNISTE + "\": " + ViestiValidator.VALIDATION_LIITETUNNISTE_INVALID,
      "Liitetunniste \"" + EI_TARJOLLA_OLEVA_LIITETUNNISTE + "\": " + ViestiValidator.VALIDATION_LIITETUNNISTE_EI_TARJOLLA
    ), ViestiValidator.validateLiitteidenTunnisteet(util.List.of(EI_UUID_MUOTOINEN_TUNNISTE, EI_TARJOLLA_OLEVA_LIITETUNNISTE), testValidator, IDENTITEETTI1))

    // duplikaatit eivät sallittuja
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LIITETUNNISTE_DUPLICATE + VALIDI_LIITETUNNISTE1),
      ViestiValidator.validateLiitteidenTunnisteet(util.List.of(VALIDI_LIITETUNNISTE1, VALIDI_LIITETUNNISTE1), testValidator, IDENTITEETTI1))

  @Test def testValidateLahettavaPalvelu(): Unit =
    // validin muotoiset avaimet sallittu
    Assertions.assertEquals(Set.empty, ViestiValidator.validateLahettavaPalvelu("kaannosavain1"))
    Assertions.assertEquals(Set.empty, ViestiValidator.validateLahettavaPalvelu("kaannosavain2"))

    // tyhjä käännösavain ei sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LAHETTAVA_PALVELU_TYHJA), ViestiValidator.validateLahettavaPalvelu(null))

    // liian pitkä avain ei sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LAHETTAVA_PALVELU_LIIAN_PITKA), ViestiValidator.validateLahettavaPalvelu("x".repeat(Viesti.LAHETTAVAPALVELU_MAX_PITUUS + 1)))

    // väärän muotoinen avain ei sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LAHETTAVA_PALVELU_INVALID), ViestiValidator.validateLahettavaPalvelu("!\\?*"))

    // kaikki virheet kerätään
    Assertions.assertEquals(Set(
      ViestiValidator.VALIDATION_LAHETTAVA_PALVELU_LIIAN_PITKA,
      ViestiValidator.VALIDATION_LAHETTAVA_PALVELU_INVALID
    ), ViestiValidator.validateLahettavaPalvelu("x".repeat(Viesti.LAHETTAVAPALVELU_MAX_PITUUS) + "!\\?*"))

  @Test def validateLahetysTunniste(): Unit =
    val VALIDI_LAHETYSTUNNISTE1 = "3fa85f64-5717-4562-b3fc-2c963f66afa6";
    val IDENTITEETTI1 = "jarjestelma1"
    val VALIDI_LAHETYSTUNNISTE2 = "4fa85f64-5717-4562-b3fc-2c963f66afa6";
    val IDENTITEETTI2 = "jarjestelma2"
    val testProvider: LahetysTunnisteIdentityProvider = lahetysTunniste => {
      if (VALIDI_LAHETYSTUNNISTE1.equals(lahetysTunniste))
        Option.apply(IDENTITEETTI1)
      else if (VALIDI_LAHETYSTUNNISTE2.equals(lahetysTunniste))
        Option.apply(IDENTITEETTI2)
      else
        Option.empty
    }

    // määrittelemätön tunniste on sallittu
    Assertions.assertEquals(Set.empty, ViestiValidator.validateLahetysTunniste(null, testProvider, IDENTITEETTI1))

    // järjestelmän luoma tunniste on sallittu
    Assertions.assertEquals(Set.empty, ViestiValidator.validateLahetysTunniste(VALIDI_LAHETYSTUNNISTE1, testProvider, IDENTITEETTI1))

    // ei validi tunniste ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LAHETYSTUNNISTE_INVALID), ViestiValidator.validateLahetysTunniste("jotain hämärää", testProvider, IDENTITEETTI1))

    // toisen identiteetin luoma tunniste ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LAHETYSTUNNISTE_EI_TARJOLLA), ViestiValidator.validateLahetysTunniste(VALIDI_LAHETYSTUNNISTE1, testProvider, IDENTITEETTI2))

  @Test def testValidatePrioriteetti(): Unit =
    // laillinen prioriteetti on sallittu
    Assertions.assertEquals(Set.empty, ViestiValidator.validatePrioriteetti(Viesti.VIESTI_PRIORITEETTI_KORKEA))
    Assertions.assertEquals(Set.empty, ViestiValidator.validatePrioriteetti(Viesti.VIESTI_PRIORITEETTI_NORMAALI))

    // tyhjä prioriteetti ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_PRIORITEETTI), ViestiValidator.validatePrioriteetti(null))
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_PRIORITEETTI), ViestiValidator.validatePrioriteetti(""))

    // väärä prioriteetti ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_PRIORITEETTI), ViestiValidator.validatePrioriteetti("jotain hämärää"))

  @Test def testValidateSailytysAika(): Unit =
    // laillinnen säilytysaika on sallittu
    Assertions.assertEquals(Set.empty, ViestiValidator.validateSailytysAika(5))
    Assertions.assertEquals(Set.empty, ViestiValidator.validateSailytysAika(365))

    // olemation säilytysaika ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_SAILYTYSAIKA), ViestiValidator.validateSailytysAika(-3))
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_SAILYTYSAIKA), ViestiValidator.validateSailytysAika(0))

  @Test def testValidateKayttooikeusRajoitukset(): Unit =
    val RAJOITUS = "RAJOITUS1"

    // merkkijonot ovat sallittuja
    Assertions.assertEquals(Set.empty, ViestiValidator.validateKayttooikeusRajoitukset(util.List.of(RAJOITUS)))

    // null-arvot käyttöoikeustunnistelistassa eivät ole sallittuja
    val rajoitukset = new util.ArrayList[String]()
    rajoitukset.add(RAJOITUS)
    rajoitukset.add(null)
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_KAYTTOOIKEUSRAJOITUS_NULL), ViestiValidator.validateKayttooikeusRajoitukset(rajoitukset))

    // duplikaatit eivät sallittuja
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_KAYTTOOIKEUSRAJOITUS_DUPLICATE + RAJOITUS),
      ViestiValidator.validateKayttooikeusRajoitukset(util.List.of(RAJOITUS, RAJOITUS)))

    // kaikki virheet kerätään
    val rajoitukset2 = new util.ArrayList[String]()
    rajoitukset2.add(RAJOITUS)
    rajoitukset2.add(null)
    rajoitukset2.add(RAJOITUS)
    Assertions.assertEquals(Set(
        ViestiValidator.VALIDATION_KAYTTOOIKEUSRAJOITUS_NULL,
        ViestiValidator.VALIDATION_KAYTTOOIKEUSRAJOITUS_DUPLICATE + RAJOITUS
      ), ViestiValidator.validateKayttooikeusRajoitukset(rajoitukset2))

  @Test def testValidateMetadata(): Unit =
    // merkkijonot ovat sallittuja
    Assertions.assertEquals(Set.empty, ViestiValidator.validateMetadata(util.Map.of("avain1", "arvo1", "avain2", "arvo2")))

    // null-arvot eivät ole sallittuja
    val metadata = util.HashMap[String, String]()
    metadata.put("avain1", null)
    metadata.put("avain2", "arvo2")
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_METADATA_NULL + "avain1"),
      ViestiValidator.validateMetadata(metadata))

  @Test def testValidateKorkeaPrioriteetti(): Unit = {
    // lailliset prioriteetti-vastaanottajamääräkombot ovat sallittuja
    Assertions.assertEquals(Set.empty, ViestiValidator.validateKorkeaPrioriteetti(Viesti.VIESTI_PRIORITEETTI_KORKEA, java.util.List.of(
      Vastaanottaja("Vallu Vastaanottaja", "vallu.vastaanottaja@example.com")
    )))
    Assertions.assertEquals(Set.empty, ViestiValidator.validateKorkeaPrioriteetti(Viesti.VIESTI_PRIORITEETTI_NORMAALI, java.util.List.of(
      Vastaanottaja("Vallu Vastaanottaja", "vallu.vastaanottaja@example.com"),
      Vastaanottaja("Veera Vastaanottaja", "veera.vastaanottaja@example.com")
    )))

    // korkealla prioriteetilla voi olla vain yksi vastaanottaja
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_KORKEA_PRIORITEETTI_VASTAANOTTAJAT), ViestiValidator.validateKorkeaPrioriteetti(Viesti.VIESTI_PRIORITEETTI_KORKEA, java.util.List.of(
      Vastaanottaja("Vallu Vastaanottaja", "vallu.vastaanottaja@example.com"),
      Vastaanottaja("Veera Vastaanottaja", "veera.vastaanottaja@example.com")
    )))
  }
}
