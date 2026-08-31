package ch.nacht.service;

import ch.nacht.dto.NkBerechnungDTO;
import ch.nacht.dto.NkMieterAbrechnungDTO;
import ch.nacht.dto.NkMieterBasisDTO;
import ch.nacht.dto.NkUmlageInfoDTO;
import ch.nacht.dto.NkZeileDTO;
import ch.nacht.entity.NkAbrechnung;
import ch.nacht.entity.NkAkonto;
import ch.nacht.entity.NkPerson;
import ch.nacht.entity.NkPosition;
import ch.nacht.entity.NkPositionsart;
import ch.nacht.entity.NkVerbrauch;
import ch.nacht.entity.NkZusatz;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rechenregeln der Nebenkostenabrechnung (Specs/Nebenkosten/Abrechnung.md, FR-2 bis FR-4).
 *
 * <p>Der Service ist <b>rein</b>: kein Repository, kein Org-Filter, kein Zustand. Er bekommt die
 * erfassten Daten und gibt die berechneten zurück. So sind die Regeln — zeitanteilige Umlage,
 * Zuschlagskaskade, Rundung — ohne Datenbank prüfbar, und genau dort sind Fehler am teuersten.
 *
 * <p><b>Geld ist durchgehend {@link BigDecimal}</b>, nie {@code double}: Jeder Zeilenbetrag wird
 * einzeln mit {@link RoundingMode#HALF_UP} auf zwei Nachkommastellen gerundet, Summen entstehen
 * aus den bereits gerundeten Zeilen (FR-5). Die dabei entstehende Differenz von wenigen Rappen
 * wird bewusst <b>nicht</b> ausgeglichen, sondern als {@code rundungsdifferenz} ausgewiesen.
 */
@Service
public class NkBerechnungService {

    /** Zwischenschritte rechnen mit Reserve, damit erst der Zeilenbetrag rundet. */
    private static final int ZWISCHEN_SCALE = 10;

    /** Nachkommastellen eines Geldbetrags. */
    private static final int GELD_SCALE = 2;

    /** Nachkommastellen einer Menge — wie {@code NUMERIC(12,3)} in der Datenbank. */
    private static final int MENGE_SCALE = 3;

    private static final BigDecimal HUNDERT = BigDecimal.valueOf(100);

    /**
     * Personen je Wohnung, wenn nichts erfasst ist. Mit dieser Vorgabe und dem Vorschlag
     * "Anzahl Personen = Anzahl Wohnungen" rechnet eine Umlage pro Person genau wie eine Umlage
     * pro Wohnung - eine bestehende Abrechnung aendert ihre Zahlen also nicht.
     */
    public static final int PERSONEN_VORGABE = 1;

    /**
     * Berechnet die gesamte Abrechnung.
     *
     * @param abrechnung Zeitraum und Anzahl Wohnungen (bildet den Nenner)
     * @param positionen Allgemeine Positionen, Reihenfolge egal — es wird selbst sortiert
     * @param verbraeuche Erfasste Mengen zu den VERBRAUCH-Positionen
     * @param zusaetze Zusatzpositionen aller Mieter
     * @param akontos Erfasste Akonto-Angaben; fehlt eine, wird sie vorgeschlagen
     * @param personen Erfasste Personenzahlen je Mieter; fehlt eine, gilt {@link #PERSONEN_VORGABE}
     * @param mieter Die abzurechnenden Mieter
     * @return Blöcke je Mieter und Kontrollzahlen je Umlageposition
     */
    public NkBerechnungDTO berechne(NkAbrechnung abrechnung,
                                    List<NkPosition> positionen,
                                    List<NkVerbrauch> verbraeuche,
                                    List<NkZusatz> zusaetze,
                                    List<NkAkonto> akontos,
                                    List<NkPerson> personen,
                                    List<NkMieterBasisDTO> mieter) {

        long tageImZeitraum = tageImZeitraum(abrechnung.getDatumVon(), abrechnung.getDatumBis());
        long nenner = (long) abrechnung.getAnzahlWohnungen() * tageImZeitraum;
        // Eigener Nenner: Die Umlage pro Person zaehlt Koepfe, nicht Wohnungen.
        long nennerPerson = (long) nullSicher(abrechnung.getAnzahlPersonen(), 0) * tageImZeitraum;

        List<NkPosition> sortierte = new ArrayList<>(positionen);
        sortierte.sort(Comparator.comparing(NkPosition::getReihenfolge));

        Map<Long, Map<Long, BigDecimal>> mengeJePosition = mengenNachPosition(verbraeuche);
        Map<Long, List<NkZusatz>> zusatzJeMieter = zusaetzeNachMieter(zusaetze);
        Map<Long, NkAkonto> akontoJeMieter = new HashMap<>();
        for (NkAkonto a : akontos) {
            akontoJeMieter.put(a.getMieterId(), a);
        }
        Map<Long, Integer> personenJeMieter = new HashMap<>();
        for (NkPerson p : personen) {
            personenJeMieter.put(p.getMieterId(), p.getAnzahlPersonen());
        }

        // Kontrollzahlen je verteilender Position (UMLAGE und ANTEIL): gefuellt waehrend der
        // Mieterschleife, damit die verteilten Betraege nur einmal gerechnet werden.
        Map<Long, NkUmlageInfoDTO> umlagen = new LinkedHashMap<>();
        for (NkPosition p : sortierte) {
            if (p.getArt() == NkPositionsart.UMLAGE
                    || p.getArt() == NkPositionsart.UMLAGE_PERSON
                    || p.getArt() == NkPositionsart.ANTEIL) {
                NkUmlageInfoDTO info = new NkUmlageInfoDTO();
                info.setPositionId(p.getId());
                info.setBezeichnung(p.getBezeichnung());
                info.setArt(p.getArt());
                info.setTotalbetrag(nullSicher(p.getTotalbetrag()).setScale(GELD_SCALE, RoundingMode.HALF_UP));
                umlagen.put(p.getId(), info);
            }
        }

        NkBerechnungDTO ergebnis = new NkBerechnungDTO();
        ergebnis.setNenner(nenner);
        ergebnis.setNennerPerson(nennerPerson);

        long summeTage = 0;
        long summePersonenTage = 0;
        for (NkMieterBasisDTO basis : mieter) {
            NkMieterAbrechnungDTO block = berechneMieter(
                    basis, abrechnung, nenner, nennerPerson, sortierte,
                    mengeJePosition, zusatzJeMieter.getOrDefault(basis.getMieterId(), List.of()),
                    akontoJeMieter.get(basis.getMieterId()),
                    personenJeMieter.get(basis.getMieterId()), umlagen);
            summeTage += block.getTage();
            summePersonenTage += block.getPersonenTage();
            ergebnis.getMieter().add(block);
        }
        ergebnis.setSummeTage(summeTage);
        ergebnis.setSummePersonenTage(summePersonenTage);

        for (NkUmlageInfoDTO info : umlagen.values()) {
            setzeAbweichungen(info, summeTage, nenner, summePersonenTage, nennerPerson);
        }
        ergebnis.setUmlagen(new ArrayList<>(umlagen.values()));

        return ergebnis;
    }

    /**
     * Tage eines Zeitraums, beide Enden eingeschlossen.
     *
     * @param von Beginn
     * @param bis Ende
     * @return Anzahl Tage
     */
    public long tageImZeitraum(LocalDate von, LocalDate bis) {
        return ChronoUnit.DAYS.between(von, bis) + 1;
    }

    /**
     * Miettage eines Mieters im Zeitraum, <b>ohne</b> Multiplikation mit den Wohnungen.
     *
     * <p>Ein fehlendes {@code mietende} heisst „läuft weiter" und wird als Ende des Zeitraums
     * gelesen — nicht als „nie".
     *
     * @param basis Mieter
     * @param von Beginn des Zeitraums
     * @param bis Ende des Zeitraums
     * @return Überschneidungstage, mindestens 0
     */
    public long miettageImZeitraum(NkMieterBasisDTO basis, LocalDate von, LocalDate bis) {
        LocalDate beginn = basis.getMietbeginn().isAfter(von) ? basis.getMietbeginn() : von;
        LocalDate ende = basis.getMietende() == null || basis.getMietende().isAfter(bis)
                ? bis : basis.getMietende();
        if (beginn.isAfter(ende)) {
            return 0;
        }
        return ChronoUnit.DAYS.between(beginn, ende) + 1;
    }

    /**
     * Anteilige Anzahl Monate im Zeitraum (FR-4).
     *
     * <p>Gerechnet wird je Kalendermonat, weil Monate unterschiedlich lang sind: Ein angebrochener
     * Monat zählt mit {@code Miettage / Tage des Monats}. Mietbeginn am 15. Februar in einem
     * Zeitraum ab 1. Januar bis 30. Juni ergibt {@code 0 + 14/28 + 1 + 1 + 1 + 1 = 4.50}.
     *
     * @param basis Mieter
     * @param von Beginn des Zeitraums
     * @param bis Ende des Zeitraums
     * @return Anzahl Monate, auf zwei Nachkommastellen gerundet
     */
    public BigDecimal anzahlMonate(NkMieterBasisDTO basis, LocalDate von, LocalDate bis) {
        LocalDate beginn = basis.getMietbeginn().isAfter(von) ? basis.getMietbeginn() : von;
        LocalDate ende = basis.getMietende() == null || basis.getMietende().isAfter(bis)
                ? bis : basis.getMietende();
        if (beginn.isAfter(ende)) {
            return BigDecimal.ZERO.setScale(GELD_SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal summe = BigDecimal.ZERO;
        YearMonth monat = YearMonth.from(beginn);
        YearMonth letzter = YearMonth.from(ende);
        while (!monat.isAfter(letzter)) {
            LocalDate monatsBeginn = monat.atDay(1).isBefore(beginn) ? beginn : monat.atDay(1);
            LocalDate monatsEnde = monat.atEndOfMonth().isAfter(ende) ? ende : monat.atEndOfMonth();
            long tage = ChronoUnit.DAYS.between(monatsBeginn, monatsEnde) + 1;
            summe = summe.add(BigDecimal.valueOf(tage)
                    .divide(BigDecimal.valueOf(monat.lengthOfMonth()), ZWISCHEN_SCALE, RoundingMode.HALF_UP));
            monat = monat.plusMonths(1);
        }
        return summe.setScale(GELD_SCALE, RoundingMode.HALF_UP);
    }

    private NkMieterAbrechnungDTO berechneMieter(NkMieterBasisDTO basis,
                                                 NkAbrechnung abrechnung,
                                                 long nenner,
                                                 long nennerPerson,
                                                 List<NkPosition> positionen,
                                                 Map<Long, Map<Long, BigDecimal>> mengeJePosition,
                                                 List<NkZusatz> zusaetze,
                                                 NkAkonto akonto,
                                                 Integer anzahlPersonen,
                                                 Map<Long, NkUmlageInfoDTO> umlagen) {

        NkMieterAbrechnungDTO block = new NkMieterAbrechnungDTO();
        block.setMieterId(basis.getMieterId());
        block.setName(basis.getName());
        block.setOhneWohnung(basis.getAnzahlWohnungen() <= 0);

        long miettage = miettageImZeitraum(basis, abrechnung.getDatumVon(), abrechnung.getDatumBis());
        long tage = miettage * Math.max(0, basis.getAnzahlWohnungen());
        block.setTage(tage);

        // "Personen je Wohnung": Die Zahl gilt je Wohnung, deshalb liegt sie ueber `tage` und nicht
        // ueber den Miettagen. Wer zwei Wohnungen mit je drei Personen mietet, traegt sechs Anteile.
        int personen = nullSicher(anzahlPersonen, PERSONEN_VORGABE);
        block.setAnzahlPersonen(personen);
        long personenTage = tage * personen;
        block.setPersonenTage(personenTage);

        // Die Zuschlagskaskade rechnet auf die Summe aller Zeilen davor. Beide Quellen teilen sich
        // deshalb einen Nummernraum; bei Gleichstand kommt die allgemeine Position zuerst.
        List<Object> zeilenQuellen = new ArrayList<>();
        zeilenQuellen.addAll(positionen);
        zeilenQuellen.addAll(zusaetze);
        zeilenQuellen.sort(Comparator
                .comparingInt(NkBerechnungService::reihenfolgeVon)
                .thenComparingInt(o -> o instanceof NkPosition ? 0 : 1)
                .thenComparingLong(NkBerechnungService::idVon));

        BigDecimal laufendeSumme = BigDecimal.ZERO;
        for (Object quelle : zeilenQuellen) {
            NkZeileDTO zeile = quelle instanceof NkPosition p
                    ? zeileAusPosition(p, basis, tage, nenner, personenTage, nennerPerson,
                            mengeJePosition, laufendeSumme, umlagen)
                    : zeileAusZusatz((NkZusatz) quelle);
            laufendeSumme = laufendeSumme.add(zeile.getBetrag());
            block.getZeilen().add(zeile);
        }
        block.setKostentotal(laufendeSumme.setScale(GELD_SCALE, RoundingMode.HALF_UP));

        setzeAkonto(block, basis, abrechnung, akonto);
        return block;
    }

    private NkZeileDTO zeileAusPosition(NkPosition p,
                                        NkMieterBasisDTO basis,
                                        long tage,
                                        long nenner,
                                        long personenTage,
                                        long nennerPerson,
                                        Map<Long, Map<Long, BigDecimal>> mengeJePosition,
                                        BigDecimal laufendeSumme,
                                        Map<Long, NkUmlageInfoDTO> umlagen) {
        NkZeileDTO zeile = new NkZeileDTO();
        zeile.setPositionId(p.getId());
        zeile.setArt(p.getArt());
        zeile.setReihenfolge(p.getReihenfolge());
        zeile.setBezeichnung(p.getBezeichnung());
        zeile.setEinheit(p.getEinheit());

        switch (p.getArt()) {
            // Beide Umlagen rechnen identisch - nur der Verteilschluessel unterscheidet sich:
            // Wohnungstage gegen Personentage. Deshalb ein gemeinsamer Zweig statt zweier fast
            // gleicher.
            case UMLAGE, UMLAGE_PERSON -> {
                BigDecimal anteil = p.getArt() == NkPositionsart.UMLAGE_PERSON
                        ? anteil(personenTage, nennerPerson)
                        : anteil(tage, nenner);
                if (p.getGesamtmenge() != null) {
                    zeile.setMenge(p.getGesamtmenge().multiply(anteil)
                            .setScale(MENGE_SCALE, RoundingMode.HALF_UP));
                }
                BigDecimal betrag = nullSicher(p.getTotalbetrag()).multiply(anteil)
                        .setScale(GELD_SCALE, RoundingMode.HALF_UP);
                zeile.setBetrag(betrag);

                NkUmlageInfoDTO info = umlagen.get(p.getId());
                if (info != null) {
                    info.setSummeVerteilt(info.getSummeVerteilt().add(betrag));
                }
            }
            case VERBRAUCH -> {
                BigDecimal menge = mengeJePosition
                        .getOrDefault(p.getId(), Map.of())
                        .get(basis.getMieterId());
                zeile.setMenge(menge);
                zeile.setBetragProEinheit(p.getBetragProEinheit());
                // Keine erfasste Menge heisst Betrag null - nicht dasselbe wie eine erfasste 0,
                // aber betraglich gleich. Unterschieden wird nur in der Anzeige.
                zeile.setBetrag(nullSicher(menge).multiply(nullSicher(p.getBetragProEinheit()))
                        .setScale(GELD_SCALE, RoundingMode.HALF_UP));
            }
            case ANTEIL -> {
                // Der Prozentsatz je Mieter steht dort, wo bei VERBRAUCH die Menge steht.
                BigDecimal prozent = mengeJePosition
                        .getOrDefault(p.getId(), Map.of())
                        .get(basis.getMieterId());
                zeile.setProzentsatz(prozent);
                BigDecimal betrag = nullSicher(p.getTotalbetrag()).multiply(nullSicher(prozent))
                        .divide(HUNDERT, ZWISCHEN_SCALE, RoundingMode.HALF_UP)
                        .setScale(GELD_SCALE, RoundingMode.HALF_UP);
                zeile.setBetrag(betrag);

                NkUmlageInfoDTO info = umlagen.get(p.getId());
                if (info != null) {
                    info.setSummeVerteilt(info.getSummeVerteilt().add(betrag));
                    info.setSummeProzent(info.getSummeProzent().add(nullSicher(prozent)));
                }
            }
            case ZUSCHLAG -> {
                zeile.setProzentsatz(p.getProzentsatz());
                zeile.setBetrag(laufendeSumme.multiply(nullSicher(p.getProzentsatz()))
                        .divide(HUNDERT, ZWISCHEN_SCALE, RoundingMode.HALF_UP)
                        .setScale(GELD_SCALE, RoundingMode.HALF_UP));
            }
            default -> zeile.setBetrag(BigDecimal.ZERO.setScale(GELD_SCALE, RoundingMode.HALF_UP));
        }
        return zeile;
    }

    private NkZeileDTO zeileAusZusatz(NkZusatz z) {
        NkZeileDTO zeile = new NkZeileDTO();
        zeile.setZusatzId(z.getId());
        // Rechnet wie VERBRAUCH (Menge x Betrag pro Einheit); unterschieden wird ueber zusatzId.
        zeile.setArt(NkPositionsart.VERBRAUCH);
        zeile.setReihenfolge(z.getReihenfolge());
        zeile.setBezeichnung(z.getBezeichnung());
        zeile.setEinheit(z.getEinheit());
        zeile.setMenge(z.getMenge());
        zeile.setBetragProEinheit(z.getBetragProEinheit());
        zeile.setBetrag(nullSicher(z.getMenge()).multiply(nullSicher(z.getBetragProEinheit()))
                .setScale(GELD_SCALE, RoundingMode.HALF_UP));
        return zeile;
    }

    private void setzeAkonto(NkMieterAbrechnungDTO block,
                             NkMieterBasisDTO basis,
                             NkAbrechnung abrechnung,
                             NkAkonto akonto) {
        BigDecimal monate;
        BigDecimal proMonat;
        BigDecimal korrektur;
        if (akonto != null) {
            monate = nullSicher(akonto.getAnzahlMonate());
            proMonat = nullSicher(akonto.getBetragProMonat());
            korrektur = nullSicher(akonto.getKorrektur());
        } else {
            // Noch nichts erfasst: Vorschlag aus Mietdauer und Stammdatum des Mieters.
            monate = anzahlMonate(basis, abrechnung.getDatumVon(), abrechnung.getDatumBis());
            proMonat = nullSicher(basis.getAkontoProMonat());
            korrektur = BigDecimal.ZERO;
        }

        BigDecimal total = monate.multiply(proMonat).add(korrektur)
                .setScale(GELD_SCALE, RoundingMode.HALF_UP);

        block.setAkontoAnzahlMonate(monate.setScale(GELD_SCALE, RoundingMode.HALF_UP));
        block.setAkontoBetragProMonat(proMonat.setScale(GELD_SCALE, RoundingMode.HALF_UP));
        block.setAkontoKorrektur(korrektur.setScale(GELD_SCALE, RoundingMode.HALF_UP));
        block.setAkontoTotal(total);
        block.setSaldo(block.getKostentotal().subtract(total));
    }

    /**
     * Nicht verteilter Anteil und Rundungsdifferenz einer verteilenden Position.
     *
     * <p>Beide Arten rechnen gleich, nur mit verschiedener Bezugsgrösse:
     * <ul>
     *   <li>{@code UMLAGE}: Anteil {@code Σ Tage / Nenner}. Was übrig bleibt, ist der
     *       <b>Leerstandsanteil</b> — fachlich begründet, weil der Nenner die mögliche und nicht
     *       die tatsächliche Mietdauer ist.</li>
     *   <li>{@code ANTEIL}: Anteil {@code Σ Prozent / 100}. Was übrig bleibt, zeigt, dass die
     *       erfassten Prozentsätze nicht 100 ergeben.</li>
     * </ul>
     *
     * <p>Die Rundungsdifferenz ist in beiden Fällen der Rest zwischen dem exakt verteilbaren
     * Betrag und der Summe der gerundeten Zeilen.
     */
    private void setzeAbweichungen(NkUmlageInfoDTO info, long summeTage, long nenner,
                                   long summePersonenTage, long nennerPerson) {
        BigDecimal anteil = switch (info.getArt()) {
            case ANTEIL -> info.getSummeProzent()
                    .divide(HUNDERT, ZWISCHEN_SCALE, RoundingMode.HALF_UP);
            case UMLAGE_PERSON -> anteil(summePersonenTage, nennerPerson);
            default -> anteil(summeTage, nenner);
        };

        BigDecimal exaktVerteilbar = info.getTotalbetrag().multiply(anteil)
                .setScale(GELD_SCALE, RoundingMode.HALF_UP);
        info.setNichtVerteilt(info.getTotalbetrag().subtract(exaktVerteilbar));
        info.setRundungsdifferenz(exaktVerteilbar.subtract(info.getSummeVerteilt()));
    }

    /** Zeitanteil {@code Tage / Nenner}; ein Nenner von 0 ergibt 0 statt einer Division durch 0. */
    private BigDecimal anteil(long tage, long nenner) {
        if (nenner <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(tage)
                .divide(BigDecimal.valueOf(nenner), ZWISCHEN_SCALE, RoundingMode.HALF_UP);
    }

    private Map<Long, Map<Long, BigDecimal>> mengenNachPosition(List<NkVerbrauch> verbraeuche) {
        Map<Long, Map<Long, BigDecimal>> map = new HashMap<>();
        for (NkVerbrauch v : verbraeuche) {
            map.computeIfAbsent(v.getPositionId(), k -> new HashMap<>())
                    .put(v.getMieterId(), v.getMenge());
        }
        return map;
    }

    private Map<Long, List<NkZusatz>> zusaetzeNachMieter(List<NkZusatz> zusaetze) {
        Map<Long, List<NkZusatz>> map = new HashMap<>();
        for (NkZusatz z : zusaetze) {
            map.computeIfAbsent(z.getMieterId(), k -> new ArrayList<>()).add(z);
        }
        return map;
    }

    private static int reihenfolgeVon(Object quelle) {
        return quelle instanceof NkPosition p ? p.getReihenfolge() : ((NkZusatz) quelle).getReihenfolge();
    }

    /** Letztes Kriterium der Sortierung: Ohne es waeren neue Zeilen ohne ID nicht stabil geordnet. */
    private static long idVon(Object quelle) {
        Long id = quelle instanceof NkPosition p ? p.getId() : ((NkZusatz) quelle).getId();
        return id != null ? id : Long.MAX_VALUE;
    }

    private static BigDecimal nullSicher(BigDecimal wert) {
        return wert != null ? wert : BigDecimal.ZERO;
    }

    private static int nullSicher(Integer wert, int ersatz) {
        return wert != null ? wert : ersatz;
    }
}
