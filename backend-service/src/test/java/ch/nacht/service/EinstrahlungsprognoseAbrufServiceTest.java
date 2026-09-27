package ch.nacht.service;

import ch.nacht.dto.SteuerKonfigurationDTO;
import ch.nacht.entity.MeldungLevel;
import ch.nacht.repository.EinstrahlungsprognoseRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit-Tests fuer {@link EinstrahlungsprognoseAbrufService} — die Beschaffung der
 * Einstrahlungsprognose bei Open-Meteo (Specs/Ladeplanung.md, FR-2).
 *
 * <p>Der HTTP-Aufruf laeuft ueber {@link MockRestServiceServer} wie in
 * {@link PreiszeitreiheAbrufServiceTest}: So wird <b>echtes JSON</b> deserialisiert und die
 * Abbildung der {@code snake_case}-Felder ({@code minutely_15},
 * {@code global_tilted_irradiance}) mitgeprueft. Ohne {@code @JsonProperty} bliebe alles
 * {@code null}, und der Abruf schriebe still nichts — ein gemockter {@code RestClient} haette
 * genau diesen Teil nicht gesehen.
 *
 * <p><b>Der Leitgedanke dieser Klasse:</b> Alles, was schiefgehen kann, darf die vorhandene
 * Prognose nicht beschaedigen. Eine veraltete Prognose ist brauchbar, eine halb ersetzte nicht —
 * niemand saehe ihr an, welcher Teil von wann stammt.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class EinstrahlungsprognoseAbrufServiceTest {

    private static final String BASIS_URL = "https://api.test.invalid/forecast";
    private static final String MODELL = "meteoswiss_icon_ch1";
    private static final int TAGE = 2;
    private static final Long ORG_ID = 4L;

    /** Obergrenze plausibler Intervalle je Abruf (MAX_INTERVALLE im Service). */
    private static final int MAX_INTERVALLE = 1_000;

    @Mock
    private EinstrahlungsprognoseRepository prognoseRepository;

    @Mock
    private EinstellungenService einstellungenService;

    @Mock
    private SystemmeldungService systemmeldungService;

    @Mock
    private HibernateFilterService hibernateFilterService;

    @Captor
    private ArgumentCaptor<String> parameterCaptor;

    private MockRestServiceServer server;
    private EinstrahlungsprognoseAbrufService abrufService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        abrufService = new EinstrahlungsprognoseAbrufService(prognoseRepository,
                einstellungenService, systemmeldungService, hibernateFilterService,
                builder.build(), BASIS_URL, MODELL, TAGE);

        when(einstellungenService.getSteuerKonfiguration(ORG_ID)).thenReturn(vollstaendig());
    }

    // ==================== Kein Abruf ohne Konfiguration ====================

    /**
     * Ohne vollstaendigen Standort und Ausrichtung geschieht <b>nichts</b> — und zwar <b>still</b>.
     *
     * <p>Das ist der Normalfall eines Mandanten, der die Ladeplanung nicht nutzt. Eine
     * Systemmeldung dafuer waere kein Hinweis, sondern Laerm: Sie traefe stuendlich ein, ohne dass
     * etwas zu tun waere, und verdeckte die Meldungen, auf die es ankommt.
     */
    @Test
    void abrufen_OhneStandortUndAusrichtung_KeinAbrufUndKeineSystemmeldung() {
        when(einstellungenService.getSteuerKonfiguration(ORG_ID))
                .thenReturn(new SteuerKonfigurationDTO());

        int geschrieben = abrufService.abrufen(ORG_ID);

        assertEquals(0, geschrieben);
        verifyNoInteractions(systemmeldungService);
        verify(prognoseRepository, never()).upsert(any(), any(), any(), any());
        server.verify();
    }

    /**
     * Drei von vier Werten reichen nicht.
     *
     * <p>Ein stillschweigend angenommener vierter (etwa „flach nach Sueden") ergaebe eine
     * Prognose, die plausibel aussieht und falsch ist.
     */
    @Test
    void abrufen_UnvollstaendigeAusrichtung_KeinAbruf() {
        SteuerKonfigurationDTO teilweise = vollstaendig();
        teilweise.setNeigung(null);
        when(einstellungenService.getSteuerKonfiguration(ORG_ID)).thenReturn(teilweise);

        assertEquals(0, abrufService.abrufen(ORG_ID));
        verifyNoInteractions(systemmeldungService);
        verify(prognoseRepository, never()).upsert(any(), any(), any(), any());
    }

    // ==================== Erfolgsfall ====================

    /**
     * Zwei Intervalle werden gelesen und geschrieben.
     *
     * <p><b>Der Zeitstempel bleibt, was er ist.</b> {@code "2026-09-25T13:15"} kommt bei
     * {@code timezone=Europe/Zurich} als Ortszeit ohne Zonenangabe und wird ohne jede Umrechnung
     * zu {@code LocalDateTime.of(2026, 9, 25, 13, 15)}. Wuerde hier eine Zone angenommen, laege
     * die ganze Prognose ein bis zwei Stunden daneben — und sie saehe weiterhin plausibel aus.
     */
    @Test
    void abrufen_ZweiIntervalle_SchreibtBeideOhneZonenrechnung() {
        antwortet(zweiIntervalle());

        int geschrieben = abrufService.abrufen(ORG_ID);

        assertEquals(2, geschrieben);
        verify(prognoseRepository).upsert(eq(ORG_ID),
                eq(LocalDateTime.of(2026, 9, 25, 13, 15)),
                eq(BigDecimal.valueOf(812.4)), any());
        verify(prognoseRepository).upsert(eq(ORG_ID),
                eq(LocalDateTime.of(2026, 9, 25, 13, 30)),
                eq(BigDecimal.valueOf(795.1)), any());
        verifyNoInteractions(systemmeldungService);
        server.verify();
    }

    /**
     * Der Abruf traegt Koordinaten, Neigung, Azimut, Modell und Tageszahl — und sonst nichts.
     *
     * <p>Nach aussen gehen keine Verbrauchs-, Erzeugungs- oder Mieterdaten (NFR-2). Der Azimut
     * steht in der <b>Open-Meteo-Konvention</b> (0 = Sued, negativ = Ost); wer ihn mit der
     * meteorologischen Zaehlweise verwechselt, richtet die Anlage rechnerisch nach Norden.
     */
    @Test
    void abrufen_BautUrlAusKonfiguration() {
        antwortet(zweiIntervalle());

        abrufService.abrufen(ORG_ID);

        server.verify();
    }

    /** Der Mandantenfilter wird gesetzt — der Job hat keinen Sicherheitskontext. */
    @Test
    void abrufen_SetztMandantenfilterMitUebergebenerOrgId() {
        antwortet(zweiIntervalle());

        abrufService.abrufen(ORG_ID);

        verify(hibernateFilterService).enableOrgFilter(ORG_ID);
    }

    /**
     * Einzelne {@code null}-Werte werden <b>uebersprungen</b>, nicht als 0 geschrieben.
     *
     * <p>Kein Wert heisst „nicht bekannt". Eine 0 hiesse „kein Licht" — und genau diese 0 landete
     * als helles Intervall ohne Erzeugung im gelernten Umrechnungsfaktor (FR-3) und zoege ihn nach
     * unten. Ein zu tiefer Faktor liesse die Ladeplanung dauerhaft „die Restsonne reicht nicht"
     * schliessen.
     */
    @Test
    void abrufen_EinzelneNullWerte_WerdenUebersprungen() {
        antwortet("""
            {
              "timezone": "Europe/Zurich",
              "minutely_15": {
                "time": ["2026-09-25T13:15", "2026-09-25T13:30", "2026-09-25T13:45"],
                "global_tilted_irradiance": [812.4, null, 795.1]
              }
            }
            """);

        int geschrieben = abrufService.abrufen(ORG_ID);

        assertEquals(2, geschrieben);
        verify(prognoseRepository, times(2)).upsert(any(), any(), any(), any());
        verify(prognoseRepository, never()).upsert(any(),
                eq(LocalDateTime.of(2026, 9, 25, 13, 30)), any(), any());
        verifyNoInteractions(systemmeldungService);
    }

    /**
     * Alle Intervalle eines Abrufs tragen <b>denselben</b> Zeitpunkt in {@code abgerufen_am}.
     *
     * <p>Sonst waere nicht mehr zu erkennen, welche Werte aus demselben Lauf stammen — und damit
     * nicht, ob eine Prognose als Ganzes noch aktuell ist.
     */
    @Test
    void abrufen_SchreibtEinenGemeinsamenAbrufzeitpunkt() {
        antwortet(zweiIntervalle());
        ArgumentCaptor<LocalDateTime> abgerufenAm = ArgumentCaptor.forClass(LocalDateTime.class);

        abrufService.abrufen(ORG_ID);

        verify(prognoseRepository, times(2))
                .upsert(anyLong(), any(), any(), abgerufenAm.capture());
        assertEquals(abgerufenAm.getAllValues().get(0), abgerufenAm.getAllValues().get(1));
    }

    // ==================== Unplausible Antworten: gar nichts schreiben ====================

    /**
     * Verschieden lange Listen → <b>nichts</b> wird geschrieben.
     *
     * <p>{@code time} und {@code global_tilted_irradiance} sind parallel; passen sie nicht
     * zusammen, ist nicht bestimmbar, welcher Wert zu welchem Zeitpunkt gehoert. Schriebe der
     * Service die gemeinsame Anfangsstrecke, stuenden verschobene Werte in der Tabelle und die
     * Verschiebung waere spaeter nicht mehr erkennbar.
     */
    @Test
    void abrufen_ListenVerschiedenLang_SchreibtNichts() {
        antwortet("""
            {
              "timezone": "Europe/Zurich",
              "minutely_15": {
                "time": ["2026-09-25T13:15", "2026-09-25T13:30", "2026-09-25T13:45"],
                "global_tilted_irradiance": [812.4, 795.1]
              }
            }
            """);

        assertEquals(0, abrufService.abrufen(ORG_ID));
        verify(prognoseRepository, never()).upsert(any(), any(), any(), any());
        meldetFehler();
    }

    /**
     * Mehr als {@value #MAX_INTERVALLE} Intervalle → <b>nichts</b> wird geschrieben.
     *
     * <p>Zwei Tage im 15-Minuten-Raster sind 192. Alles jenseits dieser Groessenordnung ist kein
     * Tagesfenster mehr, sondern ein Hinweis darauf, dass sich die Bedeutung der Antwort geaendert
     * hat — etwa weil die API auf ein anderes Raster umgestellt hat. Dann wird nichts geschrieben,
     * statt die Tabelle zu fluten.
     */
    @Test
    void abrufen_MehrAlsMaxIntervalle_SchreibtNichts() {
        antwortet(vieleIntervalle(MAX_INTERVALLE + 1));

        assertEquals(0, abrufService.abrufen(ORG_ID));
        verify(prognoseRepository, never()).upsert(any(), any(), any(), any());
        meldetFehler();
    }

    /** Genau {@value #MAX_INTERVALLE} Intervalle sind noch zulaessig — die Grenze ist einschliesslich. */
    @Test
    void abrufen_GenauMaxIntervalle_SchreibtAlle() {
        antwortet(vieleIntervalle(MAX_INTERVALLE));

        assertEquals(MAX_INTERVALLE, abrufService.abrufen(ORG_ID));
        verifyNoInteractions(systemmeldungService);
    }

    // ==================== Fehlerhafte Antworten ====================

    /**
     * Eine Antwort ohne {@code minutely_15} ist ein Fehler — sie wird gemeldet, und die vorhandene
     * Prognose bleibt stehen.
     *
     * <p>Ohne die Pruefung liefe der Abruf durch und schriebe nichts: still, taeglich, und erst
     * auffallend, wenn die Prognose alt genug ist, um zu stoeren.
     */
    @Test
    void abrufen_AntwortOhneMinutely15_MeldetFehlerUndLaesstPrognoseStehen() {
        antwortet("{\"timezone\": \"Europe/Zurich\"}");

        assertEquals(0, abrufService.abrufen(ORG_ID));
        verify(prognoseRepository, never()).upsert(any(), any(), any(), any());
        meldetFehler();
    }

    /**
     * <b>Die gemeldete Zeitzone wird gegengeprueft.</b> Weicht sie ab, wird nichts geschrieben.
     *
     * <p>Die Zeitstempel kommen ohne Zonenangabe ({@code "2026-09-25T13:15"}). Welche gemeint ist,
     * sagt einzig dieses Feld — und es lag bis dahin ungeprueft im DTO.
     *
     * <p><b>Eine Ruckversicherung, kein eingetretener Fall.</b> Open-Meteo weist eine unbrauchbare
     * Zeitzone heute mit {@code 400} ab (am 26.09.2026 nachgewiesen). Faellt die API eines Tages
     * still auf UTC zurueck, laege die ganze Prognose zwei Stunden daneben und saehe weiterhin
     * plausibel aus — erkennbar allein am Sonnenstand.
     */
    @Test
    void abrufen_AntwortInAndererZeitzone_SchreibtNichtsUndMeldetFehler() {
        antwortet("""
            {
              "timezone": "GMT",
              "minutely_15": {
                "time": ["2026-09-25T13:15"],
                "global_tilted_irradiance": [812.4]
              }
            }
            """);

        assertEquals(0, abrufService.abrufen(ORG_ID));
        verify(prognoseRepository, never()).upsert(any(), any(), any(), any());
        verify(systemmeldungService).erfasse(eq(ORG_ID), eq(MeldungLevel.WARN), any(),
                eq("LADEPLANUNG_PROGNOSE_FEHLER"), parameterCaptor.capture());
        assertTrue(parameterCaptor.getValue().contains("GMT"),
                "Die Meldung nennt die gelieferte Zeitzone: " + parameterCaptor.getValue());
    }

    /**
     * Fehlt das Feld ganz, wird ebenfalls nichts geschrieben.
     *
     * <p>Ein fehlendes Feld ist keine Bestaetigung. Wuerde {@code null} durchgelassen, waere die
     * Pruefung genau dann wirkungslos, wenn die Antwort am wenigsten dem entspricht, was erwartet
     * wird.
     */
    @Test
    void abrufen_AntwortOhneZeitzone_SchreibtNichts() {
        antwortet("""
            {
              "minutely_15": {
                "time": ["2026-09-25T13:15"],
                "global_tilted_irradiance": [812.4]
              }
            }
            """);

        assertEquals(0, abrufService.abrufen(ORG_ID));
        verify(prognoseRepository, never()).upsert(any(), any(), any(), any());
        meldetFehler();
    }

    /** Auch eine Antwort mit leerem {@code minutely_15}-Block wird gemeldet, nicht verschluckt. */
    @Test
    void abrufen_AntwortOhneZeitreihe_MeldetFehler() {
        antwortet("{\"minutely_15\": {\"time\": null, \"global_tilted_irradiance\": null}}");

        assertEquals(0, abrufService.abrufen(ORG_ID));
        verify(prognoseRepository, never()).upsert(any(), any(), any(), any());
        meldetFehler();
    }

    /**
     * Eine gestoerte API nimmt den Job <b>nicht</b> mit: Meldung, Rueckgabe 0, weiter im Text.
     *
     * <p>Das ist die Bedingung dafuer, den externen Dienst ueberhaupt zu verwenden. Flaege die
     * Ausnahme durch, braeche der Job-Lauf fuer alle folgenden Mandanten ab.
     */
    @Test
    void abrufen_ApiAntwortetMitFehler_MeldetUndGibtNullZurueck() {
        server.expect(requestTo(erwarteteUrl()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        assertEquals(0, abrufService.abrufen(ORG_ID));
        verify(prognoseRepository, never()).upsert(any(), any(), any(), any());
        meldetFehler();
    }

    /** Unlesbares JSON ist derselbe Fall — gemeldet, nicht geworfen. */
    @Test
    void abrufen_UnlesbareAntwort_MeldetUndGibtNullZurueck() {
        antwortet("kein json");

        assertEquals(0, abrufService.abrufen(ORG_ID));
        verify(prognoseRepository, never()).upsert(any(), any(), any(), any());
        meldetFehler();
    }

    /**
     * Ein unlesbarer Zeitstempel wird gemeldet und nennt den Wert, an dem es lag.
     *
     * <p>Ohne den Wert in der Meldung bliebe nur „Abruf fehlgeschlagen" — und die Ursache muesste
     * jemand an der fremden API suchen.
     */
    @Test
    void abrufen_UnlesbarerZeitstempel_MeldetDenWert() {
        antwortet("""
            {
              "timezone": "Europe/Zurich",
              "minutely_15": {
                "time": ["25.09.2026 13:15"],
                "global_tilted_irradiance": [812.4]
              }
            }
            """);

        assertEquals(0, abrufService.abrufen(ORG_ID));
        verify(systemmeldungService).erfasse(eq(ORG_ID), eq(MeldungLevel.WARN), any(),
                eq("LADEPLANUNG_PROGNOSE_FEHLER"), parameterCaptor.capture());
        assertTrue(parameterCaptor.getValue().contains("25.09.2026 13:15"),
                "Die Meldung nennt den unlesbaren Wert: " + parameterCaptor.getValue());
    }

    /**
     * Eine lange Fehlerbeschreibung wird auf 500 Zeichen gekuerzt.
     *
     * <p>{@code systemmeldung.parameter} ist begrenzt; ungekuerzt braeche das Erfassen der Meldung
     * an derselben Stelle, an der es den Fehler festhalten soll — und der Grund des Abbruchs waere
     * verloren.
     */
    @Test
    void abrufen_LangeFehlerbeschreibung_WirdAufFuenfhundertZeichenGekuerzt() {
        antwortet("""
            {
              "timezone": "Europe/Zurich",
              "minutely_15": {
                "time": ["%s"],
                "global_tilted_irradiance": [812.4]
              }
            }
            """.formatted("x".repeat(600)));

        assertEquals(0, abrufService.abrufen(ORG_ID));
        verify(systemmeldungService).erfasse(anyLong(), any(), any(), any(),
                parameterCaptor.capture());
        assertEquals(500, parameterCaptor.getValue().length());
    }

    // ==================== Hilfsmittel ====================

    /** Konfiguration mit allen vier Werten — Standort und Ausrichtung vollstaendig. */
    private SteuerKonfigurationDTO vollstaendig() {
        SteuerKonfigurationDTO k = new SteuerKonfigurationDTO();
        k.setBreitengrad(new BigDecimal("47.05"));
        k.setLaengengrad(new BigDecimal("7.62"));
        k.setNeigung(new BigDecimal("30"));
        k.setAzimut(new BigDecimal("-10"));
        return k;
    }

    /**
     * Die URL, die <b>abgeschickt</b> wird — Zeichen fuer Zeichen, nach der Kodierung durch
     * {@code RestClient}.
     *
     * <p>Genau hier lag ein Fehler: Die Zeitzone stand vorkodiert als {@code Europe%2FZurich} im
     * Quelltext, und {@code RestClient.uri(String)} kodierte sie als URI-Vorlage ein zweites Mal
     * zu {@code Europe%252FZurich}. Die API haette den Parameter als {@code "Europe%2FZurich"}
     * gelesen — keine gueltige Zeitzone. Dass der Test die vollstaendige URL vergleicht und nicht
     * nur einzelne Bestandteile, ist der Grund, warum das ueberhaupt auffiel.
     */
    private String erwarteteUrl() {
        return BASIS_URL
                + "?latitude=47.05"
                + "&longitude=7.62"
                + "&tilt=30"
                + "&azimuth=-10"
                + "&minutely_15=global_tilted_irradiance"
                + "&timezone=Europe/Zurich"
                + "&models=" + MODELL
                + "&forecast_days=" + TAGE;
    }

    private void antwortet(String rumpf) {
        server.expect(requestTo(erwarteteUrl()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(rumpf, MediaType.APPLICATION_JSON));
    }

    /** Antwort mit zwei aufeinanderfolgenden Viertelstunden. */
    private String zweiIntervalle() {
        return """
            {
              "timezone": "Europe/Zurich",
              "minutely_15": {
                "time": ["2026-09-25T13:15", "2026-09-25T13:30"],
                "global_tilted_irradiance": [812.4, 795.1]
              }
            }
            """;
    }

    /** Antwort mit {@code anzahl} Intervallen ab 2026-09-25T00:00. */
    private String vieleIntervalle(int anzahl) {
        LocalDateTime start = LocalDateTime.of(2026, 9, 25, 0, 0);
        StringBuilder zeiten = new StringBuilder();
        StringBuilder werte = new StringBuilder();
        for (int i = 0; i < anzahl; i++) {
            if (i > 0) {
                zeiten.append(", ");
                werte.append(", ");
            }
            zeiten.append('"').append(start.plusMinutes(15L * i)).append('"');
            werte.append("100.0");
        }
        return "{\"timezone\": \"Europe/Zurich\", \"minutely_15\": {\"time\": [" + zeiten + "], "
                + "\"global_tilted_irradiance\": [" + werte + "]}}";
    }

    /** Die Systemmeldung, die jeder Fehlschlag erzeugt — Stufe, Kategorie und Schluessel. */
    private void meldetFehler() {
        verify(systemmeldungService).erfasse(eq(ORG_ID), eq(MeldungLevel.WARN),
                eq(SystemmeldungService.KATEGORIE_PREISZEITREIHE),
                eq("LADEPLANUNG_PROGNOSE_FEHLER"), any());
    }
}
