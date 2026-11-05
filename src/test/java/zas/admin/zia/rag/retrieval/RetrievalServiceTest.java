package zas.admin.zia.rag.retrieval;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RetrievalServiceTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final PgVectorJdbcRetriever pgVectorJdbcRetriever = mock(PgVectorJdbcRetriever.class);
    private final BM25Retriever bm25Retriever = mock(BM25Retriever.class);
    private final Reranker reranker = mock(Reranker.class);
    private final RetrievalService service = new RetrievalService(vectorStore, pgVectorJdbcRetriever,
            bm25Retriever, reranker, new SearchFilterResolver(new RetrievalProperties(null)));

    private static Document doc(String id) {
        return Document.builder().id(id).text("content-" + id).build();
    }

    private static RetrievalConfig config(int topK, boolean useReranker, SearchMode mode) {
        return new RetrievalConfig(topK, useReranker, mode, null, null, null, null);
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
        RetrievalConfig config = new RetrievalConfig(3, false, null, null, null, true, null);

        assertThat(config.searchMode()).isEqualTo(SearchMode.HYBRID);
        assertThat(new RetrievalConfig(3, false, SearchMode.BM25, null, null, true, null).searchMode())
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
                Map.of("lang", List.of("fr", "de")), null, null, List.of("faq")));

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
    void contentFilter_usesJdbcVectorRetrieverInsteadOfVectorStore() {
        when(pgVectorJdbcRetriever.retrieve(eq("q"), any(), eq(4))).thenReturn(List.of(doc("a")));

        List<Document> result = service.retrieve("q", new RetrievalConfig(4, false, SearchMode.VECTOR,
                null, "mot de passe", null, null));

        assertThat(result).extracting(Document::getId).containsExactly("a");
        ArgumentCaptor<SearchFilter> filter = ArgumentCaptor.forClass(SearchFilter.class);
        verify(pgVectorJdbcRetriever).retrieve(eq("q"), filter.capture(), eq(4));
        assertThat(filter.getValue().contentContains()).isEqualTo("mot de passe");
        verifyNoInteractions(vectorStore);
    }

    @Test
    void reservedMetadataKey_isRejectedBeforeAnySearch() {
        RetrievalConfig config = new RetrievalConfig(4, false, SearchMode.HYBRID,
                Map.of("user_uuid", List.of("someone")), null, null, null);

        assertThatThrownBy(() -> service.retrieve("q", config))
                .isInstanceOf(InvalidSearchFilterException.class)
                .hasMessageContaining("user_uuid");
        verifyNoInteractions(vectorStore, bm25Retriever, pgVectorJdbcRetriever, reranker);
    }
}
