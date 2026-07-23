package fi.vm.sade.viestinvalitys.lahetys.model;

import jakarta.annotation.Nullable;

import java.util.UUID;

public record Viesti(
        UUID tunniste,
        UUID lahetysTunniste,
        String otsikko,
        String sisalto,
        SisallonTyyppi sisallonTyyppi,
        Kontakti lahettaja,
        @Nullable String replyTo,
        Prioriteetti prioriteetti) {
}
