package fi.vm.sade.viestinvalitys.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
public class HenkiloService {

    @Value("${host.virkailija}")
    private String virkailijaUrl;

    public String getAsiointikieli(String username) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = virkailijaUrl + "/oppijanumerorekisteri-service/henkilo/" + username + "/asiointiKieli";
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null && response.containsKey("kieliKoodi")) {
                return (String) response.get("kieliKoodi");
            }
        } catch (Exception e) {
            log.warn("Fetching asiointikieli for user {} failed: {}", username, e.getMessage());
        }
        return "fi";
    }
}
