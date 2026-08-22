# Nebenkosten

> **Basis-Spec.** Dieses Dokument beschreibt ausschliesslich das **Grundgerüst** der
> Nebenkostenabrechnung (NK): Feature-Flag, Menüpunkt mit Untermenü, zwei noch leere Unterseiten
> und die neue Permission. Die eigentliche Fachlichkeit folgt als eigene Specs mit dem Präfix
> `NK-<Feature>` — siehe Abschnitt 7.

## 1. Ziel & Kontext - Warum wird das Feature benötigt?

* **Was soll erreicht werden:** Die Nebenkosten einer Liegenschaft (Heizung, Wasser, Kehricht,
  Verwaltung, Allgemeinstrom, …) sollen künftig den Mietern in Rechnung gestellt werden können —
  getrennt von der bestehenden Stromabrechnung. Diese Basis schafft dafür den Platz in der
  Anwendung: einen Schalter, einen Menüpunkt mit Untermenü und zwei Unterseiten, die in
  Folge-Specs gefüllt werden.
* **Warum machen wir das:** Die Anwendung rechnet heute ausschliesslich Strom ab; Nebenkosten
  werden ausserhalb geführt und manuell verteilt. Weil die Fachlichkeit umfangreich wird und
  schrittweise entsteht, muss sie von Anfang an hinter einem Schalter liegen: Mandanten ohne
  Nebenkostenabrechnung dürfen von dem halbfertigen Bereich nichts sehen.
* **Warum nur ein Gerüst:** Die Rechenarten der Nebenkosten stehen noch nicht fest. Absehbar sind
  mindestens drei — **Quote** (z.B. 1/9 Allgemeinstrom), **Menge** (z.B. Wasserverbrauch) und
  **Prozent** (z.B. Verwaltungskosten). Sie rechnen grundverschieden: Eine Quote teilt einen
  Gesamtbetrag auf, eine Menge braucht einen Einheitspreis, ein Prozentsatz eine Bezugsgrösse.
  Eine Tabelle jetzt festzuschreiben hiesse, dieses Modell vorwegzunehmen und die Migration
  später wieder aufzubrechen (Entscheid vom 22.08.2026).
* **Aktueller Stand:**
  - **Feature-Flags existieren** (`Specs/FeatureFlag.md`): Enum `FeatureFlag` im Backend,
    `FeatureFlagService` und Direktive `*appFeature` im Frontend, `FeatureFlagGuard` für Routen.
    Vorbild ist `MESSWERTE_UPLOAD` (`app.routes.ts:25`, `navigation.component.html:68`).
  - **Die Navigation ist flach.** `navigation.component.html` ist eine einzige `<ul>` mit
    `<li>`-Einträgen; Untermenüs gibt es weder im Template noch im Design System
    (`design-system/src/components/navigation/`). Sie sind neu zu bauen — der grösste Posten
    dieser Basis.
  - **Es gibt bereits Tarifpositionen** (`/tarifpositionen`, `Specs/Tarifpositionen.md`): manuell
    erfasste Mengen vom Tariftyp `LADESTROM` oder `ZUSATZ`, verankert an einer Einheit, die als
    Zeilen auf der Quartalsrechnung erscheinen. Diese Erfassung bleibt **unverändert**; die
    NK-Erfassung wird später **daneben** gestellt, nicht darin untergebracht.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Ablauf / Flow

1. Ein Administrator (`zev_admin`) aktiviert auf der Seite **Einstellungen** den Feature-Flag
   **`NEBENKOSTENABRECHNUNG`** für seinen Mandanten. Globaler Default: **`false`**.
2. Nach dem Neuladen der Flags erscheint in der Navigation der Menüpunkt **Nebenkosten**.
3. Ein Klick darauf klappt die Untereinträge **Tarifpositionen** und **Abrechnung** auf.
4. Beide Unterseiten sind erreichbar und zeigen je einen Hinweis, dass die Funktion noch nicht
   verfügbar ist.
