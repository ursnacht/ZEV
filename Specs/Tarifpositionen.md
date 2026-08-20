# Tarifpositionen

> **Verhältnis zu bestehenden Specs.** Die Tabelle `tarifposition`, die Erfassungsmaske und die
> Rechnungsintegration sind in [`Specs/Ladestromtarif.md`](./Ladestromtarif.md) beschrieben, der
> Anker an der Einheit in [`Specs/Ladestationen.md`](./Ladestationen.md). Dieses Dokument
> beschreibt die **Verallgemeinerung**: einen frei konfigurierbaren Tariftyp `ZUSATZ` mit eigener
> Mengeneinheit, der auch für **Konsumenten** erfassbar ist. Es ergänzt die beiden Dokumente und
> ersetzt sie nicht; für alles, was hier nicht steht, gilt weiterhin `Ladestromtarif.md`.

## 1. Ziel & Kontext - Warum wird das Feature benötigt?

* **Was soll erreicht werden:** Ein neuer Tariftyp **`ZUSATZ`**, bei dem neben Bezeichnung und
  Preis auch die **Mengeneinheit** (kWh, Monat, Stück) frei gewählt wird. Positionen dieses Typs
  lassen sich nicht nur an Ladestationen, sondern auch an **Konsumenten-Einheiten** erfassen. Die
  Erfassungsmaske bekommt dafür eine Checkbox, die den Normalfall (nur Ladestationen) vom
  Ausnahmefall trennt.
* **Warum machen wir das:** Bisher ist jede manuell erfasste Menge an einen fest verdrahteten
  Zweck gebunden: `LADESTROM` rechnet kWh, `GRUNDGEBUEHR` rechnet Monate. Wiederkehrende
  Nebenkosten ausserhalb dieses Rasters — Sauna, Waschküche, Gästezimmer, einmalige
  Dienstleistungen — brauchten bisher je einen neuen Tariftyp samt Code- und DDL-Änderung. Mit
  `ZUSATZ` genügt ein Datensatz in der Tarifverwaltung.
* **Aktueller Stand:**
  - `TarifTyp` kennt `ZEV`, `VNB`, `GRUNDGEBUEHR`, `LADESTROM`; manuell erfassbar ist allein
    `{ LADESTROM }` (`TarifTyp.MANUELL_ERFASST`). Warum die Grundgebühr **nicht** dazugehört:
    [`Ladestromtarif.md`](./Ladestromtarif.md) FR-6.
  - Die Mengeneinheit einer Rechnungszeile ist **abgeleitet**: `TarifTyp.mengeneinheit()` liefert
    `MONAT` für `GRUNDGEBUEHR`, sonst `KWH`. Am Tarif selbst gibt es kein Einheiten-Feld.
  - `TarifpositionService.resolveEinheit` weist jede Einheit ab, die **nicht** vom Typ
    `LADESTATION` ist; die Auswahl auf `/tarifpositionen` zeigt entsprechend nur Ladestationen.
  - `TarifService.saveTarif` weist einen zweiten Tarif **desselben Typs** mit überlappender
    Gültigkeit ab (`existsOverlappingTarif`).

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Ablauf / Flow

1. Ein Benutzer legt in der **Tarifverwaltung** einen Tarif vom Typ **`ZUSATZ`** an. Zusätzlich zu
   Bezeichnung, Preis und Gültigkeit wählt er eine **Mengeneinheit**: `kWh`, `Monat` oder `Stück`.
   Das Feld erscheint **nur** bei diesem Typ — analog zum bestehenden Feld
   „Produzent verrechnen", das nur bei `GRUNDGEBUEHR` sichtbar ist.
2. **Mehrere `ZUSATZ`-Tarife dürfen gleichzeitig gültig sein.** Die Überschneidungsprüfung
   greift für diesen Typ **nicht**: Sauna, Waschküche und Gästezimmer sind alle vom Typ `ZUSATZ`
   und müssen nebeneinander bestehen. Für `ZEV`, `VNB`, `GRUNDGEBUEHR` und `LADESTROM` bleibt die
   Prüfung unverändert.
