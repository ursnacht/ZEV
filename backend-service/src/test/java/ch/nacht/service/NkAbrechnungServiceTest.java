package ch.nacht.service;

import ch.nacht.dto.NkAbrechnungDetailDTO;
import ch.nacht.dto.NkAkontoDTO;
import ch.nacht.dto.NkPersonDTO;
import ch.nacht.dto.NkPositionDTO;
import ch.nacht.dto.NkVerbrauchDTO;
import ch.nacht.dto.NkZusatzDTO;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.FeatureFlag;
import ch.nacht.entity.Mengeneinheit;
import ch.nacht.entity.Mieter;
import ch.nacht.entity.MieterEinheit;
import ch.nacht.entity.NkAbrechnung;
import ch.nacht.entity.NkAkonto;
import ch.nacht.entity.NkPerson;
import ch.nacht.entity.NkPosition;
import ch.nacht.entity.NkPositionsart;
import ch.nacht.entity.NkVerbrauch;
import ch.nacht.entity.NkZusatz;
import ch.nacht.exception.FeatureDisabledException;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.MieterEinheitRepository;
import ch.nacht.repository.MieterRepository;
import ch.nacht.repository.NkAbrechnungRepository;
import ch.nacht.repository.NkAkontoRepository;
import ch.nacht.repository.NkPersonRepository;
import ch.nacht.repository.NkPositionRepository;
import ch.nacht.repository.NkVerbrauchRepository;
import ch.nacht.repository.NkZusatzRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests der Verwaltung der Nebenkostenabrechnungen
 * ({@code Specs/Nebenkosten/Abrechnung.md}, FR-1 bis FR-6).
 *
 * <p>Der {@link NkBerechnungService} wird bewusst <b>nicht</b> gemockt, sondern als {@link Spy} mit
 * der echten Implementierung eingesetzt: Er ist rein und ohne Zustand, und die Pruefung
 * {@code Σ Tage(i) <= Nenner} liefe mit einem Mock ins Leere — jede zu klein erfasste Anzahl
 * Wohnungen kaeme durch, ohne dass der Test es merkte.
 */
@ExtendWith(MockitoExtension.class)
public class NkAbrechnungServiceTest {

    private static final Long ORG_ID = 42L;
    private static final LocalDate VON = LocalDate.of(2025, 1, 1);
    private static final LocalDate BIS = LocalDate.of(2025, 12, 31);

    @Mock
    private NkAbrechnungRepository abrechnungRepository;

    @Mock
    private NkPositionRepository positionRepository;

    @Mock
    private NkVerbrauchRepository verbrauchRepository;

    @Mock
    private NkZusatzRepository zusatzRepository;

    @Mock
    private NkAkontoRepository akontoRepository;

    @Mock
    private NkPersonRepository personRepository;

    @Mock
    private MieterRepository mieterRepository;

    @Mock
    private MieterEinheitRepository mieterEinheitRepository;

    @Mock
    private EinheitRepository einheitRepository;

    @Spy
    private NkBerechnungService berechnungService = new NkBerechnungService();

    @Mock
    private FeatureFlagService featureFlagService;

    @Mock
    private OrganizationContextService organizationContextService;

    @Mock
    private HibernateFilterService hibernateFilterService;

    @InjectMocks
    private NkAbrechnungService nkAbrechnungService;

    private NkAbrechnung testAbrechnung1;
    private NkAbrechnung testAbrechnung2;

    @BeforeEach
    void setUp() {
        testAbrechnung1 = new NkAbrechnung();
        testAbrechnung1.setId(1L);
        testAbrechnung1.setOrgId(ORG_ID);
        testAbrechnung1.setBezeichnung("Nebenkostenabrechnung 2025");
        testAbrechnung1.setDatumVon(VON);
        testAbrechnung1.setDatumBis(BIS);
        testAbrechnung1.setAnzahlWohnungen(9);

        testAbrechnung2 = new NkAbrechnung();
        testAbrechnung2.setId(2L);
        testAbrechnung2.setOrgId(ORG_ID);
        testAbrechnung2.setBezeichnung("Nebenkostenabrechnung 2024");
        testAbrechnung2.setDatumVon(LocalDate.of(2024, 1, 1));
        testAbrechnung2.setDatumBis(LocalDate.of(2024, 12, 31));
        testAbrechnung2.setAnzahlWohnungen(9);
    }

    // ==================== Feature-Flag (FR-6) ====================
    // Ohne diese Pruefung waere der Flag reine Kosmetik: Das Menue bliebe verborgen, die API aber
    // ueber jeden HTTP-Client erreichbar.

    @Test
    void getAllAbrechnungen_FeatureFlagAus_ThrowsFeatureDisabled() {
        featureFlagAus();

        assertThrows(FeatureDisabledException.class, () -> nkAbrechnungService.getAllAbrechnungen());
        verify(abrechnungRepository, never()).findAllByOrderByDatumVonDesc();
    }

    @Test
    void getAbrechnungDetail_FeatureFlagAus_ThrowsFeatureDisabled() {
        featureFlagAus();

        assertThrows(FeatureDisabledException.class, () -> nkAbrechnungService.getAbrechnungDetail(1L));
        verify(abrechnungRepository, never()).findById(anyLong());
    }

    @Test
    void getVorlage_FeatureFlagAus_ThrowsFeatureDisabled() {
        featureFlagAus();

        assertThrows(FeatureDisabledException.class, () -> nkAbrechnungService.getVorlage());
    }

    @Test
    void createAbrechnung_FeatureFlagAus_ThrowsFeatureDisabled() {
        featureFlagAus();
        NkAbrechnung neu = neueAbrechnung();

        assertThrows(FeatureDisabledException.class, () -> nkAbrechnungService.createAbrechnung(neu));
        verify(abrechnungRepository, never()).save(any());
    }

    @Test
    void saveAbrechnung_FeatureFlagAus_ThrowsFeatureDisabled() {
        featureFlagAus();
        NkAbrechnungDetailDTO detail = detail();

        assertThrows(FeatureDisabledException.class, () -> nkAbrechnungService.saveAbrechnung(1L, detail));
        verify(abrechnungRepository, never()).findById(anyLong());
    }

    @Test
    void setAbgerechnet_FeatureFlagAus_ThrowsFeatureDisabled() {
        featureFlagAus();

        assertThrows(FeatureDisabledException.class, () -> nkAbrechnungService.setAbgerechnet(1L, true));
        verify(abrechnungRepository, never()).save(any());
    }

