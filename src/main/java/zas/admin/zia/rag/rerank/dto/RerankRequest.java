package zas.admin.zia.rag.rerank.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Request payload sent to the remote reranking (score) endpoint.
 *
 * <p>The endpoint scores every {@code text_2} entry against the single query held in {@code text_1},
 * following the contract of the internal reranking service.
 *
 * @param model                the reranker model identifier
 * @param text1                a singleton list holding the formatted query
 * @param text2                the formatted documents to score
 * @param truncatePromptTokens maximum number of prompt tokens before truncation
 */
public record RerankRequest(
        String model,
        @JsonProperty("text_1") List<String> text1,
        @JsonProperty("text_2") List<String> text2,
        @JsonProperty("truncate_prompt_tokens") Integer truncatePromptTokens) {
}
