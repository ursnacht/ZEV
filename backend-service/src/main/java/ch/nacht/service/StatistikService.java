package ch.nacht.service;

import ch.nacht.dto.EinheitSummenDTO;
import ch.nacht.dto.MonatsStatistikDTO;
import ch.nacht.dto.StatistikDTO;
import ch.nacht.dto.TagMitAbweichungDTO;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.Messwerte;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.MesswerteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StatistikService {

    private static final Logger logger = LoggerFactory.getLogger(StatistikService.class);
    private static final double TOLERANZ = 0.1;

    /** Anzeige-Reihenfolge der Typen in "Summen pro Einheit" (Web und PDF-Subreport). */
    private static final List<EinheitTyp> TYP_ANZEIGE_REIHENFOLGE = List.of(
            EinheitTyp.PRODUCER, EinheitTyp.CONSUMER, EinheitTyp.RUECKLIEFERUNG, EinheitTyp.BEZUG);

    private final MesswerteRepository messwerteRepository;
    private final EinheitRepository einheitRepository;
    private final HibernateFilterService hibernateFilterService;
    private final OrganizationContextService organizationContextService;
    private final EinstellungenService einstellungenService;
    private final TranslationService translationService;

    public StatistikService(MesswerteRepository messwerteRepository,
                            EinheitRepository einheitRepository,
                            HibernateFilterService hibernateFilterService,
                            OrganizationContextService organizationContextService,
                            EinstellungenService einstellungenService,
                            TranslationService translationService) {
        this.messwerteRepository = messwerteRepository;
        this.einheitRepository = einheitRepository;
        this.hibernateFilterService = hibernateFilterService;
        this.organizationContextService = organizationContextService;
        this.einstellungenService = einstellungenService;
        this.translationService = translationService;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "statistik", key = "#von.toString() + '-' + #bis.toString() + '-' + @organizationContextService.getCurrentOrgId()")
    public StatistikDTO getStatistik(LocalDate von, LocalDate bis) {
        hibernateFilterService.enableOrgFilter();
        logger.info("Berechne Statistik für Zeitraum {} bis {}", von, bis);

        StatistikDTO statistik = new StatistikDTO();
        statistik.setToleranz(TOLERANZ);
        statistik.setVerteilmodus(einstellungenService.getVerteilmodus(organizationContextService.getCurrentOrgId()));

        // Letztes Datum mit Messwerten ermitteln
        LocalDate letztesMessdatum = ermittleLetztesMessdatum();
        statistik.setMesswerteBisDate(letztesMessdatum);

        // Datenvollständigkeit prüfen
        pruefeDatenVollstaendigkeit(statistik, von, bis);

        // Monatsstatistiken berechnen
        List<MonatsStatistikDTO> monatsStatistiken = berechneMonatsStatistiken(von, bis);
        statistik.setMonate(monatsStatistiken);

        // Gesamtvollständigkeit basierend auf Monaten
        boolean alleMonateVollstaendig = monatsStatistiken.stream()
                .allMatch(MonatsStatistikDTO::isDatenVollstaendig);
        statistik.setDatenVollstaendig(alleMonateVollstaendig && statistik.getFehlendeEinheiten().isEmpty());

        logger.info("Statistik berechnet: {} Monate, Daten vollständig: {}",
                monatsStatistiken.size(), statistik.isDatenVollstaendig());

        return statistik;
    }

    @Transactional(readOnly = true)
    public LocalDate ermittleLetztesMessdatum() {
        hibernateFilterService.enableOrgFilter();
        return messwerteRepository.findMaxZeit()
                .map(LocalDateTime::toLocalDate)
                .orElse(null);
    }

    private void pruefeDatenVollstaendigkeit(StatistikDTO statistik, LocalDate von, LocalDate bis) {
        LocalDateTime vonDateTime = von.atStartOfDay();
        LocalDateTime bisDateTime = bis.plusDays(1).atStartOfDay();

        // Alle Einheiten holen
        List<Einheit> alleEinheiten = einheitRepository.findAll();

        // Einheiten mit Daten im Zeitraum
        List<Einheit> einheitenMitDaten = messwerteRepository.findDistinctEinheitenInRange(vonDateTime, bisDateTime);

        // Fehlende Einheiten ermitteln
        Set<Long> einheitenMitDatenIds = einheitenMitDaten.stream()
                .map(Einheit::getId)
                .collect(Collectors.toSet());

        List<String> fehlendeEinheiten = alleEinheiten.stream()
                .filter(e -> !einheitenMitDatenIds.contains(e.getId()))
                .map(Einheit::getName)
                .collect(Collectors.toList());

        statistik.setFehlendeEinheiten(fehlendeEinheiten);

        // Fehlende Tage ermitteln
        List<LocalDate> tageImBereich = von.datesUntil(bis.plusDays(1)).collect(Collectors.toList());
        List<LocalDate> tageMitDaten = messwerteRepository.findDistinctDatesInRange(vonDateTime, bisDateTime);

        List<LocalDate> fehlendeTage = tageImBereich.stream()
                .filter(tag -> !tageMitDaten.contains(tag))
                .collect(Collectors.toList());

        statistik.setFehlendeTage(fehlendeTage);

        if (!fehlendeEinheiten.isEmpty()) {
            logger.warn("Fehlende Einheiten im Zeitraum: {}", fehlendeEinheiten);
        }
        if (!fehlendeTage.isEmpty()) {
            logger.warn("Fehlende Tage im Zeitraum: {} Tage", fehlendeTage.size());
        }
    }

    private List<MonatsStatistikDTO> berechneMonatsStatistiken(LocalDate von, LocalDate bis) {
        List<MonatsStatistikDTO> monatsStatistiken = new ArrayList<>();

        YearMonth startMonat = YearMonth.from(von);
        YearMonth endMonat = YearMonth.from(bis);

        YearMonth aktuellerMonat = startMonat;
        while (!aktuellerMonat.isAfter(endMonat)) {
            MonatsStatistikDTO monatsStatistik = berechneMonatsStatistik(aktuellerMonat, von, bis);
            monatsStatistiken.add(monatsStatistik);
            aktuellerMonat = aktuellerMonat.plusMonths(1);
        }

        return monatsStatistiken;
    }

    private MonatsStatistikDTO berechneMonatsStatistik(YearMonth yearMonth, LocalDate gesamtVon, LocalDate gesamtBis) {
        MonatsStatistikDTO dto = new MonatsStatistikDTO();
        dto.setJahr(yearMonth.getYear());
        dto.setMonat(yearMonth.getMonthValue());

        // Von/Bis für diesen Monat berechnen (berücksichtigt Gesamtzeitraum)
        LocalDate monatsStart = yearMonth.atDay(1);
        LocalDate monatsEnde = yearMonth.atEndOfMonth();

        LocalDate effektivVon = monatsStart.isBefore(gesamtVon) ? gesamtVon : monatsStart;
        LocalDate effektivBis = monatsEnde.isAfter(gesamtBis) ? gesamtBis : monatsEnde;

        dto.setVon(effektivVon);
        dto.setBis(effektivBis);

        LocalDateTime vonDateTime = effektivVon.atStartOfDay();
        LocalDateTime bisDateTime = effektivBis.plusDays(1).atStartOfDay();

        // Summen berechnen
        Double summeProducerTotal = messwerteRepository.sumTotalByEinheitTypAndZeitBetween(
                EinheitTyp.PRODUCER, vonDateTime, bisDateTime);
        Double summeConsumerTotal = messwerteRepository.sumTotalByEinheitTypAndZeitBetween(
                EinheitTyp.CONSUMER, vonDateTime, bisDateTime);
        Double summeProducerZev = messwerteRepository.sumZevByEinheitTypAndZeitBetween(
                EinheitTyp.PRODUCER, vonDateTime, bisDateTime);
        Double summeConsumerZev = messwerteRepository.sumZevByEinheitTypAndZeitBetween(
                EinheitTyp.CONSUMER, vonDateTime, bisDateTime);
        Double summeConsumerZevCalculated = messwerteRepository.sumZevCalculatedByEinheitTypAndZeitBetween(
                EinheitTyp.CONSUMER, vonDateTime, bisDateTime);
        Double summeBilanzBezug = messwerteRepository.sumTotalByEinheitTypAndZeitBetween(
                EinheitTyp.BEZUG, vonDateTime, bisDateTime);
        Double summeBilanzRuecklieferung = messwerteRepository.sumTotalByEinheitTypAndZeitBetween(
                EinheitTyp.RUECKLIEFERUNG, vonDateTime, bisDateTime);

        // Producer values are negative, use absolute values for display
        dto.setSummeProducerTotal(summeProducerTotal != null ? Math.abs(summeProducerTotal) : 0.0);
        dto.setSummeConsumerTotal(summeConsumerTotal != null ? summeConsumerTotal : 0.0);
        dto.setSummeProducerZev(summeProducerZev != null ? Math.abs(summeProducerZev) : 0.0);
        dto.setSummeConsumerZev(summeConsumerZev != null ? summeConsumerZev : 0.0);
        dto.setSummeConsumerZevCalculated(summeConsumerZevCalculated != null ? summeConsumerZevCalculated : 0.0);
        // Bilanzmesspunkte: Bezug positiv, Rücklieferung negativ → Vergleich über Beträge.
        // Fehlende Daten → 0.0.
        dto.setBilanzBezug(summeBilanzBezug != null ? summeBilanzBezug : 0.0);
        dto.setBilanzRuecklieferung(summeBilanzRuecklieferung != null ? Math.abs(summeBilanzRuecklieferung) : 0.0);
        // Namen der Bilanz-Einheiten (max. eine je Typ und Mandant, orgFilter aktiv);
        // null = keine Einheit → Bilanz-Zeile und -Vergleich werden nicht angezeigt (FR-4.6/FR-5.7).
        dto.setBilanzBezugName(einheitRepository.findFirstByTyp(EinheitTyp.BEZUG)
                .map(Einheit::getName).orElse(null));
        dto.setBilanzRuecklieferungName(einheitRepository.findFirstByTyp(EinheitTyp.RUECKLIEFERUNG)
                .map(Einheit::getName).orElse(null));

        logger.debug("Monat {}/{}: ProducerTotal={}, ConsumerTotal={}, ProducerZev={}, ConsumerZev={}, ConsumerZevCalc={}",
                yearMonth.getYear(), yearMonth.getMonthValue(),
                summeProducerTotal, summeConsumerTotal, summeProducerZev, summeConsumerZev, summeConsumerZevCalculated);

        // Berechnete Werte (nur fuer den Summen-Vergleich gegen die Bilanz-Einheiten):
        // Bezug von VNB  = Verbrauch (Consumer Total) − zev der Consumer (B, gemessen –
        //                  Messung gegen Messung; nicht zev_berechnet)
        // Rücklieferung  = Produktion (Producer Total) − zev der Producer (A)
        dto.setBezugVonVnb(dto.getSummeConsumerTotal() - dto.getSummeConsumerZev());
        dto.setRuecklieferung(dto.getSummeProducerTotal() - dto.getSummeProducerZev());

        logger.debug("Monat {}/{}: BezugVonVnb={}, Ruecklieferung={}",
                yearMonth.getYear(), yearMonth.getMonthValue(), dto.getBezugVonVnb(), dto.getRuecklieferung());

        // Vergleiche durchführen
        vergleicheSummen(dto);

        // Datenvollständigkeit für diesen Monat prüfen
        pruefeDatenVollstaendigkeitMonat(dto, effektivVon, effektivBis);

        // Tage mit Abweichungen ermitteln
        ermittleTageAbweichungen(dto, effektivVon, effektivBis);

        // Summen pro Einheit berechnen
        berechneEinheitSummen(dto, vonDateTime, bisDateTime);

        // Statistik-Kennzahlen (Stufe 1: aus den Summen)
        berechneKennzahlen(dto, vonDateTime, bisDateTime);
        // Batterie geladen/entladen/Wirkungsgrad (Stufe 2: Pro-Intervall-Aggregation)
        berechneBatterieKennzahlen(dto, vonDateTime, bisDateTime);

        return dto;
    }

    /**
     * Berechnet die Statistik-Kennzahlen (Spec Statistik-Kennzahlen.md, Stufe 1) aus den
     * bereits vorhandenen Monats-Summen. Quoten-KPIs sind modus-agnostisch (nur ZEV-/Total-Summen);
     * `null` bei Nenner 0. Der Netto-Speicherfluss setzt Producer + Bilanz-Bezug + Rücklieferung
     * voraus (sonst `null`). Batterie geladen/entladen/Wirkungsgrad werden in Stufe 2 ergänzt.
     *
     * <p>Zusätzlich die <b>gemessenen</b> Gegenstücke aus dem Netzbezug der BEZUG-Einheit
     * (FR-1.7): Autarkiegrad und Netzbezugsquote aus {@code B / C} statt aus dem ZEV-Anteil der
     * Consumer. Beide Zahlen sind richtig, messen aber Verschiedenes – die Differenz ist der
     * Verbrauchsanteil, der weder direkt aus der PV noch aus dem Netz kam (typischerweise
     * Batterie-Entladung).
     */
    private void berechneKennzahlen(MonatsStatistikDTO dto, LocalDateTime von, LocalDateTime bis) {
        double p = dto.getSummeProducerTotal() != null ? dto.getSummeProducerTotal() : 0.0;
        double c = dto.getSummeConsumerTotal() != null ? dto.getSummeConsumerTotal() : 0.0;
        double cz = dto.getSummeConsumerZev() != null ? dto.getSummeConsumerZev() : 0.0;
        double pz = dto.getSummeProducerZev() != null ? dto.getSummeProducerZev() : 0.0;

        // Quoten-KPIs (Anteil 0..1); null wenn Nenner 0
        dto.setAutarkiegrad(c > 0 ? cz / c : null);
        dto.setNetzbezugsquote(c > 0 ? (c - cz) / c : null);
        dto.setEigenverbrauchsquote(p > 0 ? pz / p : null);
        dto.setEinspeisequote(p > 0 ? (p - pz) / p : null);
        dto.setZevEigenverbrauch(cz);

        // Gemessene Gegenstücke (nur mit BEZUG-Einheit und Verbrauch > 0)
        boolean bilanzVerfuegbar = dto.getBilanzBezugName() != null && c > 0;
        dto.setBilanzKennzahlenVerfuegbar(bilanzVerfuegbar);
        if (bilanzVerfuegbar) {
            double b = dto.getBilanzBezug() != null ? dto.getBilanzBezug() : 0.0;
            dto.setNetzbezugsquoteGemessen(b / c);
            dto.setAutarkiegradGemessen(1.0 - b / c);
            // Lücken in der Bilanzmessung machen B zu klein und den Autarkiegrad zu optimistisch.
            // Die Vollständigkeitsprüfung arbeitet tage- und einheitengenau und sieht fehlende
            // Intervalle innerhalb eines Tages nicht - deshalb hier eine eigene Zählung.
            long intervalleBezug = messwerteRepository
                    .countDistinctZeitByEinheitTypAndZeitBetween(EinheitTyp.BEZUG, von, bis);
            long intervalleConsumer = messwerteRepository
                    .countDistinctZeitByEinheitTypAndZeitBetween(EinheitTyp.CONSUMER, von, bis);
            dto.setBilanzBezugLueckenhaft(intervalleBezug < intervalleConsumer);
            if (dto.isBilanzBezugLueckenhaft()) {
                logger.warn("Monat {}/{}: Bilanz-Bezug deckt nur {} von {} Intervallen ab - "
                        + "die gemessenen Kennzahlen sind zu optimistisch",
                        dto.getJahr(), dto.getMonat(), intervalleBezug, intervalleConsumer);
            }
        } else {
            dto.setNetzbezugsquoteGemessen(null);
            dto.setAutarkiegradGemessen(null);
            dto.setBilanzBezugLueckenhaft(false);
        }

        // Netto-Speicherfluss (berechnet/geschätzt): nur bei Producer + Bilanz-Bezug + Rücklieferung
        boolean producerVorhanden = p > 0;
        boolean bezugVorhanden = dto.getBilanzBezugName() != null;
        boolean ruecklieferungVorhanden = dto.getBilanzRuecklieferungName() != null;
        boolean batterieVerfuegbar = producerVorhanden && bezugVorhanden && ruecklieferungVorhanden;
        dto.setBatterieKennzahlenVerfuegbar(batterieVerfuegbar);
        if (batterieVerfuegbar) {
            double b = dto.getBilanzBezug() != null ? dto.getBilanzBezug() : 0.0;
            double r = dto.getBilanzRuecklieferung() != null ? dto.getBilanzRuecklieferung() : 0.0;
            dto.setBatterieNetto(p - c + b - r);
        } else {
            dto.setBatterieNetto(null);
        }
    }

    /**
     * Batterie-Kennzahlen (Spec Statistik-Kennzahlen.md, Stufe 2): geladen/entladen/Wirkungsgrad
     * aus der Pro-Intervall-Aggregation. Je Intervall {@code Netto_i = P_i − C_i + B_i − R_i};
     * geladen = Σ max(0, Netto_i), entladen = Σ max(0, −Netto_i), Wirkungsgrad = entladen/geladen
     * (nur wenn geladen > 0). Nur wenn Producer + Bilanz-Bezug + Rücklieferung vorhanden.
     */
    private void berechneBatterieKennzahlen(MonatsStatistikDTO dto, LocalDateTime vonDateTime, LocalDateTime bisDateTime) {
        if (!dto.isBatterieKennzahlenVerfuegbar()) {
            return;
        }
        double geladen = 0.0;
        double entladen = 0.0;
        for (Object[] row : messwerteRepository.sumBilanzKomponentenPerZeitBetween(vonDateTime, bisDateTime)) {
            double pI = ((Number) row[1]).doubleValue();
            double cI = ((Number) row[2]).doubleValue();
            double bI = ((Number) row[3]).doubleValue();
            double rI = ((Number) row[4]).doubleValue();
            double netto = pI - cI + bI - rI;
            if (netto > 0) {
                geladen += netto;
            } else {
                entladen += -netto;
            }
        }
        dto.setBatterieGeladen(geladen);
        dto.setBatterieEntladen(entladen);
        dto.setBatterieWirkungsgrad(geladen > 0 ? entladen / geladen : null);
    }

    private void vergleicheSummen(MonatsStatistikDTO dto) {
        Double summeC = dto.getSummeProducerZev();
        Double summeD = dto.getSummeConsumerZev();
        Double summeE = dto.getSummeConsumerZevCalculated();

        // C vs D
        double differenzCD = summeC - summeD;
        dto.setSummenCDGleich(Math.abs(differenzCD) < TOLERANZ);
        dto.setDifferenzCD(differenzCD);

        // C vs E
        double differenzCE = summeC - summeE;
        dto.setSummenCEGleich(Math.abs(differenzCE) < TOLERANZ);
        dto.setDifferenzCE(differenzCE);

        // D vs E
        double differenzDE = summeD - summeE;
        dto.setSummenDEGleich(Math.abs(differenzDE) < TOLERANZ);
        dto.setDifferenzDE(differenzDE);

        // Berechneter "Bezug von VNB" vs. Bilanzmesspunkt BEZUG
        double bezugBilanzDifferenz = dto.getBezugVonVnb() - dto.getBilanzBezug();
        dto.setBezugBilanzGleich(Math.abs(bezugBilanzDifferenz) < TOLERANZ);
        dto.setBezugBilanzDifferenz(bezugBilanzDifferenz);

        // Berechnete "Rücklieferung" vs. Bilanzmesspunkt RUECKLIEFERUNG (beide als Betrag)
        double ruecklieferungBilanzDifferenz = dto.getRuecklieferung() - dto.getBilanzRuecklieferung();
        dto.setRuecklieferungBilanzGleich(Math.abs(ruecklieferungBilanzDifferenz) < TOLERANZ);
        dto.setRuecklieferungBilanzDifferenz(ruecklieferungBilanzDifferenz);
    }

    private void pruefeDatenVollstaendigkeitMonat(MonatsStatistikDTO dto, LocalDate von, LocalDate bis) {
        LocalDateTime vonDateTime = von.atStartOfDay();
        LocalDateTime bisDateTime = bis.plusDays(1).atStartOfDay();

        // Alle Einheiten
        List<Einheit> alleEinheiten = einheitRepository.findAll();

        // Einheiten mit Daten in diesem Monat
        List<Einheit> einheitenMitDaten = messwerteRepository.findDistinctEinheitenInRange(vonDateTime, bisDateTime);

        Set<Long> einheitenMitDatenIds = einheitenMitDaten.stream()
                .map(Einheit::getId)
                .collect(Collectors.toSet());

        List<String> fehlendeEinheiten = alleEinheiten.stream()
                .filter(e -> !einheitenMitDatenIds.contains(e.getId()))
                .map(Einheit::getName)
                .collect(Collectors.toList());

        dto.setFehlendeEinheiten(fehlendeEinheiten);

        // Fehlende Tage
        List<LocalDate> tageImMonat = von.datesUntil(bis.plusDays(1)).collect(Collectors.toList());
        List<LocalDate> tageMitDaten = messwerteRepository.findDistinctDatesInRange(vonDateTime, bisDateTime);

        List<LocalDate> fehlendeTage = tageImMonat.stream()
                .filter(tag -> !tageMitDaten.contains(tag))
                .collect(Collectors.toList());

        dto.setFehlendeTage(fehlendeTage);

        dto.setDatenVollstaendig(fehlendeEinheiten.isEmpty() && fehlendeTage.isEmpty());
    }

    private void ermittleTageAbweichungen(MonatsStatistikDTO dto, LocalDate von, LocalDate bis) {
        List<TagMitAbweichungDTO> abweichungen = new ArrayList<>();

        List<LocalDate> tageImBereich = von.datesUntil(bis.plusDays(1)).collect(Collectors.toList());

        for (LocalDate tag : tageImBereich) {
            LocalDateTime tagStart = tag.atStartOfDay();
            LocalDateTime tagEnde = tag.plusDays(1).atStartOfDay();

            Double tagesSummeProducer = messwerteRepository.sumZevByEinheitTypAndZeitBetween(
                    EinheitTyp.PRODUCER, tagStart, tagEnde);
            Double tagesSummeD = messwerteRepository.sumZevByEinheitTypAndZeitBetween(
                    EinheitTyp.CONSUMER, tagStart, tagEnde);
            Double tagesSummeE = messwerteRepository.sumZevCalculatedByEinheitTypAndZeitBetween(
                    EinheitTyp.CONSUMER, tagStart, tagEnde);

            // Producer values are negative, use absolute value for comparison
            double tagesSummeC = tagesSummeProducer != null ? Math.abs(tagesSummeProducer) : 0.0;
            double summeD = tagesSummeD != null ? tagesSummeD : 0.0;
            double summeE = tagesSummeE != null ? tagesSummeE : 0.0;

            // Prüfen ob Abweichungen vorliegen
            List<String> abweichungsTypen = new ArrayList<>();

            if (Math.abs(tagesSummeC - summeD) >= TOLERANZ) {
                abweichungsTypen.add("C!=D");
            }
            if (Math.abs(tagesSummeC - summeE) >= TOLERANZ) {
                abweichungsTypen.add("C!=E");
            }
            if (Math.abs(summeD - summeE) >= TOLERANZ) {
                abweichungsTypen.add("D!=E");
            }

            if (!abweichungsTypen.isEmpty()) {
                // Maximale Differenz als Referenz
                double maxDifferenz = Math.max(
                        Math.max(Math.abs(tagesSummeC - summeD), Math.abs(tagesSummeC - summeE)),
                        Math.abs(summeD - summeE));

                TagMitAbweichungDTO abweichung = new TagMitAbweichungDTO(
                        tag,
                        String.join(", ", abweichungsTypen),
                        maxDifferenz);
                abweichungen.add(abweichung);
            }
        }

        dto.setTageAbweichungen(abweichungen);
    }

    private void berechneEinheitSummen(MonatsStatistikDTO dto, LocalDateTime vonDateTime, LocalDateTime bisDateTime) {
        List<Einheit> alleEinheiten = einheitRepository.findAll();
        List<EinheitSummenDTO> einheitSummen = new ArrayList<>();

        for (Einheit einheit : alleEinheiten) {
            Double summeTotal = messwerteRepository.sumTotalByEinheitAndZeitBetween(einheit, vonDateTime, bisDateTime);
            Double summeZev = messwerteRepository.sumZevByEinheitAndZeitBetween(einheit, vonDateTime, bisDateTime);
            Double summeZevCalculated = messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(einheit, vonDateTime, bisDateTime);

            // Producer-/Bilanz-Werte können negativ gespeichert sein → Absolutwerte für die Anzeige
            if (einheit.getTyp() != EinheitTyp.CONSUMER) {
                summeTotal = summeTotal != null ? Math.abs(summeTotal) : 0.0;
                summeZev = summeZev != null ? Math.abs(summeZev) : 0.0;
            } else {
                summeTotal = summeTotal != null ? summeTotal : 0.0;
                summeZev = summeZev != null ? summeZev : 0.0;
            }
            summeZevCalculated = summeZevCalculated != null ? summeZevCalculated : 0.0;

            EinheitSummenDTO einheitSummenDTO = new EinheitSummenDTO(
                    einheit.getId(),
                    einheit.getName(),
                    einheit.getTyp(),
                    summeTotal,
                    summeZev,
                    summeZevCalculated
            );
            einheitSummen.add(einheitSummenDTO);
        }

        // Anzeige-Reihenfolge nach Typ (Produzenten, Konsumenten, Rücklieferung, Bezug), dann Name
        einheitSummen.sort((a, b) -> {
            int typeCompare = Integer.compare(TYP_ANZEIGE_REIHENFOLGE.indexOf(a.getEinheitTyp()),
                    TYP_ANZEIGE_REIHENFOLGE.indexOf(b.getEinheitTyp()));
            if (typeCompare != 0) return typeCompare;
            return a.getEinheitName().compareTo(b.getEinheitName());
        });

        dto.setEinheitSummen(einheitSummen);
    }

    // ==================== CSV-Export der 15-Min-Werte je Consumer (Spec Export-Messdaten) ====================

    /**
     * Erzeugt eine CSV der 15-Minuten-Messwerte einer <b>Consumer</b>-Einheit für den Zeitraum
     * (Spalten: Datum+Zeit, Energiebezug Total, Anteil Bezug aus ZEV). Die Spaltentitel werden je
     * {@code sprache} übersetzt; das Monatstotal im Titel wird aus derselben Rundungsbasis (Summe der
     * auf 3 NKS gerundeten Intervallwerte) gebildet, sodass Header == Summe der Zeilen.
     *
     * <p>Sicherheit: Geladen wird über {@code findFirstById} — eine abgeleitete und damit
     * gefilterte Abfrage. Die zusätzliche explizite Prüfung der {@code org_id} bleibt als zweite
     * Verteidigungslinie stehen: Sie greift auch dann, wenn der Filter nicht eingeschaltet wäre.
     */
    @Transactional(readOnly = true)
    public byte[] exportMesswerteCsv(Long einheitId, LocalDate von, LocalDate bis, String sprache) {
        hibernateFilterService.enableOrgFilter();

        Einheit einheit = einheitRepository.findFirstById(einheitId)
                .orElseThrow(() -> new IllegalArgumentException("EINHEIT_NICHT_GEFUNDEN"));
        // Zweite Verteidigungslinie, unabhaengig vom Mandantenfilter
        if (!Objects.equals(einheit.getOrgId(), organizationContextService.getCurrentOrgId())) {
            throw new IllegalArgumentException("EINHEIT_NICHT_GEFUNDEN");
        }
        if (einheit.getTyp() != EinheitTyp.CONSUMER) {
            throw new IllegalArgumentException("EXPORT_NUR_CONSUMER");
        }

        List<Messwerte> werte = messwerteRepository.findByEinheitAndZeitBetween(
                einheit, von.atStartOfDay(), bis.atTime(23, 59, 59));
        werte.sort(Comparator.comparing(Messwerte::getZeit));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        // Skala 3 fixieren, damit auch das leere Ergebnis konsistent "0.000" ausgibt (3 NKS).
        BigDecimal totalSum = BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
        BigDecimal zevSum = BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
        List<String> zeilen = new ArrayList<>();
        for (Messwerte m : werte) {
            BigDecimal total = round3(m.getTotal());
            BigDecimal zev = round3(m.getZev());
            totalSum = totalSum.add(total);
            zevSum = zevSum.add(zev);
            zeilen.add(csv(m.getZeit().format(fmt)) + "," + total.toPlainString() + "," + zev.toPlainString());
        }

        String titelZeit = translate("EXPORT_SPALTE_DATUM_ZEIT", sprache);
        String titelTotal = translate("EXPORT_SPALTE_ENERGIEBEZUG_TOTAL", sprache);
        String titelZev = translate("EXPORT_SPALTE_ANTEIL_ZEV", sprache);

        StringBuilder sb = new StringBuilder();
        sb.append(csv(titelZeit)).append(',')
                .append(csv(titelTotal + " (" + totalSum.toPlainString() + ")")).append(',')
                .append(csv(titelZev + " (" + zevSum.toPlainString() + ")")).append('\n');
        for (String zeile : zeilen) {
            sb.append(zeile).append('\n');
        }

        logger.info("CSV-Export für Einheit {} ({} – {}): {} Zeilen", einheitId, von, bis, zeilen.size());
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Übersetzt einen Key je Sprache (analog {@code StatistikPdfService}); Fallback = Key. */
    private String translate(String key, String sprache) {
        return translationService.getTranslationByKey(key)
                .map(t -> "en".equalsIgnoreCase(sprache) ? t.getEnglisch() : t.getDeutsch())
                .filter(v -> v != null && !v.isBlank())
                .orElse(key);
    }

    private static BigDecimal round3(Double v) {
        return BigDecimal.valueOf(v != null ? v : 0.0).setScale(3, RoundingMode.HALF_UP);
    }

    /** CSV-Feld escapen (nur nötig bei Komma/Quote/Zeilenumbruch, z.B. in übersetzten Titeln). */
    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
