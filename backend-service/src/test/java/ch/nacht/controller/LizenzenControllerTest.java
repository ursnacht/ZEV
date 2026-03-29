package ch.nacht.controller;

import ch.nacht.dto.LizenzenDTO;
import ch.nacht.dto.LizenzenHashDTO;
import ch.nacht.service.LizenzenService;
import ch.nacht.service.OrganisationService;
import ch.nacht.service.OrganizationContextService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LizenzenController.class)
@AutoConfigureMockMvc(addFilters = false)
public class LizenzenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LizenzenService lizenzenService;

    @MockitoBean
    private OrganizationContextService organizationContextService;

    @MockitoBean
    private OrganisationService organisationService;

    // ==================== GET /api/lizenzen ====================

    @Test
    void getLizenzen_ReturnsListOfComponents() throws Exception {
        List<LizenzenDTO> lizenzen = List.of(
            new LizenzenDTO("spring-core", "6.0.0", "Apache-2.0", "Pivotal", "https://spring.io",
                List.of(new LizenzenHashDTO("SHA-1", "abc123"))),
            new LizenzenDTO("jackson-core", "2.15.0", "Apache-2.0", "FasterXML", null, List.of())
        );
        when(lizenzenService.getBackendLizenzen()).thenReturn(lizenzen);

        mockMvc.perform(get("/api/lizenzen"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void getLizenzen_ReturnsCorrectFields() throws Exception {
        List<LizenzenDTO> lizenzen = List.of(
            new LizenzenDTO("spring-core", "6.0.0", "Apache-2.0", "Pivotal", "https://spring.io",
                List.of(new LizenzenHashDTO("SHA-1", "abc123")))
        );
        when(lizenzenService.getBackendLizenzen()).thenReturn(lizenzen);

        mockMvc.perform(get("/api/lizenzen"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name", is("spring-core")))
            .andExpect(jsonPath("$[0].version", is("6.0.0")))
            .andExpect(jsonPath("$[0].license", is("Apache-2.0")))
            .andExpect(jsonPath("$[0].publisher", is("Pivotal")))
            .andExpect(jsonPath("$[0].url", is("https://spring.io")));
    }

    @Test
    void getLizenzen_ReturnsHashesInResponse() throws Exception {
        List<LizenzenDTO> lizenzen = List.of(
            new LizenzenDTO("spring-core", "6.0.0", "Apache-2.0", null, null,
                List.of(
                    new LizenzenHashDTO("MD5", "aabbcc"),
                    new LizenzenHashDTO("SHA-1", "ddeeff")
                ))
        );
        when(lizenzenService.getBackendLizenzen()).thenReturn(lizenzen);

        mockMvc.perform(get("/api/lizenzen"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].hashes", hasSize(2)))
            .andExpect(jsonPath("$[0].hashes[0].algorithm", is("MD5")))
            .andExpect(jsonPath("$[0].hashes[0].value", is("aabbcc")))
            .andExpect(jsonPath("$[0].hashes[1].algorithm", is("SHA-1")))
            .andExpect(jsonPath("$[0].hashes[1].value", is("ddeeff")));
    }

    @Test
    void getLizenzen_EmptyList_ReturnsEmptyArray() throws Exception {
        when(lizenzenService.getBackendLizenzen()).thenReturn(List.of());

        mockMvc.perform(get("/api/lizenzen"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getLizenzen_SbomUnavailable_Returns503() throws Exception {
        when(lizenzenService.getBackendLizenzen())
            .thenThrow(new IllegalStateException("SBOM-Datei nicht verfügbar"));

        mockMvc.perform(get("/api/lizenzen"))
            .andExpect(status().isServiceUnavailable());
    }

    @Test
    void getLizenzen_SbomUnreadable_Returns503() throws Exception {
        when(lizenzenService.getBackendLizenzen())
            .thenThrow(new IllegalStateException("SBOM konnte nicht gelesen werden"));

        mockMvc.perform(get("/api/lizenzen"))
            .andExpect(status().isServiceUnavailable());
    }

    @Test
    void getLizenzen_ComponentWithNullPublisher_SerializesNull() throws Exception {
        List<LizenzenDTO> lizenzen = List.of(
            new LizenzenDTO("commons-io", "2.11.0", "Unknown", null, null, List.of())
        );
        when(lizenzenService.getBackendLizenzen()).thenReturn(lizenzen);

        mockMvc.perform(get("/api/lizenzen"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].publisher").doesNotExist());
    }
}
