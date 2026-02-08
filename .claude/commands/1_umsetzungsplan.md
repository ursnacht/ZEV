# Erstelle Umsetzungsplan

Erstelle einen detaillierten Umsetzungsplan für eine Spezifikation.

## Input
* Spezifikation: $ARGUMENTS (z.B. `Specs/Tarifverwaltung.md` oder kurz `Tarifverwaltung`)

## Vorgehen
1. **Analysiere die Spezifikation** - Lies und verstehe alle Anforderungen
2. **Recherchiere den Code** - Finde relevante bestehende Komponenten und Patterns
3. **Prüfe Abhängigkeiten** - Identifiziere betroffene Module (Backend, Frontend, DB)
4. **Stelle Rückfragen** bei Unklarheiten BEVOR du den Plan erstellst

## Output
* Erstelle eine neue Datei: `[Spec-Name]_Umsetzungsplan.md` im gleichen Verzeichnis (/Specs/)
* Der Umsetzungsplan enthält folgende Kapitel:
  - **Zusammenfassung:** 2-3 Sätze: Was wird implementiert und warum
  - **Betroffene Komponenten:** Liste aller zu ändernden/erstellenden Dateien
  - **Phasen mit Sub-Phasen:** Detaillierte Beschreibung jeder Phase mit Code-Beispielen
  - **Umsetzungsreihenfolge:** Tabelle mit Abhängigkeiten zwischen Aufgaben
  - **Validierungen:** Liste aller Validierungsregeln im Front- und Backend
  - **Offene Punkte / Annahmen**

### Phasen-Format

Phasen werden mit Sub-Phasen strukturiert (wie in `Specs/Tarifverwaltung_Umsetzungsplan.md`):

```markdown
## Phase 1: Backend - Datenbank und Entity

### 1.1 Flyway Migration
**Datei:** `backend-service/src/main/resources/db/migration/V[XX]__[Beschreibung].sql`
(Code-Beispiel)

### 1.2 Entity-Klasse
**Datei:** `backend-service/src/main/java/ch/nacht/entity/[Name].java`
(Code-Beispiel)

---

## Phase 2: Backend - Repository und Service

### 2.1 Repository
...
```

#### Hinweise
* Phasen so granular gestalten, dass sie einzeln umsetzbar sind
* Jede Sub-Phase enthält den konkreten Dateipfad und Code-Beispiele
* Bei der Umsetzung wird der Status in der Umsetzungsreihenfolge-Tabelle nachgeführt
* Beachte bestehende Architektur-Patterns im Projekt
* **Multi-Tenancy:** Neue Entities brauchen immer `org_id` (UUID), `@Filter` und `@FilterDef`
* **Code-Vorlagen:** Verwende die Vorlagen aus CLAUDE.md (Abschnitt "Code-Vorlagen für deterministische Generierung")

## Konventionen

### Dateinamen
| Typ | Muster                                                                          |
|-----|---------------------------------------------------------------------------------|
| Flyway | `V[XX]__[Snake_Case_Beschreibung].sql`                                       |
| Entity | `/entity/[PascalCase].java`                                                  |
| Repository | `/repository/[Name]Repository.java`                                      |
| Service | `/service/[Name]Service.java`                                               |
| Controller | `/controller/[Name]Controller.java`                                      |
| Angular Model | `frontend-service/src/app/models/[kebab-case].model.ts`               |
| Angular Service | `frontend-service/src/app/services/[kebab-case].service.ts`         |
| Angular Component | `frontend-service/src/app/components/[kebab-case]/[kebab-case].component.ts` |

---

## Referenz
* CLAUDE.md - Projekt-Architektur und Code-Vorlagen
* Specs/generell.md - Allgemeine Anforderungen
* Specs/Tarifverwaltung_Umsetzungsplan.md - Beispiel eines Umsetzungsplans
