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
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.MesswerteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for calculating and generating invoice data.
 */
@Service
public class RechnungService {

    private static final Logger log = LoggerFactory.getLogger(RechnungService.class);

    private final EinheitRepository einheitRepository;
    private final MesswerteRepository messwerteRepository;
    private final EinstellungenService einstellungenService;
    private final TarifService tarifService;
    private final MieterService mieterService;
    private final HibernateFilterService hibernateFilterService;

    public RechnungService(EinheitRepository einheitRepository,
                           MesswerteRepository messwerteRepository,
                           EinstellungenService einstellungenService,
                           TarifService tarifService,
                           MieterService mieterService,
                           HibernateFilterService hibernateFilterService) {
        this.einheitRepository = einheitRepository;
        this.messwerteRepository = messwerteRepository;
        this.einstellungenService = einstellungenService;
        this.tarifService = tarifService;
        this.mieterService = mieterService;
        this.hibernateFilterService = hibernateFilterService;
    }

    /**
     * Calculate invoices for the given unit IDs and time period.
     * Creates separate invoices for each tenant within the period.
     *
     * @param einheitIds List of unit IDs to generate invoices for
     * @param von Start date (inclusive)
     * @param bis End date (inclusive)
     * @return List of calculated invoice DTOs
     * @throws IllegalStateException if tariffs don't cover the entire period
     */
    @Transactional(readOnly = true)
    public List<RechnungDTO> berechneRechnungen(List<Long> einheitIds, LocalDate von, LocalDate bis) {
        hibernateFilterService.enableOrgFilter();
        log.info("Calculating invoices for {} units from {} to {}", einheitIds.size(), von, bis);

        // Validate tariff coverage before calculating any invoices
        tarifService.validateTarifAbdeckung(von, bis);

        List<RechnungDTO> rechnungen = new ArrayList<>();

        for (Long einheitId : einheitIds) {
            einheitRepository.findById(einheitId).ifPresent(einheit -> {
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
                } else {
                    log.warn("Skipping unit {} - not a consumer", einheit.getName());
                }
            });
        }

        log.info("Generated {} invoices", rechnungen.size());
        return rechnungen;
    }

    /**
     * Calculate a single invoice for a unit, optional tenant, and time period.
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

        double totalBetrag = 0.0;

        // Calculate ZEV tariff lines (based on zevCalculated measurements)
        totalBetrag += berechneTarifZeilen(rechnung, einheit, von, bis, zevTarife, TarifTyp.ZEV);

        // Calculate VNB tariff lines (based on total - zevCalculated measurements)
        totalBetrag += berechneTarifZeilen(rechnung, einheit, von, bis, vnbTarife, TarifTyp.VNB);

        // Calculate totals with rounding to 5 Rappen
        double endBetrag = roundTo5Rappen(totalBetrag);
        double rundung = endBetrag - totalBetrag;

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
     * Calculate tariff lines for a specific tariff type.
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
    private double berechneTarifZeilen(RechnungDTO rechnung, Einheit einheit, LocalDate von, LocalDate bis,
                                       List<Tarif> tarife, TarifTyp typ) {
        double totalBetrag = 0.0;

        if (tarife.isEmpty()) {
            log.warn("No {} tariffs found for period {} to {}", typ, von, bis);
            return 0.0;
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

            double menge = Math.round(mengeRaw);
            double preis = tarif.getPreis().doubleValue();
            double betrag = menge * preis;

            TarifZeileDTO zeile = new TarifZeileDTO(
                tarif.getBezeichnung(),
                effectiveVon,
                effectiveBis,
                menge,
                preis,
                betrag,
                typ
            );
            rechnung.addTarifZeile(zeile);
            totalBetrag += betrag;

            log.debug("{} line ({} to {}): {} kWh * {} = {} CHF",
                typ, effectiveVon, effectiveBis, menge, preis, betrag);
        }

        return totalBetrag;
    }

    /**
     * Round amount to nearest 5 Rappen (0.05 CHF).
     *
     * @param amount Amount in CHF
     * @return Rounded amount
     */
    public static double roundTo5Rappen(double amount) {
        return Math.round(amount * 20.0) / 20.0;
    }
}
