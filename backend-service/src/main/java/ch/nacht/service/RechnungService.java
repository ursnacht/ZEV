package ch.nacht.service;

import ch.nacht.dto.EinstellungenDTO;
import ch.nacht.dto.RechnungDTO;
import ch.nacht.dto.RechnungKonfigurationDTO;
import ch.nacht.dto.TarifZeileDTO;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.Mieter;
import ch.nacht.entity.Tarif;
import ch.nacht.entity.TarifTyp;
import ch.nacht.entity.Tarifposition;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.MesswerteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service for calculating and generating invoice data.
 *
 * <p><b>Geld ist durchgehend {@link BigDecimal}</b>, nie {@code double}: dieselbe Zusicherung wie
 * in {@code Debitor} und in der Nebenkostenabrechnung ({@code NkBerechnungService}). Der Endbetrag
 * wandert unveraendert in die Debitorenkontrolle - eine Umrechnung an der Grenze gibt es nicht
 * mehr.
 *
 * <p>Gerundet wird <b>nur der Endbetrag</b>, auf 5 Rappen (Einzahlungsschein). Die Zeilenbetraege
 * bleiben das exakte Produkt {@code menge x preis}; die Differenz zum Endbetrag steht als
 * {@code rundung} auf der Rechnung.
 */
@Service
public class RechnungService {

    private static final Logger log = LoggerFactory.getLogger(RechnungService.class);

    /** Kleinste Muenze der Rechnung - der Endbetrag ist immer ein Vielfaches davon. */
    private static final BigDecimal FUENF_RAPPEN = new BigDecimal("0.05");

    private final EinheitRepository einheitRepository;
    private final MesswerteRepository messwerteRepository;
    private final EinstellungenService einstellungenService;
    private final TarifService tarifService;
    private final MieterService mieterService;
    private final TarifpositionService tarifpositionService;
    private final HibernateFilterService hibernateFilterService;

    public RechnungService(EinheitRepository einheitRepository,
                           MesswerteRepository messwerteRepository,
                           EinstellungenService einstellungenService,
                           TarifService tarifService,
                           MieterService mieterService,
                           TarifpositionService tarifpositionService,
                           HibernateFilterService hibernateFilterService) {
        this.einheitRepository = einheitRepository;
        this.messwerteRepository = messwerteRepository;
        this.einstellungenService = einstellungenService;
        this.tarifService = tarifService;
        this.mieterService = mieterService;
        this.tarifpositionService = tarifpositionService;
        this.hibernateFilterService = hibernateFilterService;
    }

