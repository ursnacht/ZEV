# Vulnerabilities-Check

Prüft das gesamte Projekt auf bekannte Sicherheitslücken (CVEs/Advisories) – npm-Module
via `npm audit` und Maven-Module via OWASP Dependency-Check.

## Input
* Optional: $ARGUMENTS – Scope:
  * `all` – alle Module (npm + Maven) **(Default)**
  * `npm` – nur `frontend-service` + `design-system` (`npm audit`)
  * `maven` – OWASP-Scan über den Reactor: `admin-service`, `backend-service`,
    `frontend-service`, `design-system` (+ Parent) – als `aggregate` am Root
  * konkreter Modulname (z.B. `frontend-service`, `backend-service`)
* Default: `all`

---

## Sub-Agent Ausführung

> **Als Sub-Agent:** Überspringe diesen Abschnitt und fahre direkt mit **Vorgehen** fort. Analysiere NUR:
> 1. Die tatsächlich vorhandenen `package.json` / `pom.xml` Dateien
> 2. Die tatsächliche Ausgabe von `npm audit` bzw. OWASP Dependency-Check
> 3. Projekt-Konventionen aus `CLAUDE.md`

Starte einen neuen Sub-Agenten mit dem `Agent`-Tool:

- **description:** `"Vulnerabilities-Check: [scope]"`
- **prompt:**

```
Du prüfst das Projekt auf bekannte Sicherheitslücken (npm audit + OWASP Dependency-Check).
Scope: [scope] (default: all)

Lies: .claude/commands/21_vulnerabilities-check.md
Fahre ab Abschnitt "Vorgehen" fort.

Wichtig: READ-ONLY. KEIN `npm audit fix`, KEIN `npm install`, keine Dateiaenderungen.
Du arbeitest unter Windows mit PowerShell (npm via npm.cmd, mvn via mvn.cmd).
```

- **Hinweis:** Ersetze `[scope]` im `prompt` mit dem tatsächlichen Wert aus `$ARGUMENTS` (default: `all`).

---

## Vorgehen

### Phase 1: Module inventarisieren

```
npm-Module (package.json):
  frontend-service/   → Angular-App (Runtime + Dev/Test-Tooling)
  design-system/      → CSS/TS-Build-Wrapper (nur devDependencies)

Maven-Module (pom.xml, Reactor des Root-Parents):
  pom.xml             → Parent (spring-boot-starter-parent 4.0.6)
  admin-service/      → Spring Boot Admin
  backend-service/    → Spring Boot Backend (meiste selbst gepinnte Java-Deps)
  frontend-service/   → Angular-App in Spring Boot gewrappt (webmvc, actuator,
                        micrometer-prometheus, spring-boot-admin-client → Java-CVEs!)
  design-system/      → als JAR gepackt (frontend-maven-plugin baut CSS/TS)
```

> **Achtung (ZEV-Spezifikum):** `frontend-service` und `design-system` sind **hybride**
> Module – sie haben sowohl eine `package.json` (npm) als auch eine `pom.xml` (Maven-JAR).
> `frontend-service` zieht echte Spring-Boot-Runtime-Deps, die OWASP scannt; der OWASP-
> `aggregate`-Lauf am Root deckt daher **alle vier** Maven-Module ab (nicht nur admin/backend).

Scope steuert, welche Module geprüft werden (`all` = alle).

### Phase 2: npm-Vulnerabilities (`npm audit`)

```bash
# Lesbarer Report je Modul
cd frontend-service && npm.cmd audit
cd design-system    && npm.cmd audit

# Maschinenlesbar (für Kategorisierung; Severity, direkt/transitiv, Pfade)
cd frontend-service && npm.cmd audit --json
```

