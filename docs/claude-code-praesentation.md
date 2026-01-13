---
marp: true
theme: default
paginate: true
backgroundColor: #ffffff
style: |
  :root {
    --color-primary: #4CAF50;
    --color-secondary: #2196F3;
    --color-gray-800: #333;
    --color-gray-600: #666;
  }
  section {
    font-family: Arial, sans-serif;
    font-size: 18pt;
    color: #333;
  }
  h1 {
    font-size: 1.6em;
    color: #4CAF50;
    border-bottom: 3px solid #4CAF50;
    padding-bottom: 10px;
  }
  h2 {
    font-size: 1.3em;
    color: #2196F3;
  }
  code {
    background-color: #f5f5f5;
    padding: 2px 6px;
    border-radius: 4px;
    font-size: 0.9em;
  }
  pre {
    background-color: #f8f8f8;
    border: 1px solid #ddd;
    border-radius: 8px;
    padding: 15px;
    font-size: 14pt;
  }
  table {
    font-size: 16pt;
    width: 100%;
  }
  th {
    background-color: #4CAF50;
    color: white;
    padding: 10px;
  }
  td {
    padding: 8px;
    border-bottom: 1px solid #ddd;
  }
  ul li {
    margin-bottom: 8px;
  }
  .columns {
    display: flex;
    gap: 20px;
  }
  .column {
    flex: 1;
  }
---

# Claude Code
## Einführung am Beispiel des ZEV-Projekts

