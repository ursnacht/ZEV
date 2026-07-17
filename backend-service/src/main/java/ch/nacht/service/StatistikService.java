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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
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

    public StatistikService(MesswerteRepository messwerteRepository,
                            EinheitRepository einheitRepository,
                            HibernateFilterService hibernateFilterService) {
        this.messwerteRepository = messwerteRepository;
        this.einheitRepository = einheitRepository;
        this.hibernateFilterService = hibernateFilterService;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "statistik", key = "#von.toString() + '-' + #bis.toString() + '-' + @organizationContextService.getCurrentOrgId()")
    public StatistikDTO getStatistik(LocalDate von, LocalDate bis) {
        hibernateFilterService.enableOrgFilter();
        logger.info("Berechne Statistik für Zeitraum {} bis {}", von, bis);

        StatistikDTO statistik = new StatistikDTO();
        statistik.setToleranz(TOLERANZ);

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

        return dto;
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
}
