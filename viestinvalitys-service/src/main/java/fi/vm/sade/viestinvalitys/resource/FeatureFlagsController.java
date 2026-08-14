package fi.vm.sade.viestinvalitys.resource;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FeatureFlagsController {

    @Value("${viestinvalitys.features.downloadViesti.enabled:false}")
    private boolean downloadViestiEnabled;

    @GetMapping("/v1/features")
    public Map<String, Boolean> getFeatureFlags() {
        return Map.of("downloadViestiEnabled", downloadViestiEnabled);
    }
}
