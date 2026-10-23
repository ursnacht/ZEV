# Tarifpositionen

> **Verhältnis zu bestehenden Specs.** Die Tabelle `tarifposition`, die Erfassungsmaske und die
> Rechnungsintegration sind in [`Specs/Ladestromtarif.md`](./Ladestromtarif.md) beschrieben, der
> Anker an der Einheit in [`Specs/Ladestationen.md`](./Ladestationen.md). Dieses Dokument
> beschreibt die **Verallgemeinerung**: einen frei konfigurierbaren Tariftyp `ZUSATZ` mit eigener
> Mengeneinheit, der auch für **Konsumenten** erfassbar ist. Es ergänzt die beiden Dokumente und
> ersetzt sie nicht; für alles, was hier nicht steht, gilt `Ladestromtarif.md`.

## 1. Ziel & Kontext - Warum wird das Feature benötigt?

* **Was soll erreicht werden:** Ein Tariftyp **`ZUSATZ`**, bei dem neben Bezeichnung und
  Preis auch die **Mengeneinheit** (kWh, Monat, Stück) frei gewählt wird. Positionen dieses Typs
  lassen sich nicht nur an Ladestationen, sondern auch an **Konsumenten-Einheiten** erfassen. Die
  Erfassungsmaske hat dafür eine Checkbox, die den Normalfall (nur Ladestationen) vom
  Ausnahmefall trennt.
* **Warum machen wir das:** Ohne `ZUSATZ` war jede manuell erfasste Menge an einen fest
  verdrahteten Zweck gebunden: `LADESTROM` rechnet kWh, `GRUNDGEBUEHR` rechnet Monate.
  Wiederkehrende Nebenkosten ausserhalb dieses Rasters — Sauna, Waschküche, Gästezimmer,
  einmalige Dienstleistungen — brauchten je einen neuen Tariftyp samt Code- und DDL-Änderung. Mit
  `ZUSATZ` genügt ein Datensatz in der Tarifverwaltung.
* **Ausgangslage:** Manuell erfassbar war allein `LADESTROM`, die Mengeneinheit einer
  Rechnungszeile folgte aus dem Tariftyp, und Positionen gab es nur an Ladestationen
  (Hintergrund: Anhang A.1).

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Ablauf / Flow

1. Ein Benutzer legt in der **Tarifverwaltung** einen Tarif vom Typ **`ZUSATZ`** an. Zusätzlich zu
   Bezeichnung, Preis und Gültigkeit wählt er eine **Mengeneinheit**: `kWh`, `Monat` oder `Stück`.
   Bei `ZEV`, `VNB` und `LADESTROM` ist das Feld ausgeblendet, weil deren Einheit aus dem Typ
   folgt; die Grundgebühr bietet Monat oder Tag an (`Specs/Tarifverwaltung.md`, FR-3; Hintergrund:
   Anhang A.10).
2. **Mehrere `ZUSATZ`-Tarife dürfen gleichzeitig gültig sein.** Die Überschneidungsprüfung
   greift für diesen Typ **nicht**: Sauna, Waschküche und Gästezimmer sind alle vom Typ `ZUSATZ`
   und müssen nebeneinander bestehen. Für `ZEV`, `VNB` und `GRUNDGEBUEHR` gilt
   `Specs/Tarifverwaltung.md`, FR-2 (mehrere gleichzeitig gültig, abgewiesen wird nur eine
   Überschneidung mit gleicher Bezeichnung); bei `LADESTROM` wird jede Überschneidung abgewiesen
   (Hintergrund: Anhang A.9).
3. Er öffnet die Seite **Tarifpositionen**. Oberhalb der Einheiten-Auswahl steht eine Checkbox
   **„Nur Ladestationen"**, die **standardmässig gesetzt** ist:
   - **gesetzt (Normalfall):** Die Einheiten-Auswahl zeigt ausschliesslich Einheiten vom Typ
     `LADESTATION`; als Tarife stehen `LADESTROM` und `ZUSATZ` zur Wahl.
   - **nicht gesetzt (Ausnahme):** Die Auswahl zeigt zusätzlich die **Konsumenten-Einheiten**.
   Zeigt ein Sprung aus der Einheiten-Verwaltung (`?einheitId=…`) auf einen **Konsumenten**,
   wird die Checkbox **automatisch abgewählt** — sonst landete der Benutzer auf einer Seite ohne
   die Einheit, die er angeklickt hat.
   Die Stellung der Checkbox wird **nicht gespeichert**: Sie steht bei jedem Seitenaufruf wieder
   auf „Nur Ladestationen", weil das der Normalfall ist (Hintergrund: Anhang A.6).
4. Welche Tarife wählbar sind, richtet sich nach dem **Typ der gewählten Einheit**:
   - `LADESTATION` → `LADESTROM`, `ZUSATZ`
   - `CONSUMER` → **nur** `ZUSATZ`
   > Ladestrom gehört fachlich an eine Ladestation. Für eine Wohnung bleibt damit ausschliesslich
   > der frei konfigurierbare Typ — genau der Ausnahmefall, den die Checkbox freischaltet.
5. Er erfasst die Position wie bei Ladestrom: Tarif, Jahr + Quartal, Menge. **Beschriftung und
   Hinweis des Mengenfelds nennen die Einheit des gewählten Tarifs** (kWh / Monate / Stück).
