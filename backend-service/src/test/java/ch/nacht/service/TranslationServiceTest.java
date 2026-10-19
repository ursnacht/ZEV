package ch.nacht.service;

import ch.nacht.entity.Translation;
import ch.nacht.repository.TranslationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TranslationServiceTest {

    @Mock
    private TranslationRepository translationRepository;

    @InjectMocks
    private TranslationService translationService;

    // ==================== createTranslation ====================

    @Test
    void createTranslation_NeuerKey_SavesSuccessfully() {
        Translation neu = new Translation("NEU_KEY", "Neu", "New");
        when(translationRepository.existsById("NEU_KEY")).thenReturn(false);
        when(translationRepository.save(neu)).thenReturn(neu);

        Translation result = translationService.createTranslation(neu);

        assertEquals("NEU_KEY", result.getKey());
        verify(translationRepository).save(neu);
    }

    /** Specs/Übersetzungsverwaltung.md, FR-3: ein bestehender Key wird nicht still überschrieben. */
    @Test
    void createTranslation_BestehenderKey_ThrowsUndSpeichertNicht() {
        Translation doppelt = new Translation("SPEICHERN", "Überschrieben", "Overwritten");
        when(translationRepository.existsById("SPEICHERN")).thenReturn(true);

        DuplicateKeyException e = assertThrows(DuplicateKeyException.class,
                () -> translationService.createTranslation(doppelt));

        assertEquals("TRANSLATION_KEY_EXISTIERT", e.getMessage());
        verify(translationRepository, never()).save(any());
    }

    // ==================== importTranslations (Specs/Übersetzungsverwaltung.md, FR-8) ====================

    private static final Translation BESTEHEND = new Translation("SPEICHERN", "Speichern neu", "Save new");
    private static final Translation NEU = new Translation("NEU_KEY", "Neu", "New");

    @Test
    void importTranslations_Ueberschreiben_SpeichertAlleEintraege() {
        when(translationRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        int anzahl = translationService.importTranslations(List.of(BESTEHEND, NEU), true);

        assertEquals(2, anzahl);
        verify(translationRepository).saveAll(List.of(BESTEHEND, NEU));
        verify(translationRepository, never()).findAll();
    }

    @Test
    void importTranslations_NichtUeberschreiben_UeberspringtBestehendeUndZaehltNurNeue() {
        when(translationRepository.findAll())
                .thenReturn(List.of(new Translation("SPEICHERN", "Speichern", "Save")));
        when(translationRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        int anzahl = translationService.importTranslations(List.of(BESTEHEND, NEU), false);

        assertEquals(1, anzahl);
        verify(translationRepository).saveAll(List.of(NEU));
    }

    /** Nicht in der Datei enthaltene Keys bleiben stehen: Der Import loescht nie. */
    @Test
    void importTranslations_LoeschtKeineKeys() {
        when(translationRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        translationService.importTranslations(List.of(NEU), true);
        translationService.importTranslations(List.of(NEU), false);

        verify(translationRepository, never()).deleteById(anyString());
        verify(translationRepository, never()).deleteAll();
        verify(translationRepository, never()).deleteAll(any());
    }

    @Test
    void importTranslations_EintragOhneKey_ThrowsUndSpeichertNichts() {
        Translation ohneKey = new Translation(" ", "x", "x");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> translationService.importTranslations(List.of(NEU, ohneKey), true));

        assertEquals("TRANSLATION_IMPORT_KEY_FEHLT", e.getMessage());
        verify(translationRepository, never()).saveAll(any());
    }

    @Test
    void importTranslations_KeyUeber200Zeichen_ThrowsUndSpeichertNichts() {
        Translation zuLang = new Translation("K".repeat(201), "x", "x");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> translationService.importTranslations(List.of(NEU, zuLang), true));

        assertEquals("TRANSLATION_IMPORT_KEY_ZU_LANG", e.getMessage());
        verify(translationRepository, never()).saveAll(any());
    }

    // ==================== deleteTranslation ====================

    @Test
    void deleteTranslation_UnbekannterKey_ReturnsFalse() {
        when(translationRepository.findById("GIBT_ES_NICHT")).thenReturn(java.util.Optional.empty());

        assertFalse(translationService.deleteTranslation("GIBT_ES_NICHT"));
        verify(translationRepository, never()).deleteById(anyString());
    }
}
