# Zählertausch-Erkennung

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Ein **Zählertausch** (Austausch des physischen Zählers einer Einheit) soll von der MQTT-Aggregation **richtungsunabhängig** erkannt werden, damit über die Tausch-Grenze **kein falscher Messwert** entsteht. Grundlage ist eine **Zähler-Seriennummer (`seriennummer`)** im MQTT-Payload, die je Rohdatensatz gespeichert und beim Aggregieren ausgewertet wird. Die Serie stammt aus der **Pi-Config** je `messpunkt` (Entscheid, Abschnitt 8) – die Auswertung im Backend läuft automatisch, das **Signal** kommt jedoch aus dem Config-Update beim Tausch (FR-1.4).
* **Warum machen wir das:** Der Aggregations-Job bildet je 15-Minuten-Intervall die **Differenz absoluter Zählerstände** und nullt negative Deltas per Reset-Guard. Ein Zählertausch wird damit **nur erkannt, wenn der neue Zähler NIEDRIGER startet** als der alte endete. Startet der neue Zähler **HÖHER**, ist das Delta **positiv** und wird als **echter Verbrauch/Erzeugung verbucht** – ein potenziell grosser Bogus-Wert im Übergangsintervall, **ohne** Warnung (dokumentierter Blindspot, siehe `docs/Zaehlertausch.md`). Aus den Werten allein ist ein Tausch nicht zuverlässig erkennbar; es braucht ein explizites Signal.
* **Aktueller Stand:**
  - MQTT-Payload enthält `timestamp`, `zaehlerstandBezug`, `zaehlerstandEinspeisung` (`ZaehlerMesswertPayloadDTO`); keine Zähler-Identität.
  - `MqttIngestService` löst Einheiten über `(org_id, messpunkt)` auf und speichert absolute Stände als `ZaehlerRohdaten` (Upsert je `einheit_id` + `zeit`).
  - `ZaehlerAggregationService.verarbeiteIntervall` bildet `delta = Stand(≤ende) − Stand(≤start)` je Register; `nichtNegativ()` nullt negative Deltas („Rücksprung … Delta auf 0 gesetzt"); ohne Referenz (`referenz == null`, erste Messung) entsteht **kein** Messwert (Baseline).

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Seriennummer im Payload entgegennehmen & speichern
1. Der MQTT-Payload wird um ein **optionales** Feld `seriennummer` (String) erweitert:
   `{ "timestamp": "…", "zaehlerstandBezug": …, "zaehlerstandEinspeisung": …, "seriennummer": "…" }`.
2. `MqttIngestService` **normalisiert** die `seriennummer` **vor dem Speichern** und schreibt sie an den erzeugten/aktualisierten `ZaehlerRohdaten`-Satz (je Einheit; bei geteiltem Bilanzmesspunkt an allen betroffenen Einheiten). Normalisierung, in dieser Reihenfolge:
   1. **Trimmen** (führende/folgende Whitespaces entfernen),
   2. leer nach Trim → **`NULL`**,
   3. länger als **64 Zeichen** → auf 64 Zeichen **kürzen** (die Spaltenlänge, FR-2).
   Der Inhalt wird darüber hinaus **nicht interpretiert** (keine Formatprüfung, kein Case-Mapping). Die Normalisierung ist zwingend: ein ungekürzter Überlängen-Wert würde beim Insert an `VARCHAR(64)` scheitern und – da `MqttIngestService.handle` transaktional ist – die **ganze Nachricht** verwerfen (bei geteiltem Bilanzmesspunkt die Rohdaten **beider** Einheiten).
3. Fehlt die `seriennummer` (nicht vorhanden, `null` oder leer/nur Whitespace) → Verarbeitung wie bisher (Rohdaten werden gespeichert, Spalte bleibt `NULL`). Kein Verwerfen der Nachricht deswegen.
4. Der Publisher (Pi-Gateway) sendet die Seriennummer **aus seiner Konfiguration** je `messpunkt` – ein Config-Wert, **nicht** per OBIS/Modbus aus dem Gerät gelesen (Entscheid, s. Abschnitt 8). Beim Einbau wird die Serie einmalig am Gerät abgelesen (z.B. per `mbpoll`, s. `Pi-Gateway-Software.md`) und in die Pi-Config eingetragen. **Config-Key: `seriennummer`** je Eintrag unter `zaehler:` – durchgängig derselbe Name wie Payload-Feld, Entity-Feld und DB-Spalte:
   ```yaml
   zaehler:
     - messpunkt: "ID742-Wohnung-1"
       seriennummer: "WAGO-8791234"     # einmalig am Gerät abgelesen; beim Tausch aktualisieren!
       protokoll: modbus-tcp
       # ...
   ```
   * **Betriebliche Konsequenz (wichtig):** Der Config-Eintrag ist damit das **explizite Tausch-Signal**. Wird beim Zählertausch die Pi-Config **nicht** aktualisiert, bleibt die gesendete Serie unverändert, der Wechsel wird **nicht** erkannt und der Blindspot besteht für diesen Tausch weiter (s. Abschnitt 5 und `docs/Zaehlertausch.md`). Das Config-Update gehört daher verbindlich in die Tausch-Checkliste.

### FR-2: Persistierung
* Neue Spalte `seriennummer VARCHAR(64) NULL` auf `zev.zaehler_rohdaten` (Flyway-Migration, mit `COMMENT ON COLUMN`).
* **Kein** `NOT NULL` (Rückwärtskompatibilität: Bestandsdaten und Payloads ohne Serie).
* Multi-Tenancy unverändert: `zaehler_rohdaten` trägt bereits `org_id` + `@Filter("orgFilter")`; die neue Spalte ändert die Mandanten-Isolation nicht. `seriennummer` wird **nicht** zur Mandanten-Auflösung verwendet (die bleibt `(org_id, messpunkt)`).
* Feld `seriennummer` in `ZaehlerRohdaten` (Entity) + `ZaehlerMesswertPayloadDTO`.

### FR-3: Baseline-Reset bei Serien-Wechsel (Aggregation)
1. In `verarbeiteIntervall` wird zusätzlich zur Wert-Differenz die **Seriennummer** von `referenz` (Stand ≤ Intervallstart) und `letzter` (Stand ≤ Intervallende) verglichen. Der Vergleich ist **exakt und case-sensitive** (`equals`, keine Normalisierung zur Laufzeit – die Werte sind beim Ingest bereits getrimmt, FR-1.2). `"ABC123"` und `"abc123"` gelten damit als **unterschiedliche** Zähler.
2. Sind **beide** Serien vorhanden und **unterschiedlich** → **Zählerwechsel**: es wird **kein** Delta über die Grenze gebildet und **kein** Messwert für dieses Übergangsintervall erzeugt (analog zum bestehenden „erste Messung"-Zweig, `referenz == null`). Der neue Zähler bildet ab dann die Baseline; das erste **vollständig** im neuen Zähler liegende Intervall rechnet wieder korrekt.
   * **Ein bereits vorhandener `messwerte`-Satz für dieses Intervall wird NICHT verändert** (nicht überschrieben, nicht genullt, nicht gelöscht) – konsistent zum bestehenden „erste Messung"-Zweig, der ebenfalls früh zurückkehrt. Ein vor Einführung dieses Features geschriebener Falschwert bleibt damit stehen und muss bei Bedarf manuell korrigiert werden (s. `docs/Zaehlertausch.md`).
3. Das Verhalten ist **richtungsunabhängig** (neuer Stand höher **oder** niedriger) – der bisherige Bogus-Wert bei höherem Startstand entfällt.
4. Ein erkannter Wechsel wird als `log.info`/`log.warn` mit Einheit, Intervall und alter/neuer Serie protokolliert (Nachvollziehbarkeit).

### FR-4: Fallback bei fehlender Seriennummer
1. Ist bei `referenz` **oder** `letzter` die Serie `NULL` (Bestandsdaten, gemischter Rollout, Payload ohne `seriennummer`) → **kein** Serien-Vergleich möglich → Verhalten **exakt wie heute** (Delta-Bildung + Reset-Guard). Damit bleibt das System ohne Pi-Update voll funktionsfähig; die neue Erkennung greift automatisch, sobald beide Stände eine Serie tragen.
2. **Rollout-Marker (Log-Hinweis):** **Je Übergang** – d.h. in jedem Intervall mit `referenz.seriennummer == NULL` **und** `letzter.seriennummer != NULL` – wird ein Log-Hinweis geschrieben (Einheit, Intervall, neue Serie), damit im Log nachvollziehbar ist, **ab wann** die Tausch-Erkennung für diese Einheit aktiv ist. Kein Dedup-State nötig: der Übergang tritt beim Rollout natürlicherweise genau einmal je Einheit auf (bei Nachverarbeitung spät eintreffender Daten kann er erneut greifen – das ist akzeptiert). Das Intervall selbst läuft über den Fallback (kein Baseline-Reset), da ein `NULL`-Stand keinen Wechsel belegt.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)
* [ ] Payload mit `seriennummer` wird akzeptiert; die Serie steht am gespeicherten `ZaehlerRohdaten` (`seriennummer`).
* [ ] Payload **ohne** `seriennummer` wird weiterhin akzeptiert; `seriennummer` ist `NULL`, sonst unverändertes Verhalten.
* [ ] **Serien-Wechsel im Intervall** (Referenz-Serie ≠ End-Serie, beide gesetzt): für das Übergangsintervall wird **kein** Messwert erzeugt; kein negativer und **kein** positiver Bogus-Wert.
* [ ] **Höher startender neuer Zähler** (z.B. alt endet 10 000, neu startet 50 000) mit Serien-Wechsel → **kein** Messwert im Übergangsintervall (vorher: Bogus-Wert 40 000). Regressionstest zum Blindspot.
* [ ] **Niedriger startender neuer Zähler** mit Serien-Wechsel → ebenfalls **kein** Messwert im Übergangsintervall (statt Reset-Guard-Nullung).
* [ ] Erstes vollständig im neuen Zähler liegendes Intervall (gleiche Serie an Start und Ende) → korrektes Delta.
* [ ] **Offline-Lücke über den Tausch:** letzter Stand alter Zähler (Serie A) bei `T0`, erster Stand neuer Zähler (Serie B) erst mehrere Intervalle später bei `T5` → **genau ein** Übergangsintervall ohne Messwert (jenes, das `T5` abschliesst); die dazwischenliegenden datenlosen Intervalle erzeugen wie bisher keinen Messwert; das nächste vollständige Intervall (B→B) rechnet korrekt.
* [ ] **Rücktausch A→B→A:** jeder Serien-Wechsel wird eigenständig behandelt → zwei Übergangsintervalle ohne Messwert (bei A→B und bei B→A); die Intervalle dazwischen mit jeweils gleicher Serie rechnen korrekt.
* [ ] **Fallback:** fehlt eine der beiden Serien (`NULL`) → Verhalten identisch zu heute (Delta + Reset-Guard); bestehende Aggregations-Tests bleiben grün.
* [ ] Erkannter Wechsel wird mit Einheit/Intervall/alter+neuer Serie geloggt.
* [ ] **Rollout-Marker:** Ein Intervall mit `referenz.seriennummer == NULL` **und** `letzter.seriennummer != NULL` erzeugt einen Log-Hinweis (Einheit, Intervall, neue Serie); das Intervall selbst wird über den Fallback verarbeitet (kein Baseline-Reset).
* [ ] **Gleiche Serie trotz physischem Tausch** (Pi-Config nicht aktualisiert) → Verhalten unverändert wie heute (Delta + Reset-Guard); die Erkennung greift bewusst **nicht** – dokumentiertes Restrisiko, kein Fehlerfall der Implementierung.
* [ ] Migration fügt `seriennummer` (nullable) auf `zaehler_rohdaten` hinzu; bestehende Zeilen behalten `NULL`; Mandanten-Isolation (`org_id`/`orgFilter`) unverändert.
* [ ] **Sicherheit/Isolation:** keine neue Route/Rolle; die MQTT-Ingest-Auflösung bleibt `(org_id, messpunkt)`; `seriennummer` wird **nicht** zur Mandanten-Auflösung verwendet (kein Cross-Tenant-Bezug).
* [ ] **Unbekannte Felder:** ein Payload mit `seriennummer`, das das Backend (noch) nicht kennt, wird **nicht** verworfen (Parser toleriert unbekannte Felder).
* [ ] **Längenbegrenzung:** eine `seriennummer` mit mehr als 64 Zeichen wird auf 64 Zeichen gekürzt gespeichert; die Nachricht wird **nicht** verworfen (auch nicht die Rohdaten einer zweiten Einheit am geteilten Bilanzmesspunkt).
* [ ] **Normalisierung:** `"  ABC123  "` wird als `"ABC123"` gespeichert; ein Wert aus nur Whitespace wird als `NULL` gespeichert (→ Fallback).
* [ ] **Case-Sensitivität:** `"ABC123"` (Referenz) vs. `"abc123"` (Ende) gilt als Serien-**Wechsel** → Übergangsintervall ohne Messwert.
* [ ] **Bestehender Messwert:** existiert für das Übergangsintervall bereits ein `messwerte`-Satz, bleibt er beim Baseline-Reset **unverändert** (nicht überschrieben/genullt/gelöscht).

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Der Serien-Vergleich nutzt die ohnehin geladenen `referenz`/`letzter`-Rohdatensätze – **kein** zusätzlicher DB-Zugriff je Intervall. Komplexität unverändert.

### NFR-2: Sicherheit
* Keine neue API/Route, keine UI → **keine** neue Rolle/Autorisierung. Der MQTT-Ingest ist unverändert topic-basiert (`org_id` aus Topic, kein JWT; siehe `MQTT-Integration.md`). `seriennummer` ist nicht mandanten-auflösend und wird nur gespeichert/verglichen – kein Cross-Tenant-Bezug.
* Eingabe: `seriennummer` wird auf die Spaltenlänge (**64 Zeichen**) **gekürzt**, falls länger; ein ungültiges/überlanges Feld führt **nie** zum Verwerfen der ganzen Nachricht (nur das Feld wird gekürzt bzw. bei Unbrauchbarkeit als `NULL` behandelt → Fallback).

### NFR-3: Kompatibilität
* **Additiv & rückwärtskompatibel:** neue **nullable** Spalte, optionales Payload-Feld, Fallback = heutiges Verhalten. Kein Bruch für Bestands-Rohdaten, CSV-Upload oder Nicht-MQTT-Mandanten. Der Payload-/Topic-Vertrag bleibt kompatibel (nur additives Feld) – Koordination mit `Pi-Gateway-Software.md`.
* **i18n:** **Keine UI** und keine neuen API-Felder nach außen → **keine neuen Übersetzungs-Keys**, keine Flyway-Translation-Migration. Log-Meldungen (FR-3.4, FR-4.2) sind technische Diagnose und werden **nicht** übersetzt.
* **Unbekannte-Felder-Toleranz / Deploy-Reihenfolge:** Der Payload-Parser toleriert unbekannte JSON-Felder (Jackson `FAIL_ON_UNKNOWN_PROPERTIES = false`, Spring-Boot-Default). Sendet der Pi `seriennummer`, **bevor** das Backend-DTO das Feld kennt, wird die Nachricht dennoch verarbeitet (Feld ignoriert). Die Roll-out-Reihenfolge Backend/Pi ist damit unkritisch; die Erkennung greift automatisch, sobald beide Stände eine Serie tragen.

## 5. Edge Cases & Fehlerbehandlung
| Szenario | Verhalten |
|----------|-----------|
| `seriennummer` fehlt/leer im Payload | Rohdaten gespeichert, `seriennummer = NULL`; Aggregation via Fallback (heutiges Verhalten) |
| **Beide** Serien `NULL` (Bestandsdaten / durchgängig ohne Serie) | Fallback: Delta-Bildung + Reset-Guard exakt wie heute |
| Referenz-Serie `NULL`, End-Serie gesetzt (gemischter Rollout, erster Payload mit Serie nach Umstieg) | Fallback (kein sicherer Vergleich möglich) **+ einmaliger Log-Hinweis je Einheit** (Rollout-Marker, FR-4.2); erst ab zwei gesetzten Serien greift die Erkennung |
| **Zähler physisch getauscht, aber Pi-Config nicht aktualisiert** | Serie bleibt unverändert → Wechsel wird **nicht** erkannt → Fallback (Reset-Guard): bei niedriger startendem Zähler genulltes Intervall, bei **höher** startendem weiterhin Bogus-Wert. **Restrisiko der Config-Lösung** – Config-Update ist Teil der Tausch-Checkliste (`docs/Zaehlertausch.md`) |
| Pi-Config-Serie korrigiert/getippt (Tippfehler), Gerät unverändert | wird als Serien-Wechsel gewertet → ein Übergangsintervall ohne Messwert (harmlos, im Log nachvollziehbar) |
| Beide Serien gesetzt & gleich | normaler Delta-Pfad inkl. Reset-Guard (unverändert) |
| Beide Serien gesetzt & unterschiedlich | Übergangsintervall ohne Messwert (Baseline-Reset), Log |
| Serie wechselt „zurück" (A→B→A, z.B. Rücktausch) | jeder Wechsel wird als Übergangsintervall behandelt (kein Messwert) |
| Offline-Lücke über den Tausch (mehrere leere Intervalle) | leere Intervalle wie bisher ohne Messwert; das erste Intervall mit neuer Serie ist das Übergangsintervall |
| Überlange `seriennummer` (> 64 Zeichen) | beim Ingest auf 64 Zeichen **gekürzt** gespeichert; Nachricht wird **nicht** verworfen |
| `seriennummer` mit Whitespace (`"  ABC123 "`) | getrimmt gespeichert (`"ABC123"`) → kein Scheinwechsel durch Leerzeichen |
| `seriennummer` nur Whitespace | wie fehlend behandelt → `NULL` → Fallback |
| Nur Gross-/Kleinschreibung geändert (`ABC123` → `abc123`) | gilt als Serien-**Wechsel** (Vergleich case-sensitive) → ein Übergangsintervall ohne Messwert |
| Übergangsintervall hat schon einen `messwerte`-Satz (Altdaten / Nachverarbeitung) | Satz bleibt **unverändert**; Baseline-Reset erzeugt/überschreibt nichts (manuelle Korrektur bei Bedarf) |
| Unbekanntes Zusatzfeld im Payload (z.B. `seriennummer` vor Backend-Update) | Parser ignoriert unbekannte Felder → Nachricht wird normal verarbeitet |
| CSV-Mandanten / Nicht-MQTT | nicht betroffen (keine Rohdaten-Aggregation) |

## 6. Abhängigkeiten & betroffene Funktionalität
* **Voraussetzungen:** MQTT-Integration (`Specs/MQTT-Integration.md`), Pi-Gateway (`Specs/Pi-Gateway-Software.md`), bestehende Rohdaten-Aggregation.
* **Betroffener Code (Backend):**
  - `dto/ZaehlerMesswertPayloadDTO.java` — Feld `seriennummer` (optional).
  - `entity/ZaehlerRohdaten.java` — Feld/Spalte `seriennummer` (nullable).
  - `service/MqttIngestService.java` — `seriennummer` beim Upsert übernehmen.
  - `service/ZaehlerAggregationService.java` — Serien-Vergleich in `verarbeiteIntervall`, Baseline-Reset bei Wechsel, Log.
  - `resources/db/migration/V[XX]__Add_Seriennummer_To_Zaehler_Rohdaten.sql` — neue Spalte + Kommentar (nächste freie Version zum Umsetzungszeitpunkt prüfen; aktuell höchste `V91`).
* **Betroffene Specs/Doku:** `MQTT-Integration.md` (Payload-Vertrag FR-3 + offene Frage „Zählertausch-Erkennung"), `Pi-Gateway-Software.md` (Publisher sendet `seriennummer`), `docs/Zaehlertausch.md` (Blindspot → gelöst, Verweis aktualisieren).
* **Pi-Gateway (extern):** Neuer **Config-Key `seriennummer` je Eintrag unter `zaehler:`** (s. FR-1.4), den der Publisher in jeden Payload als `seriennummer` übernimmt – keine Geräte-Auslesung im Betrieb. Die Beispiel-Config in `Pi-Gateway-Software.md` ist entsprechend zu ergänzen. Ohne Pi-Update greift der Fallback. **Beim Zählertausch muss die Config aktualisiert werden**, sonst wird der Wechsel nicht erkannt (FR-1.4).
* **Betriebsdoku:** `docs/Zaehlertausch.md` – Checkliste um den Punkt „Pi-Config: neue Seriennummer eintragen" ergänzen (ersetzt langfristig die manuelle Prüfung des Übergangsintervalls).
* **Datenmigration:** nur additive Spalte; keine Transformation bestehender Daten.

## 7. Abgrenzung / Out of Scope
* **Keine** automatische Rekonstruktion/Schätzung des Übergangsintervalls (es bleibt ohne Messwert; manuelle Korrektur weiterhin gemäss `docs/Zaehlertausch.md`, falls exakte Abrechnung nötig).
* **Keine** Historisierung/Verwaltung von Zähler-Seriennummern je Einheit (nur Vergleich aufeinanderfolgender Rohdaten). Eine „Zähler-Stammdaten"-Verwaltung ist ein separates Thema.
* **Keine** Auslesung der Seriennummer aus dem Gerät im Betrieb (OBIS/Modbus) – bewusst per Pi-Config (Entscheid, Abschnitt 8). Eine spätere Geräte-Auslesung wäre eine rein additive Erweiterung im Pi-Gateway; das Backend bliebe unverändert.
* **Kein** app-interner Wechsel-Marker (Alternative A) – die Pi-Config übernimmt diese Rolle.
* **Keine** Plausibilitäts-/Schwellwert-Erkennung großer positiver Deltas (Alternative C) – höchstens später als zusätzlicher Backstop.
* **Keine** Änderung der Verteil-/Abrechnungslogik.

## 8. Offene Fragen
* [x] **Quelle der Seriennummer im Pi-Gateway:** **Entschieden: aus der Pi-Config je `messpunkt`** – *nicht* per OBIS/Modbus aus dem Gerät ausgelesen. Die Serie wird beim Einbau **einmalig** am Gerät abgelesen (z.B. per `mbpoll`, s. `Pi-Gateway-Software.md`) und in die Pi-Config eingetragen. **Konsequenz:** Die Erkennung ist damit an eine **Bedienaktion** gekoppelt (Config-Update beim Tausch) – siehe FR-1.4 und das Restrisiko in Abschnitt 5.
* [x] **Feldname/Format im Payload:** **Entschieden:** `seriennummer` als String, max. 64 Zeichen.
* [x] **Geteilter Bilanzmesspunkt (BEZUG+RUECKLIEFERUNG am selben physischen Zähler):** **Entschieden (Annahme bestätigt):** eine gemeinsame Serie – dieselbe `seriennummer` wird an beiden Rohdatensätzen gespeichert (ein Config-Eintrag je `messpunkt`).
* [ ] **Retention-Wechselwirkung:** Der letzte Stand vor einem Wechsel darf nicht vom Cleanup entfernt werden (analog zur bestehenden Retention-Offene-Frage in `MQTT-Integration.md`). **Bleibt offen** (wird mit der Retention-Strategie zusammen entschieden).
* [x] **Verhalten bei „eine Serie NULL":** **Entschieden:** Fallback bleibt (heutiges Verhalten), **zusätzlich ein Log-Hinweis** beim Übergang `NULL` → gesetzte Serie (Rollout-Marker, FR-4.2) – damit im Log nachvollziehbar ist, ab wann die Erkennung für eine Einheit aktiv ist.