5. Schaltet der Administrator den Flag wieder aus, verschwindet der Menüpunkt und die Routen
   sind nicht mehr aufrufbar.

### FR-2: Feature-Flag

* Neuer Wert **`NEBENKOSTENABRECHNUNG`** im Enum `FeatureFlag`, Default **`false`**,
  Beschreibungs-Übersetzungsschlüssel `FEATURE_FLAG_NEBENKOSTENABRECHNUNG`.
* Der Flag wirkt in dieser Basis an **zwei** Stellen:
  1. **Navigation** — der Menüpunkt samt Untereinträgen hängt an `*appFeature`.
  2. **Routen** — alle NK-Routen tragen `FeatureFlagGuard` und
     `data: { featureFlag: 'NEBENKOSTENABRECHNUNG' }`; auch die direkte URL-Eingabe wird abgewiesen.
* **Regel für die Folge-Specs:** Sobald NK-Endpunkte entstehen, müssen sie den Flag **serverseitig**
  prüfen und bei ausgeschaltetem Flag `403` liefern. Ohne das wäre der Flag reine Kosmetik — die
  API bliebe über einen HTTP-Client erreichbar. In dieser Basis gibt es noch keine NK-Endpunkte,
  deshalb ist hier nichts zu schützen; die Regel ist trotzdem hier festgehalten, damit sie beim
  ersten Endpunkt nicht vergessen wird.

### FR-3: Navigation mit Untermenü

* Der übergeordnete Eintrag **Nebenkosten** ist **kein Navigationsziel**, sondern ausschliesslich
  Aufklapper (Entscheid). Eine Übersichtsseite hätte in dieser Ausbaustufe nichts zu zeigen, und
  „Abrechnung" ist ohnehin der natürliche Landeplatz. Eine Übersicht lässt sich später nachrüsten,
  ohne die Menüstruktur erneut zu ändern.
* Neue Fähigkeit im Design System (`design-system/src/components/navigation/`): ein Menüeintrag,
  der Untereinträge trägt und sie auf Klick auf-/zuklappt.
* **Design System verwenden.** `design-system/src/components/collapsible/` liefert die Optik des
  Auf-/Zuklappens (`collapsible.css` mit `.zev-collapsible` und `.zev-collapsible--open`) — es ist
  **reines CSS**, keine fertige Mechanik: Der Auf-/Zu-Zustand bleibt Sache der Komponente. Die
  Klassen sind wiederzuverwenden, statt neue einzuführen; alles, was darüber hinaus an Styling
  nötig ist, gehört als Untermenü-Variante in `design-system/src/components/navigation/` und nicht
  in das Komponenten-CSS der Navigation (`Specs/generell.md`, Abschnitt Design System).
* **Sichtbarkeit: Flag UND Permission.** Der Menüpunkt erscheint nur, wenn der Flag gesetzt ist
  **und** der Benutzer `nebenkosten:manage` besitzt (Entscheid). Sonst sähe ein Benutzer ohne
  Berechtigung einen Eintrag, der ihn beim Klick nur auf die Startseite zurückwirft.
* Der aktive Untereintrag wird wie bisher über `routerLinkActive` hervorgehoben. Befindet man sich
  auf einer NK-Seite, ist das Untermenü **aufgeklappt**.
* Das Untermenü funktioniert auch im Hamburger-Menü der schmalen Ansicht
  (`Specs/Hamburgermenü.md`).
* Der Design-System-Showcase wird um die neue Variante ergänzt.

### FR-3a: Permission-Prüfung im Template (neu im Frontend)

Die Sichtbarkeitsregel aus FR-3 lässt sich heute **nicht** umsetzen — sie braucht einen neuen
Baustein:

