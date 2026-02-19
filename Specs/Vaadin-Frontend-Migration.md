# Vaadin Frontend-Migration (Angular → Vaadin)

## 1. Ziel & Kontext - Warum wird das Feature benötigt?

* **Was soll erreicht werden:** Das bestehende Angular 21 SPA-Frontend soll durch ein Vaadin-basiertes Server-Side-Rendering-Frontend ersetzt werden. Die gesamte UI-Logik wird ins Backend (JVM/Spring Boot) verlagert.
* **Warum machen wir das:**
  * Vereinheitlichung des Tech-Stacks auf Java/Spring Boot – ein einziger Deployment-Artefakt statt zwei getrennter Services
  * Entwickler mit Java-Kenntnissen können UI-Features ohne TypeScript/Angular-Wissen umsetzen
  * Vaadin bietet serverseitige Session-Verwaltung, Keycloak-Integration via Spring Security ist einfacher
  * Kein eigenständiges `frontend-service` Docker-Image mehr notwendig
* **Aktueller Stand:** Das Angular-Frontend läuft als separater `frontend-service` (Docker, Nginx, Port 4200/8080). Es kommuniziert via REST-API mit dem `backend-service`. Authentication via `keycloak-angular`.

---

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

Alle bestehenden Module des Angular-Frontends werden als Vaadin-Views re-implementiert. Die REST-API des `backend-service` bleibt bestehen (für externe Konsumenten / Testing). Intern ruft Vaadin die bestehenden Spring-Services direkt auf – kein HTTP-Roundtrip mehr.

### FR-1: Architektur & Projektstruktur

1. Ein neues Maven-Modul `vaadin-service` (oder Integration in `backend-service`) wird angelegt.
2. Vaadin läuft als Spring Boot Starter (`vaadin-spring-boot-starter`).
3. Keycloak-Authentifizierung via Spring Security / Vaadin Security Integration.
4. Die bestehenden `@Service`-Klassen im `backend-service` werden direkt von den Vaadin-Views verwendet.
5. Der `frontend-service` (Angular + Nginx) wird stillgelegt.
6. Multi-Tenancy bleibt vollständig erhalten: `HibernateFilterService.enableOrgFilter()` wird in jedem View aktiviert (z.B. via `BeforeEnterObserver` oder Spring Security Context).

### FR-2: Navigation (Hamburger-/Seitenmenü)

1. Vaadin `AppLayout` mit `Drawer` ersetzt das Angular-Hamburger-Menü.
2. Menüeinträge entsprechen den bestehenden Routen:

| Menüeintrag | Route (Vaadin View) | Rollen |
|---|---|---|
| Startseite | `/` | zev, zev_admin |
| Messwerte hochladen | `/upload` | zev, zev_admin |
| Einheiten | `/einheiten` | zev, zev_admin |
| Solar-Berechnung | `/solar-calculation` | zev, zev_admin |
| Messwerte Chart | `/chart` | zev |
| Statistik | `/statistik` | zev |
| Rechnungen | `/rechnungen` | zev_admin |
| Tarife | `/tarife` | zev_admin |
| Mieter | `/mieter` | zev_admin |
| Einstellungen | `/einstellungen` | zev_admin |
| Übersetzungen | `/translations` | zev_admin |

3. Navigation ist über `@Route` + `@RolesAllowed` / Spring Security abgesichert.
4. Sprachauswahl (DE/EN) im Footer des Drawers, analog zum bestehenden Hamburger-Menü.
5. Logout-Link im Drawer.

### FR-3: Modul Startseite (`/`)

1. Nach dem Login wird die Startseite angezeigt.
2. Begrüssungstext und passendes ZEV-Bild (Solar-Thema).
3. Der Navigations-Drawer ist beim ersten Laden aufgeklappt (expanded).
4. Rollen: `zev`, `zev_admin`.

### FR-4: Modul Einheitenverwaltung (`/einheiten`)

1. Tabelle (Vaadin `Grid`) mit allen Einheiten des Mandanten:
   * Spalten: Name, Typ (Consumer/Producer), Messpunkt, Aktionen (Kebab-Menü)
2. Kebab-Menü pro Zeile: Bearbeiten, Löschen
3. Button "Neue Einheit" öffnet ein Formular-Dialog (Vaadin `Dialog` oder neue View `/einheiten/neu`):
   * Felder: Name (max. 30 Zeichen), Typ (Dropdown: Consumer/Producer), Messpunkt
