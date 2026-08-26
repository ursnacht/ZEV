# Rechnungen aus der Nebenkostenabrechnung

> **Teil des Bereichs Nebenkosten.** Die Abrechnung selbst ist in
> [`Abrechnung.md`](./Abrechnung.md) beschrieben und umgesetzt. Dieses Dokument löst zwei Punkte
> aus deren Abgrenzung ein: den **Ausdruck je Mieter** und die **Debitorenbuchung**.

## 1. Ziel & Kontext - Warum wird das Feature benötigt?

* **Was soll erreicht werden:** Aus einer abgeschlossenen Nebenkostenabrechnung soll je Mieter eine
  **Rechnung als PDF** entstehen — mit den Positionszeilen, dem Akonto und dem Saldo, samt
  Schweizer QR-Einzahlungsschein. Eine Nachzahlung wird zusätzlich als **Debitor** gebucht, damit
  sie in der Debitorenkontrolle nachverfolgbar ist. In der Debitorenkontrolle muss erkennbar sein,
  **woher** eine Forderung stammt: aus der Stromabrechnung (`ZEV`) oder aus den Nebenkosten (`NK`).
* **Warum machen wir das:** Die Nebenkostenabrechnung rechnet heute korrekt, endet aber am
  Bildschirm. Der Mieter erhält nichts, und die Forderung existiert in der Anwendung nicht — sie
  müsste ausserhalb geführt werden, also genau dort, wovon die Nebenkostenabrechnung wegführen
  sollte. Ohne Herkunftskennzeichen wäre eine gemischte Debitorenliste zudem nicht lesbar: Zwei
  Forderungen desselben Mieters für denselben Zeitraum wären nicht unterscheidbar.
* **Aktueller Stand:**
  - **Abrechnung vorhanden:** `NkAbrechnungService.getAbrechnungDetail(Long)` liefert je Mieter
    `zeilen`, `kostentotal`, `akontoTotal` und `saldo` (`NkMieterAbrechnungDTO`). Ein **positiver**
    Saldo ist eine Nachzahlung, ein negativer ein Guthaben.
  - **Kein Ausdruck, keine Buchung:** `Abrechnung.md`, Abschnitt 7 hält beides ausdrücklich als
    out of scope fest.
  - **Rechnungsgenerierung vorhanden, aber nur für ZEV:** `POST /api/rechnungen/generate` nimmt
    `von`, `bis`, `einheitIds`, `sprache`; `RechnungService` rechnet, `RechnungPdfService` füllt
    `reports/rechnung.jrxml` samt QR-Zahlteil, `RechnungStorageService` hält das PDF für den
    Download, und `RechnungController` bucht den Debitor.
  - **Debitor ohne Herkunft:** `zev.debitor` hat keine Spalte dafür, und der Unique-Key ist
    `UNIQUE (mieter_id, datum_von, org_id)`.
  - **Geldtyp einheitlich:** Seit dem 24.08.2026 rechnet auch die Quartalsrechnung mit
    `BigDecimal` — NK, Quartalsrechnung und Debitor verwenden denselben Typ, es wird an keiner
    Grenze umgerechnet.
  - **Die NK-Liste trägt schon die passenden Bedienelemente:** Jede Zeile in
    `nebenkosten-abrechnung.component.html` hat die Checkbox `abgerechnet` (Zeile 63) und ein
    Kebab-Menü mit `onMenuAction(action, abrechnung)` (Zeile 68).

### Der Ort der Auslösung — Entscheid

Die Rechnungen entstehen **auf der Seite Nebenkosten → Abrechnung**, als Zeilenaktion im
Kebab-Menü — **nicht** auf der Seite Rechnungen.

Begründung:

* **Es ist eine Aktion am Objekt.** Wer eine Abrechnung abschliesst, will sie danach abrechnen —
  derselbe Handgriff wie das Setzen von `abgerechnet` in der Nachbarspalte. Auf `/rechnungen` wäre
  es ein Moduswechsel plus eine zweite Auswahl derselben Abrechnung.
* **Es entfällt die Auswahl.** Die Zeile *ist* die Auswahl. Kein `zev-select` mit abgeschlossenen
  Abrechnungen, kein Ausblenden von Quartal-Selektor und Datumsfeldern, kein Leeren einer
  gemischten Ergebnisliste beim Umschalten.
* **Der Zustand `abgerechnet` wird trivial durchsetzbar.** Der Menüeintrag erscheint nur auf Zeilen
  mit gesetztem Flag; ein Zustand „geladen, aber inzwischen wieder geöffnet" muss nicht im Browser
  nachgeführt werden. Die serverseitige Prüfung bleibt trotzdem (FR-2).
* **Der Endpunkt bleibt sauber.** `POST /api/rechnungen/generate` wird **nicht** angefasst: keine
  art-abhängige Validierung anstelle der heutigen `@NotNull`/`@NotEmpty`, keine Antwort, die je
  nach Art anders aussieht. Der NK-Lauf bekommt einen eigenen Endpunkt mit eigener, fest
  definierter Antwort (FR-6).
* **Flag und Berechtigung kommen mit.** Die Seite hängt bereits am Feature-Flag, die Route an
  `nebenkosten:manage`.

**Preis, der bewusst bezahlt wird:** Die Ergebnisdarstellung samt Download gibt es auf der Seite
Rechnungen schon; auf der NK-Seite ist sie neu zu bauen (FR-7). Und wer „Rechnungen" sucht, geht
zuerst ins Menü Rechnungen — deshalb steht dort ein Hinweis auf den NK-Bereich (FR-7). Ein Satz ist
billiger als eine zweite Bedienoberfläche.

### Der Unique-Key der Debitoren kollidiert — Entscheid

`uq_debitor_mieter_von_org` ist `(mieter_id, datum_von, org_id)`, und das Buchen läuft als
**Upsert** (`DebitorRepository.upsert`, `ON CONFLICT`). Eine NK-Jahresabrechnung 01.01.–31.12.2026
und die ZEV-Quartalsrechnung Q1/2026 haben denselben `datum_von` — die NK-Buchung würde die
ZEV-Forderung desselben Mieters **stillschweigend überschreiben**.

Die Herkunft gehört deshalb **in den Schlüssel** und nicht bloss in eine Anzeigespalte:
`UNIQUE (mieter_id, datum_von, herkunft, org_id)`. Damit bleibt das Upsert je Herkunft idempotent
— ein wiederholter Lauf aktualisiert seine eigene Forderung und lässt die andere unberührt.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Ablauf / Flow

1. Der Benutzer öffnet **Nebenkosten → Abrechnung** (`/nebenkosten/abrechnung`).
2. In der Zeile einer **abgeschlossenen** Abrechnung (`abgerechnet` gesetzt) enthält das
   Kebab-Menü den Eintrag **Rechnungen erstellen**. Auf einer noch offenen Abrechnung fehlt der
   Eintrag.
3. Ein Klick darauf fragt zurück (`confirm`, übersetzter Text): Es entstehen Forderungen, und das
   ist keine Aktion, die man beim Zielen aufs Kebab-Menü versehentlich auslösen soll.
4. Nach Bestätigung erzeugt der Server für **alle** Mieter der Abrechnung je ein PDF und bucht für
   jeden **positiven** Saldo einen Debitor.
5. Unter der Liste erscheint ein **Ergebnis-Panel** mit einer Zeile je Mieter: Name, Saldo, ob eine
   Forderung entstanden ist, und ein Download-Knopf. Über der Tabelle stehen die Zahl der
   erzeugten Rechnungen, die Zahl der gebuchten Forderungen und deren Summe.
6. Mieter mit Saldo **≤ 0** erscheinen mit ihrem PDF, aber **ohne** Forderung; die Zeile weist das
   aus (FR-4).
7. Ein zweiter Lauf über dieselbe Abrechnung ist erlaubt und ändert nichts an den Daten
   (Upsert je Herkunft); das Ergebnis-Panel wird ersetzt, nicht ergänzt.

### FR-2: Absicherung des Zustands `abgerechnet`

* **Nur `abgerechnet`e Abrechnungen** sind abrechenbar (Entscheid). Das Flag bedeutet laut
  `Abrechnung.md` „fertig" und sperrt die Erfassungsmaske. Rechnungen aus einem noch veränderlichen
  Stand wären morgen falsch, und der Debitor stünde mit einem Betrag da, den niemand mehr
  nachvollziehen kann.
