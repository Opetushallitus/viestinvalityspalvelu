package fi.vm.sade.viestinvalitys.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import fi.vm.sade.viestinvalitys.dto.Kontakti;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LahetysValidatorTest {

    @Test
    void testValidateOtsikko() {
        // laillinen otsikko on sallittu
        assertEquals(Set.of(), LahetysValidator.validateOtsikko("Tosi hyvä otsikko"));

        // tyhjä otsikko ei ole sallittu
        assertEquals(Set.of(LahetysValidator.VALIDATION_OTSIKKO_TYHJA), LahetysValidator.validateOtsikko(null));
        assertEquals(Set.of(LahetysValidator.VALIDATION_OTSIKKO_TYHJA), LahetysValidator.validateOtsikko(""));

        // liian pitkä otsikko ei ole sallittu
        assertEquals(Set.of(LahetysValidator.VALIDATION_OTSIKKO_LIIAN_PITKA),
                LahetysValidator.validateOtsikko("x".repeat(LahetysValidator.OTSIKKO_MAX_PITUUS + 1)));
    }

    @Test
    void testValidateLahettavaPalvelu() {
        // validin muotoiset avaimet sallittu
        assertEquals(Set.of(), LahetysValidator.validateLahettavaPalvelu("kaannosavain1"));
        assertEquals(Set.of(), LahetysValidator.validateLahettavaPalvelu("kaannosavain2"));

        // tyhjä käännösavain ei sallittu
        assertEquals(Set.of(LahetysValidator.VALIDATION_LAHETTAVA_PALVELU_TYHJA),
                LahetysValidator.validateLahettavaPalvelu(null));

        // liian pitkä avain ei sallittu
        assertEquals(Set.of(LahetysValidator.VALIDATION_LAHETTAVA_PALVELU_LIIAN_PITKA),
                LahetysValidator.validateLahettavaPalvelu("x".repeat(LahetysValidator.LAHETTAVAPALVELU_MAX_PITUUS + 1)));

        // kaikki virheet kerätään
        assertEquals(Set.of(LahetysValidator.VALIDATION_LAHETTAVA_PALVELU_LIIAN_PITKA),
                LahetysValidator.validateLahettavaPalvelu("x".repeat(LahetysValidator.LAHETTAVAPALVELU_MAX_PITUUS) + "!\\?*"));
    }

    @Test
    void testValidateLahettajanOid() {
        // oph-oidit ovat sallittuja
        assertEquals(Set.of(), LahetysValidator.validateLahettavanVirkailijanOID(LahetysValidator.VALIDATION_OPH_OID_PREFIX + ".123.456.00001"));
        assertEquals(Set.of(), LahetysValidator.validateLahettavanVirkailijanOID(LahetysValidator.VALIDATION_OPH_OID_PREFIX + ".789.987.00001"));

        // määrittelemätön oid on sallittu
        assertEquals(Set.of(), LahetysValidator.validateLahettavanVirkailijanOID(null));

        // muut kuin oph-oidit eivät ole sallittuja
        assertEquals(Set.of(LahetysValidator.VALIDATION_LAHETTAJAN_OID_INVALID),
                LahetysValidator.validateLahettavanVirkailijanOID("123.456.789"));

        // liian pitkä oid ei ole sallittu
        assertEquals(Set.of(LahetysValidator.VALIDATION_LAHETTAJAN_OID_PITUUS),
                LahetysValidator.validateLahettavanVirkailijanOID(
                        LahetysValidator.VALIDATION_OPH_OID_PREFIX + "." + "0".repeat(LahetysValidator.VIRKAILIJAN_OID_MAX_PITUUS)));
    }

    @Test
    void testValidateLahettaja() {
        // lähettäjät joiden osoite validi ovat sallittuja
        assertEquals(Set.of(), LahetysValidator.validateLahettaja(new Kontakti("Opetushallitus", "noreply@opintopolku.fi")));
        assertEquals(Set.of(), LahetysValidator.validateLahettaja(new Kontakti(null, "jotain@opintopolku.fi")));

        // määrittelemätön lähettäjä ei ole sallittu
        assertEquals(Set.of(LahetysValidator.VALIDATION_LAHETTAJA_TYHJA), LahetysValidator.validateLahettaja(null));

        // määrittelemätön nimi on sallittu
        assertEquals(Set.of(), LahetysValidator.validateLahettaja(new Kontakti(null, "noreply@opintopolku.fi")));

        // liian pitkä nimi ei ole sallittu
        assertEquals(Set.of(LahetysValidator.VALIDATION_LAHETTAJA_NIMI_LIIAN_PITKA),
                LahetysValidator.validateLahettaja(new Kontakti("x".repeat(ViestiValidator.VIESTI_NIMI_MAX_PITUUS + 1), "noreply@opintopolku.fi")));

        // määrittelemätön osoite ei ole sallittu
        assertEquals(Set.of(LahetysValidator.VALIDATION_LAHETTAJAN_OSOITE_TYHJA),
                LahetysValidator.validateLahettaja(new Kontakti("Opetushallitus", null)));

        // ei validi osoite ei ole sallittu
        assertEquals(Set.of(LahetysValidator.VALIDATION_LAHETTAJAN_OSOITE_INVALID),
                LahetysValidator.validateLahettaja(new Kontakti("Opetushallitus", "ei validi osoite")));

        // ei opintopolku.fi -domain ei ole sallittu
        assertEquals(Set.of(LahetysValidator.VALIDATION_LAHETTAJAN_OSOITE_DOMAIN),
                LahetysValidator.validateLahettaja(new Kontakti("Opetushallitus", "noreply@example.com")));
    }

    @Test
    void testValidateReplyTo() {
        // määrittelemätön replyTo on sallittu
        assertEquals(Set.of(), LahetysValidator.validateReplyTo(null));

        // validi sähköpostiosoite on sallittu
        assertEquals(Set.of(), LahetysValidator.validateReplyTo("ville.virkamies@oph.fi"));

        // ei validi sähköpostiosoite ei ole sallittu
        assertEquals(Set.of(LahetysValidator.VALIDATION_REPLYTO_INVALID),
                LahetysValidator.validateReplyTo("tämä ei ole sähköpostiosoite"));
    }

    @Test
    void testValidatePrioriteetti() {
        // laillinen prioriteetti on sallittu
        assertEquals(Set.of(), LahetysValidator.validatePrioriteetti(LahetysValidator.LAHETYS_PRIORITEETTI_KORKEA));
        assertEquals(Set.of(), LahetysValidator.validatePrioriteetti(LahetysValidator.LAHETYS_PRIORITEETTI_NORMAALI));

        // tyhjä prioriteetti ei ole sallittu
        assertEquals(Set.of(LahetysValidator.VALIDATION_PRIORITEETTI), LahetysValidator.validatePrioriteetti(null));
        assertEquals(Set.of(LahetysValidator.VALIDATION_PRIORITEETTI), LahetysValidator.validatePrioriteetti(""));

        // väärä prioriteetti ei ole sallittu
        assertEquals(Set.of(LahetysValidator.VALIDATION_PRIORITEETTI), LahetysValidator.validatePrioriteetti("jotain hämärää"));
    }

    @Test
    void testValidateSailytysAika() {
        // laillinen säilytysaika on sallittu
        assertEquals(Set.of(), LahetysValidator.validateSailytysAika(5));
        assertEquals(Set.of(), LahetysValidator.validateSailytysAika(365));

        // olematon säilytysaika ei ole sallittu
        assertEquals(Set.of(LahetysValidator.VALIDATION_SAILYTYSAIKA_TYHJA), LahetysValidator.validateSailytysAika(null));
        assertEquals(Set.of(LahetysValidator.VALIDATION_SAILYTYSAIKA), LahetysValidator.validateSailytysAika(-3));
        assertEquals(Set.of(LahetysValidator.VALIDATION_SAILYTYSAIKA), LahetysValidator.validateSailytysAika(0));
    }
}