4. Bearbeiten öffnet das gleiche Formular vorbelegt.
5. Löschen: Bestätigungsdialog vor dem Löschen.
6. Rollen: Lesen `zev`, `zev_admin`; Schreiben/Löschen nur `zev_admin`.

### FR-5: Modul Messwerte hochladen (`/upload`)

1. Datei-Upload-Zone (Drag & Drop, Klick):
   * Vaadin `Upload`-Komponente (ersetzt `drop-zone`-Design-System-Klasse).
2. Nach Auswahl der Datei:
   * KI-basierte Einheitserkennung aus Dateiname via `EinheitMatchingService` (direkter Service-Aufruf, kein HTTP).
   * Erkannte Einheit wird angezeigt (Confidence: hoch = direkt übernommen, niedrig = manuelle Auswahl-Dropdown).
   * Datum wird aus Dateiname extrahiert und angezeigt.
3. Benutzer kann Einheit und Datum korrigieren.
4. Button "Importieren" startet den Upload.
5. Fehlermeldung bei KI-Fehler oder nicht erkannter Einheit.
6. KI-Bestimmung darf nicht länger als 2 Sekunden dauern (Timeout + Fallback auf manuelle Auswahl).
7. Rollen: `zev`, `zev_admin`.

### FR-6: Modul Messwerte Chart (`/chart`)

1. Vaadin integriert Chart.js via `vaadin-charts` Add-on (oder iframe / Custom Element).
   * **Alternative:** Server-Side Chart-Rendering mit JFreeChart als PNG (einfacherer Ansatz ohne JS-Abhängigkeit).
   * **Empfehlung:** `vaadin-charts` (kommerziell) ODER Lit-basiertes Custom Web Component für Chart.js.
2. Zeitbereichsfilter: Quartal-Selector (5 letzte Quartale als Buttons) + manuelle Von/Bis-Datumswahl.
3. Einheitenauswahl mit Checkboxen (alle/einzelne Consumer und Producer).
4. Linienchart: Messwerte pro Einheit über Zeit.
5. Rollen: `zev`, `zev_admin`.

### FR-7: Modul Solar-Berechnung (`/solar-calculation`)

1. Zeitbereichsfilter analog zu Chart.
2. Anzeige der Verteilungsberechnung (`SolarDistributionService` / `ProportionalConsumptionDistributionService`).
3. Ergebnistabelle: Verbrauch, ZEV-Anteil, Netzanteil pro Consumer.
4. Rollen: `zev`, `zev_admin`.

### FR-8: Modul Statistik (`/statistik`)

1. Quartal-Selector + manuelle Datumsauswahl (Default: vorheriger Monat).
2. Übersichtsbereich:
   * "Messwerte vorhanden bis" (letztes Datum mit Daten)
   * Vollständigkeitsanzeige (alle Consumer/Producer haben Daten bis zu diesem Datum?)
3. Monatstabelle mit:
   * Zeitbereich
   * Datenvollständigkeit (alle Tage vorhanden?)
   * Summen A–E (Produktion, Verbrauch Total, ZEV-Anteile)
   * Vergleiche C=D, C=E, D=E (farblich hervorgehoben)
   * Liste fehlerhafter Tage
4. Export als PDF (via `StatistikPdfService` – direkter Aufruf, kein HTTP).
5. Rollen: `zev`, `zev_admin`.

### FR-9: Modul Rechnungen (`/rechnungen`)

1. Zeitbereichsfilter: Quartal-Selector + manuelle Von/Bis-Datumsauswahl.
2. Consumer-Auswahl mit Checkboxen (alle / einzelne).
3. Button "Generieren":
   * Rechnungen werden generiert via `RechnungPdfService`.
   * Fortschrittsanzeige (Vaadin `ProgressBar`).
4. Nach Generierung: Tabelle mit generierten Rechnungen, je ein Download-Button (PDF).
5. Validierung: Für den gesamten Zeitbereich müssen gültige Tarife vorhanden sein → Fehlermeldung wenn nicht.
6. Rollen: `zev_admin`.

### FR-10: Modul Tarifverwaltung (`/tarife`)

1. Tabelle mit allen Tarifen des Mandanten:
   * Spalten: Bezeichnung, Typ (ZEV/VNB), Preis (CHF/kWh), Gültig von, Gültig bis, Aktionen (Kebab)
2. Kebab-Menü: Bearbeiten, Löschen, Kopieren
3. Button "Neuer Tarif":
   * Felder: Bezeichnung (max. 30 Zeichen), Tariftyp (Dropdown: ZEV/VNB), Preis (5 Nachkommastellen), Gültig von (Datum), Gültig bis (Datum)
   * Alle Felder Pflichtfelder
