# Separate Agenten für Tests: Ja oder Nein?

## Kontext

Diese Dokumentation bewertet, ob es sinnvoll ist, Tests durch separate Agenten (via Task tool) erstellen zu lassen, anstatt die Test-Skills direkt im Hauptagenten auszuführen.

## Aktuelle Situation

Die Test-Skills (`/3_backend-tests`, `/4_frontend-unit-tests`, `/5_e2e-tests`) wurden so konfiguriert, dass sie:

- ✅ **Unabhängig arbeiten** - Explizite Anweisung, den Session-Kontext zu ignorieren
- ✅ **3-Phasen-Vorgehen** nutzen - Analyse → Gap-Analyse → Test-Erstellung
- ✅ **Nur auf Code basieren** - Analysieren Implementierung und bestehende Tests

## Vorteile separater Agenten

| Vorteil | Beschreibung | Bewertung |
|---------|--------------|-----------|
| **Echte Parallelisierung** | Mehrere Agenten können gleichzeitig verschiedene Tests schreiben | ⭐⭐⭐ Hoch |
| **Frischer Kontext** | Garantiert kein "Bias" durch vorherige Implementierungsarbeit | ⭐⭐ Mittel |
| **Spezialisierung** | Agent könnte ausschließlich auf Tests optimiert sein | ⭐ Gering |

## Nachteile separater Agenten

| Nachteil | Beschreibung | Bewertung |
|----------|--------------|-----------|
| **Overhead** | Jeder neue Agent braucht Setup-Zeit und muss Code erst lesen | ⭐⭐⭐ Hoch |
| **Koordination** | Ergebnisse müssen zusammengeführt werden | ⭐⭐ Mittel |
| **Kontext-Verlust** | Nützliches Wissen über Architekturentscheidungen geht verloren | ⭐⭐ Mittel |
| **Kosten** | Mehr API-Calls und Token-Verbrauch | ⭐ Gering |

## Empfehlung

### Für das ZEV-Projekt

Separate Agenten lohnen sich **nur bei großen Batch-Operationen**.

| Szenario | Empfehlung |
|----------|------------|
| 1-3 fehlende Tests | Direkt mit Skill ausführen (`/4_frontend-unit-tests xyz`) |
| Viele Tests auf einmal (>5) | Parallele Agenten können sinnvoll sein |
| Nach `/6_test-gap-analyse` | Abhängig von der Anzahl der Lücken |

### Begründung

1. **Die aktualisierten Skills reichen aus** - Der "Unabhängige Ausführung" Header erzwingt bereits das gewünschte Verhalten (Code analysieren, Kontext ignorieren).

2. **Overhead überwiegt bei kleinen Tasks** - Für 1-3 Tests ist der Setup-Aufwand eines separaten Agenten nicht gerechtfertigt.

3. **Parallele Agenten bei Batch-Operationen** - Wenn nach einer Gap-Analyse 10+ Tests fehlen, können mehrere Agenten parallel arbeiten:
   - Agent 1: Backend Service Tests
   - Agent 2: Frontend Component Tests
   - Agent 3: E2E Tests

## Wann separate Agenten verwenden?

```
Anzahl fehlender Tests:
  1-3   → Direkt mit Skill
  4-7   → Optional parallel
  8+    → Parallel empfohlen
```

## Beispiel: Parallele Agenten starten

```
# Nach /6_test-gap-analyse mit vielen Lücken:

Agent 1 (Backend):
/3_backend-tests EinheitService

Agent 2 (Frontend):
/4_frontend-unit-tests einheit-list.component.ts

Agent 3 (E2E):
/5_e2e-tests Einheiten-Verwaltung
```

## Fazit

**Separate Agenten sind Over-Engineering für die meisten Anwendungsfälle in diesem Projekt.** Die aktualisierten Skills mit "Unabhängige Ausführung" bieten bereits die gewünschte Isolation. Parallele Agenten sollten nur bei großen Batch-Operationen in Betracht gezogen werden.

---

*Erstellt: Januar 2026*
*Basierend auf Erfahrungen mit Claude Code Test-Skills*
