package fi.vm.sade.viestinvalitys.local;

import fi.vm.sade.viestinvalitys.service.LahetysWriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Based on LocalUtil.setupLocal(). Functionality was replicated 2026-07-10.
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalDataInitializer implements ApplicationRunner {

    /** Käyttöoikeus that the OPH pääkäyttäjä / test users see. */
    private static final Kayttooikeus PAAKAYTTAJA_OIKEUS =
            new Kayttooikeus("APP_OIKEUS", "1.2.246.562.10.240484683010");

    private static final String OMISTAJA = "omistaja";
    private static final int SAILYTYSAIKA = 365;
    private static final Map<String, List<String>> METADATA = Map.of("avain", List.of("arvo"));

    private final JdbcTemplate jdbc;
    private final LahetysWriteService writeService;

    private record Kontakti(String nimi, String sahkoposti) {}

    private record Kayttooikeus(String oikeus, String organisaatio) {}

    @Override
    public void run(ApplicationArguments args) {
        Integer lahetyksia = jdbc.queryForObject("SELECT count(*) FROM lahetykset", Integer.class);
        if (lahetyksia != null && lahetyksia >= 3) {
            log.info("Local test data has already been initialized ({} lahetys count), skipping.", lahetyksia);
            return;
        }

        log.info("Initializing local test data");
        seedMassaviestit();
        seedRaataloidytViestit("Räätälöidyn plain text -massaviestin", "1.2.246.562.24.2", false);
        seedRaataloidytViestit("Räätälöidyn html-massaviestin", "1.2.246.562.24.1", true);
        seedYksittaisetViestit();
        log.info("Local test data initialized");
    }

    /** Mass messages (massaviesti) where the same message has several recipients. */
    private void seedMassaviestit() {
        IntStream.range(0, 350).forEach(counter -> {
            UUID lahetys = tallennaLahetys(
                    "Massalähetysotsikko " + counter,
                    "hakemuspalvelu",
                    "1.2.246.562.24.1",
                    new Kontakti("Joku Virkailija", "hakemuspalvelu@opintopolku.fi"),
                    "noreply@opintopolku.fi");
            tallennaViesti(
                    "Massaviestin " + counter + " testiotsikko",
                    "Massaviestin sisältö",
                    "TEXT",
                    Set.of("fi"),
                    Map.of(),
                    null, null, null,
                    IntStream.range(0, 20)
                            .mapToObj(suffix -> new Kontakti("Joku Vastaanottaja" + suffix, "vastaanottaja" + suffix + "@example.com"))
                            .toList(),
                    "hakemuspalvelu",
                    lahetys,
                    "NORMAALI",
                    Set.of(PAAKAYTTAJA_OIKEUS));
        });
    }

    /** Lähetys items with a customized viesti (plain text or html) for several recipients (vastaanottaja). */
    private void seedRaataloidytViestit(String otsikkoPrefix, String oid, boolean html) {
        IntStream.range(0, 6).forEach(counter -> {
            UUID lahetys = tallennaLahetys(
                    otsikkoPrefix + " " + counter + " testiotsikko",
                    "hakemuspalvelu",
                    oid,
                    new Kontakti("Testi Virkailija" + counter, "noreply@opintopolku.fi"),
                    "noreply@opintopolku.fi");
            // customized messages (viesti) with a lähetys identifier (lähetystunnus), one recipient per message (viesti)
            IntStream.range(0, 25).forEach(viestinro -> {
                String sisalto = html
                        ? "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\"><html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" /><title></title></head><body style=\"margin: 0; font-family: 'Open Sans', Arial, sans-serif;\"><H1>Otsikko</h1><p>Viestin sisältö " + viestinro + "</p><p>Ystävällisin terveisin<br/>Opintopolku</p></body></html>"
                        : "Tässä on viesti " + viestinro + " joka ei sisällä html:ää.";
                List<UUID> vastaanottajat = tallennaViesti(
                        "Viestin testiotsikko " + viestinro,
                        sisalto,
                        html ? "HTML" : "TEXT",
                        Set.of("fi"),
                        Map.of(),
                        null, null, null,
                        List.of(new Kontakti("Testi Vastaanottaja " + viestinro, "testi.vastaanottaja" + viestinro + "@example.com")),
                        "hakemuspalvelu",
                        lahetys,
                        "NORMAALI",
                        Set.of(PAAKAYTTAJA_OIKEUS));
                UUID vastaanottaja = vastaanottajat.get(0);
                if (counter == 1) {
                    paivitaVastaanottajaLahetetyksi(vastaanottaja, "ses-tunniste");
                    paivitaVastaanotonTila("ses-tunniste", "DELIVERY", null);
                } else {
                    if (viestinro <= 10) {
                        paivitaVastaanottajaLahetetyksi(vastaanottaja, "ses-tunniste");
                        paivitaVastaanotonTila("ses-tunniste", "DELIVERY", null);
                    }
                    if (viestinro > 10 && viestinro < 15) {
                        paivitaVastaanottajaVirhetilaan(vastaanottaja, "lisätiedot virheestä");
                    }
                }
            });
        });
    }

    /** Individual messages that do not have a separate lähetys identifier (lähetystunnus). */
    private void seedYksittaisetViestit() {
        // empty (orphan) lähetys that has no messages
        tallennaLahetys(
                "Orpo lähetys",
                "osoitepalvelu",
                "0.1.2.3",
                new Kontakti("Testi Virkailija", "osoitepalvelu@opintopolku.fi"),
                "noreply@opintopolku.fi");

        // one viesti with a few recipients, created without a lähetys identifier (lähetystunnus)
        tallennaViesti(
                "Yksittäinen viesti",
                "Tämä on yksittäinen viesti muutamalla vastaanottajalla",
                "TEXT",
                Set.of("fi"),
                Map.of(),
                "1.2.246.562.24.1",
                new Kontakti("Testi Virkailija", "testipalvelu@opintopolku.fi"),
                "noreply@opintopolku.fi",
                IntStream.range(0, 3)
                        .mapToObj(suffix -> new Kontakti("Testi Vastaanottaja" + suffix, "testi.vastaanottaja" + suffix + "@example.com"))
                        .toList(),
                "testipalvelu",
                null,
                "NORMAALI",
                Set.of(PAAKAYTTAJA_OIKEUS));

        // Swedish-language html message
        tallennaViesti(
                "Studieinfo för administratörer: användarrättigheter utgår inom kort",
                "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\"><html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" /><title></title></head><body style=\"margin: 0; font-family: 'Open Sans', Arial, sans-serif;\"><H1>Studieinfo för administratörer: användarrättigheter utgår inom kort</H1><p>Hej,</p><p>Dina användarrättigheter till följande tjänster i Studieinfo förfaller inom kort (förfallodag inom parentes): PROD ePerusteet_TOTSU_pääkäyttäjä (25.4.2024) Logga in i Studieinfo och anhåll om fortsatt tid via dina egna uppgifter (ditt namn uppe till höger). Du kan fortsätta till tjänsten via länken: <a href=\"https://virkailija.testiopintopolku.fi/henkilo-ui/omattiedot\">https://virkailija.testiopintopolku.fi/henkilo-ui/omattiedot</a></p></body></html>",
                "HTML",
                Set.of("sv"),
                Map.of(),
                "0.1.2.3",
                new Kontakti("Testi Virkailija", "testipalvelu@opintopolku.fi"),
                "noreply@opintopolku.fi",
                List.of(new Kontakti("Ruotsinkielinen Vastaanottaja", "ruotsi.vastaanottaja@example.com")),
                "testipalvelu",
                null,
                "NORMAALI",
                Set.of(PAAKAYTTAJA_OIKEUS));

        // html message, masked subject and message content (viestin otsikko, viestin sisältö)
        tallennaViesti(
                "Opintopolku: hakemuksesi on vastaanotettu (Hakemusnumero: 1.2.246.562.11.00000000000002065719)",
                "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\"><html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" /><title></title></head><body style=\"margin: 0; font-family: 'Open Sans', Arial, sans-serif;\"><H1>Hakemuksesi on vastaanotettu</H1><p>Hakemusnumero: 1.2.246.562.11.00000000000002065719</p><p><a href=\"https://testiopintopolku.fi/hakemus?modify=viY1D2_yrej_gDBlzDGbeCMyaCvW1BO8dYwcPT6sTN1drA\">https://testiopintopolku.fi/hakemus?modify=viY1D2_yrej_gDBlzDGbeCMyaCvW1BO8dYwcPT6sTN1drA</a>  Voit katsella ja muokata hakemustasi yllä olevan linkin kautta. Älä jaa linkkiä ulkopuolisille. Jos käytät yhteiskäyttöistä tietokonetta, muista kirjautua ulos sähköpostiohjelmasta.</p>"
                        + "<p>Jos sinulla on verkkopankkitunnukset, mobiilivarmenne tai sähköinen henkilökortti, voit vaihtoehtoisesti kirjautua sisään <a href=\"https://www.opintopolku.fi/\">Opintopolku.fi</a>:ssä, ja tehdä muutoksia hakemukseesi Oma Opintopolku -palvelussa hakuaikana. Oma Opintopolku -palvelussa voit lisäksi nähdä valintojen tulokset ja ottaa opiskelupaikan vastaan.</p>"
                        + "<p>Älä vastaa tähän viestiin - viesti on lähetetty automaattisesti.</p>"
                        + "<p>Ystävällisin terveisin<br/>Opintopolku</p></body></html>",
                "HTML",
                Set.of("fi"),
                Map.of(
                        "1.2.246.562.11.00000000000002065719", "[salattu]",
                        "https://testiopintopolku.fi/hakemus?modify=viY1D2_yrej_gDBlzDGbeCMyaCvW1BO8dYwcPT6sTN1drA", "salattuosoite"),
                "0.1.2.3",
                new Kontakti("Testi Virkailija", "testipalvelu@opintopolku.fi"),
                "noreply@opintopolku.fi",
                List.of(new Kontakti("Hakija Vastaanottaja", "hakija.vastaanottaja@example.com")),
                "testipalvelu",
                null,
                "NORMAALI",
                Set.of(PAAKAYTTAJA_OIKEUS));

        // message (viesti) for verifying the access-rights hierarchy (käyttöoikeushierarkia) (different organization)
        tallennaViesti(
                "Kuopio yhteiskunta- ja kauppatieteet viesti",
                "Tämä on viesti käyttöoikeushierarkian todentamiseen",
                "TEXT",
                Set.of("fi"),
                Map.of(),
                "0.1.2.3",
                new Kontakti("Testi Virkailija", "hakemuspalvelu@opintopolku.fi"),
                "noreply@opintopolku.fi",
                IntStream.range(0, 3)
                        .mapToObj(suffix -> new Kontakti("Testi Vastaanottaja" + suffix, "testi.vastaanottaja" + suffix + "@example.com"))
                        .toList(),
                "hakemuspalvelu",
                null,
                "NORMAALI",
                Set.of(new Kayttooikeus("APP_HAKEMUS_CRUD", "1.2.246.562.10.2014041814455745619200")));

        // message (viesti) without an organization restriction
        tallennaViesti(
                "Viesti ilman organisaatiota",
                "Tämä on viesti käyttöoikeustarkistuksen todentamiseen ilman organisaatiorajausta",
                "TEXT",
                Set.of("fi"),
                Map.of(),
                "0.1.2.3",
                new Kontakti("Testi Virkailija", "hakemuspalvelu@opintopolku.fi"),
                "noreply@opintopolku.fi",
                IntStream.range(0, 3)
                        .mapToObj(suffix -> new Kontakti("Testi Vastaanottaja" + suffix, "testi.vastaanottaja" + suffix + "@example.com"))
                        .toList(),
                "hakemuspalvelu",
                null,
                "NORMAALI",
                Set.of(new Kayttooikeus("APP_OIKEUS", null)));
    }

    // --- Persistence delegated to LahetysWriteService ---------------------------------------
    // Thin wrappers keep the seeding call sites unchanged and supply the seeding-specific
    // defaults (owner, retention, metadata) while the actual SQL lives in the shared service.

    private UUID tallennaLahetys(String otsikko, String lahettavaPalvelu, String lahettavanVirkailijanOID,
                                 Kontakti lahettaja, String replyTo) {
        return writeService.tallennaLahetys(otsikko, lahettavaPalvelu, lahettavanVirkailijanOID,
                kontakti(lahettaja), replyTo, "NORMAALI", OMISTAJA, SAILYTYSAIKA);
    }

    /**
     * Saves the message (viesti), its access rights, metadata, masks and recipients. If a lähetys identifier (lähetystunnus)
     * is not given, a dedicated lähetys is created for the viesti (like {@code KantaOperaatiot.tallennaViesti}).
     *
     * @return identifiers of the saved recipients (vastaanottaja)
     */
    private List<UUID> tallennaViesti(String otsikko, String sisalto, String sisallonTyyppi, Set<String> kielet,
                                      Map<String, String> maskit, String lahettavanVirkailijanOID, Kontakti lahettaja,
                                      String replyTo, List<Kontakti> vastaanottajat, String lahettavaPalvelu,
                                      UUID lahetysTunniste, String prioriteetti, Set<Kayttooikeus> kayttooikeusRajoitukset) {
        return writeService.tallennaViesti(otsikko, sisalto, sisallonTyyppi, kielet, maskit, lahettavanVirkailijanOID,
                kontakti(lahettaja), replyTo,
                vastaanottajat.stream().map(LocalDataInitializer::kontakti).toList(),
                lahettavaPalvelu, lahetysTunniste, prioriteetti,
                kayttooikeusRajoitukset.stream()
                        .map(k -> new LahetysWriteService.Kayttooikeus(k.oikeus(), k.organisaatio()))
                        .collect(java.util.stream.Collectors.toSet()),
                METADATA, OMISTAJA, SAILYTYSAIKA, null).vastaanottajaTunnisteet();
    }

    private static LahetysWriteService.Kontakti kontakti(Kontakti kontakti) {
        return kontakti == null ? null : new LahetysWriteService.Kontakti(kontakti.nimi(), kontakti.sahkoposti());
    }

    private void paivitaVastaanottajaLahetetyksi(UUID tunniste, String sesTunniste) {
        jdbc.update("UPDATE vastaanottajat SET tila = 'LAHETETTY', ses_tunniste = ? WHERE tunniste = ?::uuid",
                sesTunniste, tunniste.toString());
        writeService.lisaaVastaanottajanSiirtyma(tunniste, "LAHETETTY", null);
    }

    private void paivitaVastaanottajaVirhetilaan(UUID tunniste, String lisatiedot) {
        jdbc.update("UPDATE vastaanottajat SET tila = 'VIRHE' WHERE tunniste = ?::uuid", tunniste.toString());
        writeService.lisaaVastaanottajanSiirtyma(tunniste, "VIRHE", lisatiedot);
    }

    private void paivitaVastaanotonTila(String sesTunniste, String tila, String lisatiedot) {
        List<String> tunnisteet = jdbc.queryForList(
                "UPDATE vastaanottajat SET tila = ? WHERE ses_tunniste = ? RETURNING tunniste",
                String.class, tila, sesTunniste);
        for (String tunniste : tunnisteet) {
            jdbc.update("INSERT INTO vastaanottaja_siirtymat (vastaanottaja_tunniste, aika, tila, lisatiedot) "
                            + "VALUES (?::uuid, now(), ?, ?)",
                    tunniste, tila, lisatiedot);
        }
    }
}