    /**
     * Calculate invoices for the given unit IDs and time period.
     * Creates separate invoices for each tenant within the period.
     * Producers receive invoices with GRUNDGEBUEHR lines only.
     *
     * @param einheitIds List of unit IDs to generate invoices for
     * @param von Start date (inclusive)
     * @param bis End date (inclusive)
     * @return List of calculated invoice DTOs
     * @throws IllegalStateException if tariffs don't cover the entire period (consumers only)
     */
    @Transactional(readOnly = true)
    public List<RechnungDTO> berechneRechnungen(List<Long> einheitIds, LocalDate von, LocalDate bis) {
        hibernateFilterService.enableOrgFilter();
        log.info("Calculating invoices for {} units from {} to {}", einheitIds.size(), von, bis);

        // Validate ZEV/VNB tariff coverage only if consumer units are selected
        boolean hasConsumers = einheitIds.stream()
                .anyMatch(id -> einheitRepository.findFirstById(id)
                        .map(e -> e.getTyp() == EinheitTyp.CONSUMER)
                        .orElse(false));

        if (hasConsumers) {
            tarifService.validateTarifAbdeckung(von, bis);
        }

        List<RechnungDTO> rechnungen = new ArrayList<>();
        // Mieter, die bereits eine Ladestations-Rechnung erhalten haben. Ein Nutzer ohne Wohnung
        // kann mehrere Ladestationen haben; ohne diese Merkliste entstuende je gewaehlter
        // Ladestation eine Rechnung - und weil `berechneTarifpositionsZeilen` die Positionen
        // ALLER seiner Einheiten sammelt, traege jede davon saemtliche Zeilen. Das waere
        // doppelte Verrechnung (Specs/Ladestationen.md: "genau eine Rechnung").
        Set<Long> ladestationsRechnungErstellt = new HashSet<>();

        for (Long einheitId : einheitIds) {
            einheitRepository.findFirstById(einheitId).ifPresent(einheit -> {
                if (einheit.getTyp() == EinheitTyp.CONSUMER) {
                    // Get all tenants for this unit within the period
                    List<Mieter> mieter = mieterService.getMieterForQuartal(einheitId, von, bis);

                    if (mieter.isEmpty()) {
                        // No tenant: create invoice without tenant data
                        RechnungDTO rechnung = berechneRechnung(einheit, null, von, bis);
                        rechnungen.add(rechnung);
                        log.debug("Calculated invoice for unit {} (no tenant): {} CHF",
                                einheit.getName(), rechnung.getEndBetrag());
                    } else {
                        // Create separate invoice for each tenant
                        for (Mieter m : mieter) {
                            // Effective period = intersection of invoice period and lease period
                            LocalDate effektivVon = m.getMietbeginn().isBefore(von) ? von : m.getMietbeginn();
                            LocalDate effektivBis = (m.getMietende() == null || m.getMietende().isAfter(bis))
                                    ? bis : m.getMietende();

                            RechnungDTO rechnung = berechneRechnung(einheit, m, effektivVon, effektivBis);
                            rechnungen.add(rechnung);
                            log.debug("Calculated invoice for unit {}, tenant {} ({} to {}): {} CHF",
                                    einheit.getName(), m.getName(), effektivVon, effektivBis, rechnung.getEndBetrag());
                        }
                    }
                } else if (einheit.getTyp() == EinheitTyp.PRODUCER) {
                    // Producers receive GRUNDGEBUEHR lines only
                    RechnungDTO rechnung = berechneProduzentenRechnung(einheit, von, bis);
                    if (!rechnung.getTarifZeilen().isEmpty()) {
                        rechnungen.add(rechnung);
                        log.debug("Calculated producer invoice for unit {}: {} CHF",
                                einheit.getName(), rechnung.getEndBetrag());
                    } else {
                        log.debug("Skipping producer unit {} - no GRUNDGEBUEHR tariffs found", einheit.getName());
                    }
                } else if (einheit.getTyp() == EinheitTyp.LADESTATION) {
                    // Eine Ladestation erzeugt nur dann eine eigene Rechnung, wenn ihr Mieter
                    // keine Wohnung hat (Specs/Ladestationen.md FR-1.5). Hat er eine, erscheinen
                    // seine Ladestrom-Positionen auf deren Rechnung - sonst bekaeme er zwei
                    // Rechnungen mit derselben Zeile.
                    for (Mieter m : mieterService.getMieterForQuartal(einheitId, von, bis)) {
                        if (hatWohnung(m)) {
                            log.debug("Skipping charging unit {} for tenant {} - positions appear "
                                            + "on the invoice of their consumer unit",
                                    einheit.getName(), m.getName());
                            continue;
                        }
                        // Je Mieter hoechstens eine Ladestations-Rechnung, unabhaengig davon,
                        // wie viele seiner Ladestationen fuer den Lauf gewaehlt sind. Welche
                        // Einheit die Rechnung traegt, entscheidet die Auswahlreihenfolge; die
                        // Zeilen sind in jedem Fall dieselben.
                        if (!ladestationsRechnungErstellt.add(m.getId())) {
                            log.debug("Skipping charging unit {} for tenant {} - already billed "
                                            + "on another charging unit of the same tenant",
                                    einheit.getName(), m.getName());
                            continue;
                        }
                        LocalDate effektivVon = m.getMietbeginn().isBefore(von) ? von : m.getMietbeginn();
                        LocalDate effektivBis = (m.getMietende() == null || m.getMietende().isAfter(bis))
                                ? bis : m.getMietende();
                        RechnungDTO rechnung = berechneLadestationRechnung(einheit, m, effektivVon, effektivBis);
                        if (!rechnung.getTarifZeilen().isEmpty()) {
                            rechnungen.add(rechnung);
                            log.debug("Calculated charging invoice for unit {}, tenant {}: {} CHF",
                                    einheit.getName(), m.getName(), rechnung.getEndBetrag());
                        } else {
                            log.debug("Skipping charging unit {} for tenant {} - no positions",
                                    einheit.getName(), m.getName());
                        }
                    }
                } else {
                    // Bilanz-Typen (BEZUG/RUECKLIEFERUNG) messen den Netzanschluss und
                    // werden bewusst nicht verrechnet.
                    log.debug("Skipping unit {} - Bilanz-Typ {} wird nicht verrechnet",
                            einheit.getName(), einheit.getTyp());
                }
            });
        }

        log.info("Generated {} invoices", rechnungen.size());
        return rechnungen;
    }

