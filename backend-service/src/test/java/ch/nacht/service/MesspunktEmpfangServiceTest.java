package ch.nacht.service;

import ch.nacht.dto.MesspunktEmpfangDTO;
import ch.nacht.dto.MesspunktEmpfangMeldungDTO;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.MesspunktEmpfang;
import ch.nacht.entity.MesspunktEmpfangErgebnis;
import ch.nacht.entity.MesspunktEmpfangQuelle;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.MesspunktEmpfangRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit-Tests für {@link MesspunktEmpfangService} (Specs/Messpunkt-Empfang.md): Liste (FR-2.2,
 * FR-2.5), letzte Meldung (FR-1.9, FR-2.9), Löschen und Aufräumen (FR-3).
 */
@ExtendWith(MockitoExtension.class)
public class MesspunktEmpfangServiceTest {

    private static final Instant JETZT = Instant.parse("2026-10-25T09:00:00Z");

    @Mock
    private MesspunktEmpfangRepository messpunktEmpfangRepository;

    @Mock
    private EinheitRepository einheitRepository;

    @Mock
    private HibernateFilterService hibernateFilterService;

    private MesspunktEmpfangService messpunktEmpfangService;

    private MesspunktEmpfang testEmpfang1;
    private MesspunktEmpfang testEmpfang2;

    @BeforeEach
    void setUp() {
        messpunktEmpfangService = new MesspunktEmpfangService(messpunktEmpfangRepository, einheitRepository,
                hibernateFilterService, Clock.fixed(JETZT, ZoneOffset.UTC));

        testEmpfang1 = new MesspunktEmpfang();
        testEmpfang1.setId(1L);
        testEmpfang1.setOrgId(100L);
        testEmpfang1.setMesspunkt("MP-001");
        testEmpfang1.setLetzteMeldung("{\"zaehlerstandBezug\":1.5}");

        testEmpfang2 = new MesspunktEmpfang();
        testEmpfang2.setId(2L);
        testEmpfang2.setOrgId(100L);
        testEmpfang2.setMesspunkt("MP-002");
        // Zeile von vor V188: kein Payload
        testEmpfang2.setLetzteMeldung(null);
    }

    @Test
    void getLetzteMeldung_Found_ReturnsPayload() {
        when(messpunktEmpfangRepository.findFirstById(1L)).thenReturn(Optional.of(testEmpfang1));

        Optional<MesspunktEmpfangMeldungDTO> result = messpunktEmpfangService.getLetzteMeldung(1L);

        assertTrue(result.isPresent());
        assertEquals("{\"zaehlerstandBezug\":1.5}", result.get().getPayload());
        verify(hibernateFilterService).enableOrgFilter();
    }

    @Test
    void getLetzteMeldung_OhnePayload_ReturnsNullPayload() {
        when(messpunktEmpfangRepository.findFirstById(2L)).thenReturn(Optional.of(testEmpfang2));

        Optional<MesspunktEmpfangMeldungDTO> result = messpunktEmpfangService.getLetzteMeldung(2L);

        assertTrue(result.isPresent());
        assertNull(result.get().getPayload());
    }

    @Test
    void getLetzteMeldung_NotFound_ReturnsEmpty() {
        // findFirstById statt findById: Eine fremde ID ist unter dem orgFilter nicht zu finden
        when(messpunktEmpfangRepository.findFirstById(99L)).thenReturn(Optional.empty());

        Optional<MesspunktEmpfangMeldungDTO> result = messpunktEmpfangService.getLetzteMeldung(99L);

        assertTrue(result.isEmpty());
        verify(hibernateFilterService).enableOrgFilter();
        verify(messpunktEmpfangRepository, never()).findById(any());
    }

    @Test
    void deleteMesspunktEmpfang_Exists_ReturnsTrue() {
        when(messpunktEmpfangRepository.findFirstById(1L)).thenReturn(Optional.of(testEmpfang1));

        boolean result = messpunktEmpfangService.deleteMesspunktEmpfang(1L);

        assertTrue(result);
        verify(hibernateFilterService).enableOrgFilter();
        verify(messpunktEmpfangRepository).delete(testEmpfang1);
    }

    @Test
    void deleteMesspunktEmpfang_NotFound_ReturnsFalse() {
        // findFirstById statt findById: Eine fremde ID ist unter dem orgFilter nicht zu finden
        when(messpunktEmpfangRepository.findFirstById(99L)).thenReturn(Optional.empty());

        boolean result = messpunktEmpfangService.deleteMesspunktEmpfang(99L);

        assertFalse(result);
        verify(hibernateFilterService).enableOrgFilter();
        verify(messpunktEmpfangRepository, never()).findById(any());
        verify(messpunktEmpfangRepository, never()).delete(any());
    }

