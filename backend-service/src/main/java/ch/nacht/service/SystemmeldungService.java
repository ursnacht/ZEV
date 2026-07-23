package ch.nacht.service;

import ch.nacht.entity.MeldungLevel;
import ch.nacht.entity.Systemmeldung;
import ch.nacht.repository.SystemmeldungRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service für Systemmeldungen (persistente Betriebsmeldungen).
 *
 * <p>Erfassung/Auto-Resolve sind <b>org-explizit</b> (Parameter {@code orgId}) und funktionieren
 * damit auch im Hintergrund-Lauf ohne Request-Kontext (MQTT). Die Erfassung läuft in einer
 * <b>eigenen Transaktion</b> ({@code REQUIRES_NEW}), damit sie einen Rollback des auslösenden
 * Vorgangs überlebt. Die Listen-Methoden laufen im Request-Scope mit aktivem {@code orgFilter}.
 */
@Service
public class SystemmeldungService {

    private static final Logger log = LoggerFactory.getLogger(SystemmeldungService.class);

    // --- Zentrale Kategorie-/Meldungs-Keys (nicht über das Projekt verteilen) ---
    /** Kategorie-Übersetzungs-Key für Bilanzmodell-Meldungen. */
    public static final String KATEGORIE_BILANZMODELL = "SYSTEMMELDUNG_KATEGORIE_BILANZMODELL";
    /** Meldungs-/Fehler-Key: keine Bilanzdaten (Bezug) für die Verteilung vorhanden. */
    public static final String KEY_KEINE_BILANZDATEN = "BILANZMODELL_KEINE_BILANZDATEN";

    /** Erlaubte, direkt sortierbare Entity-Properties (Whitelist gegen Sort-Injection). */
    private static final Set<String> SORTIERBAR = Set.of(
            "zuletztAufgetreten", "erstmalsAufgetreten", "zaehler", "kategorie", "meldungKey", "erledigt");

    private final SystemmeldungRepository systemmeldungRepository;
    private final HibernateFilterService hibernateFilterService;

    public SystemmeldungService(SystemmeldungRepository systemmeldungRepository,
                                HibernateFilterService hibernateFilterService) {
        this.systemmeldungRepository = systemmeldungRepository;
        this.hibernateFilterService = hibernateFilterService;
    }

    /**
     * Liefert eine gefilterte, sortierte, paginierte Seite (Request-Scope, orgFilter aktiv).
     */
    @Transactional(readOnly = true)
    public Slice<Systemmeldung> getSeite(Boolean erledigt, String kategorie, MeldungLevel level,
                                         int page, int size, String sortSpalte, String sortRichtung) {
        hibernateFilterService.enableOrgFilter();
        Sort.Direction dir = "DESC".equalsIgnoreCase(sortRichtung) ? Sort.Direction.DESC : Sort.Direction.ASC;

        // Level wird nach Schweregrad sortiert (ERROR > WARN > INFO) – via dedizierte Queries.
        if ("level".equalsIgnoreCase(sortSpalte)) {
            Pageable pageable = PageRequest.of(page, size);
            return dir == Sort.Direction.ASC
                    ? systemmeldungRepository.findByFilterOrderByLevelAsc(erledigt, kategorie, level, pageable)
                    : systemmeldungRepository.findByFilterOrderByLevelDesc(erledigt, kategorie, level, pageable);
        }

        String property = SORTIERBAR.contains(sortSpalte) ? sortSpalte : "zuletztAufgetreten";
        Pageable pageable = PageRequest.of(page, size, Sort.by(dir, property));
        return systemmeldungRepository.findByFilter(erledigt, kategorie, level, pageable);
    }

    /** Vorhandene Kategorien des Mandanten (für den Kategorie-Filter). */
    @Transactional(readOnly = true)
    public List<String> getKategorien() {
        hibernateFilterService.enableOrgFilter();
        return systemmeldungRepository.findDistinctKategorien();
    }

    /**
     * Setzt/entfernt den Erledigt-Status. Beim Wieder-Öffnen werden {@code erledigtAm}/
     * {@code erledigtAutomatisch} zurückgesetzt; existiert bereits ein offener Eintrag desselben
     * {@code meldungKey}, wird das Wieder-Öffnen abgelehnt (Dedup-Invariante).
     */
    @Transactional
    public Systemmeldung setErledigt(Long id, boolean erledigt) {
        hibernateFilterService.enableOrgFilter();
        Systemmeldung meldung = systemmeldungRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("SYSTEMMELDUNG_NICHT_GEFUNDEN"));

