package ch.nacht.service;

import ch.nacht.dto.FeatureFlagDTO;
import ch.nacht.entity.FeatureFlag;
import ch.nacht.entity.Organisation;
import ch.nacht.repository.OrganisationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit-Tests für {@link FeatureFlagService}.
 * <p>
 * Hinweis: Die {@code organisation}-Tabelle ist NICHT mandantengefiltert (kein {@code @Filter},
 * kein {@link HibernateFilterService}). Der Service liest die Organisation direkt per {@code orgId}
 * über {@link OrganisationRepository#findById(Object)} – daher wird hier kein Hibernate-Filter
 * gemockt/verifiziert (Abweichung von der Standard-Service-Test-Vorlage).
 */
@ExtendWith(MockitoExtension.class)
public class FeatureFlagServiceTest {

    private static final Long ORG_ID = 1L;
    private static final String FLAG_KEY = FeatureFlag.MESSWERTE_UPLOAD.name();

    @Mock
    private OrganisationRepository organisationRepository;

    @Mock
    private OrganizationContextService organizationContextService;

    @InjectMocks
    private FeatureFlagService featureFlagService;

    @Captor
    private ArgumentCaptor<Organisation> organisationCaptor;

    private Organisation orgWithoutOverride;
    private Organisation orgWithOverrideFalse;

    @BeforeEach
    void setUp() {
        orgWithoutOverride = new Organisation();
        orgWithoutOverride.setId(ORG_ID);
        orgWithoutOverride.setFeatureFlags(null);

        orgWithOverrideFalse = new Organisation();
        orgWithOverrideFalse.setId(ORG_ID);
        Map<String, Boolean> overrides = new HashMap<>();
        overrides.put(FLAG_KEY, false);
        orgWithOverrideFalse.setFeatureFlags(overrides);
    }

    // ==================== getEffectiveFlags ====================

    @Test
    void getEffectiveFlags_NoOverride_ReturnsDefault() {
        when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.of(orgWithoutOverride));

        Map<String, Boolean> result = featureFlagService.getEffectiveFlags(ORG_ID);

        assertEquals(FeatureFlag.values().length, result.size());
        assertTrue(result.get(FLAG_KEY));
    }

    @Test
    void getEffectiveFlags_WithOverride_ReturnsOverride() {
        when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.of(orgWithOverrideFalse));

        Map<String, Boolean> result = featureFlagService.getEffectiveFlags(ORG_ID);

        assertFalse(result.get(FLAG_KEY));
    }

    @Test
    void getEffectiveFlags_OrganisationNotFound_ReturnsDefaults() {
        when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.empty());

        Map<String, Boolean> result = featureFlagService.getEffectiveFlags(ORG_ID);

        assertTrue(result.get(FLAG_KEY));
    }

    @Test
    void getEffectiveFlags_RepositoryThrows_FallsBackToDefaults() {
        when(organisationRepository.findById(ORG_ID))
                .thenThrow(new RuntimeException("DB down / corrupt JSON"));

        Map<String, Boolean> result = featureFlagService.getEffectiveFlags(ORG_ID);

        assertTrue(result.get(FLAG_KEY));
    }

    // ==================== getAdminFlags ====================

    @Test
    void getAdminFlags_NoOverride_QuelleDefault() {
        when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.of(orgWithoutOverride));

        List<FeatureFlagDTO> result = featureFlagService.getAdminFlags(ORG_ID);

        assertEquals(FeatureFlag.values().length, result.size());
        FeatureFlagDTO dto = result.stream()
                .filter(d -> d.getKey().equals(FLAG_KEY))
                .findFirst().orElseThrow();
        assertEquals(FeatureFlagDTO.Quelle.DEFAULT, dto.getQuelle());
        assertTrue(dto.isDefaultWert());
        assertTrue(dto.isEffektiv());
        assertEquals("FEATURE_FLAG_MESSWERTE_UPLOAD", dto.getBeschreibungKey());
    }

    @Test
    void getAdminFlags_WithOverride_QuelleOverride() {
        when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.of(orgWithOverrideFalse));

        List<FeatureFlagDTO> result = featureFlagService.getAdminFlags(ORG_ID);

        FeatureFlagDTO dto = result.stream()
                .filter(d -> d.getKey().equals(FLAG_KEY))
                .findFirst().orElseThrow();
        assertEquals(FeatureFlagDTO.Quelle.OVERRIDE, dto.getQuelle());
        assertTrue(dto.isDefaultWert());
        assertFalse(dto.isEffektiv());
    }

    // ==================== isEnabled ====================

    @Test
    void isEnabled_NoOverride_ReturnsDefault() {
        when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.of(orgWithoutOverride));

        assertTrue(featureFlagService.isEnabled(ORG_ID, FeatureFlag.MESSWERTE_UPLOAD));
    }

    @Test
    void isEnabled_WithOverrideFalse_ReturnsFalse() {
        when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.of(orgWithOverrideFalse));

        assertFalse(featureFlagService.isEnabled(ORG_ID, FeatureFlag.MESSWERTE_UPLOAD));
    }

    // ==================== setOverride ====================

    @Test
    void setOverride_ValidKey_SavesOverride() {
        when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.of(orgWithoutOverride));

        featureFlagService.setOverride(ORG_ID, FLAG_KEY, false);

        verify(organisationRepository).save(organisationCaptor.capture());
        Map<String, Boolean> saved = organisationCaptor.getValue().getFeatureFlags();
        assertNotNull(saved);
        assertEquals(false, saved.get(FLAG_KEY));
    }

    @Test
    void setOverride_MergesWithExistingOverrides() {
        Organisation org = new Organisation();
        org.setId(ORG_ID);
        Map<String, Boolean> existing = new HashMap<>();
        existing.put("SOME_OTHER_FLAG", true);
        org.setFeatureFlags(existing);
        when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));

        featureFlagService.setOverride(ORG_ID, FLAG_KEY, false);

        verify(organisationRepository).save(organisationCaptor.capture());
        Map<String, Boolean> saved = organisationCaptor.getValue().getFeatureFlags();
        assertEquals(false, saved.get(FLAG_KEY));
        assertEquals(true, saved.get("SOME_OTHER_FLAG"));
    }

    @Test
    void setOverride_UnknownKey_ThrowsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> featureFlagService.setOverride(ORG_ID, "UNKNOWN_FLAG", true));

        assertTrue(ex.getMessage().contains("UNKNOWN_FLAG"));
        verify(organisationRepository, never()).findById(anyLong());
        verify(organisationRepository, never()).save(any());
    }

    @Test
    void setOverride_OrganisationNotFound_ThrowsIllegalStateException() {
        when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> featureFlagService.setOverride(ORG_ID, FLAG_KEY, true));

        verify(organisationRepository, never()).save(any());
    }

    // ==================== removeOverride ====================

    @Test
    void removeOverride_ExistingOverride_RemovesAndSaves() {
        when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.of(orgWithOverrideFalse));

        featureFlagService.removeOverride(ORG_ID, FLAG_KEY);

        verify(organisationRepository).save(organisationCaptor.capture());
        // Map wird auf null gesetzt, sobald keine Überschreibungen mehr vorhanden sind.
        assertNull(organisationCaptor.getValue().getFeatureFlags());
    }

    @Test
    void removeOverride_KeepsOtherOverrides() {
        Organisation org = new Organisation();
        org.setId(ORG_ID);
        Map<String, Boolean> overrides = new HashMap<>();
        overrides.put(FLAG_KEY, false);
        overrides.put("SOME_OTHER_FLAG", true);
        org.setFeatureFlags(overrides);
        when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));

        featureFlagService.removeOverride(ORG_ID, FLAG_KEY);

        verify(organisationRepository).save(organisationCaptor.capture());
        Map<String, Boolean> saved = organisationCaptor.getValue().getFeatureFlags();
        assertNotNull(saved);
        assertFalse(saved.containsKey(FLAG_KEY));
        assertEquals(true, saved.get("SOME_OTHER_FLAG"));
    }

    @Test
    void removeOverride_NoOverridePresent_DoesNotSave() {
        when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.of(orgWithoutOverride));

        featureFlagService.removeOverride(ORG_ID, FLAG_KEY);

        verify(organisationRepository, never()).save(any());
    }

    @Test
    void removeOverride_UnknownKey_ThrowsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> featureFlagService.removeOverride(ORG_ID, "UNKNOWN_FLAG"));

        assertTrue(ex.getMessage().contains("UNKNOWN_FLAG"));
        verify(organisationRepository, never()).findById(anyLong());
        verify(organisationRepository, never()).save(any());
    }

    @Test
    void removeOverride_OrganisationNotFound_ThrowsIllegalStateException() {
        when(organisationRepository.findById(ORG_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> featureFlagService.removeOverride(ORG_ID, FLAG_KEY));

        verify(organisationRepository, never()).save(any());
    }

    // ==================== getCurrentOrgId ====================

    @Test
    void getCurrentOrgId_DelegatesToContextService() {
        when(organizationContextService.getCurrentOrgId()).thenReturn(ORG_ID);

        Long result = featureFlagService.getCurrentOrgId();

        assertEquals(ORG_ID, result);
        verify(organizationContextService).getCurrentOrgId();
    }
}
