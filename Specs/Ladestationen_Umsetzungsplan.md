# Ladestationen – Umsetzungsplan

## Zusammenfassung

Ladestationen werden als eigene Einheiten vom neuen Typ `LADESTATION` geführt, deren `messpunkt`
die **RFID** trägt. Die Tarifposition hängt künftig an der **Einheit** statt am Mieter, und ein
Mieter kann **mehreren** Einheiten zugeordnet sein. Damit ist ein Nutzer ohne Wohnung abrechenbar,
ein Nutzer mit zwei Ladestationen abbildbar, und der Mieterwechsel erzeugt keine Doppelverrechnung –
weil mit der RFID auch die Einheit wechselt.

Grundlage: [`Specs/Ladestationen.md`](./Ladestationen.md), aufbauend auf
[`Specs/Ladestromtarif.md`](./Ladestromtarif.md).

> **Zwei Dinge, die es NICHT braucht** – beide vorab am laufenden System verifiziert:
> * **Kein CHECK-Constraint an `zev.einheit.typ`.** Die Tabelle trägt nur PK und FK auf
>   `organisation`; `typ` ist `VARCHAR(20)` und fasst `LADESTATION`. Der neue Enum-Wert braucht
>   also keine DDL – anders als bei `tarif.tariftyp`, wo genau das zur Laufzeit gescheitert war.
> * **`StatistikService.TYP_ANZEIGE_REIHENFOLGE` bewusst NICHT erweitern.** Die Aufzählung hält
>   Ladestations-Einheiten aus „Summen pro Einheit" heraus – gewollt, damit dort keine leeren
>   Reihen erscheinen.
> * **Keine Datenmigration der Tarifpositionen.** Die Tabelle ist leer (0 Zeilen), der Anker lässt
>   sich ohne Übernahme umstellen. `mieter.einheit_id` ist dagegen gefüllt (11 Zeilen).

## Betroffene Komponenten

**Backend (neu):**
- `entity/MieterEinheit.java` – Zuordnung Mieter ↔ Einheit (`org_id`, `mieter_id`, `einheit_id`)
- `repository/MieterEinheitRepository.java`

**Backend (geändert):**
- `entity/EinheitTyp.java` – neuer Wert `LADESTATION`
- `entity/Einheit.java` / `service/EinheitService.java` / `repository/EinheitRepository.java` – RFID-Eindeutigkeit je Mandant für `LADESTATION`
- `entity/Mieter.java` – `einheitId` entfällt zugunsten der Zuordnung
- `repository/MieterRepository.java` – vier Finder über `einheitId` (`findAllByOrderByEinheitIdAsc…`, `findByEinheitIdOrderByMietbeginnDesc`, `existsOverlappingMieter…`, `existsOtherMieterWithoutMietende`, `findByEinheitIdAndQuartal`) auf die Zuordnung umstellen
- `service/MieterService.java` – Überschneidungs- und Mietende-Prüfung je zugeordneter Einheit
- `service/DebitorService.java` – Auflösung Mieter → Einheit
- `entity/Tarifposition.java`, `repository/TarifpositionRepository.java`, `service/TarifpositionService.java`, `dto/TarifpositionDTO.java`, `controller/TarifpositionController.java` – Anker `einheit_id`
- `service/RechnungService.java` – Positionen über die Einheiten des Mieters

**Frontend (geändert):**
- `models/einheit.model.ts` (Typ), `models/mieter.model.ts` (Einheiten-Liste), `models/tarifposition.model.ts` (`einheitId`)
- `components/einheit-form/` – Typ Ladestation, Hinweis „Messpunkt = RFID"
- `components/einheit-list/` – Kebab-Eintrag „Tarifpositionen" (Sprung mit `?einheitId`)
- `components/mieter-form/` – Mehrfachauswahl der Einheiten
- `components/mieter-list/` – Spalte Einheiten, Kebab-Eintrag „Tarifpositionen" entfällt
- `components/tarifposition-list/` – Auswahl Einheit statt Mieter
- `components/tarifposition-form/` – `einheitId`, Vorbelegung der Quell-Referenz aus `messpunkt`
- `services/tarifposition.service.ts` – `getByEinheit`

