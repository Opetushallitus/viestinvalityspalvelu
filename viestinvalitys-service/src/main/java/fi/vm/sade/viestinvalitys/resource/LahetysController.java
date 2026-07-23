package fi.vm.sade.viestinvalitys.resource;

import fi.vm.sade.viestinvalitys.dto.LuoLahetysRequest;
import fi.vm.sade.viestinvalitys.lahetys.audit.AuditLogService;
import fi.vm.sade.viestinvalitys.security.SecurityOperations;
import fi.vm.sade.viestinvalitys.service.LahetysService;
import fi.vm.sade.viestinvalitys.service.LahetysWriteService;
import fi.vm.sade.viestinvalitys.validation.LahetysValidator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class LahetysController {

    private final LahetysService lahetysService;
    private final LahetysWriteService lahetysWriteService;
    // Best-effort audit; the AuditLogService bean only exists when viestinvalitys.lahetys.enabled=true.
    // Decoupling create-audit from the send-worker flag is a follow-up (see vastaanotto-migration.md).
    private final ObjectProvider<AuditLogService> auditLogService;

    @PostMapping(
            path = "/v1/lahetykset",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> luoLahetys(
            @RequestBody LuoLahetysRequest body,
            HttpServletRequest request) {
        var secOps = new SecurityOperations(request.getSession(false));
        if (!secOps.hasSendRights()) {
            return ResponseEntity.status(403).build();
        }
        var virheet = LahetysValidator.validateLahetys(body);
        if (!virheet.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("validointiVirheet", new ArrayList<>(virheet)));
        }
        try {
            // validation guarantees lahettaja, prioriteetti and sailytysaika are present
            var lahettaja = new LahetysWriteService.Kontakti(
                    body.lahettaja().nimi(), body.lahettaja().sahkopostiOsoite());
            UUID lahetysTunniste = lahetysWriteService.tallennaLahetys(
                    body.otsikko(), body.lahettavaPalvelu(), body.lahettavanVirkailijanOid(),
                    lahettaja, body.replyTo(), body.prioriteetti().toUpperCase(Locale.ROOT),
                    secOps.getUsername(), body.sailytysaika());
            auditLogService.ifAvailable(a -> a.logCreateLahetys(lahetysTunniste));
            return ResponseEntity.ok(Map.of("lahetysTunniste", lahetysTunniste.toString()));
        } catch (Exception e) {
            log.error("Lähetyksen luonti epäonnistui", e);
            return ResponseEntity.status(500)
                    .body(Map.of("validointiVirheet", List.of("Lähetyksen luonti epäonnistui")));
        }
    }

    @GetMapping("/v1/lahetykset/lista")
    public ResponseEntity<Object> getLahetykset(
            @RequestParam Optional<String> alkaen,
            @RequestParam Optional<String> enintaan,
            @RequestParam Optional<String> vastaanottaja,
            @RequestParam Optional<String> organisaatio,
            @RequestParam Optional<String> viesti,
            @RequestParam Optional<String> palvelu,
            @RequestParam Optional<String> lahettaja,
            @RequestParam Optional<String> hakuAlkaen,
            @RequestParam Optional<String> hakuPaattyen,
            HttpServletRequest request) {
        log.debug("Fetching multiple lähetys");
        try {
            var result = lahetysService.searchLahetykset(
                request.getSession(false),
                alkaen, enintaan, vastaanottaja, organisaatio,
                viesti, palvelu, lahettaja, hakuAlkaen, hakuPaattyen);
            return ResponseEntity.ok(result);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        } catch (Exception e) {
            log.error("Fetching multiple lahetys failed", e);
            return ResponseEntity.status(500).body("Fetching multiple lähetys failed");
        }
    }

    @GetMapping("/v1/lahetykset/{lahetysTunniste}")
    public ResponseEntity<Object> getLahetys(
            @PathVariable String lahetysTunniste,
            HttpServletRequest request) {
        log.debug("Fetching lähetys {}", lahetysTunniste);
        try {
            var result = lahetysService.getLahetys(request.getSession(false), lahetysTunniste);
            return result.map(ResponseEntity::<Object>ok)
                    .orElseGet(() -> ResponseEntity.status(410).build());
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Fetching lähetys failed", e);
            return ResponseEntity.status(500).body("Fetching lähetys failed");
        }
    }

    @GetMapping("/v1/lahetykset/{lahetysTunniste}/vastaanottajat")
    public ResponseEntity<Object> getVastaanottajat(
            @PathVariable String lahetysTunniste,
            @RequestParam Optional<String> alkaen,
            @RequestParam Optional<String> enintaan,
            @RequestParam Optional<String> tila,
            @RequestParam Optional<String> vastaanottaja,
            @RequestParam Optional<String> organisaatio,
            HttpServletRequest request) {
        log.debug("Fetch recipients (vastaanottaja) for lähetys {}", lahetysTunniste);
        try {
            var result = lahetysService.getVastaanottajat(
                request.getSession(false), lahetysTunniste,
                alkaen, enintaan, tila, vastaanottaja, organisaatio);
            return ResponseEntity.ok(result);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Fetching recipients (vastaanottaja) failed", e);
            return ResponseEntity.status(500).body("Fetching recipients (vastaanottaja) failed");
        }
    }

    @GetMapping("/v1/massaviesti/{lahetysTunniste}")
    public ResponseEntity<Object> getMassaviesti(
            @PathVariable String lahetysTunniste,
            HttpServletRequest request) {
        log.debug("Fetching mass message (massaviesti) {}", lahetysTunniste);
        try {
            var result = lahetysService.getMassaviesti(request.getSession(false), lahetysTunniste);
          return result.map(ResponseEntity::<Object>ok)
                  .orElseGet(() -> ResponseEntity.status(410).build());
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        } catch (Exception e) {
            log.error("Fetching mass message (massaviesti) failed", e);
            return ResponseEntity.status(500).body("Fetching mass message (massaviesti) failed");
        }
    }

    @GetMapping("/v1/viesti/{viestiTunniste}")
    public ResponseEntity<Object> getViesti(
            @PathVariable String viestiTunniste,
            HttpServletRequest request) {
        log.debug("Fetching viesti {}", viestiTunniste);
        try {
            var result = lahetysService.getViesti(request.getSession(false), viestiTunniste);
            return result.map(ResponseEntity::<Object>ok)
                    .orElseGet(() -> ResponseEntity.status(410).build());
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        } catch (Exception e) {
            log.error("Fetching viesti failed", e);
            return ResponseEntity.status(500).body("Fetching viesti failed");
        }
    }

    @GetMapping("/v1/palvelut")
    public ResponseEntity<Object> getLahettavatPalvelut(HttpServletRequest request) {
        log.debug("Fetching posting (lähettävät) services");
        try {
            var result = lahetysService.getLahettavatPalvelut(request.getSession(false));
            return ResponseEntity.ok(result);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        } catch (Exception e) {
            log.error("Fetching posting (lähettävät) services failed", e);
            return ResponseEntity.status(500).body("Fetching posting (lähettävät) services failed");
        }
    }
}
