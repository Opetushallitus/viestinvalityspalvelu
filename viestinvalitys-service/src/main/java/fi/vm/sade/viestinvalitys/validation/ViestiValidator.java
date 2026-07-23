package fi.vm.sade.viestinvalitys.validation;

import fi.vm.sade.viestinvalitys.dto.Kayttooikeus;
import fi.vm.sade.viestinvalitys.dto.Kontakti;
import fi.vm.sade.viestinvalitys.dto.LuoViestiRequest;
import fi.vm.sade.viestinvalitys.dto.Maski;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class ViestiValidator {

    private ViestiValidator() {}

    public static final int OTSIKKO_MAX_PITUUS = 255;
    public static final int SISALTO_MAX_PITUUS = 6291456; // = 6*1024*1024
    public static final String VIESTI_MAX_SIZE_MB_STR = "8";
    public static final int VIESTI_MAX_SIZE = Integer.parseInt(VIESTI_MAX_SIZE_MB_STR) * 1024 * 1024;
    public static final int VIESTI_NIMI_MAX_PITUUS = 128;
    public static final int VIESTI_OSOITE_MAX_PITUUS = 512;
    public static final int VIESTI_SALAISUUS_MIN_PITUUS = 8;
    public static final int VIESTI_SALAISUUS_MAX_PITUUS = 1024;
    public static final int VIESTI_MASKI_MIN_PITUUS = 8;
    public static final int VIESTI_MASKI_MAX_PITUUS = 1024;
    public static final int VIESTI_MASKIT_MAX_MAARA = 32;
    public static final int VIESTI_METADATA_AVAIMET_MAX_MAARA = 1024;
    public static final String VIESTI_METADATA_SALLITUT_MERKIT = "a-z, A-Z, 0-9 ja -_.";
    public static final int VIESTI_METADATA_AVAIN_MAX_PITUUS = 64;
    public static final int VIESTI_METADATA_ARVO_MAX_PITUUS = 64;
    public static final int VIESTI_METADATA_ARVOT_MAX_MAARA = 1024;
    public static final int VIESTI_VASTAANOTTAJAT_MAX_MAARA = 512;
    public static final int VIESTI_LIITTEET_MAX_MAARA = 128;
    public static final int VIESTI_ORGANISAATIO_MAX_PITUUS = 64;
    public static final int VIESTI_OIKEUS_MAX_PITUUS = 64;
    public static final int VIESTI_KAYTTOOIKEUS_MAX_MAARA = 128;
    public static final int VIESTI_IDEMPOTENCY_KEY_MAX_PITUUS = 64;
    public static final String VIESTI_IDEMPOTENCY_KEY_SALLITUT_MERKIT = "a-z, A-Z, 0-9 ja -_.";
    public static final String VIESTI_SISALTOTYYPPI_TEXT = "text";
    public static final String VIESTI_SISALTOTYYPPI_HTML = "html";

    public static final String VALIDATION_OTSIKKO_TYHJA = "otsikko: Kenttä on pakollinen";
    public static final String VALIDATION_OTSIKKO_LIIAN_PITKA =
            "otsikko: Otsikko ei voi pidempi kuin " + OTSIKKO_MAX_PITUUS + " merkkiä";

    public static final String VALIDATION_SISALTO_TYHJA = "sisalto: Kenttä on pakollinen";
    public static final String VALIDATION_SISALTO_LIIAN_PITKA =
            "sisalto: Sisältö ei voi pidempi kuin " + SISALTO_MAX_PITUUS + " merkkiä";

    public static final String VALIDATION_SISALLONTYYPPI =
            "sisallonTyyppi: Sisällön tyypin täytyy olla joko \"" + VIESTI_SISALTOTYYPPI_TEXT
                    + "\" tai \"" + VIESTI_SISALTOTYYPPI_HTML + "\"";

    public static final String VALIDATION_KIELI_EI_SALLITTU = "kielet: Kieli ei ole sallittu (\"fi\", \"sv\" ja \"en\"): ";
    public static final String VALIDATION_KIELI_NULL = "kielet: Kenttä sisältää null-arvoja";
    public static final String VALIDATION_KIELI_DUPLICATES = "kielet: Kenttä sisältää duplikaatteja: ";

    public static final String VALIDATION_MASKIT_NULL = "maskit: Kenttä sisältää null-arvoja";
    public static final String VALIDATION_MASKIT_LIIKAA =
            "maskit: Viestillä voi maksimissaan olla " + VIESTI_MASKIT_MAX_MAARA + " maskia";
    public static final String VALIDATION_MASKIT_EI_SALAISUUTTA = "salaisuus-kenttä on pakollinen";
    public static final String VALIDATION_MASKIT_SALAISUUS_PITUUS =
            "salaisuus-kentän sallittu pituus on " + VIESTI_SALAISUUS_MIN_PITUUS + "-" + VIESTI_SALAISUUS_MAX_PITUUS + " merkkiä";
    public static final String VALIDATION_MASKIT_MASKI_PITUUS =
            "maski-kentän sallittu pituus on " + VIESTI_MASKI_MIN_PITUUS + "-" + VIESTI_MASKI_MAX_PITUUS + " merkkiä";
    public static final String VALIDATION_MASKIT_DUPLICATES = "maskit: salaisuus-kentissä on duplikaatteja: ";

    public static final String VALIDATION_VASTAANOTTAJAT_TYHJA = "vastaanottajat: Kenttä on pakollinen";
    public static final String VALIDATION_VASTAANOTTAJAT_LIIKAA =
            "vastaanottajat: Viestillä voi maksimissaan olla " + VIESTI_VASTAANOTTAJAT_MAX_MAARA + " vastaanottajaa";
    public static final String VALIDATION_VASTAANOTTAJA_NULL = "vastaanottajat: Kenttä sisältää null-arvoja";
    public static final String VALIDATION_VASTAANOTTAJA_OSOITE_DUPLICATE = "vastaanottajat: Osoite-kentissä on duplikaatteja: ";
    public static final String VALIDATION_VASTAANOTTAJAN_NIMI_LIIAN_PITKA =
            "nimi-kenttä voi maksimissaan olla " + VIESTI_NIMI_MAX_PITUUS + " merkkiä pitkä";
    public static final String VALIDATION_VASTAANOTTAJAN_OSOITE_TYHJA = "sähköpostiosoite-kenttä on pakollinen";
    public static final String VALIDATION_VASTAANOTTAJAN_OSOITE_LIIAN_PITKA =
            "sähköpostiosoite voi maksimissaan olla " + VIESTI_OSOITE_MAX_PITUUS + " merkkiä pitkä";

    public static final String VALIDATION_LIITETUNNISTE_NULL = "liiteTunnisteet: Kenttä sisältää null-arvoja";
    public static final String VALIDATION_LIITETUNNISTE_LIIKAA =
            "liiteTunnisteet: Viestillä voi maksimissaan olla " + VIESTI_LIITTEET_MAX_MAARA + " liitettä";
    public static final String VALIDATION_LIITETUNNISTE_DUPLICATE = "liiteTunnisteet: Kentässä on duplikaatteja: ";
    public static final String VALIDATION_LIITETUNNISTE_INVALID = "liitetunniste ei ole muodoltaan validi liitetunniste";
    public static final String VALIDATION_LIITETUNNISTE_EI_TARJOLLA =
            "liitetunnistetta vastaavaa liitettä ei ole järjestelmässä tai käyttäjällä ei ole siihen oikeuksia";

    public static final String VALIDATION_LAHETYSTUNNISTE_INVALID =
            "lähetysTunniste: arvo ei ole muodoltaan validi lähetysTunniste";
    public static final String VALIDATION_LAHETYSTUNNISTE_EI_TARJOLLA =
            "lähetysTunniste: tunnistetta ei ole järjestelmässä tai käyttäjällä ei ole siihen oikeuksia";

    public static final String VALIDATION_IDEMPOTENCY_KEY_LIIAN_PITKA =
            "idempotencyKey: Idempotency-avain ei voi pidempi kuin " + VIESTI_IDEMPOTENCY_KEY_MAX_PITUUS + " merkkiä";
    public static final String VALIDATION_IDEMPOTENCY_KEY_INVALID =
            "idempotencyKey: Sallitut merkit ovat " + VIESTI_IDEMPOTENCY_KEY_SALLITUT_MERKIT;

    public static final String VALIDATION_METADATA_NULL = "metadata: Seuraavat avaimet sisältävät null-arvoja: ";
    public static final String VALIDATION_METADATA_DUPLICATE = "metadata: Seuraavat avaimet sisältää duplikaattiarvoja: ";
    public static final String VALIDATION_METADATA_AVAIMET_MAARA =
            "metadata: Metadata voi sisältää maksimissaan " + VIESTI_METADATA_AVAIMET_MAX_MAARA + " avainta";
    public static final String VALIDATION_METADATA_AVAIN_INVALID =
            "avaimessa sallitut merkit ovat " + VIESTI_METADATA_SALLITUT_MERKIT;
    public static final String VALIDATION_METADATA_AVAIN_PITUUS =
            "avain on yli maksimipituuden " + VIESTI_METADATA_AVAIN_MAX_PITUUS + " merkkiä";
    public static final String VALIDATION_METADATA_ARVOT_MAARA =
            "avain sisältää yli " + VIESTI_METADATA_ARVOT_MAX_MAARA + " arvoa";
    public static final String VALIDATION_METADATA_ARVO_INVALID =
            "arvossa sallitut merkit ovat " + VIESTI_METADATA_SALLITUT_MERKIT;
    public static final String VALIDATION_METADATA_ARVO_PITUUS =
            "arvo on yli maksimipituuden " + VIESTI_METADATA_ARVO_MAX_PITUUS + " merkkiä: ";

    public static final String VALIDATION_KAYTTOOIKEUSRAJOITUS_NULL = "kayttooikeusRajoitukset: Kenttä sisältää null-arvoja";
    public static final String VALIDATION_KAYTTOOIKEUSRAJOITUS_LIIKAA =
            "kayttooikeusRajoitukset: Viestillä voi maksimissaan olla " + VIESTI_KAYTTOOIKEUS_MAX_MAARA + " käyttöoikeusrajoitusta";
    public static final String VALIDATION_KAYTTOOIKEUSRAJOITUS_DUPLICATE = "kayttooikeusRajoitukset: Kentässä on duplikaatteja: ";
    public static final String VALIDATION_ORGANISAATIO_INVALID =
            "käyttöoikeusrajoituksen organisaation oidin tulee olla muotoa 1.2.246.562.(10|99).\\d";
    public static final String VALIDATION_ORGANISAATIO_PITUUS =
            "käyttöoikeusrajoituksen organisaatio on yli maksimipituuden " + VIESTI_ORGANISAATIO_MAX_PITUUS + " merkkiä";
    public static final String VALIDATION_OIKEUS_TYHJA = "käyttöoikeusrajoituksen oikeus on tyhjä";
    public static final String VALIDATION_OIKEUS_PITUUS =
            "käyttöoikeusrajoituksen oikeus on yli maksimipituuden " + VIESTI_OIKEUS_MAX_PITUUS + " merkkiä";

    public static final String VALIDATION_LAHETTAVAPALVELU_EI_TYHJA =
            "lahettavapalvelu: Kentän pitää olla tyhjä jos lähetystunniste on määritelty";
    public static final String VALIDATION_VIRKAILIJANOID_EI_TYHJA =
            "lähettävän virkailijan oid: Kentän pitää olla tyhjä jos lähetystunniste on määritelty";
    public static final String VALIDATION_LAHETTAJA_EI_TYHJA =
            "lähettäjä: Kentän pitää olla tyhjä jos lähetystunniste on määritelty";
    public static final String VALIDATION_REPLYTO_EI_TYHJA =
            "replyTo: Kentän pitää olla tyhjä jos lähetystunniste on määritelty";
    public static final String VALIDATION_PRIORITEETTI_EI_TYHJA =
            "prioriteetti: Kentän pitää olla tyhjä jos lähetystunniste on määritelty";
    public static final String VALIDATION_SAILYTYSAIKA_EI_TYHJA =
            "sailytysaika: Kentän pitää olla tyhjä jos lähetystunniste on määritelty";

    public static final String VALIDATION_KORKEA_PRIORITEETTI_VASTAANOTTAJAT =
            "prioriteetti: Korkean prioriteetin viesteillä voi olla vain yksi vastaanottaja";

    public static final String VALIDATION_KOKO =
            "koko: viestin ja liitteiden koko on suurempi kuin " + VIESTI_MAX_SIZE_MB_STR + " megatavua";

    public static final Set<String> SALLITUT_KIELET = Set.of("fi", "sv", "en");

    private static final Pattern METADATA_AVAIN_PATTERN = Pattern.compile("[a-zA-Z0-9\\-\\._]+");
    private static final Pattern METADATA_ARVO_PATTERN = Pattern.compile("[a-zA-Z0-9\\-\\._]+");
    private static final Pattern IDEMPOTENCY_PATTERN = Pattern.compile("[A-Za-z0-9\\-\\._]+");
    private static final Pattern ORGANISAATIO_OID_PATTERN =
            Pattern.compile("1\\.2\\.246\\.562\\.(10|28|99|199|299)\\.\\d+");

    public static Set<String> validateOtsikko(String otsikko) {
        Set<String> virheet = new LinkedHashSet<>();
        if (otsikko == null || otsikko.isEmpty()) {
            virheet.add(VALIDATION_OTSIKKO_TYHJA);
        } else if (otsikko.length() > OTSIKKO_MAX_PITUUS) {
            virheet.add(VALIDATION_OTSIKKO_LIIAN_PITKA);
        }
        return virheet;
    }

    public static Set<String> validateSisalto(String sisalto) {
        Set<String> virheet = new LinkedHashSet<>();
        if (sisalto == null || sisalto.isEmpty()) {
            virheet.add(VALIDATION_SISALTO_TYHJA);
        } else if (sisalto.length() > SISALTO_MAX_PITUUS) {
            virheet.add(VALIDATION_SISALTO_LIIAN_PITKA);
        }
        return virheet;
    }

    public static Set<String> validateSisallonTyyppi(String sisallonTyyppi) {
        Set<String> virheet = new LinkedHashSet<>();
        if (sisallonTyyppi == null
                || (!sisallonTyyppi.equals(VIESTI_SISALTOTYYPPI_TEXT) && !sisallonTyyppi.equals(VIESTI_SISALTOTYYPPI_HTML))) {
            virheet.add(VALIDATION_SISALLONTYYPPI);
        }
        return virheet;
    }

    public static Set<String> validateKielet(List<String> kielet) {
        Set<String> virheet = new LinkedHashSet<>();
        if (kielet == null || kielet.isEmpty()) {
            return virheet;
        }
        for (String kieli : kielet) {
            if (kieli != null && !SALLITUT_KIELET.contains(kieli)) {
                virheet.add(VALIDATION_KIELI_EI_SALLITTU + kieli);
            }
        }
        if (kielet.stream().anyMatch(k -> k == null)) {
            virheet.add(VALIDATION_KIELI_NULL);
        }
        String duplikaatit = duplicatesOf(kielet.stream().filter(k -> k != null).toList());
        if (!duplikaatit.isEmpty()) {
            virheet.add(VALIDATION_KIELI_DUPLICATES + duplikaatit);
        }
        return virheet;
    }

    public static Set<String> validateMaskit(List<Maski> maskit) {
        Set<String> virheet = new LinkedHashSet<>();
        if (maskit == null) {
            return virheet;
        }
        if (maskit.size() > VIESTI_MASKIT_MAX_MAARA) {
            virheet.add(VALIDATION_MASKIT_LIIKAA);
        }
        if (maskit.stream().anyMatch(m -> m == null)) {
            virheet.add(VALIDATION_MASKIT_NULL);
        }
        for (Maski maski : new LinkedHashSet<>(maskit.stream().filter(m -> m != null).toList())) {
            Set<String> maskiVirheet = new LinkedHashSet<>();
            if (maski.salaisuus() == null || maski.salaisuus().isEmpty()) {
                maskiVirheet.add(VALIDATION_MASKIT_EI_SALAISUUTTA);
            } else if (maski.salaisuus().length() < VIESTI_SALAISUUS_MIN_PITUUS
                    || maski.salaisuus().length() > VIESTI_SALAISUUS_MAX_PITUUS) {
                maskiVirheet.add(VALIDATION_MASKIT_SALAISUUS_PITUUS);
            }
            if (maski.maski() != null
                    && (maski.maski().length() < VIESTI_MASKI_MIN_PITUUS || maski.maski().length() > VIESTI_MASKI_MAX_PITUUS)) {
                maskiVirheet.add(VALIDATION_MASKIT_MASKI_PITUUS);
            }
            if (!maskiVirheet.isEmpty()) {
                String salaisuusMasked = maski.salaisuus() == null ? "" : "*".repeat(maski.salaisuus().length());
                String maskiArvo = maski.maski() == null ? "" : maski.maski();
                virheet.add("Maski (salaisuus: " + salaisuusMasked + ", maski: " + maskiArvo + "): " + join(maskiVirheet));
            }
        }
        String duplikaatit = maskit.stream()
                .filter(m -> m != null && m.salaisuus() != null)
                .collect(Collectors.groupingBy(Maski::salaisuus))
                .entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(e -> "*".repeat(e.getKey().length()))
                .collect(Collectors.joining(","));
        if (!duplikaatit.isEmpty()) {
            virheet.add(VALIDATION_MASKIT_DUPLICATES + duplikaatit);
        }
        return virheet;
    }

    public static Set<String> validateVastaanottajat(List<Kontakti> vastaanottajat) {
        Set<String> virheet = new LinkedHashSet<>();
        if (vastaanottajat == null || vastaanottajat.isEmpty()) {
            virheet.add(VALIDATION_VASTAANOTTAJAT_TYHJA);
            return virheet;
        }
        if (vastaanottajat.size() > VIESTI_VASTAANOTTAJAT_MAX_MAARA) {
            virheet.add(VALIDATION_VASTAANOTTAJAT_LIIKAA);
        }
        if (vastaanottajat.stream().anyMatch(v -> v == null)) {
            virheet.add(VALIDATION_VASTAANOTTAJA_NULL);
        }
        for (Kontakti vastaanottaja : new LinkedHashSet<>(vastaanottajat.stream().filter(v -> v != null).toList())) {
            Set<String> vastaanottajaVirheet = new LinkedHashSet<>();
            if (vastaanottaja.nimi() != null && vastaanottaja.nimi().length() > VIESTI_NIMI_MAX_PITUUS) {
                vastaanottajaVirheet.add(VALIDATION_VASTAANOTTAJAN_NIMI_LIIAN_PITKA);
            }
            String osoite = vastaanottaja.sahkopostiOsoite();
            if (osoite == null || osoite.isEmpty()) {
                vastaanottajaVirheet.add(VALIDATION_VASTAANOTTAJAN_OSOITE_TYHJA);
            } else if (osoite.length() > VIESTI_OSOITE_MAX_PITUUS) {
                vastaanottajaVirheet.add(VALIDATION_VASTAANOTTAJAN_OSOITE_LIIAN_PITKA);
            }
            if (!vastaanottajaVirheet.isEmpty()) {
                String nimi = vastaanottaja.nimi() == null ? "" : vastaanottaja.nimi();
                virheet.add("Vastaanottaja (nimi: " + nimi + ", sähköpostiosoite: "
                        + Optional.ofNullable(osoite) + "): " + join(vastaanottajaVirheet));
            }
        }
        String duplikaatit = vastaanottajat.stream()
                .filter(v -> v != null && v.sahkopostiOsoite() != null)
                .collect(Collectors.groupingBy(Kontakti::sahkopostiOsoite))
                .entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(","));
        if (!duplikaatit.isEmpty()) {
            virheet.add(VALIDATION_VASTAANOTTAJA_OSOITE_DUPLICATE + duplikaatit);
        }
        return virheet;
    }

    public static Set<String> validateLiitteidenTunnisteet(
            List<String> tunnisteet, Map<UUID, LiiteMetadata> liiteMetadatat, String identiteetti) {
        Set<String> virheet = new LinkedHashSet<>();
        if (tunnisteet == null) {
            return virheet;
        }
        if (tunnisteet.size() > VIESTI_LIITTEET_MAX_MAARA) {
            virheet.add(VALIDATION_LIITETUNNISTE_LIIKAA);
        }
        if (tunnisteet.stream().anyMatch(t -> t == null)) {
            virheet.add(VALIDATION_LIITETUNNISTE_NULL);
        }
        for (String tunniste : new LinkedHashSet<>(tunnisteet.stream().filter(t -> t != null).toList())) {
            Set<String> tunnisteVirheet = new LinkedHashSet<>();
            Optional<UUID> uuid = ParametriUtil.asUUID(tunniste);
            if (uuid.isEmpty()) {
                tunnisteVirheet.add(VALIDATION_LIITETUNNISTE_INVALID);
            } else {
                LiiteMetadata metadata = liiteMetadatat.get(uuid.get());
                if (metadata == null || !identiteetti.equals(metadata.omistaja())) {
                    tunnisteVirheet.add(VALIDATION_LIITETUNNISTE_EI_TARJOLLA);
                }
            }
            if (!tunnisteVirheet.isEmpty()) {
                virheet.add("Liitetunniste \"" + tunniste + "\": " + join(tunnisteVirheet));
            }
        }
        String duplikaatit = duplicatesOf(tunnisteet.stream().filter(t -> t != null).toList());
        if (!duplikaatit.isEmpty()) {
            virheet.add(VALIDATION_LIITETUNNISTE_DUPLICATE + duplikaatit);
        }
        return virheet;
    }

    public static Set<String> validateLahetysTunniste(
            String tunniste, Optional<LahetysMetadata> lahetysMetadata, String identiteetti) {
        Set<String> virheet = new LinkedHashSet<>();
        if (tunniste == null || tunniste.isEmpty()) {
            return virheet;
        }
        if (ParametriUtil.asUUID(tunniste).isEmpty()) {
            virheet.add(VALIDATION_LAHETYSTUNNISTE_INVALID);
            return virheet;
        }
        if (lahetysMetadata.isEmpty() || !lahetysMetadata.get().omistaja().equals(identiteetti)) {
            virheet.add(VALIDATION_LAHETYSTUNNISTE_EI_TARJOLLA);
        }
        return virheet;
    }

    public static Set<String> validateMetadata(Map<String, List<String>> metadata) {
        Set<String> virheet = new LinkedHashSet<>();
        if (metadata == null) {
            return virheet;
        }
        String nullArvot = metadata.entrySet().stream()
                .filter(e -> e.getValue() == null || e.getValue().stream().anyMatch(a -> a == null))
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(","));
        if (!nullArvot.isEmpty()) {
            virheet.add(VALIDATION_METADATA_NULL + nullArvot);
        }
        String duplikaattiArvot = metadata.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue().size() > new LinkedHashSet<>(e.getValue()).size())
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(","));
        if (!duplikaattiArvot.isEmpty()) {
            virheet.add(VALIDATION_METADATA_DUPLICATE + duplikaattiArvot);
        }
        if (metadata.size() > VIESTI_METADATA_ARVOT_MAX_MAARA) {
            virheet.add(VALIDATION_METADATA_ARVOT_MAARA);
        }
        for (Map.Entry<String, List<String>> entry : metadata.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            String avain = entry.getKey();
            List<String> arvot = entry.getValue();
            Set<String> avainVirheet = new LinkedHashSet<>();
            if (!METADATA_AVAIN_PATTERN.matcher(avain).matches()) {
                avainVirheet.add(VALIDATION_METADATA_AVAIN_INVALID);
            }
            if (avain.length() > VIESTI_METADATA_AVAIN_MAX_PITUUS) {
                avainVirheet.add(VALIDATION_METADATA_AVAIN_PITUUS);
            }
            if (arvot.size() > VIESTI_METADATA_ARVOT_MAX_MAARA) {
                avainVirheet.add(VALIDATION_METADATA_ARVOT_MAARA);
            }
            for (String arvo : arvot) {
                if (arvo != null && arvo.length() > VIESTI_METADATA_ARVO_MAX_PITUUS) {
                    avainVirheet.add(VALIDATION_METADATA_ARVO_PITUUS + arvo);
                }
                if (arvo != null && !METADATA_ARVO_PATTERN.matcher(arvo).matches()) {
                    avainVirheet.add(VALIDATION_METADATA_ARVO_INVALID + arvo);
                }
            }
            if (!avainVirheet.isEmpty()) {
                virheet.add("Metadata \"" + avain + "\": " + join(avainVirheet));
            }
        }
        return virheet;
    }

    public static Set<String> validateKayttooikeusRajoitukset(List<Kayttooikeus> kayttooikeusRajoitukset) {
        Set<String> virheet = new LinkedHashSet<>();
        if (kayttooikeusRajoitukset == null) {
            return virheet;
        }
        if (kayttooikeusRajoitukset.size() > VIESTI_KAYTTOOIKEUS_MAX_MAARA) {
            virheet.add(VALIDATION_KAYTTOOIKEUSRAJOITUS_LIIKAA);
        }
        if (kayttooikeusRajoitukset.stream().anyMatch(r -> r == null)) {
            virheet.add(VALIDATION_KAYTTOOIKEUSRAJOITUS_NULL);
        }
        for (Kayttooikeus rajoitus : new LinkedHashSet<>(kayttooikeusRajoitukset.stream().filter(r -> r != null).toList())) {
            Set<String> rajoitusVirheet = new LinkedHashSet<>();
            if (rajoitus.organisaatio() == null || !ORGANISAATIO_OID_PATTERN.matcher(rajoitus.organisaatio()).matches()) {
                rajoitusVirheet.add(VALIDATION_ORGANISAATIO_INVALID);
            }
            if (rajoitus.organisaatio() != null && rajoitus.organisaatio().length() > VIESTI_ORGANISAATIO_MAX_PITUUS) {
                rajoitusVirheet.add(VALIDATION_ORGANISAATIO_PITUUS);
            }
            if (rajoitus.oikeus() == null || rajoitus.oikeus().isEmpty()) {
                rajoitusVirheet.add(VALIDATION_OIKEUS_TYHJA);
            }
            if (rajoitus.oikeus() != null && rajoitus.oikeus().length() > VIESTI_OIKEUS_MAX_PITUUS) {
                rajoitusVirheet.add(VALIDATION_OIKEUS_PITUUS);
            }
            if (!rajoitusVirheet.isEmpty()) {
                String org = rajoitus.organisaatio() == null ? "\"\"" : rajoitus.organisaatio();
                String oikeus = rajoitus.oikeus() == null ? "\"\"" : rajoitus.oikeus();
                virheet.add("Käyttöoikeusrajoitus (" + org + "," + oikeus + "): " + join(rajoitusVirheet));
            }
        }
        String duplikaatit = kayttooikeusRajoitukset.stream()
                .filter(r -> r != null)
                .collect(Collectors.groupingBy(r -> r))
                .entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(e -> e.getKey().toString())
                .collect(Collectors.joining(","));
        if (!duplikaatit.isEmpty()) {
            virheet.add(VALIDATION_KAYTTOOIKEUSRAJOITUS_DUPLICATE + duplikaatit);
        }
        return virheet;
    }

    public static Set<String> validateIdempotencyKey(String idempotencyKey) {
        Set<String> virheet = new LinkedHashSet<>();
        if (idempotencyKey == null) {
            return virheet;
        }
        if (idempotencyKey.length() > VIESTI_IDEMPOTENCY_KEY_MAX_PITUUS) {
            virheet.add(VALIDATION_IDEMPOTENCY_KEY_LIIAN_PITKA);
        }
        if (!IDEMPOTENCY_PATTERN.matcher(idempotencyKey).matches()) {
            virheet.add(VALIDATION_IDEMPOTENCY_KEY_INVALID);
        }
        return virheet;
    }

    public static Set<String> validateLahetysJaPeritytKentat(
            String lahetysTunniste, String lahettavaPalvelu, String lahettavanVirkailijanOid,
            Kontakti lahettaja, String replyTo, String prioriteetti, Integer sailytysaika) {
        boolean lahetysMaaritelty = lahetysTunniste != null && !lahetysTunniste.isEmpty();
        Set<String> virheet = new LinkedHashSet<>();
        if (lahetysMaaritelty) {
            // lähetyksen kenttiä ei voi määritellä viestikohtaisesti jos ne peritään lähetykseltä;
            // myös kentän sisältö validoidaan vaikka kenttä ei saa olla määritelty
            if (lahettavaPalvelu != null) {
                virheet.add(VALIDATION_LAHETTAVAPALVELU_EI_TYHJA);
                virheet.addAll(LahetysValidator.validateLahettavaPalvelu(lahettavaPalvelu));
            }
            if (lahettavanVirkailijanOid != null) {
                virheet.add(VALIDATION_VIRKAILIJANOID_EI_TYHJA);
                virheet.addAll(LahetysValidator.validateLahettavanVirkailijanOID(lahettavanVirkailijanOid));
            }
            if (lahettaja != null) {
                virheet.add(VALIDATION_LAHETTAJA_EI_TYHJA);
                virheet.addAll(LahetysValidator.validateLahettaja(lahettaja));
            }
            if (replyTo != null) {
                virheet.add(VALIDATION_REPLYTO_EI_TYHJA);
                virheet.addAll(LahetysValidator.validateReplyTo(replyTo));
            }
            if (prioriteetti != null) {
                virheet.add(VALIDATION_PRIORITEETTI_EI_TYHJA);
                virheet.addAll(LahetysValidator.validatePrioriteetti(prioriteetti));
            }
            if (sailytysaika != null) {
                virheet.add(VALIDATION_SAILYTYSAIKA_EI_TYHJA);
                virheet.addAll(LahetysValidator.validateSailytysAika(sailytysaika));
            }
            return virheet;
        }
        // jos lähetys ei määritelty kentät validoidaan kuten ne olisivat lähetyksessä
        return LahetysValidator.validateLahetys(new fi.vm.sade.viestinvalitys.dto.LuoLahetysRequest(
                "DUMMY OTSIKKO", lahettavaPalvelu, lahettavanVirkailijanOid, lahettaja, replyTo, prioriteetti, sailytysaika));
    }

    public static Set<String> validateKorkeaPrioriteetti(
            String prioriteetti, List<Kontakti> vastaanottajat, Optional<LahetysMetadata> lahetysMetadata) {
        boolean korkeaPrioriteetti = (lahetysMetadata.isPresent() && lahetysMetadata.get().korkeaPrioriteetti())
                || (prioriteetti != null && prioriteetti.equals(LahetysValidator.LAHETYS_PRIORITEETTI_KORKEA));
        if (!korkeaPrioriteetti) {
            return new LinkedHashSet<>();
        }
        if (vastaanottajat != null && vastaanottajat.size() > 1) {
            return new LinkedHashSet<>(Set.of(VALIDATION_KORKEA_PRIORITEETTI_VASTAANOTTAJAT));
        }
        return new LinkedHashSet<>();
    }

    public static Set<String> validateKoko(
            String sisalto, List<String> liiteTunnisteet, Map<UUID, LiiteMetadata> liiteMetadatat, String identiteetti) {
        long liitteidenKoko = new LinkedHashSet<>(liiteTunnisteet).stream()
                .map(tunniste -> liiteMetadatat.get(UUID.fromString(tunniste)))
                .filter(metadata -> metadata != null && metadata.omistaja().equals(identiteetti))
                .mapToLong(LiiteMetadata::koko)
                .sum();
        Set<String> virheet = new LinkedHashSet<>();
        if (sisalto.length() + liitteidenKoko > VIESTI_MAX_SIZE) {
            virheet.add(VALIDATION_KOKO);
        }
        return virheet;
    }

    public static List<String> validateViesti(
            LuoViestiRequest viesti, Optional<LahetysMetadata> lahetysMetadata,
            Map<UUID, LiiteMetadata> liiteMetadatat, String identiteetti) {
        List<String> virheet = new ArrayList<>();
        virheet.addAll(validateOtsikko(viesti.otsikko()));
        virheet.addAll(validateSisalto(viesti.sisalto()));
        virheet.addAll(validateSisallonTyyppi(viesti.sisallonTyyppi()));
        virheet.addAll(validateKielet(viesti.kielet()));
        virheet.addAll(validateMaskit(viesti.maskit()));
        virheet.addAll(validateVastaanottajat(viesti.vastaanottajat()));
        virheet.addAll(validateLiitteidenTunnisteet(viesti.liitteidenTunnisteet(), liiteMetadatat, identiteetti));
        virheet.addAll(validateLahetysTunniste(viesti.lahetysTunniste(), lahetysMetadata, identiteetti));
        virheet.addAll(validateMetadata(viesti.metadata()));
        virheet.addAll(validateKayttooikeusRajoitukset(viesti.kayttooikeusRajoitukset()));
        virheet.addAll(validateKorkeaPrioriteetti(viesti.prioriteetti(), viesti.vastaanottajat(), lahetysMetadata));
        virheet.addAll(validateLahetysJaPeritytKentat(viesti.lahetysTunniste(), viesti.lahettavaPalvelu(),
                viesti.lahettavanVirkailijanOid(), viesti.lahettaja(), viesti.replyTo(), viesti.prioriteetti(),
                viesti.sailytysaika()));
        return virheet;
    }

    private static String duplicatesOf(List<String> arvot) {
        return arvot.stream()
                .collect(Collectors.groupingBy(a -> a, LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(","));
    }

    private static String join(Set<String> virheet) {
        return String.join(",", virheet);
    }
}