    @Test
    void halteFest_UebergibtPayloadAnUpsert() {
        messpunktEmpfangService.halteFest(meldung("{\"a\":1}"));

        verify(messpunktEmpfangRepository).upsert(eq(100L), eq("MP-001"), eq("PI_GATEWAY"), eq(JETZT),
                any(), any(), eq("GESPEICHERT"), isNull(), eq("{\"a\":1}"));
    }

    @Test
    void halteFest_PayloadUeber10000Zeichen_WirdGekuerztMitHinweis() {
        String lang = "x".repeat(12_000);

        messpunktEmpfangService.halteFest(meldung(lang));

        verify(messpunktEmpfangRepository).upsert(anyLong(), anyString(), anyString(), any(), any(), any(),
                anyString(), isNull(), argThat(gespeichert -> gespeichert.length() == 10_000
                        && gespeichert.endsWith(MesspunktEmpfangService.GEKUERZT)));
    }

    @Test
    void kuerze_Genau10000Zeichen_Unveraendert() {
        String genau = "y".repeat(10_000);

        assertSame(genau, MesspunktEmpfangService.kuerze(genau));
    }

    @Test
    void kuerze_Null_BleibtNull() {
        assertNull(MesspunktEmpfangService.kuerze(null));
    }

    // ==================== getAllMesspunktEmpfang (FR-2.2, FR-2.3, FR-2.5) ====================

    private static Einheit einheit(String name, EinheitTyp typ, String messpunkt) {
        Einheit einheit = new Einheit(name, typ);
        einheit.setOrgId(100L);
        einheit.setMesspunkt(messpunkt);
        return einheit;
    }

    private static MesspunktEmpfang empfang(long id, String messpunkt, Instant letzter) {
        MesspunktEmpfang empfang = new MesspunktEmpfang();
        empfang.setId(id);
        empfang.setOrgId(100L);
        empfang.setMesspunkt(messpunkt);
        empfang.setQuelle(MesspunktEmpfangQuelle.PI_GATEWAY);
        empfang.setErsterEmpfang(letzter.minusSeconds(3600));
        empfang.setLetzterEmpfang(letzter);
        empfang.setAnzahlMeldungen(60);
        empfang.setZaehlerstandBezug(new BigDecimal("1.5"));
        empfang.setErgebnis(MesspunktEmpfangErgebnis.GESPEICHERT);
        return empfang;
    }

    private static MesspunktEmpfangDTO zeile(List<MesspunktEmpfangDTO> zeilen, String messpunkt) {
        return zeilen.stream().filter(z -> messpunkt.equals(z.getMesspunkt())).findFirst()
                .orElseThrow(() -> new AssertionError("Keine Zeile fuer " + messpunkt));
    }

    @Test
    void getAllMesspunktEmpfang_EmpfangUndEinheiten_ErgibtVereinigung() {
        Instant letzter = JETZT.minusSeconds(120);
        when(einheitRepository.findAllByOrderByNameAsc()).thenReturn(List.of(
                einheit("Bezug", EinheitTyp.BEZUG, "MP-BIL"),
                einheit("Ladestation", EinheitTyp.LADESTATION, "RFID-1"),
                einheit("Ladestation am Zaehler", EinheitTyp.LADESTATION, "MP-001"),
                einheit("Leerer Messpunkt", EinheitTyp.CONSUMER, " "),
                einheit("Neu", EinheitTyp.CONSUMER, "MP-NEU"),
                einheit("Ohne Messpunkt", EinheitTyp.CONSUMER, null),
                einheit("Ruecklieferung", EinheitTyp.RUECKLIEFERUNG, "MP-BIL"),
                einheit("Wohnung A", EinheitTyp.CONSUMER, "MP-001")));
        when(messpunktEmpfangRepository.findAllByOrderByLetzterEmpfangDesc()).thenReturn(List.of(
                empfang(1L, "MP-001", letzter),
                empfang(2L, "MP-BIL", letzter),
                empfang(3L, "Tippfehler", letzter)));

        List<MesspunktEmpfangDTO> zeilen = messpunktEmpfangService.getAllMesspunktEmpfang();

        // Je Messpunkt genau eine Zeile; Einheiten ohne Messpunkt und Ladestationen erscheinen nicht
        assertEquals(List.of("MP-001", "MP-BIL", "Tippfehler", "MP-NEU"),
                zeilen.stream().map(MesspunktEmpfangDTO::getMesspunkt).toList());
        // Die Ladestation am selben Messpunkt zaehlt nicht als Einheit
        assertEquals(List.of("Wohnung A"), zeile(zeilen, "MP-001").getEinheiten());
        // Bilanzmesspunkt: eine Zeile, alle Einheiten
        assertEquals(List.of("Bezug", "Ruecklieferung"), zeile(zeilen, "MP-BIL").getEinheiten());
        assertEquals(List.of(), zeile(zeilen, "Tippfehler").getEinheiten());
        assertTrue(zeile(zeilen, "MP-NEU").isKeinEmpfang());
        verify(hibernateFilterService).enableOrgFilter();
        // Einheiten einmal je Aufruf, nicht je Zeile (NFR-1)
        verify(einheitRepository, times(1)).findAllByOrderByNameAsc();
    }

