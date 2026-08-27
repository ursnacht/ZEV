package ch.nacht.service;

import ch.nacht.dto.EinstellungenDTO;
import ch.nacht.dto.NkAbrechnungDetailDTO;
import ch.nacht.dto.NkMieterAbrechnungDTO;
import ch.nacht.dto.NkRechnungDTO;
import ch.nacht.dto.NkRechnungDownloadDTO;
import ch.nacht.dto.NkRechnungLaufDTO;
import ch.nacht.dto.NkRechnungZeileDTO;
import ch.nacht.dto.NkZeileDTO;
import ch.nacht.dto.RechnungKonfigurationDTO;
import ch.nacht.entity.Debitorherkunft;
import ch.nacht.entity.FeatureFlag;
import ch.nacht.entity.Mengeneinheit;
import ch.nacht.entity.Mieter;
import ch.nacht.entity.NkAbrechnung;
import ch.nacht.exception.FeatureDisabledException;
import ch.nacht.repository.MieterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Baut die Nebenkostenrechnungen einer Abrechnung
 * (Specs/Nebenkosten/RechnungenGenerieren.md, FR-3).
 *
 * <p><b>Es wird nicht neu gerechnet.</b> Die Werte stammen aus
 * {@link NkAbrechnungService#getAbrechnungDetail(Long)} — derselben Quelle, die die Maske anzeigt.
 * Zwei Rechenwege ergaeben zwei Wahrheiten. Gerundet wird allein der Endbetrag auf 5 Rappen, weil
 * der QR-Zahlteil einen zahlbaren Betrag verlangt.
 *
 * <p>Jede oeffentliche Methode beginnt mit {@link #pruefeFeatureFlag()}. Sie verlaesst sich
 * <b>nicht</b> darauf, dass {@code NkAbrechnungService} den Flag ebenfalls prueft: Als
 * Nebenwirkung eines fremden Services waere die Zusicherung weg, sobald die Abrechnung einmal aus
 * einer anderen Quelle kommt. Die ArchUnit-Regel
 * {@code SecurityRules.nebenkostenServicesMustCheckFeatureFlag} haelt das fest.
 */
@Service
public class NkRechnungService {

    private static final Logger log = LoggerFactory.getLogger(NkRechnungService.class);

    /**
     * Uebersetzungsschluessel je Mengeneinheit — vollstaendig und ohne Rueckfall.
     *
     * <p>Bewusst kein {@code default}-Zweig auf {@code KWH}: Ein neuer Enum-Wert waere sonst
     * stillschweigend als Kilowattstunden beschriftet. Die Zahl stimmte, die Einheit daneben nicht.
     * Dieselbe Zuordnung wie im Frontend ({@code tarif.model.ts}, {@code MENGENEINHEIT_KEYS}).
     */
    private static final Map<Mengeneinheit, String> MENGENEINHEIT_KEYS = Map.of(
            Mengeneinheit.KWH, "KWH",
            Mengeneinheit.MONAT, "MONATE",
            Mengeneinheit.STUECK, "STUECK",
            Mengeneinheit.M3, "M3",
            Mengeneinheit.CHF, "CHF"
    );

    private final NkAbrechnungService nkAbrechnungService;
    private final NkRechnungPdfService nkRechnungPdfService;
    private final RechnungStorageService rechnungStorageService;
    private final DebitorService debitorService;
    private final EinstellungenService einstellungenService;
    private final MieterRepository mieterRepository;
    private final FeatureFlagService featureFlagService;
    private final OrganizationContextService organizationContextService;
    private final HibernateFilterService hibernateFilterService;

    public NkRechnungService(NkAbrechnungService nkAbrechnungService,
                            NkRechnungPdfService nkRechnungPdfService,
                            RechnungStorageService rechnungStorageService,
                            DebitorService debitorService,
                            EinstellungenService einstellungenService,
                            MieterRepository mieterRepository,
                            FeatureFlagService featureFlagService,
                            OrganizationContextService organizationContextService,
                            HibernateFilterService hibernateFilterService) {
        this.nkAbrechnungService = nkAbrechnungService;
        this.nkRechnungPdfService = nkRechnungPdfService;
        this.rechnungStorageService = rechnungStorageService;
        this.debitorService = debitorService;
        this.einstellungenService = einstellungenService;
        this.mieterRepository = mieterRepository;
        this.featureFlagService = featureFlagService;
        this.organizationContextService = organizationContextService;
        this.hibernateFilterService = hibernateFilterService;
        log.info("NkRechnungService initialized");
    }

    /**
     * Baut je Mieter der Abrechnung eine Rechnung.
     *
     * <p>Die Abrechnung muss <b>abgerechnet</b> sein: Rechnungen aus einem noch veraenderlichen
     * Stand waeren morgen falsch, und die gebuchte Forderung stuende mit einem Betrag da, den
     * niemand mehr nachvollziehen kann (FR-2). Die Sichtbarkeit des Menueeintrags im Browser ist
     * keine Absicherung — der Endpunkt ist ueber jeden HTTP-Client erreichbar.
     *
     * @param abrechnungId ID der Nebenkostenabrechnung
     * @return je Mieter eine Rechnung, in der Reihenfolge der Mieterbloecke
     * @throws NoSuchElementException wenn die Abrechnung nicht existiert oder einem anderen
     *                                Mandanten gehoert
     * @throws IllegalStateException  wenn die Abrechnung nicht abgerechnet ist
     */
    @Transactional(readOnly = true)
    public List<NkRechnungDTO> baueRechnungen(Long abrechnungId) {
        pruefeFeatureFlag();
        hibernateFilterService.enableOrgFilter();

        NkAbrechnungDetailDTO detail = nkAbrechnungService.getAbrechnungDetail(abrechnungId)
                .orElseThrow(() -> new NoSuchElementException("Nebenkostenabrechnung not found: " + abrechnungId));

        NkAbrechnung abrechnung = detail.getAbrechnung();
        if (!abrechnung.isAbgerechnet()) {
            log.warn("Rechnungslauf abgewiesen - Abrechnung {} ist nicht abgerechnet", abrechnungId);
            throw new IllegalStateException("NK_FEHLER_NICHT_ABGERECHNET");
        }

        EinstellungenDTO einstellungen = einstellungenService.getEinstellungenOrThrow();
        RechnungKonfigurationDTO config = einstellungen.getRechnung();
        RechnungKonfigurationDTO.StellerDTO steller = config.getSteller();

        List<NkMieterAbrechnungDTO> bloecke = detail.getBerechnung() != null
                ? detail.getBerechnung().getMieter()
                : List.of();

        List<NkRechnungDTO> rechnungen = new ArrayList<>();
        for (NkMieterAbrechnungDTO block : bloecke) {
            rechnungen.add(baueRechnung(abrechnung, block, config, steller));
        }

        log.info("Built {} NK invoices for abrechnung {}", rechnungen.size(), abrechnungId);
        return rechnungen;
    }

    /**
     * Erzeugt die PDF und bucht die Forderungen einer Abrechnung
     * (Specs/Nebenkosten/RechnungenGenerieren.md, FR-6).
     *
     * <p><b>Je Mieter: erst buchen, dann ablegen.</b> Dieselbe Reihenfolge wie im ZEV-Pfad
     * ({@code RechnungController}): Scheitert die Buchung, entsteht kein Beleg. Scheitert danach
     * das Ablegen, bleibt die Forderung stehen und die Zeile traegt {@code fehler} — die Rechnung
     * ist aus der abgeschlossenen Abrechnung jederzeit neu erzeugbar, eine Forderung ohne Beleg
     * also ein behebbarer Zustand. Umgekehrt bliebe ein Beleg ohne Forderung, und der fehlt in der
     * Debitorenkontrolle, wo ihn niemand vermisst.
     *
     * <p><b>Bewusst nicht {@code @Transactional}.</b> Der Lauf soll bei einem einzelnen Mieter
     * nicht abbrechen. Liefe alles in <i>einer</i> Transaktion, wuerde eine gescheiterte Buchung
     * sie als rollback-only markieren; der abgefangene Fehler waere folgenlos gewesen, und der
     * Commit am Ende schlaege mit einem generischen 500 fehl. Jedes
     * {@code upsertFromRechnung} traegt seine eigene Transaktion und schliesst fuer sich ab.
     *
     * @param abrechnungId ID der Nebenkostenabrechnung
     * @param sprache      Sprachcode ({@code de} oder {@code en}); {@code null} ergibt {@code de}
     * @return Ergebnis des Laufs
     */
    public NkRechnungLaufDTO erzeugeRechnungen(Long abrechnungId, String sprache) {
        pruefeFeatureFlag();

        List<NkRechnungDTO> rechnungen = baueRechnungen(abrechnungId);
        String spracheOderDefault = sprache != null ? sprache : "de";

        NkRechnungLaufDTO lauf = new NkRechnungLaufDTO();
        lauf.setAbrechnungId(abrechnungId);
        int anzahlForderungen = 0;
        BigDecimal summeForderungen = BigDecimal.ZERO;

        for (NkRechnungDTO rechnung : rechnungen) {
            lauf.setBezeichnung(rechnung.getBezeichnung());
            lauf.setVon(rechnung.getVon());
            lauf.setBis(rechnung.getBis());

            NkRechnungLaufDTO.NkRechnungErgebnisDTO ergebnis = new NkRechnungLaufDTO.NkRechnungErgebnisDTO();
            ergebnis.setMieterId(rechnung.getMieterId());
            ergebnis.setMieterName(rechnung.getMieterName());
            ergebnis.setSaldo(rechnung.getEndBetrag());
            lauf.getRechnungen().add(ergebnis);

            try {
                byte[] pdf = nkRechnungPdfService.generatePdf(rechnung, spracheOderDefault);

                if (rechnung.isNachzahlung()) {
                    debitorService.upsertFromRechnung(rechnung.getMieterId(), rechnung.getEndBetrag(),
                            rechnung.getVon(), rechnung.getBis(), Debitorherkunft.NK);
                    ergebnis.setForderungGebucht(true);
                    anzahlForderungen++;
                    summeForderungen = summeForderungen.add(rechnung.getEndBetrag());
                } else {
                    // Guthaben oder Null: PDF ja, Forderung nein - debitor.betrag traegt
                    // CHECK (betrag > 0), ein Guthaben liesse sich dort nicht ablegen (FR-4).
                    log.info("Keine Forderung fuer Mieter {} (Saldo {})",
                            rechnung.getMieterId(), rechnung.getEndBetrag());
                }

                rechnungStorageService.store(RechnungStorageService.Rechnungsart.NK,
                        schluessel(abrechnungId, rechnung.getMieterId()), pdf, dateiname(rechnung));
                ergebnis.setFilename(dateiname(rechnung));
            } catch (Exception e) {
                log.error("Rechnung fuer Mieter {} fehlgeschlagen: {}",
                        rechnung.getMieterId(), e.getMessage(), e);
                ergebnis.setFehler("NK_FEHLER_RECHNUNG_MIETER");
                ergebnis.setFilename(null);
            }
        }

        lauf.setAnzahlRechnungen((int) lauf.getRechnungen().stream()
                .filter(e -> e.getFehler() == null).count());
        lauf.setAnzahlForderungen(anzahlForderungen);
        lauf.setSummeForderungen(summeForderungen);

        log.info("NK-Rechnungslauf abrechnung={}: {} Rechnungen, {} Forderungen, Summe {}",
                abrechnungId, lauf.getAnzahlRechnungen(), anzahlForderungen, summeForderungen);
        return lauf;
    }

    /**
     * Holt ein erzeugtes PDF aus der flüchtigen Ablage.
     *
     * <p>Nach 30 Minuten ist es weg — dann ist der Lauf zu wiederholen, was aus einer
     * abgeschlossenen Abrechnung jederzeit dasselbe Ergebnis liefert.
     *
     * @param abrechnungId ID der Nebenkostenabrechnung
     * @param mieterId     ID des Mieters
     * @return PDF und Dateiname, falls vorhanden und nicht abgelaufen
     */
    public Optional<NkRechnungDownloadDTO> ladePdf(Long abrechnungId, Long mieterId) {
        pruefeFeatureFlag();

        String key = schluessel(abrechnungId, mieterId);
        return rechnungStorageService.get(RechnungStorageService.Rechnungsart.NK, key)
                .map(pdf -> new NkRechnungDownloadDTO(pdf,
                        rechnungStorageService.getFilename(RechnungStorageService.Rechnungsart.NK, key)));
    }

    /**
     * Ablageschluessel. Der Namensraum steckt nicht im Schluessel, sondern in
     * {@link RechnungStorageService.Rechnungsart} — siehe dort, warum ein Praefix nicht traegt.
     */
    private String schluessel(Long abrechnungId, Long mieterId) {
        return abrechnungId + "_" + mieterId;
    }

    /** Lesbarer Dateiname; der Schluessel taugt dafuer nicht, er besteht aus zwei IDs. */
    private String dateiname(NkRechnungDTO rechnung) {
        String roh = "Nebenkosten_" + (rechnung.getBezeichnung() != null ? rechnung.getBezeichnung() : "")
                + "_" + (rechnung.getMieterName() != null ? rechnung.getMieterName() : "");
        return rechnungStorageService.getFilename(roh);
    }

    private NkRechnungDTO baueRechnung(NkAbrechnung abrechnung,
                                       NkMieterAbrechnungDTO block,
                                       RechnungKonfigurationDTO config,
                                       RechnungKonfigurationDTO.StellerDTO steller) {
        NkRechnungDTO rechnung = new NkRechnungDTO();

        rechnung.setAbrechnungId(abrechnung.getId());
        rechnung.setBezeichnung(abrechnung.getBezeichnung());
        rechnung.setVon(abrechnung.getDatumVon());
        rechnung.setBis(abrechnung.getDatumBis());
        rechnung.setErstellungsdatum(LocalDate.now());

        rechnung.setMieterId(block.getMieterId());
        rechnung.setMieterName(block.getName());
        setzeAdresse(rechnung, block.getMieterId());

        for (NkZeileDTO zeile : block.getZeilen()) {
            rechnung.getZeilen().add(baueZeile(zeile));
        }

        rechnung.setKostentotal(block.getKostentotal());
        rechnung.setAkontoAnzahlMonate(block.getAkontoAnzahlMonate());
        rechnung.setAkontoBetragProMonat(block.getAkontoBetragProMonat());
        rechnung.setAkontoKorrektur(block.getAkontoKorrektur());
        rechnung.setAkontoTotal(block.getAkontoTotal());

        // Rundung des Endbetrags auf 5 Rappen - dieselbe Regel wie bei der Quartalsrechnung, und
        // bewusst dieselbe Methode: Ein zweiter Rundungsweg waere ein zweites Ergebnis.
        // Die Zeilenrundung auf 1 Rappen der Abrechnung bleibt unberuehrt.
        BigDecimal saldo = block.getSaldo();
        BigDecimal endBetrag = RechnungService.roundTo5Rappen(saldo);
        rechnung.setSaldo(saldo);
        rechnung.setEndBetrag(endBetrag);
        rechnung.setRundung(endBetrag.subtract(saldo));

        rechnung.setZahlungsfrist(config.getZahlungsfrist());
        rechnung.setIban(config.getIban());
        rechnung.setStellerName(steller.getName());
        rechnung.setStellerStrasse(steller.getStrasse());
        rechnung.setStellerPlzOrt(steller.getPlz() + " " + steller.getOrt());

        return rechnung;
    }

    private NkRechnungZeileDTO baueZeile(NkZeileDTO zeile) {
        NkRechnungZeileDTO ziel = new NkRechnungZeileDTO();
        ziel.setBezeichnung(zeile.getBezeichnung());
        ziel.setMenge(zeile.getMenge());
        ziel.setBetragProEinheit(zeile.getBetragProEinheit());
        ziel.setProzentsatz(zeile.getProzentsatz());
        ziel.setBetrag(zeile.getBetrag());
        ziel.setMengeneinheit(zeile.getEinheit() != null
                ? MENGENEINHEIT_KEYS.get(zeile.getEinheit())
                : null);
        return ziel;
    }

    /**
     * Adresse des Mieters fuer Anschrift und QR-Zahlteil.
     *
     * <p>Fehlt sie, bleibt sie leer: Der Zahlteil scheitert dann still
     * ({@code generateQrCodePng} liefert {@code null}, das Bildelement traegt
     * {@code onErrorType="Blank"}), und das PDF entsteht trotzdem. Ein Abbruch waere die
     * schlechtere Wahl — die Rechnung ist auch ohne Einzahlungsschein ein gueltiger Beleg.
     */
    private void setzeAdresse(NkRechnungDTO rechnung, Long mieterId) {
        if (mieterId == null) {
            return;
        }
        Mieter mieter = mieterRepository.findFirstById(mieterId).orElse(null);
        if (mieter == null) {
            log.warn("Mieter {} nicht gefunden - Rechnung ohne Adresse", mieterId);
            return;
        }
        rechnung.setMieterStrasse(mieter.getStrasse());
        String plzOrt = ((mieter.getPlz() != null ? mieter.getPlz() : "") + " " +
                (mieter.getOrt() != null ? mieter.getOrt() : "")).trim();
        rechnung.setMieterPlzOrt(plzOrt.isEmpty() ? null : plzOrt);
    }

    /**
     * Weist den Zugriff ab, wenn der Feature-Flag fuer den Mandanten aus ist.
     *
     * <p>Ohne diese Pruefung waere der Flag reine Kosmetik: Die Seite bliebe verborgen, der
     * Endpunkt aber ueber jeden HTTP-Client erreichbar.
     */
    private void pruefeFeatureFlag() {
        Long orgId = organizationContextService.getCurrentOrgId();
        if (!featureFlagService.isEnabled(orgId, FeatureFlag.NEBENKOSTENABRECHNUNG)) {
            log.warn("NK-Rechnungslauf rejected - feature disabled for org: {}", orgId);
            throw new FeatureDisabledException("FEATURE_FLAG_DEAKTIVIERT");
        }
    }
}
