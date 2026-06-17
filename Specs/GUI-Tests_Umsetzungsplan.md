# Umsetzungsplan: GUI-Tests (Karma/Jasmine → Vitest)

## Zusammenfassung

Die Frontend-Unit-/Component-Tests werden von Jasmine + Karma auf **Vitest** umgestellt, ausgeführt über den Angular-Builder `@angular/build:unit-test` (Runner `vitest`, Umgebung `jsdom`, headless). Karma und Jasmine werden vollständig entfernt, alle **32 `*.spec.ts`-Dateien** werden 1:1 auf Vitest-Idiome migriert (keine inhaltlichen Test-Änderungen). E2E (Playwright) und der Produktions-Build bleiben unangetastet.

---

## Betroffene Komponenten

| Typ | Datei | Änderungsart |
|-----|-------|--------------|
| Build-Config | `frontend-service/angular.json` (`test`-Target) | Änderung (Builder + Optionen) |
| Dependencies | `frontend-service/package.json` (devDependencies + scripts) | Änderung |
| TS-Config | `frontend-service/tsconfig.spec.json` (`types`, `include`) | Änderung |
| Karma-Config | `frontend-service/karma.conf.js` | **Löschen** |
| Karma-Bootstrap | `frontend-service/src/test.ts` | **Löschen** |
| Vitest-Setup | `frontend-service/src/test-setup.ts` | Neu |
| Spy-Helper | `frontend-service/src/testing/spy.ts` | Neu (Ersatz für `jasmine.createSpyObj`) |
| Tests (alle) | `frontend-service/src/**/*.spec.ts` (32 Dateien) | Änderung (API-Migration) |
| Doku | `CLAUDE.md` (Testing-Abschnitt) | Änderung |
| Doku | `Specs/generell.md` (falls Test-Framework genannt) | Prüfen/Änderung |
| Doku | `MEMORY.md` (Frontend-Tests-Hinweis) | Prüfen/Änderung |

> **Multi-Tenancy / `org_id`:** Nicht anwendbar – reine Test-Infrastruktur, kein Laufzeit-/Produktionscode, keine DB-Entities, keine Endpunkte, keine Rollenänderung.

---

## Phasen-Tabelle

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. Dependencies | `karma*`/`jasmine*`/`@types/jasmine` entfernt; `vitest@4.1.9`, `jsdom@29`, `@vitest/coverage-v8@4` ergänzt |
| [x] | 2. angular.json `test`-Target | Builder `@angular-devkit/build-angular:karma` → **`@angular/build:unit-test`**; `runner: "vitest"`, `setupFiles`, `tsConfig`. `buildTarget` weggelassen (Default `build:development`); jsdom ist Vitest-Default ohne `browsers` |
| [x] | 3. tsconfig.spec.json | `types: ["jasmine"]` → `["vitest/globals"]`; `include` um `src/test-setup.ts` ergänzt |
| [x] | 4. Test-Setup & Bootstrap | `src/test-setup.ts` neu (jsdom-Shims; TestBed-/Zone-Init macht der Builder automatisch); `karma.conf.js` und `src/test.ts` gelöscht |
| [x] | 5. Spy-Helper | `src/testing/spy.ts` mit `createSpyObj()` + Typ `SpyObj<T>` auf `vi.fn()`-Basis |
| [x] | 6a. Migration: Services (11) | `services/*.spec.ts` migriert (HttpClientTestingModule bleibt) |
| [x] | 6b. Migration: Pipes/Directives/Utils (4) | `pipes/*`, `directives/*`, `utils/*` migriert (column-resize: `waitForAsync`→`async/await`, `done`→Promise) |
| [x] | 6c. Migration: Komponenten – einfach (14) | Restliche `components/*.spec.ts` migriert |
| [x] | 6d. Migration: Komponenten – jsdom-kritisch (3) | `messwerte-chart` (lief ohne Canvas-Mock), `rechnungen` + `statistik` migriert |
| [x] | 7. jsdom-Edge-Cases | `URL.createObjectURL`/`revokeObjectURL` + `DataTransfer`/`DragEvent`-Shims in `src/test-setup.ts`; `jasmine.clock`→`vi.useFakeTimers`/`setSystemTime`. Canvas `getContext` nicht nötig |
| [x] | 8. npm-Scripts | `test` (single-run), `test:watch`, `test:coverage` ergänzt |
| [x] | 9. Doku | `CLAUDE.md` (Testing + Befehle), `MEMORY.md` aktualisiert. `generell.md` nennt kein Test-Framework → keine Änderung |
| [x] | 10. Verifikation | 727 Tests grün (32 Dateien), `npm test` Exit 0 single-run, Coverage → `coverage/frontend-service`, `ng build` dev+prod OK, Playwright unberührt, Verifikations-Grep leer |

