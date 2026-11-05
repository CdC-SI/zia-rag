package zas.admin.zia.rag.retrieval;

import org.junit.jupiter.api.Test;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqlWhereClauseTest {

    @Test
    void noFilter_onlyRestrictsToPublicDocuments() {
        SqlWhereClause where = SqlWhereClause.of(SearchFilter.NONE);

        assertThat(where.sql())
                .isEqualTo("WHERE (metadata->>'user_uuid' IS NULL OR metadata->>'user_uuid' = '') ");
    }

    @Test
    void metadataAndContentFilters_areBoundAsParameters() throws Exception {
        Map<String, List<String>> metadata = new LinkedHashMap<>();
        metadata.put("source", List.of("faq"));
        metadata.put("la'ng", List.of("fr", "de"));
        SqlWhereClause where = SqlWhereClause.of(new SearchFilter(metadata, "100%_sûr\\"));

        assertThat(where.sql())
                .contains("AND metadata->>? = ANY(?) AND metadata->>? = ANY(?) ")
                .endsWith("AND content ILIKE ? ESCAPE '\\' ")
                .doesNotContain("la'ng", "faq", "sûr");

        Connection connection = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        Array array = mock(Array.class);
        when(connection.createArrayOf(eq("text"), any())).thenReturn(array);

        int next = where.bind(connection, ps, 2);

        assertThat(next).isEqualTo(7);
        verify(ps).setString(2, "source");
        verify(ps).setString(4, "la'ng");
        verify(connection).createArrayOf("text", new Object[]{"fr", "de"});
        verify(ps).setString(6, "%100\\%\\_sûr\\\\%");
    }
}
