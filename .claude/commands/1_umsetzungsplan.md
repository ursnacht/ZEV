# Erstelle Umsetzungsplan

Erstelle einen detaillierten Umsetzungsplan für eine Spezifikation.

## Input

* **Feature-Name**: $ARGUMENTS (z.B. `Debitorkontrolle`) → liest `Specs/[Feature-Name].md`  
  Falls nicht angegeben: aus dem Konversations-Kontext ableiten (z.B. wenn zuvor `/0_anforderungen` ausgeführt wurde); nur wenn unklar: nachfragen.

## Vorgehen
1. **Analysiere die Spezifikation** in `Specs/[Feature-Name].md` - Lies und verstehe alle Anforderungen
2. **Recherchiere den Code** - Finde relevante bestehende Komponenten und Patterns
3. **Prüfe Abhängigkeiten** - Identifiziere betroffene Module (Backend, Frontend, DB)
4. **Prüfe offene Fragen** - Lies Abschnitt "8. Offene Fragen" der Spec:
   - Fragen mit Antwort (` --> ...`): als Annahme in den Plan übernehmen
   - Fragen ohne Antwort, die den Scope oder die Architektur beeinflussen: **Rückfrage stellen BEVOR du den Plan erstellst**
   - Fragen ohne Antwort, die Details betreffen: als Annahme treffen und unter "Offene Punkte / Annahmen" dokumentieren

## Output
* Erstelle einen neuen Umsetzungsplan in: `Specs/[Feature-Name]_Umsetzungsplan.md`
* Der Umsetzungsplan enthält folgende Kapitel:
    - **Zusammenfassung:** 2-3 Sätze: Was wird implementiert und warum
    - **Betroffene Komponenten:** Liste aller zu ändernden/erstellenden Dateien
    - **Phasen-Tabelle:** Mit Spalten für Status, Phase, Beschreibung
    - **Validierungen:** Liste aller Validierungsregeln im Front- und Backend
    - **Offene Punkte / Annahmen**

### Phasen-Tabelle Format

```markdown
| Status | Phase                   | Beschreibung                                                                                   |
|--------|-------------------------|------------------------------------------------------------------------------------------------|
|  [ ]   | 1. DB-Migration         | Flyway-Migration für neue Tabelle                                                              |
|  [ ]   | 2. Backend-Entity       | Entity und Repository erstellen                                                                |
|  [ ]   | 3. Backend-Service      | Service-Logik implementieren                                                                   |
|  [ ]   | 4. Backend-Controller   | REST-Endpunkte erstellen                                                                       |
|  [ ]   | 5. Frontend-Service     | Angular-Service für API-Calls                                                                  |
|  [ ]   | 6. Frontend Komponenten | Model, Service und Komponenten erstellen                                                       |
|  [ ]   | 7. Routing              | Änderung in: `frontend-service/src/app/app.routes.ts`                                          |
|  [ ]   | 8. Navigation           | Änderung in: `frontend-service/src/app/app.component.html`                                     |
|  [ ]   | 9. Übersetzungen        | Datei: `backend-service/src/main/resources/db/migration/V[XX]__Add_[Feature]_Translations.sql` |
```

#### Hinweise
* Phasen so granular gestalten, dass sie einzeln umsetzbar sind
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
* Specs/Mieterverwaltung_Umsetzungsplan.md - Beispiel eines Umsetzungsplans
