package fi.vm.sade.viestinvalitys.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Persistoi lähetykset ja viestit (vastaanottajineen, käyttöoikeuksineen, metatietoineen ja
 * maskeineen). Portattu {@code shared}-moduulin Slick-pohjaisesta
 * {@code KantaOperaatiot.tallennaLahetys}/{@code tallennaViesti}-logiikasta {@code JdbcTemplate}-muotoon
 * (SQL on nostettu {@link fi.vm.sade.viestinvalitys.local.LocalDataInitializer}-luokasta jotta sekä
 * paikallinen testidata-alustus että tuleva vastaanotto-rajapinta käyttävät samaa toteutusta).
 *
 * <p>Toistaiseksi kaikki vastaanottajat aloittavat tilassa {@code ODOTTAA}; liitteet (jotka toisivat
 * {@code SKANNAUS}-tilan), idempotency-avaimet ja korkean prioriteetin ratelimitointi tulevat
 * myöhemmissä vaiheissa (ks. vastaanotto-migration.md).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LahetysWriteService {

    private final JdbcTemplate jdbc;

    /** Lähettäjän tai vastaanottajan yhteystieto persistointia varten. */
    public record Kontakti(String nimi, String sahkoposti) {}

    /** Käyttöoikeusrajoitus (oikeus + organisaatio) persistointia varten. */
    public record Kayttooikeus(String oikeus, String organisaatio) {}

    /** Tallennetun viestin tunnisteet. */
    public record TallennettuViesti(UUID viestiTunniste, UUID lahetysTunniste, List<UUID> vastaanottajaTunnisteet) {}

    @Transactional
    public UUID tallennaLahetys(String otsikko, String lahettavaPalvelu, String lahettavanVirkailijanOID,
                                Kontakti lahettaja, String replyTo, String prioriteetti, String omistaja, int sailytysaika) {
        UUID tunniste = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lahetykset (tunniste, otsikko, lahettavapalvelu, lahettavanvirkailijanoid, lahettajannimi, "
                        + "lahettajansahkoposti, replyto, prioriteetti, omistaja, luotu, poistettava) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?::prioriteetti, ?, now(), now() + (? * interval '1 day'))",
                tunniste.toString(), otsikko, lahettavaPalvelu, lahettavanVirkailijanOID, lahettaja.nimi(),
                lahettaja.sahkoposti(), replyTo, prioriteetti, omistaja, sailytysaika);
        return tunniste;
    }

    /**
     * Tallentaa viestin, sen käyttöoikeudet, metatiedot, maskit ja vastaanottajat. Jos lähetystunnistetta
     * ei anneta, viestille luodaan oma lähetys (kuten {@code KantaOperaatiot.tallennaViesti}). Jos
     * lähetystunniste on annettu, lähetyksen kentät (prioriteetti, lähettävä palvelu, virkailijan oid)
     * ovat ensisijaisia.
     */
    @Transactional
    public TallennettuViesti tallennaViesti(String otsikko, String sisalto, String sisallonTyyppi, Set<String> kielet,
                                            Map<String, String> maskit, String lahettavanVirkailijanOID, Kontakti lahettaja,
                                            String replyTo, List<Kontakti> vastaanottajat, String lahettavaPalvelu,
                                            UUID lahetysTunniste, String prioriteetti, Set<Kayttooikeus> kayttooikeusRajoitukset,
                                            Map<String, List<String>> metadata, String omistaja, int sailytysaika) {
        UUID viestiTunniste = UUID.randomUUID();
        UUID finalLahetysTunniste = lahetysTunniste != null ? lahetysTunniste : viestiTunniste;

        // fields on the lähetys are authoritative if the viesti is attached to an existing lähetys
        String finalPrioriteetti = prioriteetti;
        String finalLahettavaPalvelu = lahettavaPalvelu;
        String finalLahettajaOid = lahettavanVirkailijanOID;
        if (lahetysTunniste == null) {
            // create a lähetys if the viesti has no existing lähetys
            jdbc.update(
                    "INSERT INTO lahetykset (tunniste, otsikko, lahettavapalvelu, lahettavanvirkailijanoid, lahettajannimi, "
                            + "lahettajansahkoposti, replyto, prioriteetti, omistaja, luotu, poistettava) "
                            + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?::prioriteetti, ?, now(), now() + (? * interval '1 day'))",
                    finalLahetysTunniste.toString(), otsikko, lahettavaPalvelu, lahettavanVirkailijanOID,
                    lahettaja != null ? lahettaja.nimi() : null, lahettaja != null ? lahettaja.sahkoposti() : null,
                    replyTo, prioriteetti, omistaja, sailytysaika);
        } else {
            var lahetys = jdbc.queryForMap(
                    "SELECT lahettavapalvelu, lahettavanvirkailijanoid, prioriteetti::text AS prioriteetti "
                            + "FROM lahetykset WHERE tunniste = ?::uuid",
                    finalLahetysTunniste.toString());
            finalPrioriteetti = (String) lahetys.get("prioriteetti");
            finalLahettavaPalvelu = (String) lahetys.get("lahettavapalvelu");
            finalLahettajaOid = (String) lahetys.get("lahettavanvirkailijanoid");
        }

        // resolve the access-right identifiers (käyttöoikeustunnus) before saving the viesti, because they are also stored in the search field
        List<Integer> oikeudet = kayttooikeusRajoitukset.stream().map(this::getOrCreateKayttooikeus).toList();

        // remove secrets from the subject and content that are stored for search
        String otsikkoSanitized = sanitoi(otsikko, maskit.keySet());
        String sisaltoSanitized = sanitoi(sisalto, maskit.keySet());
        List<String> vastaanottajaOsoitteet = vastaanottajat.stream()
                .map(v -> v.sahkoposti().toLowerCase(Locale.ROOT)).toList();
        List<String> metadataArvot = metadata.entrySet().stream()
                .flatMap(e -> e.getValue().stream().map(arvo -> e.getKey() + ":" + arvo)).toList();
        List<String> organisaatiot = kayttooikeusRajoitukset.stream()
                .map(Kayttooikeus::organisaatio).filter(Objects::nonNull).distinct().toList();

        // save the viesti with its search fields (haku_* columns are NOT NULL after the migrations)
        List<Object> args = new ArrayList<>();
        args.add(viestiTunniste.toString());
        args.add(finalLahetysTunniste.toString());
        args.add(otsikko);
        args.add(sisalto);
        args.add(sisallonTyyppi);
        args.add(kielet.contains("fi"));
        args.add(kielet.contains("sv"));
        args.add(kielet.contains("en"));
        args.add(finalPrioriteetti);
        args.add(omistaja);
        args.add(otsikkoSanitized);
        args.add(sisaltoSanitized);
        oikeudet.forEach(args::add);
        vastaanottajaOsoitteet.forEach(args::add);
        args.add(finalLahettajaOid);
        metadataArvot.forEach(args::add);
        args.add(finalLahettavaPalvelu);
        organisaatiot.forEach(args::add);

        String viestiSql = "INSERT INTO viestit (tunniste, lahetys_tunniste, otsikko, sisalto, sisallontyyppi, kielet_fi, "
                + "kielet_sv, kielet_en, prioriteetti, omistaja, luotu, haku_otsikko, haku_sisalto, haku_kayttooikeudet, "
                + "haku_vastaanottajat, haku_lahettaja, haku_metadata, haku_lahettavapalvelu, haku_organisaatiot) VALUES ("
                + "?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?::prioriteetti, ?, now(), to_tsvector('simple', ?), "
                + "to_tsvector('simple', ?), " + arrayPlaceholders(oikeudet.size(), "integer") + ", "
                + arrayPlaceholders(vastaanottajaOsoitteet.size(), "varchar") + ", ?, "
                + arrayPlaceholders(metadataArvot.size(), "varchar") + ", ?, "
                + arrayPlaceholders(organisaatiot.size(), "varchar") + ")";
        jdbc.update(viestiSql, args.toArray());

        // access rights (käyttöoikeus) for the message (viesti) and the lähetys
        for (int oikeusTunniste : oikeudet) {
            jdbc.update("INSERT INTO viestit_kayttooikeudet (viesti_tunniste, kayttooikeus_tunniste) VALUES (?::uuid, ?)",
                    viestiTunniste.toString(), oikeusTunniste);
            jdbc.update("INSERT INTO lahetykset_kayttooikeudet (lahetys_tunniste, kayttooikeus_tunniste) VALUES (?::uuid, ?) "
                            + "ON CONFLICT DO NOTHING",
                    finalLahetysTunniste.toString(), oikeusTunniste);
        }

        // metadata
        metadata.forEach((avain, arvot) -> {
            jdbc.update("INSERT INTO metadata_avaimet VALUES (?) ON CONFLICT (avain) DO NOTHING", avain);
            for (String arvo : arvot) {
                jdbc.update("INSERT INTO metadata (avain, arvo, viesti_tunniste) VALUES (?, ?, ?::uuid)",
                        avain, arvo, viestiTunniste.toString());
            }
        });

        // masks
        maskit.forEach((salaisuus, maski) ->
                jdbc.update("INSERT INTO maskit (viesti_tunniste, salaisuus, maski) VALUES (?::uuid, ?, ?)",
                        viestiTunniste.toString(), salaisuus, maski));

        // recipients (vastaanottaja) (no attachments yet -> status ODOTTAA) and their status transitions
        String vastaanottajaPrioriteetti = finalPrioriteetti;
        List<UUID> vastaanottajaTunnisteet = vastaanottajat.stream().map(vastaanottaja -> {
            UUID vastaanottajaTunniste = UUID.randomUUID();
            jdbc.update(
                    "INSERT INTO vastaanottajat (tunniste, viesti_tunniste, nimi, sahkopostiosoite, tila, luotu, prioriteetti) "
                            + "VALUES (?::uuid, ?::uuid, ?, ?, ?, now(), ?::prioriteetti)",
                    vastaanottajaTunniste.toString(), viestiTunniste.toString(), vastaanottaja.nimi(),
                    vastaanottaja.sahkoposti(), "ODOTTAA", vastaanottajaPrioriteetti);
            lisaaVastaanottajanSiirtyma(vastaanottajaTunniste, "ODOTTAA", null);
            return vastaanottajaTunniste;
        }).toList();

        return new TallennettuViesti(viestiTunniste, finalLahetysTunniste, vastaanottajaTunnisteet);
    }

    /** Lisää vastaanottajalle tilasiirtymän (vastaanottaja_siirtymat). */
    public void lisaaVastaanottajanSiirtyma(UUID vastaanottajaTunniste, String tila, String lisatiedot) {
        jdbc.update("INSERT INTO vastaanottaja_siirtymat (vastaanottaja_tunniste, aika, tila, lisatiedot) "
                        + "VALUES (?::uuid, now(), ?, ?)",
                vastaanottajaTunniste.toString(), tila, lisatiedot);
    }

    private static String sanitoi(String teksti, Set<String> salaisuudet) {
        String tulos = teksti;
        for (String salaisuus : salaisuudet) {
            tulos = tulos.replace(salaisuus, "");
        }
        return tulos;
    }

    private static String arrayPlaceholders(int koko, String tyyppi) {
        if (koko == 0) {
            return "ARRAY[]::" + tyyppi + "[]";
        }
        return "ARRAY[" + String.join(",", Collections.nCopies(koko, "?")) + "]::" + tyyppi + "[]";
    }

    private int getOrCreateKayttooikeus(Kayttooikeus kayttooikeus) {
        return jdbc.queryForObject(
                "WITH lisays AS ("
                        + "  INSERT INTO kayttooikeudet (organisaatio, oikeus) VALUES (?, ?) ON CONFLICT DO NOTHING RETURNING tunniste"
                        + ") "
                        + "SELECT tunniste FROM kayttooikeudet WHERE organisaatio IS NOT DISTINCT FROM ? AND oikeus = ? "
                        + "UNION SELECT tunniste FROM lisays",
                Integer.class,
                kayttooikeus.organisaatio(), kayttooikeus.oikeus(),
                kayttooikeus.organisaatio(), kayttooikeus.oikeus());
    }
}