**Datenbank:** (nächste freie Nummer vor dem Anlegen via `zev-db` MCP prüfen – `V103` ist die höchste angewendete)
- `V104__Add_Ladestation_Messpunkt_Unique.sql` – partieller Unique-Index auf die RFID
- `V105__Create_Mieter_Einheit.sql` – Zuordnungstabelle, Datenübernahme aus `mieter.einheit_id`, Spalte entfernen
- `V106__Tarifposition_An_Einheit.sql` – `mieter_id` → `einheit_id`, Constraints und Index neu
- `V107__Add_Ladestationen_Translations.sql` – i18n (DE/EN)
- `V108__Add_Rechnung_Ohne_Einheit_Translation.sql` – Hinweis für übersprungene Einheiten

## Umsetzungsreihenfolge (Phasen)

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. Backend-Enum | `EinheitTyp` um `LADESTATION` erweitert. **Alle 37 Verzweigungen durchgegangen:** 36 davon sind positive Auswahlen (`== PRODUCER/CONSUMER/BEZUG`, `findByTyp`, `!= CONSUMER`) und greifen für `LADESTATION` von selbst nicht — kein Eingriff nötig. Eine Stelle brauchte einen Guard: `MqttIngestService` löst Einheiten über `(org_id, messpunkt)` auf; da der `messpunkt` einer Ladestation eine **RFID** und keine Zählerkennung ist, werden Ladestationen dort jetzt herausgefiltert. Sonst entstünden bei zufälliger Kollision Messwerte an einer Einheit, die nie an der Verteilung teilnimmt. Keine DDL nötig (siehe oben). |
| [x] | 2. RFID-Eindeutigkeit | `V104__Add_Ladestation_Messpunkt_Unique.sql`: partieller Unique-Index `(org_id, messpunkt) WHERE typ = 'LADESTATION' AND messpunkt IS NOT NULL` (angewendet und verifiziert). `EinheitRepository.existsLadestationWithMesspunkt(...)`, Prüfung `pruefeLadestationMesspunkt(...)` in `EinheitService.create/update` — wirft `IllegalStateException("EINHEIT_MESSPUNKT_EXISTIERT")` wie die bestehende Bilanz-Typ-Prüfung, der `GlobalExceptionHandler` macht daraus `400` mit Übersetzungs-Key. Leerer Messpunkt wird auf `null` normalisiert. **Kein** globaler Unique-Index – `BEZUG`/`RUECKLIEFERUNG` teilen sich bewusst einen Messpunkt. |
| [x] | 3. DB-Migration Zuordnung | `V105` (nicht `V104` – die Nummer ging an Phase 2): Tabelle `zev.mieter_einheit` (`org_id`, `mieter_id` FK **ON DELETE CASCADE**, `einheit_id` FK **ON DELETE RESTRICT** – eine Einheit mit Zuordnungen darf nicht verschwinden, sonst stünde ein Mieter ohne Einheit da; PK (`mieter_id`,`einheit_id`), Index (`org_id`,`mieter_id`)); Übernahme `INSERT … SELECT org_id, id, einheit_id FROM zev.mieter`; danach `mieter.einheit_id` entfernen. |
| [x] | 4. Backend Mieter | Neue Entity `MieterEinheit` (eigene Entity statt `@ManyToMany`, weil die Tabelle wie alle anderen eine `org_id` mit `@Filter` trägt – die setzt JPA bei einer reinen Join-Tabelle nicht) + `MieterEinheitRepository`. `Mieter.einheitId` → `@Transient List<Long> einheitIds`, das nur `MieterService` füllt und auswertet. Alle fünf Finder in `MieterRepository` verbinden jetzt explizit mit `MieterEinheit`. Validierungen laufen **je zugeordneter Einheit** in einer Schleife; mindestens eine Zuordnung ist Pflicht. Löschschutz: abgewiesen, solange an einer zugeordneten Einheit Positionen hängen. `DebitorService` nennt jetzt **alle** Einheiten eines Mieters. **Zusammen mit Phase 6 umgesetzt**, weil der Löschschutz `countByEinheitId` voraussetzt. |
| [x] | 5. DB-Migration Position | `V106`: `tarifposition.mieter_id` durch `einheit_id` ersetzen (FK auf `einheit`, ON DELETE CASCADE), Unique neu `(org_id, einheit_id, tarif_id, jahr, quartal)`, Index `(org_id, einheit_id, jahr, quartal)`. Tabelle ist leer – kein `UPDATE`-Schritt. |
| [x] | 6. Backend Tarifposition | Entity, Repository (`findByEinheitId`, `findByEinheitIdAndQuartalOverlapping`, `existsByEinheitAndQuartalAndTariftyp`), Service (Auflösung der Einheit inkl. Typprüfung `LADESTATION`), DTO (`einheitId`, `einheitName`, `messpunkt`), Controller (`?einheitId=`). Eindeutigkeit je Tariftyp bleibt, bezogen auf die Einheit. |
| [x] | 7. RechnungService | `berechneTarifpositionsZeilen` sammelt die Positionen **aller** dem Mieter zugeordneten Einheiten statt über `mieter.getId()`. Ohne Mieter keine Positionen. **Neuer dritter Zweig in `berechneRechnungen`:** heute verzweigt die Methode nur auf `CONSUMER` und `PRODUCER`; eine `LADESTATION`-Einheit erzeugt neu genau dann eine eigene Rechnung, wenn ihrem Mieter keine `CONSUMER`-Einheit zugeordnet ist (Nutzer ohne Wohnung). Rechnungen ohne Ladestations-Zuordnung müssen bit-identisch bleiben. Umgesetzt als `berechneLadestationRechnung(...)`: enthält **ausschliesslich** die Tarifpositionen – eine Ladestation hat keine Messwerte und trägt keine Grundgebühr (die gilt je Wohnungszähler). |
| [x] | 8. Frontend Modelle + Service | `einheit.model.ts` (Typ `LADESTATION`), `mieter.model.ts` (`einheitIds: number[]`), `tarifposition.model.ts` (`einheitId`), `tarifposition.service.ts` (`getByEinheit`). Zusätzlich `EinheitTypPipe`: Der `default`-Zweig gab **jeden** unbekannten Typ als „Produzent" aus — eine Ladestation wäre dort falsch beschriftet worden. Eigener Fall ergänzt, der Zweig kommentiert. |
| [x] | 9. Frontend Einheit | `einheit-form`: Typ im Dropdown, Hinweistext „Messpunkt = RFID" für diesen Typ. `einheit-list`: Kebab-Eintrag „Tarifpositionen" mit `queryParams: { einheitId }`. `einheit-selector`: Ladestations-Einheiten werden **nicht** ausgeblendet — auch nicht in Messwerte-Chart und Messwerte-Upload. Drei Filter auf `EinheitTyp.CONSUMER` mussten erweitert werden, sonst waren Ladestationen unsichtbar: `mieter-list` (Auswahl für die Zuordnung — mietbar sind Wohnungen **und** Ladestationen), `debitorkontrolle-list` (Nachschlagetabelle für die Einheitennamen) und `einheit-selector` mit `[onlyConsumers]` (dessen Bedeutung jetzt „rechnungsfähig" ist, nicht „Konsument"). |
| [x] | 10. Frontend Mieter | `mieter-form`: Mehrfachauswahl der Einheiten (mindestens eine Pflicht) statt Einzel-Dropdown. `mieter-list`: Spalte mit den zugeordneten Einheiten; der Kebab-Eintrag „Tarifpositionen" entfällt dort. Mehrfachauswahl als Checkbox-Liste (`zev-checkbox-group` aus dem Design System) statt Multi-Select — sie zeigt alle Einheiten auf einen Blick. Nachgezogen: `debitorkontrolle-form` und `-list` lösten den Einheitennamen über `mieter.einheitId` auf und nennen jetzt alle Einheiten. |
| [x] | 11. Frontend Tarifpositionen | Auswahl oben von Mieter auf **Einheit** umstellen (nur `LADESTATION`), Vorauswahl über `?einheitId`, Quell-Referenz aus `messpunkt` vorbelegen. Hinweis, wenn keine Ladestations-Einheit existiert bzw. die Einheit keinem Mieter zugeordnet ist. Liste, Sortierung, Spaltenbreiten und Kebab bleiben unverändert. |
| [x] | 13. Hinweis übersprungene Einheiten | `rechnungen`-Komponente vergleicht die gewählten Einheiten mit den erzeugten Rechnungen und meldet die Differenz namentlich (`KEINE_RECHNUNG_FUER_EINHEITEN`, `V108`) — als `warning`, das stehen bleibt, bis es weggeklickt wird. **Nachträglich ergänzt:** Eine Ladestation ohne Positionen im Zeitraum erzeugte kommentarlos nichts, was beim Ausprobieren nicht nachvollziehbar war. Der Hinweis deckt auch die zwei bereits vorher stillen Fälle ab (Produzent ohne Grundgebühr-Tarif, Mietverhältnis ausserhalb des Zeitraums). |
| [x] | 12. Übersetzungen | `V107`: Einheiten-Typ `LADESTATION`, Hinweistexte (RFID, Einheit ohne Mieter, keine Ladestation vorhanden), Fehlermeldung `EINHEIT_MESSPUNKT_EXISTIERT`, Auswahl-Beschriftungen. `EINHEITEN` und `FEHLER_LADEN_EINHEITEN` existierten bereits (V34/V38). Angewendet und verifiziert. |

