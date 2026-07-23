package fi.vm.sade.viestinvalitys.resource;

import fi.vm.sade.viestinvalitys.dto.Kontakti;
import fi.vm.sade.viestinvalitys.dto.LuoLahetysRequest;
import fi.vm.sade.viestinvalitys.dto.LuoViestiRequest;
import fi.vm.sade.viestinvalitys.dto.Maski;
import fi.vm.sade.viestinvalitys.lahetys.audit.AuditLogService;
import fi.vm.sade.viestinvalitys.security.SecurityOperations;
import fi.vm.sade.viestinvalitys.service.LahetysService;
import fi.vm.sade.viestinvalitys.service.LahetysWriteService;
import fi.vm.sade.viestinvalitys.validation.LahetysMetadata;
import fi.vm.sade.viestinvalitys.validation.LahetysValidator;
import fi.vm.sade.viestinvalitys.validation.LiiteMetadata;
import fi.vm.sade.viestinvalitys.util.LanguageDetection;
import fi.vm.sade.viestinvalitys.validation.ParametriUtil;
import fi.vm.sade.viestinvalitys.validation.ViestiValidator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
public class LahetysController {

    // High-priority (KORKEA) rate limit: PRIORITEETTI_KORKEA_RATELIMIT_VIESTIA_SEKUNNISSA (=1) *
    // PRIORITEETTI_KORKEA_RATELIMIT_AIKAIKKUNA_SEKUNTIA (=5), mirroring the vastaanotto lambda.
    private static final int RATELIMIT_AIKAIKKUNA_SEKUNTIA = 5;
    private static final int RATELIMIT_VIESTEJA_AIKAIKKUNASSA = 5;
    private static final String VIESTI_RATELIMIT_VIRHE = "Liikaa korkean prioriteetin lähetyspyyntöjä";

    private final LahetysService lahetysService;
    private final LahetysWriteService lahetysWriteService;
    // Audit is best-effort: a logging failure must not fail an already-succeeded create.
    private final ObjectProvider<AuditLogService> auditLogService;

