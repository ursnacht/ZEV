# Ladestromtarif

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Der Strom für das Laden von Fahrzeugen soll den Mietern zu einem **eigenen Tarif** verrechnet werden — getrennt von ZEV- und VNB-Anteil. Dazu wird **je Mieter** eine Tarifposition erfasst (Tarif, Quartal, Menge in kWh), die bei der Rechnungserzeugung automatisch als zusätzliche Zeile erscheint.
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
| **Anker der Position** | Am **Mieter**. Damit ist ein **Mieterwechsel innerhalb eines Quartals** sauber abgebildet: Jeder Mieter trägt seine eigene Menge, ohne Aufteilungsregel. |
| **Tarif-Typisierung** | Neuer `TarifTyp.LADESTROM`, verwaltet in der bestehenden Tarifverwaltung. **Bewusst ein spezifischer Typ und kein generisches `ZUSATZ`:** Der spätere automatische Import aus dem Lademanagement muss die bezogenen Mengen einem Tarif zuordnen können — das geht über den **Typ** maschinell zuverlässig, über eine Bezeichnung nicht. |
| **Datenhaltung** | **Generische** Tabelle `tarifposition` für manuell erfasste Mengen zu einem beliebigen Tarif — nicht ladestrom-spezifisch. Weitere Anwendungsfälle (Sauna, Waschküche, …) kommen ohne Schema-Änderung dazu. |
| **Genau ein gültiger Tarif** | Je Zeitraum darf **höchstens ein** `LADESTROM`-Tarif gültig sein, und je Mieter und Quartal existiert **höchstens eine** Ladestrom-Position. Damit ist die Zuordnung beim späteren Import eindeutig. |
| **Ladepunkt-Zuordnung** | Der Mieter traegt eine **Ladepunkt-Kennung** (Stammdaten) — daran erkennt der spaetere Import, wem eine Menge gehoert. Jeder Mieter hat **hoechstens einen** Ladepunkt. Zusaetzlich traegt jede Position ihre **Erfassungsart** (manuell/importiert) und die **Quell-Referenz** als Nachweis. |
| **Mehrere Ladestationen** | Hinter einem Ladestrom-Messpunkt dürfen beliebig viele Ladestationen stehen — für ZEV ist nur die Summe am Messpunkt relevant. |

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Ablauf / Flow
1. Ein Benutzer legt in der **Tarifverwaltung** einen Tarif vom Typ **`LADESTROM`** an (Bezeichnung, Preis in CHF/kWh, Gültigkeit von/bis) — analog zu ZEV/VNB. Überschneidet sich die Gültigkeit mit einem bestehenden `LADESTROM`-Tarif, wird das **abgewiesen** (FR-2).
2. Er öffnet die **Mieterverwaltung** und wählt beim Mieter im Kebab-Menü **„Tarifpositionen"**.
3. Es erscheint die Liste der erfassten Positionen dieses Mieters (Tarif, Quartal, Menge, Betrag) mit *Anlegen*, *Bearbeiten* und *Löschen*.
4. Beim Anlegen wählt er: **Tarif** (Auswahl aus den `LADESTROM`-Tarifen), **Jahr + Quartal** (zwei einfache Dropdowns) und die **Menge in kWh**. Existiert für diesen Mieter, dieses Quartal und diesen **Tariftyp** bereits eine Position, wird das abgewiesen — die bestehende ist zu bearbeiten.
5. Bei der **Rechnungserzeugung** für Einheit + Mieter + Zeitraum werden automatisch alle Positionen **dieses Mieters** aufgenommen, deren Quartal sich mit dem Rechnungszeitraum **überschneidet** und deren **Menge > 0** ist.
6. Jede aufgenommene Position erscheint als **eigene Tarifzeile** (Bezeichnung des Tarifs, Quartalsgrenzen als Von/Bis, Menge in kWh, Preis, Betrag) und fliesst in Total, Rundung und Endbetrag ein. Menge und Betrag werden **wie die bestehenden ZEV-/VNB-Zeilen** gerundet dargestellt; gespeichert bleibt die Menge mit drei Nachkommastellen.

