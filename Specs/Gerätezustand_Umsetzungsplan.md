# Gerätezustand — Umsetzungsplan

## Zusammenfassung

Eine Zeitreihe für **Momentanwerte** von Geräten (`zev.geraetezustand`), als erste und einzige
Grösse dieser Stufe der **Ladezustand (SOC)** des Batteriespeichers. Der Wert kommt über den
bestehenden MQTT-Vertrag in einem neuen, generischen Objekt `zustand` und läuft **neben** der
Aggregation her — er wird nie zu einer Energiemenge verrechnet.

Umgesetzt wird `Specs/Gerätezustand.md`. Die Reihenfolge der Phasen ist so gewählt, dass nach
Phase 8 alles **lokal** prüfbar ist; die Modbus-Arbeit (Phase 9) betrifft nur die echte Anlage.

## Betroffene Komponenten

**Neu**

| Datei | Zweck |
|---|---|
| `db/migration/V153__Create_Geraetezustand.sql` | Tabelle, Index, CHECK, Kommentare |
| `db/migration/V154__Add_Geraetezustand_Translations.sql` | zwei Meldungs-Keys (DE/EN) |
| `entity/Zustandsgroesse.java` | Registry: Einheit, Wertebereich, zulässige Einheiten-Typen |
| `entity/Geraetezustand.java` | Entity mit `@Filter(orgFilter)` |
| `repository/GeraetezustandRepository.java` | Upsert-Lookup und die beiden Leseabfragen |

**Geändert**

| Datei | Änderung |
|---|---|
| `dto/ZaehlerMesswertPayloadDTO.java` | Feld `zustand` als `Map<String, Object>` |
| `service/MqttIngestService.java` | Validierung, Schreiben, neue Abhängigkeit auf `SystemmeldungService` |
| `service/SystemmeldungService.java` | zwei Meldungs-Key-Konstanten |
| `pi-gateway/gateway/models.py` | Zustandswerte in `MeterReading` |
| `pi-gateway/gateway/publisher.py` | `zustand` in `_to_payload` — nur wenn Werte vorliegen |
| `pi-gateway/gateway/config.py` | `uint16` in `_SUPPORTED_REGISTER_TYPES`, Konfiguration für Zustandsregister |
| `pi-gateway/gateway/readers/modbus_reader.py` | `uint16`, variable Registeranzahl |
| `pi-gateway/gateway/readers/sim_reader.py` | SOC gekoppelt an die Lade-/Entladephase |
| `pi-gateway/config.example.yaml`, `config.sim.example.yaml` | Beispiele |
| `Specs/MQTT-Integration.md` | FR-3: Payload-Vertrag um `zustand` ergänzen |

**Ausdrücklich nicht betroffen:** `ZaehlerAggregationService`, `MesswerteService`, `StatistikService`,
Abrechnung, Frontend. Es gibt in dieser Stufe **keinen Controller und keine Ansicht** (Spec §7).

