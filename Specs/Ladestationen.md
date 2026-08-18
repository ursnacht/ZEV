# Ladestationen

## 1. Ziel & Kontext - Warum wird das Feature benötigt?

* **Was soll erreicht werden:** Jede Ladestation wird als **eigene Einheit** vom neuen Typ `LADESTATION` geführt. Die Tarifposition für Ladestrom hängt künftig an dieser **Einheit** statt am Mieter, und einem Mieter lassen sich **mehrere Einheiten** zuordnen (Wohnung + Ladestation(en)). Damit ist die Grundlage für den automatischen Import aus dem Lademanagement gelegt und ein Nutzer, der keine Wohnung mietet, kann trotzdem abgerechnet werden.

* **Warum machen wir das:** Die heutige Lösung (`Specs/Ladestromtarif.md`) hängt die Menge am **Mieter** und trägt die Zuordnungskennung als Attribut. Das trägt nicht weit genug:
  - Ein Attribut hält genau **einen** Wert — ein Nutzer kann aber **mehrere** Ladestationen verwenden.
  - Ein Ladestations-Nutzer ist **nicht zwingend Mieter** einer Wohnung (Besucher, Eigentümer mit Wohnsitz anderswo, gewerbliche Nutzung). Heute gibt es im Modell keinen Rechnungsempfänger ohne Mietverhältnis: die Kette ist durchgehend Einheit → Mieter → Rechnung/Debitor.
  - Der Ladepunkt ist physische Infrastruktur; als Feld am Mieter müsste er bei jedem Mieterwechsel von Hand umgetragen werden.

* **Aktueller Stand:**
  - `Einheit` kennt die Typen `PRODUCER`, `CONSUMER`, `BEZUG`, `RUECKLIEFERUNG` und trägt das Feld `messpunkt` (VARCHAR(50)), über das der MQTT-Ingest eingehende Zählertelegramme auflöst (`EinheitRepository.findAllByOrgIdAndMesspunkt`).
  - `Mieter` hat **genau eine** Einheit (`einheit_id NOT NULL`) und einen Mietzeitraum; je Einheit ist höchstens ein Mieter ohne Mietende zulässig.
  - `tarifposition` hängt am Mieter (`mieter_id NOT NULL`), trägt Jahr/Quartal, Menge, `erfassungsart` und `quell_referenz`.
  - Eine Rechnung gilt je **Einheit + Mieter + Zeitraum** und wird nicht persistiert.
  - Die Ladestrom-Mengen werden **manuell** erfasst; eine Schnittstelle zum Lademanagement gibt es nicht.

### Getroffene Grundsatzentscheide

| Thema | Entscheid |
|---|---|
| **Ladestation = Einheit** | Neuer `EinheitTyp` **`LADESTATION`**. Keine neue Tabelle und **kein neues Attribut** — die Kennung steht im bereits vorhandenen Feld `einheit.messpunkt`. |
| **`messpunkt` = RFID** | Die Ladestationen werden mit einer **RFID** zum Starten des Ladevorgangs konfiguriert; das Lademanagement meldet die Mengen je RFID. Der `messpunkt` einer `LADESTATION`-Einheit ist genau diese RFID. |
| **Mieterwechsel** | Beim Auszug wird die RFID **invalidiert**, der Nachmieter erhält eine **neue** RFID und damit eine **neue** `LADESTATION`-Einheit. Die alte Einheit bleibt dem alten Mieter zugeordnet. **Folge:** Jede Einheit gehört über ihre ganze Lebensdauer genau einem Nutzer — eine Quartalsposition ist damit eindeutig einem Mieter zuzuordnen, auch wenn der Wechsel mitten im Quartal stattfindet. |
| **Anker der Tarifposition** | An der **Einheit** (`einheit_id`) statt am Mieter. Der Zeitbezug steckt in Jahr/Quartal der Position selbst. |
| **Mieter zu Einheiten: 1:n** | Ein Mieter kann **mehreren** Einheiten zugeordnet sein (Wohnung + Ladestation(en)), damit alles auf **einer** Rechnung erscheint. |
| **Nutzer ohne Wohnung** | Wird als Mieter-Datensatz geführt, dem **nur** eine `LADESTATION`-Einheit zugeordnet ist. Mietbeginn/Mietende sind dann der Nutzungszeitraum. |
| **Quell-Referenz** | Wird beim Erfassen aus dem `messpunkt` der gewählten Einheit vorbelegt und bleibt änderbar. |
| **Rechnungsfähigkeit** | Eine `LADESTATION`-Einheit erzeugt **nur dann** eine eigene Rechnung, wenn ihrem Mieter **keine** `CONSUMER`-Einheit zugeordnet ist (Nutzer ohne Wohnung). Hat der Mieter eine Wohnung, erscheinen seine Ladestrom-Positionen auf **deren** Rechnung — sonst erhielte er zwei Rechnungen mit derselben Zeile. Je Mieter entsteht **höchstens eine** Ladestations-Rechnung, auch wenn mehrere seiner Ladestationen für den Lauf gewählt sind. |
| **Keine Sonderbehandlung in der Verteilung** | `LADESTATION`-Einheiten nehmen **nicht** an der Solarverteilung teil, solange ihre Mengen aus dem Lademanagement stammen und nicht als Messwerte erfasst werden (§8). |

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Ablauf / Flow

