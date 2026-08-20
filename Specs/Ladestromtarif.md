# Ladestromtarif

> **Stand:** Der **Anker der Tarifposition ist seit [`Specs/Ladestationen.md`](./Ladestationen.md)
> die Einheit**, nicht mehr der Mieter: Jede Ladestation ist eine eigene Einheit vom Typ
> `LADESTATION`, deren `messpunkt` die RFID trägt, und ein Mieter kann mehreren Einheiten
> zugeordnet sein. Dieses Dokument bleibt massgebend für **Tariftyp, Tabelle `tarifposition`,
> Erfassungsmaske und Rechnungsintegration**; wo es um die Zuordnung geht, gilt `Ladestationen.md`.
> Die Stellen sind unten entsprechend nachgezogen.

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Der Strom für das Laden von Fahrzeugen soll den Mietern zu einem **eigenen Tarif** verrechnet werden — getrennt von ZEV- und VNB-Anteil. Dazu wird **je Ladestations-Einheit** eine Tarifposition erfasst (Tarif, Quartal, Menge in kWh), die bei der Rechnungserzeugung automatisch als zusätzliche Zeile erscheint. (Ursprünglich je Mieter — siehe Stand-Hinweis.)
* **Warum machen wir das:** Ladestrom ist ein abgrenzbarer, oft deutlich grösserer Verbrauch, der über eine **eigene Ladeinfrastruktur mit eigenem Messpunkt** läuft. Die Weiterverrechnung an den einzelnen Mieter braucht deshalb einen eigenen Preis und eine eigene Zeile auf der Rechnung — die Zuordnung „welcher Mieter hat wie viel geladen" kommt nicht aus den ZEV-Messwerten, sondern aus dem Lademanagement.
* **Aktueller Stand:**
  - `Tarif` kennt die Typen `ZEV`, `VNB`, `GRUNDGEBUEHR` (`TarifTyp`), jeweils mit Bezeichnung, Preis und Gültigkeitszeitraum (`Specs/Tarifverwaltung.md`).
  - `RechnungService` erzeugt die Rechnung **on the fly** als `RechnungDTO` mit `TarifZeileDTO`-Zeilen (ZEV, VNB, optional Grundgebühr); Rechnungen werden **nicht** persistiert. Eine Rechnung gilt je **Einheit + Mieter + Zeitraum**.
  - Es gibt keinerlei Möglichkeit, manuelle Mengen zu erfassen oder zusätzliche Positionen auf eine Rechnung zu bringen.

