# Umsetzung

Setze einen Umsetzungsplan schrittweise um.

## Input
* Umsetzungsplan: $ARGUMENTS (z.B. `Specs/Tarifverwaltung_Umsetzungsplan.md` oder kurz `Tarifverwaltung`)

## Vorgehen
1. **Lies den Umsetzungsplan** - Verstehe alle Phasen und deren Status
2. **Identifiziere nächste Phase** - Finde die erste Phase mit `[ ]` (nicht erledigt)
3. **Implementiere die Phase** - Setze die beschriebenen Änderungen um
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
* **Frontend:**
  * Komponenten im Verzeichnis `frontend-service/src/app/`
  * **Fehleranzeige im Frontend:**
    * Zeige Fehlermeldungen als `.zev-message--error` an
    * Erfolgreiche Aktionen mit `.zev-message--success` quittieren
  * **Mehrsprachigkeit:**
    * Verwende den TranslationService für alle Texte
    * Füge neue Text-Keys in die Datenbank ein mit `ON CONFLICT (key) DO NOTHING` (flyway)
* **Backend:** Controller -> Service -> Repository -> Entity Pattern
    * Alle Texte via TranslationService
    * **REST-API Konventionen:**
        * Endpoints: `/api/[ressource]` (Plural, kebab-case)
        * HTTP-Methoden: GET (lesen), POST (erstellen), PUT (aktualisieren), DELETE (löschen)
* **Multi-Tenancy:**
    * Neue Entities brauchen `org_id` (UUID) Spalte
    * `@Filter` und `@FilterDef` Annotationen auf der Entity
    * `HibernateFilterService` im Service verwenden
* **Datenbank:** Flyway-Migrationen in `backend-service/src/main/resources/db/migration/`
  * **Naming:** Migrations `V[nummer]__[beschreibung].sql`
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

## Referenz
* Specs/generell.md - Allgemeine Anforderungen (i18n, Design System, Multi-Tenancy)
* CLAUDE.md - Projekt-Architektur, Build-Commands und Code-Vorlagen