> **⚠️ Der Testcode kompiliert derzeit nicht.** Die bestehenden Suites prüfen durchgehend den
> Mieter-Anker (`setMieter`, `mieter.einheitId`, `getByMieter`) und müssen umgebaut werden, bevor
> wieder etwas grün läuft — `mvn compile` und `ng build` sind grün, `mvn test` bricht beim
> Kompilieren der Testquellen ab. Das ist die bewusste Konsequenz aus der Trennung
> Umsetzung/Tests, aber kein Zustand, in dem man das Feature als fertig bezeichnen kann.
>
> **Tests** (`/3_backend-tests`, `/4_frontend-unit-tests`, `/5_e2e-tests`) folgen separat und sind
> **nicht** Teil dieser Umsetzung. Sie sind hier aber kein Nachtrag, sondern Umbau: die bestehenden
> Suites prüfen durchgehend den Mieter-Anker – betroffen sind `TarifpositionServiceTest`,
> `TarifpositionControllerTest`, `TarifpositionRepositoryIT`, `RechnungServiceTest`,
> `MieterServiceTest`, `MieterRepositoryIT`, die Frontend-Specs von `mieter-form`,
> `tarifposition-list`/`-form` sowie `tests/ladestromtarif.spec.ts`.

## Validierungen

**Backend**
- `einheit.messpunkt`: bei Typ `LADESTATION` je Mandant eindeutig → sonst `400` mit Meldung. Leereingabe wird auf `null` normalisiert. Länge `VARCHAR(50)` genügt (entschieden).
- `einheit` löschen: abgewiesen, solange Mieter zugeordnet sind → `400` mit Anzahl. `EinheitService.deleteEinheit` prüft heute **nichts** und braucht diesen Guard.
- `mieter` löschen: abgewiesen, solange an einer zugeordneten Einheit Positionen hängen → `400` mit Anzahl.
- `einheit.typ`: `LADESTATION` nimmt nicht an Verteilung, Aggregation und Statistik teil.
- `mieter`: mindestens **eine** zugeordnete Einheit → sonst `400`.
- `mieter`: je zugeordneter Einheit höchstens ein Mieter ohne Mietende; Mietzeiträume dürfen sich je Einheit nicht überschneiden.
- `tarifposition.einheit_id`: Pflicht, Einheit muss existieren und vom Typ `LADESTATION` sein.
- `tarifposition`: höchstens eine je (`org_id`, `einheit_id`, `jahr`, `quartal`, **Tariftyp**); Menge ≥ 0; Quartal 1–4.
- `org_id` überall serverseitig; beim Update aus dem Bestand übernehmen (nicht aus dem DTO).