* Im Frontend steuert das die **Sichtbarkeit des Menüeintrags**, nicht ein gesperrter Knopf: Ein
  ausgegrauter Eintrag müsste erklären, warum er ausgegraut ist.
* Das Backend prüft das Flag **erneut** — die Sichtbarkeit im Browser ist keine Absicherung. Eine
  Abrechnung, die zwischen Laden und Klick wieder geöffnet wurde, wird mit **400** und
  `NK_FEHLER_NICHT_ABGERECHNET` abgewiesen (`IllegalStateException`; `GlobalExceptionHandler`
  bildet sie auf 400 mit `{"error": "..."}` ab, Zeile 49–54 — ein eigener Status wäre eine neue
  Zuordnung für einen einzigen Fall).
* **Umsetzungshinweis:** `menuItems` ist heute **eine** Instanz für alle Zeilen. Der neue Eintrag
  hängt am Zustand der Zeile, die Liste wird also zeilenabhängig. Sie darf **nicht** je
  Änderungserkennung neu gebaut werden: Das Kebab-Menü rendert mit `@for (item of items; track
  item)` — eine frisch erzeugte Liste hat neue Objektidentitäten und löst `NG0956` samt Neuaufbau
  des Menüs bei jedem Zyklus aus. Zwei **feste** Listen (mit und ohne Eintrag) und eine Methode,
  die eine davon zurückgibt.

### FR-3: Berechnung der Rechnung

* Es wird **nicht neu gerechnet.** Die Rechnung übernimmt die Werte, die
  `NkAbrechnungService.getAbrechnungDetail` für die Abrechnung liefert — dieselbe Quelle, die die
  Maske anzeigt. Zwei Rechenwege ergäben zwei Wahrheiten.
* Je Mieter entsteht eine Rechnung aus:
  * den **Zeilen** des Mieterblocks (`NkZeileDTO`): `bezeichnung`, `menge`, `betragProEinheit`,
    `prozentsatz`, `betrag` — je Positionsart gefüllt;
  * dem **Akonto**: `akontoAnzahlMonate` × `akontoBetragProMonat`, zuzüglich `akontoKorrektur`;
  * dem **Saldo**: `kostentotal − akontoTotal`.
* **Rundung des Endbetrags auf 5 Rappen** (`RechnungService.roundTo5Rappen`). Begründung: Der
  QR-Zahlteil verlangt einen zahlbaren Betrag. Die **Zeilenrundung auf 1 Rappen** aus
  `Abrechnung.md` FR-5 bleibt unberührt — gerundet wird nur der Endbetrag, und die Differenz wird
  auf der Rechnung als **Rundung** ausgewiesen, wie bei der Quartalsrechnung.
* **Mieter ohne Wohnung** (`ohneWohnung`) erhalten eine Rechnung, wenn ihr Kostentotal ungleich 0
  ist — sie können Zusatzzeilen tragen (`Abrechnung.md` FR-3).

### FR-4: Guthaben (negativer Saldo)

* **Saldo > 0** (Nachzahlung): PDF **und** Debitor.
* **Saldo ≤ 0** (Guthaben oder Null): PDF, **kein** Debitor (Entscheid).
  * Begründung: `debitor.betrag` trägt `CHECK (betrag > 0)` und `@DecimalMin("0.01")`; ein
    Guthaben liesse sich dort nicht ablegen. Dasselbe Verhalten gilt heute bei der
    0-Quartalsrechnung (`Specs/RechnungenGenerieren.md`, Abschnitt 2).
  * Die Ergebniszeile weist es aus, damit es nicht wie ein Fehler aussieht: Der Mieter bekommt
    seinen Beleg, es entsteht nur keine Forderung.
* **Akzeptierte Folge:** Die Rückzahlung eines Guthabens ist in der Anwendung nicht nachverfolgbar.
  Das ist bewusst in Kauf genommen, um `debitor` nicht anzufassen; ein Kreditoren-Begriff wäre eine
  eigene Entscheidung (Abschnitt 8).

### FR-5: Persistierung

**`zev.debitor` erhält eine Spalte `herkunft`:**

| Spalte | Typ | Pflicht | Bemerkung |
|---|---|---|---|
| `herkunft` | VARCHAR(10) | ✅ | `ZEV` \| `NK`, CHECK-Constraint; Default `'ZEV'` für den Bestand |

* Neues Enum `ch.nacht.entity.Debitorherkunft` mit `ZEV` und `NK`; im Frontend als
  `DebitorHerkunft = 'ZEV' | 'NK'` in `debitor.model.ts`.
* **Unique-Key ersetzen:** `uq_debitor_mieter_von_org` fällt,
  `UNIQUE (mieter_id, datum_von, herkunft, org_id)` tritt an seine Stelle (siehe Abschnitt 1).
  `DebitorRepository.upsert` führt `herkunft` in `ON CONFLICT` mit; die Signatur wächst um den
  Parameter (heute `upsert(mieterId, betrag, datumVon, datumBis, orgId)`), ebenso
  `DebitorService.upsertFromRechnung`.
* **Migration** (nächste freie Nummer: **V126**, höchste vorhandene ist V125):
  1. Spalte anlegen, `NOT NULL DEFAULT 'ZEV'` — der Bestand ist ausschliesslich aus der
     Stromabrechnung entstanden, `ZEV` ist also die korrekte Rückschreibung und keine Annahme.
  2. CHECK-Constraint auf die beiden Werte.
  3. Alten Unique-Constraint löschen, neuen anlegen.
  4. Übersetzungsschlüssel (FR-7), abschliessend mit `ON CONFLICT (key) DO NOTHING`.

**PDF-Ablage** — sie liegen wie die Quartalsrechnungen flüchtig in `RechnungStorageService`
(30 Minuten, org-getrennt über den internen Präfix `orgId:`). Der Speicher bekommt dafür einen
**expliziten Namensraum**:

* `store`, `get` und `exists` nehmen die **Rechnungsart** als eigenen Parameter (Enum
  `Rechnungsart` mit `ZEV` und `NK`); sie landet als eigenes Segment im internen Schlüssel
  (`orgId:NK:12_45`).
* **Warum nicht einfach ein Präfix `nk_` im Schlüssel:** Er wäre nicht disjunkt von den
  ZEV-Schlüsseln. Die entstehen aus `einheitName + "_" + mieterId` durch `sanitizeKey`, und das
  ersetzt **alle** Leerzeichen durch `_` und behält `[a-zA-Z0-9_äöüÄÖÜ-]`
  (`RechnungStorageService:97-102`). Eine Einheit „nk 12" mit `mieterId = 45` ergäbe genau
  `nk_12_45` — die beiden PDF überschrieben sich im selben Mandanten. Ein Namensraum, der aus
  Benutzereingaben nachgebildet werden kann, ist kein Namensraum.
* **Der Dateiname wird mitgespeichert**, statt aus dem Schlüssel abgeleitet zu werden. Damit ist
  der Schlüssel frei wählbar (für NK `<abrechnungId>_<mieterId>`) und der Download liefert
  trotzdem einen lesbaren Namen (`Nebenkosten_2026_Muster_Hans.pdf`). Für ZEV bleibt der Name
  derselbe wie heute — der Dateiname ist sichtbar, eine Änderung dort wäre eine Änderung am
  bestehenden Verhalten.
* **Aufräumen je Art statt mandantenweit.** `clearAll()` löscht heute *alle* PDF des Mandanten
  (Zeile 82–88). Weil die beiden Arten jetzt von zwei Seiten aus laufen, nähme ein ZEV-Lauf die
  offenen NK-Downloads mit und umgekehrt — der Download antwortete stumm mit `404`. Die Methode
  wird deshalb zu **`clearArt(Rechnungsart)`**: exakt über das Namensraum-Segment, nicht über einen
  Schlüsselvergleich. Der ZEV-Lauf ruft sie mit `ZEV` auf, sonst unverändert.
* Der **NK-Lauf räumt nicht auf.** Ein erneuter Lauf schreibt dieselben Schlüssel neu; alte
  Schlüssel anderer Abrechnungen verfallen nach 30 Minuten von selbst und stehen in keiner
  Ergebnisliste.
