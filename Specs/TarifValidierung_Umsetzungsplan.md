# Umsetzungsplan: Validierung der Tarife

## Zusammenfassung
Es werden zwei Buttons "Quartale validieren" und "Jahre validieren" zur Tarifverwaltung hinzugefügt. Diese rufen die bereits existierende Backend-Methode `validateTarifAbdeckung` auf, um Lücken in der Tarifabdeckung zu erkennen. Fehlermeldungen werden als persistente (klickbare) Meldungen angezeigt.

## Betroffene Komponenten

### Backend (Änderungen)
| Datei | Änderung |
|-------|----------|
| `TarifController.java` | Neuer Endpunkt `POST /api/tarife/validate` |
| `TarifService.java` | Neue Methode `validateQuartale()` und `validateJahre()` |

### Frontend (Änderungen)
| Datei | Änderung |
|-------|----------|
| `tarif.service.ts` | Neue Methoden `validateQuartale()` und `validateJahre()` |
| `tarif-list.component.ts` | Neue Methoden für Button-Clicks und Fehleranzeige |
| `tarif-list.component.html` | Zwei neue Buttons, persistente Fehlermeldung |

### Design System
- Keine neuen Styles erforderlich - bestehende Button-Klassen verwenden

### Datenbank (Neue Migration)
| Datei | Beschreibung |
|-------|--------------|
| `V33__Add_TarifValidierung_Translations.sql` | Übersetzungen für Buttons und Fehlermeldungen |

## Phasen-Tabelle

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. Backend-Service | Neue Methoden `validateQuartale()` und `validateJahre()` im TarifService |
| [x] | 2. Backend-Controller | Neuer Endpunkt `POST /api/tarife/validate?modus=quartale\|jahre` |
| [x] | 3. Frontend-Service | Neue Methoden im TarifService für API-Calls |
| [x] | 4. Frontend-Component | Buttons und Fehleranzeige-Logik in TarifListComponent |
| [x] | 5. Frontend-Template | Buttons im HTML hinzufügen (bestehende Design-System-Klassen) |
| [x] | 6. Übersetzungen | Flyway-Migration für neue Translation-Keys |

## Validierungen

### Backend
| Regel | Beschreibung |
|-------|--------------|
| Quartals-Prüfung | Für jedes Quartal mit mind. einem Tarif: Prüfe ob ZEV und VNB das gesamte Quartal abdecken |
| Jahres-Prüfung | Für jedes Jahr mit mind. einem Tarif: Prüfe ob ZEV und VNB das gesamte Jahr abdecken |
| Tariftypen | Beide Tariftypen (ZEV, VNB) müssen geprüft werden |

### Frontend
| Regel | Beschreibung |
|-------|--------------|
| Fehlermeldung | Bleibt sichtbar bis Benutzer sie wegklickt |
| Erfolgsmeldung | Kurze Bestätigung bei erfolgreicher Validierung |

## API-Design

### Request
```
POST /api/tarife/validate?modus=quartale
POST /api/tarife/validate?modus=jahre
```

### Response (Erfolg)
```json
{
  "valid": true,
  "message": "Alle Quartale/Jahre sind vollständig abgedeckt"
}
```

### Response (Fehler)
```json
{
  "valid": false,
  "message": "Validierungsfehler",
  "errors": [
    "Q1/2024: ZEV-Tarif fehlt für: 2024-01-15",
    "Q2/2024: VNB-Tarif fehlt für: 2024-04-01"
  ]
}
```

## Übersetzungen

| Key | Deutsch | Englisch |
|-----|---------|----------|
| QUARTALE_VALIDIEREN | Quartale validieren | Validate quarters |
| JAHRE_VALIDIEREN | Jahre validieren | Validate years |
| VALIDIERUNG_ERFOLGREICH | Validierung erfolgreich. Alle Zeiträume sind abgedeckt. | Validation successful. All periods are covered. |
| VALIDIERUNG_FEHLER | Validierungsfehler | Validation error |
| TARIF_LUECKE_QUARTAL | {quartal}: {tariftyp}-Tarif fehlt für: {datum} | {quarter}: {tariffType} tariff missing for: {date} |
| TARIF_LUECKE_JAHR | {jahr}: {tariftyp}-Tarif fehlt für: {datum} | {year}: {tariffType} tariff missing for: {date} |

## Offene Punkte / Annahmen

### Annahmen
1. Die bestehende Methode `validateTarifAbdeckung` kann wiederverwendet werden
2. Quartale werden als Q1 (Jan-Mär), Q2 (Apr-Jun), Q3 (Jul-Sep), Q4 (Okt-Dez) definiert
3. Button-Styling:
   - "Quartale validieren": `zev-button zev-button--secondary` (--color-secondary)
   - "Jahre validieren": `zev-button zev-button--secondary` (--color-secondary)
4. Bestehende Design-System-Klassen werden verwendet, keine neuen Styles nötig

### Offene Punkte
1. ~~Habe ich etwas vergessen?~~ → Keine weiteren Punkte identifiziert