### Wichtige Abweichungen / Erkenntnisse aus der Umsetzung
* **Builder-Paket:** Der `unit-test`-Builder liegt in **`@angular/build`** (nicht `@angular-devkit/build-angular`). Korrekt: `@angular/build:unit-test`. Peer: `vitest ^4`.
* **TestBed/Zone-Init automatisch:** Der Builder initialisiert TestBed + `zone.js/testing` selbst → `src/test-setup.ts` dient nur jsdom-Shims; `globals: true` und jsdom sind Builder-Defaults (keine vitest-Imports in Specs nötig).
* **`fakeAsync`/`tick`/`waitForAsync` funktionieren NICHT** unter Vitest: zone.js (0.15) patcht den Vitest-Runner nicht → „Expected to be running in 'ProxyZone'". Lösung: `fakeAsync`/`tick`-Shim auf Vitest-Faketimer-Basis (`src/testing/fake-async.ts`), `waitForAsync(()=>{…})` → `async ()=>{ await … }`. (Spec-Edge-Case #2 hatte dies vorgesehen.)
* **`vi.spyOn` ruft per Default durch** (anders als Jasmines `spyOn`): Delegations-Spies (`onEdit`/`onDelete`/…) brauchen `.mockImplementation(() => {})`.
* **`done`-Callbacks** (column-resize, translation.service) → Promise-basiert umgestellt (Vitest hat keinen `done`-Parameter).

---

## Detailbeschreibung der Phasen

### Phase 1: Dependencies
**Entfernen** (`devDependencies`): `karma`, `karma-chrome-launcher`, `karma-coverage`, `karma-jasmine`, `karma-jasmine-html-reporter`, `jasmine-core`, `@types/jasmine`.
**Ergänzen:** `vitest`, `jsdom`, `@vitest/coverage-v8` (Coverage-Provider). Versionen passend zur vom `@angular/build@^21` `unit-test`-Builder (Peer: vitest ^4.0.8) erwarteten Variante wählen (Peer-Vorgaben des Builders beachten).

### Phase 2: angular.json `test`-Target
Aktuell:
```json
"test": {
  "builder": "@angular-devkit/build-angular:karma",
  "options": {
    "polyfills": ["zone.js", "zone.js/testing"],
    "tsConfig": "tsconfig.spec.json",
    "karmaConfig": "karma.conf.js",
    "styles": ["src/styles.css"],
    "scripts": []
  }
}
```
Ziel (Optionsnamen gemäß Schema des installierten `unit-test`-Builders verifizieren):
```json
"test": {
  "builder": "@angular/build:unit-test",
  "options": {
    "tsConfig": "tsconfig.spec.json",
    "buildTarget": "frontend-service::development",
    "runner": "vitest",
    "setupFiles": ["src/test-setup.ts"]
  }
}
```
* `karmaConfig` entfällt. `environment`/`browsers` so setzen, dass **jsdom** (kein echter Browser) genutzt wird.
* Coverage-Output nach `coverage/frontend-service` (Text-Summary + HTML), als Builder-Option oder via Vitest-Config.

### Phase 3: tsconfig.spec.json
* `types: ["jasmine"]` → Vitest-Typen (`["vitest/globals"]`), damit globale `describe/it/expect/vi` typisiert sind.
* `include` weiterhin `["src/**/*.spec.ts", "src/**/*.d.ts"]`.

### Phase 4: Test-Setup & Bootstrap
* Neue `src/test-setup.ts`: importiert `zone.js` + `zone.js/testing` und initialisiert – falls vom `unit-test`-Builder nicht automatisch übernommen – die Angular-TestBed-Umgebung (`getTestBed().initTestEnvironment(BrowserDynamicTestingModule, platformBrowserDynamicTesting())`). Beim Builder-Setup prüfen, ob die TestBed-Initialisierung bereits durch den Builder erfolgt (dann nur Zone-Import nötig).
* `karma.conf.js` löschen.
* `src/test.ts` (Karma-`require.context`-Bootstrap) löschen – Vitest entdeckt Specs selbst.

