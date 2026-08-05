package zas.admin.zia.rag.retrieval;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
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

    private static final Logger LOG = LoggerFactory.getLogger(BM25Retriever.class);

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
     * @param sources  optional list of {@code source} metadata values to filter on (may be empty)
     * @param topK     the maximum number of documents to return
     * @return the matching documents, most relevant first
     */
    public List<Document> retrieve(String question, List<String> sources, int topK) {
        boolean filterBySource = !CollectionUtils.isEmpty(sources);

        StringBuilder sql = new StringBuilder()
                .append("SELECT id, content, metadata, ")
                .append("content <@> to_bm25query(?, '").append(BM25_INDEX).append("') AS bm25_score ")
                .append("FROM ").append(qualifiedTable).append(' ');

        List<Object> args = new ArrayList<>();

        // Only public documents are returned: a document is public when its
        // 'user_uuid' metadata is empty or absent.
        sql.append("WHERE (metadata->>'user_uuid' IS NULL OR metadata->>'user_uuid' = '') ");

        if (filterBySource) {
            sql.append("AND metadata->>'source' = ANY(?) ");
        }

        sql.append("ORDER BY bm25_score ASC LIMIT ?");

        RowMapper<Document> rowMapper = this::mapRow;

        if (filterBySource) {
            return jdbcTemplate.query(connection -> {
                var ps = connection.prepareStatement(sql.toString());
                ps.setString(1, question);
                ps.setArray(2, connection.createArrayOf("text", sources.toArray()));
                ps.setInt(3, topK);
                return ps;
            }, rowMapper);
        }

        args.add(question);
        args.add(topK);
        return jdbcTemplate.query(sql.toString(), rowMapper, args.toArray());
    }

    private Document mapRow(ResultSet rs, int rowNum) throws SQLException {
        String id = rs.getString("id");
        String content = rs.getString("content");
        double rawScore = rs.getDouble("bm25_score"); // négatif : plus petit = plus pertinent
        double score = -rawScore; // relevance : plus grand = plus pertinent

        Map<String, Object> metadata = parseMetadata(rs.getString("metadata"));
        metadata.put(BM25_SCORE_METADATA_KEY, score);

        return Document.builder()
                .id(id)
                .text(content)
                .metadata(metadata)
                .score(score)
                .build();
    }

    private Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            LOG.warn("Failed to parse document metadata, returning empty metadata", e);
            return new HashMap<>();
        }
    }
}