1. Ein Benutzer legt in der **Einheiten-Verwaltung** eine Einheit vom Typ **Ladestation** an und trägt im Feld **Messpunkt** die **RFID** ein.
2. In der **Mieterverwaltung** ordnet er dem Mieter neben der Wohnung zusätzlich diese Ladestations-Einheit zu. Ein Nutzer ohne Wohnung erhält einen Mieter-Datensatz, dem ausschliesslich die Ladestations-Einheit zugeordnet ist.
3. Auf der Seite **Tarifpositionen** wählt er die **Einheit** (nur Einheiten vom Typ Ladestation stehen zur Auswahl), dann Tarif, Jahr + Quartal und die Menge in kWh. Die **Quell-Referenz** ist mit dem `messpunkt` der Einheit vorbelegt.
4. Existiert für diese **Einheit**, dieses Quartal und diesen **Tariftyp** bereits eine Position, wird das abgewiesen — die bestehende ist zu bearbeiten.
5. Bei der **Rechnungserzeugung** für Einheit + Mieter + Zeitraum werden die Positionen **aller Einheiten, die diesem Mieter zugeordnet sind** aufgenommen, deren Quartal sich mit dem Rechnungszeitraum überschneidet und deren Menge > 0 ist. Darstellung, Rundung und Einfluss auf Total/Rundung/Endbetrag bleiben wie in `Specs/Ladestromtarif.md` beschrieben.
   * Eine Rechnung entsteht wie bisher je `CONSUMER`- und `PRODUCER`-Einheit. Zusätzlich entsteht eine Rechnung für eine `LADESTATION`-Einheit, **wenn** ihrem Mieter keine `CONSUMER`-Einheit zugeordnet ist. Damit erhält ein Nutzer ohne Wohnung genau eine Rechnung, ein Mieter mit Wohnung genau eine — und niemand zweimal dieselbe Zeile.
   * `RechnungService.berechneRechnungen` verzweigt heute ausschliesslich auf `CONSUMER` und `PRODUCER`; der dritte Zweig ist **neu zu bauen**. Der Einheiten-Selektor der Rechnungsmaske filtert heute mit `[onlyConsumers]="true"` und muss Ladestations-Einheiten zusätzlich anbieten.
6. **Mieterwechsel:** Die alte RFID wird invalidiert; die alte Ladestations-Einheit bleibt dem ausziehenden Mieter zugeordnet und behält ihre Positionen. Für den Nachmieter wird eine neue Ladestations-Einheit mit neuer RFID angelegt und ihm zugeordnet.

> **Warum der Mieterwechsel keine Doppelverrechnung erzeugt:** Weil mit der RFID auch die Einheit wechselt, teilen sich zwei Mieter nie dieselbe Ladestations-Einheit. Die Quartalsposition des alten Mieters hängt an dessen Einheit, die des neuen an einer anderen — beide erscheinen ausschliesslich auf der jeweils eigenen Rechnung. Ein rein einheiten-gebundener Anker **ohne** diesen RFID-Wechsel würde die Position dagegen auf beiden Rechnungen zeigen.

### FR-2: Persistierung

