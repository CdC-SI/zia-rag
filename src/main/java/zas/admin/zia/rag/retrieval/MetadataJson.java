package zas.admin.zia.rag.retrieval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

final class MetadataJson {

    private static final Logger LOG = LoggerFactory.getLogger(MetadataJson.class);

    private MetadataJson() {
    }

    static Map<String, Object> parse(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            LOG.warn("Failed to parse document metadata, returning empty metadata", e);
            return new HashMap<>();
        }
    }
}
