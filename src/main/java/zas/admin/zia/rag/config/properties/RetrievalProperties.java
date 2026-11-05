package zas.admin.zia.rag.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Retrieval settings.
 *
 * @param reservedMetadataKeys metadata keys clients are not allowed to filter on (defaults to {@code user_uuid})
 */
@ConfigurationProperties(prefix = "zia.rag.retrieval")
public record RetrievalProperties(List<String> reservedMetadataKeys) {

    public static final List<String> DEFAULT_RESERVED_METADATA_KEYS = List.of("user_uuid");

    public RetrievalProperties {
        reservedMetadataKeys = reservedMetadataKeys == null
                ? DEFAULT_RESERVED_METADATA_KEYS
                : List.copyOf(reservedMetadataKeys);
    }
}
