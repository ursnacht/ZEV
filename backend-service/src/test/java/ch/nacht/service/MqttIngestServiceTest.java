package ch.nacht.service;

import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.ZaehlerRohdaten;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.ZaehlerRohdatenRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit-Tests für den MQTT-Ingest (FR-4): Topic/Payload parsen, validieren, Einheit auflösen,
 * Rohdaten-Upsert. Fehler werden geloggt und verworfen (keine Exception nach aussen).
 *
 * <p>Bewusst KEIN {@code OrganizationContextService}/{@code HibernateFilterService} (kein
 * Request-Scope) – die {@code org_id} stammt aus dem Topic. Der {@link ObjectMapper} ist real
 * (JSON-Parsing ist Teil des zu testenden Verhaltens).
 */
@ExtendWith(MockitoExtension.class)
public class MqttIngestServiceTest {

    private static final long ORG_ID = 100L;
    private static final long EINHEIT_ID = 5L;
    private static final String MESSPUNKT = "MP-001";
    private static final String TOPIC = "zev/100/MP-001/messwert";

    @Mock
    private EinheitRepository einheitRepository;

    @Mock
    private ZaehlerRohdatenRepository rohdatenRepository;

    @Mock
    private MqttMetrics metrics;

    private ObjectMapper objectMapper;
    private MqttIngestService service;

    private Einheit einheit;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new MqttIngestService(einheitRepository, rohdatenRepository, objectMapper, metrics);

