package zas.admin.zia.rag.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration for the remote reranking service.
 *
 * <p>Holds the connection settings ({@code baseUrl}, {@code apiKey}, timeouts) and the reranker model, plus
 * the {@code truncatePromptTokens} limit forwarded to the {@code /score} endpoint. The payload
 * mapping itself lives in {@code zas.admin.zia.rag.rerank.RemoteReranker}. When the reranker fails or times
 * out, the retrieval falls back to the initial order (see {@code RetrievalService}).
 */
@ConfigurationProperties(prefix = "zia.rag.rerank")
public record RerankProperties(String baseUrl, String apiKey, String model, Integer truncatePromptTokens,
                               Duration connectTimeout, Duration readTimeout) {

    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    public static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(10);

    public RerankProperties {
        connectTimeout = connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout;
        readTimeout = readTimeout == null ? DEFAULT_READ_TIMEOUT : readTimeout;
    }
}