## Phasen

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. DB-Migration Tabelle | `V153`: Tabelle mit `org_id` (**BIGINT**, nicht UUID — das Projekt führt sie durchgehend als `BIGINT`), `einheit_id` mit **`ON DELETE CASCADE`**, `zeit` (Ortszeit), `groesse`, `wert`, `empfangen_am`. `UNIQUE (einheit_id, zeit, groesse)`, Index `(einheit_id, groesse, zeit DESC)`, CHECK `groesse NOT IN ('SOC','SOH') OR wert BETWEEN 0 AND 100`, Spaltenkommentare. **Kein** CHECK über `groesse` selbst — er verlangte für jede neue Grösse eine Migration |
| [x] | 2. DB-Migration Übersetzungen | `V154`: `GERAETEZUSTAND_WERT_UNGUELTIG` und `GERAETEZUSTAND_TYP_UNGUELTIG`, DE/EN, `ON CONFLICT (key) DO NOTHING`, deutsche Texte mit Umlauten und `ss` statt `ß` |
| [x] | 3. Registry-Enum | `Zustandsgroesse` mit **genau einem** Eintrag `SOC` (Einheit `%`, 0–100, nur `SPEICHER`) — Muster: `FeatureFlag` mit Eigenschaften je Konstante. Dazu `fromKey(String)`, das Gross-/Kleinschreibung ignoriert und `Optional.empty()` für Unbekanntes liefert |
| [x] | 4. Entity und Repository | `Geraetezustand` nach Vorlage `ZaehlerRohdaten` (`einheitId` als `Long`, nicht `@ManyToOne`). Repository: `findByEinheitIdAndZeitAndGroesse` (Upsert-Lookup), `findFirstByEinheitIdAndGroesseAndZeitLessThanOrderByZeitDesc` (FR-5.1) und `findByEinheitIdAndGroesseAndZeitBetweenOrderByZeitAsc` (FR-5.2) |
| [x] | 5. Payload-DTO | `zustand` als `Map<String, Object>` — **nicht** `Map<String, BigDecimal>`: Der engere Typ lässt Jackson schon beim Parsen scheitern und kostet über den generischen `catch` die ganze Nachricht samt Zählerständen |
| [x] | 6. Ingest | Prüfkette je Eintrag: umwandelbar → bekannte Grösse → Einheiten-Typ → Wertebereich. `null` = nicht gemeldet (keine Zeile, keine Meldung). Bei mehreren zulässigen Einheiten die mit der kleinsten `id`. Verworfener Wert lässt die Zählerstände unberührt und ruft **kein** `MqttMetrics.recordFailed()` |
| [x] | 7. Pi-Gateway Transport | `models.py`: Zustandswerte in `MeterReading`. `publisher.py`: `zustand` in `_to_payload`, **nur wenn Werte vorliegen** — sonst trüge jede Nachricht ein leeres Objekt |
| [x] | 8. Pi-Gateway Simulator | `sim_reader.py`: SOC an die bestehende Lade-/Entladephase gekoppelt (`_laedt`, `_phase_rest`), steigt beim Laden, fällt beim Entladen, bleibt in 0–100. **Ab hier ist der ganze Pfad lokal prüfbar** |
| [x] | 9. Pi-Gateway Modbus | `config.py`: `uint16` in `_SUPPORTED_REGISTER_TYPES` (bricht heute beim Start ab), Konfigurationsformat für Zustandsregister. `modbus_reader.py`: `uint16` und **variable Registeranzahl** — `_REGISTERS_PER_WERT` ist fest `2`, `SOC` belegt eines. Beispiel in `config.example.yaml` |
| [x] | 10. MQTT-Spec nachführen | `Specs/MQTT-Integration.md` FR-3: `zustand` in Payload-Beispiel und Feldtabelle. Ohne das gäbe es zwei Beschreibungen desselben Vertrags |
| [ ] | 11. Tests | Siehe unten — die Ingest-Prüfkette und die Registry sind ohne Datenbank testbar |

## Validierungen

**Pi-Gateway (Start)**
* Unbekannter Registertyp → `ConfigError`, der Dienst startet nicht. Gilt neu auch für `uint16`,
  bis Phase 9 ihn kennt.

**Backend — Ingest, je Eintrag der Map (Reihenfolge ist Fachlichkeit)**

| # | Prüfung | Bei Verstoss |
|---|---|---|
| 1 | Wert in Zahl umwandelbar | verworfen, `GERAETEZUSTAND_WERT_UNGUELTIG` |
| 2 | Wert ist `null` | **keine** Zeile, **keine** Meldung — „nicht gemeldet" ist kein Störfall |
| 3 | Schlüssel ist bekannte Grösse | verworfen, ohne Meldung (wie ein unbekanntes Feld) |
| 4 | Einheiten-Typ lässt die Grösse zu | verworfen, `GERAETEZUSTAND_TYP_UNGUELTIG` |
| 5 | Wert im Bereich der Grösse | verworfen, `GERAETEZUSTAND_WERT_UNGUELTIG` |

In **allen** Fällen: Die Zählerstände derselben Nachricht werden gespeichert, und `MqttMetrics`
bleibt unberührt. Beide Meldungen ohne Auto-Resolve.

**Datenbank (zweite Sicherung)**
* `CHECK` auf dem Wertebereich der Prozent-Grössen — greift auch an der Anwendung vorbei.
* `UNIQUE (einheit_id, zeit, groesse)` — der Upsert stützt sich darauf.
* `FK ... ON DELETE CASCADE`.

**Frontend:** keine — es gibt keine Oberfläche.

## Tests

| Ebene | Inhalt |
|---|---|
| `ZustandsgroesseTest` | `fromKey` mit `soc`, `SOC`, `Soc`, Unbekanntem, `null`; Wertebereich und zulässige Typen je Konstante |
| `MqttIngestServiceTest` | Die fünf Prüfungen oben, je mit der Zusicherung „Zählerstände trotzdem gespeichert"; `{"soc": "abc"}`, `"zustand": 5`, `{"soc": null}`; mehrere zulässige Einheiten → eine Zeile (kleinste `id`); Meldungs-Keys getrennt |
| `GeraetezustandRepositoryIT` | „letzter Wert vor Zeitpunkt" bei mehreren Werten im Intervall; leeres Ergebnis; Zeitspanne aufsteigend mit den Grenzen `[von, bis)`; Mandantentrennung |
| DB-nah | Direktes `INSERT` mit `wert = 101` scheitert am CHECK; `EXPLAIN` der Leseabfrage zeigt Index-Scan |
| `pi-gateway` | `uint16` liest **ein** Register; Rohwert `8750` × `skalierung 0.01` = `87.5`; Simulator-SOC folgt der Phase und verlässt 0–100 nicht; ohne konfigurierte Zustandsregister **kein** `zustand` im Payload |

