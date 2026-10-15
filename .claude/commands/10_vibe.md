# Vibe → Anforderungsdokument + Umsetzung

Ergänze mit frei formulierten Anforderungen ein bestehendes Anforderungsdokument und setze die neuen Anforderungen direkt um.

## Input

$ARGUMENTS

* **Feature-Name** (erster Parameter): z.B. `Zahlungsverwaltung`
* **Anforderungen** (Rest): Neue oder angepasste Anforderungen, Änderungen oder Rahmenbedingungen zu einem bereits bestehenden Anforderungsdokument.

## Vorgehen
1. **Lies die Referenzdokumente** – `Specs/[Feature-Name].md` und `Specs/generell.md`.
2. **Verstehe die neuen Anforderungen** aus dem Input.
3. **Stelle Rückfragen** bei wesentlichen Unklarheiten, die die Umsetzung beeinflussen – BEVOR du weitermachst.
4. **Ergänze das Anforderungsdokument** mit den zusätzlichen Anforderungen.
    - Ergänze die Funktionalen Anforderungen, die Akzeptanzkriterien und die Nichtfunktionalen Anforderungen wo nötig.
    - **Ändern statt anhängen** (siehe Block unten) – die Spec beschreibt den aktuellen Stand, die Geschichte steht im Anhang.

