package ch.nacht.service;

import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.MieterEinheitRepository;

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


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EinheitServiceTest {

    @Mock
    private EinheitRepository einheitRepository;

    @Mock
    private MieterEinheitRepository mieterEinheitRepository;

    @Mock
    private OrganizationContextService organizationContextService;

    @Mock
    private HibernateFilterService hibernateFilterService;

    @InjectMocks
    private EinheitService einheitService;

    private Einheit consumerEinheit;
    private Einheit producerEinheit;
    private Long testOrgId;

    @BeforeEach
    void setUp() {
        testOrgId = 1L;

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
        Long orgId = 2L;
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
        Long originalOrgId = consumerEinheit.getOrgId();
        Einheit updatedData = new Einheit("Wohnung A Updated", EinheitTyp.PRODUCER);
        updatedData.setOrgId(99L); // different orgId should be overwritten

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
    void deleteEinheit_MitZugeordnetenMietern_ThrowsException() {
        // Ohne diese Pruefung koennte ueber die Einheiten-Verwaltung ein Mieter ohne Einheit
        // entstehen; der FK weist zwar ab, aber ohne verwertbare Meldung (Specs/Ladestationen.md).
        when(einheitRepository.existsById(1L)).thenReturn(true);
        when(mieterEinheitRepository.countByEinheitId(1L)).thenReturn(2L);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> einheitService.deleteEinheit(1L));

        assertTrue(ex.getMessage().contains("2"), "Die Meldung nennt die Anzahl betroffener Mieter");
        verify(einheitRepository, never()).deleteById(any());
    }

    @Test
    void deleteEinheit_Exists_ReturnsTrue() {
        when(einheitRepository.existsById(1L)).thenReturn(true);
        when(mieterEinheitRepository.countByEinheitId(1L)).thenReturn(0L);
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

    // ==================== RFID-Eindeutigkeit der Ladestationen ====================
    // Specs/Ladestationen.md: Der `messpunkt` einer LADESTATION-Einheit ist die RFID und muss
    // je Mandant eindeutig sein. BEZUG/RUECKLIEFERUNG teilen sich bewusst einen Messpunkt,
    // deshalb greift die Pruefung nur fuer LADESTATION.

    /** Ladestation mit RFID; ohne ID = neu, mit ID = Update. */
    private Einheit ladestation(String name, String rfid) {
        Einheit einheit = new Einheit(name, EinheitTyp.LADESTATION);
        einheit.setMesspunkt(rfid);
        return einheit;
    }

    @Test
    void createEinheit_LadestationMitFreierRfid_SavesSuccessfully() {
        Einheit neu = ladestation("Ladestation 1", "RFID-001");
        when(einheitRepository.existsLadestationWithMesspunkt("RFID-001", -1L)).thenReturn(false);
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(einheitRepository.save(neu)).thenReturn(neu);

        Einheit result = einheitService.createEinheit(neu);

        assertNotNull(result);
        assertEquals(testOrgId, neu.getOrgId());
        verify(hibernateFilterService).enableOrgFilter();
        verify(einheitRepository).existsLadestationWithMesspunkt("RFID-001", -1L);
        verify(einheitRepository).save(neu);
    }

    @Test
    void createEinheit_LadestationMitVergebenerRfid_ThrowsException() {
        Einheit neu = ladestation("Ladestation 2", "RFID-001");
        when(einheitRepository.existsLadestationWithMesspunkt("RFID-001", -1L)).thenReturn(true);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> einheitService.createEinheit(neu)
        );

        assertEquals("EINHEIT_MESSPUNKT_EXISTIERT", exception.getMessage());
        verify(einheitRepository, never()).save(any());
        verify(organizationContextService, never()).getCurrentOrgId();
    }

    @Test
    void createEinheit_LadestationOhneMesspunkt_SkipsRfidCheck() {
        Einheit neu = new Einheit("Ladestation ohne RFID", EinheitTyp.LADESTATION);
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(einheitRepository.save(neu)).thenReturn(neu);

        einheitService.createEinheit(neu);

        // Leere Eingaben duerfen nicht miteinander kollidieren
        verify(einheitRepository, never()).existsLadestationWithMesspunkt(any(), anyLong());
    }

    @Test
    void createEinheit_LadestationMitLeeremMesspunkt_NormalisiertAufNull() {
        Einheit neu = ladestation("Ladestation blank", "   ");
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(einheitRepository.save(neu)).thenReturn(neu);

        einheitService.createEinheit(neu);

        assertNull(neu.getMesspunkt());
        verify(einheitRepository, never()).existsLadestationWithMesspunkt(any(), anyLong());
    }

    @Test
    void createEinheit_ConsumerMitVergebenemMesspunkt_SavesSuccessfully() {
        // Die Eindeutigkeit gilt nur fuer LADESTATION - andere Typen bleiben unberuehrt
        Einheit neu = new Einheit("Wohnung B", EinheitTyp.CONSUMER);
        neu.setMesspunkt("MP-001");
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(einheitRepository.save(neu)).thenReturn(neu);

        einheitService.createEinheit(neu);

        verify(einheitRepository, never()).existsLadestationWithMesspunkt(any(), anyLong());
    }

    @Test
    void createEinheit_RuecklieferungTeiltMesspunktMitBezug_SavesSuccessfully() {
        // Bilanz-Typen teilen sich denselben Messpunkt (Register-Projektion beim MQTT-Ingest)
        Einheit neu = new Einheit("Ruecklieferung", EinheitTyp.RUECKLIEFERUNG);
        neu.setMesspunkt("BILANZ-1");
        when(einheitRepository.existsByTyp(EinheitTyp.RUECKLIEFERUNG)).thenReturn(false);
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(einheitRepository.save(neu)).thenReturn(neu);

        einheitService.createEinheit(neu);

        verify(einheitRepository, never()).existsLadestationWithMesspunkt(any(), anyLong());
        verify(einheitRepository).save(neu);
    }

    @Test
    void updateEinheit_LadestationBehaeltEigeneRfid_SavesSuccessfully() {
        Einheit bestehend = ladestation("Ladestation 1", "RFID-001");
        bestehend.setId(5L);
        bestehend.setOrgId(testOrgId);
        Einheit geaendert = ladestation("Ladestation 1 neu", "RFID-001");

        when(einheitRepository.findById(5L)).thenReturn(Optional.of(bestehend));
        // Eigene ID ausgeschlossen -> die eigene RFID kollidiert nicht mit sich selbst
        when(einheitRepository.existsLadestationWithMesspunkt("RFID-001", 5L)).thenReturn(false);
        when(einheitRepository.save(geaendert)).thenReturn(geaendert);

        Optional<Einheit> result = einheitService.updateEinheit(5L, geaendert);

        assertTrue(result.isPresent());
        assertEquals(5L, geaendert.getId());
        assertEquals(testOrgId, geaendert.getOrgId());
        verify(einheitRepository).existsLadestationWithMesspunkt("RFID-001", 5L);
    }

    @Test
    void updateEinheit_LadestationMitFremderRfid_ThrowsException() {
        Einheit bestehend = ladestation("Ladestation 1", "RFID-001");
        bestehend.setId(5L);
        bestehend.setOrgId(testOrgId);
        Einheit geaendert = ladestation("Ladestation 1", "RFID-002");

        when(einheitRepository.findById(5L)).thenReturn(Optional.of(bestehend));
        when(einheitRepository.existsLadestationWithMesspunkt("RFID-002", 5L)).thenReturn(true);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> einheitService.updateEinheit(5L, geaendert)
        );

        assertEquals("EINHEIT_MESSPUNKT_EXISTIERT", exception.getMessage());
        verify(einheitRepository, never()).save(any());
    }

    @Test
    void updateEinheit_LadestationMitLeeremMesspunkt_NormalisiertAufNull() {
        Einheit bestehend = ladestation("Ladestation 1", "RFID-001");
        bestehend.setId(5L);
        bestehend.setOrgId(testOrgId);
        Einheit geaendert = ladestation("Ladestation 1", "");

        when(einheitRepository.findById(5L)).thenReturn(Optional.of(bestehend));
        when(einheitRepository.save(geaendert)).thenReturn(geaendert);

        einheitService.updateEinheit(5L, geaendert);

        assertNull(geaendert.getMesspunkt());
        verify(einheitRepository, never()).existsLadestationWithMesspunkt(any(), anyLong());
    }

    @Test
    void updateEinheit_TypwechselZuLadestationMitVergebenerRfid_ThrowsException() {
        Einheit geaendert = ladestation("Wohnung A", "RFID-001");

        when(einheitRepository.findById(1L)).thenReturn(Optional.of(consumerEinheit));
        when(einheitRepository.existsLadestationWithMesspunkt("RFID-001", 1L)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> einheitService.updateEinheit(1L, geaendert));

        verify(einheitRepository, never()).save(any());
    }
}
