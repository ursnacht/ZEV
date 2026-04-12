# Akzeptanzkriterien-Check

Prüft ob alle Akzeptanzkriterien einer Spec im Code erfüllt sind und aktualisiert die Checkboxen.

## Input

* **Feature-Name**: $ARGUMENTS (z.B. `Debitorkontrolle`) → liest `Specs/[Feature-Name].md`  
  Falls nicht angegeben: aus dem Konversations-Kontext ableiten (z.B. wenn zuvor `/0_anforderungen` oder `/1_umsetzungsplan` ausgeführt wurde); nur wenn unklar: nachfragen.

---

## Unabhängige Ausführung

Dieser Skill arbeitet UNABHÄNGIG vom Kontext der aktuellen Session und kann auch mit einem neuen Agenten ausgeführt werden.

**Analysiere NUR:**
1. Die Anforderungen in `Specs/[Feature-Name].md`
2. Den tatsächlich implementierten Code
3. Bestehende Tests und deren Ergebnisse

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
Für jedes Akzeptanzkriterium systematisch prüfen:

#### Navigation / Menü
- Route in `frontend-service/src/app/app.routes.ts` vorhanden?
- Menüeintrag in `frontend-service/src/app/app.component.html` vorhanden?
- Richtige Rolle im `AuthGuard` konfiguriert?

#### CRUD-Operationen
- Controller-Endpoints vorhanden? (GET, POST, PUT, DELETE)
- Service-Methoden implementiert?
- Frontend-Komponenten (List, Form) vorhanden?
- Kebab-Menü für Bearbeiten/Löschen integriert?

#### Validierungen
- Backend: Validierungslogik im Service vorhanden?
- Frontend: Formularvalidierung implementiert?
- Fehlermeldungen via TranslationService?

#### Persistierung
- Flyway-Migration vorhanden?
- Entity mit allen geforderten Feldern?
- Repository mit nötigen Queries?

#### Sicherheit
- `@PreAuthorize` mit richtiger Rolle?
- `AuthGuard` mit richtiger Rolle in Route?

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
| 1 | Beschreibung... | ✅ OK | Route `/xyz` in app.routes.ts:42, Menüeintrag vorhanden |
| 2 | Beschreibung... | ✅ OK | `MieterService.save()` validiert Überlappung (MieterService.java:87) |
| 3 | Beschreibung... | ❌ FEHLT | Keine Validierung für Mietende > Mietbeginn gefunden |

## Zusätzliche Befunde
- Befunde die nicht direkt ein Akzeptanzkriterium betreffen, aber relevant sind
```

2. Aktualisiere direkt im Anschluss die Checkboxen in der Spec-Datei:
   * **Nur erfüllte Kriterien** von `[ ]` auf `[x]` ändern
   * Nicht erfüllte Kriterien bleiben als `[ ]`

---

## Prüfmethoden pro Kriterium-Typ

| Kriterium-Typ | Wie prüfen |
|---------------|-----------|
| "ist aus dem Menü aufrufbar" | Route in `app.routes.ts`, Link in `app.component.html` |
| "können erfasst, bearbeitet, gelöscht werden" | POST/PUT/DELETE Endpoints, Form-Component, Kebab-Menü |
| "Validierung X muss gelten" | Service-Code, `@Valid`, Conditional Logic |
| "Rolle X notwendig" | `@PreAuthorize`, `AuthGuard`, Route `data.roles` |
| "Fehlermeldung anzeigen" | Exception-Handling, `.zev-message--error`, Translation-Key |
| "Daten in DB speichern" | Flyway-Migration, Entity, Repository |
| "pro Mandant" | `org_id` in Entity/Migration, Hibernate-Filter |
| "Konfiguration aus application.yml entfernt" | Prüfe ob YAML-Block und zugehörige Config-Klasse entfernt |

---

## Wichtige Regeln
* **Konservativ bewerten** - Im Zweifel als "FEHLT" markieren
* **Nachweis liefern** - Für jedes "OK" den konkreten Code-Ort angeben (Datei:Zeile)
* **Keine Code-Änderungen** - Nur die Spec-Datei wird aktualisiert (Checkboxen), keine neue Datei erstellen

## Referenz
* CLAUDE.md - Projekt-Architektur
* Specs/generell.md - Allgemeine Anforderungen
