package fi.vm.sade.viestinvalitys.lahetys.model;

import java.util.UUID;

public record Vastaanottaja(
        UUID tunniste,
        UUID viestiTunniste,
        Kontakti kontakti,
        VastaanottajanTila tila,
        Prioriteetti prioriteetti,
        String sesTunniste) {
}
