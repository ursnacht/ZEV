# Vorlagen-Check

Prüft geänderten Code gegen die **Vorlagen und Konventionen** des Projekts: Struktur wie die
Vorlagen in `CLAUDE.md`, verbindliche Regeln aus `/2_umsetzung`, Design System und i18n aus
`Specs/generell.md` — und auf Redundanz.

## Input

* **Umfang:** $ARGUMENTS
  * leer → die **nicht committeten** Änderungen (`git diff HEAD` + neue Dateien)
  * Commit-Bereich → z.B. `HEAD~3..HEAD` oder `9e408d1..HEAD`
  * Datum → z.B. `seit 2026-10-01` (alle Commits auf `main` seither)

> **Warum ohne PR:** Gearbeitet wird direkt auf `main`. `pr_verify` prüft dieselben Regeln für
> Branches; dieser Command ist die Prüfung für den Alltag — vor dem Commit oder rückblickend.

---

## Sub-Agent Ausführung

> **Als Sub-Agent:** Überspringe diesen Abschnitt und fahre direkt mit **Vorgehen** fort. Analysiere
> NUR den Diff, die Vorlagen-Dateien und die Regeln — nicht die Absicht hinter der Änderung.

Starte einen neuen Sub-Agenten mit dem `Agent`-Tool:

- **description:** `"Vorlagen-Check: [Umfang]"`
- **prompt:**

```
Du pruefst geaenderten Code gegen die Vorlagen und Konventionen des Projekts.
Umfang: [Umfang]

Lies: .claude/commands/9_vorlagen-check.md
Fahre ab Abschnitt "Vorgehen" fort.

Leitplanken: READ-ONLY - nichts aendern, nicht committen/stagen/pushen, Umgebung nicht
veraendern (Docker/Keycloak) - siehe CLAUDE.md.
```

