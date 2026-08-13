# Bilanzmodell

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Ein alternatives **Abrechnungs-/Verteilmodell**, bei dem der ZEV-Eigenverbrauch nicht aus der Producer-Messung, sondern aus der **gemessenen Bilanz am Netzanschluss** abgeleitet wird. Pro Zeitintervall gilt: `ZEV-Eigenverbrauch = ConsumerTotal − Bezug(Bilanz)`. Dieser Betrag wird – wie heute – auf die Consumer verteilt (EQUAL_SHARE/PROPORTIONAL) und bestimmt deren ZEV-/Netz-Anteil. Das Modell ist **pro Mandant wählbar** (Einstellung).
* **Warum machen wir das:** Der Bilanzzähler (Verrechnungsmessung des VNB) ist die „Ground Truth" des Netzaustauschs. Rechnet man direkt darauf ab, summiert sich die interne Verteilung **exakt** auf die externe VNB-Rechnung (keine Reconciliation-Lücke), und **Batteriespeicher, Wirkungsgradverluste und weitere Behind-the-Meter-Effekte sind automatisch enthalten**, ohne sie einzeln zu modellieren (vgl. `Specs/Batteriespeicher.md` – dort explizit modelliert). Die beiden Modelle **koexistieren**; die Umsetzung startet mit dem Bilanzmodell.
* **Aktueller Stand:**
  - Heute (Modell „Producer-Messung"): `MesswerteService.distribute` verteilt die **Producer-Produktion** je Zeitpunkt auf die Consumer (`zev_calculated`, und `zev` beim MQTT-Sentinel `zev == 0`). Consumer-Rechnung: ZEV-Anteil zum ZEV-Tarif, Rest (`total − zev`) zum VNB-Tarif; Producer erhalten nur eine Grundgebühr-Rechnung (`RechnungService`).
  - Die Bilanz-Einheiten `BEZUG`/`RUECKLIEFERUNG` existieren bereits (`Specs/Bilanzmesspunkt.md`) und werden heute nur zur **Plausibilisierung** (Summen-Vergleich) verwendet, nicht zur Verteilung.
  - Mandanten-Konfiguration liegt in `einstellungen.konfiguration` (JSONB, `RechnungKonfigurationDTO`: `zahlungsfrist`, `iban`, `steller`).

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Modus-Auswahl je Mandant
1. Neue Einstellung **`verteilmodus`** in `einstellungen.konfiguration` (Feld in `RechnungKonfigurationDTO`, Typ: neues Enum `Verteilmodus` im `entity`-Package) mit den Werten `PRODUCER_MESSUNG` (Default, heutiges Verhalten) und `BILANZ`. Der Default wird **im Code** gesetzt (kein `@NotNull` auf dem Feld), Jackson muss ein fehlendes/unbekanntes Feld null-tolerant deserialisieren. **Fehlt oder ist `null`** (Bestandsmandanten, altes JSON) **oder fehlt die ganze `konfiguration`** (Mandant ohne Einstellungen), gilt `PRODUCER_MESSUNG` (rückwärtskompatibel).
2. Der Modus ist in der **Einstellungen-Seite** wählbar (Dropdown, übersetzte Labels `VERTEILMODUS_PRODUCER_MESSUNG` / `VERTEILMODUS_BILANZ`), erfordert `einstellungen:write`. Das Dropdown steht **ganz oben** im Einstellungsformular (erstes Feld, vor Zahlungsfrist), da der Verteilmodus die grundlegende Abrechnungsart bestimmt.
3. Der gewählte Modus steuert die Solarverteilung (`MesswerteService.distribute`) sowohl beim manuellen Lauf (`/solar-calculation`) als auch beim automatischen Lauf nach der MQTT-Aggregation (`ZaehlerAggregationService`).
4. **Org-expliziter Konfig-Zugriff:** Der Modus muss über eine mandanten-explizite Methode gelesen werden (z.B. `EinstellungenService.getVerteilmodus(orgId)`), **nicht** über den request-scoped `getCurrentOrgId()`. Grund: Der MQTT-Auto-Lauf (`ZaehlerAggregationService`) läuft ohne Request-Kontext und übergibt die `org_id` explizit; ein Zugriff über `getCurrentOrgId()` wäre dort `null` und würde seit dem Fail-closed-Verhalten von `HibernateFilterService.enableOrgFilter()` eine `NoOrganizationException` auslösen. Liefert der Zugriff `null` (Mandant ohne Einstellungen/`konfiguration`), gilt `PRODUCER_MESSUNG` (Default, s. FR-1.1).

### FR-2: Verteilung im Bilanzmodell
Pro Zeitintervall mit `ConsumerTotal` = Summe Consumer-`total`, `Bezug` = `total` der Einheit vom Typ `BEZUG` (positiv):
1. **Verteilbarer ZEV-Eigenverbrauch:** `S = max(0, ConsumerTotal − Bezug)`. `S` ist der intern (aus PV und/oder Speicher) gedeckte Teil des Verbrauchs.
2. **Verteilung:** `S` wird mit dem gewählten Algorithmus (EQUAL_SHARE/PROPORTIONAL, je Consumer am eigenen `total` gekappt) auf die Consumer verteilt — dieselben Algorithmen wie heute, nur mit `S` statt der Producer-Produktion als zu verteilende Menge.
   - **Producer-unabhängige Iteration:** Im Bilanzmodus wird ein Intervall über **Consumer + BEZUG** verarbeitet, **nicht** producer-gesteuert. Der heutige Loop (`MesswerteService.distribute`) überspringt Intervalle ohne PRODUCER (`producers.isEmpty()` → `continue`); im BILANZ-Zweig darf dieser Skip **nicht** greifen, da `S` producer-unabhängig ist (bilanz-/batterie-only-ZEV ohne Producer-Messung ist ein gültiger Fall). Ein Intervall mit Consumern und Bezug, aber ohne Producer-Messwert, verteilt `S` regulär.
3. **Consumer-`zev`:** je Consumer = zugeteilte Menge; `zev_calculated` = derselbe Wert. MQTT-Sentinel-Regel bleibt (`zev == 0` → berechneter Wert; gemessene CSV-Werte bleiben).
4. **Producer-`zev` (nur Statistik):** = `|ProduktionTotal| − |Rücklieferung(Bilanz)|`, proportional zur Produktion auf die Producer verteilt (im ZEV verbrauchte Produktion lt. Bilanz). Beeinflusst die Verrechnung nicht. **Fehlt die `RUECKLIEFERUNG`-Einheit**, wird Producer-`zev` auf `0` gesetzt (Statistik unvollständig) — der Lauf **läuft weiter**, da dieser Wert nicht abrechnungsrelevant ist. Wie heute wird Producer-`zev` **nur bei `quelle == MQTT`** überschrieben; CSV-Producer behalten ihren gemessenen `zev`-Wert (bestehender Guard in `aktualisiereProducerZev` bleibt bestehen). Der Guard basiert bewusst auf `quelle` und **nicht** auf einem Feature Flag: Ob `zev` gemessen (CSV) oder berechnet (MQTT-Sentinel `zev == 0`) ist, ist eine Datenprovenienz-Entscheidung **pro Messwert** — ein mandantenweiter Flag könnte diese Unterscheidung nicht ausdrücken und würde gemessene CSV-Werte überschreiben (Datenverlust). Der mandantenweite Schalter ist bereits `verteilmodus`.

#### FR-2.4a: Berechnung von Producer-`zev` im Detail
Umsetzung: `MesswerteService.distributeBilanz` (Ermittlung der Menge) + `MesswerteService.aktualisiereProducerZev` (Aufteilung/Persistenz). Pro Zeitintervall:

1. **Im ZEV verbrauchte Produktion lt. Bilanz:**
   ```
   imZev = max(0, Σ|total_p| − Σ|total_r|)
   ```
   * `total_p` = `total` aller `PRODUCER`-Messwerte des Intervalls **mit `total < 0`** (nur echte Produktion)
   * `total_r` = `total` aller Messwerte der `RUECKLIEFERUNG`-Bilanz-Einheit
   * `max(0, …)`: Ist die Rücklieferung grösser als die Produktion (Messdifferenz/Zeitversatz), gilt `0` — kein negativer Eigenverbrauch.
2. **Aufteilung proportional zur eigenen Produktion** (bei mehreren Producern):
   ```
   anteil_i = imZev × |total_i| / Σ|total_j|      (BigDecimal, 10 Nachkommastellen, HALF_UP)
   zev_i    = −anteil_i
   ```
   Das **negative Vorzeichen** ist gewollt: Bei Produktion ist `total` negativ, `zev` bleibt dazu vorzeichenkonsistent.
3. **Guards** (in dieser Reihenfolge wirksam):

   | Fall | Ergebnis |
   |------|----------|
   | `quelle != MQTT` (z.B. CSV) | Messwert bleibt **unangetastet** (gemessener ZEV-Anteil, s. FR-2.4) |
   | Producer mit `total >= 0` (z.B. Steuergerät, kein Produktions-Messwert) | `zev = 0` |
   | Keine `RUECKLIEFERUNG`-Einheit vorhanden | `zev = 0` für alle Producer des Intervalls (`imZev` nicht bestimmbar) |
   | Keine Consumer im Intervall | Producer-`zev` wird **trotzdem** aus `imZev` gesetzt — die Ermittlung hängt allein an Produktion/Rücklieferung. **Abweichung zum Modus `PRODUCER_MESSUNG`**, wo Producer-`zev` in diesem Fall auf `0` gesetzt wird (dort ist die Verteilsumme die Quelle, und die ist ohne Consumer 0). |
   | Intervall wegen fehlender Bilanzdaten übersprungen (FR-2.5) | Producer-`zev` ist **trotzdem gesetzt**: Es wird **vor** der Bezugs-Prüfung geschrieben, da `imZev` den Bezug nicht braucht. Nur die Consumer-Verteilung entfällt. |

4. **Producer-`zev` ist nicht die an die Consumer verteilte Menge.** Im Bilanzmodus werden zwei Schätzer derselben physikalischen Grösse **getrennt** ermittelt:

   | | Formel | Verwendung |
   |---|---|---|
   | Consumer-Seite | `S = max(0, ConsumerTotal − Bezug)` | **abrechnungsrelevant** (FR-2.1/2.2) |
   | Producer-Seite | `imZev = max(0, Produktion − Rücklieferung)` | **nur Statistik** |

   Beide dürfen auseinanderlaufen — z.B. durch Verbraucher, die nicht als ZEV-Consumer erfasst sind, durch Speicherverluste oder durch Messungenauigkeit/Zeitversatz der Bilanzzähler. Das ist **kein Fehlerfall** und wird nicht korrigiert oder gemeldet; die Verrechnung folgt ausschliesslich der Consumer-Seite (FR-3).
   > *Abgrenzung zum Modus `PRODUCER_MESSUNG`:* Dort wird dieselbe Methode mit der **tatsächlich an die Consumer verteilten** Menge aufgerufen, Producer-`zev` ist dort also per Konstruktion identisch mit der Verteilsumme. Diese Kopplung entfällt im Bilanzmodus bewusst.

#### FR-2.4b: Differenz Producer-`zev` ↔ Consumer-`zev` (Interpretation/Diagnose)
Die Differenz der beiden Summen ist **kein Rechenfehler, sondern der Restposten der Knotenbilanz am Hausanschluss**: Energie, die erzeugt oder bezogen wurde, aber weder eingespeist noch bei einem ZEV-Consumer gemessen wurde.

Mit `P = |Σ Producer-zev| = imZev` und `C = Σ Consumer-zev = S` gilt — solange **keine** der beiden `max(0, …)`-Klemmungen greift:

```
Δ = P − C = (Produktion − Rücklieferung) − (ConsumerTotal − Bezug)
          = (Produktion + Bezug) − (Rücklieferung + ConsumerTotal)
             └── kommt herein ──┘   └──── geht gemessen hinaus ────┘
```

Die beiden Summen stammen aus **disjunkten Zählergruppen** (Producer + `RUECKLIEFERUNG` ↔ Consumer + `BEZUG`); Δ ist genau die Lücke zwischen ihnen.

**Woraus sich Δ zusammensetzt:**

| Δ > 0 (mehr herein als gemessen hinaus) | Δ < 0 (Consumer verbrauchen mehr als erklärbar) |
|---|---|
| Verbraucher, die **nicht** als ZEV-Consumer erfasst sind (Allgemeinstrom, WP, Ladestation, Lift) | **Batterieentladung** — Quelle ohne gemessene Produktion |
| **Batterieladung** — Senke im Intervall | Lücken in den Producer-/Rücklieferungs-Messwerten (Produktion untererfasst) |
| Verluste (Wechselrichter je nach Messpunkt, Leitungen, Speicher-Roundtrip) | Zeitversatz zwischen Zählern an Intervallgrenzen (dreht sich meist im Folgeintervall wieder heraus) |
| Messtoleranzen / unterschiedliche Genauigkeitsklassen der Zähler | dito |

Ein **konstanter positiver Sockel** ist der Normalfall (unerfasste Allgemeinverbraucher + Verluste). Aussagekräftig sind nicht der Absolutwert, sondern **Sprünge** und **Vorzeichenwechsel** — sie deuten auf Datenlücken oder Zählerprobleme.

**Wann die Gleichung nicht gilt** (Δ dann nicht als Knotenbilanz interpretierbar):
1. **Klemmung:** Intervalle mit `Rücklieferung > Produktion` bzw. `Bezug > ConsumerTotal` liefern `0` statt eines negativen Werts → über einen Zeitraum summiert ist Δ zu positiv verzerrt.
2. **Übersprungene Intervalle** (kein Bezugs-Messwert, FR-2.5): Producer-`zev` wird geschrieben, die Consumer behalten ihren **alten** Wert aus einem früheren Lauf — für solche Zeiträume ist Δ bedeutungslos.
3. **Datenprovenienz:** Producer-`zev` wird nur bei `quelle = MQTT` überschrieben, Consumer-`zev` nur beim Sentinel `zev == 0`. Bei CSV-Daten vergleicht Δ zwei **gemessene** Werte statt der Bilanz-Grössen.
4. **Rundung** auf 3 Nachkommastellen je Zuteilung (`SolarDistribution.adjustRounding`) — vernachlässigbar, aber nicht exakt null.

**Warum die Kennzahl nützlich ist:** Im Bilanzmodus ist der Vergleich „Bezug von VNB (berechnet) ↔ Bilanz-Bezug" tautologisch (FR-3.3/FR-4.2) und kann nichts aufdecken. Δ ist die einzige Grösse, die Producer- und Rücklieferungszähler **unabhängig** gegen die Consumer-Seite stellt — der brauchbare Plausibilitätscheck. Sinnvoll als **relative** Kennzahl `Δ / Produktion` je Monat:

```sql
SELECT date_trunc('month', m.zeit) AS monat,
       ROUND((SUM(-m.zev) FILTER (WHERE e.typ = 'PRODUCER'))::numeric, 1) AS prod_zev,
       ROUND((SUM( m.zev) FILTER (WHERE e.typ = 'CONSUMER'))::numeric, 1) AS cons_zev,
       ROUND((COALESCE(SUM(-m.zev) FILTER (WHERE e.typ = 'PRODUCER'), 0)
            - COALESCE(SUM( m.zev) FILTER (WHERE e.typ = 'CONSUMER'), 0))::numeric, 1) AS delta
FROM zev.messwerte m
JOIN zev.einheit e ON e.id = m.einheit_id
WHERE m.org_id = <orgId>
GROUP BY 1 ORDER BY 1;
```
5. **Nur `BEZUG` ist abrechnungskritisch** — es sind aber zwei Fälle zu unterscheiden:
   - **Fehlende Bilanzdaten in einzelnen Intervallen** (Datenlücke, z.B. Zähler-/Übertragungsausfall): `S` ist für dieses Intervall nicht bestimmbar → **nur dieses Intervall wird übersprungen, der Lauf läuft weiter** (Nachtrag; vormals harter Abbruch). Die Consumer behalten für das übersprungene Intervall ihren bisherigen `zev`-Wert; es wird nichts geschrieben.
     * **Nicht still:** Nach dem Lauf wird **eine** Systemmeldung mit Level **`WARN`** erfasst (Key `BILANZMODELL_INTERVALLE_UEBERSPRUNGEN`, Kategorie Bilanzmodell) — mit **Anzahl** der übersprungenen Intervalle und dem **Zeitraum** (erste/letzte Lücke). Bewusst **eine** Sammelmeldung statt einer je Intervall.
     * Die Anzahl wird zusätzlich im Ergebnis zurückgegeben (`CalculationResult.uebersprungeneIntervalle`) und je Lücke als `WARN` geloggt.
     * **Konsequenz für die Abrechnung:** Für übersprungene Intervalle wird **kein ZEV-Anteil** verteilt — die Abrechnung ist dort unvollständig (zu niedriger ZEV-Anteil). Nach Nachlieferung der Bilanzdaten ist die Verteilung **erneut auszuführen**; ein lückenloser Folgelauf **auto-resolvt** die Meldung (Selbstheilung).
     * Gilt gleichermaßen für den **manuellen** Lauf (kein HTTP-Fehler; Ergebnis enthält die Anzahl) und den **automatischen** Lauf (MQTT).
   - **Fehlende `BEZUG`-Einheit** (Konfigurationsfehler, nicht Datenlücke): Es liesse sich **kein einziges** Intervall verteilen → weiterhin **harter Abbruch** mit `IllegalStateException` (Message = Key `BILANZMODELL_KEINE_BILANZDATEN`), Rollback über `@Transactional`, Systemmeldung mit Level `ERROR`. Manuell → HTTP 400; automatisch → ERROR-Log, übrige Mandanten laufen weiter.
   Die fehlende `RUECKLIEFERUNG`-Einheit ist **kein** Abbruchgrund (siehe FR-2.4).

### FR-3: Verrechnung
1. Die Consumer-Rechnung ist **strukturell unverändert**: ZEV-Anteil (`zev`) zum ZEV-Tarif, Netz-Anteil (`total − zev`) zum VNB-Tarif. Nur die Herkunft von `zev` ändert sich (Bilanz statt Producer-Verteilung).
2. Producer werden **unverändert** abgerechnet (nur Grundgebühr, keine kWh-Vergütung); Bilanz-Typen werden – wie bisher – **nicht** verrechnet.
3. Da die Consumer-`zev` sich per Konstruktion zu `ConsumerTotal − Bezug` summieren, entspricht die Summe der verrechneten Netz-Anteile exakt dem Bilanz-Bezug (verrechnungstreu).

### FR-4: Statistik
1. Die bestehenden Summen A–E und die berechneten Vergleichswerte (`Bezug von VNB`, `Rücklieferung`) bleiben erhalten.
2. **Hinweis zur Plausibilisierung:** Im Bilanzmodell wird der Vergleich „Bezug von VNB (berechnet) ↔ Bilanz Bezug" **tautologisch** (per Konstruktion ≈ 0), da die Verteilung aus der Bilanz abgeleitet ist. Die Vergleiche bleiben sichtbar, verlieren aber ihre Kontrollfunktion; das wird über einen **Hinweis/Tooltip** (Key `STATISTIK_MODUS_BILANZ_HINWEIS`) am Summen-Vergleich kenntlich gemacht.
3. Der aktuell wirksame Modus wird in der Statistik angezeigt (Key `VERTEILMODUS`).
4. **Tatsächliche Rücklieferung (Bilanzmodus):** Im Modus `BILANZ` wird zusätzlich die **gemessene** Rücklieferung aus der `RUECKLIEFERUNG`-Bilanz-Einheit angezeigt (Summe deren `total` im Zeitraum) statt nur des berechneten Werts (Key `STATISTIK_RUECKLIEFERUNG_GEMESSEN`). Fehlt die `RUECKLIEFERUNG`-Einheit, entfällt diese Anzeige (kein Abbruch, vgl. FR-2.4).

### FR-5: Persistierung & i18n
* Keine neue Tabelle/Spalte: `verteilmodus` wird als Feld in `RechnungKonfigurationDTO` (JSONB `einstellungen.konfiguration`) ergänzt.
* Neue Übersetzungs-Keys via Flyway-Migration (`ON CONFLICT (key) DO NOTHING`): `VERTEILMODUS`, `VERTEILMODUS_PRODUCER_MESSUNG`, `VERTEILMODUS_BILANZ`, `BILANZMODELL_KEINE_BILANZDATEN`, `STATISTIK_MODUS_BILANZ_HINWEIS`, `STATISTIK_RUECKLIEFERUNG_GEMESSEN` (DE/EN) sowie – Nachtrag FR-2.5 – `BILANZMODELL_INTERVALLE_UEBERSPRUNGEN`.
* Multi-Tenancy unverändert: die Einstellung liegt je `org_id` in `einstellungen`; `orgId` stammt aus dem Kontext, nicht aus dem Request.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

### Modus-Auswahl
* [ ] `RechnungKonfigurationDTO` enthält `verteilmodus` (Enum `Verteilmodus`); **altes JSON ohne das Feld** deserialisiert fehlerfrei (`null`) und wird im Code auf `PRODUCER_MESSUNG` gemappt (Bestandsmandanten unverändert).
* [ ] Mandant **ohne Einstellungen** (`konfiguration == null`): `getVerteilmodus(orgId)` liefert `PRODUCER_MESSUNG` (Default), keine Exception.
* [ ] In der Einstellungen-Seite ist der Modus wählbar und wird persistiert (Roundtrip); erfordert `einstellungen:write`.

### Verteilung Bilanzmodell
* [ ] Bei `verteilmodus = BILANZ` verteilt `MesswerteService.distribute` pro Intervall `S = max(0, ConsumerTotal − Bezug)` auf die Consumer (EQUAL_SHARE/PROPORTIONAL).
* [ ] Beispiel `ConsumerTotal=10, Bezug=4` → `S=6` wird verteilt; Consumer-`zev`-Summe = 6, Netz-Anteil-Summe = 10 − 6 = 4 = Bezug (verrechnungstreu, FR-3.3).
* [ ] `Bezug > ConsumerTotal` (z.B. Batterie lädt aus Netz) → `S=0`, kein ZEV-Anteil in diesem Intervall.
* [ ] Intervall mit Consumern und Bezug, aber **ohne Producer-Messwert** → `S` wird trotzdem verteilt (kein producer-gesteuerter Skip im BILANZ-Zweig).
* [ ] Fehlt die **`BEZUG`-Einheit** → **manueller** Lauf bricht mit `IllegalStateException` (Message = Key `BILANZMODELL_KEINE_BILANZDATEN`) → HTTP 400 ab; keine `zev`-Werte werden geschrieben (Rollback).
* [ ] Gleicher Konfigurationsfehler im **MQTT-Auto-Lauf** → Lauf des Mandanten bricht ab, ERROR-Log; übrige Mandanten laufen weiter; keine `zev`-Werte geschrieben.
* [ ] Fehlen (nur) die **Bezugs-Messwerte einzelner Intervalle** → der Lauf bricht **nicht** ab: betroffene Intervalle werden übersprungen, die übrigen normal verteilt (manuell **und** automatisch).
* [ ] Fehlt (nur) die `RUECKLIEFERUNG`-Einheit → Producer-`zev` = 0, der Lauf bricht **nicht** ab (Consumer-Abrechnung unberührt).
* [ ] Producer-`zev` wird im Bilanzmodus nur bei `quelle == MQTT` gesetzt; CSV-Producer behalten den gemessenen `zev`.
* [ ] Beispiel `Produktion=8, Rücklieferung=3`, ein MQTT-Producer → `imZev=5`, dessen `zev = −5` (negatives Vorzeichen, konsistent zu `total`).
* [ ] Zwei MQTT-Producer mit `total = −6` / `−2` und `imZev = 4` → `zev = −3` / `−1` (proportional zur eigenen Produktion).
* [ ] `Rücklieferung > Produktion` (Messdifferenz/Zeitversatz) → `imZev = 0`, Producer-`zev = 0` (kein negativer Eigenverbrauch).
* [ ] Ein Producer-Messwert mit `total >= 0` (Steuergerät) erhält `zev = 0` und geht nicht in die Produktionssumme ein.
* [ ] Intervall **ohne Consumer**: Producer-`zev` wird dennoch aus `imZev` gesetzt (im Unterschied zu `PRODUCER_MESSUNG`, wo dann `0` gesetzt wird).
* [ ] Intervall **ohne Bezugs-Messwert** (übersprungen, FR-2.5): Producer-`zev` ist gesetzt, Consumer-`zev` unverändert.
* [ ] Producer-`zev`-Summe und Consumer-`zev`-Summe eines Zeitraums dürfen differieren; das erzeugt **keine** Systemmeldung und keine Korrektur.
* [ ] Der Bilanzmodus greift auch beim **MQTT-Auto-Lauf** (`ZaehlerAggregationService`): der Modus wird org-explizit geladen (kein `getCurrentOrgId()`), keine `NoOrganizationException`.
* [ ] Bei `verteilmodus = PRODUCER_MESSUNG` ist das Verteilergebnis **identisch** zu heute (Regression).

### Verrechnung
* [ ] Consumer-Rechnung nutzt `zev` (ZEV-Tarif) und `total − zev` (VNB-Tarif) wie bisher; Summe der Netz-Anteile über alle Consumer = Bilanz-Bezug des Zeitraums.
* [ ] Producer erhalten unverändert nur Grundgebühr-Rechnungen; für Bilanz-Typen wird keine Rechnung erzeugt.

### Statistik & Sicherheit
* [ ] Der aktive Modus wird in der Statistik angezeigt; im Bilanzmodus weist ein Hinweis darauf hin, dass die Bilanz-Vergleiche tautologisch sind.
* [ ] Im Bilanzmodus wird die **gemessene** Rücklieferung aus der `RUECKLIEFERUNG`-Einheit angezeigt; fehlt die Einheit, entfällt die Anzeige ohne Fehler.
* [ ] Statistik bleibt mit `statistik:read`, Einstellungen mit `einstellungen:write` erreichbar; alle neuen UI-Texte via `TranslationService` (DE/EN).

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Pro Zeitpunkt eine zusätzliche kleine Abfrage (Bilanz-`total`); Komplexität unverändert O(Zeitpunkte × Einheiten). `statistik`-Cache unverändert.

### NFR-2: Sicherheit
* Keine neuen Permissions: Modus-Einstellung `einstellungen:write` (Fachrolle `org_admin`/`zev_admin`), Verteilung `messwerte:write`, Statistik `statistik:read`. Multi-Tenancy (`org_id`, `orgFilter`) unverändert; `orgId` nie aus dem Request.

### NFR-3: Kompatibilität
* Rein additiv und rückwärtskompatibel: Default `PRODUCER_MESSUNG` = heutiges Verhalten. Kein Schema-Change (JSONB-Feld). Bestehende Auswertungen/Rechnungen unverändert, solange der Modus nicht umgestellt wird.

## 5. Edge Cases & Fehlerbehandlung
| Szenario | Verhalten |
|----------|-----------|
| `verteilmodus` fehlt (Bestandsmandant) | wie `PRODUCER_MESSUNG` (Default) |
| Modus `BILANZ`, aber **keine `BEZUG`-Einheit** (Konfigurationsfehler) | Harter Abbruch, nichts wird geschrieben. Manuell: HTTP 400 mit `BILANZMODELL_KEINE_BILANZDATEN`. Auto: ERROR-Log, Mandant übersprungen, übrige laufen weiter |
| `Bezug > ConsumerTotal` (Netzladung Batterie / Messdifferenz) | `S = 0` in diesem Intervall (kein negativer Eigenverbrauch) |
| Keine Consumer im Intervall | nichts zu verteilen (`S` irrelevant), keine Consumer-`zev`-Werte; Producer-`zev` wird dennoch aus `imZev` gesetzt (FR-2.4a) |
| `Rücklieferung > Produktion` im Intervall (Messdifferenz/Zeitversatz) | `imZev = 0` → Producer-`zev = 0`; kein Abbruch, keine Meldung (nicht abrechnungsrelevant) |
| Producer-`zev`-Summe ≠ Consumer-`zev`-Summe über den Zeitraum | **kein Fehlerfall** — zwei getrennt ermittelte Schätzer derselben Grösse (FR-2.4a.4); die Differenz ist der Restposten der Knotenbilanz (Interpretation und Grenzen: FR-2.4b). Verrechnung folgt allein der Consumer-Seite |
| Bilanzdaten-Lücke einzelner Intervalle | **Intervall überspringen, Lauf fortsetzen** (FR-2.5). Für die Lücke wird nichts geschrieben; danach **eine** `WARN`-Systemmeldung `BILANZMODELL_INTERVALLE_UEBERSPRUNGEN` mit Anzahl + Zeitraum, je Lücke ein WARN-Log. Anzahl in `CalculationResult.uebersprungeneIntervalle`. Abrechnung für diese Intervalle unvollständig → nach Datennachlieferung erneut ausführen |
| Lückenloser Folgelauf nach zuvor übersprungenen Intervallen | **Auto-Resolve** der offenen `BILANZMODELL_INTERVALLE_UEBERSPRUNGEN`-Meldung (Selbstheilung) |
| Umstellung des Modus rückwirkend | Neuberechnung überschreibt `zev`/`zev_calculated` für den gewählten Zeitraum; Nutzer wird auf Auswirkung auf bereits erstellte Rechnungen hingewiesen |
| Netzwerkfehler beim Laden von Statistik/Einstellungen | bestehende Fehlerbehandlung greift unverändert |

## 6. Abhängigkeiten & betroffene Funktionalität
* **Voraussetzungen:** Bilanzmesspunkt (`Specs/Bilanzmesspunkt.md`, Typen `BEZUG`/`RUECKLIEFERUNG`), Solarverteilung, Einstellungen, Statistik. Ein installierter/zuverlässiger Bilanzzähler ist für den Modus `BILANZ` **zwingend**.
* **Betroffener Code (Backend):**
  - `dto/RechnungKonfigurationDTO.java` — Feld `verteilmodus` (nullable, Default `PRODUCER_MESSUNG`).
  - `service/EinstellungenService.java` — **org-explizite** Lese-Methode `getVerteilmodus(orgId)` (bzw. `getEinstellungenForOrg(orgId)`), die **nicht** `getCurrentOrgId()` nutzt (für den Hintergrund-Lauf, s. FR-1.4).
  - `service/MesswerteService.java` — **Kern**: neue Abhängigkeit auf `EinstellungenService` (bislang nicht injiziert, Konstruktor erweitern); `distribute` verzweigt nach Modus; im Bilanzmodus `S = max(0, ConsumerTotal − Bezug)` als Verteilmenge (statt Producer-Produktion), Consumer-`zev` daraus; Producer-`zev` aus `Produktion − Rücklieferung` (0, falls keine `RUECKLIEFERUNG`-Einheit). BILANZ-Zweig iteriert **producer-unabhängig** (kein `producers.isEmpty()`-Skip). Abbruch bei fehlenden Bilanzdaten via `IllegalStateException` (Message = Key + Intervall) — im `GlobalExceptionHandler` bereits auf HTTP 400 gemappt (kein neuer Handler nötig); der Rollback über `@Transactional` garantiert „keine Teilwerte".
  - `service/ZaehlerAggregationService.java` — Modus beim Auto-Lauf über `getVerteilmodus(orgId)` (explizite org) berücksichtigen.
  - `service/StatistikService.java` — Modus-Anzeige / Hinweis; Vergleichswerte unverändert.
  - `service/RechnungService.java` — unverändert (nutzt weiterhin `zev`); nur Regressionsabsicherung.
* **Betroffener Code (Frontend):** `einstellungen.component.*` + Model (Modus-Dropdown), `statistik.component.*` (Modus-Anzeige/Hinweis), Tests/Mocks.
* **Datenmigration:** keine (nur Übersetzungs-Keys via Flyway, nächste freie Version zum Zeitpunkt der Umsetzung eruieren).
* **End-to-End-Test via Pi-Gateway-Simulator:** Der Publisher-Simulator (`pi-gateway/gateway/readers/sim_reader.py`) übermittelt Bilanz-Daten **bereits** – kein Anpassungsbedarf für das Bilanzmodell. Der Modus `bilanz` (Messpunkt-Name enthält „bilanz") lässt beide Register wachsen (eine Meldung mit Bezug **und** Einspeisung; der Backend-Ingest splittet sie auf die Einheiten `BEZUG`/`RUECKLIEFERUNG`); alternativ existieren die Einzel-Modi `bezug` und `ruecklieferung`. Die Beispiel-Konfiguration `pi-gateway/config.sim.yaml` enthält bereits einen `Bilanz`-Zähler. Damit ist das Bilanzmodell ohne Hardware über den kompletten MQTT-Pfad (Reader → Publisher → Ingest → Aggregation → Verteilung mit `verteilmodus=BILANZ`) testbar. *(Verifiziert – kein Code-Change; siehe Umsetzungsplan „Nachträgliche Ergänzungen".)*

## 7. Abgrenzung / Out of Scope
* **Producer-kWh-Vergütung** — Producer bleiben bei Grundgebühr (geklärt).
* **Explizite Speicher-Modellierung** — im Bilanzmodell nicht nötig (Batterie implizit über die Bilanz); die explizite Variante bleibt als `Specs/Batteriespeicher.md` bestehen (Koexistenz, andere Mandanten).
* **Automatische Modus-Erkennung** — der Modus wird bewusst manuell je Mandant gesetzt (keine Auto-Umschaltung).
* **Rückwirkende automatische Neuberechnung/Neuverrechnung** bereits gestellter Rechnungen bei Modus-Wechsel — nur Hinweis, keine automatische Korrektur.
* **Δ (FR-2.4b) als Kennzahl in Statistik/Monitoring** — bewusst **nicht** umgesetzt: keine Anzeige, kein Schwellwert, keine Systemmeldung. FR-2.4b beschreibt die Grösse für die **manuelle Diagnose** (Abfrage/Datenbank-Ansicht). Eine Aufnahme in die Statistik wäre eine eigene Anforderung.

## 8. Offene Fragen
Vorab geklärt:
* [x] **Aktivierung:** pro Mandant wählbare Einstellung (`verteilmodus`), Default `PRODUCER_MESSUNG`.
* [x] **Verteilbasis:** `S = ConsumerTotal − Bezug` (Consumer-seitig), auf 0 begrenzt.
* [x] **Verhältnis zu Batteriespeicher:** Koexistenz; Bilanzmodell wird zuerst umgesetzt.
* [x] **Producer:** unverändert (nur Grundgebühr).

Geklärt (Review):
* [x] Bei **Bilanzdaten-Lücken einzelner Intervalle**: Das betroffene Intervall wird **übersprungen**, der Lauf läuft weiter; für die Lücke wird **kein** `zev` geschrieben. Siehe FR-2.5 / §5.
* [x] Nach einem Lauf mit Lücken existiert **genau eine** `WARN`-Systemmeldung `BILANZMODELL_INTERVALLE_UEBERSPRUNGEN` mit **Anzahl** und **Zeitraum** (nicht eine Meldung je Intervall); `CalculationResult.uebersprungeneIntervalle` nennt die Anzahl.
* [x] Ein gültiges Intervall wird **auch dann** verteilt, wenn im selben Lauf ein anderes Intervall wegen fehlender Bilanzdaten übersprungen wurde.
* [x] Ein **lückenloser** Folgelauf setzt die offene Übersprungen-Meldung automatisch auf erledigt (Auto-Resolve).
* [x] Fehlt die **`BEZUG`-Einheit** ganz (Konfigurationsfehler), bricht der Lauf weiterhin hart ab (HTTP 400 / ERROR-Log, Rollback).
* [x] **Modus-Wechsel mit bereits erstellten Rechnungen:** nur **Hinweis** (keine Sperre, keine automatische Neuberechnung). Siehe §5 / §7.
* [x] **`RUECKLIEFERUNG`-Wert:** Im Bilanzmodus wird die **tatsächlich gemessene** Rücklieferung in der Statistik angezeigt (statt nur berechnet). Siehe FR-4.4.
