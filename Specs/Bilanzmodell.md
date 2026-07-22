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
2. Der Modus ist in der **Einstellungen-Seite** wählbar (Dropdown, übersetzte Labels `VERTEILMODUS_PRODUCER_MESSUNG` / `VERTEILMODUS_BILANZ`), erfordert `einstellungen:write`.
3. Der gewählte Modus steuert die Solarverteilung (`MesswerteService.distribute`) sowohl beim manuellen Lauf (`/solar-calculation`) als auch beim automatischen Lauf nach der MQTT-Aggregation (`ZaehlerAggregationService`).
4. **Org-expliziter Konfig-Zugriff:** Der Modus muss über eine mandanten-explizite Methode gelesen werden (z.B. `EinstellungenService.getVerteilmodus(orgId)`), **nicht** über den request-scoped `getCurrentOrgId()`. Grund: Der MQTT-Auto-Lauf (`ZaehlerAggregationService`) läuft ohne Request-Kontext und übergibt die `org_id` explizit; ein Zugriff über `getCurrentOrgId()` wäre dort `null` und würde seit dem Fail-closed-Verhalten von `HibernateFilterService.enableOrgFilter()` eine `NoOrganizationException` auslösen. Liefert der Zugriff `null` (Mandant ohne Einstellungen/`konfiguration`), gilt `PRODUCER_MESSUNG` (Default, s. FR-1.1).

### FR-2: Verteilung im Bilanzmodell
Pro Zeitintervall mit `ConsumerTotal` = Summe Consumer-`total`, `Bezug` = `total` der Einheit vom Typ `BEZUG` (positiv):
1. **Verteilbarer ZEV-Eigenverbrauch:** `S = max(0, ConsumerTotal − Bezug)`. `S` ist der intern (aus PV und/oder Speicher) gedeckte Teil des Verbrauchs.
2. **Verteilung:** `S` wird mit dem gewählten Algorithmus (EQUAL_SHARE/PROPORTIONAL, je Consumer am eigenen `total` gekappt) auf die Consumer verteilt — dieselben Algorithmen wie heute, nur mit `S` statt der Producer-Produktion als zu verteilende Menge.
   - **Producer-unabhängige Iteration:** Im Bilanzmodus wird ein Intervall über **Consumer + BEZUG** verarbeitet, **nicht** producer-gesteuert. Der heutige Loop (`MesswerteService.distribute`) überspringt Intervalle ohne PRODUCER (`producers.isEmpty()` → `continue`); im BILANZ-Zweig darf dieser Skip **nicht** greifen, da `S` producer-unabhängig ist (bilanz-/batterie-only-ZEV ohne Producer-Messung ist ein gültiger Fall). Ein Intervall mit Consumern und Bezug, aber ohne Producer-Messwert, verteilt `S` regulär.
3. **Consumer-`zev`:** je Consumer = zugeteilte Menge; `zev_calculated` = derselbe Wert. MQTT-Sentinel-Regel bleibt (`zev == 0` → berechneter Wert; gemessene CSV-Werte bleiben).
4. **Producer-`zev` (nur Statistik):** = `|ProduktionTotal| − |Rücklieferung(Bilanz)|`, proportional zur Produktion auf die Producer verteilt (im ZEV verbrauchte Produktion lt. Bilanz). Beeinflusst die Verrechnung nicht. **Fehlt die `RUECKLIEFERUNG`-Einheit**, wird Producer-`zev` auf `0` gesetzt (Statistik unvollständig) — der Lauf **läuft weiter**, da dieser Wert nicht abrechnungsrelevant ist. Wie heute wird Producer-`zev` **nur bei `quelle == MQTT`** überschrieben; CSV-Producer behalten ihren gemessenen `zev`-Wert (bestehender Guard in `aktualisiereProducerZev` bleibt bestehen). Der Guard basiert bewusst auf `quelle` und **nicht** auf einem Feature Flag: Ob `zev` gemessen (CSV) oder berechnet (MQTT-Sentinel `zev == 0`) ist, ist eine Datenprovenienz-Entscheidung **pro Messwert** — ein mandantenweiter Flag könnte diese Unterscheidung nicht ausdrücken und würde gemessene CSV-Werte überschreiben (Datenverlust). Der mandantenweite Schalter ist bereits `verteilmodus`.
5. **Nur `BEZUG` ist abrechnungskritisch:** Fehlt die **`BEZUG`-Einheit** oder fehlen deren Messwerte in einem Intervall, kann `S` (und damit die Consumer-Abrechnung) für dieses Intervall nicht bestimmt werden → **harter Abbruch** des Laufs, es werden **keine** teilweisen `zev`-Werte geschrieben (Rollback über `@Transactional`). Der Abbruch identifiziert das betroffene Intervall (Tag, Zeit) und verhält sich je nach Auslöser:
   - **Manueller Lauf** (`/solar-calculation`): Abbruch mit `IllegalStateException`, deren Message den Key `BILANZMODELL_KEINE_BILANZDATEN` sowie das betroffene Intervall (Tag, Zeit) trägt → HTTP 400 (der Endpoint fängt `Exception` und liefert `message`; `IllegalStateException` ist zusätzlich im `GlobalExceptionHandler` auf 400 gemappt).
   - **Automatischer Lauf** (`ZaehlerAggregationService`, MQTT): kein HTTP-Status — der Lauf des Mandanten bricht ab und loggt **ERROR** mit Intervall-Angabe (Tag, Zeit); übrige Mandanten laufen weiter.
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
* Neue Übersetzungs-Keys via Flyway-Migration (`ON CONFLICT (key) DO NOTHING`): `VERTEILMODUS`, `VERTEILMODUS_PRODUCER_MESSUNG`, `VERTEILMODUS_BILANZ`, `BILANZMODELL_KEINE_BILANZDATEN`, `STATISTIK_MODUS_BILANZ_HINWEIS`, `STATISTIK_RUECKLIEFERUNG_GEMESSEN` (DE/EN).
* Multi-Tenancy unverändert: die Einstellung liegt je `org_id` in `einstellungen`; `orgId` stammt aus dem Kontext, nicht aus dem Request.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