    /**
     * Calculate a single invoice for a consumer unit, optional tenant, and time period.
     *
     * @param einheit The unit to generate invoice for
     * @param mieter The tenant (can be null for vacant units)
     * @param von Start date (inclusive)
     * @param bis End date (inclusive)
     * @return Calculated invoice DTO
     */
    public RechnungDTO berechneRechnung(Einheit einheit, Mieter mieter, LocalDate von, LocalDate bis) {
        RechnungDTO rechnung = new RechnungDTO();

        // Unit information
        rechnung.setEinheitId(einheit.getId());
        rechnung.setEinheitName(einheit.getName());
        rechnung.setMesspunkt(einheit.getMesspunkt());

        // Tenant information (if available)
        if (mieter != null) {
            rechnung.setMieterId(mieter.getId());
            rechnung.setMieterName(mieter.getName());
            rechnung.setMieterStrasse(mieter.getStrasse());
            String plzOrt = ((mieter.getPlz() != null ? mieter.getPlz() : "") + " " +
                    (mieter.getOrt() != null ? mieter.getOrt() : "")).trim();
            rechnung.setMieterPlzOrt(plzOrt.isEmpty() ? null : plzOrt);
        }

        // Time period (effective period for this tenant)
        rechnung.setVon(von);
        rechnung.setBis(bis);
        rechnung.setErstellungsdatum(LocalDate.now());

        // Get tariffs for the period
        List<Tarif> zevTarife = tarifService.getTarifeForZeitraum(TarifTyp.ZEV, von, bis);
        List<Tarif> vnbTarife = tarifService.getTarifeForZeitraum(TarifTyp.VNB, von, bis);

        BigDecimal totalBetrag = BigDecimal.ZERO;

        // Calculate ZEV tariff lines (based on zevCalculated measurements)
        totalBetrag = totalBetrag.add(berechneTarifZeilen(rechnung, einheit, von, bis, zevTarife, TarifTyp.ZEV));

        // Calculate VNB tariff lines (based on total - zevCalculated measurements)
        totalBetrag = totalBetrag.add(berechneTarifZeilen(rechnung, einheit, von, bis, vnbTarife, TarifTyp.VNB));

        // Manually captured positions (Ladestrom etc.) - after ZEV/VNB, before GRUNDGEBUEHR
        totalBetrag = totalBetrag.add(berechneTarifpositionsZeilen(rechnung, mieter, von, bis));

        // Calculate GRUNDGEBUEHR lines (optional - no error if no tariff found)
        List<Tarif> grundgebuehrTarife = tarifService.getTarifeForZeitraum(TarifTyp.GRUNDGEBUEHR, von, bis);
        if (!grundgebuehrTarife.isEmpty()) {
            totalBetrag = totalBetrag.add(berechneGrundgebuehrZeilen(rechnung, von, bis, grundgebuehrTarife));
        }

        // Calculate totals with rounding to 5 Rappen
        BigDecimal endBetrag = roundTo5Rappen(totalBetrag);
        BigDecimal rundung = endBetrag.subtract(totalBetrag);

        rechnung.setTotalBetrag(totalBetrag);
        rechnung.setRundung(rundung);
        rechnung.setEndBetrag(endBetrag);

        // Configuration values from database settings
        EinstellungenDTO einstellungen = einstellungenService.getEinstellungenOrThrow();
        RechnungKonfigurationDTO config = einstellungen.getRechnung();
        RechnungKonfigurationDTO.StellerDTO steller = config.getSteller();

        rechnung.setZahlungsfrist(config.getZahlungsfrist());
        rechnung.setIban(config.getIban());
        rechnung.setStellerName(steller.getName());
        rechnung.setStellerStrasse(steller.getStrasse());
        rechnung.setStellerPlzOrt(steller.getPlz() + " " + steller.getOrt());

        return rechnung;
    }

