package fi.vm.sade.viestinvalitys.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Fetches the recursive child organisation oids of an organisation from organisaatio-service.
 * Results are cached in memory per oid for {@link #CACHE_TTL_MS} because the organisation
 * hierarchy changes rarely and the lookup is on the lähetys search hot path.
 */
@Slf4j
@Service
public class OrganisaatioService {

    private static final String CALLER_ID = "1.2.246.562.10.00000000001.viestinvalityspalvelu";
    private static final long CACHE_TTL_MS = 60 * 60 * 1000L; // 1 hour
    private static final Pattern ORGANISAATIO_OID_PATTERN =
            Pattern.compile("^1\\.2\\.246\\.562\\.(10|28|99|199|299)\\.\\d+$");

    @Value("${host.virkailija}")
    private String virkailijaUrl;

    private static final long PARENT_CACHE_TTL_MS = 24 * 60 * 60 * 1000L; // 1 day

    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final Map<String, Cached> parentCache = new ConcurrentHashMap<>();

    private record Cached(Set<String> childOids, long fetchedAt) {}

    public Set<String> getAllChildOids(String oid) {
        if (!ORGANISAATIO_OID_PATTERN.matcher(oid).matches()) {
            throw new IllegalArgumentException("Organisaation oid ei ole muodoltaan validi: " + oid);
        }
        Cached cached = cache.get(oid);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.fetchedAt() < CACHE_TTL_MS) {
            return cached.childOids();
        }
        Set<String> fresh = fetchChildOids(oid);
        cache.put(oid, new Cached(fresh, now));
        return fresh;
    }

    public Set<String> getParentOids(String oid) {
        if (!ORGANISAATIO_OID_PATTERN.matcher(oid).matches()) {
            log.warn("Organisaation oid {} ei ole validi, ei haeta parent-organisaatioita", oid);
            return Set.of();
        }
        Cached cached = parentCache.get(oid);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.fetchedAt() < PARENT_CACHE_TTL_MS) {
            return cached.childOids();
        }
        Set<String> fresh = fetchOids(virkailijaUrl + "/organisaatio-service/api/" + oid + "/parentoids");
        parentCache.put(oid, new Cached(fresh, now));
        return fresh;
    }

    private Set<String> fetchChildOids(String oid) {
        return fetchOids(virkailijaUrl + "/organisaatio-service/api/" + oid
                + "/childoids?rekursiivisesti=true&aktiiviset=true&suunnitellut=false&lakkautetut=false");
    }

    private Set<String> fetchOids(String url) {
        var headers = new HttpHeaders();
        headers.set("Caller-Id", CALLER_ID);
        headers.set("CSRF", CALLER_ID);
        headers.set(HttpHeaders.COOKIE, "CSRF=" + CALLER_ID);
        List<String> body = restTemplate
                .exchange(url, HttpMethod.GET, new HttpEntity<>(headers),
                        new ParameterizedTypeReference<List<String>>() {})
                .getBody();
        return body != null ? body.stream().collect(Collectors.toSet()) : Set.of();
    }
}