> **Warum Überschneidung und nicht „Quartal vollständig im Zeitraum"?** Das folgt zwingend aus dem Anker am Mieter: Zieht ein Mieter Mitte Quartal aus, deckt **seine** Rechnung nur einen Teil des Quartals ab. Bei der strengeren Regel würde seine Position nie verrechnet. Doppelverrechnung entsteht dadurch nicht, weil jede Position genau **einem** Mieter gehört und ein Mieter je Zeitraum einmal abgerechnet wird — offen bleibt nur der allgemeine Fall zweier überlappender Rechnungsläufe für denselben Mieter (§8).

### FR-2: Persistierung
* Neue Tabelle **`tarifposition`** — **bewusst generisch**, nicht ladestrom-spezifisch. Sie nimmt **manuell erfasste Mengen zu einem beliebigen Tarif** auf; Ladestrom ist der erste Anwendungsfall, weitere (Sauna, Waschküche, Wärmepumpe …) kommen ohne Schema-Änderung dazu. Die fachliche Bedeutung steckt ausschliesslich im referenzierten Tarif.
* Multi-Tenancy: `org_id` + Hibernate-`@Filter`, wie alle Entities.

  | Spalte | Typ | Pflicht | Bemerkung |
  |---|---|---|---|
  | `id` | BIGINT | ja | Sequenz `zev.tarifposition_seq` |
  | `org_id` | BIGINT | ja | Mandant |
  | `mieter_id` | BIGINT | ja | FK auf `mieter`, **ON DELETE CASCADE** — **Anker der Position** |
  | `tarif_id` | BIGINT | ja | FK auf `tarif`, **ON DELETE RESTRICT** (ein referenzierter Tarif ist nicht löschbar) — der Typ des Tarifs bestimmt die Bedeutung der Position |
  | `jahr` | INT | ja | z.B. 2026 |
  | `quartal` | INT | ja | 1–4 |
  | `menge` | NUMERIC(12,3) | ja | ≥ 0. **Nicht** `menge_kwh`: die Einheit ergibt sich aus dem Tarif, damit später auch nicht-kWh-Positionen möglich sind |
  | `erfassungsart` | VARCHAR(20) | ja | `MANUELL` (Default) oder `IMPORT` — Herkunft der Menge. **Nicht** `quelle`: `ch.nacht.entity.Quelle` existiert bereits (CSV/MQTT/API, `Messwerte.quelle`). Neues Enum `Erfassungsart`, `EnumType.STRING`, `length = 20` wie die bestehende Konvention |
  | `quell_referenz` | VARCHAR(64) | nein | Kennung, aus der die Menge stammt (bei Import die Ladepunkt-Kennung); bei manueller Erfassung aus dem Mieter vorbelegt |
  | `bemerkung` | VARCHAR(200) | nein | freier Text, rein informativ |

* **Index:** auf (`org_id`, `mieter_id`, `jahr`, `quartal`) — deckt sowohl die Abfrage bei der Rechnungserzeugung als auch die Eindeutigkeitsprüfung ab.
* **Eindeutigkeit je Tariftyp:** Pro (`org_id`, `mieter_id`, `jahr`, `quartal`, **Tariftyp**) ist **höchstens eine** Position zulässig. Da der Typ am Tarif hängt und nicht in der Tabelle steht, wird die Regel **im Service** geprüft; die Datenbank sichert zusätzlich UNIQUE (`org_id`, `mieter_id`, `tarif_id`, `jahr`, `quartal`) als Netz gegen exakte Duplikate.
* **Neues Feld am Mieter:** `mieter.ladepunkt` (VARCHAR(64), optional). Es ist die **Zuordnungsgrundlage** fuer den spaeteren Import und je Mandant **eindeutig** (UNIQUE `org_id`, `ladepunkt`, sofern gesetzt) — zwei Mieter mit derselben Kennung wuerden den Import mehrdeutig machen. Nicht jeder Mieter hat einen Ladepunkt; das Feld bleibt dann leer.
* **Herkunft je Position:** `erfassungsart` unterscheidet manuell erfasste von importierten Mengen, `quell_referenz` haelt fest, woher eine importierte Menge stammt. Damit ist spaeter belegbar, warum eine Menge auf der Rechnung steht — bei manuell erfassten Betraegen, die direkt Geld bewegen, ist das der eigentliche Zweck.
* **Höchstens ein gültiger `LADESTROM`-Tarif je Zeitraum:** Diese Regel gilt **automatisch, ohne neuen Code** — `TarifService.saveTarif` prüft bereits typbezogen auf Überschneidung (`existsOverlappingTarif(tariftyp, …)`) und weist überlappende Tarife für **jeden** Typ ab. Mit dem neuen Enum-Wert greift die Prüfung ohne Zutun auch für `LADESTROM`. Damit ist der Tarif für ein Quartal eindeutig bestimmbar — Voraussetzung für den späteren automatischen Import. Das zugehörige Akzeptanzkriterium dient der **Regressionsabsicherung**, nicht der Neuentwicklung.
* **`TarifTyp`** wird um den Wert `LADESTROM` erweitert. Die Spalte `tarif.tariftyp` ist `VARCHAR`/`EnumType.STRING` → **keine DDL-Änderung** am Tarif nötig.
* **Zulässige Tariftypen für Positionen:** Die Prüfung erfolgt gegen eine **Menge manuell erfasster Typen** (aktuell `{ LADESTROM }`), nicht gegen einen einzelnen Wert. Ein weiterer Anwendungsfall erweitert nur diese Menge — Tabelle, Service und UI bleiben unverändert. `ZEV`, `VNB` und `GRUNDGEBUEHR` sind ausgeschlossen, da sie aus Messwerten bzw. der Laufzeit berechnet werden.
* Rechnungen bleiben **unpersistiert**; die Tarifpositionen sind die einzige neue dauerhafte Datenhaltung.

