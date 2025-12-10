package ch.nacht.service;

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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StatistikService {

    private static final Logger logger = LoggerFactory.getLogger(StatistikService.class);
    private static final double TOLERANZ = 0.1;

    private final MesswerteRepository messwerteRepository;
    private final EinheitRepository einheitRepository;

    public StatistikService(MesswerteRepository messwerteRepository, EinheitRepository einheitRepository) {
        this.messwerteRepository = messwerteRepository;
        this.einheitRepository = einheitRepository;
    }

    @Cacheable(value = "statistik", key = "#von.toString() + '-' + #bis.toString()")
    public StatistikDTO getStatistik(LocalDate von, LocalDate bis) {
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

    public LocalDate ermittleLetztesMessdatum() {
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

        // Producer values are negative, use absolute values for display
        dto.setSummeProducerTotal(summeProducerTotal != null ? Math.abs(summeProducerTotal) : 0.0);
        dto.setSummeConsumerTotal(summeConsumerTotal != null ? summeConsumerTotal : 0.0);
        dto.setSummeProducerZev(summeProducerZev != null ? Math.abs(summeProducerZev) : 0.0);
        dto.setSummeConsumerZev(summeConsumerZev != null ? summeConsumerZev : 0.0);
        dto.setSummeConsumerZevCalculated(summeConsumerZevCalculated != null ? summeConsumerZevCalculated : 0.0);

        logger.debug("Monat {}/{}: ProducerTotal={}, ConsumerTotal={}, ProducerZev={}, ConsumerZev={}, ConsumerZevCalc={}",
                yearMonth.getYear(), yearMonth.getMonthValue(),
                summeProducerTotal, summeConsumerTotal, summeProducerZev, summeConsumerZev, summeConsumerZevCalculated);

        // Vergleiche durchführen
        vergleicheSummen(dto);

        // Datenvollständigkeit für diesen Monat prüfen
        pruefeDatenVollstaendigkeitMonat(dto, effektivVon, effektivBis);

        // Tage mit Abweichungen ermitteln
        ermittleTageAbweichungen(dto, effektivVon, effektivBis);

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
}
