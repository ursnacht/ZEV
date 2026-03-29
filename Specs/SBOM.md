# SBOM

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Generierung einer Software Bill of Materials (SBOM) für Backend und Frontend sowie eine Lizenzübersichtsseite im Frontend, die alle verwendeten Libraries mit ihren Lizenzen anzeigt.
* **Warum machen wir das:** Transparenz über eingesetzte Open-Source-Komponenten und deren Lizenzen (Compliance, Auditierbarkeit, Sicherheit). Pflicht bei professionellen Softwareprojekten; ermöglicht frühzeitiges Erkennen von Lizenzproblemen (z.B. GPL-Kontamination) und bekannten Schwachstellen (CVE) in Abhängigkeiten.
* **Aktueller Stand:** Es existiert keine SBOM-Generierung und keine Lizenzübersicht im Projekt.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: SBOM-Generierung beim Build

**Backend (Maven):**
1. Das `cyclonedx-maven-plugin` wird im `backend-service/pom.xml` konfiguriert und läuft automatisch in der `package`-Phase.
2. Die generierte SBOM (`bom.json`, CycloneDX-Format) wird unter `src/main/resources/sbom/backend-bom.json` eingebettet und ist im JAR als Classpath-Ressource verfügbar.

**Frontend (npm):**
1. Ein npm-Script `generate-licenses` nutzt `license-checker-rseidelsohn` um alle Frontend-Abhängigkeiten mit Lizenzinformationen in eine JSON-Datei (`src/assets/frontend-licenses.json`) zu exportieren.
2. Dieses Script wird in den Angular-Build-Prozess (`prebuild`) integriert, sodass die Datei bei jedem Build aktuell ist.

### FR-2: Backend-Endpoint

1. Ein neuer Controller `LizenzenController` stellt unter `GET /api/lizenzen` die kombinierten Lizenzdaten beider Komponenten (Backend + Frontend) als JSON zur Verfügung.
2. Der Endpoint liest die eingebettete `backend-bom.json` vom Classpath und gibt eine normalisierte Liste von `LizenzenDTO`-Einträgen zurück.
3. Die Frontend-Lizenzdaten werden als statisches Asset direkt vom Frontend geladen (nicht über den Backend-Endpoint).

### FR-3: Datenstruktur

Jeder Lizenzeintrag enthält:
* `name` - Name der Library (z.B. `spring-boot-starter-web`)
* `version` - Versionsnummer (z.B. `4.0.1`)
* `license` - Lizenzbezeichnung (z.B. `Apache-2.0`)
* `publisher` - Hersteller / Gruppe (optional, aus SBOM-Daten)
* `url` - Projekt-URL oder Repository (optional)
* `hashes` - Liste von Integritäts-Hashes des Artefakts; jeder Eintrag enthält:
  * `algorithm` - Hash-Algorithmus (z.B. `SHA-256`, `SHA-512`)
  * `value` - Hex-kodierter Hash-Wert

Die Hashes werden direkt aus der CycloneDX-SBOM übernommen (Backend: Maven-Artefakt-Hashes aus dem lokalen Repository; Frontend: Paket-Hashes aus `package-lock.json` / npm integrity). Falls für eine Library kein Hash vorliegt, ist `hashes` eine leere Liste.

### FR-4: Frontend-Seite `LizenzenComponent`

1. Neue Route `/lizenzen` mit neuer Komponente `LizenzenComponent`.
2. Die Seite zeigt zwei Abschnitte: **Backend-Libraries** (vom Backend-Endpoint geladen) und **Frontend-Libraries** (aus `assets/frontend-licenses.json` geladen).
3. Jeder Abschnitt zeigt eine Tabelle mit den Spalten: Name, Version, Lizenz, Hersteller, Hash (erster verfügbarer Hash des stärksten Algorithmus, gekürzt auf 12 Zeichen mit Tooltip für den vollständigen Wert).
4. Die Tabelle ist nach Library-Name alphabetisch vorsortiert.
5. Ein Suchfeld über jeder Tabelle erlaubt das Filtern nach Name oder Lizenz.
6. Neben dem Seitentitel erscheint ein passendes Feather Icon.
7. Alle UI-Texte werden via `TranslationService` / `TranslatePipe` ausgegeben.

