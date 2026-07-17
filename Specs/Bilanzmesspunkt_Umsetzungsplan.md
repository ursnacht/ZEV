# Bilanzmesspunkt – Umsetzungsplan

## Zusammenfassung

Es werden zwei neue Einheiten-Typen **`BEZUG`** und **`RUECKLIEFERUNG`** eingeführt, die die Gesamt­energie am ZEV-Netzanschluss messen. Ihre Werte werden **wie andere Typen aggregiert**, aber **nicht in die Solarverteilung** einbezogen. In der Statistik werden pro Monat **zwei neue Summen-Vergleiche** ergänzt: der berechnete Wert „Bezug von VNB" gegen die Bilanz-Summe des Typs `BEZUG` und „Rücklieferung" gegen die Bilanz-Summe des Typs `RUECKLIEFERUNG` (gleiche Toleranz 0.1 kWh, Web + PDF). Pro Mandant ist je Bilanz-Typ **höchstens eine** Einheit erlaubt.

Grundlage: [`Specs/Bilanzmesspunkt.md`](./Bilanzmesspunkt.md).

> **Abweichung vom Standard-Template:** Keine neue Tabelle/Entity und **keine Schema-Migration** — `einheit.typ` ist bereits `VARCHAR` (`@Enumerated(STRING)`). Es kommen nur zwei Enum-Werte, Service-/Statistik-Logik und Übersetzungen hinzu. Kein neues Routing/keine Navigation (Statistik + Einheiten-Verwaltung existieren bereits).

## Betroffene Komponenten

**Backend (geändert):**
- `entity/EinheitTyp.java` – Werte `BEZUG`, `RUECKLIEFERUNG` ergänzen.
- `repository/EinheitRepository.java` – `existsByTyp(EinheitTyp)`, `existsByTypAndIdNot(EinheitTyp, Long)` (Eindeutigkeit je Mandant via aktivem `orgFilter`).
- `service/EinheitService.java` – Eindeutigkeits-Validierung in `createEinheit`/`updateEinheit` (nur Bilanz-Typen) → `IllegalStateException("EINHEIT_BILANZ_TYP_EXISTIERT")`.
- `dto/MonatsStatistikDTO.java` – neue Felder: `bilanzBezug`, `bilanzRuecklieferung`, `bezugBilanzGleich`, `bezugBilanzDifferenz`, `ruecklieferungBilanzGleich`, `ruecklieferungBilanzDifferenz`.
- `service/StatistikService.java` – Bilanz-Summen (`sumTotalByEinheitTypAndZeitBetween` mit den neuen Typen) + Vergleiche berechnen; `berechneEinheitSummen` um `abs` für Bilanz-Typen erweitern.
- `service/RechnungService.java` – sicherstellen, dass Bilanz-Typen **nicht** verrechnet werden (weder Consumer- noch Producer-Zweig).
- `service/MqttIngestService.java` / `repository/EinheitRepository.java` – gemeinsamer Bilanzmesspunkt: `findAllByOrgIdAndMesspunkt` (Liste statt Optional), Splitting der Meldung + Register-Projektion je Bilanz-Typ (FR-2.3/2.4).
- `service/StatistikPdfService.java` / `reports/statistik.jrxml` – zwei neue Vergleiche im PDF.
- `reports/einheit-summen.jrxml` – Typ-Label im Subreport auf 4 Typen (war binär `PRODUCER`/sonst).
- (Verifikation, kein Code) `service/MesswerteService.java`, `service/ZaehlerAggregationService.java` – Verteilung nur `PRODUCER`/`CONSUMER`; `zev=0` für Bilanz-Typen bereits gegeben.

