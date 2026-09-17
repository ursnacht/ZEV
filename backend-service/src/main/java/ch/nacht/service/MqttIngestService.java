package ch.nacht.service;

import ch.nacht.dto.ZaehlerMesswertPayloadDTO;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.Geraetezustand;
import ch.nacht.entity.MeldungLevel;
import ch.nacht.entity.ZaehlerRohdaten;
import ch.nacht.entity.Zustandsgroesse;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.GeraetezustandRepository;
import ch.nacht.repository.ZaehlerRohdatenRepository;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Verarbeitet eingehende MQTT-Messwert-Nachrichten (FR-4): Topic/Payload parsen, validieren,
 * Einheiten über (org_id, messpunkt) auflösen und absolute Zählerstände als Rohdaten persistieren.
 *
 * <p>Mehrere Einheiten je Messpunkt sind zulässig (Bilanzmesspunkt FR-2.3): die Bilanz-Typen
 * BEZUG/RUECKLIEFERUNG dürfen denselben Messpunkt teilen; die Meldung wird dann aufgeteilt und
 * je Einheit nur das relevante Register übernommen (BEZUG: nur Bezug, RUECKLIEFERUNG: nur
 * Einspeisung; das jeweils andere = 0).
 *
 * <p>Kein Request-Scope/JWT: die Mandanten-ID stammt aus dem Topic und wird explizit gesetzt
 * (kein {@code OrganizationContextService}, kein {@code orgFilter}). Fehler werden geloggt und
 * die Nachricht verworfen – niemals nach aussen geworfen (der Adapter gilt als konsumiert).
 *
 * <p>Neben den Zählerständen trägt eine Nachricht optional <b>Zustandswerte</b> (Ladezustand u.a.,
 * {@code Specs/Gerätezustand.md}). Sie sind <b>Beiwerk</b>: Ein ungültiger Wert verwirft weder die
 * Nachricht noch die Zählerstände und berührt {@link MqttMetrics} nicht.
 */
@Service
@Profile("mqtt")
public class MqttIngestService {

    private static final Logger log = LoggerFactory.getLogger(MqttIngestService.class);

    /** Spaltenlänge von {@code zaehler_rohdaten.seriennummer} – längere Werte werden gekürzt. */
    private static final int MAX_SERIENNUMMER_LAENGE = 64;

    private final EinheitRepository einheitRepository;
    private final ZaehlerRohdatenRepository rohdatenRepository;
    private final GeraetezustandRepository geraetezustandRepository;
    private final SystemmeldungService systemmeldungService;
    private final ObjectMapper objectMapper;
    private final MqttMetrics metrics;

    public MqttIngestService(EinheitRepository einheitRepository,
                             ZaehlerRohdatenRepository rohdatenRepository,
                             GeraetezustandRepository geraetezustandRepository,
                             SystemmeldungService systemmeldungService,
                             ObjectMapper objectMapper,
                             MqttMetrics metrics) {
        this.einheitRepository = einheitRepository;
        this.rohdatenRepository = rohdatenRepository;
        this.geraetezustandRepository = geraetezustandRepository;
        this.systemmeldungService = systemmeldungService;
        // Offset-behaftete Zeit NICHT auf die Kontext-Zeitzone normalisieren, damit die vom Pi
        // gesendete lokale Wanduhrzeit verbatim erhalten bleibt (OffsetDateTime.toLocalDateTime()).
        this.objectMapper = objectMapper.copy()
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
        this.metrics = metrics;
    }

