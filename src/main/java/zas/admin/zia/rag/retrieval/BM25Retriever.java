package zas.admin.zia.rag.retrieval;

import tools.jackson.databind.ObjectMapper;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Full-text BM25 retriever backed by the PostgreSQL {@code pg_textsearch} extension.
 *
 * <p>It relies on the pre-existing BM25 index {@code idx_vector_store_content_bm25} and the
 * operator {@code content <@> to_bm25query(?, index)} to rank documents by textual relevance.
 * The scores produced here are <em>not</em> comparable with the cosine distances returned by the
 * vector similarity search, hence combining both retrievers forces the use of the reranker.
 */
@Component
public class BM25Retriever {

    static final String BM25_INDEX = "idx_vector_store_content_bm25";
    static final String BM25_SCORE_METADATA_KEY = "bm25_score";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String qualifiedTable;

    public BM25Retriever(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${spring.ai.vectorstore.pgvector.schema-name:public}") String schemaName,
            @Value("${spring.ai.vectorstore.pgvector.table-name:vector_store}") String tableName) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.qualifiedTable = "\"" + schemaName + "\".\"" + tableName + "\"";
    }

    /**
     * Retrieves the {@code topK} most relevant documents for the given question using BM25 ranking.
     *
     * @param question the natural language question
     * @param filter   metadata and content filters to apply
     * @param topK     the maximum number of documents to return
     * @return the matching documents, most relevant first
     */
    public List<Document> retrieve(String question, SearchFilter filter, int topK) {
        SqlWhereClause where = SqlWhereClause.of(filter == null ? SearchFilter.NONE : filter);

        String sql = "SELECT id, content, metadata, "
                + "content <@> to_bm25query(?, '" + BM25_INDEX + "') AS bm25_score "
                + "FROM " + qualifiedTable + " "
                + where.sql()
                + "ORDER BY bm25_score ASC LIMIT ?";

        return jdbcTemplate.query(connection -> {
            var ps = connection.prepareStatement(sql);
            ps.setString(1, question);
            int next = where.bind(connection, ps, 2);
            ps.setInt(next, topK);
            return ps;
        }, this::mapRow);
    }

    private Document mapRow(ResultSet rs, int rowNum) throws SQLException {
        String id = rs.getString("id");
        String content = rs.getString("content");
        double rawScore = rs.getDouble("bm25_score"); // négatif : plus petit = plus pertinent
        double score = -rawScore; // relevance : plus grand = plus pertinent

        Map<String, Object> metadata = MetadataJson.parse(objectMapper, rs.getString("metadata"));
        metadata.put(BM25_SCORE_METADATA_KEY, score);

        return Document.builder()
                .id(id)
                .text(content)
                .metadata(metadata)
                .score(score)
                .build();
    }
}
