package zas.admin.zia.rag.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request holding the user question and the retrieval configuration.")
public record RetrievalRequest(

        @Schema(
                description = "Natural language question to search the vector store with.",
                example = "How do I reset my password?",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "question must not be blank")
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
}
