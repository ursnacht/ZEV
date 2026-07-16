# Security-Check

Prüft den **Anwendungscode** gegen die ZEV-eigenen Security-Invarianten und die OWASP-Top-10.
Ergänzt `21_vulnerabilities-check` (Dependency-CVEs/SCA) um die **Code-Perspektive (SAST)** –
mit Fokus auf das zentrale ZEV-Risiko: **Mandanten-Isolation** (Multi-Tenancy).

> Nutzt die Skill **`owasp-security`** (OWASP Top 10:2025, ASVS 5.0) für die generische
> Web-Security-Bewertung und kombiniert sie mit den projektspezifischen Checks unten.

## Input
* Optional: $ARGUMENTS – Scope:
  * `all` – Backend + Frontend **(Default)**
  * `backend` – nur `backend-service` (Mandant, @PreAuthorize, Validierung, Secrets)
  * `frontend` – nur `frontend-service` (AuthGuard, Token-Handling, Interceptor)
  * konkretes Feature/Modul (z.B. `RechnungController`, `mieter`) → nur dessen Pfad prüfen
* Default: `all`

---

## Sub-Agent Ausführung

> **Als Sub-Agent:** Überspringe diesen Abschnitt und fahre direkt mit **Vorgehen** fort. Analysiere NUR:
> 1. Den tatsächlich vorhandenen Code (Controller/Service/Entity/Repository/Config)
> 2. Die Route/Rollen-Matrix und Konventionen aus `CLAUDE.md`
> 3. Die `owasp-security`-Skill als Bewertungsrahmen

Starte einen neuen Sub-Agenten mit dem `Agent`-Tool:

- **description:** `"Security-Check: [scope]"`
- **prompt:**

```
Du prüfst den ZEV-Anwendungscode auf Security-Schwachstellen (SAST + ZEV-Invarianten).
Scope: [scope] (default: all)

Lies zuerst die Skill owasp-security und dann .claude/commands/8_security-check.md.
Fahre ab Abschnitt "Vorgehen" fort.

Wichtig: READ-ONLY. Keine Code-Änderungen, kein Fix. Nur prüfen und berichten.
Belege jeden Befund mit Datei:Zeile.
```

- **Hinweis:** Ersetze `[scope]` im `prompt` mit dem tatsächlichen Wert aus `$ARGUMENTS` (default: `all`).

---

## Vorgehen

### Phase 0: OWASP-Rahmen laden
Die Skill `owasp-security` heranziehen (OWASP Top 10:2025, ASVS 5.0). Die folgenden Phasen
konkretisieren sie für ZEV. Befunde am Ende den OWASP-Kategorien zuordnen (A01 Broken Access
Control, A03 Injection, A02 Crypto/Secrets, A05 Misconfiguration, …).

### Phase 1: Inventar
- **Controller** (`backend-service/src/main/java/ch/nacht/controller/`) – jede `@*Mapping`-Methode.
- **Services** (`.../service/`) – jede Methode mit Repository-/DB-Zugriff.
- **Entities** (`.../entity/`) – Multi-Tenancy-Träger.
- **Repositories** (`.../repository/`) – Custom-`@Query`/native SQL.
- **Config** (`.../config/`) – `SecurityConfig`, `OrganizationInterceptor`, `WebMvcConfig`.
- **Frontend** – Routen + `*.guard.ts`, Interceptoren, Keycloak-Init.

> **Hinweis:** `ArchitectureTest.java` (`.../test/.../architecture/`) erzwingt bereits einige
> dieser Regeln per ArchUnit. Erst dort nachsehen, was schon abgedeckt ist – Befunde, die ein
> bestehender ArchUnit-Test fängt, als „durch ArchUnit abgesichert" markieren, **nicht** als Lücke.

### Phase 2: Mandanten-Isolation (KRITISCH – OWASP A01)
Das wichtigste ZEV-Risiko ist Cross-Tenant-Datenzugriff. Pro mandantenfähiger Entity/Service prüfen:

