package ch.nacht.service;

import ch.nacht.dto.RechnungDTO;
import ch.nacht.dto.TarifZeileDTO;
import ch.nacht.entity.Translation;
import ch.nacht.repository.TranslationRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import net.codecrete.qrbill.generator.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
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
        html.append("@page qr-page { size: A4; margin: 5mm; }");
        html.append("body { font-family: Arial, sans-serif; font-size: 10pt; margin: 0; padding: 0; }");
        html.append("h1 { color: #0099cc; font-size: 18pt; margin-top: 30px; margin-bottom: 5px; }");
        html.append("h2 { color: #0099cc; font-size: 12pt; margin-top: 15px; margin-bottom: 8px; }");
        html.append(".header-section { margin-bottom: 30px; }");
        html.append(".header-row { margin: 3px 0; font-size: 9pt; }");
        html.append(".header-label { display: inline-block; width: 120px; }");
        html.append(".header-value { font-weight: normal; }");
        html.append(".address-block { float: right; width: 200px; text-align: left; font-size: 9pt; margin-top: 50px; line-height: 1.4; }");
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
        html.append(".qr-section { page-break-before: always; page: qr-page; }");
        html.append(".clearfix::after { content: ''; display: table; clear: both; }");

        // Swiss QR-Bill styles (using tables for OpenHTMLToPDF compatibility)
        html.append(".qr-bill-table { width: 210mm; border-collapse: collapse; border-top: 1px dashed #000; font-family: Arial, Helvetica, sans-serif; border-bottom: none; }");
        html.append(".qr-bill-table td { vertical-align: top; padding: 5mm; border-bottom: none; }");
        html.append(".qr-bill-table .empfangsschein { width: 62mm; border-right: 1px dashed #000; }");
        html.append(".qr-bill-table .zahlteil { width: 148mm; }");
        html.append(".qr-bill-table .title { font-size: 11pt; font-weight: bold; margin-bottom: 3mm; }");
        html.append(".qr-bill-table .heading { font-size: 6pt; font-weight: bold; margin-bottom: 1mm; margin-top: 3mm; }");
        html.append(".qr-bill-table .value { font-size: 8pt; line-height: 1.4; }");
        html.append(".qr-bill-table .value-small { font-size: 7pt; line-height: 1.3; }");
        html.append(".qr-bill-table .amount-table { margin-top: 8mm; border: none; }");
        html.append(".qr-bill-table .amount-table td { padding: 0 2mm 0 0; border: none; }");
        html.append(".qr-bill-table .amount-heading { font-size: 6pt; font-weight: bold; }");
        html.append(".qr-bill-table .amount-value { font-size: 8pt; }");
        html.append(".qr-bill-table .acceptance { font-size: 6pt; font-weight: bold; margin-top: 10mm; }");
        html.append(".zahlteil-inner { width: 100%; border-collapse: collapse; border-bottom: none; }");
        html.append(".zahlteil-inner td { vertical-align: top; padding: 0; border-bottom: none; }");
        html.append(".zahlteil-inner .qr-amount-cell { width: 56mm; }");
        html.append(".zahlteil-inner .details-cell { padding-left: 5mm; }");
        html.append(".zahlteil-inner .qr-code-img { width: 46mm; height: 46mm; }");
        html.append(".zahlteil-amount-table { margin-top: 3mm; border: none; }");
        html.append(".zahlteil-amount-table td { padding-right: 4mm; border: none; }");
        html.append(".zahlteil-inner .amount-heading { font-size: 8pt; font-weight: bold; }");
        html.append(".zahlteil-inner .amount-value { font-size: 10pt; }");
        html.append(".zahlteil-inner .heading { font-size: 8pt; font-weight: bold; margin-bottom: 1mm; margin-top: 3mm; }");
        html.append(".zahlteil-inner .heading:first-child { margin-top: 0; }");
        html.append(".zahlteil-inner .value { font-size: 10pt; line-height: 1.4; }");
        html.append(".zahlteil-inner .value-small { font-size: 8pt; line-height: 1.3; }");
        html.append("</style>");
        html.append("</head><body>");

        // Header with invoice info and address
        html.append("<div class=\"header-section clearfix\">");

        // Right-aligned address block
        html.append("<div class=\"address-block\">");
        if (rechnung.getMieterName() != null && !rechnung.getMieterName().isEmpty()) {
            html.append(escapeHtml(rechnung.getMieterName())).append("<br/>");
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

        // Tariff line rows (dynamically from list)
        for (TarifZeileDTO zeile : rechnung.getTarifZeilen()) {
            html.append("<tr>");
            // Show date range in designation if multiple tariffs exist per type
            String bezeichnung = zeile.getBezeichnung();
            if (hasMehrereTarifeProTyp(rechnung)) {
                bezeichnung += " (" + zeile.getVon().format(DATE_FORMATTER) + " - " + zeile.getBis().format(DATE_FORMATTER) + ")";
            }
            html.append("<td>").append(escapeHtml(bezeichnung)).append("</td>");
            html.append("<td class=\"number\">").append(formatMenge(zeile.getMenge())).append("</td>");
            html.append("<td class=\"number\">").append(formatPreis(zeile.getPreis())).append("</td>");
            html.append("<td class=\"unit\">CHF / kWh</td>");
            html.append("<td class=\"number\">").append(formatBetrag(zeile.getBetrag())).append("</td>");
            html.append("</tr>");
        }

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
        html.append(buildQrBillHtml(rechnung, translations));
        html.append("</div>");

        html.append("</body></html>");

        return html.toString();
    }

    /**
     * Build Swiss QR-Bill HTML with selectable text.
     * Layout according to SIX Swiss QR-Bill standard.
     */
    private String buildQrBillHtml(RechnungDTO rechnung, Map<String, String> translations) {
        StringBuilder html = new StringBuilder();
        String qrCodePng = generateQrCodePng(rechnung);

        String formattedIban = formatIban(rechnung.getIban());
        String formattedAmount = formatBetragQrBill(rechnung.getEndBetrag());
        String message = "Stromrechnung " + rechnung.getEinheitName();

        // Creditor info
        String creditorName = escapeHtml(rechnung.getStellerName());
        String creditorStreet = escapeHtml(rechnung.getStellerStrasse());
        String creditorPlzOrt = escapeHtml(rechnung.getStellerPlzOrt());

        // Debtor info
        String debtorName = rechnung.getMieterName() != null ? escapeHtml(rechnung.getMieterName()) : "";
        String debtorStreet = escapeHtml(rechnung.getAdresseStrasse());
        String debtorPlzOrt = escapeHtml(rechnung.getAdressePlzOrt());
        boolean hasDebtor = rechnung.getMieterName() != null && !rechnung.getMieterName().isEmpty();

        // Main table: Empfangsschein | Zahlteil
        html.append("<table class=\"qr-bill-table\"><tr>");

        // === EMPFANGSSCHEIN (Receipt, left column) ===
        html.append("<td class=\"empfangsschein\">");
        html.append("<div class=\"title\">").append(t(translations, "QR_EMPFANGSSCHEIN")).append("</div>");

        // Konto / Zahlbar an
        html.append("<div class=\"heading\">").append(t(translations, "QR_KONTO_ZAHLBAR_AN")).append("</div>");
        html.append("<div class=\"value\">").append(formattedIban).append("</div>");
        html.append("<div class=\"value\">").append(creditorName).append("</div>");
        html.append("<div class=\"value-small\">").append(creditorStreet).append("</div>");
        html.append("<div class=\"value-small\">").append(creditorPlzOrt).append("</div>");

        // Zahlbar durch
        html.append("<div class=\"heading\">").append(t(translations, "QR_ZAHLBAR_DURCH")).append("</div>");
        if (hasDebtor) {
            html.append("<div class=\"value\">").append(debtorName).append("</div>");
            html.append("<div class=\"value-small\">").append(debtorStreet).append("</div>");
            html.append("<div class=\"value-small\">").append(debtorPlzOrt).append("</div>");
        }

        // Währung und Betrag
        html.append("<table class=\"amount-table\"><tr>");
        html.append("<td><div class=\"amount-heading\">").append(t(translations, "QR_WAEHRUNG")).append("</div>");
        html.append("<div class=\"amount-value\">CHF</div></td>");
        html.append("<td><div class=\"amount-heading\">").append(t(translations, "QR_BETRAG")).append("</div>");
        html.append("<div class=\"amount-value\">").append(formattedAmount).append("</div></td>");
        html.append("</tr></table>");

        // Annahmestelle
        html.append("<div class=\"acceptance\">").append(t(translations, "QR_ANNAHMESTELLE")).append("</div>");
        html.append("</td>");

        // === ZAHLTEIL (Payment part, right column) ===
        html.append("<td class=\"zahlteil\">");
        html.append("<div class=\"title\">").append(t(translations, "QR_ZAHLTEIL")).append("</div>");

        // Inner table: Left (QR + Amount) | Right (Details)
        html.append("<table class=\"zahlteil-inner\"><tr>");

        // Left cell: QR-Code + Amount below
        html.append("<td class=\"qr-amount-cell\">");
        if (qrCodePng != null) {
            html.append("<img class=\"qr-code-img\" src=\"data:image/png;base64,").append(qrCodePng).append("\"/>");
        }
        // Währung und Betrag unterhalb des QR-Codes
        html.append("<table class=\"zahlteil-amount-table\"><tr>");
        html.append("<td><div class=\"amount-heading\">").append(t(translations, "QR_WAEHRUNG")).append("</div>");
        html.append("<div class=\"amount-value\">CHF</div></td>");
        html.append("<td><div class=\"amount-heading\">").append(t(translations, "QR_BETRAG")).append("</div>");
        html.append("<div class=\"amount-value\">").append(formattedAmount).append("</div></td>");
        html.append("</tr></table>");
        html.append("</td>");

        // Right cell: Details
        html.append("<td class=\"details-cell\">");

        // Konto / Zahlbar an
        html.append("<div class=\"heading\">").append(t(translations, "QR_KONTO_ZAHLBAR_AN")).append("</div>");
        html.append("<div class=\"value\">").append(formattedIban).append("</div>");
        html.append("<div class=\"value\">").append(creditorName).append("</div>");
        html.append("<div class=\"value-small\">").append(creditorStreet).append("</div>");
        html.append("<div class=\"value-small\">").append(creditorPlzOrt).append("</div>");

        // Zusätzliche Informationen
        html.append("<div class=\"heading\">").append(t(translations, "QR_ZUSAETZLICHE_INFOS")).append("</div>");
        html.append("<div class=\"value\">").append(escapeHtml(message)).append("</div>");

        // Zahlbar durch
        html.append("<div class=\"heading\">").append(t(translations, "QR_ZAHLBAR_DURCH")).append("</div>");
        if (hasDebtor) {
            html.append("<div class=\"value\">").append(debtorName).append("</div>");
            html.append("<div class=\"value-small\">").append(debtorStreet).append("</div>");
            html.append("<div class=\"value-small\">").append(debtorPlzOrt).append("</div>");
        }

        html.append("</td>");
        html.append("</tr></table>"); // zahlteil-inner

        html.append("</td>"); // zahlteil
        html.append("</tr></table>"); // qr-bill-table

        return html.toString();
    }

    /**
     * Generate only the QR-Code as PNG (base64 encoded).
     * The surrounding text is now rendered as HTML for better selectability.
     */
    private String generateQrCodePng(RechnungDTO rechnung) {
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
            if (rechnung.getMieterName() != null && !rechnung.getMieterName().isEmpty()) {
                Address debtor = new Address();
                debtor.setName(rechnung.getMieterName());
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

            // Generate only the QR-Code as PNG (not the full payment slip)
            bill.setFormat(new BillFormat());
            bill.getFormat().setOutputSize(OutputSize.QR_CODE_ONLY);
            bill.getFormat().setGraphicsFormat(GraphicsFormat.PNG);
            bill.getFormat().setLanguage(Language.DE);

            byte[] png = QRBill.generate(bill);
            return Base64.getEncoder().encodeToString(png);

        } catch (Exception e) {
            log.error("Failed to generate QR Code: {}", e.getMessage(), e);
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

    /**
     * Format IBAN in groups of 4 characters: CH12 3456 7890 1234 5678 9
     */
    private String formatIban(String iban) {
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
    private String formatBetragQrBill(double value) {
        DecimalFormat df = new DecimalFormat("#,##0.00");
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator('.');
        df.setDecimalFormatSymbols(symbols);
        return df.format(value);
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    /**
     * Check if invoice has multiple tariffs per type.
     * If true, we show date ranges in the tariff line descriptions.
     */
    private boolean hasMehrereTarifeProTyp(RechnungDTO rechnung) {
        // Count tariffs by type - if more than 2 total (1 ZEV + 1 VNB), we have splits
        return rechnung.getTarifZeilen().size() > 2;
    }
}