* **Neuer Einheiten-Typ:** `EinheitTyp.LADESTATION`. **Geprüft (17.08.2026):** `zev.einheit` trägt nur den Primärschlüssel und den FK auf `organisation` — **keinen** CHECK-Constraint auf `typ`; die Spalte ist `VARCHAR(20)` und fasst `LADESTATION` (11 Zeichen). Für den Enum-Wert selbst ist deshalb **keine DDL** nötig. Die Prüfung war Pflicht, nicht Kür: Bei `tarif.tariftyp` zählte ein CHECK-Constraint die Werte auf und liess das Anlegen eines `LADESTROM`-Tarifs zur Laufzeit scheitern (`V102`).
* **Länge der RFID:** Das bestehende Feld `messpunkt` ist `VARCHAR(50)` — **entschieden: das genügt**, keine Spaltenerweiterung.
* **Eindeutigkeit der RFID:** Partieller Unique-Index `(org_id, messpunkt) WHERE typ = 'LADESTATION'`. Ein globaler Unique-Index auf `messpunkt` ist **nicht** möglich: `BEZUG` und `RUECKLIEFERUNG` teilen sich bewusst einen Messpunkt (Register-Projektion beim MQTT-Ingest).
* **Zuordnung Mieter ↔ Einheit (1:n):** Neue Tabelle `zev.mieter_einheit`. Die bestehende Spalte `mieter.einheit_id` wird überführt und danach entfernt. Der Mietzeitraum bleibt am Mieter — er gilt für alle seine Einheiten.

  | Spalte | Typ | Pflicht | Bemerkung |
  |---|---|---|---|
  | `org_id` | BIGINT | ja | Mandant, serverseitig gesetzt |
  | `mieter_id` | BIGINT | ja | FK auf `mieter`, **ON DELETE CASCADE** — mit dem Mieter verschwinden nur seine Zuordnungen, nicht die Einheiten |
  | `einheit_id` | BIGINT | ja | FK auf `einheit`, **ON DELETE RESTRICT** — eine Einheit mit Zuordnungen ist nicht löschbar (§5) |

  Primärschlüssel über (`mieter_id`, `einheit_id`), Index auf (`org_id`, `mieter_id`) für die Rechnungserzeugung.
* **Anker der Tarifposition:** `tarifposition.mieter_id` wird durch `einheit_id` (FK auf `einheit`, **ON DELETE CASCADE**) ersetzt. Unique-Constraint neu `(org_id, einheit_id, tarif_id, jahr, quartal)`, Index auf `(org_id, einheit_id, jahr, quartal)`. **Keine Datenmigration nötig** — die Tabelle ist leer (Stand 17.08.2026).
* **Löschen einer Einheit** wird **abgewiesen**, solange ihr Mieter zugeordnet sind (`ON DELETE RESTRICT`, Meldung mit Anzahl betroffener Mieter). Sonst könnte über die Einheiten-Verwaltung ein Mieter ohne Einheit entstehen — die Regel „mindestens eine Zuordnung" würde nur im Mieter-Formular greifen.
* **Löschen eines Mieters** wird **abgewiesen**, solange an einer ihm zugeordneten Einheit Tarifpositionen hängen (Meldung mit Anzahl). Sonst blieben abrechnungsrelevante Positionen ohne Empfänger zurück. Ohne Positionen verschwinden nur die Zuordnungen, die Einheiten bleiben bestehen.
* **Eindeutigkeit je Tariftyp** bleibt die Regel, bezieht sich aber neu auf die Einheit: höchstens eine Position je (`org_id`, `einheit_id`, `jahr`, `quartal`, **Tariftyp**). Geprüft im Service, weil der Typ am Tarif hängt.

### FR-3: Layout

* **Einheiten-Verwaltung:** Der Typ **Ladestation** erscheint im bestehenden Typ-Dropdown. Der Hinweistext zum Feld Messpunkt weist für diesen Typ auf die RFID hin.
* **Mieterverwaltung:** Das Mieter-Formular erlaubt die Zuordnung **mehrerer** Einheiten (Mehrfachauswahl statt Einzel-Dropdown). Mindestens eine Einheit ist Pflicht. Die Liste zeigt die zugeordneten Einheiten.
* **Tarifpositionen:** Die Auswahl oben wechselt von **Mieter** auf **Einheit**; angeboten werden nur Einheiten vom Typ Ladestation. Die Liste bleibt unverändert (sortierbar, Spaltenbreiten veränderbar, Kebab mit Bearbeiten/Kopieren/Löschen). Die Quell-Referenz wird aus dem `messpunkt` vorbelegt.
* **Kebab-Sprung:** Der Eintrag „Tarifpositionen" wandert von der Mieter- in die **Einheiten**-Verwaltung und springt mit `?einheitId=…`.
* **Einheiten-Auswahllisten:** Ladestations-Einheiten erscheinen in der **Rechnungserzeugung** (nötig für den Nutzer ohne Wohnung, FR-1.5), im **Messwerte-Chart** und im **Messwerte-Upload** — entschieden: dort wird nichts ausgeblendet. Einzig die Seite Tarifpositionen zeigt **ausschliesslich** Ladestations-Einheiten.
* Alle Texte via `TranslationService`/`TranslatePipe`, Zahlenformatierung nach `Specs/generell.md`.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

