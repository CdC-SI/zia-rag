package zas.admin.zia.rag.retrieval;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SQL issued by the BM25 retriever and mapping of its rows. The query itself needs the {@code pg_textsearch}
 * extension, which the pgvector test image lacks: it is checked here on a mocked JDBC layer.
 */
class BM25RetrieverTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final BM25Retriever retriever = new BM25Retriever(jdbcTemplate, JsonMapper.builder().build(), "zia", "docs");

    @SuppressWarnings("unchecked")
    private RowMapper<Document> retrieve(SearchFilter filter, Connection connection)
            throws Exception {
        ArgumentCaptor<PreparedStatementCreator> creator = ArgumentCaptor.forClass(PreparedStatementCreator.class);
        ArgumentCaptor<RowMapper<Document>> mapper = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbcTemplate.query(creator.capture(), mapper.capture())).thenReturn(List.of());

        assertThat(retriever.retrieve("cotisations AVS", filter, 5)).isEmpty();

        creator.getValue().createPreparedStatement(connection);
        return mapper.getValue();
    }

    @Test
    void ranksByBm25OnTheQualifiedTable_withThePublicVisibilityRule() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        when(connection.prepareStatement(sql.capture())).thenReturn(ps);

        retrieve(null, connection);

        assertThat(sql.getValue())
                .contains("content <@> to_bm25query(?, 'idx_vector_store_content_bm25') AS bm25_score")
                .contains("FROM \"zia\".\"docs\" ")
                .contains("WHERE (metadata->>'user_uuid' IS NULL OR metadata->>'user_uuid' = '') ")
                .endsWith("ORDER BY bm25_score ASC LIMIT ?");
        verify(ps).setString(1, "cotisations AVS");
        verify(ps).setInt(2, 5);
    }

    @Test
    void bindsTheFilterParametersBetweenTheQuestionAndTheLimit() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(ps);
        when(connection.createArrayOf(anyString(), any())).thenReturn(mock(java.sql.Array.class));

        retrieve(new SearchFilter(Map.of("source", List.of("faq")), "rente", "u-1"), connection);

        verify(ps).setString(1, "cotisations AVS");
        verify(ps).setString(2, "u-1");
        verify(ps).setString(3, "source");
        verify(ps).setString(5, "%rente%");
        verify(ps).setInt(6, 5);
    }

    @Test
    void mapsRows_withAPositiveRelevanceScoreInTheMetadata() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenReturn(mock(PreparedStatement.class));
        RowMapper<Document> mapper = retrieve(SearchFilter.NONE, connection);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("id")).thenReturn("doc-1");
        when(rs.getString("content")).thenReturn("Les cotisations");
        when(rs.getDouble("bm25_score")).thenReturn(-3.5);
        when(rs.getString("metadata")).thenReturn("{\"source\":\"faq\"}");

        Document document = mapper.mapRow(rs, 0);

        assertThat(document.getId()).isEqualTo("doc-1");
        assertThat(document.getText()).isEqualTo("Les cotisations");
        assertThat(document.getScore()).isEqualTo(3.5);
        assertThat(document.getMetadata()).containsEntry("source", "faq").containsEntry("bm25_score", 3.5);
    }
}