### FR-3: Layout
* **Mieterverwaltung:** Das Mieter-Formular erhaelt das optionale Feld **Ladepunkt**.
* **Tarifverwaltung:** Der Typ `LADESTROM` erscheint im bestehenden Typ-Dropdown; bei überlappender Gültigkeit erscheint eine verständliche Fehlermeldung. Sonst keine Änderung an der Maske.
* **Tarifpositionen** (neue, generische Ansicht, erreichbar über das Kebab-Menü des **Mieters**):
  - **Liste** (Design-System-`table`): Tarif-Bezeichnung, Jahr/Quartal, Menge (rechtsbündig `.zev-table__number`; Mengeneinheit aus dem Tarif, aktuell durchgehend kWh), Preis, Betrag, Kebab-Menü mit *Bearbeiten* / *Löschen*.
  - Die Liste weist die **Herkunft** aus (manuell/importiert); importierte Positionen sind als solche erkennbar.
  - **Formular** (Design-System-`form`): Tarif (Dropdown), **Jahr** und **Quartal** als je ein Dropdown, Menge, Bemerkung; Speichern/Abbrechen.
    Bewusst **nicht** die `QuarterSelectorComponent`: Sie arbeitet mit Datumsbereichen (`@Input selectedVon/selectedBis`, `@Output {von, bis}`), die Position speichert aber `jahr` + `quartal` — eine Hin- und Rueckrechnung waere unnoetiger Umweg. Die Quell-Referenz wird aus dem Ladepunkt des Mieters vorbelegt und ist aenderbar. Vorlagen: `tarif-form` / `tarif-list`.
  - Leere Liste → Hinweis „keine Positionen erfasst" (kein leeres Tabellengerüst).
* **Rechnung (Web + PDF):** Die Ladestrom-Zeilen erscheinen **nach** den ZEV-/VNB-Zeilen und **vor** der Grundgebühr, mit `mengeneinheit = "kWh"`. Keine strukturelle Änderung am Rechnungslayout — es kommen nur Zeilen dazu.
* **Zahlenformatierung** nach `Specs/generell.md`: Dezimalpunkt, Hochkomma als Tausendertrennzeichen (`1'234.567`), `–` bei Fehlwerten — in Liste, Formular **und** PDF gleich.
* Alle Texte via `TranslationService`/`TranslatePipe`, keine Hardcodings.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

### Tarif
* [ ] In der Tarifverwaltung lässt sich ein Tarif vom Typ `LADESTROM` anlegen, bearbeiten und löschen; er erscheint mit Preis und Gültigkeit in der Liste.
* [ ] Ein zweiter `LADESTROM`-Tarif mit **überschneidender** Gültigkeit wird abgewiesen (Meldung, kein Datensatz); ein Tarif mit anschliessendem, überschneidungsfreiem Zeitraum ist zulässig.
* [ ] Ein `LADESTROM`-Tarif beeinflusst die Solarverteilung **nicht** und erzeugt ohne erfasste Position **keine** Rechnungszeile.

