# Umsetzungsplan: Spring Boot 4 Upgrade

## Zusammenfassung

Upgrade der ZEV-Anwendung von Spring Boot 3.5.6 auf Spring Boot 4.0.x. Das Upgrade umfasst die Aktualisierung des Parent POM, Starter-Umbenennungen (Modularisierung), Migration der Test-Annotationen, Jackson 3-Kompatibilität und Aktualisierung aller Drittbibliotheken. Das Frontend (Angular) ist nicht betroffen.

---

## Betroffene Komponenten

### Anzupassende Dateien

| Datei | Änderung |
|-------|----------|
| `pom.xml` | Parent Version 3.5.6 → 4.0.x |
| `backend-service/pom.xml` | Starter-Renames, Dependency-Versionen |
| `admin-service/pom.xml` | Starter-Renames, Spring Boot Admin Version |
| `frontend-service/pom.xml` | Starter-Renames |
| `backend-service/src/test/java/ch/nacht/controller/EinheitControllerTest.java` | @MockBean → @MockitoBean |
| `backend-service/src/test/java/ch/nacht/controller/MesswerteControllerTest.java` | @MockBean → @MockitoBean |
| `backend-service/src/test/java/ch/nacht/controller/TarifControllerTest.java` | @MockBean → @MockitoBean |
| `backend-service/src/test/java/ch/nacht/controller/TranslationControllerTest.java` | @MockBean → @MockitoBean |
| `backend-service/src/test/java/ch/nacht/controller/EinstellungenControllerTest.java` | @MockBean → @MockitoBean |
| `backend-service/src/test/java/ch/nacht/controller/MieterControllerTest.java` | @MockBean → @MockitoBean |
| `backend-service/src/main/java/ch/nacht/dto/RechnungKonfigurationDTO.java` | Jackson-Annotationen prüfen |
| `backend-service/src/main/java/ch/nacht/config/SecurityConfig.java` | Spring Security 7 API prüfen |

### Nicht betroffen

| Datei/Modul | Grund |
|-------------|-------|
| `design-system/` | Kein Spring-Code |
| `frontend-service/src/` (Angular) | Nur npm/Angular, kein Java |
| `backend-service/src/main/resources/db/migration/` | Keine DB-Änderungen nötig |
| Alle `Dockerfile` | Java 21 bleibt, Images kompatibel |

---

## Phasen-Tabelle

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x]    | 1. Kompatibilitätsprüfung | Drittbibliotheken auf Spring Boot 4-Kompatibilität prüfen |
| [x]    | 2. Parent POM | Spring Boot Parent 3.5.6 → 4.0.1 |
| [x]    | 3. Backend Starter-Renames | `spring-boot-starter-web` → `webmvc`, `aop` → `aspectj`, `oauth2-resource-server` → `security-oauth2-resource-server` |
| [x]    | 4. Admin-Service Starter-Renames | `spring-boot-starter-web` → `webmvc`, Spring Boot Admin 4.0.0-M1 |
| [x]    | 5. Frontend-Service Starter-Renames | `spring-boot-starter-web` → `webmvc` |
| [x]    | 6. Test-Annotationen migrieren | `@MockBean` → `@MockitoBean` in 6 Controller-Tests |
| [x]    | 7. Jackson 3 Kompatibilität | `spring-boot-jackson2` Modul + `use-jackson2-defaults: true` |
| [x]    | 8. Spring AI aktualisieren | `spring-ai-anthropic-spring-boot-starter` 1.0.0-M6 → 2.0.0-RC2 |
| [x]    | 9. TestContainers vereinheitlichen | Alle auf 2.0.3, Artifact-Rename `testcontainers-postgresql`, Package-Rename |
| [x]    | 10. Kompilierung und Tests | 227/230 Tests grün. 3 Fehler in JasperTemplateCompileTest (JRXML-Templates müssen für JasperReports 7.x angepasst werden) |
| [x]    | 11. Integrationstests | `mvn verify` — TestContainers-Tests prüfen |
| [x]    | 12. Docker-Compose testen | `docker-compose up --build` — Gesamtstack validieren |

---

## Detaillierte Phasenbeschreibungen

### Phase 1: Kompatibilitätsprüfung

Ergebnis der Kompatibilitätsprüfung (Stand: Februar 2026):

| Bibliothek | Aktuelle Version | Neue Version | Kompatibel mit SB4? | Aktion |
|------------|-----------------|-------------|---------------------|--------|
| `de.codecentric:spring-boot-admin` | 3.4.1 | **4.0.0-M1** | Ja (Milestone) | Auf 4.0.0-M1 aktualisieren |
| `org.springframework.ai:spring-ai-anthropic-spring-boot-starter` | 1.0.0-M6 | **2.0.0-RC2** | Ja (RC) | Auf 2.0.0-RC2 aktualisieren |
| `net.sf.jasperreports:jasperreports` | 6.21.0 | **7.0.3** | Ja (Jakarta) | Auf 7.0.3 aktualisieren |
| `net.codecrete.qrbill:qrbill-generator` | 3.3.0 | 3.3.0 | Ja | Keine Spring-Abhängigkeit |
| `com.github.ben-manes.caffeine:caffeine` | inherited | inherited | Ja | Managed via BOM |
| `org.flywaydb:flyway-core` | inherited | inherited | Ja | Managed via BOM |
| `org.testcontainers:testcontainers` | 2.0.2 | **2.0.3** | Ja | Artifact rename + Version vereinheitlichen |
| `com.tngtech.archunit:archunit-junit5` | 1.3.0 | 1.3.0 | Ja | Keine Spring-Abhängigkeit |
| `io.micrometer:micrometer-registry-prometheus` | inherited | inherited | Ja | Managed via BOM |

