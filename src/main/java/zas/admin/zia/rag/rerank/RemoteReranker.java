package zas.admin.zia.rag.rerank;

import org.springframework.ai.document.Document;
import org.springframework.web.client.RestClient;
import zas.admin.zia.rag.config.properties.RerankProperties;
import zas.admin.zia.rag.rerank.dto.RerankRequest;
import zas.admin.zia.rag.rerank.dto.RerankResponse;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * {@link Reranker} implementation calling the remote reranking (score) endpoint.
 *
 * <p>The query and every candidate document are wrapped in the prompt template expected by the
 * reranker model, then sent to the {@code /score} endpoint. The endpoint returns one relevance
 * score per document, which is used to reorder and truncate the candidates.
 */
public class RemoteReranker implements Reranker {

    private static final String PREFIX = """
            <|im_start|>system
            Judge whether the Document meets the requirements based on the Query and the Instruct provided. Note that the answer can only be "yes" or "no".<|im_end|>
            <|im_start|>user
            """;

    private static final String SUFFIX = """
            <|im_end|>
            <|im_start|>assistant
            <think>

            </think>

            """;

    private static final String QUERY_TEMPLATE = """
            {prefix}<Instruct>: {instruction}
            <Query>: {query}
            """;

    private static final String DOCUMENT_TEMPLATE = """
            <Document>: {document}{suffix}
            """;

    private static final String INSTRUCTION = "Given a user search query, determine whether the document can answer the query or not.";

    private final RestClient restClient;
    private final String model;
    private final Integer truncatePromptTokens;

    public RemoteReranker(RestClient restClient, RerankProperties properties) {
        this.restClient = restClient;
        this.model = properties.model();
        this.truncatePromptTokens = properties.truncatePromptTokens();
    }

    @Override
    public List<Document> rerank(String query, List<Document> documents, int topK) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        RerankRequest request = new RerankRequest(
                model,
                List.of(formatQuery(query)),
                formatDocuments(documents),
                truncatePromptTokens);

        RerankResponse response = restClient.post()
                .uri("/score")
                .body(request)
                .retrieve()
                .body(RerankResponse.class);

        if (response == null || response.data() == null || response.data().isEmpty()) {
            // Fail open: keep the incoming order, simply truncated.
            return documents.stream().limit(topK).toList();
        }

        return response.data().stream()
                .filter(result -> result.index() >= 0 && result.index() < documents.size())
                .sorted(Comparator.comparingDouble(RerankResponse.Result::score).reversed())
                .limit(topK)
                .map(result -> documents.get(result.index()).mutate().score(result.score()).build())
                .toList();
    }

    private String formatQuery(String query) {
        return QUERY_TEMPLATE
                .replace("{prefix}", PREFIX)
                .replace("{instruction}", INSTRUCTION)
                .replace("{query}", query);
    }

    private List<String> formatDocuments(List<Document> documents) {
        return documents.stream()
                .map(document -> DOCUMENT_TEMPLATE
                        .replace("{document}", Objects.toString(document.getText(), "Empty Document"))
                        .replace("{suffix}", SUFFIX))
                .toList();
    }
}