### Getroffene Grundsatzentscheide
| Thema | Entscheid |
|---|---|
| **Solarverteilung** | Ladestationen werden **nicht** gesondert behandelt — der Ladestrom-Messpunkt ist eine normale `CONSUMER`-Einheit und nimmt regulär an der Verteilung teil. Keine Priorisierung. |
| **Steuerung** | PV-Überschuss-/dynamisches Laden übernimmt die **Ladeinfrastruktur**, nicht ZEV. ZEV rechnet nur ab. |
| **Messpunkt-Rechnung** | Der Ladestrom-Messpunkt wird wie jede Einheit abgerechnet und ist einem **Mieter** zugeordnet (der Eigentümer wird dafür als Mieter-Datensatz geführt). Die Mieter zahlen ihren Ladestrom über die Tarifpositionen; die Differenz zwischen Ladestromtarif und tatsächlichen ZEV-/VNB-Kosten bleibt beim Eigentümer. |
| **Zuordnung der Menge** | Über die **Ladestations-Einheit** und deren Mieter (`Ladestationen.md`). Ein Nutzer ohne Wohnung wird über seine Ladestation abgerechnet. |
| **Anker der Position** | An der **Einheit** vom Typ `LADESTATION` (`Ladestationen.md`). Ein **Mieterwechsel innerhalb eines Quartals** bleibt eindeutig, weil dabei die RFID invalidiert und eine neue Einheit angelegt wird — jede Einheit gehört über ihre Lebensdauer genau einem Nutzer. *(Ursprünglich am Mieter.)* |
| **Tarif-Typisierung** | Neuer `TarifTyp.LADESTROM`, verwaltet in der bestehenden Tarifverwaltung. **Bewusst ein spezifischer Typ und kein generisches `ZUSATZ`:** Der spätere automatische Import aus dem Lademanagement muss die bezogenen Mengen einem Tarif zuordnen können — das geht über den **Typ** maschinell zuverlässig, über eine Bezeichnung nicht. |
| **Datenhaltung** | **Generische** Tabelle `tarifposition` für manuell erfasste Mengen zu einem beliebigen Tarif — nicht ladestrom-spezifisch. Weitere Anwendungsfälle (Sauna, Waschküche, …) kommen ohne Schema-Änderung dazu. |
| **Genau ein gültiger Tarif** | Je Zeitraum darf **höchstens ein** `LADESTROM`-Tarif gültig sein, und je Einheit und Quartal existiert **höchstens eine** Ladestrom-Position. Damit ist die Zuordnung beim späteren Import eindeutig. |
| **Ladepunkt-Zuordnung** | Kein Stammdatenfeld am Mieter — ein Attribut kann genau einen Wert halten, ein Nutzer aber mehrere Ladestationen haben. Die Kennung steht am `messpunkt` der Ladestations-Einheit (`Ladestationen.md`); die Herkunft je Position halten **Erfassungsart** (manuell/importiert) und **Quell-Referenz** fest. |
| **Mehrere Ladestationen** | Hinter einem Ladestrom-Messpunkt dürfen beliebig viele Ladestationen stehen — für ZEV ist nur die Summe am Messpunkt relevant. |

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Ablauf / Flow
1. Ein Benutzer legt in der **Tarifverwaltung** einen Tarif vom Typ **`LADESTROM`** an (Bezeichnung, Preis in CHF/kWh, Gültigkeit von/bis) — analog zu ZEV/VNB. Überschneidet sich die Gültigkeit mit einem bestehenden `LADESTROM`-Tarif, wird das **abgewiesen** (FR-2).
2. Er öffnet die **Einheiten-Verwaltung** und wählt bei der Ladestation im Kebab-Menü **„Tarifpositionen"**. *(Ursprünglich beim Mieter.)*
3. Es erscheint die Liste der erfassten Positionen dieses Mieters (Tarif, Quartal, Menge, Betrag) mit *Anlegen*, *Bearbeiten* und *Löschen*.
4. Beim Anlegen wählt er: **Tarif** (Auswahl aus den `LADESTROM`-Tarifen), **Jahr + Quartal** (zwei einfache Dropdowns) und die **Menge in kWh**. Existiert für diesen Mieter, dieses Quartal und diesen **Tariftyp** bereits eine Position, wird das abgewiesen — die bestehende ist zu bearbeiten.
5. Bei der **Rechnungserzeugung** für Einheit + Mieter + Zeitraum werden automatisch alle Positionen **dieses Mieters** aufgenommen, deren Quartal sich mit dem Rechnungszeitraum **überschneidet** und deren **Menge > 0** ist.
6. Jede aufgenommene Position erscheint als **eigene Tarifzeile** (Bezeichnung des Tarifs, Quartalsgrenzen als Von/Bis, Menge in kWh, Preis, Betrag) und fliesst in Total, Rundung und Endbetrag ein. Ist an der Position eine **Quell-Referenz** erfasst, steht sie in der Bezeichnung in Klammern dahinter (`Ladestrom (LP-01)`) — damit ist auf der Rechnung nachvollziehbar, aus welchem Ladepunkt die Menge stammt. Ohne Quell-Referenz bleibt die Bezeichnung unverändert. Menge und Betrag werden **wie die bestehenden ZEV-/VNB-Zeilen** gerundet dargestellt; gespeichert bleibt die Menge mit drei Nachkommastellen.

> **Warum Überschneidung und nicht „Quartal vollständig im Zeitraum"?** Zieht ein Mieter Mitte Quartal aus, deckt **seine** Rechnung nur einen Teil des Quartals ab. Bei der strengeren Regel würde seine Position nie verrechnet. Doppelverrechnung entsteht dadurch nicht, weil eine Ladestations-Einheit über ihre Lebensdauer genau einem Nutzer gehört (beim Wechsel entsteht eine neue Einheit mit neuer RFID, `Ladestationen.md`) — offen bleibt nur der allgemeine Fall zweier überlappender Rechnungsläufe für denselben Mieter (§8).