    @Transactional
    public void handle(String topic, String payload) {
        log.info("MQTT data received. Topic: {}, payload: {}", topic, payload);
        metrics.recordReceived();
        try {
            // 1) Topic parsen: zev/{orgId}/{messpunkt}/messwert
            if (topic == null) {
                log.warn("MQTT: Nachricht ohne Topic verworfen");
                metrics.recordFailed();
                return;
            }
            String[] parts = topic.split("/");
            if (parts.length != 4 || !"zev".equals(parts[0]) || !"messwert".equals(parts[3])) {
                log.warn("MQTT: ungültiges Topic '{}' verworfen", topic);
                metrics.recordFailed();
                return;
            }
            long orgId;
            try {
                orgId = Long.parseLong(parts[1]);
            } catch (NumberFormatException e) {
                log.warn("MQTT: ungültige orgId im Topic '{}' verworfen", topic);
                metrics.recordFailed();
                return;
            }
            String messpunkt = parts[2];

            // 2) Payload parsen + validieren
            ZaehlerMesswertPayloadDTO p = objectMapper.readValue(payload, ZaehlerMesswertPayloadDTO.class);
            if (p.getTimestamp() == null || p.getZaehlerstandBezug() == null
                    || p.getZaehlerstandEinspeisung() == null) {
                log.warn("MQTT: Pflichtfeld fehlt (Topic {}) – verworfen", topic);
                metrics.recordFailed();
                return;
            }
            if (p.getZaehlerstandBezug().signum() < 0 || p.getZaehlerstandEinspeisung().signum() < 0) {
                log.warn("MQTT: negativer Zählerstand (Topic {}) – verworfen", topic);
                metrics.recordFailed();
                return;
            }

            // 3) Einheiten über (org_id, messpunkt) auflösen (Mandanten-Isolation). Mehrere
            //    Treffer sind zulässig: BEZUG/RUECKLIEFERUNG dürfen denselben Bilanzmesspunkt
            //    teilen – die Meldung wird dann je Einheit auf das relevante Register projiziert.
            //    Ladestationen bleiben aussen vor: Ihr `messpunkt` ist eine RFID, keine
            //    Zaehlerkennung (Specs/Ladestationen.md). Faellt eine RFID zufaellig mit einer
            //    Zaehlerkennung zusammen, entstuenden sonst Messwerte an einer Einheit, die
            //    nie an der Verteilung teilnimmt - stille Karteileichen.
            List<Einheit> einheiten = einheitRepository.findAllByOrgIdAndMesspunkt(orgId, messpunkt).stream()
                    .filter(e -> e.getTyp() != EinheitTyp.LADESTATION)
                    .toList();
            if (einheiten.isEmpty()) {
                log.warn("MQTT: unbekannter Messpunkt (org={}, messpunkt={}) – verworfen", orgId, messpunkt);
                metrics.recordFailed();
                return;
            }

            // 4) Rohdaten upsert je Einheit (org_id explizit)
            // Der Pi sendet die lokale Zeit mit Offset (ISO 8601); die lokale Wanduhrzeit
            // wird verbatim übernommen – konsistent mit dem CSV-Upload und der messwerte-Tabelle
            // (naive lokale Zeit). Unabhängig von der Backend-Zeitzone.
            LocalDateTime zeit = p.getTimestamp().toLocalDateTime();
            for (Einheit einheit : einheiten) {
                upsertRohdaten(orgId, einheit, zeit, p);
            }

            // 5) Zustandswerte (Specs/Gerätezustand.md). Beiwerk: Ein ungueltiger Wert darf weder
            //    die Zaehlerstaende noch die Metriken beruehren - die Nachricht war gueltig.
            schreibeZustandswerte(orgId, einheiten, zeit, p);

            metrics.recordProcessed();
            log.debug("MQTT: Rohdaten gespeichert (org={}, messpunkt={}, zeit={}, einheiten={})",
                    orgId, messpunkt, zeit, einheiten.size());
        } catch (Exception e) {
            metrics.recordFailed();
            log.warn("MQTT: Nachricht verworfen (Topic {}): {}", topic, e.getMessage());
        }
    }

