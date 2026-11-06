package zas.admin.zia.rag.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Search strategy: VECTOR (similarity search only), BM25 (full-text search only), "
        + "HYBRID (both, combined and reranked) or FILTER (plain SQL on metadata/content filters, no "
        + "embedding, question not required).")
public enum SearchMode {
    VECTOR,
    BM25,
    HYBRID,
    FILTER;

    public boolean requiresQuestion() {
        return this != FILTER;
    }
}
