package ch.nacht.service;

import ch.nacht.entity.MeldungLevel;
import ch.nacht.entity.Systemmeldung;
import ch.nacht.repository.SystemmeldungRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SystemmeldungServiceTest {

    @Mock
    private SystemmeldungRepository systemmeldungRepository;

    @Mock
    private OrganizationContextService organizationContextService;

    @Mock
    private HibernateFilterService hibernateFilterService;

    @InjectMocks
    private SystemmeldungService systemmeldungService;

    // --- erfasseAudit: ein Eintrag pro Vorgang, direkt erledigt (keine Dedup) ---

    @Test
    void erfasseAudit_SpeichertNeuenEintragDirektAlsErledigt() {
        systemmeldungService.erfasseAudit(42L, MeldungLevel.INFO,
                SystemmeldungService.KATEGORIE_VERTEILUNG,
                SystemmeldungService.KEY_VERTEILUNG_MANUELL, "testuser, 01.01.2024–31.01.2024, EQUAL_SHARE");

        ArgumentCaptor<Systemmeldung> captor = ArgumentCaptor.forClass(Systemmeldung.class);
        verify(systemmeldungRepository).saveAndFlush(captor.capture());

        Systemmeldung gespeichert = captor.getValue();
        assertEquals(42L, gespeichert.getOrgId());
        assertEquals(MeldungLevel.INFO, gespeichert.getLevel());
        assertEquals(SystemmeldungService.KATEGORIE_VERTEILUNG, gespeichert.getKategorie());
        assertEquals(SystemmeldungService.KEY_VERTEILUNG_MANUELL, gespeichert.getMeldungKey());
        assertEquals("testuser, 01.01.2024–31.01.2024, EQUAL_SHARE", gespeichert.getParameter());
        assertTrue(gespeichert.isErledigt());
        assertTrue(gespeichert.isErledigtAutomatisch());
        assertNotNull(gespeichert.getErledigtAm());
        assertEquals(1, gespeichert.getZaehler());
    }

    @Test
    void erfasseAudit_KeineDeduplizierung_LiestKeinenOffenenEintrag() {
        systemmeldungService.erfasseAudit(42L, MeldungLevel.INFO,
                SystemmeldungService.KATEGORIE_VERTEILUNG,
                SystemmeldungService.KEY_VERTEILUNG_MANUELL, "testuser");

        // Audit-Einträge werden nie zusammengefasst – kein Dedup-Lookup, kein Increment.
        verify(systemmeldungRepository, never())
                .findByOrgIdAndMeldungKeyAndErledigtFalse(anyLong(), anyString());
        verify(systemmeldungRepository, never()).save(any());
    }

    @Test
    void erfasseAudit_UeberlangerParameter_WirdAuf500ZeichenGekuerzt() {
        String zuLang = "x".repeat(600);

        systemmeldungService.erfasseAudit(42L, MeldungLevel.INFO,
                SystemmeldungService.KATEGORIE_VERTEILUNG,
                SystemmeldungService.KEY_VERTEILUNG_MANUELL, zuLang);

        ArgumentCaptor<Systemmeldung> captor = ArgumentCaptor.forClass(Systemmeldung.class);
        verify(systemmeldungRepository).saveAndFlush(captor.capture());
        assertEquals(500, captor.getValue().getParameter().length());
    }

    // --- erfasse: Dedup-Verhalten (Abgrenzung zu erfasseAudit) ---

    @Test
    void erfasse_OffenerEintragVorhanden_ErhoehtZaehlerStattNeuemEintrag() {
        Systemmeldung offen = new Systemmeldung(MeldungLevel.WARN, SystemmeldungService.KATEGORIE_MQTT,
                SystemmeldungService.KEY_ZAEHLER_AUSFALL, "alt",
                java.time.LocalDateTime.of(2024, 1, 1, 0, 0), java.time.LocalDateTime.of(2024, 1, 1, 0, 0));
        offen.setOrgId(42L);
        when(systemmeldungRepository.findByOrgIdAndMeldungKeyAndErledigtFalse(
                42L, SystemmeldungService.KEY_ZAEHLER_AUSFALL)).thenReturn(Optional.of(offen));

        systemmeldungService.erfasse(42L, MeldungLevel.WARN, SystemmeldungService.KATEGORIE_MQTT,
                SystemmeldungService.KEY_ZAEHLER_AUSFALL, "neu");

        verify(systemmeldungRepository, never()).saveAndFlush(any());
        verify(systemmeldungRepository).save(offen);
        assertEquals(2, offen.getZaehler());
        assertEquals("neu", offen.getParameter());
    }

    // --- erfasse: neuer Eintrag und das Rennen um den UNIQUE-Teil-Index ---

    @Test
    void erfasse_KeinOffenerEintrag_LegtNeuenAn() {
        when(systemmeldungRepository.findByOrgIdAndMeldungKeyAndErledigtFalse(
                42L, SystemmeldungService.KEY_KEINE_BILANZDATEN)).thenReturn(Optional.empty());

        systemmeldungService.erfasse(42L, MeldungLevel.ERROR,
                SystemmeldungService.KATEGORIE_BILANZMODELL,
                SystemmeldungService.KEY_KEINE_BILANZDATEN, "15.01.2024 10:15");

        ArgumentCaptor<Systemmeldung> captor = ArgumentCaptor.forClass(Systemmeldung.class);
        verify(systemmeldungRepository).saveAndFlush(captor.capture());

        Systemmeldung neu = captor.getValue();
        assertEquals(42L, neu.getOrgId());
        assertEquals(MeldungLevel.ERROR, neu.getLevel());
        assertEquals(1, neu.getZaehler());
        assertFalse(neu.isErledigt(), "eine erfasste Fehlermeldung ist offen");
        assertEquals(neu.getErstmalsAufgetreten(), neu.getZuletztAufgetreten());
    }

    @Test
    void erfasse_Dedup_LaesstErstmalsAufgetretenUnveraendert() {
        // Fachlich tragend: `erstmals` beantwortet "seit wann besteht das Problem" und darf beim
        // Zusammenfassen nicht nachwandern, sonst verliert die Liste ihre Aussage.
        LocalDateTime erstmals = LocalDateTime.of(2024, 1, 1, 8, 0);
        Systemmeldung offen = new Systemmeldung(MeldungLevel.ERROR,
                SystemmeldungService.KATEGORIE_BILANZMODELL,
                SystemmeldungService.KEY_KEINE_BILANZDATEN, "alt", erstmals, erstmals);
        offen.setOrgId(42L);
        when(systemmeldungRepository.findByOrgIdAndMeldungKeyAndErledigtFalse(
                42L, SystemmeldungService.KEY_KEINE_BILANZDATEN)).thenReturn(Optional.of(offen));

        systemmeldungService.erfasse(42L, MeldungLevel.ERROR,
                SystemmeldungService.KATEGORIE_BILANZMODELL,
                SystemmeldungService.KEY_KEINE_BILANZDATEN, "neu");

        assertEquals(erstmals, offen.getErstmalsAufgetreten());
        assertTrue(offen.getZuletztAufgetreten().isAfter(erstmals));
    }

    /**
     * Verliert die Erfassung das Rennen gegen einen parallelen Lauf, faellt sie auf das Increment
     * zurueck statt die Ausnahme durchzulassen.
     *
     * <p>Der UNIQUE-Teil-Index erlaubt nur einen offenen Eintrag je (org_id, meldung_key). Ohne
     * diesen Rueckfall wuerde der aufrufende Vorgang durch eine Meldung ueber eine Meldung
     * gestoert — und genau das soll die eigene Transaktion (REQUIRES_NEW) verhindern.
     */
    @Test
    void erfasse_RennenUmDenUniqueIndex_FaelltAufIncrementZurueck() {
        Systemmeldung vomAnderenLauf = new Systemmeldung(MeldungLevel.ERROR,
                SystemmeldungService.KATEGORIE_BILANZMODELL,
                SystemmeldungService.KEY_KEINE_BILANZDATEN, "vom anderen Lauf",
                LocalDateTime.of(2024, 1, 1, 8, 0), LocalDateTime.of(2024, 1, 1, 8, 0));
        vomAnderenLauf.setOrgId(42L);

        when(systemmeldungRepository.findByOrgIdAndMeldungKeyAndErledigtFalse(
                42L, SystemmeldungService.KEY_KEINE_BILANZDATEN))
                .thenReturn(Optional.empty())             // erster Blick: nichts da
                .thenReturn(Optional.of(vomAnderenLauf)); // nach dem Konflikt: doch da
        when(systemmeldungRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uq_systemmeldung_offen"));

        systemmeldungService.erfasse(42L, MeldungLevel.ERROR,
                SystemmeldungService.KATEGORIE_BILANZMODELL,
                SystemmeldungService.KEY_KEINE_BILANZDATEN, "mein Lauf");

        verify(systemmeldungRepository).save(vomAnderenLauf);
        assertEquals(2, vomAnderenLauf.getZaehler());
        assertEquals("mein Lauf", vomAnderenLauf.getParameter());
    }

    // --- getSeite: Sortierung, Whitelist, Level-Sonderfall ---

    @Test
    void getSeite_SchaltetDenMandantenfilterEin() {
        when(systemmeldungRepository.findByFilter(any(), any(), any(), any()))
                .thenReturn(leereSeite());

        systemmeldungService.getSeite(null, null, null, 0, 50, "zuletztAufgetreten", "DESC");

        verify(hibernateFilterService).enableOrgFilter();
    }

    @Test
    void getSeite_UebergibtFilterUndPaginierung() {
        when(systemmeldungRepository.findByFilter(any(), any(), any(), any()))
                .thenReturn(leereSeite());

        systemmeldungService.getSeite(Boolean.FALSE, SystemmeldungService.KATEGORIE_MQTT,
                MeldungLevel.WARN, 2, 25, "zaehler", "ASC");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(systemmeldungRepository).findByFilter(eq(Boolean.FALSE),
                eq(SystemmeldungService.KATEGORIE_MQTT), eq(MeldungLevel.WARN), captor.capture());

        Pageable pageable = captor.getValue();
        assertEquals(2, pageable.getPageNumber());
        assertEquals(25, pageable.getPageSize());
        assertEquals(Sort.by(Sort.Direction.ASC, "zaehler"), pageable.getSort());
    }

    /**
     * Eine unbekannte Sortierspalte fuehrt auf das Default-Feld zurueck, statt in die Abfrage zu
     * gelangen.
     *
     * <p>Die Spalte kommt als Request-Parameter. Ohne die Whitelist landete beliebiger Text in
     * einem {@code Sort} und damit in generiertem SQL — Spring Data wuerde bei unbekanntem
     * Property werfen, und der Aufrufer bestimmte mit, worauf sortiert wird.
     */
    @Test
    void getSeite_UnbekannteSortierspalte_FaelltAufDefaultZurueck() {
        when(systemmeldungRepository.findByFilter(any(), any(), any(), any()))
                .thenReturn(leereSeite());

        systemmeldungService.getSeite(null, null, null, 0, 50,
                "orgId; DROP TABLE zev.systemmeldung", "ASC");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(systemmeldungRepository).findByFilter(any(), any(), any(), captor.capture());
        assertEquals(Sort.by(Sort.Direction.ASC, "zuletztAufgetreten"), captor.getValue().getSort());
    }

    /**
     * Leere Sortierspalte — der ueber HTTP erreichbare Grenzfall ({@code ?sortSpalte=}).
     *
     * <p>{@code null} wird hier bewusst <b>nicht</b> geprueft: Der Controller deklariert
     * {@code @RequestParam(defaultValue = "zuletztAufgetreten")}, ein fehlender Parameter kommt
     * also als Default an. Ein direkter Aufruf mit {@code null} liefe in einen
     * {@code NullPointerException} aus {@code Set.of(...).contains(null)} — unerreichbar, solange
     * dieser Controller der einzige Aufrufer bleibt.
     */
    @Test
    void getSeite_LeereSortierspalte_FaelltAufDefaultZurueck() {
        when(systemmeldungRepository.findByFilter(any(), any(), any(), any()))
                .thenReturn(leereSeite());

        systemmeldungService.getSeite(null, null, null, 0, 50, "", "DESC");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(systemmeldungRepository).findByFilter(any(), any(), any(), captor.capture());
        assertEquals(Sort.by(Sort.Direction.DESC, "zuletztAufgetreten"), captor.getValue().getSort());
    }

    /**
     * Nach Level wird ueber eigene Abfragen sortiert — nach Schweregrad (ERROR &gt; WARN &gt; INFO)
     * und nicht alphabetisch. Alphabetisch ergaebe ERROR, INFO, WARN und damit eine Reihenfolge,
     * die niemand erwartet.
     */
    @Test
    void getSeite_SortierungNachLevel_VerwendetSchweregradAbfrage() {
        when(systemmeldungRepository.findByFilterOrderByLevelDesc(any(), any(), any(), any()))
                .thenReturn(leereSeite());

        systemmeldungService.getSeite(null, null, null, 0, 50, "level", "DESC");

        verify(systemmeldungRepository).findByFilterOrderByLevelDesc(any(), any(), any(), any());
        verify(systemmeldungRepository, never()).findByFilter(any(), any(), any(), any());
    }

    @Test
    void getSeite_SortierungNachLevelAufsteigend_VerwendetAufsteigendeAbfrage() {
        when(systemmeldungRepository.findByFilterOrderByLevelAsc(any(), any(), any(), any()))
                .thenReturn(leereSeite());

        // Gross-/Kleinschreibung des Parameters darf keine Rolle spielen.
        systemmeldungService.getSeite(null, null, null, 0, 50, "LEVEL", "ASC");

        verify(systemmeldungRepository).findByFilterOrderByLevelAsc(any(), any(), any(), any());
    }

    @Test
    void getSeite_LevelAbfrageTraegtKeinSort_DieReihenfolgeStehtInDerAbfrage() {
        // Ein Sort im Pageable wuerde das ORDER BY der Abfrage ueberschreiben und den
        // Schweregrad wieder aushebeln.
        when(systemmeldungRepository.findByFilterOrderByLevelAsc(any(), any(), any(), any()))
                .thenReturn(leereSeite());

        systemmeldungService.getSeite(null, null, null, 1, 10, "level", "ASC");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(systemmeldungRepository).findByFilterOrderByLevelAsc(any(), any(), any(), captor.capture());
        assertTrue(captor.getValue().getSort().isUnsorted());
        assertEquals(1, captor.getValue().getPageNumber());
    }

    // --- getKategorien ---

    @Test
    void getKategorien_LiefertKategorienUnterMandantenfilter() {
        when(systemmeldungRepository.findDistinctKategorien())
                .thenReturn(List.of(SystemmeldungService.KATEGORIE_BILANZMODELL,
                        SystemmeldungService.KATEGORIE_MQTT));

        List<String> kategorien = systemmeldungService.getKategorien();

        assertEquals(2, kategorien.size());
        verify(hibernateFilterService).enableOrgFilter();
    }

    // --- setErledigt ---

    @Test
    void setErledigt_True_SetztZeitpunktUndMarkiertAlsManuell() {
        Systemmeldung meldung = offeneMeldung();
        when(systemmeldungRepository.findFirstById(7L)).thenReturn(Optional.of(meldung));
        when(systemmeldungRepository.save(meldung)).thenReturn(meldung);

        Systemmeldung ergebnis = systemmeldungService.setErledigt(7L, true);

        assertSame(meldung, ergebnis);
        assertTrue(meldung.isErledigt());
        assertNotNull(meldung.getErledigtAm());
        assertFalse(meldung.isErledigtAutomatisch(), "manuell erledigt, nicht automatisch");
        verify(hibernateFilterService).enableOrgFilter();
    }

    @Test
    void setErledigt_False_SetztErledigtAmZurueck() {
        Systemmeldung meldung = offeneMeldung();
        meldung.setErledigt(true);
        meldung.setErledigtAm(LocalDateTime.of(2024, 2, 1, 12, 0));
        meldung.setErledigtAutomatisch(true);
        when(systemmeldungRepository.findFirstById(7L)).thenReturn(Optional.of(meldung));
        when(systemmeldungRepository.existsByOrgIdAndMeldungKeyAndErledigtFalse(anyLong(), anyString()))
                .thenReturn(false);
        when(systemmeldungRepository.save(meldung)).thenReturn(meldung);

        systemmeldungService.setErledigt(7L, false);

        assertFalse(meldung.isErledigt());
        assertNull(meldung.getErledigtAm());
        assertFalse(meldung.isErledigtAutomatisch());
    }

    /**
     * Das Wieder-Oeffnen wird abgelehnt, wenn schon ein offener Eintrag desselben Keys besteht.
     *
     * <p>Sonst liefe das Speichern in den UNIQUE-Teil-Index und der Benutzer saehe einen
     * Datenbankfehler statt einer Erklaerung.
     */
    @Test
    void setErledigt_False_MitBestehendemOffenenEintrag_WirdAbgelehnt() {
        Systemmeldung meldung = offeneMeldung();
        meldung.setErledigt(true);
        when(systemmeldungRepository.findFirstById(7L)).thenReturn(Optional.of(meldung));
        when(systemmeldungRepository.existsByOrgIdAndMeldungKeyAndErledigtFalse(
                42L, SystemmeldungService.KEY_KEINE_BILANZDATEN)).thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> systemmeldungService.setErledigt(7L, false));

        assertEquals("SYSTEMMELDUNG_REOPEN_KONFLIKT", ex.getMessage());
        verify(systemmeldungRepository, never()).save(any());
        assertTrue(meldung.isErledigt(), "der Datensatz bleibt unveraendert");
    }

    @Test
    void setErledigt_NichtGefunden_Wirft() {
        when(systemmeldungRepository.findFirstById(99L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> systemmeldungService.setErledigt(99L, true));

        assertEquals("SYSTEMMELDUNG_NICHT_GEFUNDEN", ex.getMessage());
    }

    // --- delete ---

    @Test
    void delete_Vorhanden_LoeschtUndLiefertTrue() {
        when(systemmeldungRepository.existsById(7L)).thenReturn(true);

        assertTrue(systemmeldungService.delete(7L));

        verify(systemmeldungRepository).deleteById(7L);
        verify(hibernateFilterService).enableOrgFilter();
    }

    /**
     * Nicht gefunden heisst: nicht loeschen.
     *
     * <p>Die Wache ist {@code existsById} — und die ist, anders als {@code findById}, vom
     * Mandantenfilter erfasst (belegt in {@code NkAbrechnungRepositoryIT}). Eine fremde ID
     * erscheint hier also als "nicht vorhanden", und {@code deleteById} wird nie erreicht.
     */
    @Test
    void delete_NichtVorhanden_LoeschtNichts() {
        when(systemmeldungRepository.existsById(99L)).thenReturn(false);

        assertFalse(systemmeldungService.delete(99L));

        verify(systemmeldungRepository, never()).deleteById(anyLong());
    }

    // --- loescheAlleErledigten: org-explizit, nicht ueber den Filter ---

    /**
     * Der Bulk-DELETE nimmt die {@code orgId} aus dem Kontext und uebergibt sie <b>explizit</b>.
     *
     * <p>Hibernate-{@code @Filter} greift bei Bulk-{@code DELETE}-JPQL nicht; ein Verlass auf den
     * Filter loeschte hier mandantenuebergreifend. Der Test haelt fest, dass die ID uebergeben
     * wird — der Filter ist hier gerade <b>nicht</b> die Absicherung.
     */
    @Test
    void loescheAlleErledigten_UebergibtOrgIdExplizit() {
        when(organizationContextService.getCurrentOrgId()).thenReturn(42L);
        when(systemmeldungRepository.deleteErledigtByOrgId(42L)).thenReturn(3);

        assertEquals(3, systemmeldungService.loescheAlleErledigten());

        verify(systemmeldungRepository).deleteErledigtByOrgId(42L);
    }

    // --- autoResolve / Retention ---

    @Test
    void autoResolve_LiefertAnzahlAufgeloesterEintraege() {
        when(systemmeldungRepository.autoResolve(eq(42L),
                eq(SystemmeldungService.KEY_KEINE_BILANZDATEN), any())).thenReturn(2);

        assertEquals(2, systemmeldungService.autoResolve(42L,
                SystemmeldungService.KEY_KEINE_BILANZDATEN));
    }

    @Test
    void autoResolve_NichtsOffen_LiefertNull() {
        when(systemmeldungRepository.autoResolve(anyLong(), anyString(), any())).thenReturn(0);

        assertEquals(0, systemmeldungService.autoResolve(42L,
                SystemmeldungService.KEY_KEINE_BILANZDATEN));
    }

    @Test
    void loescheErledigteAelterAls_ReichtCutoffDurch() {
        LocalDateTime cutoff = LocalDateTime.of(2024, 1, 1, 0, 0);
        when(systemmeldungRepository.deleteErledigtOlderThan(cutoff)).thenReturn(5);

        assertEquals(5, systemmeldungService.loescheErledigteAelterAls(cutoff));

        verify(systemmeldungRepository).deleteErledigtOlderThan(cutoff);
    }

    // --- Testdaten ---

    private Systemmeldung offeneMeldung() {
        Systemmeldung meldung = new Systemmeldung(MeldungLevel.ERROR,
                SystemmeldungService.KATEGORIE_BILANZMODELL,
                SystemmeldungService.KEY_KEINE_BILANZDATEN, "15.01.2024 10:15",
                LocalDateTime.of(2024, 1, 15, 10, 15), LocalDateTime.of(2024, 1, 15, 10, 15));
        meldung.setOrgId(42L);
        return meldung;
    }

    private Slice<Systemmeldung> leereSeite() {
        return new SliceImpl<>(List.of());
    }
}