* **Ist-Stand:** Permissions werden im Frontend ausschliesslich im `AuthGuard`
  (`guards/auth.guard.ts`) geprüft, und zwar aus `AuthGuardData.grantedRoles` von
  `keycloak-angular` (Realm- und Resource-Rollen). Es gibt **keine** Direktive, keinen Service und
  keine Komponenten-Methode, mit der ein Template eine Permission abfragen könnte
  (`frontend-service/src/app/directives/` enthält nur `column-resize` und `feature-flag`).
* **Folge:** Die Navigation blendet heute **keinen** Menüpunkt nach Berechtigung aus — jeder
  angemeldete Benutzer sieht alle Einträge, und erst der `AuthGuard` weist ab.
* **Zu bauen:** Eine Struktur-Direktive `*appPermission="'nebenkosten:manage'"`, gebaut nach dem
  Vorbild von `directives/feature-flag.directive.ts`, gespeist aus derselben Rollenquelle wie der
  `AuthGuard`. Die Prüflogik (Realm-Rollen, dann Resource-Rollen; eine der geforderten Permissions
  genügt) ist aus `auth.guard.ts` in eine gemeinsam genutzte Funktion zu ziehen, statt sie zu
  duplizieren.
* **Abgrenzung:** Die übrigen Menüpunkte werden **nicht** nachgerüstet. Ihr heutiges Verhalten
  bleibt unverändert; die Direktive wird nur für den NK-Eintrag verwendet. Ein flächendeckendes
  Ausblenden nach Berechtigung ist eine eigene Entscheidung (siehe Abschnitt 8).

### FR-4: Routen und Gerüstseiten

| Route | Komponente | Inhalt in dieser Ausbaustufe |
|---|---|---|
| `/nebenkosten/tarifpositionen` | `NebenkostenTarifpositionenComponent` | Titel + Hinweis „noch nicht verfügbar" |
| `/nebenkosten/abrechnung` | `NebenkostenAbrechnungComponent` | Titel + Hinweis „noch nicht verfügbar" |

* Beide Routen: `canActivate: [AuthGuard, FeatureFlagGuard]`,
  `data: { permissions: ['nebenkosten:manage'], featureFlag: 'NEBENKOSTENABRECHNUNG' }`.
* **`/nebenkosten` leitet auf `/nebenkosten/abrechnung` weiter:**
  `{ path: 'nebenkosten', redirectTo: '/nebenkosten/abrechnung', pathMatch: 'full' }`.
  Der Elterneintrag ist zwar kein Menüziel (FR-3), die URL wird aber getippt und verlinkt.
  Ohne diese Zeile liefe sie ins Leere: `app.routes.ts` hat **keine** Wildcard-Route (`**`),
  ein unbekannter Pfad erzeugt einen Router-Fehler und die Navigation bleibt hängen.
* **Abweisungsziel einheitlich `/startseite`.** Der `FeatureFlagGuard` leitet bereits dorthin
  (`feature-flag.guard.ts:24`); der `AuthGuard` verwendet `parseUrl('/')` und landet über die
  Leerpfad-Weiterleitung ebenfalls auf `/startseite` (`auth.guard.ts:47`, `app.routes.ts:23`).
  Es ist also kein Code zu ändern — das Ziel ist hier nur festgeschrieben, damit die
  Akzeptanzkriterien eindeutig prüfbar sind.

### FR-5: Persistierung

* **Keine.** Diese Basis legt weder Tabellen noch Spalten an und ändert kein Enum ausser
  `FeatureFlag`. Die einzige Migration ist die für die neuen Übersetzungsschlüssel.

### FR-6: Layout

* Beide Gerüstseiten: `zev-container` mit `<h1>` samt `app-icon` und einer
  `zev-message--info` mit dem Hinweistext — kein Platzhalter-Formular, keine leere Tabelle.
