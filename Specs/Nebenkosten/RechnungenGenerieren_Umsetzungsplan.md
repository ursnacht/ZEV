# Rechnungen aus der Nebenkostenabrechnung — Umsetzungsplan

## Zusammenfassung

Aus einer abgeschlossenen Nebenkostenabrechnung entsteht je Mieter eine Rechnung als PDF mit
QR-Zahlteil; ein positiver Saldo wird zusätzlich als Debitor gebucht. Ausgelöst wird das als
Zeilenaktion im Kebab-Menü der NK-Abrechnung, über einen eigenen Endpunkt — der ZEV-Endpunkt
`POST /api/rechnungen/generate` bleibt unberührt. Damit NK- und ZEV-Forderungen desselben Mieters
nebeneinander bestehen können, wandert die Herkunft (`ZEV`/`NK`) in den Unique-Key der Debitoren.

Grundlage: [`RechnungenGenerieren.md`](./RechnungenGenerieren.md).

---

## Betroffene Komponenten

### Datenbank (Flyway)
| Datei | Inhalt |
|---|---|
| `V126__Add_Debitor_Herkunft.sql` | Spalte `herkunft` (`NOT NULL DEFAULT 'ZEV'`), CHECK auf `ZEV`/`NK`, `uq_debitor_mieter_von_org` durch `(mieter_id, datum_von, herkunft, org_id)` ersetzen, Spaltenkommentar, Übersetzungen DE + EN |

### Backend
| Datei | Änderung |
|---|---|
| `entity/Debitorherkunft.java` | neu — `ZEV`, `NK` (persistiert, CHECK-Constraint) |
| `entity/Debitor.java` | Feld `herkunft`, `@Enumerated(EnumType.STRING)`, `nullable = false` |
| `dto/DebitorDTO.java` | Feld `herkunft` |
| `repository/DebitorRepository.java` | `upsert` um `herkunft` erweitern — **Insert-Spaltenliste und `ON CONFLICT` gemeinsam** (Zeile 47–52) |
| `service/DebitorService.java` | `upsertFromRechnung` bekommt die Herkunft; `validate` setzt `ZEV` bei fehlendem Wert und weist unbekannte ab |
| `service/RechnungStorageService.java` | neues Enum `Rechnungsart` als Namensraum in `store`/`get`/`exists`; Dateiname mitspeichern; `clearAll()` → `clearArt(art)` |
| `controller/RechnungController.java` | **zwei Stellen:** `clearArt(ZEV)` (Zeile 82) und `upsertFromRechnung(..., ZEV)` (Zeile 111). `GenerateRequest`, Antwort, Schlüssel und Dateinamen unverändert |
| `dto/NkRechnungDTO.java`, `NkRechnungZeileDTO.java` | neu — Eingangsdaten des Templates je Mieter |
| `dto/NkRechnungLaufDTO.java` | neu — Antwort des Laufs (FR-6) |
| `service/NkRechnungService.java` | neu — Rechnungsaufbau, Rundung, Debitorenbuchung, Flag- und `abgerechnet`-Prüfung |
| `service/NkRechnungPdfService.java` | neu — füllt `nk-rechnung.jasper`, erzeugt den QR-Zahlteil |
| `controller/NkRechnungController.java` | neu — `POST .../{id}/rechnungen` und `GET .../{id}/rechnungen/{mieterId}/pdf` |
| `resources/reports/nk-rechnung.jrxml` | neu — Zeilen, Akonto, Saldo, Rundung, QR-Zahlteil |
| `test/.../ArchitectureTest.java` | Geltungsbereich von `nebenkostenServicesMustCheckFeatureFlag` von `NkAbrechnung` auf `Nk` (Zeile 445) |

### Frontend
| Datei | Änderung |
|---|---|
| `models/nebenkosten.model.ts` | Ergebnistypen des Laufs (`NkRechnungLauf`, `NkRechnungErgebnis`) |
| `services/nebenkosten.service.ts` | Lauf auslösen **und** PDF holen (Route liegt im NK-Bereich, nicht in `rechnung.service.ts`) |
| `components/nebenkosten-abrechnung/` | zeilenabhängiges Kebab-Menü, Rückfrage, Ergebnis-Panel |
| `components/rechnungen/` | Hinweis auf den NK-Bereich, an `*appFeature` gebunden |
| `models/debitor.model.ts` | `herkunft: DebitorHerkunft` |
| `components/debitorkontrolle-list/` | Spalte **Herkunft** (`zev-status`, sortierbar), Filter Alle/ZEV/NK |
| `components/debitorkontrolle-form/` | Auswahlfeld **Herkunft**, Vorbelegung `ZEV`, bei ausgeschaltetem Flag gesperrt |

