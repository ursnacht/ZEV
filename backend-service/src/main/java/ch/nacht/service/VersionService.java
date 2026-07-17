package ch.nacht.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Liefert die höchste erfolgreich ausgeführte Flyway-Schema-Version (Anzeige auf der Startseite).
 *
 * <p>Bewusste Abweichung vom Repository-Pattern: {@code flyway_schema_history} ist eine
 * Flyway-interne Tabelle (keine JPA-Entity), Zugriff read-only via {@link JdbcTemplate}.
 * Kein {@code orgFilter} nötig – die Schema-Version gilt installationsweit, nicht je Mandant.
 */
@Service
public class VersionService {

    private static final Logger log = LoggerFactory.getLogger(VersionService.class);
    private static final DateTimeFormatter BUILD_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectProvider<BuildProperties> buildProperties;

    public VersionService(JdbcTemplate jdbcTemplate, ObjectProvider<BuildProperties> buildProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.buildProperties = buildProperties;
    }

    /**
     * Höchste erfolgreich angewendete Migrations-Version (z.B. "81");
     * {@code null}, wenn keine Version ermittelt werden kann.
     */
    @Transactional(readOnly = true)
    public String getSchemaVersion() {
        try {
            List<String> versions = jdbcTemplate.queryForList(
                    "SELECT version FROM zev.flyway_schema_history "
                            + "WHERE success AND version IS NOT NULL "
                            + "ORDER BY installed_rank DESC LIMIT 1",
                    String.class);
            return versions.isEmpty() ? null : versions.get(0);
        } catch (Exception e) {
            log.warn("Schema-Version konnte nicht ermittelt werden: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Build-Zeitpunkt des Backends (Schweizer Format, lokale Zeitzone) aus der vom
     * spring-boot-maven-plugin erzeugten {@code build-info.properties}. {@code null}, wenn
     * das Jar nicht über das Plugin gebaut wurde (z.B. IDE-Start) – die Anzeige entfällt dann.
     */
    public String getBuildTime() {
        BuildProperties props = buildProperties.getIfAvailable();
        if (props == null || props.getTime() == null) {
            return null;
        }
        return props.getTime().atZone(ZoneId.systemDefault()).format(BUILD_FORMAT);
    }
}
