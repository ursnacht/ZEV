package ch.nacht.service;

import ch.nacht.dto.PrognosepunktDTO;
import ch.nacht.dto.SimulationDTO;
import ch.nacht.dto.SteuerKonfigurationDTO;
import ch.nacht.dto.SteuerentscheidDTO;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.FeatureFlag;
import ch.nacht.entity.Geraetezustand;
import ch.nacht.entity.Preiszeitreihe;
import ch.nacht.entity.Steuerentscheid;
import ch.nacht.entity.Steuerregel;
import ch.nacht.entity.Steuerverfahren;
import ch.nacht.entity.Steuerzustand;
import ch.nacht.entity.Zustandsgroesse;
import ch.nacht.exception.FeatureDisabledException;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.GeraetezustandRepository;
import ch.nacht.repository.MesswerteRepository;
import ch.nacht.repository.PreiszeitreiheRepository;
import ch.nacht.repository.SteuerentscheidRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.UnexpectedRollbackException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit-Tests fuer {@link SteuerungService} mit Blick auf die <b>Zeitbezuege</b>
 * (Specs/Messwerte-Zeitkonvention.md, Specs/Einspeisesteuerung.md FR-3).
 *
 * <p>{@code messwerte.zeit} traegt den Intervall<b>beginn</b> — wie {@code steuerentscheid.zeit_von}.
 * Ein Intervall wird deshalb mit {@code [zeitVon, zeitVon + 15)} gelesen, ohne die fruehere
 * Verschiebung um eine Viertelstunde. Ein Rueckfall darauf liefe still: Der Entscheid von 10:00
 * truege die Messung von 10:15, und beide Zahlen saehen plausibel aus. Deshalb pruefen die Tests
 * hier die <b>Abfragefenster</b> und die <b>Zuordnung</b> von Stempel zu Entscheid.
 *
 * <p>Die Regel selbst ({@link SteuerRegelService}) ist gemockt — ihre Fachlichkeit pruefen
 * {@code SteuerRegelServiceTest}. Hier zaehlt nur, mit welchen Zahlen sie gefragt wird.
 */
@ExtendWith(MockitoExtension.class)
// LENIENT: setUp() stubbt die Grundlage eines Job-Laufs (Konfiguration, Flag, Regel, Messwerte,
// Speicher) fuer ALLE Tests. Die Tests der Rueckrechnung und der Fehlerfaelle brauchen davon nur
// einen Teil; strikt liefen sie an "unnecessary stubbing" auf, ohne dass etwas falsch waere.
@MockitoSettings(strictness = Strictness.LENIENT)
public class SteuerungServiceTest {

    private static final Long ORG_ID = 7L;
    private static final Long SPEICHER_ID = 42L;

    /** Ein Sommertag (MESZ): 10:00 Ortszeit = 08:00 UTC. */
    private static final LocalDate DATUM = LocalDate.of(2026, 9, 25);
    private static final LocalDateTime ZEHN_UHR = DATUM.atTime(10, 0);
    private static final LocalDateTime VIERTEL_NACH_ZEHN = DATUM.atTime(10, 15);

    @Mock
    private SteuerentscheidRepository steuerentscheidRepository;

    @Mock
    private MesswerteRepository messwerteRepository;

    @Mock
    private EinheitRepository einheitRepository;

    @Mock
    private GeraetezustandRepository geraetezustandRepository;

    @Mock
    private PreiszeitreiheRepository preiszeitreiheRepository;

    @Mock
    private SteuerRegelService steuerRegelService;

    @Mock
    private LadeplanService ladeplanService;

    @Mock
    private ProduktionsprognoseService produktionsprognoseService;

    @Mock
    private EinstellungenService einstellungenService;

    @Mock
    private FeatureFlagService featureFlagService;

    // IMMER mocken - Multi-Tenancy Dependencies
    @Mock
    private OrganizationContextService organizationContextService;

    @Mock
    private HibernateFilterService hibernateFilterService;

    /** Fuer die eigene Transaktion der Schattenrechnung (REQUIRES_NEW). */
    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private SteuerungService steuerungService;

    private Einheit speicher;
    private SteuerKonfigurationDTO konfiguration;

    @BeforeEach
    void setUp() {
        speicher = new Einheit("Batterie", EinheitTyp.SPEICHER);
        speicher.setId(SPEICHER_ID);
        speicher.setOrgId(ORG_ID);

        // Ohne Batteriekapazitaet und Standort bleibt die Schattenrechnung leer - sie ist nicht
        // Gegenstand dieser Tests.
        konfiguration = new SteuerKonfigurationDTO();

        when(organizationContextService.getCurrentOrgId()).thenReturn(ORG_ID);
        when(featureFlagService.isEnabled(ORG_ID, FeatureFlag.EINSPEISESTEUERUNG)).thenReturn(true);
        when(einstellungenService.getSteuerKonfiguration(ORG_ID)).thenReturn(konfiguration);
        when(steuerRegelService.entscheide(any(), any(), any(), any(), any(), any()))
                .thenReturn(new SteuerRegelService.Entscheid(Steuerregel.LADEN,
                        Steuerzustand.FREI, Steuerzustand.FREI, new BigDecimal("1.000")));
        when(messwerteRepository.sumBilanzKomponentenPerZeitBetween(any(), any())).thenReturn(List.of());
        when(einheitRepository.existsByTyp(EinheitTyp.SPEICHER)).thenReturn(false);
        when(einheitRepository.findFirstByTyp(EinheitTyp.SPEICHER)).thenReturn(Optional.empty());
    }

    // ==================== werteIntervallAus (Job-Pfad) ====================

