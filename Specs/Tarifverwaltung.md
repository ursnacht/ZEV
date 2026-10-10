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