**Frontend (geändert):**
- `models/einheit.model.ts` – `EinheitTyp` um `BEZUG`, `RUECKLIEFERUNG`.
- `components/einheit-form/einheit-form.component.ts` – zwei neue Typ-Optionen (übersetzte Labels).
- `pipes/einheit-typ.pipe.ts` – Mapping für **vier** Typen.
- `components/einheit-list/einheit-list.component.ts` – Erstell-/Update-Fehler übersetzt anzeigen (`error.error?.error`).
- `models/statistik.model.ts` – neue Bilanz-/Vergleichsfelder.
- `components/statistik/statistik.component.html` – zwei neue Vergleichs-Items (+ optional zwei Bilanz-Summenzeilen/Balken); Typ-Spalte in „Summen pro Einheit" über `EinheitTypPipe` (war binäres Inline-Ternary).
- Tests/Mocks: `statistik.component.spec.ts`, `statistik.service.spec.ts`, `einheit-form.component.spec.ts` (neue Enum-Werte/DTO-Felder).

**DB / i18n:**
- `db/migration/V80__Add_Bilanzmesspunkt_Translations.sql` – neue Keys (DE/EN, `ON CONFLICT DO NOTHING`).

## Umsetzungsreihenfolge (Phasen)

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. EinheitTyp erweitern | `EinheitTyp` (Backend + `einheit.model.ts`) um `BEZUG`, `RUECKLIEFERUNG`. Keine Schema-Migration (VARCHAR). |
| [x] | 2. Eindeutigkeit Backend | `EinheitRepository.existsByTyp`/`existsByTypAndIdNot`; `EinheitService.createEinheit`/`updateEinheit`: bei Bilanz-Typ prüfen (orgFilter aktiv) → sonst `IllegalStateException("EINHEIT_BILANZ_TYP_EXISTIERT")` (Mapping auf HTTP 400 via `GlobalExceptionHandler`). |
| [x] | 3. Verteilung/Aggregation absichern | Verifizieren: `MesswerteService.distribute` verarbeitet nur `PRODUCER`/`CONSUMER` (Bilanz-Typen ausgeschlossen); `ZaehlerAggregationService` setzt `zev=0` für Bilanz-Typen. Ggf. Kommentar/Test ergänzen. Kein funktionaler Code nötig. |
| [x] | 4. RechnungService absichern | Sicherstellen, dass Einheiten der Bilanz-Typen weder als Consumer noch als Producer verrechnet werden (Filter/Guard); ggf. Regressionstest. |
| [x] | 5. Statistik-Berechnung | `MonatsStatistikDTO` um Bilanz-Summen + Vergleichsfelder; `StatistikService`: Bilanz-Summen ermitteln, Vergleiche (Bezug positiv; Rücklieferung über Beträge) mit `TOLERANZ`; `berechneEinheitSummen` → `abs` auch für Bilanz-Typen. |
| [x] | 6. Statistik-Anzeige (Web) | `statistik.model.ts` + `statistik.component.html`: zwei neue Vergleichs-Items im Bereich „Summen-Vergleich" (Design-System `zev-comparison-item`, Status-Dot + Differenz); optional zwei Bilanz-Summenzeilen mit Balken (Absolutwerte). |
| [x] | 7. Statistik-PDF | `statistik.jrxml`: zwei neue Vergleiche (Felder + Elemente) analog Web; Band-Höhe/Layout anpassen. `JasperTemplateCompileTest` grün. |
| [x] | 8. Einheiten-UI | `einheit-form`: zwei neue Typ-Optionen; `einheit-typ.pipe`: vier Typen; `einheit-list`: übersetzte Fehlermeldung bei Eindeutigkeits-Verletzung. |
| [x] | 9. Übersetzungen | `V80__Add_Bilanzmesspunkt_Translations.sql`: `TYP_BEZUG`, `TYP_RUECKLIEFERUNG`, `VERGLEICH_BEZUG`, `VERGLEICH_RUECKLIEFERUNG`, `EINHEIT_BILANZ_TYP_EXISTIERT` (DE/EN, `ON CONFLICT DO NOTHING`). |
| [x] | 10. Tests anpassen |
| [x] | 11. MQTT-Splitting (Nachtrag) | Gemeinsamer Bilanzmesspunkt: `EinheitRepository.findAllByOrgIdAndMesspunkt` (mehrere Treffer zulässig), `MqttIngestService` splittet die Meldung je Einheit mit Register-Projektion (BEZUG: nur Bezug, RUECKLIEFERUNG: nur Einspeisung, jeweils andere = 0; PRODUCER/CONSUMER verbatim). `ZaehlerAggregationService` unverändert korrekt (arbeitet je Einheit auf projizierten Rohdaten). Simulator: neuer Modus `bilanz` (beide Register in einer Meldung). | Bestehende Spec-Mocks/Tests auf neue Enum-Werte und DTO-/Model-Felder anpassen (Backend `StatistikServiceTest`, Frontend `statistik.*.spec.ts`, `einheit-form.component.spec.ts`). Neue Tests gemäss separaten Test-Skills (`/3`–`/5`). |

