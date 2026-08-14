package fi.vm.sade.viestinvalitys.service;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * Fetches the virkailija's asiointikieli from oppijanumerorekisteri. The endpoint requires an
 * authenticated caller with oppijanumerorekisteri read rights, so the requests are authenticated
 * with the viestinvalityspalvelu palvelukäyttäjä using the CAS service ticket flow (TGT -> service
 * ticket -> j_spring_cas_security_check -> JSESSIONID) like the old raportointi lambda's CasClient.
 * The session cookie is reused across requests and refreshed once when it no longer works.
 */
@Slf4j
@Service
public class HenkiloService {

    private static final String CALLER_ID = "1.2.246.562.10.00000000001.viestinvalityspalvelu";

    @Value("${host.virkailija}")
    private String virkailijaUrl;

    @Value("${viestinvalitys.jarjestelmatunnus.cas-username:}")
    private String casUsername;

    @Value("${viestinvalitys.jarjestelmatunnus.cas-password:}")
    private String casPassword;

    private final RestTemplate restTemplate;
    private final AtomicReference<String> sessionCookie = new AtomicReference<>();

    public HenkiloService() {
        var factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(factory);
    }

    public String getAsiointikieli(String username) {
        try {
            String session = sessionCookie.get();
            if (session == null) {
                session = login();
            }
            try {
                return fetchAsiointikieli(username, session);
            } catch (SessionExpiredException e) {
                return fetchAsiointikieli(username, login());
            }
        } catch (Exception e) {
            log.warn("Fetching asiointikieli for user {} failed: {}", username, e.getMessage());
        }
        return "fi";
    }

    private String fetchAsiointikieli(String username, String session) {
        String url = virkailijaUrl + "/oppijanumerorekisteri-service/henkilo/" + username + "/asiointiKieli";
        var headers = new HttpHeaders();
        headers.set("Caller-Id", CALLER_ID);
        headers.set(HttpHeaders.COOKIE, "JSESSIONID=" + session);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.GET, new HttpEntity<>(headers),
            new ParameterizedTypeReference<Map<String, Object>>() {});
        if (response.getStatusCode().is3xxRedirection()) {
            throw new SessionExpiredException();
        }
        Map<String, Object> body = response.getBody();
        if (body != null && body.get("kieliKoodi") instanceof String kieliKoodi) {
            return kieliKoodi;
        }
        return "fi";
    }

    private String login() {
        String tgtUrl = createTicketGrantingTicket();
        String serviceTicket = createServiceTicket(tgtUrl);
        String session = createOnrSession(serviceTicket);
        sessionCookie.set(session);
        return session;
    }

    private String createTicketGrantingTicket() {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("username", casUsername);
        form.add("password", casPassword);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Caller-Id", CALLER_ID);
        ResponseEntity<String> response = restTemplate.postForEntity(
            virkailijaUrl + "/cas/v1/tickets", new HttpEntity<>(form, headers), String.class);
        var location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
        if (location == null) {
            throw new IllegalStateException("CAS ticket granting ticket response had no location header");
        }
        return location;
    }

    private String createServiceTicket(String tgtUrl) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("service", virkailijaUrl + "/oppijanumerorekisteri-service/j_spring_cas_security_check");
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Caller-Id", CALLER_ID);
        String serviceTicket = restTemplate.postForObject(tgtUrl, new HttpEntity<>(form, headers), String.class);
        if (serviceTicket == null || serviceTicket.isBlank()) {
            throw new IllegalStateException("CAS service ticket response was empty");
        }
        return serviceTicket;
    }

    private String createOnrSession(String serviceTicket) {
        var headers = new HttpHeaders();
        headers.set("Caller-Id", CALLER_ID);
        ResponseEntity<String> response = restTemplate.exchange(
            virkailijaUrl + "/oppijanumerorekisteri-service/j_spring_cas_security_check?ticket=" + serviceTicket,
            HttpMethod.GET, new HttpEntity<>(headers), String.class);
        for (String cookie : response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE)) {
            if (cookie.startsWith("JSESSIONID=")) {
                return cookie.substring("JSESSIONID=".length(), cookie.indexOf(';'));
            }
        }
        throw new IllegalStateException("CAS security check response had no JSESSIONID cookie");
    }

    private static class SessionExpiredException extends RuntimeException {}
}