3. Er öffnet die Seite **Tarifpositionen**. Oberhalb der Einheiten-Auswahl steht eine Checkbox
   **„Nur Ladestationen"**, die **standardmässig gesetzt** ist:
   - **gesetzt (Normalfall):** Die Einheiten-Auswahl zeigt ausschliesslich Einheiten vom Typ
     `LADESTATION`; als Tarife stehen `LADESTROM` und `ZUSATZ` zur Wahl.
   - **nicht gesetzt (Ausnahme):** Die Auswahl zeigt zusätzlich die **Konsumenten-Einheiten**.
   Zeigt ein Sprung aus der Einheiten-Verwaltung (`?einheitId=…`) auf einen **Konsumenten**,
   wird die Checkbox **automatisch abgewählt** — sonst landete der Benutzer auf einer Seite ohne
   die Einheit, die er angeklickt hat.
4. Welche Tarife wählbar sind, richtet sich nach dem **Typ der gewählten Einheit**:
   - `LADESTATION` → `LADESTROM`, `ZUSATZ`
   - `CONSUMER` → **nur** `ZUSATZ`
   Begründung: Ladestrom gehört fachlich an eine Ladestation. Für eine Wohnung bleibt damit
   ausschliesslich der frei konfigurierbare Typ — genau der Ausnahmefall, den die Checkbox
   freischaltet.
5. Er erfasst die Position wie bisher: Tarif, Jahr + Quartal, Menge. **Beschriftung und Hinweis
   des Mengenfelds nennen die Einheit des gewählten Tarifs** (kWh / Monate / Stück).
6. Bei der **Rechnungserzeugung** erscheinen `ZUSATZ`-Positionen wie die übrigen Positionen als
   eigene Zeilen — mit der am Tarif hinterlegten Mengeneinheit und dem Quartal der Position,
   **eingeschränkt auf die Gültigkeit des Tarifs** (`Ladestromtarif.md`, FR-3). Für
   Konsumenten-Einheiten greift dabei der bestehende Weg: Die Positionen **aller** Einheiten
   eines Mieters landen auf dessen Rechnung.

### FR-2: Persistierung

* **Neuer Enum-Wert `TarifTyp.ZUSATZ`.** Die Spalte `zev.tarif.tariftyp` ist `VARCHAR(20)`
  (`EnumType.STRING`) und trägt den CHECK-Constraint `tarif_tariftyp_check`, der die erlaubten
  Werte **explizit aufzählt** (zuletzt `V102`). Er **muss** um `ZUSATZ` erweitert werden — sonst
  scheitert das Anlegen mit `DataIntegrityViolationException`. Die Spaltenlänge genügt.
  > Achtung: `Tarif.tariftyp` trägt im Code `@Column(length = 10)`, die Datenbank hat aber
  > `VARCHAR(20)`. `ZUSATZ` passt in beides; die Abweichung ist hier nur zu beachten, falls
  > später ein längerer Wert dazukommt.
* **Neue Spalte `zev.tarif.mengeneinheit VARCHAR(10)`, nullable.** Zulässige Werte `KWH`,
  `MONAT`, `STUECK`; CHECK-Constraint entsprechend. **Pflicht nur für `ZUSATZ`** — bei allen
  anderen Typen bleibt sie `NULL`, weil deren Einheit aus dem Typ folgt. Die Pflicht wird im
  **Service** geprüft (nicht als DB-Constraint), damit die Regel an einer Stelle steht.
* **Ableitung der Mengeneinheit:** `TarifTyp.mengeneinheit()` bleibt die Quelle für
  `ZEV`/`VNB`/`LADESTROM` (`KWH`) und `GRUNDGEBUEHR` (`MONAT`). Für `ZUSATZ` gilt der Wert aus der
  neuen Spalte. Bestehende Tarife werden **nicht** migriert.
* **`MANUELL_ERFASST`** wird zu `{ LADESTROM, ZUSATZ }`.
* **Eindeutigkeit einer Position — typabhängig:**
  | Tariftyp | Regel |
  |----------|-------|
  | `LADESTROM` | höchstens **eine** Position je Einheit, Quartal und **Tariftyp** (unverändert) |
  | `ZUSATZ` | höchstens **eine** Position je Einheit, Quartal und **Tarif** |
  Für `ZUSATZ` je Typ zu prüfen wäre sinnlos: Man könnte dann pro Quartal nur eine einzige
  Zusatzposition erfassen. Der bestehende DB-Constraint
  `UNIQUE (org_id, einheit_id, tarif_id, jahr, quartal)` deckt die `ZUSATZ`-Regel bereits ab —
  **keine DDL-Änderung an `tarifposition` nötig**.