4. Datumsformat: dd.MM.yyyy (Schweizer Format)
5. Rollen: `zev_admin`.

### FR-11: Modul Mieterverwaltung (`/mieter`)

1. Tabelle mit allen Mietern des Mandanten:
   * Spalten: Name, Strasse, PLZ, Ort, Mietbeginn, Mietende, Einheit, Aktionen (Kebab)
2. Kebab-Menü: Bearbeiten, Löschen
3. Button "Neuer Mieter":
   * Felder: Name, Strasse, PLZ, Ort, Mietbeginn (Pflicht), Mietende (optional), Einheit (Dropdown)
4. Validierungen:
   * Mietende muss nach Mietbeginn liegen
   * Zeitspannen dürfen sich für die gleiche Einheit nicht überschneiden
   * Nur der Mieter mit dem jüngsten Mietbeginn einer Einheit darf kein Mietende haben
5. Rollen: `zev_admin`.

### FR-12: Modul Einstellungen (`/einstellungen`)

1. Formular mit den mandantenspezifischen Einstellungen:
   * Rechnung: Zahlungsfrist, IBAN, Rechnungssteller (Name, Strasse, PLZ, Ort)
2. Alle Felder Pflichtfelder.
3. Button "Speichern".
4. Daten werden direkt via `EinstellungenService` gelesen/geschrieben.
5. Rollen: `zev_admin`.

### FR-13: Modul Übersetzungen (`/translations`)

1. Tabelle mit allen Übersetzungskeys:
   * Spalten: Key, Deutsch, Englisch, Aktionen (Kebab)
2. Kebab-Menü: Bearbeiten, Löschen
3. Button "Neue Übersetzung":
   * Felder: Key, Deutsch, Englisch
4. Inline-Bearbeitung oder Formular-Dialog.
5. Rollen: `zev_admin`.

### FR-14: Mehrsprachigkeit (i18n)

1. Alle UI-Texte via `TranslationService` – keine hartcodierten Texte.
2. Vaadin-Komponenten-Labels werden via `TranslationService` gesetzt.
3. Sprachumschaltung DE/EN zur Laufzeit (ohne Page-Reload) via Vaadin `UI.getCurrent().setLocale()`.
4. Aktive Sprache wird in der Vaadin-Session gespeichert.

### FR-15: Fehlermeldungen & Notifications

1. Erfolgreiche Aktionen: Vaadin `Notification` (grün, auto-dismiss nach 5 Sekunden).
2. Fehlermeldungen: Vaadin `Notification` (rot) oder inline im Formular – bleibt bis manuell geschlossen.
3. Formularvalidierung mit sofortigem Feedback (Vaadin `Binder`).

---

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

### Navigation
* [ ] Alle Menüpunkte sind entsprechend der Rollenzuordnung sichtbar/unsichtbar
* [ ] Sprachauswahl (DE/EN) funktioniert und wird in der Session gehalten
* [ ] Logout funktioniert und leitet zu Keycloak weiter

### Startseite
* [ ] Nach Login wird die Startseite angezeigt
* [ ] Navigation ist beim ersten Laden aufgeklappt

### Einheitenverwaltung
* [ ] Einheiten können erstellt, bearbeitet und gelöscht werden
* [ ] Kebab-Menü öffnet sich und schliesst sich korrekt (Klick, ESC, Außerhalb)
* [ ] Name max. 30 Zeichen, Pflichtfelder validiert

### Messwerte hochladen
* [ ] Datei per Drag & Drop und Klick wählbar
* [ ] KI-Einheitserkennung < 2 Sekunden
* [ ] Bei hoher Konfidenz wird Einheit automatisch gesetzt
* [ ] Bei niedriger Konfidenz erscheint Dropdown zur manuellen Auswahl
* [ ] Fehlermeldung wenn KI nicht erreichbar

### Statistik
* [ ] Quartal-Selector setzt Von/Bis-Datum korrekt (5 letzte Quartale)
* [ ] Summen A–E werden korrekt berechnet und angezeigt
* [ ] Vergleiche C=D, C=E, D=E sind farblich markiert
* [ ] PDF-Export funktioniert

### Rechnungen
* [ ] Generierung nur möglich wenn für den gesamten Zeitraum Tarife vorhanden sind
* [ ] Fehlermeldung wenn Tarife fehlen
* [ ] Generierte PDFs sind einzeln downloadbar
* [ ] Bei Mieterwechsel innerhalb eines Quartals erhalten beide Mieter eine Rechnung

