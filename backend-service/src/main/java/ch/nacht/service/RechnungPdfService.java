package ch.nacht.service;

import ch.nacht.dto.RechnungDTO;
import ch.nacht.entity.Translation;
import ch.nacht.repository.TranslationRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import net.codecrete.qrbill.generator.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for generating invoice PDFs with Swiss QR payment slips.
 */
@Service
public class RechnungPdfService {

    private static final Logger log = LoggerFactory.getLogger(RechnungPdfService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final TranslationRepository translationRepository;

    public RechnungPdfService(TranslationRepository translationRepository) {
        this.translationRepository = translationRepository;
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
        String html = buildHtml(rechnung, translations);

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();

            log.info("Invoice PDF generated successfully for unit: {}, size: {} bytes",
                    rechnung.getEinheitName(), os.size());
            return os.toByteArray();
        } catch (Exception e) {
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

    private String t(Map<String, String> translations, String key) {
        return translations.getOrDefault(key, key);
    }

    private String buildHtml(RechnungDTO rechnung, Map<String, String> translations) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>");
        html.append("<html><head><meta charset=\"UTF-8\"/>");
        html.append("<style>");
        html.append("@page { size: A4; margin: 15mm 20mm 15mm 20mm; }");
        html.append("body { font-family: Arial, sans-serif; font-size: 10pt; margin: 0; padding: 0; }");
        html.append("h1 { color: #0099cc; font-size: 18pt; margin-top: 30px; margin-bottom: 5px; }");
        html.append("h2 { color: #0099cc; font-size: 12pt; margin-top: 15px; margin-bottom: 8px; }");
        html.append(".header-section { margin-bottom: 30px; }");
        html.append(".header-row { margin: 3px 0; font-size: 9pt; }");
        html.append(".header-label { display: inline-block; width: 120px; }");
        html.append(".header-value { font-weight: normal; }");
        html.append(".address-block { float: right; width: 200px; text-align: left; font-size: 9pt; line-height: 1.4; }");
        html.append(".rechnungssteller { margin-bottom: 20px; font-size: 9pt; line-height: 1.4; }");
        html.append(".intro-text { color: #666; font-size: 9pt; margin-bottom: 10px; }");
        html.append("table { width: 100%; border-collapse: collapse; margin: 10px 0; font-size: 9pt; }");
        html.append("th { background-color: #f8f8f8; color: #0099cc; text-align: left; padding: 8px; border-bottom: 2px solid #0099cc; }");
        html.append("td { padding: 6px 8px; border-bottom: 1px solid #eee; }");
        html.append(".number { text-align: right; }");
        html.append(".unit { text-align: center; }");
        html.append(".messpunkt-row { background-color: #f8f8f8; }");
        html.append(".messpunkt-row td { font-weight: bold; padding-top: 10px; }");
        html.append(".total-section { margin-top: 5px; border-top: 2px solid #0099cc; }");
        html.append(".total-row td { padding: 6px 8px; }");
        html.append(".final-total { font-weight: bold; font-size: 11pt; background-color: #f0f8ff; }");
        html.append(".final-total td { padding: 10px 8px; }");
        html.append(".thanks { margin-top: 20px; font-size: 10pt; }");
        html.append(".qr-section { page-break-before: always; margin-top: 0; }");
        html.append(".qr-container { text-align: center; }");
        html.append(".qr-image { max-width: 100%; height: auto; }");
        html.append(".clearfix::after { content: ''; display: table; clear: both; }");
        html.append("</style>");
        html.append("</head><body>");

        // Header with invoice info and address
        html.append("<div class=\"header-section clearfix\">");

        // Right-aligned address block
        html.append("<div class=\"address-block\">");
        if (rechnung.getMietername() != null && !rechnung.getMietername().isEmpty()) {
            html.append(escapeHtml(rechnung.getMietername())).append("<br/>");
        }
        html.append(escapeHtml(rechnung.getAdresseStrasse())).append("<br/>");
        html.append(escapeHtml(rechnung.getAdressePlzOrt()));
        html.append("</div>");

        // Left-aligned header info
        html.append("<div class=\"header-row\"><span class=\"header-label\">").append(t(translations, "DATUM")).append(":</span>");
        html.append("<span class=\"header-value\">").append(rechnung.getErstellungsdatum().format(DATE_FORMATTER)).append("</span></div>");

        html.append("<div class=\"header-row\"><span class=\"header-label\">").append(t(translations, "ZAHLUNGSFRIST")).append(":</span>");
        html.append("<span class=\"header-value\">").append(escapeHtml(rechnung.getZahlungsfrist())).append("</span></div>");

        html.append("<div class=\"header-row\"><span class=\"header-label\">").append(t(translations, "ZEITRAUM")).append(":</span>");
        html.append("<span class=\"header-value\">").append(rechnung.getVon().format(DATE_FORMATTER));
        html.append(" - ").append(rechnung.getBis().format(DATE_FORMATTER)).append("</span></div>");

        // Rechnungssteller
        html.append("<div class=\"header-row\"><span class=\"header-label\">").append(t(translations, "RECHNUNGSSTELLER")).append(":</span>");
        html.append("<span class=\"header-value\">").append(escapeHtml(rechnung.getStellerName())).append("</span></div>");
        html.append("<div class=\"header-row\"><span class=\"header-label\"> </span>");
        html.append("<span class=\"header-value\">").append(escapeHtml(rechnung.getStellerStrasse())).append("</span></div>");
        html.append("<div class=\"header-row\"><span class=\"header-label\"> </span>");
        html.append("<span class=\"header-value\">").append(escapeHtml(rechnung.getStellerPlzOrt())).append("</span></div>");

        // Header with invoice info and address
        html.append("</div>");

        // Title
        html.append("<h1>").append(t(translations, "STROMRECHNUNG")).append("</h1>");

        // Invoice details subtitle
        html.append("<h2>").append(escapeHtml(rechnung.getEinheitName())).append("</h2>");
        html.append("<p class=\"intro-text\">").append(t(translations, "RECHNUNG_ZUSAMMENSETZUNG")).append("</p>");

        // Invoice table
        html.append("<table>");
        html.append("<tr>");
        html.append("<th>").append(t(translations, "BEZEICHNUNG")).append("</th>");
        html.append("<th class=\"number\">").append(t(translations, "MENGE")).append("</th>");
        html.append("<th class=\"number\">").append(t(translations, "PREIS")).append("</th>");
        html.append("<th class=\"unit\">").append(t(translations, "EINHEIT_LABEL")).append("</th>");
        html.append("<th class=\"number\">").append(t(translations, "TOTAL")).append(" CHF</th>");
        html.append("</tr>");

        // Messpunkt row
        html.append("<tr class=\"messpunkt-row\">");
        html.append("<td colspan=\"5\">").append(t(translations, "MESSPUNKT")).append(": ");
        html.append(rechnung.getMesspunkt() != null ? escapeHtml(rechnung.getMesspunkt()) : "-");
        html.append(" - ").append(t(translations, "ENERGIEBEZUG")).append("</td>");
        html.append("</tr>");

        // ZEV tariff row
        html.append("<tr>");
        html.append("<td>").append(escapeHtml(rechnung.getZevBezeichnung())).append("</td>");
        html.append("<td class=\"number\">").append(formatMenge(rechnung.getZevMenge())).append("</td>");
        html.append("<td class=\"number\">").append(formatPreis(rechnung.getZevPreis())).append("</td>");
        html.append("<td class=\"unit\">CHF / kWh</td>");
        html.append("<td class=\"number\">").append(formatBetrag(rechnung.getZevBetrag())).append("</td>");
        html.append("</tr>");

        // EWB tariff row
        html.append("<tr>");
        html.append("<td>").append(escapeHtml(rechnung.getEwbBezeichnung())).append("</td>");
        html.append("<td class=\"number\">").append(formatMenge(rechnung.getEwbMenge())).append("</td>");
        html.append("<td class=\"number\">").append(formatPreis(rechnung.getEwbPreis())).append("</td>");
        html.append("<td class=\"unit\">CHF / kWh</td>");
        html.append("<td class=\"number\">").append(formatBetrag(rechnung.getEwbBetrag())).append("</td>");
        html.append("</tr>");

        html.append("</table>");

        // Total section
        html.append("<table class=\"total-section\">");
        html.append("<tr class=\"total-row\">");
        html.append("<td colspan=\"4\">").append(t(translations, "TOTAL")).append(":</td>");
        html.append("<td class=\"number\">").append(formatBetrag(rechnung.getTotalBetrag())).append("</td>");
        html.append("</tr>");

        // Rounding row (only if not zero)
        if (Math.abs(rechnung.getRundung()) > 0.001) {
            html.append("<tr class=\"total-row\">");
            html.append("<td colspan=\"4\">").append(t(translations, "RUNDUNG")).append(":</td>");
            html.append("<td class=\"number\">").append(formatBetrag(rechnung.getRundung())).append("</td>");
            html.append("</tr>");
        }

        // Final total
        html.append("<tr class=\"final-total\">");
        html.append("<td colspan=\"4\">").append(t(translations, "TOTAL_ZU_BEZAHLEN")).append(":</td>");
        html.append("<td class=\"number\">").append(formatBetrag(rechnung.getEndBetrag())).append("</td>");
        html.append("</tr>");
        html.append("</table>");

        // Thanks
        html.append("<p class=\"thanks\">").append(t(translations, "BESTEN_DANK")).append("</p>");

        // QR payment slip on new page
        html.append("<div class=\"qr-section\">");
        String qrBillPngBase64 = generateQrBillPng(rechnung);
        if (qrBillPngBase64 != null) {
            html.append("<div class=\"qr-container\">");
            html.append("<img class=\"qr-image\" src=\"data:image/png;base64,").append(qrBillPngBase64).append("\"/>");
            html.append("</div>");
        }
        html.append("</div>");

        html.append("</body></html>");

        return html.toString();
    }

    /**
     * Generate Swiss QR Bill payment slip as PNG (base64 encoded).
     */
    private String generateQrBillPng(RechnungDTO rechnung) {
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

            // Debtor (invoice recipient)
            if (rechnung.getMietername() != null && !rechnung.getMietername().isEmpty()) {
                Address debtor = new Address();
                debtor.setName(rechnung.getMietername());
                debtor.setStreet(rechnung.getAdresseStrasse());
                String[] debtorPlzOrt = rechnung.getAdressePlzOrt().split(" ", 2);
                debtor.setPostalCode(debtorPlzOrt[0]);
                debtor.setTown(debtorPlzOrt.length > 1 ? debtorPlzOrt[1] : "");
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

            // Generate PNG (better compatibility with PDF renderers)
            bill.setFormat(new BillFormat());
            bill.getFormat().setOutputSize(OutputSize.QR_BILL_ONLY);
            bill.getFormat().setGraphicsFormat(GraphicsFormat.PNG);
            bill.getFormat().setLanguage(Language.DE);

            byte[] png = QRBill.generate(bill);
            return Base64.getEncoder().encodeToString(png);

        } catch (Exception e) {
            log.error("Failed to generate QR Bill: {}", e.getMessage(), e);
            return null;
        }
    }

    private String formatMenge(double value) {
        return String.format("%.0f", value);
    }

    private String formatPreis(double value) {
        return String.format("%.5f", value);
    }

    private String formatBetrag(double value) {
        return String.format("%.2f", value);
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}