### Erfassung
* [ ] Zu einem Mieter lassen sich Positionen für **mehrere Quartale** erfassen.
* [ ] Eine zweite Position für denselben Mieter, dasselbe Quartal und denselben **Tariftyp** wird **abgewiesen** (Meldung, kein Datensatz) — auch dann, wenn ein anderer `LADESTROM`-Tarif gewählt wird.
* [ ] Menge < 0 wird abgewiesen; Menge = 0 ist speicherbar, erzeugt aber **keine** Rechnungszeile (FR-1.5).
* [ ] Als Tarif sind ausschliesslich `LADESTROM`-Tarife wählbar.
* [ ] Positionen sind mandantengetrennt: Ein Mandant sieht ausschliesslich seine eigenen (`org_id`).
* [ ] Am Mieter laesst sich eine **Ladepunkt-Kennung** erfassen; sie ist optional.
* [ ] Eine Ladepunkt-Kennung, die bereits einem anderen Mieter desselben Mandanten gehoert, wird **abgewiesen** (Meldung, kein Datensatz).
* [ ] Manuell erfasste Positionen tragen `erfassungsart = MANUELL`; die Quell-Referenz ist aus dem Ladepunkt des Mieters vorbelegt und aenderbar.
* [ ] Die Liste weist die Herkunft je Position aus (manuell/importiert).

### Rechnung
* [ ] Rechnung über ein Quartal, in dem der Mieter eine Position mit Menge > 0 hat → die Position erscheint als eigene Zeile mit korrektem Betrag (`Menge × Preis`).
* [ ] Der Betrag der Ladestrom-Zeilen fliesst in `totalBetrag`, `rundung` (5 Rappen) und `endBetrag` ein.
* [ ] Menge und Betrag einer Ladestrom-Zeile werden mit derselben Rundung dargestellt wie die ZEV-/VNB-Zeilen derselben Rechnung.
* [ ] **Mieterwechsel im Quartal:** Mieter A (Jan–Feb) und Mieter B (ab März) haben je eine Q1-Position → **jeder erhält auf seiner Rechnung ausschliesslich seine eigene** Position, obwohl beide Rechnungen nur einen Teil von Q1 abdecken.
* [ ] Rechnung über ein Halbjahr mit Positionen in beiden Quartalen → **beide** Zeilen erscheinen.
* [ ] Einheit/Mieter ohne Positionen → Rechnung unverändert wie heute (keine leere Zeile, kein Fehler).
* [ ] Rechnung **ohne** Mieter (Einheit ohne Zuordnung im Zeitraum) → keine Tarifpositionen, kein Fehler.
* [ ] Die Ladestrom-Zeilen erscheinen identisch in der **Web-Ansicht** und im **PDF**.
* [ ] Produzenten-Rechnungen bleiben unverändert (nur Grundgebühr).

### Berechtigungen
* [ ] Erfassen/Ändern/Löschen von Positionen erfordert `rechnungen:manage`; ohne diese Permission → `403` und der Menüeintrag ist nicht sichtbar.
* [ ] Lesen der Positionen erfordert die für die Mieteransicht bereits nötige Permission (`mieter:read`).

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Pro Rechnung eine zusätzliche, indizierte Abfrage (`mieter_id` + Zeitraum). Bei realistischen Mengen (wenige Positionen je Mieter und Jahr) vernachlässigbar.
* Die Rechnungserzeugung über alle Einheiten darf sich dadurch nicht spürbar verlängern (Richtwert: < 5 % zusätzliche Laufzeit).

### NFR-2: Sicherheit
* **Permissions:** Schreiben `rechnungen:manage` (Fachrollen `zev_user`, `org_admin`, `zev_admin` — konsistent damit, dass diese Rollen bereits Rechnungen erstellen und Debitoren verwalten). Lesen über `mieter:read`. Backend durchgängig `@PreAuthorize("hasAuthority('…')")`, Frontend zusätzlich über die Route/`AuthGuard`.
* **Bewusst unterschiedliche Permissions für Menge und Kennung:** Die Positionen (operative Abrechnungsdaten) schreibt `rechnungen:manage` und damit auch `zev_user`. Das Feld `mieter.ladepunkt` gehört dagegen zu den **Mieter-Stammdaten** und erfordert wie diese `mieter:manage` (org_admin). Ein `zev_user` kann also Mengen erfassen, die Zuordnungskennung aber nicht pflegen — gewollt, weil eine falsch gesetzte Kennung den späteren Import stillschweigend auf den falschen Mieter lenken würde.
* **Multi-Tenancy:** `org_id` an der Entity, `@Filter(name = "orgFilter")`, im Service `hibernateFilterService.enableOrgFilter()`. `orgId` stammt **immer** aus dem Organisations-Kontext, nie aus dem Request.
* **Validierung:** `menge ≥ 0`, `quartal ∈ 1..4`, `jahr` plausibel (z.B. 2000–2100), `tarif_id` muss existieren **und** einen zulässigen Typ haben, Eindeutigkeit je Mieter/Quartal/Tariftyp — alles **serverseitig** geprüft, nicht nur im Formular.
* **Nachvollziehbarkeit:** Da die Menge manuell erfasst wird und direkt Geld bewegt, ist die Bemerkung als Beleg-Hinweis vorgesehen; Änderungen an Positionen werden geloggt.