**Fix-Verfügbarkeit OHNE Änderung ermitteln** (zeigt nur, was ein Fix *täte*):
```bash
cd frontend-service && npm.cmd audit fix --dry-run
```
- Findings, die ohne `--force` behebbar sind → risikoarm (Patch/Minor transitiver Deps).
- Findings, die `--force` bräuchten → potenziell Breaking (Major-Downgrade), gesondert ausweisen.
- `No fix available` → blockiert, auf Upstream-Update angewiesen.

**Runtime vs. Dev unterscheiden** (entscheidend für die Risikobewertung):
```bash
# Nur Production-/Runtime-Abhängigkeiten betrachten
cd frontend-service && npm.cmd audit --omit=dev
```
- Vulns nur in `devDependencies` (Build-/Test-Tooling wie karma, webpack-dev-server,
  @angular/cli, license-checker) landen **nicht** im ausgelieferten Bundle → meist geringes
  Praxis-Risiko (Angriff nur über lokalen Build-/Dev-Server-Prozess).
- Vulns in Runtime-Deps (`@angular/*`, `keycloak-*`, `rxjs`, `zone.js`) sind **kritischer**,
  da sie an Endnutzer ausgeliefert werden.

Herkunft einer transitiven Vuln klären:
```bash
cd frontend-service && npm.cmd why <paket>
```

> **Hinweis:** `npm audit` liefert Exit-Code 1, sobald Findings existieren – das ist **kein**
> Fehler, sondern erwartetes Verhalten.

### Phase 3: Maven-Vulnerabilities (OWASP Dependency-Check)

Für die Java-Module werden CVEs via OWASP Dependency-Check geprüft. Dies benötigt einen
(kostenlosen) **NVD-API-Key**, der ausschliesslich über die Umgebungsvariable `NVD_API_KEY`
bereitgestellt wird (nie im Repo).

**Einmalige Einrichtung** – Key (kostenlos) anfordern: https://nvd.nist.gov/developers/request-an-api-key,
dann **eine** der beiden Varianten:
```powershell
# A) .env-Datei (empfohlen, lokal, gitignored): NVD_API_KEY eintragen (Vorlage: .env.example)
#    NVD_API_KEY=<dein-key>

# B) Persistente User-Env-Var:
setx NVD_API_KEY "<dein-key>"   # danach NEUES Terminal oeffnen
# (oder fuer die aktuelle Session:  $env:NVD_API_KEY = "<dein-key>")
```
Das Skript lädt die `.env` automatisch; eine gesetzte Env-Var hat Vorrang vor der `.env`.

**Scan ausführen** (bevorzugt über den Helfer, der den Key aus der Env-Var liest und maskiert):
```powershell
# Alle Maven-Module (aggregate)
./scripts/owasp-dependency-check.ps1

# Einzelnes Modul
./scripts/owasp-dependency-check.ps1 -Module backend-service
```

Äquivalenter direkter Aufruf (Plugin muss nicht im POM stehen):
```bash
mvn org.owasp:dependency-check-maven:aggregate -DnvdApiKey=$env:NVD_API_KEY -Dformat=ALL
mvn org.owasp:dependency-check-maven:check -pl backend-service -DnvdApiKey=$env:NVD_API_KEY
```

Ergebnis: `<modul>/target/dependency-check-report.html` (+ `.json`). Die JSON/HTML nach
`vulnerabilities` mit `severity` (CRITICAL/HIGH/MEDIUM/LOW) und betroffenem Artefakt auswerten.

**Wichtige Rahmenbedingungen:**
- Ohne gültigen `NVD_API_KEY` drosselt der NVD-Feed (typisch **HTTP 429**) und der Scan bricht ab.
- Der **erste Lauf lädt die CVE-Datenbank** herunter (mehrere hundert MB, dauert lange);
  Folgeläufe nutzen den lokalen Cache (`~/.m2/repository/org/owasp/dependency-check-data/`).
- Benötigt Netzwerkzugriff; in Offline-Umgebungen schlägt der Scan fehl.
- Beachte: Boot-verwaltete Dependencies werden über das **Parent-POM** versioniert – Fixes
  erfolgen i.d.R. via Spring-Boot-Update, nicht einzeln (siehe `20_dependencies-check`).

