package ch.nacht.service;

import ch.nacht.config.RechnungConfig;
import ch.nacht.dto.RechnungDTO;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.MesswerteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
    private final RechnungConfig rechnungConfig;

    public RechnungService(EinheitRepository einheitRepository,
                           MesswerteRepository messwerteRepository,
                           RechnungConfig rechnungConfig) {
        this.einheitRepository = einheitRepository;
        this.messwerteRepository = messwerteRepository;
        this.rechnungConfig = rechnungConfig;
    }

    /**
     * Calculate invoices for the given unit IDs and time period.
     *
     * @param einheitIds List of unit IDs to generate invoices for
     * @param von Start date (inclusive)
     * @param bis End date (inclusive)
     * @return List of calculated invoice DTOs
     */
    public List<RechnungDTO> berechneRechnungen(List<Long> einheitIds, LocalDate von, LocalDate bis) {
        log.info("Calculating invoices for {} units from {} to {}", einheitIds.size(), von, bis);

        List<RechnungDTO> rechnungen = new ArrayList<>();

        for (Long einheitId : einheitIds) {
            einheitRepository.findById(einheitId).ifPresent(einheit -> {
                if (einheit.getTyp() == EinheitTyp.CONSUMER) {
                    RechnungDTO rechnung = berechneRechnung(einheit, von, bis);
                    rechnungen.add(rechnung);
                    log.debug("Calculated invoice for unit {}: {} CHF", einheit.getName(), rechnung.getEndBetrag());
                } else {
                    log.warn("Skipping unit {} - not a consumer", einheit.getName());
                }
            });
        }

        log.info("Generated {} invoices", rechnungen.size());
        return rechnungen;
    }

    /**
     * Calculate a single invoice for a unit and time period.
     *
     * @param einheit The unit to generate invoice for
     * @param von Start date (inclusive)
     * @param bis End date (inclusive)
     * @return Calculated invoice DTO
     */
    public RechnungDTO berechneRechnung(Einheit einheit, LocalDate von, LocalDate bis) {
        RechnungDTO rechnung = new RechnungDTO();

        // Unit information
        rechnung.setEinheitId(einheit.getId());
        rechnung.setEinheitName(einheit.getName());
        rechnung.setMietername(einheit.getMietername());
        rechnung.setMesspunkt(einheit.getMesspunkt());

        // Time period
        rechnung.setVon(von);
        rechnung.setBis(bis);
        rechnung.setErstellungsdatum(LocalDate.now());

        // Convert dates to LocalDateTime for repository queries
        LocalDateTime vonDateTime = von.atStartOfDay();
        LocalDateTime bisDateTime = bis.plusDays(1).atStartOfDay(); // exclusive end

        // Get measurement sums
        Double sumTotal = messwerteRepository.sumTotalByEinheitAndZeitBetween(einheit, vonDateTime, bisDateTime);
        Double sumZevCalculated = messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(einheit, vonDateTime, bisDateTime);

        // ZEV (self-consumed solar energy)
        // Round quantity to whole kWh for invoice display and calculation
        double zevMengeRaw = sumZevCalculated != null ? sumZevCalculated : 0.0;
        double zevMenge = Math.round(zevMengeRaw);
        double zevPreis = rechnungConfig.getTarif().getZev().getPreis();
        double zevBetrag = zevMenge * zevPreis;

        rechnung.setZevMenge(zevMenge);
        rechnung.setZevPreis(zevPreis);
        rechnung.setZevBetrag(zevBetrag);
        rechnung.setZevBezeichnung(rechnungConfig.getTarif().getZev().getBezeichnung());

        // EWB (grid energy = total - zev)
        // Round quantity to whole kWh for invoice display and calculation
        double totalMenge = sumTotal != null ? sumTotal : 0.0;
        double ewbMengeRaw = totalMenge - zevMengeRaw;
        if (ewbMengeRaw < 0) {
            ewbMengeRaw = 0; // Safety check
        }
        double ewbMenge = Math.round(ewbMengeRaw);
        double ewbPreis = rechnungConfig.getTarif().getEwb().getPreis();
        double ewbBetrag = ewbMenge * ewbPreis;

        rechnung.setEwbMenge(ewbMenge);
        rechnung.setEwbPreis(ewbPreis);
        rechnung.setEwbBetrag(ewbBetrag);
        rechnung.setEwbBezeichnung(rechnungConfig.getTarif().getEwb().getBezeichnung());

        // Calculate totals with rounding to 5 Rappen
        // Total is now calculated with rounded quantities for invoice consistency
        double totalBetrag = zevBetrag + ewbBetrag;
        double endBetrag = roundTo5Rappen(totalBetrag);
        double rundung = endBetrag - totalBetrag;

        rechnung.setTotalBetrag(totalBetrag);
        rechnung.setRundung(rundung);
        rechnung.setEndBetrag(endBetrag);

        // Configuration values
        rechnung.setZahlungsfrist(rechnungConfig.getZahlungsfrist());
        rechnung.setIban(rechnungConfig.getIban());
        rechnung.setStellerName(rechnungConfig.getSteller().getName());
        rechnung.setStellerStrasse(rechnungConfig.getSteller().getStrasse());
        rechnung.setStellerPlzOrt(rechnungConfig.getSteller().getPlz() + " " + rechnungConfig.getSteller().getOrt());
        rechnung.setAdresseStrasse(rechnungConfig.getAdresse().getStrasse());
        rechnung.setAdressePlzOrt(rechnungConfig.getAdresse().getPlz() + " " + rechnungConfig.getAdresse().getOrt());

        return rechnung;
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
