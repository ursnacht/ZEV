# Builder-Migration `@angular-devkit/build-angular` → `@angular/build`

## Zusammenfassung

Das Frontend baut mit `@angular-devkit/build-angular`, testet aber schon mit `@angular/build`.
Diese Mischung erzeugt bei jedem Testlauf eine Warnung und ist der grösste Einzelposten des
späteren Angular-22-Upgrades. Die Migration wird deshalb **innerhalb von Angular 21**
vorgezogen, wo sie isoliert und ohne gleichzeitigen Framework-Wechsel überprüfbar ist.

Der Angular-22-Upgrade bleibt **bewusst zurückgestellt** — Begründung in Abschnitt
„Abgrenzung".

## Ausgangslage

`frontend-service/angular.json` mischt zwei Builder-Pakete:

| Target | Builder | Paket |
|---|---|---|
| `build` | `@angular-devkit/build-angular:application` | alt |
| `serve` | `@angular-devkit/build-angular:dev-server` | alt |
| `test` | `@angular/build:unit-test` | neu |

Sichtbare Folge bei jedem `npm test`:

> The 'buildTarget' is configured to use '@angular-devkit/build-angular:application', which is
> not supported. The 'unit-test' builder is designed to work with '@angular/build:application'
> or '@angular/build:ng-packagr'. Unexpected behavior or build failures may occur.

**Der Aufwand ist klein, weil das Zielpaket schon da ist:** `@angular/build@21.2.21` liegt
bereits in `node_modules` (transitive Abhängigkeit von `@angular-devkit/build-angular@21.2.21`)
und wird vom Test-Target aktiv benutzt. Es handelt sich also um einen Austausch der
Builder-Namen, nicht um die Einführung einer neuen Toolchain. Beide Pakete stammen aus
demselben Release-Zug und tragen dieselbe Version.

## Betroffene Komponenten

| Datei | Änderung |
|---|---|
| `frontend-service/angular.json` | Builder von `build` und `serve` umstellen |
| `frontend-service/package.json` | devDependency `@angular-devkit/build-angular` → `@angular/build` |
| `frontend-service/package-lock.json` | Ergebnis von `npm install` |

Nicht betroffen: Anwendungscode, `tsconfig*.json`, die Vitest-Infrastruktur
(`src/testing/spy.ts`, `src/testing/fake-async.ts`, `src/test-setup.ts`), das Design-System
(hat keine Angular-Abhängigkeit) und `frontend-service/Dockerfile` (ruft `npm run build`, also
unverändert `ng build`).

## Umsetzungsreihenfolge (Phasen)

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. Builder umstellen | In `angular.json`: `@angular-devkit/build-angular:application` → `@angular/build:application`, `@angular-devkit/build-angular:dev-server` → `@angular/build:dev-server`. Optionen unverändert übernommen. |
| [x] | 2. Abhängigkeit tauschen | `@angular-devkit/build-angular` → `@angular/build` (`^21.2.18`), `npm install`. **423 Pakete entfernt** — die Karma-/Webpack-Kette, die nur das alte Paket mitbrachte. `found 0 vulnerabilities`. |
| [x] | 3. Entwicklungs-Build | `npx ng build --configuration=development` → fehlerfrei, 3.07 MB. |
| [x] | 4. Produktions-Build | `npm run build` → fehlerfrei, 921 kB (222 kB übertragen), Budgets eingehalten, `outputHashing` aktiv. |
| [x] | 5. Unit-Tests | `npm test` → 45 Dateien, **1146 Tests grün**; die Builder-Warnung erscheint nicht mehr (`grep -c` → 0). |
| [ ] | 6. E2E-Tests | Offen — setzt einen Rebuild des Frontend-Images voraus (siehe unten). |

### Phase 6: was noch aussteht

Die E2E-Suite läuft gegen den **Container-Stack**, nicht gegen das lokal erzeugte `dist/`. Das
Frontend-Image stammt noch aus einem Build mit dem alten Builder — ein E2E-Lauf jetzt würde
über die Migration nichts aussagen. Zuerst ist das Image neu zu bauen:

```bash
docker compose up -d --build frontend-service
E2E_BASE_URL=https://localhost:8443 npx playwright test
```

`E2E_BASE_URL` muss **in der Shell** gesetzt sein: `playwright.config.ts` liest
`process.env`, nicht die `.env` des Repositories (die wertet nur Docker Compose aus). Ohne die
Variable greift der Default `http://localhost:8000`, der seit der HTTPS-Umstellung nur noch
eine 301-Weiterleitung ist — die Tests liefen dann über einen zusätzlichen Redirect je Aufruf.

## Validierungen

* **Build-Optionen:** `outputPath` (Objektform), `allowedCommonJsDependencies`, `budgets`,
  `outputHashing`, `extractLicenses` und die konfigurationsabhängigen `styles` müssen nach der
  Umstellung dieselbe Wirkung haben. Phase 3 und 4 prüfen beide Konfigurationen getrennt, weil
  `development` und `production` unterschiedliche `styles`-Pfade verwenden
  (`../design-system/dist/index.css` gegenüber `target/design-system-extracted/...`).
* **Dev-Server:** Die `ci`-Konfiguration setzt zusätzlich `port: 4200`. Nur relevant, wenn
  gegen `ng serve` getestet wird (`E2E_BASE_URL=http://localhost:4200`).
* **Erfolgskriterium:** Der Testlauf gibt die oben zitierte Warnung nicht mehr aus.

## Abgrenzung

**Angular 22 ist nicht Teil dieses Plans.** Gründe:

* Angular 22 verlangt `typescript >=6.0 <6.1` (Peer-Dependency von `@angular/compiler-cli`).
  Das Projekt liegt auf `~5.9.3`; der Upgrade zöge einen TypeScript-Major mit — in ein enges
  Fenster, während TypeScript bereits bei 7.0.2 steht.
* Angular 21 ist LTS (`v21-lts` → 21.2.21). Es besteht kein Zeitdruck.
* Die ursprüngliche Sperre besteht **nicht mehr**: `keycloak-angular@22` lässt
  `keycloak-js: ^18 || … || ^25 || ^26` zu. Der Upgrade erzwingt keycloak-js 26 also nicht,
  und die Secure-Context-Anforderung von keycloak-js 26 ist damit vom Framework-Upgrade
  entkoppelt.
* **keycloak-js bleibt trotzdem auf 25**, bis das NAS auf HTTPS läuft: `Specs/HTTPS.md`
  Abschnitt 6 ist vollständig offen, `nafam.zev:8000` ist über HTTP kein Secure Context.

## Offene Punkte / Annahmen

* **Annahme:** `@angular/build:application` und `@angular-devkit/build-angular:application`
  teilen sich in Version 21.2.21 das Optionsschema. Das ist die dokumentierte Beziehung
  (letzteres delegiert an ersteres), wird aber durch Phase 3–6 empirisch abgesichert statt
  vorausgesetzt.
* **Nebenbefund, ausserhalb des Scopes:** `angular.json` führt unter `cli.schematicCollections`
  die Sammlung `@cypress/schematic`, obwohl Cypress in keiner Abhängigkeit vorkommt. Das
  lässt `ng generate` fehlschlagen. Nicht Teil dieser Migration — separat zu bereinigen.
