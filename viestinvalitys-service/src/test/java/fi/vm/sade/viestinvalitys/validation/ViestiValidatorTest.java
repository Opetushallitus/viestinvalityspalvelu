package fi.vm.sade.viestinvalitys.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fi.vm.sade.viestinvalitys.dto.Kayttooikeus;
import fi.vm.sade.viestinvalitys.dto.Kontakti;
import fi.vm.sade.viestinvalitys.dto.Maski;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ViestiValidatorTest {

    private static Maski maski(String salaisuus, String maski) {
        return new Maski(salaisuus, maski);
    }

    private static Kontakti vastaanottaja(String nimi, String sahkoposti) {
        return new Kontakti(nimi, sahkoposti);
    }

    @Test
    void testValidateOtsikko() {
        assertEquals(Set.of(), ViestiValidator.validateOtsikko("Tosi hyvä otsikko"));

        assertEquals(Set.of(ViestiValidator.VALIDATION_OTSIKKO_TYHJA), ViestiValidator.validateOtsikko(null));
        assertEquals(Set.of(ViestiValidator.VALIDATION_OTSIKKO_TYHJA), ViestiValidator.validateOtsikko(""));

        assertEquals(Set.of(ViestiValidator.VALIDATION_OTSIKKO_LIIAN_PITKA),
                ViestiValidator.validateOtsikko("x".repeat(ViestiValidator.OTSIKKO_MAX_PITUUS + 1)));
    }

    @Test
    void testValidateSisalto() {
        assertEquals(Set.of(), ViestiValidator.validateSisalto("Tosi hyvä sisältö"));

        assertEquals(Set.of(ViestiValidator.VALIDATION_SISALTO_TYHJA), ViestiValidator.validateSisalto(null));
        assertEquals(Set.of(ViestiValidator.VALIDATION_SISALTO_TYHJA), ViestiValidator.validateSisalto(""));

        assertEquals(Set.of(ViestiValidator.VALIDATION_SISALTO_LIIAN_PITKA),
                ViestiValidator.validateSisalto("x".repeat(ViestiValidator.SISALTO_MAX_PITUUS + 1)));
    }

    @Test
    void testValidateSisallonTyyppi() {
        assertEquals(Set.of(), ViestiValidator.validateSisallonTyyppi(ViestiValidator.VIESTI_SISALTOTYYPPI_TEXT));
        assertEquals(Set.of(), ViestiValidator.validateSisallonTyyppi(ViestiValidator.VIESTI_SISALTOTYYPPI_HTML));

        assertEquals(Set.of(ViestiValidator.VALIDATION_SISALLONTYYPPI), ViestiValidator.validateSisallonTyyppi(null));
        assertEquals(Set.of(ViestiValidator.VALIDATION_SISALLONTYYPPI), ViestiValidator.validateSisallonTyyppi(""));
        assertEquals(Set.of(ViestiValidator.VALIDATION_SISALLONTYYPPI), ViestiValidator.validateSisallonTyyppi("jotain hämärää"));
    }

    @Test
    void testValidateKielet() {
        assertEquals(Set.of(), ViestiValidator.validateKielet(null));
        assertEquals(Set.of(), ViestiValidator.validateKielet(List.of()));

        assertEquals(Set.of(), ViestiValidator.validateKielet(List.of("fi", "sv")));
        assertEquals(Set.of(), ViestiValidator.validateKielet(List.of("sv", "fi")));
        assertEquals(Set.of(), ViestiValidator.validateKielet(List.of("en")));

        assertEquals(Set.of(ViestiValidator.VALIDATION_KIELI_EI_SALLITTU + "de"), ViestiValidator.validateKielet(List.of("de")));

        assertEquals(Set.of(ViestiValidator.VALIDATION_KIELI_NULL), ViestiValidator.validateKielet(Arrays.asList("en", null)));

        assertEquals(Set.of(ViestiValidator.VALIDATION_KIELI_DUPLICATES + "en"), ViestiValidator.validateKielet(Arrays.asList("en", "en")));

        assertEquals(Set.of(ViestiValidator.VALIDATION_KIELI_EI_SALLITTU + "de", ViestiValidator.VALIDATION_KIELI_DUPLICATES + "en"),
                ViestiValidator.validateKielet(Arrays.asList("de", "en", "en")));
    }

    @Test
    void testValidateMaskit() {
        // maskit-kenttä ei ole pakollinen
        assertEquals(Set.of(), ViestiValidator.validateMaskit(null));
        assertEquals(Set.of(), ViestiValidator.validateMaskit(List.of()));

        // maskit joiden salaisuus on määritelty ovat sallittuja
        assertEquals(Set.of(), ViestiValidator.validateMaskit(Arrays.asList(
                maski("salaisuus1", "<salaisuus peitetty>"),
                maski("salaisuus2", null))));

        // null-arvot maskilistassa eivät ole sallittuja
        assertEquals(Set.of(ViestiValidator.VALIDATION_MASKIT_NULL),
                ViestiValidator.validateMaskit(Arrays.asList(maski("salaisuus1", "<salaisuus peitetty>"), null)));

        // määrittelemätön maski on sallittu
        assertEquals(Set.of(), ViestiValidator.validateMaskit(List.of(maski("salaisuus1", null))));

        // määrittelemätön salaisuus ei ole sallittu
        assertEquals(Set.of("Maski (salaisuus: , maski: <salaisuus peitetty>): " + ViestiValidator.VALIDATION_MASKIT_EI_SALAISUUTTA),
                ViestiValidator.validateMaskit(List.of(maski(null, "<salaisuus peitetty>"))));

        // salaisuuden pituus on rajoitettu
        assertEquals(Set.of("Maski (salaisuus: " + "*".repeat(ViestiValidator.VIESTI_SALAISUUS_MIN_PITUUS - 1) + ", maski: peitetty): " + ViestiValidator.VALIDATION_MASKIT_SALAISUUS_PITUUS),
                ViestiValidator.validateMaskit(List.of(maski("*".repeat(ViestiValidator.VIESTI_SALAISUUS_MIN_PITUUS - 1), "peitetty"))));
        assertEquals(Set.of("Maski (salaisuus: " + "*".repeat(ViestiValidator.VIESTI_SALAISUUS_MAX_PITUUS + 1) + ", maski: peitetty): " + ViestiValidator.VALIDATION_MASKIT_SALAISUUS_PITUUS),
                ViestiValidator.validateMaskit(List.of(maski("*".repeat(ViestiValidator.VIESTI_SALAISUUS_MAX_PITUUS + 1), "peitetty"))));

        // maskin pituus on rajoitettu
        assertEquals(Set.of("Maski (salaisuus: *********, maski: " + "*".repeat(ViestiValidator.VIESTI_MASKI_MIN_PITUUS - 1) + "): " + ViestiValidator.VALIDATION_MASKIT_MASKI_PITUUS),
                ViestiValidator.validateMaskit(List.of(maski("salaisuus", "*".repeat(ViestiValidator.VIESTI_MASKI_MIN_PITUUS - 1)))));
        assertEquals(Set.of("Maski (salaisuus: *********, maski: " + "*".repeat(ViestiValidator.VIESTI_MASKI_MAX_PITUUS + 1) + "): " + ViestiValidator.VALIDATION_MASKIT_MASKI_PITUUS),
                ViestiValidator.validateMaskit(List.of(maski("salaisuus", "*".repeat(ViestiValidator.VIESTI_MASKI_MAX_PITUUS + 1)))));

        // maskien määrä on rajoitettu
        List<Maski> maskit2 = IntStream.range(0, ViestiValidator.VIESTI_MASKIT_MAX_MAARA + 1)
                .mapToObj(i -> maski("salaisuus" + i, "peitetty" + i)).collect(Collectors.toList());
        assertEquals(Set.of(ViestiValidator.VALIDATION_MASKIT_LIIKAA), ViestiValidator.validateMaskit(maskit2));

        // kaikki virheet kerätään
        assertEquals(Set.of(
                "Maski (salaisuus: , maski: <salaisuus peitetty>): " + ViestiValidator.VALIDATION_MASKIT_EI_SALAISUUTTA,
                ViestiValidator.VALIDATION_MASKIT_NULL),
                ViestiValidator.validateMaskit(Arrays.asList(maski(null, "<salaisuus peitetty>"), null)));

        // duplikaattisalaisuudet eivät ole sallittuja
        assertEquals(Set.of(ViestiValidator.VALIDATION_MASKIT_DUPLICATES + "*********"),
                ViestiValidator.validateMaskit(List.of(
                        maski("salaisuus", "<salaisuus peitetty>"),
                        maski("salaisuus", "<salaisuus peitetty toisin>"))));
    }

    @Test
    void testValidateVastaanottajat() {
        // vastaanottajat-kenttä pitää olla määritelty
        assertEquals(Set.of(ViestiValidator.VALIDATION_VASTAANOTTAJAT_TYHJA), ViestiValidator.validateVastaanottajat(null));
        assertEquals(Set.of(ViestiValidator.VALIDATION_VASTAANOTTAJAT_TYHJA), ViestiValidator.validateVastaanottajat(List.of()));

        // vastaanottajia ei saa olla liikaa
        assertEquals(Set.of(ViestiValidator.VALIDATION_VASTAANOTTAJAT_LIIKAA),
                ViestiValidator.validateVastaanottajat(IntStream.range(0, ViestiValidator.VIESTI_VASTAANOTTAJAT_MAX_MAARA + 1)
                        .mapToObj(i -> vastaanottaja(null, "vastaanottaja" + i + "@example.com")).collect(Collectors.toList())));

        // vastaanottajat joiden osoite validi ovat sallittuja
        assertEquals(Set.of(), ViestiValidator.validateVastaanottajat(Arrays.asList(
                vastaanottaja("Vallu Vastaanottaja", "vallu.vastaanottaja@example.com"),
                vastaanottaja(null, "veera.vastaanottaja@example.com"))));

        // null-arvot vastaanottajalistassa eivät ole sallittuja
        assertEquals(Set.of(ViestiValidator.VALIDATION_VASTAANOTTAJA_NULL),
                ViestiValidator.validateVastaanottajat(Arrays.asList(
                        vastaanottaja("Vallu Vastaanottaja", "vallu.vastaanottaja@example.com"), null)));

        // liian pitkä nimi ei ole sallittu
        assertEquals(Set.of("Vastaanottaja (nimi: " + "x".repeat(ViestiValidator.VIESTI_NIMI_MAX_PITUUS + 1) + ", sähköpostiosoite: Optional[vallu.vastaanottaja@example.com]): " + ViestiValidator.VALIDATION_VASTAANOTTAJAN_NIMI_LIIAN_PITKA),
                ViestiValidator.validateVastaanottajat(List.of(vastaanottaja("x".repeat(ViestiValidator.VIESTI_NIMI_MAX_PITUUS + 1), "vallu.vastaanottaja@example.com"))));

        // määrittelemätön sähköpostiosoite ei ole sallittu
        assertEquals(Set.of("Vastaanottaja (nimi: Vallu Vastaanottaja, sähköpostiosoite: Optional.empty): " + ViestiValidator.VALIDATION_VASTAANOTTAJAN_OSOITE_TYHJA),
                ViestiValidator.validateVastaanottajat(List.of(vastaanottaja("Vallu Vastaanottaja", null))));

        // liian pitkä sähköpostiosoite ei ole sallittu
        assertEquals(Set.of("Vastaanottaja (nimi: Vallu Vastaanottaja, sähköpostiosoite: Optional[" + "x".repeat(ViestiValidator.VIESTI_OSOITE_MAX_PITUUS + 1) + "]): " + ViestiValidator.VALIDATION_VASTAANOTTAJAN_OSOITE_LIIAN_PITKA),
                ViestiValidator.validateVastaanottajat(List.of(vastaanottaja("Vallu Vastaanottaja", "x".repeat(ViestiValidator.VIESTI_OSOITE_MAX_PITUUS + 1)))));

        // epävalidi sähköpostiosoite on sallittu (muotoa ei validoida)
        assertEquals(Set.of(), ViestiValidator.validateVastaanottajat(List.of(vastaanottaja("Vallu Vastaanottaja", "ei mikään osoite"))));

        // kaikki virheet kerätään
        assertEquals(Set.of(
                "Vastaanottaja (nimi: Vallu Vastaanottaja, sähköpostiosoite: Optional.empty): " + ViestiValidator.VALIDATION_VASTAANOTTAJAN_OSOITE_TYHJA,
                "Vastaanottaja (nimi: Vallu Vastaanottaja, sähköpostiosoite: Optional[" + "x".repeat(ViestiValidator.VIESTI_OSOITE_MAX_PITUUS + 1) + "]): " + ViestiValidator.VALIDATION_VASTAANOTTAJAN_OSOITE_LIIAN_PITKA),
                ViestiValidator.validateVastaanottajat(Arrays.asList(
                        vastaanottaja("Vallu Vastaanottaja", null),
                        vastaanottaja("Vallu Vastaanottaja", "x".repeat(ViestiValidator.VIESTI_OSOITE_MAX_PITUUS + 1)))));

        // duplikaattiosoitteet eivät ole sallittuja
        assertEquals(Set.of(ViestiValidator.VALIDATION_VASTAANOTTAJA_OSOITE_DUPLICATE + "vallu.vastaanottaja@example.com"),
                ViestiValidator.validateVastaanottajat(List.of(
                        vastaanottaja("Vallu Vastaanottaja", "vallu.vastaanottaja@example.com"),
                        vastaanottaja("Vallu Vastaanottaja", "vallu.vastaanottaja@example.com"))));
    }

    @Test
    void testValidateLiiteTunnisteet() {
        String validi1 = "3fa85f64-5717-4562-b3fc-2c963f66afa6";
        String identiteetti1 = "jarjestelma1";
        String validi2 = "4fa85f64-5717-4562-b3fc-2c963f66afa6";
        String identiteetti2 = "jarjestelma2";
        Map<UUID, LiiteMetadata> liiteMetadatat = Map.of(
                UUID.fromString(validi1), new LiiteMetadata(identiteetti1, 0),
                UUID.fromString(validi2), new LiiteMetadata(identiteetti2, 0));

        // validit liitetunnisteet ovat sallittuja tunnisteet ladanneelle identiteetille
        assertEquals(Set.of(), ViestiValidator.validateLiitteidenTunnisteet(List.of(validi1), liiteMetadatat, identiteetti1));

        // null-arvot liitetunnistelistassa eivät ole sallittuja
        assertEquals(Set.of(ViestiValidator.VALIDATION_LIITETUNNISTE_NULL),
                ViestiValidator.validateLiitteidenTunnisteet(Arrays.asList(validi1, null), liiteMetadatat, identiteetti1));

        // liitetunnisteita ei voi olla määrättömästi
        List<String> tunnisteet2 = IntStream.range(0, ViestiValidator.VIESTI_LIITTEET_MAX_MAARA + 1)
                .mapToObj(i -> UUID.randomUUID().toString()).collect(Collectors.toList());
        assertTrue(ViestiValidator.validateLiitteidenTunnisteet(tunnisteet2, Map.of(), identiteetti1)
                .contains(ViestiValidator.VALIDATION_LIITETUNNISTE_LIIKAA));

        // väärän muotoinen liitetunniste ei ole sallittu
        String eiUuid = "ei uuid-muotoinen tunniste";
        assertEquals(Set.of("Liitetunniste \"" + eiUuid + "\": " + ViestiValidator.VALIDATION_LIITETUNNISTE_INVALID),
                ViestiValidator.validateLiitteidenTunnisteet(List.of(eiUuid), liiteMetadatat, identiteetti1));

        // oikean muotoinen liitetunniste jolla ei liitettä ei sallittu / toisen identiteetin lataama ei sallittu
        assertEquals(Set.of("Liitetunniste \"" + validi2 + "\": " + ViestiValidator.VALIDATION_LIITETUNNISTE_EI_TARJOLLA),
                ViestiValidator.validateLiitteidenTunnisteet(List.of(validi2), liiteMetadatat, identiteetti1));

        // kaikki virheet kerätään
        assertEquals(Set.of(
                "Liitetunniste \"" + eiUuid + "\": " + ViestiValidator.VALIDATION_LIITETUNNISTE_INVALID,
                "Liitetunniste \"" + validi2 + "\": " + ViestiValidator.VALIDATION_LIITETUNNISTE_EI_TARJOLLA),
                ViestiValidator.validateLiitteidenTunnisteet(List.of(eiUuid, validi2), liiteMetadatat, identiteetti1));

        // duplikaatit eivät sallittuja
        assertEquals(Set.of(ViestiValidator.VALIDATION_LIITETUNNISTE_DUPLICATE + validi1),
                ViestiValidator.validateLiitteidenTunnisteet(List.of(validi1, validi1), liiteMetadatat, identiteetti1));
    }

    @Test
    void testValidateLahetysTunniste() {
        String validi1 = "3fa85f64-5717-4562-b3fc-2c963f66afa6";
        String identiteetti1 = "jarjestelma1";
        String identiteetti2 = "jarjestelma2";

        // määrittelemätön tunniste on sallittu
        assertEquals(Set.of(), ViestiValidator.validateLahetysTunniste(null, Optional.empty(), identiteetti1));

        // järjestelmän luoma tunniste on sallittu
        assertEquals(Set.of(), ViestiValidator.validateLahetysTunniste(validi1, Optional.of(new LahetysMetadata(identiteetti1, false)), identiteetti1));

        // ei validi tunniste ei ole sallittu
        assertEquals(Set.of(ViestiValidator.VALIDATION_LAHETYSTUNNISTE_INVALID),
                ViestiValidator.validateLahetysTunniste("jotain hämärää", Optional.empty(), identiteetti1));

        // toisen identiteetin luoma tunniste ei ole sallittu
        assertEquals(Set.of(ViestiValidator.VALIDATION_LAHETYSTUNNISTE_EI_TARJOLLA),
                ViestiValidator.validateLahetysTunniste(validi1, Optional.of(new LahetysMetadata(identiteetti1, false)), identiteetti2));
    }

    @Test
    void testValidateMetadata() {
        // sallitut merkit, ei duplikaatteja
        assertEquals(Set.of(), ViestiValidator.validateMetadata(
                Map.of("avain1-_.", List.of("arvo1-_."), "avain2", List.of("arvo1", "arvo2"))));

        // null-arvot eivät ole sallittuja
        Map<String, List<String>> metadata1 = new LinkedHashMap<>();
        metadata1.put("avain1", null);
        metadata1.put("avain2", Arrays.asList("arvo1", null));
        assertEquals(Set.of(ViestiValidator.VALIDATION_METADATA_NULL + "avain1,avain2"),
                ViestiValidator.validateMetadata(metadata1));

        // duplikaattiarvot eivät ole sallittuja
        Map<String, List<String>> metadata2 = new LinkedHashMap<>();
        metadata2.put("avain1", List.of("arvo1"));
        metadata2.put("avain2", List.of("arvo2", "arvo2"));
        assertEquals(Set.of(ViestiValidator.VALIDATION_METADATA_DUPLICATE + "avain2"),
                ViestiValidator.validateMetadata(metadata2));

        // liian monta avainta ei ole sallittu
        Map<String, List<String>> liikaaAvaimia = new LinkedHashMap<>();
        IntStream.range(0, ViestiValidator.VIESTI_METADATA_AVAIMET_MAX_MAARA + 1)
                .forEach(i -> liikaaAvaimia.put("avain" + i, List.of("arvo")));
        assertEquals(Set.of(ViestiValidator.VALIDATION_METADATA_ARVOT_MAARA), ViestiValidator.validateMetadata(liikaaAvaimia));

        // erikoismerkkejä sisältävä avain ei ole sallittu
        assertEquals(Set.of("Metadata \"avain!!!!\": " + ViestiValidator.VALIDATION_METADATA_AVAIN_INVALID),
                ViestiValidator.validateMetadata(Map.of("avain!!!!", List.of("arvo1"))));

        // liian pitkä avain ei ole sallittu
        assertEquals(Set.of("Metadata \"" + "x".repeat(ViestiValidator.VIESTI_METADATA_AVAIN_MAX_PITUUS + 1) + "\": " + ViestiValidator.VALIDATION_METADATA_AVAIN_PITUUS),
                ViestiValidator.validateMetadata(Map.of("x".repeat(ViestiValidator.VIESTI_METADATA_AVAIN_MAX_PITUUS + 1), List.of("arvo1"))));

        // liian monta arvoa ei ole sallittu
        assertEquals(Set.of("Metadata \"avain\": " + ViestiValidator.VALIDATION_METADATA_ARVOT_MAARA),
                ViestiValidator.validateMetadata(Map.of("avain", IntStream.range(0, ViestiValidator.VIESTI_METADATA_ARVOT_MAX_MAARA + 1)
                        .mapToObj(i -> "arvo" + i).collect(Collectors.toList()))));

        // liian pitkät arvot ei sallittu
        assertEquals(Set.of("Metadata \"avain\": " + ViestiValidator.VALIDATION_METADATA_ARVO_PITUUS + "x".repeat(ViestiValidator.VIESTI_METADATA_ARVO_MAX_PITUUS + 1)),
                ViestiValidator.validateMetadata(Map.of("avain", List.of("x".repeat(ViestiValidator.VIESTI_METADATA_ARVO_MAX_PITUUS + 1)))));

        // erikoismerkkejä sisältävä arvo ei sallittu
        assertEquals(Set.of("Metadata \"avain\": " + ViestiValidator.VALIDATION_METADATA_ARVO_INVALID + "arvo!!!!"),
                ViestiValidator.validateMetadata(Map.of("avain", List.of("arvo!!!!"))));
    }

    @Test
    void testValidateKayttooikeusRajoitukset() {
        Kayttooikeus rajoitus = new Kayttooikeus("RAJOITUS1", "1.2.246.562.10.00000000000000006666");
        Kayttooikeus organisaatioTyhja = new Kayttooikeus("RAJOITUS1", null);
        Kayttooikeus organisaatioInvalid = new Kayttooikeus("RAJOITUS1", "ei hyvä");
        String pitkaOrganisaatio = IntStream.range(0, ViestiValidator.VIESTI_ORGANISAATIO_MAX_PITUUS + 1)
                .mapToObj(Integer::toString).collect(Collectors.joining("."));
        Kayttooikeus pitkaOrganisaatioRajoitus = new Kayttooikeus("RAJOITUS1", pitkaOrganisaatio);
        Kayttooikeus oikeusTyhja = new Kayttooikeus(null, "1.2.246.562.10.00000000000000006666");
        String pitkaOikeus = "X".repeat(ViestiValidator.VIESTI_OIKEUS_MAX_PITUUS + 1);
        Kayttooikeus oikeusPitka = new Kayttooikeus(pitkaOikeus, "1.2.246.562.10.00000000000000006666");

        // kenttä ei ole pakollinen
        assertEquals(Set.of(), ViestiValidator.validateKayttooikeusRajoitukset(null));

        // validi rajoitus on sallittu
        assertEquals(Set.of(), ViestiValidator.validateKayttooikeusRajoitukset(List.of(rajoitus)));

        // rajoituksia ei voi olla määrättömästi
        List<Kayttooikeus> rajoitukset = IntStream.range(0, ViestiValidator.VIESTI_KAYTTOOIKEUS_MAX_MAARA + 1)
                .mapToObj(i -> new Kayttooikeus(rajoitus.oikeus(), rajoitus.organisaatio() + "" + i)).collect(Collectors.toList());
        assertEquals(Set.of(ViestiValidator.VALIDATION_KAYTTOOIKEUSRAJOITUS_LIIKAA), ViestiValidator.validateKayttooikeusRajoitukset(rajoitukset));

        // null-arvot eivät ole sallittuja
        assertEquals(Set.of(ViestiValidator.VALIDATION_KAYTTOOIKEUSRAJOITUS_NULL),
                ViestiValidator.validateKayttooikeusRajoitukset(Arrays.asList(rajoitus, null)));

        // duplikaatit eivät sallittuja
        assertEquals(Set.of(ViestiValidator.VALIDATION_KAYTTOOIKEUSRAJOITUS_DUPLICATE + rajoitus),
                ViestiValidator.validateKayttooikeusRajoitukset(List.of(rajoitus, rajoitus)));

        // oikeuksien pitää olla organisaatiorajoitettuja validiin organisaatioon
        assertEquals(Set.of("Käyttöoikeusrajoitus (\"\",RAJOITUS1): " + ViestiValidator.VALIDATION_ORGANISAATIO_INVALID),
                ViestiValidator.validateKayttooikeusRajoitukset(List.of(organisaatioTyhja)));
        assertEquals(Set.of("Käyttöoikeusrajoitus (" + organisaatioInvalid.organisaatio() + ",RAJOITUS1): " + ViestiValidator.VALIDATION_ORGANISAATIO_INVALID),
                ViestiValidator.validateKayttooikeusRajoitukset(List.of(organisaatioInvalid)));

        // organisaatio ei saa olla liian pitkä
        assertEquals(Set.of("Käyttöoikeusrajoitus (" + pitkaOrganisaatioRajoitus.organisaatio() + ",RAJOITUS1): "
                        + ViestiValidator.VALIDATION_ORGANISAATIO_INVALID + "," + ViestiValidator.VALIDATION_ORGANISAATIO_PITUUS),
                ViestiValidator.validateKayttooikeusRajoitukset(List.of(pitkaOrganisaatioRajoitus)));

        // oikeus pitää olla määritelty
        assertEquals(Set.of("Käyttöoikeusrajoitus (" + oikeusTyhja.organisaatio() + ",\"\"): " + ViestiValidator.VALIDATION_OIKEUS_TYHJA),
                ViestiValidator.validateKayttooikeusRajoitukset(List.of(oikeusTyhja)));

        // oikeus ei saa olla liian pitkä
        assertEquals(Set.of("Käyttöoikeusrajoitus (" + oikeusPitka.organisaatio() + "," + oikeusPitka.oikeus() + "): " + ViestiValidator.VALIDATION_OIKEUS_PITUUS),
                ViestiValidator.validateKayttooikeusRajoitukset(List.of(oikeusPitka)));

        // kaikki virheet kerätään
        assertEquals(Set.of(
                ViestiValidator.VALIDATION_KAYTTOOIKEUSRAJOITUS_NULL,
                ViestiValidator.VALIDATION_KAYTTOOIKEUSRAJOITUS_DUPLICATE + rajoitus),
                ViestiValidator.validateKayttooikeusRajoitukset(Arrays.asList(rajoitus, null, rajoitus)));
    }

    @Test
    void testValidateIdempotencyKey() {
        // ok että idempotency-avainta ei ole
        assertEquals(Set.of(), ViestiValidator.validateIdempotencyKey(null));

        // ok että sisältää sallittuja merkkejä ja ei liian pitkä
        assertEquals(Set.of(), ViestiValidator.validateIdempotencyKey("ABCabc123-_."));

        // avain ei saa olla liian pitkä
        assertEquals(Set.of(ViestiValidator.VALIDATION_IDEMPOTENCY_KEY_LIIAN_PITKA),
                ViestiValidator.validateIdempotencyKey("a".repeat(ViestiValidator.VIESTI_IDEMPOTENCY_KEY_MAX_PITUUS + 1)));

        // avain ei saa sisältää ei-sallittuja merkkejä
        assertEquals(Set.of(ViestiValidator.VALIDATION_IDEMPOTENCY_KEY_INVALID), ViestiValidator.validateIdempotencyKey("%"));

        // kaikki virheet kerätään
        assertEquals(Set.of(ViestiValidator.VALIDATION_IDEMPOTENCY_KEY_LIIAN_PITKA, ViestiValidator.VALIDATION_IDEMPOTENCY_KEY_INVALID),
                ViestiValidator.validateIdempotencyKey("%".repeat(ViestiValidator.VIESTI_IDEMPOTENCY_KEY_MAX_PITUUS + 1)));
    }

    @Test
    void testValidateLahetysJaPeritytKentat() {
        String lahetysTunniste = UUID.randomUUID().toString();

        // ok että lähetys määritelty ja lähetyksen kenttiä ei
        assertEquals(Set.of(), ViestiValidator.validateLahetysJaPeritytKentat(lahetysTunniste, null, null, null, null, null, null));

        // jos lähetys määritelty lähettävä palvelu ei voi olla määritelty
        assertEquals(Set.of(ViestiValidator.VALIDATION_LAHETTAVAPALVELU_EI_TYHJA),
                ViestiValidator.validateLahetysJaPeritytKentat(lahetysTunniste, "palvelu", null, null, null, null, null));

        // jos lähetys määritelty virkailijan oid ei voi olla määritelty
        assertEquals(Set.of(ViestiValidator.VALIDATION_VIRKAILIJANOID_EI_TYHJA),
                ViestiValidator.validateLahetysJaPeritytKentat(lahetysTunniste, null, LahetysValidator.VALIDATION_OPH_OID_PREFIX + ".111", null, null, null, null));

        // jos lähetys määritelty lähettäjä ei voi olla määritelty
        assertEquals(Set.of(ViestiValidator.VALIDATION_LAHETTAJA_EI_TYHJA),
                ViestiValidator.validateLahetysJaPeritytKentat(lahetysTunniste, null, null, new Kontakti(null, "noreply@opintopolku.fi"), null, null, null));

        // jos lähetys määritelty replyto ei voi olla määritelty
        assertEquals(Set.of(ViestiValidator.VALIDATION_REPLYTO_EI_TYHJA),
                ViestiValidator.validateLahetysJaPeritytKentat(lahetysTunniste, null, null, null, "vastatkaaminulle@oph.fi", null, null));

        // jos lähetys määritelty prioriteetti ei voi olla määritelty
        assertEquals(Set.of(ViestiValidator.VALIDATION_PRIORITEETTI_EI_TYHJA),
                ViestiValidator.validateLahetysJaPeritytKentat(lahetysTunniste, null, null, null, null, LahetysValidator.LAHETYS_PRIORITEETTI_NORMAALI, null));

        // jos lähetys määritelty säilytysaika ei voi olla määritelty
        assertEquals(Set.of(ViestiValidator.VALIDATION_SAILYTYSAIKA_EI_TYHJA),
                ViestiValidator.validateLahetysJaPeritytKentat(lahetysTunniste, null, null, null, null, null, 1));

        // jos lähetys ei määritelty kentät validoidaan kuten ne olisivat lähetyksessä (tässä vain oid virheellinen)
        assertEquals(Set.of(LahetysValidator.VALIDATION_LAHETTAJAN_OID_INVALID),
                ViestiValidator.validateLahetysJaPeritytKentat(null, "okpalvelu", "ei validi oid", new Kontakti(null, "ok-osoite@opintopolku.fi"), null, LahetysValidator.LAHETYS_PRIORITEETTI_NORMAALI, 1));

        // myös kentän sisältö validoidaan vaikka kenttä ei saa olla määritelty
        assertEquals(Set.of(ViestiValidator.VALIDATION_VIRKAILIJANOID_EI_TYHJA, LahetysValidator.VALIDATION_LAHETTAJAN_OID_INVALID),
                ViestiValidator.validateLahetysJaPeritytKentat(lahetysTunniste, null, "ei validdi oid", null, null, null, null));

        // kaikki virheet kerätään
        assertEquals(Set.of(ViestiValidator.VALIDATION_LAHETTAVAPALVELU_EI_TYHJA, ViestiValidator.VALIDATION_VIRKAILIJANOID_EI_TYHJA),
                ViestiValidator.validateLahetysJaPeritytKentat(lahetysTunniste, "palvelu", LahetysValidator.VALIDATION_OPH_OID_PREFIX + ".111", null, null, null, null));
    }

    @Test
    void testValidateKorkeaPrioriteetti() {
        // korkea prioriteetti ja yksi vastaanottaja ovat sallittuja
        assertEquals(Set.of(), ViestiValidator.validateKorkeaPrioriteetti(LahetysValidator.LAHETYS_PRIORITEETTI_KORKEA,
                List.of(vastaanottaja(null, "vallu.vastaanottaja@example.com")), Optional.empty()));
        assertEquals(Set.of(), ViestiValidator.validateKorkeaPrioriteetti(LahetysValidator.LAHETYS_PRIORITEETTI_NORMAALI,
                List.of(vastaanottaja(null, "vallu.vastaanottaja@example.com")), Optional.of(new LahetysMetadata("", true))));

        // normaali prioriteetti ja useampi vastaanottaja ovat sallittuja
        assertEquals(Set.of(), ViestiValidator.validateKorkeaPrioriteetti(LahetysValidator.LAHETYS_PRIORITEETTI_NORMAALI,
                List.of(vastaanottaja(null, "vallu.vastaanottaja@example.com"), vastaanottaja(null, "veera.vastaanottaja@example.com")), Optional.empty()));

        // korkealla prioriteetilla voi olla vain yksi vastaanottaja
        assertEquals(Set.of(ViestiValidator.VALIDATION_KORKEA_PRIORITEETTI_VASTAANOTTAJAT),
                ViestiValidator.validateKorkeaPrioriteetti(LahetysValidator.LAHETYS_PRIORITEETTI_KORKEA,
                        List.of(vastaanottaja(null, "vallu.vastaanottaja@example.com"), vastaanottaja(null, "veera.vastaanottaja@example.com")), Optional.empty()));
        assertEquals(Set.of(ViestiValidator.VALIDATION_KORKEA_PRIORITEETTI_VASTAANOTTAJAT),
                ViestiValidator.validateKorkeaPrioriteetti(LahetysValidator.LAHETYS_PRIORITEETTI_NORMAALI,
                        List.of(vastaanottaja(null, "vallu.vastaanottaja@example.com"), vastaanottaja(null, "veera.vastaanottaja@example.com")), Optional.of(new LahetysMetadata("", true))));
    }

    @Test
    void testValidateViestinKoko() {
        String identiteetti1 = "identiteetti1";
        String identiteetti2 = "identiteetti2";
        UUID liiteTunniste1 = UUID.randomUUID();
        UUID liiteTunniste2 = UUID.randomUUID();
        Map<UUID, LiiteMetadata> liiteMetadata1 = Map.of(liiteTunniste1, new LiiteMetadata(identiteetti1, 1024 * 1024));
        Map<UUID, LiiteMetadata> liiteMetadata2 = Map.of(liiteTunniste2, new LiiteMetadata(identiteetti2, ViestiValidator.VIESTI_MAX_SIZE + 1));

        // alle maksimikoon olevat viestit ovat sallittuja
        assertEquals(Set.of(), ViestiValidator.validateKoko("Sisältö", List.of(liiteTunniste1.toString()), liiteMetadata1, identiteetti1));

        // liian iso liite aiheuttaa virheen
        assertEquals(Set.of(ViestiValidator.VALIDATION_KOKO), ViestiValidator.validateKoko("Sisältö", List.of(liiteTunniste2.toString()), liiteMetadata2, identiteetti2));

        // liian iso sisältö aiheuttaa virheen
        assertEquals(Set.of(ViestiValidator.VALIDATION_KOKO), ViestiValidator.validateKoko("x".repeat(ViestiValidator.VIESTI_MAX_SIZE + 1), List.of(), Map.of(), identiteetti1));

        // liitteet joilta puuttuu metadata ignoorataan
        assertEquals(Set.of(), ViestiValidator.validateKoko("Sisältö", List.of(liiteTunniste1.toString()), Map.of(), identiteetti1));

        // muiden omistamat liitteet ignoorataan
        assertEquals(Set.of(), ViestiValidator.validateKoko("Sisältö", List.of(liiteTunniste2.toString()), liiteMetadata2, identiteetti1));
    }
}
