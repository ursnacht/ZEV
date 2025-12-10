package ch.nacht.service;

import ch.nacht.dto.MonatsStatistikDTO;
import ch.nacht.dto.StatistikDTO;
import ch.nacht.dto.TagMitAbweichungDTO;
import ch.nacht.entity.Translation;
import ch.nacht.repository.TranslationRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class StatistikPdfService {

    private static final Logger log = LoggerFactory.getLogger(StatistikPdfService.class);
    private final TranslationRepository translationRepository;

    public StatistikPdfService(TranslationRepository translationRepository) {
        this.translationRepository = translationRepository;
    }

    public byte[] generatePdf(StatistikDTO statistik, String sprache) {
        log.info("Generating PDF for statistik, language: {}", sprache);

        Map<String, String> translations = loadTranslations(sprache);
        String html = buildHtml(statistik, translations, sprache);

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();

            log.info("PDF generated successfully, size: {} bytes", os.size());
            return os.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate PDF: {}", e.getMessage(), e);
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

    private String buildHtml(StatistikDTO statistik, Map<String, String> translations, String sprache) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>");
        html.append("<html><head><meta charset=\"UTF-8\"/>");
        html.append("<style>");
        html.append("body { font-family: Arial, sans-serif; font-size: 10pt; margin: 20px; }");
        html.append("h1 { color: #4CAF50; font-size: 18pt; margin-bottom: 5px; }");
        html.append("h2 { color: #333; font-size: 14pt; margin-top: 20px; margin-bottom: 10px; border-bottom: 2px solid #4CAF50; padding-bottom: 5px; }");
        html.append("h3 { color: #666; font-size: 12pt; margin-top: 15px; margin-bottom: 8px; }");
        html.append(".header { margin-bottom: 20px; }");
        html.append(".subtitle { color: #666; font-size: 10pt; }");
        html.append(".info-row { margin: 5px 0; }");
        html.append(".label { font-weight: bold; display: inline-block; width: 180px; }");
        html.append(".status-ok { color: #4CAF50; }");
        html.append(".status-error { color: #f44336; }");
        html.append("table { width: 100%; border-collapse: collapse; margin: 10px 0; }");
        html.append("th, td { border: 1px solid #ddd; padding: 6px 8px; text-align: left; }");
        html.append("th { background-color: #f5f5f5; font-weight: bold; }");
        html.append(".number { text-align: right; }");
        html.append(".comparison { margin: 10px 0; padding: 10px; background: #f9f9f9; }");
        html.append(".comparison-item { margin: 5px 0; }");
        html.append(".footer { margin-top: 30px; padding-top: 10px; border-top: 1px solid #ddd; font-size: 9pt; color: #666; }");
        html.append(".page-break { page-break-before: always; }");
        html.append("</style>");
        html.append("</head><body>");

        // Header
        html.append("<div class=\"header\">");
        html.append("<h1>").append(t(translations, "STATISTIK_REPORT")).append("</h1>");
        html.append("<div class=\"subtitle\">").append(t(translations, "ZEITRAUM")).append(": ");
        if (!statistik.getMonate().isEmpty()) {
            MonatsStatistikDTO ersterMonat = statistik.getMonate().get(0);
            MonatsStatistikDTO letzterMonat = statistik.getMonate().get(statistik.getMonate().size() - 1);
            html.append(ersterMonat.getVon()).append(" - ").append(letzterMonat.getBis());
        }
        html.append("</div>");
        html.append("</div>");

        // Overview
        html.append("<h2>").append(t(translations, "STATISTIK_UEBERSICHT")).append("</h2>");
        html.append("<div class=\"info-row\">");
        html.append("<span class=\"label\">").append(t(translations, "MESSWERTE_VORHANDEN_BIS")).append(":</span>");
        html.append("<span>").append(statistik.getMesswerteBisDate() != null ? statistik.getMesswerteBisDate().toString() : "-").append("</span>");
        html.append("</div>");

        html.append("<div class=\"info-row\">");
        html.append("<span class=\"label\">").append(t(translations, "DATENSTATUS")).append(":</span>");
        String statusClass = statistik.isDatenVollstaendig() ? "status-ok" : "status-error";
        String statusText = statistik.isDatenVollstaendig() ? t(translations, "DATEN_VOLLSTAENDIG") : t(translations, "DATEN_UNVOLLSTAENDIG");
        html.append("<span class=\"").append(statusClass).append("\">").append(statusText).append("</span>");
        html.append("</div>");

        if (!statistik.getFehlendeEinheiten().isEmpty()) {
            html.append("<div class=\"info-row\">");
            html.append("<span class=\"label\">").append(t(translations, "FEHLENDE_EINHEITEN")).append(":</span>");
            html.append("<span>").append(String.join(", ", statistik.getFehlendeEinheiten())).append("</span>");
            html.append("</div>");
        }

        // Monthly statistics
        for (int i = 0; i < statistik.getMonate().size(); i++) {
            MonatsStatistikDTO monat = statistik.getMonate().get(i);

            if (i > 0 && i % 2 == 0) {
                html.append("<div class=\"page-break\"></div>");
            }

            String monthName = getMonthName(monat.getMonat(), sprache);
            html.append("<h2>").append(monthName).append(" ").append(monat.getJahr());
            html.append(" <span style=\"font-size: 10pt; font-weight: normal;\">(").append(monat.getVon()).append(" - ").append(monat.getBis()).append(")</span>");
            html.append("</h2>");

            // Status
            String monatStatusClass = monat.isDatenVollstaendig() ? "status-ok" : "status-error";
            String monatStatusText = monat.isDatenVollstaendig() ? t(translations, "DATEN_VOLLSTAENDIG") : t(translations, "DATEN_UNVOLLSTAENDIG");
            html.append("<div class=\"info-row\">");
            html.append("<span class=\"label\">").append(t(translations, "DATENSTATUS")).append(":</span>");
            html.append("<span class=\"").append(monatStatusClass).append("\">").append(monatStatusText).append("</span>");
            html.append("</div>");

            // Sums table
            html.append("<h3>").append(t(translations, "WERT")).append("</h3>");
            html.append("<table>");
            html.append("<tr><th>").append(t(translations, "BESCHREIBUNG")).append("</th><th class=\"number\">kWh</th></tr>");
            html.append("<tr><td>").append(t(translations, "PRODUKTION_TOTAL")).append("</td><td class=\"number\">").append(formatNumber(monat.getSummeProducerTotal())).append("</td></tr>");
            html.append("<tr><td>").append(t(translations, "VERBRAUCH_TOTAL")).append("</td><td class=\"number\">").append(formatNumber(monat.getSummeConsumerTotal())).append("</td></tr>");
            html.append("<tr><td>").append(t(translations, "ZEV_PRODUCER")).append(" (A)</td><td class=\"number\">").append(formatNumber(monat.getSummeProducerZev())).append("</td></tr>");
            html.append("<tr><td>").append(t(translations, "ZEV_CONSUMER")).append(" (B)</td><td class=\"number\">").append(formatNumber(monat.getSummeConsumerZev())).append("</td></tr>");
            html.append("<tr><td>").append(t(translations, "ZEV_CONSUMER_BERECHNET")).append(" (C)</td><td class=\"number\">").append(formatNumber(monat.getSummeConsumerZevCalculated())).append("</td></tr>");
            html.append("</table>");

            // Comparisons
            html.append("<h3>").append(t(translations, "SUMMEN_VERGLEICH")).append("</h3>");
            html.append("<div style=\"font-size: 9pt; color: #666; margin-bottom: 5px;\">")
                .append(t(translations, "TOLERANZ")).append(": ").append(formatNumber(statistik.getToleranz())).append(" kWh</div>");
            html.append("<div class=\"comparison\">");
            html.append(buildComparisonRow("A = B", monat.isSummenCDGleich(), monat.getDifferenzCD(), translations));
            html.append(buildComparisonRow("A = C", monat.isSummenCEGleich(), monat.getDifferenzCE(), translations));
            html.append(buildComparisonRow("B = C", monat.isSummenDEGleich(), monat.getDifferenzDE(), translations));
            html.append("</div>");

            // Days with deviations
            if (!monat.getTageAbweichungen().isEmpty()) {
                html.append("<h3>").append(t(translations, "TAGE_MIT_ABWEICHUNGEN")).append("</h3>");
                html.append("<table>");
                html.append("<tr><th>").append(t(translations, "DATUM")).append("</th><th>").append(t(translations, "ABWEICHUNG")).append("</th><th class=\"number\">").append(t(translations, "DIFFERENZ")).append(" (kWh)</th></tr>");
                for (TagMitAbweichungDTO abweichung : monat.getTageAbweichungen()) {
                    html.append("<tr>");
                    html.append("<td>").append(abweichung.getDatum()).append("</td>");
                    html.append("<td>").append(abweichung.getAbweichungstyp()).append("</td>");
                    html.append("<td class=\"number\">").append(formatNumber(abweichung.getDifferenz())).append("</td>");
                    html.append("</tr>");
                }
                html.append("</table>");
            }
        }

        // Footer
        html.append("<div class=\"footer\">");
        html.append(t(translations, "GENERIERT_AM")).append(": ");
        html.append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
        html.append("</div>");

        html.append("</body></html>");

        return html.toString();
    }

    private String buildComparisonRow(String label, boolean isEqual, Double differenz, Map<String, String> translations) {
        StringBuilder row = new StringBuilder();
        row.append("<div class=\"comparison-item\">");
        row.append("<span>").append(label).append(": </span>");
        if (isEqual) {
            row.append("<span class=\"status-ok\">&#10003; ").append(t(translations, "GLEICH")).append("</span>");
        } else {
            row.append("<span class=\"status-error\">&#10007; ").append(t(translations, "UNGLEICH"));
            row.append(" (").append(t(translations, "DIFFERENZ")).append(": ").append(formatDifferenz(differenz)).append(" kWh)");
            row.append("</span>");
        }
        row.append("</div>");
        return row.toString();
    }

    private String formatNumber(Double value) {
        if (value == null) return "-";
        return String.format("%.3f", value);
    }

    private String formatDifferenz(Double value) {
        if (value == null) return "-";
        String prefix = value >= 0 ? "+" : "";
        return prefix + String.format("%.3f", value);
    }

    private String getMonthName(int month, String sprache) {
        String[] months = {"JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
                "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER"};
        Optional<Translation> translation = this.translationRepository.findById(months[month-1]);
        if (translation.isPresent()) {
            Translation t = translation.get();
            if ("en".equalsIgnoreCase(sprache)) {
                return t.getEnglisch();
            } else if ("de".equalsIgnoreCase(sprache)) {
                return t.getDeutsch();
            }
            log.warn("Invalid language to get translation of month, language: {}", sprache);
        }
        return months[month-1];
    }
}