### FR-2: Persistierung
* Neue Tabelle **`tarifposition`** — **bewusst generisch**, nicht ladestrom-spezifisch. Sie nimmt **manuell erfasste Mengen zu einem beliebigen Tarif** auf; Ladestrom ist der erste Anwendungsfall, weitere (Sauna, Waschküche, Wärmepumpe …) kommen ohne Schema-Änderung dazu. Die fachliche Bedeutung steckt ausschliesslich im referenzierten Tarif.
* Multi-Tenancy: `org_id` + Hibernate-`@Filter`, wie alle Entities.

  | Spalte | Typ | Pflicht | Bemerkung |
  |---|---|---|---|
  | `id` | BIGINT | ja | Sequenz `zev.tarifposition_seq` |
  | `org_id` | BIGINT | ja | Mandant |
  | `einheit_id` | BIGINT | ja | FK auf `einheit`, **ON DELETE CASCADE** — **Anker der Position** (bis `V106` war es `mieter_id`, siehe Stand-Hinweis) |
  | `tarif_id` | BIGINT | ja | FK auf `tarif`, **ON DELETE RESTRICT** (ein referenzierter Tarif ist nicht löschbar) — der Typ des Tarifs bestimmt die Bedeutung der Position |
  | `jahr` | INT | ja | z.B. 2026 |
  | `quartal` | INT | ja | 1–4 |
  | `menge` | NUMERIC(12,3) | ja | ≥ 0. **Nicht** `menge_kwh`: die Einheit ergibt sich aus dem Tarif, damit später auch nicht-kWh-Positionen möglich sind |
  | `erfassungsart` | VARCHAR(20) | ja | `MANUELL` (Default) oder `IMPORT` — Herkunft der Menge. **Nicht** `quelle`: `ch.nacht.entity.Quelle` existiert bereits (CSV/MQTT/API, `Messwerte.quelle`). Neues Enum `Erfassungsart`, `EnumType.STRING`, `length = 20` wie die bestehende Konvention |
  | `quell_referenz` | VARCHAR(64) | nein | Kennung, aus der die Menge stammt (bei Import die Ladepunkt-Kennung); bei manueller Erfassung frei erfassbar |
  | `bemerkung` | VARCHAR(200) | nein | freier Text, rein informativ |

* **Index:** auf (`org_id`, `einheit_id`, `jahr`, `quartal`) — deckt sowohl die Abfrage bei der Rechnungserzeugung als auch die Eindeutigkeitsprüfung ab.
* **Eindeutigkeit je Tariftyp:** Pro (`org_id`, `einheit_id`, `jahr`, `quartal`, **Tariftyp**) ist **höchstens eine** Position zulässig. Da der Typ am Tarif hängt und nicht in der Tabelle steht, wird die Regel **im Service** geprüft; die Datenbank sichert zusätzlich UNIQUE (`org_id`, `einheit_id`, `tarif_id`, `jahr`, `quartal`) als Netz gegen exakte Duplikate.
* **Kein neues Feld am Mieter.** Ein `ladepunkt`-Attribut war in einer fruehen Fassung vorgesehen und wurde mit `V103` wieder entfernt: Es kann genau einen Wert halten, ein Nutzer kann aber mehrere Ladestationen verwenden — und ein Ladestations-Nutzer ist nicht zwingend Mieter einer Wohnung. Strukturell geloest ist die Zuordnung in [`Specs/Ladestationen.md`](./Ladestationen.md).
* **Herkunft je Position:** `erfassungsart` unterscheidet manuell erfasste von importierten Mengen, `quell_referenz` haelt fest, woher eine importierte Menge stammt. Damit ist spaeter belegbar, warum eine Menge auf der Rechnung steht — bei manuell erfassten Betraegen, die direkt Geld bewegen, ist das der eigentliche Zweck.
* **Höchstens ein gültiger `LADESTROM`-Tarif je Zeitraum:** Diese Regel gilt **automatisch, ohne neuen Code** — `TarifService.saveTarif` prüft bereits typbezogen auf Überschneidung (`existsOverlappingTarif(tariftyp, …)`) und weist überlappende Tarife für **jeden** Typ ab. Mit dem neuen Enum-Wert greift die Prüfung ohne Zutun auch für `LADESTROM`. Damit ist der Tarif für ein Quartal eindeutig bestimmbar — Voraussetzung für den späteren automatischen Import. Das zugehörige Akzeptanzkriterium dient der **Regressionsabsicherung**, nicht der Neuentwicklung.
* **`TarifTyp`** wird um den Wert `LADESTROM` erweitert. Die Spalte `tarif.tariftyp` ist zwar `VARCHAR(20)`/`EnumType.STRING` (keine Typänderung nötig), trägt aber den CHECK-Constraint `tarif_tariftyp_check`, der die erlaubten Werte **explizit aufzählt** (zuletzt gesetzt in `V50`). Er **muss** um `LADESTROM` erweitert werden, sonst scheitert das Anlegen eines Ladestromtarifs mit `DataIntegrityViolationException`.
* **Zulässige Tariftypen für Positionen:** Die Prüfung erfolgt gegen eine **Menge manuell erfasster Typen** (`{ LADESTROM }`, erweitert um `ZUSATZ`), nicht gegen einen einzelnen Wert. Ein weiterer Anwendungsfall erweitert nur diese Menge — Tabelle, Service und UI bleiben unverändert. `ZEV`, `VNB` und `GRUNDGEBUEHR` sind ausgeschlossen (Begründung für die Grundgebühr: FR-6).
* Rechnungen bleiben **unpersistiert**; die Tarifpositionen sind die einzige neue dauerhafte Datenhaltung.