**Frontend**
- Einheiten-Auswahl auf der Positions-Seite: nur Typ `LADESTATION`.
- Mieter-Formular: mindestens eine Einheit ausgewählt, sonst Submit gesperrt.
- Menge ≥ 0, Tarif und Quartal Pflicht (unverändert).

## Offene Punkte / Annahmen

- **Migrationsnummern `V104`–`V106`** sind der Stand vom 17.08.2026 (`V103` ist die höchste angewendete) – vor dem Anlegen via `zev-db` MCP verifizieren.
- **Solarverteilung und Bilanz:** Annahme – Ladestations-Einheiten erhalten keine Messwerte und bleiben aussen vor. Wird erst scharf, wenn der Import Messwerte statt Quartalsmengen liefert.
- **Wachsende Zahl inaktiver Einheiten:** Je Mieterwechsel entsteht eine neue Einheit. Ob die Auswahllisten einen Aktiv-Filter brauchen, ist offen; Annahme für diese Umsetzung: Sortierung genügt.
- **Zeitraum der Zuordnung:** Der Mietzeitraum bleibt am Mieter und gilt für alle seine Einheiten. Fallen Wohnungs- und Ladestations-Nutzung zeitlich auseinander, braucht es zwei Mieter-Datensätze – bis eine Vertragspartner-Entität existiert (Spec §8).
- **Reihenfolge der Migrationen:** Phase 3 (`V104`) muss vor Phase 4 laufen, Phase 5 (`V105`) vor Phase 6; die Anwendung ist zwischen den Phasen nicht lauffähig. Der Umbau gehört in einen Zug.