---

## Umsetzungsreihenfolge (Phasen)

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. Migration V126 | Spalte, CHECK, Unique-Key-Tausch, Übersetzungen. **Reihenfolge im Skript:** Spalte mit Default anlegen, CHECK, dann alten Constraint löschen und neuen anlegen. Der Tausch kann auf dem Bestand nicht scheitern — alle Zeilen erhalten `ZEV`, und der alte Schlüssel war bereits eindeutig. |
| [x] | 2. Debitor: Enum, Entity, DTO | `Debitorherkunft`, Feld in `Debitor` und `DebitorDTO`, Mapping in `toDTO`/`toEntity`. |
| [x] | 3. Debitor: Repository und Service | `upsert` um `herkunft` in **Insert-Liste und `ON CONFLICT`** erweitern; `upsertFromRechnung(…, herkunft)`; `validate`: fehlender Wert → `ZEV`, unbekannter → `IllegalArgumentException`. Ohne den `ON CONFLICT`-Teil scheitert **jeder** Upsert, auch der ZEV-seitige („no unique or exclusion constraint matching"). |
| [x] | 4. Speicher-Namensraum | `Rechnungsart` als Parameter in `RechnungStorageService`; Dateiname im `StoredPdf` mitführen; `clearArt(art)` statt `clearAll()`. **Nicht** über ein Schlüsselpräfix: `sanitizeKey` macht aus der Einheit „nk 12" mit `mieterId 45` genau `nk_12_45`, ein Präfix wäre also nicht disjunkt. |
| [x] | 5. ZEV-Pfad nachziehen | `RechnungController` Zeile 82 und 111; `RechnungControllerTest` Zeile 111 (`verify(...).clearAll()`) und das `upsertFromRechnung`-`verify` anpassen. Schlüssel und Dateinamen der ZEV-Rechnungen bleiben, wie sie sind. |
| [x] | 6. DTOs und Rechnungsaufbau | `NkRechnungService`: je Mieter aus `NkAbrechnungService.getAbrechnungDetail` → `NkRechnungDTO`; Zeilen, Akonto, Saldo; **Endbetrag auf 5 Rappen** über `RechnungService.roundTo5Rappen` (Zeile 625, `public static` — direkt nutzbar, keine Kopie), Differenz als Rundung. Es wird **nicht** neu gerechnet. Reine Abbildung, ohne PDF und ohne Persistenz — hier gehören die Unit-Tests hin. |
| [x] | 7. Template und PDF-Service | `nk-rechnung.jrxml` (Feldtypen `java.math.BigDecimal`, Zahlen über `PdfNumberFormat`, **kein** `pattern`), `NkRechnungPdfService` nach dem Muster von `RechnungPdfService`. QR-Zahlteil nur bei Saldo > 0 (`printWhenExpression`). |
| [x] | 8. Lauf und Endpunkte | `NkRechnungController` mit `POST .../{id}/rechnungen` und `GET .../{id}/rechnungen/{mieterId}/pdf`, `@PreAuthorize("hasAuthority('nebenkosten:manage') and hasAuthority('rechnungen:manage')")` auf Klassenebene. Im Service: `pruefeFeatureFlag()` **selbst** aufrufen, `abgerechnet` prüfen, je Mieter **erst buchen, dann ablegen**, Antwort nach FR-6. |
| [x] | 9. ArchUnit-Regel erweitern | `haveSimpleNameStartingWith("NkAbrechnung")` → `"Nk"`. Danach **gegenprüfen:** einen `pruefeFeatureFlag()`-Aufruf entfernen, Regel muss fehlschlagen, Aufruf zurücksetzen. Zu `NkRechnungPdfService` entscheiden: prüfen oder ausdrücklich ausnehmen — keine stumme Ausnahme. |
| [x] | 10. Frontend: Modell und Service | Ergebnistypen; `starteRechnungslauf(abrechnungId, sprache)` und `ladeRechnungPdf(abrechnungId, mieterId)` in `nebenkosten.service.ts`, Download über `Blob` wie in `rechnung.service.ts:45-60`. |
| [x] | 11. Liste: Menüeintrag | Zwei **feste** `KebabMenuItem[]` (mit und ohne `RECHNUNGEN_ERSTELLEN`) und eine Methode, die eine davon liefert — keine je Änderungserkennung neu gebaute Liste, sonst `NG0956` (das Menü rendert mit `track item`). Eintrag **vor** `LOESCHEN`; Rückfrage über `confirm`. |
| [x] | 12. Ergebnis-Panel | `zev-panel` unter der Tabelle, Aufbau wie `GENERIERTE_RECHNUNGEN` auf der Seite Rechnungen. Beträge über `swissNumber:2`; Betragsspalte benennt `NK_NACHZAHLUNG`/`NK_GUTHABEN` im Text. `track zeile.mieterId`. Panel verschwindet beim Neuladen und beim Öffnen der Maske. |
| [x] | 13. Hinweis auf `/rechnungen` | Ein `zev-message--info` mit Link auf `/nebenkosten/abrechnung`, gebunden an `*appFeature="'NEBENKOSTENABRECHNUNG'"`. Sonst keine Änderung an der Seite. |
| [x] | 14. Debitorkontrolle | Spalte **Herkunft** als `zev-status`-Badge, sortierbar; Filter Alle/ZEV/NK (Option **NK** nur bei gesetztem Flag, Default **Alle** bei jedem Öffnen); Feld im Formular mit Vorbelegung `ZEV`, gesperrt bei ausgeschaltetem Flag. |
| [x] | 15. Backend-Tests | `NkRechnungServiceTest`, `NkRechnungControllerTest`, `RechnungStorageServiceTest` (**neu — es gibt bisher keinen**), `DebitorServiceTest`, `DebitorRepositoryIT`, `ControllerAuthorizationTest`, `JasperTemplateCompileTest` um Kompilieren **und Füllen** von `nk-rechnung.jrxml`. Details in der Tabelle unten. |
| [x] | 16. Frontend-Unit-Tests | Menüeintrag nur bei `abgerechnet`, stabile Menü-Objekte, Rückfrage und Abbruch, Panel-Lebenszyklus, Zahlenformat, Debitorenliste mit Spalte und Filter. |
| [x] | 17. E2E | Ein Lauf über eine abgeschlossene Abrechnung bis zum Download, danach die Forderung in der Debitorenkontrolle mit Herkunft **NK**. **Aufräumen inklusive der entstandenen Debitoren** — sonst bleiben Forderungen im Mandanten stehen. |

### Validierung nach den Phasen
* Backend: `cd backend-service && mvn compile -q`; nach Phase 9 `mvn test`
* Nach Phase 7: `mvn test "-Dtest=JasperTemplateCompileTest"` (CLAUDE.md) — der Fülltest ist der
  eigentliche Nachweis: Ein Template kompiliert auch mit falschen Feldtypen.
* Vor einem manuellen Durchlauf: **`mvn package`**. Zur Laufzeit wird `/reports/nk-rechnung.jasper`
  geladen (`RechnungPdfService:44`), und die `.jasper` entsteht erst in der Phase
  `prepare-package`. Nach einem reinen `mvn test` fehlt sie, und der Fehler zeigt sich erst beim
  ersten PDF.
* Frontend: `cd frontend-service && npx ng build --configuration=development`
* Migration: `mvn -pl backend-service flyway:info` vor Phase 2 — die Nummer V126 ist heute frei
  (höchste vorhandene: V125).
* Neustart des Stacks nach Phase 1 und 13 **durch den User** (Migration bzw. neue Übersetzungen).

---

## Validierungen

### Backend
| Regel | Ort | Verhalten bei Verstoss |
|---|---|---|
| Permission `nebenkosten:manage` **und** `rechnungen:manage` | `@PreAuthorize` auf `NkRechnungController` | `403` |
| Feature-Flag `NEBENKOSTENABRECHNUNG` | `NkRechnungService`, jede öffentliche Methode | `403` (`FeatureDisabledException`) |
| Abrechnung existiert und gehört dem Mandanten | `findFirstById` unter `orgFilter` | `404`, nicht unterscheidbar von unbekannt |
| Abrechnung ist `abgerechnet` | `NkRechnungService` | `400` mit `NK_FEHLER_NICHT_ABGERECHNET` (`IllegalStateException` → `GlobalExceptionHandler:49`) |
| `herkunft` fehlt im Debitor-Request | `DebitorService.validate` | Rückfall auf `ZEV`, kein Fehler |
| `herkunft` unbekannt | `DebitorService.validate` | `400` — ohne diese Prüfung liefe der Wert in den CHECK-Constraint und käme als `500` zurück |
| `betrag > 0` beim Debitor | CHECK + `@DecimalMin("0.01")` | Saldo ≤ 0 wird **nicht** gebucht (FR-4), kein Fehler |
| `herkunft` in `ZEV`/`NK` | CHECK-Constraint | `500`, falls die Service-Prüfung umgangen wird |
| Eindeutigkeit je Herkunft | `UNIQUE (mieter_id, datum_von, herkunft, org_id)` | Upsert aktualisiert die eigene Forderung, lässt die andere Herkunft unberührt |
| Bezahlte Forderung | `WHERE zev.debitor.zahldatum IS NULL` im Upsert | erneuter Lauf verändert sie nicht |
| PDF vorhanden und nicht abgelaufen | `RechnungStorageService.get(art, key)` | `404` |

### Frontend
| Regel | Verhalten |
|---|---|
| Abrechnung nicht `abgerechnet` | Menüeintrag **fehlt** (kein gesperrter Eintrag — ein ausgegrauter müsste erklären, warum) |
| Rückfrage abgebrochen | kein Request, keine Forderung |
| Lauf läuft | Spinner, kein zweiter Lauf |
| Lauf fehlgeschlagen | `NK_FEHLER_RECHNUNGEN_ERSTELLEN` plus Servertext; ein vorhandenes Panel bleibt unverändert stehen |
| Einzelner Mieter fehlgeschlagen | Zeile trägt `NK_FEHLER_RECHNUNG_MIETER`, kein Download-Knopf |
| Download abgelaufen (`404`) | `NK_RECHNUNG_ABGELAUFEN`, Hinweis auf erneutes Erstellen |
| Feature-Flag aus | Seite unerreichbar; Hinweis auf `/rechnungen` fehlt; in der Debitorkontrolle keine Option **NK**, Formularfeld gesperrt |
| Beträge | `swissNumber:2` — `1'234.55`, keine `number`-Pipe, kein `toLocaleString()` |

---

## Offene Punkte / Annahmen

### Aus der Spec übernommen
Abschnitt 8 der Spec enthält **eine** zurückgestellte Frage: die Nachverfolgung zurückgezahlter
Guthaben (Kreditor oder negativer Debitor). Sie beeinflusst diesen Plan nicht — Guthaben erzeugen
ein PDF, aber keine Forderung (FR-4). Alle übrigen 19 Fragen sind dort entschieden und in die
Phasen eingeflossen.

### Annahme 1: Herkunft-Filter clientseitig
`GET /api/debitoren?von=&bis=` filtert serverseitig nach Zeitraum
(`DebitorController:34-42`). Der Herkunft-Filter arbeitet dagegen auf der **schon geladenen**
Liste — wie das Status-Badge offen/bezahlt, das ebenfalls clientseitig entsteht. Ein zusätzlicher
Query-Parameter kostete je Filterwechsel einen Roundtrip und eine Repository-Variante, ohne dass
die Datenmenge das nötig machte. Wird die Liste später paginiert, kehrt sich das um.

### Annahme 2: Zwei Enums statt eines
`Debitorherkunft` (persistiert, hängt am CHECK-Constraint) und `Rechnungsart` (Namensraum im
flüchtigen PDF-Speicher) tragen dieselben zwei Werte, bleiben aber getrennt: Sonst importierte
`RechnungStorageService` ein Persistenz-Enum der Debitoren, und eine Änderung am Speicher berührte
die Datenbankschicht. Die Alternative — ein gemeinsames Enum — wäre kürzer; sie wird bewusst nicht
gewählt.

### Annahme 3: Dateinamen der NK-Rechnungen
`Nebenkosten_<Bezeichnung>_<Mietername>.pdf`, durch `sanitizeKey` gezogen. Der Name wird im
Speicher mitgeführt (Phase 4), der Schlüssel bleibt `<abrechnungId>_<mieterId>`. Die Dateinamen der
ZEV-Rechnungen bleiben unverändert — sie sind für den Benutzer sichtbar.

### Annahme 4: Prozentsatz im PDF
`PdfNumberFormat.percent(Double)` erwartet einen **Anteil** (0..1) und multipliziert selbst mit 100.
`NkZeileDTO.prozentsatz` ist bereits 0..100. Im Template deshalb `decimals(wert, 1)` mit
angehängtem `%` statt `percent()` — sonst stünde `6000.0 %` auf der Rechnung.

### Annahme 5: Sprache des PDF
Wie bei ZEV ein optionales Feld im Request mit Default `"de"`. Die Maske schickt die aktuell
gewählte Sprache nicht mit; das bleibt, wie es auf der Seite Rechnungen heute ist.

### Annahme 6: Migrationsnummer
V126 ist beim Schreiben dieses Plans frei. Entsteht zwischenzeitlich eine andere Migration, wird
die Nummer verschoben — eine **bereits ausgeführte** Migration wird nie geändert (CLAUDE.md).
