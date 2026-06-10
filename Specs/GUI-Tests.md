# GUI-Tests – Karma durch Vitest ersetzen

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Die Frontend-Unit-/Component-Tests (Jasmine + Karma) werden auf **Vitest** umgestellt, ausgeführt über den offiziellen Angular-Builder `@angular-devkit/build-angular:unit-test`. Karma und Jasmine werden vollständig entfernt.
* **Warum machen wir das:**
  * Karma ist seit längerem deprecated; Vitest ist der zukünftige Standard im Angular-Toolchain.
  * Deutlich schnellere Testausführung (esbuild/Vite, keine echte Browser-Instanz), passend zum bereits eingeführten Application-Builder.
  * Einheitliches, modernes Tooling und einfachere CI-Integration (headless via jsdom).
* **Aktueller Stand:**
  * `ng test` nutzt den Builder `@angular-devkit/build-angular:karma` mit `karma.conf.js`, Chrome-Launcher und `karma-coverage`.
  * Test-Framework ist Jasmine (`describe/it/expect`, `spyOn`, `jasmine.createSpyObj`, Matcher `toBeTrue/toBeFalse`, `.and.returnValue`).
  * Es existieren **31 `*.spec.ts`-Dateien** (Services, Komponenten, Pipes, Directives, Utils) mit ca. **489 Jasmine-spezifischen API-Stellen**.
  * `tsconfig.spec.json` referenziert `types: ["jasmine"]`.
  * E2E-Tests laufen unabhängig über Playwright (`tests/`) und sind **nicht** betroffen.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Test-Runner auf Vitest umstellen
1. Der `test`-Target in `angular.json` wird vom Builder `@angular-devkit/build-angular:karma` auf `@angular-devkit/build-angular:unit-test` (Vitest-Runner) umgestellt.
2. Als Test-Umgebung wird **jsdom** verwendet (headless, kein echter Browser).
3. `npm test` (`ng test`) führt die Vitest-Suite einmalig aus und beendet sich mit korrektem Exit-Code (0 = grün, ≠0 = rot) – CI-tauglich.
4. Ein Watch-Modus für die lokale Entwicklung steht zur Verfügung (z. B. `npm run test:watch` bzw. `ng test --watch`).

### FR-2: Vitest-Konfiguration
* Test-Setup-Datei initialisiert die Angular-TestBed-Umgebung (Zone.js-Testing-Initialisierung wie bisher).
* Globale Test-APIs (`describe`, `it`, `expect`, `beforeEach`, `vi`) sind verfügbar (Vitest-`globals`), damit die Spec-Dateien ohne umfangreiche Imports auskommen.
* **Coverage** wird unterstützt (Ersatz für `karma-coverage`), Ausgabe nach `coverage/frontend-service` mit Text-Summary und HTML-Report.
* `tsconfig.spec.json`: `types: ["jasmine"]` wird durch die passenden Vitest-Typen ersetzt; `include` umfasst weiterhin alle `*.spec.ts`.

### FR-3: Migration der bestehenden Spec-Dateien (Jasmine → Vitest)
Alle 31 `*.spec.ts`-Dateien werden auf Vitest-Idiome migriert, ohne den fachlichen Testinhalt (Assertions/Szenarien) zu verändern:
* `spyOn(obj, 'm').and.returnValue(x)` → `vi.spyOn(obj, 'm').mockReturnValue(x)`
* `jasmine.createSpyObj('X', ['a', 'b'])` → Objekt mit `vi.fn()`-Methoden (ggf. über einen kleinen Test-Helper)
* `spy.and.callFake/throwError/returnValues` → entsprechende `vi.fn()`-Mock-Implementierungen
* Jasmine-Matcher `toBeTrue()` / `toBeFalse()` → `toBe(true)` / `toBe(false)`
* `jasmine.any(...)`, `jasmine.objectContaining(...)` → `expect.any(...)`, `expect.objectContaining(...)`
* Asynchrone Tests (`fakeAsync`/`tick`, `async/await`) bleiben über Angulars TestBed erhalten.
* `HttpClientTestingModule` / `HttpTestingController` werden weiterhin verwendet (keine Migration auf `provideHttpClientTesting` in diesem Schritt).