    /**
     * Calculate an invoice for a producer unit containing only GRUNDGEBUEHR lines.
     *
     * @param einheit The producer unit
     * @param von Start date (inclusive)
     * @param bis End date (inclusive)
     * @return Calculated invoice DTO (may have empty tarifZeilen if no GRUNDGEBUEHR tariffs exist)
     */
    private RechnungDTO berechneProduzentenRechnung(Einheit einheit, LocalDate von, LocalDate bis) {
        RechnungDTO rechnung = new RechnungDTO();
        rechnung.setEinheitId(einheit.getId());
        rechnung.setEinheitName(einheit.getName());
        rechnung.setMesspunkt(einheit.getMesspunkt());
        rechnung.setVon(von);
        rechnung.setBis(bis);
        rechnung.setErstellungsdatum(LocalDate.now());

        // Producers are only charged GRUNDGEBUEHR tariffs explicitly flagged for producers
        List<Tarif> tarife = tarifService.getTarifeForZeitraum(TarifTyp.GRUNDGEBUEHR, von, bis).stream()
                .filter(Tarif::isProduzentVerrechnen)
                .toList();
        BigDecimal total = berechneGrundgebuehrZeilen(rechnung, von, bis, tarife);
        BigDecimal endBetrag = roundTo5Rappen(total);

        rechnung.setTotalBetrag(total);
        rechnung.setRundung(endBetrag.subtract(total));
        rechnung.setEndBetrag(endBetrag);

        EinstellungenDTO einstellungen = einstellungenService.getEinstellungenOrThrow();
        RechnungKonfigurationDTO config = einstellungen.getRechnung();
        RechnungKonfigurationDTO.StellerDTO steller = config.getSteller();

        rechnung.setZahlungsfrist(config.getZahlungsfrist());
        rechnung.setIban(config.getIban());
        rechnung.setStellerName(steller.getName());
        rechnung.setStellerStrasse(steller.getStrasse());
        rechnung.setStellerPlzOrt(steller.getPlz() + " " + steller.getOrt());

        return rechnung;
    }

    /**
     * Hat der Mieter eine Wohnung? Nur dann erscheinen seine Ladestrom-Positionen dort und die
     * Ladestation braucht keine eigene Rechnung.
     *
     * @param mieter Tenant
     * @return true if a CONSUMER unit is assigned to the tenant
     */
    private boolean hatWohnung(Mieter mieter) {
        return mieterService.getEinheitIds(mieter.getId()).stream()
                .map(einheitRepository::findFirstById)
                .anyMatch(e -> e.map(x -> x.getTyp() == EinheitTyp.CONSUMER).orElse(false));
    }

    /**
     * Calculate an invoice for a charging unit whose tenant has no consumer unit.
     *
     * <p>Enthaelt <b>ausschliesslich</b> die Tarifpositionen dieser Einheit: Eine Ladestation hat
     * keine Messwerte (also keine ZEV-/VNB-Zeilen) und traegt keine Grundgebuehr - die gilt je
     * Wohnungszaehler.
     *
     * @param einheit The charging unit
     * @param mieter The tenant
     * @param von Start date (inclusive)
     * @param bis End date (inclusive)
     * @return Calculated invoice DTO (may have empty tarifZeilen if no positions exist)
     */
    private RechnungDTO berechneLadestationRechnung(Einheit einheit, Mieter mieter,
                                                    LocalDate von, LocalDate bis) {
        RechnungDTO rechnung = new RechnungDTO();
        rechnung.setEinheitId(einheit.getId());
        rechnung.setEinheitName(einheit.getName());
        rechnung.setMesspunkt(einheit.getMesspunkt());
        rechnung.setMieterId(mieter.getId());
        rechnung.setMieterName(mieter.getName());
        rechnung.setMieterStrasse(mieter.getStrasse());
        String plzOrt = ((mieter.getPlz() != null ? mieter.getPlz() : "") + " "
                + (mieter.getOrt() != null ? mieter.getOrt() : "")).trim();
        rechnung.setMieterPlzOrt(plzOrt.isEmpty() ? null : plzOrt);
        rechnung.setVon(von);
        rechnung.setBis(bis);
        rechnung.setErstellungsdatum(LocalDate.now());

        BigDecimal total = berechneTarifpositionsZeilen(rechnung, mieter, von, bis);
        BigDecimal endBetrag = roundTo5Rappen(total);

        rechnung.setTotalBetrag(total);
        rechnung.setRundung(endBetrag.subtract(total));
        rechnung.setEndBetrag(endBetrag);

        EinstellungenDTO einstellungen = einstellungenService.getEinstellungenOrThrow();
        RechnungKonfigurationDTO config = einstellungen.getRechnung();
        RechnungKonfigurationDTO.StellerDTO steller = config.getSteller();

        rechnung.setZahlungsfrist(config.getZahlungsfrist());
        rechnung.setIban(config.getIban());
        rechnung.setStellerName(steller.getName());
        rechnung.setStellerStrasse(steller.getStrasse());
        rechnung.setStellerPlzOrt(steller.getPlz() + " " + steller.getOrt());

        return rechnung;
    }

