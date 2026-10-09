# Umsetzungsplan: Messwerte-Zeitkonvention

## Zusammenfassung

`zev.messwerte.zeit` trägt künftig für jede Quelle den **Beginn** des 15-Minuten-Intervalls
(`Specs/Messwerte-Zeitkonvention.md`). Die MQTT-Live-Erfassung stempelt dafür den Beginn statt des
Endes, und `SteuerungService` und `ProduktionsprognoseService` verlieren ihre Verschiebungen um eine
Viertelstunde. Die Bestandsdaten von Hene stellt ein einmaliges SQL-Skript um, das der User beim
Ausrollen von Hand ausführt; es gibt **keine** Flyway-Migration.

---

## Betroffene Komponenten

| Datei | Änderung |
|---|---|
| `backend-service/src/main/java/ch/nacht/service/ZaehlerAggregationService.java` | Stempel `start` statt `ende` (Z. 203), `orgBis` = spätester Intervallbeginn (Z. 119) |
| `backend-service/src/main/java/ch/nacht/service/SteuerungService.java` | `rechneNach` (Z. 468–491), `messungFuer` (Z. 650–662), `speicherFuer` (Z. 713–715), `reichereSpeicherAn` (Z. 749–756); Klassenkommentar „ZEITBEZUEGE" (Z. 59–76) |
| `backend-service/src/main/java/ch/nacht/service/ProduktionsprognoseService.java` | Historienfenster (Z. 172–173), Lastprofil (Z. 240), Speicherfluss-Fenster (Z. 287–288), Faktor-Zuordnung (Z. 290–297), `speicherflussJeIntervall` (ab Z. 329) |
| `backend-service/src/main/java/ch/nacht/repository/MesswerteRepository.java` | Javadoc `sumLadungEntladungPerZeitBetween` (Z. 63: „Intervall**ende**") |
| `backend-service/src/main/java/ch/nacht/entity/Messwerte.java` | Javadoc der Klasse bzw. des Felds `zeit`: Konvention FR-1 |
| `scripts/messwerte-zeit-intervallbeginn.sql` | **neu** — Umstellungsskript (FR-5), Kopfkommentar mit Ablauf NFR-3 |
| `backend-service/src/test/java/ch/nacht/service/ZaehlerAggregationServiceTest.java` | Erwartungen auf Stempel `start` und Verteilfenster |
| `backend-service/src/test/java/ch/nacht/service/SteuerungServiceTest.java` | **neu** |
| `backend-service/src/test/java/ch/nacht/service/ProduktionsprognoseServiceTest.java` | Zeitversatz-Tests → Tests der fehlenden Verschiebung |
| `backend-service/src/test/java/ch/nacht/repository/MesswerteZeitUmstellungSkriptIT.java` | **neu** — Integrationstest des Skripts |
| `Specs/MQTT-Integration.md` | FR-6 (Stempel = Beginn), FR-6.7 (Verteilfenster) |
| `Specs/Einspeisesteuerung.md` | FR-3 (Zeitbezüge) |
| `Specs/Ladeplanung.md` | FR-3 (Zeitversatz; Hinweise um Z. 246 und Z. 296) |
| `Specs/Zaehlertausch-Erkennung.md` | Begriffe prüfen |

**Nicht betroffen:** Frontend, Controller, DTOs, Flyway-Migrationen, `StatistikService`,
`StatistikPdfService`, `RechnungService`, `MesswerteService`, `SteuerRegelService` (keine
Zeitverschiebung), `LadeplanService` (bezieht Lastprofil und Prognose über
`ProduktionsprognoseService`).

---

## Phasen-Tabelle

| Status | Phase | Beschreibung |
|--------|-------|--------------|
|  [ ]   | 1. Live-Erfassung | `ZaehlerAggregationService.verarbeiteIntervall`: `upsertMesswert(einheit, start, total)`. In der Schleife `orgBis.merge(org, intervallStart, …)` statt `intervallEnde`. Delta-Bildung, `markVerarbeitet(einheitId, intervallEnde, …)` und Systemmeldungen bleiben unverändert (sie arbeiten mit Intervallgrenzen, nicht mit dem Stempel). |
|  [ ]   | 2. Einspeisesteuerung | `SteuerungService`: <br>• `rechneNach`: Fenster `von.atStartOfDay()` / `bis.plusDays(1).atStartOfDay()`, `zeit = (LocalDateTime) zeile[0]` ohne `minusMinutes`; Variablen `vonEnde`/`bisEnde`/`endeOrtszeit` umbenennen. <br>• `messungFuer`: Fenster `[zeitVon, zeitVon+15)`; Warnmeldung nennt `zeitVon` – `zeitVon+15`. <br>• `speicherFuer`: Fenster `[zeitVon, zeitVon+15)`. <br>• `reichereSpeicherAn`: Fenster `[tagesbeginn, tagesende)`, Schlüssel `zeile[0]` unverschoben. <br>• **Unverändert:** `socAmIntervallende`, `lowerEntry(zeit + 15)`, `findByZeitVon(zeitVon − 15)` (Vorintervall des Entscheids), Preiszugriffe. <br>• Klassenkommentar „ZEITBEZUEGE": `messwerte.zeit` = Ortszeit, Intervall-**BEGINN**; Hinweis, dass die frühere Ende-Konvention mit `Specs/Messwerte-Zeitkonvention.md` entfiel. |
|  [ ]   | 3. Produktionsprognose | `ProduktionsprognoseService`: <br>• Historie `sumBilanzKomponentenPerZeitBetween(von, bis)` ohne `plusMinutes`. <br>• `lastprofil`: `beginn = (LocalDateTime) zeile[0]`. <br>• `umrechnungsfaktor`: `speicherflussJeIntervall(von, bis)`; in der Schleife eine Variable `zeit = (LocalDateTime) zeile[0]` für **beide** Nachschlagewege (`gtiJeZeit.get(zeit)`, `speicherJeZeit.getOrDefault(zeit, …)` — heute `beginn` bzw. `ende`); Kommentar „Intervall-ENDE … deshalb die Verschiebung" ersetzen. <br>• `speicherflussJeIntervall`: Code unverändert (schlüsselt schon nach dem Stempel), nur Javadoc „Intervall**ende**" → „Intervall**beginn**". <br>• Konstante `INTERVALL_MINUTEN` entfernen, falls danach unbenutzt. |
|  [ ]   | 4. Javadoc | `Messwerte.java`: Javadoc an `zeit` — „Beginn des 15-Minuten-Intervalls, Ortszeit Europe/Zurich ohne Zone; gilt für jede Quelle (Specs/Messwerte-Zeitkonvention.md)". `MesswerteRepository` Z. 63: „Intervall**beginn**". Danach `grep -rn "Intervall-ENDE\|Intervallende\|Intervall<b>ende" backend-service/src/main` — Treffer zu `messwerte` bereinigen, Treffer zu Ladezustand (`socAmIntervallende`) bleiben. |
|  [ ]   | 5. Umstellungsskript | `scripts/messwerte-zeit-intervallbeginn.sql`: der `DO`-Block aus FR-5 **wörtlich**. Kopfkommentar: Zweck, „einmalig, nur Hene, nur solange die alte Version geschrieben hat", Ablauf NFR-3 (Schritte 0–4 mit Befehlen), Verweis auf die Spec. Keine `psql`-Metabefehle (`\…`) und kein `BEGIN`/`COMMIT`, damit der Integrationstest die Datei unverändert per JDBC ausführen kann. **Nur Syntax prüfen** — nicht gegen lokale DB oder Hene ausführen. |
|  [ ]   | 6. Specs nachführen | `MQTT-Integration.md` FR-6: Stempel = Intervallbeginn (Verweis auf die neue Spec); FR-6.7: „`[frühester Intervall-Start … spätester Intervall-Start]`". `Einspeisesteuerung.md` FR-3 und `Ladeplanung.md` FR-3: Ende-Konvention und Zeitversatz-Hinweise durch „gleiche Konvention seit Messwerte-Zeitkonvention" ersetzen; historische Befunde (z. B. Auswertungen mit damaligem Stand) als solche kennzeichnen statt löschen. `Zaehlertausch-Erkennung.md`: Begriffe prüfen. |
|  [ ]   | 7. Kompilieren | `mvn -pl backend-service compile -q` (JAVA_HOME setzen). Bestehende Tests laufen lassen: `ZaehlerAggregationServiceTest`, `ProduktionsprognoseServiceTest`, `LadeplanServiceTest`, `SteuerRegelServiceTest` — die erwartbar roten Zeitversatz-Tests notieren, sie werden in den Test-Commands angepasst. |

> **Phasen 1–3 gehören in denselben Build** (NFR-3): Einzeln ausgerollt rechnete die Steuerung auf
> Daten der jeweils anderen Konvention. Lokal ist die Reihenfolge der Phasen frei.

### Tests (separat, über `/3_backend-tests`)

| Test | Inhalt |
|---|---|
| `ZaehlerAggregationServiceTest` | Intervall 10:00–10:15 → `upsertMesswert` mit `zeit = 10:00`; Menge unverändert; erneute Verarbeitung aktualisiert dieselbe Zeile; Verteilaufruf `calculateSolarDistributionForOrg(org, frühesterBeginn, spätesterBeginn, …)`. |
| `SteuerungServiceTest` (neu, Mockito) | `werteIntervallAus(org, 10:00)` fragt `sumBilanzKomponentenPerZeitBetween(10:00, 10:15)` und `sumLadungEntladungPerZeitBetween(SPEICHER, 10:00, 10:15)` ab. `getEntscheideSimuliert`: Messwert mit Stempel 10:00 landet im Entscheid 10:00, Speichermengen ebenso. `simuliere` über zwei Tage: Fenster `[von 00:00, bis+1 00:00)`. Ladezustand weiterhin „letzter Wert vor 10:15". |
| `ProduktionsprognoseServiceTest` | Faktor: Messwert 10:00 ↔ Einstrahlung 10:00; Historienfenster ohne Versatz; Lastprofil-Zeitpunkt = Stempel; Speicherfluss-Schlüssel = Stempel. |
| `MesswerteZeitUmstellungSkriptIT` (neu) | Siehe unten. |

**Integrationstest des Skripts — Aufbau:**

* `extends AbstractIntegrationTest` (Testcontainers, alle Flyway-Migrationen), Zugriff über
  `JdbcTemplate`.
* **Nicht transaktional** (`@Transactional(propagation = NOT_SUPPORTED)` bzw. kein `@DataJpaTest`-
  Rollback um den Skriptlauf): Der `RAISE EXCEPTION`-Fall bräche sonst die Testtransaktion ab, und
  „Datenbank unverändert" liesse sich nicht mehr prüfen. Testdaten in `@BeforeEach`/`@AfterEach`
  selbst anlegen und löschen, Spaltenkommentar in `@AfterEach` zurücksetzen
  (`COMMENT ON COLUMN zev.messwerte.zeit IS NULL`) — der Container ist für alle ITs geteilt.
* Skript lesen über `Path.of("..", "scripts", "messwerte-zeit-intervallbeginn.sql")`
  (Arbeitsverzeichnis von Failsafe = Modulverzeichnis) und **als Ganzes** mit
  `jdbcTemplate.execute(sql)` ausführen — nicht mit `ScriptUtils`, das an `;` trennt.
* Fälle: Verschiebung aller MQTT-Zeilen um 15 min mit unveränderten Werten; Anzahl und Summe je
  Einheit gleich; CSV-Zeilen unverändert; Kollision CSV genau 15 min vor MQTT → Exception mit
  Anzahl und Beispiel, Daten **und** Kommentar unverändert; zweiter Lauf → „Bereits umgestellt",
  nichts verändert; ohne MQTT-Zeilen → kein Fehler, Kommentar gesetzt.

---

## Validierungen

Keine neuen Eingaben, deshalb keine Validierungen in Frontend oder Backend. Die einzigen Prüfungen
sitzen im Umstellungsskript:

| Prüfung | Wirkung |
|---|---|
| Spaltenkommentar beginnt mit „Beginn" | Abbruch „Bereits umgestellt" — verhindert eine zweite Verschiebung |
| MQTT-Zeile fiele auf eine Nicht-MQTT-Zeile derselben Einheit | Abbruch mit Anzahl und Beispiel — verhindert Doppelzeilen |

Beide vor dem `UPDATE`, alles in einem `DO`-Block (atomar).

---

## Offene Punkte / Annahmen

* **Kein Schutz gegen einen Lauf nach dem Start der neuen Version** — Entscheid des Users
  (09.10.2026); der Schutz ist die Reihenfolge in NFR-3.
* **Ausführung des Skripts ausschliesslich durch den User.** Weder lokal noch auf Hene führt ein
  Agent es aus; geprüft wird die Syntax und über den Integrationstest das Verhalten.
* **Lokale Entwicklungsdatenbank:** 8'964 MQTT-Zeilen bleiben ohne Umstellung eine Viertelstunde
  versetzt. Will der User sie umstellen, dann **vor** dem ersten Start der neuen Version (FR-5).
* **Abnahme auf Hene** (AK „Gleichstand der Auswertungen") erst nach dem Ausrollen; vorher lassen
  sich nur die Unit- und Integrationstests prüfen. Die Vergleichswerte („vor der Umstellung") kommen
  aus dem Backup von Schritt 2.
* **`steuerentscheid`-Bezug zum Vorintervall** (`findByZeitVon(zeitVon − 15)`) ist eine Verschiebung
  auf `steuerentscheid`, nicht auf `messwerte`, und bleibt.
* **Zeilennummern** in „Betroffene Komponenten" sind Stand 09.10.2026 und dienen der Orientierung.
