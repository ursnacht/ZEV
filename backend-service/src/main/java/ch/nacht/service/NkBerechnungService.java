package ch.nacht.service;

import ch.nacht.dto.NkBerechnungDTO;
import ch.nacht.dto.NkMieterAbrechnungDTO;
import ch.nacht.dto.NkMieterBasisDTO;
import ch.nacht.dto.NkPositionSummeDTO;
import ch.nacht.dto.NkZeileDTO;
import ch.nacht.entity.NkAbrechnung;
import ch.nacht.entity.NkAkonto;
import ch.nacht.entity.Mengeneinheit;
import ch.nacht.entity.Mengeneinheit;
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

        // Zusammenstellung je Position - fuer JEDE Art, nicht nur die verteilenden (FR-10).
        // Mengen und Kosten werden waehrend der Mieterschleife gefuellt, damit die Zeilenbetraege
        // nur einmal gerechnet werden.
        Map<Long, NkPositionSummeDTO> summen = new LinkedHashMap<>();
        for (NkPosition p : sortierte) {
            NkPositionSummeDTO summe = new NkPositionSummeDTO();
            summe.setPositionId(p.getId());
            summe.setBezeichnung(p.getBezeichnung());
            summe.setArt(p.getArt());
            // Nur wo die Art einen Gesamtbetrag kennt. Sonst bleibt die Zelle leer statt "0.00" zu
            // behaupten - eine Verbrauchsposition hat keinen.
            if (verteilendeArt(p.getArt())) {
                summe.setTotalbetrag(nullSicher(p.getTotalbetrag())
                        .setScale(GELD_SCALE, RoundingMode.HALF_UP));
                summe.setSummeProzent(p.getArt() == NkPositionsart.ANTEIL
                        ? BigDecimal.ZERO : null);
            }
            // Bei ANTEIL steht in der Mengenspalte der Prozentsatz, bei ZUSCHLAG gibt es keine
            // Menge - beide bleiben ohne Mengeneinheit.
            if (p.getArt() != NkPositionsart.ANTEIL && p.getArt() != NkPositionsart.ZUSCHLAG) {
                summe.setEinheit(p.getEinheit());
            }
            summen.put(p.getId(), summe);
        }

        // Eine Sammelzeile fuer ALLE Zusatzpositionen. Ohne sie waere die Summe der Uebersicht
        // kleiner als das Kostentotal aller Mieter - die Mieterzeilen speisen sich aus zwei
        // Quellen, die Uebersicht kennte nur eine.
        NkPositionSummeDTO zusatzSumme = zusatzZeile(zusaetze);

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
                    personenJeMieter.get(basis.getMieterId()), summen);
            summeTage += block.getTage();
            summePersonenTage += block.getPersonenTage();
            ergebnis.getMieter().add(block);
        }
        ergebnis.setSummeTage(summeTage);
        ergebnis.setSummePersonenTage(summePersonenTage);

        for (NkPositionSummeDTO summe : summen.values()) {
            if (verteilendeArt(summe.getArt())) {
                setzeAbweichungen(summe, summeTage, nenner, summePersonenTage, nennerPerson);
            }
        }

        List<NkPositionSummeDTO> uebersicht = new ArrayList<>(summen.values());
        if (zusatzSumme != null) {
            uebersicht.add(zusatzSumme);
        }
        ergebnis.setPositionSummen(uebersicht);
        ergebnis.setSummeKosten(uebersicht.stream()
                .map(NkPositionSummeDTO::getSummeKosten)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(GELD_SCALE, RoundingMode.HALF_UP));

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
     * Beginn des Mietverhältnisses <b>innerhalb</b> des Abrechnungszeitraums — der spätere der
     * beiden Zeitpunkte.
     *
     * <p>Statisch und mit einzelnen Daten statt eines {@code NkMieterBasisDTO}, weil dieselbe
     * Regel auch die Rechnung braucht: Dort steht der Mieter als Entity da, und auf der PDF soll
     * im „Zeitraum" der Mietbeginn erscheinen, sobald er später liegt als der Abrechnungsbeginn
     * (Specs/Nebenkosten/RechnungenGenerieren.md, FR-10). Zwei Kopien der Regel wären zwei
     * Wahrheiten — die Zeile auf dem Papier soll denselben Zeitraum nennen, aus dem die Miettage
     * gerechnet wurden.
     *
     * @param mietbeginn Mietbeginn des Mieters; {@code null} wird als „schon immer" gelesen
     * @param von        Beginn des Abrechnungszeitraums
     * @return der spätere der beiden Zeitpunkte
     */
    public static LocalDate mietbeginnImZeitraum(LocalDate mietbeginn, LocalDate von) {
        return mietbeginn != null && mietbeginn.isAfter(von) ? mietbeginn : von;
    }

    /**
     * Ende des Mietverhältnisses <b>innerhalb</b> des Abrechnungszeitraums — der frühere der
     * beiden Zeitpunkte.
     *
     * <p>Ein fehlendes {@code mietende} heisst „läuft weiter" und ergibt das Ende des Zeitraums —
     * nicht „nie".
     *
     * @param mietende Mietende des Mieters; {@code null} heisst „läuft weiter"
     * @param bis      Ende des Abrechnungszeitraums
     * @return der frühere der beiden Zeitpunkte
     */
    public static LocalDate mietendeImZeitraum(LocalDate mietende, LocalDate bis) {
        return mietende != null && mietende.isBefore(bis) ? mietende : bis;
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
        LocalDate beginn = mietbeginnImZeitraum(basis.getMietbeginn(), von);
        LocalDate ende = mietendeImZeitraum(basis.getMietende(), bis);
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
        LocalDate beginn = mietbeginnImZeitraum(basis.getMietbeginn(), von);
        LocalDate ende = mietendeImZeitraum(basis.getMietende(), bis);
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
                                                 Map<Long, NkPositionSummeDTO> summen) {

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
                            mengeJePosition, laufendeSumme, summen)
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
                                        Map<Long, NkPositionSummeDTO> summen) {
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

                // Damit der Betrag auf der Rechnung nachvollziehbar ist: Totalbetrag als
                // Bezugsgroesse, Zeit- bzw. Personenanteil als Prozentsatz. In der Web-Maske
                // aendert das nichts - sie liest den Prozentsatz nur bei ANTEIL.
                zeile.setBezugsbetrag(nullSicher(p.getTotalbetrag())
                        .setScale(GELD_SCALE, RoundingMode.HALF_UP));
                zeile.setProzentsatz(anteil.multiply(HUNDERT)
                        .setScale(MENGE_SCALE, RoundingMode.HALF_UP));

                merke(summen.get(p.getId()), zeile.getMenge(), betrag);
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
                merke(summen.get(p.getId()), menge, zeile.getBetrag());
            }
            case ANTEIL -> {
                // Der Prozentsatz je Mieter steht dort, wo bei VERBRAUCH die Menge steht.
                BigDecimal prozent = mengeJePosition
                        .getOrDefault(p.getId(), Map.of())
                        .get(basis.getMieterId());
                zeile.setProzentsatz(prozent);
                zeile.setBezugsbetrag(nullSicher(p.getTotalbetrag())
                        .setScale(GELD_SCALE, RoundingMode.HALF_UP));
                BigDecimal betrag = nullSicher(p.getTotalbetrag()).multiply(nullSicher(prozent))
                        .divide(HUNDERT, ZWISCHEN_SCALE, RoundingMode.HALF_UP)
                        .setScale(GELD_SCALE, RoundingMode.HALF_UP);
                zeile.setBetrag(betrag);

                NkPositionSummeDTO summe = summen.get(p.getId());
                if (summe != null) {
                    // Bei ANTEIL ist die Bezugsgroesse der Prozentsatz, nicht eine Menge.
                    merke(summe, null, betrag);
                    summe.setSummeProzent(nullSicher(summe.getSummeProzent())
                            .add(nullSicher(prozent)));
                }
            }
            case ZUSCHLAG -> {
                zeile.setProzentsatz(p.getProzentsatz());
                // Das Zwischentotal der Zeilen davor - genau die Groesse, auf der der Zuschlag
                // rechnet (Kaskade, FR-2).
                zeile.setBezugsbetrag(laufendeSumme.setScale(GELD_SCALE, RoundingMode.HALF_UP));
                zeile.setBetrag(laufendeSumme.multiply(nullSicher(p.getProzentsatz()))
                        .divide(HUNDERT, ZWISCHEN_SCALE, RoundingMode.HALF_UP)
                        .setScale(GELD_SCALE, RoundingMode.HALF_UP));
                merke(summen.get(p.getId()), null, zeile.getBetrag());
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
    private void setzeAbweichungen(NkPositionSummeDTO summe, long summeTage, long nenner,
                                   long summePersonenTage, long nennerPerson) {
        BigDecimal anteil = switch (summe.getArt()) {
            case ANTEIL -> nullSicher(summe.getSummeProzent())
                    .divide(HUNDERT, ZWISCHEN_SCALE, RoundingMode.HALF_UP);
            case UMLAGE_PERSON -> anteil(summePersonenTage, nennerPerson);
            default -> anteil(summeTage, nenner);
        };

        BigDecimal total = nullSicher(summe.getTotalbetrag());
        BigDecimal exaktVerteilbar = total.multiply(anteil)
                .setScale(GELD_SCALE, RoundingMode.HALF_UP);
        summe.setNichtVerteilt(total.subtract(exaktVerteilbar));
        summe.setRundungsdifferenz(exaktVerteilbar.subtract(summe.getSummeKosten()));
    }

    /** Verteilt die Art einen erfassten Gesamtbetrag auf die Mieter? */
    private static boolean verteilendeArt(NkPositionsart art) {
        return art == NkPositionsart.UMLAGE
                || art == NkPositionsart.UMLAGE_PERSON
                || art == NkPositionsart.ANTEIL;
    }

    /**
     * Nimmt Menge und Betrag einer Mieterzeile in die Zusammenstellung ihrer Position auf.
     *
     * <p>Eine <b>nicht erfasste</b> Menge lässt die Summe unangetastet: Sonst stünde bei einer
     * Verbrauchsposition, für die noch niemand etwas eingetragen hat, eine 0 — und die sähe aus
     * wie eine gemessene Null.
     */
    private static void merke(NkPositionSummeDTO summe, BigDecimal menge, BigDecimal betrag) {
        if (summe == null) {
            return;
        }
        summe.setSummeKosten(summe.getSummeKosten().add(nullSicher(betrag)));
        if (menge != null) {
            summe.setSummeMenge(nullSicher(summe.getSummeMenge()).add(menge)
                    .setScale(MENGE_SCALE, RoundingMode.HALF_UP));
        }
    }

    /**
     * Sammelzeile aller Zusatzpositionen; {@code null}, wenn es keine gibt.
     *
     * <p>Die Mengeneinheit bleibt leer, sobald die Zusatzpositionen <b>verschiedene</b> Einheiten
     * mischen — und dann auch die Menge: „2 Stück plus 3 m³" ist keine Menge, sondern zwei. Die
     * Kosten bleiben in jedem Fall summierbar, denn Franken sind Franken.
     */
    private static NkPositionSummeDTO zusatzZeile(List<NkZusatz> zusaetze) {
        if (zusaetze.isEmpty()) {
            return null;
        }

        NkPositionSummeDTO summe = new NkPositionSummeDTO();
        summe.setZusatz(true);

        BigDecimal kosten = BigDecimal.ZERO;
        BigDecimal menge = BigDecimal.ZERO;
        Mengeneinheit einheit = null;
        boolean einheitlich = true;

        for (NkZusatz z : zusaetze) {
            kosten = kosten.add(nullSicher(z.getMenge())
                    .multiply(nullSicher(z.getBetragProEinheit()))
                    .setScale(GELD_SCALE, RoundingMode.HALF_UP));
            menge = menge.add(nullSicher(z.getMenge()));
            if (einheit == null) {
                einheit = z.getEinheit();
            } else if (einheit != z.getEinheit()) {
                einheitlich = false;
            }
        }

        summe.setSummeKosten(kosten);
        if (einheitlich) {
            summe.setEinheit(einheit);
            summe.setSummeMenge(menge.setScale(MENGE_SCALE, RoundingMode.HALF_UP));
        }
        return summe;
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
