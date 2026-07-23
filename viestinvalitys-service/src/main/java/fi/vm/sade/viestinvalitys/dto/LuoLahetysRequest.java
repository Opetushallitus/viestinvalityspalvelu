package fi.vm.sade.viestinvalitys.dto;

public record LuoLahetysRequest(
        String otsikko,
        String lahettavaPalvelu,
        String lahettavanVirkailijanOid,
        Kontakti lahettaja,
        String replyTo,
        String prioriteetti,
        Integer sailytysaika) {}
