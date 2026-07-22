# Umsetzungsplan: Bilanzmodell

## Zusammenfassung

Ein alternatives, pro Mandant wählbares Abrechnungs-/Verteilmodell: Statt aus der Producer-Produktion wird der ZEV-Eigenverbrauch aus der gemessenen Netz-Bilanz abgeleitet (`S = max(0, ConsumerTotal − Bezug)`). Die Umsetzung ist rein additiv und rückwärtskompatibel (Default `PRODUCER_MESSUNG` = heutiges Verhalten), ohne Schema-Change: der neue Modus wird als Feld im bestehenden JSONB-Konfigurationsblob (`RechnungKonfigurationDTO`) gespeichert und steuert die Verzweigung in `MesswerteService.distribute` — sowohl beim manuellen als auch beim MQTT-Auto-Lauf.

---

## Betroffene Komponenten

| Typ | Datei | Änderungsart |
|-----|-------|--------------|
| Backend Enum | `backend-service/src/main/java/ch/nacht/entity/Verteilmodus.java` | Neu |
| Backend DTO | `backend-service/src/main/java/ch/nacht/dto/RechnungKonfigurationDTO.java` | Änderung |
| Backend Service | `backend-service/src/main/java/ch/nacht/service/EinstellungenService.java` | Änderung |
| Backend Service | `backend-service/src/main/java/ch/nacht/service/MesswerteService.java` | Änderung (Kern) |
| Backend Service | `backend-service/src/main/java/ch/nacht/service/ZaehlerAggregationService.java` | Verifikation (voraussichtlich keine Änderung) |
| Backend Service | `backend-service/src/main/java/ch/nacht/service/StatistikService.java` | Änderung |
| Backend DTO | `backend-service/src/main/java/ch/nacht/dto/StatistikDTO.java` | Änderung |
| Frontend Model | `frontend-service/src/app/models/einstellungen.model.ts` | Änderung |
| Frontend Model | `frontend-service/src/app/models/statistik.model.ts` | Änderung |
| Frontend Component | `frontend-service/src/app/components/einstellungen/einstellungen.component.{ts,html}` | Änderung |
| Frontend Component | `frontend-service/src/app/components/statistik/statistik.component.{ts,html}` | Änderung |
| DB Migration | `backend-service/src/main/resources/db/migration/V[XX]__Add_Bilanzmodell_Translations.sql` | Neu |

> **Kein** neuer Controller, keine neue Route, keine Navigations-Änderung: Modus-Einstellung läuft über die bestehende `/einstellungen`-Seite, die Anzeige über die bestehende `/statistik`-Seite.

---

## Phasen-Tabelle

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. Backend-Enum | `Verteilmodus` (`PRODUCER_MESSUNG`, `BILANZ`) im `entity`-Package |
| [x] | 2. Backend-DTO | Feld `verteilmodus` in `RechnungKonfigurationDTO` (nullable, null-tolerant, Default im Code) |
| [x] | 3. Backend-EinstellungenService | Org-explizite Lese-Methode `getVerteilmodus(orgId)` (ohne `getCurrentOrgId()`) |
| [x] | 4. Backend-MesswerteService (Kern) | `distribute` verzweigt nach Modus; BILANZ-Logik, Abbruch, Producer-`zev` |
| [x] | 5. Backend-ZaehlerAggregationService | Modus zentral in `distribute`; Abbruch im Auto-Lauf als ERROR geloggt |
| [x] | 6. Backend-StatistikService/DTO | Modus in `StatistikDTO`; gemessene Rücklieferung (`bilanzRuecklieferung`) bereits vorhanden |
| [x] | 7. Frontend-Models | `verteilmodus` in `Einstellungen`/`RechnungKonfiguration` und `Statistik` |
| [x] | 8. Frontend-Einstellungen | Modus-Dropdown mit übersetzten Labels |
| [x] | 9. Frontend-Statistik | Modus-Anzeige, Tautologie-Hinweis, gemessene Rücklieferung |
| [x] | 10. Übersetzungen | Flyway-Migration `V85__Add_Bilanzmodell_Translations.sql` (DE/EN) |

---

## Detailbeschreibung der Phasen

### Phase 1: Backend-Enum `Verteilmodus`

