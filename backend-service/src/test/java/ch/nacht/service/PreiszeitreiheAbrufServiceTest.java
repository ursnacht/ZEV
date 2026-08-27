package ch.nacht.service;

import ch.nacht.dto.PreiszeitreiheDownloadDTO;
import ch.nacht.entity.FeatureFlag;
import ch.nacht.entity.MeldungLevel;
import ch.nacht.entity.Preiszeitreihe;
import ch.nacht.exception.PreiszeitreiheQuelleException;
import ch.nacht.repository.PreiszeitreiheRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit-Tests für {@link PreiszeitreiheAbrufService} — die Beschaffung bei der Fremd-API.
 *
 * <p>Der HTTP-Aufruf läuft über {@link MockRestServiceServer}: Damit wird <b>echtes JSON</b>
 * deserialisiert und die Abbildung der {@code snake_case}-Felder der Quelle mitgeprüft. Ein
 * gemockter {@code RestClient} hätte genau diesen Teil — den fremden, unkontrollierten — nicht
 * geprüft.
 *
 * <p>Kein {@code HibernateFilterService}: Die Preiszeitreihe trägt kein {@code org_id} (FR-2).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PreiszeitreiheAbrufServiceTest {

    private static final String URL = "https://api.example.test/tariffs";
    private static final Long ORG_A = 1L;
    private static final Long ORG_B = 2L;

    @Mock
    private PreiszeitreiheRepository preiszeitreiheRepository;

    @Mock
    private FeatureFlagService featureFlagService;

    @Mock
    private SystemmeldungService systemmeldungService;

    @Captor
    private ArgumentCaptor<String> parameterCaptor;

    @Captor
    private ArgumentCaptor<LocalDateTime> zeitVonCaptor;

    private MockRestServiceServer server;
    private PreiszeitreiheAbrufService abrufService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        abrufService = new PreiszeitreiheAbrufService(preiszeitreiheRepository, featureFlagService,
                systemmeldungService, builder.build(), URL);

        when(featureFlagService.getOrgIdsMitAktivemFlag(FeatureFlag.PREISZEITREIHE))
                .thenReturn(List.of(ORG_A, ORG_B));
        when(preiszeitreiheRepository.findByZeitraum(any(), any())).thenReturn(List.of());
    }

    /** Antwort der Quelle im Format der BKW-API (zwei Viertelstunden). */
    private String zweiIntervalle() {
        return """
            {
              "publication_timestamp": "2026-08-27T13:50:00Z",
              "prices": [
                { "start_timestamp": "2026-08-26T22:00:00Z",
                  "end_timestamp": "2026-08-26T22:15:00Z",
                  "feed_in": [ { "unit": "CHF_kWh", "value": 0.138 } ] },
                { "start_timestamp": "2026-08-26T22:15:00Z",
                  "end_timestamp": "2026-08-26T22:30:00Z",
                  "feed_in": [ { "unit": "CHF_kWh", "value": 0.142 } ] }
              ]
            }
            """;
    }

    private void antwortet(String rumpf) {
        server.expect(requestTo(URL))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess(rumpf, MediaType.APPLICATION_JSON));
    }

    // ==================== Erfolgsfall ====================

    @Test
    void abrufen_ZweiIntervalle_SchreibtBeideUndZaehltSieAlsNeu() {
        antwortet(zweiIntervalle());

        PreiszeitreiheDownloadDTO ergebnis = abrufService.abrufen();

        assertEquals(2, ergebnis.abgerufen());
        assertEquals(2, ergebnis.neu());
        assertEquals(0, ergebnis.aktualisiert());
        assertEquals(0, ergebnis.uebersprungen());
        verify(preiszeitreiheRepository, times(2)).upsert(any(), any(), any(), any());
        server.verify();
    }

    @Test
    void abrufen_SchreibtZeitstempelUndPreisVerbatimInUtc() {
        antwortet(zweiIntervalle());

        abrufService.abrufen();

        verify(preiszeitreiheRepository).upsert(
                eq(LocalDateTime.of(2026, 8, 26, 22, 0)),
                eq(LocalDateTime.of(2026, 8, 26, 22, 15)),
                eq(new BigDecimal("0.138")),
                eq(LocalDateTime.of(2026, 8, 27, 13, 50)));
    }

    @Test
    void abrufen_LiefertPublikationInOrtszeit() {
        antwortet(zweiIntervalle());

        PreiszeitreiheDownloadDTO ergebnis = abrufService.abrufen();

        // 13:50 UTC im August = 15:50 Ortszeit (UTC+2).
        assertEquals(LocalDateTime.of(2026, 8, 27, 15, 50), ergebnis.publikation());
    }

    @Test
    void abrufen_BestehenderWert_ZaehltAlsAktualisiert() {
        antwortet(zweiIntervalle());
        Preiszeitreihe bestehend = new Preiszeitreihe(LocalDateTime.of(2026, 8, 26, 22, 0),
                LocalDateTime.of(2026, 8, 26, 22, 15), new BigDecimal("0.100"), null);
        when(preiszeitreiheRepository.findByZeitraum(any(), any())).thenReturn(List.of(bestehend));

        PreiszeitreiheDownloadDTO ergebnis = abrufService.abrufen();

        assertEquals(1, ergebnis.neu());
        assertEquals(1, ergebnis.aktualisiert());
    }

    /**
     * Aufsteigende Schreibreihenfolge: Job und Schaltfläche nehmen die Sperren dann in derselben
     * Reihenfolge und können sich nicht verklemmen (FR-5). Die Quelle liefert hier absichtlich
     * verkehrt herum.
     */
    @Test
    void abrufen_SchreibtInAufsteigenderZeitReihenfolge() {
        antwortet("""
            {
              "publication_timestamp": "2026-08-27T13:50:00Z",
              "prices": [
                { "start_timestamp": "2026-08-26T22:15:00Z",
                  "end_timestamp": "2026-08-26T22:30:00Z",
                  "feed_in": [ { "unit": "CHF_kWh", "value": 0.142 } ] },
                { "start_timestamp": "2026-08-26T22:00:00Z",
                  "end_timestamp": "2026-08-26T22:15:00Z",
                  "feed_in": [ { "unit": "CHF_kWh", "value": 0.138 } ] }
              ]
            }
            """);

        abrufService.abrufen();

        verify(preiszeitreiheRepository, times(2))
                .upsert(zeitVonCaptor.capture(), any(), any(), any());
        assertEquals(List.of(LocalDateTime.of(2026, 8, 26, 22, 0),
                LocalDateTime.of(2026, 8, 26, 22, 15)), zeitVonCaptor.getAllValues());
    }

    @Test
    void abrufen_OhnePublikationszeitpunkt_SpeichertTrotzdem() {
        antwortet("""
            {
              "prices": [
                { "start_timestamp": "2026-08-26T22:00:00Z",
                  "end_timestamp": "2026-08-26T22:15:00Z",
                  "feed_in": [ { "unit": "CHF_kWh", "value": 0.138 } ] }
              ]
            }
            """);

        PreiszeitreiheDownloadDTO ergebnis = abrufService.abrufen();

        assertEquals(1, ergebnis.neu());
        assertNull(ergebnis.publikation());
        verify(preiszeitreiheRepository).upsert(any(), any(), any(), eq(null));
    }

    @Test
    void abrufen_LeerePreisliste_IstKeinFehler() {
        antwortet("{ \"publication_timestamp\": \"2026-08-27T13:50:00Z\", \"prices\": [] }");

        PreiszeitreiheDownloadDTO ergebnis = abrufService.abrufen();

        assertEquals(0, ergebnis.abgerufen());
        verify(preiszeitreiheRepository, never()).upsert(any(), any(), any(), any());
        verify(systemmeldungService, never()).erfasse(anyLong(), any(), any(), any(), any());
    }

    @Test
    void abrufen_UnbekanntesFeldInAntwort_WirdIgnoriert() {
        antwortet("""
            {
              "publication_timestamp": "2026-08-27T13:50:00Z",
              "neues_feld_der_quelle": "egal",
              "prices": [
                { "start_timestamp": "2026-08-26T22:00:00Z",
                  "end_timestamp": "2026-08-26T22:15:00Z",
                  "noch_ein_feld": 42,
                  "feed_in": [ { "unit": "CHF_kWh", "value": 0.138 } ] }
              ]
            }
            """);

        assertEquals(1, abrufService.abrufen().neu());
    }

    /**
      * Negative Preise sind gültige Marktwerte (Überangebot) und werden <b>übernommen</b>, nicht
      * übersprungen. Ein Vorzeichen-Filter liesse die Reihe genau dort verstummen, wo sie am
      * meisten aussagt.
      */
     @Test
     void abrufen_NegativerUndNullPreis_WerdenUebernommen() {
         antwortet("""
             {
               "prices": [
                 { "start_timestamp": "2026-08-26T22:00:00Z",
                   "end_timestamp": "2026-08-26T22:15:00Z",
                   "feed_in": [ { "unit": "CHF_kWh", "value": -0.025 } ] },
                 { "start_timestamp": "2026-08-26T22:15:00Z",
                   "end_timestamp": "2026-08-26T22:30:00Z",
                   "feed_in": [ { "unit": "CHF_kWh", "value": 0 } ] }
               ]
             }
             """);

         PreiszeitreiheDownloadDTO ergebnis = abrufService.abrufen();

         assertEquals(2, ergebnis.neu());
         assertEquals(0, ergebnis.uebersprungen());
         verify(preiszeitreiheRepository).upsert(eq(LocalDateTime.of(2026, 8, 26, 22, 0)),
                 any(), eq(new BigDecimal("-0.025")), any());
         verify(preiszeitreiheRepository).upsert(eq(LocalDateTime.of(2026, 8, 26, 22, 15)),
                 any(), eq(new BigDecimal("0")), any());
     }

     // ==================== Übersprungene Intervalle ====================

    @Test
    void abrufen_LeeresFeedIn_UeberspringtOhneNullPreis() {
        antwortet("""
            {
              "publication_timestamp": "2026-08-27T13:50:00Z",
              "prices": [
                { "start_timestamp": "2026-08-26T22:00:00Z",
                  "end_timestamp": "2026-08-26T22:15:00Z",
                  "feed_in": [] },
                { "start_timestamp": "2026-08-26T22:15:00Z",
                  "end_timestamp": "2026-08-26T22:30:00Z",
                  "feed_in": [ { "unit": "CHF_kWh", "value": 0.142 } ] }
              ]
            }
            """);

        PreiszeitreiheDownloadDTO ergebnis = abrufService.abrufen();

        assertEquals(2, ergebnis.abgerufen());
        assertEquals(1, ergebnis.neu());
        assertEquals(1, ergebnis.uebersprungen());
        // Entscheidend: nur EIN Schreibvorgang - kein 0.00000 fuer das leere Intervall.
        verify(preiszeitreiheRepository, times(1)).upsert(any(), any(), any(), any());
    }

    @Test
    void abrufen_FehlenderWert_WirdUebersprungen() {
        antwortet("""
            {
              "prices": [
                { "start_timestamp": "2026-08-26T22:00:00Z",
                  "end_timestamp": "2026-08-26T22:15:00Z",
                  "feed_in": [ { "unit": "CHF_kWh" } ] }
              ]
            }
            """);

        assertEquals(1, abrufService.abrufen().uebersprungen());
        verify(preiszeitreiheRepository, never()).upsert(any(), any(), any(), any());
    }

    @Test
    void abrufen_EndeVorBeginn_WirdUebersprungen() {
        antwortet("""
            {
              "prices": [
                { "start_timestamp": "2026-08-26T22:15:00Z",
                  "end_timestamp": "2026-08-26T22:00:00Z",
                  "feed_in": [ { "unit": "CHF_kWh", "value": 0.138 } ] }
              ]
            }
            """);

        assertEquals(1, abrufService.abrufen().uebersprungen());
        verify(preiszeitreiheRepository, never()).upsert(any(), any(), any(), any());
    }

    @Test
    void abrufen_UebersprungeneIntervalle_ErzeugenWarnungJeMandant() {
        antwortet("""
            {
              "prices": [
                { "start_timestamp": "2026-08-26T22:00:00Z",
                  "end_timestamp": "2026-08-26T22:15:00Z",
                  "feed_in": [] }
              ]
            }
            """);

        abrufService.abrufen();

        verify(systemmeldungService).erfasse(eq(ORG_A), eq(MeldungLevel.WARN),
                eq(SystemmeldungService.KATEGORIE_PREISZEITREIHE),
                eq(SystemmeldungService.KEY_PREISZEITREIHE_UEBERSPRUNGEN), any());
        verify(systemmeldungService).erfasse(eq(ORG_B), eq(MeldungLevel.WARN),
                eq(SystemmeldungService.KATEGORIE_PREISZEITREIHE),
                eq(SystemmeldungService.KEY_PREISZEITREIHE_UEBERSPRUNGEN), any());
    }

    @Test
    void abrufen_SauberDurchgelaufen_ErledigtOffeneMeldungen() {
        antwortet(zweiIntervalle());

        abrufService.abrufen();

        // Selbstheilung: beide Meldungsarten werden je Mandant auf erledigt gesetzt.
        verify(systemmeldungService).autoResolve(ORG_A,
                SystemmeldungService.KEY_PREISZEITREIHE_ABRUF_FEHLER);
        verify(systemmeldungService).autoResolve(ORG_A,
                SystemmeldungService.KEY_PREISZEITREIHE_UEBERSPRUNGEN);
        verify(systemmeldungService).autoResolve(ORG_B,
                SystemmeldungService.KEY_PREISZEITREIHE_ABRUF_FEHLER);
        verify(systemmeldungService, never()).erfasse(anyLong(), any(), any(), any(), any());
    }

    // ==================== Fehlerfälle ====================

    @Test
    void abrufen_FremdeEinheit_WeistGesamtenAbrufAb() {
        antwortet("""
            {
              "prices": [
                { "start_timestamp": "2026-08-26T22:00:00Z",
                  "end_timestamp": "2026-08-26T22:15:00Z",
                  "feed_in": [ { "unit": "CHF_kWh", "value": 0.138 } ] },
                { "start_timestamp": "2026-08-26T22:15:00Z",
                  "end_timestamp": "2026-08-26T22:30:00Z",
                  "feed_in": [ { "unit": "Rp_kWh", "value": 13.8 } ] }
              ]
            }
            """);

        PreiszeitreiheQuelleException ex =
                assertThrows(PreiszeitreiheQuelleException.class, () -> abrufService.abrufen());

        assertTrue(ex.getMessage().contains("Rp_kWh"));
        // Alles oder nichts: Auch das gueltige erste Intervall wird NICHT geschrieben - sonst
        // stuende die halbe Reihe in fremder Einheit in der Tabelle.
        verify(preiszeitreiheRepository, never()).upsert(any(), any(), any(), any());
        verify(systemmeldungService).erfasse(eq(ORG_A), eq(MeldungLevel.WARN),
                eq(SystemmeldungService.KATEGORIE_PREISZEITREIHE),
                eq(SystemmeldungService.KEY_PREISZEITREIHE_ABRUF_FEHLER), any());
    }

    @Test
    void abrufen_ZuVieleIntervalle_WeistAbOhneZuSchreiben() {
        StringBuilder rumpf = new StringBuilder("{ \"prices\": [");
        for (int i = 0; i < 10_001; i++) {
            if (i > 0) {
                rumpf.append(',');
            }
            rumpf.append("{ \"start_timestamp\": \"2026-08-26T22:00:00Z\",")
                 .append(" \"end_timestamp\": \"2026-08-26T22:15:00Z\",")
                 .append(" \"feed_in\": [ { \"unit\": \"CHF_kWh\", \"value\": 0.1 } ] }");
        }
        rumpf.append("] }");
        antwortet(rumpf.toString());

        assertThrows(PreiszeitreiheQuelleException.class, () -> abrufService.abrufen());
        verify(preiszeitreiheRepository, never()).upsert(any(), any(), any(), any());
    }

    @Test
    void abrufen_PricesFehlt_WeistAb() {
        antwortet("{ \"publication_timestamp\": \"2026-08-27T13:50:00Z\" }");

        PreiszeitreiheQuelleException ex =
                assertThrows(PreiszeitreiheQuelleException.class, () -> abrufService.abrufen());

        assertTrue(ex.getMessage().contains("keine Preise"));
        verify(systemmeldungService).erfasse(eq(ORG_A), eq(MeldungLevel.WARN), any(),
                eq(SystemmeldungService.KEY_PREISZEITREIHE_ABRUF_FEHLER), any());
    }

    @Test
    void abrufen_QuelleAntwortetMitFehlerstatus_WeistAb() {
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(PreiszeitreiheQuelleException.class, () -> abrufService.abrufen());
        verify(preiszeitreiheRepository, never()).upsert(any(), any(), any(), any());
        verify(systemmeldungService).erfasse(eq(ORG_A), eq(MeldungLevel.WARN), any(),
                eq(SystemmeldungService.KEY_PREISZEITREIHE_ABRUF_FEHLER), any());
    }

    @Test
    void abrufen_UnlesbaresJson_WeistAb() {
        antwortet("kein json");

        assertThrows(PreiszeitreiheQuelleException.class, () -> abrufService.abrufen());
        verify(preiszeitreiheRepository, never()).upsert(any(), any(), any(), any());
    }

    /**
     * Der Meldungstext wird auf 500 Zeichen gekürzt — {@code systemmeldung.parameter} ist so breit,
     * und {@code erfasse} kürzt nicht selbst. Ohne das Kürzen scheitert das Melden des Fehlers am
     * Fehler selbst.
     */
    @Test
    void abrufen_LangerFehlertext_WirdAufSpaltenbreiteGekuerzt() {
        antwortet("""
            {
              "prices": [
                { "start_timestamp": "2026-08-26T22:00:00Z",
                  "end_timestamp": "2026-08-26T22:15:00Z",
                  "feed_in": [ { "unit": "%s", "value": 0.138 } ] }
              ]
            }
            """.formatted("X".repeat(600)));

        assertThrows(PreiszeitreiheQuelleException.class, () -> abrufService.abrufen());

        verify(systemmeldungService).erfasse(eq(ORG_A), any(), any(),
                eq(SystemmeldungService.KEY_PREISZEITREIHE_ABRUF_FEHLER), parameterCaptor.capture());
        assertEquals(500, parameterCaptor.getValue().length());
    }

    @Test
    void abrufen_OhneKonfigurierteUrl_ThrowsIllegalArgumentException() {
        PreiszeitreiheAbrufService ohneUrl = new PreiszeitreiheAbrufService(preiszeitreiheRepository,
                featureFlagService, systemmeldungService, RestClient.builder().build(), "  ");

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> ohneUrl.abrufen());
        assertTrue(ex.getMessage().contains("preiszeitreihe.url"));
    }

    @Test
    void abrufen_KeinMandantMitFlag_MeldetNichts() {
        when(featureFlagService.getOrgIdsMitAktivemFlag(FeatureFlag.PREISZEITREIHE))
                .thenReturn(List.of());
        antwortet(zweiIntervalle());

        abrufService.abrufen();

        verify(systemmeldungService, never()).erfasse(anyLong(), any(), any(), any(), any());
        verify(systemmeldungService, never()).autoResolve(anyLong(), any());
    }
}
