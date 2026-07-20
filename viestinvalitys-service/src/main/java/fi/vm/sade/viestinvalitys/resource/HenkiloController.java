package fi.vm.sade.viestinvalitys.resource;

import fi.vm.sade.viestinvalitys.service.HenkiloService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class HenkiloController {

    private final HenkiloService henkiloService;

    @GetMapping("/v1/asiointikieli")
    public ResponseEntity<Object> getAsiointikieli() {
        log.debug("Fetching asiointikieli");
        try {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            String lang = henkiloService.getAsiointikieli(username);
            return ResponseEntity.ok(lang);
        } catch (Exception e) {
            log.error("Fetching asiointikieli failed", e);
            return ResponseEntity.status(500).body("Fetching asiointikieli failed");
        }
    }
}
