# Tarifverwaltung

## 1. Ziel & Kontext
* **Was soll erreicht werden?** Die Tarife können sich von Quartal zu Quartal ändern. Dies muss berücksichtigt werden.
* **Warum machen wir das?** Beim Erstellen der Rechnungen müssen die Tarife verwendet werden, die für den Zeitraum der Rechnung gültig sind.
* **Aktueller Stand:** Die Tarife sind ohne Gültigkeitsbereich in application.yml spezifiziert.

## 2. Funktionale Anforderungen (Functional Requirements)
* **User Story:** Als Admin möchte ich zu den Tarifen auch die Gültigkeit (von, bis) angeben können, damit beim Erstellen der Rechnungen die richtigen Tarife verwendet werden.
* **Ablauf / Flow:**
  1. Der User kann im Menü die Seite "Tarifverwaltung" aufrufen (im Menü unterhalb /einheiten).
  2. In der Tarifverwaltung können bestehende Tarife bearbeitet oder gelöscht und neue Tarife hinzugefügt werden.
  3. Beim Erstellen von Rechnungen werden die für den Zeitbereich der Rechnung gültigen Tarife verwendet.

### FR-2: Mehrere gleichzeitig gültige Tarife je Typ (Entscheid vom 10.10.2026)
* **User Story:** Als Admin möchte ich für denselben Zeitraum mehrere Tarife desselben Typs erfassen, weil sich der Preis aus mehreren Bestandteilen zusammensetzt:
  * **Grundgebühr:** Energielieferung, Netznutzung, Messtarif
  * **Strombezug (VNB, ZEV):** Energielieferung und Netznutzung
* **Regel je Tariftyp:**

  | Tariftyp | Mehrere gleichzeitig gültig? | Abgewiesen wird |
  |---|---|---|
  | `ZEV`, `VNB`, `GRUNDGEBUEHR` | **ja** | eine Überschneidung mit einem Tarif desselben Typs **und derselben Bezeichnung** (ohne Gross-/Kleinschreibung, ohne Leerzeichen am Rand) |
  | `LADESTROM` | nein (unverändert, `Specs/Ladestromtarif.md`) | jede Überschneidung desselben Typs — die Position ist je Typ eindeutig, ein zweiter Tarif hiesse, dieselben kWh zweimal zu erfassen |
  | `ZUSATZ` | ja (unverändert, `Specs/Tarifpositionen.md`) | nichts — der Tarif wird an der Position gewählt |

* **Warum die Bezeichnung als Schutz:** Ein versehentlich doppelt erfasster oder mit falschen Daten kopierter Tarif stünde sonst ein zweites Mal auf **jeder** Rechnung. „Energielieferung" und „Netznutzung" unterscheiden sich im Namen, ein Duplikat nicht.
* **Rechnung:** unverändert in der Logik — sie schreibt schon heute **jeden** gültigen Tarif als eigene Zeile:
  * `ZEV`/`VNB`: je Tarif eine Zeile mit **derselben Menge** (die kWh des Teilzeitraums), aber eigenem Preis und Betrag.
  * `GRUNDGEBUEHR`: je Tarif eine Zeile (volle Monate × Preis), auf **jeder** Konsumenten-Rechnung; bei Produzenten nur die Tarife mit „Produzent verrechnen".
  * **Reihenfolge** gleichzeitig gültiger Tarife: nach Gültigkeitsbeginn, dann nach Bezeichnung — auf jeder Rechnung gleich.