* Alle Texte über `TranslationService` / `TranslatePipe`. Neue Schlüssel per Flyway-Migration
  (nächste freie Nummer: **V116**, höchste vorhandene ist V115) in die Tabelle
  `zev.translation (key, deutsch, englisch)` — **beide Sprachen sind Pflichtspalten**, je Schlüssel
  ist also ein deutscher *und* ein englischer Text zu hinterlegen. Abschluss mit
  `ON CONFLICT (key) DO NOTHING`, damit die Migration wiederholbar bleibt.
* Mindestens benötigte Schlüssel: `NEBENKOSTEN`, `NK_TARIFPOSITIONEN`, `NK_ABRECHNUNG`,
  `NK_NOCH_NICHT_VERFUEGBAR`, `FEATURE_FLAG_NEBENKOSTENABRECHNUNG`.
* Design System verwenden (`zev-container`, `zev-message`, `app-icon`); kein neues
  komponentenspezifisches CSS ausser dem Untermenü aus FR-3, das ins Design System gehört.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

**Feature-Flag**
* [ ] Der globale Default von `NEBENKOSTENABRECHNUNG` ist `false` — ein Mandant ohne
      Überschreibung sieht nichts von der Nebenkostenabrechnung.
* [ ] Bei ausgeschaltetem Flag erscheint der Menüpunkt „Nebenkosten" nicht.
* [ ] Bei ausgeschaltetem Flag führt der direkte Aufruf von `/nebenkosten/tarifpositionen`
      **nicht** zur Seite.
* [ ] Nach dem Einschalten in den Einstellungen ist der Menüpunkt nach dem Neuladen sichtbar.
* [ ] Der Flag erscheint in der Feature-Flag-Tabelle der Einstellungen mit übersetzter
      Beschreibung.

**Navigation**
* [ ] Der Menüpunkt „Nebenkosten" zeigt die Untereinträge „Tarifpositionen" und „Abrechnung".
* [ ] Ein Klick auf „Nebenkosten" **navigiert nicht**, sondern klappt nur auf und wieder zu.
* [ ] Steht man auf einer NK-Seite, ist das Untermenü aufgeklappt und der aktive Untereintrag
      markiert.
* [ ] Das Untermenü lässt sich auch im Hamburger-Menü bedienen.
* [ ] Der bestehende Menüpunkt „Tarifpositionen" (`/tarifpositionen`) bleibt unverändert
      erhalten und ist von den NK-Einträgen unterscheidbar.
* [ ] Die übrigen Menüpunkte verhalten sich unverändert (kein Aufklapper, direkte Navigation)
      und werden **nicht** nach Berechtigung ausgeblendet.
* [ ] Bei eingeschaltetem Flag, aber **fehlender** Permission `nebenkosten:manage` erscheint der
      Menüpunkt „Nebenkosten" **nicht**.
* [ ] Mit Permission, aber **ausgeschaltetem** Flag erscheint er ebenfalls nicht — beide
      Bedingungen müssen erfüllt sein.
* [ ] Die Prüflogik für Permissions existiert **einmal** und wird von `AuthGuard` und Direktive
      gemeinsam genutzt (keine zweite Kopie der Rollenauswertung).

**Gerüstseiten**
* [ ] `/nebenkosten/tarifpositionen` und `/nebenkosten/abrechnung` sind erreichbar und zeigen je
      einen übersetzten Hinweis, dass die Funktion noch nicht verfügbar ist.
* [ ] Keine der beiden Seiten setzt einen API-Aufruf ab oder verändert Daten.
* [ ] Der Aufruf von `/nebenkosten` leitet auf `/nebenkosten/abrechnung` weiter — kein
      Router-Fehler, keine hängende Navigation.
* [ ] Die Umsetzung legt **keine** neue Tabelle und keine neue Spalte an; die einzige Migration
      ist V116 mit den Übersetzungen.

**Sicherheit**
* [ ] Ohne die Permission `nebenkosten:manage` sind beide Routen nicht aufrufbar — auch bei
      eingeschaltetem Flag.
