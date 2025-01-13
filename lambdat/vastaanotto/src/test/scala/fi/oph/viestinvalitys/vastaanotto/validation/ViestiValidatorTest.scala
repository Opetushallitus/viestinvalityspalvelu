package fi.oph.viestinvalitys.vastaanotto.validation

import fi.oph.viestinvalitys.vastaanotto.model.Viesti.{Kayttooikeus, Maski, Vastaanottaja}
import fi.oph.viestinvalitys.vastaanotto.model.*
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
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_OTSIKKO_LIIAN_PITKA), ViestiValidator.validateOtsikko(Optional.of("x".repeat(Viesti.OTSIKKO_MAX_PITUUS + 1))))
  }

  @Test def testValidateSisalto(): Unit = {
    // laillinen sisältö on sallittu
    Assertions.assertEquals(Set.empty, ViestiValidator.validateSisalto(Optional.of("Tosi hyvä sisältö")))

    // tyhjä sisältö ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_SISALTO_TYHJA), ViestiValidator.validateSisalto(Optional.empty()))
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_SISALTO_TYHJA), ViestiValidator.validateSisalto(Optional.of("")))

    // liian pitkä sisältö ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_SISALTO_LIIAN_PITKA), ViestiValidator.validateSisalto(Optional.of("x".repeat(Viesti.SISALTO_MAX_PITUUS + 1))))
  }

  @Test def testValidateSisallonTyyppi(): Unit = {
    // laillinen sisällönTyyppi on sallittu
    Assertions.assertEquals(Set.empty, ViestiValidator.validateSisallonTyyppi(Optional.of(Viesti.VIESTI_SISALTOTYYPPI_TEXT)))
    Assertions.assertEquals(Set.empty, ViestiValidator.validateSisallonTyyppi(Optional.of(Viesti.VIESTI_SISALTOTYYPPI_HTML)))

    // tyhjä sisällönTyyppi ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_SISALLONTYYPPI), ViestiValidator.validateSisallonTyyppi(Optional.empty()))
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_SISALLONTYYPPI), ViestiValidator.validateSisallonTyyppi(Optional.of("")))

    // väärä sisällönTyyppi ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_SISALLONTYYPPI), ViestiValidator.validateSisallonTyyppi(Optional.of("jotain hämärää")))
  }

  @Test def testValidateKielet(): Unit = {
    // määrittelemätön kieli on sallittu
    Assertions.assertEquals(Set.empty, ViestiValidator.validateKielet(Optional.empty()))
    Assertions.assertEquals(Set.empty, ViestiValidator.validateKielet(Optional.of(util.List.of())))

    // lailliset kielet ovat sallittuja
    Assertions.assertEquals(Set.empty, ViestiValidator.validateKielet(Optional.of(util.List.of("fi", "sv"))))
    Assertions.assertEquals(Set.empty, ViestiValidator.validateKielet(Optional.of(util.List.of("sv", "fi"))))
    Assertions.assertEquals(Set.empty, ViestiValidator.validateKielet(Optional.of(util.List.of("en"))))

    // ei validit kielit eivät sallittuja
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_KIELI_EI_SALLITTU + "de"), ViestiValidator.validateKielet(Optional.of(util.List.of("de"))))

    // null arvot eivät ole sallittuja
    val kielet = util.ArrayList[String]()
    kielet.add("en")
    kielet.add(null)
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_KIELI_NULL), ViestiValidator.validateKielet(Optional.of(kielet)))

    // duplikaatit eivät ole sallittuja
    val kielet2 = util.ArrayList[String]()
    kielet2.add("en")
    kielet2.add("en")
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_KIELI_DUPLICATES + "en"), ViestiValidator.validateKielet(Optional.of(kielet2)))

    // kaikki virheet kerätään
    val kielet3 = util.ArrayList[String]()
    kielet3.add("de")
    kielet3.add("en")
    kielet3.add("en")
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_KIELI_EI_SALLITTU + "de", ViestiValidator.VALIDATION_KIELI_DUPLICATES + "en"),
      ViestiValidator.validateKielet(Optional.of(kielet3)))
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
    Assertions.assertEquals(Set("Maski (salaisuus: " + "*".repeat(Viesti.VIESTI_SALAISUUS_MIN_PITUUS - 1) + ", maski: peitetty): " + ViestiValidator.VALIDATION_MASKIT_SALAISUUS_PITUUS),
      ViestiValidator.validateMaskit(Optional.of(util.List.of(getMaski("*".repeat(Viesti.VIESTI_SALAISUUS_MIN_PITUUS - 1), "peitetty")))))
    Assertions.assertEquals(Set("Maski (salaisuus: " + "*".repeat(Viesti.VIESTI_SALAISUUS_MAX_PITUUS + 1) + ", maski: peitetty): " + ViestiValidator.VALIDATION_MASKIT_SALAISUUS_PITUUS),
      ViestiValidator.validateMaskit(Optional.of(util.List.of(getMaski("*".repeat(Viesti.VIESTI_SALAISUUS_MAX_PITUUS + 1), "peitetty")))))

    // maskin pituus on rajoitettu
    Assertions.assertEquals(Set("Maski (salaisuus: *********, maski: " + "*".repeat(Viesti.VIESTI_MASKI_MIN_PITUUS-1) + "): " + ViestiValidator.VALIDATION_MASKIT_MASKI_PITUUS),
      ViestiValidator.validateMaskit(Optional.of(util.List.of(getMaski("salaisuus", "*".repeat(Viesti.VIESTI_MASKI_MIN_PITUUS-1))))))
    Assertions.assertEquals(Set("Maski (salaisuus: *********, maski: " + "*".repeat(Viesti.VIESTI_MASKI_MAX_PITUUS + 1) + "): " + ViestiValidator.VALIDATION_MASKIT_MASKI_PITUUS),
      ViestiValidator.validateMaskit(Optional.of(util.List.of(getMaski("salaisuus", "*".repeat(Viesti.VIESTI_MASKI_MAX_PITUUS + 1))))))

    // maskien määrä on rajoitettu
    val maskit2: java.util.List[Maski] = Range(0, Viesti.VIESTI_MASKIT_MAX_MAARA + 1).map(i => getMaski(s"salaisuus${i}", s"peitetty${i}")).asJava
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_MASKIT_LIIKAA), ViestiValidator.validateMaskit(Optional.of(maskit2)))

    // kaikki virheet kerätään
    val maskit3 = new util.ArrayList[Maski]()
    maskit3.add(getMaski(null, "<salaisuus peitetty>"))
    maskit3.add(null)
    Assertions.assertEquals(Set(
      "Maski (salaisuus: , maski: <salaisuus peitetty>): " + ViestiValidator.VALIDATION_MASKIT_EI_SALAISUUTTA,
      ViestiValidator.VALIDATION_MASKIT_NULL),
      ViestiValidator.validateMaskit(Optional.of(maskit3)))

    // duplikaattiosoitteet eivät ole sallittuja
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_MASKIT_DUPLICATES + "*********"),
      ViestiValidator.validateMaskit(Optional.of(util.List.of(
        getMaski("salaisuus", "<salaisuus peitetty>"),
        getMaski("salaisuus", "<salaisuus peitetty toisin>")
      ))))
  }

  @Test def testValidateVastaanottajat(): Unit = {

    def getVastaanottaja(nimi: String, sahkoposti: String): VastaanottajaImpl =
      VastaanottajaImpl(Optional.ofNullable(nimi), Optional.ofNullable(sahkoposti))

    // vastaanottajat-kenttä pitää olla määritelty
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_VASTAANOTTAJAT_TYHJA), ViestiValidator.validateVastaanottajat(Optional.empty()))
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_VASTAANOTTAJAT_TYHJA), ViestiValidator.validateVastaanottajat(Optional.of(Collections.emptyList())))

    // vastaanottajia ei saa olla liikaa
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_VASTAANOTTAJAT_LIIKAA),
      ViestiValidator.validateVastaanottajat(Optional.of(Range(0, Viesti.VIESTI_VASTAANOTTAJAT_MAX_MAARA + 1).map(i => getVastaanottaja(null, "vastaanottaja" + i + "@example.com")).asJava)))

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
    Assertions.assertEquals(Set("Vastaanottaja (nimi: " + "x".repeat(Viesti.VIESTI_NIMI_MAX_PITUUS+1)+ ", sähköpostiosoite: Optional[vallu.vastaanottaja@example.com]): " + ViestiValidator.VALIDATION_VASTAANOTTAJAN_NIMI_LIIAN_PITKA),
      ViestiValidator.validateVastaanottajat(Optional.of(util.List.of(getVastaanottaja("x".repeat(Viesti.VIESTI_NIMI_MAX_PITUUS+1), "vallu.vastaanottaja@example.com")))))

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

    // liitetunnisteita ei voi olla määrättömästi
    val tunnisteet2 = Range(0, Viesti.VIESTI_LIITTEET_MAX_MAARA + 1).map(i => UUID.randomUUID().toString).asJava
    Assertions.assertTrue(ViestiValidator.validateLiitteidenTunnisteet(Optional.of(tunnisteet2), Map.empty, IDENTITEETTI1).contains(ViestiValidator.VALIDATION_LIITETUNNISTE_LIIKAA))

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
    Assertions.assertEquals(Set.empty, ViestiValidator.validateLahetysTunniste(VALIDI_LAHETYSTUNNISTE1, Option(LahetysMetadata(IDENTITEETTI1, false)), IDENTITEETTI1))

    // ei validi tunniste ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LAHETYSTUNNISTE_INVALID), ViestiValidator.validateLahetysTunniste(Optional.of("jotain hämärää"), Option.empty, IDENTITEETTI1))

    // toisen identiteetin luoma tunniste ei ole sallittu
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LAHETYSTUNNISTE_EI_TARJOLLA), ViestiValidator.validateLahetysTunniste(VALIDI_LAHETYSTUNNISTE1, Option(LahetysMetadata(IDENTITEETTI1, false)), IDENTITEETTI2))

  @Test def testValidateMetadata(): Unit =
    // merkkijonot joissa sallittuja merkkejä ja ei duplikaatteja saman avaimen sisällä ovat sallittuja
    Assertions.assertEquals(Set.empty, ViestiValidator.validateMetadata(
      Optional.of(util.Map.of("avain1-_.", util.List.of("arvo1-_."), "avain2", util.List.of("arvo1", "arvo2")))))

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
      Optional.of(Range(0, Viesti.VIESTI_METADATA_AVAIMET_MAX_MAARA + 1).map(i => "avain" + i -> util.List.of("arvo")).toMap.asJava)))

    // erikoismerkkejä sisältävä avain ei ole sallittu
    Assertions.assertEquals(Set("Metadata \"avain!!!!\": " +
      ViestiValidator.VALIDATION_METADATA_AVAIN_INVALID), ViestiValidator.validateMetadata(
      Optional.of(util.Map.of("avain!!!!", util.List.of("arvo1")))))

    // liian pitkä avain ei ole sallittu
    Assertions.assertEquals(Set("Metadata \"" + "x".repeat(Viesti.VIESTI_METADATA_AVAIN_MAX_PITUUS+1) + "\": " +
      ViestiValidator.VALIDATION_METADATA_AVAIN_PITUUS), ViestiValidator.validateMetadata(
      Optional.of(util.Map.of("x".repeat(Viesti.VIESTI_METADATA_AVAIN_MAX_PITUUS+1), util.List.of("arvo1")))))

    // liian monta arvoa ei ole sallittu
    Assertions.assertEquals(Set("Metadata \"avain\": " + ViestiValidator.VALIDATION_METADATA_ARVOT_MAARA), ViestiValidator.validateMetadata(
      Optional.of(util.Map.of("avain", Range(0, Viesti.VIESTI_METADATA_ARVOT_MAX_MAARA + 1).map(i => "arvo" + i).asJava))))

    // liian pitkät arvot ei sallittu
    Assertions.assertEquals(Set("Metadata \"avain\": " + ViestiValidator.VALIDATION_METADATA_ARVO_PITUUS + "x".repeat(Viesti.VIESTI_METADATA_ARVO_MAX_PITUUS + 1)), ViestiValidator.validateMetadata(
      Optional.of(util.Map.of("avain", util.List.of("x".repeat(Viesti.VIESTI_METADATA_ARVO_MAX_PITUUS + 1))))))

    // erikoismerkkejä sisältävä arvo ei sallittu
    Assertions.assertEquals(Set("Metadata \"avain\": " + ViestiValidator.VALIDATION_METADATA_ARVO_INVALID + "arvo!!!!"), ViestiValidator.validateMetadata(
      Optional.of(util.Map.of("avain", util.List.of("arvo!!!!")))))


  @Test def testValidateKayttooikeusRajoitukset(): Unit =
    val RAJOITUS = KayttooikeusImpl(Optional.of("RAJOITUS1"), Optional.of("1.2.246.562.10.00000000000000006666"))
    val RAJOITUS_ORGANISAATIO_TYHJA = KayttooikeusImpl(Optional.of("RAJOITUS1"), Optional.empty)
    val RAJOITUS_ORGANISAATIO_INVALID = KayttooikeusImpl(Optional.of("RAJOITUS1"), Optional.of("ei hyvä"))
    val RAJOITUS_PITKA_ORGANISAATIO = KayttooikeusImpl(Optional.of("RAJOITUS1"), Optional.of(Range(0, Viesti.VIESTI_ORGANISAATIO_MAX_PITUUS+1).mkString(".")))
    val RAJOITUS_OIKEUS_TYHJA = KayttooikeusImpl(Optional.empty, Optional.of("1.2.246.562.10.00000000000000006666"))
    val RAJOITUS_OIKEUS_PITKA = KayttooikeusImpl(Optional.of(Range(0, Viesti.VIESTI_OIKEUS_MAX_PITUUS + 1).map(i => "X").mkString("")), Optional.of("1.2.246.562.10.00000000000000006666"))

    // kenttä ei ole pakollinen (jos ei määritelty niin vain rekisterinpitäjä voi katsoa viestejä)
    Assertions.assertEquals(Set.empty, ViestiValidator.validateKayttooikeusRajoitukset(Optional.empty))

    // merkkijonot ovat sallittuja
    Assertions.assertEquals(Set.empty, ViestiValidator.validateKayttooikeusRajoitukset(Optional.of(util.List.of(RAJOITUS))))

    // rajoituksia ei voi olla määrättömästi
    val rajoitukset = Range(0, Viesti.VIESTI_KAYTTOOIKEUS_MAX_MAARA + 1)
      .map(i => KayttooikeusImpl(RAJOITUS.oikeus, Optional.of(RAJOITUS.organisaatio.get + "" + i)).asInstanceOf[Kayttooikeus]).asJava
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_KAYTTOOIKEUSRAJOITUS_LIIKAA), ViestiValidator.validateKayttooikeusRajoitukset(Optional.of(rajoitukset)))

    // null-arvot käyttöoikeustunnistelistassa eivät ole sallittuja
    val rajoitukset2 = new util.ArrayList[Kayttooikeus]()
    rajoitukset2.add(RAJOITUS)
    rajoitukset2.add(null)
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_KAYTTOOIKEUSRAJOITUS_NULL), ViestiValidator.validateKayttooikeusRajoitukset(Optional.of(rajoitukset2)))

    // duplikaatit eivät sallittuja
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_KAYTTOOIKEUSRAJOITUS_DUPLICATE + RAJOITUS),
      ViestiValidator.validateKayttooikeusRajoitukset(Optional.of(util.List.of(RAJOITUS, RAJOITUS))))

    // oikeuksien pitää olla organisaatiorajoitettuja validiin organisaatioon
    Assertions.assertEquals(Set("Käyttöoikeusrajoitus (\"\",RAJOITUS1): " + ViestiValidator.VALIDATION_ORGANISAATIO_INVALID),
      ViestiValidator.validateKayttooikeusRajoitukset(Optional.of(util.List.of(RAJOITUS_ORGANISAATIO_TYHJA))))
    Assertions.assertEquals(Set("Käyttöoikeusrajoitus (" + RAJOITUS_ORGANISAATIO_INVALID.organisaatio.get + ",RAJOITUS1): " + ViestiValidator.VALIDATION_ORGANISAATIO_INVALID),
      ViestiValidator.validateKayttooikeusRajoitukset(Optional.of(util.List.of(RAJOITUS_ORGANISAATIO_INVALID))))

    // organisaatio ei saa olla liian pitkä
    Assertions.assertEquals(Set("Käyttöoikeusrajoitus (" + RAJOITUS_PITKA_ORGANISAATIO.organisaatio.get + ",RAJOITUS1): "
      + ViestiValidator.VALIDATION_ORGANISAATIO_INVALID + "," + ViestiValidator.VALIDATION_ORGANISAATIO_PITUUS), ViestiValidator.validateKayttooikeusRajoitukset(Optional.of(util.List.of(RAJOITUS_PITKA_ORGANISAATIO))))

    // oikeus pitää olla määritelty
    Assertions.assertEquals(Set("Käyttöoikeusrajoitus (" + RAJOITUS_OIKEUS_TYHJA.organisaatio.get + ",\"\"): " + ViestiValidator.VALIDATION_OIKEUS_TYHJA),
      ViestiValidator.validateKayttooikeusRajoitukset(Optional.of(util.List.of(RAJOITUS_OIKEUS_TYHJA))))

    // oikeus ei saa olla liian pitkä
    Assertions.assertEquals(Set("Käyttöoikeusrajoitus (" + RAJOITUS_OIKEUS_PITKA.organisaatio.get + "," + RAJOITUS_OIKEUS_PITKA.oikeus.get + "): " + ViestiValidator.VALIDATION_OIKEUS_PITUUS),
      ViestiValidator.validateKayttooikeusRajoitukset(Optional.of(util.List.of(RAJOITUS_OIKEUS_PITKA))))

    // kaikki virheet kerätään
    val rajoitukset3 = new util.ArrayList[Kayttooikeus]()
    rajoitukset3.add(RAJOITUS)
    rajoitukset3.add(null)
    rajoitukset3.add(RAJOITUS)
    Assertions.assertEquals(Set(
      ViestiValidator.VALIDATION_KAYTTOOIKEUSRAJOITUS_NULL,
      ViestiValidator.VALIDATION_KAYTTOOIKEUSRAJOITUS_DUPLICATE + RAJOITUS
    ), ViestiValidator.validateKayttooikeusRajoitukset(Optional.of(rajoitukset3)))

  @Test def testValidateIdempotencyKey(): Unit = {
    // ok että idempotency-avainta ei ole
    Assertions.assertEquals(Set.empty, ViestiValidator.validateIdempotencyKey(Optional.empty))

    // ok että sisältää sallittuja merkkejä ja ei liian pitkä
    Assertions.assertEquals(Set.empty, ViestiValidator.validateIdempotencyKey(Optional.of("ABCabc123-_.")))

    // avain ei saa olla liian pitkä
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_IDEMPOTENCY_KEY_LIIAN_PITKA), ViestiValidator.validateIdempotencyKey(Optional.of("a".repeat(Viesti.VIESTI_IDEMPOTENCY_KEY_MAX_PITUUS+1))))

    // avain ei saa sisältää ei-sallittuja merkkejä
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_IDEMPOTENCY_KEY_INVALID), ViestiValidator.validateIdempotencyKey(Optional.of("%")))

    // kaikki virheet kerätään
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_IDEMPOTENCY_KEY_LIIAN_PITKA, ViestiValidator.VALIDATION_IDEMPOTENCY_KEY_INVALID), ViestiValidator.validateIdempotencyKey(Optional.of("%".repeat(Viesti.VIESTI_IDEMPOTENCY_KEY_MAX_PITUUS+1))))
  }

  @Test def testValidateLahetysJaPeritytKentat(): Unit = {
    // ok että lähetys määritelty ja lähetyksen kenttiä ei
    Assertions.assertEquals(Set.empty, ViestiValidator.validateLahetysJaPeritytKentat(Optional.of(UUID.randomUUID().toString), Optional.empty, Optional.empty, Optional.empty, Optional.empty, Optional.empty, Optional.empty))

    // jos lähetys määritelty lähettävä palvelu ei voi olla määritelty
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LAHETTAVAPALVELU_EI_TYHJA),
      ViestiValidator.validateLahetysJaPeritytKentat(Optional.of(UUID.randomUUID().toString), Optional.of("palvelu"), Optional.empty, Optional.empty, Optional.empty, Optional.empty, Optional.empty))

    // jos lähetys määritelty virkailijan oid ei voi olla määritelty
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_VIRKAILIJANOID_EI_TYHJA),
      ViestiValidator.validateLahetysJaPeritytKentat(Optional.of(UUID.randomUUID().toString), Optional.empty, Optional.of(LahetysValidator.VALIDATION_OPH_OID_PREFIX + ".111"), Optional.empty, Optional.empty, Optional.empty, Optional.empty))

    // jos lähetys määritelty lähettäjä ei voi olla määritelty
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LAHETTAJA_EI_TYHJA),
      ViestiValidator.validateLahetysJaPeritytKentat(Optional.of(UUID.randomUUID().toString), Optional.empty, Optional.empty, Optional.of(LahettajaImpl(Optional.empty, Optional.of("noreply@opintopolku.fi"))), Optional.empty, Optional.empty, Optional.empty))

    // jos lähetys määritelty replyto ei voi olla määritelty
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_REPLYTO_EI_TYHJA),
      ViestiValidator.validateLahetysJaPeritytKentat(Optional.of(UUID.randomUUID().toString), Optional.empty, Optional.empty, Optional.empty, Optional.of("vastatkaaminulle@oph.fi"), Optional.empty, Optional.empty))

    // jos lähetys määritelty prioriteetti ei voi olla määritelty
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_PRIORITEETTI_EI_TYHJA),
      ViestiValidator.validateLahetysJaPeritytKentat(Optional.of(UUID.randomUUID().toString), Optional.empty, Optional.empty, Optional.empty, Optional.empty, Optional.of(LahetysImpl.LAHETYS_PRIORITEETTI_NORMAALI), Optional.empty))

    // jos lähetys määritelty prioriteetti ei voi olla määritelty
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_SAILYTYSAIKA_EI_TYHJA),
      ViestiValidator.validateLahetysJaPeritytKentat(Optional.of(UUID.randomUUID().toString), Optional.empty, Optional.empty, Optional.empty, Optional.empty, Optional.empty, Optional.of(1)))

    // jos lähetys ei määritelty kentät validoidaan kuten ne olisivat lähetyksessä, tässä tapauksessa muut ok paitsi lähettävän virkailijan oid
    Assertions.assertEquals(Set(LahetysValidator.VALIDATION_LAHETTAJAN_OID_INVALID),
      ViestiValidator.validateLahetysJaPeritytKentat(Optional.empty, Optional.of("okpalvelu"), Optional.of("ei validi oid"), Optional.of(LahettajaImpl(Optional.empty, Optional.of("ok-osoite@opintopolku.fi"))), Optional.empty, Optional.of(LahetysImpl.LAHETYS_PRIORITEETTI_NORMAALI), Optional.of(1)))

    // myös kentän sisältö validoidaan vaikka kenttä ei saa olla määritelty
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_VIRKAILIJANOID_EI_TYHJA, LahetysValidator.VALIDATION_LAHETTAJAN_OID_INVALID),
      ViestiValidator.validateLahetysJaPeritytKentat(Optional.of(UUID.randomUUID().toString), Optional.empty, Optional.of("ei validdi oid"), Optional.empty, Optional.empty, Optional.empty, Optional.empty))

    // kaikki virheet kerätään
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_LAHETTAVAPALVELU_EI_TYHJA, ViestiValidator.VALIDATION_VIRKAILIJANOID_EI_TYHJA),
      ViestiValidator.validateLahetysJaPeritytKentat(Optional.of(UUID.randomUUID().toString), Optional.of("palvelu"), Optional.of(LahetysValidator.VALIDATION_OPH_OID_PREFIX + ".111"), Optional.empty, Optional.empty, Optional.empty, Optional.empty))
  }

  @Test def testValidateKorkeaPrioriteetti(): Unit = {
    // korkea prioriteetti ja yksi vastaanottaja ovat sallittuja
    Assertions.assertEquals(Set.empty, ViestiValidator.validateKorkeaPrioriteetti(Optional.of(LahetysImpl.LAHETYS_PRIORITEETTI_KORKEA), Optional.of(util.List.of(
      VastaanottajaImpl(Optional.empty(), Optional.of("vallu.vastaanottaja@example.com"))
    )), Option.empty))
    Assertions.assertEquals(Set.empty, ViestiValidator.validateKorkeaPrioriteetti(Optional.of(LahetysImpl.LAHETYS_PRIORITEETTI_NORMAALI), Optional.of(util.List.of(
      VastaanottajaImpl(Optional.empty(), Optional.of("vallu.vastaanottaja@example.com"))
    )), Option.apply(LahetysMetadata("", true)))) // lähetyksella korkea prioriteetti

    // normaali prioriteetti ja useampi vastaanottaja ovat sallittuja
    Assertions.assertEquals(Set.empty, ViestiValidator.validateKorkeaPrioriteetti(Optional.of(LahetysImpl.LAHETYS_PRIORITEETTI_NORMAALI), Optional.of(util.List.of(
      VastaanottajaImpl(Optional.empty(), Optional.of("vallu.vastaanottaja@example.com")),
      VastaanottajaImpl(Optional.empty(), Optional.of("veera.vastaanottaja@example.com"))
    )), Option.empty))

    // korkealla prioriteetilla voi olla vain yksi vastaanottaja
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_KORKEA_PRIORITEETTI_VASTAANOTTAJAT), ViestiValidator.validateKorkeaPrioriteetti(Optional.of(LahetysImpl.LAHETYS_PRIORITEETTI_KORKEA), Optional.of(util.List.of(
      VastaanottajaImpl(Optional.empty(), Optional.of("vallu.vastaanottaja@example.com")),
      VastaanottajaImpl(Optional.empty(), Optional.of("veera.vastaanottaja@example.com"))
    )), Option.empty))
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_KORKEA_PRIORITEETTI_VASTAANOTTAJAT), ViestiValidator.validateKorkeaPrioriteetti(Optional.of(LahetysImpl.LAHETYS_PRIORITEETTI_NORMAALI), Optional.of(util.List.of(
      VastaanottajaImpl(Optional.empty(), Optional.of("vallu.vastaanottaja@example.com")),
      VastaanottajaImpl(Optional.empty(), Optional.of("veera.vastaanottaja@example.com"))
    )), Option.apply(LahetysMetadata("", true)))) // lähetyksella korkea prioriteetti
  }

  @Test def testValidateViestinKoko(): Unit = {
    val identiteetti1 = "identiteetti1"
    val identiteetti2 = "identiteetti2"
    val liiteTunniste1 = UUID.randomUUID();
    val liiteTunniste2 = UUID.randomUUID();
    val liiteMetadata1 = Map(liiteTunniste1 -> LiiteMetadata(identiteetti1, 1024*1024))
    val liiteMetadata2 = Map(liiteTunniste2 -> LiiteMetadata(identiteetti2, Viesti.VIESTI_MAX_SIZE + 1))

    // alle maksimikoon olevat viestit ovat sallittuja
    Assertions.assertEquals(Set.empty, ViestiValidator.validateKoko("Sisältö", util.List.of(liiteTunniste1.toString), liiteMetadata1, identiteetti1))

    // liian iso liite auheuttaa virheen
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_KOKO), ViestiValidator.validateKoko("Sisältö", util.List.of(liiteTunniste2.toString), liiteMetadata2, identiteetti2))

    // liian iso sisältö auheuttaa virheen
    Assertions.assertEquals(Set(ViestiValidator.VALIDATION_KOKO), ViestiValidator.validateKoko("x".repeat(Viesti.VIESTI_MAX_SIZE + 1), util.List.of(), Map.empty, identiteetti1))

    // liitteet joilta puuttuu metadata ignotaan (tämä tarkoittaa ettei liitettä ole olemassa, aiheuttaa toisenlaisen virheen)
    Assertions.assertEquals(Set.empty, ViestiValidator.validateKoko("Sisältö", util.List.of(liiteTunniste1.toString), Map.empty, identiteetti1))

    // muiden omistamat liitteet ignotaan (ei anneta tietoa muiden omistamien liitteiden koosta)
    Assertions.assertEquals(Set.empty, ViestiValidator.validateKoko("Sisältö", util.List.of(liiteTunniste2.toString), liiteMetadata2, identiteetti1))
  }
}