* **Keine Änderung an der Tabelle `tarifposition`.** Menge, Erfassungsart, Quell-Referenz und
  Bemerkung bleiben wie sie sind; die Bedeutung steckt weiterhin ausschliesslich im Tarif.
* **Flyway:** Umgesetzt in fünf Migrationen — `V111` (CHECK-Constraint, Spalte `mengeneinheit`,
  Übersetzungen), `V112` (Platzhalter der Einheiten-Auswahl), `V113` (Schlüssel-Namensraum
  korrigiert: `TARIFTYP_*` sind Tariftypen, `TYP_*` Einheitentypen), `V114` (Preis-Hinweis
  einheitneutral), `V115` (Hinweis bei leerer Einheiten-Auswahl). Die nächste freie Nummer ist **vor** dem Anlegen über den `zev-db`-MCP-Server
  zu prüfen; eine bereits ausgeführte Migration wird nie nachträglich geändert.

### FR-3: Layout

* **Tarifverwaltung**
  - `ZUSATZ` erscheint im bestehenden Typ-Dropdown.
  - Bei ausgewähltem `ZUSATZ` erscheint ein zusätzliches Dropdown **Mengeneinheit**
    (kWh / Monat / Stück) mit Hinweistext. Umschalten auf einen anderen Typ blendet es wieder aus
    und verwirft den Wert. Vorlage: der bestehende, nur bei `GRUNDGEBUEHR` sichtbare Block
    „Produzent verrechnen" in `tarif-form.component.html`.
  - **Preis-Label und -Hinweis folgen der Einheit:** `Preis (CHF/kWh)`, `(CHF/Monat)`,
    `(CHF/Stück)` — und nur `(CHF)`, solange ein Zusatz-Tarif noch keine Einheit hat. Der
    Hinweistext darunter nennt deshalb nur noch das Zahlenformat statt „pro kWh".
  - Die Tarif-Liste zeigt die Einheit **beim Preis** je Zeile (`5.00 / Stück`) statt in der
    Spaltenüberschrift. Die lautete bisher fest „(CHF/kWh)" und war damit schon für die
    Grundgebühr falsch; eine eigene, bei vier von fünf Typen leere Spalte wäre der schlechtere
    Tausch.
* **Tarifpositionen**
  - **Checkbox „Nur Ladestationen"** oberhalb der Einheiten-Auswahl, Design-System-Klassen
    `.zev-checkbox-item` + `.zev-checkbox` (Vorlage: `tarif-form`), mit Hinweistext, der den
    Ausnahmecharakter benennt. Default **gesetzt**.
  - Wird die Checkbox **wieder gesetzt**, während eine Konsumenten-Einheit gewählt ist, wird die
    Auswahl **zurückgesetzt** (kein Zustand, in dem eine nicht mehr angebotene Einheit selektiert
    bleibt).
  - Die Einheiten-Auswahl nennt bei gemischter Liste den **Typ** je Eintrag, damit Wohnung und
    Ladestation unterscheidbar sind.
  - Liste und Formular bleiben im Übrigen unverändert; die Anzeige der Mengeneinheit **je Zeile**
    und im Mengen-Label existiert bereits.
* **Rechnung (Web + PDF):** keine strukturelle Änderung — es kommen nur Zeilen dazu. Die
  `mengeneinheit` der Zeile stammt aus dem Tarif.
* Alle Texte via `TranslationService`/`TranslatePipe`; Zahlenformatierung nach
  `Specs/generell.md`.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

### Tarif
* [ ] In der Tarifverwaltung lässt sich ein Tarif vom Typ `ZUSATZ` mit Mengeneinheit `kWh`,
      `Monat` oder `Stück` anlegen, bearbeiten und löschen.
* [ ] Das Feld **Mengeneinheit** ist **nur** bei Typ `ZUSATZ` sichtbar und dort **Pflicht**;
      ohne Auswahl ist das Speichern nicht möglich (Serverseitig `400`, Formular deaktiviert).
* [ ] **Zwei `ZUSATZ`-Tarife mit überlappender Gültigkeit lassen sich beide speichern.**
* [ ] Zwei `LADESTROM`-Tarife (bzw. `ZEV`/`VNB`/`GRUNDGEBUEHR`) mit überlappender Gültigkeit
      werden weiterhin **abgewiesen** — die Ausnahme gilt ausschliesslich für `ZUSATZ`.