1. **Entity** hat `@Column(name = "org_id", nullable = false)` **und** den Hibernate-`@Filter`
   (`@Filter(name = "orgFilter", condition = "org_id = :orgId")`). Fehlt eines → **High**.
   (Vorlage: `entity/Tarif.java`.)
2. **Service** aktiviert vor *jedem* lesenden/schreibenden Zugriff `hibernateFilterService.enableOrgFilter()`
   und setzt `orgId` beim Speichern aus dem `OrganizationContextService` (nicht aus dem Request-Body!).
   Methode ohne `enableOrgFilter()` vor einer Query → **High** (Tenant-Leak).
3. **Repository**: Custom-`@Query`/native SQL umgeht den Hibernate-Filter → muss `org_id` selbst
   im `WHERE` führen. Native Query ohne `org_id`-Bedingung → **High**.
4. **Cache**: `@Cacheable`-Keys (z.B. `statistik`) müssen die `orgId` enthalten, sonst sieht
   Mandant A die gecachten Werte von Mandant B → **High**.
5. **org_id aus dem Request**: Wird `orgId` jemals aus DTO/PathVariable/Param übernommen statt
   aus dem Security-Context? → **High** (IDOR / Tenant-Spoofing).

### Phase 3: Autorisierung (OWASP A01)
1. **Jede** Controller-Methode (bzw. die Klasse) trägt `@PreAuthorize`. Fehlt es auf einem
   nicht-`public`-Endpoint → **High**.
2. Die Rolle stimmt mit der **Route/Rollen-Matrix in `CLAUDE.md`** überein
   (`zev` vs `zev_admin`; z.B. `/api/tarife`, `/api/mieter`, `/api/rechnungen`, `/api/einstellungen`
   = `zev_admin`). Abweichung (zu schwache Rolle) → **High**, (zu strenge) → **Low/Hinweis**.
3. `PingController` / Actuator-/`public`-Endpoints bewusst offen? Sonst → **Medium**.
4. **Frontend-`AuthGuard`** schützt dieselben Routen mit denselben Rollen wie das Backend
   (Frontend-Guard ist nur UX, **nicht** die Sicherheitsgrenze – Backend muss immer prüfen).

### Phase 4: Injection & Eingabe-Validierung (OWASP A03)
1. **`@Valid`** auf Request-Bodies + Bean-Validation-Constraints in den DTOs. Fehlende Validierung
   auf schreibenden Endpoints → **Medium**.
2. **SQL**: Nur parametrisierte Queries/JPA. String-konkateniertes SQL/JPQL → **High**.
3. **Datei-/PDF-Pfade** (JasperReports, QR-Bill, CSV-Upload): Datei-/Pfadangaben aus Nutzereingabe
   ohne Sanitisierung → Path-Traversal → **High**.
4. **CSV-Upload + KI-Matching** (`EinheitMatchingService`): Prompt-Injection über CSV-Inhalte,
   Größen-/Typ-Limits, kein Verarbeiten fremder org-Daten.

### Phase 5: Secrets, Krypto & Konfiguration (OWASP A02/A05)
1. **Keine hartcodierten Secrets** im Code/Repo (`ANTHROPIC_API_KEY`, DB-Passwörter, NVD-Key) –
   nur via Env/`.env` (gitignored). Treffer → **High**.
2. **`SecurityConfig`/JWT**: Issuer/Audience-Validierung aktiv, Keycloak-Org-Claim korrekt gemappt,
   kein `permitAll()` zu breit, CORS nicht `*` mit Credentials.
3. **Actuator** (`backend`/`admin`/`frontend`): nur `health`/`info`/`prometheus` öffentlich; alle
   übrigen (`/actuator/env`, `/heapdump`, `/loggers`, ...) nur mit Basic Auth
   (`actuatorFilterChain`, Credentials via `ACTUATOR_USER`/`ACTUATOR_PASSWORD`) erreichbar.
4. **Fehlerbehandlung**: `GlobalExceptionHandler` leakt keine Stacktraces/internen Details an den Client.
5. **i18n**: keine sicherheitsrelevanten/internen Infos in hartcodierten Strings (Konvention: Texte
   über `TranslationService`).