### Tarifverwaltung
* [ ] Tarife können erstellt, bearbeitet, gelöscht und kopiert werden
* [ ] Alle Felder Pflichtfelder
* [ ] Schweizer Datumsformat dd.MM.yyyy

### Mieterverwaltung
* [ ] Mieter können erstellt, bearbeitet und gelöscht werden
* [ ] Mietende muss nach Mietbeginn liegen (Validierung)
* [ ] Überschneidungen von Mietperioden der gleichen Einheit werden abgelehnt

### Allgemein
* [ ] Multi-Tenancy: Jeder Mandant sieht nur seine eigenen Daten
* [ ] Alle UI-Texte sind übersetzt (kein hartcodierter Text)
* [ ] Fehlerhafte Serverantworten werden dem Benutzer verständlich angezeigt
* [ ] Leere Listen zeigen eine hilfreiche Meldung ("Keine Daten vorhanden")

---

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Seitenlade-Zeit < 2 Sekunden (Vaadin serverseitig, keine initialen JS-Bundle-Downloads)
* Grids mit Lazy Loading für Listen > 50 Einträge
* Statistik- und Rechnungsgenerierung können länger dauern → Fortschrittsanzeige

### NFR-2: Sicherheit
* Authentifizierung via Keycloak / Spring Security (OAuth2 Resource Server oder Authorization Code Flow)
* Alle Views mit `@RolesAllowed` oder `@AnonymousAllowed` annotiert
* Multi-Tenancy: `org_id`-Filter via `HibernateFilterService` in jedem View aktiv
* CSRF-Schutz via Vaadin integriert (serverseitig)
* Keine Secrets in Templates oder Logs

### NFR-3: Kompatibilität & Browser-Support
* Moderne Browser: Chrome, Firefox, Safari, Edge (aktuelle Versionen)
* Vaadin benötigt JavaScript im Browser (für WebSocket-Verbindung)
* Mobilgeräte: Grundfunktionalität, keine vollständige Mobile-Optimierung (Out of Scope)

### NFR-4: Deployment
* Vaadin wird Teil des `backend-service` (neuer Maven-Modul oder direkte Integration)
* `frontend-service` (Docker, Nginx) wird entfernt
* `docker-compose.yml` wird angepasst: kein separater `frontend-service` mehr
* Produktions-Build: `mvn clean package -Pproduction` (Vaadin Production Build bündelt und optimiert Assets)

### NFR-5: Testing
* Unit-Tests für Service-Aufrufe aus Views (bestehende Backend-Tests bleiben gültig)
* Playwright E2E-Tests (oder TestBench, Vaadin eigene Lösung) für kritische User Flows
* Bestehende Backend-Unit- und Integrationstests bleiben unverändert

---

## 5. Edge Cases & Fehlerbehandlung

* **Session-Timeout:** Vaadin zeigt automatisch einen "Session expired"-Dialog → Redirect zu Keycloak-Login
* **Netzwerkfehler (WebSocket):** Vaadin zeigt Reconnect-Indikator, bei Timeout Fehlermeldung
* **Leere Listen:** Hilfreiche Meldung (via Translation, z.B. "Keine Tarife vorhanden")
* **Gleichzeitige Bearbeitung:** Optimistic Locking auf DB-Ebene (bestehend), Fehlermeldung bei Konflikt
* **Löschen referenzierter Daten:** Fehlermeldung vom Backend wird im Frontend angezeigt
* **Ungültige Formularwerte:** Sofortiges Feedback via Vaadin `Binder`-Validierung
* **KI nicht erreichbar (Messwerte-Upload):** Fehlermeldung + manuelle Einheitenauswahl als Fallback

---

## 6. Abhängigkeiten & betroffene Funktionalität

* **Voraussetzungen:**
  * `backend-service` mit allen bestehenden `@Service`-Klassen (werden direkt aufgerufen)
  * Keycloak (weiterhin für Authentifizierung)
  * PostgreSQL (unverändert)
* **Betroffener Code:**
  * `frontend-service/` – wird komplett abgelöst und gelöscht
  * `docker-compose.yml` – `frontend-service`-Service wird entfernt, ggf. Port-Anpassung
  * `backend-service/pom.xml` – Vaadin-Dependency hinzufügen
  * `SecurityConfig.java` – Anpassung auf Vaadin Security (VaadinWebSecurity)
  * REST-Controller bleiben bestehen (für API-Tests und externe Konsumenten)
* **Datenmigration:** Keine – Datenbankschema bleibt unverändert

