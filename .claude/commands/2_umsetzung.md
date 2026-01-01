# Umsetzung

Setze einen Umsetzungsplan schrittweise um.

## Input
* Umsetzungsplan: $ARGUMENTS (z.B. `Specs/Tarifverwaltung_Umsetzungsplan.md` oder kurz `Tarifverwaltung`)

## Vorgehen
1. **Lies den Umsetzungsplan** - Verstehe alle Phasen und deren Status
2. **Identifiziere nächste Phase** - Finde die erste Phase mit `[ ]` (nicht erledigt)
3. **Implementiere die Phase** - Setze die beschriebenen Änderungen um
4. **Aktualisiere den Status** - Markiere die Phase mit `[x]` als erledigt
5. **Wiederhole** - Fahre mit der nächsten Phase fort

## Konventionen
* **Backend:** Controller → Service → Repository → Entity Pattern
  * Alle Texte via TranslationService
* **Frontend:** Komponenten nutzen `@zev/design-system`, Texte via `TranslationService`
* **Datenbank:** Flyway-Migrationen in `backend-service/src/main/resources/db/migration/`
  * **Naming:** Migrations `V[nummer]__[beschreibung].sql`

## Wichtige Regeln
* **Keine Tests erstellen** - Tests werden separat mit anderen Commands erstellt
* **Inkrementell arbeiten** - Eine Phase nach der anderen abschliessen
* **Status aktuell halten** - Umsetzungsplan nach jeder Phase aktualisieren

## Referenz
* Specs/generell.md - Allgemeine Anforderungen (i18n, Design System)
* CLAUDE.md - Projekt-Architektur und Build-Commands
