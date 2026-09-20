package ch.nacht.service;

import ch.nacht.dto.SimulationDTO;
import ch.nacht.dto.SteuerKonfigurationDTO;
import ch.nacht.dto.SteuerentscheidDTO;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.FeatureFlag;
import ch.nacht.entity.Geraetezustand;
import ch.nacht.entity.Preiszeitreihe;
import ch.nacht.entity.Steuerentscheid;
import ch.nacht.entity.Steuerregel;
import ch.nacht.entity.Steuerzustand;
import ch.nacht.entity.Zustandsgroesse;
import ch.nacht.exception.FeatureDisabledException;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.GeraetezustandRepository;
import ch.nacht.repository.MesswerteRepository;
import ch.nacht.repository.PreiszeitreiheRepository;
import ch.nacht.repository.SteuerentscheidRepository;
import ch.nacht.util.PreiszeitreiheZeit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Die Einspeisesteuerung: wertet Intervalle aus, hält die Entscheide fest und rechnet sie
 * rückblickend mit anderen Schwellen nach (Specs/Einspeisesteuerung.md).
 *
 * <p><b>Es wird nichts geschaltet.</b> Dieser Service schreibt Entscheide — einen Schreibpfad zur
 * Anlage gibt es nicht. Die Fachlichkeit der Regel selbst liegt in {@link SteuerRegelService};
 * hier steht nur, woher die Zahlen kommen und wohin das Ergebnis geht.
 *
 * <p>Jede öffentliche Methode prüft den Feature-Flag. Sie verlässt sich <b>nicht</b> darauf, dass
 * ein Aufrufer das bereits getan hat: Der Endpunkt ist über jeden HTTP-Client erreichbar.
 */
@Service
public class SteuerungService {

    private static final Logger log = LoggerFactory.getLogger(SteuerungService.class);

    /** Länge eines Messintervalls — dasselbe Raster wie {@code messwerte} und Preiszeitreihe. */
    private static final int INTERVALL_MINUTEN = 15;

    /*
     * ZEITBEZUEGE - die Ursache eines behobenen Fehlers, deshalb hier ausbuchstabiert:
     *
     *   messwerte.zeit           Ortszeit (Europe/Zurich), Intervall-ENDE
     *                            Die Aggregation rechnet mit LocalDateTime.now() und schreibt
     *                            upsertMesswert(einheit, ende, total).
     *   steuerentscheid.zeit_von Ortszeit, Intervall-BEGINN
     *   preiszeitreihe.zeit_von  UTC, Intervall-BEGINN  <- der einzige Fremdkoerper
     *
     * DIESE KLASSE RECHNET IN ORTSZEIT. Die Preise sind die einzige Groesse, die umgerechnet
     * werden muss, und das geschieht an genau zwei Stellen: beim Laden im Job-Pfad (preisFuer,
     * tiefstpreisRestDesTages) und einmalig beim Buendeln in der Simulation (preiseJeOrtstag).
     * Dahinter ist alles Ortszeit.
     *
     * Frueher galten hier drei Konventionen nebeneinander; ein Entscheid trug den Preis von 11:45
     * Ortszeit neben der Produktion von 09:30-09:45. Beide Zahlen sahen plausibel aus - genau das
     * macht solche Fehler teuer. Bleibt die Umrechnung auf eine Groesse beschraenkt, gibt es die
     * Verwechslung nicht mehr.
     */

    /** Nachkommastellen einer Energiemenge, wie {@code NUMERIC(12,3)}. */
    private static final int MENGE_SCALE = 3;

    private final SteuerentscheidRepository steuerentscheidRepository;
    private final MesswerteRepository messwerteRepository;
    private final EinheitRepository einheitRepository;
    private final GeraetezustandRepository geraetezustandRepository;
    private final PreiszeitreiheRepository preiszeitreiheRepository;
    private final SteuerRegelService steuerRegelService;
    private final EinstellungenService einstellungenService;
    private final FeatureFlagService featureFlagService;
    private final OrganizationContextService organizationContextService;
    private final HibernateFilterService hibernateFilterService;

    public SteuerungService(SteuerentscheidRepository steuerentscheidRepository,
                            MesswerteRepository messwerteRepository,
                            EinheitRepository einheitRepository,
                            GeraetezustandRepository geraetezustandRepository,
                            PreiszeitreiheRepository preiszeitreiheRepository,
                            SteuerRegelService steuerRegelService,
                            EinstellungenService einstellungenService,
                            FeatureFlagService featureFlagService,
                            OrganizationContextService organizationContextService,
                            HibernateFilterService hibernateFilterService) {
        this.steuerentscheidRepository = steuerentscheidRepository;
        this.messwerteRepository = messwerteRepository;
        this.einheitRepository = einheitRepository;
        this.geraetezustandRepository = geraetezustandRepository;
        this.preiszeitreiheRepository = preiszeitreiheRepository;
        this.steuerRegelService = steuerRegelService;
        this.einstellungenService = einstellungenService;
        this.featureFlagService = featureFlagService;
        this.organizationContextService = organizationContextService;
        this.hibernateFilterService = hibernateFilterService;
        log.info("SteuerungService initialized");
    }

