package fi.vm.sade.viestinvalitys.dto;

import java.util.List;
import java.util.Map;

public record LuoViestiRequest(
        String otsikko,
        String sisalto,
        String sisallonTyyppi,
        List<String> kielet,
        List<Maski> maskit,
        String lahettavanVirkailijanOid,
        Kontakti lahettaja,
        String replyTo,
        List<Kontakti> vastaanottajat,
        List<String> liitteidenTunnisteet,
        String lahettavaPalvelu,
        String lahetysTunniste,
        String prioriteetti,
        Integer sailytysaika,
        List<Kayttooikeus> kayttooikeusRajoitukset,
        Map<String, List<String>> metadata,
        String idempotencyKey) {}
