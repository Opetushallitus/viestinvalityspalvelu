package fi.vm.sade.viestinvalitys.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Fetches UI localisations from the shared lokalisointi service server-side (the same way
 * tiedotuspalvelu does), so the browser never has to call lokalisointi cross-origin. Results are
 * cached in memory for {@link #CACHE_TTL_MS}. On failure the last successful result is served if
 * available, otherwise an empty list (the UI then falls back to its bundled default messages).
 */
@Slf4j
@Service
public class LokalisointiService {

    private static final String CATEGORY = "viestinvalitys";
    private static final long CACHE_TTL_MS = 60 * 60 * 1000L; // 1 hour

    @Value("${host.virkailija}")
    private String virkailijaUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final AtomicReference<Cached> cache = new AtomicReference<>();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Localisation(String key, String locale, String value) {}

    private record Cached(List<Localisation> localisations, long fetchedAt) {}

    public List<Localisation> getLocalisations() {
        Cached cached = cache.get();
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.fetchedAt() < CACHE_TTL_MS) {
            return cached.localisations();
        }
        try {
            List<Localisation> fresh = fetchFromLokalisointi();
            cache.set(new Cached(fresh, now));
            return fresh;
        } catch (Exception e) {
            log.warn("Fetching localisations from lokalisointi failed: {}", e.getMessage());
            return cached != null ? cached.localisations() : List.of();
        }
    }

    private List<Localisation> fetchFromLokalisointi() {
        String url = virkailijaUrl + "/lokalisointi/api/v1/localisation?category=" + CATEGORY;
        List<Localisation> body =
                restTemplate
                        .exchange(
                                url,
                                HttpMethod.GET,
                                null,
                                new ParameterizedTypeReference<List<Localisation>>() {})
                        .getBody();
        return body != null ? body : List.of();
    }
}
