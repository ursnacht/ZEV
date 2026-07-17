package ch.nacht.controller;

import ch.nacht.service.VersionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/version")
@PreAuthorize("isAuthenticated()")
public class VersionController {

    private static final Logger log = LoggerFactory.getLogger(VersionController.class);
    private final VersionService versionService;

    public VersionController(VersionService versionService) {
        this.versionService = versionService;
        log.info("VersionController initialized");
    }

    @GetMapping
    public Map<String, String> getVersion() {
        Map<String, String> version = new HashMap<>();
        version.put("schemaVersion", versionService.getSchemaVersion());
        version.put("buildTime", versionService.getBuildTime());
        return version;
    }
}