### Einheit
* [ ] In der Einheiten-Verwaltung lässt sich eine Einheit vom Typ **Ladestation** anlegen, bearbeiten und löschen.
* [ ] Eine RFID (`messpunkt`), die bereits einer anderen Ladestations-Einheit desselben Mandanten gehört, wird **abgewiesen** (Meldung, kein Datensatz).
* [ ] Dieselbe Zeichenfolge darf weiterhin bei `BEZUG` und `RUECKLIEFERUNG` doppelt vorkommen — der Ingest-Pfad bleibt unverändert.
* [ ] Eine Ladestations-Einheit ohne Messwerte beeinflusst die Solarverteilung und den Bilanzabgleich **nicht**.
* [ ] Das Löschen einer Einheit, der ein Mieter zugeordnet ist, wird **abgewiesen** (Meldung mit Anzahl, kein Datensatz gelöscht).

### Mieter
* [ ] Einem Mieter lassen sich **mehrere** Einheiten zuordnen; die Zuordnung bleibt nach dem Bearbeiten erhalten.
* [ ] Ein Mieter **ohne** zugeordnete Einheit wird abgewiesen.
* [ ] Ein Mieter, dem **nur** eine Ladestations-Einheit zugeordnet ist, lässt sich anlegen (Nutzer ohne Wohnung).
* [ ] Die Regel „höchstens ein Mieter ohne Mietende je Einheit" gilt weiterhin **je zugeordneter Einheit**.
* [ ] Das Löschen eines Mieters wird **abgewiesen**, solange an einer ihm zugeordneten Einheit Tarifpositionen hängen; ohne Positionen ist es zulässig und lässt die Einheiten bestehen.

### Erfassung
* [ ] Auf der Seite Tarifpositionen sind ausschliesslich Einheiten vom Typ **Ladestation** wählbar.
* [ ] Die Quell-Referenz ist beim Anlegen mit dem `messpunkt` der gewählten Einheit vorbelegt und änderbar.
* [ ] Eine zweite Position für dieselbe **Einheit**, dasselbe Quartal und denselben **Tariftyp** wird abgewiesen — auch bei einem anderen `LADESTROM`-Tarif.
* [ ] Zwei Positionen desselben Quartals für **zwei verschiedene** Ladestations-Einheiten desselben Mieters sind zulässig.
* [ ] Positionen sind mandantengetrennt (`org_id`).
* [ ] Für eine Ladestations-Einheit ohne zugeordneten Mieter lassen sich Positionen erfassen; die Ansicht weist darauf hin, dass sie auf keiner Rechnung erscheinen.

### Rechnung
* [ ] Eine Rechnung für Mieter M enthält die Positionen **aller** M zugeordneten Einheiten, deren Quartal sich mit dem Zeitraum überschneidet und deren Menge > 0 ist.
* [ ] **Mieterwechsel im Quartal:** Alter Mieter (alte RFID-Einheit) und neuer Mieter (neue RFID-Einheit) haben je eine Q1-Position → jeder erhält **ausschliesslich seine eigene** Position, obwohl beide Rechnungen Q1 überschneiden.
* [ ] Ein Mieter mit zwei Ladestations-Einheiten erhält **beide** Positionen als je eigene Zeile — auf **einer** Rechnung, auch wenn beide Einheiten für den Lauf ausgewählt sind.
* [ ] Ein Nutzer ohne Wohnung erhält eine Rechnung, die ausschliesslich seine Ladestrom-Zeile(n) enthält.
* [ ] Rechnungen von Einheiten ohne Ladestations-Zuordnung sind **unverändert** zu vorher.
* [ ] Für einen Mieter mit Wohnung **und** Ladestation entsteht **eine** Rechnung (die der Wohnung) — auch wenn beide Einheiten für den Lauf ausgewählt sind.
* [ ] Wird eine Einheit ausgewählt, für die im Zeitraum keine Rechnung entsteht, nennt eine Meldung diese Einheit namentlich.

