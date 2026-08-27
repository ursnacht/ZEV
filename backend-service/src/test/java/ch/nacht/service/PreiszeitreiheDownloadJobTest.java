package ch.nacht.service;

import ch.nacht.dto.PreiszeitreiheDownloadDTO;
import ch.nacht.entity.FeatureFlag;
import ch.nacht.exception.PreiszeitreiheQuelleException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit-Tests für {@link PreiszeitreiheDownloadJob}.
 *
 * <p>Zwei Zusicherungen, die im Betrieb zählen: Eine Installation, die das Feature nicht nutzt, ruft
 * <b>keine Fremd-API</b> auf; und ein Fehlschlag der Quelle reisst den geplanten Lauf nicht mit —
 * die Meldung an den Benutzer hat der Abruf-Service schon erfasst.
 */
@ExtendWith(MockitoExtension.class)
public class PreiszeitreiheDownloadJobTest {

    @Mock
    private PreiszeitreiheAbrufService abrufService;

    @Mock
    private FeatureFlagService featureFlagService;

    @InjectMocks
    private PreiszeitreiheDownloadJob job;

    @Test
    void hole_KeinMandantMitFlag_RuftDieQuelleNichtAuf() {
        when(featureFlagService.getOrgIdsMitAktivemFlag(FeatureFlag.PREISZEITREIHE))
                .thenReturn(List.of());

        job.hole();

        verifyNoInteractions(abrufService);
    }

    @Test
    void hole_MandantMitFlag_Ruft() {
        when(featureFlagService.getOrgIdsMitAktivemFlag(FeatureFlag.PREISZEITREIHE))
                .thenReturn(List.of(1L));
        when(abrufService.abrufen()).thenReturn(new PreiszeitreiheDownloadDTO(96, 96, 0, 0, null));

        job.hole();

        verify(abrufService).abrufen();
    }

    @Test
    void hole_QuelleVersagt_LaeuftDurchOhneAusnahme() {
        when(featureFlagService.getOrgIdsMitAktivemFlag(FeatureFlag.PREISZEITREIHE))
                .thenReturn(List.of(1L));
        when(abrufService.abrufen())
                .thenThrow(new PreiszeitreiheQuelleException("Quelle nicht erreichbar"));

        assertDoesNotThrow(() -> job.hole());
    }

    @Test
    void hole_UnerwarteteAusnahme_LaeuftDurchOhneAusnahme() {
        when(featureFlagService.getOrgIdsMitAktivemFlag(FeatureFlag.PREISZEITREIHE))
                .thenReturn(List.of(1L));
        when(abrufService.abrufen()).thenThrow(new IllegalStateException("unerwartet"));

        assertDoesNotThrow(() -> job.hole());
        verify(abrufService).abrufen();
    }

    @Test
    void hole_KeinMandantMitFlag_FragtNurDieFlagMenge() {
        when(featureFlagService.getOrgIdsMitAktivemFlag(FeatureFlag.PREISZEITREIHE))
                .thenReturn(List.of());

        job.hole();

        verify(featureFlagService).getOrgIdsMitAktivemFlag(FeatureFlag.PREISZEITREIHE);
        verify(abrufService, never()).abrufen();
    }
}
