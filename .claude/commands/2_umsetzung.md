# Umsetzung

Setze einen Umsetzungsplan schrittweise um.

## Input

* **Feature-Name**: $ARGUMENTS (z.B. `Debitorkontrolle`) → liest `Specs/[Feature-Name]_Umsetzungsplan.md`  
  Falls nicht angegeben: aus dem Konversations-Kontext ableiten (z.B. wenn zuvor `/0_anforderungen` oder `/1_umsetzungsplan` ausgeführt wurde); nur wenn unklar: nachfragen.

## Vorgehen
1. **Lies den Umsetzungsplan** `Specs/[Feature-Name]_Umsetzungsplan.md` – verstehe alle Phasen und deren Status
2. **Identifiziere nächste Phase** - Finde die erste Phase mit `[ ]` (nicht erledigt)
3. **Implementiere die Phase** - Setze die beschriebenen Änderungen um (Patterns unten beachten!)
4. **Kompiliere und prüfe** - Stelle sicher, dass der Code kompiliert (siehe Validierung)
5. **Aktualisiere den Status** - Markiere die Phase mit `[x]` als erledigt
6. **Wiederhole** - Fahre mit der nächsten Phase fort

## Konventionen
* **Design System (WICHTIG):**
    * **Immer zuerst prüfen:** Vor dem Erstellen neuer CSS-Styles im Design System nachschauen (`design-system/src/components/`)
    * **Verfügbare Komponenten:** Siehe `Specs/generell.md` (Abschnitt Design System) für die vollständige Liste
    * **Neue Styles ins Design System:** Wiederverwendbare Styles gehören in `design-system/src/components/`
    * **Komponenten-CSS minimal halten:** Nur komponentenspezifische Styles, keine Duplikate
    * **Design System bauen:** Nach Änderungen `cd design-system && npm run build`
    * **Design System Showcase:** bei neu erstellten Komponenten oder Styles ergänzen
* **Datenbank:** 
  * **Flyway-Migrationen** in `backend-service/src/main/resources/db/migration/`
  * **Naming:** Migrations `V[nummer]__[beschreibung].sql`
  * **Spalten-Kommentare:** Jede neue Spalte mit `COMMENT ON COLUMN app.[tabelle].[spalte] IS '...'` dokumentieren
* **Code-Vorlagen:** Verwende die Vorlagen aus CLAUDE.md (Abschnitt "Code-Vorlagen für deterministische Generierung")

## Validierung nach jeder Phase
* **Backend-Änderungen:** `cd backend-service && mvn compile -q` (muss fehlerfrei kompilieren)
* **Frontend-Änderungen:** `cd frontend-service && npx ng build --configuration=development 2>&1 | head -20` (muss fehlerfrei kompilieren)
* **Design-System-Änderungen:** `cd design-system && npm run build`

## Wichtige Regeln
* **Keine Tests erstellen** - Tests werden separat mit anderen Commands erstellt
* **Inkrementell arbeiten** - Eine Phase nach der anderen abschliessen
* **Status aktuell halten** - Umsetzungsplan nach jeder Phase aktualisieren
* **Kompilierbarkeit sicherstellen** - Nach jeder Phase validieren (siehe oben)
* **Deterministische Patterns** - Code MUSS exakt den unten definierten Patterns folgen

---

## Code-Patterns

Die Struktur jeder Datei wird aus den **Template-Dateien in CLAUDE.md** übernommen (Abschnitt "Code-Vorlagen für deterministische Generierung"). Lies die entsprechende Vorlage und passe sie an den neuen Use Case an.

| Neuer Code | Vorlage lesen |
|------------|---------------|
| Entity | `backend-service/src/main/java/ch/nacht/entity/Tarif.java` |
| Repository | `backend-service/src/main/java/ch/nacht/repository/TarifRepository.java` |
| Service | `backend-service/src/main/java/ch/nacht/service/TarifService.java` |
| Controller | `backend-service/src/main/java/ch/nacht/controller/TarifController.java` |
| Angular Model | `frontend-service/src/app/models/tarif.model.ts` |
| Angular Service | `frontend-service/src/app/services/tarif.service.ts` |
| List-Component | `frontend-service/src/app/components/tarif-list/` |
| Form-Component | `frontend-service/src/app/components/tarif-form/` |

### Verbindliche Regeln Backend

**Import-Reihenfolge:** Projekt-Imports → Framework-Imports → Java-Standard-Imports (je alphabetisch)

**Service:**
* `hibernateFilterService.enableOrgFilter()` am Anfang **jeder** Methode
* `@Transactional(readOnly = true)` für Lese-Methoden, `@Transactional` für Schreib-Methoden
* Validierungsfehler → `throw new IllegalArgumentException("Meldung")`
* Neue Entities: `orgId` setzen via `organizationContextService.getCurrentOrgId()`
* Delete: gibt `boolean` zurück (true = gelöscht, false = nicht gefunden)
* Logging: `log.info()` für Operationen, `log.warn()` für nicht-gefunden
* Methoden-Reihenfolge: `getAll` → `getById` → `save` → `delete` → Business-Logic

**Controller:**
* `@PreAuthorize` auf Klassen-Ebene (nicht pro Methode)
* GET all: gibt `List` direkt zurück (kein `ResponseEntity`)
* GET by id: `Optional.map()` / `orElseGet()` Pattern
* POST: gibt `HttpStatus.CREATED` zurück
* PUT: prüft Existenz zuerst, dann Update
* DELETE: `204 No Content` oder `404 Not Found`
* POST/PUT: `catch (IllegalArgumentException e)` → `badRequest().body(e.getMessage())`

### Verbindliche Regeln Frontend

**Angular-Templates:** Immer neue Control-Flow-Syntax (`@if`, `@for`) – kein `*ngIf`/`*ngFor`

**List-Component:**
* `subscribe({ next, error })` Objekt-Syntax (nicht `.subscribe(data => ...)`)
* Success-Messages verschwinden nach 5 Sekunden (`setTimeout`), Error-Messages bleiben
* Kopieren: `const { id, ...ohneId } = entity` Pattern
* Error-Handling: `error.error || 'FALLBACK_KEY'` (Backend-Meldung oder Fallback)
* Nach jeder Mutation `load[Entities]()` aufrufen
* Methoden-Reihenfolge: `ngOnInit` → `load` → `onCreateNew` → `onEdit` → `onCopy` → `onDelete` → `onMenuAction` → `onFormSubmit` → `onFormCancel` → `onSort` → `showMessage` → `dismissMessage`

**Form-Component:**
* Kommunikation nur via `@Input` / `@Output` (kein direkter Service-Aufruf)
* `isFormValid()` mit `!!()` Pattern
* `ngOnInit`: Input-Objekt kopieren (`{ ...this.entity }`) oder Defaults setzen

---

## Referenz
* `CLAUDE.md` - Template-Dateien und Projekt-Architektur
* `Specs/generell.md` - i18n, Design System, Multi-Tenancy
