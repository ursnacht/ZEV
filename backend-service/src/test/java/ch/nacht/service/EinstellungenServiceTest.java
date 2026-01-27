package ch.nacht.service;

import ch.nacht.dto.EinstellungenDTO;
import ch.nacht.dto.RechnungKonfigurationDTO;
import ch.nacht.entity.Einstellungen;
import ch.nacht.repository.EinstellungenRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EinstellungenServiceTest {

    @Mock
    private EinstellungenRepository einstellungenRepository;

    @Mock
    private OrganizationContextService organizationContextService;

    @InjectMocks
    private EinstellungenService einstellungenService;

    private UUID testOrgId;
    private Einstellungen testEinstellungen;
    private String validKonfigurationJson;

    @BeforeEach
    void setUp() {
        testOrgId = UUID.randomUUID();
        validKonfigurationJson = """
            {"zahlungsfrist":"30 Tage","iban":"CH7006300016946459910","steller":{"name":"Urs Nacht","strasse":"Hangstrasse 14a","plz":"3044","ort":"Innerberg"}}""";

        testEinstellungen = new Einstellungen(testOrgId, validKonfigurationJson);
        testEinstellungen.setId(1L);
    }

    // ==================== getEinstellungen Tests ====================

    @Test
    void getEinstellungen_Found_ReturnsDTO() {
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(einstellungenRepository.findByOrgId(testOrgId)).thenReturn(Optional.of(testEinstellungen));

        EinstellungenDTO result = einstellungenService.getEinstellungen();

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertNotNull(result.getRechnung());
        assertEquals("30 Tage", result.getRechnung().getZahlungsfrist());
        assertEquals("CH7006300016946459910", result.getRechnung().getIban());
        assertNotNull(result.getRechnung().getSteller());
        assertEquals("Urs Nacht", result.getRechnung().getSteller().getName());
        assertEquals("Hangstrasse 14a", result.getRechnung().getSteller().getStrasse());
        assertEquals("3044", result.getRechnung().getSteller().getPlz());
        assertEquals("Innerberg", result.getRechnung().getSteller().getOrt());
        verify(organizationContextService).getCurrentOrgId();
        verify(einstellungenRepository).findByOrgId(testOrgId);
    }

    @Test
    void getEinstellungen_NotFound_ReturnsNull() {
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(einstellungenRepository.findByOrgId(testOrgId)).thenReturn(Optional.empty());

        EinstellungenDTO result = einstellungenService.getEinstellungen();

        assertNull(result);
        verify(einstellungenRepository).findByOrgId(testOrgId);
    }

    // ==================== getEinstellungenOrThrow Tests ====================

    @Test
    void getEinstellungenOrThrow_Found_ReturnsDTO() {
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(einstellungenRepository.findByOrgId(testOrgId)).thenReturn(Optional.of(testEinstellungen));

        EinstellungenDTO result = einstellungenService.getEinstellungenOrThrow();

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("30 Tage", result.getRechnung().getZahlungsfrist());
    }

    @Test
    void getEinstellungenOrThrow_NotFound_ThrowsIllegalStateException() {
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(einstellungenRepository.findByOrgId(testOrgId)).thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> einstellungenService.getEinstellungenOrThrow()
        );

        assertTrue(exception.getMessage().contains("nicht konfiguriert"));
    }

    // ==================== saveEinstellungen Tests ====================

    @Test
    void saveEinstellungen_NewSettings_CreatesSuccessfully() {
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(einstellungenRepository.findByOrgId(testOrgId)).thenReturn(Optional.empty());
        when(einstellungenRepository.save(any(Einstellungen.class))).thenAnswer(invocation -> {
            Einstellungen saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        RechnungKonfigurationDTO.StellerDTO steller = new RechnungKonfigurationDTO.StellerDTO(
            "Urs Nacht", "Hangstrasse 14a", "3044", "Innerberg"
        );
        RechnungKonfigurationDTO rechnung = new RechnungKonfigurationDTO(
            "30 Tage", "CH7006300016946459910", steller
        );
        EinstellungenDTO dto = new EinstellungenDTO(null, rechnung);

        EinstellungenDTO result = einstellungenService.saveEinstellungen(dto);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("30 Tage", result.getRechnung().getZahlungsfrist());
        verify(einstellungenRepository).save(any(Einstellungen.class));
    }

    @Test
    void saveEinstellungen_ExistingSettings_UpdatesSuccessfully() {
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(einstellungenRepository.findByOrgId(testOrgId)).thenReturn(Optional.of(testEinstellungen));
        when(einstellungenRepository.save(any(Einstellungen.class))).thenReturn(testEinstellungen);

        RechnungKonfigurationDTO.StellerDTO steller = new RechnungKonfigurationDTO.StellerDTO(
            "Max Muster", "Neue Strasse 1", "8000", "Zürich"
        );
        RechnungKonfigurationDTO rechnung = new RechnungKonfigurationDTO(
            "60 Tage", "CH7006300016946459910", steller
        );
        EinstellungenDTO dto = new EinstellungenDTO(1L, rechnung);

        EinstellungenDTO result = einstellungenService.saveEinstellungen(dto);

        assertNotNull(result);
        verify(einstellungenRepository).save(testEinstellungen);
        // Verify that setKonfiguration was called on the existing entity
        verify(einstellungenRepository, never()).save(argThat(e -> e.getId() == null));
    }

    @Test
    void saveEinstellungen_UpdateSetsNewKonfiguration() {
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(einstellungenRepository.findByOrgId(testOrgId)).thenReturn(Optional.of(testEinstellungen));
        when(einstellungenRepository.save(any(Einstellungen.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RechnungKonfigurationDTO.StellerDTO steller = new RechnungKonfigurationDTO.StellerDTO(
            "Max Muster", "Neue Strasse 1", "8000", "Zürich"
        );
        RechnungKonfigurationDTO rechnung = new RechnungKonfigurationDTO(
            "60 Tage", "CH7006300016946459910", steller
        );
        EinstellungenDTO dto = new EinstellungenDTO(1L, rechnung);

        EinstellungenDTO result = einstellungenService.saveEinstellungen(dto);

        assertNotNull(result);
        assertEquals("60 Tage", result.getRechnung().getZahlungsfrist());
        assertEquals("Max Muster", result.getRechnung().getSteller().getName());
    }

    // ==================== JSON Conversion Error Tests ====================

    @Test
    void getEinstellungen_InvalidJson_ThrowsIllegalArgumentException() {
        Einstellungen invalidEntity = new Einstellungen(testOrgId, "invalid json{{{");
        invalidEntity.setId(2L);

        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(einstellungenRepository.findByOrgId(testOrgId)).thenReturn(Optional.of(invalidEntity));

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> einstellungenService.getEinstellungen()
        );

        assertTrue(exception.getMessage().contains("Error reading settings from JSON"));
    }

    // ==================== Multi-Tenant Isolation Tests ====================

    @Test
    void getEinstellungen_UsesCurrentOrgId() {
        UUID orgId1 = UUID.randomUUID();
        UUID orgId2 = UUID.randomUUID();

        when(organizationContextService.getCurrentOrgId()).thenReturn(orgId1);
        when(einstellungenRepository.findByOrgId(orgId1)).thenReturn(Optional.empty());

        einstellungenService.getEinstellungen();

        verify(einstellungenRepository).findByOrgId(orgId1);
        verify(einstellungenRepository, never()).findByOrgId(orgId2);
    }

    @Test
    void saveEinstellungen_NewSettings_SetsCorrectOrgId() {
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(einstellungenRepository.findByOrgId(testOrgId)).thenReturn(Optional.empty());
        when(einstellungenRepository.save(any(Einstellungen.class))).thenAnswer(invocation -> {
            Einstellungen saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        RechnungKonfigurationDTO.StellerDTO steller = new RechnungKonfigurationDTO.StellerDTO(
            "Test", "Strasse 1", "1000", "Ort"
        );
        RechnungKonfigurationDTO rechnung = new RechnungKonfigurationDTO(
            "30 Tage", "CH7006300016946459910", steller
        );
        EinstellungenDTO dto = new EinstellungenDTO(null, rechnung);

        einstellungenService.saveEinstellungen(dto);

        verify(einstellungenRepository).save(argThat(e -> testOrgId.equals(e.getOrgId())));
    }
}
