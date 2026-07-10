package fi.vm.sade.viestinvalitys.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.simplejavamail.api.email.ContentTransferEncoding;
import org.simplejavamail.api.email.Email;
import org.simplejavamail.converter.EmailConverter;
import org.simplejavamail.email.EmailBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Constructs a single message into an email (eml/message-rfc822) along with attachments.
 * Only in use when feature for downloading messages is enabled.
 *
 * <p>Based on lambdat/raportointi DownloadResource functionality.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "viestinvalitys.features.downloadViesti.enabled", havingValue = "true")
public class DownloadService {

    private final JdbcTemplate jdbcTemplate;
    private final S3Client s3Client;

    @Value("${attachments.bucket.name}")
    private String bucketName;

    public Optional<byte[]> generateEml(UUID viestiTunniste) {
        var rows = jdbcTemplate.queryForList(
            "SELECT v.tunniste, v.otsikko, v.sisalto, v.sisallontyyppi, l.replyto " +
            "FROM viestit v JOIN lahetykset l ON v.lahetys_tunniste = l.tunniste " +
            "WHERE v.tunniste = ?::uuid",
            viestiTunniste.toString());

        if (rows.isEmpty()) {
            return Optional.empty();
        }

        var row = rows.get(0);
        var builder = EmailBuilder.startingBlank()
            .withContentTransferEncoding(ContentTransferEncoding.BASE_64)
            .withSubject((String) row.get("otsikko"));

        var replyTo = (String) row.get("replyto");
        if (replyTo != null) {
            builder = builder.withReplyTo(replyTo);
        }

        var sisalto = (String) row.get("sisalto");
        if ("HTML".equals(row.get("sisallontyyppi"))) {
            builder = builder.withHTMLText(sisalto);
        } else {
            builder = builder.withPlainText(sisalto);
        }

        for (var liite : getLiitteet(viestiTunniste)) {
            var bytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(bucketName)
                .key(liite.get("tunniste").toString())
                .build()).asByteArray();
            builder = builder.withAttachment((String) liite.get("nimi"), bytes, (String) liite.get("contenttype"));
        }

        Email email = builder.buildEmail();
        String eml = EmailConverter.emailToEML(email);
        return Optional.of(eml.getBytes(StandardCharsets.UTF_8));
    }

    private List<Map<String, Object>> getLiitteet(UUID viestiTunniste) {
        return jdbcTemplate.queryForList(
            "SELECT l.tunniste, l.nimi, l.contenttype FROM liitteet l " +
            "JOIN viestit_liitteet vl ON l.tunniste = vl.liite_tunniste " +
            "WHERE vl.viesti_tunniste = ?::uuid ORDER BY vl.indeksi",
            viestiTunniste.toString());
    }
}
