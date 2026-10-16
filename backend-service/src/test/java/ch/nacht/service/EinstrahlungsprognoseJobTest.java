package ch.nacht.service;

import ch.nacht.entity.FeatureFlag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit-Tests für {@link EinstrahlungsprognoseJob} (Specs/Ladeplanung.md, FR-2).
 *
 * <p>Zwei Zusicherungen, die im Betrieb zählen: Ein Mandant ohne Freigabe löst <b>keinen Aufruf
 * der Fremd-API</b> aus — wichtig auch für die Lizenz von Open-Meteo (nicht-kommerziell, NFR-3);
 * und ein Fehler bei einem Mandanten nimmt die übrigen nicht mit.
 */
@ExtendWith(MockitoExtension.class)
public class EinstrahlungsprognoseJobTest {

    @Mock
    private EinstrahlungsprognoseAbrufService abrufService;

    @Mock
    private FeatureFlagService featureFlagService;

    @InjectMocks
    private EinstrahlungsprognoseJob job;

    @Test
    void hole_KeinMandantMitFlag_RuftDieApiNichtAuf() {
        when(featureFlagService.getOrgIdsMitAktivemFlag(FeatureFlag.EINSPEISESTEUERUNG))
                .thenReturn(List.of());

        job.hole();

        verifyNoInteractions(abrufService);
    }

    /** Abgerufen wird je Mandant mit aktivem Flag - und nur für diese. */
    @Test
    void hole_ZweiMandantenMitFlag_RuftJedenAb() {
        when(featureFlagService.getOrgIdsMitAktivemFlag(FeatureFlag.EINSPEISESTEUERUNG))
                .thenReturn(List.of(1L, 2L));

        job.hole();

        verify(abrufService).abrufen(1L);
        verify(abrufService).abrufen(2L);
    }

    /**
     * Ein Fehler beim ersten Mandanten verhindert den Abruf für den zweiten nicht, und der Job
     * selbst wirft nicht - sonst fiele der nächste geplante Lauf aus.
     */
    @Test
    void hole_FehlerBeiEinemMandanten_UebrigeLaufenWeiter() {
        when(featureFlagService.getOrgIdsMitAktivemFlag(FeatureFlag.EINSPEISESTEUERUNG))
                .thenReturn(List.of(1L, 2L));
        when(abrufService.abrufen(1L)).thenThrow(new IllegalStateException("Open-Meteo nicht erreichbar"));

        assertDoesNotThrow(() -> job.hole());

        verify(abrufService).abrufen(2L);
    }
}
