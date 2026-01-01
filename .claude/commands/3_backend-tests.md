# Erstelle Backend-Tests

Erstelle Unit- und Integrationstests für Backend-Code.

## Input
* Ziel: $ARGUMENTS (z.B. `TarifService` oder `Specs/Tarifverwaltung_Umsetzungsplan.md`)

## Vorgehen
1. **Analysiere den Code** - Verstehe die zu testende Klasse/Funktionalität
2. **Prüfe existierende Tests** - Schaue ob bereits Tests existieren und was fehlt
3. **Orientiere dich an bestehenden Tests** - Nutze vorhandene Tests als Vorlage

## Test-Typen

### Unit Tests (`*Test.java`)
* **Tool:** JUnit 5 (Jupiter) mit AssertJ
* **Ausführung:** `mvn test` (Surefire Plugin)
* **Mocking:** Mockito für alle Abhängigkeiten
* **Scope:** Einzelne Klassen/Methoden isoliert
* **Fokus:** Service-Logik, Validierungen, Edge Cases

### Integration Tests (`*IT.java`)
* **Tool:** Spring Test Slices (@WebMvcTest, @DataJpaTest)
* **Ausführung:** `mvn verify` (Failsafe Plugin)
* **Datenbank:** Testcontainers (PostgreSQL)
* **Scope:** Repository-Queries, Controller-Endpoints
* **Hinweise:**
  - @SpringBootTest vermeiden - Spring Context minimieren
  - @MockBean sparsam einsetzen

## Wann welcher Test-Typ?
| Komponente | Unit Test | Integration Test |
|------------|-----------|------------------|
| Service-Logik | Ja | Nein |
| Repository | Nein | Ja (@DataJpaTest) |
| Controller | Optional | Ja (@WebMvcTest) |
| Utility-Klassen | Ja | Nein |

## Ausführung
```bash
cd backend-service
mvn test                        # Unit Tests
mvn verify                      # Unit + Integration Tests
mvn test -Dtest=TarifServiceTest  # Einzelne Testklasse
```

## Referenz
* Specs/AutomatisierteTests.md, Kapitel "2. Backend Tests"