6. Bei der **Rechnungserzeugung** erscheinen `ZUSATZ`-Positionen wie die übrigen Positionen als
   eigene Zeilen — mit der am Tarif hinterlegten Mengeneinheit und dem Quartal der Position,
   **eingeschränkt auf die Gültigkeit des Tarifs** (`Ladestromtarif.md`, FR-3). Für
   Konsumenten-Einheiten gilt der Weg aus `Ladestationen.md`: Die Positionen **aller** Einheiten
   eines Mieters landen auf dessen Rechnung. Die Reihenfolge der Zeilen bestimmt die
   Tarifverwaltung (`Specs/Tarifverwaltung.md`, FR-4; Hintergrund: Anhang A.7).

### FR-2: Persistierung

* **Enum-Wert `TarifTyp.ZUSATZ`.** Die Spalte `zev.tarif.tariftyp` ist `VARCHAR(20)`
  (`EnumType.STRING`) und trägt den CHECK-Constraint `tarif_tariftyp_check`, der die erlaubten
  Werte **explizit aufzählt** und `ZUSATZ` enthält. Ohne diesen Eintrag scheitert das Anlegen mit
  `DataIntegrityViolationException`. Die Spaltenlänge genügt.
  > Achtung: `Tarif.tariftyp` trägt im Code `@Column(length = 10)`, die Datenbank hat aber
  > `VARCHAR(20)`. `ZUSATZ` passt in beides; die Abweichung ist nur zu beachten, falls
  > später ein längerer Wert dazukommt.
* **Spalte `zev.tarif.mengeneinheit VARCHAR(10)`, nullable**, mit CHECK-Constraint.
  * `ZUSATZ`: **Pflicht**, `KWH`, `MONAT` oder `STUECK`.
  * `GRUNDGEBUEHR`: `MONAT` oder `TAG`, ohne Angabe `MONAT` (`Specs/Tarifverwaltung.md`, FR-3).
  * Alle anderen Typen: `NULL`, weil deren Einheit aus dem Typ folgt.

  Die Regeln prüft der **Service** (nicht ein DB-Constraint je Typ), damit sie an einer Stelle
  stehen.
* **Ableitung der Mengeneinheit:** `TarifTyp.mengeneinheit()` ist die Quelle für
  `ZEV`/`VNB`/`LADESTROM` (`KWH`). Für `ZUSATZ` und `GRUNDGEBUEHR` gilt der Wert aus der Spalte.
* **`MANUELL_ERFASST`** ist `{ LADESTROM, ZUSATZ }`.
* **Eindeutigkeit einer Position — typabhängig:**

  | Tariftyp | Regel |
  |----------|-------|
  | `LADESTROM` | höchstens **eine** Position je Einheit, Quartal und **Tariftyp** |
  | `ZUSATZ` | höchstens **eine** Position je Einheit, Quartal und **Tarif** |
  > Für `ZUSATZ` je Typ zu prüfen wäre sinnlos: Man könnte dann pro Quartal nur eine einzige
  > Zusatzposition erfassen. Der DB-Constraint `UNIQUE (org_id, einheit_id, tarif_id, jahr, quartal)`
  > deckt die `ZUSATZ`-Regel ab — an `tarifposition` braucht es dafür keine DDL.
* **Tabelle `tarifposition`:** Menge, Erfassungsart, Quell-Referenz und Bemerkung gelten für
  `ZUSATZ` wie für `LADESTROM`; die Bedeutung steckt ausschliesslich im Tarif.
* **Flyway:** `V111` (CHECK-Constraint, Spalte `mengeneinheit`, Übersetzungen), `V112`
  (Platzhalter der Einheiten-Auswahl), `V113` (Schlüssel-Namensraum: `TARIFTYP_*` sind
  Tariftypen, `TYP_*` Einheitentypen), `V114` (Preis-Hinweis einheitneutral), `V115` (Hinweis bei
  leerer Einheiten-Auswahl) (Hintergrund: Anhang A.3).

### FR-3: Layout

* **Tarifverwaltung**
  - `ZUSATZ` erscheint im Typ-Dropdown.
  - Bei ausgewähltem `ZUSATZ` erscheint ein zusätzliches Dropdown **Mengeneinheit**
    (kWh / Monat / Stück) mit Hinweistext. Umschalten auf `ZEV`, `VNB` oder `LADESTROM` blendet es
    wieder aus und verwirft den Wert; bei der Grundgebühr zeigt es Monat und Tag
    (`Specs/Tarifverwaltung.md`, FR-3). Vorlage: der nur bei `GRUNDGEBUEHR` sichtbare Block
    „Produzent verrechnen" in `tarif-form.component.html`.
  - Ein `ZUSATZ`-Tarif mit Mengeneinheit **Monat** kann einen **Jahrespreis** tragen, der anteilig
    verrechnet wird (`Specs/Tarifverwaltung.md`, FR-6).
  - **Preis-Label und -Hinweis folgen der Einheit:** `Preis (CHF/kWh)`, `(CHF/Monat)`,
    `(CHF/Stück)` — und nur `(CHF)`, solange ein Zusatz-Tarif noch keine Einheit hat. Der
    Hinweistext darunter nennt deshalb nur das Zahlenformat statt „pro kWh".
  - Die Tarif-Liste zeigt die Einheit **beim Preis** je Zeile (`5.00 / Stück`) statt in der
    Spaltenüberschrift.
    > Eine eigene, bei vier von fünf Typen leere Spalte wäre der schlechtere Tausch (Hintergrund:
    > Anhang A.2).
