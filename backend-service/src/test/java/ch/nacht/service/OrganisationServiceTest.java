package ch.nacht.service;

import ch.nacht.entity.Organisation;
import ch.nacht.repository.OrganisationRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
public class OrganisationServiceTest {

    @Mock
    private OrganisationRepository organisationRepository;

    @InjectMocks
    private OrganisationService organisationService;

    private UUID keycloakOrgId;
    private Organisation existingOrg;

    @BeforeEach
    void setUp() {
        keycloakOrgId = UUID.fromString("c2c9ba74-de18-4491-9489-8185629edd93");

        existingOrg = new Organisation();
        existingOrg.setId(1L);
        existingOrg.setKeycloakOrgId(keycloakOrgId);
        existingOrg.setName("Test Organisation");
        existingOrg.setErstelltAm(LocalDateTime.now());
    }

    @Test
    void findOrCreate_NewOrganisation_CreatesAndSavesOrg() {
        // Arrange
        when(organisationRepository.findByKeycloakOrgId(keycloakOrgId)).thenReturn(Optional.empty());
        when(organisationRepository.save(any(Organisation.class))).thenReturn(existingOrg);

        // Act
        Organisation result = organisationService.findOrCreate(keycloakOrgId, "Test Organisation");

        // Assert
        assertNotNull(result);
        ArgumentCaptor<Organisation> captor = ArgumentCaptor.forClass(Organisation.class);
        verify(organisationRepository).save(captor.capture());
        Organisation saved = captor.getValue();
        assertEquals(keycloakOrgId, saved.getKeycloakOrgId());
        assertEquals("Test Organisation", saved.getName());
        assertNotNull(saved.getErstelltAm());
    }

    @Test
    void findOrCreate_ExistingOrganisation_SameName_ReturnsWithoutSaving() {
        // Arrange
        when(organisationRepository.findByKeycloakOrgId(keycloakOrgId)).thenReturn(Optional.of(existingOrg));

        // Act
        Organisation result = organisationService.findOrCreate(keycloakOrgId, "Test Organisation");

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
        verify(organisationRepository, never()).save(any());
    }

    @Test
    void findOrCreate_ExistingOrganisation_NameChanged_UpdatesName() {
        // Arrange
        when(organisationRepository.findByKeycloakOrgId(keycloakOrgId)).thenReturn(Optional.of(existingOrg));
        when(organisationRepository.save(existingOrg)).thenReturn(existingOrg);

        // Act
        Organisation result = organisationService.findOrCreate(keycloakOrgId, "Neuer Name");

        // Assert
        assertEquals("Neuer Name", existingOrg.getName());
        verify(organisationRepository).save(existingOrg);
    }

    @Test
    void findOrCreate_NewOrganisation_NullName_UsesUuidAsName() {
        // Arrange
        when(organisationRepository.findByKeycloakOrgId(keycloakOrgId)).thenReturn(Optional.empty());
        when(organisationRepository.save(any(Organisation.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        organisationService.findOrCreate(keycloakOrgId, null);

        // Assert
        ArgumentCaptor<Organisation> captor = ArgumentCaptor.forClass(Organisation.class);
        verify(organisationRepository).save(captor.capture());
        assertEquals(keycloakOrgId.toString(), captor.getValue().getName());
    }

    @Test
    void findOrCreate_ExistingOrganisation_NullName_DoesNotUpdateName() {
        // Arrange
        when(organisationRepository.findByKeycloakOrgId(keycloakOrgId)).thenReturn(Optional.of(existingOrg));

        // Act
        organisationService.findOrCreate(keycloakOrgId, null);

        // Assert
        assertEquals("Test Organisation", existingOrg.getName());
        verify(organisationRepository, never()).save(any());
    }

    @Test
    void findOrCreate_NewOrganisation_SetsErstelltAm() {
        // Arrange
        when(organisationRepository.findByKeycloakOrgId(keycloakOrgId)).thenReturn(Optional.empty());
        when(organisationRepository.save(any(Organisation.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        // Act
        organisationService.findOrCreate(keycloakOrgId, "Test");

        // Assert
        ArgumentCaptor<Organisation> captor = ArgumentCaptor.forClass(Organisation.class);
        verify(organisationRepository).save(captor.capture());
        assertNotNull(captor.getValue().getErstelltAm());
        assertTrue(captor.getValue().getErstelltAm().isAfter(before));
    }

    @Test
    void findOrCreate_ExistingOrganisation_ReturnsExistingInstance() {
        // Arrange
        when(organisationRepository.findByKeycloakOrgId(keycloakOrgId)).thenReturn(Optional.of(existingOrg));

        // Act
        Organisation result = organisationService.findOrCreate(keycloakOrgId, "Test Organisation");

        // Assert
        assertSame(existingOrg, result);
        verify(organisationRepository, never()).save(any());
    }

    @Test
    void findOrCreate_NameUpdate_UeberschreibtKonfigurationNicht() {
        // Arrange
        existingOrg.setKonfiguration("{\"zahlungsfrist\":\"30 Tage\"}");
        when(organisationRepository.findByKeycloakOrgId(keycloakOrgId)).thenReturn(Optional.of(existingOrg));
        when(organisationRepository.save(existingOrg)).thenReturn(existingOrg);

        // Act
        Organisation result = organisationService.findOrCreate(keycloakOrgId, "Neuer Name");

        // Assert
        assertEquals("Neuer Name", result.getName());
        assertEquals("{\"zahlungsfrist\":\"30 Tage\"}", result.getKonfiguration());
    }
}
