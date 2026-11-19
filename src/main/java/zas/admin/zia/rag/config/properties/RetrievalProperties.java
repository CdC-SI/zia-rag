package zas.admin.zia.rag.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import zas.admin.zia.rag.web.dto.SearchMode;

import java.util.List;

/**
 * Retrieval settings.
 *
 * @param reservedMetadataKeys metadata keys clients are not allowed to filter on (defaults to {@code user_uuid})
 * @param minRerankScore       default minimum reranker score of the returned documents, or {@code null} for none
 * @param minBm25Score         default minimum BM25 score of the BM25 results, or {@code null} for none
 * @param defaultTopK          number of documents returned when the request omits {@code topK} (defaults to 10)
 * @param defaultSearchMode    search mode used when the request omits it (defaults to {@code VECTOR}; FILTER is
 *                             not allowed, since it ignores the question)
 * @param defaultUseReranker   whether to rerank when the request omits {@code useReranker} (defaults to false)
 */
@ConfigurationProperties(prefix = "zia.rag.retrieval")
public record RetrievalProperties(List<String> reservedMetadataKeys, Double minRerankScore, Double minBm25Score,
                                  Integer defaultTopK, SearchMode defaultSearchMode, Boolean defaultUseReranker) {

    public static final List<String> DEFAULT_RESERVED_METADATA_KEYS = List.of("user_uuid");
    public static final int DEFAULT_TOP_K = 10;

    public RetrievalProperties {
        reservedMetadataKeys = reservedMetadataKeys == null
                ? DEFAULT_RESERVED_METADATA_KEYS
                : List.copyOf(reservedMetadataKeys);
        if (defaultTopK == null) {
            defaultTopK = DEFAULT_TOP_K;
        }
        if (defaultTopK <= 0) {
            throw new IllegalArgumentException("zia.rag.retrieval.default-top-k must be positive");
        }
        if (defaultSearchMode == null) {
            defaultSearchMode = SearchMode.VECTOR;
        }
        if (defaultSearchMode == SearchMode.FILTER) {
            throw new IllegalArgumentException("zia.rag.retrieval.default-search-mode cannot be FILTER");
        }
        if (defaultUseReranker == null) {
            defaultUseReranker = false;
        }
    }
}
