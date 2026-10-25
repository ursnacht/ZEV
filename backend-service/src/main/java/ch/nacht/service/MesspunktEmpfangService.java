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
import ch.nacht.util.Ortszeit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Empfang je Messpunkt festhalten und anzeigen (Specs/Messpunkt-Empfang.md).
 *
 * <p><b>Bewusst ohne {@code @Profile("mqtt")}</b>: Die Ansicht soll auch ohne MQTT erreichbar sein
 * und dann jede Einheit mit Messpunkt als „Kein Empfang“ zeigen. Nur der Ingest, der
 * {@link #halteFest} aufruft, hängt am MQTT-Profil.
 */
@Service
public class MesspunktEmpfangService {

    private static final Logger log = LoggerFactory.getLogger(MesspunktEmpfangService.class);

    /** Aufbewahrung für Zeilen ohne Einheit (FR-3.2). */
    static final Duration AUFBEWAHRUNG = Duration.ofDays(30);

    /** Längster festgehaltener Payload (FR-1.9); schützt vor einem Gerät mit riesigen Meldungen. */
    static final int MAX_PAYLOAD_LAENGE = 10_000;

    /** Endung eines gekürzten Payloads (FR-1.9). */
    static final String GEKUERZT = "…[gekürzt]";

    private final MesspunktEmpfangRepository messpunktEmpfangRepository;
    private final EinheitRepository einheitRepository;
    private final HibernateFilterService hibernateFilterService;
    private final Clock clock;

    public MesspunktEmpfangService(MesspunktEmpfangRepository messpunktEmpfangRepository,
                                   EinheitRepository einheitRepository,
                                   HibernateFilterService hibernateFilterService,
                                   Clock clock) {
        this.messpunktEmpfangRepository = messpunktEmpfangRepository;
        this.einheitRepository = einheitRepository;
        this.hibernateFilterService = hibernateFilterService;
        this.clock = clock;
    }

    /**
     * Was der Ingest über eine Meldung weiss, sobald ihr Ausgang feststeht (FR-1.2 bis FR-1.4).
     *
     * @param grund   nur bei {@link MesspunktEmpfangErgebnis#UNGUELTIG}, sonst {@code null}
     * @param payload die Meldung, wie sie ankam (FR-1.9); gekürzt wird beim Festhalten
     */
    public record Meldung(long orgId, String messpunkt, MesspunktEmpfangQuelle quelle,
                          BigDecimal bezug, BigDecimal einspeisung,
                          MesspunktEmpfangErgebnis ergebnis, String grund, String payload) {
    }

    /**
     * Alle Zeilen der Ansicht: festgehaltener Empfang und Einheiten ohne Empfang (FR-2.2).
     *
     * <p>Die Einheiten werden <b>einmal</b> geladen, nicht je Zeile (NFR-1). Sortiert wird im
     * Frontend (FR-2.6); hier nach letztem Empfang absteigend, Kein Empfang am Ende.
     *
     * @return Zeilen des eigenen Mandanten
     */
    @Transactional(readOnly = true)
    public List<MesspunktEmpfangDTO> getAllMesspunktEmpfang() {
        hibernateFilterService.enableOrgFilter();
        Instant jetzt = clock.instant();

        // Messpunkt -> Namen der Einheiten. Ladestationen tragen eine RFID, keine Zaehlerkennung
        // (Specs/Ladestationen.md) - wie im Ingest gehoeren sie nicht dazu.
        Map<String, List<String>> einheitenJeMesspunkt = new LinkedHashMap<>();
        for (Einheit einheit : einheitRepository.findAllByOrderByNameAsc()) {
            if (einheit.getTyp() == EinheitTyp.LADESTATION || einheit.getMesspunkt() == null
                    || einheit.getMesspunkt().isBlank()) {
                continue;
            }
            einheitenJeMesspunkt.computeIfAbsent(einheit.getMesspunkt(), k -> new ArrayList<>())
                    .add(einheit.getName());
        }

        List<MesspunktEmpfangDTO> zeilen = new ArrayList<>();
        for (MesspunktEmpfang empfang : messpunktEmpfangRepository.findAllByOrderByLetzterEmpfangDesc()) {
            List<String> einheiten = einheitenJeMesspunkt.remove(empfang.getMesspunkt());
            zeilen.add(toDTO(empfang, einheiten == null ? List.of() : einheiten, jetzt));
        }
        // Was uebrig bleibt, hat noch nie gesendet
        einheitenJeMesspunkt.forEach((messpunkt, einheiten) -> zeilen.add(keinEmpfang(messpunkt, einheiten)));
        return zeilen;
    }

    /**
     * Payload der letzten Meldung einer Zeile des eigenen Mandanten (FR-2.9).
     *
     * @param id Technischer Schlüssel
     * @return leer, wenn nicht gefunden oder fremd; sonst der Payload ({@code null} vor V188)
     */
    @Transactional(readOnly = true)
    public Optional<MesspunktEmpfangMeldungDTO> getLetzteMeldung(Long id) {
        hibernateFilterService.enableOrgFilter();
        return messpunktEmpfangRepository.findFirstById(id)
                .map(empfang -> new MesspunktEmpfangMeldungDTO(empfang.getLetzteMeldung()));
    }

    /**
     * Löscht eine Zeile des eigenen Mandanten (FR-3.1).
     *
     * @param id Technischer Schlüssel
     * @return {@code true} wenn gelöscht, {@code false} wenn nicht gefunden oder fremd
     */
    @Transactional
    public boolean deleteMesspunktEmpfang(Long id) {
        hibernateFilterService.enableOrgFilter();
        Optional<MesspunktEmpfang> empfang = messpunktEmpfangRepository.findFirstById(id);
        if (empfang.isEmpty()) {
            log.warn("Messpunkt-Empfang nicht gefunden: id={}", id);
            return false;
        }
        messpunktEmpfangRepository.delete(empfang.get());
        log.info("Messpunkt-Empfang gelöscht: id={}, messpunkt={}", id, empfang.get().getMesspunkt());
        return true;
    }

    /**
     * Hält eine Meldung fest (FR-1.1).
     *
     * <p><b>Eigene Transaktion</b> ({@code REQUIRES_NEW}): Der Ingest ruft dies erst nach seinem
     * eigenen Commit auf (FR-1.6). Ein Fehler hier darf die schon gespeicherten Rohdaten nicht
     * mehr berühren können. Org-explizit, ohne Request-Kontext.
     *
     * @param meldung Ausgang der Meldung
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void halteFest(Meldung meldung) {
        messpunktEmpfangRepository.upsert(meldung.orgId(), meldung.messpunkt(), meldung.quelle().name(),
                clock.instant(), meldung.bezug(), meldung.einspeisung(), meldung.ergebnis().name(),
                meldung.grund(), kuerze(meldung.payload()));
    }

    /** Kürzt einen Payload auf {@link #MAX_PAYLOAD_LAENGE} Zeichen samt Hinweis (FR-1.9). */
    static String kuerze(String payload) {
        if (payload == null || payload.length() <= MAX_PAYLOAD_LAENGE) {
            return payload;
        }
        return payload.substring(0, MAX_PAYLOAD_LAENGE - GEKUERZT.length()) + GEKUERZT;
    }

    /**
     * Löscht Zeilen ohne Einheit, deren letzter Empfang mehr als 30 Tage zurückliegt (FR-3.2) —
     * über alle Mandanten.
     *
     * @return Anzahl gelöschter Zeilen
     */
    @Transactional
    public int raeumeAuf() {
        return messpunktEmpfangRepository.loescheVerwaisteVor(clock.instant().minus(AUFBEWAHRUNG));
    }

    private MesspunktEmpfangDTO toDTO(MesspunktEmpfang empfang, List<String> einheiten, Instant jetzt) {
        MesspunktEmpfangDTO dto = new MesspunktEmpfangDTO();
        dto.setId(empfang.getId());
        dto.setMesspunkt(empfang.getMesspunkt());
        dto.setEinheiten(einheiten);
        dto.setQuelle(empfang.getQuelle());
        dto.setErsterEmpfang(ortszeit(empfang.getErsterEmpfang()));
        dto.setLetzterEmpfang(ortszeit(empfang.getLetzterEmpfang()));
        // Auf 0 geklemmt: Eine zurueckgestellte Serveruhr ergaebe sonst "vor -3 Min." (FR-2.5)
        dto.setSekundenSeitEmpfang(Math.max(0, Duration.between(empfang.getLetzterEmpfang(), jetzt).getSeconds()));
        dto.setAnzahlMeldungen(empfang.getAnzahlMeldungen());
        dto.setZaehlerstandBezug(empfang.getZaehlerstandBezug());
        dto.setZaehlerstandEinspeisung(empfang.getZaehlerstandEinspeisung());
        dto.setErgebnis(empfang.getErgebnis());
        dto.setGrund(empfang.getGrund());
        dto.setKeinEmpfang(false);
        return dto;
    }

    private MesspunktEmpfangDTO keinEmpfang(String messpunkt, List<String> einheiten) {
        MesspunktEmpfangDTO dto = new MesspunktEmpfangDTO();
        dto.setMesspunkt(messpunkt);
        dto.setEinheiten(einheiten);
        dto.setKeinEmpfang(true);
        return dto;
    }

    private static LocalDateTime ortszeit(Instant zeitpunkt) {
        return LocalDateTime.ofInstant(zeitpunkt, Ortszeit.ZONE);
    }
}