* [ ] Ein `ZUSATZ`-Tarif, auf den Positionen verweisen, lässt sich **nicht** löschen (bestehende
      Regel, Meldung mit Anzahl).

### Erfassung
* [ ] Beim Öffnen der Seite Tarifpositionen ist die Checkbox „Nur Ladestationen" **gesetzt** und
      die Einheiten-Auswahl enthält **ausschliesslich** Ladestationen.
* [ ] Nach dem Abwählen der Checkbox enthält die Auswahl zusätzlich die **Konsumenten**-Einheiten;
      Produzenten und Bilanz-Einheiten (Bezug/Rücklieferung) erscheinen **nie**.
* [ ] Bei gewählter **Konsumenten**-Einheit sind ausschliesslich `ZUSATZ`-Tarife wählbar;
      `LADESTROM` erscheint dort **nicht**.
* [ ] Bei gewählter **Ladestations**-Einheit sind `LADESTROM` und `ZUSATZ` wählbar,
      `GRUNDGEBUEHR` dagegen nicht.
* [ ] Wird die Checkbox bei gewählter Konsumenten-Einheit wieder gesetzt, ist **keine** Einheit
      mehr ausgewählt und die Positionsliste ist leer.
* [ ] Beschriftung und Hinweis des Mengenfelds nennen die Mengeneinheit des gewählten Tarifs
      (`kWh` / `Monate` / `Stück`).
* [ ] Für dieselbe Einheit und dasselbe Quartal lassen sich **mehrere `ZUSATZ`-Positionen mit
      verschiedenen Tarifen** erfassen (z.B. Sauna und Waschküche).
* [ ] Eine **zweite Position mit demselben `ZUSATZ`-Tarif** für dieselbe Einheit und dasselbe
      Quartal wird **abgewiesen** (Meldung, kein Datensatz).
* [ ] Das Erfassen einer Position an einer Einheit vom Typ `PRODUCER`, `BEZUG` oder
      `RUECKLIEFERUNG` wird serverseitig **abgewiesen** (`400`), auch bei manipuliertem Request.

### Rechnung
* [ ] Eine `ZUSATZ`-Position an einer Konsumenten-Einheit erscheint auf der Rechnung des Mieters
      als eigene Zeile mit der **am Tarif hinterlegten Mengeneinheit**.
* [ ] Mehrere `ZUSATZ`-Positionen desselben Quartals erscheinen als **mehrere Zeilen**.
* [ ] Eine Position mit **Menge = 0** erzeugt **keine** Rechnungszeile (bestehende Regel).
* [ ] Die automatische Grundgebühr und die ZEV-/VNB-Zeilen bleiben unverändert.

### Sicherheit / Mandant
* [ ] Erfassen/Ändern/Löschen von Positionen erfordert `rechnungen:manage`, das Anlegen von
      Tarifen `tarife:manage`; ohne diese Permission → `403`.
* [ ] Ein Mandant sieht ausschliesslich seine eigenen Tarife und Positionen (`org_id`).

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Die Einheiten-Auswahl lädt bei abgewählter Checkbox zusätzlich die Konsumenten-Einheiten — eine
  bereits vorhandene Abfrage (`getAllEinheiten`), nur ein anderer Filter im Frontend. Keine
  zusätzliche Last.
* Die Rechnungserzeugung bleibt unverändert: dieselbe indizierte Abfrage über
  (`einheit_id`, Zeitraum), nur mit potenziell mehr Treffern je Einheit.

### NFR-2: Sicherheit
* **Permissions:** Tarife verwalten `tarife:manage` (Fachrollen `org_admin`, `zev_admin`);
  Positionen schreiben `rechnungen:manage`, lesen `mieter:read` (Fachrollen `zev_user`,
  `org_admin`, `zev_admin`) — unverändert gegenüber heute. Backend durchgängig
  `@PreAuthorize("hasAuthority('…')")`, Frontend zusätzlich über `AuthGuard`.
* **Multi-Tenancy:** `org_id` an Tarif und Position, `@Filter(name = "orgFilter")`, im Service
  `hibernateFilterService.enableOrgFilter()`. `orgId` stammt **immer** aus dem
  Organisations-Kontext, nie aus dem Request.
