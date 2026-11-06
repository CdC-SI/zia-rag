package zas.admin.zia.rag.retrieval;

import org.junit.jupiter.api.Test;
import zas.admin.zia.rag.config.properties.RetrievalProperties;
import zas.admin.zia.rag.web.dto.RetrievalConfig;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchFilterResolverTest {

    private final SearchFilterResolver resolver = new SearchFilterResolver(new RetrievalProperties(null));

    private static RetrievalConfig config(Map<String, List<String>> metadata, String content, List<String> sources) {
        return new RetrievalConfig(null, null, null, metadata, content, null, sources);
    }

    @Test
    void mergesDeprecatedSourcesIntoSourceKeyWithoutDuplicates() {
        SearchFilter filter = resolver.resolve(config(
                Map.of("source", List.of("faq", "handbook")), null, List.of("handbook", "wiki")));

        assertThat(filter.metadata()).containsExactly(Map.entry("source", List.of("faq", "handbook", "wiki")));
    }

    @Test
    void ignoresNullValuesAndEmptyLists() {
        Map<String, List<String>> metadata = new LinkedHashMap<>();
        metadata.put("lang", Arrays.asList("fr", null));
        metadata.put("empty", List.of());
        metadata.put("nullValues", null);

        SearchFilter filter = resolver.resolve(config(metadata, null, null));

        assertThat(filter.metadata()).containsExactly(Map.entry("lang", List.of("fr")));
    }

    @Test
    void blankContentIsIgnored() {
        assertThat(resolver.resolve(config(null, "  ", null)).hasContentFilter()).isFalse();
        assertThat(resolver.resolve(config(null, "abc", null)).contentContains()).isEqualTo("abc");
    }

    @Test
    void rejectsReservedKeysCaseInsensitively() {
        assertThatThrownBy(() -> resolver.resolve(config(Map.of("USER_UUID", List.of("x")), null, null)))
                .isInstanceOf(InvalidSearchFilterException.class);
        assertThatThrownBy(() -> resolver.resolve(config(Map.of("user_uuid", List.of()), null, null)))
                .isInstanceOf(InvalidSearchFilterException.class);
    }

    @Test
    void rejectsBlankKeys() {
        assertThatThrownBy(() -> resolver.resolve(config(Map.of(" ", List.of("x")), null, null)))
                .isInstanceOf(InvalidSearchFilterException.class);
    }

    @Test
    void reservedKeysAreConfigurable() {
        SearchFilterResolver custom = new SearchFilterResolver(new RetrievalProperties(List.of("user_uuid", "secret")));

        assertThatThrownBy(() -> custom.resolve(config(Map.of("secret", List.of("x")), null, null)))
                .isInstanceOf(InvalidSearchFilterException.class);
        assertThat(custom.resolve(config(Map.of("lang", List.of("fr")), null, null)).metadata()).containsKey("lang");
    }
}