* **Tarifpositionen**
  - **Checkbox „Nur Ladestationen"** oberhalb der Einheiten-Auswahl, Design-System-Klassen
    `.zev-checkbox-item` + `.zev-checkbox` (Vorlage: `tarif-form`), mit Hinweistext, der den
    Ausnahmecharakter benennt. Default **gesetzt**.
  - Wird die Checkbox **wieder gesetzt**, während eine Konsumenten-Einheit gewählt ist, wird die
    Auswahl **zurückgesetzt** (kein Zustand, in dem eine nicht mehr angebotene Einheit selektiert
    bleibt).
  - Die Einheiten-Auswahl nennt bei gemischter Liste den **Typ** je Eintrag, damit Wohnung und
    Ladestation unterscheidbar sind.
  - Liste und Formular zeigen die Mengeneinheit **je Zeile** und im Mengen-Label, wie bei
    Ladestrom.
* **Rechnung (Web + PDF):** keine strukturelle Änderung — es kommen nur Zeilen dazu. Die
  `mengeneinheit` der Zeile stammt aus dem Tarif.
* Alle Texte via `TranslationService`/`TranslatePipe`; Zahlenformatierung nach
  `Specs/generell.md`.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

### Tarif
* [x] In der Tarifverwaltung lässt sich ein Tarif vom Typ `ZUSATZ` mit Mengeneinheit `kWh`,
      `Monat` oder `Stück` anlegen, bearbeiten und löschen. (Test: tarif-form.component.spec.ts › mengeneinheit (ZUSATZ, GRUNDGEBUEHR) › should offer kWh, month and piece for ZUSATZ; Test: tarif-form.component.spec.ts › mengeneinheit (ZUSATZ, GRUNDGEBUEHR) › should keep the unit of an existing ZUSATZ tariff and save a changed one; Test: TarifServiceTest#deleteTarif_Exists_ReturnsTrue; E2E: tarifpositionen.spec.ts › should show and require the unit field for ZUSATZ, not for LADESTROM; E2E: tarifpositionen.spec.ts › should allow two overlapping zusatz tariffs; E2E: tarifpositionen.spec.ts › afterEach (löscht die angelegten `ZUSATZ`-Tarife über die Maske, scheitert sichtbar bei Rückstand))
* [x] Das Feld **Mengeneinheit** ist bei Typ `ZUSATZ` sichtbar und dort **Pflicht** (kWh, Monat,
      Stück), bei `ZEV`, `VNB` und `LADESTROM` ausgeblendet; ohne Auswahl ist das Speichern eines
      `ZUSATZ`-Tarifs nicht möglich (Serverseitig `400`, Formular deaktiviert). Für die
      Grundgebühr gilt `Specs/Tarifverwaltung.md`, FR-3. (Test: TarifServiceTest#saveTarif_ZusatzOhneMengeneinheit_ThrowsException; Test: TarifControllerTest#createTarif_OverlappingTarif_ReturnsBadRequest (IllegalArgumentException → 400); Test: tarif-form.component.spec.ts › mengeneinheit (ZUSATZ, GRUNDGEBUEHR) › should not require a unit for the fixed types; Test: tarif-form.component.spec.ts › mengeneinheit (ZUSATZ, GRUNDGEBUEHR) › should render the unit dropdown for ZUSATZ but not for the fixed types; Test: tarif-form.component.spec.ts › mengeneinheit (ZUSATZ, GRUNDGEBUEHR) › should be invalid while a ZUSATZ tariff has no unit; E2E: tarifpositionen.spec.ts › should show and require the unit field for ZUSATZ, not for LADESTROM)
* [x] **Zwei `ZUSATZ`-Tarife mit überlappender Gültigkeit lassen sich beide speichern.** (Test: TarifServiceTest#saveTarif_ZweiUeberlappendeZusatzTarife_SavesBoth; E2E: tarifpositionen.spec.ts › should allow two overlapping zusatz tariffs)
* [x] Zwei `LADESTROM`-Tarife mit überlappender Gültigkeit werden **abgewiesen**, auch mit
      anderer Bezeichnung; für `ZEV`/`VNB`/`GRUNDGEBUEHR` gelten die Kriterien von
      `Specs/Tarifverwaltung.md`, FR-2. (Test: TarifServiceTest#saveTarif_LadestromUeberschneidung_ThrowsException; Test: TarifServiceTest#saveTarif_OverlappingTarifGleicheBezeichnung_ThrowsException; Test: TarifServiceTest#saveTarif_VnbNetznutzungNebenEnergielieferung_SavesSuccessfully; Test: TarifServiceTest#saveTarif_DreiGrundgebuehren_PruefungJeBezeichnung; E2E: ladestromtarif.spec.ts › should reject a second LADESTROM tariff with overlapping validity but accept an adjacent one)
* [x] Ein `ZUSATZ`-Tarif, auf den Positionen verweisen, lässt sich **nicht** löschen (bestehende
      Regel, Meldung mit Anzahl). (Test: TarifServiceTest#deleteTarif_MitTarifpositionen_ThrowsException; E2E: ladestationen.spec.ts › should refuse deleting a tariff that positions refer to)

### Erfassung
* [x] Beim Öffnen der Seite Tarifpositionen ist die Checkbox „Nur Ladestationen" **gesetzt** und
      die Einheiten-Auswahl enthält **ausschliesslich** Ladestationen. (Test: tarifposition-list.component.spec.ts › nurLadestationen (Checkbox) › should be checked by default and offer charging stations only; Test: tarifposition-list.component.spec.ts › einheiten-auswahl › should offer only einheiten of type LADESTATION; E2E: tarifpositionen.spec.ts › should offer consumers only when the filter is unchecked)
* [x] Nach dem Abwählen der Checkbox enthält die Auswahl zusätzlich die **Konsumenten**-Einheiten;
      Produzenten und Bilanz-Einheiten (Bezug/Rücklieferung) erscheinen **nie**. (Test: tarifposition-list.component.spec.ts › nurLadestationen (Checkbox) › should add the consumers when unchecked, but never producers; Test: tarifposition-list.component.spec.ts › einheiten-auswahl › should offer only einheiten of type LADESTATION; E2E: tarifpositionen.spec.ts › should offer consumers only when the filter is unchecked)
* [x] Bei gewählter **Konsumenten**-Einheit sind ausschliesslich `ZUSATZ`-Tarife wählbar;
      `LADESTROM` erscheint dort **nicht**. (Test: tarifposition-list.component.spec.ts › tarifeFuerAuswahl › should offer only zusatz for a consumer; Test: TarifpositionServiceTest#saveTarifposition_KonsumentMitLadestromTarif_ThrowsException; E2E: tarifpositionen.spec.ts › should record a zusatz position for a consumer)
* [x] Bei gewählter **Ladestations**-Einheit sind `LADESTROM` und `ZUSATZ` wählbar,
      `GRUNDGEBUEHR` dagegen nicht. (Test: tarifposition-list.component.spec.ts › tarifeFuerAuswahl › should offer ladestrom and zusatz for a charging station; Test: TarifpositionServiceTest#saveTarifposition_GrundgebuehrTariftyp_ThrowsException; E2E: ladestromtarif.spec.ts › should offer only manually captured tariff types and reject a negative quantity)
* [x] Wird die Checkbox bei gewählter Konsumenten-Einheit wieder gesetzt, ist **keine** Einheit
      mehr ausgewählt und die Positionsliste ist leer. (Test: tarifposition-list.component.spec.ts › nurLadestationen (Checkbox) › should reset a consumer selection when checked again; E2E: tarifpositionen.spec.ts › should offer consumers only when the filter is unchecked)
* [x] Beschriftung und Hinweis des Mengenfelds nennen die Mengeneinheit des gewählten Tarifs
      (`kWh` / `Monate` / `Stück`). (Test: tarifposition-form.component.spec.ts › mengeneinheit nach Tarif › should label a ladestrom position in kWh; Test: tarifposition-form.component.spec.ts › mengeneinheit nach Tarif › should label a zusatz position in months; Test: tarifposition-form.component.spec.ts › mengeneinheit nach Tarif › should label a zusatz position in pieces; Test: tarifposition-form.component.spec.ts › mengeneinheit nach Tarif › should render the unit in the label; E2E: tarifpositionen.spec.ts › should record a zusatz position for a consumer)
* [x] Für dieselbe Einheit und dasselbe Quartal lassen sich **mehrere `ZUSATZ`-Positionen mit
      verschiedenen Tarifen** erfassen (z.B. Sauna und Waschküche). (Test: TarifpositionServiceTest#saveTarifposition_ZweiZusatzPositionenVerschiedenerTarife_SavesBoth)
* [x] Eine **zweite Position mit demselben `ZUSATZ`-Tarif** für dieselbe Einheit und dasselbe
      Quartal wird **abgewiesen** (Meldung, kein Datensatz). (Test: TarifpositionServiceTest#saveTarifposition_ZweitePositionMitDemselbenZusatzTarif_ThrowsException; Test: TarifpositionControllerTest#createTarifposition_DuplicatePosition_ReturnsBadRequest; Test: tarifposition-list.component.spec.ts › onFormSubmit - create › should show the duplicate message from the server and keep the form open)
* [x] Das Erfassen einer Position an einer Einheit vom Typ `PRODUCER`, `BEZUG` oder
      `RUECKLIEFERUNG` wird serverseitig **abgewiesen** (`400`), auch bei manipuliertem Request. (Test: TarifpositionServiceTest#saveTarifposition_EinheitVomTypProducer_ThrowsException; Test: TarifpositionServiceTest#saveTarifposition_EinheitVomTypBezug_ThrowsException; Test: TarifpositionServiceTest#saveTarifposition_EinheitVomTypRuecklieferung_ThrowsException; Test: TarifpositionControllerTest#createTarifposition_EinheitNichtVomTypLadestation_ReturnsBadRequest)

### Rechnung
* [x] Eine `ZUSATZ`-Position an einer Konsumenten-Einheit erscheint auf der Rechnung des Mieters
      als eigene Zeile mit der **am Tarif hinterlegten Mengeneinheit**. (Test: RechnungServiceTest#berechneRechnung_ZusatzPositionenAnWohnung_JeEigeneZeileMitEinheitDesTarifs; RechnungServiceTest#berechneRechnung_ZusatzMitJahrespreis_MonateDurchZwoelf; Test: RechnungServiceTest#berechneRechnungen_WohnungUndLadestationAusgewaehlt_ErzeugtGenauEineRechnung; Test: MengeneinheitTest#keyFuer_EinzahlUndMehrzahl)
* [x] Mehrere `ZUSATZ`-Positionen desselben Quartals erscheinen als **mehrere Zeilen**. (Test: RechnungServiceTest#berechneRechnung_ZusatzPositionenAnWohnung_JeEigeneZeileMitEinheitDesTarifs; RechnungServiceTest#berechneRechnungen_MieterMitZweiLadestationen_BeidePositionenAlsEigeneZeile)
* [x] Eine Position mit **Menge = 0** erzeugt **keine** Rechnungszeile (bestehende Regel). (Test: TarifpositionRepositoryIT#shouldExcludePositionsWithMengeZero; E2E: ladestromtarif.spec.ts › should bill a charging unit whose tenant has no apartment, but not for a zero quantity)
* [x] Die automatische Grundgebühr und die ZEV-/VNB-Zeilen bleiben unverändert. (Test: RechnungServiceTest#berechneRechnung_LadestromZeilen_AfterZevVnbAndBeforeGrundgebuehr; Test: RechnungServiceTest#berechneRechnung_KeineTarifpositionen_RechnungUnveraendert)

### Sicherheit / Mandant
* [x] Erfassen/Ändern/Löschen von Positionen erfordert `rechnungen:manage`, das Anlegen von
      Tarifen `tarife:manage`; ohne diese Permission → `403`. (Test: ControllerAuthorizationTest#updateTarifposition_withMieterReadOnly_forbidden; Test: ControllerAuthorizationTest#getTarife_withMieterReadOnly_forbidden; Test: ControllerAuthorizationTest#createTarif_withRechnungenManageOnly_forbidden; Test: ControllerAuthorizationTest#getTarife_withTarifeManage_reachesController; Test: ControllerAuthorizationTest#getTarife_unauthenticated_unauthorized; Test: ControllerAuthorizationTest#createTarifposition_withMieterReadOnly_forbidden; Test: ControllerAuthorizationTest#deleteTarifposition_withMieterReadOnly_forbidden; Test: ControllerAuthorizationTest#createTarifposition_withRechnungenManage_reachesController)
* [x] Ein Mandant sieht ausschliesslich seine eigenen Tarife und Positionen (`org_id`). (Test: TarifRepositoryIT#shouldNotSeeTarifOfOtherOrgWhenOrgFilterEnabled; Test: TarifpositionRepositoryIT#shouldNotSeeTarifpositionOfOtherOrgWhenOrgFilterEnabled)

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Die Einheiten-Auswahl lädt bei abgewählter Checkbox zusätzlich die Konsumenten-Einheiten — mit
  derselben Abfrage (`getAllEinheiten`), nur ein anderer Filter im Frontend. Keine
  zusätzliche Last.
* Die Rechnungserzeugung nutzt dieselbe indizierte Abfrage über (`einheit_id`, Zeitraum), nur mit
  potenziell mehr Treffern je Einheit.

### NFR-2: Sicherheit
* **Permissions:** Tarife verwalten `tarife:manage` (Fachrollen `org_admin`, `zev_admin`);
  Positionen schreiben `rechnungen:manage`, lesen `mieter:read` (Fachrollen `zev_user`,
  `org_admin`, `zev_admin`). Backend durchgängig `@PreAuthorize("hasAuthority('…')")`, Frontend
  zusätzlich über `AuthGuard`.
* **Multi-Tenancy:** `org_id` an Tarif und Position, `@Filter(name = "orgFilter")`, im Service
  `hibernateFilterService.enableOrgFilter()`. `orgId` stammt **immer** aus dem
  Organisations-Kontext, nie aus dem Request.
* **Validierung serverseitig, nicht nur im Formular:** zulässiger Einheitentyp
  (`LADESTATION` oder `CONSUMER`), zulässiger Tariftyp zum Einheitentyp, Mengeneinheit bei
  `ZUSATZ` gesetzt, `menge ≥ 0`, `quartal ∈ 1..4`, `jahr` plausibel, Eindeutigkeit nach der
  Tabelle in FR-2. Die Checkbox ist **reine Anzeigehilfe** und darf serverseitig nichts erlauben,
  was sonst verboten wäre.

### NFR-3: Kompatibilität
* **Rückwärtskompatibel:** Die Spalte `mengeneinheit` ist nullable; mit `ZUSATZ` wurden
  bestehende Tarife **nicht** migriert. Bestehende Positionen sind nicht betroffen. (Die
  Grundgebühren hat später `V176` auf `MONAT` gesetzt, `Specs/Tarifverwaltung.md`, FR-3.)
* **Rollback:** Die Migration ist additiv (neuer Enum-Wert, neue nullable Spalte). Ein Rückfall
  auf die Vorversion funktioniert, solange keine `ZUSATZ`-Tarife angelegt wurden — danach wären
  deren Positionen für die alte Version unbekannt. Das ist der übliche Vorbehalt bei neuen
  Enum-Werten und kein Sonderfall dieses Features.

## 5. Edge Cases & Fehlerbehandlung

| Fall | Verhalten |
|------|-----------|
| `ZUSATZ`-Tarif ohne Mengeneinheit gespeichert (manipulierter Request) | `400` mit verständlicher Meldung, kein Datensatz |
| Tariftyp von `ZUSATZ` auf einen anderen geändert | Mengeneinheit wird verworfen (`NULL`), beim Wechsel auf `GRUNDGEBUEHR` mit `MONAT` vorbelegt (`Specs/Tarifverwaltung.md`, FR-3); bestehende Positionen behalten ihren Tarif und zeigen künftig die Einheit des neuen Typs |
| Kein `ZUSATZ`-Tarif vorhanden, Checkbox abgewählt, Konsument gewählt | Hinweis „kein erfassbarer Tarif vorhanden" statt leerer Auswahl |
| Keine erfassbare Einheit vorhanden | Hinweistext statt leerer Auswahl, **passend zur Checkbox**: „keine Ladestation erfasst" bzw. „keine erfassbare Einheit erfasst" |
| Quell-Referenz an einer Wohnung | Wird wie bei der Ladestation aus dem `messpunkt` vorbelegt — dort die RFID, hier die Zählernummer. Bleibt frei änderbar |
| Konsumenten-Einheit ohne Mieter im Zeitraum | Position ist erfassbar, erscheint aber auf keiner Rechnung — es gilt derselbe Hinweis wie bei der Ladestation ohne Mieter |
| Mieter mit Wohnung **und** Ladestation, Positionen an beiden | **Eine** Rechnung mit allen Zeilen (bestehende Regel aus `Ladestationen.md`) |
| Menge < 0 | abgewiesen; Menge = 0 speicherbar, erzeugt keine Rechnungszeile |
| Tarif mit Positionen löschen | abgewiesen, Meldung mit Anzahl (bestehende Regel) |
| Einheit mit Positionen löschen | Positionen werden mitgelöscht (`ON DELETE CASCADE`, bestehende Regel) |
| Tarif, dessen Gültigkeit das Quartal der Position nicht berührt (z.B. Q3/2026 mit einem erst 2030 gültigen Tarif) | Position ist erfassbar — es gibt keine Prüfung beim Speichern; die Rechnung weist in diesem Fall das Quartal aus (`RechnungService.zeitraumDerPositionszeile`), weil ein umgekehrter Zeitraum („30.09. – 01.07.") nach einem Fehler aussähe (Hintergrund: Anhang A.5) |
| Leere Liste | Hinweistext statt leerer Tabelle |
| Netzwerkfehler | Meldung über den Message-Bereich, Formulareingaben bleiben erhalten |
| Gleichzeitige Bearbeitung derselben Position | Letzter Schreibvorgang gewinnt (wie im übrigen System; kein optimistisches Sperren) |

## 6. Abhängigkeiten & betroffene Funktionalität

* **Voraussetzungen:** [`Specs/Tarifverwaltung.md`](./Tarifverwaltung.md) (Tarif-CRUD,
  Überschneidungsprüfung), [`Specs/Ladestromtarif.md`](./Ladestromtarif.md) (Tabelle
  `tarifposition`, Erfassungsmaske, Rechnungsintegration),
  [`Specs/Ladestationen.md`](./Ladestationen.md) (Anker an der Einheit, Mehrfachzuordnung),
  [`Specs/Tarifverwaltung.md`](./Tarifverwaltung.md), FR-7 (Grundgebühr; typabhängiges
  Zusatzfeld als Vorlage).
* **Betroffener Code:**
  - Backend: `entity/TarifTyp.java` (Wert + `MANUELL_ERFASST` + `mengeneinheit()`),
    `entity/Tarif.java` (Spalte `mengeneinheit`), `entity/Mengeneinheit.java`,
    `service/TarifService.java` (Überschneidungsprüfung ausnehmen, Pflichtfeld prüfen),
    `service/TarifpositionService.java` (zulässige Einheitentypen, Eindeutigkeit je Typ/Tarif),
    `service/RechnungService.java` (Mengeneinheit aus dem Tarif statt aus dem Typ),
    `repository/TarifpositionRepository.java` (`existsByEinheitAndQuartalAndTarif` für die
    Eindeutigkeit je Tarif), `dto/TarifpositionDTO.java` (Mengeneinheit mitliefern)
  - Frontend: `models/tarif.model.ts`, `models/tarifposition.model.ts`,
    `components/tarif-form`, `components/tarif-list`, `components/tarifposition-list`,
    `components/tarifposition-form`
  - Tests: `TarifServiceTest`, `TarifpositionServiceTest`, `RechnungServiceTest`,
    `TarifControllerTest`, die zugehörigen Frontend-Specs sowie
    `tests/ladestromtarif.spec.ts` — dort wird geprüft, welche Tarife und Einheiten die Maske
    anbietet
* **Datenmigration:** für `ZUSATZ` keine. Die Spalte ist nullable, bestehende Tarife und
  Positionen bleiben unverändert. Übersetzungen für Typ, Mengeneinheiten, Checkbox und
  Fehlermeldungen. Die Grundgebühren setzt `V176` auf `MONAT` (`Specs/Tarifverwaltung.md`, FR-3).

## 7. Abgrenzung / Out of Scope

* **Produzenten-Einheiten** bleiben aussen vor. Ihre Rechnung enthält ausschliesslich
  Grundgebühr-Zeilen (`berechneProduzentenRechnung` ruft die Positionen gar nicht ab); sie
  aufzunehmen wäre eine eigene Änderung an der Rechnungslogik.
* **Bilanz-Einheiten** (`BEZUG`, `RUECKLIEFERUNG`) werden grundsätzlich nicht verrechnet.
* **`GRUNDGEBUEHR` wird nicht (wieder) erfassbar.** Der Versuch scheiterte an der
  Überschneidungsregel und daran, dass jeder gültige Grundgebühr-Tarif automatisch auf jede
  Konsumenten-Rechnung geschrieben wird (`Ladestromtarif.md` FR-6). Der Anwendungsfall wird von
  `ZUSATZ` mit Mengeneinheit *Monat* abgedeckt.
* **`LADESTROM`, `ZEV` und `VNB` werden nicht umgestellt.** Sie behalten ihre abgeleitete
  Mengeneinheit; die Spalte bleibt bei ihnen leer. Eine Vereinheitlichung wäre möglich, brächte
  aber eine Datenmigration ohne fachlichen Gewinn. Die Grundgebühr trägt ihre Einheit in der
  Spalte (`Specs/Tarifverwaltung.md`, FR-3).
* **Kein automatischer Import** von `ZUSATZ`-Mengen; die Erfassung bleibt manuell.
* **Keine Preisstaffelung, keine Rabatte, keine Mengenrabatte** — ein Tarif hat genau einen Preis.
* **Kein Mehrwertsteuer-Ausweis** je Position.
* **Keine gebrochenen Mengen auf der Rechnung.** `RechnungService.berechneTarifpositionsZeilen`
  rundet jede Menge mit `Math.round(...)` auf ganze Zahlen — eingeführt für Ladestrom-kWh, damit
  eine Rechnung keine gemischten Konventionen zeigt. Für `Monat` und `Stück` ist das ohnehin
  richtig; für eine `ZUSATZ`-Menge in kWh mit Nachkommastellen ist der Verlust **bewusst in Kauf
  genommen** (Anhang A.4). Erfassbar bleiben Nachkommastellen — sie wirken sich nur nicht auf den
  Rechnungsbetrag aus.
* **Keine Prüfung beim Speichern, ob die Gültigkeit des Tarifs das Quartal der Position berührt**
  (siehe Edge Cases, Anhang A.5).

## 8. Offene Fragen

* keine — die bisherigen Fragen und ihre Antworten stehen in Anhang A.5 bis A.8.

## Anhang A – Entscheidungen und Verlauf

### A.1 Ausgangslage vor `ZUSATZ`
* `TarifTyp` kannte `ZEV`, `VNB`, `GRUNDGEBUEHR`, `LADESTROM`; manuell erfassbar war allein
  `{ LADESTROM }` (`TarifTyp.MANUELL_ERFASST`). Warum die Grundgebühr **nicht** dazugehört:
  [`Ladestromtarif.md`](./Ladestromtarif.md) FR-6.
* Die Mengeneinheit einer Rechnungszeile war **abgeleitet**: `TarifTyp.mengeneinheit()` lieferte
  `MONAT` für `GRUNDGEBUEHR`, sonst `KWH`. Am Tarif selbst gab es kein Einheiten-Feld.
* `TarifpositionService.resolveEinheit` wies jede Einheit ab, die **nicht** vom Typ
  `LADESTATION` war; die Auswahl auf `/tarifpositionen` zeigte entsprechend nur Ladestationen.
* `TarifService.saveTarif` wies einen zweiten Tarif **desselben Typs** mit überlappender
  Gültigkeit ab (`existsOverlappingTarif`).
* Der CHECK-Constraint `tarif_tariftyp_check` war zuletzt mit `V102` gesetzt worden und musste um
  `ZUSATZ` erweitert werden; an der Tabelle `tarifposition` änderte sich nichts.

### A.2 Einheit beim Preis statt in der Spaltenüberschrift (FR-3)
Die Spaltenüberschrift der Tarif-Liste lautete fest „(CHF/kWh)" und war damit schon für die
Grundgebühr falsch. Statt einer eigenen, bei vier von fünf Typen leeren Spalte steht die Einheit
seither beim Preis je Zeile.

### A.3 Migrationen (FR-2)
Umgesetzt in fünf Migrationen (`V111`–`V115`, Inhalt siehe FR-2). Hinweis aus der Planung: Die
nächste freie Nummer ist **vor** dem Anlegen über den `zev-db`-MCP-Server zu prüfen; eine bereits
ausgeführte Migration wird nie nachträglich geändert.

### A.4 Gebrochene Mengen (Abgrenzung, Entscheid vom 19.08.2026)
Die Rundung jeder Positionsmenge auf ganze Zahlen gilt auch für `ZUSATZ`-Mengen in kWh; der
Verlust der Nachkommastellen auf der Rechnung wurde am 19.08.2026 bewusst in Kauf genommen.

### A.5 Keine Gültigkeitsprüfung beim Speichern (Edge Cases, Entscheid vom 20.08.2026)
Frage: Soll `TarifpositionService.saveTarifposition` prüfen, ob die Gültigkeit des Tarifs das
gewählte Quartal berührt? Geprüft werden Einheitentyp, zulässiger Tariftyp und Eindeutigkeit,
nicht aber die Gültigkeit. Auf der Rechnung fällt eine solche Position nicht auf, weil
`RechnungService.zeitraumDerPositionszeile` dann das Quartal ausweist.
*Entscheid 20.08.2026: vorerst so belassen* — die Kombination entsteht nur durch bewusste
Fehlbedienung. Die Prüfung beim Speichern (`400` bei fehlender Überschneidung) wäre der saubere
Weg und würde die Rückfallregel zur reinen Absicherung machen.

### A.6 Stellung der Checkbox merken? (FR-1.3)
Frage: Soll sich die Seite die Stellung der Checkbox „Nur Ladestationen" merken? *Annahme:*
nein — sie steht bei jedem Seitenaufruf wieder auf „nur Ladestationen", weil das der Normalfall
ist. Der Mehrfachverrechnungs-Hinweis derselben Seite merkt sich sein Wegklicken dagegen in
`localStorage`; das ist bewusst anders, weil es dort um eine einmalige Erklärung geht.

### A.7 Sortierung der Zeilen auf der Rechnung (FR-1.6)
Frage: Sollen mehrere `ZUSATZ`-Zeilen fachlich sortiert werden (z.B. alphabetisch)? Damals
erschienen Positionen nach ZEV/VNB und vor der Grundgebühr, in der Reihenfolge der Abfrage;
*Annahme:* unverändert. Beantwortet durch `Specs/Tarifverwaltung.md`, FR-4 (11.10.2026): Die
Zeilen stehen in der Reihenfolge der Tarife, die der Admin festlegt.

### A.8 Bezeichnungslänge
Frage: `tarif.bezeichnung` war auf **30 Zeichen** begrenzt — reicht das für sprechende
Zusatz-Bezeichnungen („Waschküche Gemeinschaftsraum")? *Annahme:* unverändert; eine Verlängerung
wäre eine eigene, einfache Migration. Beantwortet durch `Specs/RechnungenGenerieren.md`
(10.10.2026): seither 50 Zeichen (`Specs/Tarifverwaltung.md`, Anhang A.2).

### A.9 Abgleich mit der Tarifverwaltung (FR-1.2, FR-2, NFR-3, Abgrenzung, Edge Cases, 18.10.2026)
Bei der Konsolidierung am 18.10.2026 nachgeführt, weil `Specs/Tarifverwaltung.md` (FR-2, FR-3 und
FR-6 vom 10./11.10.2026) Teile dieser Spec abgelöst hat. Vorher stand hier:
* FR-1.2: „Für `ZEV`, `VNB`, `GRUNDGEBUEHR` und `LADESTROM` bleibt die Prüfung unverändert."
  Akzeptanzkriterium: „Zwei `LADESTROM`-Tarife (bzw. `ZEV`/`VNB`/`GRUNDGEBUEHR`) mit
  überlappender Gültigkeit werden weiterhin abgewiesen — die Ausnahme gilt ausschliesslich für
  `ZUSATZ`."
* FR-2: Spalte `mengeneinheit` mit zulässigen Werten `KWH`, `MONAT`, `STUECK`, „Pflicht nur für
  `ZUSATZ` — bei allen anderen Typen bleibt sie `NULL`"; `TarifTyp.mengeneinheit()` als Quelle
  auch für `GRUNDGEBUEHR` (`MONAT`).
* NFR-3, Abschnitt 6 und Abgrenzung: Bestehende Typen — auch `GRUNDGEBUEHR` — werden nicht
  umgestellt und behalten ihre abgeleitete Mengeneinheit; keine Datenmigration.
* Edge Case: Beim Wechsel weg von `ZUSATZ` wird die Mengeneinheit immer `NULL`.

Ergänzt wurde der Verweis auf den Jahrespreis für `ZUSATZ` pro Monat (Tarifverwaltung FR-6).

### A.10 Mengeneinheit nicht mehr nur bei `ZUSATZ` (FR-1.1, FR-3, 18.10.2026)
Vorher hiess es: „Das Feld erscheint **nur** bei diesem Typ" (FR-1.1), „Umschalten auf einen anderen
Typ blendet es wieder aus und verwirft den Wert" (FR-3) und im Akzeptanzkriterium „Das Feld
**Mengeneinheit** ist **nur** bei Typ `ZUSATZ` sichtbar". Seit `Specs/Tarifverwaltung.md`, FR-3
(10.10.2026) zeigt die Tarifmaske das Feld auch bei der Grundgebühr (Monat, Tag). Aufgefallen beim
Akzeptanzkriterien-Check vom 18.10.2026.