### NFR-3: Kompatibilität
* Rein additiv: neue Tabelle, neue Enums, zusätzliche Rechnungszeilen. Bestehende Rechnungen ohne Positionen sind **bit-identisch** zu vorher.
* **Weder PDF-Template noch Web-Ansicht müssen angepasst werden:** `rechnung.jrxml` kennt nur die Felder `bezeichnung, von, bis, menge, mengeneinheit, preis, betrag` — **kein** `typ` — und rendert die Positionen damit typunabhängig; die `rechnungen`-Komponente verzweigt ebenfalls nirgends nach `TarifTyp`. Neue Zeilenarten erscheinen automatisch. Das Akzeptanzkriterium „identisch in Web und PDF" bleibt als Regressionsschutz.
* Die Erweiterung von `TarifTyp` erfordert keine DDL an `tarif` (String-Enum), aber eine Prüfung aller Stellen, die über `TarifTyp` verzweigen (`RechnungService`, `TarifService`, Frontend-Dropdowns, PDF-Export).
* Neue Übersetzungs-Keys via Flyway (`ON CONFLICT (key) DO NOTHING`); nächste freie Migrationsnummer zum Umsetzungszeitpunkt eruieren (aktuell wäre es `V100`).

## 5. Edge Cases & Fehlerbehandlung
| Szenario | Verhalten |
|----------|-----------|
| Keine Positionen erfasst | Rechnung wie bisher, keine zusätzliche Zeile |
| Menge = 0 | Position bleibt gespeichert, erscheint **nicht** auf der Rechnung |
| Mieterwechsel innerhalb des Quartals | Jeder Mieter trägt seine eigene Position; beide Rechnungen enthalten je die eigene Menge (Überschneidungsregel, FR-1.5) |
| Rechnungszeitraum überschneidet das Quartal nur teilweise | Position wird **vollständig** aufgenommen — keine anteilige Aufteilung; die erfasste Menge gilt als die des jeweiligen Mieters |
| Tarif ist im gewählten Quartal nicht gültig | Zulässig: Der Tarif ist die bewusste Wahl des Benutzers, verrechnet wird sein Preis. Die Eindeutigkeit sichert die Regel „ein gültiger Tarif je Zeitraum" |
| Tarif wird gelöscht, während Positionen darauf verweisen | Löschen wird **abgewiesen** (referenzielle Integrität), Meldung mit Anzahl betroffener Positionen |
| Mieter wird gelöscht | Zugehörige Positionen werden mitgelöscht (`ON DELETE CASCADE`) |
| Mieter ohne Ladepunkt-Kennung | Manuelle Erfassung bleibt uneingeschraenkt moeglich; nur der spaetere Import kann diesen Mieter nicht zuordnen |
| Ladepunkt-Kennung wechselt den Mieter (Umzug) | Kennung beim alten Mieter entfernen, beim neuen setzen. Bereits erfasste Positionen behalten ihre `quell_referenz` — die Historie bleibt korrekt |
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
  - `entity/Mieter.java` — neues Feld `ladepunkt` (mandantenweit eindeutig). Ein `MieterDTO` gibt es **nicht**; `MieterController` liefert die Entity direkt — das bleibt unveraendert
  - `service/MieterService.java` — Eindeutigkeitspruefung der Ladepunkt-Kennung
  - `service/RechnungService.java` — zusätzliche Zeilen nach ZEV/VNB, vor Grundgebühr; Positionen über den Mieter der Rechnung
  - Flyway: neue Tabelle + Sequenz + Index, neue Übersetzungs-Keys