- **Hinweis:** `[Umfang]` durch `$ARGUMENTS` ersetzen („nicht committete Änderungen", wenn leer).

> **Warum ein Sub-Agent:** Wer den Code eben geschrieben hat, liest ihn als das, was er gemeint hat.
> Eine Abweichung von der Vorlage, die beim Schreiben „hier passt es halt anders" war, fällt nur
> einem Leser auf, der die Begründung nicht kennt.

---

## Vorgehen

### Phase 1: Diff und Regeln laden

1. Diff ermitteln:
   ```bash
   # leer: nicht committet
   git diff HEAD --stat && git diff HEAD && git status --short
   # Bereich
   git diff <von>..<bis> --stat && git diff <von>..<bis>
   # Datum
   git log --since=<datum> --oneline main && git diff $(git rev-list -1 --before=<datum> main)..HEAD
   ```
2. Regeln lesen — **die Quelle, nicht eine Abschrift**:
   * `CLAUDE.md`, Abschnitt „Code-Vorlagen für deterministische Generierung" — und die dort
     genannte **Vorlage-Datei** zu jeder geänderten Datei gleicher Art **öffnen**
   * `.claude/commands/2_umsetzung.md`, Abschnitte „Verbindliche Regeln Backend/Frontend"
   * `Specs/generell.md`, Abschnitte Design System und Mehrsprachigkeit

### Phase 2: Prüfen

Nur, was im Diff vorkommt. Für jede geänderte Datei die passende Vorlage danebenlegen.

#### 2.1 Struktur wie die Vorlage
* Aufbau, Reihenfolge der Methoden, Import-Reihenfolge, Benennung wie in der Vorlage?
* Neue Datei einer Art, für die es eine Vorlage gibt: folgt sie ihr — oder ist sie aus einer
  **anderen** Datei abgeleitet, die selbst von der Vorlage abweicht?

#### 2.2 Backend (`*Service.java`, `*Controller.java`, Entities)
* Service: `hibernateFilterService.enableOrgFilter()` am Anfang **jeder** öffentlichen Methode;
  `@Transactional(readOnly = true)` für Lesen, `@Transactional` für Schreiben; neue Entities setzen
  `orgId` über `organizationContextService.getCurrentOrgId()` (Jobs: org-explizit); Delete gibt
  `boolean` zurück; Validierungsfehler als `IllegalArgumentException`
* Controller: `@PreAuthorize("hasAuthority('<permission>')")` auf **Klassenebene**, Permission
  passend zu `Specs/Berechtigungen.md`; POST → `201 CREATED`; DELETE → `204`/`404`;
  `IllegalArgumentException` → `badRequest().body(e.getMessage())`
* Entity: `org_id`, `@FilterDef` + `@Filter`; zugehörige Migration vorhanden

#### 2.3 Frontend (`*.component.ts/html`, `*.service.ts`, Routen)
* Standalone; `@if`/`@for` statt `*ngIf`/`*ngFor`; `subscribe({ next, error })`
* Kein hartcodierter UI-Text — alles über `TranslatePipe`/`TranslationService`
* Erfolgsmeldungen verschwinden nach 5 s, Fehler bleiben
* **Design System:** neues CSS nur, wenn keine bestehende Klasse passt (`Specs/generell.md`);
  wiederverwendbare Styles gehören nach `design-system/src/components/`, nicht ins Komponenten-CSS
* Neue Route: `AuthGuard` mit `data.permissions`, Menüeintrag vorhanden

#### 2.4 Migrationen
* **❌ Blocker:** eine **bestehende** Migration geändert (`git diff … --name-status -- backend-service/src/main/resources/db/migration/ | grep -v '^A'`) — sie darf nie geändert werden (CLAUDE.md)
* Neue `V*.sql`: Nummer höher als alle bestehenden; `COMMENT ON COLUMN` für neue Spalten;
  `org_id BIGINT NOT NULL` in neuen Tabellen; Übersetzungen mit `ON CONFLICT (key) DO NOTHING`,
  deutsch mit Umlauten und `ss` statt `ß`

#### 2.5 Tests
* Testklassen folgen den Test-Vorlagen aus `CLAUDE.md` (Service Test, Controller Test, Component
  Test, Transaktions-IT); Vitest-Regeln aus `/4_frontend-unit-tests`
* Für neue Services, Controller, Komponenten existiert die zugehörige Testdatei

#### 2.6 Redundanz
* Gibt es die neue Logik schon? Im Diff nach neuen Hilfsmethoden, Konstanten, Formatierungen,
  Datums- und Zahlenumrechnungen suchen und im Bestand gegenprüfen (`utils/`, `ch.nacht.util`,
  bestehende Services). Doppelte Logik läuft auseinander — genau das war bei `SOC_TIEF` die Gefahr,
  bis die Prüfung herausgelöst wurde (`SteuerRegelService.socTief`).
* Innerhalb des Diffs: derselbe Block zweimal (kopiert statt herausgezogen)?

#### 2.7 Begründete Abweichungen
Eine Abweichung, die **im Code begründet** ist (Kommentar, der sagt, warum hier anders), ist kein
Befund, sondern ➖ mit Verweis auf den Kommentar — z.B. ein Job ohne Sicherheitskontext, der
`enableOrgFilter(orgId)` org-explizit aufruft. Eine Abweichung **ohne** Begründung ist ⚠️.

### Phase 3: Bericht

**Im Chat**, keine Datei:

```markdown
# Vorlagen-Check: [Umfang]

## Ergebnis: X Blocker, Y Warnungen, Z Hinweise

| Datei:Zeile | Regel | Status | Befund | Vorlage / Quelle |
|---|---|---|---|---|
| XxxService.java:42 | enableOrgFilter | ⚠️ | fehlt in `getById` | TarifService.java:38 |
| xxx.component.html:17 | i18n | ❌ | „Speichern" hartcodiert | generell.md, Mehrsprachigkeit |
| SteuerungService.java:150 | Org-Filter | ➖ | org-explizit, im Kommentar begründet (Job) | — |
| foo-utils.ts:12 | Redundanz | ⚠️ | `formatDatum` doppelt zu `utils/date-utils.ts:30` | — |

## Zusammenfassung
### Blocker
### Warnungen
### Hinweise
```

| Symbol | Bedeutung |
|---|---|
| ❌ | Blocker – muss vor dem Commit behoben werden |
| ⚠️ | Abweichung ohne Begründung – beheben oder begründen |
| ➖ | Abweichung mit Begründung im Code – kein Handlungsbedarf |

## Wichtige Regeln
* **READ-ONLY** – berichten, nicht ändern
* **Quelle statt Abschrift** – Regeln immer aus `CLAUDE.md`, `/2_umsetzung` und `generell.md` lesen;
  dieser Command zählt sie nur auf, um nichts zu vergessen
* **Nachweis** – für jeden Befund Datei:Zeile **und** die Stelle in der Vorlage oder Regel
* **Nur der Diff** – Altbestand ausserhalb des Diffs ist kein Befund, auch wenn er abweicht

## Referenz
* `CLAUDE.md` – Code-Vorlagen
* `.claude/commands/2_umsetzung.md` – verbindliche Regeln
* `Specs/generell.md` – Design System, i18n
* `.claude/commands/pr_verify.md` – dieselben Prüfungen für Branches