        einheit = new Einheit("Wohnung 1", EinheitTyp.CONSUMER);
        einheit.setId(EINHEIT_ID);
        einheit.setOrgId(ORG_ID);
        einheit.setMesspunkt(MESSPUNKT);
    }

    private String payload(String timestamp, String bezug, String einspeisung) {
        StringBuilder sb = new StringBuilder("{");
        if (timestamp != null) {
            sb.append("\"timestamp\":\"").append(timestamp).append("\",");
        }
        if (bezug != null) {
            sb.append("\"zaehlerstandBezug\":").append(bezug).append(",");
        }
        if (einspeisung != null) {
            sb.append("\"zaehlerstandEinspeisung\":").append(einspeisung).append(",");
        }
        if (sb.charAt(sb.length() - 1) == ',') {
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append("}");
        return sb.toString();
    }

    // --- Gültige Nachricht ---------------------------------------------------

    @Test
    void handle_ValidMessage_SavesRohdaten() {
        // Arrange
        when(einheitRepository.findAllByOrgIdAndMesspunkt(ORG_ID, MESSPUNKT)).thenReturn(List.of(einheit));
        when(rohdatenRepository.findByEinheitIdAndZeit(eq(EINHEIT_ID), any())).thenReturn(Optional.empty());
        when(rohdatenRepository.save(any(ZaehlerRohdaten.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act – lokale Zeit mit Offset (Wire-Contract); Wanduhrzeit wird verbatim gespeichert
        service.handle(TOPIC, payload("2026-01-01T10:07:00+01:00", "123.4500", "10.0000"));

        // Assert
        ArgumentCaptor<ZaehlerRohdaten> captor = ArgumentCaptor.forClass(ZaehlerRohdaten.class);
        verify(rohdatenRepository).save(captor.capture());
        ZaehlerRohdaten saved = captor.getValue();
        assertEquals(ORG_ID, saved.getOrgId());
        assertEquals(EINHEIT_ID, saved.getEinheitId());
        // Verbatim: 10:07 lokal (unabhängig von der Test-JVM-Zeitzone)
        assertEquals(LocalDateTime.of(2026, 1, 1, 10, 7), saved.getZeit());
        assertEquals(0, new BigDecimal("123.4500").compareTo(saved.getZaehlerstandBezug()));
        assertEquals(0, new BigDecimal("10.0000").compareTo(saved.getZaehlerstandEinspeisung()));
        assertFalse(saved.isVerarbeitet());
        assertNotNull(saved.getEmpfangenAm());

        verify(metrics).recordReceived();
        verify(metrics).recordProcessed();
        verify(metrics, never()).recordFailed();
    }

    @Test
    void handle_DuplicateEinheitAndZeit_UpdatesInsteadOfInsert() {
        // Arrange – bestehender Rohdatensatz zu (Einheit, Zeit)
        ZaehlerRohdaten existing = new ZaehlerRohdaten(ORG_ID, EINHEIT_ID,
                LocalDateTime.of(2026, 1, 1, 10, 7),
                new BigDecimal("100.0000"), new BigDecimal("5.0000"));
        when(einheitRepository.findAllByOrgIdAndMesspunkt(ORG_ID, MESSPUNKT)).thenReturn(List.of(einheit));
        when(rohdatenRepository.findByEinheitIdAndZeit(eq(EINHEIT_ID), any())).thenReturn(Optional.of(existing));
        when(rohdatenRepository.save(any(ZaehlerRohdaten.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        service.handle(TOPIC, payload("2026-01-01T10:07:00Z", "123.4500", "10.0000"));

        // Assert – dieselbe Instanz wird aktualisiert, keine neue erstellt
        ArgumentCaptor<ZaehlerRohdaten> captor = ArgumentCaptor.forClass(ZaehlerRohdaten.class);
        verify(rohdatenRepository).save(captor.capture());
        assertSame(existing, captor.getValue());
        assertEquals(0, new BigDecimal("123.4500").compareTo(captor.getValue().getZaehlerstandBezug()));
        assertEquals(0, new BigDecimal("10.0000").compareTo(captor.getValue().getZaehlerstandEinspeisung()));
        verify(metrics).recordProcessed();
        verify(metrics, never()).recordFailed();
    }

    // --- Bilanzmesspunkt: Splitting & Register-Projektion (FR-2.3/2.4) --------

    @Test
    void handle_GeteilterBilanzMesspunkt_SplittetAufBeideEinheiten() {
        Einheit bezugEinheit = new Einheit("Bezug", EinheitTyp.BEZUG);
        bezugEinheit.setId(20L);
        bezugEinheit.setOrgId(ORG_ID);
        bezugEinheit.setMesspunkt(MESSPUNKT);
        Einheit ruecklieferungEinheit = new Einheit("Rücklieferung", EinheitTyp.RUECKLIEFERUNG);
        ruecklieferungEinheit.setId(21L);
        ruecklieferungEinheit.setOrgId(ORG_ID);
        ruecklieferungEinheit.setMesspunkt(MESSPUNKT);

        when(einheitRepository.findAllByOrgIdAndMesspunkt(ORG_ID, MESSPUNKT))
                .thenReturn(List.of(bezugEinheit, ruecklieferungEinheit));
        when(rohdatenRepository.findByEinheitIdAndZeit(anyLong(), any())).thenReturn(Optional.empty());
        when(rohdatenRepository.save(any(ZaehlerRohdaten.class))).thenAnswer(inv -> inv.getArgument(0));

        service.handle(TOPIC, payload("2026-01-01T10:07:00+01:00", "123.4500", "10.0000"));

        ArgumentCaptor<ZaehlerRohdaten> captor = ArgumentCaptor.forClass(ZaehlerRohdaten.class);
        verify(rohdatenRepository, times(2)).save(captor.capture());

        ZaehlerRohdaten bezugRow = captor.getAllValues().get(0);
        assertEquals(20L, bezugRow.getEinheitId());
        assertEquals(0, new BigDecimal("123.4500").compareTo(bezugRow.getZaehlerstandBezug()));
        assertEquals(0, BigDecimal.ZERO.compareTo(bezugRow.getZaehlerstandEinspeisung()));

        ZaehlerRohdaten ruecklieferungRow = captor.getAllValues().get(1);
        assertEquals(21L, ruecklieferungRow.getEinheitId());
        assertEquals(0, BigDecimal.ZERO.compareTo(ruecklieferungRow.getZaehlerstandBezug()));
        assertEquals(0, new BigDecimal("10.0000").compareTo(ruecklieferungRow.getZaehlerstandEinspeisung()));

        // Eine Meldung = einmal verarbeitet
        verify(metrics).recordProcessed();
        verify(metrics, never()).recordFailed();
    }

    @Test
    void handle_EinzelneBezugEinheit_ProjiziertNurBezugRegister() {
        Einheit bezugEinheit = new Einheit("Bezug", EinheitTyp.BEZUG);
        bezugEinheit.setId(20L);
        bezugEinheit.setOrgId(ORG_ID);
        bezugEinheit.setMesspunkt(MESSPUNKT);

        when(einheitRepository.findAllByOrgIdAndMesspunkt(ORG_ID, MESSPUNKT)).thenReturn(List.of(bezugEinheit));
        when(rohdatenRepository.findByEinheitIdAndZeit(eq(20L), any())).thenReturn(Optional.empty());
        when(rohdatenRepository.save(any(ZaehlerRohdaten.class))).thenAnswer(inv -> inv.getArgument(0));

        service.handle(TOPIC, payload("2026-01-01T10:07:00Z", "123.4500", "10.0000"));

        ArgumentCaptor<ZaehlerRohdaten> captor = ArgumentCaptor.forClass(ZaehlerRohdaten.class);
        verify(rohdatenRepository).save(captor.capture());
        // Projektion gilt auch ohne geteilten Messpunkt: Einspeisung wird ignoriert
        assertEquals(0, new BigDecimal("123.4500").compareTo(captor.getValue().getZaehlerstandBezug()));
        assertEquals(0, BigDecimal.ZERO.compareTo(captor.getValue().getZaehlerstandEinspeisung()));
    }

    // --- Ungültiges Topic ----------------------------------------------------

    @Test
    void handle_NullTopic_Discarded() {
        service.handle(null, payload("2026-01-01T10:07:00Z", "1.0", "0.0"));

        verify(metrics).recordReceived();
        verify(metrics).recordFailed();
        verify(rohdatenRepository, never()).save(any());
        verifyNoInteractions(einheitRepository);
    }

    @Test
    void handle_TopicWrongSegmentCount_Discarded() {
        service.handle("zev/100/MP-001", payload("2026-01-01T10:07:00Z", "1.0", "0.0"));

        verify(metrics).recordFailed();
        verify(rohdatenRepository, never()).save(any());
        verifyNoInteractions(einheitRepository);
    }

    @Test
    void handle_TopicWrongPrefix_Discarded() {
        service.handle("foo/100/MP-001/messwert", payload("2026-01-01T10:07:00Z", "1.0", "0.0"));

        verify(metrics).recordFailed();
        verify(rohdatenRepository, never()).save(any());
    }

    @Test
    void handle_TopicWrongSuffix_Discarded() {
        service.handle("zev/100/MP-001/other", payload("2026-01-01T10:07:00Z", "1.0", "0.0"));

        verify(metrics).recordFailed();
        verify(rohdatenRepository, never()).save(any());
    }

    @Test
    void handle_NonNumericOrgId_Discarded() {
        service.handle("zev/abc/MP-001/messwert", payload("2026-01-01T10:07:00Z", "1.0", "0.0"));

        verify(metrics).recordFailed();
        verify(rohdatenRepository, never()).save(any());
        verifyNoInteractions(einheitRepository);
    }

    // --- Ungültiger Payload --------------------------------------------------

    @Test
    void handle_InvalidJson_Discarded() {
        service.handle(TOPIC, "kein-json");

        verify(metrics).recordReceived();
        verify(metrics).recordFailed();
        verify(rohdatenRepository, never()).save(any());
    }

    @Test
    void handle_EmptyPayload_Discarded() {
        service.handle(TOPIC, "");

        verify(metrics).recordFailed();
        verify(rohdatenRepository, never()).save(any());
    }

    @Test
    void handle_MissingTimestamp_Discarded() {
        service.handle(TOPIC, payload(null, "1.0", "0.0"));

        verify(metrics).recordFailed();
        verify(rohdatenRepository, never()).save(any());
    }

    @Test
    void handle_MissingBezug_Discarded() {
        service.handle(TOPIC, payload("2026-01-01T10:07:00Z", null, "0.0"));

        verify(metrics).recordFailed();
        verify(rohdatenRepository, never()).save(any());
    }

    @Test
    void handle_MissingEinspeisung_Discarded() {
        service.handle(TOPIC, payload("2026-01-01T10:07:00Z", "1.0", null));

        verify(metrics).recordFailed();
        verify(rohdatenRepository, never()).save(any());
    }

    @Test
    void handle_NegativeBezug_Discarded() {
        service.handle(TOPIC, payload("2026-01-01T10:07:00Z", "-1.0", "0.0"));

        verify(metrics).recordFailed();
        verify(rohdatenRepository, never()).save(any());
    }

    @Test
    void handle_NegativeEinspeisung_Discarded() {
        service.handle(TOPIC, payload("2026-01-01T10:07:00Z", "1.0", "-0.5"));

        verify(metrics).recordFailed();
        verify(rohdatenRepository, never()).save(any());
    }

    // --- Fachliche Fehler ----------------------------------------------------

    @Test
    void handle_UnknownMesspunkt_Discarded() {
        when(einheitRepository.findAllByOrgIdAndMesspunkt(ORG_ID, MESSPUNKT)).thenReturn(List.of());

        service.handle(TOPIC, payload("2026-01-01T10:07:00Z", "1.0", "0.0"));

        verify(metrics).recordFailed();
        verify(rohdatenRepository, never()).save(any());
    }

    @Test
    void handle_RepositoryThrows_DiscardedWithoutPropagation() {
        when(einheitRepository.findAllByOrgIdAndMesspunkt(ORG_ID, MESSPUNKT))
                .thenThrow(new RuntimeException("DB down"));

        assertDoesNotThrow(() ->
                service.handle(TOPIC, payload("2026-01-01T10:07:00Z", "1.0", "0.0")));

        verify(metrics).recordFailed();
        verify(rohdatenRepository, never()).save(any());
    }
}