### FR-5: Navigation

1. Die neue Seite `/lizenzen` wird im Hamburger-Menü (`NavigationComponent`) als Navigationspunkt aufgeführt.
2. Der Menüpunkt ist für alle authentifizierten Benutzer sichtbar (Rollen `zev` und `zev_admin`).

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

* [ ] Der Maven-Build (`mvn package`) erzeugt `backend-bom.json` als Classpath-Ressource im JAR.
* [ ] Das npm-Script `generate-licenses` erzeugt `src/assets/frontend-licenses.json` mit allen direkten und transitiven Abhängigkeiten.
* [ ] `GET /api/lizenzen` liefert HTTP 200 mit einer nicht-leeren JSON-Liste von Backend-Library-Einträgen.
* [ ] Jeder Eintrag enthält mindestens `name`, `version`, `license` und `hashes` (Liste, kann leer sein).
* [ ] Backend-Einträge enthalten mindestens einen SHA-256-Hash (aus dem Maven-Artefakt).
* [ ] Frontend-Einträge enthalten den npm-Integrity-Hash (SHA-512) aus `package-lock.json`.
* [ ] In der Tabelle wird der Hash auf 12 Zeichen gekürzt; der vollständige Wert ist per Tooltip sichtbar.
* [ ] Einträge ohne Hash zeigen `–` in der Hash-Spalte.
* [ ] Die Seite `/lizenzen` ist über das Hamburger-Menü erreichbar.
* [ ] Die Seite zeigt zwei separate Abschnitte (Backend / Frontend) mit je einer Tabelle.
* [ ] Tabellen sind alphabetisch nach Library-Name sortiert.
* [ ] Das Suchfeld filtert die Tabellen in Echtzeit nach Name oder Lizenz (case-insensitive).
* [ ] Bei leerem Suchergebnis wird eine Meldung „Keine Ergebnisse" angezeigt.
* [ ] Bei Ladefehler des Backend-Endpoints wird eine Fehlermeldung (`.zev-message--error`) angezeigt.
* [ ] Alle sichtbaren Texte sind übersetzt (Deutsch + Englisch) und kommen aus dem `TranslationService`.
* [ ] Die Seite ist für nicht authentifizierte Benutzer nicht zugänglich.

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* `GET /api/lizenzen` muss unter 500 ms antworten (Daten sind statisch, können gecacht werden).
* Die `frontend-licenses.json` wird vom Angular-Asset-System gecacht (Browser-Cache).

### NFR-2: Sicherheit
* Der Endpoint `GET /api/lizenzen` erfordert die Rolle `zev` (mindestens eingeloggter Benutzer).
* Die Route `/lizenzen` ist mit `AuthGuard` und den Rollen `['zev', 'zev_admin']` geschützt.

### NFR-3: Kompatibilität
* Die SBOM wird im CycloneDX v1.4+ JSON-Format generiert (Industriestandard).
* Die `frontend-licenses.json` ist ein flaches JSON-Objekt (kompatibel mit `license-checker-rseidelsohn`-Output).
* Keine Datenbankänderungen erforderlich; die Daten sind rein build-zeitlich generiert.

## 5. Edge Cases & Fehlerbehandlung

* **Backend-SBOM nicht verfügbar:** Falls `backend-bom.json` im Classpath fehlt (falsch konfigurierter Build), gibt der Endpoint HTTP 503 mit einer aussagekräftigen Fehlermeldung zurück.
* **Frontend-Licenses-Datei fehlt:** Falls `assets/frontend-licenses.json` nicht gefunden wird, zeigt der Frontend-Abschnitt eine Fehlermeldung an, statt die Seite komplett zu blockieren.
* **Leere Komponentenliste:** Falls die SBOM leer ist (unwahrscheinlich, aber möglich), zeigt die Tabelle den Leerstate an: „Keine Libraries gefunden."
* **Unbekannte Lizenz:** Falls eine Library keine Lizenzangabe enthält, wird `Unbekannt` / `Unknown` angezeigt.
* **Fehlender Hash:** Falls für eine Library kein Hash in der SBOM enthalten ist (z.B. lokale Artefakte, Snapshots), bleibt `hashes` eine leere Liste; die UI zeigt `–` in der Hash-Spalte.
* **Netzwerkfehler:** Frontend fängt HTTP-Fehler vom Backend-Endpoint ab und zeigt eine `.zev-message--error`-Meldung (kein Crash der Seite).