    /**
     * Schreibt die Zustandswerte einer Nachricht (Specs/Gerätezustand.md, FR-3 und FR-4).
     *
     * <p><b>Jeder Eintrag wird einzeln geprüft.</b> Ein ungültiger lässt die übrigen unberührt —
     * bei mehreren Grössen soll nicht eine falsche die richtigen mitnehmen. Und keiner von ihnen
     * berührt die Zählerstände oder {@link MqttMetrics}: Die Nachricht war gültig, nur ein Beiwerk
     * nicht.
     *
     * <p>Geprüft wird in dieser Reihenfolge — sie ist Fachlichkeit, nicht Stil:
     * <ol>
     *   <li>{@code null} → <b>nicht gemeldet</b>. Keine Zeile, keine Meldung. Ein Gerät, das eine
     *       Grösse gerade nicht liefern kann, ist kein Störfall — und erzeugte sonst im Minutentakt
     *       Meldungen.</li>
     *   <li>nicht in eine Zahl umwandelbar → verworfen mit Meldung.</li>
     *   <li>unbekannter Schlüssel → verworfen, <b>ohne</b> Meldung (wie ein unbekanntes Feld). Der
     *       Pi darf eine Grösse senden, die dieses Backend noch nicht kennt.</li>
     *   <li>Einheiten-Typ führt die Grösse nicht → verworfen mit eigenem Meldungs-Key.</li>
     *   <li>Wert ausserhalb des Bereichs → verworfen mit Meldung.</li>
     * </ol>
     */
    private void schreibeZustandswerte(long orgId, List<Einheit> einheiten, LocalDateTime zeit,
                                       ZaehlerMesswertPayloadDTO p) {
        Map<String, Object> zustand = p.getZustand();
        if (zustand == null || zustand.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> eintrag : zustand.entrySet()) {
            if (eintrag.getValue() == null) {
                continue;   // nicht gemeldet - kein Stoerfall
            }
            BigDecimal wert = alsZahl(eintrag.getValue());
            if (wert == null) {
                meldeZustandsfehler(orgId, SystemmeldungService.KEY_GERAETEZUSTAND_WERT_UNGUELTIG,
                        String.format("%s: '%s' ist keine Zahl", eintrag.getKey(),
                                eintrag.getValue()));
                continue;
            }
            Optional<Zustandsgroesse> groesseOpt = Zustandsgroesse.fromKey(eintrag.getKey());
            if (groesseOpt.isEmpty()) {
                log.debug("MQTT: unbekannte Zustandsgroesse '{}' (org={}) - ignoriert",
                        eintrag.getKey(), orgId);
                continue;
            }
            Zustandsgroesse groesse = groesseOpt.get();

            // Nur Einheiten, deren Typ die Groesse fuehrt. Lassen mehrere sie zu, gewinnt die mit
            // der kleinsten id - willkuerlich, aber deterministisch: Ohne diese Wahl haenge das
            // Ergebnis an der Sortierreihenfolge der Abfrage, und an einem geteilten
            // Bilanzmesspunkt entstuenden zwei identische Zeilen.
            Optional<Einheit> zielOpt = einheiten.stream()
                    .filter(e -> groesse.giltFuer(e.getTyp()))
                    .min(Comparator.comparing(Einheit::getId));
            if (zielOpt.isEmpty()) {
                meldeZustandsfehler(orgId, SystemmeldungService.KEY_GERAETEZUSTAND_TYP_UNGUELTIG,
                        String.format("%s (%s): %s", einheiten.get(0).getName(),
                                einheiten.get(0).getTyp(), groesse.name()));
                continue;
            }
            if (!groesse.istImBereich(wert)) {
                meldeZustandsfehler(orgId, SystemmeldungService.KEY_GERAETEZUSTAND_WERT_UNGUELTIG,
                        String.format("%s (%s): %s, erlaubt %s", zielOpt.get().getName(),
                                groesse.name(), wert.toPlainString(), groesse.getBereich()));
                continue;
            }
            upsertZustand(orgId, zielOpt.get().getId(), zeit, groesse, wert);
        }
    }