### Berechtigungen
* [ ] Ohne `einheit:write` lässt sich keine Einheit anlegen oder ändern → `403`.
* [ ] Ohne `mieter:manage` lässt sich die Zuordnung Mieter ↔ Einheit nicht ändern → `403`.
* [ ] Ohne `rechnungen:manage` lässt sich keine Tarifposition anlegen, ändern oder löschen → `403`.
* [ ] Ohne `mieter:read` lassen sich die Positionen nicht lesen → `403`.
* [ ] Nicht authentifiziert → `401`.

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Die zusätzliche Zuordnungstabelle darf die Rechnungserzeugung nicht spürbar verlangsamen: Zugriff über Index auf (`org_id`, `mieter_id`).

### NFR-2: Sicherheit
* Einheiten (inkl. Typ und `messpunkt`) sind **Stammdaten**: `einheit:write` (org_admin) — unverändert.
* Die Zuordnung Mieter ↔ Einheit gehört zu den Mieter-Stammdaten: `mieter:manage`.
* Tarifpositionen bleiben operative Abrechnungsdaten: Schreiben `rechnungen:manage`, Lesen `mieter:read`.
* Mandantentrennung über `org_id` + Hibernate-`@Filter` für die neue Zuordnungstabelle wie für alle Entities; `org_id` wird **serverseitig** gesetzt.

### NFR-3: Kompatibilität
* Der neue Einheiten-Typ wird an **37 Stellen in 8 Backend-Dateien** ausgewertet (`MesswerteRepository`, `EinheitService`, `MesswerteService`, `MqttIngestService`, `RechnungService`, `StatistikService`, `ZaehlerAggregationService`, `Einheit`). Jede Stelle ist zu prüfen; die meisten Prüfungen lauten `== CONSUMER` und greifen für `LADESTATION` von selbst nicht.
* **Ausnahmen von dieser Faustregel** — hier entscheidet eine Aufzählung, nicht ein Vergleich:
  - `StatistikService.TYP_ANZEIGE_REIHENFOLGE` listet die vier bestehenden Typen auf. Ladestations-Einheiten fehlen dadurch automatisch in „Summen pro Einheit" — **gewollt**, damit sie die Statistik nicht mit leeren Reihen füllen. Beim Durchgehen der Verzweigungen nicht versehentlich „korrigieren".
  - `RechnungService.berechneRechnungen` verzweigt auf `CONSUMER`/`PRODUCER` und braucht den neuen dritten Zweig (FR-1.5).
* `tarifposition` ist leer — der Wechsel des Ankers braucht **keine** Datenübernahme.
* `mieter.einheit_id` ist gefüllt (11 Datensätze) und muss in die Zuordnungstabelle überführt werden.
* Rechnungen ohne Ladestations-Beteiligung müssen **bit-identisch** bleiben.

## 5. Edge Cases & Fehlerbehandlung

| Fall | Verhalten |
|------|-----------|
| Keine Ladestations-Einheit erfasst | Seite Tarifpositionen zeigt einen Hinweis statt einer leeren Auswahl |
| Ladestations-Einheit ohne zugeordneten Mieter | Positionen sind erfassbar, erscheinen aber auf keiner Rechnung — die Ansicht weist darauf hin |
| Gewählte Einheit ergibt im Zeitraum keine Rechnung | Eine Meldung nennt die übersprungenen Einheiten namentlich. Betrifft die Ladestation ohne Positionen, den Produzenten ohne Grundgebühr-Tarif und die Einheit, deren Mietverhältnis den Zeitraum nicht berührt — bisher verschwanden diese Fälle kommentarlos |
| Ladestations-Einheit wird gelöscht | **Abgewiesen**, solange ihr ein Mieter zugeordnet ist (Meldung mit Anzahl). Ohne Zuordnung löschbar; die Positionen der Einheit verschwinden mit ihr (`ON DELETE CASCADE`) |
| Mieter wird gelöscht | **Abgewiesen**, solange an einer zugeordneten Einheit Positionen hängen (Meldung mit Anzahl). Ohne Positionen werden nur die Zuordnungen gelöscht, die Einheiten bleiben bestehen |
| Letzte Einheit eines Mieters entfernt | Abgewiesen — mindestens eine Zuordnung ist Pflicht |
| Zwei Mieter beanspruchen dieselbe Einheit mit offenem Mietende | Abgewiesen (bestehende Regel, neu je Zuordnung geprüft) |
| RFID doppelt erfasst | Abgewiesen mit Meldung, kein Datensatz |
| Ungültige Eingaben (negative Menge, Quartal 5, fehlender Tarif) | `400` mit verständlicher Meldung, kein Datensatz |
| Netzwerkfehler im Frontend | Meldung über den Message-Bereich, Formulareingaben bleiben erhalten |
| Leere Liste | Hinweistext statt leerer Tabelle |