### FR-3: Layout
* **Tarifverwaltung:** Der Typ `LADESTROM` erscheint im bestehenden Typ-Dropdown; bei überlappender Gültigkeit erscheint eine verständliche Fehlermeldung. Sonst keine Änderung an der Maske.
* **Tarifpositionen** (neue, generische Ansicht, erreichbar über das Kebab-Menü der **Einheit**):
  - **Liste** (Design-System-`table`): Tarif-Bezeichnung, Jahr/Quartal, Menge (rechtsbündig `.zev-table__number`; Mengeneinheit **je Zeile** aus dem Tarif — bei Ladestrom kWh), Preis, Betrag, Kebab-Menü mit *Bearbeiten* / *Kopieren* / *Löschen*.
  - **Kopieren** (analog Tarifverwaltung): öffnet das Formular mit allen Werten der Position, aber **ohne ID** — gespeichert wird eine neue Position. Jahr und Quartal werden **nicht** automatisch weitergeschaltet; da je Einheit, Quartal und Tariftyp nur **eine** Position zulässig ist (FR-1.3), muss der Zeitraum bewusst gewählt werden. Eine unverändert gespeicherte Kopie wird mit der Duplikat-Meldung abgewiesen — das ist die gewollte Absicherung, kein Fehler.
  - Die Liste verhält sich wie die übrigen Verwaltungstabellen (Tarife, Einheiten, Mieter): **sortierbar** über alle Datenspalten (`.zev-table__header--sortable` + `.zev-table__sort-indicator`) und mit **veränderbarer Spaltenbreite** (`appColumnResize`). Die Spalte Quartal sortiert nach Jahr **und** Quartal zusammen, die Spalte Betrag nach dem berechneten Wert. Eine gewählte Sortierung bleibt nach Erfassen, Ändern und Löschen erhalten.
  - Die Liste weist die **Herkunft** aus (manuell/importiert); importierte Positionen sind als solche erkennbar.
  - **Formular** (Design-System-`form`): Tarif (Dropdown), **Jahr** und **Quartal** als je ein Dropdown, Menge, Bemerkung; Speichern/Abbrechen.
    Bewusst **nicht** die `QuarterSelectorComponent`: Sie arbeitet mit Datumsbereichen (`@Input selectedVon/selectedBis`, `@Output {von, bis}`), die Position speichert aber `jahr` + `quartal` — eine Hin- und Rueckrechnung waere unnoetiger Umweg. Die Quell-Referenz wird frei erfasst. Vorlagen: `tarif-form` / `tarif-list`.
  - Leere Liste → Hinweis „keine Positionen erfasst" (kein leeres Tabellengerüst).
  - **Hinweis** in der Ansicht: Positionen werden bei **jeder** Rechnungserstellung erneut aufgenommen — es gibt keinen „bereits verrechnet"-Status. Gegenmassnahme ist organisatorisch (Rechnungen je Zeitraum nur einmal erstellen), nicht technisch. Der Hinweis steht **im Textfluss** (nicht als Overlay, das sonst mit den Erfolgs-/Fehlermeldungen kollidiert) und ist **wegklickbar**; die Entscheidung wird pro Browser in `localStorage` gemerkt, weil er einmalig erklärt und nicht bei jedem Seitenaufruf erneut weggeklickt werden soll.
* **Rechnung (Web + PDF):** Die Positionszeilen erscheinen **nach** den ZEV-/VNB-Zeilen und **vor** der automatisch berechneten Grundgebühr, mit der Mengeneinheit des jeweiligen Tarifs (`"KWH"` bei Ladestrom). Als Zeitraum steht das **Quartal der Position, eingeschränkt auf die Gültigkeit des Tarifs** — damit rechnet die Zeile wie die ZEV-/VNB- und Grundgebühr-Zeilen, die ihren Zeitraum ebenfalls mit der Tarifgültigkeit schneiden. Mit dem **Rechnungszeitraum** wird bewusst *nicht* geschnitten: Die erfasste Menge gehört zum ganzen Quartal und wird nicht anteilig aufgeteilt (FR-1.5). Keine strukturelle Änderung am Rechnungslayout — es kommen nur Zeilen dazu.
  - Die **Quell-Referenz** wird in das bestehende Feld `bezeichnung` eingebettet (`Ladestrom (LP-01)`), **nicht** als eigene Spalte: `rechnung.jrxml` kennt nur `bezeichnung/von/bis/menge/mengeneinheit/preis/betrag`, eine neue Spalte hiesse Template- und Layoutänderung für eine Angabe, die nur eine Zeilenart betrifft. Das Template hängt den Zeitraum bereits selbst in Klammern an, die Zeile lautet also `Ladestrom (LP-01) (01.07.2026 - 30.09.2026)`.