* [ ] Flag und Permission wirken **unabhängig**: Fehlt eines von beiden, ist der Bereich gesperrt.
* [ ] Jede Abweisung — durch `AuthGuard` wie durch `FeatureFlagGuard` — endet auf `/startseite`.
* [ ] `keycloak/realms/zev-realm.json` enthält die Rolle `nebenkosten:manage` und führt sie in
      den `composite`-Listen der vorgesehenen Fachrollen; ein frisch importierter Realm kennt sie.
* [ ] `Specs/Berechtigungen.md` führt `nebenkosten:manage` in der Permission-Matrix.

**Performance**
* [ ] Der Aufbau des Menüs löst **keinen** zusätzlichen HTTP-Request aus (im Netzwerk-Tab prüfbar);
      die Flags stammen aus dem beim App-Start geladenen Zustand.

**i18n**
* [ ] Alle sichtbaren Texte stammen aus dem `TranslationService`; keine fest verdrahteten Strings.
* [ ] Jeder neue Schlüssel hat einen deutschen **und** einen englischen Text.
* [ ] Die Übersetzungsmigration ist wiederholbar (`ON CONFLICT (key) DO NOTHING`).

**Verhalten bei Änderung des Flags**
* [ ] Wird der Flag während einer laufenden Sitzung ausgeschaltet, bleibt der Menüpunkt bis zum
      nächsten Laden der Flags sichtbar; ein Klick darauf landet jedoch auf `/startseite`.

> **Hinweis zur Prüfung:** Feature-Flags sind **mandantenweiter** Zustand. Alle Kriterien, die den
> Flag umschalten, müssen in den E2E-Tests **serial und in einem einzigen Browser** laufen —
> parallele Worker überlagern sich sonst gegenseitig und die Tests werden unzuverlässig
> (im Projekt bereits aufgetreten, siehe `frontend-service/tests/` und die dortigen
> `test.describe.serial`-Blöcke).

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Der Feature-Flag wird beim App-Start einmal geladen; der Aufbau des Menüs löst **keine**
  zusätzliche Abfrage aus.
* Das Auf- und Zuklappen des Untermenüs erfolgt rein clientseitig ohne Navigation.

### NFR-2: Sicherheit
* Neue Permission **`nebenkosten:manage`**.
* **Die Permission muss an zwei Orten entstehen — sonst ist das Feature für niemanden sichtbar:**
  1. **`keycloak/realms/zev-realm.json`** (versioniert): Dort sind heute 22 Realm-Rollen
     definiert — 19 Permissions und die drei Composite-Fachrollen `zev_user`, `org_admin`,
     `zev_admin`. `nebenkosten:manage` ist als weitere Permission-Rolle aufzunehmen **und** in die
     `composite`-Liste der vorgesehenen Fachrollen einzutragen. Das gilt für **Neuinstallationen**:
     Ohne diesen Eintrag existiert die Rolle dort gar nicht.
  2. **In der laufenden Keycloak-Instanz** (bestehende Installation): Die JSON-Änderung wirkt dort
     **nicht** — `--import-realm` greift nur bei leerer Datenbank (siehe `Specs/HTTPS.md`,
     Abschnitt 4). Der **Betreiber** legt die Rolle in der Admin-Konsole an und weist sie den
     Fachrollen zu.
* **Reihenfolge:** Rolle in Keycloak anlegen → den Fachrollen zuweisen → abmelden/anmelden, damit
  der Token die Rolle trägt. Erst danach sind die Akzeptanzkriterien zur Sichtbarkeit prüfbar.
  Fehlt dieser Schritt, verhält sich die Anwendung wie bei einem nicht umgesetzten Feature:
  Menüpunkt unsichtbar, Route abgewiesen — ohne Fehlermeldung, weil Flag und Guard beide
  unauffällig „nein" sagen.
* `Specs/Berechtigungen.md` wird um die Permission ergänzt, damit die Matrix vollständig bleibt.
* Frontend: beide Routen mit `data.permissions: ['nebenkosten:manage']` im `AuthGuard`.
* Der Flag ersetzt die Permission **nicht** und umgekehrt — beide werden geprüft.

