package ch.nacht.service;

import ch.nacht.ProportionalConsumptionDistribution;
import ch.nacht.SolarDistribution;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.MeldungLevel;
import ch.nacht.entity.Messwerte;
import ch.nacht.entity.Quelle;
import ch.nacht.entity.Verteilmodus;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.MesswerteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MesswerteService {

    private static final Logger log = LoggerFactory.getLogger(MesswerteService.class);
    private final MesswerteRepository messwerteRepository;
    private final EinheitRepository einheitRepository;
    private final OrganizationContextService organizationContextService;
    private final HibernateFilterService hibernateFilterService;
    private final CalculationProgressService calculationProgressService;
    private final EinstellungenService einstellungenService;
    private final SystemmeldungService systemmeldungService;

    public MesswerteService(MesswerteRepository messwerteRepository,
                            EinheitRepository einheitRepository,
                            OrganizationContextService organizationContextService,
                            HibernateFilterService hibernateFilterService,
                            CalculationProgressService calculationProgressService,
                            EinstellungenService einstellungenService,
                            SystemmeldungService systemmeldungService) {
        this.messwerteRepository = messwerteRepository;
        this.einheitRepository = einheitRepository;
        this.organizationContextService = organizationContextService;
        this.hibernateFilterService = hibernateFilterService;
        this.calculationProgressService = calculationProgressService;
        this.einstellungenService = einstellungenService;
        this.systemmeldungService = systemmeldungService;
        log.info("MesswerteService initialized");
    }

    @Transactional
    @CacheEvict(value = "statistik", allEntries = true)
    public Map<String, Object> processCsvUpload(MultipartFile file, Long einheitId, String dateStr) throws Exception {
        hibernateFilterService.enableOrgFilter();
        log.info("Starting CSV upload processing - einheitId: {}, date: {}, filename: {}, size: {} bytes",
                einheitId, dateStr, file.getOriginalFilename(), file.getSize());

        // Fetch the Einheit entity
        Einheit einheit = einheitRepository.findById(einheitId)
                .orElseThrow(() -> {
                    log.error("Einheit not found with id: {}", einheitId);
                    return new RuntimeException("Einheit not found with id: " + einheitId);
                });

        log.debug("Found einheit: {} (type: {})", einheit.getName(), einheit.getTyp());

        LocalDate date = LocalDate.parse(dateStr);
        LocalDateTime zeit = LocalDateTime.of(date, LocalTime.of(0, 0));

        List<Messwerte> messwerteList = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            reader.readLine(); // Skip header line
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String[] parts = line.split("[,;]");
                if (parts.length >= 3) {
                    Double total = Double.parseDouble(parts[1].trim());
                    Double zev = Double.parseDouble(parts[2].trim());

                    Messwerte messwert = new Messwerte(zeit, total, zev, einheit);
                    messwert.setOrgId(organizationContextService.getCurrentOrgId());
                    messwerteList.add(messwert);
                    zeit = zeit.plusMinutes(15);
                } else {
                    log.warn("Skipping invalid line {} in CSV: insufficient columns", lineNumber);
                }
            }
            log.info("Parsed {} records from CSV file", messwerteList.size());
        } catch (Exception e) {
            log.error("Error reading CSV file: {}", e.getMessage(), e);
            throw e;
        }

        // Delete existing messwerte for the same einheit and entire month to allow
        // overwrite
        LocalDateTime dateTimeFrom = date.withDayOfMonth(1).atStartOfDay();
        LocalDateTime dateTimeTo = date.withDayOfMonth(date.lengthOfMonth()).atTime(23, 59, 59);
        List<Messwerte> existingMesswerte = messwerteRepository.findByEinheitAndZeitBetween(einheit, dateTimeFrom,
                dateTimeTo);
        if (!existingMesswerte.isEmpty()) {
            log.info("Deleting {} existing messwerte records for month {}", existingMesswerte.size(), date.getMonth());
            messwerteRepository.deleteAll(existingMesswerte);
        }

        messwerteRepository.saveAll(messwerteList);
        log.info("Successfully saved {} messwerte records for einheit: {}", messwerteList.size(), einheit.getName());

        return Map.of(
                "status", "success",
                "count", messwerteList.size(),
                "einheitId", einheitId,
                "einheitName", einheit.getName());
    }

    /**
     * Verarbeitet eine Bilanz-CSV (Netzbezug + Rücklieferung in einer Datei, 15-Min-Raster).
     * Die Werte werden den Einheiten vom Typ BEZUG bzw. RUECKLIEFERUNG zugeordnet (total signiert,
     * zev = 0, quelle = CSV). Zeitstempel-Bildung ist <b>identisch zum Consumer-Upload</b>
     * ({@link #processCsvUpload}): der {@code dateStr}-Parameter liefert den Startzeitpunkt (00:00),
     * je gespeicherter Zeile wird +15 min fortgeschrieben. Die "category"-Spalte der Datei wird
     * dabei – wie parts[0] beim Consumer-Upload – nicht ausgewertet. Überschreibt die Monatsdaten
     * beider Einheiten (Monat des dateStr-Parameters).
     */
    @Transactional
    @CacheEvict(value = "statistik", allEntries = true)
    public Map<String, Object> processBilanzCsvUpload(MultipartFile file, String dateStr) throws Exception {
        hibernateFilterService.enableOrgFilter();
        log.info("Starting Bilanz CSV upload - date: {}, filename: {}, size: {} bytes",
                dateStr, file.getOriginalFilename(), file.getSize());

        // Bilanz-Einheiten im aktuellen Mandanten auflösen
        Einheit bezugEinheit = einheitRepository.findFirstByTyp(EinheitTyp.BEZUG)
                .orElseThrow(() -> new IllegalArgumentException("BILANZ_EINHEIT_FEHLT"));
        Einheit ruecklieferungEinheit = einheitRepository.findFirstByTyp(EinheitTyp.RUECKLIEFERUNG)
                .orElseThrow(() -> new IllegalArgumentException("BILANZ_EINHEIT_FEHLT"));

        LocalDate date = LocalDate.parse(dateStr);
        LocalDateTime zeit = LocalDateTime.of(date, LocalTime.of(0, 0));
        Long orgId = organizationContextService.getCurrentOrgId();
        List<Messwerte> messwerteList = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            // Kopfzeile lesen und Spalten positions- + titelbasiert plausibilisieren.
            // Trennzeichen wie beim Consumer-Upload sowohl Komma als auch Semikolon.
            String header = reader.readLine();
            String[] headerCols = header != null ? header.split("[,;]", -1) : new String[0];
            if (headerCols.length < 3
                    || !headerCols[1].toLowerCase().contains("bezug")
                    || !headerCols[2].toLowerCase().contains("cklieferung")) {
                throw new IllegalArgumentException("BILANZ_CSV_UNGUELTIG");
            }

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("[,;]", -1);
                if (parts.length < 3) {
                    log.warn("Bilanz-CSV Zeile {} übersprungen: zu wenige Spalten", lineNumber);
                    continue;
                }

                String bezugStr = parts[1].trim();
                String ruecklieferungStr = parts[2].trim();
                boolean hatBezug = !bezugStr.isEmpty();
                boolean hatRuecklieferung = !ruecklieferungStr.isEmpty();
                if (hatBezug == hatRuecklieferung) {
                    // beide gefüllt oder beide leer → überspringen
                    log.warn("Bilanz-CSV Zeile {} übersprungen: nicht genau eine Spalte gefüllt", lineNumber);
                    continue;
                }

                try {
                    double total = Double.parseDouble(hatBezug ? bezugStr : ruecklieferungStr);
                    Einheit einheit = hatBezug ? bezugEinheit : ruecklieferungEinheit;
                    Messwerte messwert = new Messwerte(zeit, total, 0.0, einheit);
                    messwert.setOrgId(orgId);
                    messwert.setQuelle(Quelle.CSV);
                    messwerteList.add(messwert);
                    zeit = zeit.plusMinutes(15);
                } catch (NumberFormatException e) {
                    log.warn("Bilanz-CSV Zeile {} übersprungen: nicht-numerischer Wert", lineNumber);
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error reading Bilanz CSV file: {}", e.getMessage(), e);
            throw e;
        }

        if (messwerteList.isEmpty()) {
            throw new IllegalArgumentException("BILANZ_CSV_UNGUELTIG");
        }

        // Monats-Overwrite beider Einheiten (Monat des date-Parameters), analog processCsvUpload
        LocalDateTime monatVon = date.withDayOfMonth(1).atStartOfDay();
        LocalDateTime monatBis = date.withDayOfMonth(date.lengthOfMonth()).atTime(23, 59, 59);
        for (Einheit einheit : List.of(bezugEinheit, ruecklieferungEinheit)) {
            List<Messwerte> bestehende = messwerteRepository.findByEinheitAndZeitBetween(einheit, monatVon, monatBis);
            if (!bestehende.isEmpty()) {
                log.info("Deleting {} existing Bilanz records for einheit {}", bestehende.size(), einheit.getName());
                messwerteRepository.deleteAll(bestehende);
            }
        }

        messwerteRepository.saveAll(messwerteList);
        log.info("Successfully saved {} Bilanz messwerte records", messwerteList.size());

        return Map.of(
                "status", "success",
                "count", messwerteList.size(),
                "bezugEinheit", bezugEinheit.getName(),
                "ruecklieferungEinheit", ruecklieferungEinheit.getName());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMesswerteByEinheit(Long einheitId, LocalDate dateFrom, LocalDate dateTo) {
        hibernateFilterService.enableOrgFilter();
        log.info("Fetching messwerte for einheitId: {}, dateFrom: {}, dateTo: {}", einheitId, dateFrom, dateTo);

        LocalDateTime dateTimeFrom = dateFrom.atStartOfDay();
        LocalDateTime dateTimeTo = dateTo.atTime(23, 59, 59);

        Einheit einheit = einheitRepository.findById(einheitId)
                .orElseThrow(() -> {
                    log.error("Einheit not found with id: {}", einheitId);
                    return new RuntimeException("Einheit not found");
                });

        List<Messwerte> messwerte = messwerteRepository.findByEinheitAndZeitBetween(einheit, dateTimeFrom, dateTimeTo);
        log.info("Found {} messwerte records for einheit: {}", messwerte.size(), einheit.getName());

        return messwerte.stream()
                .map(m -> {
                    Map<String, Object> data = new java.util.HashMap<>();
                    data.put("zeit", m.getZeit().toString());
                    data.put("total", m.getTotal() != null ? m.getTotal() : 0.0);
                    data.put("zev", m.getZev() != null ? m.getZev() : 0.0);
                    return data;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = "statistik", allEntries = true)
    public CalculationResult calculateSolarDistribution(LocalDateTime dateFrom, LocalDateTime dateTo,
            String algorithm) {
        hibernateFilterService.enableOrgFilter();
        // Fortschritt über den Request-Org-Kontext (UI-Polling)
        return distribute(dateFrom, dateTo, algorithm, organizationContextService.getCurrentOrgId(), true);
    }

    /**
     * Führt die Solarverteilung für eine explizit angegebene {@code org_id} und einen Zeitraum aus.
     * Für Hintergrund-Aufrufe ohne JWT/Request-Kontext (z.B. unmittelbar nach der MQTT-Aggregation).
     * Setzt den orgFilter explizit für den Mandanten. {@code showProgress = false} deaktiviert das
     * Fortschritts-Tracking (im Hintergrund-Job gibt es kein UI-Polling).
     */
    @Transactional
    @CacheEvict(value = "statistik", allEntries = true)
    public CalculationResult calculateSolarDistributionForOrg(Long orgId, LocalDateTime dateFrom,
            LocalDateTime dateTo, String algorithm, boolean showProgress) {
        hibernateFilterService.enableOrgFilter(orgId);
        return distribute(dateFrom, dateTo, algorithm, orgId, showProgress);
    }

    /**
     * Kern der Solarverteilung über alle Zeitpunkte im Bereich. Der orgFilter muss bereits aktiviert
     * sein. Bei {@code showProgress = true} wird der Fortschritt für {@code progressOrgId} gemeldet
     * (UI-Polling); bei {@code false} läuft die Berechnung ohne Fortschrittsmeldung (Hintergrund-Job).
     */
    private CalculationResult distribute(LocalDateTime dateFrom, LocalDateTime dateTo,
            String algorithm, Long progressOrgId, boolean showProgress) {
        Verteilmodus modus = einstellungenService.getVerteilmodus(progressOrgId);
        log.info("Solar distribution - dateFrom: {}, dateTo: {}, algorithm: {}, verteilmodus: {} (org={})",
                dateFrom, dateTo, algorithm, modus, progressOrgId);

        long startTime = System.currentTimeMillis();

        // Get all distinct timestamps in the date range
        List<LocalDateTime> distinctZeiten = messwerteRepository.findDistinctZeitBetween(dateFrom, dateTo);
        log.info("Found {} distinct timestamps to process", distinctZeiten.size());

        if (showProgress) {
            calculationProgressService.startCalculation(progressOrgId, distinctZeiten.size());
        }

        if (modus == Verteilmodus.BILANZ) {
            return distributeBilanz(distinctZeiten, dateFrom, dateTo, algorithm, progressOrgId, showProgress, startTime);
        }
        return distributeProducerMessung(distinctZeiten, dateFrom, dateTo, algorithm, progressOrgId, showProgress, startTime);
    }

    /**
     * Verteilmodus {@code PRODUCER_MESSUNG} (heutiges Verhalten, unverändert): verteilt die
     * Producer-Produktion je Zeitpunkt auf die Consumer.
     */
    private CalculationResult distributeProducerMessung(List<LocalDateTime> distinctZeiten,
            LocalDateTime dateFrom, LocalDateTime dateTo, String algorithm, Long progressOrgId,
            boolean showProgress, long startTime) {
        int processedTimestamps = 0;
        int processedRecords = 0;
        BigDecimal totalSolarProduced = BigDecimal.ZERO;
        BigDecimal totalDistributed = BigDecimal.ZERO;

        // Process each timestamp. Nur PRODUCER/CONSUMER nehmen an der Verteilung teil –
        // Bilanz-Typen (BEZUG/RUECKLIEFERUNG) werden hier nie geladen (zev/zev_calculated
        // bleiben unangetastet).
        for (LocalDateTime zeit : distinctZeiten) {
            // Get all producers for this timestamp
            List<Messwerte> producers = messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.PRODUCER);

            // Skip if no producers
            if (producers.isEmpty()) {
                log.debug("No producers found for timestamp: {}", zeit);
                continue;
            }

            // Calculate total solar production at this timestamp
            // Producer values are negative (production) or positive (control unit consumption)
            // Sum gives net production (negative), we need absolute value for distribution
            BigDecimal netProduction = producers.stream()
                    .map(m -> BigDecimal.valueOf(m.getTotal()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Net production should be negative (production > consumption)
            // If positive or zero, there's nothing to distribute
            BigDecimal solarProduction = netProduction.compareTo(BigDecimal.ZERO) < 0
                    ? netProduction.abs()
                    : BigDecimal.ZERO;

            totalSolarProduced = totalSolarProduced.add(solarProduction);
            log.debug("Timestamp: {}, Solar production: {} kWh from {} producers",
                    zeit, solarProduction, producers.size());

            // Get all consumers for this timestamp
            List<Messwerte> consumers = messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.CONSUMER);

            // Skip if no consumers
            if (consumers.isEmpty()) {
                log.debug("No consumers found for timestamp: {}", zeit);
                // Ohne Consumer wird nichts im ZEV konsumiert: MQTT-Produzenten auf zev = 0
                // (alles Rücklieferung).
                aktualisiereProducerZev(producers, BigDecimal.ZERO);
                continue;
            }

            // Get consumption values
            List<BigDecimal> consumptions = consumers.stream()
                    .map(m -> BigDecimal.valueOf(m.getTotal()))
                    .collect(Collectors.toList());

            // Calculate distribution using selected algorithm
            List<BigDecimal> distributions;
            if ("PROPORTIONAL".equalsIgnoreCase(algorithm)) {
                log.debug("Using PROPORTIONAL distribution algorithm");
                distributions = ProportionalConsumptionDistribution.distributeSolarPower(solarProduction, consumptions);
            } else {
                log.debug("Using EQUAL_SHARE distribution algorithm");
                // Default to EQUAL_SHARE
                distributions = SolarDistribution.distributeSolarPower(solarProduction, consumptions);
            }

            // Update zev_calculated for each consumer
            for (int i = 0; i < consumers.size(); i++) {
                Messwerte consumer = consumers.get(i);
                BigDecimal distributedAmount = distributions.get(i);
                consumer.setZevCalculated(distributedAmount.doubleValue());
                // FR-9 (MQTT-Integration): MQTT-Messwerte tragen den Sentinel zev == 0 und
                // erhalten hier den berechneten Anteil; gemessene Werte (zev != 0, z.B. CSV) bleiben.
                if (consumer.getZev() != null && consumer.getZev() == 0.0) {
                    consumer.setZev(distributedAmount.doubleValue());
                }
                messwerteRepository.save(consumer);
                totalDistributed = totalDistributed.add(distributedAmount);
                processedRecords++;
            }

            // Producer-zev = im ZEV konsumierte Produktion (= tatsächlich verteilte Menge);
            // die Algorithmen kappen die Zuteilung am Verbrauch, der Rest ist Rücklieferung.
            BigDecimal verteiltFuerZeit = distributions.stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            aktualisiereProducerZev(producers, verteiltFuerZeit);

            processedTimestamps++;
            if (showProgress) {
                calculationProgressService.updateProgress(progressOrgId, processedTimestamps);
            }

            if (processedTimestamps % 100 == 0) {
                log.debug("Progress: {} timestamps processed", processedTimestamps);
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info(
                "Solar distribution calculation completed - timestamps: {}, records: {}, totalProduced: {} kWh, totalDistributed: {} kWh, duration: {} ms",
                processedTimestamps, processedRecords, totalSolarProduced, totalDistributed, duration);

        return new CalculationResult(
                processedTimestamps,
                processedRecords,
                dateFrom,
                dateTo,
                totalSolarProduced.doubleValue(),
                totalDistributed.doubleValue());
    }

    /**
     * Verteilmodus {@code BILANZ} (Spec FR-2): der ZEV-Eigenverbrauch wird aus der Netz-Bilanz
     * abgeleitet – {@code S = max(0, ConsumerTotal − Bezug)} – und mit demselben Algorithmus wie
     * heute auf die Consumer verteilt. Iteriert <b>producer-unabhängig</b> (kein Skip bei fehlenden
     * Producern). Producer-{@code zev} ist nur statistisch: {@code |Produktion| − |Rücklieferung|},
     * proportional (MQTT-Guard). Fehlt die {@code BEZUG}-Einheit oder deren Messwert in einem
     * Intervall mit Consumern, bricht der Lauf mit {@code IllegalStateException} ab (Rollback über
     * {@code @Transactional}); die fehlende {@code RUECKLIEFERUNG}-Einheit ist kein Abbruchgrund.
     */
    private CalculationResult distributeBilanz(List<LocalDateTime> distinctZeiten,
            LocalDateTime dateFrom, LocalDateTime dateTo, String algorithm, Long progressOrgId,
            boolean showProgress, long startTime) {
        // BEZUG-Einheit ist abrechnungskritisch: fehlt sie komplett, sofort abbrechen.
        if (!einheitRepository.existsByTyp(EinheitTyp.BEZUG)) {
            systemmeldungService.erfasse(progressOrgId, MeldungLevel.ERROR,
                    SystemmeldungService.KATEGORIE_BILANZMODELL, SystemmeldungService.KEY_KEINE_BILANZDATEN, null);
            throw new IllegalStateException(
                    SystemmeldungService.KEY_KEINE_BILANZDATEN + ": keine BEZUG-Einheit vorhanden");
        }
        // Fehlt die RUECKLIEFERUNG-Einheit ganz, ist der im ZEV verbrauchte Anteil der Produktion
        // nicht bestimmbar → Producer-zev = 0 (Statistik unvollständig, FR-2.4). Der Lauf läuft
        // weiter, da dieser Wert nicht abrechnungsrelevant ist.
        boolean hatRuecklieferung = einheitRepository.existsByTyp(EinheitTyp.RUECKLIEFERUNG);

        int processedTimestamps = 0;
        int processedRecords = 0;
        BigDecimal totalSolarProduced = BigDecimal.ZERO; // hier: Summe der verteilten Menge S
        BigDecimal totalDistributed = BigDecimal.ZERO;

        for (LocalDateTime zeit : distinctZeiten) {
            List<Messwerte> producers = messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.PRODUCER);

            // Producer-zev (nur Statistik): im ZEV verbrauchte Produktion lt. Bilanz =
            // |Produktion| − |Rücklieferung|, proportional auf die Producer (MQTT-Guard).
            if (!producers.isEmpty()) {
                if (!hatRuecklieferung) {
                    // Keine RUECKLIEFERUNG-Einheit → Anteil unbekannt → 0 (siehe FR-2.4).
                    aktualisiereProducerZev(producers, BigDecimal.ZERO);
                } else {
                    BigDecimal produktion = producers.stream()
                            .map(m -> BigDecimal.valueOf(m.getTotal()))
                            .filter(t -> t.signum() < 0)
                            .map(BigDecimal::abs)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal ruecklieferung = messwerteRepository
                            .findByZeitAndEinheitTyp(zeit, EinheitTyp.RUECKLIEFERUNG).stream()
                            .map(m -> BigDecimal.valueOf(m.getTotal()).abs())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal imZev = produktion.subtract(ruecklieferung).max(BigDecimal.ZERO);
                    aktualisiereProducerZev(producers, imZev);
                }
            }

            List<Messwerte> consumers = messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.CONSUMER);
            if (consumers.isEmpty()) {
                // Keine Consumer → nichts im ZEV zu verteilen (S irrelevant).
                continue;
            }

            // Bezug ist für dieses Intervall abrechnungskritisch.
            List<Messwerte> bezugMesswerte = messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.BEZUG);
            if (bezugMesswerte.isEmpty()) {
                String intervall = zeit.toLocalDate() + " " + zeit.toLocalTime();
                systemmeldungService.erfasse(progressOrgId, MeldungLevel.ERROR,
                        SystemmeldungService.KATEGORIE_BILANZMODELL, SystemmeldungService.KEY_KEINE_BILANZDATEN,
                        intervall);
                throw new IllegalStateException(SystemmeldungService.KEY_KEINE_BILANZDATEN + ": " + intervall);
            }

            BigDecimal bezug = bezugMesswerte.stream()
                    .map(m -> BigDecimal.valueOf(m.getTotal()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal consumerTotal = consumers.stream()
                    .map(m -> BigDecimal.valueOf(m.getTotal()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Verteilbarer ZEV-Eigenverbrauch: S = max(0, ConsumerTotal − Bezug)
            BigDecimal s = consumerTotal.subtract(bezug).max(BigDecimal.ZERO);
            totalSolarProduced = totalSolarProduced.add(s);

            List<BigDecimal> consumptions = consumers.stream()
                    .map(m -> BigDecimal.valueOf(m.getTotal()))
                    .collect(Collectors.toList());

            List<BigDecimal> distributions;
            if ("PROPORTIONAL".equalsIgnoreCase(algorithm)) {
                distributions = ProportionalConsumptionDistribution.distributeSolarPower(s, consumptions);
            } else {
                distributions = SolarDistribution.distributeSolarPower(s, consumptions);
            }

            for (int i = 0; i < consumers.size(); i++) {
                Messwerte consumer = consumers.get(i);
                BigDecimal distributedAmount = distributions.get(i);
                consumer.setZevCalculated(distributedAmount.doubleValue());
                // MQTT-Sentinel-Regel bleibt: zev == 0 → berechneter Wert; gemessene CSV-Werte bleiben.
                if (consumer.getZev() != null && consumer.getZev() == 0.0) {
                    consumer.setZev(distributedAmount.doubleValue());
                }
                messwerteRepository.save(consumer);
                totalDistributed = totalDistributed.add(distributedAmount);
                processedRecords++;
            }

            processedTimestamps++;
            if (showProgress) {
                calculationProgressService.updateProgress(progressOrgId, processedTimestamps);
            }
            if (processedTimestamps % 100 == 0) {
                log.debug("Progress (Bilanz): {} timestamps processed", processedTimestamps);
            }
        }

        // Erfolgreicher Bilanzlauf → offene Bilanzdaten-Meldungen des Mandanten auto-resolven (Selbstheilung).
        systemmeldungService.autoResolve(progressOrgId, SystemmeldungService.KEY_KEINE_BILANZDATEN);

        long duration = System.currentTimeMillis() - startTime;
        log.info(
                "Solar distribution (Bilanz) completed - timestamps: {}, records: {}, totalS: {} kWh, totalDistributed: {} kWh, duration: {} ms",
                processedTimestamps, processedRecords, totalSolarProduced, totalDistributed, duration);

        return new CalculationResult(
                processedTimestamps,
                processedRecords,
                dateFrom,
                dateTo,
                totalSolarProduced.doubleValue(),
                totalDistributed.doubleValue());
    }

    /**
     * Setzt bei MQTT-Produzenten {@code zev} auf den im ZEV konsumierten Anteil der Produktion
     * (= tatsächlich verteilte Menge), bei mehreren Produzenten proportional zu ihrer Produktion.
     * Messwerte ohne Produktion ({@code total >= 0}, z.B. Steuergerät) erhalten 0. Gespeichert
     * wird mit negativem Vorzeichen, konsistent zu {@code total}. CSV-Messwerte tragen den vom
     * Messdienstleister gemessenen ZEV-Anteil und bleiben unangetastet.
     */
    private void aktualisiereProducerZev(List<Messwerte> producers, BigDecimal verteilt) {
        BigDecimal produktion = producers.stream()
                .map(m -> BigDecimal.valueOf(m.getTotal()))
                .filter(t -> t.signum() < 0)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        for (Messwerte producer : producers) {
            if (producer.getQuelle() != Quelle.MQTT) {
                continue;
            }
            BigDecimal total = BigDecimal.valueOf(producer.getTotal());
            BigDecimal anteil = (produktion.signum() > 0 && total.signum() < 0)
                    ? verteilt.multiply(total.abs()).divide(produktion, 10, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            producer.setZev(anteil.negate().doubleValue());
            messwerteRepository.save(producer);
        }
    }

    public static class CalculationResult {
        private final int processedTimestamps;
        private final int processedRecords;
        private final LocalDateTime dateFrom;
        private final LocalDateTime dateTo;
        private final double totalSolarProduced;
        private final double totalDistributed;

        public CalculationResult(int processedTimestamps, int processedRecords, LocalDateTime dateFrom,
                LocalDateTime dateTo, double totalSolarProduced, double totalDistributed) {
            this.processedTimestamps = processedTimestamps;
            this.processedRecords = processedRecords;
            this.dateFrom = dateFrom;
            this.dateTo = dateTo;
            this.totalSolarProduced = totalSolarProduced;
            this.totalDistributed = totalDistributed;
        }

        public int getProcessedTimestamps() {
            return processedTimestamps;
        }

        public int getProcessedRecords() {
            return processedRecords;
        }

        public LocalDateTime getDateFrom() {
            return dateFrom;
        }

        public LocalDateTime getDateTo() {
            return dateTo;
        }

        public double getTotalSolarProduced() {
            return totalSolarProduced;
        }

        public double getTotalDistributed() {
            return totalDistributed;
        }
    }
}
