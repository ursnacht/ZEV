# Umsetzungsplan: SBOM

## Zusammenfassung

Implementierung einer SBOM-Generierung (CycloneDX) für Backend und Frontend sowie einer Lizenzübersichtsseite im Frontend. Beim Backend-Build erzeugt das `cyclonedx-maven-plugin` eine `backend-bom.json` als Classpath-Ressource; beim Frontend-Build generiert ein Node-Script (`generate-licenses.js`) eine `frontend-licenses.json` mit Lizenz- und Integritätsdaten aus `license-checker-rseidelsohn` und `package-lock.json`. Ein neuer `LizenzenController` mit Caffeine-Cache liefert die Backend-Lizenzdaten; die Frontend-Lizenzdaten werden direkt als statisches Asset geladen.

---

## Betroffene Komponenten

| Typ | Datei | Änderungsart |
|-----|-------|--------------|
| Build-Config Backend | `backend-service/pom.xml` | Änderung (Plugin hinzufügen) |
| Build-Config Frontend | `frontend-service/package.json` | Änderung (Script + devDependency) |
| Build-Script Frontend | `frontend-service/scripts/generate-licenses.js` | Neu |
| Backend DTO | `backend-service/src/main/java/ch/nacht/dto/LizenzenDTO.java` | Neu |
| Backend DTO | `backend-service/src/main/java/ch/nacht/dto/LizenzenHashDTO.java` | Neu |
| Backend Service | `backend-service/src/main/java/ch/nacht/service/LizenzenService.java` | Neu |
| Backend Controller | `backend-service/src/main/java/ch/nacht/controller/LizenzenController.java` | Neu |
| Backend Config | `backend-service/src/main/java/ch/nacht/config/CacheConfig.java` | Änderung (Cache „lizenzen") |
| Frontend Model | `frontend-service/src/app/models/lizenzen.model.ts` | Neu |
| Frontend Service | `frontend-service/src/app/services/lizenzen.service.ts` | Neu |
| Frontend Component | `frontend-service/src/app/components/lizenzen/lizenzen.component.ts` | Neu |
| Frontend Component | `frontend-service/src/app/components/lizenzen/lizenzen.component.html` | Neu |
| Frontend Component | `frontend-service/src/app/components/lizenzen/lizenzen.component.css` | Neu |
| Frontend Routing | `frontend-service/src/app/app.routes.ts` | Änderung |
| Frontend Navigation | `frontend-service/src/app/components/navigation/navigation.component.html` | Änderung |
| DB Migration | `backend-service/src/main/resources/db/migration/V53__Add_Lizenzen_Translations.sql` | Neu |
| Backend Unit-Test | `backend-service/src/test/java/ch/nacht/service/LizenzenServiceTest.java` | Neu |
| Backend Controller-Test | `backend-service/src/test/java/ch/nacht/controller/LizenzenControllerTest.java` | Neu |
| Test-Ressource | `backend-service/src/test/resources/test-sbom/valid-bom.json` | Neu |
| Test-Ressource | `backend-service/src/test/resources/test-sbom/empty-components-bom.json` | Neu |
| Test-Ressource | `backend-service/src/test/resources/test-sbom/no-components-bom.json` | Neu |

---

## Phasen-Tabelle

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. Maven SBOM-Plugin | `cyclonedx-maven-plugin` in `backend-service/pom.xml` konfigurieren |
| [x] | 2. npm Lizenz-Script | `generate-licenses.js` + `prebuild`-Integration + devDependency |
| [x] | 3. Backend DTOs | `LizenzenDTO` und `LizenzenHashDTO` erstellen |
| [x] | 4. Backend Service | `LizenzenService` liest und normalisiert `backend-bom.json` |
| [x] | 5. Backend Cache | Cache `lizenzen` in `CacheConfig` registrieren |
| [x] | 6. Backend Controller | `LizenzenController` mit `GET /api/lizenzen` und `@Cacheable` |
| [x] | 7. Frontend Model | TypeScript-Interfaces `Lizenz` und `LizenzHash` |
| [x] | 8. Frontend Service | `LizenzenService` für Backend-Call + Frontend-Asset-Load |
| [x] | 9. Frontend Komponente | `LizenzenComponent` mit zwei Tabellen und Suchfeldern |
| [x] | 10. Routing | Route `/lizenzen` in `app.routes.ts` |
| [x] | 11. Navigation | Menüeintrag „Lizenzen" in `navigation.component.html` |
| [x] | 12. Übersetzungen | Flyway-Migration V53 für Translation-Keys |
| [x] | 13. Backend-Tests | `LizenzenServiceTest` (21 Unit-Tests) und `LizenzenControllerTest` (7 Controller-Tests) |

---

## Detailbeschreibung der Phasen

### Phase 1: Maven SBOM-Plugin

**Datei:** `backend-service/pom.xml`

Plugin zur `<build><plugins>`-Sektion hinzufügen:

```xml
<plugin>
  <groupId>org.cyclonedx</groupId>
  <artifactId>cyclonedx-maven-plugin</artifactId>
  <version>2.9.1</version>
  <configuration>
    <projectType>library</projectType>
    <schemaVersion>1.6</schemaVersion>
    <includeBomSerialNumber>true</includeBomSerialNumber>
    <includeCompileScope>true</includeCompileScope>
    <includeProvidedScope>true</includeProvidedScope>
    <includeRuntimeScope>true</includeRuntimeScope>
    <includeSystemScope>false</includeSystemScope>
    <includeTestScope>false</includeTestScope>
    <outputFormat>json</outputFormat>
  </configuration>
  <executions>
    <execution>
      <phase>prepare-package</phase>
      <goals>
        <goal>makeBom</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

Ergebnis: `target/classes/META-INF/sbom/application.cdx.json` → Classpath-Ressource `META-INF/sbom/application.cdx.json`

> **Erkenntnisse aus der Umsetzung:**
> * `outputName` und `outputDirectory` beeinflussen nur den separaten Build-Output in `target/`; die Classpath-Ressource landet **immer** am Standard-Pfad `META-INF/sbom/application.cdx.json` (Spring-Boot-Konvention).
> * Phase muss `prepare-package` sein (nicht `package`), damit die Datei **vor** dem Spring-Boot-Repackaging in `target/classes/` liegt und korrekt in den Fat-JAR eingebettet wird.
> * Goal `makeBom` (nicht `makeAggregateBom`) verwenden, da nur der `backend-service` betrachtet wird.
> * Maven-Repository-Hashes im SBOM: nur MD5 und SHA-1 (kein SHA-256/SHA-512). Die Frontend-Hash-Prioritätsliste muss SHA-1 enthalten.

---

### Phase 2: npm Lizenz-Script

**Neue devDependency** in `frontend-service/package.json`:
```json
"license-checker-rseidelsohn": "^4.4.2"
```

**Neues Script** in `frontend-service/package.json`:
```json
"generate-licenses": "node scripts/generate-licenses.js",
"prebuild": "npm run generate-licenses"
```

**Neue Datei:** `frontend-service/scripts/generate-licenses.js`

```javascript
const licenseChecker = require('license-checker-rseidelsohn');
const fs = require('fs');
const path = require('path');

const packageLockPath = path.join(__dirname, '..', 'package-lock.json');
const outputPath = path.join(__dirname, '..', 'src', 'assets', 'frontend-licenses.json');

const packageLock = JSON.parse(fs.readFileSync(packageLockPath, 'utf8'));

// Integrity-Hash aus package-lock.json für ein Paket ermitteln
function getIntegrity(name, version) {
  const key = `node_modules/${name}`;
  const pkg = packageLock.packages?.[key];
  if (pkg?.integrity) {
    return pkg.integrity; // Format: "sha512-<base64>"
  }
  return null;
}

// Integrity-String ("sha512-<base64>") in {algorithm, value} (hex) umwandeln
function parseIntegrity(integrity) {
  if (!integrity) return null;
  const [alg, b64] = integrity.split('-');
  if (!alg || !b64) return null;
  const algorithm = alg.toUpperCase().replace('SHA', 'SHA-');
  const value = Buffer.from(b64, 'base64').toString('hex');
  return { algorithm, value };
}

licenseChecker.init(
  {
    start: path.join(__dirname, '..'),
    production: true,       // nur dependencies, keine devDependencies
    json: true,
    excludePrivatePackages: true,
  },
  (err, packages) => {
    if (err) {
      console.error('license-checker Fehler:', err);
      process.exit(1);
    }

    const result = Object.entries(packages).map(([nameVersion, info]) => {
      const atIdx = nameVersion.lastIndexOf('@');
      const name = nameVersion.substring(0, atIdx);
      const version = nameVersion.substring(atIdx + 1);

      const integrity = getIntegrity(name, version);
      const hash = parseIntegrity(integrity);

      return {
        name,
        version,
        license: Array.isArray(info.licenses) ? info.licenses.join(', ') : (info.licenses || 'Unknown'),
        publisher: info.publisher || null,
        url: info.repository || null,
        hashes: hash ? [hash] : [],
      };
    });

    // Alphabetisch sortieren
    result.sort((a, b) => a.name.localeCompare(b.name));

    fs.mkdirSync(path.dirname(outputPath), { recursive: true });
    fs.writeFileSync(outputPath, JSON.stringify(result, null, 2));
    console.log(`✓ ${result.length} Frontend-Lizenzen nach ${outputPath} geschrieben`);
  }
);
```

Ergebnis: `src/assets/frontend-licenses.json` mit Array von `{name, version, license, publisher, url, hashes}`.

---

### Phase 3: Backend DTOs

**Datei:** `backend-service/src/main/java/ch/nacht/dto/LizenzenHashDTO.java`

```java
package ch.nacht.dto;

public record LizenzenHashDTO(String algorithm, String value) {}
```

**Datei:** `backend-service/src/main/java/ch/nacht/dto/LizenzenDTO.java`

```java
package ch.nacht.dto;

import java.util.List;

public record LizenzenDTO(
    String name,
    String version,
    String license,
    String publisher,
    String url,
    List<LizenzenHashDTO> hashes
) {}
```

---

### Phase 4: Backend Service

**Datei:** `backend-service/src/main/java/ch/nacht/service/LizenzenService.java`

```java
package ch.nacht.service;

import ch.nacht.dto.LizenzenDTO;
import ch.nacht.dto.LizenzenHashDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final String BOM_PATH = "META-INF/sbom/application.cdx.json";

    private final ObjectMapper objectMapper;

    public LizenzenService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Cacheable("lizenzen")
    public List<LizenzenDTO> getBackendLizenzen() {
        log.info("Lade Backend-SBOM von Classpath: {}", BOM_PATH);
        ClassPathResource resource = new ClassPathResource(BOM_PATH);
        if (!resource.exists()) {
            log.error("backend-bom.json nicht gefunden unter: {}", BOM_PATH);
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

            // Lizenz: erstes SPDX-ID oder Name aus licenses-Array
            String license = parseLicense(comp);

            String publisher = comp.path("publisher").asText(null);
            if (publisher == null) {
                publisher = comp.path("group").asText(null);
            }

            // purl oder externalReferences für URL
            String url = parseUrl(comp);

            // Hashes: Priorität SHA-512 > SHA-256 > MD5
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
```

---

### Phase 5: Backend Cache

**Datei:** `backend-service/src/main/java/ch/nacht/config/CacheConfig.java`

Cache `lizenzen` registrieren. Da die Daten sich nur bei Neudeployment ändern, wird kein TTL-Ablauf konfiguriert (Cache läuft bis zum Neustart):

```java
@Bean
public CacheManager cacheManager() {
    CaffeineCacheManager cacheManager = new CaffeineCacheManager("statistik", "lizenzen");
    cacheManager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .maximumSize(100));
    return cacheManager;
}
```

> **Anmerkung:** Ein gemeinsamer `CacheManager` mit TTL 15 min ist ausreichend. Die Lizenzdaten ändern sich ausschliesslich bei Neudeployment, sodass das Cache-Warmup nach dem ersten Request automatisch erfolgt.

---

### Phase 6: Backend Controller

**Datei:** `backend-service/src/main/java/ch/nacht/controller/LizenzenController.java`

```java
package ch.nacht.controller;

import ch.nacht.dto.LizenzenDTO;
import ch.nacht.service.LizenzenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/lizenzen")
@PreAuthorize("hasRole('zev')")
public class LizenzenController {

    private static final Logger log = LoggerFactory.getLogger(LizenzenController.class);
    private final LizenzenService lizenzenService;

    public LizenzenController(LizenzenService lizenzenService) {
        this.lizenzenService = lizenzenService;
        log.info("LizenzenController initialized");
    }

    @GetMapping
    public ResponseEntity<?> getBackendLizenzen() {
        log.info("Lieferung Backend-Lizenzen");
        try {
            List<LizenzenDTO> lizenzen = lizenzenService.getBackendLizenzen();
            log.info("Liefere {} Backend-Lizenzen", lizenzen.size());
            return ResponseEntity.ok(lizenzen);
        } catch (IllegalStateException e) {
            log.error("SBOM nicht verfügbar: {}", e.getMessage());
            return ResponseEntity.status(503).body(e.getMessage());
        }
    }
}
```

---

### Phase 7: Frontend Model

**Datei:** `frontend-service/src/app/models/lizenzen.model.ts`

```typescript
export interface LizenzHash {
  algorithm: string;
  value: string;
}

export interface Lizenz {
  name: string;
  version: string | null;
  license: string;
  publisher: string | null;
  url: string | null;
  hashes: LizenzHash[];
}
```

---

### Phase 8: Frontend Service

**Datei:** `frontend-service/src/app/services/lizenzen.service.ts`

```typescript
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Lizenz } from '../models/lizenzen.model';

@Injectable({
  providedIn: 'root'
})
export class LizenzenService {
  private apiUrl = 'http://localhost:8090/api/lizenzen';
  private assetsUrl = 'assets/frontend-licenses.json';

  constructor(private http: HttpClient) {}

  getBackendLizenzen(): Observable<Lizenz[]> {
    return this.http.get<Lizenz[]>(this.apiUrl);
  }

  getFrontendLizenzen(): Observable<Lizenz[]> {
    return this.http.get<Lizenz[]>(this.assetsUrl);
  }
}
```

---

### Phase 9: Frontend Komponente

**Dateien:**
- `frontend-service/src/app/components/lizenzen/lizenzen.component.ts`
- `frontend-service/src/app/components/lizenzen/lizenzen.component.html`
- `frontend-service/src/app/components/lizenzen/lizenzen.component.css`

**Aufbau:**
- Standalone Component, importiert `CommonModule`, `TranslatePipe`, `IconComponent`
- Zwei Datenlisten: `backendLizenzen` / `frontendLizenzen` (jeweils alle + gefiltert)
- Zwei separate Suchfelder (`backendFilter` / `frontendFilter`), Filterung case-insensitive auf `name` und `license`
- Tabellenspalten: Name, Version, Lizenz, Hersteller, Hash
- Hash-Anzeige: `getBestHash(lizenz)` liefert stärksten Hash (SHA-512 > SHA-256 > SHA-384 > SHA-1 > MD5) oder `null`; in der Tabelle: 12-Zeichen-Kürzel + `title`-Attribut für vollständigen Wert
- Leerstate: „KEINE_ERGEBNISSE" wenn Suchergebnis leer; „KEINE_LIZENZEN" wenn Liste selbst leer
- Fehlerstate: `backendError` / `frontendError` Boolean + `.zev-message--error`-Block
- Seitentitel mit `<app-icon name="shield">` (Feather-Icon)

**Hash-Priorisierung (TypeScript-Hilfsfunktion):**

```typescript
private readonly HASH_PRIORITY = ['SHA-512', 'SHA-256', 'SHA-384', 'SHA-1', 'MD5'];

getBestHash(lizenz: Lizenz): { algorithm: string; value: string } | null {
  for (const alg of this.HASH_PRIORITY) {
    const h = lizenz.hashes.find(h => h.algorithm === alg);
    if (h) return h;
  }
  return lizenz.hashes.length > 0 ? lizenz.hashes[0] : null;
}

truncateHash(value: string): string {
  return value.substring(0, 12);
}
```

---

### Phase 10: Routing

**Datei:** `frontend-service/src/app/app.routes.ts`

Import und Route hinzufügen:

```typescript
import { LizenzenComponent } from './components/lizenzen/lizenzen.component';

// in der routes-Array:
{ path: 'lizenzen', component: LizenzenComponent, canActivate: [AuthGuard], data: { roles: ['zev', 'zev_admin'] } },
```

---

### Phase 11: Navigation

**Datei:** `frontend-service/src/app/components/navigation/navigation.component.html`

Neuer `<li>`-Eintrag, z.B. nach dem Eintrag „Einstellungen":

```html
<li>
  <a routerLink="/lizenzen" routerLinkActive="zev-navbar__link--active" class="zev-navbar__link"
    (click)="closeMenu()">
    <app-icon name="shield"></app-icon>
    {{ 'LIZENZEN' | translate }}
  </a>
</li>
```

---

### Phase 12: Übersetzungen

**Datei:** `backend-service/src/main/resources/db/migration/V53__Add_Lizenzen_Translations.sql`

```sql
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('LIZENZEN',                     'Lizenzen',                          'Licenses'),
('LIZENZEN_BACKEND',             'Backend-Libraries',                 'Backend Libraries'),
('LIZENZEN_FRONTEND',            'Frontend-Libraries',                'Frontend Libraries'),
('LIZENZ_NAME',                  'Name',                              'Name'),
('LIZENZ_VERSION',               'Version',                           'Version'),
('LIZENZ_LIZENZ',                'Lizenz',                            'License'),
('LIZENZ_HERSTELLER',            'Hersteller',                        'Publisher'),
('LIZENZ_HASH',                  'Hash',                              'Hash'),
('LIZENZ_SUCHEN',                'Suchen nach Name oder Lizenz...',   'Search by name or license...'),
('LIZENZ_UNBEKANNT',             'Unbekannt',                         'Unknown'),
('LIZENZ_KEIN_HASH',             '–',                                 '–'),
('LIZENZEN_LEER',                'Keine Libraries gefunden.',         'No libraries found.'),
('LIZENZEN_FEHLER_BACKEND',      'Backend-Lizenzen konnten nicht geladen werden.', 'Failed to load backend licenses.'),
('LIZENZEN_FEHLER_FRONTEND',     'Frontend-Lizenzen konnten nicht geladen werden.', 'Failed to load frontend licenses.')
ON CONFLICT (key) DO NOTHING;
```

---

### Phase 13: Backend-Tests

#### Anpassung LizenzenService für Testbarkeit

`static final`-Konstante auf ein injizierbares Instanzfeld umgestellt; package-privater Konstruktor für Tests ergänzt:

```java
static final String DEFAULT_BOM_PATH = "META-INF/sbom/application.cdx.json";

private final ObjectMapper objectMapper;
private final String bomPath;

@Autowired                          // explizit: Spring nutzt diesen Konstruktor
public LizenzenService(ObjectMapper objectMapper) {
    this(objectMapper, DEFAULT_BOM_PATH);
}

// Package-private: nur für Tests mit abweichendem BOM-Pfad
LizenzenService(ObjectMapper objectMapper, String bomPath) {
    this.objectMapper = objectMapper;
    this.bomPath = bomPath;
}
```

> **Erkenntnis:** Bei mehreren Konstruktoren sucht Spring ohne `@Autowired` nach einem No-Arg-Konstruktor → `BeanInstantiationException`. `@Autowired` am öffentlichen Konstruktor macht die Wahl eindeutig.

#### Test-Ressourcen

Drei minimale CycloneDX-JSON-Dateien in `src/test/resources/test-sbom/`:

| Datei | Zweck |
|-------|-------|
| `valid-bom.json` | 6 Komponenten mit allen Variationen (SPDX-ID, Lizenz-Name, kein Publisher, group-Fallback, website-/vcs-Ref, mehrere Hashes, leere Hashes, fehlende Hashes, Komponente ohne Name) |
| `empty-components-bom.json` | `"components": []` – Leerlist-Test |
| `no-components-bom.json` | Kein `components`-Feld – fehlende-Node-Test |

> Bewusst anderer Pfad als `META-INF/sbom/` gewählt, um die Produktions-SBOM nicht zu überschatten.

#### LizenzenServiceTest (21 Unit-Tests, kein Spring-Kontext)

| Kategorie | Tests |
|-----------|-------|
| Happy Path | Nicht-leere Liste, Komponente ohne Name wird übersprungen, alphabetische Sortierung |
| Lizenz-Parsing | SPDX-ID bevorzugt, Lizenz-Name als Fallback, kein Eintrag → `"Unknown"` |
| Publisher-Parsing | `publisher`-Feld, `group`-Fallback, keines → `null` |
| URL-Parsing | `website`-Ref, `vcs`-Ref, nur `other`-Ref → `null`, kein Ref → `null` |
| Hash-Parsing | Anzahl, MD5-Wert, SHA-1-Wert, leeres Array, fehlendes Feld → leer |
| BOM-Struktur | Leeres Array → leer, fehlendes Feld → leer |
| Fehlerbehandlung | Datei nicht gefunden → `IllegalStateException("SBOM-Datei nicht verfügbar")` |

#### LizenzenControllerTest (7 Controller-Tests, `@WebMvcTest`)

| Test | Erwartung |
|------|-----------|
| `getLizenzen_ReturnsListOfComponents` | HTTP 200, Array-Grösse korrekt |
| `getLizenzen_ReturnsCorrectFields` | Alle DTO-Felder (`name`, `version`, `license`, `publisher`, `url`) in JSON |
| `getLizenzen_ReturnsHashesInResponse` | Hash-Array mit `algorithm` + `value` |
| `getLizenzen_EmptyList_ReturnsEmptyArray` | HTTP 200, leeres JSON-Array |
| `getLizenzen_SbomUnavailable_Returns503` | `IllegalStateException` → HTTP 503 |
| `getLizenzen_SbomUnreadable_Returns503` | `IllegalStateException` (andere Meldung) → HTTP 503 |
| `getLizenzen_ComponentWithNullPublisher_SerializesNull` | `publisher`-Feld nicht im JSON vorhanden |

---

## Validierungen

### Backend-Validierungen

1. **SBOM-Verfügbarkeit:** `LizenzenService` prüft mit `ClassPathResource.exists()` – bei fehlendem File HTTP 503.
2. **JSON-Parsing:** Fehlende/unbekannte Felder in der CycloneDX-JSON werden tolerant behandelt (`asText(null)`, `isMissingNode()`).
3. **Lizenz leer:** Falls kein Lizenz-Eintrag vorhanden → Fallback `"Unknown"`.

### Frontend-Validierungen

1. **HTTP-Fehler Backend-Endpoint:** `subscribe({ error })` → `backendError = true` + Fehlermeldung.
2. **HTTP-Fehler Frontend-Asset:** `subscribe({ error })` → `frontendError = true` + Fehlermeldung.
3. **Leere Suchergebnisse:** Gefiltertes Array leer → Leerstate-Meldung (kein weiteres Fehler-Handling).
4. **Hash fehlt:** `getBestHash()` gibt `null` zurück → Template zeigt `'LIZENZ_KEIN_HASH' | translate` (`–`).

---

## Offene Punkte / Annahmen

1. **Bestätigt:** `cyclonedx-maven-plugin` 2.9.1 generiert 230 Backend-Komponenten mit MD5- und SHA-1-Hashes (Maven Central liefert keine SHA-256/SHA-512).
2. **Bestätigt:** `outputDirectory`/`outputName` beeinflussen nur den separaten Build-Output in `target/`; der Classpath-Pfad ist immer `META-INF/sbom/application.cdx.json` (Spring-Boot-Standard). Die Parameter wurden aus der Plugin-Konfiguration entfernt.
3. **Bestätigt:** Goal `makeBom` (nicht `makeAggregateBom`) ist korrekt für Single-Module-Builds.
4. **Bestätigt:** Phase `prepare-package` ist zwingend notwendig – bei `package` besteht das Risiko, dass Spring Boot das JAR vor dem CycloneDX-Output repackaged. Mit `prepare-package` ist die Reihenfolge sicher.
5. **Bestätigt:** `license-checker-rseidelsohn` 4.x ist kompatibel mit Node 20; generiert 19 Frontend-Einträge mit SHA-512-Hashes aus `package-lock.json`.
6. **Offen:** Die `frontend-licenses.json` ist generiert, aber nicht in `.gitignore` eingetragen. Empfehlung: Eintrag in `frontend-service/.gitignore` für `src/assets/frontend-licenses.json` hinzufügen, da die Datei bei jedem Build neu erstellt wird.
7. **Bestätigt:** `hasRole('zev')` deckt auch `zev_admin` ab (wie im restlichen Projekt).
8. **Bestätigt:** Der gemeinsame `CacheManager` mit 15-min-TTL für `"statistik"` und `"lizenzen"` funktioniert korrekt.
9. **Bestätigt:** Bei mehreren Konstruktoren in einer `@Service`-Klasse ist `@Autowired` am öffentlichen Konstruktor zwingend, sonst `BeanInstantiationException` zur Laufzeit.
10. **Bestätigt:** Test-BOM-Ressourcen unter `test-sbom/` (statt `META-INF/sbom/`) ablegen, damit die Produktions-SBOM im Classpath nicht überschattet wird.

## Build-Anleitung

Damit die SBOM im JAR und Docker-Image verfügbar ist, muss vor `docker-compose up --build` immer zuerst der Maven-Build laufen:

```bash
cd backend-service
mvn package -DskipTests
cd ..
docker-compose up --build
```

Der `cyclonedx-maven-plugin` läuft in der `prepare-package`-Phase und bettet `META-INF/sbom/application.cdx.json` in `target/classes/` ein, bevor Spring Boot das Fat-JAR zusammenstellt.
