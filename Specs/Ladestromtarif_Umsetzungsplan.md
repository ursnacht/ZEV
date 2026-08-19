# Ladestromtarif – Umsetzungsplan

## Zusammenfassung

Ladestrom wird den Mietern zu einem **eigenen Tarif** verrechnet: Ein neuer `TarifTyp.LADESTROM`
in der bestehenden Tarifverwaltung, dazu eine **generische** Tabelle `tarifposition`, in der je
Mieter und Quartal eine manuell erfasste Menge hinterlegt wird. Bei der Rechnungserzeugung
erscheinen diese Positionen automatisch als zusätzliche Zeilen. Die Erfassung läuft über eine
**eigene Seite** `/tarifpositionen`; der spätere automatische Bezug aus dem Lademanagement ist
vorbereitet (Erfassungsart und Quell-Referenz je Position), aber nicht Teil dieser
Umsetzung.

Grundlage: [`Specs/Ladestromtarif.md`](./Ladestromtarif.md).

> **Stand:** Die Phasentabelle unten hält fest, was **damals** umgesetzt wurde — inklusive des
> Ankers am Mieter und des inzwischen entfallenen `mieter.ladepunkt`. Der Anker liegt seit
> [`Specs/Ladestationen.md`](./Ladestationen.md) an der **Einheit** (`V106`). Die Phasen bleiben
> als Historie stehen; die Validierungsregeln weiter unten sind nachgezogen.

> **Zwei Arbeitspakete, die es NICHT gibt** – beide im Review verifiziert, damit sie nicht aus
> Gewohnheit doch eingeplant werden:
> * **Keine Tarif-Überschneidungsprüfung bauen.** `TarifService.saveTarif` prüft bereits
>   typbezogen (`existsOverlappingTarif(tariftyp, …)`) und greift mit dem neuen Enum-Wert von
>   selbst auch für `LADESTROM`.
> * **Keine Änderung an PDF-Template oder Rechnungs-Ansicht.** `rechnung.jrxml` kennt die Felder
>   `bezeichnung, von, bis, menge, mengeneinheit, preis, betrag` – **kein** `typ` – und rendert
>   Positionen typunabhängig; die `rechnungen`-Komponente verzweigt ebenfalls nicht nach `TarifTyp`.
>   Neue Zeilen erscheinen automatisch in Web **und** PDF.

## Betroffene Komponenten

**Backend (neu):**
- `entity/Tarifposition.java` – `org_id`, `mieter_id`, `tarif_id`, `jahr`, `quartal`, `menge`, `erfassungsart`, `quell_referenz`, `bemerkung`
- `entity/Erfassungsart.java` – Enum `{ MANUELL, IMPORT }` (Name bewusst **nicht** `Quelle` – `ch.nacht.entity.Quelle` ist mit CSV/MQTT/API belegt)
- `repository/TarifpositionRepository.java`
- `service/TarifpositionService.java`
- `controller/TarifpositionController.java` – `/api/tarifpositionen`
- `dto/TarifpositionDTO.java`

**Backend (geändert):**
- `entity/TarifTyp.java` – neuer Wert `LADESTROM`
- `service/RechnungService.java` – zusätzliche Tarifzeilen nach ZEV/VNB, vor Grundgebühr

**Frontend (neu):**
- `models/tarifposition.model.ts`
- `services/tarifposition.service.ts`
- `components/tarifposition-list/tarifposition-list.component.{ts,html,css}` – Mieter-Auswahl, Liste und eingebettetes Formular (Muster: `tarif-list` mit `showForm`)

**Frontend (geändert):**
- `app.routes.ts` – Route `/tarifpositionen`
- `components/navigation/navigation.component.html` – Menüeintrag
- `components/mieter-list/` – Kebab-Eintrag „Tarifpositionen" (Sprung mit `queryParams`)
- `components/tarif-form/` – Typ-Dropdown um `LADESTROM`

**Design System (geändert):**
- `components/message/message.css` – neue Variante `zev-message--statisch` (Hinweis im Textfluss statt Overlay)
- `components/table/table.css` – `.zev-table__number` von `statistik.css` hierher verschoben (Tabellen-Konzern, von allen Tabellen mit Zahlenspalten genutzt)
- Bereinigt: `tarif-list`/`mieter-list` nutzen jetzt `zev-message--dismissible` aus dem Design System statt lokal dupliziertem `zev-message--clickable`