> **Tests** (Unit/Integration/E2E) werden gemäss Projekt-Workflow separat mit `/3_backend-tests`, `/4_frontend-unit-tests`, `/5_e2e-tests` erstellt; hier nur die Anpassung bestehender Tests, damit der Build grün bleibt.

## Validierungen

### Backend (maßgeblich)
- **Eindeutigkeit Bilanz-Typ:** Beim Anlegen/Ändern einer Einheit mit Typ `BEZUG` bzw. `RUECKLIEFERUNG` darf im selben Mandanten **keine weitere** Einheit desselben Bilanz-Typs existieren (`existsByTyp` bei Create, `existsByTypAndIdNot` bei Update; `orgFilter` aktiv) — sonst HTTP 400 mit Key `EINHEIT_BILANZ_TYP_EXISTIERT`. Für `PRODUCER`/`CONSUMER` keine Beschränkung.
- **Vergleichs-Toleranz:** `|Differenz| < 0.1 kWh` ⇒ „gleich" (Konstante `StatistikService.TOLERANZ`).
- **Vorzeichen:** Bilanz-Summe `BEZUG` positiv (direkter Vergleich), Bilanz-Summe `RUECKLIEFERUNG` negativ → Vergleich über Beträge (`Rücklieferung − |BilanzRücklieferung|`).
- **Null-Handling:** Fehlende Bilanz-Summe (keine Einheit/keine Daten) → `0.0`; Vergleich wird angezeigt.
- **Verteilungs-Ausschluss:** Bilanz-Typen nie in Producer-/Consumer-Aggregation der Verteilung; `zev_calculated` bleibt ungesetzt.
- **Autorisierung:** Statistik `statistik:read`; Einheiten-Änderung `einheit:write` (unverändert).

### Frontend
- Typ-Auswahl im Formular enthält alle vier Typen; Speichern löst bei Eindeutigkeits-Verletzung eine übersetzte Fehlermeldung aus (aus `error.error.error`).
- `EinheitTypPipe` liefert für alle vier Typen die korrekte Übersetzung.
- Statistik zeigt beide neuen Vergleiche inkl. Status/Differenz; leere Bilanz → Vergleich mit Summe 0 sichtbar.

## Offene Punkte / Annahmen

- **Alle Spec-Fragen sind geklärt** (Bilanzmesspunkt.md §8): Enum `BEZUG`/`RUECKLIEFERUNG`; Vorzeichen Bezug +/Rücklieferung − (Vergleich über Beträge); PDF konsistent zur Web-Ansicht; leerer Vergleich anzeigen; **max. eine** Bilanz-Einheit je Typ/Mandant; „Summen pro Einheit" mit Absolutwerten; eigene Vergleichs-Label-Keys.
- **Migrationsnummer `V80`:** höchste vergebene ist `V79` (per Dateiliste/`flyway:info` vor der Umsetzung verifizieren).
- **Eindeutigkeit vs. `orgFilter`:** `existsByTyp`/`existsByTypAndIdNot` laufen mit aktivem `orgFilter` → per Mandant. Vor den Repository-Aufrufen `hibernateFilterService.enableOrgFilter()` sicherstellen (wie in `EinheitService` bereits üblich).
- **Vergleichs-Kürzel:** `VERGLEICH_BEZUG`/`VERGLEICH_RUECKLIEFERUNG` mit kurzem Label (z.B. „Bezug ↔ Bilanz" / „Rücklieferung ↔ Bilanz"); finale Wortwahl in Phase 9.
- **PDF-Layout:** Einfügen der zwei Vergleiche erhöht die Detail-Band-Höhe (analog zur letzten Statistik-PDF-Erweiterung); Positionen der nachfolgenden Elemente entsprechend verschieben.
- **Bilanz-Einheiten in „Summen pro Einheit":** werden mitgelistet (Absolutwerte); der Typ wird über `EinheitTypPipe` angezeigt.
- **`total`-Vorzeichen der Bilanz-Einheiten** hängt von der (späteren) Datenquelle ab (Import folgt separat); die Vorzeichen-/`abs`-Logik ist unabhängig davon korrekt.