**Ergebnis:** Alle Bibliotheken haben kompatible Versionen. Spring Boot Admin (4.0.0-M1) und Spring AI (2.0.0-RC2) sind noch Pre-Release, aber funktional.

**Wichtige Hinweise:**
- **Spring Boot Admin 4.0.0-M1:** Jolokia-Support eingeschränkt (wartet auf Jolokia 2.5.0)
- **Spring AI 2.0.0-RC2:** GA-Release erwartet im März 2026
- **JasperReports 7.0.3:** Breaking Change — .jasper-Dateien nicht abwärtskompatibel, müssen neu kompiliert werden
- **TestContainers 2.0.3:** Artifact-Rename von `org.testcontainers:postgresql` → `org.testcontainers:testcontainers-postgresql`, Package-Rename `org.testcontainers.containers.PostgreSQLContainer` → `org.testcontainers.postgresql.PostgreSQLContainer`

---

### Phase 2: Parent POM aktualisieren

**Datei:** `pom.xml`

```xml
<!-- Vorher -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.6</version>
    <relativePath/>
</parent>

<!-- Nachher -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.0</version>
    <relativePath/>
</parent>
```

---

### Phase 3: Backend Starter-Renames

**Datei:** `backend-service/pom.xml`

| Vorher | Nachher |
|--------|---------|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `spring-boot-starter-aop` | `spring-boot-starter-aspectj` |
| `spring-boot-starter-oauth2-resource-server` | `spring-boot-starter-security-oauth2-resource-server` |
| `spring-boot-starter-test` | `spring-boot-starter-test-classic` (Übergangslösung) |

Die übrigen Starter bleiben unverändert:
- `spring-boot-starter-validation` ✓
- `spring-boot-starter-data-jpa` ✓
- `spring-boot-starter-security` ✓
- `spring-boot-starter-actuator` ✓
- `spring-boot-starter-cache` ✓
- `spring-boot-testcontainers` ✓

---

### Phase 4: Admin-Service Starter-Renames

**Datei:** `admin-service/pom.xml`

| Vorher | Nachher |
|--------|---------|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `spring-boot-admin-starter-server` 3.4.1 | Version kompatibel mit SB4 |
| `spring-boot-admin-starter-client` 3.4.1 | Version kompatibel mit SB4 |

---

### Phase 5: Frontend-Service Starter-Renames

**Datei:** `frontend-service/pom.xml`

| Vorher | Nachher |
|--------|---------|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `spring-boot-admin-starter-client` 3.4.1 | Version kompatibel mit SB4 |

---

### Phase 6: Test-Annotationen migrieren

In allen 6 Controller-Test-Dateien:

**Import ändern:**
```java
// Vorher
import org.springframework.boot.test.mock.mockito.MockBean;

// Nachher
import org.springframework.test.context.bean.override.mockito.MockitoBean;
```

**Annotation ändern:**
```java
// Vorher
@MockBean
private EinheitService einheitService;

// Nachher
@MockitoBean
private EinheitService einheitService;
```

**Betroffene Dateien (6):**
- `backend-service/src/test/java/ch/nacht/controller/EinheitControllerTest.java`
- `backend-service/src/test/java/ch/nacht/controller/MesswerteControllerTest.java`
- `backend-service/src/test/java/ch/nacht/controller/TarifControllerTest.java`
- `backend-service/src/test/java/ch/nacht/controller/TranslationControllerTest.java`
- `backend-service/src/test/java/ch/nacht/controller/EinstellungenControllerTest.java`
- `backend-service/src/test/java/ch/nacht/controller/MieterControllerTest.java`

---

### Phase 7: Jackson 3 Kompatibilität

Spring Boot 4 verwendet standardmässig Jackson 3 (`tools.jackson` statt `com.fasterxml.jackson`).

**Betroffene Datei:**
- `backend-service/src/main/java/ch/nacht/dto/RechnungKonfigurationDTO.java` — verwendet `@JsonProperty`, `@JsonIgnore` o.ä.

**Option A — Jackson 2 Kompatibilitätsmodus:**
```xml
<!-- backend-service/pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-jackson2</artifactId>
</dependency>
```
```yaml
# application.yml
spring.jackson.use-jackson2-defaults: true
```