* **Zahlenformatierung** nach `Specs/generell.md`: Dezimalpunkt, Hochkomma als Tausendertrennzeichen (`1'234.567`), `–` bei Fehlwerten — in Liste, Formular **und** PDF gleich.
* Alle Texte via `TranslationService`/`TranslatePipe`, keine Hardcodings.

### FR-6: Warum die Grundgebühr **nicht** erfassbar ist

Ein Versuch, `GRUNDGEBUEHR` als Positionstyp zuzulassen, wurde nach einem Tag wieder
zurückgenommen — er lief in zwei Sackgassen:

* **Ein eigener Tarif war gar nicht anlegbar.** Die Überschneidungsregel lässt je Zeitraum nur
  *einen* Grundgebühr-Tarif zu. Eine Ladestation konnte damit nur die Grundgebühr der Wohnungen
  erfassen — zu deren Preis.
* **Die Regel aufzuheben verbietet sich.** `RechnungService.berechneGrundgebuehrZeilen` schreibt
  **jeden** gültigen Grundgebühr-Tarif automatisch auf **jede** Konsumenten-Rechnung. Ein zweiter,
  für Ladestationen gedachter Tarif landete damit auf allen Wohnungsrechnungen.

Der Anwendungsfall „Grundgebühr für eine Ladestation" gehört deshalb zum Tariftyp `ZUSATZ` mit
Mengeneinheit *Monat* — eigener Preis, beliebig viele nebeneinander, ohne Eingriff in die
automatische Berechnung. Siehe [`Specs/Tarifpositionen.md`](./Tarifpositionen.md).

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

### Tarif
* [ ] In der Tarifverwaltung lässt sich ein Tarif vom Typ `LADESTROM` anlegen, bearbeiten und löschen; er erscheint mit Preis und Gültigkeit in der Liste.
* [ ] Ein zweiter `LADESTROM`-Tarif mit **überschneidender** Gültigkeit wird abgewiesen (Meldung, kein Datensatz); ein Tarif mit anschliessendem, überschneidungsfreiem Zeitraum ist zulässig.
* [ ] Ein `LADESTROM`-Tarif beeinflusst die Solarverteilung **nicht** und erzeugt ohne erfasste Position **keine** Rechnungszeile.

### Erfassung
* [ ] Zu einer Ladestations-Einheit lassen sich Positionen für **mehrere Quartale** erfassen.
* [ ] Über *Kopieren* im Kebab-Menü öffnet sich das Formular mit den Werten der gewählten Position (Tarif, Jahr, Quartal, Menge, Quell-Referenz, Bemerkung); Speichern legt eine **neue** Position an, die Ausgangsposition bleibt unverändert.
* [ ] Eine Kopie, die ohne Änderung von Jahr/Quartal gespeichert wird, wird mit der Duplikat-Meldung abgewiesen (kein zweiter Datensatz).
* [ ] Eine zweite Position für dieselbe Einheit, dasselbe Quartal und denselben **Tariftyp** wird **abgewiesen** (Meldung, kein Datensatz) — auch dann, wenn ein anderer `LADESTROM`-Tarif gewählt wird.
* [ ] Menge < 0 wird abgewiesen; Menge = 0 ist speicherbar, erzeugt aber **keine** Rechnungszeile (FR-1.5).
* [ ] Ein Tarif vom Typ `GRUNDGEBUEHR` ist in der Tarif-Auswahl der Position **nicht** wählbar (FR-6); der Versuch, eine solche Position zu speichern, wird serverseitig abgewiesen.
* [ ] Als Tarif sind ausschliesslich Tarife eines **manuell erfassbaren** Typs wählbar (`LADESTROM`, `GRUNDGEBUEHR`) — `ZEV`- und `VNB`-Tarife erscheinen nicht in der Auswahl.
* [ ] Positionen sind mandantengetrennt: Ein Mandant sieht ausschliesslich seine eigenen (`org_id`).
* [ ] Manuell erfasste Positionen tragen `erfassungsart = MANUELL`; die Quell-Referenz ist frei erfassbar und optional.
* [ ] Die Liste weist die Herkunft je Position aus (manuell/importiert).
* [ ] Jede Datenspalte der Liste ist auf- und absteigend sortierbar; die Spalte Quartal sortiert chronologisch über Jahresgrenzen hinweg (Q4/2026 vor Q1/2027).
* [ ] Eine gewählte Sortierung bleibt nach dem Erfassen, Ändern oder Löschen einer Position erhalten.
* [ ] Die Spaltenbreiten der Liste lassen sich mit der Maus verändern.
* [ ] In der Ansicht steht der Hinweis, dass Positionen bei jeder Rechnungserstellung erneut aufgenommen werden; er lässt sich wegklicken und bleibt danach ausgeblendet.