## Umsetzungsnotizen (nach Abschluss)

- **Alle 10 Phasen umgesetzt** (Backend 425 Unit-Tests, Frontend 779 Tests, `JasperTemplateCompileTest` grün). Die **optionalen** Bilanz-Summenzeilen/-Balken in der Werte-Tabelle (Phase 6) wurden bewusst **nicht** umgesetzt.
- **Nachbesserung ZEV-Spalte (FR-5.5):** In „Summen pro Einheit" zeigt die Spalte **ZEV (kWh)** für Bilanz-Typen `-` statt `0.000` (Web: `isBilanzTyp()`-Helper, PDF: `einheit-summen.jrxml`) – nur `total` ist für Bilanzmesspunkte fachlich relevant.
- **Nachbesserung Typ-Anzeige (FR-5.4):** Die binäre Typ-Logik existierte ausser in der `EinheitTypPipe` auch inline in `statistik.component.html` („Summen pro Einheit") und im PDF-Subreport `einheit-summen.jrxml` – beide zeigten Bilanz-Einheiten als „Konsument" und wurden nachträglich auf 4 Typen erweitert (Web über `EinheitTypPipe`, PDF über verschachteltes Ternary mit `TYP_BEZUG`/`TYP_RUECKLIEFERUNG`).
- **`einheit-list`:** Fehlermeldungen sind jetzt wegklickbar (Message-Konvention: Error bleibt bis dismissed) – das Dismiss-Markup fehlte im Template.
- **`EinheitControllerTest`:** Test ergänzt, der das Fehlerformat `400 {"error":"EINHEIT_BILANZ_TYP_EXISTIERT"}` absichert (`GlobalExceptionHandler`-Mapping).
- **Publisher-Simulator** (`pi-gateway`): `sim_reader.py` um die Modi `bezug`/`rücklieferung` erweitert (nur Bezug- bzw. nur Einspeisungs-Register wächst); `config.sim(.example).yaml` mit den Messpunkten `Bezug`/`Rücklieferung` ergänzt.
- **Übersetzungen:** Migration `V80` liefert die Defaults „Bezug"/„Rücklieferung"; im Zielsystem wurden die Typ-Labels via Übersetzungs-Editor auf „Bezug VNB"/„Rücklieferung VNB" angepasst (Migration bleibt unverändert, `ON CONFLICT DO NOTHING`).
- **Nachtrag MQTT-Splitting (Phase 11, FR-2.3/2.4):** Der physische Bilanzzähler liefert beide Register in einer Meldung → beide Bilanz-Einheiten dürfen denselben `messpunkt` tragen; der Ingest splittet mit Register-Projektion (gilt für Bilanz-Typen immer, auch ohne geteilten Messpunkt). Tests: `MqttIngestServiceTest` (+2, Splitting/Projektion), `ZaehlerRohdatenRepositoryIT` auf `findAllByOrgIdAndMesspunkt` angepasst. Simulator-Config nutzt neu den gemeinsamen Messpunkt `Bilanz` (Modus `bilanz`) statt der zwei Einzel-Messpunkte.
