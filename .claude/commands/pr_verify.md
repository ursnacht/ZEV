# PR Verify

Prüft einen Pull Request auf Code-Qualität, Korrektheit und Projektkonventionen.

## Input
* PR-Nummer oder Branch: $ARGUMENTS (z.B. `42`, `feature/mieterverwaltung`, oder leer für den aktuellen Branch)

---

## Sub-Agent Ausführung

> **Als Sub-Agent:** Überspringe diesen Abschnitt und fahre direkt mit **Vorgehen** fort. Analysiere NUR:
> 1. Den tatsächlichen Diff des PRs / Branches gegenüber `main`
> 2. Den betroffenen Code und seine Abhängigkeiten
> 3. Projekt-Konventionen aus `CLAUDE.md` und `Specs/generell.md`

Starte einen neuen Sub-Agenten mit dem `Agent`-Tool:

- **description:** `"PR Verify: [PR-Nummer/Branch]"`
- **prompt:**

```
Du prüfst einen Pull Request auf Code-Qualität, Korrektheit und Projektkonventionen.
PR/Branch: [PR-Nummer oder Branch-Name]

Lies: .claude/commands/pr_verify.md
Fahre ab Abschnitt "Vorgehen" fort.

Leitplanken: nicht committen/stagen/pushen, Umgebung nicht veraendern (Docker/Keycloak)
- siehe CLAUDE.md.
```

- **Hinweis:** Ersetze `[PR-Nummer oder Branch-Name]` im `prompt` mit dem tatsächlichen Wert aus `$ARGUMENTS` (oder dem aktuellen Branch falls leer).

---

## Vorgehen

### Phase 1: Diff ermitteln

Falls eine PR-Nummer angegeben:
```bash
gh pr diff <nummer>
gh pr view <nummer> --json title,body,additions,deletions,changedFiles,baseRefName,headRefName
```

Falls ein Branch-Name oder kein Argument angegeben:
```bash
git diff main...HEAD --stat
git diff main...HEAD
git log main..HEAD --oneline
```

### Phase 2: Geänderte Dateien kategorisieren

Kategorisiere alle geänderten Dateien:
- **Backend:** `*.java` in `backend-service/src/main/`
- **Backend Tests:** `*.java` in `backend-service/src/test/`
- **Frontend:** `*.ts`, `*.html`, `*.css` in `frontend-service/src/`
- **Frontend Tests:** `*.spec.ts`
- **E2E Tests:** `frontend-service/tests/*.spec.ts`
- **Flyway Migrations:** `db/migration/V*.sql`
- **PDF-Templates:** `backend-service/src/main/resources/reports/*.jrxml`
- **Design System:** `design-system/src/`
- **Config / Infra:** `docker-compose.yml`, `*.yml`, `*.properties`

### Phase 3: Validierungen durchführen

Führe alle relevanten Checks durch (nur für geänderte Bereiche):

#### 3.1 Kompilierbarkeit
```bash
# Backend (falls Java-Dateien geändert)
cd backend-service && mvn compile -q 2>&1 | tail -20

# Frontend (falls TS/HTML geändert)
cd frontend-service && npx ng build --configuration=development 2>&1 | tail -20

# Design System (falls Design-System geändert)
cd design-system && npm run build 2>&1 | tail -10
```

#### 3.2 Tests ausführen
```bash
# Backend Unit Tests (falls Java-Dateien geändert)
cd backend-service && mvn test -q 2>&1 | tail -30

# Frontend Unit Tests (falls spec.ts-Dateien geändert)
cd frontend-service && npm.cmd test 2>&1 | tail -30

# Einzelne Test-Klasse (für schnelles Feedback)
cd backend-service && mvn test -Dtest=<GeändertesKlassenTest> -q 2>&1 | tail -20

# PDF-Templates (falls *.jrxml geändert) - Pflicht laut CLAUDE.md
cd backend-service && mvn test -Dtest=JasperTemplateCompileTest -q 2>&1 | tail -20
```

