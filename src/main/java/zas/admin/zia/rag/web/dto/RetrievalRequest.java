package zas.admin.zia.rag.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;

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
        RetrievalConfig config,

        @Schema(
                description = "Optional identifier (users.uuid) of the end user on whose behalf the search is "
                        + "made. When set, the private documents of this user (metadata 'user_uuid') are "
                        + "searched on top of the public documents; otherwise only public documents are "
                        + "returned. Declarative: any caller allowed by zia.security.retriever-groups may pass any "
                        + "uuid; reserved to the trusted ZIA backend, which sends the authenticated user's uuid.",
                example = "3f2c1b8e-5d4a-4e7b-9c1a-2b3c4d5e6f70",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Pattern(regexp = "[A-Za-z0-9._@-]{1,255}", message = "userId must match [A-Za-z0-9._@-]{1,255}")
        String userId
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
        // The server default search mode cannot be FILTER, so an omitted mode requires a question.
        boolean requiresQuestion = config.searchMode() == null || config.searchMode().requiresQuestion();
        return !requiresQuestion || (question != null && !question.isBlank());
    }
}
