package ch.nacht.service;

import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.Geraetezustand;
import ch.nacht.entity.MeldungLevel;
import ch.nacht.entity.MesspunktEmpfangErgebnis;
import ch.nacht.entity.MesspunktEmpfangQuelle;
import ch.nacht.entity.ZaehlerRohdaten;
import ch.nacht.entity.Zustandsgroesse;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.GeraetezustandRepository;
import ch.nacht.repository.ZaehlerRohdatenRepository;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private GeraetezustandRepository geraetezustandRepository;

    @Mock
    private SystemmeldungService systemmeldungService;

    @Mock
    private MqttMetrics metrics;

    @Mock
    private MesspunktEmpfangService messpunktEmpfangService;

    private ObjectMapper objectMapper;
    private MqttIngestService service;

    private Einheit einheit;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new MqttIngestService(einheitRepository, rohdatenRepository,
                geraetezustandRepository, systemmeldungService, objectMapper, metrics,
                messpunktEmpfangService);

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
                geraetezustandRepository, systemmeldungService,
                new ObjectMapper().registerModule(new JavaTimeModule())
                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES),
                metrics, messpunktEmpfangService);
        stubEinheitOhneBestand();

        // Act
        toleranterService.handle(TOPIC, payload("2026-01-01T10:07:00+01:00", "123.4500", "10.0000",
                "\"qualitaet\":\"OK\"," + seriennummerJson("SN-1")));

        // Assert – Nachricht nicht verworfen
        assertEquals("SN-1", captureSavedRohdaten().getSeriennummer());
        verify(metrics).recordProcessed();
        verify(metrics, never()).recordFailed();
    }

    // --- Gerätezustand: zustand ist kein Objekt (Specs/Gerätezustand.md, FR-1.2) -
    // Ist `zustand` eine Zahl, ein Text oder eine Liste, darf nur der Zustandswert verloren gehen.
    // Mit `Map<String, Object>` als Feldtyp scheiterte Jackson schon beim Parsen, und der
    // generische catch verwarf die ganze Nachricht samt Zaehlerstaenden.

    @ParameterizedTest
    @ValueSource(strings = {"5", "\"x\"", "[1]"})
    void handle_ZustandKeinObjekt_SpeichertZaehlerstaendeUndMeldet(String zustandJson) {
        // Arrange
        stubEinheitOhneBestand();

        // Act
        service.handle(TOPIC, payload("2026-01-01T10:07:00+01:00", "123.4500", "10.0000",
                "\"zustand\":" + zustandJson));

        // Assert - Zaehlerstaende gespeichert, Nachricht gilt als verarbeitet
        assertEquals(0, new BigDecimal("123.4500").compareTo(captureSavedRohdaten().getZaehlerstandBezug()));
        verify(metrics).recordProcessed();
        verify(metrics, never()).recordFailed();
        // Kein Zustandswert, aber eine Meldung - ein solcher Payload ist ein Konfigurationsfehler
        verifyNoInteractions(geraetezustandRepository);
        verify(systemmeldungService).erfasse(eq(ORG_ID), eq(MeldungLevel.WARN),
                eq(SystemmeldungService.KATEGORIE_MQTT),
                eq(SystemmeldungService.KEY_GERAETEZUSTAND_WERT_UNGUELTIG), any());
    }

    // --- Gerätezustand: Zustandswerte (Specs/Gerätezustand.md, Abschnitt 3) --
    // Zustandswerte sind Beiwerk: Ein verworfener Wert kostet weder die Zaehlerstaende noch die
    // Metriken, und eine Meldung dazu wird nie automatisch erledigt.

    private static final long SPEICHER_ID = 30L;

    /** Zeitstempel mit Offset +02:00 (Sommerzeit) - gespeichert wird die Wanduhrzeit verbatim. */
    private static final String SOMMER_ZEIT = "2026-07-10T14:30:00+02:00";

    private Einheit speicher(Long id, String name) {
        Einheit speicher = new Einheit(name, EinheitTyp.SPEICHER);
        speicher.setId(id);
        speicher.setOrgId(ORG_ID);
        speicher.setMesspunkt(MESSPUNKT);
        return speicher;
    }

    /** Payload mit Zaehlerstaenden und dem rohen {@code zustand}-JSON. */
    private String payloadMitZustand(String timestamp, String zustandJson) {
        return payload(timestamp, "123.4500", "10.0000", "\"zustand\":" + zustandJson);
    }

    /** Verdrahtet die uebergebenen Einheiten am Messpunkt + leere Rohdaten-Upserts. */
    private void stubEinheitenOhneRohdaten(Einheit... einheiten) {
        when(einheitRepository.findAllByOrgIdAndMesspunkt(ORG_ID, MESSPUNKT)).thenReturn(List.of(einheiten));
        when(rohdatenRepository.findByEinheitIdAndZeit(anyLong(), any())).thenReturn(Optional.empty());
        when(rohdatenRepository.save(any(ZaehlerRohdaten.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** Ein SPEICHER am Messpunkt, ohne vorhandene Rohdaten und ohne vorhandenen Zustandswert. */
    private void stubSpeicherOhneBestand() {
        stubEinheitenOhneRohdaten(speicher(SPEICHER_ID, "Batterie"));
        when(geraetezustandRepository.findByEinheitIdAndZeitAndGroesse(anyLong(), any(), any()))
                .thenReturn(Optional.empty());
    }

    /** Faengt den einzigen gespeicherten Zustandswert ab. */
    private Geraetezustand captureSavedZustand() {
        ArgumentCaptor<Geraetezustand> captor = ArgumentCaptor.forClass(Geraetezustand.class);
        verify(geraetezustandRepository).save(captor.capture());
        return captor.getValue();
    }

    /** Die Zaehlerstaende der Nachricht sind gespeichert, und sie gilt als verarbeitet. */
    private void assertZaehlerstaendeGespeichert() {
        ArgumentCaptor<ZaehlerRohdaten> captor = ArgumentCaptor.forClass(ZaehlerRohdaten.class);
        verify(rohdatenRepository, atLeastOnce()).save(captor.capture());
        assertEquals(0, new BigDecimal("123.4500").compareTo(captor.getValue().getZaehlerstandBezug()));
        verify(metrics).recordProcessed();
        verify(metrics, never()).recordFailed();
    }

    /** Faengt den Parameter der einzigen Systemmeldung mit diesem Key ab (WARN, Kategorie MQTT). */
    private String captureMeldungsParameter(String key) {
        ArgumentCaptor<String> parameter = ArgumentCaptor.forClass(String.class);
        verify(systemmeldungService).erfasse(eq(ORG_ID), eq(MeldungLevel.WARN),
                eq(SystemmeldungService.KATEGORIE_MQTT), eq(key), parameter.capture());
        return parameter.getValue();
    }

    @Test
    void handle_ZustandSocAnSpeicher_SpeichertZustandswertUndZaehlerstaende() {
        // Arrange
        stubSpeicherOhneBestand();

        // Act
        service.handle(TOPIC, payloadMitZustand(SOMMER_ZEIT, "{\"soc\": 87.5}"));

        // Assert
        Geraetezustand saved = captureSavedZustand();
        assertEquals(ORG_ID, saved.getOrgId());
        assertEquals(SPEICHER_ID, saved.getEinheitId());
        assertEquals(Zustandsgroesse.SOC, saved.getGroesse());
        assertEquals(0, new BigDecimal("87.5").compareTo(saved.getWert()));
        assertNotNull(saved.getEmpfangenAm());
        assertZaehlerstaendeGespeichert();
        verifyNoInteractions(systemmeldungService);
    }

    @Test
    void handle_ZustandZeitMitOffsetPlus2_OrtszeitVerbatim() {
        // Arrange
        stubSpeicherOhneBestand();

        // Act - 14:30+02:00 ist 12:30 UTC; gespeichert wird die Wanduhrzeit 14:30
        service.handle(TOPIC, payloadMitZustand(SOMMER_ZEIT, "{\"soc\": 87.5}"));

        // Assert - unabhaengig von der Zeitzone der Test-JVM
        LocalDateTime erwartet = LocalDateTime.of(2026, 7, 10, 14, 30);
        assertEquals(erwartet, captureSavedZustand().getZeit());
        verify(geraetezustandRepository).findByEinheitIdAndZeitAndGroesse(SPEICHER_ID, erwartet,
                Zustandsgroesse.SOC);
    }

    @Test
    void handle_ZustandMitOrgIdImPayload_OrgIdStammtAusTopic() {
        // Arrange - toleranter Mapper wie im Betrieb, damit die fremden Felder nicht verwerfen
        MqttIngestService toleranterService = new MqttIngestService(einheitRepository, rohdatenRepository,
                geraetezustandRepository, systemmeldungService,
                new ObjectMapper().registerModule(new JavaTimeModule())
                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES),
                metrics, messpunktEmpfangService);
        stubSpeicherOhneBestand();

        // Act - der Payload behauptet einen anderen Mandanten
        toleranterService.handle(TOPIC, payload(SOMMER_ZEIT, "123.4500", "10.0000",
                "\"orgId\":999,\"org_id\":999,\"zustand\":{\"soc\":50}"));

        // Assert
        assertEquals(ORG_ID, captureSavedZustand().getOrgId());
        verify(einheitRepository).findAllByOrgIdAndMesspunkt(ORG_ID, MESSPUNKT);
    }

    @Test
    void handle_OhneZustand_KeinZustandswert() {
        // Arrange
        stubEinheitenOhneRohdaten(speicher(SPEICHER_ID, "Batterie"));

        // Act
        service.handle(TOPIC, payload(SOMMER_ZEIT, "123.4500", "10.0000"));

        // Assert
        assertZaehlerstaendeGespeichert();
        verifyNoInteractions(geraetezustandRepository);
        verifyNoInteractions(systemmeldungService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null"})
    void handle_ZustandLeerOderNull_KeinZustandswert(String zustandJson) {
        // Arrange
        stubEinheitenOhneRohdaten(speicher(SPEICHER_ID, "Batterie"));

        // Act
        service.handle(TOPIC, payloadMitZustand(SOMMER_ZEIT, zustandJson));

        // Assert
        assertZaehlerstaendeGespeichert();
        verifyNoInteractions(geraetezustandRepository);
        verifyNoInteractions(systemmeldungService);
    }

    @Test
    void handle_ZustandSocUndUnbekannterSchluessel_GenauEineZeile() {
        // Arrange
        stubSpeicherOhneBestand();

        // Act
        service.handle(TOPIC, payloadMitZustand(SOMMER_ZEIT, "{\"soc\": 87.5, \"temperatur\": 21.5}"));

        // Assert - nur SOC; die unbekannte Groesse wird ohne Meldung verworfen
        Geraetezustand saved = captureSavedZustand();
        assertEquals(Zustandsgroesse.SOC, saved.getGroesse());
        assertEquals(0, new BigDecimal("87.5").compareTo(saved.getWert()));
        assertZaehlerstaendeGespeichert();
        verifyNoInteractions(systemmeldungService);
    }

    @Test
    void handle_ZustandUnbekannteGroesse_VerworfenOhneMeldung() {
        // Arrange
        stubEinheitenOhneRohdaten(speicher(SPEICHER_ID, "Batterie"));

        // Act
        service.handle(TOPIC, payloadMitZustand(SOMMER_ZEIT, "{\"temperatur\": 21.5}"));

        // Assert - die Nachricht bleibt gueltig
        assertZaehlerstaendeGespeichert();
        verifyNoInteractions(geraetezustandRepository);
        verifyNoInteractions(systemmeldungService);
    }

    @Test
    void handle_ZustandSocKeineZahl_VerwirftNurZustandswert() {
        // Arrange
        stubEinheitenOhneRohdaten(speicher(SPEICHER_ID, "Batterie"));

        // Act
        service.handle(TOPIC, payloadMitZustand(SOMMER_ZEIT, "{\"soc\": \"abc\"}"));

        // Assert - Zaehlerstaende gespeichert, Zustandswert verworfen und gemeldet
        assertZaehlerstaendeGespeichert();
        verify(geraetezustandRepository, never()).save(any());
        assertTrue(captureMeldungsParameter(SystemmeldungService.KEY_GERAETEZUSTAND_WERT_UNGUELTIG)
                .contains("abc"));
        verify(systemmeldungService, never()).autoResolve(anyLong(), any());
    }

    @Test
    void handle_ZustandSocNull_KeineZeileKeineMeldung() {
        // Arrange
        stubEinheitenOhneRohdaten(speicher(SPEICHER_ID, "Batterie"));

        // Act
        service.handle(TOPIC, payloadMitZustand(SOMMER_ZEIT, "{\"soc\": null}"));

        // Assert - "nicht gemeldet" ist kein Stoerfall
        assertZaehlerstaendeGespeichert();
        verifyNoInteractions(geraetezustandRepository);
        verifyNoInteractions(systemmeldungService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"soc", "SOC", "Soc"})
    void handle_ZustandSchluesselSchreibweise_WirdAufSocAbgebildet(String schluessel) {
        // Arrange
        stubSpeicherOhneBestand();

        // Act
        service.handle(TOPIC, payloadMitZustand(SOMMER_ZEIT, "{\"" + schluessel + "\": 42}"));

        // Assert - in der Tabelle steht der Enum-Name SOC
        assertEquals(Zustandsgroesse.SOC, captureSavedZustand().getGroesse());
    }

    @Test
    void handle_ZustandBestehenderDatensatz_AktualisiertStattNeu() {
        // Arrange - dieselbe Nachricht wurde schon einmal empfangen
        Geraetezustand vorhanden = new Geraetezustand(ORG_ID, SPEICHER_ID,
                LocalDateTime.of(2026, 7, 10, 14, 30), Zustandsgroesse.SOC, new BigDecimal("80.0"));
        vorhanden.setId(77L);
        stubEinheitenOhneRohdaten(speicher(SPEICHER_ID, "Batterie"));
        when(geraetezustandRepository.findByEinheitIdAndZeitAndGroesse(SPEICHER_ID,
                LocalDateTime.of(2026, 7, 10, 14, 30), Zustandsgroesse.SOC))
                .thenReturn(Optional.of(vorhanden));

        // Act
        service.handle(TOPIC, payloadMitZustand(SOMMER_ZEIT, "{\"soc\": 87.5}"));

        // Assert - dieselbe Instanz (Upsert), keine zweite Zeile
        Geraetezustand saved = captureSavedZustand();
        assertSame(vorhanden, saved);
        assertEquals(77L, saved.getId());
        assertEquals(0, new BigDecimal("87.5").compareTo(saved.getWert()));
    }

    @Test
    void handle_ZustandHerbstZeitumstellung_ZweiterDurchgangUeberschreibtErsten() {
        // Arrange - 02:30 tritt am 25.10.2026 zweimal auf: erst mit +02:00, dann mit +01:00.
        // Der Upsert-Schluessel ist die Ortszeit; der zweite Durchgang trifft den ersten Datensatz.
        LocalDateTime ortszeit = LocalDateTime.of(2026, 10, 25, 2, 30);
        stubEinheitenOhneRohdaten(speicher(SPEICHER_ID, "Batterie"));
        Geraetezustand[] gespeichert = new Geraetezustand[1];
        when(geraetezustandRepository.findByEinheitIdAndZeitAndGroesse(SPEICHER_ID, ortszeit,
                Zustandsgroesse.SOC))
                .thenAnswer(inv -> Optional.ofNullable(gespeichert[0]));
        when(geraetezustandRepository.save(any(Geraetezustand.class))).thenAnswer(inv -> {
            gespeichert[0] = inv.getArgument(0);
            return gespeichert[0];
        });

        // Act
        service.handle(TOPIC, payloadMitZustand("2026-10-25T02:30:00+02:00", "{\"soc\": 40}"));
        Geraetezustand ersterDurchgang = gespeichert[0];
        service.handle(TOPIC, payloadMitZustand("2026-10-25T02:30:00+01:00", "{\"soc\": 45}"));

        // Assert - beide Male dieselbe Zeile, der Wert des zweiten Durchgangs gilt
        ArgumentCaptor<Geraetezustand> captor = ArgumentCaptor.forClass(Geraetezustand.class);
        verify(geraetezustandRepository, times(2)).save(captor.capture());
        assertNotNull(ersterDurchgang);
        assertSame(ersterDurchgang, captor.getAllValues().get(1));
        assertEquals(ortszeit, captor.getAllValues().get(1).getZeit());
        assertEquals(0, new BigDecimal("45").compareTo(captor.getAllValues().get(1).getWert()));
    }

    @Test
    void handle_ZustandHerbstZeitumstellung_GanzerTagErgibt96ViertelstundenMitWerten() {
        // Arrange - der 25.10.2026 hat 25 Stunden, also 100 Viertelstunden. Ein Wert je
        // Viertelstunde, wie ihn der Pi mit Offset sendet. Der Speicher im Test haelt die Zeilen
        // nach dem Upsert-Schluessel (Ortszeit) - wie der UNIQUE-Index in V153.
        stubEinheitenOhneRohdaten(speicher(SPEICHER_ID, "Batterie"));
        Map<LocalDateTime, Geraetezustand> zeilen = new HashMap<>();
        when(geraetezustandRepository.findByEinheitIdAndZeitAndGroesse(eq(SPEICHER_ID), any(),
                eq(Zustandsgroesse.SOC)))
                .thenAnswer(inv -> Optional.ofNullable(zeilen.get(inv.<LocalDateTime>getArgument(1))));
        when(geraetezustandRepository.save(any(Geraetezustand.class))).thenAnswer(inv -> {
            Geraetezustand z = inv.getArgument(0);
            zeilen.put(z.getZeit(), z);
            return z;
        });
        ZoneId zuerich = ZoneId.of("Europe/Zurich");
        ZonedDateTime zeitpunkt = ZonedDateTime.of(2026, 10, 25, 0, 0, 0, 0, zuerich);
        ZonedDateTime tagesende = zeitpunkt.plusDays(1);

        // Act - der SOC steigt je Viertelstunde, damit erkennbar ist, welcher Durchgang gilt
        int nachrichten = 0;
        while (zeitpunkt.isBefore(tagesende)) {
            service.handle(TOPIC, payloadMitZustand(zeitpunkt.toOffsetDateTime().toString(),
                    "{\"soc\": " + (nachrichten / 2.0) + "}"));
            nachrichten++;
            zeitpunkt = zeitpunkt.plusMinutes(15);   // Instant-Addition: 02:00-03:00 kommt zweimal
        }

        // Assert - 100 Nachrichten, aber nur 96 Viertelstunden mit Werten; in der doppelten
        // Stunde gilt der zweite Durchgang (02:30 ist Nachricht 10 bzw. 14)
        assertEquals(100, nachrichten);
        assertEquals(96, zeilen.size());
        assertEquals(0, new BigDecimal("7").compareTo(
                zeilen.get(LocalDateTime.of(2026, 10, 25, 2, 30)).getWert()));
        verify(metrics, times(100)).recordProcessed();
        verify(metrics, never()).recordFailed();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "100", "0.0", "100.000"})
    void handle_ZustandSocAufBereichsgrenze_WirdGespeichert(String wert) {
        // Arrange
        stubSpeicherOhneBestand();

        // Act
        service.handle(TOPIC, payloadMitZustand(SOMMER_ZEIT, "{\"soc\": " + wert + "}"));

        // Assert - Grenzen eingeschlossen
        assertEquals(0, new BigDecimal(wert).compareTo(captureSavedZustand().getWert()));
        verifyNoInteractions(systemmeldungService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"101", "-1", "100.001"})
    void handle_ZustandSocAusserhalbBereich_VerworfenMitMeldungZaehlerstaendeBleiben(String wert) {
        // Arrange
        stubEinheitenOhneRohdaten(speicher(SPEICHER_ID, "Batterie"));

        // Act
        service.handle(TOPIC, payloadMitZustand(SOMMER_ZEIT, "{\"soc\": " + wert + "}"));

        // Assert - Zustandswert verworfen, Zaehlerstaende gespeichert, Metriken unberuehrt
        verify(geraetezustandRepository, never()).save(any());
        assertZaehlerstaendeGespeichert();
        String parameter = captureMeldungsParameter(SystemmeldungService.KEY_GERAETEZUSTAND_WERT_UNGUELTIG);
        assertTrue(parameter.contains("Batterie"), parameter);      // Einheit
        assertTrue(parameter.contains("SOC"), parameter);           // Groesse
        assertTrue(parameter.contains(wert), parameter);            // Wert
        assertTrue(parameter.contains("0–100 %"), parameter);       // erlaubter Bereich
        // Nie automatisch erledigt - der Betreiber hakt sie ab
        verify(systemmeldungService, never()).autoResolve(anyLong(), any());
        verify(systemmeldungService, never()).erfasse(anyLong(), any(), any(),
                eq(SystemmeldungService.KEY_GERAETEZUSTAND_TYP_UNGUELTIG), any());
    }

    @Test
    void handle_ZustandSocAnConsumer_VerworfenMitTypMeldung() {
        // Arrange - Standard-Einheit ist CONSUMER
        stubEinheitOhneBestand();

        // Act
        service.handle(TOPIC, payloadMitZustand(SOMMER_ZEIT, "{\"soc\": 87.5}"));

        // Assert - eigener Key, nicht der der Bereichsverletzung
        verify(geraetezustandRepository, never()).save(any());
        assertZaehlerstaendeGespeichert();
        String parameter = captureMeldungsParameter(SystemmeldungService.KEY_GERAETEZUSTAND_TYP_UNGUELTIG);
        assertTrue(parameter.contains("Wohnung 1"), parameter);
        assertTrue(parameter.contains("CONSUMER"), parameter);
        assertTrue(parameter.contains("SOC"), parameter);
        verify(systemmeldungService, never()).erfasse(anyLong(), any(), any(),
                eq(SystemmeldungService.KEY_GERAETEZUSTAND_WERT_UNGUELTIG), any());
        verify(systemmeldungService, never()).autoResolve(anyLong(), any());
    }

    @Test
    void handle_ZustandMehrereZulaessigeEinheiten_GenauEineZeileAnKleinsterId() {
        // Arrange - konstruiert: zwei SPEICHER am selben Messpunkt, die groessere id zuerst
        stubEinheitenOhneRohdaten(speicher(31L, "Batterie B"), speicher(SPEICHER_ID, "Batterie A"));
        when(geraetezustandRepository.findByEinheitIdAndZeitAndGroesse(anyLong(), any(), any()))
                .thenReturn(Optional.empty());

        // Act
        service.handle(TOPIC, payloadMitZustand(SOMMER_ZEIT, "{\"soc\": 87.5}"));

        // Assert - genau eine Zeile, an der kleinsten id; Rohdaten fuer beide Einheiten
        assertEquals(SPEICHER_ID, captureSavedZustand().getEinheitId());
        verify(rohdatenRepository, times(2)).save(any(ZaehlerRohdaten.class));
        verifyNoInteractions(systemmeldungService);
    }

    @Test
    void handle_ZustandSpeicherUndConsumerAmMesspunkt_ZeileAmSpeicherOhneMeldung() {
        // Arrange - nur der SPEICHER fuehrt SOC; der CONSUMER daneben ist kein Fehler
        stubEinheitenOhneRohdaten(einheit, speicher(SPEICHER_ID, "Batterie"));
        when(geraetezustandRepository.findByEinheitIdAndZeitAndGroesse(anyLong(), any(), any()))
                .thenReturn(Optional.empty());

        // Act
        service.handle(TOPIC, payloadMitZustand(SOMMER_ZEIT, "{\"soc\": 87.5}"));

        // Assert
        assertEquals(SPEICHER_ID, captureSavedZustand().getEinheitId());
        verifyNoInteractions(systemmeldungService);
    }

    @Test
    void handle_ZustandUnbekannterMesspunkt_VerwirftAuchZustandswert() {
        // Arrange
        when(einheitRepository.findAllByOrgIdAndMesspunkt(ORG_ID, MESSPUNKT)).thenReturn(List.of());

        // Act
        service.handle(TOPIC, payloadMitZustand(SOMMER_ZEIT, "{\"soc\": 87.5}"));

        // Assert - die ganze Nachricht ist verworfen, wie bisher
        verify(metrics).recordFailed();
        verify(rohdatenRepository, never()).save(any());
        verifyNoInteractions(geraetezustandRepository);
        verifyNoInteractions(systemmeldungService);
    }

    @Test
    void handle_ZustandMeldungScheitert_ZaehlerstaendeBleiben() {
        // Arrange - die Systemmeldung selbst wirft; das darf die Nachricht nicht kosten
        stubEinheitenOhneRohdaten(speicher(SPEICHER_ID, "Batterie"));
        doThrow(new RuntimeException("DB down")).when(systemmeldungService)
                .erfasse(anyLong(), any(), any(), any(), any());

        // Act
        service.handle(TOPIC, payloadMitZustand(SOMMER_ZEIT, "{\"soc\": 101}"));

        // Assert
        assertZaehlerstaendeGespeichert();
        verify(geraetezustandRepository, never()).save(any());
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

    // --- gPlugK (FR-10): Tasmota-SENSOR-Meldung ------------------------------

    private static final String GPLUG_TOPIC = "gPlug/100/MP-001/tele/SENSOR";

    /** Eine gPlug-Meldung wie in FR-10.3; {@code zFelder} ist der Inhalt des Objekts {@code z}. */
    private String gplug(String time, String zFelder) {
        String zeit = time == null ? "" : "\"Time\":\"" + time + "\",";
        return "{" + zeit + "\"z\":{" + zFelder + "}}";
    }

    /** Vollständige Meldung mit allen Feldern, die ein gPlugK typischerweise liefert. */
    private static final String GPLUG_Z_VOLL = "\"SMid\":\"12345678\","
            + "\"Pi\":0.512,\"Po\":0.000,\"Pi1\":0.170,\"Pi2\":0.210,\"Pi3\":0.132,"
            + "\"I1\":0.80,\"I2\":0.95,\"I3\":0.61,\"U1\":231,\"U2\":230,\"U3\":232,"
            + "\"Ei\":12345.678,\"Eo\":4321.000,"
            + "\"Ei1\":8000.123,\"Ei2\":4345.555,\"Eo1\":3000.000,\"Eo2\":1321.000";

    @Test
    void handle_GplugMeldung_SpeichertRohdaten() {
        // Arrange
        stubEinheitOhneBestand();

        // Act - Tasmota-Zeit ohne Offset, alle typischen Felder
        service.handle(GPLUG_TOPIC, gplug("2026-10-20T14:30:00", GPLUG_Z_VOLL));

        // Assert - Time verbatim, Ei -> Bezug, Eo -> Einspeisung, SMid -> Seriennummer
        ZaehlerRohdaten saved = captureSavedRohdaten();
        assertEquals(ORG_ID, saved.getOrgId());
        assertEquals(EINHEIT_ID, saved.getEinheitId());
        assertEquals(LocalDateTime.of(2026, 10, 20, 14, 30), saved.getZeit());
        assertEquals(0, new BigDecimal("12345.678").compareTo(saved.getZaehlerstandBezug()));
        assertEquals(0, new BigDecimal("4321.000").compareTo(saved.getZaehlerstandEinspeisung()));
        assertEquals("12345678", saved.getSeriennummer());
        assertFalse(saved.isVerarbeitet());
        verify(metrics).recordReceived();
        verify(metrics).recordProcessed();
        verify(metrics, never()).recordFailed();
    }

    /** Dieselbe Messung als Pi- und als gPlug-Meldung ergibt denselben Rohdatensatz (FR-10.4). */
    @Test
    void handle_GplugUndPiMeldung_GleicherRohdatensatz() {
        // Arrange
        stubEinheitOhneBestand();

        // Act
        service.handle(TOPIC, payload("2026-10-20T14:30:00+02:00", "12345.678", "4321.000",
                seriennummerJson("12345678")));
        service.handle(GPLUG_TOPIC, gplug("2026-10-20T14:30:00",
                "\"SMid\":\"12345678\",\"Ei\":12345.678,\"Eo\":4321.000"));

        // Assert
        ArgumentCaptor<ZaehlerRohdaten> captor = ArgumentCaptor.forClass(ZaehlerRohdaten.class);
        verify(rohdatenRepository, times(2)).save(captor.capture());
        ZaehlerRohdaten pi = captor.getAllValues().get(0);
        ZaehlerRohdaten gplug = captor.getAllValues().get(1);
        assertEquals(pi.getOrgId(), gplug.getOrgId());
        assertEquals(pi.getEinheitId(), gplug.getEinheitId());
        assertEquals(pi.getZeit(), gplug.getZeit());
        assertEquals(0, pi.getZaehlerstandBezug().compareTo(gplug.getZaehlerstandBezug()));
        assertEquals(0, pi.getZaehlerstandEinspeisung().compareTo(gplug.getZaehlerstandEinspeisung()));
        assertEquals(pi.getSeriennummer(), gplug.getSeriennummer());
        verify(metrics, times(2)).recordProcessed();
    }

    @Test
    void handle_GplugOhneEoUndSMid_EinspeisungNullSeriennummerLeer() {
        // Arrange - Zaehler ohne Einspeiseregister und ohne Kennung
        stubEinheitOhneBestand();

        // Act
        service.handle(GPLUG_TOPIC, gplug("2026-10-20T14:30:00", "\"Ei\":12345.678"));

        // Assert
        ZaehlerRohdaten saved = captureSavedRohdaten();
        assertEquals(0, BigDecimal.ZERO.compareTo(saved.getZaehlerstandEinspeisung()));
        assertNull(saved.getSeriennummer());
        verify(metrics).recordProcessed();
    }

    @Test
    void handle_GplugZeitMitOffset_LokaleWanduhrzeitVerbatim() {
        // Arrange
        stubEinheitOhneBestand();

        // Act
        service.handle(GPLUG_TOPIC, gplug("2026-10-20T14:30:00+02:00", "\"Ei\":1.5"));

        // Assert
        assertEquals(LocalDateTime.of(2026, 10, 20, 14, 30), captureSavedRohdaten().getZeit());
    }

    /**
     * Unbekannte Felder - in {@code z} wie auf oberster Ebene - verhindern das Speichern nicht
     * (Vertrags-Regel wie FR-3). Schützt vor einer späteren Umstellung auf ein DTO, das bei
     * unbekannten Feldern abbricht.
     */
    @Test
    void handle_GplugUnbekannteFelder_WirdVerarbeitet() {
        // Arrange
        stubEinheitOhneBestand();

        // Act
        service.handle(GPLUG_TOPIC, "{\"Time\":\"2026-10-20T14:30:00\",\"ENERGY\":{\"Total\":1.0},"
                + "\"z\":{\"Q5\":1.0,\"Neu\":\"x\",\"Ei\":12345.678,\"Eo\":4321.0}}");

        // Assert
        assertEquals(0, new BigDecimal("12345.678").compareTo(captureSavedRohdaten().getZaehlerstandBezug()));
        verify(metrics).recordProcessed();
        verify(metrics, never()).recordFailed();
    }

    @Test
    void handle_GplugUngueltigeZeit_Discarded() {
        service.handle(GPLUG_TOPIC, gplug("abc", "\"Ei\":12345.678"));

        verify(metrics).recordFailed();
        verify(rohdatenRepository, never()).save(any());
    }

    @Test
    void handle_GplugOhneTime_Discarded() {
        service.handle(GPLUG_TOPIC, gplug(null, "\"Ei\":12345.678"));

        verify(metrics).recordFailed();
        verify(rohdatenRepository, never()).save(any());
    }

    @Test
    void handle_GplugOhneEi_Discarded() {
        service.handle(GPLUG_TOPIC, gplug("2026-10-20T14:30:00", "\"Eo\":4321.0"));

        verify(metrics).recordFailed();
        verify(rohdatenRepository, never()).save(any());
    }

    @Test
    void handle_GplugKeinJson_Discarded() {
        service.handle(GPLUG_TOPIC, "kein-json");

        verify(metrics).recordFailed();
        verify(rohdatenRepository, never()).save(any());
    }

    /** Vor der ersten NTP-Synchronisation zählt Tasmota ab 1970 (FR-10.5). */
    @Test
    void handle_GplugZeitVor2020_Discarded() {
        service.handle(GPLUG_TOPIC, gplug("1970-01-01T00:05:12", "\"Ei\":12345.678"));

        verify(metrics).recordFailed();
        verify(rohdatenRepository, never()).save(any());
    }

    /** Nullwerte nach einem Neustart des gPlug lösten sonst einen Sprung um den ganzen Stand aus. */
    @Test
    void handle_GplugEiUndEoNull_Discarded() {
        service.handle(GPLUG_TOPIC, gplug("2026-10-20T14:30:00", "\"Ei\":0,\"Eo\":0"));

        verify(metrics).recordFailed();
        verify(rohdatenRepository, never()).save(any());
    }

    @Test
    void handle_GplugUnbekannterMesspunkt_Discarded() {
        when(einheitRepository.findAllByOrgIdAndMesspunkt(ORG_ID, "Fremd")).thenReturn(List.of());

        service.handle("gPlug/100/Fremd/tele/SENSOR", gplug("2026-10-20T14:30:00", "\"Ei\":1.0"));

        verify(metrics).recordFailed();
        verify(rohdatenRepository, never()).save(any());
    }

    @Test
    void handle_GplugUngueltigesTopic_Discarded() {
        service.handle("gPlug/100/MP-001/tele/STATE", gplug("2026-10-20T14:30:00", "\"Ei\":1.0"));
        service.handle("gPlug/abc/MP-001/tele/SENSOR", gplug("2026-10-20T14:30:00", "\"Ei\":1.0"));

        verify(metrics, times(2)).recordFailed();
        verify(einheitRepository, never()).findAllByOrgIdAndMesspunkt(anyLong(), any());
        verify(rohdatenRepository, never()).save(any());
    }

    @Test
    void handle_GplugAnLadestation_Discarded() {
        Einheit ladestation = new Einheit("Ladestation", EinheitTyp.LADESTATION);
        ladestation.setId(30L);
        ladestation.setOrgId(ORG_ID);
        ladestation.setMesspunkt(MESSPUNKT);
        when(einheitRepository.findAllByOrgIdAndMesspunkt(ORG_ID, MESSPUNKT)).thenReturn(List.of(ladestation));

        service.handle(GPLUG_TOPIC, gplug("2026-10-20T14:30:00", "\"Ei\":1.0"));

        verify(metrics).recordFailed();
        verify(rohdatenRepository, never()).save(any());
    }

    @Test
    void handle_GplugGeteilterBilanzMesspunkt_ProjiziertJeRegister() {
        // Arrange
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

        // Act
        service.handle(GPLUG_TOPIC, gplug("2026-10-20T14:30:00", GPLUG_Z_VOLL));

        // Assert - BEZUG nur Ei, RUECKLIEFERUNG nur Eo
        ArgumentCaptor<ZaehlerRohdaten> captor = ArgumentCaptor.forClass(ZaehlerRohdaten.class);
        verify(rohdatenRepository, times(2)).save(captor.capture());
        assertEquals(0, new BigDecimal("12345.678").compareTo(captor.getAllValues().get(0).getZaehlerstandBezug()));
        assertEquals(0, BigDecimal.ZERO.compareTo(captor.getAllValues().get(0).getZaehlerstandEinspeisung()));
        assertEquals(0, BigDecimal.ZERO.compareTo(captor.getAllValues().get(1).getZaehlerstandBezug()));
        assertEquals(0, new BigDecimal("4321.000").compareTo(captor.getAllValues().get(1).getZaehlerstandEinspeisung()));
    }

    /** Ein fremder Mandant im Topic findet die Einheit nicht (Mandanten-Isolation wie FR-4). */
    @Test
    void handle_GplugFremdeOrgId_Discarded() {
        when(einheitRepository.findAllByOrgIdAndMesspunkt(999L, MESSPUNKT)).thenReturn(List.of());

        service.handle("gPlug/999/MP-001/tele/SENSOR", gplug("2026-10-20T14:30:00", "\"Ei\":1.0"));

        verify(einheitRepository).findAllByOrgIdAndMesspunkt(999L, MESSPUNKT);
        verify(metrics).recordFailed();
        verify(rohdatenRepository, never()).save(any());
    }

    // ==================== Messpunkt-Empfang: letzte Meldung (Specs/Messpunkt-Empfang.md, FR-1.9) ====================

    /** Fängt den einzigen festgehaltenen Empfangsvermerk ab (ohne Transaktion: direkt festgehalten). */
    private MesspunktEmpfangService.Meldung captureEmpfang() {
        ArgumentCaptor<MesspunktEmpfangService.Meldung> captor =
                ArgumentCaptor.forClass(MesspunktEmpfangService.Meldung.class);
        verify(messpunktEmpfangService).halteFest(captor.capture());
        return captor.getValue();
    }

    @Test
    void handle_GueltigeMeldung_HaeltPayloadFest() {
        stubEinheitOhneBestand();
        String payload = payload("2026-01-01T10:07:00+01:00", "123.4500", "10.0000");

        service.handle(TOPIC, payload);

        MesspunktEmpfangService.Meldung meldung = captureEmpfang();
        assertEquals(MesspunktEmpfangErgebnis.GESPEICHERT, meldung.ergebnis());
        assertEquals(payload, meldung.payload());
    }

    @Test
    void handle_UnlesbarerPayload_HaeltPayloadTrotzdemFest() {
        // Gerade der unlesbare Payload ist der, den man sehen will
        service.handle(TOPIC, "kein-json");

        MesspunktEmpfangService.Meldung meldung = captureEmpfang();
        assertEquals(MesspunktEmpfangErgebnis.UNGUELTIG, meldung.ergebnis());
        assertEquals(MqttIngestService.GRUND_PAYLOAD_NICHT_LESBAR, meldung.grund());
        assertEquals("kein-json", meldung.payload());
    }

    // ==================== Messpunkt-Empfang: Ergebnis und Grund (Specs/Messpunkt-Empfang.md, FR-1.3 bis FR-1.6) ====================

    private static void assertStand(String erwartet, BigDecimal ist) {
        assertNotNull(ist, "Stand erwartet: " + erwartet);
        assertEquals(0, new BigDecimal(erwartet).compareTo(ist), "Stand " + ist + " statt " + erwartet);
    }

    private static void assertVerworfen(MesspunktEmpfangService.Meldung meldung, String grund) {
        assertEquals(MesspunktEmpfangErgebnis.UNGUELTIG, meldung.ergebnis());
        assertEquals(grund, meldung.grund());
    }

    // --- Pi-Gateway ---

    @Test
    void handle_GueltigeMeldung_VermerkGespeichertMitQuelleUndStaenden() {
        stubEinheitOhneBestand();

        service.handle(TOPIC, payload("2026-01-01T10:07:00+01:00", "123.4500", "10.0000"));

        MesspunktEmpfangService.Meldung meldung = captureEmpfang();
        assertEquals(ORG_ID, meldung.orgId());
        assertEquals(MESSPUNKT, meldung.messpunkt());
        assertEquals(MesspunktEmpfangQuelle.PI_GATEWAY, meldung.quelle());
        assertEquals(MesspunktEmpfangErgebnis.GESPEICHERT, meldung.ergebnis());
        assertNull(meldung.grund());
        assertStand("123.4500", meldung.bezug());
        assertStand("10.0000", meldung.einspeisung());
    }

    @Test
    void handle_KeinJson_VermerkPayloadNichtLesbarOhneStaende() {
        service.handle(TOPIC, "{kein json");

        MesspunktEmpfangService.Meldung meldung = captureEmpfang();
        assertVerworfen(meldung, MqttIngestService.GRUND_PAYLOAD_NICHT_LESBAR);
        assertNull(meldung.bezug());
        assertNull(meldung.einspeisung());
        verify(einheitRepository, never()).findAllByOrgIdAndMesspunkt(anyLong(), any());
    }

    @Test
    void handle_LeererPayload_VermerkPayloadNichtLesbar() {
        service.handle(TOPIC, "");

        MesspunktEmpfangService.Meldung meldung = captureEmpfang();
        assertVerworfen(meldung, MqttIngestService.GRUND_PAYLOAD_NICHT_LESBAR);
        assertEquals("", meldung.payload());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"timestamp\":\"abc\",\"zaehlerstandBezug\":1.0,\"zaehlerstandEinspeisung\":0.0}",
            "{\"timestamp\":\"2026-01-01T10:07:00+01:00\",\"zaehlerstandBezug\":\"abc\",\"zaehlerstandEinspeisung\":0.0}",
            "{\"timestamp\":\"2026-01-01T10:07:00+01:00\",\"zaehlerstandBezug\":{\"x\":1},\"zaehlerstandEinspeisung\":0.0}"
    })
    void handle_FeldVomFalschenTyp_VermerkPayloadNichtLesbar(String payload) {
        service.handle(TOPIC, payload);

        MesspunktEmpfangService.Meldung meldung = captureEmpfang();
        assertVerworfen(meldung, MqttIngestService.GRUND_PAYLOAD_NICHT_LESBAR);
        assertNull(meldung.bezug());
        assertNull(meldung.einspeisung());
    }

    @Test
    void handle_FalscherTypUndPflichtfeldFehlt_LesbarkeitZuerst() {
        // Pruefreihenfolge #1 vor #2: Die unlesbare Zeit entscheidet, nicht die fehlenden Staende
        service.handle(TOPIC, "{\"timestamp\":\"abc\"}");

        assertVerworfen(captureEmpfang(), MqttIngestService.GRUND_PAYLOAD_NICHT_LESBAR);
    }

    @Test
    void handle_OhneTimestamp_VermerkPflichtfeldFehltMitLesbarenStaenden() {
        service.handle(TOPIC, payload(null, "1.5", "2.0"));

        MesspunktEmpfangService.Meldung meldung = captureEmpfang();
        assertVerworfen(meldung, MqttIngestService.GRUND_PFLICHTFELD_FEHLT);
        assertStand("1.5", meldung.bezug());
        assertStand("2.0", meldung.einspeisung());
    }

    @Test
    void handle_OhneEinspeisung_VermerkPflichtfeldFehltEinspeisungNull() {
        service.handle(TOPIC, payload("2026-01-01T10:07:00Z", "1.5", null));

        MesspunktEmpfangService.Meldung meldung = captureEmpfang();
        assertVerworfen(meldung, MqttIngestService.GRUND_PFLICHTFELD_FEHLT);
        assertStand("1.5", meldung.bezug());
        assertNull(meldung.einspeisung());
    }

    @Test
    void handle_PflichtfeldFehltUndNegativ_PflichtfeldZuerst() {
        // Pruefreihenfolge #2 vor #5
        service.handle(TOPIC, payload(null, "-1.0", "2.0"));

        assertVerworfen(captureEmpfang(), MqttIngestService.GRUND_PFLICHTFELD_FEHLT);
    }

    @Test
    void handle_NegativerStand_VermerkNegativMitNegativemStand() {
        service.handle(TOPIC, payload("2026-01-01T10:07:00Z", "-5.0", "0"));

        MesspunktEmpfangService.Meldung meldung = captureEmpfang();
        assertVerworfen(meldung, MqttIngestService.GRUND_NEGATIV);
        assertStand("-5.0", meldung.bezug());
        assertStand("0", meldung.einspeisung());
        verify(einheitRepository, never()).findAllByOrgIdAndMesspunkt(anyLong(), any());
    }

    @Test
    void handle_UnbekannterMesspunkt_VermerkUnbekanntMitStaendenOhneRohdaten() {
        when(einheitRepository.findAllByOrgIdAndMesspunkt(ORG_ID, MESSPUNKT)).thenReturn(List.of());

        service.handle(TOPIC, payload("2026-01-01T10:07:00Z", "1.0", "0.5"));

        MesspunktEmpfangService.Meldung meldung = captureEmpfang();
        assertEquals(MesspunktEmpfangErgebnis.UNBEKANNTER_MESSPUNKT, meldung.ergebnis());
        assertNull(meldung.grund());
        assertStand("1.0", meldung.bezug());
        assertStand("0.5", meldung.einspeisung());
        verify(rohdatenRepository, never()).save(any());
    }

    @Test
    void handle_UngueltigerPayloadUnbekannterMesspunkt_VermerkUngueltig() {
        // Erst eine gueltige Meldung sagt, ob der Messpunkt passt (Pruefreihenfolge #1 vor #6)
        service.handle("zev/100/Fremd/messwert", "kein-json");

        MesspunktEmpfangService.Meldung meldung = captureEmpfang();
        assertEquals("Fremd", meldung.messpunkt());
        assertVerworfen(meldung, MqttIngestService.GRUND_PAYLOAD_NICHT_LESBAR);
        verify(einheitRepository, never()).findAllByOrgIdAndMesspunkt(anyLong(), any());
    }

    @Test
    void handle_NurLadestationAmMesspunkt_VermerkUnbekannterMesspunkt() {
        when(einheitRepository.findAllByOrgIdAndMesspunkt(ORG_ID, MESSPUNKT))
                .thenReturn(List.of(ladestation(900L)));

        service.handle(TOPIC, payload("2026-01-01T10:07:00Z", "1.0", "0.0"));

        assertEquals(MesspunktEmpfangErgebnis.UNBEKANNTER_MESSPUNKT, captureEmpfang().ergebnis());
    }

    @Test
    void handle_AllgemeinesCatch_VermerkVerarbeitungFehlgeschlagenNichtGespeichert() {
        when(einheitRepository.findAllByOrgIdAndMesspunkt(ORG_ID, MESSPUNKT)).thenReturn(List.of(einheit));
        when(rohdatenRepository.findByEinheitIdAndZeit(eq(EINHEIT_ID), any())).thenReturn(Optional.empty());
        when(rohdatenRepository.save(any(ZaehlerRohdaten.class))).thenThrow(new RuntimeException("DB down"));

        service.handle(TOPIC, payload("2026-01-01T10:07:00Z", "7.0", "3.0"));

        MesspunktEmpfangService.Meldung meldung = captureEmpfang();
        assertVerworfen(meldung, MqttIngestService.GRUND_VERARBEITUNG);
        assertStand("7.0", meldung.bezug());
        assertStand("3.0", meldung.einspeisung());
    }

    // --- Topic nicht lesbar: kein Vermerk (FR-1.5) ---

    @ParameterizedTest
    @ValueSource(strings = {
            "zev/100/MP-001",               // zu wenige Segmente
            "zev/abc/MP-001/messwert",      // Mandant keine Zahl
            "zev/100//messwert",            // leerer Messpunkt
            "gPlug/100//tele/SENSOR",       // leerer Messpunkt (gPlug)
            "gPlug/abc/MP-001/tele/SENSOR"  // Mandant keine Zahl (gPlug)
    })
    void handle_TopicNichtLesbar_KeinVermerk(String topic) {
        service.handle(topic, payload("2026-01-01T10:07:00Z", "1.0", "0.0"));

        verify(messpunktEmpfangService, never()).halteFest(any());
    }

    @Test
    void handle_OhneTopic_KeinVermerk() {
        service.handle(null, payload("2026-01-01T10:07:00Z", "1.0", "0.0"));

        verify(messpunktEmpfangService, never()).halteFest(any());
    }

    @Test
    void handle_MesspunktUeber255Zeichen_KeinVermerk() {
        String messpunkt = "m".repeat(256);

        service.handle("zev/100/" + messpunkt + "/messwert", payload("2026-01-01T10:07:00Z", "1.0", "0.0"));

        verify(messpunktEmpfangService, never()).halteFest(any());
    }

    @Test
    void handle_MesspunktGenau255Zeichen_Vermerk() {
        String messpunkt = "m".repeat(255);

        service.handle("zev/100/" + messpunkt + "/messwert", payload("2026-01-01T10:07:00Z", "1.0", "0.0"));

        assertEquals(messpunkt, captureEmpfang().messpunkt());
    }

    // --- Festhalten nach dem Ende der Transaktion (FR-1.6) ---

    @Test
    void handle_FesthaltenWirft_RohdatenGespeichertKeineException() {
        stubEinheitOhneBestand();
        doThrow(new RuntimeException("Upsert kaputt")).when(messpunktEmpfangService).halteFest(any());

        assertDoesNotThrow(() ->
                service.handle(TOPIC, payload("2026-01-01T10:07:00Z", "1.0", "0.0")));

        verify(rohdatenRepository).save(any(ZaehlerRohdaten.class));
        verify(metrics).recordProcessed();
    }

    @Test
    void handle_MitTransaktion_HaeltErstNachAbschlussFest() {
        stubEinheitOhneBestand();
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.handle(TOPIC, payload("2026-01-01T10:07:00Z", "1.0", "0.0"));

            verify(messpunktEmpfangService, never()).halteFest(any());
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertEquals(MesspunktEmpfangErgebnis.GESPEICHERT, captureEmpfang().ergebnis());
    }

    @Test
    void handle_MitTransaktionRollback_VermerkSpeichernFehlgeschlagen() {
        stubEinheitOhneBestand();
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.handle(TOPIC, payload("2026-01-01T10:07:00Z", "1.0", "0.0"));

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        MesspunktEmpfangService.Meldung meldung = captureEmpfang();
        assertVerworfen(meldung, MqttIngestService.GRUND_SPEICHERN);
        assertStand("1.0", meldung.bezug());
    }

    // --- gPlug ---

    @Test
    void handle_GplugGueltig_VermerkGespeichertQuelleGplug() {
        stubEinheitOhneBestand();

        service.handle(GPLUG_TOPIC, gplug("2026-10-20T14:30:00", GPLUG_Z_VOLL));

        MesspunktEmpfangService.Meldung meldung = captureEmpfang();
        assertEquals(MesspunktEmpfangQuelle.GPLUG, meldung.quelle());
        assertEquals(MesspunktEmpfangErgebnis.GESPEICHERT, meldung.ergebnis());
        assertStand("12345.678", meldung.bezug());
        assertStand("4321.000", meldung.einspeisung());
    }

    @Test
    void handle_GplugOhneEo_VermerkEinspeisungNullRohdatenNull() {
        stubEinheitOhneBestand();

        service.handle(GPLUG_TOPIC, gplug("2026-10-20T14:30:00", "\"Ei\":12345.678"));

        MesspunktEmpfangService.Meldung meldung = captureEmpfang();
        assertEquals(MesspunktEmpfangErgebnis.GESPEICHERT, meldung.ergebnis());
        // So wie gemeldet: NULL im Vermerk, 0 in den Rohdaten (FR-1.4)
        assertNull(meldung.einspeisung());
        assertEquals(0, BigDecimal.ZERO.compareTo(captureSavedRohdaten().getZaehlerstandEinspeisung()));
    }

    @Test
    void handle_GplugKeinJson_VermerkPayloadNichtLesbar() {
        service.handle(GPLUG_TOPIC, "kein-json");

        MesspunktEmpfangService.Meldung meldung = captureEmpfang();
        assertEquals(MesspunktEmpfangQuelle.GPLUG, meldung.quelle());
        assertVerworfen(meldung, MqttIngestService.GRUND_PAYLOAD_NICHT_LESBAR);
        assertNull(meldung.bezug());
        assertNull(meldung.einspeisung());
    }

    @Test
    void handle_GplugUnlesbareTime_VermerkPayloadNichtLesbarMitStaenden() {
        service.handle(GPLUG_TOPIC, gplug("abc", "\"Ei\":1.5,\"Eo\":2.5"));

        MesspunktEmpfangService.Meldung meldung = captureEmpfang();
        assertVerworfen(meldung, MqttIngestService.GRUND_PAYLOAD_NICHT_LESBAR);
        // Die Staende liessen sich lesen und stehen im Vermerk (FR-1.4)
        assertStand("1.5", meldung.bezug());
        assertStand("2.5", meldung.einspeisung());
    }

    @Test
    void handle_GplugUnlesbareTimeOhneEi_LesbarkeitVorVollstaendigkeit() {
        service.handle(GPLUG_TOPIC, gplug("abc", "\"Eo\":2.5"));

        assertVerworfen(captureEmpfang(), MqttIngestService.GRUND_PAYLOAD_NICHT_LESBAR);
    }

    @Test
    void handle_GplugOhneTime_VermerkPflichtfeldFehlt() {
        service.handle(GPLUG_TOPIC, gplug(null, "\"Ei\":1.5"));

        MesspunktEmpfangService.Meldung meldung = captureEmpfang();
        assertVerworfen(meldung, MqttIngestService.GRUND_PFLICHTFELD_FEHLT);
        assertStand("1.5", meldung.bezug());
    }

    @Test
    void handle_GplugOhneEi_VermerkPflichtfeldFehltBezugNull() {
        service.handle(GPLUG_TOPIC, gplug("2026-10-20T14:30:00", "\"Eo\":4321.0"));

        MesspunktEmpfangService.Meldung meldung = captureEmpfang();
        assertVerworfen(meldung, MqttIngestService.GRUND_PFLICHTFELD_FEHLT);
        assertNull(meldung.bezug());
        assertStand("4321.0", meldung.einspeisung());
    }

    @Test
    void handle_GplugUhrNichtSynchronisiert_VermerkGplugUhr() {
        service.handle(GPLUG_TOPIC, gplug("1970-01-01T00:05:12", "\"Ei\":12345.678"));

        MesspunktEmpfangService.Meldung meldung = captureEmpfang();
        assertVerworfen(meldung, MqttIngestService.GRUND_GPLUG_UHR);
        assertStand("12345.678", meldung.bezug());
    }

    @Test
    void handle_GplugUhrNichtSynchronisiertUndNullen_UhrZuerst() {
        // Pruefreihenfolge #3 vor #4
        service.handle(GPLUG_TOPIC, gplug("1970-01-01T00:05:12", "\"Ei\":0,\"Eo\":0"));

        assertVerworfen(captureEmpfang(), MqttIngestService.GRUND_GPLUG_UHR);
    }

    @Test
    void handle_GplugEiUndEoNull_VermerkEiEoNullMitNullStaenden() {
        service.handle(GPLUG_TOPIC, gplug("2026-10-20T14:30:00", "\"Ei\":0,\"Eo\":0"));

        MesspunktEmpfangService.Meldung meldung = captureEmpfang();
        assertVerworfen(meldung, MqttIngestService.GRUND_EI_EO_NULL);
        assertStand("0", meldung.bezug());
        assertStand("0", meldung.einspeisung());
    }

    @Test
    void handle_GplugEiNullOhneEo_VermerkEiEoNullEinspeisungNull() {
        service.handle(GPLUG_TOPIC, gplug("2026-10-20T14:30:00", "\"Ei\":0"));

        MesspunktEmpfangService.Meldung meldung = captureEmpfang();
        assertVerworfen(meldung, MqttIngestService.GRUND_EI_EO_NULL);
        assertNull(meldung.einspeisung());
    }

    @Test
    void handle_GplugNegativerStand_VermerkNegativ() {
        // Einspeisung 0, aber Bezug nicht: kein "Ei = Eo = 0", sondern negativ
        service.handle(GPLUG_TOPIC, gplug("2026-10-20T14:30:00", "\"Ei\":-3.0,\"Eo\":0"));

        MesspunktEmpfangService.Meldung meldung = captureEmpfang();
        assertVerworfen(meldung, MqttIngestService.GRUND_NEGATIV);
        assertStand("-3.0", meldung.bezug());
    }

    @Test
    void handle_GplugUnbekannterMesspunkt_VermerkUnbekannt() {
        when(einheitRepository.findAllByOrgIdAndMesspunkt(ORG_ID, "Fremd")).thenReturn(List.of());

        service.handle("gPlug/100/Fremd/tele/SENSOR", gplug("2026-10-20T14:30:00", "\"Ei\":1.0"));

        MesspunktEmpfangService.Meldung meldung = captureEmpfang();
        assertEquals(MesspunktEmpfangQuelle.GPLUG, meldung.quelle());
        assertEquals(MesspunktEmpfangErgebnis.UNBEKANNTER_MESSPUNKT, meldung.ergebnis());
    }

    // --- FR-1.3 #1: Lesbarkeit vor Vollstaendigkeit (Befunde aus /3 vom 25.10.2026, behoben) ---

    /**
     * FR-1.3 #1 verlangt fuer ein Feld vom falschen Typ {@code Payload nicht lesbar}. Zuvor lieferte
     * der gPlug-Ingest {@code Pflichtfeld fehlt}: Er pruefte nur {@code isTextual()} bzw.
     * {@code isNumber()} und behandelte einen Knoten vom falschen Typ wie einen fehlenden.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "{\"Time\":\"2026-10-20T14:30:00\",\"z\":{\"Ei\":\"abc\"}}",
            "{\"Time\":12345,\"z\":{\"Ei\":1.5}}"
    })
    void handle_GplugFeldVomFalschenTyp_VermerkPayloadNichtLesbar(String payload) {
        service.handle(GPLUG_TOPIC, payload);

        assertVerworfen(captureEmpfang(), MqttIngestService.GRUND_PAYLOAD_NICHT_LESBAR);
    }

    /**
     * Gueltiges JSON, aber kein Objekt: {@code readValue("null")} liefert {@code null}. Zuvor warf der
     * Zugriff auf die Staende eine NullPointerException, die erst das allgemeine catch fing
     * ({@code Verarbeitung fehlgeschlagen}). Nach FR-1.3 #1 ist der Payload nicht lesbar.
     */
    @Test
    void handle_PayloadJsonNull_VermerkPayloadNichtLesbar() {
        service.handle(TOPIC, "null");

        assertVerworfen(captureEmpfang(), MqttIngestService.GRUND_PAYLOAD_NICHT_LESBAR);
    }
}