![bg right:30%](https://upload.wikimedia.org/wikipedia/commons/thumb/8/8a/Banana-Single.jpg/1200px-Banana-Single.jpg)

**Schulungs- und Einführungsmaterial**

---

# 1. Was ist Claude Code?

## Definition
Claude Code ist Anthropic's offizielles CLI-Tool für KI-gestützte Softwareentwicklung.

## Kernfähigkeiten
- **CLI-Tool**: Läuft direkt im Terminal
- **Code-Verständnis**: Analysiert komplette Codebases
- **Ausführung**: Führt Befehle und Änderungen aus
- **Kontext**: Versteht Projektstruktur und Abhängigkeiten

---

# 2. Was kann Claude Code?

<div class="columns">
<div class="column">

## Verstehen
- Codebase analysieren
- Architektur erklären
- Abhängigkeiten erkennen

## Planen
- Features designen
- Umsetzungspläne erstellen
- Aufwand einschätzen

</div>
<div class="column">

## Implementieren
- Code schreiben
- Tests erstellen
- Refactoring durchführen

## Validieren
- Tests ausführen
- Builds prüfen
- Code-Qualität sichern

</div>
</div>

---

# 3. Bausteine von Claude Code

| Baustein | Zweck |
|----------|-------|
| `CLAUDE.md` | Projektkontext (Architektur, Commands, Konventionen) |
| `Specs/generell.md` | Allgemeine Anforderungen (i18n, Design System) |
| `Specs/SPEC.md` | Template für neue Feature-Spezifikationen |
| MCP Server | Direkter Datenbankzugriff (`zev-db`) |
| Skills/Commands | Vordefinierte Workflows (`.claude/commands/`) |

---

# 3. Bausteine - CLAUDE.md

```markdown
## Project Overview
ZEV ist eine Solarstrom-Verteilungsanwendung...

## Build & Test Commands
mvn clean compile test    # Backend
npm test                  # Frontend Unit Tests
npm run e2e               # E2E Tests

## Architecture
Controller → Service → Repository → Entity
```

**Wichtig**: Claude liest diese Datei automatisch bei jedem Start!

---

# 3. Bausteine - MCP Server

## Direkter Datenbankzugriff

```sql
-- Via MCP Server 'zev-db'
SELECT * FROM zev.einheit WHERE typ = 'VERBRAUCHER';
```

**Vorteile**:
- Kein manuelles Docker-Exec nötig
- Schnelle Datenabfragen während der Entwicklung
- Schema-Verständnis für bessere Code-Generierung

---

# 4. Wozu Commands?

## Problem: Vibe Coding
- Unvorhersehbarer Code-Stil
- Inkonsistente Strukturen
- Schwer wartbar

## Lösung: Strukturierte Software-Entwicklung
- Code nach **meinen Vorgaben** erzeugen
- **Deterministische** Ergebnisse
- **Vorlage-Dateien** als Muster
- Explizite **Namenskonventionen**

---

# 5. Deterministischer Code

## Entsteht durch:

| Methode | Beispiel |
|---------|----------|
| Konkrete Vorlagen | `TarifService.java` als Muster für neue Services |
| Referenz-Dateien | "Orientiere dich an `tarif.service.ts`" |
| Naming-Konventionen | `*Service.java`, `*.spec.ts`, `V[XX]__*.sql` |
| Strukturvorgaben | Import-Reihenfolge, Methoden-Anordnung |

**Ergebnis**: Jeder Service sieht gleich aus!

---

# 5. Deterministischer Code - Beispiel

## Vorlage in CLAUDE.md

| Neuer Code | Vorlage |
|------------|---------|
| Entity | `Tarif.java` |
| Repository | `TarifRepository.java` |
| Service | `TarifService.java` |
| Controller | `TarifController.java` |
| Angular Model | `tarif.model.ts` |
| Angular Service | `tarif.service.ts` |

---

# 6. Welche Commands gibt es?

| # | Command | Beschreibung |
|---|---------|--------------|
| 1 | `/1_umsetzungsplan` | Erstellt Umsetzungsplan aus Spec |
| 2 | `/2_umsetzung` | Implementiert den Plan schrittweise |
| 3 | `/3_backend-tests` | Erstellt Backend Unit & Integration Tests |
| 4 | `/4_frontend-unit-tests` | Erstellt Angular Unit Tests |
| 5 | `/5_e2e-tests` | Erstellt Playwright E2E Tests |

**Sequentieller Workflow**: 1 → 2 → 3 → 4 → 5

---

# 6. Command: /1_umsetzungsplan

## Input
```
/1_umsetzungsplan Specs/SpaltenbreiteVeränderbar.md
```

## Output: `SpaltenbreiteVeränderbar_Umsetzungsplan.md`
- Zusammenfassung
- Betroffene Komponenten
- Phasen-Tabelle mit Status
- Validierungen
- Offene Punkte

---

# 6. Command: /2_umsetzung

## Input
```
/2_umsetzung Specs/SpaltenbreiteVeränderbar_Umsetzungsplan.md
```

## Vorgehen
1. Lies den Umsetzungsplan
2. Finde erste Phase mit `[ ]`
3. Implementiere die Änderungen
4. Markiere Phase mit `[x]`
5. Wiederhole

**Wichtig**: Keine Tests - werden separat erstellt!

---

# 6. Command: /3_backend-tests

## Input
```
/3_backend-tests TarifService
```

## Erstellt
- **Unit Tests** (`*Test.java`) - Surefire Plugin
- **Integration Tests** (`*IT.java`) - Failsafe Plugin

```java
@Test
void getTarifById_NotFound_ReturnsEmpty() {
    when(repository.findById(99L)).thenReturn(Optional.empty());
    Optional<Tarif> result = service.getTarifById(99L);
    assertTrue(result.isEmpty());
}
```

---

# 6. Command: /4_frontend-unit-tests

## Input
```
/4_frontend-unit-tests tarif.service.ts
```

## Erstellt
- Jasmine/Karma Tests (`*.spec.ts`)
- Service- und Component-Tests

```typescript
describe('TarifService', () => {
  it('should return all tarife', () => {
    service.getAll().subscribe(result => {
      expect(result.length).toBe(2);
    });
  });
});
```

---

# 6. Command: /5_e2e-tests

## Input
```
/5_e2e-tests Tarifverwaltung
```

## Erstellt
- Playwright Tests in `frontend-service/tests/`
- Testet komplette User Flows

```typescript
test('should create new tarif', async ({ page }) => {
  await page.goto('/tarife');
  await page.click('[data-testid="create-button"]');
  await page.fill('[data-testid="name-input"]', 'Testtarif');
  await page.click('[data-testid="save-button"]');
  await expect(page.locator('text=Testtarif')).toBeVisible();
});
```

---

# 7. Workflow der Commands

```
┌─────────────────────────────────────────────────────────────────┐
│                         SPEC.md                                 │
│              (Feature-Spezifikation)                            │
└─────────────────┬───────────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────────────┐
│               /1_umsetzungsplan                                 │
│         → *_Umsetzungsplan.md                                   │
└─────────────────┬───────────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                 /2_umsetzung                                    │
│         → Code implementieren                                   │
└─────────────────┬───────────────────────────────────────────────┘
                  │
          ┌───────┴───────┬───────────────┐
          ▼               ▼               ▼
┌─────────────────┐ ┌───────────────┐ ┌─────────────────┐
│ /3_backend-tests│ │/4_frontend-   │ │   /5_e2e-tests  │
│                 │ │  unit-tests   │ │                 │
└─────────────────┘ └───────────────┘ └─────────────────┘
```

---

# 8. Wozu Specs/SPEC.md?

## Template-Struktur

```markdown
# Titel des Features

## 1. Ziel & Kontext
## 2. Funktionale Anforderungen (FR)
## 3. Akzeptanzkriterien
## 4. Nicht-funktionale Anforderungen (NFR)
## 5. Edge Cases & Fehlerbehandlung
## 6. Abgrenzung / Out of Scope
## 7. Offene Fragen
```

---

# 8. Vorteile strukturierter Specs

| Vorteil | Beschreibung |
|---------|--------------|
| Klarheit | Eindeutige Anforderungen, keine Interpretation |
| Testbarkeit | Akzeptanzkriterien sind direkt prüfbar |
| Modularität | System in kleine Teilbereiche aufteilen |
| Dokumentation | Spec bleibt als Referenz erhalten |
| Kommunikation | Gemeinsames Verständnis im Team |

---

# 9. Praxisbeispiel: SpaltenbreiteVeränderbar

## Spec-Auszug
```markdown
## 1. Ziel & Kontext
In den Tabellen soll die Spaltenbreite durch den
User verändert werden können.

## 2. Funktionale Anforderungen
1. Links von der Spaltenüberschrift wird ein Strich eingeblendet
2. Durch Ziehen (drag) kann der Benutzer die Breite ändern
3. Beim Loslassen (drop) bleibt die Spalte in der Breite
```

---

# 9. Praxisbeispiel - Workflow

## Schritt 1: Umsetzungsplan erstellen
```
/1_umsetzungsplan Specs/SpaltenbreiteVeränderbar.md
```

## Schritt 2: Implementieren
```
/2_umsetzung Specs/SpaltenbreiteVeränderbar_Umsetzungsplan.md
```

## Schritt 3: Tests erstellen
```
/4_frontend-unit-tests resizable-column.directive.ts
/5_e2e-tests SpaltenbreiteVeränderbar
```

---

# 9. Praxisbeispiel - Ergebnis

## Generierter Umsetzungsplan

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [ ] | 1. Angular Directive | `ResizableColumnDirective` erstellen |
| [ ] | 2. CSS Styles | Resize-Handle und Cursor-Styles |
| [ ] | 3. Integration | Directive in Tabellen einbinden |
| [ ] | 4. Übersetzungen | Falls UI-Texte benötigt |

---

# Zusammenfassung

## Claude Code ermöglicht:

- **Strukturierte Entwicklung** statt Vibe Coding
- **Deterministische Code-Generierung** durch Vorlagen
- **Sequentieller Workflow** von Spec zu Tests
- **Konsistente Qualität** durch Commands

## Ressourcen
- `CLAUDE.md` - Projektdokumentation
- `Specs/SPEC.md` - Spec-Template
- `.claude/commands/` - Alle Commands

---

# Fragen?

## Nützliche Links

| Ressource | Pfad |
|-----------|------|
| Projektdoku | `CLAUDE.md` |
| Specs | `Specs/*.md` |
| Commands | `.claude/commands/*.md` |
| Design System | `design-system/` |

## Kontakt
Bei Fragen zur Anwendung: Issue im Repository erstellen
