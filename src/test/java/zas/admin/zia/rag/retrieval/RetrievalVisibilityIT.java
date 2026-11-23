package zas.admin.zia.rag.retrieval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import zas.admin.zia.rag.observability.CorrelationIdFilter;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Visibility of the documents on a real pgvector database, through the whole application: public documents,
 * plus the private documents of {@code userId} when given, on the Spring AI {@code VectorStore} path (filter
 * expression) and on the SQL paths ({@code SqlWhereClause}: FILTER mode, vector search with a content filter).
 */
@SpringBootTest(properties = {
        "zas.security.blue-token.header-name=Blue",
        "zia.security.retriever-groups=" + RetrievalVisibilityIT.RETRIEVER_GROUP,
        "spring.ai.vectorstore.pgvector.dimensions=3"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class RetrievalVisibilityIT {

    static final String RETRIEVER_GROUP =
            "cn=RETRIEVER,ou=DEV,ou=MyApp,ou=Applications,ou=Groups,dc=example,dc=org";
    private static final String BLUE_HEADER = "Blue";
    private static final KeyPair GATEWAY_KEYS = generateRsaKeyPair();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg18").asCompatibleSubstituteFor("postgres"))
            // Extension et table créées hors transaction de test : créées puis annulées, elles laisseraient au
            // driver un OID de type périmé ("cache lookup failed for type").
            .withInitScript("db/retrieval-visibility-it.sql");

    private static final String PUBLIC_EMPTY = "00000000-0000-0000-0000-000000000001";
    private static final String PUBLIC_MISSING = "00000000-0000-0000-0000-000000000002";
    private static final String PRIVATE_U1 = "00000000-0000-0000-0000-000000000003";
    private static final String PRIVATE_U2 = "00000000-0000-0000-0000-000000000004";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private OpenAiEmbeddingModel embeddingModel;

    @DynamicPropertySource
    static void gatewayPublicKey(DynamicPropertyRegistry registry) {
        registry.add("zas.security.blue-token.public-key",
                () -> Base64.getEncoder().encodeToString(GATEWAY_KEYS.getPublic().getEncoded()));
    }

    @BeforeEach
    void data() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{1f, 0f, 0f});
        insert(PUBLIC_EMPTY, "{\"source\": \"it\", \"user_uuid\": \"\"}");
        insert(PUBLIC_MISSING, "{\"source\": \"it\"}");
        insert(PRIVATE_U1, "{\"source\": \"it\", \"user_uuid\": \"u-1\"}");
        insert(PRIVATE_U2, "{\"source\": \"it\", \"user_uuid\": \"u-2\"}");
    }

    private void insert(String id, String metadata) {
        jdbcTemplate.update("INSERT INTO vector_store (id, content, metadata, embedding) "
                + "VALUES (?::uuid, ?, ?::json, '[1,0,0]'::vector)", id, "cotisations " + id, metadata);
    }

    private ResultActions retrieve(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/retrieval/documents")
                .header(BLUE_HEADER, "Bearer " + blueToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String request(String searchMode, String userId, String contentContains) {
        return """
                { "question": "cotisations", %s
                  "config": { "searchMode": "%s", "useReranker": false, "topK": 10,
                              "metadataFilters": { "source": ["it"] }%s } }
                """.formatted(
                userId == null ? "" : "\"userId\": \"" + userId + "\",",
                searchMode,
                contentContains == null ? "" : ", \"contentContains\": \"" + contentContains + "\"");
    }

    @Test
    void sqlPath_withoutUserId_returnsPublicDocumentsOnly() throws Exception {
        retrieve(request("FILTER", null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(PUBLIC_EMPTY, PUBLIC_MISSING)));
    }

    @Test
    void sqlPath_withUserId_addsThePrivateDocumentsOfThatUserOnly() throws Exception {
        retrieve(request("FILTER", "u-1", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(PUBLIC_EMPTY, PUBLIC_MISSING, PRIVATE_U1)));
    }

    @Test
    void jdbcVectorPath_withUserId_addsThePrivateDocumentsOfThatUserOnly() throws Exception {
        retrieve(request("VECTOR", "u-1", "cotisations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(PUBLIC_EMPTY, PUBLIC_MISSING, PRIVATE_U1)));
    }

    // The Spring AI pgvector filter cannot match a missing key: public documents need "user_uuid": "" there.
    @Test
    void vectorStorePath_withoutUserId_returnsPublicDocumentsOnly() throws Exception {
        retrieve(request("VECTOR", null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(PUBLIC_EMPTY)));
    }

    @Test
    void vectorStorePath_withUserId_addsThePrivateDocumentsOfThatUserOnly() throws Exception {
        retrieve(request("VECTOR", "u-1", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(PUBLIC_EMPTY, PRIVATE_U1)));
    }

    @Test
    void userUuidMetadataFilter_isRejected_evenWithUserId() throws Exception {
        retrieve("""
                { "question": "cotisations", "userId": "u-1",
                  "config": { "searchMode": "FILTER", "metadataFilters": { "user_uuid": ["u-2"] } } }
                """)
                .andExpect(status().isBadRequest());
    }

    @Test
    void sources_listOnlyThePublicDocumentSources() throws Exception {
        jdbcTemplate.update("INSERT INTO vector_store (id, content, metadata, embedding) "
                + "VALUES ('00000000-0000-0000-0000-000000000005'::uuid, 'perso', "
                + "'{\"source\": \"user_pdf_upload\", \"user_uuid\": \"u-1\"}'::json, '[1,0,0]'::vector)");
        jdbcTemplate.update("INSERT INTO vector_store (id, content, metadata, embedding) "
                + "VALUES ('00000000-0000-0000-0000-000000000006'::uuid, 'faq', "
                + "'{\"source\": \"faq_bsv\"}'::json, '[1,0,0]'::vector)");

        mockMvc.perform(get("/api/v1/retrieval/sources").header(BLUE_HEADER, "Bearer " + blueToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", contains("faq_bsv", "it")));
    }

    @Test
    void correlationId_isEchoedOrGenerated() throws Exception {
        mockMvc.perform(post("/api/v1/retrieval/documents")
                        .header(CorrelationIdFilter.HEADER, "it-correlation-1")
                .header(BLUE_HEADER, "Bearer " + blueToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(request("FILTER", null, null)))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.HEADER, "it-correlation-1"));

        retrieve(request("FILTER", null, null))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.HEADER, matchesPattern("[0-9a-f-]{36}")));
    }

    private static String blueToken() throws GeneralSecurityException {
        String rolesJson = List.of(RETRIEVER_GROUP).stream()
                .map(role -> "\"" + role + "\"")
                .collect(Collectors.joining(",", "[", "]"));
        String subject = """
                {"userUID":"test-user","visa":"TST","firstname":"Test","lastname":"User","roles":%s}"""
                .formatted(rolesJson);
        String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
        String payload = "{\"sub\":\"%s\",\"exp\":%d}"
                .formatted(subject.replace("\"", "\\\""), Instant.now().plusSeconds(300).getEpochSecond());

        String signingInput = base64Url(header.getBytes(StandardCharsets.UTF_8)) + "."
                + base64Url(payload.getBytes(StandardCharsets.UTF_8));
        Signature signature = Signature.getInstance("SHA256withRSA");
        PrivateKey signingKey = GATEWAY_KEYS.getPrivate();
        signature.initSign(signingKey);
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + base64Url(signature.sign());
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
