# Tarifverwaltung

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Die Tarife können sich von Quartal zu Quartal ändern. Dies muss berücksichtigt werden.
* **Warum machen wir das:** Beim Erstellen der Rechnungen müssen die Tarife verwendet werden, die für den Zeitraum der Rechnung gültig sind.
* **Ausgangslage:** Die Tarife waren ohne Gültigkeitsbereich in `application.yml` spezifiziert (Hintergrund: Anhang A.1).

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Tarife mit Gültigkeit verwalten
* **User Story:** Als Admin möchte ich zu den Tarifen auch die Gültigkeit (von, bis) angeben können, damit beim Erstellen der Rechnungen die richtigen Tarife verwendet werden.
* **Ablauf / Flow:**
  1. Der User kann im Menü die Seite "Tarifverwaltung" (`/tarife`) aufrufen; sie steht auf der ersten Ebene des Menüs (`Specs/Hamburgermenü.md`).
  2. In der Tarifverwaltung können bestehende Tarife bearbeitet oder gelöscht und neue Tarife hinzugefügt werden.
  3. Beim Erstellen von Rechnungen werden die für den Zeitbereich der Rechnung gültigen Tarife verwendet.
* **Rechnungen:**
  * Für den Zeitbereich einer Rechnung (i.d.R. ein Quartal) können mehrere Tarife gültig sein, da diese monatlich angepasst werden.
  * Der Stromverbrauch wird pro Zeile für den Gültigkeitsbereich des Tarifes und nicht der Rechnung berechnet.
  * Auf der Rechnung können somit mehrere Zeilen für den Strombezug vom ZEV und vom VNB nötig sein.
  * Die Rundungen der Mengen auf den Rechnungen bleiben erhalten.

### FR-2: Mehrere gleichzeitig gültige Tarife je Typ
* **User Story:** Als Admin möchte ich für denselben Zeitraum mehrere Tarife desselben Typs erfassen, weil sich der Preis aus mehreren Bestandteilen zusammensetzt:
  * **Grundgebühr:** Energielieferung, Netznutzung, Messtarif
  * **Strombezug (VNB, ZEV):** Energielieferung und Netznutzung
* **Regel je Tariftyp:**

  | Tariftyp | Mehrere gleichzeitig gültig? | Abgewiesen wird |
  |---|---|---|
  | `ZEV`, `VNB`, `GRUNDGEBUEHR` | **ja** | eine Überschneidung mit einem Tarif desselben Typs **und derselben Bezeichnung** (ohne Gross-/Kleinschreibung, ohne Leerzeichen am Rand) |
  | `LADESTROM` | nein (`Specs/Ladestromtarif.md`) | jede Überschneidung desselben Typs — die Position ist je Typ eindeutig, ein zweiter Tarif hiesse, dieselben kWh zweimal zu erfassen |
  | `ZUSATZ` | ja (`Specs/Tarifpositionen.md`) | nichts — der Tarif wird an der Position gewählt |

  > **Warum die Bezeichnung als Schutz:** Ein versehentlich doppelt erfasster oder mit falschen Daten kopierter Tarif stünde sonst ein zweites Mal auf **jeder** Rechnung. „Energielieferung" und „Netznutzung" unterscheiden sich im Namen, ein Duplikat nicht.
* **Rechnung:** Sie schreibt **jeden** gültigen Tarif als eigene Zeile:
  * `ZEV`/`VNB`: je Tarif eine Zeile mit **derselben Menge** (die kWh des Teilzeitraums), aber eigenem Preis und Betrag.
  * `GRUNDGEBUEHR`: je Tarif eine Zeile (Menge nach FR-3, Betrag nach FR-3 bzw. FR-6), auf **jeder** Konsumenten-Rechnung; bei Produzenten nur die Tarife mit „Produzent verrechnen".
  * Die Reihenfolge der Zeilen bestimmt FR-4.
