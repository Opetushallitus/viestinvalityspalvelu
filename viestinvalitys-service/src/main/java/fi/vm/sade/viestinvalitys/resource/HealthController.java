package fi.vm.sade.viestinvalitys.resource;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/raportointi/v1/healthcheck")
    public ResponseEntity<Map<String, String>> healthcheck() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
