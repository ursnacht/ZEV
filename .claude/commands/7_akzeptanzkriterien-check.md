# Akzeptanzkriterien-Check

Prüft ob alle Akzeptanzkriterien einer Spec im Code erfüllt sind und aktualisiert die Checkboxen.

## Input

* **Feature-Name**: $ARGUMENTS (z.B. `Debitorkontrolle`) → liest `Specs/[Feature-Name].md`  
  Falls nicht angegeben: aus dem Konversations-Kontext ableiten (z.B. wenn zuvor `/0_anforderungen` oder `/1_umsetzungsplan` ausgeführt wurde); nur wenn unklar: nachfragen.

---

## Sub-Agent Ausführung

> **Als Sub-Agent:** Überspringe diesen Abschnitt und fahre direkt mit **Vorgehen** fort. Analysiere NUR:
> 1. Die Anforderungen in `Specs/[Feature-Name].md`
> 2. Den tatsächlich implementierten Code
> 3. Bestehende Tests und deren Ergebnisse

Starte einen neuen Sub-Agenten mit dem `Agent`-Tool:

- **description:** `"Akzeptanzkriterien-Check: [Feature-Name]"`
- **prompt:**

```
Du prüfst ob alle Akzeptanzkriterien einer Spec im Code erfüllt sind.
Feature: [Feature-Name]

Lies: .claude/commands/7_akzeptanzkriterien-check.md
Fahre ab Abschnitt "Vorgehen" fort.

Leitplanken: nicht committen/stagen/pushen, Umgebung nicht veraendern (Docker/Keycloak)
- siehe CLAUDE.md.
```

- **Hinweis:** Ersetze `[Feature-Name]` im `prompt` mit dem tatsächlichen Wert aus `$ARGUMENTS` (oder dem abgeleiteten Kontext).

---

## Vorgehen

### Phase 1: Akzeptanzkriterien extrahieren
1. Lies die Anforderungen `Specs/[Feature-Name].md`
2. Extrahiere alle Akzeptanzkriterien (Zeilen mit `[ ]` oder `[x]` im Abschnitt "Akzeptanzkriterien")
3. Extrahiere zusätzlich prüfbare Anforderungen aus:
   - Funktionale Anforderungen (FR-1, FR-2, FR-3)
   - Nicht-funktionale Anforderungen (Sicherheit, Kompatibilität)
   - Edge Cases & Fehlerbehandlung

### Phase 2: Code-Analyse pro Kriterium
Für jedes Akzeptanzkriterium zwei Fragen — **beide** müssen mit Ja beantwortet sein, damit es `[x]`
wird (Konvention: `Specs/SPEC.md`, Abschnitt 3):

1. **Ist es im Code erfüllt?** (Prüfpunkte unten)
2. **Prüft ein Test genau dieses Kriterium, und ist er grün?**
   * Steht ein Verweis `(Test: …)` / `(E2E: …)` am Kriterium: Test öffnen und bestätigen, dass er
     das **Kriterium** prüft — nicht nur denselben Code berührt. Ein Verweis auf einen Test, der
     etwas anderes prüft, ist ein Befund.
   * Fehlt der Verweis: passenden Test suchen. Gefunden → Verweis am Kriterium **eintragen**.
     Keiner da → Status **⚠️ OHNE TEST**, Checkbox bleibt `[ ]`.
   * **Ausführen:** Backend- und Frontend-Unit-Tests, auf die verwiesen wird, gezielt laufen lassen
     (`mvn test -Dtest=Klasse`, `npm test -- --include=<datei>`). E2E-Tests nur, wenn der Stack
     läuft; sonst im Bericht „E2E nicht ausgeführt" vermerken und die Checkbox nur setzen, wenn der
     User den letzten Lauf als grün bestätigt hat.
   * `(Nachweis: Datei:Zeile)` genügt nur für statische Sachverhalte ohne Verhalten
     (Übersetzungstext, Spaltenkommentar, Lizenzhinweis).

Prüfpunkte für Frage 1:

#### Navigation / Menü
- Route in `frontend-service/src/app/app.routes.ts` vorhanden?
- Menüeintrag in `frontend-service/src/app/components/navigation/navigation.component.html` vorhanden?
- Richtige Permission im `AuthGuard` (`data.permissions`) konfiguriert?

#### CRUD-Operationen
- Controller-Endpoints vorhanden? (GET, POST, PUT, DELETE)
- Service-Methoden implementiert?
- Frontend-Komponenten (List, Form) vorhanden?
- Kebab-Menü für Bearbeiten/Löschen integriert?

#### Validierungen
- Backend: Validierungslogik im Service vorhanden?
- Frontend: Formularvalidierung implementiert?
- Fehlermeldungen via TranslationService?