### NFR-3: Kompatibilität
* Rein additiv: ein Enum-Wert, zwei Routen, zwei Komponenten, eine Übersetzungsmigration und eine
  Erweiterung der Navigation. Keine bestehende Tabelle, kein bestehender Ablauf wird berührt.
* Die Erweiterung der Navigation darf das Verhalten der bestehenden Menüpunkte nicht verändern;
  sie bleiben einstufige Links.
* Rücknahme: Flag ausschalten genügt, um den Bereich vollständig zu verbergen.

## 5. Edge Cases & Fehlerbehandlung

* **Flag mitten in der Sitzung ausgeschaltet:** Der Menüpunkt verschwindet erst beim nächsten
  Laden der Flags. Ein Navigationsversuch auf eine NK-Route wird vom `FeatureFlagGuard`
  abgewiesen. Ein sofortiges Ausblenden ohne Neuladen wird nicht verlangt.
* **Flag an, Permission fehlt:** Der Menüpunkt erscheint gar nicht (FR-3). Ruft der Benutzer die
  Route dennoch direkt auf, weist der `AuthGuard` ihn auf die Startseite zurück.
* **Direkte URL-Eingabe** auf `/nebenkosten/abrechnung` bei ausgeschaltetem Flag: keine Seite,
  Weiterleitung auf `/startseite` (`MESSWERTE_UPLOAD` als Vorbild).
* **Aufruf von `/nebenkosten`:** Weiterleitung auf `/nebenkosten/abrechnung` (FR-4); von dort
  greifen Flag- und Permission-Prüfung wie bei jedem direkten Aufruf.
* **Schmale Ansicht:** Das Untermenü darf im Hamburger-Menü nicht überlappen oder abgeschnitten
  werden; bei mehreren Untereinträgen scrollt das Menü.
* **Fehlende Übersetzung:** Fehlt ein Schlüssel, wird — wie im Rest der Anwendung — der Schlüssel
  selbst angezeigt; die Seite bleibt bedienbar.
* **Leere Listen / Netzwerkfehler:** In dieser Ausbaustufe nicht anwendbar, da die Seiten keine
  Daten laden. Ab der ersten Folge-Spec gelten die Projektkonventionen (Leerstate,
  `zev-message--error` bleibt stehen, Erfolgsmeldung blendet nach 5 s aus).

## 6. Abhängigkeiten & betroffene Funktionalität

**Voraussetzungen**
* `Specs/FeatureFlag.md` — Flag-Mechanik, `FeatureFlagGuard`, Direktive `*appFeature` (vorhanden).
* `Specs/Berechtigungen.md` / `Specs/Composite-Roles.md` — Permission-Modell (vorhanden).
* `Specs/Hamburgermenü.md` — Verhalten der schmalen Ansicht (vorhanden).

**Betroffener Code**
* `FeatureFlag`-Enum (Backend) — neuer Wert `NEBENKOSTENABRECHNUNG`.
* `design-system/src/components/navigation/` — Untermenü-Variante; Showcase ergänzen.
* `frontend-service/src/app/components/navigation/navigation.component.{html,ts}` — neuer
  Menüpunkt mit Untereinträgen.
* `frontend-service/src/app/app.routes.ts` — zwei neue Routen plus die Weiterleitung von
  `/nebenkosten` (FR-4).
* `frontend-service/src/app/directives/` — neue Direktive `appPermission` (FR-3a).
* `frontend-service/src/app/guards/auth.guard.ts` — Prüflogik in eine gemeinsam nutzbare
  Funktion ziehen (FR-3a); Verhalten unverändert.
* Zwei neue Komponenten unter `frontend-service/src/app/components/`.
* **`keycloak/realms/zev-realm.json`** — neue Permission-Rolle `nebenkosten:manage` und Aufnahme
  in die `composite`-Listen der vorgesehenen Fachrollen (NFR-2).