    @Test
    void deleteAbrechnung_FeatureFlagAus_ThrowsFeatureDisabled() {
        featureFlagAus();

        assertThrows(FeatureDisabledException.class, () -> nkAbrechnungService.deleteAbrechnung(1L));
        verify(abrechnungRepository, never()).deleteById(anyLong());
    }

    // ==================== getAllAbrechnungen ====================

    @Test
    void getAllAbrechnungen_ReturnsListeNeusteZuerst() {
        featureFlagAn();
        when(abrechnungRepository.findAllByOrderByDatumVonDesc())
                .thenReturn(List.of(testAbrechnung1, testAbrechnung2));

        List<NkAbrechnung> result = nkAbrechnungService.getAllAbrechnungen();

        assertEquals(2, result.size());
        assertEquals("Nebenkostenabrechnung 2025", result.get(0).getBezeichnung());
        verify(hibernateFilterService).enableOrgFilter();
        verify(abrechnungRepository).findAllByOrderByDatumVonDesc();
    }

    // ==================== getAbrechnungDetail ====================

    @Test
    void getAbrechnungDetail_Found_ReturnsDetailMitBerechnung() {
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));

        Optional<NkAbrechnungDetailDTO> result = nkAbrechnungService.getAbrechnungDetail(1L);

        assertTrue(result.isPresent());
        assertNotNull(result.get().getBerechnung());
        // 9 Wohnungen x 365 Tage - der Nenner kommt aus dem erfassten Feld, nicht aus den Einheiten.
        assertEquals(3285, result.get().getBerechnung().getNenner());
        verify(hibernateFilterService).enableOrgFilter();
    }

    @Test
    void getAbrechnungDetail_NotFound_ReturnsEmpty() {
        featureFlagAn();
        when(abrechnungRepository.findFirstById(99L)).thenReturn(Optional.empty());

        Optional<NkAbrechnungDetailDTO> result = nkAbrechnungService.getAbrechnungDetail(99L);

        assertTrue(result.isEmpty());
    }

    @Test
    void getAbrechnungDetail_MitPositionen_LiefertMieterbloeckeInEinemAufruf() {
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));
        when(positionRepository.findByAbrechnungIdOrderByReihenfolge(1L))
                .thenReturn(List.of(umlagePosition(10L, 1, "900.00")));
        when(mieterRepository.findByZeitraumOverlapping(VON, BIS)).thenReturn(List.of(mieter(1L, VON, null)));
        when(mieterEinheitRepository.findByMieterIdIn(List.of(1L)))
                .thenReturn(List.of(new MieterEinheit(ORG_ID, 1L, 100L)));
        when(einheitRepository.findAllById(any())).thenReturn(List.of(einheit(100L, EinheitTyp.CONSUMER)));

        NkAbrechnungDetailDTO detail = nkAbrechnungService.getAbrechnungDetail(1L).orElseThrow();

        assertEquals(1, detail.getPositionen().size());
        assertEquals(1, detail.getBerechnung().getMieter().size());
        // 900.00 x 365 / 3285 = 100.00
        assertEquals(new BigDecimal("100.00"),
                detail.getBerechnung().getMieter().get(0).getZeilen().get(0).getBetrag());
    }

    @Test
    void getAbrechnungDetail_EinheitOhneKennzeichen_ZaehltNichtAlsWohnung() {
        // Zaehler und Nenner MUESSEN dieselbe Regel verwenden: Der Nenner zaehlt nur Einheiten mit
        // gesetztem Kennzeichen. Zaehlte der Mieterblock auch abgewaehlte Einheiten mit, erhielte
        // der Eigentuemer fuer seinen Allgemeinstrom-Messpunkt einen Umlageanteil - und die Summe
        // der Miettage uebersteige den Nenner, worauf jedes Speichern abgewiesen wuerde.
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));
        when(positionRepository.findByAbrechnungIdOrderByReihenfolge(1L))
                .thenReturn(List.of(umlagePosition(10L, 1, "900.00")));
        when(mieterRepository.findByZeitraumOverlapping(VON, BIS))
                .thenReturn(List.of(mieter(1L, VON, null), mieter(2L, VON, null)));
        when(mieterEinheitRepository.findByMieterIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(new MieterEinheit(ORG_ID, 1L, 100L),
                        new MieterEinheit(ORG_ID, 2L, 101L)));

        Einheit wohnung = einheit(100L, EinheitTyp.CONSUMER);
        Einheit allgemeinstrom = einheit(101L, EinheitTyp.CONSUMER);
        allgemeinstrom.setNebenkostenRelevant(false);
        when(einheitRepository.findAllById(any())).thenReturn(List.of(wohnung, allgemeinstrom));

        NkAbrechnungDetailDTO detail = nkAbrechnungService.getAbrechnungDetail(1L).orElseThrow();

        assertEquals(365L, detail.getBerechnung().getMieter().get(0).getTage());
        assertEquals(0L, detail.getBerechnung().getMieter().get(1).getTage());
        assertTrue(detail.getBerechnung().getMieter().get(1).isOhneWohnung());
        // Nur ein Mieter traegt: 900.00 x 365 / 3285 = 100.00, der Rest bleibt unverteilt.
        assertEquals(new BigDecimal("100.00"),
                detail.getBerechnung().getMieter().get(0).getZeilen().get(0).getBetrag());
        assertEquals(new BigDecimal("0.00"),
                detail.getBerechnung().getMieter().get(1).getZeilen().get(0).getBetrag());
    }

    // ==================== getVorlage ====================

    @Test
    void getVorlage_MitConsumerEinheiten_SchlaegtDerenAnzahlVor() {
        // Gezaehlt werden nur Verbraucher MIT gesetztem Kennzeichen - Allgemeinstrom und
        // PV-Eigenverbrauch sind keine Wohnungen und gehoeren nicht in den Nenner der Umlage.
        featureFlagAn();
        when(einheitRepository.countByTypAndNebenkostenRelevantTrue(EinheitTyp.CONSUMER)).thenReturn(9L);

        NkAbrechnungDetailDTO vorlage = nkAbrechnungService.getVorlage();

        assertEquals(Integer.valueOf(9), vorlage.getAnzahlWohnungenVorschlag());
        assertNotNull(vorlage.getAbrechnung());
        assertNull(vorlage.getAbrechnung().getId());
    }

    @Test
    void getVorlage_OhneConsumerEinheiten_VorschlagBleibtNull() {
        // Eine vorgeschlagene 0 verstiesse gegen den eigenen CHECK-Constraint (FR-2).
        featureFlagAn();
        when(einheitRepository.countByTypAndNebenkostenRelevantTrue(EinheitTyp.CONSUMER)).thenReturn(0L);

        NkAbrechnungDetailDTO vorlage = nkAbrechnungService.getVorlage();

        assertNull(vorlage.getAnzahlWohnungenVorschlag());
    }

    // ==================== createAbrechnung ====================

    @Test
    void createAbrechnung_ValidInput_SetztOrgIdServerseitig() {
        featureFlagAn();
        NkAbrechnung neu = neueAbrechnung();
        neu.setId(999L);
        neu.setOrgId(4711L);
        when(abrechnungRepository.save(any())).thenReturn(testAbrechnung1);

        NkAbrechnung result = nkAbrechnungService.createAbrechnung(neu);

        assertNotNull(result);
        ArgumentCaptor<NkAbrechnung> captor = ArgumentCaptor.forClass(NkAbrechnung.class);
        verify(abrechnungRepository).save(captor.capture());
        // Eine mitgeschickte ID und orgId werden ignoriert.
        assertNull(captor.getValue().getId());
        assertEquals(ORG_ID, captor.getValue().getOrgId());
        verify(hibernateFilterService).enableOrgFilter();
    }

    @Test
    void createAbrechnung_DatumVonNachDatumBis_ThrowsException() {
        featureFlagAn();
        NkAbrechnung neu = neueAbrechnung();
        neu.setDatumVon(LocalDate.of(2025, 12, 31));
        neu.setDatumBis(LocalDate.of(2025, 1, 1));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> nkAbrechnungService.createAbrechnung(neu));

        assertEquals("NK_FEHLER_ZEITRAUM", ex.getMessage());
        verify(abrechnungRepository, never()).save(any());
    }

    @Test
    void createAbrechnung_ZeitraumFehlt_ThrowsException() {
        featureFlagAn();
        NkAbrechnung neu = neueAbrechnung();
        neu.setDatumBis(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> nkAbrechnungService.createAbrechnung(neu));

        assertEquals("NK_FEHLER_ZEITRAUM_PFLICHT", ex.getMessage());
    }

    @Test
    void createAbrechnung_AnzahlWohnungenNull_ThrowsException() {
        // Ohne CONSUMER-Einheiten bleibt das Feld leer - das Speichern verlangt trotzdem eine Zahl.
        featureFlagAn();
        NkAbrechnung neu = neueAbrechnung();
        neu.setAnzahlWohnungen(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> nkAbrechnungService.createAbrechnung(neu));

        assertEquals("NK_FEHLER_ANZAHL_WOHNUNGEN", ex.getMessage());
    }

    @Test
    void createAbrechnung_AnzahlWohnungenNullWert_ThrowsException() {
        featureFlagAn();
        NkAbrechnung neu = neueAbrechnung();
        neu.setAnzahlWohnungen(0);

        assertThrows(IllegalArgumentException.class, () -> nkAbrechnungService.createAbrechnung(neu));
        verify(abrechnungRepository, never()).save(any());
    }

    // ==================== saveAbrechnung ====================

    @Test
    void saveAbrechnung_NotFound_ReturnsEmpty() {
        featureFlagAn();
        when(abrechnungRepository.findFirstById(99L)).thenReturn(Optional.empty());

        Optional<NkAbrechnungDetailDTO> result = nkAbrechnungService.saveAbrechnung(99L, detail());

        assertTrue(result.isEmpty());
        verify(abrechnungRepository, never()).save(any());
    }

    @Test
    void saveAbrechnung_BereitsAbgerechnet_ThrowsException() {
        // Der praktisch relevante Fall gleichzeitiger Bearbeitung.
        featureFlagAn();
        testAbrechnung1.setAbgerechnet(true);
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));

        NkAbrechnungDetailDTO detail = detail();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> nkAbrechnungService.saveAbrechnung(1L, detail));

        assertEquals("NK_FEHLER_ABGERECHNET", ex.getMessage());
        verify(positionRepository, never()).deleteByAbrechnungId(anyLong());
    }

    @Test
    void saveAbrechnung_ValidInput_NummeriertReihenfolgeLueckenlosNeu() {
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));
        stubPositionSave();

        NkAbrechnungDetailDTO detail = detail(
                umlageDTO("Allgemeinstrom", "900.00", 7),
                verbrauchDTO("Warmwasser", "3.5000", 3),
                zuschlagDTO("Verwaltung", "5.00", 99));

        Optional<NkAbrechnungDetailDTO> result = nkAbrechnungService.saveAbrechnung(1L, detail);

        assertTrue(result.isPresent());
        ArgumentCaptor<NkPosition> captor = ArgumentCaptor.forClass(NkPosition.class);
        verify(positionRepository, org.mockito.Mockito.times(3)).save(captor.capture());
        // Die Reihenfolge kommt aus der Listenposition (Drag & Drop), nicht aus dem Rumpf.
        assertEquals(1, captor.getAllValues().get(0).getReihenfolge());
        assertEquals(2, captor.getAllValues().get(1).getReihenfolge());
        assertEquals(3, captor.getAllValues().get(2).getReihenfolge());
        assertEquals(ORG_ID, captor.getAllValues().get(0).getOrgId());
    }

    @Test
    void saveAbrechnung_ErsetztAlteZeilenStattSieAbzugleichen() {
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));
        when(positionRepository.findByAbrechnungIdOrderByReihenfolge(1L))
                .thenReturn(List.of(umlagePosition(10L, 1, "900.00")));
        stubPositionSave();

        nkAbrechnungService.saveAbrechnung(1L, detail(umlageDTO("Allgemeinstrom", "800.00", 1)));

        verify(verbrauchRepository).deleteByPositionId(10L);
        verify(positionRepository).deleteByAbrechnungId(1L);
        verify(zusatzRepository).deleteByAbrechnungId(1L);
        verify(akontoRepository).deleteByAbrechnungId(1L);
    }

    @Test
    void saveAbrechnung_LeerePositionsliste_IstZulaessig() {
        // Speichern einer leeren Huelle muss moeglich bleiben.
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));

        Optional<NkAbrechnungDetailDTO> result = nkAbrechnungService.saveAbrechnung(1L, detail());

        assertTrue(result.isPresent());
        verify(positionRepository, never()).save(any());
    }

    @Test
    void saveAbrechnung_AendertKopfdaten_UebernimmtSieAufDieBestehendeAbrechnung() {
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));

        NkAbrechnungDetailDTO detail = detail();
        detail.getAbrechnung().setBezeichnung("Neu benannt");
        detail.getAbrechnung().setAnzahlWohnungen(12);

        nkAbrechnungService.saveAbrechnung(1L, detail);

        assertEquals("Neu benannt", testAbrechnung1.getBezeichnung());
        assertEquals(Integer.valueOf(12), testAbrechnung1.getAnzahlWohnungen());
        verify(abrechnungRepository).save(testAbrechnung1);
    }

    // ---------- Nennerpruefung (FR-2) ----------

    @Test
    void saveAbrechnung_AnzahlWohnungenZuKlein_ThrowsExceptionMitBeidenWerten() {
        // Zwei ganzjaehrige Mieter mit je einer Wohnung, aber nur 1 Wohnung erfasst:
        // Σ Tage(i) = 730 > Nenner = 365.
        featureFlagAn();
        testAbrechnung1.setAnzahlWohnungen(1);
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));
        zweiGanzjaehrigeMieter();

        NkAbrechnungDetailDTO detail = detail();
        detail.getAbrechnung().setAnzahlWohnungen(1);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> nkAbrechnungService.saveAbrechnung(1L, detail));

        // Klartext statt Uebersetzungs-Key: Die Meldung nennt beide Zahlen, ein Key mit
        // angehaengten Werten liesse sich im Frontend nicht aufloesen.
        assertTrue(ex.getMessage().contains("Anzahl Wohnungen"), ex.getMessage());
        assertTrue(ex.getMessage().contains("730"), ex.getMessage());
        assertTrue(ex.getMessage().contains("365"), ex.getMessage());
        verify(positionRepository, never()).save(any());
    }

    @Test
    void saveAbrechnung_MehrWohnungenAlsBelegt_IstZulaessigerLeerstand() {
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));
        zweiGanzjaehrigeMieter();

        // 9 Wohnungen erfasst, nur 2 belegt -> 730 <= 3285, der Rest bleibt unverteilt.
        Optional<NkAbrechnungDetailDTO> result = nkAbrechnungService.saveAbrechnung(1L, detail());

        assertTrue(result.isPresent());
        assertEquals(730, result.get().getBerechnung().getSummeTage());
    }

    // ---------- Umlage pro Person (FR-2) ----------

    @Test
    void saveAbrechnung_UmlageProPerson_WirdGespeichert() {
        // Der Fall, der in der ersten Fassung fehlte: pruefePositionen hatte einen eigenen switch
        // ueber die Positionsart, dessen default-Zweig NK_FEHLER_POSITION_ART wirft. Die neue Art
        // fiel dort hinein - jedes Speichern wurde abgewiesen, obwohl das Rechnen stimmte.
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));
        stubPositionSave();

        NkAbrechnungDetailDTO detail = detail(umlagePersonDTO("Gruenabfuhr", "1000.00", 1));

        assertTrue(nkAbrechnungService.saveAbrechnung(1L, detail).isPresent());

        ArgumentCaptor<NkPosition> captor = ArgumentCaptor.forClass(NkPosition.class);
        verify(positionRepository).save(captor.capture());
        assertEquals(NkPositionsart.UMLAGE_PERSON, captor.getValue().getArt());
        assertEquals(new BigDecimal("1000.00"), captor.getValue().getTotalbetrag());
        // Einheit bleibt erhalten - anders als bei ANTEIL, wo sie geleert wird.
        assertEquals(Mengeneinheit.CHF, captor.getValue().getEinheit());
    }

    @Test
    void saveAbrechnung_UmlageProPersonOhneTotalbetrag_ThrowsException() {
        // Gleiche Pflichtfelder wie bei der Umlage pro Wohnung - also auch dieselbe Meldung.
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));

        NkPositionDTO ohneBetrag = umlagePersonDTO("Gruenabfuhr", "1000.00", 1);
        ohneBetrag.setTotalbetrag(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> nkAbrechnungService.saveAbrechnung(1L, detail(ohneBetrag)));
        assertEquals("NK_FEHLER_POSITION_UMLAGE", ex.getMessage());
    }

    // ---------- Nennerpruefung Personen (FR-2) ----------

    @Test
    void saveAbrechnung_AnzahlPersonenZuKlein_ThrowsExceptionMitBeidenWerten() {
        // Zwei ganzjaehrige Mieter mit je 3 Personen, aber nur 2 Personen erfasst:
        // Σ (Tage x Personen) = 2190 > Nenner = 730.
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));
        zweiGanzjaehrigeMieter();

        NkAbrechnungDetailDTO detail = detail(umlagePersonDTO("Gruenabfuhr", "1000.00", 1));
        detail.getAbrechnung().setAnzahlPersonen(2);
        detail.setPersonen(List.of(new NkPersonDTO(1L, 3), new NkPersonDTO(2L, 3)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> nkAbrechnungService.saveAbrechnung(1L, detail));

        assertTrue(ex.getMessage().contains("Anzahl Personen"), ex.getMessage());
        assertTrue(ex.getMessage().contains("2190"), ex.getMessage());
        assertTrue(ex.getMessage().contains("730"), ex.getMessage());
        verify(positionRepository, never()).save(any());
    }

    @Test
    void saveAbrechnung_AnzahlPersonenZuKleinOhnePersonenumlage_IstZulaessig() {
        // Ohne eine Position dieser Art hat die Personenzahl keine Wirkung - dann darf sie das
        // Speichern auch nicht blockieren.
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));
        zweiGanzjaehrigeMieter();

        NkAbrechnungDetailDTO detail = detail(umlageDTO("Allgemeinstrom", "900.00", 1));
        detail.getAbrechnung().setAnzahlPersonen(2);
        detail.setPersonen(List.of(new NkPersonDTO(1L, 3), new NkPersonDTO(2L, 3)));

        assertTrue(nkAbrechnungService.saveAbrechnung(1L, detail).isPresent());
    }

    @Test
    void saveAbrechnung_PersonenzahlGleichVorgabe_WirdNichtGespeichert() {
        // Sonst entstuende fuer jeden Mieter jeder Abrechnung eine Zeile, nur um "1" festzuhalten -
        // und weil nk_person mit ON DELETE RESTRICT auf den Mieter zeigt, waere danach kein Mieter
        // mehr loeschbar, der ueberhaupt in einer Abrechnung vorkommt.
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));
        zweiGanzjaehrigeMieter();

        NkAbrechnungDetailDTO detail = detail();
        detail.setPersonen(List.of(new NkPersonDTO(1L, 1), new NkPersonDTO(2L, 4)));

        nkAbrechnungService.saveAbrechnung(1L, detail);

        ArgumentCaptor<NkPerson> captor = ArgumentCaptor.forClass(NkPerson.class);
        verify(personRepository).save(captor.capture());
        assertEquals(2L, captor.getValue().getMieterId());
        assertEquals(4, captor.getValue().getAnzahlPersonen());
    }

    @Test
    void saveAbrechnung_OhneAnzahlPersonen_UebernimmtDieAnzahlWohnungen() {
        // Ein Aufrufer, der das Feld nicht kennt, soll dieselbe Rechnung bekommen wie vorher.
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));
        zweiGanzjaehrigeMieter();

        NkAbrechnungDetailDTO detail = detail();
        detail.getAbrechnung().setAnzahlPersonen(null);

        Optional<NkAbrechnungDetailDTO> result = nkAbrechnungService.saveAbrechnung(1L, detail);

        assertTrue(result.isPresent());
        assertEquals(9, result.get().getAbrechnung().getAnzahlPersonen());
        assertEquals(3285, result.get().getBerechnung().getNennerPerson());
    }

    @Test
    void saveAbrechnung_PersonenzahlUnterEins_ThrowsException() {
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));
        zweiGanzjaehrigeMieter();

        NkAbrechnungDetailDTO detail = detail();
        detail.setPersonen(List.of(new NkPersonDTO(1L, 0)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> nkAbrechnungService.saveAbrechnung(1L, detail));
        assertEquals("NK_FEHLER_ANZAHL_PERSONEN", ex.getMessage());
    }

    // ---------- Art-abhaengige Pflichtfelder (FR-2) ----------

    @Test
    void saveAbrechnung_PositionOhneArt_ThrowsException() {
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));

        NkPositionDTO ohneArt = new NkPositionDTO();
        ohneArt.setBezeichnung("Irgendwas");
        NkAbrechnungDetailDTO detail = detail(ohneArt);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> nkAbrechnungService.saveAbrechnung(1L, detail));

        assertEquals("NK_FEHLER_POSITION_ART", ex.getMessage());
    }

    @Test
    void saveAbrechnung_PositionOhneBezeichnung_ThrowsException() {
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));

        NkPositionDTO ohneBezeichnung = umlageDTO("  ", "900.00", 1);
        NkAbrechnungDetailDTO detail = detail(ohneBezeichnung);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> nkAbrechnungService.saveAbrechnung(1L, detail));

        assertEquals("NK_FEHLER_POSITION_BEZEICHNUNG", ex.getMessage());
    }

    @Test
    void saveAbrechnung_UmlageOhneTotalbetrag_ThrowsException() {
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));

        NkPositionDTO ohneBetrag = umlageDTO("Allgemeinstrom", "900.00", 1);
        ohneBetrag.setTotalbetrag(null);
        NkAbrechnungDetailDTO detail = detail(ohneBetrag);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> nkAbrechnungService.saveAbrechnung(1L, detail));

        assertEquals("NK_FEHLER_POSITION_UMLAGE", ex.getMessage());
    }

    @Test
    void saveAbrechnung_UmlageOhneEinheit_ThrowsException() {
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));

        NkPositionDTO ohneEinheit = umlageDTO("Allgemeinstrom", "900.00", 1);
        ohneEinheit.setEinheit(null);
        NkAbrechnungDetailDTO detail = detail(ohneEinheit);

        assertThrows(IllegalArgumentException.class, () -> nkAbrechnungService.saveAbrechnung(1L, detail));
    }

    @Test
    void saveAbrechnung_UmlageOhneGesamtmenge_IstZulaessig() {
        // Die Gesamtmenge ist optional - ohne sie wird nur der Betrag verteilt.
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));
        stubPositionSave();

        NkPositionDTO ohneMenge = umlageDTO("Allgemeinstrom", "900.00", 1);
        ohneMenge.setGesamtmenge(null);

        assertTrue(nkAbrechnungService.saveAbrechnung(1L, detail(ohneMenge)).isPresent());
    }

    @Test
    void saveAbrechnung_VerbrauchOhneBetragProEinheit_ThrowsException() {
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));

        NkPositionDTO ohnePreis = verbrauchDTO("Warmwasser", "3.5000", 1);
        ohnePreis.setBetragProEinheit(null);
        NkAbrechnungDetailDTO detail = detail(ohnePreis);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> nkAbrechnungService.saveAbrechnung(1L, detail));

        assertEquals("NK_FEHLER_POSITION_VERBRAUCH", ex.getMessage());
    }

    @Test
    void saveAbrechnung_ZuschlagOhneProzentsatz_ThrowsException() {
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));

        NkPositionDTO ohneProzent = zuschlagDTO("Verwaltung", "5.00", 1);
        ohneProzent.setProzentsatz(null);
        NkAbrechnungDetailDTO detail = detail(ohneProzent);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> nkAbrechnungService.saveAbrechnung(1L, detail));

        assertEquals("NK_FEHLER_POSITION_ZUSCHLAG", ex.getMessage());
    }

    @Test
    void saveAbrechnung_AnteilOhneTotalbetrag_ThrowsException() {
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));

        NkPositionDTO ohneTotal = new NkPositionDTO();
        ohneTotal.setArt(NkPositionsart.ANTEIL);
        ohneTotal.setBezeichnung("Heizkosten");
        ohneTotal.setReihenfolge(1);
        NkAbrechnungDetailDTO detail = detail(ohneTotal);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> nkAbrechnungService.saveAbrechnung(1L, detail));

        assertEquals("NK_FEHLER_POSITION_ANTEIL", ex.getMessage());
    }

    @Test
    void saveAbrechnung_Anteil_LeertNichtZutreffendeFelderUndSpeichertProzentsaetze() {
        // Wer die Art einer Zeile wechselt, schickt sonst Reste der alten Art mit und laeuft in
        // den CHECK-Constraint, ohne dass die Maske etwas Falsches zeigte.
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));
        stubPositionSave();
        zweiGanzjaehrigeMieter();

        NkPositionDTO heizung = new NkPositionDTO();
        heizung.setArt(NkPositionsart.ANTEIL);
        heizung.setBezeichnung("Heizkosten");
        heizung.setReihenfolge(1);
        heizung.setTotalbetrag(new BigDecimal("2400.00"));
        heizung.setEinheit(Mengeneinheit.M3);
        heizung.setProzentsatz(new BigDecimal("5.00"));
        heizung.setVerbraeuche(List.of(new NkVerbrauchDTO(1L, new BigDecimal("60.000")),
                new NkVerbrauchDTO(2L, new BigDecimal("40.000"))));

        nkAbrechnungService.saveAbrechnung(1L, detail(heizung));

        ArgumentCaptor<NkPosition> captor = ArgumentCaptor.forClass(NkPosition.class);
        verify(positionRepository).save(captor.capture());
        assertEquals(new BigDecimal("2400.00"), captor.getValue().getTotalbetrag());
        assertNull(captor.getValue().getEinheit());
        assertNull(captor.getValue().getProzentsatz());
        // Die Prozentsaetze je Mieter stehen in derselben Tabelle wie Verbrauchsmengen.
        verify(verbrauchRepository, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void saveAbrechnung_ProzentsatzUeber100_ThrowsException() {
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));

        NkAbrechnungDetailDTO detail = detail(zuschlagDTO("Verwaltung", "101.00", 1));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> nkAbrechnungService.saveAbrechnung(1L, detail));

        assertEquals("NK_FEHLER_PROZENTSATZ", ex.getMessage());
    }

    @Test
    void saveAbrechnung_NegativerProzentsatz_ThrowsException() {
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));

        NkAbrechnungDetailDTO detail = detail(zuschlagDTO("Verwaltung", "-1.00", 1));

        assertThrows(IllegalArgumentException.class, () -> nkAbrechnungService.saveAbrechnung(1L, detail));
    }

    @Test
    void saveAbrechnung_ZuschlagMitArtfremdenFeldern_LeertSieStattAbzuweisen() {
        // Wer die Art einer Zeile wechselt, schickt Reste der alten Art mit; sie liefen sonst in
        // den CHECK-Constraint, ohne dass die Maske etwas Falsches zeigte.
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));
        stubPositionSave();

        NkPositionDTO zuschlag = zuschlagDTO("Verwaltung", "5.00", 1);
        zuschlag.setTotalbetrag(new BigDecimal("900.00"));
        zuschlag.setGesamtmenge(new BigDecimal("500.000"));
        zuschlag.setBetragProEinheit(new BigDecimal("3.5000"));
        zuschlag.setEinheit(Mengeneinheit.M3);

        nkAbrechnungService.saveAbrechnung(1L, detail(zuschlag));

        ArgumentCaptor<NkPosition> captor = ArgumentCaptor.forClass(NkPosition.class);
        verify(positionRepository).save(captor.capture());
        assertNull(captor.getValue().getTotalbetrag());
        assertNull(captor.getValue().getGesamtmenge());
        assertNull(captor.getValue().getBetragProEinheit());
        assertNull(captor.getValue().getEinheit());
        assertEquals(new BigDecimal("5.00"), captor.getValue().getProzentsatz());
    }

    @Test
    void saveAbrechnung_UmlageMitZuschlagsfeldern_LeertSie() {
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));
        stubPositionSave();

        NkPositionDTO umlage = umlageDTO("Allgemeinstrom", "900.00", 1);
        umlage.setProzentsatz(new BigDecimal("5.00"));
        umlage.setBetragProEinheit(new BigDecimal("3.5000"));

        nkAbrechnungService.saveAbrechnung(1L, detail(umlage));

        ArgumentCaptor<NkPosition> captor = ArgumentCaptor.forClass(NkPosition.class);
        verify(positionRepository).save(captor.capture());
        assertNull(captor.getValue().getProzentsatz());
        assertNull(captor.getValue().getBetragProEinheit());
        assertEquals(new BigDecimal("900.00"), captor.getValue().getTotalbetrag());
    }

    // ---------- Verbrauchsmengen, Zusatzpositionen, Akonto ----------

    @Test
    void saveAbrechnung_VerbrauchsmengeJeMieter_WirdMitDerPositionGespeichert() {
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));
        stubPositionSave();

        NkPositionDTO warmwasser = verbrauchDTO("Warmwasser", "3.5000", 1);
        warmwasser.setVerbraeuche(List.of(new NkVerbrauchDTO(7L, new BigDecimal("12.500"))));

        nkAbrechnungService.saveAbrechnung(1L, detail(warmwasser));

        ArgumentCaptor<NkVerbrauch> captor = ArgumentCaptor.forClass(NkVerbrauch.class);
        verify(verbrauchRepository).save(captor.capture());
        assertEquals(Long.valueOf(7L), captor.getValue().getMieterId());
        assertEquals(new BigDecimal("12.500"), captor.getValue().getMenge());
        assertEquals(ORG_ID, captor.getValue().getOrgId());
    }

    @Test
    void saveAbrechnung_VerbrauchsmengeOhneWert_WirdUebersprungen() {
        // Eine nicht erfasste Menge ist kein Fehler - sonst liesse sich nicht zwischenspeichern.
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));
        stubPositionSave();

        NkPositionDTO warmwasser = verbrauchDTO("Warmwasser", "3.5000", 1);
        warmwasser.setVerbraeuche(List.of(
                new NkVerbrauchDTO(7L, null),
                new NkVerbrauchDTO(null, new BigDecimal("5.000")),
                new NkVerbrauchDTO(8L, new BigDecimal("5.000"))));

        nkAbrechnungService.saveAbrechnung(1L, detail(warmwasser));

        verify(verbrauchRepository, org.mockito.Mockito.times(1)).save(any());
    }

    @Test
    void saveAbrechnung_MengenZuNichtVerbrauchsposition_WerdenIgnoriert() {
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));
        stubPositionSave();

        NkPositionDTO umlage = umlageDTO("Allgemeinstrom", "900.00", 1);
        umlage.setVerbraeuche(List.of(new NkVerbrauchDTO(7L, new BigDecimal("12.500"))));

        nkAbrechnungService.saveAbrechnung(1L, detail(umlage));

        verify(verbrauchRepository, never()).save(any());
    }

    @Test
    void saveAbrechnung_Zusatzposition_WirdMitOrgIdUndAbrechnungGespeichert() {
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));

        NkAbrechnungDetailDTO detail = detail();
        detail.setZusaetze(List.of(zusatzDTO(7L, 1, "Saunagaenge")));

        nkAbrechnungService.saveAbrechnung(1L, detail);

        ArgumentCaptor<NkZusatz> captor = ArgumentCaptor.forClass(NkZusatz.class);
        verify(zusatzRepository).save(captor.capture());
        assertEquals(ORG_ID, captor.getValue().getOrgId());
        assertEquals(Long.valueOf(1L), captor.getValue().getAbrechnungId());
        assertEquals(Integer.valueOf(1), captor.getValue().getReihenfolge());
    }

    @Test
    void saveAbrechnung_ZusatzpositionOhneReihenfolge_NummeriertJeMieterDurch() {
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));

        NkZusatzDTO erste = zusatzDTO(7L, null, "Sauna");
        NkZusatzDTO zweite = zusatzDTO(7L, null, "Schluessel");
        NkZusatzDTO andererMieter = zusatzDTO(8L, null, "Sauna");
        NkAbrechnungDetailDTO detail = detail();
        detail.setZusaetze(List.of(erste, zweite, andererMieter));

        nkAbrechnungService.saveAbrechnung(1L, detail);

        ArgumentCaptor<NkZusatz> captor = ArgumentCaptor.forClass(NkZusatz.class);
        verify(zusatzRepository, org.mockito.Mockito.times(3)).save(captor.capture());
        assertEquals(Integer.valueOf(1), captor.getAllValues().get(0).getReihenfolge());
        assertEquals(Integer.valueOf(2), captor.getAllValues().get(1).getReihenfolge());
        // Die Nummer ist je Abrechnung UND Mieter eindeutig - der zweite Mieter faengt neu an.
        assertEquals(Integer.valueOf(1), captor.getAllValues().get(2).getReihenfolge());
    }

    @Test
    void saveAbrechnung_ZusatzpositionOhneMieter_ThrowsException() {
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));

        NkAbrechnungDetailDTO detail = detail();
        detail.setZusaetze(List.of(zusatzDTO(null, 1, "Sauna")));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> nkAbrechnungService.saveAbrechnung(1L, detail));

        assertEquals("Zusatzposition ohne Mieter", ex.getMessage());
    }

    @Test
    void saveAbrechnung_Akonto_WirdMitOrgIdGespeichert() {
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));

        NkAkontoDTO dto = new NkAkontoDTO();
        dto.setMieterId(7L);
        dto.setAnzahlMonate(new BigDecimal("4.50"));
        dto.setBetragProMonat(new BigDecimal("150.00"));
        dto.setKorrektur(new BigDecimal("-50.00"));
        NkAbrechnungDetailDTO detail = detail();
        detail.setAkonto(List.of(dto));

        nkAbrechnungService.saveAbrechnung(1L, detail);

        ArgumentCaptor<NkAkonto> captor = ArgumentCaptor.forClass(NkAkonto.class);
        verify(akontoRepository).save(captor.capture());
        assertEquals(ORG_ID, captor.getValue().getOrgId());
        assertEquals(new BigDecimal("4.50"), captor.getValue().getAnzahlMonate());
        // Der Korrekturbetrag darf als einziges Feld negativ sein.
        assertEquals(new BigDecimal("-50.00"), captor.getValue().getKorrektur());
    }

    @Test
    void saveAbrechnung_AkontoOhneWerte_WirdMitNullBetraegenGespeichert() {
        // Fehlt das Stammdatum, bleibt das Feld leer und die Abrechnung ist trotzdem speicherbar.
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));

        NkAkontoDTO leer = new NkAkontoDTO();
        leer.setMieterId(7L);
        NkAbrechnungDetailDTO detail = detail();
        detail.setAkonto(List.of(leer));

        assertTrue(nkAbrechnungService.saveAbrechnung(1L, detail).isPresent());

        ArgumentCaptor<NkAkonto> captor = ArgumentCaptor.forClass(NkAkonto.class);
        verify(akontoRepository).save(captor.capture());
        assertEquals(BigDecimal.ZERO, captor.getValue().getBetragProMonat());
        assertEquals(BigDecimal.ZERO, captor.getValue().getKorrektur());
    }

    @Test
    void saveAbrechnung_AkontoOhneMieter_ThrowsException() {
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));

        NkAkontoDTO ohneMieter = new NkAkontoDTO();
        ohneMieter.setAnzahlMonate(new BigDecimal("4.50"));
        NkAbrechnungDetailDTO detail = detail();
        detail.setAkonto(List.of(ohneMieter));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> nkAbrechnungService.saveAbrechnung(1L, detail));

        assertEquals("Akonto ohne Mieter", ex.getMessage());
    }

    // ==================== setAbgerechnet ====================

    @Test
    void setAbgerechnet_Found_SetztDasFlag() {
        featureFlagAn();
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));
        when(abrechnungRepository.save(testAbrechnung1)).thenReturn(testAbrechnung1);

        Optional<NkAbrechnung> result = nkAbrechnungService.setAbgerechnet(1L, true);

        assertTrue(result.isPresent());
        assertTrue(result.get().isAbgerechnet());
        verify(hibernateFilterService).enableOrgFilter();
    }

    @Test
    void setAbgerechnet_AufAbgeschlossenerAbrechnung_GibtSieWiederFrei() {
        // Der einzige Schreibzugriff, der auf einer abgeschlossenen Abrechnung erlaubt ist -
        // sonst liesse sie sich nie wieder oeffnen.
        featureFlagAn();
        testAbrechnung1.setAbgerechnet(true);
        when(abrechnungRepository.findFirstById(1L)).thenReturn(Optional.of(testAbrechnung1));
        when(abrechnungRepository.save(testAbrechnung1)).thenReturn(testAbrechnung1);

        Optional<NkAbrechnung> result = nkAbrechnungService.setAbgerechnet(1L, false);

        assertTrue(result.isPresent());
        assertFalse(result.get().isAbgerechnet());
    }

    @Test
    void setAbgerechnet_NotFound_ReturnsEmpty() {
        featureFlagAn();
        when(abrechnungRepository.findFirstById(99L)).thenReturn(Optional.empty());

        assertTrue(nkAbrechnungService.setAbgerechnet(99L, true).isEmpty());
        verify(abrechnungRepository, never()).save(any());
    }

    // ==================== deleteAbrechnung ====================

    @Test
    void deleteAbrechnung_Exists_ReturnsTrue() {
        featureFlagAn();
        when(abrechnungRepository.existsById(1L)).thenReturn(true);

        assertTrue(nkAbrechnungService.deleteAbrechnung(1L));

        verify(abrechnungRepository).deleteById(1L);
        verify(hibernateFilterService).enableOrgFilter();
    }

    @Test
    void deleteAbrechnung_NotExists_ReturnsFalse() {
        featureFlagAn();
        when(abrechnungRepository.existsById(99L)).thenReturn(false);

        assertFalse(nkAbrechnungService.deleteAbrechnung(99L));

        verify(abrechnungRepository, never()).deleteById(anyLong());
    }

    // ==================== Testdaten und Stubs ====================

    private void featureFlagAn() {
        when(organizationContextService.getCurrentOrgId()).thenReturn(ORG_ID);
        when(featureFlagService.isEnabled(ORG_ID, FeatureFlag.NEBENKOSTENABRECHNUNG)).thenReturn(true);
    }

    private void featureFlagAus() {
        when(organizationContextService.getCurrentOrgId()).thenReturn(ORG_ID);
        when(featureFlagService.isEnabled(ORG_ID, FeatureFlag.NEBENKOSTENABRECHNUNG)).thenReturn(false);
    }

    /** Vergibt beim Speichern eine ID, damit die Verbrauchsmengen eine Position referenzieren. */
    private void stubPositionSave() {
        AtomicLong naechsteId = new AtomicLong(100L);
        when(positionRepository.save(any(NkPosition.class))).thenAnswer(aufruf -> {
            NkPosition position = aufruf.getArgument(0);
            if (position.getId() == null) {
                position.setId(naechsteId.getAndIncrement());
            }
            return position;
        });
    }

    /** Zwei ganzjaehrige Mieter mit je einer CONSUMER-Einheit — zusammen 730 Wohnungs-Tage. */
    private void zweiGanzjaehrigeMieter() {
        when(mieterRepository.findByZeitraumOverlapping(VON, BIS))
                .thenReturn(List.of(mieter(1L, VON, null), mieter(2L, VON, null)));
        when(mieterEinheitRepository.findByMieterIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(new MieterEinheit(ORG_ID, 1L, 100L), new MieterEinheit(ORG_ID, 2L, 101L)));
        when(einheitRepository.findAllById(any()))
                .thenReturn(List.of(einheit(100L, EinheitTyp.CONSUMER), einheit(101L, EinheitTyp.CONSUMER)));
    }

    private NkAbrechnung neueAbrechnung() {
        NkAbrechnung abrechnung = new NkAbrechnung();
        abrechnung.setBezeichnung("Nebenkostenabrechnung 2025");
        abrechnung.setDatumVon(VON);
        abrechnung.setDatumBis(BIS);
        abrechnung.setAnzahlWohnungen(9);
        return abrechnung;
    }

    private NkAbrechnungDetailDTO detail(NkPositionDTO... positionen) {
        NkAbrechnungDetailDTO detail = new NkAbrechnungDetailDTO();
        detail.setAbrechnung(neueAbrechnung());
        detail.setPositionen(List.of(positionen));
        return detail;
    }

    private NkPositionDTO umlageDTO(String bezeichnung, String totalbetrag, int reihenfolge) {
        NkPositionDTO dto = new NkPositionDTO();
        dto.setArt(NkPositionsart.UMLAGE);
        dto.setBezeichnung(bezeichnung);
        dto.setReihenfolge(reihenfolge);
        dto.setEinheit(Mengeneinheit.M3);
        dto.setTotalbetrag(new BigDecimal(totalbetrag));
        dto.setGesamtmenge(new BigDecimal("500.000"));
        return dto;
    }

    private NkPositionDTO umlagePersonDTO(String bezeichnung, String totalbetrag, int reihenfolge) {
        NkPositionDTO dto = new NkPositionDTO();
        dto.setArt(NkPositionsart.UMLAGE_PERSON);
        dto.setBezeichnung(bezeichnung);
        dto.setReihenfolge(reihenfolge);
        dto.setEinheit(Mengeneinheit.CHF);
        dto.setTotalbetrag(new BigDecimal(totalbetrag));
        return dto;
    }

    private NkPositionDTO verbrauchDTO(String bezeichnung, String betragProEinheit, int reihenfolge) {
        NkPositionDTO dto = new NkPositionDTO();
        dto.setArt(NkPositionsart.VERBRAUCH);
        dto.setBezeichnung(bezeichnung);
        dto.setReihenfolge(reihenfolge);
        dto.setEinheit(Mengeneinheit.KWH);
        dto.setBetragProEinheit(new BigDecimal(betragProEinheit));
        return dto;
    }

    private NkPositionDTO zuschlagDTO(String bezeichnung, String prozentsatz, int reihenfolge) {
        NkPositionDTO dto = new NkPositionDTO();
        dto.setArt(NkPositionsart.ZUSCHLAG);
        dto.setBezeichnung(bezeichnung);
        dto.setReihenfolge(reihenfolge);
        dto.setProzentsatz(new BigDecimal(prozentsatz));
        return dto;
    }

    private NkZusatzDTO zusatzDTO(Long mieterId, Integer reihenfolge, String bezeichnung) {
        NkZusatzDTO dto = new NkZusatzDTO();
        dto.setMieterId(mieterId);
        dto.setReihenfolge(reihenfolge);
        dto.setBezeichnung(bezeichnung);
        dto.setEinheit(Mengeneinheit.STUECK);
        dto.setMenge(new BigDecimal("4.000"));
        dto.setBetragProEinheit(new BigDecimal("6.5000"));
        return dto;
    }

    private NkPosition umlagePosition(Long id, int reihenfolge, String totalbetrag) {
        NkPosition position = new NkPosition();
        position.setId(id);
        position.setOrgId(ORG_ID);
        position.setAbrechnungId(1L);
        position.setArt(NkPositionsart.UMLAGE);
        position.setBezeichnung("Allgemeinstrom");
        position.setReihenfolge(reihenfolge);
        position.setEinheit(Mengeneinheit.M3);
        position.setTotalbetrag(new BigDecimal(totalbetrag));
        return position;
    }

    private Mieter mieter(Long id, LocalDate mietbeginn, LocalDate mietende) {
        Mieter mieter = new Mieter();
        mieter.setId(id);
        mieter.setOrgId(ORG_ID);
        mieter.setName("Mieter " + id);
        mieter.setMietbeginn(mietbeginn);
        mieter.setMietende(mietende);
        mieter.setAkontoProMonat(new BigDecimal("150.00"));
        return mieter;
    }

    private Einheit einheit(Long id, EinheitTyp typ) {
        Einheit einheit = new Einheit("Einheit " + id, typ);
        einheit.setId(id);
        einheit.setOrgId(ORG_ID);
        return einheit;
    }
}