### Rechnung
* [ ] Rechnung über ein Quartal, in dem eine dem Mieter zugeordnete Einheit eine Position mit Menge > 0 hat → die Position erscheint als eigene Zeile mit korrektem Betrag (`Menge × Preis`).
* [ ] Der Betrag der Ladestrom-Zeilen fliesst in `totalBetrag`, `rundung` (5 Rappen) und `endBetrag` ein.
* [ ] Menge und Betrag einer Ladestrom-Zeile werden mit derselben Rundung dargestellt wie die ZEV-/VNB-Zeilen derselben Rechnung.
* [ ] **Mieterwechsel im Quartal:** Mieter A (Jan–Feb, alte RFID-Einheit) und Mieter B (ab März, neue RFID-Einheit) haben je eine Q1-Position → **jeder erhält auf seiner Rechnung ausschliesslich seine eigene** Position, obwohl beide Rechnungen nur einen Teil von Q1 abdecken.
* [ ] Rechnung über ein Halbjahr mit Positionen in beiden Quartalen → **beide** Zeilen erscheinen.
* [ ] Einheit/Mieter ohne Positionen → Rechnung unverändert wie heute (keine leere Zeile, kein Fehler).
* [ ] Rechnung **ohne** Mieter (Einheit ohne Zuordnung im Zeitraum) → keine Tarifpositionen, kein Fehler.
* [ ] Ist an der Position eine Quell-Referenz erfasst, erscheint sie auf der Rechnungszeile in Klammern hinter der Tarif-Bezeichnung; ohne Quell-Referenz steht dort unverändert nur die Bezeichnung.
* [ ] Die Ladestrom-Zeilen erscheinen identisch in der **Web-Ansicht** und im **PDF**.
* [ ] Produzenten-Rechnungen bleiben unverändert (nur Grundgebühr).

### Berechtigungen
* [ ] Erfassen/Ändern/Löschen von Positionen erfordert `rechnungen:manage`; ohne diese Permission → `403` und der Menüeintrag ist nicht sichtbar.
* [ ] Lesen der Positionen erfordert die für die Mieteransicht bereits nötige Permission (`mieter:read`).

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Pro Rechnung eine zusätzliche, indizierte Abfrage (`einheit_id` + Zeitraum). Bei realistischen Mengen (wenige Positionen je Einheit und Jahr) vernachlässigbar.
* Die Rechnungserzeugung über alle Einheiten darf sich dadurch nicht spürbar verlängern (Richtwert: < 5 % zusätzliche Laufzeit).

### NFR-2: Sicherheit
* **Permissions:** Schreiben `rechnungen:manage` (Fachrollen `zev_user`, `org_admin`, `zev_admin` — konsistent damit, dass diese Rollen bereits Rechnungen erstellen und Debitoren verwalten). Lesen über `mieter:read`. Backend durchgängig `@PreAuthorize("hasAuthority('…')")`, Frontend zusätzlich über die Route/`AuthGuard`.
* **Multi-Tenancy:** `org_id` an der Entity, `@Filter(name = "orgFilter")`, im Service `hibernateFilterService.enableOrgFilter()`. `orgId` stammt **immer** aus dem Organisations-Kontext, nie aus dem Request.
* **Validierung:** `menge ≥ 0`, `quartal ∈ 1..4`, `jahr` plausibel (z.B. 2000–2100), `tarif_id` muss existieren **und** einen zulässigen Typ haben, Eindeutigkeit je Einheit/Quartal/Tariftyp — alles **serverseitig** geprüft, nicht nur im Formular.
* **Nachvollziehbarkeit:** Da die Menge manuell erfasst wird und direkt Geld bewegt, ist die Bemerkung als Beleg-Hinweis vorgesehen; Änderungen an Positionen werden geloggt.

### NFR-3: Kompatibilität
* Rein additiv: neue Tabelle, neue Enums, zusätzliche Rechnungszeilen. Bestehende Rechnungen ohne Positionen sind **bit-identisch** zu vorher.
* **Weder PDF-Template noch Web-Ansicht müssen angepasst werden:** `rechnung.jrxml` kennt nur die Felder `bezeichnung, von, bis, menge, mengeneinheit, preis, betrag` — **kein** `typ` — und rendert die Positionen damit typunabhängig; die `rechnungen`-Komponente verzweigt ebenfalls nirgends nach `TarifTyp`. Neue Zeilenarten erscheinen automatisch. Das Akzeptanzkriterium „identisch in Web und PDF" bleibt als Regressionsschutz.
* Die Erweiterung von `TarifTyp` erfordert eine DDL-Migration an `tarif` (CHECK-Constraint `tarif_tariftyp_check`, siehe FR-2) sowie eine Prüfung aller Stellen, die über `TarifTyp` verzweigen (`RechnungService`, `TarifService`, Frontend-Dropdowns, PDF-Export).
* Neue Übersetzungs-Keys via Flyway (`ON CONFLICT (key) DO NOTHING`); nächste freie Migrationsnummer zum Umsetzungszeitpunkt eruieren (aktuell wäre es `V100`).