### FR-4: Entfernung von Karma & Jasmine
* `karma.conf.js` wird gelöscht.
* Dev-Dependencies werden entfernt: `karma`, `karma-chrome-launcher`, `karma-coverage`, `karma-jasmine`, `karma-jasmine-html-reporter`, `jasmine-core`, `@types/jasmine`.
* Erforderliche Vitest-/Test-Dependencies werden ergänzt (z. B. `vitest`, jsdom, Coverage-Provider) gemäss der vom Angular-`unit-test`-Builder vorgegebenen Variante.
* `karma.conf.js` aus `karma.conf.js`-Referenz im `test`-Target von `angular.json` entfällt.

### FR-5: npm-Scripts & Dokumentation
* `npm test` bleibt als Haupt-Einstieg bestehen (jetzt Vitest, single-run).
* Optional: `test:watch` und `test:coverage` ergänzen.
* Doku-Verweise auf das Test-Framework anpassen: `CLAUDE.md` (Testing-Abschnitt nennt „Jasmine 5.13.0 / Karma"), ggf. `Specs/generell.md` und `MEMORY.md`-Hinweis zu Frontend-Tests.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)
* [ ] `npm test` startet Vitest (nicht Karma) und läuft headless in jsdom ohne echten Browser durch.
* [ ] Alle bisher grünen Tests sind nach der Migration ebenfalls grün (gleiche Anzahl bzw. nachvollziehbar zugeordnete Tests, keine inhaltlichen Regressionen).
* [ ] `npm test` liefert bei einem fehlschlagenden Test einen Exit-Code ≠ 0 (CI bricht ab).
* [ ] Ein Coverage-Report kann erzeugt werden und landet unter `coverage/frontend-service`.
* [ ] Es existiert kein `karma.conf.js` mehr und keine `karma-*`/`jasmine-*`-Dependency in `package.json`.
* [ ] `@types/jasmine` ist entfernt; `tsconfig.spec.json` nutzt die Vitest-Typen.
* [ ] Keine Spec-Datei verwendet mehr Jasmine-APIs (`jasmine.*`, `spyOn(...).and.*`, `toBeTrue/toBeFalse`).
* [ ] `ng build` (dev & prod) und die Playwright-E2E-Tests sind unverändert lauffähig.
* [ ] `npm test` läuft unter Node ≥ 20.19.0 (siehe `engines`) auch in CI (ohne Chrome-Installation).

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Die Gesamtlaufzeit der Unit-Test-Suite soll gegenüber Karma nicht langsamer sein; Zielsetzung ist eine spürbare Beschleunigung (kein Browser-Start, paralleles Ausführen via Vitest).

### NFR-2: Sicherheit
* **Nicht anwendbar** – reine Test-Infrastruktur ohne Laufzeit-/Produktionscode. Keine Änderung an Rollen (`zev`, `zev_admin`), Authentifizierung oder Endpoints. Test-Dependencies bleiben `devDependencies` und gelangen nicht ins Produktions-Bundle.

### NFR-3: Kompatibilität
* Keine Änderung am Produktions-Build-Output oder am Anwendungsverhalten.
* CI-Pipeline muss ohne Chrome/Chromium-Browser auskommen (jsdom).
* Bleibt mit dem bereits aktiven `application`-Builder und Angular 21 kompatibel.
* E2E-Tests (Playwright) und deren Scripts (`e2e`, `e2e:ci`, `e2e:ui`) bleiben unverändert.

## 5. Edge Cases & Fehlerbehandlung
* **`jasmine.createSpyObj`-Mocks:** Müssen 1:1 auf `vi.fn()`-basierte Mocks abgebildet werden; Rückgabewerte (`.and.returnValue`) und Reset-Verhalten zwischen Tests (`beforeEach`/`vi.clearAllMocks`) prüfen.
* **Asynchrone Tests:** `fakeAsync`/`tick`, Timer und Promises müssen unter Vitest+jsdom dasselbe Verhalten zeigen; Zeit-/Timer-Mocks ggf. auf `vi.useFakeTimers()` umstellen, wo Jasmine-Clock genutzt wurde.
* **Keycloak-/Browser-APIs:** Komponenten, die `Keycloak`, `window`, `localStorage` oder `URL.createObjectURL` nutzen (z. B. Rechnungs-Download, Navigation), müssen unter jsdom korrekt gemockt sein.
* **Leere/asynchrone HTTP-Mocks:** `HttpTestingController.verify()` muss weiterhin offene Requests erkennen; Fehlerpfade (`error`-Responses) bleiben getestet.
* **Globale Matcher fehlen:** Jasmine-spezifische Matcher ohne Vitest-Äquivalent (`toBeTrue/toBeFalse`) führen sonst zu „is not a function" – vollständige Suche/Ersetzung notwendig.
* **CI ohne Browser:** Falls einzelne Tests echtes Browser-Verhalten benötigen, müssen sie als Edge Case identifiziert und entweder angepasst oder (begründet) nach Playwright verschoben werden.

## 6. Abhängigkeiten & betroffene Funktionalität
* **Voraussetzungen:**
  * Angular 21 mit aktivem `application`-Builder (bereits umgesetzt).
  * `@angular-devkit/build-angular` ^21 (enthält den `unit-test`-Builder).
* **Betroffener Code / Dateien:**
  * `frontend-service/angular.json` – `test`-Target (Builder + Optionen).
  * `frontend-service/package.json` – Scripts und Dev-Dependencies.
  * `frontend-service/tsconfig.spec.json` – `types`.
  * `frontend-service/karma.conf.js` – wird gelöscht.
  * Neue Test-Setup-Datei (z. B. `src/test-setup.ts`) für die TestBed-/Zone-Initialisierung.
  * Alle 31 `frontend-service/src/**/*.spec.ts`.
  * Doku: `CLAUDE.md`, ggf. `Specs/generell.md`, `MEMORY.md`.
* **Datenmigration:** Keine (keine Datenbank-/Laufzeitdaten betroffen).

## 7. Abgrenzung / Out of Scope
* **Keine** Migration der Playwright-E2E-Tests – diese bleiben unverändert.
* **Keine** neuen Tests und **keine** Erhöhung der Testabdeckung in diesem Schritt (reine 1:1-Migration).
* **Keine** Änderung am Anwendungscode/-verhalten oder am Produktions-Build.
* **Keine** Migration von `HttpClientTestingModule` auf `provideHttpClientTesting`.
* **Kein** Vitest-Browser-Mode (Chromium) – bewusst jsdom.
* **Keine** Einführung von Mutation-Testing, Snapshot-Strategien o. Ä.

## 8. Offene Fragen
* **Status des Builders:** `@angular-devkit/build-angular:unit-test` ist in Angular 21 noch als experimentell markiert. Akzeptiert für dieses Projekt? (Annahme: ja, gewählt.)
* **Coverage-Provider:** `v8` (schnell) oder `istanbul` (genauer)? (Annahme: Default des Builders/`v8`.)
* **Watch-/Coverage-Scripts:** Sollen zusätzliche npm-Scripts (`test:watch`, `test:coverage`) ergänzt werden, oder reicht `ng test --watch`/`--coverage`? (Annahme: schlanke Zusatz-Scripts werden ergänzt.)
* **CI-Anpassung:** Gibt es eine CI-Pipeline (z. B. GitHub Actions), in der der Chrome-Schritt entfernt und `npm test` angepasst werden muss? (Im Repo aktuell nicht gefunden – bei Bedarf nachziehen.)
* **Test-Helper für Spies:** Soll ein gemeinsamer Helper (Ersatz für `jasmine.createSpyObj`) eingeführt werden, um Wiederholung in den 31 Dateien zu reduzieren?
