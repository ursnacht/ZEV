# Test-Gap-Analyse

Führt eine umfassende Analyse aller fehlenden Tests im Projekt durch.

## Input
* Optional: $ARGUMENTS (z.B. `backend`, `frontend`, `e2e` oder `all` für alles)
* Default: `all`

---

## Unabhängige Ausführung

Dieser Skill arbeitet UNABHÄNGIG vom Kontext der aktuellen Session.

**Analysiere NUR:**
1. Tatsächlich vorhandene Implementierungs-Dateien
2. Tatsächlich vorhandene Test-Dateien
3. Projekt-Konventionen aus CLAUDE.md

**IGNORIERE** jeglichen Kontext aus der vorherigen Konversation.

---

## Vorgehen

### Phase 1: Implementierungen inventarisieren

**Backend (Java):**
```
backend-service/src/main/java/ch/nacht/service/*.java     → Services
backend-service/src/main/java/ch/nacht/controller/*.java  → Controllers
backend-service/src/main/java/ch/nacht/repository/*.java  → Repositories
```

**Frontend (TypeScript):**
```
frontend-service/src/app/components/**/*.component.ts  → Components
frontend-service/src/app/services/*.service.ts         → Services
frontend-service/src/app/pipes/*.pipe.ts               → Pipes
frontend-service/src/app/directives/*.directive.ts     → Directives
frontend-service/src/app/utils/*.ts                    → Utilities
```

**Features (für E2E):**
```
frontend-service/src/app/app.routes.ts  → Alle Routes = Features
```

### Phase 2: Vorhandene Tests inventarisieren

**Backend:**
```
backend-service/src/test/java/ch/nacht/**/*Test.java  → Unit Tests
backend-service/src/test/java/ch/nacht/**/*IT.java    → Integration Tests
```

**Frontend Unit:**
```
frontend-service/src/app/**/*.spec.ts  → Unit Tests (Jasmine/Karma)
```

**E2E:**
```
frontend-service/tests/*.spec.ts  → E2E Tests (Playwright)
```

### Phase 3: Gap-Analyse erstellen

Für jede Implementierung prüfen:
- Existiert ein entsprechender Test?
- Test-Namenskonvention: `XxxService.java` → `XxxServiceTest.java`
- Component-Konvention: `xxx.component.ts` → `xxx.component.spec.ts`

### Phase 4: Bericht erstellen

Ausgabe als Markdown-Tabellen mit:

1. **Backend Services** - Unit Tests (`*Test.java`)
2. **Backend Controllers** - Integration Tests (`*Test.java` mit @WebMvcTest)
3. **Backend Repositories** - Integration Tests (`*IT.java`)
4. **Frontend Components** - Unit Tests (`*.spec.ts`)
5. **Frontend Services** - Unit Tests (`*.spec.ts`)
6. **Frontend Pipes/Directives** - Unit Tests (`*.spec.ts`)
7. **E2E Tests** - Feature-Coverage (`tests/*.spec.ts`)

---

## Ausgabe-Format

```markdown
# Test-Gap-Analyse

## 1. Backend Tests

### Services (Unit Tests)
| Service | Test | Status |
|---------|------|--------|
| TarifService | TarifServiceTest.java | ✅ OK |
| EinheitService | - | ❌ FEHLT |

### Controllers (Integration Tests)
| Controller | Test | Status |
|------------|------|--------|
| ... | ... | ... |

## 2. Frontend Unit Tests

### Components
| Component | Test | Status |
|-----------|------|--------|
| ... | ... | ... |

### Services
| Service | Test | Status |
|---------|------|--------|
| ... | ... | ... |

## 3. E2E Tests

| Feature | Test | Status |
|---------|------|--------|
| ... | ... | ... |

## Zusammenfassung

| Bereich | Vorhanden | Fehlend | Abdeckung |
|---------|-----------|---------|-----------|
| Backend Services | X | Y | Z% |
| ... | ... | ... | ... |

## Priorisierte Empfehlungen

### Hohe Priorität
1. ...

### Mittlere Priorität
1. ...

### Niedrige Priorität
1. ...
```

---

## Priorisierungs-Kriterien

**Hohe Priorität:**
- Kern-Entities (Einheit, Messwerte, Tarif)
- CRUD-Operationen
- Haupt-UI-Komponenten (List, Form)
- HTTP-Services

**Mittlere Priorität:**
- Statistik, Reporting
- PDF-Generierung
- Sekundäre UI-Komponenten

**Niedrige Priorität:**
- Infrastructure-Services (Filter, Context)
- UI-Hilfskomponenten (Navigation, Menus)
- Showcase/Demo-Komponenten

---

## Ausführung

Nach der Analyse:
1. Zeige den vollständigen Bericht
2. Frage ob Tests erstellt werden sollen
3. Bei "ja": Verwende `/3_backend-tests`, `/4_frontend-unit-tests` oder `/5_e2e-tests`

---

## Hinweise

- Diese Analyse ist READ-ONLY - es werden keine Dateien erstellt oder geändert
- Die Analyse basiert auf Dateinamen-Konventionen
- Qualität der vorhandenen Tests wird NICHT geprüft (nur Existenz)
- Für Test-Qualitäts-Analyse: Tests manuell reviewen oder Coverage-Report nutzen