* **Betroffener Code (Frontend):**
  - `models/tarifposition.model.ts`, `services/tarifposition.service.ts`
  - `components/tarifposition-list/`, `components/tarifposition-form/` (Vorlagen: `tarif-list`, `tarif-form`)
  - `components/mieter-form/` — neues Feld Ladepunkt
  - `components/mieter-list/` — Kebab-Menü-Eintrag „Tarifpositionen"
  - `components/tarif-form/` — Typ-Dropdown um `LADESTROM` erweitern
  - `components/rechnungen/` — nur indirekt (zusätzliche Zeilen aus dem DTO)
* **Dokumentation:** `Specs/Tarifverwaltung.md` (neuer Typ + Überschneidungsregel), `Specs/Mieterverwaltung.md` (neuer Kebab-Eintrag).
* **Datenmigration:** Keine — Bestandsdaten sind nicht betroffen.

## 7. Abgrenzung / Out of Scope
* **Keine Priorisierung in der Solarverteilung.** Ladestationen bleiben normale Consumer (bewusster Entscheid, s. §1).
* **Keine Steuerung/Lastmanagement** — PV-Überschussladen liegt bei der Ladeinfrastruktur.
* **Keine automatische Schnittstelle zum Lademanagement.** Die Mengen werden vorerst **manuell** erfasst; der automatische Bezug ist der geplante Folgeschritt (§8).
* **Keine Aufschlüsselung nach Ladevorgang, Fahrzeug oder RFID-Karte** — nur eine Summe je Mieter, Tariftyp und Quartal.
* **Keine weiteren Tarif-Anwendungsfälle in dieser Umsetzung.** Die Tabelle ist generisch angelegt, umgesetzt und getestet wird aber ausschliesslich der Ladestrom-Fall; Sauna & Co. sind Folgeanforderungen.
* **Keine anteilige Aufteilung** einer Position auf Teilzeiträume.
* **Keine Persistierung der Rechnung** und damit kein „bereits verrechnet"-Status (unverändert zum heutigen Verhalten).
* **Keine Sonderbehandlung des Ladestrom-Messpunkts** in der Verteilung; er wird wie jede andere Einheit abgerechnet.

## 8. Offene Fragen
* [ ] **Doppelverrechnungsschutz:** Soll eine Position nach dem Erstellen einer Rechnung als „verrechnet" markiert werden? Das würde ein Persistieren der Rechnung (oder zumindest eines Verrechnungsvermerks) voraussetzen — heute nicht vorhanden. Vorschlag: vorerst bewusst ohne, dafür Hinweis in der Erfassungsmaske.
* [ ] **Schnittstelle Lademanagement:** Welches System, welches Protokoll (API/CSV/OCPP), welche Granularität (je Ladepunkt, je Nutzer, je Quartal)? Eigene Spec, sobald bekannt. Die Tarif-Auflösung ist bereits geklärt: höchstens ein gültiger `LADESTROM`-Tarif je Zeitraum, höchstens eine Position je Mieter und Quartal — damit ist die Zuordnung eindeutig. Die **Identifikation des Mieters** laeuft ueber `mieter.ladepunkt`; offen bleibt, **welche Kennung** das Lademanagement liefert (Ladepunkt-ID, RFID-Karte, Benutzerkonto) und damit, was fachlich in dieses Feld gehoert. Ebenfalls dort zu regeln: der Umgang mit bereits manuell erfassten Positionen.

### Beantwortet (in §1/§2 eingearbeitet)
| Frage | Antwort |
|---|---|
| Permission | `rechnungen:manage` |
| Mieterwechsel im Quartal | Anker am **Mieter** — jeder trägt seine eigene Position |
| Tarifgültigkeit vs. Quartal | Weder harte Abweisung noch Warnung: stattdessen **Eindeutigkeit** je Mieter/Quartal/Tariftyp und höchstens **ein gültiger** `LADESTROM`-Tarif je Zeitraum |
| Tarif-Auflösung beim Import | Nur ein gültiger Tarif je Zeitraum und Mieter |
| Zuordnung des Ladestrom-Messpunkts | Über einen **Mieter** (Eigentümer als Mieter-Datensatz) |
| Ladepunkt-Kennung | Am **Mieter** (Zuordnung, mandantenweit eindeutig, hoechstens eine je Mieter) **und** an der Position (Herkunft: `erfassungsart` + `quell_referenz`) |