#### 3.3 Backend-Konventionen prüfen

Für jede geänderte `*Service.java`:
- [ ] `hibernateFilterService.enableOrgFilter()` am Anfang **jeder** Methode?
- [ ] `@Transactional(readOnly = true)` für Lese-Methoden?
- [ ] `@Transactional` für Schreib-Methoden?
- [ ] Neue Entities: `orgId` via `organizationContextService.getCurrentOrgId()` gesetzt?
- [ ] Delete-Methoden geben `boolean` zurück?
- [ ] Methoden-Reihenfolge: `getAll` → `getById` → `save` → `delete` → Business-Logic?

Für jede geänderte `*Controller.java`:
- [ ] `@PreAuthorize` auf Klassen-Ebene (nicht pro Methode)?
- [ ] POST gibt `HttpStatus.CREATED` zurück?
- [ ] DELETE gibt `204 No Content` oder `404 Not Found` zurück?
- [ ] `catch (IllegalArgumentException e)` → `badRequest().body(e.getMessage())`?
- [ ] Richtige Permission in `@PreAuthorize("hasAuthority('<permission>')")` — passend zu `Specs/Berechtigungen.md`?

Für jede neue `*Entity.java`:
- [ ] `@FilterDef` und `@Filter` für Multi-Tenancy vorhanden?
- [ ] `org_id` Feld vorhanden?
- [ ] Zugehörige Flyway-Migration vorhanden?

#### 3.4 Frontend-Konventionen prüfen

Für jede geänderte `*.component.ts`:
- [ ] Standalone Component (`standalone: true`)?
- [ ] Neue Control-Flow-Syntax (`@if`, `@for`) – kein `*ngIf`/`*ngFor`?
- [ ] `subscribe({ next, error })` Objekt-Syntax (nicht `.subscribe(data => ...)`)?
- [ ] Kein hartcodierter UI-Text (alles via `TranslatePipe` / `TranslationService`)?
- [ ] Error-Messages bleiben, Success-Messages verschwinden nach 5 Sekunden?

Für jede geänderte `*.service.ts`:
- [ ] HTTP-URL korrekt (kein trailing slash, kein hardcoded Port ausser in Tests)?

Für jede neue Route in `app.routes.ts`:
- [ ] `AuthGuard` mit korrekter Permission (`data: { permissions: [...] }`) konfiguriert?
- [ ] Menüeintrag in `navigation.component.html` vorhanden?

#### 3.5 Migrations prüfen

**Zuerst — Blocker, falls verletzt:** Ändert der Diff eine **bestehende** Migration?
```bash
git diff main...HEAD --name-status -- backend-service/src/main/resources/db/migration/ | grep -v '^A'
```
Jede Zeile ausser `A` (neu hinzugefügt) ist ein **❌ Blocker**: Bereits ausgeführte Migrationen dürfen
nie geändert werden — die Checksum-Prüfung bricht sonst beim nächsten Start (CLAUDE.md). Korrektur
gehört in eine neue Migration.

Für jede neue `V*.sql`:
- [ ] Versionsnummer ist höher als alle bestehenden Migrationen?
- [ ] `COMMENT ON COLUMN` für alle neuen Spalten vorhanden?
- [ ] Multi-Tenancy: `org_id BIGINT NOT NULL` in neuen Tabellen?
- [ ] Naming-Konvention: `V[nummer]__[beschreibung].sql`?
- [ ] Übersetzungs-Inserts: deutsche Texte mit Umlauten (`ä`/`ö`/`ü`) statt `ae`/`oe`/`ue`?

#### 3.6 Sicherheits-Check

Prüfe im Diff auf:
- [ ] Keine Secrets / Credentials im Code (Passwörter, API-Keys, Tokens)?
- [ ] Keine `System.out.println()` oder `console.log()` mit sensiblen Daten?
- [ ] SQL: Kein String-Concatenation in Queries (SQL-Injection-Risiko)?
- [ ] Keine `@CrossOrigin("*")` ohne Begründung?