    /**
     * Wertet <b>ein</b> Intervall eines Mandanten aus und hält den Entscheid fest (FR-1).
     *
     * <p>Für den Job gedacht und deshalb <b>org-explizit</b>: Er läuft ohne Sicherheitskontext,
     * die parameterlose Filtervariante liefe dort ins Leere.
     *
     * <p>Geschrieben wird per Upsert. Treffen Messwerte verspätet ein, wertet der nächste Lauf
     * dasselbe Intervall erneut aus und überschreibt den Entscheid — statt einen zweiten
     * anzulegen.
     *
     * @param orgId   Mandant
     * @param zeitVon Beginn des Intervalls in <b>Ortszeit</b> (Europe/Zurich)
     */
    @Transactional
    public void werteIntervallAus(Long orgId, LocalDateTime zeitVon) {
        pruefeFeatureFlag(orgId);
        hibernateFilterService.enableOrgFilter(orgId);

        SteuerKonfigurationDTO konfiguration = einstellungenService.getSteuerKonfiguration(orgId);
        BigDecimal schwellwert = konfiguration.schwellwertOderVorgabe();
        BigDecimal speicherwert = konfiguration.speicherwertOderVorgabe();
        BigDecimal socMinimum = konfiguration.socMinimumOderVorgabe();

        Messung messung = messungFuer(zeitVon);
        BigDecimal preis = preisFuer(zeitVon);
        BigDecimal preisTiefRest = tiefstpreisRestDesTages(zeitVon);
        BigDecimal soc = socAmIntervallende(orgId, zeitVon);
        Speicher speicher = speicherFuer(zeitVon);

        SteuerRegelService.Entscheid entscheid = steuerRegelService.entscheide(
                new SteuerRegelService.Eingabe(preis, preisTiefRest,
                        messung.produktion(), messung.verbrauch(), soc),
                schwellwert, speicherwert, socMinimum);

        steuerentscheidRepository.upsert(orgId, zeitVon, preis, preisTiefRest,
                messung.produktion(), messung.verbrauch(), messung.bezug(),
                messung.ruecklieferung(), soc, speicher.ladung(), speicher.entladung(),
                entscheid.ueberschuss(),
                entscheid.regel().name(), entscheid.batterieladung().name(),
                entscheid.einspeisung().name(), schwellwert, speicherwert, socMinimum);

        // Auf INFO und mit den Eingangsgroessen: Ein Entscheid ohne die Zahlen, aus denen er
        // entstand, laesst sich im Nachhinein nicht pruefen - und genau diese Pruefung war noetig,
        // um den Zeitversatz zu finden.
        log.info("Steuerung. Org: {}, zeit(Ortszeit): {}, preis={}, tiefRest={}, "
                        + "produktion={}, verbrauch={}, ueberschuss={}, soc={} (min {}) "
                        + "-> {} (ladung={}, einspeisung={})",
                orgId, zeitVon, preis, preisTiefRest, messung.produktion(), messung.verbrauch(),
                entscheid.ueberschuss(), soc, socMinimum, entscheid.regel(),
                entscheid.batterieladung(), entscheid.einspeisung());
    }

    /**
     * Die Entscheide eines <b>Ortstages</b>, aufsteigend (FR-4).
     *
     * <p>Gespeichert ist ebenfalls Ortszeit, das Datum braucht deshalb <b>keine Umrechnung</b> —
     * der Tag beginnt um 00:00 und endet um 00:00 des Folgetages. Vorher stand hier eine Rechnung
     * über {@link PreiszeitreiheZeit}, weil die Entscheide in UTC lagen.
     *
     * <p>Am Umstellungstag im Herbst liefert das 96 statt 100 Entscheide: Die doppelte Stunde
     * 02:00–03:00 kann unter einem Ortszeit-Schlüssel nur einmal stehen (siehe §5 der Spec).
     *
     * @param datum Tag in Ortszeit
     * @return Entscheide des Tages; leer, wenn keine vorliegen
     */
    @Transactional(readOnly = true)
    public List<SteuerentscheidDTO> getEntscheide(LocalDate datum) {
        pruefeFeatureFlag(organizationContextService.getCurrentOrgId());
        hibernateFilterService.enableOrgFilter();

        LocalDateTime von = datum.atStartOfDay();
        LocalDateTime bis = datum.plusDays(1).atStartOfDay();

        List<SteuerentscheidDTO> dtos = new ArrayList<>();
        for (Steuerentscheid entscheid : steuerentscheidRepository.findByZeitVonBetween(von, bis)) {
            dtos.add(zuDto(entscheid));
        }
        return dtos;
    }

