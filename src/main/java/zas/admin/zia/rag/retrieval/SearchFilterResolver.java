package zas.admin.zia.rag.retrieval;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import zas.admin.zia.rag.config.properties.RetrievalProperties;
import zas.admin.zia.rag.web.dto.RetrievalConfig;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates and normalizes the filters of a {@link RetrievalConfig} into a {@link SearchFilter}.
 *
 * <p>The deprecated {@code sources} list is merged into the {@code source} metadata filter. Reserved
 * metadata keys (see {@link RetrievalProperties#reservedMetadataKeys()}) are rejected.
 */
@Component
public class SearchFilterResolver {

    static final String SOURCE_KEY = "source";

    private final Set<String> reservedKeys;

    public SearchFilterResolver(RetrievalProperties properties) {
        this.reservedKeys = properties.reservedMetadataKeys().stream()
                .filter(StringUtils::hasText)
                .map(key -> key.strip().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    @SuppressWarnings("deprecation")
    public SearchFilter resolve(RetrievalConfig config) {
        if (config == null) {
            return SearchFilter.NONE;
        }
        Map<String, Set<String>> merged = new LinkedHashMap<>();
        config.metadataFilters().forEach((key, values) -> add(merged, key, values));
        if (!config.sources().isEmpty()) {
            add(merged, SOURCE_KEY, config.sources());
        }

        Map<String, List<String>> metadata = new LinkedHashMap<>();
        merged.forEach((key, values) -> metadata.put(key, List.copyOf(values)));

        String content = StringUtils.hasText(config.contentContains()) ? config.contentContains() : null;
        return new SearchFilter(Collections.unmodifiableMap(metadata), content);
    }

    private void add(Map<String, Set<String>> merged, String key, Collection<String> values) {
        validateKey(key);
        if (values == null) {
            return;
        }
        List<String> nonNull = values.stream().filter(Objects::nonNull).toList();
        if (!nonNull.isEmpty()) {
            merged.computeIfAbsent(key, k -> new LinkedHashSet<>()).addAll(nonNull);
        }
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new InvalidSearchFilterException("Metadata filter keys must not be blank");
        }
        if (reservedKeys.contains(key.strip().toLowerCase(Locale.ROOT))) {
            throw new InvalidSearchFilterException("Filtering on metadata key '%s' is not allowed".formatted(key));
        }
    }
}
