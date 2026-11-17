package zas.admin.zia.rag.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Request correlation: reads the {@value #HEADER} header (or generates an identifier when it is absent or
 * invalid), exposes it in the MDC under {@value #MDC_KEY} for the logs, returns it in the response header and
 * propagates it to the outgoing calls (see {@link #restClientInterceptor()}).
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlation";

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = resolve(request.getHeader(HEADER));
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** The incoming identifier when it is valid (no log injection), a new one otherwise. */
    static String resolve(String incoming) {
        if (incoming != null && VALID.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }

    /** Adds the current correlation identifier to the outgoing requests that do not carry one yet. */
    public static ClientHttpRequestInterceptor restClientInterceptor() {
        return (request, body, execution) -> {
            String correlationId = MDC.get(MDC_KEY);
            if (correlationId != null && !request.getHeaders().containsHeader(HEADER)) {
                request.getHeaders().set(HEADER, correlationId);
            }
            return execution.execute(request, body);
        };
    }
}