> **Das Gateway hat bis heute keine Testinfrastruktur.** Die vier Punkte der letzten Zeile sind
> deshalb entweder als erste `pytest`-Dateien anzulegen oder — wie beim `uint32`-Decoder — als
> ausgeführte Prüfskripte zu belegen und im Commit festzuhalten. Ein stillschweigendes Auslassen
> wäre die dritte Variante und die schlechteste.

## Offene Punkte / Annahmen

* **Aus Spec §8, weiterhin offen:** Ob `TEMPERATUR` mehrere Sensoren je Gerät unterscheiden soll
  (der Solinteg liefert vier). Blockiert **nicht** — die Frage stellt sich erst mit der zweiten
  Grösse, und durch den generischen Wire-Contract ist die Variante „eigene Grössen" billig.
* **Annahme:** Die Migrationsnummern **V153/V154** sind beim Umsetzen noch frei. Höchste vergebene
  Nummer heute: `V152`. Vor dem Anlegen erneut prüfen — in dieser Woche sind acht Migrationen
  entstanden.
* **Annahme:** Der Ingest schreibt Zustandswerte im selben `@Transactional`-Block wie die Rohdaten.
  Scheitert das Schreiben eines Zustandswerts unerwartet (nicht durch Validierung, sondern etwa
  durch einen Constraint), rollt die Nachricht komplett zurück. Das ist hinnehmbar, weil die
  Validierung alle bekannten Fälle vorher abfängt.
* **Kein Retention-Konzept.** Bei Minutentakt entstehen rund 525'000 Zeilen pro Jahr und Gerät.
  Falls das Thema aufkommt, betrifft es `geraetezustand` und `zaehler_rohdaten` gemeinsam — beide
  werden heute nie gelöscht.
* **Der Nutzen entsteht erst danach.** Diese Stufe legt die Daten ab; dass die Steuerungsregel den
  Ladestand auswertet, ist eine Änderung an `Specs/Einspeisesteuerung.md` und bewusst nicht Teil
  dieses Plans (Spec §7).


## Umsetzung — Stand 17.09.2026

**Phasen 1 bis 10 erledigt, Phase 11 (Tests) offen** — `/2_umsetzung` erstellt keine Tests.

**Geprüft:** 1325 Backend-Tests grün, alle Gateway-Module importierbar. Die Änderungen am Gateway
wurden mangels Testinfrastruktur als ausgeführte Prüfskripte belegt:

| Prüfung | Ergebnis |
|---|---|
| `uint16` über **ein** Register, `float32`/`uint32` weiter über zwei | `{'float32': 2, 'uint32': 2, 'uint16': 1}` |
| Rohwert `8750` × `skalierung 0.01` | `87.5 %` |
| `uint16` vorzeichenlos | `0x8000` → `32768`, nicht `-32768` |
| Wortfolge bei einem Register bedeutungslos | `big == little` |
| Simulator: SOC folgt der Lade-/Entladephase | 40 Lesevorgänge, **0 Widersprüche**, immer 0–100 |
| Zähler ohne Zustandsregister | Payload **ohne** `zustand`-Feld |
| Konfiguration: `SOC:` wird kleingeschrieben abgelegt | `{'soc': (0x80e8, 'uint16', 0.01)}` |
| Unbekannter Registertyp | `ConfigError` beim Start |

**Zwei Stellen mussten über den Plan hinaus angefasst werden:**

`MeterConfig` und das Parsen der Konfiguration brauchten ein Feld `register_zustand`
(Grössenname → Register) — der Plan nannte nur „Konfigurationsformat für Zustandsregister", ohne
dass klar war, dass Modell *und* Parser betroffen sind. Die Grössennamen werden dort **nicht**
gegen eine Liste geprüft: Welche es gibt, entscheidet das Backend, und der Pi soll eine Grösse
senden dürfen, die ein älteres Backend noch nicht kennt.

`MqttIngestServiceTest` rief den Konstruktor an **zwei** Stellen auf, nicht nur im `setUp` — die
zweite steckte in einem Test zur Deploy-Reihenfolge und fiel erst beim Kompilieren auf.

**Nötig:** Rebuild für V153 und V154. Danach in `config.sim.yaml` beim Speicher-Eintrag nichts
ändern — der Simulator liefert den SOC automatisch, sobald der Messpunkt „speicher" oder „batterie"
enthält.
