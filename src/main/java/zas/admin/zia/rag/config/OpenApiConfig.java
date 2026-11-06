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
                        + "with vector, BM25 or hybrid search, reranking, metadata and content filtering."))
public class OpenApiConfig {
}