* **Validierung serverseitig, nicht nur im Formular:** zulässiger Einheitentyp
  (`LADESTATION` oder `CONSUMER`), zulässiger Tariftyp zum Einheitentyp, Mengeneinheit bei
  `ZUSATZ` gesetzt, `menge ≥ 0`, `quartal ∈ 1..4`, `jahr` plausibel, Eindeutigkeit nach der
  Tabelle in FR-2. Die Checkbox ist **reine Anzeigehilfe** und darf serverseitig nichts erlauben,
  was sonst verboten wäre.

### NFR-3: Kompatibilität
* **Rückwärtskompatibel:** Die neue Spalte ist nullable, bestehende Tarife bleiben unverändert
  und behalten ihre abgeleitete Mengeneinheit. Bestehende Positionen sind nicht betroffen.
* **Rollback:** Die Migration ist additiv (neuer Enum-Wert, neue nullable Spalte). Ein Rückfall
  auf die Vorversion funktioniert, solange keine `ZUSATZ`-Tarife angelegt wurden — danach wären
  deren Positionen für die alte Version unbekannt. Das ist der übliche Vorbehalt bei neuen
  Enum-Werten und kein Sonderfall dieses Features.

## 5. Edge Cases & Fehlerbehandlung

| Fall | Verhalten |
|------|-----------|
| `ZUSATZ`-Tarif ohne Mengeneinheit gespeichert (manipulierter Request) | `400` mit verständlicher Meldung, kein Datensatz |
| Tariftyp von `ZUSATZ` auf einen anderen geändert | Mengeneinheit wird verworfen (`NULL`); bestehende Positionen behalten ihren Tarif und zeigen künftig die abgeleitete Einheit |
| Kein `ZUSATZ`-Tarif vorhanden, Checkbox abgewählt, Konsument gewählt | Hinweis „kein erfassbarer Tarif vorhanden" statt leerer Auswahl |
| Keine erfassbare Einheit vorhanden | Hinweistext statt leerer Auswahl, **passend zur Checkbox**: „keine Ladestation erfasst" bzw. „keine erfassbare Einheit erfasst" |
| Quell-Referenz an einer Wohnung | Wird wie bei der Ladestation aus dem `messpunkt` vorbelegt — dort die RFID, hier die Zählernummer. Bleibt frei änderbar |
| Konsumenten-Einheit ohne Mieter im Zeitraum | Position ist erfassbar, erscheint aber auf keiner Rechnung — es gilt derselbe Hinweis wie bei der Ladestation ohne Mieter |
| Mieter mit Wohnung **und** Ladestation, Positionen an beiden | **Eine** Rechnung mit allen Zeilen (bestehende Regel aus `Ladestationen.md`) |
| Menge < 0 | abgewiesen; Menge = 0 speicherbar, erzeugt keine Rechnungszeile |
| Tarif mit Positionen löschen | abgewiesen, Meldung mit Anzahl (bestehende Regel) |
| Einheit mit Positionen löschen | Positionen werden mitgelöscht (`ON DELETE CASCADE`, bestehende Regel) |
| Leere Liste | Hinweistext statt leerer Tabelle |
| Netzwerkfehler | Meldung über den Message-Bereich, Formulareingaben bleiben erhalten |
| Gleichzeitige Bearbeitung derselben Position | Letzter Schreibvorgang gewinnt (wie im übrigen System; kein optimistisches Sperren) |

## 6. Abhängigkeiten & betroffene Funktionalität

* **Voraussetzungen:** [`Specs/Tarifverwaltung.md`](./Tarifverwaltung.md) (Tarif-CRUD,
  Überschneidungsprüfung), [`Specs/Ladestromtarif.md`](./Ladestromtarif.md) (Tabelle
  `tarifposition`, Erfassungsmaske, Rechnungsintegration),
  [`Specs/Ladestationen.md`](./Ladestationen.md) (Anker an der Einheit, Mehrfachzuordnung),
  [`Specs/Tarifverwaltung-Grundgebuehr.md`](./Tarifverwaltung-Grundgebuehr.md) (typabhängiges
  Zusatzfeld als Vorlage).
* **Betroffener Code:**
  - Backend: `entity/TarifTyp.java` (Wert + `MANUELL_ERFASST` + `mengeneinheit()`),
    `entity/Tarif.java` (Spalte `mengeneinheit`), `entity/Mengeneinheit.java` *(neu)*,
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
* **Datenmigration:** keine. Neue Spalte nullable, bestehende Tarife und Positionen bleiben
  unverändert. Neue Übersetzungen für Typ, Mengeneinheiten, Checkbox und Fehlermeldungen.