    /**
     * Rechnet die Regel über einen Zeitraum <b>neu</b> — mit abweichenden Schwellen (FR-6).
     *
     * <p><b>Rein lesend.</b> Gespeicherte Entscheide werden weder gelesen noch verändert; gerechnet
     * wird aus Preisen und Messwerten. Damit lässt sich am Schwellwert drehen und sofort sehen, was
     * gewesen wäre — ohne die Aufzeichnung zu verfälschen.
     *
     * <p><b>Gezählt werden nur Intervalle mit Überschuss.</b> {@code PREIS_NEGATIV} greift vor der
     * Überschussprüfung und damit auch nachts; in einer Zählung, mit der ein Schwellwert kalibriert
     * werden soll, stünde die Regel sonst vielfach über den Fällen, in denen die Steuerung
     * tatsächlich etwas entschieden hat.
     *
     * @param von          erster Ortstag (einschliesslich)
     * @param bis          letzter Ortstag (einschliesslich)
     * @param schwellwert  zu erprobender Schwellwert
     * @param speicherwert zu erprobender Speicherwert; {@code null} → Wert des Mandanten
     * @return Kennzahlen des Laufs
     */
    @Transactional(readOnly = true)
    public SimulationDTO simuliere(LocalDate von, LocalDate bis,
                                   BigDecimal schwellwert, BigDecimal speicherwert) {
        Long orgId = organizationContextService.getCurrentOrgId();
        pruefeFeatureFlag(orgId);
        hibernateFilterService.enableOrgFilter();

        BigDecimal wirksamerSpeicherwert = speicherwertOderMandant(orgId, speicherwert);
        BigDecimal wirksamesSocMinimum = socMinimumDesMandanten(orgId);

        Map<Steuerregel, Integer> jeRegel = new EnumMap<>(Steuerregel.class);
        for (Steuerregel regel : Steuerregel.values()) {
            jeRegel.put(regel, 0);
        }
        int[] zaehler = new int[3];  // 0 = Intervalle, 1 = Ladung gesperrt, 2 = Einspeisung gesperrt
        BigDecimal[] energieVerschoben = { BigDecimal.ZERO };
        int[] tage = new int[1];

        tage[0] = rechneNach(orgId, von, bis, schwellwert, wirksamerSpeicherwert,
                wirksamesSocMinimum, n -> {
            // Nur Intervalle mit Ueberschuss zaehlen - siehe Methodenkommentar.
            if (n.entscheid().ueberschuss().signum() <= 0) {
                return;
            }
            zaehler[0]++;
            jeRegel.merge(n.entscheid().regel(), 1, Integer::sum);
            if (n.entscheid().batterieladung() == Steuerzustand.GESPERRT) {
                zaehler[1]++;
                energieVerschoben[0] = energieVerschoben[0].add(n.entscheid().ueberschuss());
            }
            if (n.entscheid().einspeisung() == Steuerzustand.GESPERRT) {
                zaehler[2]++;
            }
        });

        int intervalle = zaehler[0];
        int ladungGesperrt = zaehler[1];
        int einspeisungGesperrt = zaehler[2];

        SimulationDTO dto = new SimulationDTO();
        dto.setVon(von);
        dto.setBis(bis);
        dto.setSchwellwert(schwellwert);
        dto.setSpeicherwert(wirksamerSpeicherwert);
        dto.setTage(tage[0]);
        dto.setIntervalle(intervalle);
        dto.setJeRegel(jeRegel);
        dto.setStundenLadungGesperrt(alsStunden(ladungGesperrt));
        dto.setStundenEinspeisungGesperrt(alsStunden(einspeisungGesperrt));
        dto.setEnergieVerschoben(energieVerschoben[0].setScale(MENGE_SCALE,
                java.math.RoundingMode.HALF_UP));

        log.info("Simulation org={} {} bis {} schwellwert={}: {} Intervalle mit Ueberschuss",
                orgId, von, bis, schwellwert, intervalle);
        return dto;
    }

    /**
     * Die Entscheide <b>eines Tages</b>, nachgerechnet mit abweichenden Schwellen (FR-6a).
     *
     * <p><b>Rein lesend — es wird nichts gespeichert.</b> Das Ergebnis hat dieselbe Form wie
     * {@link #getEntscheide(LocalDate)}, stammt aber aus der Rechnung statt aus der Aufzeichnung.
     * Damit zeigt die Tagesansicht, <i>wann</i> ein anderer Schwellwert gesperrt hätte — die
     * Kennzahlen der Rückrechnung sagen nur, <i>wie oft</i>.
     *
     * <p><b>Derselbe Rechenweg wie {@link #simuliere}</b> ({@link #rechneNach}). Zwei Wege wären
     * zwei Wahrheiten, und die Tagesansicht soll genau das zeigen, was die Kennzahlen zählen.
     *
     * <p>Intervalle <b>ohne</b> Überschuss sind hier — anders als in der Zählung — enthalten: Die
     * Tagesansicht zeichnet einen Verlauf, und eine Lücke darin wäre irreführend.
     *
     * @param datum        Tag in Ortszeit
     * @param schwellwert  zu erprobender Schwellwert
     * @param speicherwert zu erprobender Speicherwert; {@code null} → Wert des Mandanten
     * @return nachgerechnete Entscheide des Tages, aufsteigend; leer, wenn keine Messwerte vorliegen
     */
    @Transactional(readOnly = true)
    public List<SteuerentscheidDTO> getEntscheideSimuliert(LocalDate datum, BigDecimal schwellwert,
                                                           BigDecimal speicherwert) {
        Long orgId = organizationContextService.getCurrentOrgId();
        pruefeFeatureFlag(orgId);
        hibernateFilterService.enableOrgFilter();

        BigDecimal wirksamerSpeicherwert = speicherwertOderMandant(orgId, speicherwert);

        BigDecimal wirksamesSocMinimum = socMinimumDesMandanten(orgId);

        List<SteuerentscheidDTO> dtos = new ArrayList<>();
        rechneNach(orgId, datum, datum, schwellwert, wirksamerSpeicherwert, wirksamesSocMinimum,
                n -> dtos.add(zuDto(n, schwellwert, wirksamerSpeicherwert, wirksamesSocMinimum)));
        reichereSpeicherAn(dtos, datum);

        log.info("Steuerung nachgerechnet: org={} tag={} schwellwert={} -> {} Intervalle",
                orgId, datum, schwellwert, dtos.size());
        return dtos;
    }

