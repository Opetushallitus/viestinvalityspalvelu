package fi.vm.sade.viestinvalitys.resource;

import fi.vm.sade.viestinvalitys.service.LokalisointiService;
import fi.vm.sade.viestinvalitys.service.LokalisointiService.Localisation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LokalisointiController {

    private final LokalisointiService lokalisointiService;

    @GetMapping("/viestinvalityspalvelu/v1/localisations")
    public List<Localisation> getLocalisations() {
        return lokalisointiService.getLocalisations();
    }
}
