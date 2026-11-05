package zas.admin.zia.rag.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import zas.admin.zia.rag.config.RetrieverAccess;
import zas.admin.zia.rag.retrieval.RetrievalService;
import zas.admin.zia.rag.retrieval.SourceService;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RetrievalController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({RetrievalControllerSecurityTest.MethodSecurityTestConfig.class, RetrieverAccess.class})
@TestPropertySource(properties = "zia.security.retriever-groups=" + RetrievalControllerSecurityTest.RETRIEVER_GROUP)
class RetrievalControllerSecurityTest {

    static final String RETRIEVER_GROUP =
            "cn=RETRIEVER,ou=DEV,ou=MyApp,ou=Applications,ou=Groups,dc=example,dc=org";
    private static final String OTHER_GROUP =
            "cn=READER,ou=DEV,ou=MyApp,ou=Applications,ou=Groups,dc=example,dc=org";

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RetrievalService retrievalService;

    @MockitoBean
    private SourceService sourceService;

    @Test
    @WithMockUser(authorities = {OTHER_GROUP, RETRIEVER_GROUP})
    void callerWithRetrieverGroup_isAllowed() throws Exception {
        when(sourceService.listSources()).thenReturn(List.of("faq"));

        mockMvc.perform(get("/api/v1/retrieval/sources"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = OTHER_GROUP)
    void callerWithoutRetrieverGroup_isForbiddenOnEveryEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/retrieval/sources"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/retrieval/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"question\": \"hello\" }"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(sourceService, retrievalService);
    }

    @Test
    @WithAnonymousUser
    void anonymousCaller_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/retrieval/sources"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(sourceService);
    }
}
