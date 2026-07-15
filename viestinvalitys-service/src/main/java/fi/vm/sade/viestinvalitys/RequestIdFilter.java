package fi.vm.sade.viestinvalitys.logging;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;

public class RequestIdFilter implements Filter {

    public static final String REQUEST_ID_ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            var requestId = UUID.randomUUID().toString();
            MDC.put(REQUEST_ID_ATTRIBUTE, requestId);
            request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
            chain.doFilter(request, response);
        } finally {
            MDC.remove(REQUEST_ID_ATTRIBUTE);
        }
    }
}
