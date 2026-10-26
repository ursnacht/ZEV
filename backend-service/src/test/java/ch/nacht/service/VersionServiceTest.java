package ch.nacht.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.info.BuildProperties;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit-Tests für {@link VersionService} – die Angaben der Versionszeile auf der Startseite
 * (Specs/Startseite.md, FR-4).
 */
@ExtendWith(MockitoExtension.class)
public class VersionServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ObjectProvider<BuildProperties> buildProperties;

    private VersionService versionService;

    @BeforeEach
    void setUp() {
        versionService = new VersionService(jdbcTemplate, buildProperties);
    }

    @Test
    void getSchemaVersion_HoechsteVersion_ReturnsVersion() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(List.of("188"));

        assertEquals("188", versionService.getSchemaVersion());
    }

    @Test
    void getSchemaVersion_DatenbankWirft_ReturnsNull() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenThrow(new RuntimeException("weg"));

        assertNull(versionService.getSchemaVersion());
    }

    @Test
    void getBuildTime_OhneBuildInfo_ReturnsNull() {
        when(buildProperties.getIfAvailable()).thenReturn(null);

        assertNull(versionService.getBuildTime());
    }

    @Test
    void getBuildTime_MitBuildInfo_SchweizerFormatInOrtszeit() {
        Properties props = new Properties();
        props.setProperty("time", Instant.parse("2026-10-26T09:09:00Z").toString());
        when(buildProperties.getIfAvailable()).thenReturn(new BuildProperties(props));

        assertEquals("26.10.2026 10:09", versionService.getBuildTime());
    }

    /** Specs/Startseite.md, FR-4.3: die Version des laufenden Spring Boot, ohne Konfiguration. */
    @Test
    void getSpringBootVersion_ReturnsLaufendeVersion() {
        String version = versionService.getSpringBootVersion();

        assertEquals(SpringBootVersion.getVersion(), version);
        assertTrue(version.matches("[0-9]+[.][0-9]+[.][0-9]+.*"), "Format x.y.z, war: " + version);
    }
}