## 📝 Spec ändern statt anhängen
Ohne diese Regel wuchsen Specs auf über 1000 Zeilen („geändert am …, vorher …" mitten im Text), und
der aktuelle Stand war nur noch mit der ganzen Geschichte zu lesen. Aufräumen von Altlasten:
`/15_anforderungen_konsolidieren`.

* **Die gültige Anforderung direkt umschreiben**, im Präsens – kein „neu seit …", „vorher …",
  „bis 14.10.2026 …" im aktuellen Teil.
* **Die ersetzte Fassung in den Anhang** `## Anhang A – Entscheidungen und Verlauf` am Ende der Spec
  (anlegen, falls er fehlt): eigener Unterabschnitt `### A.n <Thema> (FR-x, <Datum>)` mit dem, was
  vorher galt, und dem Grund der Änderung.
* **Kurze Begründungen** (1–3 Sätze) bleiben als `>`-Block bei der Anforderung; lange Begründungen,
  Fehlergeschichten und Belege kommen in den Anhang, beim FR ein Verweis „(Hintergrund: Anhang A.n)".
* **Ersetzte Akzeptanzkriterien** werden umgeschrieben oder in den Anhang verschoben – nicht stehen
  gelassen, sie wären sonst ein falscher Prüfauftrag. Neue Kriterien in die passende Gruppe in
  Abschnitt 3, nicht als eigener Nachtragsblock.
* **FR-/NFR-Nummern nie ändern** – der Code verweist darauf. Neue Anforderung: nächste freie Nummer
  oder Unternummer (`FR-5b`).
* **Kein Umsetzungsstand im Fliesstext** – kein „umgesetzt", „Stand …", „fehlt noch". Die Spec
  beschreibt das Soll; den Stand zeigen die Checkboxen und der Umsetzungsplan.
* Der **Umsetzungsplan** bleibt ein Protokoll: Dort ist ein datierter Nachtrag richtig.
5. **Setze die neuen Anforderungen um** gemäss den Vorgaben in `Specs/generell.md`.
    - **Design System zuerst prüfen** (siehe Block unten) – kein UI-CSS ohne diese Prüfung.
6. **Kompiliere und teste** – Frontend-Build und betroffene Unit-Tests müssen grün sein (Befehle: `.claude/commands/2_umsetzung.md`, Abschnitt „Validierung").
    - Jedes neue oder geänderte Akzeptanzkriterium bekommt einen Test, und der Verweis steht am Kriterium (`Specs/SPEC.md`, Abschnitt 3). **Die Checkbox setzt du nicht selbst.**
7. **Prüfen – Anforderungen und Vorlagen**, zwei Sub-Agenten **parallel** (in einer Nachricht starten):
    - `/7_akzeptanzkriterien-check`, beschränkt auf die in diesem Durchgang neuen oder geänderten Kriterien (im Prompt aufzählen). Er setzt die Checkboxen; ⚠️ OHNE TEST oder ❌ FEHLT beheben und erneut prüfen.
    - `/9_vorlagen-check` auf die nicht committeten Änderungen. ❌ und ⚠️ beheben (oder im Code begründen) und erneut prüfen.
    - Beide Berichte unverändert weitergeben.
8. **Sichtprüfung im Browser** (bei UI-Änderungen) – siehe Block unten. Ohne UI-Änderung (reines Backend, nur Spec) entfällt sie; das im Abschlussbericht ausdrücklich sagen.

## ⚖️ Kurzer Weg – Änderungen ohne neues Verhalten
Der Aufwand soll zur Änderung passen. **Ohne neues oder geändertes Verhalten** – reine Layout-,
Text- oder Stilkorrekturen (z.B. eine Schaltfläche verschieben, eine Beschriftung ändern, einen
Abstand korrigieren) – gilt:

* **Schritt 7 entfällt** (kein Akzeptanzkriterien- und kein Vorlagen-Check per Sub-Agent). Ein bestehendes Kriterium,
  das die Änderung betrifft, wird angepasst; seine Checkbox und sein Test-Verweis bleiben, sofern
  der Test weiterhin grün ist.
* **Kein neuer Test nur für die Position** eines Elements. Bestehende Tests anpassen, die an der
  alten Darstellung hängen.
* **Die Sichtprüfung (Schritt 8) ist der Nachweis** – bei UI-Änderungen also gerade nicht
  auslassen.
* Spec und Umsetzungsplan werden trotzdem nachgeführt (Schritt 4), knapp.

**Verhalten ändert sich, sobald** Daten anders geladen, gespeichert, berechnet, validiert oder
berechtigt werden, ein Ablauf andere Schritte hat oder etwas Neues erscheint, das vorher fehlte
(z.B. ein Diagramm, das nun auch ohne Entscheide zeichnet). Dann gilt der volle Ablauf. **Im Zweifel
der volle Ablauf** – und im Abschlussbericht sagen, welcher Weg gewählt wurde und warum.

## 👁️ Sichtprüfung im Browser – bei UI-Änderungen
Grüne Unit-Tests sagen nicht, dass es **aussieht** wie gewollt. Belegt am 14.10.2026: Alle Tests der
Einspeisesteuerung waren grün, im Browser lag der neue Hinweis aber unterhalb der Diagrammfläche,
ausserhalb des sichtbaren Bereichs.

1. **Rebuild-Befehl nennen und auf den User warten** – die Umgebung gehört dem User (CLAUDE.md), z.B.
   `docker compose up -d --build frontend-service`. Erst nach seiner Bestätigung weiter.
2. **Prüfen, dass der Container die Änderung enthält** – `docker ps --format "{{.Names}} {{.CreatedAt}}"`
   (nur lesend) gegen die Änderungszeit der Dateien. Sonst prüft man den alten Stand.
3. **Temporäres Playwright-Skript** `frontend-service/tests/zz-sichtpruefung.spec.ts` gegen
   `http://localhost:8000` (Login über `navigateToHome` aus `tests/helpers.ts`, Testuser `testuser`):
   * Nur `chromium`, ausführen mit `npx playwright test tests/zz-sichtpruefung.spec.ts --project=chromium --retries=0 --reporter=line`.
   * `page.on('console')` und `page.on('pageerror')` mitschreiben – Fehler in der Konsole sind ein Befund.
   * Screenshot in die Scratchpad (`page.screenshot({ path, fullPage: true })`) und mit dem Read-Tool **ansehen**.
     Bei Farben oder Kontrasten zusätzlich im Dark Mode (`page.emulateMedia({ colorScheme: 'dark' })`).
   * Gezielt prüfen, was die Änderung verspricht: Position, Sichtbarkeit **ohne Scrollen**, Abstände,
     Umbruch bei schmaler Breite (`page.setViewportSize({ width: 390, height: 844 })`), Texte übersetzt
     (kein roher Key).
4. **Nur lesen, nichts verändern:** Keine Datensätze über die UI anlegen, ändern oder löschen; keine
   DB-, Keycloak- oder Feature-Flag-Änderungen. Fehlen dem Testuser Flag oder Daten (anderer Mandant),
   die Antworten **nur im Browser** mit `page.route(...)` umschreiben (z.B. `/api/feature-flags` auf
   `true`, eine Liste vorgeben) und das im Bericht nennen.
5. **Aufräumen:** Skript nach `scratchpad/` verschieben oder löschen, `test-results/zz-sichtpruefung*`
   entfernen. Es wird **nie** committet – `git status` muss danach nur die eigentlichen Änderungen zeigen.
6. **Befund:** Weicht etwas ab, korrigieren und die Sichtprüfung wiederholen. Im Abschlussbericht
   sagen, was angesehen wurde – und was nicht (z.B. „nur mit vorgegebenen Daten“).

## 🎨 Design System – zwingend beachten (bei UI-Änderungen)
* **Kein neues UI-CSS ohne vorherige Prüfung des Design Systems.** Bestehende Klassen wiederverwenden, wiederverwendbare Styles gehören ins Design System – Details, Klassenliste und Regeln stehen in `Specs/generell.md` (Abschnitt „Design System"). Dort nachschlagen, nicht hier duplizieren.

## ⚠️ Datenbank-Migrationen (Flyway) – zwingend beachten
* **Ändere NIEMALS ein bestehendes Migrationsskript** (auch keines, das du in derselben Session erstellt hast), bevor du nicht via MCP-Server `zev-db` geprüft hast, ob es bereits ausgeführt wurde:
  `SELECT version, success FROM zev.flyway_schema_history WHERE version = '<NR>';`
* Wurde die Migration bereits ausgeführt (Treffer in `flyway_schema_history`), lege **immer eine neue Migration** mit der nächsthöheren Versionsnummer an – auch für kleine Ergänzungen wie zusätzliche Übersetzungs-Keys.
* Neue Übersetzungen immer mit `ON CONFLICT (key) DO NOTHING`.
* **Deutsche Übersetzungstexte immer mit Umlauten** (`ä`/`ö`/`ü`) – nie `ae`/`oe`/`ue`. **Kein `ß`**: Schweizer Schreibweise, also `ss` („Strasse", „gemäss"). Betrifft nur die Textspalte `deutsch`, nicht die Keys. Details: `Specs/generell.md`, Abschnitt „Mehrsprachigkeit (i18n)".
* Prüfe die höchste vergebene Versionsnummer, bevor du eine neue Migration anlegst (Dateien in `backend-service/src/main/resources/db/migration/`).

## Output
* Ergänze die bestehende Anforderungsdatei: `Specs/[Feature-Name].md`
* Ergänze den bestehenden Umsetzungsplan (falls vorhanden): `Specs/[Feature-Name]_Umsetzungsplan.md`
* Weiche nicht von der Struktur des Templates `Specs/SPEC.md` ab
* Implementiere die neuen Anforderungen im Anwendungscode
* Passe die bestehenden Tests falls nötig an
* Neue/geänderte Akzeptanzkriterien mit Test-Verweis, Checkboxen von `/7_akzeptanzkriterien-check` gesetzt
* `/9_vorlagen-check` ohne offene ❌/⚠️
* Bei UI-Änderungen: Sichtprüfung im Browser durchgeführt (Screenshot angesehen) – oder begründet, warum nicht

## Referenz
* `Specs/[Feature-Name].md` - das bestehende und zu ergänzende Anforderungsdokument
* `Specs/[Feature-Name]_Umsetzungsplan.md` - der zu ergänzende Umsetzungsplan (falls vorhanden) 
* `Specs/SPEC.md` – Template-Struktur
* `Specs/generell.md` – Allgemeine Projektanforderungen (i18n, Design System, Multi-Tenancy)
* `Specs/Übersetzungsverwaltung.md` – Beispiel einer vollständigen Spec