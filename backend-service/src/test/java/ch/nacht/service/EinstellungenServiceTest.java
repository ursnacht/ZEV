package ch.nacht.service;

import ch.nacht.dto.EinstellungenDTO;
import ch.nacht.dto.RechnungKonfigurationDTO;
import ch.nacht.entity.Organisation;
import ch.nacht.repository.OrganisationRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EinstellungenServiceTest {

    @Mock
    private OrganisationRepository organisationRepository;

    @Mock
    private OrganizationContextService organizationContextService;

    @InjectMocks
    private EinstellungenService einstellungenService;

    private Long testOrgId;
    private Organisation testOrganisation;
    private String validKonfigurationJson;

    @BeforeEach
    void setUp() {
        testOrgId = 1L;
        validKonfigurationJson = """
            {"zahlungsfrist":"30 Tage","iban":"CH7006300016946459910","steller":{"name":"Urs Nacht","strasse":"Hangstrasse 14a","plz":"3044","ort":"Innerberg"}}""";

        testOrganisation = new Organisation();
        testOrganisation.setId(testOrgId);
        testOrganisation.setKeycloakOrgId(UUID.randomUUID());
        testOrganisation.setName("Test Organisation");
        testOrganisation.setErstelltAm(LocalDateTime.now());
        testOrganisation.setKonfiguration(validKonfigurationJson);
    }

    // ==================== getEinstellungen Tests ====================

    @Test
    void getEinstellungen_KonfigurationVorhanden_ReturnsDTO() {
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(organisationRepository.findById(testOrgId)).thenReturn(Optional.of(testOrganisation));

        EinstellungenDTO result = einstellungenService.getEinstellungen();

        assertNotNull(result);
        assertEquals(testOrgId, result.getId());
        assertNotNull(result.getRechnung());
        assertEquals("30 Tage", result.getRechnung().getZahlungsfrist());
        assertEquals("CH7006300016946459910", result.getRechnung().getIban());
        assertNotNull(result.getRechnung().getSteller());
        assertEquals("Urs Nacht", result.getRechnung().getSteller().getName());
        assertEquals("Hangstrasse 14a", result.getRechnung().getSteller().getStrasse());
        assertEquals("3044", result.getRechnung().getSteller().getPlz());
        assertEquals("Innerberg", result.getRechnung().getSteller().getOrt());
        verify(organizationContextService).getCurrentOrgId();
        verify(organisationRepository).findById(testOrgId);
    }

    @Test
    void getEinstellungen_KonfigurationNull_ReturnsNull() {
        testOrganisation.setKonfiguration(null);
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(organisationRepository.findById(testOrgId)).thenReturn(Optional.of(testOrganisation));

        EinstellungenDTO result = einstellungenService.getEinstellungen();

        assertNull(result);
        verify(organisationRepository).findById(testOrgId);
    }

    @Test
    void getEinstellungen_OrganisationNichtGefunden_ReturnsNull() {
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(organisationRepository.findById(testOrgId)).thenReturn(Optional.empty());

        EinstellungenDTO result = einstellungenService.getEinstellungen();

        assertNull(result);
        verify(organisationRepository).findById(testOrgId);
    }

    // ==================== getEinstellungenOrThrow Tests ====================

    @Test
    void getEinstellungenOrThrow_Vorhanden_ReturnsDTO() {
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(organisationRepository.findById(testOrgId)).thenReturn(Optional.of(testOrganisation));

        EinstellungenDTO result = einstellungenService.getEinstellungenOrThrow();

        assertNotNull(result);
        assertEquals(testOrgId, result.getId());
        assertEquals("30 Tage", result.getRechnung().getZahlungsfrist());
    }

    @Test
    void getEinstellungenOrThrow_NichtKonfiguriert_ThrowsIllegalStateException() {
        testOrganisation.setKonfiguration(null);
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(organisationRepository.findById(testOrgId)).thenReturn(Optional.of(testOrganisation));

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> einstellungenService.getEinstellungenOrThrow()
        );

        assertTrue(exception.getMessage().contains("nicht konfiguriert"));
    }

    // ==================== saveEinstellungen Tests ====================

    @Test
    void saveEinstellungen_Speichert_Konfiguration() {
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(organisationRepository.findById(testOrgId)).thenReturn(Optional.of(testOrganisation));
        when(organisationRepository.save(any(Organisation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RechnungKonfigurationDTO.StellerDTO steller = new RechnungKonfigurationDTO.StellerDTO(
            "Max Muster", "Neue Strasse 1", "8000", "Zürich"
        );
        RechnungKonfigurationDTO rechnung = new RechnungKonfigurationDTO(
            "60 Tage", "CH7006300016946459910", steller
        );
        EinstellungenDTO dto = new EinstellungenDTO(null, rechnung);

        EinstellungenDTO result = einstellungenService.saveEinstellungen(dto);

        assertNotNull(result);
        assertEquals(testOrgId, result.getId());
        assertEquals("60 Tage", result.getRechnung().getZahlungsfrist());
        assertEquals("Max Muster", result.getRechnung().getSteller().getName());
        verify(organisationRepository).save(testOrganisation);
    }

    @Test
    void saveEinstellungen_OrganisationNichtGefunden_ThrowsIllegalStateException() {
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(organisationRepository.findById(testOrgId)).thenReturn(Optional.empty());

        RechnungKonfigurationDTO.StellerDTO steller = new RechnungKonfigurationDTO.StellerDTO(
            "Test", "Strasse 1", "1000", "Ort"
        );
        RechnungKonfigurationDTO rechnung = new RechnungKonfigurationDTO(
            "30 Tage", "CH7006300016946459910", steller
        );
        EinstellungenDTO dto = new EinstellungenDTO(null, rechnung);

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> einstellungenService.saveEinstellungen(dto)
        );

        assertTrue(exception.getMessage().contains("Organisation nicht gefunden"));
        verify(organisationRepository, never()).save(any());
    }

    // ==================== JSON Conversion Error Tests ====================

    @Test
    void getEinstellungen_UngueltigesJson_ThrowsIllegalArgumentException() {
        testOrganisation.setKonfiguration("invalid json{{{");
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(organisationRepository.findById(testOrgId)).thenReturn(Optional.of(testOrganisation));

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> einstellungenService.getEinstellungen()
        );

        assertTrue(exception.getMessage().contains("Error reading settings from JSON"));
    }

    // ==================== Multi-Tenant Isolation Tests ====================

    @Test
    void getEinstellungen_VerwendetCurrentOrgId() {
        Long orgId1 = 1L;
        Long orgId2 = 2L;

        when(organizationContextService.getCurrentOrgId()).thenReturn(orgId1);
        when(organisationRepository.findById(orgId1)).thenReturn(Optional.empty());

        einstellungenService.getEinstellungen();

        verify(organisationRepository).findById(orgId1);
        verify(organisationRepository, never()).findById(orgId2);
    }
}
