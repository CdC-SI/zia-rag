package zas.admin.zia.rag.retrieval;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataJsonTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void parsesNativeJsonTypes() {
        var metadata = MetadataJson.parse(objectMapper, "{\"source\":\"faq\",\"page\":3,\"tags\":[\"a\",\"b\"]}");

        assertThat(metadata).containsEntry("source", "faq").containsEntry("page", 3).containsEntry("tags", List.of("a", "b"));
    }

    @Test
    void missingOrInvalidJson_givesAMutableEmptyMap() {
        for (String json : new String[]{null, "", "  ", "{not json", "[1,2]"}) {
            var metadata = MetadataJson.parse(objectMapper, json);
            assertThat(metadata).as(String.valueOf(json)).isEmpty();
            metadata.put("bm25_score", 1.0);
        }
    }
}
