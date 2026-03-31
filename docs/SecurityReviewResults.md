# Security Review Results

**Datum:** 2026-03-31
**Reviewer:** Claude Code (OWASP Top 10:2025 / ASVS 5.0)
**Scope:** ZEV Backend (Spring Boot 4), Frontend (Angular 21), Konfiguration

---

## Zusammenfassung

| Schweregrad | Anzahl |
|-------------|--------|
| CRITICAL    | 2      |
| HIGH        | 6      |
| MEDIUM      | 9      |
| LOW         | 5      |
| **Total**   | **22** |

---

## CRITICAL — Sofortiger Handlungsbedarf

### C1 — Exponierter Anthropic API-Key im Repository
- **Datei:** `.env:5`
- **OWASP:** A02 – Cryptographic Failures
- **Problem:** Der echte Anthropic API-Key ist in `.env` committed. Obwohl `.gitignore` die Datei ausschließt, ist der Key möglicherweise bereits in der Git-History.
- **Massnahmen:**
  1. API-Key sofort unter [console.anthropic.com](https://console.anthropic.com) rotieren
  2. Key aus Git-History entfernen (`git filter-repo --path .env --invert-paths`)
  3. Sicherstellen, dass nur `.env.example` mit Platzhaltern eingecheckt ist

### C2 — Hardcodierte Datenbank- und Keycloak-Credentials
- **Datei:** `docker-compose.yml:9-10, 28-36`
- **OWASP:** A02 – Cryptographic Failures
- **Problem:** PostgreSQL (`postgres/postgres`) und Keycloak (`admin/admin`) Credentials sind hartcodiert.
- **Massnahmen:**
  1. Credentials in `.env`-Datei auslagern (nicht ins Repository)
  2. `docker-compose.yml` mit `${POSTGRES_PASSWORD}` etc. auf `.env` verweisen
  3. In Produktion starke, zufällig generierte Passwörter verwenden

---

## HIGH

### H1 — Hardcodierte DB-Credentials in Application-Config
- **Datei:** `backend-service/src/main/resources/application.yml:27-29`
- **OWASP:** A02 – Cryptographic Failures
- **Problem:** `username: postgres` / `password: postgres` direkt in der Konfigurationsdatei.
- **Fix:**
  ```yaml
  datasource:
    username: ${SPRING_DATASOURCE_USERNAME:postgres}
    password: ${SPRING_DATASOURCE_PASSWORD}
  ```

### H2 — Alle Actuator-Endpunkte exponiert
- **Datei:** `backend-service/src/main/resources/application.yml:49-65`
- **OWASP:** A01 – Broken Access Control, A02 – Security Misconfiguration
- **Problem:** `include: "*"` exponiert alle Actuator-Endpunkte inkl. `/actuator/env` (gibt alle Umgebungsvariablen inkl. Secrets zurück), `/actuator/configprops`, `/actuator/loggers`.
- **Fix:**
  ```yaml
  management:
    endpoints:
      web:
        exposure:
          include: "health,info,prometheus"
    endpoint:
      health:
        show-details: when-authorized
      env:
        show-values: never
      configprops:
        show-values: never
  ```

### H3 — CSRF für `/actuator/**` deaktiviert
- **Datei:** `backend-service/src/main/java/ch/nacht/config/SecurityConfig.java:26-27`
- **OWASP:** A01 – Broken Access Control
- **Problem:** `csrf.ignoringRequestMatchers("/actuator/**")` erlaubt CSRF-Angriffe auf Actuator-Endpunkte.
- **Fix:** Actuator-Endpunkte auf Admin-Service beschränken oder CSRF korrekt konfigurieren.

### H4 — Übermässig permissive CORS-Konfiguration
- **Datei:** `backend-service/src/main/java/ch/nacht/config/SecurityConfig.java:54-62`
- **OWASP:** A01 – Broken Access Control
- **Problem:** `setAllowedHeaders(Arrays.asList("*"))` erlaubt alle HTTP-Header inkl. potenziell gefährlicher wie `X-Forwarded-For`. Kombiniert mit `setAllowCredentials(true)` ein erhöhtes Risiko.
- **Fix:**
  ```java
  configuration.setAllowedHeaders(Arrays.asList("Content-Type", "Authorization"));
  configuration.setAllowedOrigins(
      Arrays.asList(System.getenv("ALLOWED_ORIGINS").split(",")));
  ```

### H5 — Interne Fehlermeldungen werden an den Client zurückgegeben
- **Datei:** `backend-service/src/main/java/ch/nacht/controller/PingController.java:33`
- **OWASP:** A09 – Security Logging and Monitoring Failures
- **Problem:** `response.put("error", e.getMessage())` exponiert interne Exception-Messages.
- **Fix:**
  ```java
  log.error("Database health check failed", e);
  response.put("database", "unavailable");
  // e.getMessage() nicht an Client zurückgeben
  ```

### H6 — Unvollständiges Exception-Handling (kein Catch-All)
- **Datei:** `backend-service/src/main/java/ch/nacht/exception/GlobalExceptionHandler.java`
- **OWASP:** A09 – Security Logging and Monitoring Failures
- **Problem:** Kein Handler für generische `Exception`-Klasse. Unbehandelte Exceptions können Stack-Traces an den Client leaken.
- **Fix:**
  ```java
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
      log.error("Unhandled exception", ex);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", "Internal Server Error"));
  }
  ```

---

## MEDIUM

### M1 — Fehlende Autorisierung auf `GET /api/einheit/{id}`
- **Datei:** `backend-service/src/main/java/ch/nacht/controller/EinheitController.java:42`
- **OWASP:** A01 – Broken Access Control
- **Problem:** Methode hat kein `@PreAuthorize`, obwohl `zev`-Rolle erwartet wird.
- **Fix:** `@PreAuthorize("hasRole('zev')")` zur Methode hinzufügen.

### M2 — Unauthentifizierter Zugriff auf `/api/translations`
- **Datei:** `backend-service/src/main/java/ch/nacht/controller/TranslationController.java:27`
- **OWASP:** A01 – Broken Access Control
- **Problem:** GET-Endpunkt ohne Authentifizierung. Kann als Enumeration-Vector genutzt werden.
- **Fix:** Explizit dokumentieren: `@PreAuthorize("permitAll()")` oder Authentifizierung fordern.

### M3 — Keine Eigentumsvalidierung bei Invoice-Generierung (IDOR)
- **Datei:** `backend-service/src/main/java/ch/nacht/controller/RechnungController.java:51`
- **OWASP:** A01 – Broken Access Control
- **Problem:** `einheitIds` kommen aus dem Request-Body ohne Prüfung, ob der User die Einheiten besitzt.
- **Fix:** Vor der Verarbeitung prüfen, ob alle `einheitIds` zur aktuellen Organisation gehören.

### M4 — Kein Ownership-Check beim Invoice-Download
- **Datei:** `backend-service/src/main/java/ch/nacht/controller/RechnungController.java:130`
- **OWASP:** A01 – Broken Access Control
- **Problem:** `GET /api/rechnungen/download/{key}` prüft nicht, ob der aktuelle User die Rechnung generiert hat.
- **Fix:** Sicherstellen, dass der `key` mit der aktuellen `org_id` verknüpft ist.

### M5 — Unzureichende CSV-Validierung
- **Datei:** `backend-service/src/main/java/ch/nacht/service/MesswerteService.java:69`
- **OWASP:** A05 – Injection
- **Problem:** Kein Dateigrössenlimit, `Double.parseDouble()` wirft unkontrolliert `NumberFormatException`, keine Validierung der Spaltenanzahl.
- **Fix:**
  ```java
  if (file.getSize() > 10 * 1024 * 1024) {
      throw new IllegalArgumentException("File too large");
  }
  ```

### M6 — Timezone-Problem bei Datumsverarbeitung
- **Datei:** `backend-service/src/main/java/ch/nacht/controller/MesswerteController.java:78`
- **OWASP:** A05 – Injection (Time-based bypass)
- **Problem:** `atStartOfDay()` nutzt System-Timezone statt UTC, kann zu Daten-Leakage über Timezone-Grenzen führen.
- **Fix:**
  ```java
  LocalDateTime dateTimeFrom = dateFrom.atStartOfDay(ZoneId.of("UTC")).toLocalDateTime();
  ```

### M7 — Header-Injection-Risiko im Content-Disposition
- **Datei:** `backend-service/src/main/java/ch/nacht/controller/StatistikController.java:102`
- **OWASP:** A01 – Broken Access Control
- **Problem:** Dateiname wird nicht RFC-5987-konform enkodiert.
- **Fix:**
  ```java
  String encoded = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
  "attachment; filename=\"" + encoded + "\""
  ```

### M8 — Kein Rate-Limiting auf API-Endpunkten
- **Datei:** Alle Controller
- **OWASP:** A06 – Vulnerable and Outdated Components
- **Problem:** Kein Rate-Limiting. Besonders kritisch: CSV-Upload und AI-Matching-Endpunkt (externe API-Kosten).
- **Fix:** Spring Security Rate-Limiting-Filter oder API-Gateway mit Throttling implementieren.

### M9 — Unzureichende Validierung des Datumsbereichs
- **Datei:** `backend-service/src/main/java/ch/nacht/controller/RechnungController.java:52`
- **OWASP:** A05 – Injection
- **Problem:** Kein Maximum für Datumsbereiche. Sehr grosse Bereiche können DoS verursachen.
- **Fix:**
  ```java
  if (ChronoUnit.DAYS.between(request.von, request.bis) > 365) {
      return ResponseEntity.badRequest().body(Map.of("error", "Max. 1 Jahr"));
  }
  ```

---

## LOW

### L1 — Logging sensibler Daten
- **Datei:** Mehrere Services
- **OWASP:** A09 – Security Logging and Monitoring Failures
- **Problem:** Einheitsnamen und Dateinamen werden geloggt (PII). Logs könnten von Unbefugten eingesehen werden.
- **Fix:** Sensitive Felder aus Log-Statements entfernen.

### L2 — Fehlende Security-Headers
- **Datei:** `SecurityConfig.java`
- **OWASP:** A05 – Security Misconfiguration
- **Problem:** Keine HTTP-Security-Headers: `X-Frame-Options`, `X-Content-Type-Options`, `Strict-Transport-Security`, `Content-Security-Policy`.
- **Fix:**
  ```java
  http.headers(h -> h
      .frameOptions().deny()
      .contentTypeOptions()
      .and()
      .httpStrictTransportSecurity()
  );
  ```

### L3 — Keine Pagination auf List-Endpunkten
- **Datei:** `MieterController`, `TarifController` etc.
- **OWASP:** A05 – Security Misconfiguration
- **Problem:** `GET /api/mieter` etc. liefern alle Datensätze ohne Limit → potenzieller DoS.
- **Fix:** `Pageable`-Parameter mit maximalem `size`-Limit (z.B. 100) einführen.

### L4 — Schwache JWT-Validierung
- **Datei:** `SecurityConfig.java`
- **OWASP:** A01 – Broken Access Control
- **Problem:** Keine explizite Validierung von Issuer und Audience im JWT-Decoder.
- **Fix:**
  ```java
  decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuerUri));
  ```

### L5 — Kein Audit-Logging für kritische Operationen
- **Datei:** Alle Controller
- **OWASP:** A09 – Security Logging and Monitoring Failures
- **Problem:** Rechnungserstellung, CSV-Upload, Einstellungsänderungen werden nicht auditiert.
- **Fix:** `@Auditable`-Annotation mit AOP-Aspect implementieren.

---

## Priorisierte Massnahmen

| Priorität | Massnahme |
|-----------|-----------|
| 1 | **Anthropic API-Key sofort rotieren** und aus Git-History entfernen |
| 2 | **Actuator-Endpunkte einschränken** (`include: "health,info,prometheus"`, `show-values: never`) |
| 3 | **Credentials externalisieren** — `application.yml` und `docker-compose.yml` auf Env-Variablen umstellen |
| 4 | **Catch-All Exception Handler** in `GlobalExceptionHandler` ergänzen |
| 5 | **`@PreAuthorize`** auf fehlenden Endpunkten nachziehen (`EinheitController.getEinheitById`) |
| 6 | **CORS einschränken** — erlaubte Header explizit auflisten |
| 7 | **Rate-Limiting** für CSV-Upload und AI-Endpunkte |
| 8 | **CSV-Dateigrössenlimit** und robustes Parsing implementieren |
