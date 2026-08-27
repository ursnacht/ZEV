package ch.nacht.service;

import ch.nacht.dto.PreiszeitreiheDownloadDTO;
import ch.nacht.dto.PreiszeitreihePunktDTO;
import ch.nacht.entity.FeatureFlag;
import ch.nacht.entity.Preiszeitreihe;
import ch.nacht.exception.FeatureDisabledException;
import ch.nacht.repository.PreiszeitreiheRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit-Tests für {@link PreiszeitreiheService} — die Maske-zugewandte Seite.
 *
 * <p><b>Abweichung von der Service-Test-Vorlage:</b> Kein {@code HibernateFilterService}. Die
 * Preiszeitreihe trägt bewusst kein {@code org_id} (Specs/Preiszeitreihe.md, FR-2), es gibt also
 * nichts zu filtern. Geprüft wird stattdessen, dass <b>jede</b> öffentliche Methode das
 * Feature-Flag prüft — ohne das wäre der Flag reine Kosmetik.
 */
@ExtendWith(MockitoExtension.class)
public class PreiszeitreiheServiceTest {

    private static final Long ORG_ID = 1L;
    private static final LocalDate VON = LocalDate.of(2026, 1, 15);
    private static final LocalDate BIS = LocalDate.of(2026, 1, 15);

    @Mock
    private PreiszeitreiheRepository preiszeitreiheRepository;

    @Mock
    private PreiszeitreiheAbrufService abrufService;

    @Mock
    private FeatureFlagService featureFlagService;

    @Mock
    private OrganizationContextService organizationContextService;

    @InjectMocks
    private PreiszeitreiheService preiszeitreiheService;

    @Captor
    private ArgumentCaptor<LocalDateTime> vonCaptor;

    @Captor
    private ArgumentCaptor<LocalDateTime> bisCaptor;

    private Preiszeitreihe wert1;
    private Preiszeitreihe wert2;

    @BeforeEach
    void setUp() {
        wert1 = new Preiszeitreihe(LocalDateTime.of(2026, 1, 15, 10, 0),
                LocalDateTime.of(2026, 1, 15, 10, 15), new BigDecimal("0.13800"),
                LocalDateTime.of(2026, 1, 14, 13, 50));
        wert2 = new Preiszeitreihe(LocalDateTime.of(2026, 1, 15, 10, 15),
                LocalDateTime.of(2026, 1, 15, 10, 30), new BigDecimal("0.14200"),
                LocalDateTime.of(2026, 1, 14, 13, 50));
    }

    private void flagAktiv() {
        when(organizationContextService.getCurrentOrgId()).thenReturn(ORG_ID);
        when(featureFlagService.isEnabled(ORG_ID, FeatureFlag.PREISZEITREIHE)).thenReturn(true);
    }

    private void flagAus() {
        when(organizationContextService.getCurrentOrgId()).thenReturn(ORG_ID);
        when(featureFlagService.isEnabled(ORG_ID, FeatureFlag.PREISZEITREIHE)).thenReturn(false);
    }

    // ==================== getPunkte ====================

    @Test
    void getPunkte_MitWerten_LiefertPunkteInOrtszeit() {
        flagAktiv();
        when(preiszeitreiheRepository.findByZeitraum(any(), any())).thenReturn(List.of(wert1, wert2));

        List<PreiszeitreihePunktDTO> punkte = preiszeitreiheService.getPunkte(VON, BIS);

        assertEquals(2, punkte.size());
        // Januar: UTC+1 — 10:00 UTC wird 11:00 Ortszeit.
        assertEquals(LocalDateTime.of(2026, 1, 15, 11, 0), punkte.get(0).zeit());
        assertEquals(new BigDecimal("0.13800"), punkte.get(0).preis());
        assertEquals(LocalDateTime.of(2026, 1, 15, 11, 15), punkte.get(1).zeit());
    }

