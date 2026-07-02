package ch.nacht.entity;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-Tests für das {@link FeatureFlag}-Enum (Registry aller bekannten Feature-Flags).
 */
public class FeatureFlagTest {

    @Test
    void messwerteUpload_HasExpectedDefaultAndBeschreibungKey() {
        assertTrue(FeatureFlag.MESSWERTE_UPLOAD.isDefaultEnabled());
        assertEquals("FEATURE_FLAG_MESSWERTE_UPLOAD", FeatureFlag.MESSWERTE_UPLOAD.getBeschreibungKey());
    }

    @Test
    void fromKey_KnownKey_ReturnsFlag() {
        Optional<FeatureFlag> result = FeatureFlag.fromKey("MESSWERTE_UPLOAD");

        assertTrue(result.isPresent());
        assertEquals(FeatureFlag.MESSWERTE_UPLOAD, result.get());
    }

    @Test
    void fromKey_UnknownKey_ReturnsEmpty() {
        Optional<FeatureFlag> result = FeatureFlag.fromKey("DOES_NOT_EXIST");

        assertTrue(result.isEmpty());
    }

    @Test
    void fromKey_Null_ReturnsEmpty() {
        Optional<FeatureFlag> result = FeatureFlag.fromKey(null);

        assertTrue(result.isEmpty());
    }

    @Test
    void fromKey_IsCaseSensitive_ReturnsEmptyForWrongCase() {
        Optional<FeatureFlag> result = FeatureFlag.fromKey("messwerte_upload");

        assertTrue(result.isEmpty());
    }
}
