package ch.nacht.service;

import ch.nacht.entity.MeldungLevel;
import ch.nacht.entity.Systemmeldung;
import ch.nacht.repository.SystemmeldungRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SystemmeldungServiceTest {

    @Mock
    private SystemmeldungRepository systemmeldungRepository;

    @Mock
    private OrganizationContextService organizationContextService;

    @Mock
    private HibernateFilterService hibernateFilterService;

    @InjectMocks
    private SystemmeldungService systemmeldungService;

    // --- erfasseAudit: ein Eintrag pro Vorgang, direkt erledigt (keine Dedup) ---

    @Test
    void erfasseAudit_SpeichertNeuenEintragDirektAlsErledigt() {
        systemmeldungService.erfasseAudit(42L, MeldungLevel.INFO,
                SystemmeldungService.KATEGORIE_VERTEILUNG,
                SystemmeldungService.KEY_VERTEILUNG_MANUELL, "testuser, 01.01.2024–31.01.2024, EQUAL_SHARE");

        ArgumentCaptor<Systemmeldung> captor = ArgumentCaptor.forClass(Systemmeldung.class);
        verify(systemmeldungRepository).saveAndFlush(captor.capture());

        Systemmeldung gespeichert = captor.getValue();
        assertEquals(42L, gespeichert.getOrgId());
        assertEquals(MeldungLevel.INFO, gespeichert.getLevel());
        assertEquals(SystemmeldungService.KATEGORIE_VERTEILUNG, gespeichert.getKategorie());
        assertEquals(SystemmeldungService.KEY_VERTEILUNG_MANUELL, gespeichert.getMeldungKey());
        assertEquals("testuser, 01.01.2024–31.01.2024, EQUAL_SHARE", gespeichert.getParameter());
        assertTrue(gespeichert.isErledigt());
        assertTrue(gespeichert.isErledigtAutomatisch());
        assertNotNull(gespeichert.getErledigtAm());
        assertEquals(1, gespeichert.getZaehler());
    }

    @Test
    void erfasseAudit_KeineDeduplizierung_LiestKeinenOffenenEintrag() {
        systemmeldungService.erfasseAudit(42L, MeldungLevel.INFO,
                SystemmeldungService.KATEGORIE_VERTEILUNG,
                SystemmeldungService.KEY_VERTEILUNG_MANUELL, "testuser");

        // Audit-Einträge werden nie zusammengefasst – kein Dedup-Lookup, kein Increment.
        verify(systemmeldungRepository, never())
                .findByOrgIdAndMeldungKeyAndErledigtFalse(anyLong(), anyString());
        verify(systemmeldungRepository, never()).save(any());
    }

    @Test
    void erfasseAudit_UeberlangerParameter_WirdAuf500ZeichenGekuerzt() {
        String zuLang = "x".repeat(600);

        systemmeldungService.erfasseAudit(42L, MeldungLevel.INFO,
                SystemmeldungService.KATEGORIE_VERTEILUNG,
                SystemmeldungService.KEY_VERTEILUNG_MANUELL, zuLang);

        ArgumentCaptor<Systemmeldung> captor = ArgumentCaptor.forClass(Systemmeldung.class);
        verify(systemmeldungRepository).saveAndFlush(captor.capture());
        assertEquals(500, captor.getValue().getParameter().length());
    }

    // --- erfasse: Dedup-Verhalten (Abgrenzung zu erfasseAudit) ---

    @Test
    void erfasse_OffenerEintragVorhanden_ErhoehtZaehlerStattNeuemEintrag() {
        Systemmeldung offen = new Systemmeldung(MeldungLevel.WARN, SystemmeldungService.KATEGORIE_MQTT,
                SystemmeldungService.KEY_ZAEHLER_AUSFALL, "alt",
                java.time.LocalDateTime.of(2024, 1, 1, 0, 0), java.time.LocalDateTime.of(2024, 1, 1, 0, 0));
        offen.setOrgId(42L);
        when(systemmeldungRepository.findByOrgIdAndMeldungKeyAndErledigtFalse(
                42L, SystemmeldungService.KEY_ZAEHLER_AUSFALL)).thenReturn(Optional.of(offen));

        systemmeldungService.erfasse(42L, MeldungLevel.WARN, SystemmeldungService.KATEGORIE_MQTT,
                SystemmeldungService.KEY_ZAEHLER_AUSFALL, "neu");

        verify(systemmeldungRepository, never()).saveAndFlush(any());
        verify(systemmeldungRepository).save(offen);
        assertEquals(2, offen.getZaehler());
        assertEquals("neu", offen.getParameter());
    }
}