**Datei:** `backend-service/src/main/java/ch/nacht/entity/Verteilmodus.java`

```java
package ch.nacht.entity;

public enum Verteilmodus {
    /** Heutiges Verhalten: Verteilung der Producer-Produktion (Default). */
    PRODUCER_MESSUNG,
    /** Verteilung aus der Netz-Bilanz: S = max(0, ConsumerTotal − Bezug). */
    BILANZ
}
```

### Phase 2: Backend-DTO `RechnungKonfigurationDTO`

**Datei:** `backend-service/src/main/java/ch/nacht/dto/RechnungKonfigurationDTO.java`

Neues Feld `verteilmodus` ergänzen — **nullable, ohne `@NotNull`** (Bestandsmandanten / altes JSON haben das Feld nicht), Default wird im Code gemappt:

```java
// Verteilmodus (null bei Bestandsmandanten → im Code auf PRODUCER_MESSUNG gemappt)
private Verteilmodus verteilmodus;

public Verteilmodus getVerteilmodus() {
    return verteilmodus;
}

public void setVerteilmodus(Verteilmodus verteilmodus) {
    this.verteilmodus = verteilmodus;
}
```

- Import `ch.nacht.entity.Verteilmodus`.
- Der genutzte `ObjectMapper` in `EinstellungenService` deserialisiert ein fehlendes Feld ohne Konfiguration bereits als `null` (Jackson-Default). Um bei künftigen unbekannten Enum-Werten robust zu sein, `objectMapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)` in `EinstellungenService` setzen.

### Phase 3: Backend-`EinstellungenService`

**Datei:** `backend-service/src/main/java/ch/nacht/service/EinstellungenService.java`

Org-explizite Methoden ergänzen (für den Hintergrund-Lauf ohne Request-Kontext, s. Spec FR-1.4). `OrganisationRepository.findById(orgId)` greift direkt auf die Mandanten-Tabelle zu — **kein** `enableOrgFilter()`, **kein** `getCurrentOrgId()`:

```java
@Transactional(readOnly = true)
public EinstellungenDTO getEinstellungenForOrg(Long orgId) {
    return organisationRepository.findById(orgId)
            .filter(org -> org.getKonfiguration() != null)
            .map(this::toDTO)
            .orElse(null);
}

/** Verteilmodus des Mandanten; Default PRODUCER_MESSUNG bei fehlender Konfig/Feld. */
@Transactional(readOnly = true)
public Verteilmodus getVerteilmodus(Long orgId) {
    EinstellungenDTO dto = getEinstellungenForOrg(orgId);
    if (dto == null || dto.getRechnung() == null || dto.getRechnung().getVerteilmodus() == null) {
        return Verteilmodus.PRODUCER_MESSUNG;
    }
    return dto.getRechnung().getVerteilmodus();
}
```

### Phase 4: Backend-`MesswerteService` (Kern)

**Datei:** `backend-service/src/main/java/ch/nacht/service/MesswerteService.java`

1. **Dependency:** `EinstellungenService` in den Konstruktor injizieren (bislang nicht vorhanden).
2. **Modus-Verzweigung in `distribute(...)`:** Der Methode wird bereits die Mandanten-`orgId` (`progressOrgId`) übergeben — sowohl im manuellen Lauf (`getCurrentOrgId()`) als auch im Hintergrund (`calculateSolarDistributionForOrg` → explizite org). Über diese `orgId` den Modus laden:

```java
Verteilmodus modus = einstellungenService.getVerteilmodus(progressOrgId);
```