    // ==================== Hilfsmittel ====================

    /** Ein nachgerechnetes Intervall — Eingangsgrössen und das Ergebnis der Regel. */
    private record Nachgerechnet(LocalDateTime zeit, BigDecimal preis, BigDecimal preisTiefRest,
                                 Messung messung, BigDecimal soc,
                                 SteuerRegelService.Entscheid entscheid) {
    }

    /**
     * Rechnet die Regel über eine Tagesspanne nach und reicht <b>jedes</b> Intervall weiter.
     *
     * <p><b>Der einzige Rechenweg der Rückrechnung.</b> {@link #simuliere} zählt darüber,
     * {@link #getEntscheideSimuliert} sammelt — beide sehen dieselben Entscheide. Bewusst ein
     * {@link Consumer} und keine Liste: Über 366 Tage sind das 35'000 Intervalle, die niemand
     * zwischenlagern muss.
     *
     * @return Anzahl der Ortstage, für die Preise vorlagen
     */
    private int rechneNach(Long orgId, LocalDate von, LocalDate bis, BigDecimal schwellwert,
                           BigDecimal speicherwert, BigDecimal socMinimum,
                           Consumer<Nachgerechnet> verbraucher) {
        // Preise einmal laden, nach Ortstag buendeln und dabei auf Ortszeit umschluesseln: Der
        // Tiefstpreis des Resttages ist fuer jedes Intervall neu zu bestimmen, und eine Abfrage je
        // Intervall waere bei 35'000 Intervallen die eigentliche Laufzeit (NFR-1). Ab hier ist
        // alles in dieser Methode Ortszeit - die einzige Umrechnung steckt in preiseJeOrtstag().
        Map<LocalDate, TreeMap<LocalDateTime, BigDecimal>> preiseJeTag = preiseJeOrtstag(
                PreiszeitreiheZeit.tagesbeginnUtc(von), PreiszeitreiheZeit.tagesendeUtc(bis));

        // Messwerte tragen das Intervall-ENDE; die Grenzen sind deshalb um ein Intervall zu
        // verschieben. Eine Zonenrechnung braucht es nicht mehr - beide Seiten sind Ortszeit.
        LocalDateTime vonEnde = von.atStartOfDay().plusMinutes(INTERVALL_MINUTEN);
        LocalDateTime bisEnde = bis.plusDays(1).atStartOfDay().plusMinutes(INTERVALL_MINUTEN);

        // Der Ladezustand geht seit V160 in die Regel ein (SOC_TIEF) und muss deshalb HIER
        // vorliegen - nicht erst in der Anzeige. Rechnete die Rueckrechnung ohne ihn, ergaeben
        // Job und Kennzahlen fuer dasselbe Intervall verschiedene Entscheide, und niemand koennte
        // sagen, welcher gilt (FR-6a).
        TreeMap<LocalDateTime, BigDecimal> socVerlauf = socVerlauf(orgId,
                von.atStartOfDay(), bis.plusDays(1).atStartOfDay());

        for (Object[] zeile : messwerteRepository.sumBilanzKomponentenPerZeitBetween(
                vonEnde, bisEnde)) {
            // Vom Intervall-Ende zurueck auf den Beginn - die Form, in der Preise (umgeschluesselt)
            // und Entscheide gefuehrt werden.
            LocalDateTime endeOrtszeit = (LocalDateTime) zeile[0];
            LocalDateTime zeit = endeOrtszeit.minusMinutes(INTERVALL_MINUTEN);
            Messung messung = new Messung(alsBigDecimal(zeile[1]), alsBigDecimal(zeile[2]),
                    alsBigDecimal(zeile[3]), alsBigDecimal(zeile[4]));

            TreeMap<LocalDateTime, BigDecimal> preiseDesTages =
                    preiseJeTag.getOrDefault(zeit.toLocalDate(), new TreeMap<>());

            BigDecimal preis = preiseDesTages.get(zeit);
            BigDecimal preisTiefRest = tiefstpreisNach(preiseDesTages, zeit);

            // Der letzte bekannte Wert bis zu diesem Intervall - ein Intervall ohne eigene
            // Meldung erbt den vorigen, statt als "kein Ladezustand" zu gelten.
            var socEintrag = socVerlauf.floorEntry(zeit);
            BigDecimal soc = socEintrag == null ? null : socEintrag.getValue();

            SteuerRegelService.Entscheid entscheid = steuerRegelService.entscheide(
                    new SteuerRegelService.Eingabe(preis, preisTiefRest,
                            messung.produktion(), messung.verbrauch(), soc),
                    schwellwert, speicherwert, socMinimum);

            verbraucher.accept(
                    new Nachgerechnet(zeit, preis, preisTiefRest, messung, soc, entscheid));
        }
        return preiseJeTag.size();
    }

    /**
     * Der Mindest-Ladezustand des Mandanten für {@code SOC_TIEF}.
     *
     * <p><b>Nicht erprobbar</b>, anders als Schwellwert und Speicherwert: Die Rückrechnung dreht
     * am Preis-Schwellwert. Eine zweite frei wählbare Grösse machte die Kennzahlen mehrdeutig —
     * man sähe eine Wirkung und wüsste nicht, welche der beiden sie verursacht hat.
     */
    private BigDecimal socMinimumDesMandanten(Long orgId) {
        return einstellungenService.getSteuerKonfiguration(orgId).socMinimumOderVorgabe();
    }

