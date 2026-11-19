package zas.admin.zia.rag.retrieval;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.pgvector.PgVectorFilterExpressionConverter;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import zas.admin.zia.rag.config.properties.RetrievalProperties;
import zas.admin.zia.rag.rerank.Reranker;
import zas.admin.zia.rag.web.dto.RetrievalConfig;
import zas.admin.zia.rag.web.dto.SearchMode;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RetrievalServiceTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final PgVectorJdbcRetriever pgVectorJdbcRetriever = mock(PgVectorJdbcRetriever.class);
    private final BM25Retriever bm25Retriever = mock(BM25Retriever.class);
    private final FilterRetriever filterRetriever = mock(FilterRetriever.class);
    private final Reranker reranker = mock(Reranker.class);
    private final RetrievalService service = new RetrievalService(vectorStore, pgVectorJdbcRetriever,
            bm25Retriever, filterRetriever, reranker, new SearchFilterResolver(new RetrievalProperties(null, null, null, null, null, null)),
            new RetrievalProperties(null, null, null, null, null, null));

    private static Document doc(String id) {
        return Document.builder().id(id).text("content-" + id).build();
    }

    private static RetrievalConfig config(int topK, boolean useReranker, SearchMode mode) {
        return new RetrievalConfig(topK, useReranker, mode, null, null, null, null, null, null);
    }

    @Test
    void vectorOnlyWithoutReranker_truncatesToTopK() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc("a"), doc("b"), doc("c")));

        List<Document> result = service.retrieve("q", config(2, false, SearchMode.VECTOR));

        assertThat(result).extracting(Document::getId).containsExactly("a", "b");
        verifyNoInteractions(reranker, bm25Retriever, pgVectorJdbcRetriever);
    }

    @Test
    void vectorOnlyWithReranker_delegatesToReranker() {
        List<Document> vectorDocs = List.of(doc("a"), doc("b"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(vectorDocs);
        when(reranker.rerank(eq("q"), any(), anyInt())).thenReturn(List.of(doc("b")));

        List<Document> result = service.retrieve("q", config(5, true, SearchMode.VECTOR));

        assertThat(result).extracting(Document::getId).containsExactly("b");
        verify(reranker).rerank(eq("q"), eq(vectorDocs), eq(5));
        verifyNoInteractions(bm25Retriever);
    }

    @Test
    void hybrid_forcesRerankerAndUnionDeduplicatesById() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc("a"), doc("b")));
        when(bm25Retriever.retrieve(eq("q"), any(), eq(3)))
                .thenReturn(List.of(doc("b"), doc("c")));
        when(reranker.rerank(eq("q"), any(), anyInt())).thenReturn(List.of(doc("c")));

        // useReranker=false, but HYBRID must force the reranker.
        service.retrieve("q", config(3, false, SearchMode.HYBRID));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(reranker).rerank(eq("q"), captor.capture(), eq(3));
        assertThat(captor.getValue()).extracting(Document::getId).containsExactly("a", "b", "c");
        verify(bm25Retriever).retrieve(eq("q"), any(), eq(3));
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedUseBm25_meansHybrid() {
        RetrievalConfig config = new RetrievalConfig(3, false, null, null, null, true, null, null, null);

        assertThat(config.searchMode()).isEqualTo(SearchMode.HYBRID);
        assertThat(new RetrievalConfig(3, false, SearchMode.BM25, null, null, true, null, null, null).searchMode())
                .isEqualTo(SearchMode.BM25);
    }

    @Test
    void bm25OnlyWithoutReranker_skipsVectorStoreAndTruncates() {
        when(bm25Retriever.retrieve(eq("q"), any(), eq(2)))
                .thenReturn(List.of(doc("x"), doc("y")));

        List<Document> result = service.retrieve("q", config(2, false, SearchMode.BM25));

        assertThat(result).extracting(Document::getId).containsExactly("x", "y");
        verifyNoInteractions(vectorStore, pgVectorJdbcRetriever, reranker);
    }

    @Test
    void bm25OnlyWithReranker_delegatesToReranker() {
        List<Document> bm25Docs = List.of(doc("x"), doc("y"));
        when(bm25Retriever.retrieve(eq("q"), any(), eq(2))).thenReturn(bm25Docs);
        when(reranker.rerank(eq("q"), any(), anyInt())).thenReturn(List.of(doc("y")));

        List<Document> result = service.retrieve("q", config(2, true, SearchMode.BM25));

        assertThat(result).extracting(Document::getId).containsExactly("y");
        verify(reranker).rerank("q", bm25Docs, 2);
        verifyNoInteractions(vectorStore);
    }

    @Test
    void nullConfig_appliesDefaultsAndSkipsReranker() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc("a")));

        List<Document> result = service.retrieve("q", null);

        assertThat(result).extracting(Document::getId).containsExactly("a");
        verify(reranker, never()).rerank(any(), any(), anyInt());
    }

    @Test
    void metadataFilters_areAppliedToVectorStoreAndBm25() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(bm25Retriever.retrieve(any(), any(), anyInt())).thenReturn(List.of());
        when(reranker.rerank(any(), any(), anyInt())).thenReturn(List.of());

        service.retrieve("q", new RetrievalConfig(5, false, SearchMode.HYBRID,
                Map.of("lang", List.of("fr", "de")), null, null, List.of("faq"), null, null));

        ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(request.capture());
        String expression = request.getValue().getFilterExpression().toString();
        assertThat(expression).contains("user_uuid", "lang", "fr", "de", "source", "faq");

        ArgumentCaptor<SearchFilter> filter = ArgumentCaptor.forClass(SearchFilter.class);
        verify(bm25Retriever).retrieve(eq("q"), filter.capture(), eq(5));
        assertThat(filter.getValue().metadata())
                .containsEntry("lang", List.of("fr", "de"))
                .containsEntry("source", List.of("faq"));
    }

    @Test
    void buildFilter_combinesPublicOnlyAndEveryKeyWithAnd() {
        Filter.Expression expression = RetrievalService.buildFilter(
                new SearchFilter(Map.of("source", List.of("faq")), null));

        assertThat(expression.type()).isEqualTo(Filter.ExpressionType.AND);
        assertThat(((Filter.Expression) expression.right()).type()).isEqualTo(Filter.ExpressionType.IN);
    }

    @Test
    void userId_widensVisibilityToPrivateDocumentsInEveryRetriever() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(bm25Retriever.retrieve(any(), any(), anyInt())).thenReturn(List.of());
        when(reranker.rerank(any(), any(), anyInt())).thenReturn(List.of());

        service.retrieve("q", config(5, false, SearchMode.HYBRID), "u-1");

        ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(request.capture());
        Filter.Expression expression = request.getValue().getFilterExpression();
        assertThat(expression.type()).isEqualTo(Filter.ExpressionType.IN);
        assertThat(expression.toString()).contains("user_uuid", "u-1");

        ArgumentCaptor<SearchFilter> filter = ArgumentCaptor.forClass(SearchFilter.class);
        verify(bm25Retriever).retrieve(eq("q"), filter.capture(), eq(5));
        assertThat(filter.getValue().userId()).isEqualTo("u-1");
    }

    @Test
    void withoutUserId_onlyPublicDocumentsAreVisible() {
        Filter.Expression expression = RetrievalService.buildFilter(SearchFilter.NONE);

        assertThat(new PgVectorFilterExpressionConverter().convertExpression(expression))
                .isEqualTo("metadata::jsonb @@ '($.\"user_uuid\" == \"\" || $.\"user_uuid\" == null)'::jsonpath");
    }

    @Test
    void withUserId_publicAndOwnPrivateDocumentsAreVisible() {
        Filter.Expression expression = RetrievalService.buildFilter(
                new SearchFilter(Map.of("source", List.of("faq")), null, "u-1"));

        assertThat(new PgVectorFilterExpressionConverter().convertExpression(expression))
                .isEqualTo("metadata::jsonb @@ '($.\"user_uuid\" == \"\" || $.\"user_uuid\" == null "
                        + "|| $.\"user_uuid\" == \"u-1\") && ($.\"source\" == \"faq\")'::jsonpath");
    }

    @Test
    void blankUserId_meansPublicDocumentsOnly() {
        assertThat(RetrievalService.buildFilter(new SearchFilter(Map.of(), null, "  ")).toString())
                .isEqualTo(RetrievalService.buildFilter(SearchFilter.NONE).toString());
    }

    @Test
    void rerankerHttpError_fallsBackToInitialOrder() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc("a"), doc("b")));
        when(reranker.rerank(any(), any(), anyInt())).thenThrow(
                HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "Bad Gateway", null, null, null));

        assertThat(service.retrieve("q", config(5, true, SearchMode.VECTOR, 0.5, null)))
                .extracting(Document::getId).containsExactly("a", "b");
    }

    @Test
    void userId_doesNotSatisfyFilterModeRequirement() {
        RetrievalConfig config = config(3, false, SearchMode.FILTER);

        assertThatThrownBy(() -> service.retrieve(null, config, "u-1"))
                .isInstanceOf(InvalidSearchFilterException.class);
        verifyNoInteractions(filterRetriever);
    }

    @Test
    void contentFilter_usesJdbcVectorRetrieverInsteadOfVectorStore() {
        when(pgVectorJdbcRetriever.retrieve(eq("q"), any(), eq(4))).thenReturn(List.of(doc("a")));

        List<Document> result = service.retrieve("q", new RetrievalConfig(4, false, SearchMode.VECTOR,
                null, "mot de passe", null, null, null, null));

        assertThat(result).extracting(Document::getId).containsExactly("a");
        ArgumentCaptor<SearchFilter> filter = ArgumentCaptor.forClass(SearchFilter.class);
        verify(pgVectorJdbcRetriever).retrieve(eq("q"), filter.capture(), eq(4));
        assertThat(filter.getValue().contentContains()).isEqualTo("mot de passe");
        verifyNoInteractions(vectorStore);
    }

    @Test
    void reservedMetadataKey_isRejectedBeforeAnySearch() {
        RetrievalConfig config = new RetrievalConfig(4, false, SearchMode.HYBRID,
                Map.of("user_uuid", List.of("someone")), null, null, null, null, null);

        assertThatThrownBy(() -> service.retrieve("q", config))
                .isInstanceOf(InvalidSearchFilterException.class)
                .hasMessageContaining("user_uuid");
        verifyNoInteractions(vectorStore, bm25Retriever, pgVectorJdbcRetriever, reranker);
    }

    @Test
    void filterMode_issuesPlainSqlQueryWithoutQuestionNorReranker() {
        when(filterRetriever.retrieve(any(), eq(7))).thenReturn(List.of(doc("a")));

        List<Document> result = service.retrieve(null, new RetrievalConfig(7, true, SearchMode.FILTER,
                Map.of("file_name", List.of("318_104_01_f")), null, null, null, null, null));

        assertThat(result).extracting(Document::getId).containsExactly("a");
        ArgumentCaptor<SearchFilter> filter = ArgumentCaptor.forClass(SearchFilter.class);
        verify(filterRetriever).retrieve(filter.capture(), eq(7));
        assertThat(filter.getValue().metadata()).containsEntry("file_name", List.of("318_104_01_f"));
        verifyNoInteractions(vectorStore, bm25Retriever, pgVectorJdbcRetriever, reranker);
    }

    @Test
    void filterMode_acceptsContentFilterOnly() {
        when(filterRetriever.retrieve(any(), anyInt())).thenReturn(List.of());

        service.retrieve("  ", new RetrievalConfig(3, false, SearchMode.FILTER, null, "mot de passe", null, null, null, null));

        verify(filterRetriever).retrieve(any(), eq(3));
    }

    @Test
    void filterMode_withoutAnyFilter_isRejected() {
        RetrievalConfig config = config(3, false, SearchMode.FILTER);

        assertThatThrownBy(() -> service.retrieve(null, config))
                .isInstanceOf(InvalidSearchFilterException.class)
                .hasMessageContaining("FILTER");
        verifyNoInteractions(filterRetriever, vectorStore, bm25Retriever, pgVectorJdbcRetriever, reranker);
    }

    @Test
    void blankQuestion_outsideFilterMode_isRejected() {
        for (SearchMode mode : List.of(SearchMode.VECTOR, SearchMode.BM25, SearchMode.HYBRID)) {
            assertThatThrownBy(() -> service.retrieve(" ", config(3, false, mode)))
                    .isInstanceOf(InvalidSearchFilterException.class)
                    .hasMessageContaining("question");
        }
        verifyNoInteractions(filterRetriever, vectorStore, bm25Retriever, pgVectorJdbcRetriever, reranker);
    }

    private static Document doc(String id, double score) {
        return Document.builder().id(id).text("content-" + id).score(score).build();
    }

    private static RetrievalConfig config(int topK, boolean useReranker, SearchMode mode,
                                          Double minRerankScore, Double minBm25Score) {
        return new RetrievalConfig(topK, useReranker, mode, null, null, null, null, minRerankScore, minBm25Score);
    }

    @Test
    void rerankerFailure_fallsBackToInitialOrderTruncated() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc("a"), doc("b"), doc("c")));
        when(reranker.rerank(any(), any(), anyInt())).thenThrow(new RestClientException("connection refused"));

        List<Document> result = service.retrieve("q", config(2, true, SearchMode.VECTOR));

        assertThat(result).extracting(Document::getId).containsExactly("a", "b");
    }

    @Test
    void hybrid_rerankerFailure_keepsVectorThenBm25Order() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc("a")));
        when(bm25Retriever.retrieve(eq("q"), any(), eq(3))).thenReturn(List.of(doc("a"), doc("b"), doc("c")));
        when(reranker.rerank(any(), any(), anyInt())).thenThrow(new IllegalStateException("Empty response"));

        List<Document> result = service.retrieve("q", config(3, false, SearchMode.HYBRID));

        assertThat(result).extracting(Document::getId).containsExactly("a", "b", "c");
    }

    @Test
    void minRerankScore_dropsLowRerankedDocuments_andIgnoresThresholdOnFallback() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc("a"), doc("b")));
        when(reranker.rerank(any(), any(), anyInt())).thenReturn(List.of(doc("b", 0.9), doc("a", 0.2)));

        assertThat(service.retrieve("q", config(5, true, SearchMode.VECTOR, 0.5, null)))
                .extracting(Document::getId).containsExactly("b");

        when(reranker.rerank(any(), any(), anyInt())).thenThrow(new RestClientException("timeout"));
        assertThat(service.retrieve("q", config(5, true, SearchMode.VECTOR, 0.5, null)))
                .extracting(Document::getId).containsExactly("a", "b");
    }

    @Test
    void minRerankScore_isIgnoredWithoutReranker() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc("a", 0.1)));

        assertThat(service.retrieve("q", config(5, false, SearchMode.VECTOR, 0.5, null)))
                .extracting(Document::getId).containsExactly("a");
    }

    @Test
    void thresholds_defaultToProperties_andRequestOverridesThem() {
        RetrievalService withDefaults = new RetrievalService(vectorStore, pgVectorJdbcRetriever, bm25Retriever,
                filterRetriever, reranker, new SearchFilterResolver(new RetrievalProperties(null, null, null, null, null, null)),
                new RetrievalProperties(null, 0.5, 2.0, null, null, null));
        when(bm25Retriever.retrieve(eq("q"), any(), anyInt()))
                .thenReturn(List.of(doc("x", 3.0), doc("y", 1.0)));
        when(reranker.rerank(any(), any(), anyInt())).thenReturn(List.of(doc("x", 0.6), doc("y", 0.4)));

        assertThat(withDefaults.retrieve("q", config(5, false, SearchMode.BM25)))
                .extracting(Document::getId).containsExactly("x");
        assertThat(withDefaults.retrieve("q", config(5, false, SearchMode.BM25, null, 0.5)))
                .extracting(Document::getId).containsExactly("x", "y");
        assertThat(withDefaults.retrieve("q", config(5, true, SearchMode.BM25, 0.3, 0.5)))
                .extracting(Document::getId).containsExactly("x", "y");
        assertThat(withDefaults.retrieve("q", config(5, true, SearchMode.BM25, null, 0.5)))
                .extracting(Document::getId).containsExactly("x");
    }

    @Test
    void omittedSettings_useServerDefaults_andRequestOverridesThem() {
        RetrievalService withDefaults = new RetrievalService(vectorStore, pgVectorJdbcRetriever, bm25Retriever,
                filterRetriever, reranker, new SearchFilterResolver(new RetrievalProperties(null, null, null, null, null, null)),
                new RetrievalProperties(null, null, null, 2, SearchMode.BM25, true));
        when(bm25Retriever.retrieve(eq("q"), any(), anyInt())).thenReturn(List.of(doc("x"), doc("y"), doc("z")));
        when(reranker.rerank(any(), any(), anyInt())).thenReturn(List.of(doc("y"), doc("x")));

        assertThat(withDefaults.retrieve("q", null)).extracting(Document::getId).containsExactly("y", "x");
        verify(bm25Retriever).retrieve(eq("q"), any(), eq(2));
        verify(reranker).rerank(eq("q"), any(), eq(2));
        verifyNoInteractions(vectorStore, pgVectorJdbcRetriever);

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc("a"), doc("b")));
        assertThat(withDefaults.retrieve("q", config(1, false, SearchMode.VECTOR)))
                .extracting(Document::getId).containsExactly("a");
        verify(reranker, times(1)).rerank(any(), any(), anyInt());
    }

    @Test
    void defaultSearchMode_cannotBeFilter_andDefaultTopKMustBePositive() {
        assertThatThrownBy(() -> new RetrievalProperties(null, null, null, null, SearchMode.FILTER, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetrievalProperties(null, null, null, 0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        RetrievalProperties defaults = new RetrievalProperties(null, null, null, null, null, null);
        assertThat(defaults.defaultTopK()).isEqualTo(10);
        assertThat(defaults.defaultSearchMode()).isEqualTo(SearchMode.VECTOR);
        assertThat(defaults.defaultUseReranker()).isFalse();
    }

    @Test
    void minBm25Score_filtersBm25ResultsBeforeHybridReranking() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc("a", 0.8)));
        when(bm25Retriever.retrieve(eq("q"), any(), eq(3))).thenReturn(List.of(doc("b", 4.0), doc("c", 0.5)));
        when(reranker.rerank(any(), any(), anyInt())).thenReturn(List.of());

        service.retrieve("q", config(3, false, SearchMode.HYBRID, null, 1.0));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(reranker).rerank(eq("q"), captor.capture(), eq(3));
        assertThat(captor.getValue()).extracting(Document::getId).containsExactly("a", "b");
    }
}