    /**
     * Calculate GRUNDGEBUEHR tariff lines based on full calendar months.
     * A month is counted only if both its first and last day lie within the effective period.
     *
     * @param rechnung The invoice DTO to add lines to
     * @param von Invoice start date
     * @param bis Invoice end date
     * @param tarife List of applicable GRUNDGEBUEHR tariffs
     * @return Total amount for all GRUNDGEBUEHR lines
     */
    /**
     * Add one line per manually captured tariff position of the tenant.
     *
     * <p>A position is included when its quarter <b>overlaps</b> the billing period — not only
     * when the quarter lies fully inside it. Reason: a tenant moving out mid-quarter is billed
     * for a partial period only, and with the stricter rule their position would never appear.
     * Double billing cannot arise because each position belongs to exactly one tenant.
     *
     * @param rechnung Invoice to add the lines to
     * @param mieter Tenant (may be null for vacant units - then there are no positions)
     * @param von Period start (inclusive)
     * @param bis Period end (inclusive)
     * @return Sum of the added line amounts
     */
    private BigDecimal berechneTarifpositionsZeilen(RechnungDTO rechnung, Mieter mieter,
                                                    LocalDate von, LocalDate bis) {
        if (mieter == null) {
            return BigDecimal.ZERO;
        }

        // Positionen ALLER Einheiten des Mieters - Wohnung und Ladestation(en) landen damit auf
        // derselben Rechnung (Specs/Ladestationen.md FR-1.5).
        BigDecimal total = BigDecimal.ZERO;
        for (Tarifposition position : tarifpositionService.getFuerRechnung(
                mieterService.getEinheitIds(mieter.getId()), von, bis)) {
            // Same rounding as the ZEV/VNB lines, so an invoice does not mix conventions.
            BigDecimal menge = position.getMenge().setScale(0, RoundingMode.HALF_UP);
            BigDecimal preis = position.getTarif().getPreis();
            BigDecimal betrag = menge.multiply(preis);

            LocalDate[] zeitraum = zeitraumDerPositionszeile(position);

            rechnung.addTarifZeile(new TarifZeileDTO(
                    bezeichnungMitQuellReferenz(position),
                    zeitraum[0],
                    zeitraum[1],
                    menge,
                    preis,
                    betrag,
                    position.getTarif().getTariftyp(),
                    // Bei ZUSATZ steht die Einheit am Tarif, sonst folgt sie aus dem Typ -
                    // sonst stuende auf der Rechnung "3 kWh" fuer drei Monate Gaestezimmer.
                    position.getTarif().effektiveMengeneinheit()
            ));
            total = total.add(betrag);

            log.debug("Tarifposition line (Q{}/{}): {} * {} = {} CHF",
                    position.getQuartal(), position.getJahr(), menge, preis, betrag);
        }
        return total;
    }