    /**
     * Ladezustand je Intervall über einen Zeitraum, als Nachschlagewerk für die Rückrechnung.
     *
     * <p>Der Schlüssel ist der <b>Beginn</b> des 15-Minuten-Intervalls, der Wert der letzte darin
     * gemeldete Ladezustand. Abgefragt wird aggregiert ({@code letzterWertJeIntervall}) statt die
     * ganze Zeitreihe zu laden: Der Zähler meldet alle 30 Sekunden, über 366 Tage wäre das die
     * eigentliche Laufzeit (NFR-1).
     *
     * <p>Der <b>Anfangswert</b> wird eigens geholt: Für das erste Intervall liegt der letzte Wert
     * davor vor dem Zeitraum. Ohne ihn begonne jede Rückrechnung mit einem unbekannten Ladezustand,
     * und {@code SOC_TIEF} griffe dort nie.
     *
     * <p>Leer, wenn kein Speicher erfasst ist — dann greift die Regel nicht, und es wird gar nicht
     * erst abgefragt.
     */
    private TreeMap<LocalDateTime, BigDecimal> socVerlauf(Long orgId, LocalDateTime von,
                                                          LocalDateTime bis) {
        TreeMap<LocalDateTime, BigDecimal> verlauf = new TreeMap<>();
        var speicher = einheitRepository.findFirstByTyp(EinheitTyp.SPEICHER);
        if (speicher.isEmpty()) {
            return verlauf;
        }
        Long einheitId = speicher.get().getId();

        geraetezustandRepository.findFirstByEinheitIdAndGroesseAndZeitLessThanOrderByZeitDesc(
                        einheitId, Zustandsgroesse.SOC, von)
                .ifPresent(z -> verlauf.put(von.minusMinutes(INTERVALL_MINUTEN), z.getWert()));

        for (Object[] zeile : geraetezustandRepository.letzterWertJeIntervall(
                orgId, einheitId, Zustandsgroesse.SOC.name(), von, bis)) {
            verlauf.put(((java.sql.Timestamp) zeile[0]).toLocalDateTime(),
                    (BigDecimal) zeile[1]);
        }
        return verlauf;
    }

    /** Der übergebene Speicherwert, oder — bei {@code null} — der des Mandanten. */
    private BigDecimal speicherwertOderMandant(Long orgId, BigDecimal speicherwert) {
        return speicherwert != null
                ? speicherwert
                : einstellungenService.getSteuerKonfiguration(orgId).speicherwertOderVorgabe();
    }

    /**
     * Die vier Bilanzkomponenten eines Intervalls.
     *
     * <p>{@code produktion} und {@code ruecklieferung} sind <b>Betraege</b> — in
     * {@code messwerte.total} stehen beide negativ.
     *
     * <p>Bezug und Ruecklieferung gehen in <b>keine</b> Regel ein; sie machen die Energiebilanz
     * pruefbar: {@code Produktion + Bezug − Verbrauch − Ruecklieferung} ist der Netto-Batteriefluss
     * (FR-5). Ohne sie liess sich nicht feststellen, ob die Zahlen ueberhaupt zusammenpassen.
     */
    private record Messung(BigDecimal produktion, BigDecimal verbrauch,
                           BigDecimal bezug, BigDecimal ruecklieferung) {
    }

