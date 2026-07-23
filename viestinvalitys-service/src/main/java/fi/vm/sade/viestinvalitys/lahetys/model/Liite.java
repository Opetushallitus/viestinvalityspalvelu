package fi.vm.sade.viestinvalitys.lahetys.model;

import java.util.UUID;

/**
 * {@code tunniste} doubles as the S3 object key.
 */
public record Liite(UUID tunniste, String nimi, String contentType) {
}
