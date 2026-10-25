# Icons für Buttons und Menü

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Die Anwendung soll durch geeignete Icons attraktiver und modernen gestaletet werden.
* **Warum machen wir das:** Durch verbesserte Attraktivität sollen mehr Kunden gewonnen werden. 
* **Aktueller Stand:** Die Anwendung inkl. Buttons und Menü ist sehr nüchtern und wenig ansprechend.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Layout
* Die Buttons (Berechnen, Anzeigen, Validieren etc.) sollen durch ein kleines Icon ergänzt werden.
* Im Menü soll jede Zeile durch ein kleines Icon ergänzt werden
  * Das Icon soll links vom Text angezeigt werden
  * Das Icon soll auf der entsprechenden Seite ebenfalls eingeblendet werden
* Im Titel soll ein geeignetes Icon eingeblendet werden.
* **Blätter-Schaltflächen** (Tag, Zeitraum oder Seite zurück/vor) tragen die Feather-Icons
  `chevron-left` und `chevron-right` in doppelter Grösse (`size="xl"`, 2em) – auch im
  Design-System-Showcase. Die Schaltfläche wird dadurch nicht höher. Mit Text steht `chevron-left`
  vor „Vorherige“ und `chevron-right` nach „Nächste“; ohne Text (z. B. Steuerzeile der
  Einspeisesteuerung) trägt die Schaltfläche nur das Icon.
  > Das Icon zeigt die Richtung. Am Ende von „Nächste“ zeigt der Pfeil dorthin, wohin geblättert wird.
  > Der Strich eines Chevrons füllt nur die Mitte des Icon-Rasters – in normaler Grösse war er kaum zu treffen.
  > (Hintergrund: Anhang A.1)

### FR-2: Icon-Registry
* Neue Icons werden in `frontend-service/src/app/components/icon/icons.ts` registriert.
* Verwendete Icons: sun, moon, upload, home, zap, bar-chart-2, pie-chart, file-text, tag, globe, log-out, plus, check, x, edit-2, trash-2, copy, download, search, calculator, save, refresh-cw, eye, settings, user, users, calendar, filter, plus-circle, alert-circle, info, check-circle, grid, shield, layout, **credit-card** (Debitorkontrolle), **chevron-left**, **chevron-right** (Blättern, FR-1), **radio** (Messpunkt-Empfang).

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)
* [ ] Die Anwendung hat in der Titelzeile ein tolles Icon, das das Thema der Anwendung widerspiegelt. 
* [ ] Jede Zeile im Menü hat ein Icon, das zum Thema passt
* [ ] Jeder Button hat ein Icon, das zum Thema/zur Aktion passt


## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Das System darf nicht langsamer werden oder die Anzeige verzögern
* Das Icon kann auch nachgeladen werden

### NFR-2: Sicherheit
* - 

### NFR-3: Kompatibilität
* Die Grösse der Icons soll nicht allzu gross sein - der Text ist immer noch wichtiger


## 5. Edge Cases & Fehlerbehandlung
* Bei langsamer Reaktion der Anwendung kann das Icon nachgeladen werden.

## 6. Abgrenzung / Out of Scope
* Es muss zwingend für jeder Menüeintrag und Button ein Icon angezeigt werden.

## 7. Offene Fragen
* Werden die Icon generiert oder im Internet gesucht?

## Anhang A – Entscheidungen und Verlauf

### A.1 Blätter-Schaltflächen mit Chevron-Icons (FR-1, 23.10.2026)
FR-1 nannte Blätter-Schaltflächen nicht eigens. Die Anwendung verwendete `chevron-left`/`chevron-right` bereits (Statistik, Einspeisesteuerung, Preiszeitreihe, Systemmeldungen, Datenbank-Ansicht), die Paginierungsbeispiele im Design-System-Showcase zeigten aber nur „Vorherige“/„Nächste“ als Text. Auf Wunsch des Users tragen sie jetzt dieselben Icons, und die Regel steht in FR-1.

Die Annahme 6 im Umsetzungsplan („Icons nur links vom Text, nie rechts“) gilt für Blätter-Schaltflächen nicht: „Nächste“ trägt den Pfeil rechts, wie schon in der Datenbank-Ansicht.

Eine eigene Spec „Design-System“ wurde nicht angelegt: Die Regeln für das Design System stehen in `Specs/generell.md` (Abschnitt „Design System“), Icon-Regeln hier. Eine dritte Stelle hätte dieselben Regeln doppelt geführt.

### A.2 Grössere Chevrons auf Blätter-Schaltflächen (FR-1, 23.10.2026)
Bis dahin trugen die Blätter-Schaltflächen ihre Chevrons in Standardgrösse (`md`, 1em). Auf Wunsch des Users sind sie mindestens doppelt so gross. Dafür gibt es die neue Icon-Grösse `xl` (2em, `design-system/src/components/icon/icon.css`). In Schaltflächen gleicht ein negativer Rand oben und unten die zusätzliche Höhe aus, damit sie in ihrer Zeile nicht höher werden als die Nachbarn. „Zurück zur Übersicht“ in der Nebenkostenabrechnung trägt ebenfalls `chevron-left`, blättert aber nicht und bleibt in Standardgrösse.

