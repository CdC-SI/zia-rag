package zas.admin.zia.rag.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private String run(String incoming, AtomicReference<String> seenInChain, MockHttpServletResponse response)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/retrieval/documents");
        if (incoming != null) {
            request.addHeader(CorrelationIdFilter.HEADER, incoming);
        }
        filter.doFilter(request, response, (req, res) -> seenInChain.set(MDC.get(CorrelationIdFilter.MDC_KEY)));
        return response.getHeader(CorrelationIdFilter.HEADER);
    }

    @Test
    void validIncomingId_isKeptInMdcAndResponse_thenCleared() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();

        String returned = run("abc-123", seen, new MockHttpServletResponse());

        assertThat(seen.get()).isEqualTo("abc-123");
        assertThat(returned).isEqualTo("abc-123");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void missingOrInvalidId_isReplacedByAGeneratedOne() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();

        String generated = run(null, seen, new MockHttpServletResponse());
        assertThat(generated).hasSize(36).isEqualTo(seen.get());

        String replaced = run("bad\nid", seen, new MockHttpServletResponse());
        assertThat(replaced).isNotEqualTo("bad\nid").hasSize(36);
    }

    @Test
    void filter_isRegisteredBeforeSpringSecurity() {
        int order = new ObservabilityConfig().correlationIdFilter().getOrder();

        assertThat(order).isEqualTo(Ordered.HIGHEST_PRECEDENCE)
                .isLessThan(SecurityFilterProperties.DEFAULT_FILTER_ORDER);
    }

    @Test
    void restClientInterceptor_propagatesTheCurrentId() {
        RestClient.Builder builder = RestClient.builder().requestInterceptor(CorrelationIdFilter.restClientInterceptor());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://reranker/score"))
                .andExpect(header(CorrelationIdFilter.HEADER, "abc-123"))
                .andRespond(withSuccess());

        MDC.put(CorrelationIdFilter.MDC_KEY, "abc-123");
        builder.build().post().uri("http://reranker/score").retrieve().toBodilessEntity();

        server.verify();
    }
}
