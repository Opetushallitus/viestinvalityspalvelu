package fi.vm.sade.viestinvalitys.service;

import fi.vm.sade.viestinvalitys.security.Kayttooikeus;
import fi.vm.sade.viestinvalitys.security.SecurityOperations;
import jakarta.servlet.http.HttpSession;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Resolves which käyttöoikeus (oikeus, organisaatio) pairs a user effectively holds and whether
 * they grant visibility to an entity, replicating the old raportointi lambda's SecurityOperaatiot:
 * a user sees an entity if they own it, are pääkäyttäjä, or hold one of the entity's
 * käyttöoikeusrajoitukset either directly or via a parent organisation. Resolution results are
 * cached in the session because the parent-organisation expansion is expensive; the käyttöoikeus
 * tunniste cache is invalidated when new käyttöoikeus rows appear in the database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KayttooikeusService {

    static final String SESSION_ATTR_KAYTTOOIKEUDET = "kayttooikeudet";
    static final String SESSION_ATTR_KAYTTOOIKEUSTUNNISTEET = "kayttooikeustunnisteet";
    static final String SESSION_ATTR_UUSIN_KAYTTOOIKEUSTUNNISTE = "uusintunniste";

    private final JdbcTemplate jdbcTemplate;
    private final OrganisaatioService organisaatioService;

    public boolean onOikeusKatsellaLahetys(HttpSession session, String lahetysTunniste, String omistaja) {
        return onOikeusKatsellaEntiteetti(session, omistaja, getLahetyksenKayttooikeudet(lahetysTunniste));
    }

    public boolean onOikeusKatsellaViesti(HttpSession session, UUID viestiTunniste) {
        var omistajat = jdbcTemplate.queryForList(
            "SELECT omistaja FROM viestit WHERE tunniste = ?::uuid", String.class, viestiTunniste.toString());
        if (omistajat.isEmpty()) {
            return true;
        }
        return onOikeusKatsellaEntiteetti(session, omistajat.get(0), getViestinKayttooikeudet(viestiTunniste.toString()));
    }

    public boolean onOikeusKatsellaEntiteetti(HttpSession session, String omistaja, Set<Kayttooikeus> entiteetinOikeudet) {
        var secOps = new SecurityOperations(session);
        if (secOps.getUsername().equals(omistaja) || secOps.isPaakayttaja()) {
            return true;
        }
        if (!secOps.hasReadRights()) {
            return false;
        }
        var kayttajanOikeudet = resolveKayttajanOikeudet(session);
        return entiteetinOikeudet.stream().anyMatch(kayttajanOikeudet::contains);
    }

    /**
     * Returns the kayttooikeudet table tunnisteet matching the user's resolved rights for scoping
     * queries against the viestit haku_kayttooikeudet column, or null for pääkäyttäjät (no scoping).
     */
    public Set<Integer> getKayttooikeusTunnisteet(HttpSession session) {
        var secOps = new SecurityOperations(session);
        if (secOps.isPaakayttaja()) {
            return null;
        }
        var cached = readTunnisteetFromSession(session);
        if (cached != null && getUusinKayttooikeusTunniste().equals(getSessionAttribute(session, SESSION_ATTR_UUSIN_KAYTTOOIKEUSTUNNISTE))) {
            return cached;
        }
        var oikeudet = resolveKayttajanOikeudet(session).stream()
                .filter(k -> k.organisaatio() != null)
                .collect(Collectors.toList());
        var tunnisteet = new HashSet<Integer>();
        if (!oikeudet.isEmpty()) {
            var placeholders = oikeudet.stream().map(k -> "(?, ?)").collect(Collectors.joining(","));
            var params = oikeudet.stream()
                    .flatMap(k -> java.util.stream.Stream.of(k.organisaatio(), k.oikeus()))
                    .toArray();
            tunnisteet.addAll(jdbcTemplate.queryForList(
                "SELECT tunniste FROM kayttooikeudet WHERE (organisaatio, oikeus) IN (" + placeholders + ")",
                Integer.class, params));
        }
        setSessionAttribute(session, SESSION_ATTR_KAYTTOOIKEUSTUNNISTEET, tunnisteet);
        setSessionAttribute(session, SESSION_ATTR_UUSIN_KAYTTOOIKEUSTUNNISTE, getUusinKayttooikeusTunniste());
        return tunnisteet;
    }

    @SuppressWarnings("unchecked")
    private Set<Kayttooikeus> resolveKayttajanOikeudet(HttpSession session) {
        var secOps = new SecurityOperations(session);
        var casOikeudet = secOps.getCasKayttooikeudet();
        if (secOps.isPaakayttaja()) {
            return casOikeudet;
        }
        var cached = getSessionAttribute(session, SESSION_ATTR_KAYTTOOIKEUDET);
        if (cached instanceof Set<?> s) {
            return (Set<Kayttooikeus>) s;
        }

        long resoluutionAlkuhetki = System.currentTimeMillis();
        var kaikkiOikeudet = getKaikkiKayttooikeudet();
        var kayttajanOikeudet = new HashSet<Kayttooikeus>();
        kaikkiOikeudet.stream().filter(casOikeudet::contains).forEach(kayttajanOikeudet::add);
        casOikeudet.stream()
                .filter(k -> k.oikeus().startsWith("APP_VIESTINVALITYS"))
                .forEach(kayttajanOikeudet::add);

        var kayttajanOikeusNimet = casOikeudet.stream().map(Kayttooikeus::oikeus).collect(Collectors.toSet());
        kaikkiOikeudet.stream()
                .filter(puuttuva -> !casOikeudet.contains(puuttuva))
                .filter(puuttuva -> puuttuva.organisaatio() != null)
                .filter(puuttuva -> kayttajanOikeusNimet.contains(puuttuva.oikeus()))
                .filter(puuttuva -> hasOikeusForParentOrg(puuttuva, casOikeudet))
                .forEach(kayttajanOikeudet::add);

        setSessionAttribute(session, SESSION_ATTR_KAYTTOOIKEUDET, kayttajanOikeudet);
        log.info("Käyttäjän {} käyttöoikeuksien organisaatioresoluutio kesti {} ms ({} järjestelmän käyttöoikeutta, tulos {} käyttöoikeutta)",
            secOps.getUsername(), System.currentTimeMillis() - resoluutionAlkuhetki, kaikkiOikeudet.size(), kayttajanOikeudet.size());
        return kayttajanOikeudet;
    }

    private boolean hasOikeusForParentOrg(Kayttooikeus puuttuva, Set<Kayttooikeus> casOikeudet) {
        if (casOikeudet.contains(new Kayttooikeus(puuttuva.oikeus(), SecurityOperations.OPH_ORGANISAATIO_OID))) {
            return true;
        }
        return organisaatioService.getParentOids(puuttuva.organisaatio()).stream()
                .anyMatch(parentOid -> casOikeudet.contains(new Kayttooikeus(puuttuva.oikeus(), parentOid)));
    }

    public Set<Kayttooikeus> getLahetyksenKayttooikeudet(String lahetysTunniste) {
        return jdbcTemplate.queryForList(
                "SELECT k.oikeus, k.organisaatio FROM lahetykset_kayttooikeudet lk "
                    + "JOIN kayttooikeudet k ON lk.kayttooikeus_tunniste = k.tunniste "
                    + "WHERE lk.lahetys_tunniste = ?::uuid",
                lahetysTunniste).stream()
            .map(row -> new Kayttooikeus((String) row.get("oikeus"), (String) row.get("organisaatio")))
            .collect(Collectors.toSet());
    }

    public Set<Kayttooikeus> getViestinKayttooikeudet(String viestiTunniste) {
        return jdbcTemplate.queryForList(
                "SELECT k.oikeus, k.organisaatio FROM viestit_kayttooikeudet vk "
                    + "JOIN kayttooikeudet k ON vk.kayttooikeus_tunniste = k.tunniste "
                    + "WHERE vk.viesti_tunniste = ?::uuid",
                viestiTunniste).stream()
            .map(row -> new Kayttooikeus((String) row.get("oikeus"), (String) row.get("organisaatio")))
            .collect(Collectors.toSet());
    }

    private Set<Kayttooikeus> getKaikkiKayttooikeudet() {
        return jdbcTemplate.queryForList("SELECT oikeus, organisaatio FROM kayttooikeudet").stream()
            .map(row -> new Kayttooikeus((String) row.get("oikeus"), (String) row.get("organisaatio")))
            .collect(Collectors.toSet());
    }

    private Integer getUusinKayttooikeusTunniste() {
        Integer max = jdbcTemplate.queryForObject("SELECT MAX(tunniste) FROM kayttooikeudet", Integer.class);
        return max != null ? max : 0;
    }

    @SuppressWarnings("unchecked")
    private Set<Integer> readTunnisteetFromSession(HttpSession session) {
        var cached = getSessionAttribute(session, SESSION_ATTR_KAYTTOOIKEUSTUNNISTEET);
        if (cached instanceof Set<?> s) {
            return (Set<Integer>) s;
        }
        return null;
    }

    private Object getSessionAttribute(HttpSession session, String name) {
        return session != null ? session.getAttribute(name) : null;
    }

    private void setSessionAttribute(HttpSession session, String name, Object value) {
        if (session != null) {
            session.setAttribute(name, value);
        }
    }
}