        if (erledigt) {
            meldung.setErledigt(true);
            meldung.setErledigtAm(LocalDateTime.now());
            meldung.setErledigtAutomatisch(false);
        } else {
            if (systemmeldungRepository.existsByOrgIdAndMeldungKeyAndErledigtFalse(
                    meldung.getOrgId(), meldung.getMeldungKey())) {
                throw new IllegalStateException("SYSTEMMELDUNG_REOPEN_KONFLIKT");
            }
            meldung.setErledigt(false);
            meldung.setErledigtAm(null);
            meldung.setErledigtAutomatisch(false);
        }
        log.info("Systemmeldung {} auf erledigt={} gesetzt", id, erledigt);
        return systemmeldungRepository.save(meldung);
    }

    /** Löscht eine Systemmeldung. {@code true} = gelöscht, {@code false} = nicht gefunden. */
    @Transactional
    public boolean delete(Long id) {
        hibernateFilterService.enableOrgFilter();
        if (systemmeldungRepository.existsById(id)) {
            systemmeldungRepository.deleteById(id);
            log.info("Systemmeldung {} gelöscht", id);
            return true;
        }
        log.warn("Systemmeldung {} nicht gefunden (delete)", id);
        return false;
    }

    // --- Business-Logic: Erfassung / Auto-Resolve / Retention (org-explizit) ---

    /**
     * Erfasst eine Systemmeldung <b>zusätzlich zum Log-Eintrag</b> – deduplizierend nach
     * {@code meldungKey}: existiert ein offener Eintrag, werden {@code zaehler} erhöht,
     * {@code zuletztAufgetreten} und {@code parameter} aktualisiert; sonst wird ein neuer Eintrag
     * angelegt. Eigene Transaktion ({@code REQUIRES_NEW}), damit sie einen Rollback des
     * auslösenden Vorgangs überlebt. Org-explizit (kein Request-Kontext nötig).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void erfasse(Long orgId, MeldungLevel level, String kategorie, String meldungKey, String parameter) {
        LocalDateTime jetzt = LocalDateTime.now();
        Optional<Systemmeldung> offen =
                systemmeldungRepository.findByOrgIdAndMeldungKeyAndErledigtFalse(orgId, meldungKey);
        if (offen.isPresent()) {
            inkrementiere(offen.get(), jetzt, parameter);
            return;
        }
        Systemmeldung neu = new Systemmeldung(level, kategorie, meldungKey, parameter, jetzt, jetzt);
        neu.setOrgId(orgId);
        try {
            systemmeldungRepository.saveAndFlush(neu);
            log.info("Systemmeldung erfasst (org={}, key={}, level={})", orgId, meldungKey, level);
        } catch (DataIntegrityViolationException e) {
            // Race mit parallelem Lauf (UNIQUE-Teil-Index) → auf Increment zurückfallen.
            systemmeldungRepository.findByOrgIdAndMeldungKeyAndErledigtFalse(orgId, meldungKey)
                    .ifPresent(m -> inkrementiere(m, jetzt, parameter));
        }
    }

    private void inkrementiere(Systemmeldung meldung, LocalDateTime jetzt, String parameter) {
        meldung.setZaehler(meldung.getZaehler() + 1);
        meldung.setZuletztAufgetreten(jetzt);
        meldung.setParameter(parameter);
        systemmeldungRepository.save(meldung);
    }

    /**
     * Auto-Resolve: setzt offene Einträge des {@code meldungKey} im Mandanten automatisch auf
     * erledigt (Selbstheilung nach erfolgreichem Folgelauf). Org-explizit.
     */
    @Transactional
    public int autoResolve(Long orgId, String meldungKey) {
        int anzahl = systemmeldungRepository.autoResolve(orgId, meldungKey, LocalDateTime.now());
        if (anzahl > 0) {
            log.info("Auto-Resolve: {} Systemmeldung(en) erledigt (org={}, key={})", anzahl, orgId, meldungKey);
        }
        return anzahl;
    }

    /** Retention: löscht erledigte Einträge, die älter als {@code cutoff} sind (mandantenübergreifend). */
    @Transactional
    public int loescheErledigteAelterAls(LocalDateTime cutoff) {
        int anzahl = systemmeldungRepository.deleteErledigtOlderThan(cutoff);
        if (anzahl > 0) {
            log.info("Retention: {} erledigte Systemmeldung(en) gelöscht (älter als {})", anzahl, cutoff);
        }
        return anzahl;
    }
}
