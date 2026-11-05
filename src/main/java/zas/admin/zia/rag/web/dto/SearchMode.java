package zas.admin.zia.rag.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Search strategy: VECTOR (similarity search only), BM25 (full-text search only) "
        + "or HYBRID (both, combined and reranked).")
public enum SearchMode {
    VECTOR,
    BM25,
    HYBRID
}
