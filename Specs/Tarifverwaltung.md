# Tarifverwaltung

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Die Tarife können sich von Quartal zu Quartal ändern. Dies muss berücksichtigt werden. Neben den verbrauchsabhängigen Tarifen (ZEV, VNB) gibt es eine **Grundgebühr** pro Stromzähler, die auch Produzenten verrechnet werden kann (FR-7, FR-8), und Tarife lassen sich durch **Kopieren** erfassen (FR-9).
* **Warum machen wir das:** Beim Erstellen der Rechnungen müssen die Tarife verwendet werden, die für den Zeitraum der Rechnung gültig sind. Energieversorger verlangen neben dem Arbeitspreis (CHF/kWh) auch eine Grundgebühr pro Zähler; sie muss Konsumenten und — wo vereinbart — Produzenten verrechnet werden.
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
* **Lösung:** Ein `GRUNDGEBUEHR`-Tarif (FR-7) trägt eine **Mengeneinheit**: `MONAT` oder `TAG`. Kein eigener Tariftyp — alles, was an der Grundgebühr hängt (FR-2, „Produzent verrechnen", automatische Aufnahme auf jede Rechnung), gilt für beide.

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

### FR-7: Tariftyp Grundgebühr
* **Tariftyp `GRUNDGEBUEHR`:** ein Festpreis **pro Stromzähler**, der zusätzlich zu den verbrauchsabhängigen `ZEV`- und `VNB`-Tarifen auf der Rechnung steht. `bezeichnung`, `preis`, `gueltig_von` und `gueltig_bis` werden wie bei den anderen Tariftypen erfasst; Genauigkeit `preis` 5 Nachkommastellen.
* **Menge und Betrag** richten sich nach der Mengeneinheit (FR-3: volle Kalendermonate oder Tage) und gegebenenfalls nach dem Jahrespreis (FR-6). Der Teilzeitraum ist der Schnitt aus Gültigkeit des Tarifs und Rechnungszeitraum.
* **Bei Konsumenten** wird jede gültige Grundgebühr verrechnet, automatisch und ohne Erfassung einer Position.
* **Optional:** Fehlt ein `GRUNDGEBUEHR`-Tarif für den Rechnungszeitraum, wird keine Grundgebühr berechnet — kein Fehler. Die Lückenprüfung (`Specs/TarifValidierung.md`, FR-1.5) prüft nur `ZEV` und `VNB`.
* **Flag „Auch Produzenten verrechnen" (`produzentVerrechnen`, Spalte `produzent_verrechnen`):**
  * In der Tarifmaske als **Checkbox**, sichtbar nur beim Tariftyp `GRUNDGEBUEHR`; Standard `false`.
  * Konsumenten zahlen die Grundgebühr unabhängig vom Flag; Produzenten nur bei `true` (FR-8).
* **Tarif-Übersicht:** Die Spalte „Produzenten" zeigt bei `GRUNDGEBUEHR`-Tarifen den Wert des Flags als **deaktivierte (read-only) Checkbox** — angehakt = `true`. Bearbeitet wird das Flag nur in der Tarifmaske. Bei allen anderen Tariftypen steht „–".
* **Darstellung auf der Rechnung (PDF):** im gleichen Zeilenlayout wie `ZEV`/`VNB`, kein eigener Abschnitt — Bezeichnung | Zeitraum | Menge mit Einheit | Preis | Betrag. Die Reihenfolge bestimmt FR-4. Der `endBetrag` enthält die Grundgebühr.
* Läuft ein `GRUNDGEBUEHR`-Tarif mitten in der Rechnungsperiode ab, entstehen zwei Zeilen (wie bei `ZEV`/`VNB`).
* Alle UI-Texte der Grundgebühr kommen aus dem `TranslationService` (`Specs/generell.md`).
* (Hintergrund: Anhang A.9)

### FR-8: Rechnungen für Produzenten
* Produzenten (`EinheitTyp.PRODUCER`) erhalten eigene Rechnungen, die **ausschliesslich** `GRUNDGEBUEHR`-Zeilen enthalten — nur für Tarife mit `produzentVerrechnen = true` (FR-7). Keine `ZEV`/`VNB`-Zeilen, auch nicht bei vorhandenen Messwerten.
* Im Rechnungsformular stehen Produzenten **in derselben Liste** wie die Konsumenten, mit Typ-Anzeige.
* Produzenten haben keinen Mieter; die Rechnung wird auf die Einheit ausgestellt (kein Mieterblock auf dem PDF).
* Hat ein ausgewählter Produzent keinen für Produzenten freigegebenen `GRUNDGEBUEHR`-Tarif, wird für ihn **keine Rechnung** erzeugt (kein Fehler).
* Ein Produzent ohne Messwerte erhält trotzdem seine Rechnung mit den Grundgebühr-Zeilen.
* (Hintergrund: Anhang A.9)

### FR-9: Tarif kopieren
1. Der Benutzer wählt im Kebab-Menü eines Tarifs „Kopieren" (Reihenfolge: Bearbeiten – **Kopieren** – Löschen, mit passendem Icon).
2. Das System öffnet die Maske „Neuer Tarif", deren Felder die Werte des kopierten Tarifs enthalten.
3. Der Benutzer kann Anpassungen machen und „Erfassen" oder „Abbrechen" klicken.
* Beim Kopieren findet keine zusätzliche automatische Validierung statt; beim Erfassen gelten die üblichen Prüfungen (FR-2, FR-3, FR-5, FR-6).
* (Hintergrund: Anhang A.10)

### Datenmodell & Persistierung
* **Tabelle:** `zev.tarif` (ID aus einer Sequenz). Diese Spalten sind Pflicht:
  * **Bezeichnung:** maximale Länge 50 Zeichen (Anhang A.2), Beispiele: "vZEV PV Tarif" oder "Strombezug EWB"
  * **Tariftyp:**
    * "ZEV" für Strombezug aus dem ZEV (`messwerte.zev_calculated`)
    * "VNB" für Strombezug vom Verteilnetzbetreiber (`messwerte.total - messwerte.zev_calculated`)
    * "GRUNDGEBUEHR" für eine fixe Grundgebühr (z.B. Messgebühr, Netznutzungspauschale), FR-7
    * "LADESTROM" für Strom an Ladestationen (`Specs/Ladestromtarif.md`)
    * "ZUSATZ" für Tarifpositionen, die an der Position gewählt werden (`Specs/Tarifpositionen.md`)
  * **Tarif:** Genauigkeit 5 Nachkommastellen
  * **gueltig_von:** Datum
  * **gueltig_bis:** Datum
* **`tarif.produzent_verrechnen BOOLEAN NOT NULL DEFAULT false`** (FR-7); bestehende Grundgebühr-Tarife wurden mit `false` initialisiert.
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
* **Spalte „Produzenten"** in der Tarif-Übersicht (FR-7).
* **Kebab-Menü** je Tarif: Bearbeiten – Kopieren – Löschen (FR-9).
* **Design System:** `sortable.css` (`.zev-table__griff`, Stile der CDK-Drag-Vorschau — global, weil Angular die Vorschau an den `<body>` hängt).

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

### Mehrere gleichzeitig gültige Tarife (FR-2)
* [x] Zwei `VNB`-Tarife „Energielieferung" und „Netznutzung" mit gleicher Gültigkeit lassen sich speichern. (Test: TarifServiceTest#saveTarif_VnbNetznutzungNebenEnergielieferung_SavesSuccessfully; TarifRepositoryIT#existsOverlappingTarifMitBezeichnung_NurGleicheBezeichnungZaehlt) (E2E: tarif-verwaltung.spec.ts › should accept an overlapping tariff of the same type with a different name)
* [x] Drei `GRUNDGEBUEHR`-Tarife (Energielieferung, Netznutzung, Messtarif) mit gleicher Gültigkeit lassen sich speichern. (Test: TarifServiceTest#saveTarif_DreiGrundgebuehren_PruefungJeBezeichnung)
* [x] Ein zweiter Tarif desselben Typs mit **gleicher Bezeichnung** und überschneidender Gültigkeit wird abgewiesen („Tarif überschneidet sich mit bestehendem Tarif gleicher Bezeichnung"); auch bei anderer Gross-/Kleinschreibung oder Leerzeichen am Rand. (Test: TarifServiceTest#saveTarif_OverlappingTarifGleicheBezeichnung_ThrowsException; TarifRepositoryIT#existsOverlappingTarifMitBezeichnung_NurGleicheBezeichnungZaehlt) (E2E: tarif-verwaltung.spec.ts › should show error when creating an overlapping tariff with the same name)
* [x] Beim Bearbeiten eines Tarifs zählt er nicht als Überschneidung mit sich selbst. (Test: TarifServiceTest#saveTarif_UpdateExisting_ExcludesItself; TarifRepositoryIT#existsOverlappingTarifMitBezeichnung_NurGleicheBezeichnungZaehlt)
* [x] Ein zweiter `LADESTROM`-Tarif mit überschneidender Gültigkeit wird abgewiesen, auch mit anderer Bezeichnung. (Test: TarifServiceTest#saveTarif_LadestromUeberschneidung_ThrowsException)
* [x] Auf der Rechnung erscheinen zwei gleichzeitig gültige `VNB`-Tarife als zwei Zeilen mit derselben Menge; drei Grundgebühren als drei Zeilen; das Total ist ihre Summe. (Test: RechnungServiceTest#berechneRechnung_MehrereGleichzeitigGueltigeTarife_JeTarifEineZeile)
* [x] Die Überschneidungsprüfung sieht nur Tarife des eigenen Mandanten. (Test: TarifRepositoryIT#shouldNotLetTarifOfOtherOrgBlockOverlapCheck)

### Grundgebühr pro Monat oder pro Tag (FR-3)
* [x] Ein Grundgebühr-Tarif lässt sich mit Mengeneinheit „Tag" speichern; ohne Angabe gilt „Monat". (Test: TarifServiceTest#saveTarif_GrundgebuehrProTag_BleibtTag; TarifServiceTest#saveTarif_GrundgebuehrOhneMengeneinheit_GiltProMonat)
* [x] Eine andere Mengeneinheit als Monat oder Tag wird bei der Grundgebühr abgewiesen („Für die Grundgebühr ist nur die Mengeneinheit MONAT oder TAG zulässig"). (Test: TarifServiceTest#saveTarif_GrundgebuehrMitStueck_ThrowsException)
* [x] Grundgebühr pro Tag, ganzes Q3 2026: Menge 92, Einheit „Tag", Betrag 92 × Preis. (Test: RechnungServiceTest#berechneRechnung_GrundgebuehrProTag_ZaehltJedenTag)
* [x] Grundgebühr pro Tag, gültig ab 16.08.2026, Rechnung Q3: Menge 46. (Test: RechnungServiceTest#berechneRechnung_GrundgebuehrProTag_ZaehltJedenTag)
* [x] Grundgebühr pro Monat, gültig ab 16.08.2026, Rechnung Q3: Menge 1 (nur September). (Test: RechnungServiceTest#berechneRechnung_GrundgebuehrProTag_ZaehltJedenTag)
* [x] Bestehende Grundgebühren rechnen nach der Migration wie bisher (Mengeneinheit `MONAT`). (Test: RechnungServiceTest#berechneRechnung_GrundgebuehrZeile_HasMonatMengeneinheit; RechnungServiceTest#berechneRechnung_GrundgebuehrFullQuarter_ThreeMonths) (Nachweis: V176__Grundgebuehr_Pro_Tag.sql:15)
* [x] Die Tarifmaske bietet bei der Grundgebühr nur Monat und Tag an, bei ZUSATZ kWh, Monat, Stück; bei jedem Wechsel des Typs wird die Einheit neu gesetzt — Grundgebühr: Monat vorbelegt; ZUSATZ: leer, muss bewusst gewählt werden (die Vorbelegung „Monat" der Grundgebühr wandert nicht in einen ZUSATZ-Tarif). (Test: tarif-form.component.spec.ts › mengeneinheit (ZUSATZ, GRUNDGEBUEHR) › should offer month and day for the Grundgebuehr; tarif-form.component.spec.ts › mengeneinheit (ZUSATZ, GRUNDGEBUEHR) › should offer kWh, month and piece for ZUSATZ; tarif-form.component.spec.ts › mengeneinheit (ZUSATZ, GRUNDGEBUEHR) › should preselect month when switching to the Grundgebuehr; tarif-form.component.spec.ts › mengeneinheit (ZUSATZ, GRUNDGEBUEHR) › should not carry the month of the Grundgebuehr over to ZUSATZ; tarif-form.component.spec.ts › mengeneinheit (ZUSATZ, GRUNDGEBUEHR) › should drop day when switching from the Grundgebuehr to ZUSATZ)
* [x] Auf der Rechnung (PDF) steht „CHF / Tag". (Test: JasperTemplateCompileTest#testRechnungTemplate_OhneJahrespreisZeigtCHFproTagUndMonat)

### Reihenfolge und Panels (FR-4)
* [x] Die Tarifverwaltung zeigt drei Panels: Zukünftige Tarife, Tarife *aktuelles Jahr*, Frühere Tarife; beim Aufruf ist nur das aktuelle Jahr offen. (Test: tarif-list.component.spec.ts › initialization › should open only the panel of the current year; tarif-list.component.spec.ts › gruppiere › should assign tariffs by validity to future, current and past) (E2E: tarif-verwaltung.spec.ts › should open only the panel of the current year)
* [x] Ein Tarif von 01.10. des Vorjahres bis 31.12. dieses Jahres steht im Panel des aktuellen Jahres. (Test: tarif-list.component.spec.ts › gruppiere › should put a tariff spanning into the current year into the current panel)
* [x] Innerhalb eines Panels lässt sich ein Tarif am Griff verschieben; die neue Reihenfolge bleibt nach dem Neuladen erhalten. (Test: tarif-list.component.spec.ts › onDrop › should move a tariff up before its new successor and reload) (E2E: tarif-verwaltung.spec.ts › should reorder tariffs by drag and drop and keep the order after reload)
* [x] Zwischen Panels lässt sich nicht verschieben (je Panel eine eigene Drop-Liste). (Test: tarif-list.component.spec.ts › Darstellung der Panels › should give each panel its own drop list that is not connected to the others)
* [x] Die Spaltenköpfe sind nicht sortierbar. (E2E: tarif-verwaltung.spec.ts › should open only the panel of the current year)
* [x] Das Kebab-Menü der unteren Zeilen wird nicht abgeschnitten: Es liegt fest im Fenster (`.zev-kebab-menu--fixed`), sodass kein Panel und keine scrollende Tabelle es abschneidet, und klappt **nach oben** auf, wenn bis zum Fensterrand darunter zu wenig Platz ist (`.zev-kebab-menu--up`) — auf allen Listen (Hintergrund: Anhang A.14). (Test: kebab-menu.component.spec.ts › feste Lage im Fenster › should be positioned fixed; kebab-menu.component.spec.ts › nach oben aufklappen › should open upwards near the bottom of the window; kebab-menu.component.spec.ts › nach oben aufklappen › should ignore a clipping container and open downwards when the window has room; kebab-menu.component.spec.ts › feste Lage im Fenster › should stay open on scroll and follow the button) (E2E: messpunkt-empfang.spec.ts › Messpunkt-Empfang - Einheit anlegen › should open the unit form with the meter point prefilled from a row without unit) (Nachweis: tarif-list.component.html:44)
* [x] Auf der Rechnung stehen die Zeilen in der Reihenfolge der Tarife, auch über Typen hinweg (z.B. Grundgebühr vor Strombezug). (Test: RechnungServiceTest#berechneRechnung_ZeilenInDerReihenfolgeDerTarife)
* [x] Ein neuer VNB-Tarif steht direkt hinter dem letzten VNB-Tarif; ein Bearbeiten ändert den Platz nicht. (Test: TarifServiceTest#saveTarif_Neu_KommtHinterDenLetztenTarifDesselbenTyps; TarifServiceTest#saveTarif_Update_BehaeltGespeicherteReihenfolge)
* [x] `PUT /api/tarife/{id}/verschieben` weist fehlende oder doppelte Ziele, unbekannte (auch mandantenfremde) Tarife und ein Verschieben relativ zu sich selbst mit 400 ab. (Test: TarifServiceTest#verschiebe_OhneOderMitZweiZielen_ThrowsException; TarifServiceTest#verschiebe_UnbekannterOderFremderTarif_ThrowsException; TarifServiceTest#verschiebe_RelativZuSichSelbst_ThrowsException; TarifControllerTest#verschiebe_UnbekannterTarif_ReturnsBadRequest)
* [x] Eine Verschiebung ändert nur den verschobenen Tarif und die Plätze zwischen Start und Ziel; andere Tarife, auch anderer Mandanten, bleiben unberührt. (Test: TarifServiceTest#verschiebe_NachVornVorZiel_RuecktDieDazwischenNach; TarifServiceTest#verschiebe_NachHintenHinterZiel_RuecktDieDazwischenVor; TarifRepositoryIT#verschiebeReihenfolgeBereich_NurBereichUndEigenerMandant)
* [x] Auch ein Tarif mit Altbestand-Werten ausserhalb der heutigen Regeln (auf Hene: „Grundgebühr Ladestation" mit Preis 0) lässt sich verschieben — die Reihenfolge wird als einzelne Spalte gesetzt, nicht über die Entity mit ihrer Bean Validation. (Test: TarifRepositoryIT#setzeReihenfolge_TarifMitPreisNull_OhneBeanValidation; TarifServiceTest#verschiebe_NachVornVorZiel_RuecktDieDazwischenNach)

### Preis 0 (FR-5)
* [x] Ein Tarif mit Preis 0 lässt sich anlegen und bearbeiten; ein negativer Preis wird abgewiesen (Backend `@PositiveOrZero`, Formular `min="0"`). (Test: TarifControllerTest#createTarif_PreisNull_ReturnsCreated; TarifControllerTest#createTarif_NegativePreis_ReturnsBadRequest; tarif-form.component.spec.ts › isFormValid › should return true when preis is zero (free service); tarif-form.component.spec.ts › isFormValid › should return false when preis is negative) (E2E: tarif-verwaltung.spec.ts › should show error for negative price)
* [x] Ein neues Formular zeigt ein leeres Preisfeld; ohne Preis bleibt Speichern gesperrt. (Test: tarif-form.component.spec.ts › initialization › should initialize with default form data when no tarif input; tarif-form.component.spec.ts › isFormValid › should return false when preis is empty)
* [x] Die Meldung lautet „Preis ist erforderlich und darf nicht negativ sein" (V179). (Nachweis: V179__Tarif_Preis_Null_Zulaessig.sql:4)

### Jahrespreis (FR-6)
* [x] Grundgebühr 100.– pro Jahr nach Monaten: die vier Quartale ergeben zusammen genau 100.–. (Test: RechnungServiceTest#berechneRechnung_JahrespreisNachMonaten_QuartaleErgebenGenauDenJahrespreis)
* [x] Nach Tagen 2026 (365) und 2028 (366): die Quartale ergeben zusammen den Jahrespreis. (Test: RechnungServiceTest#berechneRechnung_JahrespreisNachTagen_QuartaleErgebenDenJahrespreis; RechnungServiceTest#berechneRechnung_JahrespreisNachTagen_SchaltjahrMit366Tagen)
* [x] Nach Tagen, gültig ab 01.08.2026, Rechnung Q3: Menge „61 Tage", Betrag 61/365 des Jahrespreises. (Test: RechnungServiceTest#berechneRechnung_JahrespreisNachTagen_AnteiligAbMitteQuartal)
* [x] Über den Jahreswechsel wird je Kalenderjahr mit dessen Tagen gerechnet. (Test: RechnungServiceTest#berechneRechnung_JahrespreisNachTagen_UeberJahreswechselJeJahrGeteilt)
* [x] Zusatz-Tarif pro Monat mit Jahrespreis: Betrag = Jahrespreis × erfasste Monate / 12. (Test: RechnungServiceTest#berechneRechnung_ZusatzMitJahrespreis_MonateDurchZwoelf)
* [x] Auf der Rechnung: Menge „3 Monate", Preis 120.00000, Einheit „CHF / Jahr". (Test: JasperTemplateCompileTest#testRechnungTemplate_JahrespreisZeigtMonateUndCHFproJahr; RechnungServiceTest#berechneRechnung_JahrespreisNachMonaten_ZeigtMonateUndCHFproJahr)
* [x] Ohne Jahrespreis gilt Menge × Preis mit „CHF / Monat". (Test: RechnungServiceTest#berechneRechnung_OhneJahrespreis_KeineJahresAnzeige; Test: JasperTemplateCompileTest#testRechnungTemplate_OhneJahrespreisZeigtCHFproTagUndMonat)
* [x] Ein Jahrespreis bei ZEV, VNB, Ladestrom oder Zusatz pro Stück/kWh wird abgewiesen. (Test: TarifServiceTest#saveTarif_JahrespreisBeiVnb_ThrowsException; TarifServiceTest#saveTarif_JahrespreisBeiZusatzProStueck_ThrowsException)

### Grundgebühr (FR-7)
* [x] In der Tarifverwaltung kann ein Tarif mit Typ „Grundgebühr" erfasst, bearbeitet und gelöscht werden. (E2E: tarif-verwaltung.spec.ts › should create a GRUNDGEBUEHR tariff successfully; tarif-verwaltung.spec.ts › should edit a GRUNDGEBUEHR tariff; tarif-verwaltung.spec.ts › should delete a GRUNDGEBUEHR tariff)
* [x] In der Dropdown-Liste der Tariftypen erscheint der Eintrag „Grundgebühr". (Test: tarif-form.component.spec.ts › initialization › should have five tarif type options; tarif-form.component.spec.ts › i18n der Auswahllisten › should translate the tariff type options) (E2E: tarif-verwaltung.spec.ts › should show GRUNDGEBUEHR option in tarif type dropdown)
* [x] Beim Generieren von Rechnungen wird die Grundgebühr für Konsumenten berechnet; für Produzenten nur bei freigegebenem Tarif (FR-8). (Test: RechnungServiceTest#berechneRechnungen_ConsumerGrundgebuehr_IgnoresProduzentFlag; RechnungServiceTest#berechneRechnungen_ProducerWithGrundgebuehr_GetsInvoice; RechnungServiceTest#berechneRechnungen_ProducerWithGrundgebuehrNotFlagged_NotIncluded)
* [x] Die Anzahl Monate wird korrekt berechnet (z. B. 01.01.–31.03. = 3 Monate). (Test: RechnungServiceTest#berechneRechnung_GrundgebuehrFullQuarter_ThreeMonths)
* [x] Läuft ein `GRUNDGEBUEHR`-Tarif mitten in einer Rechnungsperiode ab, werden zwei separate Zeilen erzeugt (analog ZEV/VNB). (Test: RechnungServiceTest#berechneRechnung_GrundgebuehrWechseltImQuartal_ZweiZeilenMitTeilzeitraum)
* [x] Beim Tariftyp „Grundgebühr" wird im Tarifformular eine Checkbox „Auch Produzenten verrechnen" angezeigt; bei `ZEV`/`VNB` nicht. (Test: tarif-form.component.spec.ts › produzentVerrechnen checkbox › should render checkbox only for GRUNDGEBUEHR tariffs; tarif-form.component.spec.ts › produzentVerrechnen checkbox › should not render checkbox for ZEV/VNB tariffs)
* [x] Das Flag beeinflusst die Verrechnung bei Konsumenten **nicht** (Konsumenten zahlen die Grundgebühr immer). (Test: RechnungServiceTest#berechneRechnungen_ConsumerGrundgebuehr_IgnoresProduzentFlag)
* [x] In der Tarif-Übersichtstabelle zeigt die Spalte „Produzenten" für `GRUNDGEBUEHR`-Tarife eine deaktivierte Checkbox (angehakt = freigegeben) und für `ZEV`/`VNB` einen Platzhalter „–". (Test: tarif-list.component.spec.ts › Darstellung der Panels › should show the producer flag as a disabled checkbox for the Grundgebuehr; Test: tarif-list.component.spec.ts › Darstellung der Panels › should show a dash instead of the producer flag for other tariff types)
* [x] Der `endBetrag` auf der Rechnung enthält die Grundgebühr. (Test: RechnungServiceTest#berechneRechnung_GrundgebuehrIncludedInTotal)
* [x] Fehlt ein `GRUNDGEBUEHR`-Tarif für den Rechnungszeitraum, wird keine Grundgebühr berechnet (kein Fehler, da der Typ optional ist). (Test: RechnungServiceTest#berechneRechnung_GrundgebuehrOptional_NoErrorWhenNoTarif)

### Rechnungen für Produzenten (FR-8)
* [x] Produzenten erscheinen im Rechnungsformular und können ausgewählt werden. (Test: rechnungen.component.spec.ts › Produzent in der Einheiten-Auswahl › should list the producer and select it individually)
* [x] Für Produzenten wird eine Rechnung mit nur `GRUNDGEBUEHR`-Positionen generiert (keine ZEV/VNB-Zeilen). (Test: RechnungServiceTest#berechneRechnungen_ProducerHasNoZevVnbLines; RechnungServiceTest#berechneRechnungen_ProducerWithGrundgebuehr_GetsInvoice)
* [x] Ein `GRUNDGEBUEHR`-Tarif mit `produzentVerrechnen = true` wird Produzenten verrechnet; ein Tarif mit `false` nicht. (Test: RechnungServiceTest#berechneRechnungen_ProducerWithGrundgebuehr_GetsInvoice; RechnungServiceTest#berechneRechnungen_ProducerWithGrundgebuehrNotFlagged_NotIncluded)
* [x] Hat ein ausgewählter Produzent nur nicht-freigegebene `GRUNDGEBUEHR`-Tarife, wird für ihn keine Rechnung erzeugt. (Test: RechnungServiceTest#berechneRechnungen_ProducerWithGrundgebuehrNotFlagged_NotIncluded)

### Tarif kopieren (FR-9)
* [x] Kebabmenü hat weiteren Eintrag "Kopieren": Bearbeiten - **Kopieren** - Löschen (Test: tarif-list.component.spec.ts › initialization › should have menu items for edit, copy and delete; tarif-list.component.spec.ts › onMenuAction › should call onCopy for copy action)
* [x] Die Felder sind mit den Daten des kopierten Tarifes abgefüllt (Test: tarif-list.component.spec.ts › onCopy › should copy all properties except id; tarif-list.component.spec.ts › onCopy › should set selectedTarif without id and show form; tarif-form.component.spec.ts › initialization › should populate form with tarif data when input is provided)
* [x] Der Benutzer kann "Erfassen" oder "Abbrechen" klicken (Test: tarif-form.component.spec.ts › events › should emit save event with form data on valid submit; tarif-form.component.spec.ts › events › should emit cancel event; tarif-list.component.spec.ts › onFormSubmit - create › should call createTarif when tarif has no id)

### Mandantentrennung beim Speichern (NFR-2)
* [ ] `id` und `orgId` im Body von `POST /api/tarife` werden nicht übernommen. (Test: TarifControllerTest#createTarif_IdUndOrgIdImBody_WerdenIgnoriert)
* [ ] Speichern mit der ID eines Tarifs, der nicht zum eigenen Mandanten gehört, wird abgewiesen; der fremde Tarif bleibt unverändert. (Test: TarifServiceTest#saveTarif_IdNichtImEigenenMandanten_ThrowsException; Test: FremdeIdBeimSpeichernIT#saveTarif_MitFremderId_WirdAbgewiesenUndFremderTarifBleibtUnveraendert)
* [ ] Beim Ändern bleibt der Tarif im eigenen Mandanten, auch wenn der Body eine andere `orgId` trägt. (Test: TarifServiceTest#saveTarif_Update_UebernimmtOrgIdVomBestehendenTarif; Test: FremdeIdBeimSpeichernIT#saveTarif_EigenerTarifMitFremderOrgId_BleibtImEigenenMandanten)

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Optimierte Berechnung der Rechnungsbeträge: Gehe für die Berechnung der Kosten nicht Zeile für Zeile der messwerte.zeit vor, sondern bilde Teil-Zeitbereiche gemäss Gültigkeit der Tarife.
* Die Grundgebühr verlängert die Rechnungsgenerierung nicht merklich: keine zusätzlichen DB-Queries pro Zeile, die Tarife werden einmalig geladen (FR-7).

### NFR-2: Sicherheit
* Die Tarifverwaltung verlangt die Permission `tarife:manage` (Route `/tarife` und alle Endpunkte, auch das Verschieben, FR-4); sie haben die Fachrollen `org_admin` und `zev_admin` (`Specs/Berechtigungen.md`, Hintergrund: Anhang A.8).
* Die Überschneidungsprüfung (FR-2) und das Verschieben (FR-4) sehen nur Tarife des eigenen Mandanten.
* `id` und `orgId` aus dem Request-Body übernimmt der Server nicht (`@JsonProperty(access = READ_ONLY)`); `PUT` nimmt die ID aus dem Pfad. Beim Ändern lädt der Service den bestehenden Tarif unter dem Mandantenfilter – fehlt er, wird abgewiesen („Tarif nicht gefunden“) – und übernimmt dessen `org_id` (Hintergrund: Anhang A.13).
* Rechnungen — auch für Produzenten (FR-8) — verlangen `rechnungen:manage` (`Specs/RechnungenGenerieren.md`, `Specs/Berechtigungen.md`).
* **Multi-Tenancy:** Alle Tarife tragen `org_id`; der Hibernate-Filter wird angewendet.

### NFR-3: Kompatibilität
* Die Migrationen V176 und V178 ändern bestehende Rechnungen nicht: Grundgebühren rechnen weiter pro Monat, die Reihenfolge der Rechnung bleibt.
* Rechnungen für Konsumenten ohne `GRUNDGEBUEHR`-Tarif werden korrekt berechnet (FR-7).
* Das `TarifZeileDTO` trägt die Einheit der Menge (kWh, Monate, Tage …), ohne bestehende Felder zu ändern.

## 5. Edge Cases & Fehlerbehandlung
* Es muss für den gesamten Zeitbereich der Rechnung ein gültiger Tarif **je Typ** (ZEV, VNB) vorhanden sein; mehrere gleichzeitig gültige Tarife eines Typs decken den Zeitraum gemeinsam ab (FR-2). Falls dies nicht der Fall ist, wird eine Fehlermeldung angezeigt.
* Fehler beim Verschieben → die Liste zeigt wieder den gespeicherten Stand (FR-4).
* Tarif mit Altbestand-Werten ausserhalb der heutigen Regeln → lässt sich trotzdem verschieben (FR-4).
* Kein `GRUNDGEBUEHR`-Tarif vorhanden → keine Grundgebühr, keine Fehlermeldung (FR-7).
* Grundgebühr pro Monat bei Teilmonat (Mieter zieht Mitte Monat ein/aus) → nur vollständige Kalendermonate zählen; ein Monat ist vollständig, wenn sein erster und letzter Tag im effektiven Zeitraum liegen (FR-3).
* Rechnungszeitraum unter einem Monat bei Grundgebühr pro Monat → 0 Monate, die Zeile wird nicht ausgegeben.
* Produzent ohne Messwerte → Rechnung mit den Grundgebühr-Zeilen (FR-8); Produzent mit Mieter ist nicht vorgesehen, kein Mieterblock.
* Netzwerkfehler beim PDF-Download → bestehende Fehlerbehandlung, keine Spezialbehandlung.

## 6. Abhängigkeiten & betroffene Funktionalität
* **Verwandte Specs:** `Specs/RechnungenGenerieren.md` (Rechnung, Länge der Bezeichnung), `Specs/TarifValidierung.md` (Lückenprüfung), `Specs/Ladestromtarif.md` (`LADESTROM`), `Specs/Tarifpositionen.md` (`ZUSATZ`).
* **Betroffener Code:** `TarifTyp` (`GRUNDGEBUEHR`), `RechnungService` (Grundgebühr, Produzenten-Rechnungen), `RechnungDTO`/`TarifZeileDTO` (Einheit der Menge), `rechnung.jrxml`, Rechnungsformular (Produzenten-Auswahl), Rechnungserzeugung und PDF (`RechnungZeilenText`), Tarifmaske und Tarifliste, Design System (`sortable.css`, `.zev-collapsible--overflow-visible`, `.zev-kebab-menu--up`), Komponente `app-kebab-menu` (wirkt auf allen Listen).
* **Datenmigration:** V176 (Mengeneinheit), V178 (Reihenfolge), V179 (Preis 0), V180 (Jahrespreis) — siehe Datenmodell.

## 7. Abgrenzung / Out of Scope
* Keine Lückenprüfung je Bezeichnung und keine Gruppierung oder Zwischensumme der Bestandteile (FR-2).
* `TAG` nur für die ZEV-Stromrechnung, nicht für die Nebenkostenabrechnung (FR-3).
* Kein Verschieben zwischen Panels (FR-4).
* Grundgebühr: keine Berechnung nach Einheitsgrösse oder Leistung (immer Festpreis), keine Anzeige auf der Statistikseite, keine Historisierung von Rechnungen in der Datenbank (`RechnungenGenerieren.md`); Produzenten erhalten eigene Rechnungen statt einer Zeile auf einer Konsumenten-Rechnung (FR-7, FR-8).
* Kopieren: keine automatische Validierung beim Kopieren selbst (FR-9).

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

### A.9 Grundgebühr und Rechnungen für Produzenten (FR-7, FR-8; zusammengeführt am 18.10.2026)
Die Grundgebühr war eine eigene Spec, `Specs/Tarifverwaltung-Grundgebuehr.md` (Inhalt im Git-Verlauf). Ausgangslage damals: Es gab nur `ZEV` und `VNB`, beide pro kWh; Produzenten erhielten keine Rechnungen. Bei der Zusammenführung abgelöst bzw. angepasst:
* **„Festpreis pro Stromzähler und Monat (CHF/Monat)", „Anzahl der vollständigen Kalendermonate", „Menge (Anzahl Monate, Einheit Monate) | Preis (CHF/Monat)"** — seit FR-3 (10.10.2026) auch pro Tag, seit FR-6 (11.10.2026) als Jahrespreis.
* **„GRUNDGEBUEHR-Zeilen werden nach den ZEV- und VNB-Zeilen aufgeführt"** — seit FR-4 (11.10.2026) bestimmt die Reihenfolge der Tarife.
* **„Tarifverwaltung (inkl. GRUNDGEBUEHR) ist nur mit Rolle zev_admin zugänglich; Rechnungsgenerierung für Produzenten ebenfalls nur mit zev_admin"** — seit der Umstellung auf Permissions: `tarife:manage` bzw. `rechnungen:manage`.
* **„Die Tarifvalidierung (validateTarifAbdeckung) wird um GRUNDGEBUEHR erweitert (separater Validierungsaufruf)"** — durch die Antwort auf die offene Frage (Typ optional) überholt; die Lückenprüfung prüft nur ZEV und VNB.
* **Abgrenzung „Automatische Aufteilung der Grundgebühr bei Mieterwechsel innerhalb eines Monats (wird nur auf volle Monate gerechnet)"** — seit FR-3 (10.10.2026) mit der Mengeneinheit `TAG` taggenau möglich; für `MONAT` gilt weiter: nur volle Monate.
* **Spalte „Produzenten": „Für ZEV/VNB-Tarife wird ein Platzhalter angezeigt"** — gilt für alle Typen ausser `GRUNDGEBUEHR`, also auch `LADESTROM` und `ZUSATZ`.
* **Persistierung:** Kein neues Datenbankschema; Migration für den Enum-Wert (sofern als DB-Enum gespeichert) und für die Spalte `produzent_verrechnen`.
* **Datenmigration:** keine; der Typ wurde nur für neue Tarife genutzt.
* **Betroffener Code damals:** `TarifTyp`, `TarifService` (Laden der Grundgebühr), `RechnungService` (`berechneRechnung`/`berechneTarifZeilen`, `berechneRechnungen` für PRODUCER), `RechnungDTO`/`TarifZeileDTO`, `rechnung.jrxml`, `TarifFormComponent`, `RechnungenComponent`.

Offene Fragen und Antworten:
* Pflicht oder optional? → **optional** (kein Fehler ohne Tarif).
* Ganze oder partielle Monate? → **volle Monate** (später ergänzt um „pro Tag", FR-3).
* Produzenten im Rechnungsformular in derselben Liste oder separat? → **gleiche Liste mit Typ-Label**.
* Grundgebühr in der Quartals-/Jahresvalidierung mitprüfen? → **nein, optional**.
* Eigener Abschnitt im PDF? → **kein eigener Abschnitt**.

Zuordnung der alten Nummern: Grundgebühr FR-1 (Tariftyp), FR-2 (Berechnung), FR-4 (PDF), FR-5 (UI), FR-6 (Persistierung), FR-7 (Flag), FR-8 (Spalte) → **FR-7**; Grundgebühr FR-3 (Produzenten-Rechnungen) → **FR-8**. NFR-1 → NFR-1, NFR-2 und NFR-4 → NFR-2, NFR-3 → NFR-3.

### A.10 Tarif kopieren (FR-9; zusammengeführt am 18.10.2026)
War eine eigene Spec, `Specs/Tarifverwaltung-Tarif-kopieren.md` („Erweiterung Tarifverwaltung", Inhalt im Git-Verlauf). Ziel: einen neuen Tarif durch Kopieren eines bestehenden erfassen, aus Gründen der Benutzerfreundlichkeit; vorher mussten alle Felder manuell erfasst werden. Persistierung, Performance, Kompatibilität und Edge Cases waren leer, Sicherheit „wie bisher". Zuordnung: Kopieren FR-1 (Ablauf) und FR-3 (Layout) → **FR-9**.

### A.11 Glättung (18.10.2026)
Bei der zweiten Konsolidierung nach der Zusammenführung wurden nur Formulierungen geglättet, Inhalt und Status der Kriterien blieben gleich. Vorher lauteten sie: „… wird **weiterhin** abgewiesen, auch mit anderer Bezeichnung" (FR-2); „Menge 1 (nur September) — **unverändert**" und „bei ZUSATZ **weiterhin** kWh, Monat, Stück" (FR-3); „Die Spaltenköpfe sind **nicht mehr** sortierbar" (FR-4); „Ohne Jahrespreis **bleibt die Rechnung wie bisher** (Menge × Preis, „CHF / Monat")" (FR-6); „Alle **neuen** UI-Texte kommen aus dem `TranslationService`" (FR-7). In NFR-2 wurden zwei Punkte zur Permission `tarife:manage` zusammengefasst; FR-3 verweist neu auf FR-7, und der erste Edge Case steht im Präsens („wird“ statt „soll … angezeigt werden“). Im Datenmodell hiess es „Alle Spalten sind Pflicht", gemeint waren die aufgezählten Grundspalten (`mengeneinheit` ist nullable).

### A.12 Zwei Kriterien gestrichen (FR-4, FR-7, 18.10.2026)
Beim Akzeptanzkriterien-Check vom 18.10.2026 als „kein Akzeptanzkriterium" erkannt und gestrichen:
* FR-4: „Nach der Migration ist die Reihenfolge der Rechnung unverändert." — eine einmalige, längst ausgeführte Migration (V178); ein Test schützte vor nichts mehr. Die Aussage steht in NFR-3.
* FR-7: „Alle neuen UI-Texte kommen aus dem `TranslationService` (keine hardcodierten Strings)." — eine allgemeine Konvention aus `Specs/generell.md`, die `/9_vorlagen-check` prüft; jetzt als Satz in FR-7.

### A.13 Mitgeschickte `id` und `orgId` (NFR-2, 18.10.2026)
Gefunden beim Security-Check zur Einheitenverwaltung (OWASP A01, `Specs/Einheitenverwaltung.md`, Anhang A.4). `saveTarif` dient für Anlegen und Ändern und setzte `org_id` nur bei `id == null`; sonst kam sie aus dem Request-Body, und der gefilterte Abgleich mit dem bestehenden Tarif war ein blosses `ifPresent`. Folgen: Ein `POST` mit fremder `id` und `orgId` überschrieb einen fremden Tarif (`save` → `merge` lädt ungefiltert), ein `PUT` mit fremder `orgId` schob einen eigenen Tarif in einen anderen Mandanten. `FremdeIdBeimSpeichernIT` war gegen den alten Code rot. Behoben durch die Regel in NFR-2.

### A.14 Kebab-Menü fest im Fenster (25.10.2026)
Das Kriterium lautete: „Die Panels schneiden nicht ab (`.zev-collapsible--overflow-visible`), und
das Menü klappt allgemein **nach oben** auf, wenn darunter bis zum nächsten abschneidenden Element
(Panel, scrollende Tabelle nach Spaltenbreiten-Änderung) oder zum Fensterrand zu wenig Platz ist.“
In einer kurzen Tabelle in `.zev-table-scroll` passte das Menü aber weder nach unten noch nach
oben und wurde abgeschnitten (`Specs/Messpunkt-Empfang.md`, Anhang A.2). Seither liegt es fest im
Fenster, wandert beim Scrollen mit dem Knopf mit; abschneidende Vorfahren zählen nicht mehr.