## 6. Abhängigkeiten & betroffene Funktionalität

* **Voraussetzungen:** `Specs/Ladestromtarif.md` ist umgesetzt (Tabelle `tarifposition`, `TarifTyp.LADESTROM`, Seite `/tarifpositionen`).
* **Betroffener Code:**
  - `entity/EinheitTyp.java`, `entity/Einheit.java`, `service/EinheitService.java`, `repository/EinheitRepository.java` — neuer Typ, RFID-Eindeutigkeit
  - `entity/Mieter.java`, `repository/MieterRepository.java` (vier Finder über `einheitId`), `service/MieterService.java` (Überschneidungs- und Mietende-Prüfungen), `service/DebitorService.java`
  - `entity/Tarifposition.java`, `repository/TarifpositionRepository.java`, `service/TarifpositionService.java`, `dto/TarifpositionDTO.java`, `controller/TarifpositionController.java`
  - `service/RechnungService.java` — Positionen über die Einheiten des Mieters statt über den Mieter
  - Frontend: `models/mieter.model.ts`, `models/einheit.model.ts`, `models/tarifposition.model.ts`, `components/mieter-form`, `components/mieter-list`, `components/einheit-form`, `components/einheit-list`, `components/tarifposition-list`, `components/tarifposition-form`
  - Tests: `TarifpositionServiceTest`, `TarifpositionControllerTest`, `TarifpositionRepositoryIT`, `RechnungServiceTest`, `MieterServiceTest`, `MieterRepositoryIT`, `EinheitServiceTest`, die zugehörigen Frontend-Specs sowie `tests/ladestromtarif.spec.ts` — sie prüfen durchgehend den Mieter-Anker
* **Datenmigration:** `mieter.einheit_id` → `mieter_einheit`, danach Spalte entfernen. Für `tarifposition` keine Migration (leer). Neue Übersetzungen für Typ, Hinweise und Fehlermeldungen.

## 7. Abgrenzung / Out of Scope

* **Keine Schnittstelle zum Lademanagement.** Die Mengen werden weiterhin manuell erfasst; der automatische Import ist der Folgeschritt und braucht eine eigene Spec (Protokoll, Granularität, Umgang mit bereits erfassten Positionen).
* **Keine Messwerte an Ladestations-Einheiten.** Sie erhalten keine Zählerdaten und nehmen nicht an Aggregation, Verteilung oder Statistik teil.
* **Keine Vertragspartner-Entität.** `Mieter` bleibt zugleich Mietverhältnis und Rechnungsempfänger; ein Nutzer, der Wohnung und Ladestation hat, bleibt **ein** Mieter-Datensatz — mehrere Datensätze derselben Person entstehen nur, wenn Nutzungszeiträume auseinanderfallen. Die saubere Trennung ist ein eigenes Vorhaben (§8).
* **Keine Verwaltung der RFID-Karten** (Ausgabe, Sperrung, Gültigkeit) — das macht das Lademanagement. ZEV kennt nur die Kennung.
* **Keine anteilige Aufteilung** einer Position auf Teilzeiträume.

## 8. Offene Fragen

* [ ] **Solarverteilung und Bilanz:** Die Annahme ist, dass Ladestations-Einheiten **nicht** an der Verteilung teilnehmen. Sobald der Ladestrom physisch hinter dem Hausanschluss bezogen wird, taucht er aber im BEZUG auf, ohne dass ihn ein Konsument beansprucht — der Vergleich in der Statistik würde „UNGLEICH" melden. Zu klären, sobald Messwerte fliessen (`Specs/Bilanzmodell.md`).
* [ ] **Wachsende Zahl inaktiver Einheiten:** Bei jedem Mieterwechsel entsteht eine neue Ladestations-Einheit; alte bleiben für die Historie bestehen. Braucht es einen Aktiv-Filter in den Auswahllisten, oder genügt die Sortierung?
* [ ] **Vertragspartner-Entität (Fernziel):** `Mieter` vermischt Mietverhältnis und Rechnungsempfänger. Fallen Wohnungs- und Ladestations-Nutzung zeitlich auseinander, entstehen zwei Datensätze derselben Person mit dupliziertem Namen und Adresse — und zwei Rechnungen. Eine eigene Vertragspartner-Entität räumt das auf; eigenes Vorhaben, das Rechnung, Debitor und Mieterverwaltung berührt.