    @Test
    void getPunkte_RechnetTagesgrenzenNachUtc() {
        flagAktiv();
        when(preiszeitreiheRepository.findByZeitraum(any(), any())).thenReturn(List.of());

        preiszeitreiheService.getPunkte(VON, BIS);

        verify(preiszeitreiheRepository).findByZeitraum(vonCaptor.capture(), bisCaptor.capture());
        // 15.01. 00:00 Ortszeit = 14.01. 23:00 UTC, obere Grenze ausschliessend = 15.01. 23:00 UTC.
        assertEquals(LocalDateTime.of(2026, 1, 14, 23, 0), vonCaptor.getValue());
        assertEquals(LocalDateTime.of(2026, 1, 15, 23, 0), bisCaptor.getValue());
    }

    @Test
    void getPunkte_OhneWerte_LiefertLeereListe() {
        flagAktiv();
        when(preiszeitreiheRepository.findByZeitraum(any(), any())).thenReturn(List.of());

        assertTrue(preiszeitreiheService.getPunkte(VON, BIS).isEmpty());
    }

    @Test
    void getPunkte_FlagAus_ThrowsFeatureDisabledException() {
        flagAus();

        assertThrows(FeatureDisabledException.class,
                () -> preiszeitreiheService.getPunkte(VON, BIS));
        verifyNoInteractions(preiszeitreiheRepository);
    }

    @Test
    void getPunkte_VonNull_ThrowsIllegalArgumentException() {
        flagAktiv();

        assertThrows(IllegalArgumentException.class,
                () -> preiszeitreiheService.getPunkte(null, BIS));
        verify(preiszeitreiheRepository, never()).findByZeitraum(any(), any());
    }

    @Test
    void getPunkte_BisNull_ThrowsIllegalArgumentException() {
        flagAktiv();

        assertThrows(IllegalArgumentException.class,
                () -> preiszeitreiheService.getPunkte(VON, null));
    }

    @Test
    void getPunkte_VonNachBis_ThrowsIllegalArgumentException() {
        flagAktiv();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> preiszeitreiheService.getPunkte(BIS.plusDays(1), BIS));
        assertTrue(ex.getMessage().contains("Datum von"));
        verify(preiszeitreiheRepository, never()).findByZeitraum(any(), any());
    }

    @Test
    void getPunkte_SpanneGenau366Tage_IstErlaubt() {
        flagAktiv();
        when(preiszeitreiheRepository.findByZeitraum(any(), any())).thenReturn(List.of());

        preiszeitreiheService.getPunkte(VON, VON.plusDays(366));

        verify(preiszeitreiheRepository).findByZeitraum(any(), any());
    }

    @Test
    void getPunkte_SpanneUeber366Tage_ThrowsIllegalArgumentException() {
        flagAktiv();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> preiszeitreiheService.getPunkte(VON, VON.plusDays(367)));
        assertTrue(ex.getMessage().contains("366"));
        verify(preiszeitreiheRepository, never()).findByZeitraum(any(), any());
    }

    // ==================== download ====================

    @Test
    void download_FlagAktiv_DelegiertAnAbrufService() {
        flagAktiv();
        PreiszeitreiheDownloadDTO erwartet =
                new PreiszeitreiheDownloadDTO(96, 96, 0, 0, LocalDateTime.of(2026, 1, 14, 14, 50));
        when(abrufService.abrufen()).thenReturn(erwartet);

        PreiszeitreiheDownloadDTO ergebnis = preiszeitreiheService.download();

        assertEquals(erwartet, ergebnis);
        verify(abrufService).abrufen();
    }

    @Test
    void download_FlagAus_ThrowsFeatureDisabledExceptionOhneAbruf() {
        flagAus();

        assertThrows(FeatureDisabledException.class, () -> preiszeitreiheService.download());
        // Entscheidend: Ohne aktives Flag darf die Fremd-API gar nicht erst angefasst werden.
        verifyNoInteractions(abrufService);
    }
}
