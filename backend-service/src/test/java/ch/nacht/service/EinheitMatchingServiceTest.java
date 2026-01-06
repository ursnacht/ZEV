package ch.nacht.service;

import ch.nacht.dto.EinheitMatchResponseDTO;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EinheitMatchingServiceTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec responseSpec;

    @Mock
    private EinheitService einheitService;

    @Mock
    private ChatResponse chatResponse;

    @Mock
    private Generation generation;

    private EinheitMatchingService einheitMatchingService;

    private Einheit einheit1;
    private Einheit einheit2;
    private Einheit einheit3;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        einheitMatchingService = new EinheitMatchingService(chatClientBuilder, einheitService);

        einheit1 = new Einheit("Allgemein", EinheitTyp.CONSUMER);
        einheit1.setId(1L);

        einheit2 = new Einheit("1. Stock links", EinheitTyp.CONSUMER);
        einheit2.setId(2L);

        einheit3 = new Einheit("PV-Anlage", EinheitTyp.PRODUCER);
        einheit3.setId(3L);
    }

    @Test
    void matchEinheitByFilename_NoUnitsAvailable_ReturnsNotMatched() {
        when(einheitService.getAllEinheiten()).thenReturn(Collections.emptyList());

        EinheitMatchResponseDTO result = einheitMatchingService.matchEinheitByFilename("2025-07-test.csv");

        assertFalse(result.isMatched());
        assertEquals("Keine Einheiten verfügbar", result.getMessage());
        assertEquals(0.0, result.getConfidence());
        verify(chatClient, never()).prompt();
    }

    private void setupChatClientMock(String responseText) {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.chatResponse()).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(new AssistantMessage(responseText));
    }

    @Test
    void matchEinheitByFilename_ClaudeReturnsValidId_ReturnsMatch() {
        List<Einheit> einheiten = Arrays.asList(einheit1, einheit2, einheit3);
        when(einheitService.getAllEinheiten()).thenReturn(einheiten);

        setupChatClientMock("2");

        EinheitMatchResponseDTO result = einheitMatchingService.matchEinheitByFilename("2025-07-1-li.csv");

        assertTrue(result.isMatched());
        assertEquals(2L, result.getEinheitId());
        assertEquals("1. Stock links", result.getEinheitName());
        assertEquals(0.9, result.getConfidence());
    }

    @Test
    void matchEinheitByFilename_ClaudeReturnsKEINE_ReturnsNotMatched() {
        List<Einheit> einheiten = Arrays.asList(einheit1, einheit2, einheit3);
        when(einheitService.getAllEinheiten()).thenReturn(einheiten);

        setupChatClientMock("KEINE");

        EinheitMatchResponseDTO result = einheitMatchingService.matchEinheitByFilename("2025-07-unknown.csv");

        assertFalse(result.isMatched());
        assertEquals("Keine passende Einheit gefunden", result.getMessage());
        assertEquals(0.0, result.getConfidence());
    }

    @Test
    void matchEinheitByFilename_ClaudeReturnsInvalidId_ReturnsNotMatched() {
        List<Einheit> einheiten = Arrays.asList(einheit1, einheit2, einheit3);
        when(einheitService.getAllEinheiten()).thenReturn(einheiten);

        setupChatClientMock("999");

        EinheitMatchResponseDTO result = einheitMatchingService.matchEinheitByFilename("2025-07-test.csv");

        assertFalse(result.isMatched());
        assertEquals("Einheit-ID nicht gefunden", result.getMessage());
        assertEquals(0.0, result.getConfidence());
    }

    @Test
    void matchEinheitByFilename_ClaudeReturnsNonNumeric_ReturnsNotMatched() {
        List<Einheit> einheiten = Arrays.asList(einheit1, einheit2, einheit3);
        when(einheitService.getAllEinheiten()).thenReturn(einheiten);

        setupChatClientMock("invalid response");

        EinheitMatchResponseDTO result = einheitMatchingService.matchEinheitByFilename("2025-07-test.csv");

        assertFalse(result.isMatched());
        assertEquals("Ungültige Antwort vom KI-Service", result.getMessage());
        assertEquals(0.0, result.getConfidence());
    }

    @Test
    void matchEinheitByFilename_ClaudeApiError_ReturnsErrorMessage() {
        List<Einheit> einheiten = Arrays.asList(einheit1, einheit2, einheit3);
        when(einheitService.getAllEinheiten()).thenReturn(einheiten);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenThrow(new RuntimeException("Connection refused"));

        EinheitMatchResponseDTO result = einheitMatchingService.matchEinheitByFilename("2025-07-test.csv");

        assertFalse(result.isMatched());
        assertTrue(result.getMessage().contains("KI-Service"));
        assertEquals(0.0, result.getConfidence());
    }

    private void setupChatClientMockForError(RuntimeException exception) {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenThrow(exception);
    }

    @Test
    void matchEinheitByFilename_InvalidApiKey_ReturnsSpecificErrorMessage() {
        List<Einheit> einheiten = Arrays.asList(einheit1, einheit2, einheit3);
        when(einheitService.getAllEinheiten()).thenReturn(einheiten);

        setupChatClientMockForError(new RuntimeException("Invalid API key"));

        EinheitMatchResponseDTO result = einheitMatchingService.matchEinheitByFilename("2025-07-test.csv");

        assertFalse(result.isMatched());
        assertEquals("Ungültiger API-Key für KI-Service", result.getMessage());
    }

    @Test
    void matchEinheitByFilename_RateLimitError_ReturnsSpecificErrorMessage() {
        List<Einheit> einheiten = Arrays.asList(einheit1, einheit2, einheit3);
        when(einheitService.getAllEinheiten()).thenReturn(einheiten);

        setupChatClientMockForError(new RuntimeException("Rate limit exceeded"));

        EinheitMatchResponseDTO result = einheitMatchingService.matchEinheitByFilename("2025-07-test.csv");

        assertFalse(result.isMatched());
        assertEquals("KI-Service überlastet, bitte später versuchen", result.getMessage());
    }

    @Test
    void matchEinheitByFilename_TimeoutError_ReturnsSpecificErrorMessage() {
        List<Einheit> einheiten = Arrays.asList(einheit1, einheit2, einheit3);
        when(einheitService.getAllEinheiten()).thenReturn(einheiten);

        setupChatClientMockForError(new RuntimeException("Request timed out"));

        EinheitMatchResponseDTO result = einheitMatchingService.matchEinheitByFilename("2025-07-test.csv");

        assertFalse(result.isMatched());
        assertEquals("KI-Service antwortet nicht (Timeout)", result.getMessage());
    }

    @Test
    void matchEinheitByFilename_ConnectionError_ReturnsSpecificErrorMessage() {
        List<Einheit> einheiten = Arrays.asList(einheit1, einheit2, einheit3);
        when(einheitService.getAllEinheiten()).thenReturn(einheiten);

        setupChatClientMockForError(new RuntimeException("Connection refused"));

        EinheitMatchResponseDTO result = einheitMatchingService.matchEinheitByFilename("2025-07-test.csv");

        assertFalse(result.isMatched());
        assertEquals("KI-Service nicht erreichbar", result.getMessage());
    }

    @Test
    void matchEinheitByFilename_NullExceptionMessage_ReturnsDefaultErrorMessage() {
        List<Einheit> einheiten = Arrays.asList(einheit1, einheit2, einheit3);
        when(einheitService.getAllEinheiten()).thenReturn(einheiten);

        setupChatClientMockForError(new RuntimeException((String) null));

        EinheitMatchResponseDTO result = einheitMatchingService.matchEinheitByFilename("2025-07-test.csv");

        assertFalse(result.isMatched());
        assertEquals("KI-Service nicht verfügbar", result.getMessage());
    }

    @Test
    void matchEinheitByFilename_FilenameWithDatePrefix_ExtractsUnitIdentifier() {
        List<Einheit> einheiten = Arrays.asList(einheit1, einheit2, einheit3);
        when(einheitService.getAllEinheiten()).thenReturn(einheiten);

        setupChatClientMock("1");

        EinheitMatchResponseDTO result = einheitMatchingService.matchEinheitByFilename("2025-07-allg.csv");

        assertTrue(result.isMatched());
        assertEquals(1L, result.getEinheitId());
        assertEquals("Allgemein", result.getEinheitName());
    }

    @Test
    void matchEinheitByFilename_ClaudeReturnsIdWithWhitespace_ParsesCorrectly() {
        List<Einheit> einheiten = Arrays.asList(einheit1, einheit2, einheit3);
        when(einheitService.getAllEinheiten()).thenReturn(einheiten);

        setupChatClientMock("  3  ");

        EinheitMatchResponseDTO result = einheitMatchingService.matchEinheitByFilename("2025-07-pv.csv");

        assertTrue(result.isMatched());
        assertEquals(3L, result.getEinheitId());
        assertEquals("PV-Anlage", result.getEinheitName());
    }

    @Test
    void matchEinheitByFilename_ClaudeReturnsKeineLowercase_ReturnsNotMatched() {
        List<Einheit> einheiten = Arrays.asList(einheit1, einheit2, einheit3);
        when(einheitService.getAllEinheiten()).thenReturn(einheiten);

        setupChatClientMock("keine");

        EinheitMatchResponseDTO result = einheitMatchingService.matchEinheitByFilename("2025-07-unknown.csv");

        assertFalse(result.isMatched());
        assertEquals("Keine passende Einheit gefunden", result.getMessage());
    }
}
