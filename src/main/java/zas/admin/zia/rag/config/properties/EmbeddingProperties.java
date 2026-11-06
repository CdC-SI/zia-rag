package zas.admin.zia.rag.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zia.rag.embedding")
public record EmbeddingProperties(String apiKey, String model, String baseUrl, Integer dimensions) {
}
