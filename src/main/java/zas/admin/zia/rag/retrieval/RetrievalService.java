package zas.admin.zia.rag.retrieval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import zas.admin.zia.rag.config.properties.RetrievalProperties;
import zas.admin.zia.rag.rerank.Reranker;
import zas.admin.zia.rag.web.dto.RetrievalConfig;
import zas.admin.zia.rag.web.dto.SearchMode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates document retrieval from the vector store, the BM25 full-text search and/or a plain
 * SQL filter, combining and reranking the results according to the requested {@link RetrievalConfig}.
 *
 * <p>BM25 results below the minimum BM25 score and reranked documents below the minimum reranker score are
 * dropped (thresholds from the request, else {@link RetrievalProperties}). When the reranker fails, the
 * documents keep their initial order (vector results first in HYBRID mode), simply truncated to
 * {@code topK}, and the failure is logged.
 */
@Service
public class RetrievalService {

    private static final Logger LOG = LoggerFactory.getLogger(RetrievalService.class);

    private final VectorStore vectorStore;
    private final PgVectorJdbcRetriever pgVectorJdbcRetriever;
    private final BM25Retriever bm25Retriever;
    private final FilterRetriever filterRetriever;
    private final Reranker reranker;
    private final SearchFilterResolver searchFilterResolver;
    private final RetrievalProperties properties;

    public RetrievalService(VectorStore vectorStore,
                            PgVectorJdbcRetriever pgVectorJdbcRetriever,
                            BM25Retriever bm25Retriever,
                            FilterRetriever filterRetriever,
                            Reranker reranker,
                            SearchFilterResolver searchFilterResolver,
                            RetrievalProperties properties) {
        this.vectorStore = vectorStore;
        this.pgVectorJdbcRetriever = pgVectorJdbcRetriever;
        this.bm25Retriever = bm25Retriever;
        this.filterRetriever = filterRetriever;
        this.reranker = reranker;
        this.searchFilterResolver = searchFilterResolver;
        this.properties = properties;
    }

    /**
     * Retrieves the most relevant documents for the given question and configuration.
     *
     * <p>In {@link SearchMode#FILTER} mode, the question and the reranker are ignored and at least one
     * filter is required; the question is mandatory in every other mode.
     *
     * @throws InvalidSearchFilterException when the requested filters or question are invalid
     */
    public List<Document> retrieve(String question, RetrievalConfig config) {
        return retrieve(question, config, null);
    }

    /**
     * Same as {@link #retrieve(String, RetrievalConfig)}, the private documents of {@code userId} being
     * visible on top of the public ones.
     *
     * @param userId user ({@code users.uuid}) whose private documents are also visible, or {@code null}
     */
    public List<Document> retrieve(String question, RetrievalConfig config, String userId) {
        RetrievalConfig effectiveConfig = config == null ? RetrievalConfig.defaults() : config;
        SearchFilter filter = searchFilterResolver.resolve(effectiveConfig, userId);
        int topK = effectiveConfig.topK();
        SearchMode mode = effectiveConfig.searchMode();
        Double minRerankScore = effectiveConfig.minRerankScore() != null
                ? effectiveConfig.minRerankScore() : properties.minRerankScore();
        Double minBm25Score = effectiveConfig.minBm25Score() != null
                ? effectiveConfig.minBm25Score() : properties.minBm25Score();
        LOG.debug("Retrieval: mode={}, topK={}, userId present={}, minRerankScore={}, minBm25Score={}",
                mode, topK, filter.hasUserId(), minRerankScore, minBm25Score);

        if (mode.requiresQuestion() && !StringUtils.hasText(question)) {
            throw new InvalidSearchFilterException("question must not be blank unless searchMode is FILTER");
        }

        return switch (mode) {
            case FILTER -> {
                if (filter.isEmpty()) {
                    throw new InvalidSearchFilterException(
                            "FILTER mode requires at least one metadata filter or contentContains");
                }
                yield filterRetriever.retrieve(filter, topK);
            }
            case VECTOR -> rerankOrTruncate(question, retrieveFromVectorStore(question, filter, topK),
                    topK, effectiveConfig.useReranker(), minRerankScore);
            case BM25 -> rerankOrTruncate(question, retrieveBm25(question, filter, topK, minBm25Score),
                    topK, effectiveConfig.useReranker(), minRerankScore);
            case HYBRID -> {
                List<Document> combined = unionDeduplicate(
                        retrieveFromVectorStore(question, filter, topK),
                        retrieveBm25(question, filter, topK, minBm25Score));
                // Vector and BM25 scores are not comparable, so reranking is mandatory here.
                yield rerankOrTruncate(question, combined, topK, true, minRerankScore);
            }
        };
    }

    private List<Document> rerankOrTruncate(String question, List<Document> documents, int topK, boolean rerank,
                                            Double minRerankScore) {
        if (!rerank) {
            return truncate(documents, topK);
        }
        List<Document> reranked;
        try {
            reranked = reranker.rerank(question, documents, topK);
        } catch (RuntimeException e) {
            LOG.warn("Reranker unavailable, falling back to the initial order of {} documents: {}",
                    documents.size(), describe(e));
            return truncate(documents, topK);
        }
        return atLeast(reranked, minRerankScore);
    }

    /** Failure summary without the response body (it could echo the documents sent to the reranker). */
    private static String describe(RuntimeException e) {
        if (e instanceof RestClientResponseException responseException) {
            return e.getClass().getSimpleName() + " (HTTP " + responseException.getStatusCode().value() + ")";
        }
        return e.toString();
    }

    private List<Document> retrieveBm25(String question, SearchFilter filter, int topK, Double minBm25Score) {
        return atLeast(bm25Retriever.retrieve(question, filter, topK), minBm25Score);
    }

    /** Keeps the documents whose score is at least {@code minScore} (every document when {@code minScore} is null). */
    static List<Document> atLeast(List<Document> documents, Double minScore) {
        if (minScore == null) {
            return documents;
        }
        return documents.stream()
                .filter(document -> document.getScore() != null && document.getScore() >= minScore)
                .toList();
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

    /**
     * Spring AI filter: visible documents ('user_uuid' empty or JSON null, plus {@code userId} when set) AND
     * every metadata filter.
     *
     * <p>Limitation: the PgVector jsonpath converter cannot express a missing key, so documents without any
     * {@code user_uuid} metadata are only matched by the SQL retrievers ({@link SqlWhereClause}), not by this
     * expression (unchanged behaviour; the ingestion writes {@code user_uuid}).
     */
    static Filter.Expression buildFilter(SearchFilter filter) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();

        List<Object> visibleOwners = new ArrayList<>();
        visibleOwners.add("");
        visibleOwners.add(null);
        if (filter.hasUserId()) {
            visibleOwners.add(filter.userId());
        }
        FilterExpressionBuilder.Op expression = b.in(SearchFilter.USER_UUID_KEY, visibleOwners);

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