    @Test
    void getAllMesspunktEmpfang_EinheitOhneEmpfang_ZeileKeinEmpfangOhneEmpfangsfelder() {
        when(einheitRepository.findAllByOrderByNameAsc())
                .thenReturn(List.of(einheit("Neu", EinheitTyp.CONSUMER, "MP-NEU")));
        when(messpunktEmpfangRepository.findAllByOrderByLetzterEmpfangDesc()).thenReturn(List.of());

        List<MesspunktEmpfangDTO> zeilen = messpunktEmpfangService.getAllMesspunktEmpfang();

        assertEquals(1, zeilen.size());
        MesspunktEmpfangDTO zeile = zeilen.get(0);
        assertTrue(zeile.isKeinEmpfang());
        assertEquals(List.of("Neu"), zeile.getEinheiten());
        assertNull(zeile.getId());
        assertNull(zeile.getQuelle());
        assertNull(zeile.getErsterEmpfang());
        assertNull(zeile.getLetzterEmpfang());
        assertNull(zeile.getSekundenSeitEmpfang());
        assertNull(zeile.getAnzahlMeldungen());
        assertNull(zeile.getZaehlerstandBezug());
        assertNull(zeile.getZaehlerstandEinspeisung());
        assertNull(zeile.getErgebnis());
        assertNull(zeile.getGrund());
    }

    @Test
    void getAllMesspunktEmpfang_NurLadestationAmMesspunkt_KeineKeinEmpfangZeile() {
        when(einheitRepository.findAllByOrderByNameAsc())
                .thenReturn(List.of(einheit("Ladestation", EinheitTyp.LADESTATION, "RFID-1")));
        when(messpunktEmpfangRepository.findAllByOrderByLetzterEmpfangDesc()).thenReturn(List.of());

        assertTrue(messpunktEmpfangService.getAllMesspunktEmpfang().isEmpty());
    }

    @Test
    void getAllMesspunktEmpfang_ZeileMitEmpfang_FelderUndOrtszeit() {
        MesspunktEmpfang empfang = empfang(1L, "MP-001", Instant.parse("2026-07-01T10:00:00Z"));
        empfang.setErgebnis(MesspunktEmpfangErgebnis.UNGUELTIG);
        empfang.setGrund("negativer Zählerstand");
        empfang.setZaehlerstandEinspeisung(new BigDecimal("-2"));
        when(einheitRepository.findAllByOrderByNameAsc()).thenReturn(List.of());
        when(messpunktEmpfangRepository.findAllByOrderByLetzterEmpfangDesc()).thenReturn(List.of(empfang));

        MesspunktEmpfangDTO zeile = messpunktEmpfangService.getAllMesspunktEmpfang().get(0);

        assertFalse(zeile.isKeinEmpfang());
        assertEquals(1L, zeile.getId());
        assertEquals(MesspunktEmpfangQuelle.PI_GATEWAY, zeile.getQuelle());
        // Sommerzeit: 10:00 UTC = 12:00 Ortszeit
        assertEquals(LocalDateTime.of(2026, 7, 1, 12, 0), zeile.getLetzterEmpfang());
        assertEquals(LocalDateTime.of(2026, 7, 1, 11, 0), zeile.getErsterEmpfang());
        assertEquals(60L, zeile.getAnzahlMeldungen());
        assertEquals(new BigDecimal("1.5"), zeile.getZaehlerstandBezug());
        assertEquals(new BigDecimal("-2"), zeile.getZaehlerstandEinspeisung());
        assertEquals(MesspunktEmpfangErgebnis.UNGUELTIG, zeile.getErgebnis());
        assertEquals("negativer Zählerstand", zeile.getGrund());
    }

