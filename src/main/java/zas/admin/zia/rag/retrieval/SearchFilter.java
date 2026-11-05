package zas.admin.zia.rag.retrieval;

import java.util.List;
import java.util.Map;

/**
 * Validated filters applied to every retriever, on top of the "public documents only" rule.
 *
 * @param metadata        metadata key → accepted values (any value of a key matches, every key must match)
 * @param contentContains case-insensitive substring the document content must contain, or {@code null}
 */
public record SearchFilter(Map<String, List<String>> metadata, String contentContains) {

    public static final SearchFilter NONE = new SearchFilter(Map.of(), null);

    public SearchFilter {
        metadata = metadata == null ? Map.of() : metadata;
    }

    public boolean hasContentFilter() {
        return contentContains != null;
    }

    public boolean isEmpty() {
        return metadata.isEmpty() && !hasContentFilter();
    }
}
