package ch.nacht.service;

import ch.nacht.controller.MesspunktEmpfangController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Profile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit-Test für {@link MesspunktEmpfangCleanupJob} (Specs/Messpunkt-Empfang.md, FR-3.2): Der Job
 * delegiert an den Service. Die Regel selbst prüfen {@code MesspunktEmpfangServiceTest} (Grenze)
 * und {@code MesspunktEmpfangRepositoryIT} (Abfrage).
 */
@ExtendWith(MockitoExtension.class)
public class MesspunktEmpfangCleanupJobTest {

    @Mock
    private MesspunktEmpfangService messpunktEmpfangService;

    @InjectMocks
    private MesspunktEmpfangCleanupJob job;

    @Test
    void bereinige_RuftRaeumeAuf() {
        when(messpunktEmpfangService.raeumeAuf()).thenReturn(2);

        job.bereinige();

        verify(messpunktEmpfangService).raeumeAuf();
        verifyNoMoreInteractions(messpunktEmpfangService);
    }

    /**
     * Job, Service und Controller laufen ohne MQTT-Profil (API-Endpunkte, „Profile“): Die Ansicht
     * ist dann erreichbar, und alte Zeilen werden weiter aufgeräumt. Nur der Ingest hängt am Profil.
     */
    @Test
    void komponenten_OhneMqttProfil() {
        assertFalse(MesspunktEmpfangCleanupJob.class.isAnnotationPresent(Profile.class));
        assertFalse(MesspunktEmpfangService.class.isAnnotationPresent(Profile.class));
        assertFalse(MesspunktEmpfangController.class.isAnnotationPresent(Profile.class));
        // Gegenstueck: der Ingest schon
        assertTrue(MqttIngestService.class.isAnnotationPresent(Profile.class));
    }
}
