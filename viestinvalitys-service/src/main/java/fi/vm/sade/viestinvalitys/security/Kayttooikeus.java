package fi.vm.sade.viestinvalitys.security;

import java.io.Serializable;

public record Kayttooikeus(String oikeus, String organisaatio) implements Serializable {}