### Externe Design-System-Abhängigkeit
* Das bestehende `@zev/design-system` (npm, CSS-Klassen) wird **nicht** in Vaadin verwendet
* Styling erfolgt via Vaadin Lumo Theme (Customization via CSS Custom Properties / `@CssImport`)
* Die Vaadin-Komponenten ersetzen die Design-System-Komponenten 1:1:

| Design System (Angular) | Vaadin Äquivalent |
|---|---|
| `.zev-table`, `KebabMenuComponent` | `Grid` + `GridContextMenu` |
| `.zev-form-*`, `.zev-input` | `FormLayout`, `TextField`, `DatePicker`, `Select` |
| `.zev-button--primary/secondary` | `Button` (primary/secondary theme variant) |
| `.zev-message--success/error` | `Notification` |
| `.zev-spinner` | `ProgressBar` (indeterminate) |
| `.zev-collapsible` | `Details` |
| `.zev-drop-zone` | `Upload` |
| `QuarterSelectorComponent` | Custom Vaadin Component (Horizontal Layout mit Buttons) |
| `NavigationComponent` (Hamburger) | `AppLayout` + `Drawer` |

---

## 7. Abgrenzung / Out of Scope

* **Mobile-Optimierung:** Kein responsives Layout für Smartphones (Vaadin-Standard-Responsiveness genügt)
* **Design System weiterentwickeln:** Das `@zev/design-system` npm-Paket wird nicht migriert, sondern durch Vaadin Lumo ersetzt
* **Angular beibehalten:** Kein Hybrid-Betrieb (Angular + Vaadin gleichzeitig)
* **Migration bestehender E2E-Tests:** Playwright-Tests werden neu geschrieben (Selektoren ändern sich grundlegend)
* **Offline-Fähigkeit / PWA:** Kein Service Worker / Offline-Modus
* **Vaadin Charts (kommerziell):** Falls Lizenzkosten nicht tragbar, wird Chart.js via Custom Web Component oder JFreeChart (Server-Side PNG) als Alternative verwendet → wird in Phase Messwerte Chart konkretisiert
* **Prometheus Metriken für Frontend:** Das Angular-Frontend hat keinen eigenen Actuator-Endpunkt. Der `backend-service` liefert weiterhin alle relevanten Metriken.

---

## 8. Offene Fragen

1. **Vaadin-Modul-Strategie:** Vaadin direkt in `backend-service` integrieren (ein Artefakt) oder neues Maven-Modul `vaadin-service`? → Empfehlung: Integration in `backend-service` (einfacher, direkter Service-Zugriff)
2. **Vaadin-Version:** Vaadin 24 (LTS) oder Vaadin 25? → Vaadin 24.x (aktuelle LTS, Spring Boot 4 kompatibel prüfen)
3. **Charting-Lösung:** `vaadin-charts` (kommerziell, ~1200 EUR/Jahr) vs. Chart.js Custom Web Component vs. JFreeChart (server-side PNG)? → Entscheidung vor Umsetzung Phase Chart notwendig
4. **Vaadin TestBench vs. Playwright:** Vaadin TestBench (kommerziell, optimal für Vaadin) vs. Playwright (kostenlos, weniger Vaadin-aware) für E2E-Tests?
5. **Spring Security Integration:** Keycloak BearerToken (wie bisher) vs. Authorization Code Flow für serverseitige Vaadin-Sessions? → Authorization Code Flow empfohlen für SSR
6. **QuarterSelector:** Custom Vaadin Component oder Wiederverwendung bestehender Angular-Logik als Web Component?
7. **Rollout-Strategie:** Big Bang (alles auf einmal) oder modul-weise Migration mit parallelem Betrieb?

---

## 9. Vorgeschlagene Implementierungsreihenfolge (Phasen)

| Phase | Module | Begründung |
|---|---|---|
| 1 | Architektur-Setup, Keycloak-Integration, Navigation, Startseite | Fundament ohne Businesslogik |
| 2 | Einheitenverwaltung, Tarifverwaltung, Mieterverwaltung | CRUD-Module (Tabelle + Formular Pattern) |
| 3 | Statistik, Solar-Berechnung | Lese-/Berechnungs-Views |
| 4 | Messwerte-Upload (inkl. KI) | Komplexer wegen KI + Datei-Upload |
| 5 | Rechnungen | PDF-Generierung + Download |
| 6 | Messwerte Chart | Komplexe Visualisierung |
| 7 | Einstellungen, Übersetzungen, i18n | Verwaltungs-Module |
| 8 | E2E-Tests, Performance-Optimierung | Absicherung |
