# Umsetzungsplan

Erstelle einen detaillierten Umsetzungsplan für eine Spezifikation.

## Input
* Spezifikation: $ARGUMENTS (z.B. `Specs/Tarifverwaltung.md`)

## Vorgehen
1. **Analysiere die Spezifikation** - Lies und verstehe alle Anforderungen
2. **Prüfe Abhängigkeiten** - Identifiziere betroffene Module (Backend, Frontend, DB)
3. **Stelle Rückfragen** - Kläre Unklarheiten bevor du planst
4. **Recherchiere den Code** - Finde relevante bestehende Komponenten und Patterns

## Output
* Erstelle eine neue Datei: `[Spec-Name]_Umsetzungsplan.md` im gleichen Verzeichnis
* Der Plan enthält:
  - **Übersicht:** Kurze Zusammenfassung des Features
  - **Betroffene Komponenten:** Liste aller zu ändernden/erstellenden Dateien
  - **Phasen-Tabelle:** Mit Spalten für Status, Phase, Beschreibung

### Phasen-Tabelle Format
```markdown
| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [ ] | 1. DB-Migration | Flyway-Migration für neue Tabelle |
| [ ] | 2. Backend-Entity | Entity und Repository erstellen |
| [ ] | 3. Backend-Service | Service-Logik implementieren |
| [ ] | 4. Backend-Controller | REST-Endpunkte erstellen |
| [ ] | 5. Frontend-Service | Angular-Service für API-Calls |
| [ ] | 6. Frontend-UI | Komponenten erstellen |
```

## Hinweise
* Phasen so granular gestalten, dass sie einzeln umsetzbar sind
* Bei der Umsetzung wird `[x]` für abgeschlossene Phasen verwendet
* Beachte bestehende Architektur-Patterns im Projekt
