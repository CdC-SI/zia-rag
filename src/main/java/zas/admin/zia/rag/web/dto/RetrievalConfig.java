package zas.admin.zia.rag.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.Map;

@Schema(description = "Configuration used to optimize the retrieval of documents.")
public record RetrievalConfig(

        @Schema(
                description = "Maximum number of documents to return. Defaults to "
                        + "zia.rag.retrieval.default-top-k (10 unless configured).",
                example = "10")
        @Positive(message = "topK must be a positive number")
        Integer topK,

        @Schema(
                description = "Whether to rerank the retrieved documents with the remote reranker. "
                        + "Automatically forced to true in HYBRID mode, because vector and BM25 scores "
                        + "are not comparable. Defaults to zia.rag.retrieval.default-use-reranker "
                        + "(false unless configured).")
        Boolean useReranker,

        @Schema(
                description = "Search strategy. VECTOR: vector similarity search only. BM25: full-text "
                        + "BM25 search only. HYBRID: both, unioned, deduplicated and reranked. FILTER: plain "
                        + "SQL query on metadataFilters/contentContains (at least one required), no "
                        + "embedding, question and useReranker ignored, ordered by id. "
                        + "Defaults to HYBRID when the deprecated 'useBm25' is true, to "
                        + "zia.rag.retrieval.default-search-mode (VECTOR unless configured) otherwise.")
        SearchMode searchMode,

        @Schema(
                description = "Optional metadata filters: metadata key → accepted values. A document "
                        + "matches when, for every key, its metadata value is one of the given values. "
                        + "Reserved keys (e.g. 'user_uuid') are rejected with 400.",
                example = "{\"source\": [\"handbook\", \"faq\"], \"lang\": [\"fr\"]}")
        Map<String, List<String>> metadataFilters,

        @Schema(
                description = "Optional text the document content must contain (case-insensitive "
                        + "substring). Combined with the metadata filters (AND).",
                example = "mot de passe")
        String contentContains,

        @Schema(
                description = "Deprecated: use searchMode=HYBRID. When true and searchMode is not set, "
                        + "the HYBRID mode is used.",
                defaultValue = "false",
                deprecated = true)
        @Deprecated
        Boolean useBm25,

        @Schema(
                description = "Deprecated: use metadataFilters.source. Values are merged into the "
                        + "'source' metadata filter.",
                example = "[\"handbook\", \"faq\"]",
                deprecated = true)
        @Deprecated
        List<String> sources,

        @Schema(
                description = "Optional minimum reranker relevance score: reranked documents scoring below are "
                        + "dropped. Overrides zia.rag.retrieval.min-rerank-score. Ignored when the reranker is "
                        + "not used or unavailable.",
                example = "0.3")
        Double minRerankScore,

        @Schema(
                description = "Optional minimum BM25 relevance score (BM25 and HYBRID modes): BM25 results "
                        + "scoring below are dropped before reranking. Overrides "
                        + "zia.rag.retrieval.min-bm25-score.",
                example = "1.5")
        Double minBm25Score
) {

    /**
     * {@code topK}, {@code useReranker} and {@code searchMode} stay {@code null} when omitted: the server defaults
     * ({@code zia.rag.retrieval.default-*}) are applied by the retrieval service.
     */
    public RetrievalConfig {
        if (useBm25 == null) {
            useBm25 = false;
        }
        if (searchMode == null && useBm25) {
            searchMode = SearchMode.HYBRID;
        }
        if (metadataFilters == null) {
            metadataFilters = Map.of();
        }
        if (sources == null) {
            sources = List.of();
        }
    }

    public static RetrievalConfig defaults() {
        return new RetrievalConfig(null, null, null, null, null, null, null, null, null);
    }
}
