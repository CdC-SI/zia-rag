package zas.admin.zia.rag.retrieval;

import java.util.List;
import java.util.Map;

/**
 * Validated filters applied to every retriever, on top of the document visibility rule: public documents
 * (metadata {@code user_uuid} absent or empty) plus, when {@code userId} is set, the private documents of
 * that user.
 *
 * @param metadata        metadata key → accepted values (any value of a key matches, every key must match)
 * @param contentContains case-insensitive substring the document content must contain, or {@code null}
 * @param userId          user whose private documents are also visible, or {@code null} for public documents only
 */
public record SearchFilter(Map<String, List<String>> metadata, String contentContains, String userId) {

    public static final String USER_UUID_KEY = "user_uuid";

    public static final SearchFilter NONE = new SearchFilter(Map.of(), null, null);

    public SearchFilter {
        metadata = metadata == null ? Map.of() : metadata;
        userId = userId == null || userId.isBlank() ? null : userId.strip();
    }

    public SearchFilter(Map<String, List<String>> metadata, String contentContains) {
        this(metadata, contentContains, null);
    }

    public boolean hasContentFilter() {
        return contentContains != null;
    }

    public boolean hasUserId() {
        return userId != null;
    }

    /** Whether no metadata nor content filter is set (the visibility rule, {@code userId} included, is not a filter). */
    public boolean isEmpty() {
        return metadata.isEmpty() && !hasContentFilter();
    }
}