    @Test
    void getAllMesspunktEmpfang_UeberWinterzeitUmstellung_AlterAusAbsolutenZeitpunkten() {
        // 02:59 Sommerzeit (00:59 UTC) bis 02:10 Winterzeit (01:10 UTC): 11 Minuten, nicht -49
        Instant jetzt = Instant.parse("2026-10-25T01:10:00Z");
        MesspunktEmpfangService service = new MesspunktEmpfangService(messpunktEmpfangRepository,
                einheitRepository, hibernateFilterService, Clock.fixed(jetzt, ZoneOffset.UTC));
        when(einheitRepository.findAllByOrderByNameAsc()).thenReturn(List.of());
        when(messpunktEmpfangRepository.findAllByOrderByLetzterEmpfangDesc())
                .thenReturn(List.of(empfang(1L, "MP-001", Instant.parse("2026-10-25T00:59:00Z"))));

        MesspunktEmpfangDTO zeile = service.getAllMesspunktEmpfang().get(0);

        assertEquals(660L, zeile.getSekundenSeitEmpfang());
        assertEquals(LocalDateTime.of(2026, 10, 25, 2, 59), zeile.getLetzterEmpfang());
    }

    @Test
    void getAllMesspunktEmpfang_LetzterEmpfangInDerZukunft_AlterAufNullGeklemmt() {
        // Serveruhr zurueckgestellt: der letzte Empfang liegt 3 Minuten nach "jetzt"
        when(einheitRepository.findAllByOrderByNameAsc()).thenReturn(List.of());
        when(messpunktEmpfangRepository.findAllByOrderByLetzterEmpfangDesc())
                .thenReturn(List.of(empfang(1L, "MP-001", JETZT.plusSeconds(180))));

        assertEquals(0L, messpunktEmpfangService.getAllMesspunktEmpfang().get(0).getSekundenSeitEmpfang());
    }

    @Test
    void getAllMesspunktEmpfang_Alter_SekundenSeitLetztemEmpfang() {
        when(einheitRepository.findAllByOrderByNameAsc()).thenReturn(List.of());
        when(messpunktEmpfangRepository.findAllByOrderByLetzterEmpfangDesc())
                .thenReturn(List.of(empfang(1L, "MP-001", JETZT.minusSeconds(3601))));

        assertEquals(3601L, messpunktEmpfangService.getAllMesspunktEmpfang().get(0).getSekundenSeitEmpfang());
    }

    @Test
    void getAllMesspunktEmpfang_EinheitErstNachEmpfangAngelegt_ZeigtAktuelleZuordnung() {
        // Die Zeile traegt keine Einheiten: die Zuordnung entsteht beim Lesen (FR-1.7)
        when(einheitRepository.findAllByOrderByNameAsc())
                .thenReturn(List.of(einheit("Spaet angelegt", EinheitTyp.CONSUMER, "MP-001")));
        MesspunktEmpfang empfang = empfang(1L, "MP-001", JETZT.minusSeconds(60));
        empfang.setErgebnis(MesspunktEmpfangErgebnis.UNBEKANNTER_MESSPUNKT);
        when(messpunktEmpfangRepository.findAllByOrderByLetzterEmpfangDesc()).thenReturn(List.of(empfang));

        MesspunktEmpfangDTO zeile = messpunktEmpfangService.getAllMesspunktEmpfang().get(0);

        assertEquals(List.of("Spaet angelegt"), zeile.getEinheiten());
        // Das Ergebnis aendert sich erst mit der naechsten Meldung
        assertEquals(MesspunktEmpfangErgebnis.UNBEKANNTER_MESSPUNKT, zeile.getErgebnis());
    }

    @Test
    void halteFest_ZeitpunktIstBackendUhr() {
        messpunktEmpfangService.halteFest(meldung("{}"));

        // Die Meldung traegt keine Zeit - festgehalten wird die Uhr des Backends (FR-1.8)
        verify(messpunktEmpfangRepository).upsert(anyLong(), anyString(), anyString(), eq(JETZT), any(), any(),
                anyString(), any(), any());
    }

    // ==================== raeumeAuf (FR-3.2) ====================

    @Test
    void raeumeAuf_LoeschtVorJetztMinus30Tagen() {
        when(messpunktEmpfangRepository.loescheVerwaisteVor(any())).thenReturn(3);

        int geloescht = messpunktEmpfangService.raeumeAuf();

        assertEquals(3, geloescht);
        verify(messpunktEmpfangRepository).loescheVerwaisteVor(Instant.parse("2026-09-25T09:00:00Z"));
        // Ueber alle Mandanten: kein orgFilter
        verify(hibernateFilterService, never()).enableOrgFilter();
    }

    private MesspunktEmpfangService.Meldung meldung(String payload) {
        return new MesspunktEmpfangService.Meldung(100L, "MP-001", MesspunktEmpfangQuelle.PI_GATEWAY,
                new BigDecimal("1.5"), BigDecimal.ZERO, MesspunktEmpfangErgebnis.GESPEICHERT, null, payload);
    }
}