    // In non-PRODUCTION mode the rate limiter can be bypassed with the disableRateLimiter param.
    @Value("${viestinvalitys.mode:PRODUCTION}")
    private String mode;

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
            UUID lahetysTunniste = lahetysWriteService.tallennaLahetys(
                    body.otsikko(), body.lahettavaPalvelu(), body.lahettavanVirkailijanOid(),
                    toKontakti(body.lahettaja()), body.replyTo(), body.prioriteetti().toUpperCase(Locale.ROOT),
                    secOps.getUsername(), body.sailytysaika());
            bestEffortAudit(a -> a.logCreateLahetys(lahetysTunniste));
            return ResponseEntity.ok(Map.of("lahetysTunniste", lahetysTunniste.toString()));
        } catch (Exception e) {
            log.error("Lähetyksen luonti epäonnistui", e);
            return ResponseEntity.status(500)
                    .body(Map.of("validointiVirheet", List.of("Lähetyksen luonti epäonnistui")));
        }
    }

    @PostMapping(
            path = "/v1/viestit",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> luoViesti(
            @RequestBody LuoViestiRequest body,
            @RequestParam(name = "disableRateLimiter", defaultValue = "false") boolean disableRateLimiter,
            HttpServletRequest request) {
        var secOps = new SecurityOperations(request.getSession(false));
        if (!secOps.hasSendRights()) {
            return ResponseEntity.status(403).build();
        }
        String identiteetti = secOps.getUsername();

        // rate limit high-priority (KORKEA) requests per sender (checked on the request's own
        // prioriteetti field, like the lambda; bypassable outside PRODUCTION for tests)
        boolean korkeaPrioriteetti =
                body.prioriteetti() != null && "korkea".equalsIgnoreCase(body.prioriteetti());
        if (("PRODUCTION".equals(mode) || !disableRateLimiter) && korkeaPrioriteetti) {
            int maara = lahetysWriteService.korkeanPrioriteetinViestienMaara(
                    identiteetti, RATELIMIT_AIKAIKKUNA_SEKUNTIA);
            if (maara + 1 > RATELIMIT_VIESTEJA_AIKAIKKUNASSA) {
                return ResponseEntity.status(429).body(Map.of("validointiVirheet", List.of(VIESTI_RATELIMIT_VIRHE)));
            }
        }

        // resolve the target Lahetys (owner + priority) so the validator can check existence/ownership
        Optional<LahetysMetadata> lahetysMetadata = ParametriUtil.asUUID(body.lahetysTunniste())
                .flatMap(lahetysWriteService::haeLahetysMetadata);

        // liitteet are not supported yet, so no LiiteMetadata is available
        var virheet = ViestiValidator.validateViesti(
                body, lahetysMetadata, Map.<UUID, LiiteMetadata>of(), identiteetti);
        if (!virheet.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("validointiVirheet", virheet));
        }
        try {
            // idempotency: return the previously saved Viesti instead of creating (and sending) a duplicate
            String idempotencyKey = body.idempotencyKey();
            if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
                var olemassa = lahetysWriteService.haeOlemassaOlevaViesti(identiteetti, idempotencyKey);
                if (olemassa.isPresent()) {
                    return ResponseEntity.ok(Map.of(
                            "viestiTunniste", olemassa.get().viestiTunniste().toString(),
                            "lahetysTunniste", olemassa.get().lahetysTunniste().toString()));
                }
            }

            // detect language from content when kielet is omitted (like the lambda)
            Set<String> kielet = (body.kielet() == null || body.kielet().isEmpty())
                    ? LanguageDetection.tunnistaKieli(body.sisalto())
                    : new LinkedHashSet<>(body.kielet());

            var saved = lahetysWriteService.tallennaViesti(
                    body.otsikko(),
                    body.sisalto(),
                    body.sisallonTyyppi().toUpperCase(Locale.ROOT),
                    kielet,
                    maskitToMap(body.maskit()),
                    body.lahettavanVirkailijanOid(),
                    toKontakti(body.lahettaja()),
                    body.replyTo(),
                    body.vastaanottajat().stream().map(LahetysController::toKontakti).toList(),
                    body.lahettavaPalvelu(),
                    ParametriUtil.asUUID(body.lahetysTunniste()).orElse(null),
                    body.prioriteetti() == null ? null : body.prioriteetti().toUpperCase(Locale.ROOT),
                    kayttooikeudet(body.kayttooikeusRajoitukset()),
                    body.metadata() == null ? Map.<String, List<String>>of() : body.metadata(),
                    identiteetti,
                    body.sailytysaika() == null ? 0 : body.sailytysaika(),
                    idempotencyKey);
            bestEffortAudit(a -> a.logCreateViesti(saved.viestiTunniste(), saved.lahetysTunniste()));
            return ResponseEntity.ok(Map.of(
                    "viestiTunniste", saved.viestiTunniste().toString(),
                    "lahetysTunniste", saved.lahetysTunniste().toString()));
        } catch (Exception e) {
            log.error("Viestin luonti epäonnistui", e);
            return ResponseEntity.status(500)
                    .body(Map.of("validointiVirheet", List.of("Viestin luonti epäonnistui")));
        }
    }

    private void bestEffortAudit(Consumer<AuditLogService> op) {
        try {
            auditLogService.ifAvailable(op);
        } catch (Exception e) {
            log.warn("Audit-lokitus epäonnistui", e);
        }
    }

    private static LahetysWriteService.Kontakti toKontakti(Kontakti kontakti) {
        return kontakti == null ? null : new LahetysWriteService.Kontakti(kontakti.nimi(), kontakti.sahkopostiOsoite());
    }

    private static Map<String, String> maskitToMap(List<Maski> maskit) {
        if (maskit == null) {
            return Map.of();
        }
        Map<String, String> tulos = new LinkedHashMap<>();
        maskit.forEach(maski -> tulos.put(maski.salaisuus(), maski.maski()));
        return tulos;
    }

    private static Set<LahetysWriteService.Kayttooikeus> kayttooikeudet(
            List<fi.vm.sade.viestinvalitys.dto.Kayttooikeus> rajoitukset) {
        if (rajoitukset == null) {
            return Set.of();
        }
        return rajoitukset.stream()
                .map(k -> new LahetysWriteService.Kayttooikeus(k.oikeus(), k.organisaatio()))
                .collect(Collectors.toSet());
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
