package ch.nacht.service;

import ch.nacht.dto.EinstellungenDTO;
import ch.nacht.dto.RechnungKonfigurationDTO;
import ch.nacht.entity.Organisation;
import ch.nacht.entity.Verteilmodus;
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

    @Test
    void getEinstellungenOrThrow_OrganisationNichtGefunden_ThrowsIllegalStateException() {
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(organisationRepository.findById(testOrgId)).thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> einstellungenService.getEinstellungenOrThrow()
        );

        assertTrue(exception.getMessage().contains("nicht konfiguriert"));
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

    @Test
    void saveEinstellungen_VerwendetCurrentOrgId() {
        Long orgId1 = 1L;
        Long orgId2 = 2L;

        when(organizationContextService.getCurrentOrgId()).thenReturn(orgId1);
        when(organisationRepository.findById(orgId1)).thenReturn(Optional.of(testOrganisation));
        when(organisationRepository.save(any(Organisation.class))).thenAnswer(inv -> inv.getArgument(0));

        RechnungKonfigurationDTO.StellerDTO steller = new RechnungKonfigurationDTO.StellerDTO(
            "Test", "Strasse 1", "1000", "Ort"
        );
        EinstellungenDTO dto = new EinstellungenDTO(null, new RechnungKonfigurationDTO("30 Tage", "CH7006300016946459910", steller));

        einstellungenService.saveEinstellungen(dto);

        verify(organisationRepository).findById(orgId1);
        verify(organisationRepository, never()).findById(orgId2);
    }

    // ==================== getEinstellungenForOrg Tests (org-explizit) ====================

    @Test
    void getEinstellungenForOrg_KonfigurationVorhanden_ReturnsDTO() {
        when(organisationRepository.findById(testOrgId)).thenReturn(Optional.of(testOrganisation));

        EinstellungenDTO result = einstellungenService.getEinstellungenForOrg(testOrgId);

        assertNotNull(result);
        assertEquals(testOrgId, result.getId());
        assertEquals("30 Tage", result.getRechnung().getZahlungsfrist());
        verify(organisationRepository).findById(testOrgId);
        // org-explizit: darf NICHT über den Request-Kontext gehen (FR-1.4)
        verify(organizationContextService, never()).getCurrentOrgId();
    }

    @Test
    void getEinstellungenForOrg_KonfigurationNull_ReturnsNull() {
        testOrganisation.setKonfiguration(null);
        when(organisationRepository.findById(testOrgId)).thenReturn(Optional.of(testOrganisation));

        assertNull(einstellungenService.getEinstellungenForOrg(testOrgId));
        verify(organizationContextService, never()).getCurrentOrgId();
    }

    @Test
    void getEinstellungenForOrg_OrganisationNichtGefunden_ReturnsNull() {
        when(organisationRepository.findById(testOrgId)).thenReturn(Optional.empty());

        assertNull(einstellungenService.getEinstellungenForOrg(testOrgId));
    }

    // ==================== getVerteilmodus Tests ====================

    @Test
    void getVerteilmodus_KeineKonfiguration_ReturnsDefaultProducerMessung() {
        // Mandant ohne Einstellungen (konfiguration == null) → Default, keine Exception
        testOrganisation.setKonfiguration(null);
        when(organisationRepository.findById(testOrgId)).thenReturn(Optional.of(testOrganisation));

        assertEquals(Verteilmodus.PRODUCER_MESSUNG, einstellungenService.getVerteilmodus(testOrgId));
        verify(organizationContextService, never()).getCurrentOrgId();
    }

    @Test
    void getVerteilmodus_OrganisationNichtGefunden_ReturnsDefault() {
        when(organisationRepository.findById(testOrgId)).thenReturn(Optional.empty());

        assertEquals(Verteilmodus.PRODUCER_MESSUNG, einstellungenService.getVerteilmodus(testOrgId));
    }

    @Test
    void getVerteilmodus_FeldFehltImJson_ReturnsDefault() {
        // Bestandsmandant: altes JSON ohne verteilmodus-Feld → null → Default (rückwärtskompatibel)
        when(organisationRepository.findById(testOrgId)).thenReturn(Optional.of(testOrganisation));

        assertEquals(Verteilmodus.PRODUCER_MESSUNG, einstellungenService.getVerteilmodus(testOrgId));
    }

    @Test
    void getVerteilmodus_BilanzGesetzt_ReturnsBilanz() {
        testOrganisation.setKonfiguration("""
            {"zahlungsfrist":"30 Tage","iban":"CH7006300016946459910","steller":{"name":"Urs Nacht","strasse":"Hangstrasse 14a","plz":"3044","ort":"Innerberg"},"verteilmodus":"BILANZ"}""");
        when(organisationRepository.findById(testOrgId)).thenReturn(Optional.of(testOrganisation));

        assertEquals(Verteilmodus.BILANZ, einstellungenService.getVerteilmodus(testOrgId));
    }

    @Test
    void getVerteilmodus_ProducerMessungGesetzt_ReturnsProducerMessung() {
        testOrganisation.setKonfiguration("""
            {"zahlungsfrist":"30 Tage","iban":"CH7006300016946459910","steller":{"name":"Urs Nacht","strasse":"Hangstrasse 14a","plz":"3044","ort":"Innerberg"},"verteilmodus":"PRODUCER_MESSUNG"}""");
        when(organisationRepository.findById(testOrgId)).thenReturn(Optional.of(testOrganisation));

        assertEquals(Verteilmodus.PRODUCER_MESSUNG, einstellungenService.getVerteilmodus(testOrgId));
    }

    @Test
    void getVerteilmodus_UnbekannterEnumWert_ReturnsDefault() {
        // READ_UNKNOWN_ENUM_VALUES_AS_NULL: unbekannter/künftiger Wert → null → Default
        testOrganisation.setKonfiguration("""
            {"zahlungsfrist":"30 Tage","iban":"CH7006300016946459910","steller":{"name":"Urs Nacht","strasse":"Hangstrasse 14a","plz":"3044","ort":"Innerberg"},"verteilmodus":"UNBEKANNT"}""");
        when(organisationRepository.findById(testOrgId)).thenReturn(Optional.of(testOrganisation));

        assertEquals(Verteilmodus.PRODUCER_MESSUNG, einstellungenService.getVerteilmodus(testOrgId));
    }

    @Test
    void getVerteilmodus_VerwendetExpliziteOrgId_NichtCurrentOrgId() {
        // Hintergrund-Lauf (MQTT): org-explizit lesen, nie getCurrentOrgId (sonst NoOrganizationException)
        Long explicitOrgId = 42L;
        when(organisationRepository.findById(explicitOrgId)).thenReturn(Optional.empty());

        einstellungenService.getVerteilmodus(explicitOrgId);

        verify(organisationRepository).findById(explicitOrgId);
        verify(organizationContextService, never()).getCurrentOrgId();
    }

    @Test
    void saveEinstellungen_MitVerteilmodusBilanz_RoundtripBehaeltModus() {
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(organisationRepository.findById(testOrgId)).thenReturn(Optional.of(testOrganisation));
        when(organisationRepository.save(any(Organisation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RechnungKonfigurationDTO.StellerDTO steller = new RechnungKonfigurationDTO.StellerDTO(
            "Max Muster", "Neue Strasse 1", "8000", "Zürich"
        );
        RechnungKonfigurationDTO rechnung = new RechnungKonfigurationDTO(
            "60 Tage", "CH7006300016946459910", steller
        );
        rechnung.setVerteilmodus(Verteilmodus.BILANZ);
        EinstellungenDTO dto = new EinstellungenDTO(null, rechnung);

        EinstellungenDTO result = einstellungenService.saveEinstellungen(dto);

        // Roundtrip: Modus wird als JSONB serialisiert und wieder gelesen
        assertEquals(Verteilmodus.BILANZ, result.getRechnung().getVerteilmodus());
    }
}
