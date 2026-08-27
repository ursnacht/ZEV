package ch.nacht.service;

import ch.nacht.dto.NkRechnungDTO;
import ch.nacht.entity.Translation;
import ch.nacht.repository.TranslationRepository;
import jakarta.annotation.PostConstruct;
import net.codecrete.qrbill.generator.*;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.util.JRLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Erzeugt das PDF einer Nebenkostenrechnung samt QR-Zahlteil
 * (Specs/Nebenkosten/RechnungenGenerieren.md, FR-8).
 *
 * <p>Eigenes Template {@code nk-rechnung.jrxml} statt {@code rechnung.jrxml}: Die Zeilen tragen
 * andere Felder (Prozentsatz, Mengeneinheit je Zeile), und darunter stehen Akonto und Saldo. Eine
 * gemeinsame Vorlage muesste beide Formen bedingt zeichnen und waere fuer keine der beiden mehr
 * lesbar.
 *
 * <p>Dieser Service laedt <b>keine Mandantendaten</b> — er formt nur, was ihm uebergeben wird.
 * Deshalb prueft er den Feature-Flag nicht selbst; das tut {@code NkRechnungService}, bevor er
 * hierher kommt.
 */
@Service
public class NkRechnungPdfService {

    private static final Logger log = LoggerFactory.getLogger(NkRechnungPdfService.class);

    private final TranslationRepository translationRepository;

    private JasperReport compiledReport;

    public NkRechnungPdfService(TranslationRepository translationRepository) {
        this.translationRepository = translationRepository;
    }

    @PostConstruct
    public void init() {
        try {
            InputStream reportStream = getClass().getResourceAsStream("/reports/nk-rechnung.jasper");
            if (reportStream == null) {
                throw new RuntimeException("Could not find nk-rechnung.jasper template");
            }
            compiledReport = (JasperReport) JRLoader.loadObject(reportStream);
            log.info("Loaded nk-rechnung.jasper template successfully");
        } catch (JRException e) {
            log.error("Failed to load nk-rechnung template: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to load nk-rechnung template", e);
        }
    }

    /**
     * Erzeugt das PDF einer Nebenkostenrechnung.
     *
     * @param rechnung Rechnungsdaten eines Mieters
     * @param sprache  Sprachcode ({@code de} oder {@code en})
     * @return das PDF
     */
    public byte[] generatePdf(NkRechnungDTO rechnung, String sprache) {
        log.info("Generating NK invoice PDF for mieter: {}", rechnung.getMieterName());

        Map<String, String> translations = loadTranslations(sprache);
        // Nur bei einer Nachzahlung: Ein Einzahlungsschein ueber 0 oder einen negativen Betrag
        // ist ungueltig. Das Template laesst den Zahlteil dann ebenfalls weg.
        byte[] qrCodeBytes = rechnung.isNachzahlung() ? generateQrCodePng(rechnung) : null;
        InputStream qrCodeStream = qrCodeBytes != null ? new ByteArrayInputStream(qrCodeBytes) : null;

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("RECHNUNG", rechnung);
        parameters.put("TRANSLATIONS", translations);
        parameters.put("QR_CODE_IMAGE", qrCodeStream);

        JRBeanCollectionDataSource zeilenDataSource = new JRBeanCollectionDataSource(rechnung.getZeilen());

        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            JasperPrint jasperPrint = JasperFillManager.fillReport(compiledReport, parameters, zeilenDataSource);
            JasperExportManager.exportReportToPdfStream(jasperPrint, os);

            log.info("NK invoice PDF generated for mieter {}, size: {} bytes",
                    rechnung.getMieterName(), os.size());
            return os.toByteArray();
        } catch (JRException e) {
            log.error("Failed to generate NK invoice PDF for mieter {}: {}",
                    rechnung.getMieterName(), e.getMessage(), e);
            throw new RuntimeException("NK PDF generation failed", e);
        }
    }

    private Map<String, String> loadTranslations(String sprache) {
        Map<String, String> translations = new HashMap<>();
        for (Translation t : translationRepository.findAll()) {
            String value = "en".equalsIgnoreCase(sprache) ? t.getEnglisch() : t.getDeutsch();
            translations.put(t.getKey(), value != null ? value : t.getKey());
        }
        return translations;
    }

    /**
     * Erzeugt den QR-Code als PNG.
     *
     * <p>Fehlt die Empfaengeradresse oder ist die IBAN unbrauchbar, liefert die Methode
     * {@code null}: Das Bildelement traegt {@code onErrorType="Blank"}, das PDF entsteht also ohne
     * Zahlteil. Ein Abbruch waere die schlechtere Wahl — die Rechnung ist auch ohne
     * Einzahlungsschein ein gueltiger Beleg, und die Forderung steht in der Debitorenkontrolle.
     */
    private byte[] generateQrCodePng(NkRechnungDTO rechnung) {
        try {
            Bill bill = new Bill();
            bill.setVersion(Bill.Version.V2_0);
            bill.setAmount(rechnung.getEndBetrag());
            bill.setCurrency("CHF");

            Address creditor = new Address();
            creditor.setName(rechnung.getStellerName());
            creditor.setStreet(rechnung.getStellerStrasse());
            String[] plzOrt = rechnung.getStellerPlzOrt() != null
                    ? rechnung.getStellerPlzOrt().split(" ", 2)
                    : new String[]{""};
            creditor.setPostalCode(plzOrt[0]);
            creditor.setTown(plzOrt.length > 1 ? plzOrt[1] : "");
            creditor.setCountryCode("CH");
            bill.setCreditor(creditor);

            bill.setAccount(rechnung.getIban() != null ? rechnung.getIban().replace(" ", "") : null);

            if (rechnung.getMieterName() != null && !rechnung.getMieterName().isEmpty()) {
                Address debtor = new Address();
                debtor.setName(rechnung.getMieterName());
                if (rechnung.getMieterStrasse() != null) {
                    debtor.setStreet(rechnung.getMieterStrasse());
                }
                if (rechnung.getMieterPlzOrt() != null && !rechnung.getMieterPlzOrt().isEmpty()) {
                    String[] mieterPlzOrt = rechnung.getMieterPlzOrt().split(" ", 2);
                    debtor.setPostalCode(mieterPlzOrt[0]);
                    debtor.setTown(mieterPlzOrt.length > 1 ? mieterPlzOrt[1] : "");
                }
                debtor.setCountryCode("CH");
                bill.setDebtor(debtor);
            }

            bill.setUnstructuredMessage(nachricht(rechnung));

            ValidationResult validation = QRBill.validate(bill);
            if (validation.hasErrors()) {
                log.error("QR Bill validation errors: {}", validation.getValidationMessages());
                return null;
            }

            bill.setFormat(new BillFormat());
            bill.getFormat().setOutputSize(OutputSize.QR_CODE_ONLY);
            bill.getFormat().setGraphicsFormat(GraphicsFormat.PNG);
            bill.getFormat().setLanguage(Language.DE);

            return QRBill.generate(bill);

        } catch (Exception e) {
            log.error("Failed to generate QR Code: {}", e.getMessage(), e);
            return null;
        }
    }

    /** Mitteilung auf dem Zahlteil — Bezeichnung der Abrechnung, damit die Zahlung zuordenbar ist. */
    private String nachricht(NkRechnungDTO rechnung) {
        String bezeichnung = rechnung.getBezeichnung();
        return bezeichnung != null && !bezeichnung.isBlank()
                ? "Nebenkosten " + bezeichnung
                : "Nebenkosten";
    }
}