* **Abgrenzung:**
  * **Lückenprüfung bleibt je Typ** (Rechnungserzeugung und „Quartale/Jahre validieren", `Specs/TarifValidierung.md`): Fehlt nur ein Bestandteil — etwa die Netznutzung, während die Energielieferung gilt —, meldet sie keine Lücke. Eine Prüfung je Bezeichnung würde bei jedem Namenswechsel („Netznutzung 2026" → „Netznutzung 2027") eine Scheinlücke melden.
  * Keine Gruppierung oder Zwischensumme der Bestandteile auf der Rechnung.
* (Hintergrund: Anhang A.3)

### FR-3: Grundgebühr pro Monat oder pro Tag
* **User Story:** Als Admin möchte ich eine Grundgebühr der ZEV-Stromrechnung **taggenau** abrechnen können, weil Lieferanten Grundtarife in CHF pro Tag und Zähler angeben und ein Mieterwechsel mitten im Monat sonst einen ganzen Monat ausfallen lässt.
* **Lösung:** Ein `GRUNDGEBUEHR`-Tarif trägt eine **Mengeneinheit**: `MONAT` oder `TAG`. Kein eigener Tariftyp — alles, was an der Grundgebühr hängt (FR-2, „Produzent verrechnen", automatische Aufnahme auf jede Rechnung), gilt für beide.

  | Mengeneinheit | Menge auf der Rechnung | Preis |
  |---|---|---|
  | `MONAT` (Vorgabe) | **volle** Kalendermonate im Teilzeitraum (angebrochene Monate zählen nicht) | CHF / Monat |
  | `TAG` | **jeder Tag** im Teilzeitraum, inklusive Beginn und Ende | CHF / Tag |

  Teilzeitraum = Schnitt aus Gültigkeit des Tarifs und Rechnungszeitraum (bei Mieterwechsel der Zeitraum des Mieters).
* **Erfassung:** Die Tarifmaske zeigt bei der Grundgebühr die Auswahl „Mengeneinheit" mit **Monat** und **Tag**; neu gewählt ist **Monat** vorbelegt. Der Hinweis erklärt den Unterschied (`GRUNDGEBUEHR_EINHEIT_HINT`).
* **Abgrenzung:** Nur die ZEV-Stromrechnung. Die Nebenkostenabrechnung kennt `TAG` nicht (ihre Constraints bleiben unverändert); ein Grundgebühr-Tarif für einzelne Einheiten bleibt ein `ZUSATZ`-Tarif.
* (Hintergrund: Anhang A.4)

### FR-4: Reihenfolge der Tarife und Panels nach Zeitraum
* **User Story:** Als Admin möchte ich die Reihenfolge der Tarife festlegen, damit die Positionen auf der Rechnung in einer bewussten Reihenfolge stehen statt nach Typ und Name. Die Tarife sind dazu nach Zeitraum in Panels gruppiert.
* **Panels** (einklappbar, `.zev-collapsible`), nach Gültigkeit:

  | Panel | Enthält | Beim Aufruf |
  |---|---|---|
  | Zukünftige Tarife | `gueltig_von` nach dem 31.12. des aktuellen Jahres | zu |
  | Tarife *Jahr* | gilt an mindestens einem Tag des aktuellen Jahres (auch über die Jahresgrenze, z.B. 01.10.2025–31.12.2026) | **offen** |
  | Frühere Tarife | `gueltig_bis` vor dem 01.01. des aktuellen Jahres | zu |

  Die Kopfzeile nennt die Zahl der Tarife; ein leeres Panel zeigt „Keine Tarife".
* **Reihenfolge:**
  * Verschieben per **Drag & Drop am Griff** (Angular CDK), **nur innerhalb eines Panels**. Die Gruppe tauscht dabei nur ihre eigenen Plätze; Tarife anderer Panels behalten ihren Platz.
  * Beim Ziehen zeigt die Vorschau **nur Tariftyp und Bezeichnung**, mit Abstand (`.zev-sortable__vorschau`).
    > Die kopierte ganze Zeile verlöre ausserhalb der Tabelle ihre Spaltenbreiten, und die Inhalte rutschten ineinander.
  * Gespeichert wird sofort, und zwar nur die **eine Verschiebung**: `PUT /api/tarife/{id}/verschieben?vor={zielId}` bzw. `?nach={zielId}` (nach oben vor den neuen Nachfolger, nach unten hinter den neuen Vorgänger). Der Server verschiebt den Tarif und rückt nur die Plätze **zwischen** Start und Ziel um eins nach. Bei einem Fehler zeigt die Liste wieder den gespeicherten Stand.
    > **Warum nicht die ganze Reihenfolge des Panels senden:** Zwei gleichzeitige Sitzungen überschrieben sich damit gegenseitig mit ihrem veralteten Stand — beobachtet im E2E-Lauf mit Chromium und Firefox parallel.
  * Die Spaltenköpfe sind **nicht sortierbar** — die Tabelle zeigt immer die Reihenfolge der Rechnung (Hintergrund: Anhang A.5).
  * Ein **neuer Tarif** kommt direkt hinter den letzten Tarif desselben Typs (die dahinter rücken nach); der erste Tarif eines Typs ans Ende. Beim Bearbeiten bleibt der Platz unverändert.
* **Rechnung:** Die Zeilen stehen in der Reihenfolge ihrer Tarife, **über alle Tariftypen hinweg** (ZEV, VNB, Tarifpositionen, Grundgebühr gemischt nach Reihenfolge). Mehrere Zeilen desselben Tarifs (z.B. Tarifpositionen) nach Beginn.
* (Hintergrund: Anhang A.5)

### FR-5: Preis 0 zulässig
* Ein Tarif darf den Preis **0** haben. Zweck ist **Goodwill**: Die Gebühr gibt es eigentlich, auf die Verrechnung wird aber verzichtet — und das soll auf der Rechnung sichtbar sein (auf Hene: „Grundgebühr Ladestation" mit Betrag 0.00). Negative Preise bleiben verboten.
* Das Preisfeld eines **neuen** Tarifs startet **leer** statt mit 0.
  > Sonst ginge ein vergessener Preis unbemerkt als Gratis-Tarif durch.
* (Hintergrund: Anhang A.6)

### FR-6: Jahrespreis, anteilig verrechnet
* **User Story:** Als Admin möchte ich bei einer Gebühr den **Preis für das ganze Jahr** erfassen; die Rechnung verteilt ihn anteilig auf die verrechneten Monate oder Tage.
* **Wo möglich:** bei der **Grundgebühr** (pro Monat oder pro Tag verteilt, FR-3) und bei **Zusatz-Tarifen mit Mengeneinheit Monat**. Bei kWh oder Stück gibt es keinen Anteil am Jahr — dort erscheint die Option nicht, und das Backend weist sie ab.
* **Erfassung:** Checkbox „Preis gilt für das ganze Jahr" in der Tarifmaske. In der Liste steht der Preis mit „/ Jahr".
* **Rechnung:**

  | Verteilung | Menge (in Worten) | Betrag |
  |---|---|---|
  | nach Monaten | „3 Monate" (volle Monate bzw. erfasste Monate der Tarifposition) | Jahrespreis × Monate / 12 |
  | nach Tagen | „61 Tage" | Jahrespreis × Tage / Tage des Kalenderjahres (365 bzw. 366); über den Jahreswechsel je Jahr gerechnet |

  Als Preis steht der **Jahrespreis** mit „CHF / Jahr". Die vier Quartalsrechnungen ergeben zusammen genau den Jahrespreis (nach Tagen auf 10 Nachkommastellen; gerundet wird wie immer nur der Endbetrag). Menge × Preis ergibt hier bewusst nicht direkt den Betrag — „pro Jahr" in der Preisspalte erklärt den Anteil.
* **PDF-Layout:** Die Mengenspalte ist 65 pt breit für „92 Tage", die Bezeichnung 265 pt; der Zeitraum wandert ab 52 Zeichen in die zweite Zeile (`RechnungZeilenText`) (Hintergrund: Anhang A.7).

### Datenmodell & Persistierung
* **Tabelle:** `zev.tarif` (ID aus einer Sequenz). Alle Spalten sind Pflicht:
  * **Bezeichnung:** maximale Länge 50 Zeichen (Anhang A.2), Beispiele: "vZEV PV Tarif" oder "Strombezug EWB"
  * **Tariftyp:**
    * "ZEV" für Strombezug aus dem ZEV (`messwerte.zev_calculated`)
    * "VNB" für Strombezug vom Verteilnetzbetreiber (`messwerte.total - messwerte.zev_calculated`)
    * "GRUNDGEBUEHR" für eine fixe Grundgebühr (z.B. Messgebühr, Netznutzungspauschale)
    * "LADESTROM" für Strom an Ladestationen (`Specs/Ladestromtarif.md`)
    * "ZUSATZ" für Tarifpositionen, die an der Position gewählt werden (`Specs/Tarifpositionen.md`)
  * **Tarif:** Genauigkeit 5 Nachkommastellen
  * **gueltig_von:** Datum
  * **gueltig_bis:** Datum
* **`tarif.mengeneinheit`** (FR-3): `MONAT` oder `TAG` bei der Grundgebühr. Migration `V176__Grundgebuehr_Pro_Tag.sql`: CHECK-Constraint um `TAG` erweitert, bestehende Grundgebühren auf `MONAT` gesetzt, Spaltenkommentar, Übersetzungen `TAG` („Tag"/„Day"), `TAGE`, `GRUNDGEBUEHR_EINHEIT_HINT`.
* **`tarif.reihenfolge INTEGER NOT NULL`** (FR-4, je Mandant fortlaufend). Migration `V178__Tarif_Reihenfolge.sql` belegt sie mit der vorherigen Reihenfolge der Rechnung (ZEV, VNB, Ladestrom, Zusatz, Grundgebühr; je Typ nach Beginn und Bezeichnung) — bestehende Rechnungen sehen danach gleich aus. Übersetzungen ebenda.
* **Preis** (FR-5): `@PositiveOrZero`; Meldung aus V179.
* **`tarif.preis_pro_jahr BOOLEAN NOT NULL DEFAULT false`** (FR-6, V180); bestehende Tarife unverändert.

### API-Endpunkte
* `PUT /api/tarife/{id}/verschieben?vor={zielId}` bzw. `?nach={zielId}` (FR-4), Permission `tarife:manage` wie die übrige Tarifverwaltung.

### Validierungen
* **Überschneidung** je Tariftyp nach FR-2 („Tarif überschneidet sich mit bestehendem Tarif gleicher Bezeichnung").
* **Mengeneinheit** der Grundgebühr nur `MONAT` oder `TAG` (FR-3, „Für die Grundgebühr ist nur die Mengeneinheit MONAT oder TAG zulässig").
* **Preis** erforderlich und nicht negativ (FR-5, „Preis ist erforderlich und darf nicht negativ sein").
* **Jahrespreis** nur bei Grundgebühr und Zusatz pro Monat (FR-6).
* **Verschieben** (FR-4): fehlende oder doppelte Ziele, unbekannte (auch mandantenfremde) Tarife und ein Verschieben relativ zu sich selbst → 400.

### Layout & Navigation
* **Badge-Darstellung Tariftyp** (Tarifverwaltungstabelle):
  * `ZEV` → hellgrüner Hintergrund, dunkelgrüne Schrift (`.tarif-typ-badge--zev`)
  * `VNB` → hellorangefarbener Hintergrund, dunkle Schrift (`.tarif-typ-badge--vnb`)
  * `GRUNDGEBUEHR` → anthrazitfarbener Hintergrund (#424242), weisse Schrift (`.tarif-typ-badge--grundgebuehr`)
  * `LADESTROM`, `ZUSATZ` → Grunddarstellung ohne eigene Farbe (`.tarif-typ-badge`)
* **Design System:** `sortable.css` (`.zev-table__griff`, Stile der CDK-Drag-Vorschau — global, weil Angular die Vorschau an den `<body>` hängt).

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

### Mehrere gleichzeitig gültige Tarife (FR-2)
* [x] Zwei `VNB`-Tarife „Energielieferung" und „Netznutzung" mit gleicher Gültigkeit lassen sich speichern.
* [x] Drei `GRUNDGEBUEHR`-Tarife (Energielieferung, Netznutzung, Messtarif) mit gleicher Gültigkeit lassen sich speichern.
* [x] Ein zweiter Tarif desselben Typs mit **gleicher Bezeichnung** und überschneidender Gültigkeit wird abgewiesen („Tarif überschneidet sich mit bestehendem Tarif gleicher Bezeichnung"); auch bei anderer Gross-/Kleinschreibung oder Leerzeichen am Rand.
* [x] Beim Bearbeiten eines Tarifs zählt er nicht als Überschneidung mit sich selbst.
* [x] Ein zweiter `LADESTROM`-Tarif mit überschneidender Gültigkeit wird weiterhin abgewiesen, auch mit anderer Bezeichnung.
* [x] Auf der Rechnung erscheinen zwei gleichzeitig gültige `VNB`-Tarife als zwei Zeilen mit derselben Menge; drei Grundgebühren als drei Zeilen; das Total ist ihre Summe.
* [x] Die Überschneidungsprüfung sieht nur Tarife des eigenen Mandanten.

### Grundgebühr pro Monat oder pro Tag (FR-3)
* [x] Ein Grundgebühr-Tarif lässt sich mit Mengeneinheit „Tag" speichern; ohne Angabe gilt „Monat".
* [x] Eine andere Mengeneinheit als Monat oder Tag wird bei der Grundgebühr abgewiesen („Für die Grundgebühr ist nur die Mengeneinheit MONAT oder TAG zulässig").
* [x] Grundgebühr pro Tag, ganzes Q3 2026: Menge 92, Einheit „Tag", Betrag 92 × Preis.
* [x] Grundgebühr pro Tag, gültig ab 16.08.2026, Rechnung Q3: Menge 46.
* [x] Grundgebühr pro Monat, gültig ab 16.08.2026, Rechnung Q3: Menge 1 (nur September) — unverändert.
* [x] Bestehende Grundgebühren rechnen nach der Migration wie bisher (Mengeneinheit `MONAT`).
* [x] Die Tarifmaske bietet bei der Grundgebühr nur Monat und Tag an, bei ZUSATZ weiterhin kWh, Monat, Stück; bei jedem Wechsel des Typs wird die Einheit neu gesetzt — Grundgebühr: Monat vorbelegt; ZUSATZ: leer, muss bewusst gewählt werden (die Vorbelegung „Monat" der Grundgebühr wandert nicht in einen ZUSATZ-Tarif).
* [x] Auf der Rechnung (PDF) steht „CHF / Tag".

### Reihenfolge und Panels (FR-4)
* [x] Die Tarifverwaltung zeigt drei Panels: Zukünftige Tarife, Tarife *aktuelles Jahr*, Frühere Tarife; beim Aufruf ist nur das aktuelle Jahr offen.
* [x] Ein Tarif von 01.10. des Vorjahres bis 31.12. dieses Jahres steht im Panel des aktuellen Jahres.
* [x] Innerhalb eines Panels lässt sich ein Tarif am Griff verschieben; die neue Reihenfolge bleibt nach dem Neuladen erhalten.
* [x] Zwischen Panels lässt sich nicht verschieben (je Panel eine eigene Drop-Liste).
* [x] Die Spaltenköpfe sind nicht mehr sortierbar.
* [x] Das Kebab-Menü der unteren Zeilen wird nicht abgeschnitten: Die Panels schneiden nicht ab (`.zev-collapsible--overflow-visible`), und das Menü klappt allgemein **nach oben** auf, wenn darunter bis zum nächsten abschneidenden Element (Panel, scrollende Tabelle nach Spaltenbreiten-Änderung) oder zum Fensterrand zu wenig Platz ist (`app-kebab-menu`, `.zev-kebab-menu--up`) — auf allen Listen.
* [x] Auf der Rechnung stehen die Zeilen in der Reihenfolge der Tarife, auch über Typen hinweg (z.B. Grundgebühr vor Strombezug).
* [x] Ein neuer VNB-Tarif steht direkt hinter dem letzten VNB-Tarif; ein Bearbeiten ändert den Platz nicht.
* [x] `PUT /api/tarife/{id}/verschieben` weist fehlende oder doppelte Ziele, unbekannte (auch mandantenfremde) Tarife und ein Verschieben relativ zu sich selbst mit 400 ab.
* [x] Eine Verschiebung ändert nur den verschobenen Tarif und die Plätze zwischen Start und Ziel; andere Tarife, auch anderer Mandanten, bleiben unberührt.
* [x] Auch ein Tarif mit Altbestand-Werten ausserhalb der heutigen Regeln (auf Hene: „Grundgebühr Ladestation" mit Preis 0) lässt sich verschieben — die Reihenfolge wird als einzelne Spalte gesetzt, nicht über die Entity mit ihrer Bean Validation.
* [x] Nach der Migration ist die Reihenfolge der Rechnung unverändert.

### Preis 0 (FR-5)
* [x] Ein Tarif mit Preis 0 lässt sich anlegen und bearbeiten; ein negativer Preis wird abgewiesen (Backend `@PositiveOrZero`, Formular `min="0"`).
* [x] Ein neues Formular zeigt ein leeres Preisfeld; ohne Preis bleibt Speichern gesperrt.
* [x] Die Meldung lautet „Preis ist erforderlich und darf nicht negativ sein" (V179).

### Jahrespreis (FR-6)
* [x] Grundgebühr 100.– pro Jahr nach Monaten: die vier Quartale ergeben zusammen genau 100.–.
* [x] Nach Tagen 2026 (365) und 2028 (366): die Quartale ergeben zusammen den Jahrespreis.
* [x] Nach Tagen, gültig ab 01.08.2026, Rechnung Q3: Menge „61 Tage", Betrag 61/365 des Jahrespreises.
* [x] Über den Jahreswechsel wird je Kalenderjahr mit dessen Tagen gerechnet.
* [x] Zusatz-Tarif pro Monat mit Jahrespreis: Betrag = Jahrespreis × erfasste Monate / 12.
* [x] Auf der Rechnung: Menge „3 Monate", Preis 120.00000, Einheit „CHF / Jahr".
* [x] Ohne Jahrespreis bleibt die Rechnung wie bisher (Menge × Preis, „CHF / Monat").
* [x] Ein Jahrespreis bei ZEV, VNB, Ladestrom oder Zusatz pro Stück/kWh wird abgewiesen.

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Optimierte Berechnung der Rechnungsbeträge: Gehe für die Berechnung der Kosten nicht Zeile für Zeile der messwerte.zeit vor, sondern bilde Teil-Zeitbereiche gemäss Gültigkeit der Tarife.

### NFR-2: Sicherheit
* Die Tarifverwaltung verlangt die Permission `tarife:manage` (Route `/tarife` und Endpunkte); sie haben die Fachrollen `org_admin` und `zev_admin` (`Specs/Berechtigungen.md`, Hintergrund: Anhang A.8).
* Das Verschieben (FR-4) verlangt `tarife:manage`; die Überschneidungsprüfung (FR-2) und das Verschieben sehen nur Tarife des eigenen Mandanten.

### NFR-3: Kompatibilität
* Die Migrationen V176 und V178 ändern bestehende Rechnungen nicht: Grundgebühren rechnen weiter pro Monat, die Reihenfolge der Rechnung bleibt.

## 5. Edge Cases & Fehlerbehandlung
* Es muss für den gesamten Zeitbereich der Rechnung ein gültiger Tarif **je Typ** (ZEV, VNB) vorhanden sein; mehrere gleichzeitig gültige Tarife eines Typs decken den Zeitraum gemeinsam ab (FR-2). Falls dies nicht der Fall ist, soll eine Fehlermeldung angezeigt werden.
* Fehler beim Verschieben → die Liste zeigt wieder den gespeicherten Stand (FR-4).
* Tarif mit Altbestand-Werten ausserhalb der heutigen Regeln → lässt sich trotzdem verschieben (FR-4).

## 6. Abhängigkeiten & betroffene Funktionalität
* **Verwandte Specs:** `Specs/RechnungenGenerieren.md` (Rechnung, Länge der Bezeichnung), `Specs/TarifValidierung.md` (Lückenprüfung), `Specs/Ladestromtarif.md` (`LADESTROM`), `Specs/Tarifpositionen.md` (`ZUSATZ`).
* **Betroffener Code:** Rechnungserzeugung und PDF (`RechnungZeilenText`), Tarifmaske und Tarifliste, Design System (`sortable.css`, `.zev-collapsible--overflow-visible`, `.zev-kebab-menu--up`), Komponente `app-kebab-menu` (wirkt auf allen Listen).
* **Datenmigration:** V176 (Mengeneinheit), V178 (Reihenfolge), V179 (Preis 0), V180 (Jahrespreis) — siehe Datenmodell.

## 7. Abgrenzung / Out of Scope
* Keine Lückenprüfung je Bezeichnung und keine Gruppierung oder Zwischensumme der Bestandteile (FR-2).
* `TAG` nur für die ZEV-Stromrechnung, nicht für die Nebenkostenabrechnung (FR-3).
* Kein Verschieben zwischen Panels (FR-4).

## 8. Offene Fragen
* keine

## Anhang A – Entscheidungen und Verlauf

### A.1 Ursprüngliche Tarife in application.yml (FR-1)
Die Tarife waren ohne Gültigkeitsbereich in `application.yml` spezifiziert: `rechnung.tarif.zev` (ZEV-Strom, `messwerte.zev_calculated`) und `rechnung.tarif.ewb` (VNB-Strom, `messwerte.total - messwerte.zev_calculated`). Die ursprüngliche Spec verlangte: „Die aktuellen Spezifikationen der beiden Tarife rechnung.tarif aus application.yml entfernen."

### A.2 Länge der Bezeichnung (Datenmodell, 10.10.2026)
Bis 10.10.2026 war die Bezeichnung auf 30 Zeichen begrenzt; seither 50 (`Specs/RechnungenGenerieren.md`).

### A.3 Mehrere gleichzeitig gültige Tarife je Typ (FR-2, Entscheid vom 10.10.2026)
Vorher galt für `ZEV`, `VNB` und `GRUNDGEBUEHR` je Typ höchstens ein Tarif zur selben Zeit. Für `LADESTROM` (`Specs/Ladestromtarif.md`) und `ZUSATZ` (`Specs/Tarifpositionen.md`) blieb die jeweilige Regel unverändert. Die Logik der Rechnung musste nicht angepasst werden: Sie schrieb schon damals jeden gültigen Tarif als eigene Zeile. Die Reihenfolge gleichzeitig gültiger Tarife war zuerst „nach Gültigkeitsbeginn, dann nach Bezeichnung — auf jeder Rechnung gleich"; seit FR-4 bestimmt sie die Spalte `reihenfolge`.

### A.4 Grundgebühr pro Tag (FR-3, Entscheid vom 10.10.2026)
Gewählt wurde Variante c: eine Mengeneinheit am bestehenden Tariftyp statt eines neuen Typs. Vorher rechnete jede Grundgebühr nach vollen Monaten; das gilt für `MONAT` unverändert weiter, und die Migration V176 hat alle bestehenden Grundgebühren auf `MONAT` gesetzt.

### A.5 Reihenfolge und Panels (FR-4, Entscheid vom 11.10.2026)
Vorher standen die Zeilen der Rechnung nach Typ und Name, und die Tarifverwaltung liess sich per Klick auf die Spaltenköpfe sortieren. Die Spaltensortierung entfiel mit FR-4, damit die Tabelle immer die Reihenfolge der Rechnung zeigt. Die Spalte `tarif.reihenfolge` kam mit V178 neu dazu.

### A.6 Preis 0 zulässig (FR-5, Entscheid vom 11.10.2026)
Bisher galt `@Positive`; ein solcher Altbestand-Tarif liess sich nicht mehr bearbeiten und brach das Umsortieren (FR-4) ab.

### A.7 Jahrespreis und PDF-Layout (FR-6, Entscheid vom 11.10.2026)
Für „92 Tage" wurde die Mengenspalte im PDF von 35 auf 65 pt verbreitert, die Bezeichnung von 305 auf 265 pt verschmälert; der Zeitraum wandert seither ab 52 statt 60 Zeichen in die zweite Zeile.

### A.8 Berechtigung und Menü (NFR-2, FR-1, 18.10.2026)
Die ursprüngliche Spec verlangte die Rolle `zev_admin` und nannte als Menüplatz „unterhalb /einheiten“. Mit der Umstellung auf Permissions (`Specs/Berechtigungen.md`) verlangt die Tarifverwaltung `tarife:manage`, das auch `org_admin` hat. „Einheiten“ liegt seit 17.10.2026 im Untermenü „Admin“ (`Specs/Hamburgermenü.md`), die Tarifverwaltung blieb auf der ersten Ebene. Bei der Konsolidierung am 18.10.2026 nachgeführt, ebenso der Tabellenname (`zev.tarif` statt „tarife“), die Tariftypen `LADESTROM` und `ZUSATZ` sowie die Grundgebühr in FR-2, deren Menge seit FR-3/FR-6 nicht mehr nur „volle Monate“ ist.