3. **PRODUCER_MESSUNG:** Bestehende Logik unverändert (Regression!).
4. **BILANZ-Zweig** (neue private Methode, z.B. `distributeBilanz`), pro Zeitpunkt:
   - `ConsumerTotal` = Σ Consumer-`total`; `Bezug` = `total` der `BEZUG`-Einheit (via `findByZeitAndEinheitTyp(zeit, EinheitTyp.BEZUG)`).
   - **Producer-unabhängige Iteration:** kein `if (producers.isEmpty()) continue;` — Intervalle mit Consumern + Bezug werden verarbeitet, auch wenn keine PRODUCER-Messwerte vorliegen.
   - `S = max(0, ConsumerTotal − Bezug)`; Verteilung von `S` über EQUAL_SHARE/PROPORTIONAL (dieselben Algorithmen, `S` statt `solarProduction`), je Consumer am eigenen `total` gekappt.
   - Consumer: `zev_calculated = zugeteilteMenge`; MQTT-Sentinel-Regel beibehalten (`zev == 0` → berechneter Wert; CSV-Werte bleiben).
   - **Producer-`zev` (nur Statistik):** `|ProduktionTotal| − |Rücklieferung(Bilanz)|` proportional zur Produktion; `aktualisiereProducerZev`-MQTT-Guard **beibehalten** (CSV-Producer unangetastet). Fehlt die `RUECKLIEFERUNG`-Einheit → Producer-`zev = 0`, Lauf läuft weiter.
   - **Abbruch bei fehlenden Bilanzdaten** (fehlende `BEZUG`-Einheit oder fehlender `BEZUG`-Messwert im Intervall):
     ```java
     throw new IllegalStateException(
         "BILANZMODELL_KEINE_BILANZDATEN: " + zeit.toLocalDate() + " " + zeit.toLocalTime());
     ```
     `distribute` ist `@Transactional` → alle bereits gespeicherten `zev`-Werte werden zurückgerollt (keine Teilwerte). Manueller Lauf: `MesswerteController` fängt `Exception` → HTTP 400 mit `message` (zusätzlich mappt `GlobalExceptionHandler` `IllegalStateException` auf 400). Auto-Lauf: `ZaehlerAggregationService` fängt und loggt — Log-Level dort ggf. auf ERROR anheben (s. Phase 5).

### Phase 5: Backend-`ZaehlerAggregationService`

**Datei:** `backend-service/src/main/java/ch/nacht/service/ZaehlerAggregationService.java`

- **Voraussichtlich keine funktionale Änderung:** Der Modus wird zentral in `MesswerteService.distribute` über die bereits explizit übergebene `orgId` geladen; `calculateSolarDistributionForOrg(org, ...)` liefert diese org. Damit greift der Bilanzmodus automatisch auch im Auto-Lauf, ohne `getCurrentOrgId()` → keine `NoOrganizationException`.
- **Anzupassen:** Das bestehende `catch` (Zeile ~132) loggt aktuell `log.warn`. Für den Bilanzdaten-Abbruch (Spec: **ERROR** mit Intervall-Angabe) das Log-Level auf `log.error` anheben bzw. den Abbruch-Fall gezielt als ERROR loggen; übrige Mandanten laufen weiter (bestehende Schleifen-Semantik bleibt).

### Phase 6: Backend-`StatistikService` / `StatistikDTO`

**Dateien:** `StatistikService.java`, `dto/StatistikDTO.java`

- `StatistikDTO`: neues Feld `verteilmodus` (String/Enum) + Getter/Setter.
- `StatistikService.getStatistik`: `EinstellungenService` injizieren und `verteilmodus` setzen (läuft im Request-Scope → `getVerteilmodus(organizationContextService.getCurrentOrgId())` oder `getEinstellungen()`).
- **Gemessene Rücklieferung:** Der Wert `bilanzRuecklieferung` (`sumTotalByEinheitTypAndZeitBetween(RUECKLIEFERUNG, …)`, als Betrag) wird bereits berechnet und ins DTO gesetzt (`setBilanzRuecklieferung`). FR-4.4 ist damit im Backend abgedeckt; im Frontend wird dieser Wert im Bilanzmodus als „tatsächliche Rücklieferung" gelabelt. Fehlt die `RUECKLIEFERUNG`-Einheit, ist der Wert `0.0`/leer → Anzeige entfällt (kein Fehler).
- Vergleichswerte (A–E, `bezugVonVnb`, `ruecklieferung`) bleiben **unverändert**.

### Phase 7: Frontend-Models

**Dateien:** `models/einstellungen.model.ts`, `models/statistik.model.ts`

```typescript
// einstellungen.model.ts
export type Verteilmodus = 'PRODUCER_MESSUNG' | 'BILANZ';

export interface RechnungKonfiguration {
  zahlungsfrist: string;
  iban: string;
  steller: Steller;
  verteilmodus?: Verteilmodus; // optional; fehlt bei Bestandsmandanten
}
```

