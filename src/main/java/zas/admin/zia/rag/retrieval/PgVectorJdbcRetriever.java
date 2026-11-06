package zas.admin.zia.rag.retrieval;

import com.pgvector.PGvector;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Vector similarity search issued directly in SQL against the pgvector table.
 *
 * <p>Used instead of the Spring AI {@code VectorStore} when a filter cannot be expressed with the
 * Spring AI filter expressions (e.g. a substring filter on the document content). The distance
 * computation mirrors {@code PgVectorStore} so that scores stay consistent: {@code score = 1 - distance}.
 */
@Component
public class PgVectorJdbcRetriever {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;
    private final String qualifiedTable;
    private final String distanceExpression;

    public PgVectorJdbcRetriever(
            JdbcTemplate jdbcTemplate,
            EmbeddingModel embeddingModel,
            ObjectMapper objectMapper,
            @Value("${spring.ai.vectorstore.pgvector.schema-name:public}") String schemaName,
            @Value("${spring.ai.vectorstore.pgvector.table-name:vector_store}") String tableName,
            @Value("${spring.ai.vectorstore.pgvector.distance-type:COSINE_DISTANCE}") PgDistanceType distanceType) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
        this.objectMapper = objectMapper;
        this.qualifiedTable = "\"" + schemaName + "\".\"" + tableName + "\"";
        this.distanceExpression = distanceExpression(distanceType);
    }

    /**
     * Retrieves the {@code topK} documents closest to the question embedding, matching the filter.
     */
    public List<Document> retrieve(String question, SearchFilter filter, int topK) {
        SqlWhereClause where = SqlWhereClause.of(filter == null ? SearchFilter.NONE : filter);
        PGvector queryEmbedding = new PGvector(embeddingModel.embed(question));

        String sql = "SELECT id, content, metadata, " + distanceExpression + " AS distance "
                + "FROM " + qualifiedTable + " "
                + where.sql()
                + "ORDER BY distance LIMIT ?";

        return jdbcTemplate.query(connection -> {
            var ps = connection.prepareStatement(sql);
            ps.setObject(1, queryEmbedding);
            int next = where.bind(connection, ps, 2);
            ps.setInt(next, topK);
            return ps;
        }, this::mapRow);
    }

    private Document mapRow(ResultSet rs, int rowNum) throws SQLException {
        double distance = rs.getDouble("distance");
        Map<String, Object> metadata = MetadataJson.parse(objectMapper, rs.getString("metadata"));
        metadata.put(DocumentMetadata.DISTANCE.value(), (float) distance);

        return Document.builder()
                .id(rs.getString("id"))
                .text(rs.getString("content"))
                .metadata(metadata)
                .score(1.0 - distance)
                .build();
    }

    static String distanceExpression(PgDistanceType distanceType) {
        return switch (distanceType) {
            case COSINE_DISTANCE -> "(embedding <=> ?)";
            case EUCLIDEAN_DISTANCE -> "(embedding <-> ?)";
            case NEGATIVE_INNER_PRODUCT -> "(1 + (embedding <#> ?))";
        };
    }
}