## 6. Abhängigkeiten & betroffene Funktionalität

* **Voraussetzungen:**
  * Maven 3.6+ und Java 21 (bereits vorhanden).
  * Node.js 20.19.0+ und npm (bereits vorhanden).
  * Neue npm devDependency: `license-checker-rseidelsohn` (oder `license-report`).
  * Neues Maven-Plugin: `org.cyclonedx:cyclonedx-maven-plugin`.

* **Betroffener Code:**
  * `backend-service/pom.xml` — Plugin-Konfiguration hinzufügen.
  * `frontend-service/package.json` — neues Script `generate-licenses`, neue devDependency.
  * `frontend-service/src/app/app.routes.ts` — neue Route `/lizenzen`.
  * `frontend-service/src/app/components/navigation/navigation.component.html` — neuer Menüpunkt.
  * Neue Flyway-Migration für Übersetzungskeys (keine Tabellenänderung, nur `INSERT INTO zev.translation`).

* **Neuer Code:**
  * `backend-service/.../controller/LizenzenController.java`
  * `backend-service/.../dto/LizenzenDTO.java`
  * `backend-service/.../service/LizenzenService.java` (liest und parst `backend-bom.json`)
  * `frontend-service/src/app/services/lizenzen.service.ts`
  * `frontend-service/src/app/components/lizenzen/lizenzen.component.ts/html/css`

* **Datenmigration:** Keine Datenmigration; jedoch neue Flyway-Migration für Übersetzungskeys.

## 7. Abgrenzung / Out of Scope

* **CVE-Scanning / Vulnerability-Management:** Die SBOM wird generiert, aber keine automatische Auswertung bekannter Schwachstellen (CVEs) umgesetzt. Das bleibt externen Tools (z.B. OWASP Dependency-Check, Dependabot) überlassen.
* **SBOM-Export:** Die Seite bietet keinen Download der SBOM-Datei an (nur Anzeige).
* **Automatische SBOM-Updates im laufenden Betrieb:** Die SBOM wird bei jedem Build aktualisiert, nicht zur Laufzeit.
* **SBOM für den Admin-Service:** Nur Backend-Service und Frontend-Service werden berücksichtigt. Der Admin-Service hat keine eigenen Geschäfts-Abhängigkeiten.
* **Transitive Abhängigkeiten im Frontend anzeigen:** Nur direkte `dependencies` und `devDependencies` werden angezeigt (nicht tiefe Transitive), um die Liste übersichtlich zu halten.

## 8. Offene Fragen

* **Welches npm-Tool für Frontend-Lizenzen?** `license-checker-rseidelsohn` ist etabliert; alternativ `license-report`. Empfehlung: `license-checker-rseidelsohn`, da es JSON-Output unterstützt. --> setze Empfehlung um
* **Backend-Endpoint vs. statisches Asset auch für Backend?** Die Backend-Lizenzdaten könnten ebenfalls als statisches Angular-Asset eingebettet werden (einfacher, kein API-Call). Alternative: Backend-Endpoint mit Caching. Empfehlung: Backend-Endpoint (flexibler, konsistenter mit der übrigen API-Nutzung). --> Backend-Endpoint mit Caching
* **Soll `devDependencies` im Frontend-Output enthalten sein?** Dev-Dependencies sind im Build nicht enthalten, aber könnten für Transparenz trotzdem aufgeführt werden. Standard: nur `dependencies` (Produktiv-Abhängigkeiten). --> nur `dependencies`
* **Caching des Backend-Endpoints?** Da die Daten statisch sind, könnte ein Caffeine-Cache (wie bei Statistik) sinnvoll sein. Zu klären: Cache-Invalidierung bei Neudeployment. --> Cache verwenden
