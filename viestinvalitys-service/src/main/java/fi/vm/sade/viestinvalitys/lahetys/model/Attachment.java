package fi.vm.sade.viestinvalitys.lahetys.model;

public record Attachment(String nimi, String contentType, byte[] bytes) {
}
