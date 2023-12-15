package fi.oph.viestinvalitys.vastaanotto.model

import org.junit.jupiter.api.{Assertions, Test}

import java.util
import java.util.{Optional, UUID}

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
    val liiteMetadatat = Map(
      UUID.fromString(VALIDI_LIITETUNNISTE1) -> LiiteMetadata(IDENTITEETTI1, 0),
      UUID.fromString(VALIDI_LIITETUNNISTE2) -> LiiteMetadata(IDENTITEETTI2, 0)
    )

    // validit liitetunnisteet ovat sallittuja tunnisteet ladanneelle identiteetille
    Assertions.assertEquals(Set.empty, ViestiValidator.validateLiitteidenTunnisteet(util.List.of(VALIDI_LIITETUNNISTE1), liiteMetadatat, IDENTITEETTI1))

    // null-arvot liitetunnistelistassa eivät ole sallittuja
    val tunnisteet = new util.ArrayList[String]()
    tunnisteet.add(VALIDI_LIITETUNNISTE1)
    tunnisteet.add(null)
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LIITETUNNISTE_NULL), ViestiValidator.validateLiitteidenTunnisteet(tunnisteet, liiteMetadatat, IDENTITEETTI1))

    // väärän muotoinen liitetunniste ei ole sallittu
    val EI_UUID_MUOTOINEN_TUNNISTE = "ei uuid-muotoinen tunniste"
    Assertions.assertEquals(Set("Liitetunniste \"" + EI_UUID_MUOTOINEN_TUNNISTE + "\": " + ViestiValidator.VALIDATION_LIITETUNNISTE_INVALID),
      ViestiValidator.validateLiitteidenTunnisteet(util.List.of(EI_UUID_MUOTOINEN_TUNNISTE), liiteMetadatat, IDENTITEETTI1))

    // oikean muotoinen liitetunniste jolla ei liitettä ei sallittu
    val EI_TARJOLLA_OLEVA_LIITETUNNISTE = "4fa85f64-5717-4562-b3fc-2c963f66afa6";
    Assertions.assertEquals(Set("Liitetunniste \"" + EI_TARJOLLA_OLEVA_LIITETUNNISTE + "\": " + ViestiValidator.VALIDATION_LIITETUNNISTE_EI_TARJOLLA),
      ViestiValidator.validateLiitteidenTunnisteet(util.List.of(EI_TARJOLLA_OLEVA_LIITETUNNISTE), liiteMetadatat, IDENTITEETTI1))

    // toisen identiteetin lataama tunniste ei sallittu (VALIDI_LIITETUNNISTE2, IDENTITEETTI1)
    Assertions.assertEquals(Set("Liitetunniste \"" + VALIDI_LIITETUNNISTE2 + "\": " + ViestiValidator.VALIDATION_LIITETUNNISTE_EI_TARJOLLA),
      ViestiValidator.validateLiitteidenTunnisteet(util.List.of(VALIDI_LIITETUNNISTE2), liiteMetadatat, IDENTITEETTI1))

    // kaikki virheet kerätään
    Assertions.assertEquals(Set(
      "Liitetunniste \"" + EI_UUID_MUOTOINEN_TUNNISTE + "\": " + ViestiValidator.VALIDATION_LIITETUNNISTE_INVALID,
      "Liitetunniste \"" + EI_TARJOLLA_OLEVA_LIITETUNNISTE + "\": " + ViestiValidator.VALIDATION_LIITETUNNISTE_EI_TARJOLLA
    ), ViestiValidator.validateLiitteidenTunnisteet(util.List.of(EI_UUID_MUOTOINEN_TUNNISTE, EI_TARJOLLA_OLEVA_LIITETUNNISTE), liiteMetadatat, IDENTITEETTI1))

    // duplikaatit eivät sallittuja
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LIITETUNNISTE_DUPLICATE + VALIDI_LIITETUNNISTE1),
      ViestiValidator.validateLiitteidenTunnisteet(util.List.of(VALIDI_LIITETUNNISTE1, VALIDI_LIITETUNNISTE1), liiteMetadatat, IDENTITEETTI1))

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

    // määrittelemätön tunniste on sallittu
    Assertions.assertEquals(Set.empty, ViestiValidator.validateLahetysTunniste(null, Option.empty, IDENTITEETTI1))
    Assertions.assertEquals(Set.empty, ViestiValidator.validateLahetysTunniste("", Option.empty, IDENTITEETTI1))

    // järjestelmän luoma tunniste on sallittu
    Assertions.assertEquals(Set.empty, ViestiValidator.validateLahetysTunniste(VALIDI_LAHETYSTUNNISTE1, Option(LahetysMetadata(IDENTITEETTI1)), IDENTITEETTI1))

    // ei validi tunniste ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LAHETYSTUNNISTE_INVALID), ViestiValidator.validateLahetysTunniste("jotain hämärää", Option.empty, IDENTITEETTI1))

    // toisen identiteetin luoma tunniste ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LAHETYSTUNNISTE_EI_TARJOLLA), ViestiValidator.validateLahetysTunniste(VALIDI_LAHETYSTUNNISTE1, Option(LahetysMetadata(IDENTITEETTI1)), IDENTITEETTI2))

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
    // laillinen säilytysaika on sallittu
    Assertions.assertEquals(Set.empty, ViestiValidator.validateSailytysAika(5))
    Assertions.assertEquals(Set.empty, ViestiValidator.validateSailytysAika(365))

    // olemation säilytysaika ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_SAILYTYSAIKA), ViestiValidator.validateSailytysAika(-3))
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_SAILYTYSAIKA), ViestiValidator.validateSailytysAika(0))

  @Test def testValidateKayttooikeusRajoitukset(): Unit =
    val RAJOITUS = "RAJOITUS1_1.2.246.562.00.00000000000000006666"
    val RAJOITUS_INVALID = "RAJOITUS1"

    // kenttä ei ole pakollinen (jos ei määritelty niin vain rekisterinpitäjä voi katsoa viestejä)
    Assertions.assertEquals(Set.empty, ViestiValidator.validateKayttooikeusRajoitukset(null))

    // merkkijonot ovat sallittuja
    Assertions.assertEquals(Set.empty, ViestiValidator.validateKayttooikeusRajoitukset(util.List.of(RAJOITUS)))

    // arvojen pitää olla organisaatiorajoitettuja, ts. loppua oidiin
    Assertions.assertEquals(Set("Käyttöoikeusrajoitus \"RAJOITUS1\": " + ViestiValidator.VALIDATION_KAYTTOOIKEUSRAJOITUS_INVALID), ViestiValidator.validateKayttooikeusRajoitukset(java.util.List.of(RAJOITUS_INVALID)))

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
    // merkkijonot joissa ei duplikaatteja saman avaimen sisällä ovat sallittuja
    Assertions.assertEquals(Set.empty, ViestiValidator.validateMetadata(
      util.Map.of("avain1", java.util.List.of("arvo1"), "avain2", java.util.List.of("arvo1", "arvo2"))))

    // null-arvot eivät ole sallittuja
    val metadata1 = util.HashMap[String, java.util.List[String]]()
    metadata1.put("avain1", null)
    metadata1.put("avain2", java.util.stream.Stream.of("arvo1", null).toList())
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_METADATA_NULL + "avain1,avain2"),
      ViestiValidator.validateMetadata(metadata1))

    // duplikaattiarvot eivät ole sallittuja
    val metadata2 = util.HashMap[String, java.util.List[String]]()
    metadata2.put("avain1", java.util.List.of("arvo1"))
    metadata2.put("avain2", java.util.List.of("arvo2", "arvo2"))
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_METADATA_DUPLICATE + "avain2"),
      ViestiValidator.validateMetadata(metadata2))

  @Test def testValidateLahetysJaKayttooikeusRajoitukset(): Unit = {
    // ok että lähetys määritelty ja käyttöoikeusrajoituksia ei
    Assertions.assertEquals(Set.empty, ViestiValidator.validateLahetysJaKayttooikeusRajoitukset(UUID.randomUUID().toString, null))

    // ok että käyttöoikeusrajoitukset määritelty mutta lähetystä ei
    Assertions.assertEquals(Set.empty, ViestiValidator.validateLahetysJaKayttooikeusRajoitukset(null, new java.util.ArrayList()))
    Assertions.assertEquals(Set.empty, ViestiValidator.validateLahetysJaKayttooikeusRajoitukset("", new java.util.ArrayList()))

    // ok että lähetys ja käyttöoikeusrajoitukset ei kumpikaan määritelty
    Assertions.assertEquals(Set.empty, ViestiValidator.validateLahetysJaKayttooikeusRajoitukset(null, null))
    Assertions.assertEquals(Set.empty, ViestiValidator.validateLahetysJaKayttooikeusRajoitukset("", null))

    // lähetys ja käyttöoikeusrajoitukset molemmat määritelty on virhe
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_KAYTTOOIKEUSRAJOITUS_EI_TYHJA),
      ViestiValidator.validateLahetysJaKayttooikeusRajoitukset(UUID.randomUUID().toString, new util.ArrayList[String]()))
  }

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

  @Test def testValidateViestinKoko(): Unit = {
    val identiteetti1 = "identiteetti1"
    val identiteetti2 = "identiteetti2"
    val liiteTunniste1 = UUID.randomUUID();
    val liiteTunniste2 = UUID.randomUUID();
    val liiteMetadata1 = Map(liiteTunniste1 -> LiiteMetadata(identiteetti1, 1024*1024))
    val liiteMetadata2 = Map(liiteTunniste2 -> LiiteMetadata(identiteetti2, ViestiValidator.VIESTI_MAX_SIZE + 1))

    // alle maksimikoon olevat viestit ovat sallittuja
    Assertions.assertEquals(Set.empty, ViestiValidator.validateKoko("Sisältö", java.util.List.of(liiteTunniste1.toString), liiteMetadata1, identiteetti1))

    // liian iso liite auheuttaa virheen
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_KOKO), ViestiValidator.validateKoko("Sisältö", java.util.List.of(liiteTunniste2.toString), liiteMetadata2, identiteetti2))

    // liian iso sisältö auheuttaa virheen
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_KOKO), ViestiValidator.validateKoko("x".repeat(ViestiValidator.VIESTI_MAX_SIZE + 1), java.util.List.of(), Map.empty, identiteetti1))

    // liitteet joilta puuttuu metadata ignotaan (tämä tarkoittaa ettei liitettä ole olemassa, aiheuttaa toisenlaisen virheen)
    Assertions.assertEquals(Set.empty, ViestiValidator.validateKoko("Sisältö", java.util.List.of(liiteTunniste1.toString), Map.empty, identiteetti1))

    // muiden omistamat liitteet ignotaan (ei anneta tietoa muiden omistamien liitteiden koosta)
    Assertions.assertEquals(Set.empty, ViestiValidator.validateKoko("Sisältö", java.util.List.of(liiteTunniste2.toString), liiteMetadata2, identiteetti1))
  }
}
