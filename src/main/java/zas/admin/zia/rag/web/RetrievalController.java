package zas.admin.zia.rag.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.ai.document.Document;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zas.admin.zia.rag.config.RetrieverAccess;
import zas.admin.zia.rag.retrieval.RetrievalService;
import zas.admin.zia.rag.retrieval.SourceService;
import zas.admin.zia.rag.web.dto.RetrievalRequest;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/retrieval", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Retrieval", description = "Query the vector store for relevant documents and sources.")
@PreAuthorize(RetrieverAccess.PRE_AUTHORIZE_EXPRESSION)
public class RetrievalController {

    private final RetrievalService retrievalService;
    private final SourceService sourceService;

    public RetrievalController(RetrievalService retrievalService, SourceService sourceService) {
        this.retrievalService = retrievalService;
        this.sourceService = sourceService;
    }

    @Operation(
            summary = "Retrieve relevant documents",
            description = "Returns the documents most relevant to the given question. The retrieval "
                    + "can be tuned with topK, the search mode (VECTOR, BM25, HYBRID or FILTER), reranking, "
                    + "filters on any non-reserved metadata key and a case-insensitive substring the "
                    + "content must contain. In HYBRID mode, reranking is forced. In FILTER mode, a plain "
                    + "SQL query on the filters is issued (no embedding, ordered by id): the question is "
                    + "optional and ignored, the reranker is ignored and at least one filter is required.")
    @ApiResponse(
            responseCode = "200",
            description = "The relevant documents, most relevant first.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = Document.class))))
    @ApiResponse(responseCode = "400", description = "Invalid request (e.g. filter on a reserved metadata "
            + "key, missing question outside FILTER mode, FILTER mode without any filter).")
    @ApiResponse(responseCode = "403", description = "The caller is not member of an allowed group.")
    @PostMapping(value = "/documents", consumes = MediaType.APPLICATION_JSON_VALUE)
    public List<Document> retrieveDocuments(@Valid @RequestBody RetrievalRequest request) {
        return retrievalService.retrieve(request.question(), request.config());
    }

    @Operation(
            summary = "List available sources",
            description = "Returns the distinct 'source' metadata values available in the vector "
                    + "store. These values can be used to filter documents in a retrieval request.")
    @ApiResponse(
            responseCode = "200",
            description = "The distinct source values.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(type = "string"))))
    @ApiResponse(responseCode = "403", description = "The caller is not member of an allowed group.")
    @GetMapping("/sources")
    public List<String> listSources() {
        return sourceService.listSources();
    }
}