### Phase 5: Spy-Helper (`src/testing/spy.ts`)
Typisierter Ersatz für `jasmine.createSpyObj`, um Churn in 31 Dateien gering zu halten und das AK „keine Jasmine-API" zu erfüllen:
```ts
import { vi } from 'vitest';
export function createSpyObj<T>(_name: string, methods: (keyof T)[]): { [K in keyof T]: ReturnType<typeof vi.fn> } {
  const obj = {} as any;
  for (const m of methods) obj[m] = vi.fn();
  return obj;
}
```
In den Spec-Dateien wird `jasmine.createSpyObj('X', [...])` → `createSpyObj<X>('X', [...])` (mit Import). `.and.returnValue(...)` am Spy → `.mockReturnValue(...)`.

### Phasen 6a–6d: Migration der 32 Spec-Dateien
Mechanische API-Migration gemäß Mapping-Tabelle (siehe „Validierungen / API-Mapping"). Aufteilung in Batches für einzeln prüfbare, grüne Zwischenstände:
* **6a Services (11):** `auth, debitor, einheit, einstellungen, lizenzen, messwerte, mieter, rechnung, statistik, tarif, translation`. `HttpClientTestingModule`/`HttpTestingController` bleiben.
* **6b Pipes/Directives/Utils (4):** `swiss-date.pipe`, `translate.pipe`, `column-resize.directive`, `date-utils`.
* **6c Komponenten – einfach (14):** alle übrigen `components/*` außer den drei jsdom-kritischen.
* **6d Komponenten – jsdom-kritisch (3):** `messwerte-chart`, `rechnungen`, `statistik` (siehe Phase 7).

Nach jedem Batch `npm test` ausführen → Batch muss grün sein, bevor der nächste beginnt.

### Phase 7: jsdom-Edge-Cases
* **`window.URL.createObjectURL` / `revokeObjectURL`** (in `rechnung.service.spec.ts`, 3 Stellen): jsdom implementiert diese nicht. Im `test-setup.ts` oder lokal vor dem `vi.spyOn` als Stub definieren (`URL.createObjectURL = () => 'blob:...'`), dann `vi.spyOn(window.URL, 'createObjectURL').mockReturnValue(...)`.
* **Chart.js / Canvas** (`messwerte-chart.component.spec.ts`): `HTMLCanvasElement.prototype.getContext` ist in jsdom nicht implementiert → Chart-Erzeugung mocken (Chart.js-Instanz stubben) oder `getContext` im Setup neutralisieren, sodass der Test das Komponentenverhalten ohne echtes Rendering prüft.
* **Timer/Clock** (`jasmine.clock`, 26 Stellen): `jasmine.clock().install()/mockDate()/uninstall()` → `vi.useFakeTimers()` / `vi.setSystemTime(new Date(...))` / `vi.useRealTimers()`. Zusammenspiel mit Angulars `fakeAsync`/`tick` beibehalten (diese bleiben aus `@angular/core/testing`).

### Phase 8: npm-Scripts
```json
"test": "ng test",                 // single-run, CI-tauglich (Exit-Code ≠ 0 bei Fehler)
"test:watch": "ng test --watch",
"test:coverage": "ng test --coverage"
```
(Exakte Flags gemäß `unit-test`-Builder-Schema; ggf. `--no-watch` für single-run absichern.)

### Phase 9: Doku
* `CLAUDE.md`: Testing-Abschnitt „Jasmine 5.13.0 / Karma" → „Vitest (via `@angular/build:unit-test`, jsdom)"; `npm test`-Beschreibung anpassen.
* `Specs/generell.md`: Prüfen, ob Test-Framework genannt ist → aktualisieren.
* `MEMORY.md`: Hinweis „Frontend Tests: HttpClientTestingModule …" um Vitest-Kontext ergänzen (HttpClientTestingModule bleibt).

### Phase 10: Verifikation
Siehe „Verifikationskriterien".

---

## Validierungen

> Kein Formular-/Backend-Validierungs-Kontext (reine Test-Infra). Stattdessen: **API-Mapping** (verbindliche Ersetzungsregeln) und **Verifikationskriterien**.

### API-Mapping (Jasmine → Vitest) – verbindlich
| Jasmine | Vitest | Vorkommen (ca.) |
|---------|--------|------|
| `jasmine.createSpyObj('X', [...])` | `createSpyObj<X>('X', [...])` (Helper) | 31 |
| `spy.and.returnValue(v)` | `spy.mockReturnValue(v)` | 131 |
| `spy.and.callFake(fn)` | `spy.mockImplementation(fn)` | 12 |
| `spyOn(obj, 'm')` | `vi.spyOn(obj, 'm')` | 54 |
| `spyOn(obj, 'm').and.returnValue(v)` | `vi.spyOn(obj, 'm').mockReturnValue(v)` | (Teil obiger) |
| `expect(x).toBeTrue()` | `expect(x).toBe(true)` | 75 |
| `expect(x).toBeFalse()` | `expect(x).toBe(false)` | 120 |
| `jasmine.objectContaining(o)` | `expect.objectContaining(o)` | 6 |
| `jasmine.any(T)` | `expect.any(T)` | 1 |
| `jasmine.clock().install()` | `vi.useFakeTimers()` | 26 (Gruppe) |
| `jasmine.clock().mockDate(d)` | `vi.setSystemTime(d)` | |
| `jasmine.clock().uninstall()` | `vi.useRealTimers()` | |
| `fakeAsync` / `tick()` | **unverändert** (`@angular/core/testing`) | 38 / 30 |
| `HttpClientTestingModule` / `HttpTestingController` | **unverändert** | 24 / 36 |

### Verifikationskriterien (aus Spec, Abschnitt 3)
1. `npm test` startet Vitest (nicht Karma), headless in jsdom, ohne echten Browser.
2. Alle zuvor grünen Tests sind grün; keine inhaltlichen Regressionen (gleiche Test-Anzahl/Zuordnung).
3. Fehlschlagender Test → Exit-Code ≠ 0 (CI bricht ab).
4. Coverage-Report erzeugbar, landet unter `coverage/frontend-service` (Text-Summary + HTML).
5. Kein `karma.conf.js`, keine `karma-*`/`jasmine-*`-Dependency in `package.json`.
6. `@types/jasmine` entfernt; `tsconfig.spec.json` nutzt Vitest-Typen.
7. Keine Spec-Datei nutzt mehr Jasmine-APIs (`jasmine.*`, `*.and.*`, `toBeTrue/toBeFalse`) – per Grep verifiziert.
8. `ng build` (dev & prod) und Playwright-E2E unverändert lauffähig.
9. Läuft unter Node ≥ 20.19.0 ohne Chrome-Installation.

**Verifikations-Grep (muss leer sein):**
```bash
grep -rE "jasmine\.|\.and\.(returnValue|callFake|throwError|returnValues)|toBeTrue|toBeFalse" \
  frontend-service/src --include="*.spec.ts"
```

---

## Offene Punkte / Annahmen

Aus Spec-Abschnitt 8 (alle mit eingebetteter Annahme – als Annahme übernommen):
1. **Builder-Status:** `@angular/build:unit-test` ist in Angular 21 als experimentell markiert → **akzeptiert/gewählt**. Exakte Options-Namen (`runner`, `buildTarget`, `setupFiles`, `environment`, Coverage) werden gegen das Schema der installierten `@angular-devkit/build-angular`-Version verifiziert.
2. **Coverage-Provider:** `v8` (`@vitest/coverage-v8`) – schnell, ausreichend.
3. **Zusatz-Scripts:** `test:watch` und `test:coverage` werden ergänzt; `test` bleibt single-run.
4. **CI-Pipeline:** Im Repo existiert **keine** (`.github/workflows` nicht vorhanden) → Akzeptanzkriterium „CI ohne Chrome" ist aktuell gegenstandslos; bei späterer CI-Einführung direkt mit Vitest aufsetzen. Kein Handlungsbedarf in diesem Schritt.
5. **Spy-Helper:** Gemeinsamer `createSpyObj`-Helper (`src/testing/spy.ts`) wird eingeführt, um Wiederholung in den 31 Dateien zu reduzieren und das „keine Jasmine-API"-Kriterium sauber zu erfüllen.

Zusätzliche, beim Recherchieren identifizierte Annahmen:
6. **TestBed-Initialisierung:** Annahme, dass der `unit-test`-Builder die Angular-Testumgebung bereitstellt; falls nicht, übernimmt `src/test-setup.ts` die explizite `initTestEnvironment`-Initialisierung (wie bisher in `src/test.ts`).
7. **jsdom-Mocks:** `messwerte-chart` (Canvas/Chart.js) und `rechnung.service`/`statistik` (Blob-Download, `URL.createObjectURL`) benötigen explizite Mocks unter jsdom; Testszenarien bleiben inhaltlich gleich, nur die Umgebungs-Stubs kommen hinzu.
8. **`fakeAsync`/`tick` und `HttpClientTestingModule`** bleiben bewusst erhalten (Out-of-Scope der Migration laut Spec).
