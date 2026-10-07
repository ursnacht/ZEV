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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for generating statistics PDFs using JasperReports.
 */
@Service
public class StatistikPdfService {

    private static final Logger log = LoggerFactory.getLogger(StatistikPdfService.class);

    private final TranslationRepository translationRepository;
    private final OrganizationContextService organizationContextService;

    private JasperReport compiledReport;
    private JasperReport compiledEinheitSummenReport;

    public StatistikPdfService(TranslationRepository translationRepository,
                               OrganizationContextService organizationContextService) {
        this.translationRepository = translationRepository;
        this.organizationContextService = organizationContextService;
    }

    @PostConstruct
    public void init() {
        try {
            InputStream reportStream = getClass().getResourceAsStream("/reports/statistik.jasper");
            if (reportStream == null) {
                throw new RuntimeException("Could not find statistik.jasper template");
            }
            compiledReport = (JasperReport) JRLoader.loadObject(reportStream);
            log.info("Loaded statistik.jasper template successfully");

            InputStream subreportStream = getClass().getResourceAsStream("/reports/einheit-summen.jasper");
            if (subreportStream == null) {
                throw new RuntimeException("Could not find einheit-summen.jasper template");
            }
            compiledEinheitSummenReport = (JasperReport) JRLoader.loadObject(subreportStream);
            log.info("Loaded einheit-summen.jasper template successfully");
        } catch (JRException e) {
            log.error("Failed to load JasperReports template: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to load JasperReports template", e);
        }
    }

    /**
     * Setzt die Berichte direkt — für Tests.
     *
     * <p>{@link #init()} lädt die vorkompilierten {@code .jasper}-Dateien. Die entstehen aber erst
     * in der Maven-Phase {@code prepare-package}, also <b>nach</b> den Unit-Tests: Ein Test sähe
     * dort den Stand des letzten Package-Builds, in einem frischen {@code mvn clean test} gar
     * keinen. Tests kompilieren die Templates deshalb selbst aus dem {@code .jrxml}.
     */
    void verwendeBerichte(JasperReport bericht, JasperReport einheitSummenBericht) {
        this.compiledReport = bericht;
        this.compiledEinheitSummenReport = einheitSummenBericht;
    }

    public byte[] generatePdf(StatistikDTO statistik, String sprache) {
        log.info("Generating PDF for statistik, language: {}", sprache);

        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            JasperExportManager.exportReportToPdfStream(fuelle(statistik, sprache), os);

            log.info("PDF generated successfully, size: {} bytes", os.size());
            return os.toByteArray();
        } catch (JRException e) {
            log.error("Failed to generate PDF: {}", e.getMessage(), e);
            throw new RuntimeException("PDF generation failed", e);
        }
    }

    /**
     * Füllt den Bericht, ohne ihn zu exportieren.
     *
     * <p>Getrennt vom PDF-Export, damit ein Test den gefüllten Bericht nach seinen Texten durchsuchen
     * kann — gegen das echte Template, ohne eine PDF-Bibliothek zum Auslesen.
     */
    JasperPrint fuelle(StatistikDTO statistik, String sprache) throws JRException {

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
        parameters.put("EINHEIT_SUMMEN_SUBREPORT", compiledEinheitSummenReport);
        // Anzeigename der Organisation (JWT-Claim displayName, Fallback Alias) für den Titel
        parameters.put("ORG_NAME", organizationContextService.getCurrentOrgName());
        // Verteilmodus BILANZ → Summen-Vergleich wird durch das Kennzahlen-Panel ersetzt (ausgeblendet).
        parameters.put("IST_BILANZ", statistik.getVerteilmodus() == ch.nacht.entity.Verteilmodus.BILANZ);

        // Ein Detail-Band je Eintrag: zuerst der gesamte Zeitraum, dann die Monate
        return JasperFillManager.fillReport(compiledReport, parameters,
                new JRBeanCollectionDataSource(baender(statistik)));
    }

    /**
     * Die Einträge des Detail-Bandes: das Gesamt <b>vor</b> den Monaten (Specs/Statistik.md).
     *
     * <p>Das Gesamt läuft durch <b>dasselbe</b> Band wie ein Monat — wie auf der Seite durch
     * dieselbe Vorlage. Zwei Bänder liefen beim nächsten Ausbau auseinander. Erkannt wird es im
     * Template an {@code monat == 0}.
     *
     * <p><b>Eine NEUE Liste, nicht {@code statistik.getMonate()} ergänzt.</b> Die Statistik stammt
     * aus dem Cache von {@code StatistikService.getStatistik}; das Gesamt dort einzufügen, setzte
     * es dauerhaft in den gecachten Eintrag — und die Seite zeigte es danach ein zweites Mal, als
     * vermeintlichen Monat.
     */
    static List<MonatsStatistikDTO> baender(StatistikDTO statistik) {
        List<MonatsStatistikDTO> baender = new ArrayList<>();
        if (statistik.getGesamt() != null) {
            baender.add(statistik.getGesamt());
        }
        baender.addAll(statistik.getMonate());
        return baender;
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
