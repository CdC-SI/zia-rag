package zas.admin.zia.rag.rerank;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Reranks a list of candidate documents against a query and returns the most relevant ones.
 */
public interface Reranker {

    /**
     * Reranks the given documents and returns at most {@code topK} documents, most relevant first.
     *
     * @param query     the user query
     * @param documents the candidate documents (union of the retrievers)
     * @param topK      the maximum number of documents to return
     * @return the reranked, truncated list of documents
     */
    List<Document> rerank(String query, List<Document> documents, int topK);
}
