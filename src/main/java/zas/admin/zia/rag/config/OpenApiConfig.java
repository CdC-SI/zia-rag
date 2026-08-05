package zas.admin.zia.rag.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "ZIA RAG Retrieval API",
                version = "v1",
                description = "REST endpoints to retrieve relevant documents from a vector store, "
                        + "with optional BM25 full-text search, reranking and source filtering."))
public class OpenApiConfig {
}
