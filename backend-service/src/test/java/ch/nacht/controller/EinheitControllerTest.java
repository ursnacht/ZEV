package ch.nacht.controller;

import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.service.EinheitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EinheitController.class)
public class EinheitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EinheitService einheitService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void createEinheit_ValidInput_ReturnsCreated() throws Exception {
        Einheit einheit = new Einheit("Valid Name", EinheitTyp.CONSUMER);
        when(einheitService.createEinheit(any(Einheit.class))).thenReturn(einheit);

        mockMvc.perform(post("/api/einheit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(einheit)))
                .andExpect(status().isCreated());
    }

    @Test
    public void createEinheit_InvalidName_ReturnsBadRequest() throws Exception {
        Einheit einheit = new Einheit("", EinheitTyp.CONSUMER); // Empty name

        mockMvc.perform(post("/api/einheit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(einheit)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void createEinheit_NullTyp_ReturnsBadRequest() throws Exception {
        Einheit einheit = new Einheit("Valid Name", null); // Null typ

        mockMvc.perform(post("/api/einheit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(einheit)))
                .andExpect(status().isBadRequest());
    }
}