**Datenbank:**
- `V100__Create_Tarifposition.sql` – Tabelle, Sequenz, Indizes, `mieter.ladepunkt`
- `V101__Add_Ladestromtarif_Translations.sql` – i18n (DE/EN)
- `V102__Extend_Tariftyp_Check_Ladestrom.sql` – CHECK-Constraint `tarif_tariftyp_check` um `LADESTROM` erweitern
- `V103__Remove_Mieter_Ladepunkt.sql` – nimmt `mieter.ladepunkt` aus V100 wieder zurück (siehe Phase 14)

## Umsetzungsreihenfolge (Phasen)

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. DB-Migration | `V100`: Tabelle `zev.tarifposition` + Sequenz `tarifposition_seq`; FK `mieter_id` **ON DELETE CASCADE**, FK `tarif_id` **ON DELETE RESTRICT**; UNIQUE (`org_id`,`mieter_id`,`tarif_id`,`jahr`,`quartal`); Index (`org_id`,`mieter_id`,`jahr`,`quartal`). Zusätzlich Spalte `mieter.ladepunkt VARCHAR(64)` + **partieller** Unique-Index `(org_id, ladepunkt) WHERE ladepunkt IS NOT NULL`. **Vor dem Anlegen die nächste freie Nummer via `zev-db` MCP prüfen.** |
| [x] | 1b. DB-Migration Tariftyp | `V102`: `tarif_tariftyp_check` droppen und mit `LADESTROM` neu anlegen (Aufzählung der erlaubten Werte, zuletzt in `V50` gesetzt). **Nachträglich ergänzt** – ohne diese Migration scheitert das Anlegen eines Ladestromtarifs mit `DataIntegrityViolationException`. Spaltenlänge `VARCHAR(20)` genügt. |
| [x] | 2. Backend-Enums | `TarifTyp` um `LADESTROM` erweitern; neues Enum `Erfassungsart { MANUELL, IMPORT }`. Konstante für die **Menge manuell erfasster Tariftypen** (aktuell nur `LADESTROM`) an zentraler Stelle, damit ein weiterer Anwendungsfall nur diese Menge erweitert. |
| [x] | 3. Backend-Entity + Repository | `Tarifposition` (Vorlage: `Tarif.java`) mit `@Filter(name = "orgFilter")`, `EnumType.STRING` (`length = 20`) für `erfassungsart`, `BigDecimal` für `menge`. `TarifpositionRepository` mit Findern nach Mieter sowie nach Mieter + Quartalsüberschneidung. |
| [x] | 4. Backend-Service | `TarifpositionService` (Vorlage: `TarifService`): CRUD, `hibernateFilterService.enableOrgFilter()`, `orgId` aus `OrganizationContextService`; Validierungen s. u.; Eindeutigkeit **je Tariftyp** (nicht je Tarif) serverseitig; Änderungen protokollieren. |
| [x] | 5. Backend-Controller | `TarifpositionController` unter `/api/tarifpositionen`, `@PreAuthorize("hasAuthority('rechnungen:manage')")` für Schreiben, `hasAuthority('mieter:read')` für Lesen. `IllegalArgumentException` → `400` (bestehender `GlobalExceptionHandler`). |
| [x] | 6. Mieter erweitern | `Mieter.ladepunkt` + Eindeutigkeitsprüfung in `MieterService.save…`. **Kein DTO anlegen** – `MieterController` liefert die Entity direkt, das bleibt so. |
| [x] | 7. RechnungService | Nach den ZEV-/VNB-Zeilen und **vor** der Grundgebühr: Positionen des Mieters laden, deren Quartal sich mit `von`/`bis` **überschneidet** und `menge > 0`; je Position eine `TarifZeileDTO` (`bezeichnung` = Tarif-Bezeichnung, ergänzt um die **Quell-Referenz** in Klammern via `bezeichnungMitQuellReferenz()`, sofern erfasst; `von`/`bis` = Quartalsgrenzen, `mengeneinheit` = "kWh", `typ` = `LADESTROM`). Ohne Mieter (`mieter == null`) keine Positionen. Produzenten-Rechnungen unberührt. |
| [x] | 8. Frontend Model + Service | `tarifposition.model.ts`, `tarifposition.service.ts` (`getByMieter`, `create`, `update`, `delete`), `subscribe({ next, error })`. |
| [x] | 9. Frontend Komponente | `tarifposition-list` (standalone, `WithMessage`): oben **Mieter-Auswahl** (Dropdown), darunter Liste (`.zev-table`, Menge rechtsbündig `.zev-table__number`, Spalte Herkunft, **sortierbar** über alle Datenspalten und **spaltenbreiten-veränderbar** via `appColumnResize` – wie `tarif-list`/`einheit-list`/`mieter-list`) und eingebettetes Formular über `showForm` – Muster `tarif-list`/`tarif-form`. Kebab-Menü *Bearbeiten* / *Kopieren* (`const { id, ...ohneId }`, Muster `tarif-list.onCopy`) / *Löschen*; der Key `KOPIEREN` existiert seit `V35`. **Jahr und Quartal als je ein Dropdown**, bewusst nicht `QuarterSelectorComponent` (die arbeitet mit Datumsbereichen). Quell-Referenz frei erfassbar. Hinweis in der Ansicht, dass Positionen bei jeder Rechnungserstellung erneut aufgenommen werden (kein „bereits verrechnet"-Status) — im Textfluss (`zev-message--statisch`, neue Design-System-Variante) und wegklickbar, gemerkt in `localStorage`. |
| [x] | 10. Routing | `app.routes.ts`: `{ path: 'tarifpositionen', component: TarifpositionListComponent, canActivate: [AuthGuard], data: { permissions: ['rechnungen:manage'] } }`. |
| [x] | 11. Navigation + Kebab | Menüeintrag in `navigation.component.html` (Icon + `TARIFPOSITIONEN`-Key; **keine** Sichtbarkeitsprüfung im Template – Projektkonvention, der `AuthGuard` blockt). Kebab-Eintrag „Tarifpositionen" in `mieter-list` mit `routerLink="/tarifpositionen"` und `queryParams: { mieterId }`; die Komponente liest den Parameter und wählt den Mieter vor. |
| [x] | 12. Formulare ergänzen | `tarif-form`: `LADESTROM` im Typ-Dropdown. (Das ursprünglich hier ergänzte Feld „Ladepunkt" im `mieter-form` ist mit Phase 14 wieder entfallen.) |
| [x] | 13. Übersetzungen | `V101__Add_Ladestromtarif_Translations.sql` (DE/EN, `ON CONFLICT (key) DO NOTHING`): Menü- und Seitentitel, Spaltenüberschriften, Formularlabels, Erfassungsart-Werte, Fehlermeldungen (Duplikat, negative Menge, unzulässiger Tariftyp, doppelte Ladepunkt-Kennung), Tariftyp `LADESTROM`, Hinweistext zur Mehrfachverrechnung. |
| [x] | 14. Ladepunkt am Mieter zurücknehmen | `V103__Remove_Mieter_Ladepunkt.sql` plus Entfernen von `Mieter.ladepunkt`, `MieterRepository.existsByLadepunkt`, der Prüfung in `MieterService`, dem Feld im `mieter-form` und der Vorbelegung der Quell-Referenz. **Grund:** Ein Attribut hält genau einen Wert, ein Nutzer kann aber mehrere Ladestationen haben — und ein Ladestations-Nutzer ist nicht zwingend Mieter einer Wohnung. Die Zuordnung löst das Zielbild strukturell (Spec Abschnitt 7). Die Übersetzungs-Keys `LADEPUNKT` und `LADEPUNKT_HINT` bleiben bestehen — sie werden dort wiederverwendet. |
| [x] | 15. Grundgebühr wieder zurückgenommen | Die mit `f7efce4` eingefuehrte Erfassbarkeit von `GRUNDGEBUEHR` ist mit dieser Phase wieder entfallen (Begruendung: Spec FR-6). Was aus dem Versuch **bleibt**, weil `ZUSATZ` es ohnehin braucht: die Eindeutigkeitspruefung je **Typ dieser Position** (statt gegen die ganze Menge), `TarifTyp.mengeneinheit()`, `tarifTyp` im `TarifpositionDTO`, die Mengeneinheit je Listenzeile und im Mengen-Label sowie die Uebersetzungen aus `V110`. |

> **Tests** (`/3_backend-tests`, `/4_frontend-unit-tests`, `/5_e2e-tests`) werden separat erstellt und
> sind **nicht** Teil dieser Umsetzung. Schwerpunkte: Eindeutigkeit je Tariftyp, Überschneidungsregel
> bei Mieterwechsel im Quartal, `menge = 0`, Rechnung ohne Mieter, `403` ohne `rechnungen:manage`,
> Mandantentrennung.

## Validierungen

### Backend (massgeblich)
- **`einheit_id`:** muss existieren, zum Mandanten gehören und vom Typ `LADESTATION` sein → sonst `400`. *(Bis `V106` war es `mieter_id`.)*
- **`tarif_id`:** muss existieren **und** einen Typ aus der Menge der manuell erfassten Typen haben (`LADESTROM`, `GRUNDGEBUEHR`) → sonst `400`.
- **`menge`:** `≥ 0`; `< 0` → `400`. `= 0` ist gültig, erzeugt aber keine Rechnungszeile.
- **`quartal`:** `1..4`; **`jahr`:** plausibel (2000–2100).
- **Eindeutigkeit:** höchstens eine Position je (`org_id`, `einheit_id`, `jahr`, `quartal`, **Tariftyp**) – im Service geprüft, da der Typ am Tarif hängt; DB-UNIQUE über `tarif_id` als Netz gegen exakte Duplikate.
- **Autorisierung:** Schreiben `rechnungen:manage`, Lesen `mieter:read` – sonst `403`.
- **Mandantentrennung:** `orgFilter` in jedem Service-Zugriff; `orgId` **nie** aus dem Request.

### Frontend
- Speichern deaktiviert, solange Mieter, Tarif, Jahr, Quartal oder Menge fehlen.
- Menge: numerisch, `≥ 0`; Anzeige im Schweizer Format (`generell.md`: Dezimalpunkt, Hochkomma als Tausendertrennzeichen).
- Tarif-Dropdown zeigt ausschliesslich `LADESTROM`-Tarife.
- Backend-Fehler (`400`/`403`) als Message anzeigen; leere Liste → Hinweistext statt leerer Tabelle.
- Die Validierung ist **Komfort**, nicht Schutz – massgeblich ist das Backend.

## Offene Punkte / Annahmen

- **Doppelverrechnungsschutz: entschieden — vorerst ohne** (2026-08-16). Rechnungen werden nicht persistiert, es gibt keinen „bereits verrechnet"-Status; zwei Läufe über denselben Zeitraum nehmen die Position zweimal auf. Abgesichert wird das **organisatorisch** plus einem dauerhaften Hinweis in der Erfassungsansicht (Phase 9). Ein technischer Schutz setzte das Persistieren von Rechnungen voraus und waere ein eigenes Vorhaben.
- **Schnittstelle Lademanagement: out of scope.** Vorbereitet sind `erfassungsart` und `quell_referenz`; `IMPORT` wird in dieser Umsetzung nie geschrieben. Das Zielbild (Einheit vom Typ `LADESTATION`, Kennung in `einheit.messpunkt`, mehrere Einheiten je Mieter) ist inzwischen umgesetzt und steht in [`Specs/Ladestationen.md`](./Ladestationen.md).
- **Migrationsnummern `V100`–`V102`** waren zum Umsetzungszeitpunkt frei (`V99` war die höchste vorhandene), alle drei sind angewendet.
- **Gelernt:** Ein neuer `TarifTyp` ist **nicht** rein additiv – der CHECK-Constraint auf `tarif.tariftyp` zählt die Werte auf. Bei künftigen Enum-Erweiterungen die DB-Constraints der betroffenen Spalte prüfen (`pg_get_constraintdef`), nicht nur den Spaltentyp.
- **Mieter-Auswahl auf der neuen Seite:** Annahme – alle Mieter des Mandanten, alphabetisch, ohne Filter auf aktive Mietverhältnisse. Bei vielen Mietern wäre eine Suche nachzurüsten.
- **`queryParams` sind ein neues Muster** in diesem Frontend (bisher nirgends verwendet) – Standard-Angular, aber erstmalig hier.
- **Menüeintrag ohne Sichtbarkeitsprüfung:** folgt der bestehenden Konvention (alle Einträge werden gerendert, der `AuthGuard` blockt). Ein `zev_user` ohne `rechnungen:manage` sähe den Eintrag, käme aber nicht auf die Seite.
- **Ladestrom-Messpunkt** muss betrieblich als `CONSUMER`-Einheit mit zugeordnetem Mieter (Eigentümer) existieren – reine Stammdatenpflege, kein Code.
