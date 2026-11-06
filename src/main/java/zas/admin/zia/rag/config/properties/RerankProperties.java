package zas.admin.zia.rag.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the remote reranking service.
 *
 * <p>Holds the connection settings ({@code baseUrl}, {@code apiKey}) and the reranker model, plus
 * the {@code truncatePromptTokens} limit forwarded to the {@code /score} endpoint. The payload
 * mapping itself lives in {@code zas.admin.zia.rag.rerank.RemoteReranker}.
 */
@ConfigurationProperties(prefix = "zia.rag.rerank")
public record RerankProperties(String baseUrl, String apiKey, String model, Integer truncatePromptTokens) {
}