    /**
     * Zeitraum der Positionszeile: das Quartal der Position, <b>eingeschränkt auf die Gültigkeit
     * des Tarifs</b>.
     *
     * <p>Damit rechnet die Positionszeile wie die ZEV-/VNB- und Grundgebühr-Zeilen, die ihren
     * Zeitraum ebenfalls mit der Tarifgültigkeit schneiden. Ein Tarif, der erst Mitte Quartal
     * beginnt, erscheint also nicht mehr über das ganze Quartal.
     *
     * <p>Bewusst <b>nicht</b> zusätzlich mit dem Rechnungszeitraum geschnitten: Die erfasste
     * Menge gehört zum ganzen Quartal und wird nicht anteilig aufgeteilt — zieht ein Mieter
     * Mitte Quartal aus, erhält er trotzdem seine volle Menge (Specs/Ladestromtarif.md FR-1.5).
     *
     * <p>Überschneiden sich Quartal und Gültigkeit gar nicht, bleibt es beim Quartal: Ein
     * umgekehrter Zeitraum („30.09. – 01.07.") sähe auf der Rechnung nach einem Fehler aus. Diese
     * Kombination lässt sich derzeit erfassen — die Prüfung beim Speichern fehlt noch.
     *
     * @param position Tarifposition
     * @return Zweielementiges Feld {@code [von, bis]}
     */
    private LocalDate[] zeitraumDerPositionszeile(Tarifposition position) {
        LocalDate quartalVon = TarifpositionService.quartalBeginn(position.getJahr(), position.getQuartal());
        LocalDate quartalBis = TarifpositionService.quartalEnde(position.getJahr(), position.getQuartal());
        LocalDate gueltigVon = position.getTarif().getGueltigVon();
        LocalDate gueltigBis = position.getTarif().getGueltigBis();

        if (gueltigBis.isBefore(quartalVon) || gueltigVon.isAfter(quartalBis)) {
            return new LocalDate[] { quartalVon, quartalBis };
        }
        return new LocalDate[] {
                gueltigVon.isAfter(quartalVon) ? gueltigVon : quartalVon,
                gueltigBis.isBefore(quartalBis) ? gueltigBis : quartalBis
        };
    }

    /**
     * Bezeichnung der Rechnungszeile, ergaenzt um die Quell-Referenz der Position (z.B. die
     * Ladepunkt-Kennung), sofern erfasst: {@code "Ladestrom (LP-01)"}.
     *
     * <p>Bewusst im bestehenden Bezeichnungsfeld statt als eigene Spalte: {@code rechnung.jrxml}
     * kennt nur die Felder bezeichnung/von/bis/menge/mengeneinheit/preis/betrag. So erscheint die
     * Angabe ohne Layoutaenderung in Web und PDF identisch.
     *
     * @param position Tarifposition
     * @return Bezeichnung mit Quell-Referenz in Klammern, sonst die Bezeichnung allein
     */
    private String bezeichnungMitQuellReferenz(Tarifposition position) {
        String bezeichnung = position.getTarif().getBezeichnung();
        String quellReferenz = position.getQuellReferenz();
        if (quellReferenz == null || quellReferenz.isBlank()) {
            return bezeichnung;
        }
        return bezeichnung + " (" + quellReferenz.trim() + ")";
    }

    private BigDecimal berechneGrundgebuehrZeilen(RechnungDTO rechnung, LocalDate von, LocalDate bis,
                                                  List<Tarif> tarife) {
        BigDecimal total = BigDecimal.ZERO;

        for (Tarif tarif : tarife) {
            LocalDate effVon = tarif.getGueltigVon().isBefore(von) ? von : tarif.getGueltigVon();
            LocalDate effBis = tarif.getGueltigBis().isAfter(bis) ? bis : tarif.getGueltigBis();

            int monate = zaehleVolleMonate(effVon, effBis);
            if (monate <= 0) {
                continue;
            }

            BigDecimal preis = tarif.getPreis();
            BigDecimal betrag = BigDecimal.valueOf(monate).multiply(preis);

            rechnung.addTarifZeile(new TarifZeileDTO(
                    tarif.getBezeichnung(),
                    effVon,
                    effBis,
                    BigDecimal.valueOf(monate),
                    preis,
                    betrag,
                    TarifTyp.GRUNDGEBUEHR,
                    "MONAT"
            ));
            total = total.add(betrag);

            log.debug("GRUNDGEBUEHR line ({} to {}): {} Monate * {} = {} CHF",
                    effVon, effBis, monate, preis, betrag);
        }

        return total;
    }

    /**
     * Count full calendar months within the period [von, bis] (inclusive).
     * A month is counted as full if both its first and last day lie within the period.
     *
     * @param von Start date (inclusive)
     * @param bis End date (inclusive)
     * @return Number of full calendar months
     */
    private int zaehleVolleMonate(LocalDate von, LocalDate bis) {
        int count = 0;
        LocalDate monthStart = von.withDayOfMonth(1);
        while (!monthStart.isAfter(bis)) {
            LocalDate monthEnd = monthStart.withDayOfMonth(
                    monthStart.getMonth().length(Year.isLeap(monthStart.getYear()))
            );
            if (!monthStart.isBefore(von) && !monthEnd.isAfter(bis)) {
                count++;
            }
            monthStart = monthStart.plusMonths(1);
        }
        return count;
    }