* `Specs/Berechtigungen.md` — Permission-Matrix um `nebenkosten:manage`.
* Neue Flyway-Migration **V116** für die Übersetzungsschlüssel (DE und EN).

**Voraussetzung ausserhalb des Codes**
* Der Betreiber legt `nebenkosten:manage` in der laufenden Keycloak-Instanz an und weist sie den
  Fachrollen zu (NFR-2). **Vor diesem Schritt sind die Akzeptanzkriterien nicht prüfbar.**

**Datenmigration**
* Keine.

## 7. Abgrenzung / Out of Scope

Diese Basis enthält **keine Fachlichkeit**. Bewusst nicht umgesetzt:

* **Erfassung von NK-Tarifpositionen** — Tabelle, CRUD, Formular und Liste. Folgt als
  **`NK-Tarifpositionen`**, sobald die Rechenarten entschieden sind.
* **Abrechnungslauf** — Berechnung, PDF, Debitorenbuchung. Folgt als **`NK-Abrechnung`**.
* **Verteilschlüssel** (Fläche, Personen, Wertquote) und **Rechenarten** (Quote, Menge, Prozent).
* **Neuer Tariftyp.** `tarif_tariftyp_check` zählt die erlaubten Werte auf
  (`ZEV`, `VNB`, `GRUNDGEBUEHR`, `LADESTROM`, `ZUSATZ`) — ein neuer Typ ist eine DDL-Änderung und
  wird erst mit den Rechenarten entschieden.
* **Keine Änderung an den bestehenden Tarifpositionen.** `/tarifpositionen` samt `LADESTROM` und
  `ZUSATZ` bleibt, wo und wie es ist.
* **Keycloak-Rollenzuordnung** — dokumentiert, aber vom Betreiber ausgeführt.

**Bereits entschieden und für `NK-Tarifpositionen` vorzumerken** (damit die Antworten nicht
verloren gehen):

| Frage | Entscheid |
|---|---|
| Anker der Position | **Mieter**, nicht Einheit |
| Zeitraum | **jährlich mit Eingabe von/bis**, Default **01.07.–30.06.** |
| Rechenarten | mindestens Quote, Menge, Prozent — Modell offen |

## 8. Offene Fragen

* **Rechenarten:** Wie werden Quote (`1/9`), Menge (`m³` × Preis) und Prozent (`5 %` einer
  Bezugsgrösse) modelliert — ein Tariftyp mit Unterscheidungsmerkmal, drei Typen, oder eine
  eigene NK-Tarifstruktur ausserhalb von `zev.tarif`? Blockiert `NK-Tarifpositionen`.
* **Betrag statt Menge:** Bei Nebenkosten ist oft der **Betrag** bekannt (die Heizkostenabrechnung
  des Lieferanten) und keine Menge mal Einheitspreis. Braucht die Position ein Betragsfeld? Hängt
  unmittelbar an den Rechenarten und ist mit ihnen zusammen zu entscheiden.
* **Abrechnungsjahr 01.07.–30.06.:** Ist der Default mandantenweit fix oder in den Einstellungen
  konfigurierbar? Wird er auch für die spätere Abrechnung als Vorschlag verwendet?
* **Menüpunkte flächendeckend nach Berechtigung ausblenden?** Mit FR-3a entsteht die dafür
  nötige Direktive. Sie wird vorerst **nur** für den NK-Eintrag verwendet; alle übrigen
  Menüpunkte bleiben für jeden angemeldeten Benutzer sichtbar. Soll das später vereinheitlicht
  werden — und wenn ja, als eigene Aufräumaufgabe?
* **Weitere Untereinträge:** Angekündigt ist „vermutlich später weitere". Kommt eine
  Übersichtsseite oder eine Einstellungsseite dazu, wäre der Elterneintrag als reiner Aufklapper
  zu überdenken.
