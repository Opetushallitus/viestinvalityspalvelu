package fi.vm.sade.viestinvalitys.security;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SecurityOperations {

    public static final String SECURITY_ROOLI_LAHETYS = "APP_VIESTINVALITYS_LAHETYS";
    public static final String SECURITY_ROOLI_KATSELU = "APP_VIESTINVALITYS_KATSELU";
    public static final String SECURITY_ROOLI_PAAKAYTTAJA = "APP_VIESTINVALITYS_OPH_PAAKAYTTAJA";
    public static final String OPH_ORGANISAATIO_OID = "1.2.246.562.10.00000000001";
    private static final Pattern KAYTTOOIKEUS_PATTERN = Pattern.compile("^(.*)_([0-9]+(\\.[0-9]+)+)$");
    private static final String ROLE_PREFIX = "ROLE_";

    private final HttpSession session;

    public SecurityOperations(HttpSession session) {
        this.session = session;
    }

    public boolean hasReadRights() {
        var authorities = getAuthorities();
        return authorities.stream().anyMatch(a ->
                a.contains(SECURITY_ROOLI_KATSELU) ||
                a.contains(SECURITY_ROOLI_PAAKAYTTAJA) ||
                a.contains(SECURITY_ROOLI_LAHETYS));
    }

    public boolean hasSendRights() {
        return getAuthorities().stream().anyMatch(a ->
                a.contains(SECURITY_ROOLI_LAHETYS) ||
                a.contains(SECURITY_ROOLI_PAAKAYTTAJA));
    }

    public boolean isPaakayttaja() {
        return getAuthorities().stream().anyMatch(a -> a.contains(SECURITY_ROOLI_PAAKAYTTAJA));
    }

    public List<String> getCasOrganisaatiot() {
        return getAuthorities().stream()
                .filter(a -> a.startsWith("APP_VIESTINVALITYS"))
                .map(this::extractOrganisaatio)
                .filter(o -> o != null && !o.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    public String getUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private List<String> getAuthorities() {
        return SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith(ROLE_PREFIX) ? a.substring(ROLE_PREFIX.length()) : a)
                .collect(Collectors.toList());
    }

    private String extractOrganisaatio(String authority) {
        Matcher m = KAYTTOOIKEUS_PATTERN.matcher(authority);
        if (m.matches()) {
            return m.group(2);
        }
        return null;
    }
}
