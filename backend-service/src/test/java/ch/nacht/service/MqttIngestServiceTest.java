package ch.nacht.service;

import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.ZaehlerRohdaten;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.ZaehlerRohdatenRepository;
import com.fasterxml.jackson.databind.DeserializationFeature;
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
        return payload(timestamp, bezug, einspeisung, null);
    }

    /**
     * Wie {@link #payload(String, String, String)}, zusätzlich mit dem optionalen Feld
     * {@code seriennummer}. {@code zusatzJson} ist ein rohes JSON-Fragment (z. B.
     * {@code "seriennummer":"ABC123"}); {@code null} lässt das Feld weg.
     */
    private String payload(String timestamp, String bezug, String einspeisung, String zusatzJson) {
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
        if (zusatzJson != null) {
            sb.append(zusatzJson).append(",");
        }
        if (sb.charAt(sb.length() - 1) == ',') {
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append("}");
        return sb.toString();
    }

    /** JSON-Fragment {@code "seriennummer":"<wert>"} (Wert wird unverändert eingesetzt). */
    private String seriennummerJson(String wert) {
        return "\"seriennummer\":\"" + wert + "\"";
    }

    /** Fängt den einzigen gespeicherten Rohdatensatz ab. */
    private ZaehlerRohdaten captureSavedRohdaten() {
        ArgumentCaptor<ZaehlerRohdaten> captor = ArgumentCaptor.forClass(ZaehlerRohdaten.class);
        verify(rohdatenRepository).save(captor.capture());
        return captor.getValue();
    }

    /** Verdrahtet die Standard-Einheit + leeren Upsert-Treffer. */
    private void stubEinheitOhneBestand() {
        when(einheitRepository.findAllByOrgIdAndMesspunkt(ORG_ID, MESSPUNKT)).thenReturn(List.of(einheit));
        when(rohdatenRepository.findByEinheitIdAndZeit(eq(EINHEIT_ID), any())).thenReturn(Optional.empty());
        when(rohdatenRepository.save(any(ZaehlerRohdaten.class))).thenAnswer(inv -> inv.getArgument(0));
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

    // --- Seriennummer (Spec Zaehlertausch-Erkennung.md, FR-1) ----------------

    @Test
    void handle_MitSeriennummer_SpeichertSeriennummer() {
        // Arrange
        stubEinheitOhneBestand();

        // Act
        service.handle(TOPIC, payload("2026-01-01T10:07:00+01:00", "123.4500", "10.0000",
                seriennummerJson("WAGO-8791234")));

        // Assert
        assertEquals("WAGO-8791234", captureSavedRohdaten().getSeriennummer());
        verify(metrics).recordProcessed();
        verify(metrics, never()).recordFailed();
    }

    @Test
    void handle_OhneSeriennummer_SeriennummerNull() {
        // Arrange
        stubEinheitOhneBestand();

        // Act – Payload ohne das optionale Feld (Bestands-Publisher)
        service.handle(TOPIC, payload("2026-01-01T10:07:00+01:00", "123.4500", "10.0000"));

        // Assert – Rohdaten trotzdem gespeichert, Spalte bleibt NULL
        ZaehlerRohdaten saved = captureSavedRohdaten();
        assertNull(saved.getSeriennummer());
        assertEquals(0, new BigDecimal("123.4500").compareTo(saved.getZaehlerstandBezug()));
        verify(metrics).recordProcessed();
        verify(metrics, never()).recordFailed();
    }

    @Test
    void handle_SeriennummerJsonNull_SeriennummerNull() {
        // Arrange
        stubEinheitOhneBestand();

        // Act
        service.handle(TOPIC, payload("2026-01-01T10:07:00+01:00", "123.4500", "10.0000",
                "\"seriennummer\":null"));

        // Assert
        assertNull(captureSavedRohdaten().getSeriennummer());
        verify(metrics, never()).recordFailed();
    }

    @Test
    void handle_SeriennummerMitWhitespace_WirdGetrimmt() {
        // Arrange
        stubEinheitOhneBestand();

        // Act
        service.handle(TOPIC, payload("2026-01-01T10:07:00+01:00", "123.4500", "10.0000",
                seriennummerJson("  ABC123  ")));

        // Assert – kein Scheinwechsel durch Leerzeichen
        assertEquals("ABC123", captureSavedRohdaten().getSeriennummer());
    }

    @Test
    void handle_SeriennummerNurWhitespace_WirdNull() {
        // Arrange
        stubEinheitOhneBestand();

        // Act
        service.handle(TOPIC, payload("2026-01-01T10:07:00+01:00", "123.4500", "10.0000",
                seriennummerJson("   ")));

        // Assert – wie fehlend behandelt → Fallback in der Aggregation
        assertNull(captureSavedRohdaten().getSeriennummer());
        verify(metrics, never()).recordFailed();
    }

    @Test
    void handle_SeriennummerLeer_WirdNull() {
        // Arrange
        stubEinheitOhneBestand();

        // Act
        service.handle(TOPIC, payload("2026-01-01T10:07:00+01:00", "123.4500", "10.0000",
                seriennummerJson("")));

        // Assert
        assertNull(captureSavedRohdaten().getSeriennummer());
    }

    @Test
    void handle_SeriennummerLaengerAls64Zeichen_WirdGekuerztGespeichert() {
        // Arrange – 80 Zeichen; ungekürzt würde der Insert an VARCHAR(64) scheitern und die
        // ganze (transaktionale) Nachricht verwerfen.
        stubEinheitOhneBestand();
        String zuLang = "X".repeat(80);

        // Act
        service.handle(TOPIC, payload("2026-01-01T10:07:00+01:00", "123.4500", "10.0000",
                seriennummerJson(zuLang)));

        // Assert
        assertEquals("X".repeat(64), captureSavedRohdaten().getSeriennummer());
        verify(metrics).recordProcessed();
        verify(metrics, never()).recordFailed();
    }

    @Test
    void handle_SeriennummerGenau64Zeichen_UnveraendertGespeichert() {
        // Arrange – Grenzwert: exakt die Spaltenlänge
        stubEinheitOhneBestand();
        String genau64 = "Y".repeat(64);

        // Act
        service.handle(TOPIC, payload("2026-01-01T10:07:00+01:00", "123.4500", "10.0000",
                seriennummerJson(genau64)));

        // Assert
        assertEquals(genau64, captureSavedRohdaten().getSeriennummer());
    }

    @Test
    void handle_SeriennummerMitWhitespaceUndUeberlaenge_ErstTrimmenDannKuerzen() {
        // Arrange – Reihenfolge der Normalisierung: trimmen → kürzen (64 Nutzzeichen bleiben)
        stubEinheitOhneBestand();
        String kern = "Z".repeat(70);

        // Act
        service.handle(TOPIC, payload("2026-01-01T10:07:00+01:00", "123.4500", "10.0000",
                seriennummerJson("   " + kern + "   ")));

        // Assert
        assertEquals("Z".repeat(64), captureSavedRohdaten().getSeriennummer());
    }

    @Test
    void handle_GeteilterBilanzMesspunkt_SelbeSeriennummerAnBeidenEinheiten() {
        // Arrange – ein physischer Bilanzzähler, zwei Einheiten (BEZUG + RUECKLIEFERUNG)
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

        // Act – ein Config-Eintrag je Messpunkt ⇒ eine gemeinsame Serie für beide Einheiten
        service.handle(TOPIC, payload("2026-01-01T10:07:00+01:00", "123.4500", "10.0000",
                seriennummerJson("BILANZ-4711")));

        // Assert – beide Rohdatensätze tragen dieselbe Serie
        ArgumentCaptor<ZaehlerRohdaten> captor = ArgumentCaptor.forClass(ZaehlerRohdaten.class);
        verify(rohdatenRepository, times(2)).save(captor.capture());
        assertEquals("BILANZ-4711", captor.getAllValues().get(0).getSeriennummer());
        assertEquals("BILANZ-4711", captor.getAllValues().get(1).getSeriennummer());
        verify(metrics, never()).recordFailed();
    }

    @Test
    void handle_BestehenderRohdatensatz_AktualisiertSeriennummer() {
        // Arrange – Upsert-Zweig: vorhandene Zeile mit alter Serie
        ZaehlerRohdaten existing = new ZaehlerRohdaten(ORG_ID, EINHEIT_ID,
                LocalDateTime.of(2026, 1, 1, 10, 7),
                new BigDecimal("100.0000"), new BigDecimal("5.0000"));
        existing.setSeriennummer("SN-ALT");
        when(einheitRepository.findAllByOrgIdAndMesspunkt(ORG_ID, MESSPUNKT)).thenReturn(List.of(einheit));
        when(rohdatenRepository.findByEinheitIdAndZeit(eq(EINHEIT_ID), any())).thenReturn(Optional.of(existing));
        when(rohdatenRepository.save(any(ZaehlerRohdaten.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        service.handle(TOPIC, payload("2026-01-01T10:07:00+01:00", "123.4500", "10.0000",
                seriennummerJson(" SN-NEU ")));

        // Assert
        ZaehlerRohdaten saved = captureSavedRohdaten();
        assertSame(existing, saved);
        assertEquals("SN-NEU", saved.getSeriennummer());
    }

    @Test
    void handle_UnbekanntesZusatzfeldImPayload_WirdVerarbeitet() {
        // Arrange – Spring Boot konfiguriert den injizierten ObjectMapper mit
        // FAIL_ON_UNKNOWN_PROPERTIES = false; hier nachgestellt, damit der Deploy-Reihenfolge-
        // Vertrag (Pi sendet ein Feld, das das Backend noch nicht kennt) geprüft wird.
        MqttIngestService toleranterService = new MqttIngestService(einheitRepository, rohdatenRepository,
                new ObjectMapper().registerModule(new JavaTimeModule())
                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES),
                metrics);
        stubEinheitOhneBestand();

        // Act
        toleranterService.handle(TOPIC, payload("2026-01-01T10:07:00+01:00", "123.4500", "10.0000",
                "\"qualitaet\":\"OK\"," + seriennummerJson("SN-1")));

        // Assert – Nachricht nicht verworfen
        assertEquals("SN-1", captureSavedRohdaten().getSeriennummer());
        verify(metrics).recordProcessed();
        verify(metrics, never()).recordFailed();
    }

    // --- Ladestationen bleiben aussen vor (Specs/Ladestationen.md) ------------
    // Der `messpunkt` einer LADESTATION ist eine RFID, keine Zaehlerkennung. Faellt sie zufaellig
    // mit einer Zaehlerkennung zusammen, entstuenden ohne Filter Messwerte an einer Einheit, die
    // nie an der Verteilung teilnimmt.

    /** Ladestations-Einheit, deren RFID mit dem Topic-Messpunkt zusammenfaellt. */
    private Einheit ladestation(Long id) {
        Einheit ladestation = new Einheit("Ladestation 1", EinheitTyp.LADESTATION);
        ladestation.setId(id);
        ladestation.setOrgId(ORG_ID);
        ladestation.setMesspunkt(MESSPUNKT);
        return ladestation;
    }

    @Test
    void handle_NurLadestationAmMesspunkt_Discarded() {
        when(einheitRepository.findAllByOrgIdAndMesspunkt(ORG_ID, MESSPUNKT))
                .thenReturn(List.of(ladestation(900L)));

        service.handle(TOPIC, payload("2026-01-01T10:07:00+01:00", "123.4500", "10.0000"));

        // Wie ein unbekannter Messpunkt: verworfen, keine Rohdaten
        verify(metrics).recordFailed();
        verify(metrics, never()).recordProcessed();
        verify(rohdatenRepository, never()).save(any());
        verify(rohdatenRepository, never()).findByEinheitIdAndZeit(anyLong(), any());
    }

    @Test
    void handle_LadestationUndConsumerAmMesspunkt_NurConsumerErhaeltRohdaten() {
        when(einheitRepository.findAllByOrgIdAndMesspunkt(ORG_ID, MESSPUNKT))
                .thenReturn(List.of(ladestation(900L), einheit));
        when(rohdatenRepository.findByEinheitIdAndZeit(eq(EINHEIT_ID), any())).thenReturn(Optional.empty());
        when(rohdatenRepository.save(any(ZaehlerRohdaten.class))).thenAnswer(inv -> inv.getArgument(0));

        service.handle(TOPIC, payload("2026-01-01T10:07:00+01:00", "123.4500", "10.0000"));

        // Genau ein Rohdatensatz - und zwar der der CONSUMER-Einheit
        assertEquals(EINHEIT_ID, captureSavedRohdaten().getEinheitId());
        verify(metrics).recordProcessed();
        verify(metrics, never()).recordFailed();
    }
}
