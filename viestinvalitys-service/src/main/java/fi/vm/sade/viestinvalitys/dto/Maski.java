package fi.vm.sade.viestinvalitys.dto;

/**
 * Viestin sisällöstä peitettävä salaisuus ja sen tilalle tuleva maski. {@code null} tarkoittaa
 * määrittelemätöntä kenttää.
 */
public record Maski(String salaisuus, String maski) {}