    /**
     * Calculate tariff lines for a specific tariff type (ZEV or VNB).
     * For each tariff, queries the actual measurements for that tariff's validity period.
     *
     * @param rechnung The invoice DTO to add lines to
     * @param einheit The unit
     * @param von Invoice start date
     * @param bis Invoice end date
     * @param tarife List of applicable tariffs
     * @param typ Tariff type (ZEV or VNB)
     * @return Total amount for all lines of this type
     */
    private BigDecimal berechneTarifZeilen(RechnungDTO rechnung, Einheit einheit, LocalDate von, LocalDate bis,
                                           List<Tarif> tarife, TarifTyp typ) {
        BigDecimal totalBetrag = BigDecimal.ZERO;

        if (tarife.isEmpty()) {
            log.warn("No {} tariffs found for period {} to {}", typ, von, bis);
            return BigDecimal.ZERO;
        }

        for (Tarif tarif : tarife) {
            // Determine effective dates: intersection of tariff validity and invoice period
            LocalDate effectiveVon = tarif.getGueltigVon().isBefore(von) ? von : tarif.getGueltigVon();
            LocalDate effectiveBis = tarif.getGueltigBis().isAfter(bis) ? bis : tarif.getGueltigBis();

            // Query actual measurements for this specific period
            LocalDateTime periodStart = effectiveVon.atStartOfDay();
            LocalDateTime periodEnd = effectiveBis.plusDays(1).atStartOfDay(); // exclusive end

            double mengeRaw;
            if (typ == TarifTyp.ZEV) {
                // ZEV: use zevCalculated values
                Double sum = messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(einheit, periodStart, periodEnd);
                mengeRaw = sum != null ? sum : 0.0;
            } else {
                // VNB: use (total - zevCalculated) values
                Double sumTotal = messwerteRepository.sumTotalByEinheitAndZeitBetween(einheit, periodStart, periodEnd);
                Double sumZev = messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(einheit, periodStart, periodEnd);
                double total = sumTotal != null ? sumTotal : 0.0;
                double zev = sumZev != null ? sumZev : 0.0;
                mengeRaw = Math.max(0, total - zev);
            }

            // Die Messwerte liegen als Double in der Datenbank; auf ganze kWh gerundet ist die
            // Menge exakt darstellbar und wird ab hier als BigDecimal weitergerechnet.
            BigDecimal menge = BigDecimal.valueOf(Math.round(mengeRaw));
            BigDecimal preis = tarif.getPreis();
            BigDecimal betrag = menge.multiply(preis);

            TarifZeileDTO zeile = new TarifZeileDTO(
                tarif.getBezeichnung(),
                effectiveVon,
                effectiveBis,
                menge,
                preis,
                betrag,
                typ,
                "KWH"
            );
            rechnung.addTarifZeile(zeile);
            totalBetrag = totalBetrag.add(betrag);

            log.debug("{} line ({} to {}): {} kWh * {} = {} CHF",
                typ, effectiveVon, effectiveBis, menge, preis, betrag);
        }

        return totalBetrag;
    }

    /**
     * Round amount to nearest 5 Rappen (0.05 CHF).
     *
     * <p>Gerechnet wird {@code round(amount / 0.05) * 0.05} mit {@link RoundingMode#HALF_UP} -
     * kaufmaennisch und von Null weg, also symmetrisch fuer negative Betraege. Das Ergebnis traegt
     * immer zwei Nachkommastellen und ist damit unmittelbar als {@code debitor.betrag}
     * ({@code NUMERIC(10,2)}) und als Betrag des Einzahlungsscheins verwendbar.
     *
     * @param amount Amount in CHF
     * @return Rounded amount, scale 2
     */
    public static BigDecimal roundTo5Rappen(BigDecimal amount) {
        return amount.divide(FUENF_RAPPEN, 0, RoundingMode.HALF_UP)
                .multiply(FUENF_RAPPEN)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