### Phase 6: Frontend-spezifisch
- Keycloak-Token nicht in `localStorage`/Logs; kein Token in URLs.
- `ErrorInterceptor` behandelt `NO_ORGANIZATION`-403 korrekt (Logout) und leakt keine Details.
- Keine eingebetteten Secrets im Bundle/`environment.ts`.

### Phase 7: Befunde interpretieren
Pro Finding erfassen: **Severity** (High/Medium/Low), **OWASP-Kategorie**, **Scope** (Backend/Frontend),
**Ort** (`Datei:Zeile`), **bereits durch ArchUnit/Test abgesichert?**.
Priorisierung: **Mandanten-Isolation + fehlende Autorisierung zuerst** (direkte Datenexposition),
dann Injection/Validierung, dann Konfiguration/Härtung.

---

## Ausgabe-Format

```markdown
# Security-Check[: scope]

## Übersicht
- Geprüft: [Backend/Frontend], N Controller, M Services, K Entities
- Befunde: High: A, Medium: B, Low: C
- Schwerpunkt-Status: Mandanten-Isolation [ok/Lücken], Autorisierung [ok/Lücken]

## Befunde

| # | Severity | OWASP | Kategorie | Ort (Datei:Zeile) | Beschreibung | Empfehlung |
|---|----------|-------|-----------|-------------------|--------------|------------|
| 1 | High | A01 | Tenant-Leak | service/XService.java:58 | Query ohne enableOrgFilter() | Filter vor Zugriff aktivieren |
| 2 | High | A01 | AuthZ fehlt | controller/XController.java:34 | kein @PreAuthorize | Rolle gem. CLAUDE.md ergänzen |
| ... | ... | ... | ... | ... | ... | ... |

## Bereits abgesichert (kein Handlungsbedarf)
- z.B. „org_id-Filter wird durch ArchitectureTest erzwungen"

## Zusammenfassung

### Sofort beheben (High – Datenexposition)
1. ...

### Härten (Medium)
1. ...

### Hinweise / Beobachten (Low)
1. ...

### Empfohlene nächste Schritte
1. ... (ggf. neuen ArchUnit-Test ergänzen, der die Lücke dauerhaft verhindert)
```

---

## Hinweise

- **READ-ONLY** – dieser Command prüft und berichtet nur; er ändert **keinen** Code und behebt nichts.
- **Nachweis liefern** – jeder Befund mit `Datei:Zeile`; Vermutungen als solche kennzeichnen.
- **Defense in Depth** – Frontend-`AuthGuard` ist UX, nicht die Sicherheitsgrenze; maßgeblich ist
  immer die Backend-Autorisierung + der Mandanten-Filter.
- **Komplementär** zu `21_vulnerabilities-check` (Dependency-CVEs) und `7_akzeptanzkriterien-check`
  (fachliche Spec-Kriterien) – dieser Command deckt die **Code-Security** ab.
- Wo sinnvoll, als nachhaltige Maßnahme einen **ArchUnit-Test** vorschlagen (z.B. „jeder Controller
  hat @PreAuthorize", „jede Entity hat org_id"), damit die Invariante im Build erzwungen wird.

## Referenz
* Skill `owasp-security` – OWASP Top 10:2025, ASVS 5.0 (Bewertungsrahmen)
* `CLAUDE.md` – Route/Rollen-Matrix, Authentication, Multi-Tenancy-Konventionen
* `Specs/Mandantenfähigkeit.md` – Soll-Verhalten der Mandanten-Isolation
* `backend-service/.../config/SecurityConfig.java` – OAuth2/JWT-Konfiguration
* `backend-service/.../service/HibernateFilterService.java`, `OrganizationContextService.java`
* `backend-service/.../entity/Tarif.java` – Vorlage `org_id` + `@Filter`
* `backend-service/src/test/java/ch/nacht/architecture/ArchitectureTest.java` – bestehende ArchUnit-Regeln
* `.claude/commands/21_vulnerabilities-check.md` – Dependency-Sicherheit (Schwesterkommando)
