package fi.vm.sade.viestinvalitys.logging;

import fi.vm.sade.viestinvalitys.security.OpintopolkuUserDetailsService.OpintopolkuUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.MDC;
import org.springframework.security.cas.authentication.CasAuthenticationToken;
import org.springframework.web.filter.GenericFilterBean;

public class RequestCallerFilter extends GenericFilterBean {

    public static final String CALLER_HENKILO_OID_ATTRIBUTE =
            RequestCallerFilter.class.getName() + ".callerHenkiloOid";

    @Override
    public void doFilter(
            ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        try {
            getCallerHenkiloOid(servletRequest)
                    .ifPresent(
                            oid -> {
                                MDC.put(CALLER_HENKILO_OID_ATTRIBUTE, oid);
                                servletRequest.setAttribute(CALLER_HENKILO_OID_ATTRIBUTE, oid);
                            });
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            MDC.remove(CALLER_HENKILO_OID_ATTRIBUTE);
        }
    }

    private Optional<String> getCallerHenkiloOid(ServletRequest servletRequest) {
        if (servletRequest instanceof HttpServletRequest request
                && request.getUserPrincipal() instanceof CasAuthenticationToken token
                && token.getUserDetails() instanceof OpintopolkuUserDetails userDetails) {
            return Optional.ofNullable(userDetails.getUsername());
        }
        return Optional.empty();
    }
}
