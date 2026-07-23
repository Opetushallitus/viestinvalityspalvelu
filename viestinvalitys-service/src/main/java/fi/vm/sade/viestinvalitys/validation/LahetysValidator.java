package fi.vm.sade.viestinvalitys.validation;

import fi.vm.sade.viestinvalitys.dto.Kontakti;
import fi.vm.sade.viestinvalitys.dto.LuoLahetysRequest;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.validator.routines.EmailValidator;

public final class LahetysValidator {

    private LahetysValidator() {}

    public static final int OTSIKKO_MAX_PITUUS = 255;
    public static final int LAHETTAVAPALVELU_MAX_PITUUS = 127;
    public static final int LAHETTAJA_NIMI_MAX_PITUUS = 64;
    public static final int VIRKAILIJAN_OID_MAX_PITUUS = 64;
    public static final int SAILYTYSAIKA_MIN_PITUUS = 1;
    public static final int SAILYTYSAIKA_MAX_PITUUS = 3650;
    public static final String LAHETYS_PRIORITEETTI_KORKEA = "korkea";
    public static final String LAHETYS_PRIORITEETTI_NORMAALI = "normaali";

    public static final String VALIDATION_OPH_OID_PREFIX = "1.2.246.562";
    public static final String VALIDATION_OPH_DOMAIN = "@opintopolku.fi";

    public static final String VALIDATION_OTSIKKO_TYHJA = "otsikko: Kenttä on pakollinen";
    public static final String VALIDATION_OTSIKKO_LIIAN_PITKA =
            "otsikko: Otsikko ei voi pidempi kuin " + OTSIKKO_MAX_PITUUS + " merkkiä";

    public static final String VALIDATION_LAHETTAVA_PALVELU_TYHJA = "lahettavaPalvelu: Kenttä on pakollinen";
    public static final String VALIDATION_LAHETTAVA_PALVELU_LIIAN_PITKA =
            "lahettavaPalvelu: Kentän pituus voi olla korkeintaan " + LAHETTAVAPALVELU_MAX_PITUUS + " merkkiä";

    public static final String VALIDATION_LAHETTAJAN_OID_INVALID =
            "lähettäjänOid: Oid ei ole validi (1.2.246.562-alkuinen) oph-oid";
    public static final String VALIDATION_LAHETTAJAN_OID_PITUUS =
            "lähettäjänOid-kentän suurin sallittu pituus on " + VIRKAILIJAN_OID_MAX_PITUUS + " merkkiä";

    public static final String VALIDATION_LAHETTAJA_TYHJA = "lähettäjä: Kenttä on pakollinen";
    public static final String VALIDATION_LAHETTAJA_NIMI_LIIAN_PITKA =
            "lähettäjä: nimi-kenttä voi maksimissaan olla " + LAHETTAJA_NIMI_MAX_PITUUS + " merkkiä pitkä";
    public static final String VALIDATION_LAHETTAJAN_OSOITE_TYHJA =
            "lähettäjä: Lähettäjän sähköpostiosoite -kenttä on pakollinen";
    public static final String VALIDATION_LAHETTAJAN_OSOITE_INVALID =
            "lähettäjä: Lähettäjän sähköpostiosoite ei ole validi sähköpostiosoite";
    public static final String VALIDATION_LAHETTAJAN_OSOITE_DOMAIN =
            "lähettäjä: Lähettäjän sähköpostiosoite ei ole opintopolku.fi -domainissa";

    public static final String VALIDATION_REPLYTO_INVALID = "replyTo: arvo ei ole validi sähköpostiosoite";

    public static final String VALIDATION_PRIORITEETTI =
            "prioriteetti: Prioriteetti täytyy olla joko \"" + LAHETYS_PRIORITEETTI_NORMAALI
                    + "\" tai \"" + LAHETYS_PRIORITEETTI_KORKEA + "\"";

    public static final String VALIDATION_SAILYTYSAIKA_TYHJA = "sailytysAika: Kenttä on pakollinen";
    public static final String VALIDATION_SAILYTYSAIKA =
            "sailytysAika: Säilytysajan tulee olla " + SAILYTYSAIKA_MIN_PITUUS + "-" + SAILYTYSAIKA_MAX_PITUUS + " päivää";

    private static final Pattern OPH_OID_PATTERN = Pattern.compile(VALIDATION_OPH_OID_PREFIX + "(\\.[0-9]+)+");