## 5. Edge Cases & Fehlerbehandlung
| Szenario | Verhalten |
|----------|-----------|
| Keine Positionen erfasst | Rechnung wie bisher, keine zusätzliche Zeile |
| Menge = 0 | Position bleibt gespeichert, erscheint **nicht** auf der Rechnung |
| Mieterwechsel innerhalb des Quartals | Jeder Mieter hat seine eigene Ladestations-Einheit (neue RFID) und damit seine eigene Position; beide Rechnungen enthalten je die eigene Menge (Überschneidungsregel, FR-1.5) |
| Rechnungszeitraum überschneidet das Quartal nur teilweise | Position wird **vollständig** aufgenommen — keine anteilige Aufteilung; die erfasste Menge gilt als die der jeweiligen Einheit |
| Tarif ist im gewählten Quartal nicht gültig | Zulässig: Der Tarif ist die bewusste Wahl des Benutzers, verrechnet wird sein Preis. Die Eindeutigkeit sichert die Regel „ein gültiger Tarif je Zeitraum" |
| Tarif wird gelöscht, während Positionen darauf verweisen | Löschen wird **abgewiesen** (referenzielle Integrität), Meldung mit Anzahl betroffener Positionen |
| Mieter wird gelöscht | **Abgewiesen**, solange an einer zugeordneten Einheit Positionen hängen (`Ladestationen.md`) |
| Ladestations-Einheit wird gelöscht | Zugehörige Positionen werden mitgelöscht (`ON DELETE CASCADE`) |
| Importierte Menge trifft auf eine manuell erfasste Position | Regel ist Teil der Schnittstellen-Spec (§8): Vorschlag ist, manuell erfasste Positionen **nicht** stillschweigend zu ueberschreiben, sondern zu melden |
| Zweimalige Rechnungserstellung für denselben Mieter und Zeitraum | Die Position erscheint **beide Male** — es gibt keinen „bereits verrechnet"-Status (Rechnungen sind nicht persistiert, siehe §8) |
| Ungültige Eingaben (negative Menge, Quartal 5, fehlender Tarif) | `400` mit verständlicher Meldung, kein Datensatz |
| Netzwerkfehler im Frontend | Fehlermeldung über den Message-Bereich, kein Absturz, Formulareingaben bleiben erhalten |
| Leere Liste | Hinweistext statt leerer Tabelle |

## 6. Abhängigkeiten & betroffene Funktionalität
* **Voraussetzungen:** Tarifverwaltung (`Specs/Tarifverwaltung.md`), Mieterverwaltung (`Specs/Mieterverwaltung.md`), Rechnungserstellung, Berechtigungsmodell (`Specs/Berechtigungen.md`). Betrieblich: ein **eigener Ladestrom-Messpunkt** als `CONSUMER`-Einheit, einem Mieter (Eigentümer) zugeordnet.
* **Betroffener Code (Backend):**
  - `entity/TarifTyp.java` — neuer Wert `LADESTROM`
  - `entity/Tarifposition.java`, `repository/TarifpositionRepository.java`, `service/TarifpositionService.java`, `controller/TarifpositionController.java` (Vorlagen: `Tarif*`)
  - `dto/TarifpositionDTO.java`
  - `entity/Erfassungsart.java` — neues Enum `{ MANUELL, IMPORT }` (Name bewusst **nicht** `Quelle`, das ist bereits vergeben)
  - `service/RechnungService.java` — zusätzliche Zeilen nach ZEV/VNB, vor Grundgebühr; Positionen über den Mieter der Rechnung
  - Flyway: neue Tabelle + Sequenz + Index, neue Übersetzungs-Keys
* **Betroffener Code (Frontend):**
  - `models/tarifposition.model.ts`, `services/tarifposition.service.ts`
  - `components/tarifposition-list/`, `components/tarifposition-form/` (Vorlagen: `tarif-list`, `tarif-form`)
  - `components/mieter-list/` — Kebab-Menü-Eintrag „Tarifpositionen"
  - `components/tarif-form/` — Typ-Dropdown um `LADESTROM` erweitern
  - `components/rechnungen/` — nur indirekt (zusätzliche Zeilen aus dem DTO)