### Modus-Auswahl
* [ ] `RechnungKonfigurationDTO` enthält `verteilmodus` (Enum `Verteilmodus`); **altes JSON ohne das Feld** deserialisiert fehlerfrei (`null`) und wird im Code auf `PRODUCER_MESSUNG` gemappt (Bestandsmandanten unverändert).
* [ ] Mandant **ohne Einstellungen** (`konfiguration == null`): `getVerteilmodus(orgId)` liefert `PRODUCER_MESSUNG` (Default), keine Exception.
* [ ] In der Einstellungen-Seite ist der Modus wählbar und wird persistiert (Roundtrip); erfordert `einstellungen:write`.

### Verteilung Bilanzmodell
* [ ] Bei `verteilmodus = BILANZ` verteilt `MesswerteService.distribute` pro Intervall `S = max(0, ConsumerTotal − Bezug)` auf die Consumer (EQUAL_SHARE/PROPORTIONAL).
* [ ] Beispiel `ConsumerTotal=10, Bezug=4` → `S=4` wird verteilt; Consumer-`zev`-Summe = 4, Netz-Anteil-Summe = 6 = Bezug.
* [ ] `Bezug > ConsumerTotal` (z.B. Batterie lädt aus Netz) → `S=0`, kein ZEV-Anteil in diesem Intervall.
* [ ] Intervall mit Consumern und Bezug, aber **ohne Producer-Messwert** → `S` wird trotzdem verteilt (kein producer-gesteuerter Skip im BILANZ-Zweig).
* [ ] Fehlt die `BEZUG`-Einheit oder deren Messwerte in einem Intervall → **manueller** Lauf bricht mit `IllegalStateException` (Message = Key `BILANZMODELL_KEINE_BILANZDATEN` + Intervall Tag/Zeit) → HTTP 400 ab; keine `zev`-Werte werden geschrieben (Rollback).
* [ ] Gleicher Fehlerfall im **MQTT-Auto-Lauf** → Lauf des Mandanten bricht ab, ERROR-Log mit Intervall-Angabe; übrige Mandanten laufen weiter; keine `zev`-Werte geschrieben.
* [ ] Fehlt (nur) die `RUECKLIEFERUNG`-Einheit → Producer-`zev` = 0, der Lauf bricht **nicht** ab (Consumer-Abrechnung unberührt).
* [ ] Producer-`zev` wird im Bilanzmodus nur bei `quelle == MQTT` gesetzt; CSV-Producer behalten den gemessenen `zev`.
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
| Modus `BILANZ`, aber keine `BEZUG`-Einheit / keine Bilanzdaten | Harter Abbruch, nichts wird geschrieben. Manuell: HTTP 400 mit `BILANZMODELL_KEINE_BILANZDATEN` + Intervall (Tag/Zeit). Auto: ERROR-Log mit Intervall, Mandant übersprungen |
| `Bezug > ConsumerTotal` (Netzladung Batterie / Messdifferenz) | `S = 0` in diesem Intervall (kein negativer Eigenverbrauch) |
| Keine Consumer im Intervall | nichts zu verteilen (`S` irrelevant), keine `zev`-Werte |
| Bilanzdaten-Lücke einzelner Intervalle | **Harter Abbruch** (keine partielle Verteilung). Manuell: Fehlermeldung mit Intervall-Angabe (Tag, Zeit) → HTTP 400. Auto: Verteilung bricht ab und loggt **ERROR** mit Intervall-Angabe |
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

## 8. Offene Fragen
Vorab geklärt:
* [x] **Aktivierung:** pro Mandant wählbare Einstellung (`verteilmodus`), Default `PRODUCER_MESSUNG`.
* [x] **Verteilbasis:** `S = ConsumerTotal − Bezug` (Consumer-seitig), auf 0 begrenzt.
* [x] **Verhältnis zu Batteriespeicher:** Koexistenz; Bilanzmodell wird zuerst umgesetzt.
* [x] **Producer:** unverändert (nur Grundgebühr).

Geklärt (Review):
* [x] Bei **Bilanzdaten-Lücken**: **harter Abbruch** (keine partielle Verteilung). Manuell → Fehlermeldung mit Intervall (Tag, Zeit)/HTTP 400; Auto → ERROR-Log mit Intervall. Siehe FR-2.5 / §5.
* [x] **Modus-Wechsel mit bereits erstellten Rechnungen:** nur **Hinweis** (keine Sperre, keine automatische Neuberechnung). Siehe §5 / §7.
* [x] **`RUECKLIEFERUNG`-Wert:** Im Bilanzmodus wird die **tatsächlich gemessene** Rücklieferung in der Statistik angezeigt (statt nur berechnet). Siehe FR-4.4.
