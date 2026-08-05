package zas.admin.zia.rag.config;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zas.admin.zia.rag.config.properties.EmbeddingProperties;

@Configuration
@EnableConfigurationProperties({
        EmbeddingProperties.class
})
public class AIConfig {

    @Bean
    public OpenAiEmbeddingModel embeddingModel(EmbeddingProperties embeddingProperties) {
        return OpenAiEmbeddingModel.builder()
                .metadataMode(MetadataMode.EMBED)
                .options(OpenAiEmbeddingOptions.builder()
                        .baseUrl(embeddingProperties.baseUrl())
                        .apiKey(embeddingProperties.apiKey())
                        .model(embeddingProperties.model())
                        .build())
                .build();
    }
}