    /**
     * Wandelt einen Payload-Wert in eine Zahl; {@code null}, wenn das nicht geht.
     *
     * <p>Jackson liefert je nach JSON-Literal {@code Integer}, {@code Double} oder {@code String}.
     * Der Umweg über {@code toString()} deckt alle drei ab und vermeidet den Genauigkeitsverlust
     * von {@code BigDecimal.valueOf(double)}.
     */
    private BigDecimal alsZahl(Object roh) {
        try {
            return new BigDecimal(roh.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Upsert auf {@code (einheit_id, zeit, groesse)} - eine wiederholte Nachricht aktualisiert. */
    private void upsertZustand(long orgId, Long einheitId, LocalDateTime zeit,
                               Zustandsgroesse groesse, BigDecimal wert) {
        Geraetezustand zustand = geraetezustandRepository
                .findByEinheitIdAndZeitAndGroesse(einheitId, zeit, groesse)
                .orElseGet(() -> new Geraetezustand(orgId, einheitId, zeit, groesse, wert));
        zustand.setWert(wert);
        // Aus der Anwendung, nicht aus dem DB-DEFAULT: Sonst haenge der Wert an der Zeitzone der
        // Datenbank-Session, und das System haette seine vierte Zeitkonvention.
        zustand.setEmpfangenAm(LocalDateTime.now());
        geraetezustandRepository.save(zustand);
    }

    /**
     * Meldet einen verworfenen Zustandswert.
     *
     * <p><b>Bricht den Ingest nicht ab</b> — dieselbe Abwägung wie beim Zählerwechsel: Eine
     * fehlgeschlagene Meldung darf die Nachricht nicht kosten.
     */
    private void meldeZustandsfehler(long orgId, String key, String parameter) {
        log.warn("MQTT: Zustandswert verworfen (org={}, {}): {}", orgId, key, parameter);
        try {
            systemmeldungService.erfasse(orgId, MeldungLevel.WARN,
                    SystemmeldungService.KATEGORIE_MQTT, key, parameter);
        } catch (RuntimeException e) {
            log.warn("MQTT: Systemmeldung zum Zustandswert konnte nicht erfasst werden: {}",
                    e.getMessage());
        }
    }

    /**
     * Schreibt (Upsert) den Rohdatensatz einer Einheit. Bilanz-Typen erhalten nur ihr
     * relevantes Register (FR-2.4): BEZUG nur den Bezug, RUECKLIEFERUNG nur die Einspeisung,
     * das jeweils andere Register wird auf 0 gesetzt – so zählt eine BEZUG-Einheit nie
     * Einspeisung (und umgekehrt), auch wenn der physische Bilanzzähler beide Register in
     * einer Meldung liefert. PRODUCER/CONSUMER übernehmen die Payload unverändert.
     */
    private void upsertRohdaten(long orgId, Einheit einheit, LocalDateTime zeit, ZaehlerMesswertPayloadDTO p) {
        BigDecimal bezug = p.getZaehlerstandBezug();
        BigDecimal einspeisung = p.getZaehlerstandEinspeisung();
        if (einheit.getTyp() == EinheitTyp.BEZUG) {
            einspeisung = BigDecimal.ZERO;
        } else if (einheit.getTyp() == EinheitTyp.RUECKLIEFERUNG) {
            bezug = BigDecimal.ZERO;
        }

        ZaehlerRohdaten row = rohdatenRepository.findByEinheitIdAndZeit(einheit.getId(), zeit).orElse(null);
        if (row == null) {
            row = new ZaehlerRohdaten(orgId, einheit.getId(), zeit, bezug, einspeisung);
        } else {
            row.setZaehlerstandBezug(bezug);
            row.setZaehlerstandEinspeisung(einspeisung);
        }
        row.setSeriennummer(normalisiereSeriennummer(p.getSeriennummer()));
        row.setEmpfangenAm(LocalDateTime.now());
        row.setVerarbeitet(false);
        rohdatenRepository.save(row);
    }

    /**
     * Normalisiert die optionale Seriennummer für die Zählertausch-Erkennung (FR-1.2):
     * trimmen → leer = {@code null} → auf die Spaltenlänge (64) kürzen. Der Inhalt wird nicht
     * interpretiert (keine Formatprüfung, kein Case-Mapping).
     *
     * <p>Die Kürzung ist zwingend: ein längerer Wert würde beim Insert an {@code VARCHAR(64)}
     * scheitern und – da die Verarbeitung transaktional ist – die ganze Nachricht verwerfen
     * (bei geteiltem Bilanzmesspunkt die Rohdaten beider Einheiten).
     */
    private String normalisiereSeriennummer(String seriennummer) {
        if (seriennummer == null) {
            return null;
        }
        String normalisiert = seriennummer.trim();
        if (normalisiert.isEmpty()) {
            return null;
        }
        if (normalisiert.length() > MAX_SERIENNUMMER_LAENGE) {
            log.warn("MQTT: Seriennummer länger als {} Zeichen – gekürzt gespeichert",
                    MAX_SERIENNUMMER_LAENGE);
            return normalisiert.substring(0, MAX_SERIENNUMMER_LAENGE);
        }
        return normalisiert;
    }
}