* Ein abgelaufener Schlüssel bleibt trotzdem möglich (30 Minuten). Der Download antwortet dann mit
  `404`, und die Seite zeigt einen übersetzten Hinweis, die Rechnung sei erneut zu erzeugen — die
  NK-Rechnung ist aus der abgeschlossenen Abrechnung jederzeit reproduzierbar.

**Keine gespeicherte NK-Rechnung.** Es entsteht keine Tabelle für erzeugte Rechnungen — die
Rechnung ist jederzeit aus der Abrechnung reproduzierbar, solange diese `abgerechnet` ist.

### FR-6: REST-Endpunkte

**Neu: `POST /api/nebenkosten/abrechnungen/{abrechnungId}/rechnungen`**

Ein eigener Endpunkt in einem neuen `NkRechnungController`.
`POST /api/rechnungen/generate` bleibt **unverändert** — kein neues Feld `art`, keine
art-abhängige Validierung, keine geänderte Antwort, keine Rückwärtskompatibilitätsfrage.

*Request* (der Rest steht im Pfad):

| Feld | Typ | Pflicht | Bemerkung |
|---|---|---|---|
| `sprache` | `String` | ❌ | Default `"de"`, wie bei ZEV |

*Antwort* `200 OK` — fest definiert, weil sie nur eine Form hat:

```json
{
  "abrechnungId": 12,
  "bezeichnung": "Nebenkosten 2026",
  "von": "2026-01-01",
  "bis": "2026-12-31",
  "anzahlRechnungen": 7,
  "anzahlForderungen": 5,
  "summeForderungen": 4310.55,
  "rechnungen": [
    {
      "mieterId": 45,
      "mieterName": "Muster Hans",
      "saldo": 812.35,
      "forderungGebucht": true,
      "filename": "Nebenkosten_2026_Muster_Hans.pdf",
      "fehler": null
    }
  ]
}
```

* `saldo` ist der **gerundete** Endbetrag — derselbe Wert wie im PDF und im Debitor.
* `forderungGebucht` ist `false` bei Saldo ≤ 0. Die Zahlen `anzahlRechnungen` und
  `anzahlForderungen` stehen getrennt, damit „0 Forderungen" nicht wie ein Fehlschlag aussieht.
* **Kein `downloadKey`.** Der Download läuft über `abrechnungId` und `mieterId`, die schon in der
  Antwort stehen (siehe unten) — ein Schlüssel im Nutzdatenteil wäre ein zweiter Namensraum neben
  dem der Ablage.
* `fehler` trägt bei einem gescheiterten Mieter den Schlüssel **`NK_FEHLER_RECHNUNG_MIETER`**, sonst
  `null`; ein PDF ist für diesen Mieter dann nicht abholbar. Der Lauf bricht nicht ab (Abschnitt 5).

