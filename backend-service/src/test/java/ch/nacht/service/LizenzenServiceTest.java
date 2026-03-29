package ch.nacht.service;

import ch.nacht.dto.LizenzenDTO;
import ch.nacht.dto.LizenzenHashDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class LizenzenServiceTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    // ==================== getBackendLizenzen – Happy Path ====================

    @Test
    void getBackendLizenzen_ValidBom_ReturnsNonEmptyList() {
        LizenzenService service = new LizenzenService(objectMapper, "test-sbom/valid-bom.json");

        List<LizenzenDTO> result = service.getBackendLizenzen();

        assertFalse(result.isEmpty());
    }

    @Test
    void getBackendLizenzen_ValidBom_SkipsComponentWithoutName() {
        LizenzenService service = new LizenzenService(objectMapper, "test-sbom/valid-bom.json");

        List<LizenzenDTO> result = service.getBackendLizenzen();

        // The test BOM has one component with only "version" (no "name") → must be skipped
        assertTrue(result.stream().noneMatch(l -> l.name() == null));
    }

    @Test
    void getBackendLizenzen_ValidBom_ResultIsSortedAlphabetically() {
        LizenzenService service = new LizenzenService(objectMapper, "test-sbom/valid-bom.json");

        List<LizenzenDTO> result = service.getBackendLizenzen();

        for (int i = 0; i < result.size() - 1; i++) {
            String a = result.get(i).name().toLowerCase();
            String b = result.get(i + 1).name().toLowerCase();
            assertTrue(a.compareTo(b) <= 0,
                "Erwartete alphabetische Sortierung, aber '%s' kommt vor '%s'".formatted(a, b));
        }
    }

    // ==================== Lizenz-Parsing ====================

    @Test
    void getBackendLizenzen_ComponentWithSpdxId_UsesSpdxId() {
        LizenzenService service = new LizenzenService(objectMapper, "test-sbom/valid-bom.json");

        List<LizenzenDTO> result = service.getBackendLizenzen();

        LizenzenDTO springCore = findByName(result, "spring-core");
        assertEquals("Apache-2.0", springCore.license());
    }

    @Test
    void getBackendLizenzen_ComponentWithLicenseName_UsesName() {
        LizenzenService service = new LizenzenService(objectMapper, "test-sbom/valid-bom.json");

        List<LizenzenDTO> result = service.getBackendLizenzen();

        LizenzenDTO jackson = findByName(result, "jackson-core");
        assertEquals("Apache License 2.0", jackson.license());
    }

    @Test
    void getBackendLizenzen_ComponentWithNoLicense_ReturnsUnknown() {
        LizenzenService service = new LizenzenService(objectMapper, "test-sbom/valid-bom.json");

        List<LizenzenDTO> result = service.getBackendLizenzen();

        LizenzenDTO commonsIo = findByName(result, "commons-io");
        assertEquals("Unknown", commonsIo.license());
    }

    // ==================== Publisher-Parsing ====================

    @Test
    void getBackendLizenzen_ComponentWithPublisher_UsesPublisher() {
        LizenzenService service = new LizenzenService(objectMapper, "test-sbom/valid-bom.json");

        List<LizenzenDTO> result = service.getBackendLizenzen();

        LizenzenDTO springCore = findByName(result, "spring-core");
        assertEquals("Pivotal", springCore.publisher());
    }

    @Test
    void getBackendLizenzen_ComponentWithGroupFallback_UsesGroup() {
        LizenzenService service = new LizenzenService(objectMapper, "test-sbom/valid-bom.json");

        List<LizenzenDTO> result = service.getBackendLizenzen();

        LizenzenDTO jackson = findByName(result, "jackson-core");
        assertEquals("com.fasterxml.jackson", jackson.publisher());
    }

    @Test
    void getBackendLizenzen_ComponentWithNoPublisher_ReturnsNull() {
        LizenzenService service = new LizenzenService(objectMapper, "test-sbom/valid-bom.json");

        List<LizenzenDTO> result = service.getBackendLizenzen();

        LizenzenDTO commonsIo = findByName(result, "commons-io");
        assertNull(commonsIo.publisher());
    }

    // ==================== URL-Parsing ====================

    @Test
    void getBackendLizenzen_ComponentWithWebsiteRef_ParsesUrl() {
        LizenzenService service = new LizenzenService(objectMapper, "test-sbom/valid-bom.json");

        List<LizenzenDTO> result = service.getBackendLizenzen();

        LizenzenDTO springCore = findByName(result, "spring-core");
        assertEquals("https://spring.io", springCore.url());
    }

    @Test
    void getBackendLizenzen_ComponentWithVcsRef_ParsesUrl() {
        LizenzenService service = new LizenzenService(objectMapper, "test-sbom/valid-bom.json");

        List<LizenzenDTO> result = service.getBackendLizenzen();

        LizenzenDTO jackson = findByName(result, "jackson-core");
        assertEquals("https://github.com/FasterXML/jackson-core", jackson.url());
    }

    @Test
    void getBackendLizenzen_ComponentWithOnlyOtherRef_UrlIsNull() {
        LizenzenService service = new LizenzenService(objectMapper, "test-sbom/valid-bom.json");

        List<LizenzenDTO> result = service.getBackendLizenzen();

        LizenzenDTO zebra = findByName(result, "zebra-lib");
        assertNull(zebra.url());
    }

    @Test
    void getBackendLizenzen_ComponentWithNoRefs_UrlIsNull() {
        LizenzenService service = new LizenzenService(objectMapper, "test-sbom/valid-bom.json");

        List<LizenzenDTO> result = service.getBackendLizenzen();

        LizenzenDTO commonsIo = findByName(result, "commons-io");
        assertNull(commonsIo.url());
    }

    // ==================== Hash-Parsing ====================

    @Test
    void getBackendLizenzen_ComponentWithHashes_ParsesAllHashes() {
        LizenzenService service = new LizenzenService(objectMapper, "test-sbom/valid-bom.json");

        List<LizenzenDTO> result = service.getBackendLizenzen();

        LizenzenDTO springCore = findByName(result, "spring-core");
        assertEquals(2, springCore.hashes().size());
    }

    @Test
    void getBackendLizenzen_ComponentWithHashes_ContainsMd5() {
        LizenzenService service = new LizenzenService(objectMapper, "test-sbom/valid-bom.json");

        List<LizenzenDTO> result = service.getBackendLizenzen();

        LizenzenDTO springCore = findByName(result, "spring-core");
        LizenzenHashDTO md5 = findHash(springCore.hashes(), "MD5");
        assertNotNull(md5);
        assertEquals("aabbccddeeff0011", md5.value());
    }

    @Test
    void getBackendLizenzen_ComponentWithHashes_ContainsSha1() {
        LizenzenService service = new LizenzenService(objectMapper, "test-sbom/valid-bom.json");

        List<LizenzenDTO> result = service.getBackendLizenzen();

        LizenzenDTO springCore = findByName(result, "spring-core");
        LizenzenHashDTO sha1 = findHash(springCore.hashes(), "SHA-1");
        assertNotNull(sha1);
        assertEquals("112233445566778899aabb", sha1.value());
    }

    @Test
    void getBackendLizenzen_ComponentWithEmptyHashes_ReturnsEmptyList() {
        LizenzenService service = new LizenzenService(objectMapper, "test-sbom/valid-bom.json");

        List<LizenzenDTO> result = service.getBackendLizenzen();

        LizenzenDTO zebra = findByName(result, "zebra-lib");
        assertTrue(zebra.hashes().isEmpty());
    }

    @Test
    void getBackendLizenzen_ComponentWithNoHashesField_ReturnsEmptyList() {
        LizenzenService service = new LizenzenService(objectMapper, "test-sbom/valid-bom.json");

        List<LizenzenDTO> result = service.getBackendLizenzen();

        LizenzenDTO commonsIo = findByName(result, "commons-io");
        assertTrue(commonsIo.hashes().isEmpty());
    }

    // ==================== Edge Cases – BOM-Struktur ====================

    @Test
    void getBackendLizenzen_EmptyComponentsArray_ReturnsEmptyList() {
        LizenzenService service = new LizenzenService(objectMapper, "test-sbom/empty-components-bom.json");

        List<LizenzenDTO> result = service.getBackendLizenzen();

        assertTrue(result.isEmpty());
    }

    @Test
    void getBackendLizenzen_NoComponentsField_ReturnsEmptyList() {
        LizenzenService service = new LizenzenService(objectMapper, "test-sbom/no-components-bom.json");

        List<LizenzenDTO> result = service.getBackendLizenzen();

        assertTrue(result.isEmpty());
    }

    // ==================== Fehlerbehandlung ====================

    @Test
    void getBackendLizenzen_FileNotFound_ThrowsIllegalStateException() {
        LizenzenService service = new LizenzenService(objectMapper, "test-sbom/does-not-exist.json");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            service::getBackendLizenzen);
        assertEquals("SBOM-Datei nicht verfügbar", ex.getMessage());
    }

    // ==================== Hilfsmethoden ====================

    private LizenzenDTO findByName(List<LizenzenDTO> list, String name) {
        return list.stream()
            .filter(l -> name.equals(l.name()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Komponente nicht gefunden: " + name));
    }

    private LizenzenHashDTO findHash(List<LizenzenHashDTO> hashes, String algorithm) {
        return hashes.stream()
            .filter(h -> algorithm.equals(h.algorithm()))
            .findFirst()
            .orElse(null);
    }
}
