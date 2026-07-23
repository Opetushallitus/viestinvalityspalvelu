package fi.vm.sade.viestinvalitys.lahetys.repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import fi.vm.sade.viestinvalitys.lahetys.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Java/JdbcTemplate port of the send-path methods of the Scala {@code KantaOperaatiot}.
 */
@Repository
@RequiredArgsConstructor
public class LahetysSendRepository {

  private final JdbcTemplate jdbc;
  private final NamedParameterJdbcTemplate namedJdbc;

  @Transactional
  public List<UUID> getLahetettavatVastaanottajat(int maara) {
    if (maara <= 0) {
      return List.of();
    }
    List<UUID> tunnisteet =
            jdbc.query(
                    "SELECT tunniste FROM vastaanottajat WHERE tila = 'ODOTTAA' "
                            + "ORDER BY prioriteetti, luotu ASC FOR UPDATE SKIP LOCKED LIMIT ?",
                    (rs, i) -> rs.getObject("tunniste", UUID.class),
                    maara);
    if (tunnisteet.isEmpty()) {
      return List.of();
    }
    List<Object[]> batch = tunnisteet.stream().map(t -> new Object[]{t}).toList();
    jdbc.batchUpdate("UPDATE vastaanottajat SET tila = 'LAHETYKSESSA' WHERE tunniste = ?", batch);
    jdbc.batchUpdate(
            "INSERT INTO vastaanottaja_siirtymat VALUES (?, now(), 'LAHETYKSESSA', null)", batch);
    return tunnisteet;
  }

  public List<Vastaanottaja> getVastaanottajat(List<UUID> tunnisteet) {
    if (tunnisteet.isEmpty()) {
      return List.of();
    }
    return namedJdbc.query(
            "SELECT tunniste, viesti_tunniste, nimi, sahkopostiosoite, tila, prioriteetti, ses_tunniste "
                    + "FROM vastaanottajat WHERE tunniste IN (:ids)",
            new MapSqlParameterSource("ids", tunnisteet),
            (rs, i) ->
                    new Vastaanottaja(
                            rs.getObject("tunniste", UUID.class),
                            rs.getObject("viesti_tunniste", UUID.class),
                            new Kontakti(rs.getString("nimi"), rs.getString("sahkopostiosoite")),
                            VastaanottajanTila.valueOf(rs.getString("tila")),
                            Prioriteetti.valueOf(rs.getString("prioriteetti")),
                            rs.getString("ses_tunniste")));
  }

  public Map<UUID, Viesti> getViestit(Collection<UUID> viestiTunnisteet) {
    if (viestiTunnisteet.isEmpty()) {
      return Map.of();
    }
    List<Viesti> viestit =
            namedJdbc.query(
                    "SELECT viestit.tunniste, lahetys_tunniste, viestit.otsikko, sisalto, sisallontyyppi, "
                            + "replyto, viestit.prioriteetti, lahettajannimi, lahettajansahkoposti "
                            + "FROM viestit JOIN lahetykset ON viestit.lahetys_tunniste = lahetykset.tunniste "
                            + "WHERE viestit.tunniste IN (:ids)",
                    new MapSqlParameterSource("ids", viestiTunnisteet),
                    (rs, i) ->
                            new Viesti(
                                    rs.getObject("tunniste", UUID.class),
                                    rs.getObject("lahetys_tunniste", UUID.class),
                                    rs.getString("otsikko"),
                                    rs.getString("sisalto"),
                                    SisallonTyyppi.valueOf(rs.getString("sisallontyyppi")),
                                    new Kontakti(rs.getString("lahettajannimi"), rs.getString("lahettajansahkoposti")),
                                    rs.getString("replyto"),
                                    Prioriteetti.valueOf(rs.getString("prioriteetti"))));
    Map<UUID, Viesti> map = new HashMap<>();
    viestit.forEach(v -> map.put(v.tunniste(), v));
    return map;
  }

  public Map<UUID, List<Liite>> getViestinLiitteet(Collection<UUID> viestiTunnisteet) {
    if (viestiTunnisteet.isEmpty()) {
      return Map.of();
    }
    Map<UUID, List<Liite>> result = new LinkedHashMap<>();
    namedJdbc.query(
            "SELECT viestit_liitteet.viesti_tunniste, liitteet.tunniste, liitteet.nimi, "
                    + "liitteet.contenttype "
                    + "FROM viestit_liitteet JOIN liitteet ON viestit_liitteet.liite_tunniste = liitteet.tunniste "
                    + "WHERE viestit_liitteet.viesti_tunniste IN (:ids) ORDER BY indeksi ASC",
            new MapSqlParameterSource("ids", viestiTunnisteet),
            rs -> {
              UUID viestiTunniste = rs.getObject("viesti_tunniste", UUID.class);
              Liite liite =
                      new Liite(
                              rs.getObject("tunniste", UUID.class),
                              rs.getString("nimi"),
                              rs.getString("contenttype"));
              result.computeIfAbsent(viestiTunniste, k -> new ArrayList<>()).add(liite);
            });
    return result;
  }

  @Transactional
  public void paivitaVastaanottajaLahetetyksi(UUID tunniste, String sesTunniste) {
    jdbc.update(
            "UPDATE vastaanottajat SET tila = 'LAHETETTY', ses_tunniste = ? WHERE tunniste = ?",
            sesTunniste,
            tunniste);
    jdbc.update("INSERT INTO vastaanottaja_siirtymat VALUES (?, now(), 'LAHETETTY', null)", tunniste);
  }

  @Transactional
  public void paivitaVastaanottajaVirhetilaan(UUID tunniste, String lisatiedot) {
    jdbc.update("UPDATE vastaanottajat SET tila = 'VIRHE' WHERE tunniste = ?", tunniste);
    jdbc.update(
            "INSERT INTO vastaanottaja_siirtymat VALUES (?, now(), 'VIRHE', ?)", tunniste, lisatiedot);
  }

  @Transactional
  public void paivitaVastaanottajaOdottaaTilaan(UUID tunniste, String lisatiedot) {
    jdbc.update("UPDATE vastaanottajat SET tila = 'ODOTTAA' WHERE tunniste = ?", tunniste);
    jdbc.update(
            "INSERT INTO vastaanottaja_siirtymat VALUES (?, now(), 'ODOTTAA', ?)", tunniste, lisatiedot);
  }
}
