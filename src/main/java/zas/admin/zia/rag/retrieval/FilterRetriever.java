package zas.admin.zia.rag.retrieval;

import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Plain SQL retriever: returns the documents matching the metadata and/or content filters, without
 * any embedding or relevance ranking. Results are ordered by id to stay deterministic and carry no score.
 */
@Component
public class FilterRetriever {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String qualifiedTable;

    public FilterRetriever(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${spring.ai.vectorstore.pgvector.schema-name:public}") String schemaName,
            @Value("${spring.ai.vectorstore.pgvector.table-name:vector_store}") String tableName) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.qualifiedTable = "\"" + schemaName + "\".\"" + tableName + "\"";
    }

    /**
     * Retrieves at most {@code topK} documents matching the filter, ordered by id.
     */
    public List<Document> retrieve(SearchFilter filter, int topK) {
        SqlWhereClause where = SqlWhereClause.of(filter == null ? SearchFilter.NONE : filter);

        String sql = "SELECT id, content, metadata FROM " + qualifiedTable + " "
                + where.sql()
                + "ORDER BY id LIMIT ?";

        return jdbcTemplate.query(connection -> {
            var ps = connection.prepareStatement(sql);
            int next = where.bind(connection, ps, 1);
            ps.setInt(next, topK);
            return ps;
        }, this::mapRow);
    }

    private Document mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Document.builder()
                .id(rs.getString("id"))
                .text(rs.getString("content"))
                .metadata(MetadataJson.parse(objectMapper, rs.getString("metadata")))
                .build();
    }
}
