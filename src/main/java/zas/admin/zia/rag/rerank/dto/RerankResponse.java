package zas.admin.zia.rag.rerank.dto;

import java.util.List;

/**
 * Response payload returned by the remote reranking (score) endpoint.
 *
 * @param id      the response identifier
 * @param object  the response object type
 * @param created the creation timestamp
 * @param model   the reranker model identifier
 * @param data    the per-document scores, one {@link Result} per scored document
 */
public record RerankResponse(
        String id,
        String object,
        Long created,
        String model,
        List<Result> data) {

    /**
     * Score of a single document.
     *
     * @param index  the index of the document in the request {@code text_2} list
     * @param object the result object type
     * @param score  the relevance score
     */
    public record Result(int index, String object, double score) {
    }
}
