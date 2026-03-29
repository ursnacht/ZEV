package ch.nacht.service;

import ch.nacht.dto.LizenzenDTO;
import ch.nacht.dto.LizenzenHashDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class LizenzenService {

    private static final Logger log = LoggerFactory.getLogger(LizenzenService.class);
    static final String DEFAULT_BOM_PATH = "META-INF/sbom/application.cdx.json";

    private final ObjectMapper objectMapper;
    private final String bomPath;

    @Autowired
    public LizenzenService(ObjectMapper objectMapper) {
        this(objectMapper, DEFAULT_BOM_PATH);
    }

    // Package-private constructor for testing with a custom BOM path
    LizenzenService(ObjectMapper objectMapper, String bomPath) {
        this.objectMapper = objectMapper;
        this.bomPath = bomPath;
    }

    @Cacheable("lizenzen")
    public List<LizenzenDTO> getBackendLizenzen() {
        log.info("Lade Backend-SBOM von Classpath: {}", bomPath);
        ClassPathResource resource = new ClassPathResource(bomPath);
        if (!resource.exists()) {
            log.error("backend-bom.json nicht gefunden unter: {}", bomPath);
            throw new IllegalStateException("SBOM-Datei nicht verfügbar");
        }
        try (InputStream is = resource.getInputStream()) {
            JsonNode bom = objectMapper.readTree(is);
            return parseComponents(bom);
        } catch (IOException e) {
            log.error("Fehler beim Lesen der SBOM", e);
            throw new IllegalStateException("SBOM konnte nicht gelesen werden", e);
        }
    }

    private List<LizenzenDTO> parseComponents(JsonNode bom) {
        List<LizenzenDTO> result = new ArrayList<>();
        JsonNode components = bom.path("components");
        if (components.isMissingNode() || !components.isArray()) {
            return result;
        }
        for (JsonNode comp : components) {
            String name = comp.path("name").asText(null);
            String version = comp.path("version").asText(null);
            String license = parseLicense(comp);

            String publisher = comp.path("publisher").asText(null);
            if (publisher == null) {
                publisher = comp.path("group").asText(null);
            }

            String url = parseUrl(comp);
            List<LizenzenHashDTO> hashes = parseHashes(comp);

            if (name != null) {
                result.add(new LizenzenDTO(name, version, license, publisher, url, hashes));
            }
        }
        result.sort(Comparator.comparing(LizenzenDTO::name, String.CASE_INSENSITIVE_ORDER));
        log.info("SBOM geparst: {} Komponenten", result.size());
        return result;
    }

    private String parseLicense(JsonNode comp) {
        JsonNode licenses = comp.path("licenses");
        if (licenses.isArray() && licenses.size() > 0) {
            JsonNode first = licenses.get(0).path("license");
            String id = first.path("id").asText(null);
            if (id != null) return id;
            return first.path("name").asText("Unknown");
        }
        return "Unknown";
    }

    private String parseUrl(JsonNode comp) {
        JsonNode refs = comp.path("externalReferences");
        if (refs.isArray()) {
            for (JsonNode ref : refs) {
                String type = ref.path("type").asText("");
                if ("website".equals(type) || "vcs".equals(type)) {
                    return ref.path("url").asText(null);
                }
            }
        }
        return null;
    }

    private List<LizenzenHashDTO> parseHashes(JsonNode comp) {
        List<LizenzenHashDTO> hashes = new ArrayList<>();
        JsonNode hashArray = comp.path("hashes");
        if (hashArray.isArray()) {
            for (JsonNode h : hashArray) {
                String alg = h.path("alg").asText(null);
                String content = h.path("content").asText(null);
                if (alg != null && content != null) {
                    hashes.add(new LizenzenHashDTO(alg, content));
                }
            }
        }
        return hashes;
    }
}
