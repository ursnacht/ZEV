package ch.nacht.service;

import ch.nacht.dto.MonatsStatistikDTO;
import ch.nacht.dto.StatistikDTO;
import ch.nacht.entity.Translation;
import ch.nacht.repository.TranslationRepository;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.util.JRLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for generating statistics PDFs using JasperReports.
 */
@Service
public class StatistikPdfService {

    private static final Logger log = LoggerFactory.getLogger(StatistikPdfService.class);

    private final TranslationRepository translationRepository;

    private JasperReport compiledReport;

    public StatistikPdfService(TranslationRepository translationRepository) {
        this.translationRepository = translationRepository;
    }

    @PostConstruct
    public void init() {
        try {
            // InputStream reportStream = getClass().getResourceAsStream("/reports/statistik.jrxml");
            InputStream reportStream = getClass().getResourceAsStream("/reports/statistik.jasper");
            if (reportStream == null) {
                throw new RuntimeException("Could not find statistik.jasper template");
            }
            compiledReport = (JasperReport) JRLoader.loadObject(reportStream);
            // compiledReport = JasperCompileManager.compileReport(reportStream);
            log.info("Compiled statistik.jasper template successfully");
        } catch (JRException e) {
            log.error("Failed to compile JasperReports template: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to compile JasperReports template", e);
        }
    }

    public byte[] generatePdf(StatistikDTO statistik, String sprache) {
        log.info("Generating PDF for statistik, language: {}", sprache);

        Map<String, String> translations = loadTranslations(sprache);

        // Build time range string
        String zeitraum = "";
        if (!statistik.getMonate().isEmpty()) {
            MonatsStatistikDTO ersterMonat = statistik.getMonate().get(0);
            MonatsStatistikDTO letzterMonat = statistik.getMonate().get(statistik.getMonate().size() - 1);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            zeitraum = ersterMonat.getVon().format(formatter) + " - " + letzterMonat.getBis().format(formatter);
        }

        String generiertAm = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("STATISTIK", statistik);
        parameters.put("TRANSLATIONS", translations);
        parameters.put("SPRACHE", sprache);
        parameters.put("ZEITRAUM", zeitraum);
        parameters.put("GENERIERT_AM", generiertAm);

        // Monate als DataSource für das Detail-Band
        JRBeanCollectionDataSource monateDataSource =
            new JRBeanCollectionDataSource(statistik.getMonate());

        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            JasperPrint jasperPrint = JasperFillManager.fillReport(
                compiledReport, parameters, monateDataSource);
            JasperExportManager.exportReportToPdfStream(jasperPrint, os);

            log.info("PDF generated successfully, size: {} bytes", os.size());
            return os.toByteArray();
        } catch (JRException e) {
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
}
