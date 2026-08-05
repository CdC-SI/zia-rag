package zas.admin.zia.rag.retrieval;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import zas.admin.zia.rag.rerank.Reranker;
import zas.admin.zia.rag.web.dto.RetrievalConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
    private final BM25Retriever bm25Retriever = mock(BM25Retriever.class);
    private final Reranker reranker = mock(Reranker.class);
    private final RetrievalService service = new RetrievalService(vectorStore, bm25Retriever, reranker);

    private static Document doc(String id) {
        return Document.builder().id(id).text("content-" + id).build();
    }

    @Test
    void vectorOnlyWithoutReranker_truncatesToTopK() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc("a"), doc("b"), doc("c")));

        RetrievalConfig config = new RetrievalConfig(2, false, false, List.of());
        List<Document> result = service.retrieve("q", config);

        assertThat(result).extracting(Document::getId).containsExactly("a", "b");
        verifyNoInteractions(reranker, bm25Retriever);
    }

    @Test
    void vectorOnlyWithReranker_delegatesToReranker() {
        List<Document> vectorDocs = List.of(doc("a"), doc("b"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(vectorDocs);
        when(reranker.rerank(eq("q"), any(), anyInt())).thenReturn(List.of(doc("b")));

        RetrievalConfig config = new RetrievalConfig(5, true, false, List.of());
        List<Document> result = service.retrieve("q", config);

        assertThat(result).extracting(Document::getId).containsExactly("b");
        verify(reranker).rerank(eq("q"), eq(vectorDocs), eq(5));
        verifyNoInteractions(bm25Retriever);
    }

    @Test
    void bm25Combined_forcesRerankerAndUnionDeduplicatesById() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc("a"), doc("b")));
        when(bm25Retriever.retrieve(eq("q"), any(), eq(3)))
                .thenReturn(List.of(doc("b"), doc("c")));
        when(reranker.rerank(eq("q"), any(), anyInt())).thenReturn(List.of(doc("c")));

        // useReranker=false, but BM25 combined must force the reranker.
        RetrievalConfig config = new RetrievalConfig(3, false, true, List.of());
        service.retrieve("q", config);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(reranker).rerank(eq("q"), captor.capture(), eq(3));
        assertThat(captor.getValue()).extracting(Document::getId).containsExactly("a", "b", "c");
        verify(bm25Retriever).retrieve(eq("q"), any(), eq(3));
    }

    @Test
    void nullConfig_appliesDefaultsAndSkipsReranker() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc("a")));

        List<Document> result = service.retrieve("q", null);

        assertThat(result).extracting(Document::getId).containsExactly("a");
        verify(reranker, never()).rerank(any(), any(), anyInt());
    }
}
