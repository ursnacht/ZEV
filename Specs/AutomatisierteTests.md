# Automatisiertes Testing

## 1. Ziel & Kontext
* **Was soll erreicht werden:** Die Anwendung soll automatisiert getestet werden.
* **Warum machen wir das:** Wir wollen sicher sein, dass bei Änderungen die Anwendung immer noch so funktioniert wie bisher.
* Die Testpyramide soll beachtet werden:
  * Unit Tests: 70-80%
  * Integration Tests: 15-20%
  * End-to-End Tests: 5-10%
* Die automatisierte Teststrategie muss nahtlos in den Build-Prozess integriert werden, wobei Unit- und Integrationstests strikt voneinander getrennt ablaufen müssen.

## 2. Backend Tests

### Unit Tests
* **Tool:** JUnit 5 (Jupiter) mit AssertJ
* **Namenskonvention:** `*Test.java`
* **Ausführung:** Maven Surefire Plugin (`mvn test`)
* **Mocking:** Mockito für Abhängigkeiten
* **Scope:** Einzelne Klassen/Methoden isoliert testen

### Integration Tests
* **Tool:** Spring Test Slices (@WebMvcTest, @DataJpaTest, @JsonTest)
* **Namenskonvention:** `*IT.java`
* **Ausführung:** Maven Failsafe Plugin (`mvn verify`)
* **Datenbank:** Testcontainers (keine In-Memory-DB)
* **Hinweise:**
  * @SpringBootTest vermeiden - Spring Context minimieren
  * ArgumentCaptor für komplexe Validierungen
  * @MockBean sparsam einsetzen (deutet auf Architekturproblem hin)
  * Post-Test Teardown: Testcontainer herunterfahren, Testdaten löschen

## 3. Frontend Tests

### Unit Tests
* **Tool:** Jasmine mit Karma
* **Namenskonvention:** `*.spec.ts`
* **Ausführung:** `npm.cmd test` (im frontend-service Verzeichnis)
* **Scope:** Komponenten, Services, Pipes isoliert testen

### End-to-End Tests
* **Tool:** Playwright
* **Verzeichnis:** `frontend-service`
* **Ausführung:** `npm.cmd run e2e:ci` oder `npm run e2e:ui` (interaktiv)
* **Scope:** Komplette User Flows durch die Anwendung

## 4. Ausführung

| Befehl                                      | Beschreibung |
|---------------------------------------------|--------------|
| `mvn test`                                  | Backend Unit Tests |
| `mvn verify`                                | Backend Unit + Integration Tests |
| `cd frontend-service && npm.cmd test`       | Frontend Unit Tests |
| `cd frontend-service && npm.cmd run e2e:ci` | Frontend E2E Tests |
| `mvn clean compile test verify`             | Alles (ohne E2E) |

## 5. Test-Daten & Mocking
* **Unit Tests:** Mocks für alle externen Abhängigkeiten
* **Integration Tests:** Testcontainers mit echten Datenbank-Operationen
* **E2E Tests:** Dedizierte Testbenutzer in Keycloak (testuser/testpassword)
* **Fixtures:** Wiederverwendbare Testdaten in separaten Dateien/Klassen