    /**
     * Die Messung des Intervalls 10:00–10:15 wird mit {@code [10:00, 10:15)} gelesen — der
     * Stempel ist der Beginn. Das fruehere Fenster {@code [10:15, 10:30)} lieferte die Messung
     * des Folgeintervalls.
     */
    @Test
    void werteIntervallAus_FragtMesswerteAbIntervallbeginn() {
        steuerungService.werteIntervallAus(ORG_ID, ZEHN_UHR);

        // Zweimal: im Job und erneut in der eigenen Transaktion der Schattenrechnung
        verify(hibernateFilterService, times(2)).enableOrgFilter(ORG_ID);
        verify(messwerteRepository).sumBilanzKomponentenPerZeitBetween(ZEHN_UHR, VIERTEL_NACH_ZEHN);
        verify(messwerteRepository, never())
                .sumBilanzKomponentenPerZeitBetween(VIERTEL_NACH_ZEHN, DATUM.atTime(10, 30));
    }

    /** Mit Speicher werden Ladung und Entladung im selben Fenster {@code [10:00, 10:15)} gelesen. */
    @Test
    void werteIntervallAus_MitSpeicher_FragtSpeichermengenAbIntervallbeginn() {
        when(einheitRepository.existsByTyp(EinheitTyp.SPEICHER)).thenReturn(true);
        when(messwerteRepository.sumLadungEntladungPerZeitBetween(any(), any(), any()))
                .thenReturn(List.of());

        steuerungService.werteIntervallAus(ORG_ID, ZEHN_UHR);

        verify(messwerteRepository).sumLadungEntladungPerZeitBetween(
                EinheitTyp.SPEICHER, ZEHN_UHR, VIERTEL_NACH_ZEHN);
    }

    /** Ohne Speicher-Einheit wird der Speicherfluss gar nicht erst abgefragt. */
    @Test
    void werteIntervallAus_OhneSpeicher_FragtKeineSpeichermengen() {
        steuerungService.werteIntervallAus(ORG_ID, ZEHN_UHR);

        verify(messwerteRepository, never()).sumLadungEntladungPerZeitBetween(any(), any(), any());
    }