* **Akzeptanzkriterien:**
  * [x] Zwei `VNB`-Tarife „Energielieferung" und „Netznutzung" mit gleicher Gültigkeit lassen sich speichern.
  * [x] Drei `GRUNDGEBUEHR`-Tarife (Energielieferung, Netznutzung, Messtarif) mit gleicher Gültigkeit lassen sich speichern.
  * [x] Ein zweiter Tarif desselben Typs mit **gleicher Bezeichnung** und überschneidender Gültigkeit wird abgewiesen („Tarif überschneidet sich mit bestehendem Tarif gleicher Bezeichnung"); auch bei anderer Gross-/Kleinschreibung oder Leerzeichen am Rand.
  * [x] Beim Bearbeiten eines Tarifs zählt er nicht als Überschneidung mit sich selbst.
  * [x] Ein zweiter `LADESTROM`-Tarif mit überschneidender Gültigkeit wird weiterhin abgewiesen, auch mit anderer Bezeichnung.
  * [x] Auf der Rechnung erscheinen zwei gleichzeitig gültige `VNB`-Tarife als zwei Zeilen mit derselben Menge; drei Grundgebühren als drei Zeilen; das Total ist ihre Summe.
  * [x] Die Überschneidungsprüfung sieht nur Tarife des eigenen Mandanten.
* **Abgrenzung:**
  * **Lückenprüfung bleibt je Typ** (Rechnungserzeugung und „Quartale/Jahre validieren", `Specs/TarifValidierung.md`): Fehlt nur ein Bestandteil — etwa die Netznutzung, während die Energielieferung gilt —, meldet sie keine Lücke. Eine Prüfung je Bezeichnung würde bei jedem Namenswechsel („Netznutzung 2026" → „Netznutzung 2027") eine Scheinlücke melden.
  * Keine Gruppierung oder Zwischensumme der Bestandteile auf der Rechnung.

### FR-3: Grundgebühr pro Monat oder pro Tag (Entscheid vom 10.10.2026)
* **User Story:** Als Admin möchte ich eine Grundgebühr der ZEV-Stromrechnung **taggenau** abrechnen können, weil Lieferanten Grundtarife in CHF pro Tag und Zähler angeben und ein Mieterwechsel mitten im Monat sonst einen ganzen Monat ausfallen lässt.
* **Lösung (Variante c):** Ein `GRUNDGEBUEHR`-Tarif trägt eine **Mengeneinheit**: `MONAT` oder `TAG`. Kein neuer Tariftyp — alles, was an der Grundgebühr hängt (FR-2, „Produzent verrechnen", automatische Aufnahme auf jede Rechnung), gilt unverändert für beide.

  | Mengeneinheit | Menge auf der Rechnung | Preis |
  |---|---|---|
  | `MONAT` (Vorgabe) | **volle** Kalendermonate im Teilzeitraum (unverändert; angebrochene Monate zählen nicht) | CHF / Monat |
  | `TAG` | **jeder Tag** im Teilzeitraum, inklusive Beginn und Ende | CHF / Tag |

  Teilzeitraum = Schnitt aus Gültigkeit des Tarifs und Rechnungszeitraum (bei Mieterwechsel der Zeitraum des Mieters).
* **Erfassung:** Die Tarifmaske zeigt bei der Grundgebühr die Auswahl „Mengeneinheit" mit **Monat** und **Tag**; neu gewählt ist **Monat** vorbelegt. Der Hinweis erklärt den Unterschied (`GRUNDGEBUEHR_EINHEIT_HINT`).
* **Persistierung:** `tarif.mengeneinheit` (bestehende Spalte). Migration `V176__Grundgebuehr_Pro_Tag.sql`: CHECK-Constraint um `TAG` erweitert, bestehende Grundgebühren auf `MONAT` gesetzt, Spaltenkommentar, Übersetzungen `TAG` („Tag"/„Day"), `TAGE`, `GRUNDGEBUEHR_EINHEIT_HINT`.
* **Akzeptanzkriterien:**
  * [x] Ein Grundgebühr-Tarif lässt sich mit Mengeneinheit „Tag" speichern; ohne Angabe gilt „Monat".
  * [x] Eine andere Mengeneinheit als Monat oder Tag wird bei der Grundgebühr abgewiesen („Für die Grundgebühr ist nur die Mengeneinheit MONAT oder TAG zulässig").
  * [x] Grundgebühr pro Tag, ganzes Q3 2026: Menge 92, Einheit „Tag", Betrag 92 × Preis.
  * [x] Grundgebühr pro Tag, gültig ab 16.08.2026, Rechnung Q3: Menge 46.
  * [x] Grundgebühr pro Monat, gültig ab 16.08.2026, Rechnung Q3: Menge 1 (nur September) — unverändert.
  * [x] Bestehende Grundgebühren rechnen nach der Migration wie bisher (Mengeneinheit `MONAT`).
  * [x] Die Tarifmaske bietet bei der Grundgebühr nur Monat und Tag an, bei ZUSATZ weiterhin kWh, Monat, Stück; bei jedem Wechsel des Typs wird die Einheit neu gesetzt — Grundgebühr: Monat vorbelegt; ZUSATZ: leer, muss bewusst gewählt werden (die Vorbelegung „Monat" der Grundgebühr wandert nicht in einen ZUSATZ-Tarif).
  * [x] Auf der Rechnung (PDF) steht „CHF / Tag".
* **Abgrenzung:** Nur die ZEV-Stromrechnung. Die Nebenkostenabrechnung kennt `TAG` nicht (ihre Constraints bleiben unverändert); ein Grundgebühr-Tarif für einzelne Einheiten bleibt ein `ZUSATZ`-Tarif.

### FR-4: Reihenfolge der Tarife und Panels nach Zeitraum (Entscheid vom 11.10.2026)
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
  * Beim Ziehen zeigt die Vorschau **nur Tariftyp und Bezeichnung**, mit Abstand (`.zev-sortable__vorschau`). Die kopierte ganze Zeile verlöre ausserhalb der Tabelle ihre Spaltenbreiten, und die Inhalte rutschten ineinander.
  * Gespeichert wird sofort, und zwar nur die **eine Verschiebung**: `PUT /api/tarife/{id}/verschieben?vor={zielId}` bzw. `?nach={zielId}` (nach oben vor den neuen Nachfolger, nach unten hinter den neuen Vorgänger; Permission `tarife:manage` wie die übrige Tarifverwaltung). Der Server verschiebt den Tarif und rückt nur die Plätze **zwischen** Start und Ziel um eins nach. Bei einem Fehler zeigt die Liste wieder den gespeicherten Stand.
  * **Warum nicht die ganze Reihenfolge des Panels senden:** Zwei gleichzeitige Sitzungen überschrieben sich damit gegenseitig mit ihrem veralteten Stand — beobachtet im E2E-Lauf mit Chromium und Firefox parallel.
  * Die **Spaltensortierung** per Klick auf die Spaltenköpfe **entfällt** — die Tabelle zeigt immer die Reihenfolge der Rechnung.
  * Ein **neuer Tarif** kommt direkt hinter den letzten Tarif desselben Typs (die dahinter rücken nach); der erste Tarif eines Typs ans Ende. Beim Bearbeiten bleibt der Platz unverändert.
* **Rechnung:** Die Zeilen stehen in der Reihenfolge ihrer Tarife, **über alle Tariftypen hinweg** (ZEV, VNB, Tarifpositionen, Grundgebühr gemischt nach Reihenfolge). Mehrere Zeilen desselben Tarifs (z.B. Tarifpositionen) nach Beginn.
* **Persistierung:** neue Spalte `tarif.reihenfolge INTEGER NOT NULL` (je Mandant fortlaufend). Migration `V178__Tarif_Reihenfolge.sql` belegt sie mit der bisherigen Reihenfolge der Rechnung (ZEV, VNB, Ladestrom, Zusatz, Grundgebühr; je Typ nach Beginn und Bezeichnung) — bestehende Rechnungen sehen danach gleich aus. Übersetzungen ebenda.
* **Design System:** neu `sortable.css` (`.zev-table__griff`, Stile der CDK-Drag-Vorschau — global, weil Angular die Vorschau an den `<body>` hängt).
* **Akzeptanzkriterien:**
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

### FR-5: Preis 0 zulässig (Entscheid vom 11.10.2026)
* Ein Tarif darf den Preis **0** haben. Zweck ist **Goodwill**: Die Gebühr gibt es eigentlich, auf die Verrechnung wird aber verzichtet — und das soll auf der Rechnung sichtbar sein (auf Hene: „Grundgebühr Ladestation" mit Betrag 0.00). Negative Preise bleiben verboten.
* Bisher galt `@Positive`; ein solcher Altbestand-Tarif liess sich nicht mehr bearbeiten und brach das Umsortieren (FR-4) ab.
* Das Preisfeld eines **neuen** Tarifs startet **leer** statt mit 0 — sonst ginge ein vergessener Preis unbemerkt als Gratis-Tarif durch.
* **Akzeptanzkriterien:**
  * [x] Ein Tarif mit Preis 0 lässt sich anlegen und bearbeiten; ein negativer Preis wird abgewiesen (Backend `@PositiveOrZero`, Formular `min="0"`).
  * [x] Ein neues Formular zeigt ein leeres Preisfeld; ohne Preis bleibt Speichern gesperrt.
  * [x] Die Meldung lautet „Preis ist erforderlich und darf nicht negativ sein" (V179).

### FR-6: Jahrespreis, anteilig verrechnet (Entscheid vom 11.10.2026)
* **User Story:** Als Admin möchte ich bei einer Gebühr den **Preis für das ganze Jahr** erfassen; die Rechnung verteilt ihn anteilig auf die verrechneten Monate oder Tage.
* **Wo möglich:** bei der **Grundgebühr** (pro Monat oder pro Tag verteilt, FR-3) und bei **Zusatz-Tarifen mit Mengeneinheit Monat**. Bei kWh oder Stück gibt es keinen Anteil am Jahr — dort erscheint die Option nicht, und das Backend weist sie ab.
* **Erfassung:** Checkbox „Preis gilt für das ganze Jahr" in der Tarifmaske. In der Liste steht der Preis mit „/ Jahr".
* **Rechnung:**

  | Verteilung | Menge (in Worten) | Betrag |
  |---|---|---|
  | nach Monaten | „3 Monate" (volle Monate bzw. erfasste Monate der Tarifposition) | Jahrespreis × Monate / 12 |
  | nach Tagen | „61 Tage" | Jahrespreis × Tage / Tage des Kalenderjahres (365 bzw. 366); über den Jahreswechsel je Jahr gerechnet |

  Als Preis steht der **Jahrespreis** mit „CHF / Jahr". Die vier Quartalsrechnungen ergeben zusammen genau den Jahrespreis (nach Tagen auf 10 Nachkommastellen; gerundet wird wie immer nur der Endbetrag). Menge × Preis ergibt hier bewusst nicht direkt den Betrag — „pro Jahr" in der Preisspalte erklärt den Anteil.
* **PDF-Layout:** Die Mengenspalte ist breiter (65 statt 35 pt) für „92 Tage"; die Bezeichnung dafür schmaler (265 statt 305 pt), der Zeitraum wandert ab 52 statt 60 Zeichen in die zweite Zeile (`RechnungZeilenText`).
* **Persistierung:** `tarif.preis_pro_jahr BOOLEAN NOT NULL DEFAULT false` (V180); bestehende Tarife unverändert.
* **Akzeptanzkriterien:**
  * [x] Grundgebühr 100.– pro Jahr nach Monaten: die vier Quartale ergeben zusammen genau 100.–.
  * [x] Nach Tagen 2026 (365) und 2028 (366): die Quartale ergeben zusammen den Jahrespreis.
  * [x] Nach Tagen, gültig ab 01.08.2026, Rechnung Q3: Menge „61 Tage", Betrag 61/365 des Jahrespreises.
  * [x] Über den Jahreswechsel wird je Kalenderjahr mit dessen Tagen gerechnet.
  * [x] Zusatz-Tarif pro Monat mit Jahrespreis: Betrag = Jahrespreis × erfasste Monate / 12.
  * [x] Auf der Rechnung: Menge „3 Monate", Preis 120.00000, Einheit „CHF / Jahr".
  * [x] Ohne Jahrespreis bleibt die Rechnung wie bisher (Menge × Preis, „CHF / Monat").
  * [x] Ein Jahrespreis bei ZEV, VNB, Ladestrom oder Zusatz pro Stück/kWh wird abgewiesen.

## 3. Technische Spezifikationen (Technical Specs)
* **DB-Änderungen:**
  * Das System speichert die Tarife in der Datenbank in einer neuen Tabelle "tarife".
  * Die Tabelle enthält folgende Spalten:
    * ID aus einer Sequenz
    * Bezeichnung: maximale Länge 50 Zeichen (bis 10.10.2026: 30, `Specs/RechnungenGenerieren.md`), Beispiele: "vZEV PV Tarif" oder "Strombezug EWB"
    * Tariftyp
      * "ZEV" für Strombezug aus dem ZEV, bisher rechnung.tarif.zev (messwerte.zev_calculated)
      * "VNB" für Strombezug vom Verteilnetzbetreiber, bisher rechnung.tarif.ewb (messwerte.total - messwerte.zev_calculated)
      * "GRUNDGEBUEHR" für eine fixe Grundgebühr (z.B. Messgebühr, Netznutzungspauschale)
    * Tarif: Genauigkeit 5 Nachkommastellen
    * gueltig_von: Datum
    * gueltig_bis: Datum
  * Alle Spalten sind Pflicht
* **Badge-Darstellung Tariftyp** (Tarifverwaltungstabelle):
  * `ZEV` → hellgrüner Hintergrund, dunkelgrüne Schrift (`.tarif-typ-badge--zev`)
  * `VNB` → hellorangefarbener Hintergrund, dunkle Schrift (`.tarif-typ-badge--vnb`)
  * `GRUNDGEBUEHR` → anthrazitfarbener Hintergrund (#424242), weisse Schrift (`.tarif-typ-badge--grundgebuehr`)
* Rechnungen 
  * Für den Zeitbereich einer Rechnung (i.d.R. ein Quartal) können mehrere Tarife gültig sein, da diese monatlich angepasst werden.
  * Der Stromverbrauch muss pro Zeile für den Gültigkeitsbereich des Tarifes und nicht der Rechnung berechnet werden.
  * Auf der Rechnung können somit mehrere Zeilen für den Strombezug vom ZEV und vom VNB nötig sein.
  * Rundungen der Mengen auf den Rechnungen beibehalten
* Die aktuellen Spezifikationen der beiden Tarife rechnung.tarif aus application.yml entfernen.

## 4. Nicht-funktionale Anforderungen
* Sicherheit: die Tarifverwaltung ist nur mit der Rolle zev_admin aufrufbar
* Optimierte Berechnung der Rechnungsbeträge: Gehe für die Berechnung der Kosten nicht Zeile für Zeile der messwerte.zeit vor, sondern bilde Teil-Zeitbereiche gemäss Gültigkeit der Tarife.

## 5. Edge Cases & Fehlerbehandlung
* Es muss für den gesamten Zeitbereich der Rechnung ein gültiger Tarif **je Typ** (ZEV, VNB) vorhanden sein; mehrere gleichzeitig gültige Tarife eines Typs decken den Zeitraum gemeinsam ab (FR-2). Falls dies nicht der Fall ist, soll eine Fehlermeldung angezeigt werden. 
