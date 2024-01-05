package fi.oph.viestinvalitys.vastaanotto.model

import Viesti.{Lahettaja, Maski, Vastaanottaja}
import org.junit.jupiter.api.{Assertions, Test}

import java.util
import java.util.{Collections, Optional, UUID}
import scala.jdk.CollectionConverters.*

@Test
class ViestiValidatorTest {

  @Test def testValidateOtsikko(): Unit = {
    // laillinen otsikko on sallittu
    Assertions.assertEquals(Set.empty, ViestiValidator.validateOtsikko(Optional.of("Tosi hyvä otsikko")))

    // tyhjä otsikko ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_OTSIKKO_TYHJA), ViestiValidator.validateOtsikko(Optional.empty()))
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_OTSIKKO_TYHJA), ViestiValidator.validateOtsikko(Optional.of("")))

    // liian pitkä otsikko ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_OTSIKKO_LIIAN_PITKA), ViestiValidator.validateOtsikko(Optional.of("x".repeat(ViestiImpl.OTSIKKO_MAX_PITUUS + 1))))
  }

  @Test def testValidateSisalto(): Unit = {
    // laillinen sisältö on sallittu
    Assertions.assertEquals(Set.empty, ViestiValidator.validateSisalto(Optional.of("Tosi hyvä sisältö")))

    // tyhjä sisältö ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_SISALTO_TYHJA), ViestiValidator.validateSisalto(Optional.empty()))
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_SISALTO_TYHJA), ViestiValidator.validateSisalto(Optional.of("")))

    // liian pitkä sisältö ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_SISALTO_LIIAN_PITKA), ViestiValidator.validateSisalto(Optional.of("x".repeat(ViestiImpl.SISALTO_MAX_PITUUS + 1))))
  }

  @Test def testValidateSisallonTyyppi(): Unit = {
    // laillinen sisällönTyyppi on sallittu
    Assertions.assertEquals(Set.empty, ViestiValidator.validateSisallonTyyppi(Optional.of(ViestiImpl.VIESTI_SISALTOTYYPPI_TEXT)))
    Assertions.assertEquals(Set.empty, ViestiValidator.validateSisallonTyyppi(Optional.of(ViestiImpl.VIESTI_SISALTOTYYPPI_HTML)))

    // tyhjä sisällönTyyppi ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_SISALLONTYYPPI), ViestiValidator.validateSisallonTyyppi(Optional.empty()))
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_SISALLONTYYPPI), ViestiValidator.validateSisallonTyyppi(Optional.of("")))

    // väärä sisällönTyyppi ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_SISALLONTYYPPI), ViestiValidator.validateSisallonTyyppi(Optional.of("jotain hämärää")))
  }

  @Test def testValidateKielet(): Unit = {
    // lailliset kielet ovat sallittuja
    Assertions.assertEquals(Set.empty, ViestiValidator.validateKielet(Optional.of(util.List.of("fi", "sv"))))
    Assertions.assertEquals(Set.empty, ViestiValidator.validateKielet(Optional.of(util.List.of("sv", "fi"))))
    Assertions.assertEquals(Set.empty, ViestiValidator.validateKielet(Optional.of(util.List.of("en"))))

    // määrittelemätön kieli ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_KIELET_TYHJA), ViestiValidator.validateKielet(Optional.empty()))
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_KIELET_TYHJA), ViestiValidator.validateKielet(Optional.of(util.List.of())))

    // ei validit kielit eivät sallittuja
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_KIELI_EI_SALLITTU + "de"), ViestiValidator.validateKielet(Optional.of(util.List.of("de"))))

    // null arvot eivät ole sallittuja
    val kielet = util.ArrayList[String]()
    kielet.add("en")
    kielet.add(null)
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_KIELI_NULL), ViestiValidator.validateKielet(Optional.of(kielet)))
  }

  @Test def testValidateMaskit(): Unit = {

    def getMaski(salaisuus: String, maski: String): MaskiImpl =
      MaskiImpl(Optional.ofNullable(salaisuus), Optional.ofNullable(maski))

    // maskit-kenttä ei ole pakollinen
    Assertions.assertEquals(Set.empty, ViestiValidator.validateMaskit(Optional.empty))
    Assertions.assertEquals(Set.empty, ViestiValidator.validateMaskit(Optional.of(util.List.of())))

    // maskit joiden salaisuus on määritelty ovat sallittuja
    Assertions.assertEquals(Set.empty, ViestiValidator.validateMaskit(Optional.of(util.List.of(
      getMaski("salaisuus1", "<salaisuus peitetty>"),
      getMaski("salaisuus2", null),
    ))))

    // null-arvot maskilistassa eivät ole sallittuja
    val maskit = new util.ArrayList[Maski]()
    maskit.add(getMaski("salaisuus1", "<salaisuus peitetty>"))
    maskit.add(null)
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_MASKIT_NULL), ViestiValidator.validateMaskit(Optional.of(maskit)))

    // määrittelemätön maski on sallittu
    Assertions.assertEquals(Set.empty, ViestiValidator.validateMaskit(Optional.of(util.List.of(getMaski("salaisuus1", null)))))

    // määrittelemätön salaisuus ei ole sallittu
    Assertions.assertEquals(Set("Maski (salaisuus: , maski: <salaisuus peitetty>): " + ViestiValidator.VALIDATION_MASKIT_EI_SALAISUUTTA),
      ViestiValidator.validateMaskit(Optional.of(util.List.of(getMaski(null, "<salaisuus peitetty>")))))

    // salaisuuden pituus on rajoitettu
    Assertions.assertEquals(Set("Maski (salaisuus: " + "*".repeat(ViestiImpl.VIESTI_SALAISUUS_MIN_PITUUS - 1) + ", maski: peitetty): " + ViestiValidator.VALIDATION_MASKIT_SALAISUUS_PITUUS),
      ViestiValidator.validateMaskit(Optional.of(util.List.of(getMaski("*".repeat(ViestiImpl.VIESTI_SALAISUUS_MIN_PITUUS - 1), "peitetty")))))
    Assertions.assertEquals(Set("Maski (salaisuus: " + "*".repeat(ViestiImpl.VIESTI_SALAISUUS_MAX_PITUUS + 1) + ", maski: peitetty): " + ViestiValidator.VALIDATION_MASKIT_SALAISUUS_PITUUS),
      ViestiValidator.validateMaskit(Optional.of(util.List.of(getMaski("*".repeat(ViestiImpl.VIESTI_SALAISUUS_MAX_PITUUS + 1), "peitetty")))))

    // maskin pituus on rajoitettu
    Assertions.assertEquals(Set("Maski (salaisuus: *********, maski: " + "*".repeat(ViestiImpl.VIESTI_MASKI_MIN_PITUUS-1) + "): " + ViestiValidator.VALIDATION_MASKIT_MASKI_PITUUS),
      ViestiValidator.validateMaskit(Optional.of(util.List.of(getMaski("salaisuus", "*".repeat(ViestiImpl.VIESTI_MASKI_MIN_PITUUS-1))))))
    Assertions.assertEquals(Set("Maski (salaisuus: *********, maski: " + "*".repeat(ViestiImpl.VIESTI_MASKI_MAX_PITUUS + 1) + "): " + ViestiValidator.VALIDATION_MASKIT_MASKI_PITUUS),
      ViestiValidator.validateMaskit(Optional.of(util.List.of(getMaski("salaisuus", "*".repeat(ViestiImpl.VIESTI_MASKI_MAX_PITUUS + 1))))))

    // kaikki virheet kerätään
    val maskit2 = new util.ArrayList[Maski]()
    maskit2.add(getMaski(null, "<salaisuus peitetty>"))
    maskit2.add(null)
    Assertions.assertEquals(Set(
      "Maski (salaisuus: , maski: <salaisuus peitetty>): " + ViestiValidator.VALIDATION_MASKIT_EI_SALAISUUTTA,
      ViestiValidator.VALIDATION_MASKIT_NULL),
      ViestiValidator.validateMaskit(Optional.of(maskit2)))

    // duplikaattiosoitteet eivät ole sallittuja
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_MASKIT_DUPLICATES + "*********"),
      ViestiValidator.validateMaskit(Optional.of(util.List.of(
        getMaski("salaisuus", "<salaisuus peitetty>"),
        getMaski("salaisuus", "<salaisuus peitetty toisin>")
      ))))
  }

  @Test def testValidateLahettajanOid(): Unit = {
    // oph-oidit ovat sallittuja
    Assertions.assertEquals(Set.empty, ViestiValidator.validateLahettavanVirkailijanOID(Optional.of(ViestiValidator.VALIDATION_OPH_OID_PREFIX + ".123.456.00001")))
    Assertions.assertEquals(Set.empty, ViestiValidator.validateLahettavanVirkailijanOID(Optional.of(ViestiValidator.VALIDATION_OPH_OID_PREFIX + ".789.987.00001")))

    // määrittelemätön oid on sallittu
    Assertions.assertEquals(Set.empty, ViestiValidator.validateLahettavanVirkailijanOID(Optional.empty()))

    // muut kuin oph-oidit eivät ole sallittuja
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LAHETTAJAN_OID_INVALID), ViestiValidator.validateLahettavanVirkailijanOID(Optional.of("123.456.789")))

    // liian pitkä oid ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LAHETTAJAN_OID_PITUUS), ViestiValidator.validateLahettavanVirkailijanOID(Optional.of(ViestiValidator.VALIDATION_OPH_OID_PREFIX + "." + "0".repeat(ViestiImpl.VIESTI_VIRKALIJAN_OID_MAX_PITUUS))))
  }

  @Test def testValidateLahettaja(): Unit = {

    def getLahettaja(nimi: String, sahkoposti: String): Optional[Lahettaja] =
      Optional.of(LahettajaImpl(Optional.ofNullable(nimi), Optional.ofNullable(sahkoposti)))

    // lähettäjät joiden osoite validi ovat sallittuja
    Assertions.assertEquals(Set.empty, ViestiValidator.validateLahettaja(getLahettaja("Opetushallitus", "noreply@opintopolku.fi")))
    Assertions.assertEquals(Set.empty, ViestiValidator.validateLahettaja(getLahettaja(null, "jotain@opintopolku.fi")))

    // määrittelemätön lähettäjä ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LAHETTAJA_TYHJA), ViestiValidator.validateLahettaja(Optional.empty()))

    // määrittelemätön nimi on sallittu
    Assertions.assertEquals(Set.empty, ViestiValidator.validateLahettaja(getLahettaja(null, "noreply@opintopolku.fi")))

    // liian pitkä nimi ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LAHETTAJA_NIMI_LIIAN_PITKA),
      ViestiValidator.validateLahettaja(getLahettaja("x".repeat(ViestiImpl.VIESTI_NIMI_MAX_PITUUS + 1), "noreply@opintopolku.fi")))

    // määrittelemätön osoite ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LAHETTAJAN_OSOITE_TYHJA), ViestiValidator.validateLahettaja(getLahettaja("Opetushallitus", null)))

    // ei validi osoite ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LAHETTAJAN_OSOITE_INVALID), ViestiValidator.validateLahettaja(getLahettaja("Opetushallitus", "ei validi osoite")))

    // ei opintopolku.fi -domain ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LAHETTAJAN_OSOITE_DOMAIN), ViestiValidator.validateLahettaja(getLahettaja("Opetushallitus", "noreply@example.com")))
  }

  @Test def testValidateReplyTo(): Unit = {
    // määrittelemätön replyTo on sallittu
    Assertions.assertEquals(Set.empty, ViestiValidator.validateReplyTo(Optional.empty()))

    // validi sähköpostiosoite on sallittu
    Assertions.assertEquals(Set.empty, ViestiValidator.validateReplyTo(Optional.of("ville.virkamies@oph.fi")))

    // ei validi sähköpostiosoite ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_REPLYTO_INVALID), ViestiValidator.validateReplyTo(Optional.of("tämä ei ole sähköpostiosoite")))
  }

  @Test def testValidateVastaanottajat(): Unit = {

    def getVastaanottaja(nimi: String, sahkoposti: String): VastaanottajaImpl =
      VastaanottajaImpl(Optional.ofNullable(nimi), Optional.ofNullable(sahkoposti))

    // vastaanottajat-kenttä pitää olla määritelty
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_VASTAANOTTAJAT_TYHJA), ViestiValidator.validateVastaanottajat(Optional.empty()))
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_VASTAANOTTAJAT_TYHJA), ViestiValidator.validateVastaanottajat(Optional.of(Collections.emptyList())))

    // vastaanottajia ei saa olla liikaa
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_VASTAANOTTAJAT_LIIKAA),
      ViestiValidator.validateVastaanottajat(Optional.of(Range(0, ViestiImpl.VIESTI_VASTAANOTTAJAT_MAX_MAARA + 1).map(i => getVastaanottaja(null, "vastaanottaja" + i + "@example.com")).asJava)))

    // vastaanottajat joiden osoite validi ovat sallittuja
    Assertions.assertEquals(Set.empty, ViestiValidator.validateVastaanottajat(Optional.of(util.List.of(
      getVastaanottaja("Vallu Vastaanottaja","vallu.vastaanottaja@example.com"),
      getVastaanottaja(null,"veera.vastaanottaja@example.com"),
    ))))

    // null-arvot vastaanottajalistassa eivät ole sallittuja
    val vastaanottajat = new util.ArrayList[Vastaanottaja]()
    vastaanottajat.add(getVastaanottaja("Vallu Vastaanottaja","vallu.vastaanottaja@example.com"))
    vastaanottajat.add(null)
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_VASTAANOTTAJA_NULL), ViestiValidator.validateVastaanottajat(Optional.of(vastaanottajat)))

    // liian pitkä nimi ei ole sallittu
    Assertions.assertEquals(Set("Vastaanottaja (nimi: " + "x".repeat(ViestiImpl.VIESTI_NIMI_MAX_PITUUS+1)+ ", sähköpostiosoite: Optional[vallu.vastaanottaja@example.com]): " + ViestiValidator.VALIDATION_VASTAANOTTAJAN_NIMI_LIIAN_PITKA),
      ViestiValidator.validateVastaanottajat(Optional.of(util.List.of(getVastaanottaja("x".repeat(ViestiImpl.VIESTI_NIMI_MAX_PITUUS+1), "vallu.vastaanottaja@example.com")))))

    // määrittelemätön sähköpostiosoite ei ole sallittu
    Assertions.assertEquals(Set("Vastaanottaja (nimi: Vallu Vastaanottaja, sähköpostiosoite: Optional.empty): " + ViestiValidator.VALIDATION_VASTAANOTTAJAN_OSOITE_TYHJA),
      ViestiValidator.validateVastaanottajat(Optional.of(util.List.of(getVastaanottaja("Vallu Vastaanottaja", null)))))

    // ei validi sähköpostiosoite ei ole sallittu
    Assertions.assertEquals(Set("Vastaanottaja (nimi: Vallu Vastaanottaja, sähköpostiosoite: Optional[ei validi osoite]): " + ViestiValidator.VALIDATION_VASTAANOTTAJAN_OSOITE_INVALID),
      ViestiValidator.validateVastaanottajat(Optional.of(util.List.of(getVastaanottaja("Vallu Vastaanottaja", "ei validi osoite")))))

    // kaikki virheet kerätään
    Assertions.assertEquals(Set(
      "Vastaanottaja (nimi: Vallu Vastaanottaja, sähköpostiosoite: Optional.empty): " + ViestiValidator.VALIDATION_VASTAANOTTAJAN_OSOITE_TYHJA,
      "Vastaanottaja (nimi: Vallu Vastaanottaja, sähköpostiosoite: Optional[ei validi osoite]): " + ViestiValidator.VALIDATION_VASTAANOTTAJAN_OSOITE_INVALID),
      ViestiValidator.validateVastaanottajat(Optional.of(util.List.of(
        getVastaanottaja("Vallu Vastaanottaja", null),
        getVastaanottaja("Vallu Vastaanottaja", "ei validi osoite")
      ))))

    // duplikaattiosoitteet eivät ole sallittuja
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_VASTAANOTTAJA_OSOITE_DUPLICATE + "vallu.vastaanottaja@example.com"),
      ViestiValidator.validateVastaanottajat(Optional.of(util.List.of(
        getVastaanottaja("Vallu Vastaanottaja", "vallu.vastaanottaja@example.com"),
        getVastaanottaja("Vallu Vastaanottaja", "vallu.vastaanottaja@example.com")
      ))))
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
    Assertions.assertEquals(Set.empty, ViestiValidator.validateLiitteidenTunnisteet(Optional.of(util.List.of(VALIDI_LIITETUNNISTE1)), liiteMetadatat, IDENTITEETTI1))

    // null-arvot liitetunnistelistassa eivät ole sallittuja
    val tunnisteet = new util.ArrayList[String]()
    tunnisteet.add(VALIDI_LIITETUNNISTE1)
    tunnisteet.add(null)
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LIITETUNNISTE_NULL), ViestiValidator.validateLiitteidenTunnisteet(Optional.of(tunnisteet), liiteMetadatat, IDENTITEETTI1))

    // väärän muotoinen liitetunniste ei ole sallittu
    val EI_UUID_MUOTOINEN_TUNNISTE = "ei uuid-muotoinen tunniste"
    Assertions.assertEquals(Set("Liitetunniste \"" + EI_UUID_MUOTOINEN_TUNNISTE + "\": " + ViestiValidator.VALIDATION_LIITETUNNISTE_INVALID),
      ViestiValidator.validateLiitteidenTunnisteet(Optional.of(util.List.of(EI_UUID_MUOTOINEN_TUNNISTE)), liiteMetadatat, IDENTITEETTI1))

    // oikean muotoinen liitetunniste jolla ei liitettä ei sallittu
    val EI_TARJOLLA_OLEVA_LIITETUNNISTE = "4fa85f64-5717-4562-b3fc-2c963f66afa6";
    Assertions.assertEquals(Set("Liitetunniste \"" + EI_TARJOLLA_OLEVA_LIITETUNNISTE + "\": " + ViestiValidator.VALIDATION_LIITETUNNISTE_EI_TARJOLLA),
      ViestiValidator.validateLiitteidenTunnisteet(Optional.of(util.List.of(EI_TARJOLLA_OLEVA_LIITETUNNISTE)), liiteMetadatat, IDENTITEETTI1))

    // toisen identiteetin lataama tunniste ei sallittu (VALIDI_LIITETUNNISTE2, IDENTITEETTI1)
    Assertions.assertEquals(Set("Liitetunniste \"" + VALIDI_LIITETUNNISTE2 + "\": " + ViestiValidator.VALIDATION_LIITETUNNISTE_EI_TARJOLLA),
      ViestiValidator.validateLiitteidenTunnisteet(Optional.of(util.List.of(VALIDI_LIITETUNNISTE2)), liiteMetadatat, IDENTITEETTI1))

    // kaikki virheet kerätään
    Assertions.assertEquals(Set(
      "Liitetunniste \"" + EI_UUID_MUOTOINEN_TUNNISTE + "\": " + ViestiValidator.VALIDATION_LIITETUNNISTE_INVALID,
      "Liitetunniste \"" + EI_TARJOLLA_OLEVA_LIITETUNNISTE + "\": " + ViestiValidator.VALIDATION_LIITETUNNISTE_EI_TARJOLLA
    ), ViestiValidator.validateLiitteidenTunnisteet(Optional.of(util.List.of(EI_UUID_MUOTOINEN_TUNNISTE, EI_TARJOLLA_OLEVA_LIITETUNNISTE)), liiteMetadatat, IDENTITEETTI1))

    // duplikaatit eivät sallittuja
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LIITETUNNISTE_DUPLICATE + VALIDI_LIITETUNNISTE1),
      ViestiValidator.validateLiitteidenTunnisteet(Optional.of(util.List.of(VALIDI_LIITETUNNISTE1, VALIDI_LIITETUNNISTE1)), liiteMetadatat, IDENTITEETTI1))

  @Test def validateLahetysTunniste(): Unit =
    val VALIDI_LAHETYSTUNNISTE1 = Optional.of("3fa85f64-5717-4562-b3fc-2c963f66afa6");
    val IDENTITEETTI1 = "jarjestelma1"
    val VALIDI_LAHETYSTUNNISTE2 = Optional.of("4fa85f64-5717-4562-b3fc-2c963f66afa6");
    val IDENTITEETTI2 = "jarjestelma2"

    // määrittelemätön tunniste on sallittu
    Assertions.assertEquals(Set.empty, ViestiValidator.validateLahetysTunniste(Optional.empty, Option.empty, IDENTITEETTI1))

    // järjestelmän luoma tunniste on sallittu
    Assertions.assertEquals(Set.empty, ViestiValidator.validateLahetysTunniste(VALIDI_LAHETYSTUNNISTE1, Option(LahetysMetadata(IDENTITEETTI1)), IDENTITEETTI1))

    // ei validi tunniste ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LAHETYSTUNNISTE_INVALID), ViestiValidator.validateLahetysTunniste(Optional.of("jotain hämärää"), Option.empty, IDENTITEETTI1))

    // toisen identiteetin luoma tunniste ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LAHETYSTUNNISTE_EI_TARJOLLA), ViestiValidator.validateLahetysTunniste(VALIDI_LAHETYSTUNNISTE1, Option(LahetysMetadata(IDENTITEETTI1)), IDENTITEETTI2))

  @Test def testValidatePrioriteetti(): Unit =
    // laillinen prioriteetti on sallittu
    Assertions.assertEquals(Set.empty, ViestiValidator.validatePrioriteetti(Optional.of(ViestiImpl.VIESTI_PRIORITEETTI_KORKEA)))
    Assertions.assertEquals(Set.empty, ViestiValidator.validatePrioriteetti(Optional.of(ViestiImpl.VIESTI_PRIORITEETTI_NORMAALI)))

    // tyhjä prioriteetti ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_PRIORITEETTI), ViestiValidator.validatePrioriteetti(Optional.empty()))
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_PRIORITEETTI), ViestiValidator.validatePrioriteetti(Optional.of("")))

    // väärä prioriteetti ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_PRIORITEETTI), ViestiValidator.validatePrioriteetti(Optional.of("jotain hämärää")))

  @Test def testValidateSailytysAika(): Unit =
    // laillinen säilytysaika on sallittu
    Assertions.assertEquals(Set.empty, ViestiValidator.validateSailytysAika(Optional.of(5)))
    Assertions.assertEquals(Set.empty, ViestiValidator.validateSailytysAika(Optional.of(365)))

    // olematon säilytysaika ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_SAILYTYSAIKA_TYHJA), ViestiValidator.validateSailytysAika(Optional.empty()))
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_SAILYTYSAIKA), ViestiValidator.validateSailytysAika(Optional.of(-3)))
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_SAILYTYSAIKA), ViestiValidator.validateSailytysAika(Optional.of(0)))

  @Test def testValidateMetadata(): Unit =
    // merkkijonot joissa ei duplikaatteja saman avaimen sisällä ovat sallittuja
    Assertions.assertEquals(Set.empty, ViestiValidator.validateMetadata(
      Optional.of(util.Map.of("avain1", util.List.of("arvo1"), "avain2", util.List.of("arvo1", "arvo2")))))

    // null-arvot eivät ole sallittuja
    val metadata1 = util.HashMap[String, util.List[String]]()
    metadata1.put("avain1", null)
    metadata1.put("avain2", util.stream.Stream.of("arvo1", null).toList())
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_METADATA_NULL + "avain1,avain2"),
      ViestiValidator.validateMetadata(Optional.of(metadata1)))

    // duplikaattiarvot eivät ole sallittuja
    val metadata2 = util.HashMap[String, util.List[String]]()
    metadata2.put("avain1", util.List.of("arvo1"))
    metadata2.put("avain2", util.List.of("arvo2", "arvo2"))
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_METADATA_DUPLICATE + "avain2"),
      ViestiValidator.validateMetadata(Optional.of(metadata2)))

    // liian monta avainta ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_METADATA_ARVOT_MAARA), ViestiValidator.validateMetadata(
      Optional.of(Range(0, ViestiImpl.VIESTI_METADATA_AVAIMET_MAX_MAARA + 1).map(i => "avain" + i -> util.List.of("arvo")).toMap.asJava)))

    // liian pitkä avain ei ole sallittu
    Assertions.assertEquals(Set("Metadata \"" + "x".repeat(ViestiImpl.VIESTI_METADATA_AVAIN_MAX_PITUUS+1) + "\": " +
      ViestiValidator.VALIDATION_METADATA_AVAIN_PITUUS), ViestiValidator.validateMetadata(
      Optional.of(util.Map.of("x".repeat(ViestiImpl.VIESTI_METADATA_AVAIN_MAX_PITUUS+1), util.List.of("arvo1")))))

    // liian monta arvoa ei ole sallittu
    Assertions.assertEquals(Set("Metadata \"avain\": " + ViestiValidator.VALIDATION_METADATA_ARVOT_MAARA), ViestiValidator.validateMetadata(
      Optional.of(util.Map.of("avain", Range(0, ViestiImpl.VIESTI_METADATA_ARVOT_MAX_MAARA + 1).map(i => "arvo" + i).asJava))))

    // liian pitkät arvot ei sallittu
    Assertions.assertEquals(Set("Metadata \"avain\": " + ViestiValidator.VALIDATION_METADATA_ARVO_PITUUS + "x".repeat(ViestiImpl.VIESTI_METADATA_ARVO_MAX_PITUUS + 1)), ViestiValidator.validateMetadata(
      Optional.of(util.Map.of("avain", util.List.of("x".repeat(ViestiImpl.VIESTI_METADATA_ARVO_MAX_PITUUS + 1))))))

  @Test def testValidateLahetysJaPeritytKentat(): Unit = {
    // ok että lähetys määritelty ja lähetyksen kenttiä ei
    Assertions.assertEquals(Set.empty, ViestiValidator.validateLahetysJaPeritytKentat(Optional.of(UUID.randomUUID().toString), Optional.empty, Optional.empty))

    // ok että lähettävä palvelu ja käyttöoikeusrajoitukset määritelty mutta lähetystä ei
    Assertions.assertEquals(Set.empty, ViestiValidator.validateLahetysJaPeritytKentat(Optional.empty, Optional.of("palvelu"), Optional.of(new util.ArrayList())))

    // ok että lähetys ja käyttöoikeusrajoitukset ei kumpikaan määritelty mutta lähettävä palvelu määritelty
    Assertions.assertEquals(Set.empty, ViestiValidator.validateLahetysJaPeritytKentat(Optional.empty, Optional.of("palvelu"), Optional.empty))

    // lähetys ja käyttöoikeusrajoitukset molemmat määritelty on virhe
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_KAYTTOOIKEUSRAJOITUS_EI_TYHJA),
      ViestiValidator.validateLahetysJaPeritytKentat(Optional.of(UUID.randomUUID().toString), Optional.empty, Optional.of(new util.ArrayList[String]())))

    // lähetys ja lähettävä palvelu molemmat määritelty on virhe
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LAHETTAVAPALVELU_EI_TYHJA),
      ViestiValidator.validateLahetysJaPeritytKentat(Optional.of(UUID.randomUUID().toString), Optional.of("palvelu"), Optional.empty))

    // lähetys ei määritelty ja lähettäväpalvelu ei määritelty on virhe
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LAHETTAVA_PALVELU_TYHJA),
      ViestiValidator.validateLahetysJaPeritytKentat(Optional.empty, Optional.empty, Optional.empty))
  }

  @Test def testValidateKorkeaPrioriteetti(): Unit = {
    // lailliset prioriteetti-vastaanottajamääräkombot ovat sallittuja
    Assertions.assertEquals(Set.empty, ViestiValidator.validateKorkeaPrioriteetti(Optional.of(ViestiImpl.VIESTI_PRIORITEETTI_KORKEA), Optional.of(util.List.of(
      VastaanottajaImpl(Optional.empty(), Optional.of("vallu.vastaanottaja@example.com"))
    ))))
    Assertions.assertEquals(Set.empty, ViestiValidator.validateKorkeaPrioriteetti(Optional.of(ViestiImpl.VIESTI_PRIORITEETTI_NORMAALI), Optional.of(util.List.of(
      VastaanottajaImpl(Optional.empty(), Optional.of("vallu.vastaanottaja@example.com")),
      VastaanottajaImpl(Optional.empty(), Optional.of("veera.vastaanottaja@example.com"))
    ))))

    // korkealla prioriteetilla voi olla vain yksi vastaanottaja
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_KORKEA_PRIORITEETTI_VASTAANOTTAJAT), ViestiValidator.validateKorkeaPrioriteetti(Optional.of(ViestiImpl.VIESTI_PRIORITEETTI_KORKEA), Optional.of(util.List.of(
      VastaanottajaImpl(Optional.empty(), Optional.of("vallu.vastaanottaja@example.com")),
      VastaanottajaImpl(Optional.empty(), Optional.of("veera.vastaanottaja@example.com"))
    ))))
  }

  @Test def testValidateViestinKoko(): Unit = {
    val identiteetti1 = "identiteetti1"
    val identiteetti2 = "identiteetti2"
    val liiteTunniste1 = UUID.randomUUID();
    val liiteTunniste2 = UUID.randomUUID();
    val liiteMetadata1 = Map(liiteTunniste1 -> LiiteMetadata(identiteetti1, 1024*1024))
    val liiteMetadata2 = Map(liiteTunniste2 -> LiiteMetadata(identiteetti2, ViestiImpl.VIESTI_MAX_SIZE + 1))

    // alle maksimikoon olevat viestit ovat sallittuja
    Assertions.assertEquals(Set.empty, ViestiValidator.validateKoko("Sisältö", util.List.of(liiteTunniste1.toString), liiteMetadata1, identiteetti1))

    // liian iso liite auheuttaa virheen
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_KOKO), ViestiValidator.validateKoko("Sisältö", util.List.of(liiteTunniste2.toString), liiteMetadata2, identiteetti2))

    // liian iso sisältö auheuttaa virheen
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_KOKO), ViestiValidator.validateKoko("x".repeat(ViestiImpl.VIESTI_MAX_SIZE + 1), util.List.of(), Map.empty, identiteetti1))

    // liitteet joilta puuttuu metadata ignotaan (tämä tarkoittaa ettei liitettä ole olemassa, aiheuttaa toisenlaisen virheen)
    Assertions.assertEquals(Set.empty, ViestiValidator.validateKoko("Sisältö", util.List.of(liiteTunniste1.toString), Map.empty, identiteetti1))

    // muiden omistamat liitteet ignotaan (ei anneta tietoa muiden omistamien liitteiden koosta)
    Assertions.assertEquals(Set.empty, ViestiValidator.validateKoko("Sisältö", util.List.of(liiteTunniste2.toString), liiteMetadata2, identiteetti1))
  }
}