    /**
     * Messung des Intervalls, das bei {@code zeitVonUtc} <b>beginnt</b>.
     *
     * <p><b>Nur noch eine Verschiebung, keine Zonenrechnung:</b> {@code messwerte.zeit} trägt das
     * Intervall<b>ende</b> und liegt — wie {@code zeit_von} — in Ortszeit. Das Intervall
     * 11:45–12:00 steht dort also unter {@code zeit = 12:00}.
     *
     * <p>Über {@code sumBilanzKomponentenPerZeitBetween}, weil diese Abfrage die Produktion bereits
     * mit {@code ABS()} liefert: In {@code messwerte.total} steht sie <b>negativ</b>, und wer selbst
     * summiert, erhält einen Überschuss von immer 0.
     *
     * <p>Fehlen Messwerte, ergeben sich Nullen — der Entscheid wird trotzdem geschrieben, damit die
     * Lücke sichtbar ist statt unsichtbar.
     */
    private Messung messungFuer(LocalDateTime zeitVon) {
        LocalDateTime endeOrtszeit = zeitVon.plusMinutes(INTERVALL_MINUTEN);
        for (Object[] zeile : messwerteRepository.sumBilanzKomponentenPerZeitBetween(
                endeOrtszeit, endeOrtszeit.plusMinutes(INTERVALL_MINUTEN))) {
            return new Messung(alsBigDecimal(zeile[1]), alsBigDecimal(zeile[2]),
                    alsBigDecimal(zeile[3]), alsBigDecimal(zeile[4]));
        }
        // Die Lücke wird gemeldet, nicht nur in Nullen abgebildet: Ein Entscheid mit Produktion 0
        // sieht aus wie Nacht. Ortszeit im Text, weil das Messraster in Ortszeit liegt.
        log.warn("Steuerung: keine Messwerte fuer das Intervall (Ortszeit {} - {}) - Entscheid mit 0",
                endeOrtszeit.minusMinutes(INTERVALL_MINUTEN), endeOrtszeit);
        return new Messung(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * Ladezustand des Speichers am <b>Ende</b> des Intervalls; {@code null}, wenn kein Speicher
     * erfasst ist oder kein Wert vorliegt.
     *
     * <p><b>Geht in keine Regel ein</b> — er erklärt den Entscheid im Nachhinein und erscheint im
     * Diagramm. Dass die Regel ihn auswerten sollte, ist die offene Frage aus §8: Eine Sperre bei
     * 95 % Ladestand war wirkungslos, eine bei 40 % hat Kapazität freigehalten.
     *
     * <p><b>Das Ende, nicht der Beginn:</b> Der Entscheid beschreibt das abgeschlossene Intervall,
     * also zählt der Zustand an dessen Ende. Genommen wird der letzte Wert <b>vor</b> diesem
     * Zeitpunkt — einer genau darauf gehört bereits zum nächsten Intervall.
     *
     * <p>Ohne Speicher-Einheit wird gar nicht erst gesucht: Dann gibt es keinen Ladezustand, und
     * eine Abfrage je Intervall wäre verschenkt.
     */
    private BigDecimal socAmIntervallende(Long orgId, LocalDateTime zeitVon) {
        return einheitRepository.findFirstByTyp(EinheitTyp.SPEICHER)
                .flatMap(speicher -> geraetezustandRepository
                        .findFirstByEinheitIdAndGroesseAndZeitLessThanOrderByZeitDesc(
                                speicher.getId(), Zustandsgroesse.SOC,
                                zeitVon.plusMinutes(INTERVALL_MINUTEN)))
                .map(Geraetezustand::getWert)
                .orElse(null);
    }

    /**
     * Gemessene Lade- und Entlademenge eines Intervalls, beide als <b>Betrag</b>.
     *
     * <p>{@code null} in beiden Feldern heisst <b>nicht gemessen</b> — kein Speicher erfasst oder
     * kein Messwert für das Intervall. Eine 0 hiesse „Speicher stand still"; das ist eine andere
     * Aussage.
     */
    private record Speicher(BigDecimal ladung, BigDecimal entladung) {
        static final Speicher LEER = new Speicher(null, null);
    }

    /**
     * Ladung und Entladung des Speichers im Intervall, das bei {@code zeitVon} <b>beginnt</b>.
     *
     * <p><b>Geht in keine Regel ein</b> (FR-5b): Die Mengen erklären den Entscheid und erlauben es,
     * die wirkliche Erzeugung darzustellen — entschieden wird weiter auf den gemessenen Werten von
     * Produktion und Verbrauch.
     *
     * <p>Ohne Speicher-Einheit wird gar nicht erst abgefragt, wie beim Ladezustand.
     */
    private Speicher speicherFuer(LocalDateTime zeitVon) {
        if (!einheitRepository.existsByTyp(EinheitTyp.SPEICHER)) {
            return Speicher.LEER;
        }
        LocalDateTime ende = zeitVon.plusMinutes(INTERVALL_MINUTEN);
        for (Object[] zeile : messwerteRepository.sumLadungEntladungPerZeitBetween(
                EinheitTyp.SPEICHER, ende, ende.plusMinutes(INTERVALL_MINUTEN))) {
            return new Speicher(alsBigDecimal(zeile[1]), alsBigDecimal(zeile[2]));
        }
        return Speicher.LEER;
    }

    /**
     * Ergänzt nachgerechnete Entscheide um Speichermengen und Ladezustand.
     *
     * <p><b>Warum hier und nicht in {@link #rechneNach}:</b> Jener Weg trägt auch die Rückrechnung
     * über bis zu 366 Tage, und beides wird dort nicht gebraucht — die Kennzahlen zählen Regeln.
     * Der Ladezustand käme aus einer Zeitreihe mit mehreren tausend Werten je Tag; über ein Jahr
     * geladen wäre das die eigentliche Laufzeit (NFR-1). Die Tagesansicht fragt einen Tag ab.
     *
     * <p><b>Warum überhaupt:</b> Ohne das zeigte die nachgerechnete Ansicht weder Speichermengen
     * noch Ladezustand, die Aufzeichnung aber schon — beim Umschalten verschwänden Spalten und
     * Kurve, ohne dass sich an den Daten etwas geändert hätte.
     *
     * <p>Der <b>Anfangswert</b> des Ladezustands wird eigens geholt: Für das erste Intervall des
     * Tages liegt der letzte Wert davor im Vortag. Ohne ihn bliebe 00:00–00:15 als einziges
     * Intervall leer, obwohl ein Wert existiert.
     */
    private void reichereSpeicherAn(List<SteuerentscheidDTO> dtos, LocalDate datum) {
        if (dtos.isEmpty()) {
            return;
        }
        var speicherEinheit = einheitRepository.findFirstByTyp(EinheitTyp.SPEICHER);
        if (speicherEinheit.isEmpty()) {
            return;
        }
        Long einheitId = speicherEinheit.get().getId();
        LocalDateTime tagesbeginn = datum.atStartOfDay();
        LocalDateTime tagesende = datum.plusDays(1).atStartOfDay();

        // Mengen je Intervall. messwerte.zeit traegt das Intervall-ENDE, die Entscheide den Beginn.
        Map<LocalDateTime, Speicher> mengen = new java.util.HashMap<>();
        for (Object[] zeile : messwerteRepository.sumLadungEntladungPerZeitBetween(
                EinheitTyp.SPEICHER, tagesbeginn.plusMinutes(INTERVALL_MINUTEN),
                tagesende.plusMinutes(INTERVALL_MINUTEN))) {
            LocalDateTime beginn = ((LocalDateTime) zeile[0]).minusMinutes(INTERVALL_MINUTEN);
            mengen.put(beginn, new Speicher(alsBigDecimal(zeile[1]), alsBigDecimal(zeile[2])));
        }

        TreeMap<LocalDateTime, BigDecimal> zustaende = new TreeMap<>();
        geraetezustandRepository.findFirstByEinheitIdAndGroesseAndZeitLessThanOrderByZeitDesc(
                        einheitId, Zustandsgroesse.SOC, tagesbeginn)
                .ifPresent(z -> zustaende.put(z.getZeit(), z.getWert()));
        for (Geraetezustand zustand : geraetezustandRepository
                .findByEinheitIdAndGroesseAndZeitGreaterThanEqualAndZeitLessThanOrderByZeitAsc(
                        einheitId, Zustandsgroesse.SOC, tagesbeginn, tagesende)) {
            zustaende.put(zustand.getZeit(), zustand.getWert());
        }

        for (SteuerentscheidDTO dto : dtos) {
            Speicher speicher = mengen.get(dto.getZeit());
            if (speicher != null) {
                dto.setSpeicherLadung(skaliere(speicher.ladung()));
                dto.setSpeicherEntladung(skaliere(speicher.entladung()));
            }
            // Der letzte Wert VOR dem Intervallende - einer genau darauf gehoert zum naechsten
            // Intervall. Dieselbe Regel wie im Job (socAmIntervallende).
            var eintrag = zustaende.lowerEntry(dto.getZeit().plusMinutes(INTERVALL_MINUTEN));
            if (eintrag != null) {
                dto.setSoc(eintrag.getValue());
            }
        }
    }

    /** Menge auf die Stellenzahl der Spalte bringen; {@code null} bleibt {@code null}. */
    private BigDecimal skaliere(BigDecimal wert) {
        return wert == null ? null : wert.setScale(MENGE_SCALE, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Preis eines Intervalls; {@code null}, wenn keiner vorliegt.
     *
     * <p><b>Hier wird umgerechnet</b> — die Preiszeitreihe ist die einzige Quelle in UTC.
     *
     * @param zeitVon Intervallbeginn in Ortszeit
     */
    private BigDecimal preisFuer(LocalDateTime zeitVon) {
        LocalDateTime vonUtc = PreiszeitreiheZeit.nachUtc(zeitVon);
        List<Preiszeitreihe> treffer = preiszeitreiheRepository.findByZeitraum(
                vonUtc, vonUtc.plusMinutes(INTERVALL_MINUTEN));
        if (treffer.isEmpty()) {
            // Ohne Preis greifen die Regeln 1 und 3 nicht - die Steuerung entscheidet dann auf
            // unvollstaendiger Grundlage. Das gehoert ins Log, nicht nur in eine leere Spalte.
            log.warn("Steuerung: kein Preis fuer das Intervall (Ortszeit {}) - Regeln 1 und 3 greifen nicht",
                    zeitVon);
            return null;
        }
        return treffer.get(0).getPreis();
    }

    /**
     * Tiefster Preis im <b>Rest</b> des Ortstages, in dem das Intervall liegt; {@code null}, wenn
     * für den Rest keine Preise vorliegen.
     *
     * <p>Bewusst kein festes Mittagsfenster: Die Frage lautet „kommt noch etwas Billigeres?", und
     * die Antwort ist am Nachmittag eine andere als am Morgen.
     */
    private BigDecimal tiefstpreisRestDesTages(LocalDateTime zeitVon) {
        // Beide Grenzen nach UTC - die Preise liegen dort. Das Tagesende ueber tagesendeUtc(), weil
        // ein Ortstag nicht immer 24 Stunden hat.
        LocalDateTime abUtc = PreiszeitreiheZeit.nachUtc(zeitVon.plusMinutes(INTERVALL_MINUTEN));
        LocalDateTime tagesende = PreiszeitreiheZeit.tagesendeUtc(zeitVon.toLocalDate());

        BigDecimal tiefster = null;
        for (Preiszeitreihe punkt : preiszeitreiheRepository.findByZeitraum(abUtc, tagesende)) {
            if (tiefster == null || punkt.getPreis().compareTo(tiefster) < 0) {
                tiefster = punkt.getPreis();
            }
        }
        return tiefster;
    }

    /**
     * Preise der Spanne, gebündelt nach Ortstag und darin nach Zeit sortiert — <b>auf Ortszeit
     * umgeschlüsselt</b>.
     *
     * <p>Die einzige Zonenrechnung der Rückrechnung steckt hier. Danach sind Schlüssel und
     * Messwerte in derselben Zone, und der Abgleich ist ein schlichter Kartenzugriff.
     *
     * @param vonUtc untere Grenze in UTC (einschliesslich)
     * @param bisUtc obere Grenze in UTC (ausschliesslich)
     */
    private Map<LocalDate, TreeMap<LocalDateTime, BigDecimal>> preiseJeOrtstag(
            LocalDateTime vonUtc, LocalDateTime bisUtc) {
        Map<LocalDate, TreeMap<LocalDateTime, BigDecimal>> jeTag = new java.util.HashMap<>();
        for (Preiszeitreihe punkt : preiszeitreiheRepository.findByZeitraum(vonUtc, bisUtc)) {
            LocalDateTime ortszeit = PreiszeitreiheZeit.nachOrtszeit(punkt.getZeitVon());
            jeTag.computeIfAbsent(ortszeit.toLocalDate(), t -> new TreeMap<>())
                    .put(ortszeit, punkt.getPreis());
        }
        return jeTag;
    }

    /** Tiefster Preis nach {@code zeit} innerhalb desselben Tages; {@code null}, wenn keiner folgt. */
    private BigDecimal tiefstpreisNach(TreeMap<LocalDateTime, BigDecimal> preiseDesTages,
                                       LocalDateTime zeit) {
        BigDecimal tiefster = null;
        for (BigDecimal preis : preiseDesTages.tailMap(zeit, false).values()) {
            if (tiefster == null || preis.compareTo(tiefster) < 0) {
                tiefster = preis;
            }
        }
        return tiefster;
    }

    private BigDecimal alsStunden(int intervalle) {
        return BigDecimal.valueOf(intervalle)
                .multiply(BigDecimal.valueOf(INTERVALL_MINUTEN))
                .divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal alsBigDecimal(Object wert) {
        if (wert == null) {
            return BigDecimal.ZERO;
        }
        return wert instanceof BigDecimal b ? b : BigDecimal.valueOf(((Number) wert).doubleValue());
    }

    /**
     * DTO eines <b>nachgerechneten</b> Intervalls — formgleich mit dem eines gespeicherten.
     *
     * <p>Die Schwellen sind die <b>erprobten</b>, nicht die des Mandanten: Das DTO soll erklären,
     * woraus dieser Entscheid entstand, und das waren hier die eingegebenen Werte.
     */
    private SteuerentscheidDTO zuDto(Nachgerechnet n, BigDecimal schwellwert,
                                     BigDecimal speicherwert, BigDecimal socMinimum) {
        SteuerentscheidDTO dto = new SteuerentscheidDTO();
        dto.setZeit(n.zeit());
        dto.setPreis(n.preis());
        dto.setPreisTiefRest(n.preisTiefRest());
        dto.setProduktion(n.messung().produktion()
                .setScale(MENGE_SCALE, java.math.RoundingMode.HALF_UP));
        dto.setVerbrauch(n.messung().verbrauch()
                .setScale(MENGE_SCALE, java.math.RoundingMode.HALF_UP));
        dto.setBezug(n.messung().bezug().setScale(MENGE_SCALE, java.math.RoundingMode.HALF_UP));
        dto.setRuecklieferung(n.messung().ruecklieferung()
                .setScale(MENGE_SCALE, java.math.RoundingMode.HALF_UP));
        dto.setUeberschuss(n.entscheid().ueberschuss()
                .setScale(MENGE_SCALE, java.math.RoundingMode.HALF_UP));
        dto.setRegel(n.entscheid().regel());
        dto.setBatterieladung(n.entscheid().batterieladung());
        dto.setEinspeisung(n.entscheid().einspeisung());
        dto.setSoc(n.soc());
        dto.setSchwellwert(schwellwert);
        dto.setSpeicherwert(speicherwert);
        dto.setSocMinimum(socMinimum);
        return dto;
    }

    private SteuerentscheidDTO zuDto(Steuerentscheid entscheid) {
        SteuerentscheidDTO dto = new SteuerentscheidDTO();
        // Gespeichert ist bereits Ortszeit - keine Umrechnung mehr noetig (V147).
        dto.setZeit(entscheid.getZeitVon());
        dto.setPreis(entscheid.getPreis());
        dto.setPreisTiefRest(entscheid.getPreisTiefRest());
        dto.setProduktion(entscheid.getProduktion());
        dto.setVerbrauch(entscheid.getVerbrauch());
        dto.setBezug(entscheid.getBezug());
        dto.setRuecklieferung(entscheid.getRuecklieferung());
        dto.setSoc(entscheid.getSoc());
        dto.setSpeicherLadung(entscheid.getSpeicherLadung());
        dto.setSpeicherEntladung(entscheid.getSpeicherEntladung());
        dto.setUeberschuss(entscheid.getUeberschuss());
        dto.setRegel(entscheid.getRegel());
        dto.setBatterieladung(entscheid.getBatterieladung());
        dto.setEinspeisung(entscheid.getEinspeisung());
        dto.setSchwellwert(entscheid.getSchwellwert());
        dto.setSpeicherwert(entscheid.getSpeicherwert());
        dto.setSocMinimum(entscheid.getSocMinimum());
        return dto;
    }

    /**
     * Weist den Zugriff ab, wenn der Flag für den Mandanten aus ist.
     *
     * <p>Ohne diese Prüfung wäre der Flag reine Kosmetik: Die Seite bliebe verborgen, der Endpunkt
     * aber über jeden HTTP-Client erreichbar.
     */
    private void pruefeFeatureFlag(Long orgId) {
        if (!featureFlagService.isEnabled(orgId, FeatureFlag.EINSPEISESTEUERUNG)) {
            log.warn("Einspeisesteuerung rejected - feature disabled for org: {}", orgId);
            throw new FeatureDisabledException("FEATURE_FLAG_DEAKTIVIERT");
        }
    }
}
