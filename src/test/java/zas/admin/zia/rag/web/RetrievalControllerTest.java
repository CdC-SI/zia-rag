package zas.admin.zia.rag.web;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import zas.admin.zia.rag.retrieval.RetrievalService;
import zas.admin.zia.rag.retrieval.SourceService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    void listSources_returnsSources() throws Exception {
        when(sourceService.listSources()).thenReturn(List.of("faq", "handbook"));

        mockMvc.perform(get("/api/v1/retrieval/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("faq"))
                .andExpect(jsonPath("$[1]").value("handbook"));
    }
}
