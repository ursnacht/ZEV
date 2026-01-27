package ch.nacht.service;

import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.repository.EinheitRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EinheitServiceTest {

    @Mock
    private EinheitRepository einheitRepository;

    @Mock
    private OrganizationContextService organizationContextService;

    @Mock
    private HibernateFilterService hibernateFilterService;

    @InjectMocks
    private EinheitService einheitService;

    private Einheit consumerEinheit;
    private Einheit producerEinheit;
    private UUID testOrgId;

    @BeforeEach
    void setUp() {
        testOrgId = UUID.randomUUID();

        consumerEinheit = new Einheit("Wohnung A", EinheitTyp.CONSUMER);
        consumerEinheit.setId(1L);
        consumerEinheit.setOrgId(testOrgId);
        consumerEinheit.setMesspunkt("MP-001");

        producerEinheit = new Einheit("Solaranlage", EinheitTyp.PRODUCER);
        producerEinheit.setId(2L);
        producerEinheit.setOrgId(testOrgId);
        producerEinheit.setMesspunkt("MP-002");
    }

    // ==================== getAllEinheiten Tests ====================

    @Test
    void getAllEinheiten_ReturnsSortedList() {
        when(einheitRepository.findAllByOrderByNameAsc())
            .thenReturn(Arrays.asList(producerEinheit, consumerEinheit));

        List<Einheit> result = einheitService.getAllEinheiten();

        assertNotNull(result);
        assertEquals(2, result.size());
        verify(hibernateFilterService).enableOrgFilter();
        verify(einheitRepository).findAllByOrderByNameAsc();
    }

    @Test
    void getAllEinheiten_EmptyList_ReturnsEmpty() {
        when(einheitRepository.findAllByOrderByNameAsc())
            .thenReturn(Collections.emptyList());

        List<Einheit> result = einheitService.getAllEinheiten();

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(hibernateFilterService).enableOrgFilter();
    }

    // ==================== getEinheitById Tests ====================

    @Test
    void getEinheitById_Found_ReturnsEinheit() {
        when(einheitRepository.findById(1L)).thenReturn(Optional.of(consumerEinheit));

        Optional<Einheit> result = einheitService.getEinheitById(1L);

        assertTrue(result.isPresent());
        assertEquals("Wohnung A", result.get().getName());
        assertEquals(EinheitTyp.CONSUMER, result.get().getTyp());
        verify(hibernateFilterService).enableOrgFilter();
    }

    @Test
    void getEinheitById_NotFound_ReturnsEmpty() {
        when(einheitRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<Einheit> result = einheitService.getEinheitById(999L);

        assertFalse(result.isPresent());
        verify(hibernateFilterService).enableOrgFilter();
    }

    // ==================== createEinheit Tests ====================

    @Test
    void createEinheit_ValidEinheit_SavesSuccessfully() {
        Einheit newEinheit = new Einheit("Wohnung B", EinheitTyp.CONSUMER);
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(einheitRepository.save(newEinheit)).thenReturn(newEinheit);

        Einheit result = einheitService.createEinheit(newEinheit);

        assertNotNull(result);
        assertEquals(testOrgId, newEinheit.getOrgId());
        verify(hibernateFilterService).enableOrgFilter();
        verify(organizationContextService).getCurrentOrgId();
        verify(einheitRepository).save(newEinheit);
    }

    @Test
    void createEinheit_SetsOrgIdFromContext() {
        UUID orgId = UUID.randomUUID();
        Einheit newEinheit = new Einheit("Wohnung C", EinheitTyp.CONSUMER);
        when(organizationContextService.getCurrentOrgId()).thenReturn(orgId);
        when(einheitRepository.save(newEinheit)).thenReturn(newEinheit);

        einheitService.createEinheit(newEinheit);

        assertEquals(orgId, newEinheit.getOrgId());
    }

    // ==================== updateEinheit Tests ====================

    @Test
    void updateEinheit_Found_UpdatesSuccessfully() {
        Einheit updatedData = new Einheit("Wohnung A Updated", EinheitTyp.CONSUMER);
        when(einheitRepository.findById(1L)).thenReturn(Optional.of(consumerEinheit));
        when(einheitRepository.save(updatedData)).thenReturn(updatedData);

        Optional<Einheit> result = einheitService.updateEinheit(1L, updatedData);

        assertTrue(result.isPresent());
        assertEquals(1L, updatedData.getId());
        assertEquals(testOrgId, updatedData.getOrgId());
        verify(hibernateFilterService).enableOrgFilter();
        verify(einheitRepository).save(updatedData);
    }

    @Test
    void updateEinheit_NotFound_ReturnsEmpty() {
        Einheit updatedData = new Einheit("Wohnung X", EinheitTyp.CONSUMER);
        when(einheitRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<Einheit> result = einheitService.updateEinheit(999L, updatedData);

        assertFalse(result.isPresent());
        verify(hibernateFilterService).enableOrgFilter();
        verify(einheitRepository, never()).save(any());
    }

    @Test
    void updateEinheit_PreservesOrgId() {
        UUID originalOrgId = consumerEinheit.getOrgId();
        Einheit updatedData = new Einheit("Wohnung A Updated", EinheitTyp.PRODUCER);
        updatedData.setOrgId(UUID.randomUUID()); // different orgId should be overwritten

        when(einheitRepository.findById(1L)).thenReturn(Optional.of(consumerEinheit));
        when(einheitRepository.save(updatedData)).thenReturn(updatedData);

        einheitService.updateEinheit(1L, updatedData);

        assertEquals(originalOrgId, updatedData.getOrgId());
    }

    @Test
    void updateEinheit_SetsIdFromPath() {
        Einheit updatedData = new Einheit("Wohnung A Updated", EinheitTyp.CONSUMER);
        updatedData.setId(999L); // should be overwritten by path id

        when(einheitRepository.findById(1L)).thenReturn(Optional.of(consumerEinheit));
        when(einheitRepository.save(updatedData)).thenReturn(updatedData);

        einheitService.updateEinheit(1L, updatedData);

        assertEquals(1L, updatedData.getId());
    }

    // ==================== deleteEinheit Tests ====================

    @Test
    void deleteEinheit_Exists_ReturnsTrue() {
        when(einheitRepository.existsById(1L)).thenReturn(true);
        doNothing().when(einheitRepository).deleteById(1L);

        boolean result = einheitService.deleteEinheit(1L);

        assertTrue(result);
        verify(hibernateFilterService).enableOrgFilter();
        verify(einheitRepository).deleteById(1L);
    }

    @Test
    void deleteEinheit_NotExists_ReturnsFalse() {
        when(einheitRepository.existsById(999L)).thenReturn(false);

        boolean result = einheitService.deleteEinheit(999L);

        assertFalse(result);
        verify(hibernateFilterService).enableOrgFilter();
        verify(einheitRepository, never()).deleteById(anyLong());
    }
}
