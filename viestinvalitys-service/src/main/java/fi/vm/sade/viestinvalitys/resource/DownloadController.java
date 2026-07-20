package fi.vm.sade.viestinvalitys.resource;

import fi.vm.sade.viestinvalitys.service.DownloadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

/**
 * Endpoint for downloading a single message as an EML format email message.
 * Available only when feature for downloading messages is enabled (property
 * `viestinvalitys.features.downloadViesti.enabled`).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/download")
@ConditionalOnProperty(name = "viestinvalitys.features.downloadViesti.enabled", havingValue = "true")
public class DownloadController {

    private final DownloadService downloadService;

    @GetMapping("/viesti")
    public ResponseEntity<byte[]> generateEml(@RequestParam(name = "viestiTunniste") UUID viestiTunniste) {
        log.debug("Downloading message {} in eml-format", viestiTunniste);
        try {
            Optional<byte[]> eml = downloadService.generateEml(viestiTunniste);
            if (eml.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            byte[] body = eml.get();
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("message/rfc822"));
            headers.setContentLength(body.length);
            headers.setContentDisposition(ContentDisposition.attachment()
                .filename("viesti-" + viestiTunniste + ".eml")
                .build());
            return ResponseEntity.ok().headers(headers).body(body);
        } catch (Exception e) {
            log.error("Downloading message failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
