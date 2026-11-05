package zas.admin.zia.rag.retrieval;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import zas.admin.zia.rag.rerank.Reranker;
import zas.admin.zia.rag.web.dto.RetrievalConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates document retrieval from the vector store and/or the BM25 full-text search,
 * combining and reranking the results according to the requested {@link RetrievalConfig}.
 */
@Service
public class RetrievalService {

    private final VectorStore vectorStore;
    private final PgVectorJdbcRetriever pgVectorJdbcRetriever;
    private final BM25Retriever bm25Retriever;
    private final Reranker reranker;
    private final SearchFilterResolver searchFilterResolver;

    public RetrievalService(VectorStore vectorStore,
                            PgVectorJdbcRetriever pgVectorJdbcRetriever,
                            BM25Retriever bm25Retriever,
                            Reranker reranker,
                            SearchFilterResolver searchFilterResolver) {
        this.vectorStore = vectorStore;
        this.pgVectorJdbcRetriever = pgVectorJdbcRetriever;
        this.bm25Retriever = bm25Retriever;
        this.reranker = reranker;
        this.searchFilterResolver = searchFilterResolver;
    }

    /**
     * Retrieves the most relevant documents for the given question and configuration.
     *
     * @throws InvalidSearchFilterException when the requested filters are invalid
     */
    public List<Document> retrieve(String question, RetrievalConfig config) {
        RetrievalConfig effectiveConfig = config == null ? RetrievalConfig.defaults() : config;
        SearchFilter filter = searchFilterResolver.resolve(effectiveConfig);
        int topK = effectiveConfig.topK();

        return switch (effectiveConfig.searchMode()) {
            case VECTOR -> rerankOrTruncate(question, retrieveFromVectorStore(question, filter, topK),
                    topK, effectiveConfig.useReranker());
            case BM25 -> rerankOrTruncate(question, bm25Retriever.retrieve(question, filter, topK),
                    topK, effectiveConfig.useReranker());
            case HYBRID -> {
                List<Document> combined = unionDeduplicate(
                        retrieveFromVectorStore(question, filter, topK),
                        bm25Retriever.retrieve(question, filter, topK));
                // Vector and BM25 scores are not comparable, so reranking is mandatory here.
                yield reranker.rerank(question, combined, topK);
            }
        };
    }

    private List<Document> rerankOrTruncate(String question, List<Document> documents, int topK, boolean rerank) {
        return rerank ? reranker.rerank(question, documents, topK) : truncate(documents, topK);
    }

    private List<Document> retrieveFromVectorStore(String question, SearchFilter filter, int topK) {
        // Spring AI filter expressions cannot express a substring match on the content.
        if (filter.hasContentFilter()) {
            return pgVectorJdbcRetriever.retrieve(question, filter, topK);
        }
        return VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(topK)
                .filterExpression(buildFilter(filter))
                .build()
                .retrieve(Query.builder().text(question).build());
    }

    static Filter.Expression buildFilter(SearchFilter filter) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();

        // Only public documents are returned: a document is public when its
        // 'user_uuid' metadata is empty or absent.
        FilterExpressionBuilder.Op expression = b.eq("user_uuid", "");

        for (Map.Entry<String, List<String>> entry : filter.metadata().entrySet()) {
            expression = b.and(expression, b.in(entry.getKey(), entry.getValue().toArray()));
        }
        return expression.build();
    }

    private List<Document> unionDeduplicate(List<Document> first, List<Document> second) {
        Map<String, Document> byId = new LinkedHashMap<>();
        for (Document document : first) {
            byId.putIfAbsent(document.getId(), document);
        }
        for (Document document : second) {
            byId.putIfAbsent(document.getId(), document);
        }
        return new ArrayList<>(byId.values());
    }

    private List<Document> truncate(List<Document> documents, int topK) {
        if (documents.size() <= topK) {
            return documents;
        }
        return new ArrayList<>(documents.subList(0, topK));
    }
}