> Falls der OWASP-Scan im aktuellen Umfeld nicht lauffähig ist (kein Key/Offline/HTTP 429),
> das im Report unter „Maven (OWASP)" klar als „nicht ausgeführt: <Grund>" vermerken und mit
> dem npm-Teil fortfahren.

### Phase 4: Befunde interpretieren

Pro Finding erfassen:
- **Severity:** Critical / High / Moderate(Medium) / Low
- **Scope:** Runtime (ausgeliefert) vs. Dev/Build/Test (lokal)
- **Pfad:** direkt vs. transitiv (über welches Top-Level-Paket)
- **Fix:** ohne `--force` behebbar / nur mit `--force` (breaking) / `No fix available` / via Parent-Update

Priorisierung: **Runtime + High/Critical** zuerst, dann Runtime/Moderate, dann Dev-Tooling.

---

## Ausgabe-Format

```markdown
# Vulnerabilities-Check

## Übersicht
- Geprüfte Module: npm (frontend-service, design-system) + Maven (admin-service, backend-service, frontend-service, design-system)
- npm: Critical: A, High: B, Moderate: C, Low: D  (davon Runtime: X, Dev-only: Y)
- Maven (OWASP): Critical: …, High: …, Medium: …  [oder "nicht ausgeführt: Grund"]

## npm-Vulnerabilities

| Modul | Paket | Severity | Scope | Pfad (Herkunft) | Fix verfügbar |
|-------|-------|----------|-------|-----------------|---------------|
| frontend-service | lodash | High | Dev (karma) | transitiv | ja (ohne --force) |
| ... | ... | ... | ... | ... | ... |

## Maven-Vulnerabilities (OWASP Dependency-Check)

| Modul | Artefakt | CVE | Severity | Bemerkung |
|-------|----------|-----|----------|-----------|
| backend-service | … | CVE-… | High | via Spring-Boot-BOM → Parent-Update |
| ... | ... | ... | ... | ... |

## Zusammenfassung

### Sofort beheben (Runtime, High/Critical)
1. ...

### Risikoarm behebbar (npm audit fix ohne --force / Patch)
1. ...

### Beobachten / blockiert
1. <paket> – No fix available (Upstream), nur Dev-Tooling → geringes Risiko
2. Spring-Boot-verwaltete CVEs → nur via Parent-Update behebbar

### Empfohlene nächste Schritte
1. ...  (z.B. `npm audit fix`, Parent-Update, gezieltes Major-Update)
```

---

## Hinweise

- Dieser Command ist **READ-ONLY** – er führt **kein** `npm audit fix`, **kein** `npm install`
  und ändert keine `package.json` / `pom.xml`. Behebung erfolgt bewusst separat.
- Dev-only-Findings (Build-/Test-Tooling) sind nicht im ausgelieferten Bundle – Severity im
  Kontext bewerten, nicht nur die nackte CVSS-Zahl.
- Benötigt Netzwerkzugriff (npm-Registry-Advisories, NVD-Feed); offline schlägt die Prüfung fehl.
- Ergänzt `20_dependencies-check` (verfügbare Versions-Updates) um die Sicherheits-Perspektive.

## Referenz
* `CLAUDE.md` – Tech-Stack, Modulstruktur, Runtime- vs. Dev-Dependencies
* `scripts/owasp-dependency-check.ps1` – OWASP-Scan-Helfer (liest `NVD_API_KEY` aus der Env-Var)
* `.claude/commands/20_dependencies-check.md` – Versions-Updates (Schwesterkommando)
* `frontend-service/package.json`, `design-system/package.json` – npm-Module
* [npm audit](https://docs.npmjs.com/cli/commands/npm-audit)
* [OWASP Dependency-Check Maven Plugin](https://jeremylong.github.io/DependencyCheck/dependency-check-maven/)