#### Übersetzungen (i18n)
- Alle Translation-Keys der Spec in einer Flyway-Migration vorhanden?
- Deutsche Übersetzungstexte mit Umlauten (`ä`/`ö`/`ü`) statt `ae`/`oe`/`ue`?

#### Persistierung
- Flyway-Migration vorhanden?
- Entity mit allen geforderten Feldern?
- Repository mit nötigen Queries?

#### Sicherheit
- `@PreAuthorize("hasAuthority('<permission>')")` mit richtiger Permission?
- `AuthGuard` mit richtiger Permission in Route (`data.permissions`)?

#### Multi-Tenancy
- `org_id` Spalte in Migration?
- `@Filter` / `@FilterDef` auf Entity?

### Phase 3: Ergebnis-Bericht + Spec aktualisieren

1. Zeige dem User den Bericht im folgenden Format **im Chat** (keine separate Datei erstellen):

```markdown
# Akzeptanzkriterien-Check: [Feature-Name]

## Ergebnis: X/Y Kriterien erfüllt

| # | Kriterium | Status | Nachweis |
|---|-----------|--------|----------|
| 1 | Beschreibung... | ✅ OK | Route `/xyz` in app.routes.ts:42; (E2E: mieter.spec.ts › should open via menu) grün |
| 2 | Beschreibung... | ✅ OK | `MieterService.save()` validiert Überlappung (MieterService.java:87); (Test: MieterServiceTest#save_Ueberlappung_Wirft) grün |
| 3 | Beschreibung... | ⚠️ OHNE TEST | Im Code erfüllt (MieterService.java:95), aber kein Test prüft es |
| 4 | Beschreibung... | ❌ FEHLT | Keine Validierung für Mietende > Mietbeginn gefunden |

## Zusätzliche Befunde
- Befunde die nicht direkt ein Akzeptanzkriterium betreffen, aber relevant sind
```

2. Aktualisiere direkt im Anschluss die Spec-Datei:
   * `[x]` **nur** für ✅ OK (erfüllt **und** grüner Test bzw. statischer Nachweis)
   * ⚠️ OHNE TEST und ❌ FEHLT bleiben bzw. werden `[ ]` — auch ein bisheriges `[x]` ohne Test
     wird zurückgesetzt; im Bericht ausdrücklich auflisten, welche zurückgesetzt wurden
   * Gefundene Test-Verweise am Kriterium eintragen (`(Test: …)`, `(E2E: …)`)
   * Sonst nichts an der Spec ändern
3. Im Bericht die **⚠️ OHNE TEST**-Kriterien als Auftrag für `/3`–`/5` zusammenfassen.

---

## Prüfmethoden pro Kriterium-Typ

| Kriterium-Typ | Wie prüfen |
|---------------|-----------|
| "ist aus dem Menü aufrufbar" | Route in `app.routes.ts`, Link in `navigation.component.html` |
| "können erfasst, bearbeitet, gelöscht werden" | POST/PUT/DELETE Endpoints, Form-Component, Kebab-Menü |
| "Validierung X muss gelten" | Service-Code, `@Valid`, Conditional Logic |
| "Rolle/Permission X notwendig" | `@PreAuthorize("hasAuthority(...)")`, `AuthGuard`, Route `data.permissions`; Rolle→Permission in `Specs/Berechtigungen.md` |
| "Fehlermeldung anzeigen" | Exception-Handling, `.zev-message--error`, Translation-Key |
| "Texte mehrsprachig" | Migration mit DE/EN und `ON CONFLICT (key) DO NOTHING`; deutsche Texte mit Umlauten statt `ae`/`oe`/`ue` (`ss` statt `ß` ist korrekt) |
| "Daten in DB speichern" | Flyway-Migration, Entity, Repository |
| "pro Mandant" | `org_id` in Entity/Migration, Hibernate-Filter |
| "Konfiguration aus application.yml entfernt" | Prüfe ob YAML-Block und zugehörige Config-Klasse entfernt |

---

## Wichtige Regeln
* **Konservativ bewerten** - Im Zweifel als "FEHLT" markieren
* **Nachweis liefern** - Für jedes "OK" den Code-Ort (Datei:Zeile) **und** den Test angeben
* **Kein `[x]` ohne Test** - siehe Phase 2; „im Code gesehen" genügt nicht
* **Keine Code-Änderungen** - Nur die Spec-Datei wird aktualisiert (Checkboxen, Test-Verweise), keine neue Datei erstellen

## Referenz
* CLAUDE.md - Projekt-Architektur
* Specs/generell.md - Allgemeine Anforderungen
