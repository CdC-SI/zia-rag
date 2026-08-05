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
import zas.admin.zia.rag.rerank.Reranker;
import zas.admin.zia.rag.web.dto.RetrievalConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates document retrieval from the vector store and, optionally, the BM25 full-text search,
 * combining and reranking the results according to the requested {@link RetrievalConfig}.
 */
@Service
public class RetrievalService {

    private static final Logger LOG = LoggerFactory.getLogger(RetrievalService.class);

    private final VectorStore vectorStore;
    private final BM25Retriever bm25Retriever;
    private final Reranker reranker;

    public RetrievalService(VectorStore vectorStore, BM25Retriever bm25Retriever, Reranker reranker) {
        this.vectorStore = vectorStore;
        this.bm25Retriever = bm25Retriever;
        this.reranker = reranker;
    }

    /**
     * Retrieves the most relevant documents for the given question and configuration.
     */
    public List<Document> retrieve(String question, RetrievalConfig config) {
        RetrievalConfig effectiveConfig = normalize(config);
        int topK = effectiveConfig.topK();

        List<Document> vectorDocuments = retrieveFromVectorStore(question, effectiveConfig);

        if (Boolean.FALSE.equals(effectiveConfig.useBm25())) {
            if (Boolean.TRUE.equals(effectiveConfig.useReranker())) {
                return reranker.rerank(question, vectorDocuments, topK);
            }
            return truncate(vectorDocuments, topK);
        }

        List<Document> bm25Documents = bm25Retriever.retrieve(question, effectiveConfig.sources(), topK);
        List<Document> combined = unionDeduplicate(vectorDocuments, bm25Documents);

        // Vector and BM25 scores are not comparable, so reranking is mandatory here.
        return reranker.rerank(question, combined, topK);
    }

    private RetrievalConfig normalize(RetrievalConfig config) {
        RetrievalConfig base = config == null ? RetrievalConfig.defaults() : config;
        boolean forceReranker = base.useReranker() || base.useBm25();
        if (forceReranker == base.useReranker()) {
            return base;
        }
        LOG.debug("Forcing reranker because BM25 is combined with the vector search");
        return new RetrievalConfig(base.topK(), true, true, base.sources());
    }

    private List<Document> retrieveFromVectorStore(String question, RetrievalConfig config) {
        VectorStoreDocumentRetriever.Builder builder = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(config.topK());

        if (config.hasSourceFilter()) {
            builder.filterExpression(buildSourceFilter(config.sources()));
        }

        return builder.build().retrieve(Query.builder().text(question).build());
    }

    private Filter.Expression buildSourceFilter(List<String> sources) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        return b.in("source", sources.toArray()).build();
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
