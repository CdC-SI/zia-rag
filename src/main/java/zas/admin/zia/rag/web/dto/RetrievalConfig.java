package zas.admin.zia.rag.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

import java.util.List;

@Schema(description = "Configuration used to optimize the retrieval of documents.")
public record RetrievalConfig(

        @Schema(
                description = "Maximum number of documents to return.",
                defaultValue = "10",
                example = "10")
        @Positive(message = "topK must be a positive number")
        Integer topK,

        @Schema(
                description = "Whether to rerank the retrieved documents with the remote reranker. "
                        + "Automatically forced to true when BM25 is combined with the vector search, "
                        + "because their scores are not comparable.",
                defaultValue = "false")
        Boolean useReranker,

        @Schema(
                description = "Whether to run a BM25 full-text search in addition to the classic "
                        + "vector similarity search. When enabled, the reranker is forced.",
                defaultValue = "false")
        Boolean useBm25,

        @Schema(
                description = "Optional list of source values used to filter documents by their "
                        + "'source' metadata. When empty or null, no source filter is applied.",
                example = "[\"handbook\", \"faq\"]")
        List<String> sources
) {

    public static final int DEFAULT_TOP_K = 10;

    public RetrievalConfig {
        if (topK == null) {
            topK = DEFAULT_TOP_K;
        }
        if (useReranker == null) {
            useReranker = false;
        }
        if (useBm25 == null) {
            useBm25 = false;
        }
        if (sources == null) {
            sources = List.of();
        }
    }

    public static RetrievalConfig defaults() {
        return new RetrievalConfig(DEFAULT_TOP_K, false, false, List.of());
    }

    public boolean hasSourceFilter() {
        return sources != null && !sources.isEmpty();
    }
}
