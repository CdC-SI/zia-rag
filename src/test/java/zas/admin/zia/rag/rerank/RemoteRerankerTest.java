package zas.admin.zia.rag.rerank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import zas.admin.zia.rag.config.properties.RerankProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RemoteRerankerTest {

    private MockRestServiceServer server;
    private RemoteReranker reranker;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://reranker");
        server = MockRestServiceServer.bindTo(builder).build();
        reranker = new RemoteReranker(builder.build(),
                new RerankProperties(null, null, "model", 1024, null, null));
    }

    private static List<Document> documents() {
        return List.of(Document.builder().id("a").text("A").build(),
                Document.builder().id("b").text("B").build(),
                Document.builder().id("c").text("C").build());
    }

    @Test
    void reordersByScoreAndTruncates() {
        server.expect(requestTo("http://reranker/score")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        { "data": [
                            { "index": 0, "score": 0.1 },
                            { "index": 1, "score": 0.9 },
                            { "index": 2, "score": 0.5 } ] }
                        """, MediaType.APPLICATION_JSON));

        List<Document> result = reranker.rerank("q", documents(), 2);

        assertThat(result).extracting(Document::getId).containsExactly("b", "c");
        assertThat(result).extracting(Document::getScore).containsExactly(0.9, 0.5);
    }

    @Test
    void emptyResponse_isAFailure() {
        server.expect(requestTo("http://reranker/score"))
                .andRespond(withSuccess("{ \"data\": [] }", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> reranker.rerank("q", documents(), 2)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void serverError_isAFailure() {
        server.expect(requestTo("http://reranker/score")).andRespond(withServerError());

        assertThatThrownBy(() -> reranker.rerank("q", documents(), 2)).isInstanceOf(RestClientException.class);
    }

    @Test
    void noDocuments_noCall() {
        assertThat(reranker.rerank("q", List.of(), 2)).isEmpty();
        assertThat(reranker.rerank("q", null, 2)).isEmpty();
        server.verify();
    }

    @Test
    void outOfRangeIndexes_areIgnored_andTheQueryAndDocumentsAreWrappedInThePromptTemplate() {
        server.expect(requestTo("http://reranker/score"))
                .andExpect(content().string(containsString("<Query>: quelle cotisation")))
                .andExpect(content().string(containsString("<Document>: A")))
                .andExpect(content().string(containsString("\"truncate_prompt_tokens\":1024")))
                .andRespond(withSuccess("""
                        { "data": [
                            { "index": -1, "score": 0.99 },
                            { "index": 7, "score": 0.98 },
                            { "index": 1, "score": 0.4 } ] }
                        """, MediaType.APPLICATION_JSON));

        List<Document> docs = List.of(Document.builder().id("a").text("A").build(),
                Document.builder().id("b").text("B").build());
        List<Document> result = reranker.rerank("quelle cotisation", docs, 5);

        assertThat(result).extracting(Document::getId).containsExactly("b");
    }

    @Test
    void missingDataField_isAFailure() {
        server.expect(requestTo("http://reranker/score"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> reranker.rerank("q", documents(), 2)).isInstanceOf(IllegalStateException.class);
    }
}