```typescript
// statistik.model.ts – Statistik-Interface um das Modus-Feld ergänzen
verteilmodus?: 'PRODUCER_MESSUNG' | 'BILANZ';
```

### Phase 8: Frontend-Einstellungen-Component

**Dateien:** `components/einstellungen/einstellungen.component.{ts,html}`

- Reactive-Form um `verteilmodus`-Control erweitern (Default `PRODUCER_MESSUNG`, wenn Wert fehlt).
- Dropdown (`.zev-select`) mit übersetzten Labels `VERTEILMODUS_PRODUCER_MESSUNG` / `VERTEILMODUS_BILANZ`, Feld-Label `VERTEILMODUS`.
- Persistierung über den bestehenden Save-Flow (`einstellungen.service`), erfordert `einstellungen:write` (Route unverändert geschützt).

### Phase 9: Frontend-Statistik-Component

**Dateien:** `components/statistik/statistik.component.{ts,html}`

- Aktiven Modus anzeigen (Label `VERTEILMODUS` + Wert-Label).
- Im Modus `BILANZ`: Hinweis/Tooltip `STATISTIK_MODUS_BILANZ_HINWEIS` am Summen-Vergleich (Bilanz-Vergleiche tautologisch).
- Im Modus `BILANZ`: gemessene Rücklieferung aus `bilanzRuecklieferung` mit Label `STATISTIK_RUECKLIEFERUNG_GEMESSEN` anzeigen; fehlt der Wert (keine `RUECKLIEFERUNG`-Einheit) → Zeile ausblenden.

### Phase 10: Übersetzungen

**Datei:** `backend-service/src/main/resources/db/migration/V[XX]__Add_Bilanzmodell_Translations.sql`
(nächste freie Versionsnummer zum Umsetzungszeitpunkt via `zev-db` prüfen — aktuell höchste vorhandene ist `V84`, d.h. voraussichtlich `V85`.)

```sql
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('VERTEILMODUS', 'Verteilmodus', 'Distribution mode'),
('VERTEILMODUS_PRODUCER_MESSUNG', 'Producer-Messung', 'Producer measurement'),
('VERTEILMODUS_BILANZ', 'Bilanzmessung', 'Balance measurement'),
('BILANZMODELL_KEINE_BILANZDATEN', 'Keine Bilanzdaten (Bezug) für das Intervall vorhanden – Verteilung abgebrochen.', 'No balance data (grid draw) available for the interval – distribution aborted.'),
('STATISTIK_MODUS_BILANZ_HINWEIS', 'Im Bilanzmodus werden die Bezug/Rücklieferung-Vergleiche aus der Bilanz abgeleitet und sind daher tautologisch (Kontrollfunktion entfällt).', 'In balance mode the draw/feed-in comparisons are derived from the balance and are therefore tautological (control function no longer applies).'),
('STATISTIK_RUECKLIEFERUNG_GEMESSEN', 'Rücklieferung (gemessen)', 'Feed-in (measured)')
ON CONFLICT (key) DO NOTHING;
```

---

## Validierungen

### Backend-Validierungen
1. **Modus-Default:** Fehlt `verteilmodus` / fehlt die ganze `konfiguration` → `PRODUCER_MESSUNG` (`getVerteilmodus`).
2. **Bilanzdaten-Pflicht (nur BILANZ):** Fehlt die `BEZUG`-Einheit oder deren Messwert in einem Intervall → `IllegalStateException` mit Key `BILANZMODELL_KEINE_BILANZDATEN` + Intervall (Tag/Zeit); Rollback über `@Transactional`.
3. **Kein negativer Eigenverbrauch:** `S = max(0, ConsumerTotal − Bezug)`.
4. **Producer-`zev` nur bei `quelle == MQTT`** überschreiben (CSV-Producer behalten gemessenen Wert).
5. **Autorisierung unverändert:** Einstellungen `einstellungen:write`, Verteilung `messwerte:write`, Statistik `statistik:read` (`@PreAuthorize`). `orgId` nie aus dem Request im Hintergrund-Lauf.