## 7. Abgrenzung / Out of Scope

* **Produzenten-Einheiten** bleiben aussen vor. Ihre Rechnung enthält heute ausschliesslich
  Grundgebühr-Zeilen (`berechneProduzentenRechnung` ruft die Positionen gar nicht ab); sie
  aufzunehmen wäre eine eigene Änderung an der Rechnungslogik.
* **Bilanz-Einheiten** (`BEZUG`, `RUECKLIEFERUNG`) werden grundsätzlich nicht verrechnet.
* **`GRUNDGEBUEHR` wird nicht (wieder) erfassbar.** Der Versuch scheiterte an der
  Überschneidungsregel und daran, dass jeder gültige Grundgebühr-Tarif automatisch auf jede
  Konsumenten-Rechnung geschrieben wird (`Ladestromtarif.md` FR-6). Der Anwendungsfall wird von
  `ZUSATZ` mit Mengeneinheit *Monat* abgedeckt.
* **Bestehende Typen werden nicht umgestellt.** `LADESTROM` und `GRUNDGEBUEHR` behalten ihre
  abgeleitete Mengeneinheit; die neue Spalte bleibt bei ihnen leer. Eine Vereinheitlichung wäre
  möglich, brächte aber eine Datenmigration ohne fachlichen Gewinn.
* **Kein automatischer Import** von `ZUSATZ`-Mengen; die Erfassung bleibt manuell.
* **Keine Preisstaffelung, keine Rabatte, keine Mengenrabatte** — ein Tarif hat genau einen Preis.
* **Kein Mehrwertsteuer-Ausweis** je Position.
* **Keine gebrochenen Mengen auf der Rechnung.** `RechnungService.berechneTarifpositionsZeilen`
  rundet jede Menge mit `Math.round(...)` auf ganze Zahlen — eingeführt für Ladestrom-kWh, damit
  eine Rechnung keine gemischten Konventionen zeigt. Für `Monat` und `Stück` ist das ohnehin
  richtig; für eine `ZUSATZ`-Menge in kWh mit Nachkommastellen ist der Verlust **bewusst in Kauf
  genommen** (entschieden 19.08.2026). Erfassbar bleiben Nachkommastellen — sie wirken sich nur
  nicht auf den Rechnungsbetrag aus.

## 8. Offene Fragen

* **Keine Prüfung, ob der Tarif im Quartal der Position gültig ist.**
  `TarifpositionService.saveTarifposition` prüft Einheitentyp, zulässigen Tariftyp und
  Eindeutigkeit — nicht aber, ob die Gültigkeit des Tarifs das gewählte Quartal überhaupt
  berührt. Eine Position für Q3/2026 mit einem erst 2030 gültigen Tarif ist damit erfassbar.
  Auf der Rechnung fällt das nicht auf: `RechnungService.zeitraumDerPositionszeile` weist in
  diesem Fall das Quartal aus, weil ein umgekehrter Zeitraum („30.09. – 01.07.") nach einem
  Fehler aussähe. *Entscheid 20.08.2026: vorerst so belassen* — die Kombination entsteht nur
  durch bewusste Fehlbedienung. Die Prüfung beim Speichern (`400` bei fehlender Überschneidung)
  wäre der saubere Weg und würde die Rückfallregel zur reinen Absicherung machen.
* **Stellung der Checkbox merken?** *Annahme:* nein — sie steht bei jedem Seitenaufruf wieder auf
  „nur Ladestationen", weil das der Normalfall ist. (Der Mehrfachverrechnungs-Hinweis derselben
  Seite merkt sich sein Wegklicken dagegen in `localStorage`; das ist bewusst anders, weil es dort
  um eine einmalige Erklärung geht.)
* **Sortierung der Zeilen auf der Rechnung:** Positionen erscheinen heute nach ZEV/VNB und vor der
  Grundgebühr, in der Reihenfolge der Abfrage. *Annahme:* unverändert. Ob mehrere
  `ZUSATZ`-Zeilen fachlich sortiert werden sollen (z.B. alphabetisch), ist offen.
* **Bezeichnungslänge:** `tarif.bezeichnung` ist auf **30 Zeichen** begrenzt. Für sprechende
  Zusatz-Bezeichnungen („Waschküche Gemeinschaftsraum") könnte das knapp werden. *Annahme:*
  bleibt unverändert; eine Verlängerung wäre eine eigene, einfache Migration.
