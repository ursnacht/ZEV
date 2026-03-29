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

---

## Phasen-Tabelle

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [ ] | 1. Maven SBOM-Plugin | `cyclonedx-maven-plugin` in `backend-service/pom.xml` konfigurieren |
| [ ] | 2. npm Lizenz-Script | `generate-licenses.js` + `prebuild`-Integration + devDependency |
| [ ] | 3. Backend DTOs | `LizenzenDTO` und `LizenzenHashDTO` erstellen |
| [ ] | 4. Backend Service | `LizenzenService` liest und normalisiert `backend-bom.json` |
| [ ] | 5. Backend Cache | Cache `lizenzen` in `CacheConfig` registrieren |
| [ ] | 6. Backend Controller | `LizenzenController` mit `GET /api/lizenzen` und `@Cacheable` |
| [ ] | 7. Frontend Model | TypeScript-Interfaces `Lizenz` und `LizenzHash` |
| [ ] | 8. Frontend Service | `LizenzenService` für Backend-Call + Frontend-Asset-Load |
| [ ] | 9. Frontend Komponente | `LizenzenComponent` mit zwei Tabellen und Suchfeldern |
| [ ] | 10. Routing | Route `/lizenzen` in `app.routes.ts` |
| [ ] | 11. Navigation | Menüeintrag „Lizenzen" in `navigation.component.html` |
| [ ] | 12. Übersetzungen | Flyway-Migration V53 für Translation-Keys |

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
    <outputName>backend-bom</outputName>
    <outputFormat>json</outputFormat>
    <outputDirectory>${project.build.outputDirectory}/sbom</outputDirectory>
  </configuration>
  <executions>
    <execution>
      <phase>package</phase>
      <goals>
        <goal>makeAggregateBom</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

Ergebnis: `target/classes/sbom/backend-bom.json` → Classpath-Ressource `sbom/backend-bom.json`

> **Wichtig:** `outputDirectory` zeigt auf `${project.build.outputDirectory}/sbom`, sodass die Datei direkt im Classpath landet (kein `src/main/resources`-Commit nötig).

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
    private static final String BOM_PATH = "sbom/backend-bom.json";

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
- Hash-Anzeige: `getBestHash(lizenz)` liefert stärksten Hash (SHA-512 > SHA-256 > MD5) oder `null`; in der Tabelle: 12-Zeichen-Kürzel + `title`-Attribut für vollständigen Wert
- Leerstate: „KEINE_ERGEBNISSE" wenn Suchergebnis leer; „KEINE_LIZENZEN" wenn Liste selbst leer
- Fehlerstate: `backendError` / `frontendError` Boolean + `.zev-message--error`-Block
- Seitentitel mit `<app-icon name="shield">` (Feather-Icon)

**Hash-Priorisierung (TypeScript-Hilfsfunktion):**

```typescript
private readonly HASH_PRIORITY = ['SHA-512', 'SHA-256', 'SHA-384', 'MD5'];

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

1. **Annahme:** `cyclonedx-maven-plugin` Version 2.9.1 ist aktuell (Stand März 2026). Version ggf. auf Maven Central verifizieren.
2. **Annahme:** `outputDirectory=${project.build.outputDirectory}/sbom` funktioniert mit dem Plugin; ggf. muss `goal` auf `makeBom` (statt `makeAggregateBom`) umgestellt werden, falls nur `backend-service` betrachtet wird (kein Multi-Modul-Aggregat).
3. **Annahme:** `license-checker-rseidelsohn` ≥ 4.x ist kompatibel mit Node 20.
4. **Annahme:** Die `frontend-licenses.json` wird nicht ins Git committed (`.gitignore`-Eintrag für `src/assets/frontend-licenses.json` im Frontend-Service empfohlen, oder eine leere Placeholder-Datei committen).
5. **Annahme:** Der `CacheManager`-Bean wird auf einen gemeinsamen `CaffeineSpec` umgestellt; da `lizenzen`-Daten nie ablaufen müssten, ist die 15-min-TTL eine konservative Wahl (kein Nachteil bei statischen Daten).
6. **Annahme:** `hasRole('zev')` im Backend-Controller deckt auch `zev_admin` ab (wie im restlichen Projekt).
7. **Annahme:** Die `@PreAuthorize`-Annotation auf Klassen-Ebene schützt alle Methoden; bei Erweiterungen auf Methoden-Ebene anpassen.
