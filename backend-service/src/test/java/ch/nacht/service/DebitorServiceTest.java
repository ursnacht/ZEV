package ch.nacht.service;

import ch.nacht.dto.DebitorDTO;
import ch.nacht.entity.Debitor;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.Mieter;
import ch.nacht.repository.DebitorRepository;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.MieterRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DebitorServiceTest {

    @Mock
    private DebitorRepository debitorRepository;

    @Mock
    private MieterRepository mieterRepository;

    @Mock
    private EinheitRepository einheitRepository;

    @Mock
    private OrganizationContextService organizationContextService;

    @Mock
    private HibernateFilterService hibernateFilterService;

    @InjectMocks
    private DebitorService debitorService;

    private static final LocalDate VON = LocalDate.of(2024, 1, 1);
    private static final LocalDate BIS = LocalDate.of(2024, 3, 31);
    private static final LocalDate ZAHLDATUM = LocalDate.of(2024, 4, 15);
    private static final Long ORG_ID = 1L;

    private Debitor testDebitor1;
    private Debitor testDebitor2;
    private Mieter testMieter;
    private Einheit testEinheit;

    @BeforeEach
    void setUp() {
        testMieter = new Mieter();
        testMieter.setId(10L);
        testMieter.setName("Max Muster");
        testMieter.setEinheitId(5L);

        testEinheit = new Einheit();
        testEinheit.setId(5L);
        testEinheit.setName("EG links");

        testDebitor1 = new Debitor();
        testDebitor1.setId(1L);
        testDebitor1.setOrgId(ORG_ID);
        testDebitor1.setMieterId(10L);
        testDebitor1.setBetrag(new BigDecimal("125.50"));
        testDebitor1.setDatumVon(VON);
        testDebitor1.setDatumBis(BIS);

        testDebitor2 = new Debitor();
        testDebitor2.setId(2L);
        testDebitor2.setOrgId(ORG_ID);
        testDebitor2.setMieterId(10L);
        testDebitor2.setBetrag(new BigDecimal("98.00"));
        testDebitor2.setDatumVon(LocalDate.of(2024, 4, 1));
        testDebitor2.setDatumBis(LocalDate.of(2024, 6, 30));
        testDebitor2.setZahldatum(ZAHLDATUM);
    }

    // ==================== getDebitoren ====================

    @Test
    void getDebitoren_ReturnsDTOList() {
        when(debitorRepository.findByDatumVonBetween(VON, BIS))
                .thenReturn(List.of(testDebitor1));
        when(mieterRepository.findById(10L)).thenReturn(Optional.of(testMieter));
        when(einheitRepository.findById(5L)).thenReturn(Optional.of(testEinheit));

        List<DebitorDTO> result = debitorService.getDebitoren(VON, BIS);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals("Max Muster", result.get(0).getMieterName());
        assertEquals("EG links", result.get(0).getEinheitName());
        verify(hibernateFilterService).enableOrgFilter();
    }

    @Test
    void getDebitoren_EmptyRange_ReturnsEmptyList() {
        when(debitorRepository.findByDatumVonBetween(any(), any())).thenReturn(List.of());

        List<DebitorDTO> result = debitorService.getDebitoren(VON, BIS);

        assertTrue(result.isEmpty());
        verify(hibernateFilterService).enableOrgFilter();
    }

    @Test
    void getDebitoren_MieterNotFound_EinheitNameIsNull() {
        when(debitorRepository.findByDatumVonBetween(VON, BIS)).thenReturn(List.of(testDebitor1));
        when(mieterRepository.findById(10L)).thenReturn(Optional.empty());

        List<DebitorDTO> result = debitorService.getDebitoren(VON, BIS);

        assertEquals(1, result.size());
        assertNull(result.get(0).getMieterName());
        assertNull(result.get(0).getEinheitName());
    }

    // ==================== getDebitorById ====================

    @Test
    void getDebitorById_Found_ReturnsDTO() {
        when(debitorRepository.findById(1L)).thenReturn(Optional.of(testDebitor1));
        when(mieterRepository.findById(10L)).thenReturn(Optional.of(testMieter));
        when(einheitRepository.findById(5L)).thenReturn(Optional.of(testEinheit));

        Optional<DebitorDTO> result = debitorService.getDebitorById(1L);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
        assertEquals(new BigDecimal("125.50"), result.get().getBetrag());
        verify(hibernateFilterService).enableOrgFilter();
    }

    @Test
    void getDebitorById_NotFound_ReturnsEmpty() {
        when(debitorRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<DebitorDTO> result = debitorService.getDebitorById(99L);

        assertTrue(result.isEmpty());
        verify(hibernateFilterService).enableOrgFilter();
    }

    // ==================== create ====================

    @Test
    void create_ValidDTO_SavesAndReturnsDTO() {
        DebitorDTO dto = buildValidDTO();
        when(organizationContextService.getCurrentOrgId()).thenReturn(ORG_ID);
        when(debitorRepository.save(any())).thenReturn(testDebitor1);
        when(mieterRepository.findById(10L)).thenReturn(Optional.of(testMieter));
        when(einheitRepository.findById(5L)).thenReturn(Optional.of(testEinheit));

        DebitorDTO result = debitorService.create(dto);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Max Muster", result.getMieterName());
        verify(organizationContextService).getCurrentOrgId();
        verify(debitorRepository).save(any());
        verify(hibernateFilterService).enableOrgFilter();
    }

    @Test
    void create_WithZahldatum_SavesSuccessfully() {
        DebitorDTO dto = buildValidDTO();
        dto.setZahldatum(BIS.plusDays(1)); // zahldatum > datumBis → valid
        when(organizationContextService.getCurrentOrgId()).thenReturn(ORG_ID);
        when(debitorRepository.save(any())).thenReturn(testDebitor1);
        when(mieterRepository.findById(any())).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> debitorService.create(dto));
    }

    @Test
    void create_ZahldatumEqualsDatumBis_SavesSuccessfully() {
        DebitorDTO dto = buildValidDTO();
        dto.setZahldatum(BIS); // zahldatum == datumBis → valid (not before)
        when(organizationContextService.getCurrentOrgId()).thenReturn(ORG_ID);
        when(debitorRepository.save(any())).thenReturn(testDebitor1);
        when(mieterRepository.findById(any())).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> debitorService.create(dto));
    }

    @Test
    void create_NullMieterId_ThrowsIllegalArgumentException() {
        DebitorDTO dto = buildValidDTO();
        dto.setMieterId(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> debitorService.create(dto));
        assertEquals("Mieter ist Pflicht", ex.getMessage());
        verifyNoInteractions(debitorRepository);
    }

    @Test
    void create_NullBetrag_ThrowsIllegalArgumentException() {
        DebitorDTO dto = buildValidDTO();
        dto.setBetrag(null);

        assertThrows(IllegalArgumentException.class, () -> debitorService.create(dto));
        verifyNoInteractions(debitorRepository);
    }

    @Test
    void create_ZeroBetrag_ThrowsIllegalArgumentException() {
        DebitorDTO dto = buildValidDTO();
        dto.setBetrag(BigDecimal.ZERO);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> debitorService.create(dto));
        assertEquals("Betrag muss grösser als 0 sein", ex.getMessage());
    }

    @Test
    void create_NegativeBetrag_ThrowsIllegalArgumentException() {
        DebitorDTO dto = buildValidDTO();
        dto.setBetrag(new BigDecimal("-1.00"));

        assertThrows(IllegalArgumentException.class, () -> debitorService.create(dto));
    }

    @Test
    void create_DatumVonAfterDatumBis_ThrowsIllegalArgumentException() {
        DebitorDTO dto = buildValidDTO();
        dto.setDatumVon(BIS.plusDays(1));
        dto.setDatumBis(VON);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> debitorService.create(dto));
        assertEquals("Datum von muss vor oder gleich Datum bis liegen", ex.getMessage());
    }

    @Test
    void create_ZahldatumBeforeDatumBis_ThrowsIllegalArgumentException() {
        DebitorDTO dto = buildValidDTO();
        dto.setZahldatum(BIS.minusDays(1)); // zahldatum < datumBis → invalid

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> debitorService.create(dto));
        assertEquals("Zahldatum darf nicht vor Datum bis liegen", ex.getMessage());
    }

    @Test
    void create_NullDatumVon_ThrowsIllegalArgumentException() {
        DebitorDTO dto = buildValidDTO();
        dto.setDatumVon(null);

        assertThrows(IllegalArgumentException.class, () -> debitorService.create(dto));
    }

    // ==================== update ====================

    @Test
    void update_Exists_UpdatesAndReturnsDTO() {
        DebitorDTO dto = buildValidDTO();
        when(debitorRepository.findById(1L)).thenReturn(Optional.of(testDebitor1));
        when(debitorRepository.save(testDebitor1)).thenReturn(testDebitor1);
        when(mieterRepository.findById(10L)).thenReturn(Optional.of(testMieter));
        when(einheitRepository.findById(5L)).thenReturn(Optional.of(testEinheit));

        DebitorDTO result = debitorService.update(1L, dto);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        verify(debitorRepository).save(testDebitor1);
        verify(hibernateFilterService).enableOrgFilter();
    }

    @Test
    void update_NotFound_ThrowsNoSuchElementException() {
        when(debitorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class,
                () -> debitorService.update(99L, buildValidDTO()));
    }

    @Test
    void update_InvalidBetrag_ThrowsIllegalArgumentException() {
        DebitorDTO dto = buildValidDTO();
        dto.setBetrag(BigDecimal.ZERO);
        when(debitorRepository.findById(1L)).thenReturn(Optional.of(testDebitor1));

        assertThrows(IllegalArgumentException.class, () -> debitorService.update(1L, dto));
        verify(debitorRepository, never()).save(any());
    }

    // ==================== delete ====================

    @Test
    void delete_Exists_ReturnsTrue() {
        when(debitorRepository.existsById(1L)).thenReturn(true);

        boolean result = debitorService.delete(1L);

        assertTrue(result);
        verify(debitorRepository).deleteById(1L);
        verify(hibernateFilterService).enableOrgFilter();
    }

    @Test
    void delete_NotFound_ReturnsFalse() {
        when(debitorRepository.existsById(99L)).thenReturn(false);

        boolean result = debitorService.delete(99L);

        assertFalse(result);
        verify(debitorRepository, never()).deleteById(any());
    }

    // ==================== upsertFromRechnung ====================

    @Test
    void upsertFromRechnung_CallsRepositoryWithOrgId() {
        when(organizationContextService.getCurrentOrgId()).thenReturn(ORG_ID);

        debitorService.upsertFromRechnung(10L, new BigDecimal("125.50"), VON, BIS);

        verify(organizationContextService).getCurrentOrgId();
        verify(debitorRepository).upsert(10L, new BigDecimal("125.50"), VON, BIS, ORG_ID);
    }

    // ==================== Helpers ====================

    private DebitorDTO buildValidDTO() {
        DebitorDTO dto = new DebitorDTO();
        dto.setMieterId(10L);
        dto.setBetrag(new BigDecimal("125.50"));
        dto.setDatumVon(VON);
        dto.setDatumBis(BIS);
        return dto;
    }
}