*Reihenfolge je Mieter:* **erst den Debitor buchen, dann das PDF ablegen** — wie im ZEV-Pfad
(`RechnungController:104-122`, samt Kommentar „if upsert fails, no PDF is stored"). Scheitert das
Ablegen danach, bleibt die gebuchte Forderung stehen und die Zeile trägt `fehler`: Die Rechnung ist
aus der abgeschlossenen Abrechnung jederzeit neu erzeugbar, eine Forderung ohne Beleg ist also ein
behebbarer Zustand — eine stille Rücknahme der Buchung wäre der schlechtere Weg, weil sie den
Mieter aus der Debitorenkontrolle verschwinden liesse.

*Fehlerantworten:*

| Status | Fall |
|---|---|
| `403` | Permission fehlt, oder Feature-Flag `NEBENKOSTENABRECHNUNG` aus (FR-9) |
| `404` | `abrechnungId` unbekannt **oder** von einem fremden Mandanten — nicht unterscheidbar |
| `400` | Abrechnung ist nicht `abgerechnet`; Body `{"error": "NK_FEHLER_NICHT_ABGERECHNET"}` |

*Autorisierung:* `@PreAuthorize("hasAuthority('nebenkosten:manage') and
hasAuthority('rechnungen:manage')")` auf Klassenebene. **Beide**, weil hier zwei Dinge zusammen
passieren: Es ist eine NK-Aktion, aber sie stellt Rechnungen und bucht Forderungen. Heute halten
alle drei Fachrollen beide Permissions (`Specs/Berechtigungen.md`), die Forderung ist also keine
Einschränkung — aber sie bleibt richtig, wenn die Rollen später auseinanderlaufen.

**Neu: `GET /api/nebenkosten/abrechnungen/{abrechnungId}/rechnungen/{mieterId}/pdf`**

Ein eigener Download im `NkRechnungController`, statt `GET /api/rechnungen/download/{key}`
mitzubenutzen. Das ist die Folge des Namensraums in der Ablage (FR-5): Der bestehende Endpunkt
kennt nur einen Schlüssel und müsste ihm ansehen, welcher Art er ist — genau die Erkennung am
Schlüsselstring, die FR-5 abschafft. Über die Route ist die Art **strukturell** gegeben.

* Antwort `200` mit `Content-Type: application/pdf` und dem gespeicherten Dateinamen im
  `Content-Disposition`; `404`, wenn der Schlüssel abgelaufen oder unbekannt ist.
* Es wird **keine Logik kopiert**: Der Endpunkt liest aus demselben `RechnungStorageService`, nur
  mit `Rechnungsart.NK`. Doppelt ist allein das Zusammenbauen der `ResponseEntity`.
* **Der Feature-Flag greift hier mit** — ohne Präfix-Erkennung, weil der Endpunkt im NK-Controller
  liegt und `NkRechnungService` den Flag ohnehin prüft (FR-9). Nach dem Abschalten ist auch ein
  bereits erzeugtes PDF nicht mehr abholbar.
* `GET /api/rechnungen/download/{key}` bleibt **unverändert** — gleiche Route, gleiche Schlüssel,
  gleiche Dateinamen für ZEV.

**Debitoren:**

* `GET /api/debitoren` liefert `herkunft` im `DebitorDTO` mit; die Liste lässt sich optional nach
  Herkunft filtern.
* `POST`/`PUT /api/debitoren`: Fehlt `herkunft` im Request, setzt der Server **`ZEV`** — bestehende
  Aufrufer und Tests bleiben gültig, und der Bestand ist ohnehin `ZEV`. Ein unbekannter Wert wird
  in `DebitorService.validate` (Zeile 157–173) mit `IllegalArgumentException` abgewiesen; der
  Fallback deckt nur das **Fehlen**, nicht einen Tippfehler. Ohne diese Prüfung liefe ein falscher
  Wert bis in den CHECK-Constraint und käme als `500` zurück.

### FR-7: Layout

**Seite Nebenkosten → Abrechnung (`/nebenkosten/abrechnung`)**

* Kebab-Menü: neuer Eintrag **Rechnungen erstellen** mit Icon `file-text`, **nur** auf Zeilen mit
  gesetztem `abgerechnet` (FR-2). Er steht **vor** `LOESCHEN`, damit der gefährliche Eintrag unten
  bleibt.
* Unter der Tabelle ein **Ergebnis-Panel**, sichtbar erst nach einem Lauf. Aufbau wie das Panel
  `GENERIERTE_RECHNUNGEN` auf der Seite Rechnungen (`zev-panel` mit `zev-panel__title` und
  `zev-panel__content`, darin eine `zev-table`), damit die beiden Ergebnisdarstellungen gleich
  aussehen:
  * Kopfzeile: Bezeichnung und Zeitraum der Abrechnung, Zahl der Rechnungen, Zahl und Summe der
    Forderungen.
  * Spalten: **Mieter**, **Betrag** (rechtsbündig, `zev-table__number`), **Forderung**, Download.
    Keine Spalte Einheit — eine NK-Rechnung hängt am Mieter.
  * Die Betragsspalte benennt das Vorzeichen **im Text**, nicht durch ein Minus allein:
    `NK_NACHZAHLUNG` bei Saldo > 0, `NK_GUTHABEN` bei Saldo ≤ 0. Beide Schlüssel bestehen seit V120
    (Zeile 191–197) und werden in der Abrechnungsmaske schon so verwendet
    (`nebenkosten-abrechnung-form.component.html:271,428`) — dieselbe Zahl darf nicht auf zwei
    Seiten zwei Namen haben.
  * Spalte **Forderung**: bei gebuchter Forderung ein `zev-status`-Badge; bei Saldo ≤ 0 der Hinweis
    `NK_KEINE_FORDERUNG` statt eines leeren Feldes.
  * Zeilen mit `fehler` zeigen `NK_FEHLER_RECHNUNG_MIETER` und tragen keinen Download-Knopf.
  * `@for` über die Ergebniszeilen mit **`track zeile.mieterId`** — nicht `track zeile`
    (`NG0956`, vgl. FR-2).
* **Zahlenformatierung nach `Specs/generell.md`** (Zeilen 23–34): Beträge über
  `{{ wert | swissNumber:2 }}` bzw. `formatSwissNumber()` aus `utils/number-utils.ts` — Punkt als
  Dezimaltrenner, Hochkomma als Tausendertrenner (`1'234.55`). **Keine** Angular-`number`-Pipe,
  kein `toLocaleString()`; beide liefern je Umgebung ein Komma oder ein typografisches Apostroph.
* Das Panel verschwindet beim Neuladen der Liste und beim Öffnen der Erfassungsmaske. Ein Ergebnis
  zu einem Stand, den die Tabelle darüber nicht mehr zeigt, ist irreführend.
* **Kein komponentenspezifisches CSS** — ausschliesslich Klassen des Design Systems
  (`Specs/generell.md`).

**Seite Rechnungen (`/rechnungen`)**

* Ein Hinweis unter der bestehenden Auswahl: Nebenkostenrechnungen werden im Bereich Nebenkosten
  erzeugt, mit Link auf `/nebenkosten/abrechnung`. Der Hinweis hängt an
  `*appFeature="'NEBENKOSTENABRECHNUNG'"` — bei ausgeschaltetem Flag sieht die Seite aus wie vor
  dieser Änderung.
* **Sonst keine Änderung.** Kein Umschalter, keine Auswahl der Rechnungsart, keine zweite
  Ergebnistabelle.

**Seite Debitorkontrolle (`/debitoren`)**

* Neue Spalte **Herkunft** als übersetztes Status-Badge (`zev-status`), sortierbar wie die übrigen
  Spalten. Die Spalte erscheint **immer**, auch bei ausgeschaltetem Flag: Bestehende NK-Forderungen
  bleiben erhalten, wenn der Flag später abgeschaltet wird, und eine Forderung ohne erkennbare
  Herkunft wäre schlechter als eine mit.
* Neuer Filter **Herkunft**: Alle / ZEV / NK, **Default Alle bei jedem Öffnen der Seite**
  (Entscheid) — der Filter behält seinen Zustand nicht, konsistent mit den übrigen Filtern der
  Seite. Die Option **NK** wird nur angeboten, wenn der Flag gesetzt ist — sonst stünde dort eine
  Auswahl für einen Bereich, den es für diesen Mandanten nicht gibt.
* Das Erfassungsformular erhält ein Auswahlfeld **Herkunft** mit Vorbelegung `ZEV`. Bei
  ausgeschaltetem Flag ist `ZEV` der einzige Wert, und das Feld ist gesperrt — ein manuell
  erfasster NK-Debitor ohne NK-Bereich wäre eine Forderung, die niemand erklären kann.

**Übersetzungen** (Migration V126, je Schlüssel deutsch **und** englisch):
`NK_RECHNUNGEN_ERSTELLEN`, `NK_CONFIRM_RECHNUNGEN_ERSTELLEN`, `NK_RECHNUNGEN_ERGEBNIS`,
`NK_ANZAHL_RECHNUNGEN`, `NK_ANZAHL_FORDERUNGEN`, `NK_SUMME_FORDERUNGEN`, `NK_FORDERUNG`,
`NK_KEINE_FORDERUNG`, `NK_FEHLER_NICHT_ABGERECHNET`, `NK_FEHLER_RECHNUNGEN_ERSTELLEN`,
`NK_FEHLER_RECHNUNG_MIETER`, `NK_RECHNUNG_ABGELAUFEN`, `NK_HINWEIS_AUF_RECHNUNGEN_SEITE`,
`DEBITOR_HERKUNFT`, `DEBITOR_HERKUNFT_ZEV`, `DEBITOR_HERKUNFT_NK`, `DEBITOR_HERKUNFT_ALLE`,
`NK_RECHNUNG_TITEL`.

**Wiederverwendet, nicht neu angelegt:** `NK_NACHZAHLUNG`, `NK_GUTHABEN`, `NK_AKONTO_TOTAL`,
`NK_KOSTENTOTAL` (alle V120), `NK_HINWEIS_ABGERECHNET`. Ein zweiter Schlüssel für „Saldo",
„Akonto" oder „Kostentotal" wäre eine zweite Wahrheit für dieselbe Zahl — auf dem PDF stehen die
Bezeichnungen, die die Maske schon verwendet.

### FR-8: PDF

* **Neues Template** `reports/nk-rechnung.jrxml`. Bewusst nicht `rechnung.jrxml` mitbenutzen: Die
  Zeilen einer NK-Rechnung tragen andere Felder (Prozentsatz, Positionsart), und darunter stehen
  Akonto und Saldo — eine gemeinsame Vorlage müsste beide Formen bedingt zeichnen und wäre für
  keine der beiden mehr lesbar.
* Aufbau: Kopf mit Steller, Mieter und Zeitraum der Abrechnung; Tabelle der Zeilen; darunter
  Kostentotal, Akonto, Rundung und Saldo; Schweizer QR-Einzahlungsschein wie bei der
  Quartalsrechnung.
* **Feldtypen `java.math.BigDecimal`** — nicht `java.lang.Double`. Ein Template kompiliert auch mit
  falschen Feldtypen; der Fehler kommt erst beim Füllen
  (`Specs/RechnungenGenerieren.md`, Abschnitt 2).
* **Zahlenformatierung über `ch.nacht.util.PdfNumberFormat`** (`decimals()` / `chf()`), wie
  `Specs/generell.md` Zeilen 23–34 es vorschreibt: **kein** `pattern="#,##0.00"` im `.jrxml` und
  kein `String.format` ohne `Locale.ROOT` — beide liefern je nach Umgebung ein Komma statt des
  Punkts. **Ausnahme:** Die Betragsfelder von Empfangsschein und Zahlteil folgen den Vorgaben der
  QR-Rechnung, nicht dieser Regel.
* Der Betrag des Zahlteils ist der **gerundete Saldo**; bei Saldo ≤ 0 wird der Zahlteil
  **weggelassen** (`printWhenExpression`) — ein Einzahlungsschein über 0 oder einen negativen
  Betrag ist ungültig.
* **Zur Laufzeit wird das vorkompilierte `.jasper` geladen**, nicht das `.jrxml`
  (`RechnungPdfService:44`). `nk-rechnung.jasper` entsteht erst in der Maven-Phase
  `prepare-package` — nach einem reinen `mvn test` oder einem Start aus der IDE fehlt es, und der
  Fehler zeigt sich erst beim ersten PDF („Could not find nk-rechnung.jasper"). Das
  `jasperreports-maven-plugin` kompiliert das ganze Verzeichnis `src/main/resources/reports`, ein
  `pom.xml`-Eintrag für das neue Template ist also nicht nötig.

### FR-9: Feature-Flag `NEBENKOSTENABRECHNUNG`

Der ganze NK-Bereich liegt hinter dem Flag (`Nebenkosten.md`, FR-2). Weil Erzeugung **und**
Download im NK-Bereich liegen, greift der Flag überall dort, wo er ohnehin schon greift — es
entsteht keine Stelle, die ihn eigens nachbilden müsste:

1. **Frontend.** Die Seite `/nebenkosten/abrechnung` und ihr Menüpunkt hängen bereits am Flag —
   der neue Menüeintrag ist ohne Zusatzarbeit unerreichbar. Neu am Flag hängt nur der Hinweis auf
   `/rechnungen` (FR-7).
2. **Backend, Erzeugung.** `NkRechnungService` ruft `pruefeFeatureFlag()` in **jeder** öffentlichen
   Methode auf und wirft `FeatureDisabledException` → **403**, auch bei gültigen Permissions. Das
   ist die tragende Prüfung: Der Endpunkt ist über jeden HTTP-Client erreichbar.
   Er verlässt sich dabei **nicht** darauf, dass `NkAbrechnungService.getAbrechnungDetail` den Flag
   bereits prüft (Zeile 118–119). Die Prüfung passiert dort ohnehin — als **Nebenwirkung eines
   fremden Services** wäre die Zusicherung aber weg, sobald die Abrechnung einmal aus einer anderen
   Quelle kommt.
3. **Backend, Download.** `GET /api/nebenkosten/abrechnungen/{id}/rechnungen/{mieterId}/pdf` liegt
   im NK-Controller und läuft über `NkRechnungService`, prüft den Flag also auf demselben Weg wie
   die Erzeugung → **403** nach dem Abschalten, auch für ein bereits erzeugtes PDF. Das kostet hier
   nichts: Die Art steht in der Route, es muss ihr **nichts** am Schlüsselstring angesehen werden.
   Genau daran wäre die Prüfung am gemeinsamen Endpunkt `GET /api/rechnungen/download/{key}`
   gescheitert — eine Erkennung am `nk_`-Präfix wäre brüchig gewesen (FR-5 zeigt, warum das Präfix
   nicht einmal eindeutig ist).
4. **Debitorkontrolle.** Filter-Option und Formularfeld richten sich nach dem Flag (FR-7); die
   **Spalte** bleibt sichtbar, weil bestehende NK-Forderungen ein Abschalten überleben.

**Das bestehende Schutzgeländer greift hier nicht.** Die ArchUnit-Regel
`ArchitectureTest.SecurityRules.nebenkostenServicesMustCheckFeatureFlag` erzwingt den Flag-Aufruf in
jeder öffentlichen Methode — aber nur für Klassen mit dem Präfix **`NkAbrechnung`**
(`haveSimpleNameStartingWith("NkAbrechnung")`, Zeile 445). `NkRechnungService` und
`NkRechnungPdfService` fielen nicht darunter; die Regel hätte sie stillschweigend übersprungen. Der
Geltungsbereich ist deshalb auf **alle** Services mit dem Präfix `Nk` zu erweitern. Damit ist auch
jeder künftige NK-Service automatisch erfasst — deny by default, wie bei der `findById`-Regel
daneben.

> **Abgrenzung:** `NkRechnungPdfService` erzeugt nur das PDF und lädt keine Daten. Trägt er keine
> öffentliche Methode, die einen Mandantenzugriff auslöst, ist ein `pruefeFeatureFlag()` dort
> Zierde. Wird die Regel auf `Nk` erweitert, ist er entweder ebenfalls zu prüfen oder ausdrücklich
> auszunehmen — eine stumme Ausnahme wäre der schlechtere Weg.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

**Auslösung**
* [ ] Im Kebab-Menü einer Zeile mit gesetztem `abgerechnet` steht der Eintrag
      **Rechnungen erstellen**.
* [ ] Auf einer Zeile **ohne** `abgerechnet` fehlt der Eintrag; die übrigen Einträge sind
      unverändert vorhanden.
* [ ] Der Eintrag steht vor `LOESCHEN`.
* [ ] Ein Klick fragt zurück; bei Abbruch passiert nichts — kein Aufruf, keine Forderung.
* [ ] Die Zeilen-Menüs werden **nicht** je Änderungserkennung neu erzeugt: Die Konsole zeigt kein
      `NG0956`, und zwei Aufrufe der Menü-Methode für dieselbe Zeile liefern dasselbe Objekt.
* [ ] Auf `/rechnungen` gibt es **keinen** Umschalter der Rechnungsart; die Seite verhält sich wie
      vor dieser Änderung, ergänzt um den Hinweis auf den NK-Bereich.

**Erzeugung**
* [ ] Ein Lauf über eine Abrechnung mit *n* Mietern erzeugt *n* PDF und *n* Ergebniszeilen.
* [ ] Jede Ergebniszeile bietet den Download ihrer Rechnung an; die Datei ist ein PDF
      (beginnt mit `%PDF`).
* [ ] Die Beträge im PDF entsprechen denen der Abrechnungsmaske — es wird nicht neu gerechnet.
* [ ] Der Endbetrag ist ein Vielfaches von 5 Rappen; die Differenz erscheint als **Rundung**.
* [ ] Bei Saldo ≤ 0 fehlt der QR-Zahlteil im PDF.
* [ ] Das Ergebnis-Panel nennt Zahl der Rechnungen, Zahl der Forderungen und deren Summe getrennt.
* [ ] Ein zweiter Lauf **ersetzt** das Panel; es stehen nicht zwei Ergebnislisten untereinander.
* [ ] Das Panel verschwindet beim Neuladen der Liste und beim Öffnen der Erfassungsmaske.
* [ ] Ein NK-Lauf macht die Downloads eines vorangegangenen **ZEV**-Laufs nicht ungültig, und ein
      ZEV-Lauf nicht die eines NK-Laufs (`clearArt` trifft nur die eigene Art).
* [ ] Eine ZEV-Einheit mit dem Namen **„nk 12"** und `mieterId = 45` überschreibt **nicht** das PDF
      der NK-Abrechnung 12 für Mieter 45 — die Namensräume sind getrennt, nicht bloss verschieden
      benannt.
* [ ] Der Download liefert den **gespeicherten** Dateinamen (lesbar, z.B.
      `Nebenkosten_2026_Muster_Hans.pdf`); der Dateiname einer ZEV-Rechnung ist unverändert.
* [ ] Ein abgelaufener Download führt zu einem übersetzten Hinweis (`NK_RECHNUNG_ABGELAUFEN`),
      nicht zu einem stummen Fehlschlag.
* [ ] Scheitert das Ablegen des PDF nach der Buchung, bleibt die Forderung bestehen und die Zeile
      trägt `NK_FEHLER_RECHNUNG_MIETER`; die übrigen Mieter sind davon nicht betroffen.

**Zahlenformatierung**
* [ ] Beträge im Ergebnis-Panel erscheinen als `1'234.55` — Punkt als Dezimaltrenner, Hochkomma
      (ASCII `'`) als Tausendertrenner, unabhängig von der Browser-Locale.
* [ ] Beträge im PDF erscheinen im selben Format; das `.jrxml` enthält **kein** `pattern`-Attribut
      auf einem Betragsfeld.
* [ ] Die Betragsfelder von Empfangsschein und Zahlteil folgen dem QR-Standard, nicht dieser Regel.
* [ ] Die Betragsspalte des Panels benennt **Nachzahlung** bzw. **Guthaben** im Text und verlässt
      sich nicht auf das Vorzeichen; verwendet werden die bestehenden Schlüssel `NK_NACHZAHLUNG`
      und `NK_GUTHABEN`.

**Debitoren**
* [ ] Für jeden Mieter mit Saldo > 0 entsteht ein Debitor mit `herkunft = NK`, dem gerundeten
      Saldo und dem Zeitraum der Abrechnung.
* [ ] Für Saldo ≤ 0 entsteht **kein** Debitor; die Ergebniszeile weist das aus.
* [ ] Ein zweiter Lauf über dieselbe Abrechnung erzeugt **keine** zweite Forderung, sondern
      aktualisiert die bestehende (Upsert je Herkunft).
* [ ] Ein NK-Lauf **überschreibt keine ZEV-Forderung** desselben Mieters mit demselben
      `datum_von` — beide Einträge bestehen nebeneinander.
* [ ] Ein Debitor mit gesetztem `zahldatum` wird von einem erneuten Lauf **nicht** verändert
      (bestehendes Upsert-Verhalten).
* [ ] Die Debitorenliste zeigt die Spalte **Herkunft** als übersetztes Badge.
* [ ] Der Herkunft-Filter zeigt bei **ZEV** nur ZEV-Einträge, bei **NK** nur NK-Einträge, bei
      **Alle** beide.
* [ ] Alle bestehenden Debitoren tragen nach der Migration `herkunft = ZEV`.
* [ ] Ein manuell erfasster Debitor bekommt eine Herkunft; das Formular gibt `ZEV` vor.
* [ ] `POST /api/debitoren` **ohne** `herkunft` legt den Eintrag mit `ZEV` an (kein `400`).
* [ ] `POST /api/debitoren` mit einem **unbekannten** Wert antwortet mit `400`, nicht mit `500`.
* [ ] **Regression ZEV:** Ein ZEV-Lauf bucht nach der Migration weiterhin seine Forderung. Die
      `ON CONFLICT`-Klausel in `DebitorRepository:49` ist im Gleichschritt mit dem Unique-Key
      angepasst — bleibt sie auf `(mieter_id, datum_von, org_id)`, scheitert **jeder** Upsert mit
      „no unique or exclusion constraint matching the ON CONFLICT specification", auch der
      ZEV-seitige.

**Sicherheit**
* [ ] `POST /api/nebenkosten/abrechnungen/{id}/rechnungen` verlangt `nebenkosten:manage` **und**
      `rechnungen:manage`; fehlt eine der beiden, antwortet der Endpunkt mit `403`.
* [ ] Bei ausgeschaltetem Feature-Flag antwortet der Endpunkt mit `403` — auch mit beiden
      Permissions.
* [ ] Eine Abrechnung, die zwischen Laden und Klick wieder geöffnet wurde, wird mit `400` und
      `NK_FEHLER_NICHT_ABGERECHNET` abgewiesen.
* [ ] Eine `abrechnungId` eines **fremden Mandanten** liefert `404` und ist nicht von einer
      unbekannten ID unterscheidbar. Der Zugriff läuft über eine gefilterte Abfrage
      (`findFirstById`), nicht über `findById`; die ArchUnit-Regel in
      `ArchitectureTest.SecurityRules` hält das fest.
* [ ] `POST /api/rechnungen/generate` ist unverändert: Ein Aufruf mit `von`, `bis`, `einheitIds`
      verhält sich wie vor dieser Änderung, und ein Aufruf ohne `einheitIds` antwortet weiterhin
      mit `400`.
* [ ] Die Antwort des NK-Endpunkts hat genau die Form aus FR-6; `anzahlForderungen` und
      `summeForderungen` passen zu den tatsächlich gebuchten Debitoren.
* [ ] Der NK-Download verlangt dieselben beiden Permissions wie die Erzeugung; eine fremde
      `abrechnungId` liefert `404`.

**Feature-Flag (FR-9)**
* [ ] Bei ausgeschaltetem Flag ist die Seite `/nebenkosten/abrechnung` nicht erreichbar — der
      Menüeintrag samt Aktion ist damit unerreichbar.
* [ ] Bei ausgeschaltetem Flag antwortet der **NK-Download** mit `403`, auch für ein vorher
      erzeugtes PDF.
* [ ] Bei ausgeschaltetem Flag bleibt `GET /api/rechnungen/download/{key}` für ZEV-Rechnungen
      uneingeschränkt nutzbar — der Endpunkt wurde nicht angefasst.
* [ ] Bei ausgeschaltetem Flag ist `POST /api/rechnungen/generate` (ZEV) uneingeschränkt möglich —
      der Flag sperrt nur den NK-Teil.
* [ ] Bei ausgeschaltetem Flag fehlt der Hinweis auf `/rechnungen`; die Seite sieht aus wie vor
      dieser Änderung.
* [ ] Bei ausgeschaltetem Flag fehlt die Filter-Option **NK** in der Debitorkontrolle, und das
      Erfassungsformular lässt nur `ZEV` zu.
* [ ] Bei ausgeschaltetem Flag bleibt die **Spalte Herkunft** sichtbar und zeigt bestehende
      NK-Forderungen weiterhin als `NK` — ein Abschalten verändert keine Daten.
* [ ] `NkRechnungService` ruft in jeder öffentlichen Methode `pruefeFeatureFlag()` **selbst** auf;
      die ArchUnit-Regel `nebenkostenServicesMustCheckFeatureFlag` erfasst ihn, weil ihr
      Geltungsbereich auf das Präfix `Nk` erweitert wurde.
* [ ] Die erweiterte ArchUnit-Regel ist **gegengeprüft**: Ein entfernter `pruefeFeatureFlag()`-Aufruf
      in einem `Nk`-Service lässt sie fehlschlagen. Eine Regel, die nie ausgelöst hat, beweist
      nichts.

**i18n**
* [ ] Alle neuen Texte stammen aus dem `TranslationService`; keine fest verdrahteten Strings.
* [ ] Jeder neue Schlüssel hat einen deutschen **und** einen englischen Text.
* [ ] Die Übersetzungsmigration ist wiederholbar (`ON CONFLICT (key) DO NOTHING`).

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Ein Lauf über eine Abrechnung mit 20 Mietern lädt die Abrechnung **einmal** und erzeugt daraus
  alle PDF; es entsteht kein Aufruf je Mieter.
* Die Liste der Abrechnungen bleibt unverändert — die Aktion braucht keine zusätzliche Abfrage je
  Zeile, insbesondere keine, die vom Kebab-Menü ausgelöst wird.

### NFR-2: Sicherheit
* `POST /api/nebenkosten/abrechnungen/{id}/rechnungen` verlangt **`nebenkosten:manage` und
  `rechnungen:manage`** (heute: `zev_user`, `org_admin`, `zev_admin` — `Specs/Berechtigungen.md`)
  **zusätzlich** zum Feature-Flag `NEBENKOSTENABRECHNUNG`.
* Der NK-Download `GET /api/nebenkosten/abrechnungen/{id}/rechnungen/{mieterId}/pdf` liegt im
  selben Controller und verlangt damit dieselben beiden Permissions **und** den Flag.
* `GET /api/rechnungen/download/{key}` bleibt unverändert auf **`rechnungen:manage`**, ohne
  Flag-Prüfung — dort liegen nach dieser Änderung nur noch ZEV-Rechnungen.
* `GET/POST/PUT/DELETE /api/debitoren` bleibt auf **`debitoren:manage`**.
* **Multi-Tenancy:** `debitor.herkunft` ist ein Feld einer bereits mandantenfähigen Tabelle; die
  `org_id` wird weiterhin serverseitig gesetzt und nie aus dem Request übernommen. Die
  `abrechnungId` aus dem Pfad wird über eine **gefilterte** Abfrage geladen, damit eine fremde ID
  nicht erreichbar ist.
* Der Zugriff auf `debitor` erfolgt weiterhin unter aktivem `orgFilter`; der neue Unique-Key führt
  `org_id` mit.
* Die PDF liegen org-getrennt (`RechnungStorageService` stellt jedem Schlüssel intern die `org_id`
  voran) — daran ändert der NK-Schlüssel nichts.

### NFR-3: Kompatibilität
* **`POST /api/rechnungen/generate` bleibt unangetastet** — keine neuen Felder, keine geänderte
  Validierung, keine geänderte Antwort. Der NK-Lauf hat seinen eigenen Endpunkt.
* Die Spalte `herkunft` ist `NOT NULL DEFAULT 'ZEV'`, bestehende Zeilen bleiben gültig. Ein
  `POST /api/debitoren` ohne `herkunft` bleibt gültig und legt `ZEV` an.
* **Rücknahme:** Die Spalte liesse sich löschen; der alte Unique-Key wäre dann wiederherzustellen.
  Solange NK-Forderungen bestehen, würde das Kollisionen erzeugen — eine Rücknahme setzt also
  voraus, dass keine NK-Debitoren existieren.
* Der Feature-Flag verbirgt den NK-Teil vollständig: Bei ausgeschaltetem Flag ist die NK-Seite und
  damit die Aktion unerreichbar, und die Seite Rechnungen bleibt wie bisher.

## 5. Edge Cases & Fehlerbehandlung

* **Abrechnung ohne Mieter:** Der Lauf erzeugt nichts und meldet es als Hinweis — kein Fehler, die
  Abrechnung ist bloss leer. Das Panel erscheint mit 0 Rechnungen.
* **Alle Mieter mit Guthaben:** PDF für alle, kein einziger Debitor. Das Panel nennt die Zahl der
  erzeugten Rechnungen und die Zahl der gebuchten Forderungen getrennt, damit „0 Forderungen" nicht
  wie ein Fehlschlag aussieht.
* **Saldo genau 0:** wie Guthaben — PDF, kein Debitor.
* **Mieter ohne Adresse:** Der QR-Zahlteil braucht eine gültige Empfängeradresse. Fehlt sie,
  scheitert die Erzeugung des Zahlteils; das PDF entsteht trotzdem ohne Zahlteil (bestehendes
  Verhalten: `generateQrCodePng` liefert `null`, das Bildelement trägt `onErrorType="Blank"`). Der
  Debitor wird gebucht.
* **Fehler bei einem einzelnen Mieter:** Der Lauf bricht **nicht** ab. Die Zeile trägt
  `NK_FEHLER_RECHNUNG_MIETER`, die übrigen Rechnungen entstehen — dasselbe Verhalten wie bei ZEV,
  wo eine fehlgeschlagene Einheit die anderen nicht mitnimmt.
  * **Scheitert das PDF nach der Buchung** (Reihenfolge in FR-6), bleibt die Forderung stehen. Der
    Beleg ist nachholbar, indem der Lauf wiederholt wird; die Buchung ist idempotent. Umgekehrt —
    PDF zuerst, Buchung danach — bliebe im Fehlerfall ein Beleg ohne Forderung, und der fehlt in
    der Debitorenkontrolle, wo ihn niemand vermisst.
* **Abrechnung wird während des Laufs wieder geöffnet:** Der Lauf läuft in einer Transaktion; Flag
  und Zustand werden zu Beginn geprüft. Ein Wiedereröffnen danach macht die bereits gebuchten
  Forderungen nicht ungültig — sie bleiben stehen und sind über die Debitorenkontrolle korrigierbar.
* **Abrechnung wird gelöscht, nachdem Rechnungen erstellt wurden:** Die Forderungen bleiben; sie
  hängen am Mieter, nicht an der Abrechnung. Die PDF verfallen mit dem Speicher. Bewusst so — eine
  Kaskade auf `debitor` wäre ein stiller Datenverlust.
* **Flag wird während einer laufenden Sitzung abgeschaltet:** Der nächste Klick läuft in `403`; die
  Seite zeigt die Fehlermeldung. Der Menüpunkt verschwindet erst beim nächsten Laden der Flags —
  dasselbe Verhalten wie beim Menüeintrag (`Nebenkosten.md`, Abschnitt 5). Bereits gebuchte
  Forderungen bleiben unberührt.
* **Netzwerkfehler beim Erstellen:** Die Seite zeigt `NK_FEHLER_RECHNUNGEN_ERSTELLEN` plus den
  Servertext und lässt ein vorhandenes Panel unverändert stehen; ein halb ersetztes Ergebnis wäre
  schlimmer als ein altes.
* **Doppelklick auf den Menüeintrag:** Das Kebab-Menü schliesst beim Klick, ein zweiter Klick ist
  also nicht ohne erneutes Öffnen möglich. Während der Lauf läuft, zeigt die Seite einen Spinner;
  ein zweiter Lauf wäre ohnehin idempotent (Upsert), erzeugte aber ein zweites Panel.
* **Abgelaufener Download (30 Minuten):** `404` → übersetzter Hinweis `NK_RECHNUNG_ABGELAUFEN`, die
  Rechnung ist erneut zu erstellen. Das PDF ist aus der abgeschlossenen Abrechnung reproduzierbar.
* **Migration auf einer Tabelle mit Daten:** Der Unique-Key wird auf dem bestehenden Bestand
  ersetzt. Da alle Zeilen den Default `ZEV` erhalten und der alte Schlüssel bereits eindeutig war,
  kann der neue nicht verletzt werden — er ist eine Erweiterung um eine konstante Spalte.

## 6. Abhängigkeiten & betroffene Funktionalität

**Voraussetzungen**
* `Specs/Nebenkosten/Abrechnung.md` — Abrechnung samt Berechnung je Mieter (**vorhanden**).
* `Specs/Nebenkosten/Nebenkosten.md` — Feature-Flag `NEBENKOSTENABRECHNUNG`, Permission
  `nebenkosten:manage` (**vorhanden**).
* `Specs/RechnungenGenerieren.md` — Quartalsrechnung, PDF-Pipeline, QR-Zahlteil, Geldtyp
  `BigDecimal` (**vorhanden**).
* `Specs/Debitorkontrolle.md` — Tabelle `debitor`, Upsert, Liste (**vorhanden**).
* `Specs/Einzahlungsschein.md` — QR-Rechnung (**vorhanden**).
* `Specs/Berechtigungen.md` — Permission-Matrix für den neuen Endpunkt (**vorhanden**).

**Betroffener Code**
* `backend-service/.../entity/Debitor.java` — Feld `herkunft`; neues Enum `Debitorherkunft`.
* `backend-service/.../dto/DebitorDTO.java` — Feld `herkunft`.
* `backend-service/.../repository/DebitorRepository.java` — `upsert` um `herkunft` erweitern
  (`ON CONFLICT (mieter_id, datum_von, herkunft, org_id)`); Liste optional nach Herkunft filtern.
* `backend-service/.../service/DebitorService.java` — `upsertFromRechnung` bekommt die Herkunft;
  `validate` prüft sie und setzt `ZEV`, wenn sie fehlt.
* `backend-service/.../service/RechnungStorageService.java` — neues Enum `Rechnungsart` als
  Namensraum in `store`/`get`/`exists`, Dateiname mitspeichern, `clearAll()` → `clearArt(art)`
  (FR-5).
* `backend-service/.../controller/RechnungController.java` — zwei Stellen: der Aufruf der
  umbenannten Aufräummethode (Zeile 82) und `upsertFromRechnung` (Zeile 111), das jetzt
  `Debitorherkunft.ZEV` mitgibt. **`GenerateRequest`, Antwort, Schlüssel und Dateinamen bleiben
  unverändert.**
* **Neu:** `NkRechnungController` (`POST /api/nebenkosten/abrechnungen/{id}/rechnungen` und
  `GET .../{id}/rechnungen/{mieterId}/pdf`),
  `NkRechnungService` (baut die Rechnung je Mieter aus dem Abrechnungsdetail, bucht die Debitoren)
  und `NkRechnungPdfService` (füllt `nk-rechnung.jrxml`, erzeugt den QR-Zahlteil). Bewusst getrennt
  von `RechnungService`/`RechnungPdfService`: Die beiden Rechnungsarten teilen keine Rechenlogik,
  und eine gemeinsame Klasse müsste durchgehend verzweigen.
* `backend-service/src/main/resources/reports/nk-rechnung.jrxml` — **neu**.
* `frontend-service/.../components/nebenkosten-abrechnung/` — Menüeintrag (zeilenabhängig, feste
  Listen), Aufruf, Ergebnis-Panel.
* `frontend-service/.../services/nebenkosten.service.ts` — Aufruf des Laufs **und** des
  NK-Downloads (nicht über `rechnung.service.ts` — die Route liegt im NK-Bereich).
* `frontend-service/.../models/nebenkosten.model.ts` — Ergebnistypen des Laufs.
* `frontend-service/.../components/rechnungen/` — nur der Hinweis auf den NK-Bereich.
* `frontend-service/.../components/debitorkontrolle-list/` und `-form/` — Spalte, Filter, Feld.
* `frontend-service/.../models/debitor.model.ts` — `herkunft`.
* `Specs/Debitorkontrolle.md` und `Specs/RechnungenGenerieren.md` — um die Herkunft bzw. den
  Verweis auf diesen Weg ergänzen.
* `backend-service/src/test/java/ch/nacht/architecture/ArchitectureTest.java` — Geltungsbereich von
  `nebenkostenServicesMustCheckFeatureFlag` von `NkAbrechnung` auf `Nk` erweitern (FR-9). Ohne das
  greift die Regel bei den neuen Services nicht.
* Neue Flyway-Migration **V126**.

**Tests**
* `DebitorRepositoryIT` — der neue Unique-Key: dieselbe `(mieter_id, datum_von)` mit
  verschiedener Herkunft ist erlaubt, mit gleicher nicht; `upsert` trifft nur die eigene Herkunft.
* `DebitorServiceTest` — fehlende Herkunft wird `ZEV`, unbekannter Wert wird abgewiesen.
* `NkRechnungServiceTest` — Aufbau der Rechnung, Rundung, Guthaben ohne Debitor, Zustand
  `abgerechnet`, Feature-Flag.
* `NkRechnungControllerTest` — Antwortform aus FR-6, `400` bei offener Abrechnung, `404` bei
  fremder ID, `403` ohne Flag.
* `RechnungStorageServiceTest` (**neu** — es gibt bisher keinen): `clearArt(ZEV)` lässt NK-Einträge
  stehen und umgekehrt; derselbe Schlüssel in beiden Arten liefert zwei verschiedene PDF (der Fall
  „Einheit `nk 12`, Mieter 45"); der gespeicherte Dateiname kommt zurück.
* `RechnungControllerTest` — unverändertes Verhalten des ZEV-Endpunkts (Regression). **Achtung:**
  Zeile 111 prüft heute `verify(rechnungStorageService).clearAll()` und bricht mit der Umbenennung;
  dazu ist die Herkunft im `upsertFromRechnung`-`verify` zu ergänzen.
* `DebitorRepositoryIT` — ausserdem: ein ZEV-Upsert funktioniert nach dem Constraint-Tausch
  weiterhin (`ON CONFLICT` im Gleichschritt angepasst).
* `PdfNumberFormat`-Nachweis: Ein Fill-Test prüft, dass im erzeugten PDF `1'234.55` steht und kein
  Komma — der Fehler entsteht sonst nur auf Maschinen mit anderer Locale.
* `ControllerAuthorizationTest` — die Permission-Matrix für den neuen Endpunkt, inklusive des
  Falls „nur eine der beiden Permissions".
* `JasperTemplateCompileTest` — `nk-rechnung.jrxml` kompiliert **und füllt** (ein Template
  kompiliert auch mit falschen Feldtypen; der Fehler kommt erst beim Füllen).
* Frontend: Menüeintrag nur bei `abgerechnet`, Rückfrage und Abbruch, Panel nach dem Lauf, Panel
  weg beim Neuladen, stabile Menü-Objekte (kein Neuaufbau je Zyklus); Debitorenliste mit Spalte und
  Filter.
* E2E: ein Lauf über eine abgeschlossene Abrechnung bis zum Download, danach die Forderung in der
  Debitorenkontrolle mit Herkunft **NK**. Die Testdaten sind wieder abzuräumen — auch die
  entstandenen Debitoren.

**Datenmigration**
* Bestehende Debitoren erhalten `herkunft = 'ZEV'` über den Spalten-Default. Das ist eine korrekte
  Rückschreibung und keine Annahme: Bis heute konnte eine Forderung nur aus der Stromabrechnung
  entstehen.

## 7. Abgrenzung / Out of Scope

* **Keine Erzeugung auf der Seite Rechnungen** (Entscheid, Abschnitt 1). Dort steht nur ein Hinweis;
  `POST /api/rechnungen/generate` bekommt kein Feld `art`.
* **Keine Radiogruppe im Design System.** Die Auswahl der Rechnungsart entfällt mit dem
  Platzierungsentscheid, und damit auch die Konsolidierung von `.zev-radio-group` /
  `.zev-radio-label` aus `design-system/src/components/form/form.css` in eine eigene Kategorie samt
  Showcase-Abschnitt. Der Bestand ist unverändert unvollständig (keine Klasse für das `input`
  selbst, kein Showcase, zwei Verwender: `solar-calculation` und `translation-editor`) — das bleibt
  eine eigene, kleine Aufgabe und ist kein Teil dieses Features.
* **Kein Versand.** Die Rechnung wird erzeugt und heruntergeladen, nicht verschickt.
* **Keine gespeicherten Rechnungen.** Es entsteht keine Tabelle erzeugter NK-Rechnungen; sie sind
  aus der Abrechnung reproduzierbar. Die PDF liegen flüchtig im bestehenden Speicher.
* **Kein Sammel-Download.** Kein ZIP über alle Rechnungen einer Abrechnung; jede Zeile wird einzeln
  geholt, wie bei ZEV.
* **Kein Kreditor.** Guthaben werden nicht als negative Forderung geführt (FR-4).
* **Keine Sammelrechnung.** Ein Mieter mit Wohnung **und** Ladestation erhält seine ZEV- und seine
  NK-Rechnung getrennt; sie werden nicht zu einem Dokument zusammengefasst.
* **Keine Rechnungsnummer** (Entscheid). Die Quartalsrechnung hat auch keine
  (`Specs/RechnungenGenerieren.md`, Abschnitt 3); käme sie, wäre sie für **beide** Rechnungsarten
  einzuführen und nicht nur für NK.
* **Kein Probedruck** für nicht abgeschlossene Abrechnungen (Entscheid, FR-2). Ein Ausdruck ohne
  Debitorenbuchung wäre eine eigene Anforderung.
* **Keine Mahnungen und keine Teilzahlungen.** Die Debitorenkontrolle kennt weiterhin nur ein
  Zahldatum.
* **Keine Herkunft an anderen Stellen.** Die Statistik und die Startseite werten `herkunft` nicht
  aus.
* **Kein Umbenennen** des Menüpunkts „Tarifpositionen" unter Nebenkosten (siehe `Abrechnung.md`,
  Abschnitt 7).

## 8. Offene Fragen

| Frage | Stand | Entscheiden bis |
|---|---|---|
| Soll die Rückzahlung eines Guthabens nachverfolgbar werden (Kreditor oder negativer Debitor)? | **Zurückgestellt** (Entscheid: noch warten). Heute bewusst ausgeklammert — Guthaben erzeugen ein PDF, aber keine Forderung (FR-4). Sobald der erste Fall auftritt und jemand die Rückzahlung nachvollziehen will, ist es eine eigene Spec: `debitor.betrag` trägt `CHECK (betrag > 0)`, es wäre also eine Migration auf der bestehenden Tabelle samt Anpassung von Validierung, Summenanzeige und Status-Logik. | wenn der erste Fall auftritt |

Alle übrigen Fragen sind entschieden und in den Abschnitten 2 bis 7 eingearbeitet:

| Frage | Entscheid | Wo |
|---|---|---|
| Wo wird ausgelöst | Zeilenaktion auf der NK-Abrechnung, nicht auf `/rechnungen` | Abschnitt 1, FR-1 |
| Endpunkt | eigener `POST /api/nebenkosten/abrechnungen/{id}/rechnungen` mit fest definierter Antwort; ZEV-Endpunkt unangetastet | FR-6 |
| Download | eigene Route im NK-Bereich über `abrechnungId`/`mieterId`, kein Schlüssel in der Antwort | FR-6 |
| Berechtigung | `nebenkosten:manage` **und** `rechnungen:manage` | FR-6, NFR-2 |
| Guthaben (negativer Saldo) | PDF ja, kein Debitor | FR-4 |
| Umfang des PDF | eigenes Template mit QR-Zahlteil, Endbetrag auf 5 Rappen | FR-3, FR-8 |
| Abrechenbare Abrechnungen | nur `abgerechnet`e; Menüeintrag fehlt sonst, Server prüft erneut | FR-2 |
| Herkunft im Unique-Key | ja — sonst überschreibt NK die ZEV-Forderung | Abschnitt 1, FR-5 |
| Herkunft im Debitor-Request | fehlt sie, gilt `ZEV`; unbekannter Wert wird abgewiesen | FR-6 |
| PDF-Speicher zwischen den Arten | expliziter Namensraum `Rechnungsart` statt Schlüsselpräfix; `clearArt(art)` räumt nur die eigene Art ab; Dateiname wird mitgespeichert | FR-5 |
| Feature-Flag beim Download | **ja** — über die eigene NK-Route, ohne Erkennung am Schlüsselstring | FR-6, FR-9 |
| Reihenfolge Buchung / PDF | Debitor zuerst, dann ablegen — wie im ZEV-Pfad | FR-6, Abschnitt 5 |
| Zahlenformatierung | `swissNumber`/`formatSwissNumber` im Frontend, `PdfNumberFormat` im jrxml, QR-Felder ausgenommen | FR-7, FR-8 |
| Bezeichnung des Saldos | bestehende Schlüssel `NK_NACHZAHLUNG`/`NK_GUTHABEN` wiederverwenden, kein neues `NK_SALDO` | FR-7 |
| Rechnungsnummer | nein | Abschnitt 7 |
| Probedruck ohne Abschluss | nein | FR-2, Abschnitt 7 |
| Radiogruppe im Design System | entfällt mit dem Platzierungsentscheid — eigene Aufgabe | Abschnitt 7 |
| Zustand des Herkunft-Filters | kein Zustand, Default **Alle** bei jedem Öffnen | FR-7 |
| Feature-Flag bei der Rechnungserstellung | Seite, Erzeugung, Download, Debitorkontrolle; ArchUnit-Regel auf Präfix `Nk` erweitern | FR-9 |
