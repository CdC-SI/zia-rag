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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zas.admin.zia.rag.retrieval.RetrievalService;
import zas.admin.zia.rag.retrieval.SourceService;
import zas.admin.zia.rag.web.dto.RetrievalRequest;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/retrieval", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Retrieval", description = "Query the vector store for relevant documents and sources.")
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
                    + "can be tuned with topK, an optional BM25 full-text search combined with the "
                    + "vector similarity search, reranking and a filter on the document 'source' "
                    + "metadata. When BM25 is combined with the vector search, reranking is forced.")
    @ApiResponse(
            responseCode = "200",
            description = "The relevant documents, most relevant first.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = Document.class))))
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
    @GetMapping("/sources")
    public List<String> listSources() {
        return sourceService.listSources();
    }
}
