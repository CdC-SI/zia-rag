package zas.admin.zia.rag.web;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import zas.admin.zia.rag.retrieval.InvalidSearchFilterException;
import zas.admin.zia.rag.retrieval.RetrievalService;
import zas.admin.zia.rag.retrieval.SourceService;
import zas.admin.zia.rag.web.dto.RetrievalConfig;
import zas.admin.zia.rag.web.dto.SearchMode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RetrievalController.class)
@AutoConfigureMockMvc(addFilters = false)
class RetrievalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RetrievalService retrievalService;

    @MockitoBean
    private SourceService sourceService;

    @Test
    void retrieveDocuments_returnsDocuments() throws Exception {
        when(retrievalService.retrieve(eq("hello"), any()))
                .thenReturn(List.of(Document.builder().id("d1").text("body").build()));

        mockMvc.perform(post("/api/v1/retrieval/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "question": "hello", "config": { "topK": 3 } }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("d1"))
                .andExpect(jsonPath("$[0].text").value("body"));
    }

    @Test
    void retrieveDocuments_bindsNewConfigFields() throws Exception {
        when(retrievalService.retrieve(eq("hello"), any())).thenReturn(List.of());

        mockMvc.perform(post("/api/v1/retrieval/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "hello",
                                  "config": {
                                    "searchMode": "BM25",
                                    "metadataFilters": { "lang": ["fr"] },
                                    "contentContains": "mot de passe"
                                  }
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<RetrievalConfig> captor = ArgumentCaptor.forClass(RetrievalConfig.class);
        verify(retrievalService).retrieve(eq("hello"), captor.capture());
        RetrievalConfig config = captor.getValue();
        assertThat(config.searchMode()).isEqualTo(SearchMode.BM25);
        assertThat(config.metadataFilters()).containsEntry("lang", List.of("fr"));
        assertThat(config.contentContains()).isEqualTo("mot de passe");
    }

    @Test
    void retrieveDocuments_invalidFilter_returnsBadRequest() throws Exception {
        when(retrievalService.retrieve(eq("hello"), any()))
                .thenThrow(new InvalidSearchFilterException("Filtering on metadata key 'user_uuid' is not allowed"));

        mockMvc.perform(post("/api/v1/retrieval/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "question": "hello", "config": { "metadataFilters": { "user_uuid": ["x"] } } }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Filtering on metadata key 'user_uuid' is not allowed"));
    }

    @Test
    void retrieveDocuments_unknownSearchMode_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/retrieval/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "question": "hello", "config": { "searchMode": "FOO" } }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(retrievalService);
    }

    @Test
    void retrieveDocuments_blankQuestion_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/retrieval/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "question": "  " }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(retrievalService);
    }

    @Test
    void retrieveDocuments_missingQuestionOutsideFilterMode_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/retrieval/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "config": { "searchMode": "VECTOR", "metadataFilters": { "lang": ["fr"] } } }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(retrievalService);
    }

    @Test
    void retrieveDocuments_filterModeWithoutQuestion_returnsDocuments() throws Exception {
        when(retrievalService.retrieve(any(), any()))
                .thenReturn(List.of(Document.builder().id("d1").text("body").build()));

        mockMvc.perform(post("/api/v1/retrieval/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "config": { "searchMode": "FILTER", "metadataFilters": { "file_name": ["f1"] } } }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("d1"));

        ArgumentCaptor<RetrievalConfig> captor = ArgumentCaptor.forClass(RetrievalConfig.class);
        verify(retrievalService).retrieve(isNull(), captor.capture());
        assertThat(captor.getValue().searchMode()).isEqualTo(SearchMode.FILTER);
    }

    @Test
    void listSources_returnsSources() throws Exception {
        when(sourceService.listSources()).thenReturn(List.of("faq", "handbook"));

        mockMvc.perform(get("/api/v1/retrieval/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("faq"))
                .andExpect(jsonPath("$[1]").value("handbook"));
    }
}
