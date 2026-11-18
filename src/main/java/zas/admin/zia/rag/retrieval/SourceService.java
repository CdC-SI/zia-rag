package zas.admin.zia.rag.retrieval;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Lists the distinct {@code source} metadata values of the public documents of the vector store (the
 * sources of private documents are never listed).
 */
@Service
public class SourceService {

    private final JdbcTemplate jdbcTemplate;
    private final String qualifiedTable;

    public SourceService(
            JdbcTemplate jdbcTemplate,
            @Value("${spring.ai.vectorstore.pgvector.schema-name:public}") String schemaName,
            @Value("${spring.ai.vectorstore.pgvector.table-name:vector_store}") String tableName) {
        this.jdbcTemplate = jdbcTemplate;
        this.qualifiedTable = "\"" + schemaName + "\".\"" + tableName + "\"";
    }

    /**
     * @return the distinct, non-null {@code source} metadata values of the public documents, sorted alphabetically
     */
    public List<String> listSources() {
        String sql = "SELECT DISTINCT metadata->>'source' AS source "
                + "FROM " + qualifiedTable + " "
                + SqlWhereClause.of(SearchFilter.NONE).sql()
                + "AND metadata->>'source' IS NOT NULL "
                + "ORDER BY source";
        return jdbcTemplate.queryForList(sql, String.class);
    }
}
