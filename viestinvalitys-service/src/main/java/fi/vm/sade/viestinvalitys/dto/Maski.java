package fi.vm.sade.viestinvalitys.dto;

/**
 * A secret (salaisuus) to be hidden from the Viesti content and the maski that replaces it.
 * {@code null} means an undefined field.
 */
public record Maski(String salaisuus, String maski) {}