#### 3.7 Test-Coverage prüfen

- [ ] Für jede neue `*Service.java`: Existiert `*ServiceTest.java`?
- [ ] Für jeden neuen `*Controller.java`: Existiert `*ControllerTest.java`?
- [ ] Für jede neue `*.component.ts`: Existiert `*.component.spec.ts`?
- [ ] Für jede neue `*.service.ts`: Existiert `*.service.spec.ts`?

---

## Phase 4: Bericht ausgeben

Gib den vollständigen Bericht im folgenden Format aus:

```markdown
# PR Verify: [Branch/PR-Titel]

## Übersicht
- Branch: `feature/xxx` → `main`
- Geänderte Dateien: X (Backend: A, Frontend: B, Tests: C, Migrations: D)
- Additions: +X, Deletions: -Y

## Build & Tests

| Check | Status | Details |
|-------|--------|---------|
| Backend kompiliert | ✅ / ❌ | |
| Frontend kompiliert | ✅ / ❌ | |
| Backend Unit Tests | ✅ / ❌ | X/Y Tests bestanden |
| Frontend Unit Tests | ✅ / ❌ | X/Y Tests bestanden |

## Konventions-Check

### Backend
| Datei | Check | Status | Bemerkung |
|-------|-------|--------|-----------|
| XxxService.java | enableOrgFilter | ✅ | |
| XxxService.java | @Transactional | ⚠️ | readOnly fehlt bei getAll() |
| XxxController.java | @PreAuthorize | ✅ | |

### Frontend
| Datei | Check | Status | Bemerkung |
|-------|-------|--------|-----------|
| xxx.component.ts | Standalone | ✅ | |
| xxx.component.ts | Kein *ngIf | ✅ | |
| xxx.component.html | Kein hardcoded Text | ❌ | Zeile 42: "Speichern" ohne TranslatePipe |

### Migrations
| Datei | Check | Status | Bemerkung |
|-------|-------|--------|-----------|
| V5__xxx.sql | Versionsnummer | ✅ | |
| V5__xxx.sql | COMMENT ON COLUMN | ⚠️ | Spalte 'beschreibung' ohne Kommentar |

## Sicherheit
| Check | Status | Bemerkung |
|-------|--------|-----------|
| Keine Secrets | ✅ | |
| Kein SQL-Injection-Risiko | ✅ | |

## Test-Coverage
| Implementierung | Test | Status |
|-----------------|------|--------|
| XxxService.java | XxxServiceTest.java | ✅ |
| XxxController.java | XxxControllerTest.java | ❌ FEHLT |

## Zusammenfassung

### Kritische Probleme (Blocker)
1. ...

### Warnungen (sollten behoben werden)
1. ...

### Hinweise (optional)
1. ...

### Gesamturteil
✅ PR ist merge-ready / ⚠️ Kleinere Probleme / ❌ Kritische Probleme müssen behoben werden
```

---

## Symbole

| Symbol | Bedeutung |
|--------|-----------|
| ✅ | Erfüllt / Test bestanden |
| ⚠️ | Warnung – sollte behoben werden |
| ❌ | Kritisches Problem – muss behoben werden |
| ➖ | Nicht anwendbar |

---

## Wichtige Regeln

* **READ-ONLY**: Dieser Command ändert keinen Code – er analysiert und berichtet nur
* **Nur relevante Checks**: Prüfe nur Bereiche, die im Diff tatsächlich geändert wurden
* **Konkrete Nachweise**: Für jedes Problem Datei:Zeile angeben
* **Konservativ bewerten**: Im Zweifel als Warnung oder Blocker markieren

## Referenz
* `CLAUDE.md` – Architektur, Konventionen, Build-Befehle
* `Specs/generell.md` – i18n, Design System, Multi-Tenancy
