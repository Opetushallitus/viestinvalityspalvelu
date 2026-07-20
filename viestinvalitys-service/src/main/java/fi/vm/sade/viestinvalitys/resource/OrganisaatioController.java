package fi.vm.sade.viestinvalitys.resource;

import fi.vm.sade.viestinvalitys.security.SecurityOperations;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class OrganisaatioController {

    @GetMapping("/v1/organisaatiot/oikeudet")
    public ResponseEntity<Object> getOrganisaatiot(HttpServletRequest request) {
        log.debug("Fetching käyttöoikeus organisations");
        try {
            var secOps = new SecurityOperations(request.getSession(false));
            var orgs = secOps.getCasOrganisaatiot();
            return ResponseEntity.ok(orgs);
        } catch (Exception e) {
            log.error("Fetching organisations failed", e);
            return ResponseEntity.status(500).body("Fetching organisations failed");
        }
    }
}