    /**
     * Die Messung mit Stempel 10:00 geht in die Regel <b>und</b> in den Entscheid mit
     * {@code zeit_von = 10:00} — samt Speichermengen desselben Stempels.
     */
    @Test
    void werteIntervallAus_MesswertUndSpeicher_LandenImEntscheidDesselbenBeginns() {
        when(einheitRepository.existsByTyp(EinheitTyp.SPEICHER)).thenReturn(true);
        when(messwerteRepository.sumBilanzKomponentenPerZeitBetween(ZEHN_UHR, VIERTEL_NACH_ZEHN))
                .thenReturn(zeilen(bilanz(ZEHN_UHR, "3.000", "1.000", "0.200", "2.100")));
        when(messwerteRepository.sumLadungEntladungPerZeitBetween(
                EinheitTyp.SPEICHER, ZEHN_UHR, VIERTEL_NACH_ZEHN))
                .thenReturn(zeilen(new Object[]{ZEHN_UHR, new BigDecimal("0.800"), new BigDecimal("0.100")}));

        steuerungService.werteIntervallAus(ORG_ID, ZEHN_UHR);

        ArgumentCaptor<SteuerRegelService.Eingabe> eingabe =
                ArgumentCaptor.forClass(SteuerRegelService.Eingabe.class);
        verify(steuerRegelService).entscheide(eingabe.capture(), any(), any(), any(), any(), any());
        assertEquals(new BigDecimal("3.000"), eingabe.getValue().produktion());
        assertEquals(new BigDecimal("1.000"), eingabe.getValue().verbrauch());

        verify(steuerentscheidRepository).upsert(eq(ORG_ID), eq(ZEHN_UHR), any(), any(),
                eq(new BigDecimal("3.000")), eq(new BigDecimal("1.000")),
                eq(new BigDecimal("0.200")), eq(new BigDecimal("2.100")), any(),
                eq(new BigDecimal("0.800")), eq(new BigDecimal("0.100")),
                any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    /**
     * Fehlen Messwerte im Fenster, wird der Entscheid mit Nullen geschrieben — die Luecke bleibt
     * sichtbar, statt den Entscheid zu verschlucken.
     */
    @Test
    void werteIntervallAus_OhneMesswerte_EntscheidetMitNullen() {
        steuerungService.werteIntervallAus(ORG_ID, ZEHN_UHR);

        ArgumentCaptor<SteuerRegelService.Eingabe> eingabe =
                ArgumentCaptor.forClass(SteuerRegelService.Eingabe.class);
        verify(steuerRegelService).entscheide(eingabe.capture(), any(), any(), any(), any(), any());
        assertEquals(0, BigDecimal.ZERO.compareTo(eingabe.getValue().produktion()));
        assertEquals(0, BigDecimal.ZERO.compareTo(eingabe.getValue().verbrauch()));
        verify(steuerentscheidRepository).upsert(eq(ORG_ID), eq(ZEHN_UHR), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // ==================== Schattenrechnung isoliert (Specs/Ladeplanung.md, FR-1a) ====================

    /**
     * Wirft die Prognose, wird der Entscheid der Kaskade trotzdem geschrieben — mit leeren
     * Schattenwerten.
     */
    @Test
    void werteIntervallAus_PrognoseWirft_EntscheidWirdTrotzdemGeschrieben() {
        mitVoraussetzungenDerSchattenrechnung();
        when(produktionsprognoseService.getPrognose(ORG_ID, DATUM))
                .thenThrow(new IllegalStateException("Prognose kaputt"));

        steuerungService.werteIntervallAus(ORG_ID, ZEHN_UHR);

        verify(steuerentscheidRepository).upsert(eq(ORG_ID), eq(ZEHN_UHR), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(),
                eq("LADEN"), eq("FREI"), eq("FREI"), any(), any(), any(), any(), any(),
                eq("REGEL"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull());
    }

    /**
     * Der eigentliche Fehlerfall: Scheitert die Transaktion der Schattenrechnung erst beim Commit
     * (rollback-only nach einer Exception in einer transaktionalen Bean), darf auch das den
     * Entscheid nicht mitreissen. Vor der Isolierung lief die Schattenrechnung in der Transaktion
     * des Jobs - der Commit des Entscheids waere gescheitert.
     */
    @Test
    void werteIntervallAus_SchattenTransaktionScheitertBeimCommit_EntscheidWirdTrotzdemGeschrieben() {
        mitVoraussetzungenDerSchattenrechnung();
        when(produktionsprognoseService.getPrognose(ORG_ID, DATUM)).thenReturn(List.of());
        doThrow(new UnexpectedRollbackException("rollback-only"))
                .when(transactionManager).commit(any());

        steuerungService.werteIntervallAus(ORG_ID, ZEHN_UHR);

        verify(steuerentscheidRepository).upsert(eq(ORG_ID), eq(ZEHN_UHR), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(),
                eq("LADEN"), eq("FREI"), eq("FREI"), any(), any(), any(), any(), any(),
                eq("REGEL"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull());
    }

    /**
     * Unter dem Mindest-Ladezustand fragt die Schattenrechnung den Plan NICHT - SOC_TIEF ist der
     * Merit-Order vorgeschaltet. Protokolliert wird FREI ohne Rang, die Prognosegroessen bleiben.
     * Vorher stand hier GESPERRT (09.10.2026, 09:00 bei 16 %): eine Scheinabweichung.
     */
    @Test
    void werteIntervallAus_UnterMindestLadezustand_SchattenFreiOhnePlan() {
        mitVoraussetzungenDerSchattenrechnung();
        mitLadezustand("16.0");
        when(produktionsprognoseService.getPrognose(ORG_ID, DATUM))
                .thenReturn(List.of(prognosepunkt(ZEHN_UHR, "0.661")));

        steuerungService.werteIntervallAus(ORG_ID, ZEHN_UHR);

        verify(ladeplanService, never()).plane(any(), any(), any());
        verify(steuerentscheidRepository).upsert(eq(ORG_ID), eq(ZEHN_UHR), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                eq("REGEL"), eq("FREI"), eq(new BigDecimal("0.661")), eq(new BigDecimal("400")),
                eq(new BigDecimal("0.005")), isNull(), isNull(), eq(new BigDecimal("34.272")), eq(new BigDecimal("0.250")));
    }

    /**
     * Dieselbe Hysterese wie die Kaskade: Galt SOC_TIEF im Vorintervall, bleibt die Freigabe bis
     * Minimum + Hysterese (20 + 5 = 25 %) - bei 22 % also weiterhin FREI ohne Plan.
     */
    @Test
    void werteIntervallAus_HystereseNachSocTief_SchattenWeiterhinFreiOhnePlan() {
        mitVoraussetzungenDerSchattenrechnung();
        mitLadezustand("22.0");
        Steuerentscheid vorher = new Steuerentscheid();
        vorher.setRegel(Steuerregel.SOC_TIEF);
        when(steuerentscheidRepository.findByZeitVon(DATUM.atTime(9, 45))).thenReturn(Optional.of(vorher));
        when(produktionsprognoseService.getPrognose(ORG_ID, DATUM))
                .thenReturn(List.of(prognosepunkt(ZEHN_UHR, "0.661")));

        steuerungService.werteIntervallAus(ORG_ID, ZEHN_UHR);

        verify(ladeplanService, never()).plane(any(), any(), any());
        verify(steuerentscheidRepository).upsert(eq(ORG_ID), eq(ZEHN_UHR), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                eq("REGEL"), eq("FREI"), any(), any(), any(), isNull(), isNull(), any(), any());
    }

    /**
     * Ohne vorherige Freigabe gilt die einfache Grenze: 22 % liegt darueber, also fragt die
     * Schattenrechnung den Plan - und protokolliert dessen Entscheid, nicht das FREI von SOC_TIEF.
     */
    @Test
    void werteIntervallAus_UeberMindestLadezustand_SchattenFragtDenPlan() {
        mitVollstaendigerSchattenrechnung();
        mitLadezustand("22.0");

        steuerungService.werteIntervallAus(ORG_ID, ZEHN_UHR);

        verify(ladeplanService).plane(any(), eq(ZEHN_UHR), any());
        verify(steuerentscheidRepository).upsert(eq(ORG_ID), eq(ZEHN_UHR), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                eq("REGEL"), eq("GESPERRT"), any(), any(), any(), eq(1), eq(1), any(), any());
    }

    // ==================== Schattenrechnung: Erfolgsfall (Specs/Ladeplanung.md, FR-1a) ====================

    /**
     * Der Kern der Schattenrechnung: Die Merit-Order haette GESPERRT, die Kaskade entscheidet FREI.
     * Geschrieben wird der Entscheid der Kaskade - die Merit-Order steht nur daneben, samt allen
     * Plangroessen. Verwechselte jemand die beiden Spalten, wuerde die Schattenrechnung schalten.
     */
    @Test
    void werteIntervallAus_MeritOrderSperrt_KaskadeBestimmtDenEntscheid() {
        mitVoraussetzungenDerSchattenrechnung();
        when(produktionsprognoseService.getPrognose(ORG_ID, DATUM)).thenReturn(List.of(
                prognosepunkt(ZEHN_UHR, "0.661"),
                prognosepunkt(VIERTEL_NACH_ZEHN, "1.200")));
        // 10:00 und 10:15 Ortszeit = 08:00 und 08:15 UTC (Sommerzeit)
        when(preiszeitreiheRepository.findByZeitraum(any(), any())).thenReturn(List.of(
                preis(LocalDateTime.of(2026, 9, 25, 8, 0), "0.190"),
                preis(LocalDateTime.of(2026, 9, 25, 8, 15), "0.170")));
        when(ladeplanService.plane(any(), eq(ZEHN_UHR), any())).thenReturn(new LadeplanService.Plan(
                Steuerzustand.GESPERRT, 25, 21, new BigDecimal("20.400")));

        steuerungService.werteIntervallAus(ORG_ID, ZEHN_UHR);

        // Kaskade (setUp): LADEN, FREI/FREI - sie bestimmt den Entscheid; die Merit-Order daneben
        verify(steuerentscheidRepository).upsert(eq(ORG_ID), eq(ZEHN_UHR), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(),
                eq("LADEN"), eq("FREI"), eq("FREI"), any(), any(), any(), any(), any(),
                eq("REGEL"), eq("GESPERRT"), eq(new BigDecimal("0.661")), eq(new BigDecimal("400")),
                eq(new BigDecimal("0.005")), eq(25), eq(21), eq(new BigDecimal("20.400")),
                eq(new BigDecimal("0.250")));
    }

    /**
     * Der Plan bekommt das ausgewertete Intervall und die folgenden des Tages - keine frueheren,
     * und keines ohne Preis. Die freie Kapazitaet ist {@code kapazitaet x (1 - soc/100)}.
     */
    @Test
    @SuppressWarnings("unchecked")
    void werteIntervallAus_PlanBekommtDenResttagAbDemAusgewertetenIntervall() {
        mitVoraussetzungenDerSchattenrechnung();
        LocalDateTime zehnUhrDreissig = DATUM.atTime(10, 30);
        when(produktionsprognoseService.getPrognose(ORG_ID, DATUM)).thenReturn(List.of(
                prognosepunkt(DATUM.atTime(9, 45), "0.500"),   // frueher - gehoert nicht dazu
                prognosepunkt(ZEHN_UHR, "0.661"),
                prognosepunkt(VIERTEL_NACH_ZEHN, "1.200"),
                prognosepunkt(zehnUhrDreissig, "1.300")));     // ohne Preis - entfaellt
        when(preiszeitreiheRepository.findByZeitraum(any(), any())).thenReturn(List.of(
                preis(LocalDateTime.of(2026, 9, 25, 7, 45), "0.200"),
                preis(LocalDateTime.of(2026, 9, 25, 8, 0), "0.190"),
                preis(LocalDateTime.of(2026, 9, 25, 8, 15), "0.170")));
        when(ladeplanService.plane(any(), any(), any())).thenReturn(new LadeplanService.Plan(
                Steuerzustand.FREI, 2, 3, new BigDecimal("20.400")));

        steuerungService.werteIntervallAus(ORG_ID, ZEHN_UHR);

        ArgumentCaptor<List<LadeplanService.Intervall>> resttag = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<BigDecimal> kapazitaetFrei = ArgumentCaptor.forClass(BigDecimal.class);
        verify(ladeplanService).plane(resttag.capture(), eq(ZEHN_UHR), kapazitaetFrei.capture());
        assertEquals(List.of(ZEHN_UHR, VIERTEL_NACH_ZEHN),
                resttag.getValue().stream().map(LadeplanService.Intervall::zeit).toList());
        // 40.8 kWh x (1 - 50/100)
        assertEquals(0, new BigDecimal("20.400").compareTo(kapazitaetFrei.getValue()));
    }

    // ==================== Schattenrechnung: Rueckfall je Voraussetzung (FR-4) ====================

    /** Ohne Standort und Ausrichtung keine Prognose - die Merit-Order rechnet nicht. */
    @Test
    void werteIntervallAus_OhneStandort_SchattenLeer() {
        mitVollstaendigerSchattenrechnung();
        konfiguration.setBreitengrad(null);

        steuerungService.werteIntervallAus(ORG_ID, ZEHN_UHR);

        pruefeSchattenLeer();
    }

    /** Ohne Speicher-Einheit gibt es nichts zu laden. */
    @Test
    void werteIntervallAus_OhneSpeicherEinheit_SchattenLeer() {
        mitVollstaendigerSchattenrechnung();
        when(einheitRepository.existsByTyp(EinheitTyp.SPEICHER)).thenReturn(false);

        steuerungService.werteIntervallAus(ORG_ID, ZEHN_UHR);

        pruefeSchattenLeer();
    }

    /** Ohne Ladezustand keine freie Kapazitaet. */
    @Test
    void werteIntervallAus_OhneLadezustand_SchattenLeer() {
        mitVollstaendigerSchattenrechnung();
        when(geraetezustandRepository.findFirstByEinheitIdAndGroesseAndZeitLessThanOrderByZeitDesc(
                SPEICHER_ID, Zustandsgroesse.SOC, VIERTEL_NACH_ZEHN)).thenReturn(Optional.empty());

        steuerungService.werteIntervallAus(ORG_ID, ZEHN_UHR);

        pruefeSchattenLeer();
    }

    /** Ohne Batteriekapazitaet keine freie Kapazitaet. */
    @Test
    void werteIntervallAus_OhneKapazitaet_SchattenLeer() {
        mitVollstaendigerSchattenrechnung();
        konfiguration.setBatteriekapazitaet(null);

        steuerungService.werteIntervallAus(ORG_ID, ZEHN_UHR);

        pruefeSchattenLeer();
    }

    /** Kapazitaet 0 wirkt wie eine fehlende - sie ergaebe dauerhaft einen leeren Plan. */
    @Test
    void werteIntervallAus_KapazitaetNull_WieFehlend() {
        mitVollstaendigerSchattenrechnung();
        konfiguration.setBatteriekapazitaet(BigDecimal.ZERO);

        steuerungService.werteIntervallAus(ORG_ID, ZEHN_UHR);

        pruefeSchattenLeer();
    }

    /**
     * Fehlt Faktor oder Lastprofil, liefert die Prognose den Punkt, aber ohne erwarteten
     * Ueberschuss - auch dann rechnet die Merit-Order nicht. Ohne diese Wache plante sie mit einem
     * Resttag ohne das ausgewertete Intervall, und niemand saehe es.
     */
    @Test
    void werteIntervallAus_OhneErwartetenUeberschuss_SchattenLeer() {
        mitVollstaendigerSchattenrechnung();
        PrognosepunktDTO ohneUeberschuss = prognosepunkt(ZEHN_UHR, "0.661");
        ohneUeberschuss.setErwarteterUeberschuss(null);
        // Der Folgepunkt HAT Ueberschuss und Preis: Ohne die Wache entstuende daraus ein Resttag,
        // und geplant wuerde - nur die Wache fuer DIESES Intervall verhindert das.
        when(produktionsprognoseService.getPrognose(ORG_ID, DATUM)).thenReturn(List.of(
                ohneUeberschuss, prognosepunkt(VIERTEL_NACH_ZEHN, "1.200")));
        when(preiszeitreiheRepository.findByZeitraum(any(), any())).thenReturn(List.of(
                preis(LocalDateTime.of(2026, 9, 25, 8, 0), "0.190"),
                preis(LocalDateTime.of(2026, 9, 25, 8, 15), "0.170")));

        steuerungService.werteIntervallAus(ORG_ID, ZEHN_UHR);

        pruefeSchattenLeer();
    }

    /** Ohne Ausrichtung (nur der Azimut fehlt) gibt es keine Prognose - Standort allein genuegt nicht. */
    @Test
    void werteIntervallAus_OhneAusrichtung_SchattenLeer() {
        mitVollstaendigerSchattenrechnung();
        konfiguration.setAzimut(null);

        steuerungService.werteIntervallAus(ORG_ID, ZEHN_UHR);

        pruefeSchattenLeer();
    }

    /**
     * Bei negativem Preis rechnet die Schattenrechnung weiter: Die Kaskade sperrt die Einspeisung
     * ({@code PREIS_NEGATIV}), die Merit-Order haelt daneben fest, was sie fuer die Ladung entschiede.
     * Das ist die heutige Form des Zielverhaltens nach dem Umschalten (Kriterium „Nach dem
     * Umschalten"): Dort entscheidet sie die Ladung dann selbst.
     */
    @Test
    @SuppressWarnings("unchecked")
    void werteIntervallAus_NegativerPreis_SchattenrechnungRechnetWeiter() {
        mitVollstaendigerSchattenrechnung();
        // Ein wirklich negativer Preis - ein kuenftiger Filter darauf beim Aufbau des Resttags fiele auf
        when(preiszeitreiheRepository.findByZeitraum(any(), any()))
                .thenReturn(List.of(preis(LocalDateTime.of(2026, 9, 25, 8, 0), "-0.020")));
        when(steuerRegelService.entscheide(any(), any(), any(), any(), any(), any()))
                .thenReturn(new SteuerRegelService.Entscheid(Steuerregel.PREIS_NEGATIV,
                        Steuerzustand.FREI, Steuerzustand.GESPERRT, new BigDecimal("1.000")));

        steuerungService.werteIntervallAus(ORG_ID, ZEHN_UHR);

        ArgumentCaptor<List<LadeplanService.Intervall>> resttag = ArgumentCaptor.forClass(List.class);
        verify(ladeplanService).plane(resttag.capture(), eq(ZEHN_UHR), any());
        assertEquals(0, new BigDecimal("-0.020").compareTo(resttag.getValue().get(0).preis()),
                "das Intervall mit negativem Preis gehoert in den Plan");
        verify(steuerentscheidRepository).upsert(eq(ORG_ID), eq(ZEHN_UHR), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(),
                eq("PREIS_NEGATIV"), eq("FREI"), eq("GESPERRT"), any(), any(), any(), any(), any(),
                eq("REGEL"), eq("GESPERRT"), any(), any(), any(), any(), any(), any(), any());
    }

    /** Ohne Prognosepunkt fuer DIESES Intervall rechnet sie nicht. */
    @Test
    void werteIntervallAus_OhnePrognoseFuerDasIntervall_SchattenLeer() {
        mitVollstaendigerSchattenrechnung();
        when(produktionsprognoseService.getPrognose(ORG_ID, DATUM))
                .thenReturn(List.of(prognosepunkt(VIERTEL_NACH_ZEHN, "1.200")));

        steuerungService.werteIntervallAus(ORG_ID, ZEHN_UHR);

        pruefeSchattenLeer();
    }

    /** Ohne Preise fuer den Resttag keine Reihenfolge - eine willkuerliche waere schlimmer. */
    @Test
    void werteIntervallAus_OhnePreise_SchattenLeer() {
        mitVollstaendigerSchattenrechnung();
        when(preiszeitreiheRepository.findByZeitraum(any(), any())).thenReturn(List.of());

        steuerungService.werteIntervallAus(ORG_ID, ZEHN_UHR);

        pruefeSchattenLeer();
    }

    /**
     * Die Schattenrechnung laeuft in einer EIGENEN, nur lesenden Transaktion - nur so trifft ein
     * Fehler darin nicht die Transaktion des Entscheids.
     */
    @Test
    void werteIntervallAus_SchattenrechnungLaeuftInEigenerLesenderTransaktion() {
        mitVoraussetzungenDerSchattenrechnung();
        when(produktionsprognoseService.getPrognose(ORG_ID, DATUM)).thenReturn(List.of());

        steuerungService.werteIntervallAus(ORG_ID, ZEHN_UHR);

        ArgumentCaptor<TransactionDefinition> definition =
                ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                definition.getValue().getPropagationBehavior());
        assertTrue(definition.getValue().isReadOnly());
        // Neue Transaktion = neue Session: der Org-Filter wird dort erneut gesetzt
        verify(hibernateFilterService, times(2)).enableOrgFilter(ORG_ID);
        verify(produktionsprognoseService).getPrognose(ORG_ID, DATUM);
    }

    /**
     * Der Ladezustand bleibt der <b>letzte Wert vor dem Intervallende</b> (10:15) — die Umstellung
     * der Messwerte betrifft ihn nicht.
     */
    @Test
    void werteIntervallAus_Ladezustand_LetzterWertVorIntervallende() {
        when(einheitRepository.findFirstByTyp(EinheitTyp.SPEICHER)).thenReturn(Optional.of(speicher));
        when(geraetezustandRepository.findFirstByEinheitIdAndGroesseAndZeitLessThanOrderByZeitDesc(
                SPEICHER_ID, Zustandsgroesse.SOC, VIERTEL_NACH_ZEHN))
                .thenReturn(Optional.of(soc(DATUM.atTime(10, 14), "63.5")));

        steuerungService.werteIntervallAus(ORG_ID, ZEHN_UHR);

        ArgumentCaptor<SteuerRegelService.Eingabe> eingabe =
                ArgumentCaptor.forClass(SteuerRegelService.Eingabe.class);
        verify(steuerRegelService).entscheide(eingabe.capture(), any(), any(), any(), any(), any());
        assertEquals(new BigDecimal("63.5"), eingabe.getValue().soc());
    }

    /**
     * Die Hysterese liest den Entscheid des <b>Vorintervalls</b> (09:45) — eine Verschiebung auf
     * {@code steuerentscheid}, nicht auf {@code messwerte}; sie bleibt.
     */
    @Test
    void werteIntervallAus_Hysterese_LiestEntscheidDesVorintervalls() {
        steuerungService.werteIntervallAus(ORG_ID, ZEHN_UHR);

        verify(steuerentscheidRepository).findByZeitVon(DATUM.atTime(9, 45));
    }

    /** Ohne Flag wird nichts gelesen und nichts geschrieben. */
    @Test
    void werteIntervallAus_FeatureDeaktiviert_WirftFeatureDisabledException() {
        when(featureFlagService.isEnabled(ORG_ID, FeatureFlag.EINSPEISESTEUERUNG)).thenReturn(false);

        assertThrows(FeatureDisabledException.class,
                () -> steuerungService.werteIntervallAus(ORG_ID, ZEHN_UHR));

        verify(messwerteRepository, never()).sumBilanzKomponentenPerZeitBetween(any(), any());
        verifyNoInteractions(steuerentscheidRepository);
    }

    // ==================== getEntscheideSimuliert (Tagesansicht) ====================

    /**
     * Ein Tag wird mit {@code [00:00, 00:00 des Folgetags)} gelesen — ohne Verschiebung. Der
     * Messwert mit Stempel 10:00 landet im nachgerechneten Entscheid 10:00, zusammen mit dem
     * Preis desselben Intervalls (08:00 UTC).
     */
    @Test
    void getEntscheideSimuliert_MesswertMitStempelZehnUhr_LandetImEntscheidZehnUhr() {
        when(messwerteRepository.sumBilanzKomponentenPerZeitBetween(any(), any()))
                .thenReturn(zeilen(
                        bilanz(ZEHN_UHR, "3.000", "1.000", "0", "2.000"),
                        bilanz(VIERTEL_NACH_ZEHN, "5.000", "1.500", "0", "3.500")));
        when(preiszeitreiheRepository.findByZeitraum(any(), any())).thenReturn(List.of(
                preis(LocalDateTime.of(2026, 9, 25, 8, 0), "0.111"),
                preis(LocalDateTime.of(2026, 9, 25, 8, 15), "0.222")));

        List<SteuerentscheidDTO> dtos = steuerungService.getEntscheideSimuliert(
                DATUM, new BigDecimal("0.05"), null, null);

        verify(hibernateFilterService).enableOrgFilter();
        verify(messwerteRepository).sumBilanzKomponentenPerZeitBetween(
                DATUM.atStartOfDay(), DATUM.plusDays(1).atStartOfDay());
        assertEquals(2, dtos.size());
        assertEquals(ZEHN_UHR, dtos.get(0).getZeit());
        assertEquals(new BigDecimal("3.000"), dtos.get(0).getProduktion());
        assertEquals(new BigDecimal("0.111"), dtos.get(0).getPreis());
        assertEquals(VIERTEL_NACH_ZEHN, dtos.get(1).getZeit());
        assertEquals(new BigDecimal("5.000"), dtos.get(1).getProduktion());
        assertEquals(new BigDecimal("0.222"), dtos.get(1).getPreis());
    }

    /**
     * Speichermengen werden ueber {@code [00:00, 00:00 des Folgetags)} geholt und nach dem
     * <b>Stempel selbst</b> zugeordnet: Die Mengen unter 10:00 gehoeren zum Entscheid 10:00, nicht
     * zu dem von 09:45. Der Ladezustand bleibt der letzte Wert <b>vor</b> 10:15 — der Wert genau
     * um 10:15 gehoert schon zum naechsten Intervall.
     */
    @Test
    void getEntscheideSimuliert_SpeichermengenUndLadezustand_UnterDemStempel() {
        when(einheitRepository.findFirstByTyp(EinheitTyp.SPEICHER)).thenReturn(Optional.of(speicher));
        when(messwerteRepository.sumBilanzKomponentenPerZeitBetween(any(), any()))
                .thenReturn(zeilen(
                        bilanz(DATUM.atTime(9, 45), "1.000", "1.000", "0", "0"),
                        bilanz(ZEHN_UHR, "3.000", "1.000", "0", "2.000")));
        when(messwerteRepository.sumLadungEntladungPerZeitBetween(any(), any(), any()))
                .thenReturn(zeilen(new Object[]{ZEHN_UHR, new BigDecimal("0.8"), new BigDecimal("0.1")}));
        when(geraetezustandRepository
                .findByEinheitIdAndGroesseAndZeitGreaterThanEqualAndZeitLessThanOrderByZeitAsc(
                        eq(SPEICHER_ID), eq(Zustandsgroesse.SOC), any(), any()))
                .thenReturn(List.of(
                        soc(DATUM.atTime(10, 10), "55"),
                        soc(VIERTEL_NACH_ZEHN, "60")));

        List<SteuerentscheidDTO> dtos = steuerungService.getEntscheideSimuliert(
                DATUM, new BigDecimal("0.05"), null, null);

        verify(messwerteRepository).sumLadungEntladungPerZeitBetween(EinheitTyp.SPEICHER,
                DATUM.atStartOfDay(), DATUM.plusDays(1).atStartOfDay());
        SteuerentscheidDTO neunUhrFuenfundvierzig = dtos.get(0);
        SteuerentscheidDTO zehnUhr = dtos.get(1);
        assertEquals(ZEHN_UHR, zehnUhr.getZeit());
        assertEquals(new BigDecimal("0.800"), zehnUhr.getSpeicherLadung());
        assertEquals(new BigDecimal("0.100"), zehnUhr.getSpeicherEntladung());
        assertNull(neunUhrFuenfundvierzig.getSpeicherLadung(),
                "Mengen mit Stempel 10:00 gehoeren nicht zum Intervall 09:45");
        assertEquals(new BigDecimal("55"), zehnUhr.getSoc(), "letzter Wert VOR 10:15");
    }

    /**
     * Die Rueckrechnung kennzeichnet ihre Entscheide als Regel-Entscheide - und stuetzt sich nicht
     * auf {@code zev.einstrahlungsprognose}: Dort stuende die nachtraeglich geholte Fassung, die das
     * eingetretene Wetter schon kennt (Specs/Ladeplanung.md, FR-8).
     */
    @Test
    void getEntscheideSimuliert_KennzeichnetRegelUndLiestKeinePrognose() {
        when(messwerteRepository.sumBilanzKomponentenPerZeitBetween(any(), any()))
                .thenReturn(zeilen(bilanz(ZEHN_UHR, "3.000", "1.000", "0", "2.000")));
        when(preiszeitreiheRepository.findByZeitraum(any(), any())).thenReturn(List.of(
                preis(LocalDateTime.of(2026, 9, 25, 8, 0), "0.111")));

        List<SteuerentscheidDTO> dtos = steuerungService.getEntscheideSimuliert(
                DATUM, new BigDecimal("0.05"), null, null);

        assertFalse(dtos.isEmpty());
        dtos.forEach(dto -> assertEquals(Steuerverfahren.REGEL, dto.getVerfahren()));
        verifyNoInteractions(produktionsprognoseService);
        verifyNoInteractions(ladeplanService);
    }

    /** Ohne Messwerte gibt es keine nachgerechneten Entscheide — und keine Speicherabfrage. */
    @Test
    void getEntscheideSimuliert_OhneMesswerte_LiefertLeereListe() {
        List<SteuerentscheidDTO> dtos = steuerungService.getEntscheideSimuliert(
                DATUM, new BigDecimal("0.05"), null, null);

        assertTrue(dtos.isEmpty());
        verify(messwerteRepository, never()).sumLadungEntladungPerZeitBetween(any(), any(), any());
    }

    // ==================== simuliere (Rueckrechnung ueber mehrere Tage) ====================

    /**
     * Ueber zwei Tage lautet das Fenster {@code [von 00:00, bis+1 00:00)} — der Zeitraum selbst.
     * Mit der frueheren Verschiebung um 00:15 fehlte das erste Intervall, und das erste des
     * Folgetags zaehlte mit.
     */
    @Test
    void simuliere_ZweiTage_FensterVonTagesbeginnBisFolgetag() {
        LocalDate bis = DATUM.plusDays(1);
        when(messwerteRepository.sumBilanzKomponentenPerZeitBetween(any(), any()))
                .thenReturn(zeilen(
                        bilanz(DATUM.atStartOfDay(), "1.000", "0", "0", "1.000"),
                        bilanz(bis.atTime(23, 45), "1.000", "0", "0", "1.000")));

        SimulationDTO dto = steuerungService.simuliere(DATUM, bis, new BigDecimal("0.05"), null, null);

        verify(hibernateFilterService).enableOrgFilter();
        verify(messwerteRepository).sumBilanzKomponentenPerZeitBetween(
                DATUM.atStartOfDay(), bis.plusDays(1).atStartOfDay());
        // Erstes (00:00) und letztes (23:45) Intervall des Zeitraums zaehlen beide
        assertEquals(2, dto.getIntervalle());
        assertEquals(DATUM, dto.getVon());
        assertEquals(bis, dto.getBis());
    }

    /** Ohne Flag rechnet die Simulation nicht. */
    @Test
    void simuliere_FeatureDeaktiviert_WirftFeatureDisabledException() {
        when(featureFlagService.isEnabled(ORG_ID, FeatureFlag.EINSPEISESTEUERUNG)).thenReturn(false);

        assertThrows(FeatureDisabledException.class, () -> steuerungService.simuliere(
                DATUM, DATUM, new BigDecimal("0.05"), null, null));

        verify(messwerteRepository, never()).sumBilanzKomponentenPerZeitBetween(any(), any());
    }

    // ==================== Hilfsmittel ====================

    /** Zeile wie {@code sumBilanzKomponentenPerZeitBetween}: {@code [zeit (Beginn), produktion, verbrauch, bezug, ruecklieferung]}. */
    private Object[] bilanz(LocalDateTime zeit, String produktion, String verbrauch,
                            String bezug, String ruecklieferung) {
        return new Object[]{zeit, new BigDecimal(produktion), new BigDecimal(verbrauch),
                new BigDecimal(bezug), new BigDecimal(ruecklieferung)};
    }

    private List<Object[]> zeilen(Object[]... zeilen) {
        return Arrays.asList(zeilen);
    }

    /**
     * Alle Voraussetzungen der Schattenrechnung erfuellt (Specs/Ladeplanung.md, FR-4) - damit sie
     * bis zur Prognose kommt.
     */
    private void mitVoraussetzungenDerSchattenrechnung() {
        konfiguration.setBatteriekapazitaet(new BigDecimal("40.8"));
        konfiguration.setBreitengrad(new BigDecimal("47.0"));
        konfiguration.setLaengengrad(new BigDecimal("7.5"));
        konfiguration.setAzimut(BigDecimal.ZERO);
        konfiguration.setNeigung(new BigDecimal("30"));
        when(einheitRepository.existsByTyp(EinheitTyp.SPEICHER)).thenReturn(true);
        when(einheitRepository.findFirstByTyp(EinheitTyp.SPEICHER)).thenReturn(Optional.of(speicher));
        when(messwerteRepository.sumLadungEntladungPerZeitBetween(any(), any(), any()))
                .thenReturn(List.of());
        when(geraetezustandRepository.findFirstByEinheitIdAndGroesseAndZeitLessThanOrderByZeitDesc(
                SPEICHER_ID, Zustandsgroesse.SOC, VIERTEL_NACH_ZEHN))
                .thenReturn(Optional.of(soc(DATUM.atTime(10, 14), "50.0")));
    }

    /** Alle Voraussetzungen plus Prognose und Preise - die Schattenrechnung kaeme bis zum Plan. */
    private void mitVollstaendigerSchattenrechnung() {
        mitVoraussetzungenDerSchattenrechnung();
        when(produktionsprognoseService.getPrognose(ORG_ID, DATUM))
                .thenReturn(List.of(prognosepunkt(ZEHN_UHR, "0.661")));
        when(preiszeitreiheRepository.findByZeitraum(any(), any()))
                .thenReturn(List.of(preis(LocalDateTime.of(2026, 9, 25, 8, 0), "0.190")));
        when(ladeplanService.plane(any(), any(), any())).thenReturn(new LadeplanService.Plan(
                Steuerzustand.GESPERRT, 1, 1, new BigDecimal("20.400")));
    }

    /**
     * Rueckfall: Der Entscheid der Kaskade wird geschrieben, alle Schattenwerte sind {@code null},
     * {@code verfahren = REGEL}, und der Plan wird gar nicht erst gefragt.
     */
    private void pruefeSchattenLeer() {
        verify(ladeplanService, never()).plane(any(), any(), any());
        verify(steuerentscheidRepository).upsert(eq(ORG_ID), eq(ZEHN_UHR), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(),
                eq("LADEN"), eq("FREI"), eq("FREI"), any(), any(), any(), any(), any(),
                eq("REGEL"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull());
    }

    private void mitLadezustand(String wert) {
        when(geraetezustandRepository.findFirstByEinheitIdAndGroesseAndZeitLessThanOrderByZeitDesc(
                SPEICHER_ID, Zustandsgroesse.SOC, VIERTEL_NACH_ZEHN))
                .thenReturn(Optional.of(soc(DATUM.atTime(10, 14), wert)));
    }

    /** Ein Prognosepunkt mit Einstrahlung 400 W/m2, Faktor 0.005 und Lastprofil 0.250 kWh. */
    private PrognosepunktDTO prognosepunkt(LocalDateTime zeit, String ueberschuss) {
        PrognosepunktDTO punkt = new PrognosepunktDTO();
        punkt.setZeit(zeit);
        punkt.setGti(new BigDecimal("400"));
        punkt.setFaktor(new BigDecimal("0.005"));
        punkt.setErwarteterUeberschuss(new BigDecimal(ueberschuss));
        punkt.setLastprofil(new BigDecimal("0.250"));
        return punkt;
    }

    private Geraetezustand soc(LocalDateTime zeit, String wert) {
        return new Geraetezustand(ORG_ID, SPEICHER_ID, zeit, Zustandsgroesse.SOC, new BigDecimal(wert));
    }

    /** Preis mit Beginn in <b>UTC</b>, wie die Preiszeitreihe ihn fuehrt. */
    private Preiszeitreihe preis(LocalDateTime zeitVonUtc, String wert) {
        return new Preiszeitreihe(zeitVonUtc, zeitVonUtc.plusMinutes(15), new BigDecimal(wert),
                zeitVonUtc.minusDays(1));
    }
}