**Option B — Auf Jackson 3 migrieren:**
- Package-Imports von `com.fasterxml.jackson` → `tools.jackson` ändern
- Annotationen bleiben grösstenteils gleich (`@JsonProperty` etc.)

**Empfehlung:** Option A als Übergangslösung, später auf Jackson 3 migrieren.

---

### Phase 8: Spring AI aktualisieren

**Datei:** `backend-service/pom.xml`

Die aktuelle Version `1.0.0-M6` ist ein Milestone und an Spring Boot 3.x gebunden. Für Spring Boot 4 wird eine kompatible Version benötigt.

**Betroffene Java-Datei:**
- `backend-service/src/main/java/ch/nacht/service/EinheitMatchingService.java`

**Falls keine kompatible Version existiert — Workaround:**
- Spring AI Dependency vorübergehend entfernen
- `EinheitMatchingService` direkt mit dem Anthropic Java SDK implementieren
- `spring-milestones` Repository kann entfernt werden wenn Spring AI GA verwendet wird

---

### Phase 9: TestContainers vereinheitlichen

**Datei:** `backend-service/pom.xml`

Aktuelle Inkonsistenz:
- `testcontainers:testcontainers` → 2.0.2
- `testcontainers:postgresql` → 1.21.3
- `testcontainers:junit-jupiter` → 1.21.3

**Aktion:** Alle auf die gleiche aktuelle Version bringen:
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>2.0.2</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>2.0.2</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>2.0.2</version>
    <scope>test</scope>
</dependency>
```

**Hinweis:** TestContainers 2.x hat Breaking Changes gegenüber 1.x. Die `AbstractIntegrationTest.java` und alle IT-Klassen müssen auf API-Änderungen geprüft werden.

---

### Phase 10: Kompilierung und Unit-Tests

```bash
mvn clean compile test
```

Erwartete Fehler und Lösungen:

| Fehler | Lösung |
|--------|--------|
| `package org.springframework.boot.test.mock.mockito does not exist` | Phase 6 (Import ändern) |
| `cannot find symbol: MockBean` | Phase 6 (Annotation ändern) |
| Jackson-Serialisierungsfehler | Phase 7 (Jackson 2 Kompatibilität) |
| Spring AI Startup-Fehler | Phase 8 (Version aktualisieren oder entfernen) |
| Spring Security API-Änderungen | SecurityConfig.java anpassen |

---

### Phase 11: Integrationstests

```bash
cd backend-service
mvn verify
```

Prüfpunkte:
- TestContainers starten korrekt (PostgreSQL 16-alpine)
- `@ServiceConnection` funktioniert mit Spring Boot 4
- `@DynamicPropertySource` funktioniert
- Flyway-Migrationen laufen durch
- Hibernate `@Filter` für Multi-Tenancy funktioniert

---

### Phase 12: Docker-Compose testen

```bash
docker-compose up --build
```

Prüfpunkte:
- Alle 3 Services starten (Backend :8090, Frontend :4200, Admin :8081)
- Keycloak-Authentifizierung funktioniert
- REST-API erreichbar
- PDF-Generierung funktioniert
- Spring AI / Claude-Integration funktioniert

---

## Validierungen

### Vor dem Upgrade
1. Alle Tests laufen grün mit Spring Boot 3.5.6 (`mvn clean verify`)
2. Drittbibliotheken-Kompatibilität bestätigt

### Nach dem Upgrade
1. `mvn clean compile` — Kompilierung ohne Fehler
2. `mvn test` — Alle 24 Testklassen grün
3. `mvn verify` — Alle 5 IT-Klassen grün
4. `docker-compose up --build` — Stack startet fehlerfrei
5. Manuelle Prüfung: Login, CRUD-Operationen, PDF-Generierung, KI-Matching

---

## Offene Punkte / Annahmen

### Offene Punkte

| # | Frage | Status |
|---|-------|--------|
| 1 | Gibt es eine Spring Boot Admin Version ≥ 4.0? | **Ja: 4.0.0-M1** (Milestone) |
| 2 | Gibt es eine Spring AI GA-Version für Spring Framework 7? | **RC: 2.0.0-RC2** (GA erwartet März 2026) |
| 3 | Ist JasperReports 6.21.0 kompatibel mit Servlet 6.1? | **Nein, Upgrade auf 7.0.3** nötig |
| 4 | Hat TestContainers 2.0.2 Breaking Changes bei `PostgreSQLContainer`? | **Ja: Artifact + Package-Rename** |

### Annahmen

1. **Classic Starter als Übergangslösung:** `spring-boot-starter-test-classic` wird verwendet statt sofort auf modulare Test-Starter zu migrieren
2. **Jackson 2 Kompatibilität:** Das `spring-boot-jackson2` Modul wird als Übergangslösung eingesetzt
3. **Kein JUnit 6 Upgrade:** JUnit 5 bleibt, da JUnit 6 optional ist
4. **Keine DB-Änderungen:** Das Upgrade betrifft nur Java/Maven-Konfiguration
5. **Docker-Images bleiben:** `eclipse-temurin:21-jre-alpine` ist kompatibel mit Spring Boot 4
