package zas.admin.zia.rag.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.Map;

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
                        + "Automatically forced to true in HYBRID mode, because vector and BM25 scores "
                        + "are not comparable.",
                defaultValue = "false")
        Boolean useReranker,

        @Schema(
                description = "Search strategy. VECTOR: vector similarity search only. BM25: full-text "
                        + "BM25 search only. HYBRID: both, unioned, deduplicated and reranked. "
                        + "Defaults to HYBRID when the deprecated 'useBm25' is true, VECTOR otherwise.",
                defaultValue = "VECTOR")
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
        if (searchMode == null) {
            searchMode = useBm25 ? SearchMode.HYBRID : SearchMode.VECTOR;
        }
        if (metadataFilters == null) {
            metadataFilters = Map.of();
        }
        if (sources == null) {
            sources = List.of();
        }
    }

    public static RetrievalConfig defaults() {
        return new RetrievalConfig(null, null, null, null, null, null, null);
    }
}