### Frontend-Validierungen
1. **Dropdown-Werte:** nur `PRODUCER_MESSUNG` / `BILANZ`; Default `PRODUCER_MESSUNG` wenn Wert fehlt.
2. **Alle UI-Texte über `TranslationService`/`TranslatePipe`** (keine hartcodierten Texte, DE/EN).
3. **Fehleranzeige:** Backend-Fehler (`BILANZMODELL_KEINE_BILANZDATEN`) im Solar-Calculation-Flow als `.zev-message--error` mit übersetztem Text + Intervall darstellen.

---

## Offene Punkte / Annahmen

1. **Annahme (geklärt in Spec §8):** Modus-Wechsel bei bereits erstellten Rechnungen → **nur Hinweis**, keine Sperre, keine automatische Neuberechnung.
2. **Annahme (geklärt in Spec §8/FR-4.4):** Im Bilanzmodus wird die **tatsächlich gemessene** Rücklieferung (`bilanzRuecklieferung`) angezeigt; dieser Wert existiert bereits im `StatistikDTO` → nur Frontend-Label + Modus-abhängige Anzeige nötig.
3. **Annahme:** `ZaehlerAggregationService` benötigt keine funktionale Änderung, da der Modus zentral in `MesswerteService.distribute` über die übergebene `orgId` geladen wird; im Auto-Lauf ist lediglich das Log-Level des Abbruch-Falls auf ERROR anzuheben. Bei der Umsetzung verifizieren, dass `distribute` durchgängig die korrekte Mandanten-`orgId` erhält.
4. **Annahme:** `verteilmodus` wird als String-Enum im bestehenden JSONB-Blob gespeichert; kein Schema-Change, kein Backfill bestehender `einstellungen`-Zeilen nötig.
5. **Annahme:** Der `BEZUG`-Messwert pro Intervall ist der `total`-Wert der (max. einen) `BEZUG`-Einheit; positiv. Vorzeichen-/Betrags-Behandlung analog `StatistikService` (Bezug positiv, Rücklieferung als Betrag).
6. **Tests:** Unit-/Integration-/E2E-Tests werden über die Folge-Kommandos (`3_backend-tests`, `4_frontend-unit-tests`, `5_e2e-tests`) erstellt; besonders wichtig: **Regressionstest** `PRODUCER_MESSUNG` = heutiges Ergebnis sowie die Beispielrechnung `ConsumerTotal=10, Bezug=4 → S=4`.

---

## Nachträgliche Ergänzungen

### Pi-Gateway-Simulator übermittelt Bilanz/Rücklieferung (verifiziert, kein Code-Change)

Frage: Muss `pi-gateway/gateway/readers/sim_reader.py` für das Bilanzmodell angepasst werden, damit Bilanz/Rücklieferung übermittelt werden?

**Ergebnis: Nein – bereits vorhanden** (eingeführt mit der Bilanzmesspunkt-Umsetzung, Commit `85e0c81`). Verifiziert durch Ausführen des Readers:

| Messpunkt-Name enthält | `_mode` | Δ Bezug | Δ Einspeisung |
|---|---|---|---|
| „bilanz" | `bilanz` | wächst | wächst (eine Meldung, beide Register → Ingest splittet auf `BEZUG`/`RUECKLIEFERUNG`) |
| „bezug" | `bezug` | wächst | 0 |
| „rücklieferung"/„ruecklieferung" | `ruecklieferung` | 0 | wächst |
| „producer" | `producer` | ~0 | wächst (Einspeisung überwiegt) |
| sonst | `consumer` | wächst | ~0 |

- `pi-gateway/config.sim.yaml` enthält bereits einen `Bilanz`-Zähler; `publisher.py` sendet beide Register (`zaehlerstandBezug`/`zaehlerstandEinspeisung`) je Meldung.
- Damit ist das Bilanzmodell ohne Hardware über den kompletten MQTT-Pfad testbar (Reader → Publisher → Ingest → Aggregation → Verteilung mit `verteilmodus=BILANZ`).
- **Bewusst nicht geändert:** koherente/korrelierte Bilanzwerte (Bilanz-Bezug ist ein unabhängiger Zufalls-Random-Walk, physikalisch nicht an Consumer/Producer gekoppelt) – für einen synthetischen End-to-End-Test akzeptiert; keine Anforderung.
