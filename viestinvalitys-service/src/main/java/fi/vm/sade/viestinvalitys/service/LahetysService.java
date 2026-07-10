package fi.vm.sade.viestinvalitys.service;

import fi.vm.sade.viestinvalitys.security.SecurityOperations;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LahetysService {

    private static final int DEFAULT_ENINTAAN = 20;
    private static final int DEFAULT_VASTAANOTTAJAT_ENINTAAN = 10;

    private final JdbcTemplate jdbcTemplate;

    public Map<String, Object> searchLahetykset(
            HttpSession session,
            Optional<String> alkaen,
            Optional<String> enintaan,
            Optional<String> vastaanottaja,
            Optional<String> organisaatio,
            Optional<String> viesti,
            Optional<String> palvelu,
            Optional<String> lahettaja,
            Optional<String> hakuAlkaen,
            Optional<String> hakuPaattyen) {

        var secOps = new SecurityOperations(session);
        if (!secOps.onOikeusKatsella()) {
            throw new SecurityException("Ei katseluoikeutta");
        }

        int limit = enintaan.map(Integer::parseInt).orElse(DEFAULT_ENINTAAN) + 1;
        var params = new ArrayList<Object>();
        var conditions = new ArrayList<String>();

        if (!secOps.onPaakayttaja()) {
            var orgs = secOps.getCasOrganisaatiot();
            if (!orgs.isEmpty()) {
                conditions.add("(l.omistaja = ? OR EXISTS (SELECT 1 FROM lahetykset_kayttooikeudet lk JOIN kayttooikeudet k ON lk.kayttooikeus_tunniste = k.tunniste WHERE lk.lahetys_tunniste = l.tunniste AND k.organisaatio = ANY(?::varchar[])))");
                params.add(secOps.getUsername());
                params.add(orgs.toArray(new String[0]));
            }
        }

        alkaen.ifPresent(a -> {
            conditions.add("l.tunniste < ?::uuid");
            params.add(a);
        });
        hakuAlkaen.ifPresent(a -> {
            conditions.add("l.luotu >= ?::timestamptz");
            params.add(a);
        });
        hakuPaattyen.ifPresent(p -> {
            conditions.add("l.luotu <= ?::timestamptz");
            params.add(p);
        });
        palvelu.ifPresent(p -> {
            conditions.add("l.lahettavapalvelu = ?");
            params.add(p);
        });

        String where = conditions.isEmpty() ? "" : "WHERE " + String.join(" AND ", conditions);
        String sql = "SELECT l.tunniste, l.otsikko, l.omistaja, l.lahettavapalvelu, l.lahettavanvirkailijanoid, l.lahettajannimi, l.lahettajansahkoposti, l.replyto, l.luotu " +
                "FROM lahetykset l " + where + " ORDER BY l.luotu DESC LIMIT ?";
        params.add(limit);

        var rows = jdbcTemplate.queryForList(sql, params.toArray());
        boolean hasMore = rows.size() == limit;
        if (hasMore) rows.remove(rows.size() - 1);

        var lahetykset = rows.stream().map(row -> {
            var m = new LinkedHashMap<String, Object>();
            m.put("lahetysTunniste", row.get("tunniste").toString());
            m.put("otsikko", row.get("otsikko"));
            m.put("omistaja", row.get("omistaja"));
            m.put("lahettavaPalvelu", row.get("lahettavapalvelu"));
            m.put("lahettavanVirkailijanOID", row.get("lahettavanvirkailijanoid"));
            m.put("lahettajanNimi", row.get("lahettajannimi"));
            m.put("lahettajanSahkoposti", row.get("lahettajansahkoposti"));
            m.put("replyTo", row.get("replyto"));
            m.put("luotu", row.get("luotu").toString());
            m.put("tilat", getVastaanottotilat(row.get("tunniste").toString()));
            m.put("viestiLkm", getViestiLkm(row.get("tunniste").toString()));
            return (Map<String, Object>) m;
        }).collect(Collectors.toList());

        var result = new LinkedHashMap<String, Object>();
        result.put("lahetykset", lahetykset);
        result.put("seuraavatAlkaen", hasMore ? rows.get(rows.size() - 1).get("tunniste").toString() : null);
        return result;
    }

    public Optional<Map<String, Object>> getLahetys(HttpSession session, String lahetysTunniste) {
        validateUUID(lahetysTunniste);
        var secOps = new SecurityOperations(session);
        if (!secOps.onOikeusKatsella()) throw new SecurityException("Ei katseluoikeutta");

        var rows = jdbcTemplate.queryForList(
            "SELECT tunniste, otsikko, omistaja, lahettavapalvelu, lahettavanvirkailijanoid, lahettajannimi, lahettajansahkoposti, replyto, luotu FROM lahetykset WHERE tunniste = ?::uuid",
            lahetysTunniste);

        if (rows.isEmpty()) return Optional.empty();
        var row = rows.get(0);
        var m = new LinkedHashMap<String, Object>();
        m.put("lahetysTunniste", row.get("tunniste").toString());
        m.put("otsikko", row.get("otsikko"));
        m.put("omistaja", row.get("omistaja"));
        m.put("lahettavaPalvelu", row.get("lahettavapalvelu"));
        m.put("lahettavanVirkailijanOID", row.get("lahettavanvirkailijanoid"));
        m.put("lahettajanNimi", row.get("lahettajannimi"));
        m.put("lahettajanSahkoposti", row.get("lahettajansahkoposti"));
        m.put("replyTo", row.get("replyto"));
        m.put("luotu", row.get("luotu").toString());
        m.put("tilat", getVastaanottotilat(lahetysTunniste));
        m.put("viestiLkm", getViestiLkm(lahetysTunniste));
        return Optional.of(m);
    }

    public Map<String, Object> getVastaanottajat(
            HttpSession session, String lahetysTunniste,
            Optional<String> alkaen, Optional<String> enintaan,
            Optional<String> tila, Optional<String> vastaanottaja,
            Optional<String> organisaatio) {
        validateUUID(lahetysTunniste);
        var secOps = new SecurityOperations(session);
        if (!secOps.onOikeusKatsella()) throw new SecurityException("Ei katseluoikeutta");

        int limit = enintaan.map(Integer::parseInt).orElse(DEFAULT_VASTAANOTTAJAT_ENINTAAN) + 1;
        var params = new ArrayList<Object>();
        params.add(lahetysTunniste);
        var conditions = new ArrayList<String>();
        conditions.add("v.viesti_tunniste IN (SELECT tunniste FROM viestit WHERE lahetys_tunniste = ?::uuid)");

        tila.ifPresent(t -> {
            conditions.add("v.tila = ?");
            params.add(t);
        });
        vastaanottaja.ifPresent(v -> {
            conditions.add("v.sahkopostiosoite ILIKE ?");
            params.add("%" + v + "%");
        });
        alkaen.ifPresent(a -> {
            conditions.add("v.tunniste < ?::uuid");
            params.add(a);
        });

        String where = "WHERE " + String.join(" AND ", conditions);
        String sql = "SELECT v.tunniste, v.nimi, v.sahkopostiosoite, v.viesti_tunniste, v.tila FROM vastaanottajat v " + where + " ORDER BY v.tila, v.sahkopostiosoite LIMIT ?";
        params.add(limit);

        var rows = jdbcTemplate.queryForList(sql, params.toArray());
        boolean hasMore = rows.size() == limit;
        if (hasMore) rows.remove(rows.size() - 1);

        var vastaanottajat = rows.stream().map(row -> {
            var m = new LinkedHashMap<String, Object>();
            m.put("tunniste", row.get("tunniste").toString());
            m.put("nimi", row.get("nimi"));
            m.put("sahkoposti", row.get("sahkopostiosoite"));
            m.put("viestiTunniste", row.get("viesti_tunniste").toString());
            m.put("tila", row.get("tila"));
            return (Map<String, Object>) m;
        }).collect(Collectors.toList());

        var result = new LinkedHashMap<String, Object>();
        result.put("vastaanottajat", vastaanottajat);
        result.put("seuraavatAlkaen", hasMore ? rows.get(rows.size() - 1).get("tunniste").toString() : null);
        return result;
    }

    public Optional<Map<String, Object>> getMassaviesti(HttpSession session, String lahetysTunniste) {
        validateUUID(lahetysTunniste);
        var secOps = new SecurityOperations(session);
        if (!secOps.onOikeusKatsella()) throw new SecurityException("Ei katseluoikeutta");

        var rows = jdbcTemplate.queryForList(
            "SELECT v.tunniste, v.otsikko, v.sisalto, v.sisallontyyppi, v.kielet_fi, v.kielet_sv, v.kielet_en FROM viestit v WHERE v.lahetys_tunniste = ?::uuid LIMIT 1",
            lahetysTunniste);

        if (rows.isEmpty()) return Optional.empty();
        return Optional.of(buildViestiMap(rows.get(0)));
    }

    public Optional<Map<String, Object>> getViesti(HttpSession session, String viestiTunniste) {
        validateUUID(viestiTunniste);
        var secOps = new SecurityOperations(session);
        if (!secOps.onOikeusKatsella()) throw new SecurityException("Ei katseluoikeutta");

        var rows = jdbcTemplate.queryForList(
            "SELECT v.tunniste, v.otsikko, v.sisalto, v.sisallontyyppi, v.kielet_fi, v.kielet_sv, v.kielet_en FROM viestit v WHERE v.tunniste = ?::uuid",
            viestiTunniste);

        if (rows.isEmpty()) return Optional.empty();
        return Optional.of(buildViestiMap(rows.get(0)));
    }

    public List<String> getLahettavatPalvelut(HttpSession session) {
        var secOps = new SecurityOperations(session);
        if (!secOps.onOikeusKatsella()) throw new SecurityException("Ei katseluoikeutta");
        return jdbcTemplate.queryForList(
            "SELECT DISTINCT lahettavapalvelu FROM lahetykset ORDER BY lahettavapalvelu",
            String.class);
    }

    private List<Map<String, Object>> getVastaanottotilat(String lahetysTunniste) {
        return jdbcTemplate.queryForList(
            "SELECT v.tila AS vastaanottotila, COUNT(*) AS vastaanottaja_lkm FROM vastaanottajat v JOIN viestit vi ON v.viesti_tunniste = vi.tunniste WHERE vi.lahetys_tunniste = ?::uuid GROUP BY v.tila",
            lahetysTunniste).stream().map(row -> {
                var m = new LinkedHashMap<String, Object>();
                m.put("vastaanottotila", row.get("vastaanottotila"));
                m.put("vastaanottajaLkm", ((Number) row.get("vastaanottaja_lkm")).intValue());
                return (Map<String, Object>) m;
            }).collect(Collectors.toList());
    }

    private int getViestiLkm(String lahetysTunniste) {
        var count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM viestit WHERE lahetys_tunniste = ?::uuid", Integer.class, lahetysTunniste);
        return count != null ? count : 0;
    }

    private Map<String, Object> buildViestiMap(Map<String, Object> row) {
        var kielet = new ArrayList<String>();
        if (Boolean.TRUE.equals(row.get("kielet_fi"))) kielet.add("fi");
        if (Boolean.TRUE.equals(row.get("kielet_sv"))) kielet.add("sv");
        if (Boolean.TRUE.equals(row.get("kielet_en"))) kielet.add("en");
        var m = new LinkedHashMap<String, Object>();
        m.put("tunniste", row.get("tunniste").toString());
        m.put("otsikko", row.get("otsikko"));
        m.put("sisalto", row.get("sisalto"));
        m.put("sisallonTyyppi", row.get("sisallontyyppi"));
        m.put("kielet", kielet);
        return m;
    }

    private void validateUUID(String id) {
        try {
            UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tunniste ei ole muodoltaan validi uuid");
        }
    }
}
