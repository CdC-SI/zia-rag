package zas.admin.zia.rag.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;

@Schema(description = "Request holding the user question and the retrieval configuration.")
public record RetrievalRequest(

        @Schema(
                description = "Natural language question to search the vector store with. Required "
                        + "for VECTOR, BM25 and HYBRID modes; ignored in FILTER mode.",
                example = "How do I reset my password?",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String question,

        @Schema(description = "Optional retrieval configuration. Defaults are applied when omitted.")
        @Valid
        RetrievalConfig config
) {

    public RetrievalRequest {
        if (config == null) {
            config = RetrievalConfig.defaults();
        }
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "question must not be blank unless searchMode is FILTER")
    public boolean isQuestionValid() {
        return !config.searchMode().requiresQuestion() || (question != null && !question.isBlank());
    }
}
