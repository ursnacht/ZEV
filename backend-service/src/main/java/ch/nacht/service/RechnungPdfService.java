package ch.nacht.service;

import ch.nacht.dto.RechnungDTO;
import ch.nacht.entity.Translation;
import ch.nacht.repository.TranslationRepository;
import net.codecrete.qrbill.generator.*;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.util.JRLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for generating invoice PDFs with Swiss QR payment slips using JasperReports.
 */
@Service
public class RechnungPdfService {

    private static final Logger log = LoggerFactory.getLogger(RechnungPdfService.class);

    private final TranslationRepository translationRepository;

    private JasperReport compiledReport;
    private JasperReport compiledQrReport;

    public RechnungPdfService(TranslationRepository translationRepository) {
        this.translationRepository = translationRepository;
    }

    @PostConstruct
    public void init() {
        try {
            // main invoice template
            InputStream reportStream = getClass().getResourceAsStream("/reports/rechnung.jasper");
            if (reportStream == null) {
                throw new RuntimeException("Could not find rechnung.jasper template");
            }
            compiledReport = (JasperReport) JRLoader.loadObject(reportStream);
            log.info("Loaded rechnung.jasper template successfully");

            // QR-Bill sub-report template
            InputStream qrStream = getClass().getResourceAsStream("/reports/qr-zahlteil.jasper");
            if (qrStream == null) {
                throw new RuntimeException("Could not find qr-zahlteil.jasper template");
            }
            compiledQrReport = (JasperReport) JRLoader.loadObject(qrStream);
            log.info("Loaded qr-zahlteil.jasper template successfully");

        } catch (JRException e) {
            log.error("Failed to load JasperReports templates: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to load JasperReports templates", e);
        }
    }

    /**
     * Generate a PDF invoice with QR payment slip.
     *
     * @param rechnung Invoice data
     * @param sprache Language code (de or en)
     * @return PDF as byte array
     */
    public byte[] generatePdf(RechnungDTO rechnung, String sprache) {
        log.info("Generating invoice PDF for unit: {}", rechnung.getEinheitName());

        Map<String, String> translations = loadTranslations(sprache);
        byte[] qrCodeBytes = generateQrCodePng(rechnung);
        InputStream qrCodeStream = qrCodeBytes != null ? new ByteArrayInputStream(qrCodeBytes) : null;

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("RECHNUNG", rechnung);
        parameters.put("TRANSLATIONS", translations);
        parameters.put("QR_SUBREPORT", compiledQrReport);
        parameters.put("QR_CODE_IMAGE", qrCodeStream);

        // TarifZeilen als DataSource für das Detail-Band
        JRBeanCollectionDataSource tarifDataSource = new JRBeanCollectionDataSource(rechnung.getTarifZeilen());

        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            JasperPrint jasperPrint = JasperFillManager.fillReport(compiledReport, parameters, tarifDataSource);
            JasperExportManager.exportReportToPdfStream(jasperPrint, os);

            log.info("Invoice PDF generated successfully for unit: {}, size: {} bytes",
                    rechnung.getEinheitName(), os.size());
            return os.toByteArray();
        } catch (JRException e) {
            log.error("Failed to generate invoice PDF for unit {}: {}",
                    rechnung.getEinheitName(), e.getMessage(), e);
            throw new RuntimeException("PDF generation failed", e);
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
     * Generate only the QR-Code as PNG bytes.
     * Uses the qrbill-generator library to create the QR code.
     */
    private byte[] generateQrCodePng(RechnungDTO rechnung) {
        try {
            Bill bill = new Bill();
            bill.setVersion(Bill.Version.V2_0);
            bill.setAmount(java.math.BigDecimal.valueOf(rechnung.getEndBetrag()));
            bill.setCurrency("CHF");

            // Creditor (invoice issuer)
            Address creditor = new Address();
            creditor.setName(rechnung.getStellerName());
            creditor.setStreet(rechnung.getStellerStrasse());
            // Parse PLZ and Ort from combined string
            String[] plzOrt = rechnung.getStellerPlzOrt().split(" ", 2);
            creditor.setPostalCode(plzOrt[0]);
            creditor.setTown(plzOrt.length > 1 ? plzOrt[1] : "");
            creditor.setCountryCode("CH");
            bill.setCreditor(creditor);

            // IBAN
            String iban = rechnung.getIban().replace(" ", "");
            bill.setAccount(iban);

            // Debtor (invoice recipient) - use tenant address
            if (rechnung.getMieterName() != null && !rechnung.getMieterName().isEmpty()) {
                Address debtor = new Address();
                debtor.setName(rechnung.getMieterName());
                if (rechnung.getMieterStrasse() != null) {
                    debtor.setStreet(rechnung.getMieterStrasse());
                }
                if (rechnung.getMieterPlzOrt() != null && !rechnung.getMieterPlzOrt().isEmpty()) {
                    String[] mieterPlzOrtParts = rechnung.getMieterPlzOrt().split(" ", 2);
                    debtor.setPostalCode(mieterPlzOrtParts[0]);
                    debtor.setTown(mieterPlzOrtParts.length > 1 ? mieterPlzOrtParts[1] : "");
                }
                debtor.setCountryCode("CH");
                bill.setDebtor(debtor);
            }

            // Reference/message
            bill.setUnstructuredMessage("Stromrechnung " + rechnung.getEinheitName());

            // Validate the bill
            ValidationResult validation = QRBill.validate(bill);
            if (validation.hasErrors()) {
                log.error("QR Bill validation errors: {}", validation.getValidationMessages());
                return null;
            }

            // Generate only the QR-Code as PNG (not the full payment slip)
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

    /**
     * Format IBAN in groups of 4 characters: CH12 3456 7890 1234 5678 9
     */
    public static String formatIban(String iban) {
        if (iban == null) return "";
        String clean = iban.replace(" ", "");
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < clean.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                formatted.append(" ");
            }
            formatted.append(clean.charAt(i));
        }
        return formatted.toString();
    }

    /**
     * Format amount with space as thousands separator: 1 234.50
     */
    public static String formatBetragQrBill(double value) {
        DecimalFormat df = new DecimalFormat("#,##0.00");
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator('.');
        df.setDecimalFormatSymbols(symbols);
        return df.format(value);
    }
}