    public static Set<String> validateOtsikko(String otsikko) {
        Set<String> virheet = new LinkedHashSet<>();
        if (otsikko == null || otsikko.isEmpty()) {
            virheet.add(VALIDATION_OTSIKKO_TYHJA);
        } else if (otsikko.length() > OTSIKKO_MAX_PITUUS) {
            virheet.add(VALIDATION_OTSIKKO_LIIAN_PITKA);
        }
        return virheet;
    }

    public static Set<String> validateLahettavaPalvelu(String lahettavaPalvelu) {
        Set<String> virheet = new LinkedHashSet<>();
        if (lahettavaPalvelu == null || lahettavaPalvelu.isEmpty()) {
            virheet.add(VALIDATION_LAHETTAVA_PALVELU_TYHJA);
        } else if (lahettavaPalvelu.length() > LAHETTAVAPALVELU_MAX_PITUUS) {
            virheet.add(VALIDATION_LAHETTAVA_PALVELU_LIIAN_PITKA);
        }
        return virheet;
    }

    public static Set<String> validateLahettavanVirkailijanOID(String oid) {
        Set<String> virheet = new LinkedHashSet<>();
        if (oid == null) {
            return virheet;
        }
        if (!OPH_OID_PATTERN.matcher(oid).matches()) {
            virheet.add(VALIDATION_LAHETTAJAN_OID_INVALID);
        }
        if (oid.length() > VIRKAILIJAN_OID_MAX_PITUUS) {
            virheet.add(VALIDATION_LAHETTAJAN_OID_PITUUS);
        }
        return virheet;
    }

    public static Set<String> validateLahettaja(Kontakti lahettaja) {
        Set<String> virheet = new LinkedHashSet<>();
        if (lahettaja == null) {
            virheet.add(VALIDATION_LAHETTAJA_TYHJA);
            return virheet;
        }
        if (lahettaja.nimi() != null && lahettaja.nimi().length() > LAHETTAJA_NIMI_MAX_PITUUS) {
            virheet.add(VALIDATION_LAHETTAJA_NIMI_LIIAN_PITKA);
        }
        String osoite = lahettaja.sahkopostiOsoite();
        if (osoite == null || osoite.isEmpty()) {
            virheet.add(VALIDATION_LAHETTAJAN_OSOITE_TYHJA);
        } else if (!EmailValidator.getInstance(false).isValid(osoite)) {
            virheet.add(VALIDATION_LAHETTAJAN_OSOITE_INVALID);
        } else if (!osoite.endsWith(VALIDATION_OPH_DOMAIN)) {
            virheet.add(VALIDATION_LAHETTAJAN_OSOITE_DOMAIN);
        }
        return virheet;
    }

    public static Set<String> validateReplyTo(String replyTo) {
        Set<String> virheet = new LinkedHashSet<>();
        if (replyTo == null) {
            return virheet;
        }
        if (!EmailValidator.getInstance(false).isValid(replyTo)) {
            virheet.add(VALIDATION_REPLYTO_INVALID);
        }
        return virheet;
    }

    public static Set<String> validatePrioriteetti(String prioriteetti) {
        Set<String> virheet = new LinkedHashSet<>();
        if (prioriteetti == null
                || (!prioriteetti.equals(LAHETYS_PRIORITEETTI_KORKEA) && !prioriteetti.equals(LAHETYS_PRIORITEETTI_NORMAALI))) {
            virheet.add(VALIDATION_PRIORITEETTI);
        }
        return virheet;
    }

    public static Set<String> validateSailytysAika(Integer sailytysAika) {
        Set<String> virheet = new LinkedHashSet<>();
        if (sailytysAika == null) {
            virheet.add(VALIDATION_SAILYTYSAIKA_TYHJA);
        } else if (sailytysAika < SAILYTYSAIKA_MIN_PITUUS || sailytysAika > SAILYTYSAIKA_MAX_PITUUS) {
            virheet.add(VALIDATION_SAILYTYSAIKA);
        }
        return virheet;
    }

    public static Set<String> validateLahetys(LuoLahetysRequest lahetys) {
        Set<String> virheet = new LinkedHashSet<>();
        virheet.addAll(validateOtsikko(lahetys.otsikko()));
        virheet.addAll(validateLahettavaPalvelu(lahetys.lahettavaPalvelu()));
        virheet.addAll(validateLahettavanVirkailijanOID(lahetys.lahettavanVirkailijanOid()));
        virheet.addAll(validateLahettaja(lahetys.lahettaja()));
        virheet.addAll(validateReplyTo(lahetys.replyTo()));
        virheet.addAll(validatePrioriteetti(lahetys.prioriteetti()));
        virheet.addAll(validateSailytysAika(lahetys.sailytysaika()));
        return virheet;
    }
}
