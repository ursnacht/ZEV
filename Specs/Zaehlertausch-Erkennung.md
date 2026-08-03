# Zählertausch-Erkennung

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Ein **Zählertausch** (Austausch des physischen Zählers einer Einheit) soll von der MQTT-Aggregation **automatisch und richtungsunabhängig** erkannt werden, damit über die Tausch-Grenze **kein falscher Messwert** entsteht. Grundlage ist eine **Zähler-Seriennummer (`zaehlerId`)** im MQTT-Payload, die je Rohdatensatz gespeichert und beim Aggregieren ausgewertet wird.
* **Warum machen wir das:** Der Aggregations-Job bildet je 15-Minuten-Intervall die **Differenz absoluter Zählerstände** und nullt negative Deltas per Reset-Guard. Ein Zählertausch wird damit **nur erkannt, wenn der neue Zähler NIEDRIGER startet** als der alte endete. Startet der neue Zähler **HÖHER**, ist das Delta **positiv** und wird als **echter Verbrauch/Erzeugung verbucht** – ein potenziell grosser Bogus-Wert im Übergangsintervall, **ohne** Warnung (dokumentierter Blindspot, siehe `docs/Zaehlertausch.md`). Aus den Werten allein ist ein Tausch nicht zuverlässig erkennbar; es braucht ein explizites Signal.
* **Aktueller Stand:**
  - MQTT-Payload enthält `timestamp`, `zaehlerstandBezug`, `zaehlerstandEinspeisung` (`ZaehlerMesswertPayloadDTO`); keine Zähler-Identität.
  - `MqttIngestService` löst Einheiten über `(org_id, messpunkt)` auf und speichert absolute Stände als `ZaehlerRohdaten` (Upsert je `einheit_id` + `zeit`).
  - `ZaehlerAggregationService.verarbeiteIntervall` bildet `delta = Stand(≤ende) − Stand(≤start)` je Register; `nichtNegativ()` nullt negative Deltas („Rücksprung … Delta auf 0 gesetzt"); ohne Referenz (`referenz == null`, erste Messung) entsteht **kein** Messwert (Baseline).

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Seriennummer im Payload entgegennehmen & speichern
1. Der MQTT-Payload wird um ein **optionales** Feld `zaehlerId` (String) erweitert:
   `{ "timestamp": "…", "zaehlerstandBezug": …, "zaehlerstandEinspeisung": …, "zaehlerId": "…" }`.
2. `MqttIngestService` übernimmt `zaehlerId` unverändert und speichert sie am erzeugten/aktualisierten `ZaehlerRohdaten`-Satz (je Einheit; bei geteiltem Bilanzmesspunkt an allen betroffenen Einheiten).
3. Fehlt `zaehlerId` (null/leer) → Verarbeitung wie bisher (Rohdaten werden gespeichert, `zaehler_id` bleibt `NULL`). Kein Verwerfen der Nachricht deswegen.
4. Der Publisher (Pi-Gateway) sendet die Seriennummer des ausgelesenen Zählers je `messpunkt` (Koordination mit `Pi-Gateway-Software.md` / `MQTT-Integration.md`).

### FR-2: Persistierung
* Neue Spalte `zaehler_id VARCHAR(64) NULL` auf `zev.zaehler_rohdaten` (Flyway-Migration, mit `COMMENT ON COLUMN`).
* **Kein** `NOT NULL` (Rückwärtskompatibilität: Bestandsdaten und Payloads ohne Serie).
* Multi-Tenancy unverändert: `zaehler_rohdaten` trägt bereits `org_id` + `@Filter("orgFilter")`; die neue Spalte ändert die Mandanten-Isolation nicht. `zaehlerId` wird **nicht** zur Mandanten-Auflösung verwendet (die bleibt `(org_id, messpunkt)`).
* Feld `zaehlerId` in `ZaehlerRohdaten` (Entity) + `ZaehlerMesswertPayloadDTO`.

### FR-3: Baseline-Reset bei Serien-Wechsel (Aggregation)
1. In `verarbeiteIntervall` wird zusätzlich zur Wert-Differenz die **Seriennummer** von `referenz` (Stand ≤ Intervallstart) und `letzter` (Stand ≤ Intervallende) verglichen.
2. Sind **beide** Serien vorhanden und **unterschiedlich** → **Zählerwechsel**: es wird **kein** Delta über die Grenze gebildet und **kein** Messwert für dieses Übergangsintervall erzeugt (analog zum bestehenden „erste Messung"-Zweig, `referenz == null`). Der neue Zähler bildet ab dann die Baseline; das erste **vollständig** im neuen Zähler liegende Intervall rechnet wieder korrekt.
3. Das Verhalten ist **richtungsunabhängig** (neuer Stand höher **oder** niedriger) – der bisherige Bogus-Wert bei höherem Startstand entfällt.
4. Ein erkannter Wechsel wird als `log.info`/`log.warn` mit Einheit, Intervall und alter/neuer Serie protokolliert (Nachvollziehbarkeit).

### FR-4: Fallback bei fehlender Seriennummer
* Ist bei `referenz` **oder** `letzter` die Serie `NULL` (Bestandsdaten, gemischter Rollout, Payload ohne `zaehlerId`) → **kein** Serien-Vergleich möglich → Verhalten **exakt wie heute** (Delta-Bildung + Reset-Guard). Damit bleibt das System ohne Pi-Update voll funktionsfähig; die neue Erkennung greift automatisch, sobald beide Stände eine Serie tragen.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)
* [ ] Payload mit `zaehlerId` wird akzeptiert; die Serie steht am gespeicherten `ZaehlerRohdaten` (`zaehler_id`).
* [ ] Payload **ohne** `zaehlerId` wird weiterhin akzeptiert; `zaehler_id` ist `NULL`, sonst unverändertes Verhalten.
* [ ] **Serien-Wechsel im Intervall** (Referenz-Serie ≠ End-Serie, beide gesetzt): für das Übergangsintervall wird **kein** Messwert erzeugt; kein negativer und **kein** positiver Bogus-Wert.
* [ ] **Höher startender neuer Zähler** (z.B. alt endet 10 000, neu startet 50 000) mit Serien-Wechsel → **kein** Messwert im Übergangsintervall (vorher: Bogus-Wert 40 000). Regressionstest zum Blindspot.
* [ ] **Niedriger startender neuer Zähler** mit Serien-Wechsel → ebenfalls **kein** Messwert im Übergangsintervall (statt Reset-Guard-Nullung).
* [ ] Erstes vollständig im neuen Zähler liegendes Intervall (gleiche Serie an Start und Ende) → korrektes Delta.
* [ ] **Fallback:** fehlt eine der beiden Serien (`NULL`) → Verhalten identisch zu heute (Delta + Reset-Guard); bestehende Aggregations-Tests bleiben grün.
* [ ] Erkannter Wechsel wird mit Einheit/Intervall/alter+neuer Serie geloggt.
* [ ] Migration fügt `zaehler_id` (nullable) auf `zaehler_rohdaten` hinzu; bestehende Zeilen behalten `NULL`; Mandanten-Isolation (`org_id`/`orgFilter`) unverändert.
* [ ] **Sicherheit/Isolation:** keine neue Route/Rolle; die MQTT-Ingest-Auflösung bleibt `(org_id, messpunkt)`; `zaehlerId` wird **nicht** zur Mandanten-Auflösung verwendet (kein Cross-Tenant-Bezug).
* [ ] **Unbekannte Felder:** ein Payload mit `zaehlerId`, das das Backend (noch) nicht kennt, wird **nicht** verworfen (Parser toleriert unbekannte Felder).
* [ ] **Längenbegrenzung:** `zaehlerId` mit mehr als 64 Zeichen wird auf 64 gekürzt gespeichert; die Nachricht wird **nicht** verworfen.

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Der Serien-Vergleich nutzt die ohnehin geladenen `referenz`/`letzter`-Rohdatensätze – **kein** zusätzlicher DB-Zugriff je Intervall. Komplexität unverändert.

### NFR-2: Sicherheit
* Keine neue API/Route, keine UI → **keine** neue Rolle/Autorisierung. Der MQTT-Ingest ist unverändert topic-basiert (`org_id` aus Topic, kein JWT; siehe `MQTT-Integration.md`). `zaehlerId` ist nicht mandanten-auflösend und wird nur gespeichert/verglichen – kein Cross-Tenant-Bezug.
* Eingabe: `zaehlerId` wird auf die Spaltenlänge (**64 Zeichen**) **gekürzt**, falls länger; ein ungültiges/überlanges Feld führt **nie** zum Verwerfen der ganzen Nachricht (nur das Feld wird gekürzt bzw. bei Unbrauchbarkeit als `NULL` behandelt → Fallback).

### NFR-3: Kompatibilität
* **Additiv & rückwärtskompatibel:** neue **nullable** Spalte, optionales Payload-Feld, Fallback = heutiges Verhalten. Kein Bruch für Bestands-Rohdaten, CSV-Upload oder Nicht-MQTT-Mandanten. Der Payload-/Topic-Vertrag bleibt kompatibel (nur additives Feld) – Koordination mit `Pi-Gateway-Software.md`.
* **Unbekannte-Felder-Toleranz / Deploy-Reihenfolge:** Der Payload-Parser toleriert unbekannte JSON-Felder (Jackson `FAIL_ON_UNKNOWN_PROPERTIES = false`, Spring-Boot-Default). Sendet der Pi `zaehlerId`, **bevor** das Backend-DTO das Feld kennt, wird die Nachricht dennoch verarbeitet (Feld ignoriert). Die Roll-out-Reihenfolge Backend/Pi ist damit unkritisch; die Erkennung greift automatisch, sobald beide Stände eine Serie tragen.

## 5. Edge Cases & Fehlerbehandlung
| Szenario | Verhalten |
|----------|-----------|
| `zaehlerId` fehlt/leer im Payload | Rohdaten gespeichert, `zaehler_id = NULL`; Aggregation via Fallback (heutiges Verhalten) |
| **Beide** Serien `NULL` (Bestandsdaten / durchgängig ohne Serie) | Fallback: Delta-Bildung + Reset-Guard exakt wie heute |
| Referenz-Serie `NULL`, End-Serie gesetzt (gemischter Rollout, erster Payload mit Serie nach Umstieg) | Fallback (kein sicherer Vergleich möglich); erst ab zwei gesetzten, gleichen/ungleichen Serien greift die Erkennung |
| Beide Serien gesetzt & gleich | normaler Delta-Pfad inkl. Reset-Guard (unverändert) |
| Beide Serien gesetzt & unterschiedlich | Übergangsintervall ohne Messwert (Baseline-Reset), Log |
| Serie wechselt „zurück" (A→B→A, z.B. Rücktausch) | jeder Wechsel wird als Übergangsintervall behandelt (kein Messwert) |
| Offline-Lücke über den Tausch (mehrere leere Intervalle) | leere Intervalle wie bisher ohne Messwert; das erste Intervall mit neuer Serie ist das Übergangsintervall |
| Überlange `zaehlerId` (> 64 Zeichen) | Feld auf 64 Zeichen **gekürzt** gespeichert; Nachricht wird **nicht** verworfen |
| Unbekanntes Zusatzfeld im Payload (z.B. `zaehlerId` vor Backend-Update) | Parser ignoriert unbekannte Felder → Nachricht wird normal verarbeitet |
| CSV-Mandanten / Nicht-MQTT | nicht betroffen (keine Rohdaten-Aggregation) |

## 6. Abhängigkeiten & betroffene Funktionalität
* **Voraussetzungen:** MQTT-Integration (`Specs/MQTT-Integration.md`), Pi-Gateway (`Specs/Pi-Gateway-Software.md`), bestehende Rohdaten-Aggregation.
* **Betroffener Code (Backend):**
  - `dto/ZaehlerMesswertPayloadDTO.java` — Feld `zaehlerId` (optional).
  - `entity/ZaehlerRohdaten.java` — Feld/Spalte `zaehler_id` (nullable).
  - `service/MqttIngestService.java` — `zaehlerId` beim Upsert übernehmen.
  - `service/ZaehlerAggregationService.java` — Serien-Vergleich in `verarbeiteIntervall`, Baseline-Reset bei Wechsel, Log.
  - `resources/db/migration/V[XX]__Add_ZaehlerId_To_Zaehler_Rohdaten.sql` — neue Spalte + Kommentar (nächste freie Version zum Umsetzungszeitpunkt prüfen; aktuell höchste `V91`).
* **Betroffene Specs/Doku:** `MQTT-Integration.md` (Payload-Vertrag FR-3 + offene Frage „Zählertausch-Erkennung"), `Pi-Gateway-Software.md` (Publisher sendet `zaehlerId`), `docs/Zaehlertausch.md` (Blindspot → gelöst, Verweis aktualisieren).
* **Pi-Gateway (extern):** Publisher muss die Seriennummer je Zähler mitsenden (Config/Auslesung); ohne Update greift der Fallback.
* **Datenmigration:** nur additive Spalte; keine Transformation bestehender Daten.

## 7. Abgrenzung / Out of Scope
* **Keine** automatische Rekonstruktion/Schätzung des Übergangsintervalls (es bleibt ohne Messwert; manuelle Korrektur weiterhin gemäss `docs/Zaehlertausch.md`, falls exakte Abrechnung nötig).
* **Keine** Historisierung/Verwaltung von Zähler-Seriennummern je Einheit (nur Vergleich aufeinanderfolgender Rohdaten). Eine „Zähler-Stammdaten"-Verwaltung ist ein separates Thema.
* **Kein** app-interner Wechsel-Marker (Alternative A) – bewusst zugunsten der automatischen Serien-Erkennung verworfen.
* **Keine** Plausibilitäts-/Schwellwert-Erkennung großer positiver Deltas (Alternative C) – höchstens später als zusätzlicher Backstop.
* **Keine** Änderung der Verteil-/Abrechnungslogik.

## 8. Offene Fragen
* [ ] **Quelle der Seriennummer im Pi-Gateway:** OBIS-Objekt (z.B. 0.0.0 / 96.1.0) aus dem Zähler auslesen oder aus der Pi-Config je `messpunkt` setzen? (Annahme: aus dem Gerät ausgelesen, wo verfügbar; sonst Config.)
* [ ] **Feldname/Format im Payload:** `zaehlerId` als String bestätigt? Max. Länge 64 ausreichend? (Annahme: ja.)
* [ ] **Geteilter Bilanzmesspunkt (BEZUG+RUECKLIEFERUNG am selben physischen Zähler):** eine gemeinsame Serie für beide Einheiten (Annahme: ja – dieselbe `zaehlerId` an beiden Rohdatensätzen).
* [ ] **Retention-Wechselwirkung:** Der letzte Stand vor einem Wechsel darf nicht vom Cleanup entfernt werden (analog zur bestehenden Retention-Offene-Frage in `MQTT-Integration.md`).
* [ ] **Verhalten bei „eine Serie NULL":** Fallback ist gesetzt (Annahme). Soll ein einmaliger Log-Hinweis kommen, wenn plötzlich von `NULL` auf gesetzte Serie gewechselt wird (Rollout-Marker)? (Annahme: nein, nur Debug.)