* **Dokumentation:** `Specs/Tarifverwaltung.md` (neuer Typ + Überschneidungsregel), `Specs/Mieterverwaltung.md` (neuer Kebab-Eintrag).
* **Datenmigration:** Keine — Bestandsdaten sind nicht betroffen.

## 7. Abgrenzung / Out of Scope
* **Keine Priorisierung in der Solarverteilung.** Ladestationen bleiben normale Consumer (bewusster Entscheid, s. §1).
* **Keine Steuerung/Lastmanagement** — PV-Überschussladen liegt bei der Ladeinfrastruktur.
* **Keine automatische Schnittstelle zum Lademanagement.** Die Mengen werden vorerst **manuell** erfasst; der automatische Bezug ist der geplante Folgeschritt (§8).
* **Keine Aufschlüsselung nach Ladevorgang oder Fahrzeug** — nur eine Summe je Einheit, Tariftyp und Quartal. (Die RFID identifiziert seit `Ladestationen.md` die Ladestations-Einheit, nicht den einzelnen Ladevorgang.)
* **Keine weiteren Tarif-Anwendungsfälle in dieser Umsetzung.** Die Tabelle ist generisch angelegt, umgesetzt und getestet wird aber ausschliesslich der Ladestrom-Fall; Sauna & Co. sind Folgeanforderungen.
* **Keine anteilige Aufteilung** einer Position auf Teilzeiträume.
* **Keine Persistierung der Rechnung** und damit kein „bereits verrechnet"-Status (unverändert zum heutigen Verhalten).
* **Keine Sonderbehandlung des Ladestrom-Messpunkts** in der Verteilung; er wird wie jede andere Einheit abgerechnet.

### Umgesetzt in einer Folge-Spec

Das frühere „Zielbild" dieses Abschnitts ist umgesetzt und vollständig nach
[`Specs/Ladestationen.md`](./Ladestationen.md) gewandert: Ladestationen als eigene Einheiten vom
Typ `LADESTATION` mit der RFID im `messpunkt`, Tarifposition an der Einheit, mehrere Einheiten je
Mieter. Dort stehen auch die verbliebenen offenen Punkte (Teilnahme an der Solarverteilung und
Bilanzabgleich, Vertragspartner-Entität als Fernziel).

## 8. Offene Fragen
* [ ] **Schnittstelle Lademanagement:** Welches System, welches Protokoll (API/CSV/OCPP), welche Granularität (je Ladepunkt, je Nutzer, je Quartal)? Eigene Spec, sobald bekannt. Die Tarif-Auflösung ist bereits geklärt: höchstens ein gültiger `LADESTROM`-Tarif je Zeitraum, höchstens eine Position je Einheit und Quartal — damit ist die Zuordnung eindeutig. Die **Identifikation des Nutzers** ist inzwischen geklärt (`Ladestationen.md`): Das Lademanagement meldet je **RFID**, und die RFID steht im `messpunkt` der Ladestations-Einheit. Offen bleiben Protokoll und Granularität. Ebenfalls dort zu regeln: der Umgang mit bereits manuell erfassten Positionen.

### Beantwortet (in §1/§2 eingearbeitet)
| Frage | Antwort |
|---|---|
| Permission | `rechnungen:manage` |
| Mieterwechsel im Quartal | Ursprünglich Anker am **Mieter**; seit `Ladestationen.md` an der **Einheit** — beim Wechsel entsteht mit der neuen RFID eine neue Einheit, jeder trägt damit weiterhin seine eigene Position |
| Tarifgültigkeit vs. Quartal | Weder harte Abweisung noch Warnung: stattdessen **Eindeutigkeit** je Einheit/Quartal/Tariftyp und höchstens **ein gültiger** `LADESTROM`-Tarif je Zeitraum |
| Tarif-Auflösung beim Import | Nur ein gültiger Tarif je Zeitraum und Einheit |
| Zuordnung des Ladestrom-Messpunkts | Über einen **Mieter** (Eigentümer als Mieter-Datensatz) |
| Doppelverrechnungsschutz | **Vorerst bewusst ohne.** Ein „bereits verrechnet"-Status setzte voraus, dass Rechnungen (oder ein Verrechnungsvermerk) persistiert werden — das ist heute nicht der Fall und waere ein eigenes Vorhaben. Stattdessen ein Hinweis in der Erfassungsmaske (FR-3) |
| Ladepunkt-Kennung | An der Position als Herkunft (`erfassungsart` + `quell_referenz`) und seit `Ladestationen.md` als **RFID im `messpunkt`** der Ladestations-Einheit. Das urspruengliche Feld am Mieter ist mit `V103` entfallen |
