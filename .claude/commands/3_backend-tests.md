# Erstelle Backend-Tests

Erstelle Unit- und Integrationstests für Backend-Code.

## Input
* Ziel: $ARGUMENTS (z.B. `Specs/Tarifverwaltung_Umsetzungsplan.md` oder kurz `TarifService`)

---

## Unabhängige Ausführung

Dieser Skill arbeitet UNABHÄNGIG vom Kontext der aktuellen Session.

**Analysiere NUR:**
1. Die Spec-Datei (falls angegeben)
2. Den tatsächlich implementierten Code
3. Bestehende Tests als Vorlage

**IGNORIERE** jeglichen Kontext aus der vorherigen Konversation.

---

## Vorgehen

### Phase 1: Unabhängige Code-Analyse
1. Lies die Spec-Datei (falls vorhanden) - extrahiere Anforderungen
2. Finde alle relevanten Implementierungs-Dateien mit Glob/Grep:
   - `backend-service/src/main/java/ch/nacht/**/*.java`
3. Analysiere public API: Methoden, Parameter, Return-Types, Exceptions
4. Identifiziere Edge Cases aus dem Code selbst (null-checks, Validierungen, Fehlerbehandlung)

### Phase 2: Test-Gap-Analyse
1. Prüfe existierende Tests in `backend-service/src/test/java/`
2. Vergleiche mit Spec-Anforderungen und implementiertem Code
3. Liste fehlende Test-Cases auf

### Phase 3: Test-Erstellung
1. Erstelle Tests für fehlende Cases (Vorlagen unten beachten)
2. Führe Tests aus: `mvn test -Dtest=XxxTest`
3. Behebe Fehler bis Tests grün sind

## Testpyramide
* **Unit Tests:** 70-80% der Tests
* **Integration Tests:** 15-20% der Tests
* Unit- und Integrationstests müssen strikt voneinander getrennt ablaufen

---

## Unit Tests (`*Test.java`)

* **Tool:** JUnit 5 (Jupiter) mit Mockito
* **Namenskonvention:** `*Test.java`
* **Ausführung:** Maven Surefire Plugin (`mvn test`)
* **Mocking:** Mockito für alle Abhängigkeiten
* **Scope:** Einzelne Klassen/Methoden isoliert testen
* **Fokus:** Service-Logik, Validierungen, Edge Cases

### Datei-Struktur (exakt einhalten)

```java
package ch.nacht.service;

// 1. Projekt-Imports (alphabetisch)
import ch.nacht.entity.[Entity];
import ch.nacht.repository.[Entity]Repository;
import ch.nacht.service.[Entity]Service;
import ch.nacht.service.HibernateFilterService;
import ch.nacht.service.OrganizationContextService;

// 2. JUnit/Mockito-Imports
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 3. Java-Standard-Imports
import java.util.*;

// 4. Static-Imports
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class [Entity]ServiceTest {

    @Mock
    private [Entity]Repository [entity]Repository;

    // IMMER mocken - Multi-Tenancy Dependencies
    @Mock
    private OrganizationContextService organizationContextService;

    @Mock
    private HibernateFilterService hibernateFilterService;

    @InjectMocks
    private [Entity]Service [entity]Service;

    // Testdaten als Instanzvariablen
    private [Entity] test[Entity]1;
    private [Entity] test[Entity]2;

    @BeforeEach
    void setUp() {
        // Testdaten initialisieren mit allen Pflichtfeldern
        test[Entity]1 = new [Entity]();
        test[Entity]1.setId(1L);
        test[Entity]1.setOrgId(UUID.randomUUID());
        // ... weitere Felder

        test[Entity]2 = new [Entity]();
        test[Entity]2.setId(2L);
        test[Entity]2.setOrgId(test[Entity]1.getOrgId());
        // ... weitere Felder
    }

    // Test-Reihenfolge: getAll → getById → save → delete → Business-Logic

    @Test
    void getAll[Entities]_ReturnsList() {
        // Arrange
        when([entity]Repository.findAllByOrderBy...()).thenReturn(List.of(test[Entity]1, test[Entity]2));

        // Act
        List<[Entity]> result = [entity]Service.getAll[Entities]();

        // Assert
        assertEquals(2, result.size());
        verify(hibernateFilterService).enableOrgFilter();
        verify([entity]Repository).findAllByOrderBy...();
    }

    @Test
    void get[Entity]ById_Found_Returns[Entity]() {
        when([entity]Repository.findById(1L)).thenReturn(Optional.of(test[Entity]1));

        Optional<[Entity]> result = [entity]Service.get[Entity]ById(1L);

        assertTrue(result.isPresent());
        verify(hibernateFilterService).enableOrgFilter();
    }

    @Test
    void get[Entity]ById_NotFound_ReturnsEmpty() {
        when([entity]Repository.findById(99L)).thenReturn(Optional.empty());

        Optional<[Entity]> result = [entity]Service.get[Entity]ById(99L);

        assertTrue(result.isEmpty());
    }

    @Test
    void save[Entity]_ValidNew[Entity]_SavesSuccessfully() {
        // Arrange - neue Entity (ohne ID)
        [Entity] new[Entity] = new [Entity]();
        // ... Felder setzen
        UUID orgId = UUID.randomUUID();
        when(organizationContextService.getCurrentOrgId()).thenReturn(orgId);
        when([entity]Repository.save(any())).thenReturn(test[Entity]1);

        // Act
        [Entity] result = [entity]Service.save[Entity](new[Entity]);

        // Assert
        assertNotNull(result);
        verify(organizationContextService).getCurrentOrgId();
        verify([entity]Repository).save(any());
    }

    @Test
    void delete[Entity]_Exists_ReturnsTrue() {
        when([entity]Repository.existsById(1L)).thenReturn(true);

        boolean result = [entity]Service.delete[Entity](1L);

        assertTrue(result);
        verify([entity]Repository).deleteById(1L);
    }

    @Test
    void delete[Entity]_NotExists_ReturnsFalse() {
        when([entity]Repository.existsById(99L)).thenReturn(false);

        boolean result = [entity]Service.delete[Entity](99L);

        assertFalse(result);
        verify([entity]Repository, never()).deleteById(any());
    }
}
```

**Verbindliche Regeln für Service-Tests:**
* `OrganizationContextService` und `HibernateFilterService` IMMER als `@Mock` deklarieren
* `verify(hibernateFilterService).enableOrgFilter()` in Tests prüfen
* Für neue Entities: `verify(organizationContextService).getCurrentOrgId()` prüfen
* Zwei Testdaten-Objekte in `@BeforeEach` initialisieren
* Arrange/Act/Assert Struktur in jedem Test

### Naming-Konvention für Test-Methoden
```
methodName_Scenario_ExpectedResult
```
Beispiele:
- `getAllTarife_ReturnsSortedList`
- `getTarifById_Found_ReturnsTarif`
- `getTarifById_NotFound_ReturnsEmpty`
- `saveTarif_ValidNewTarif_SavesSuccessfully`
- `saveTarif_OverlappingTarif_ThrowsException`
- `deleteTarif_Exists_ReturnsTrue`
- `deleteTarif_NotExists_ReturnsFalse`

---

## Integration Tests (`*IT.java`)

* **Tool:** Spring Test Slices (@WebMvcTest, @DataJpaTest, @JsonTest)
* **Namenskonvention:** `*IT.java`
* **Ausführung:** Maven Failsafe Plugin (`mvn verify`)
* **Datenbank:** Testcontainers (PostgreSQL) - keine In-Memory-DB
* **Scope:** Repository-Queries, Controller-Endpoints

### Hinweise
- @SpringBootTest vermeiden - Spring Context minimieren
- ArgumentCaptor für komplexe Validierungen
- `@MockitoBean` sparsam einsetzen (deutet auf Architekturproblem hin)
- Post-Test Teardown: Testcontainer herunterfahren, Testdaten löschen

### Controller Test Struktur

```java
package ch.nacht.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(XxxController.class)
@AutoConfigureMockMvc(addFilters = false)
public class XxxControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private XxxService xxxService;

    @MockitoBean
    private OrganizationContextService organizationContextService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void getAll_ReturnsListOfXxx() throws Exception {
        when(xxxService.getAll()).thenReturn(Arrays.asList(...));

        mockMvc.perform(get("/api/xxx"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)));
    }
}
```

---

## Wann welcher Test-Typ?

| Komponente | Unit Test | Integration Test |
|------------|-----------|------------------|
| Service-Logik | Ja | Nein |
| Repository | Nein | Ja (@DataJpaTest) |
| Controller | Optional | Ja (@WebMvcTest) |
| Utility-Klassen | Ja | Nein |

## Pflicht-Tests pro Endpoint

| HTTP | Endpoint | Tests |
|------|----------|-------|
| GET | /api/xxx | `getAll_ReturnsList` |
| GET | /api/xxx/{id} | `getById_Found_ReturnsEntity`, `getById_NotFound_Returns404` |
| POST | /api/xxx | `create_ValidInput_ReturnsCreated`, `create_InvalidInput_ReturnsBadRequest` |
| PUT | /api/xxx/{id} | `update_ValidInput_ReturnsOk`, `update_NotFound_Returns404` |
| DELETE | /api/xxx/{id} | `delete_Exists_ReturnsNoContent`, `delete_NotFound_Returns404` |

---

## Test-Daten & Mocking

* **Unit Tests:** Mocks für alle externen Abhängigkeiten
* **Integration Tests:** Testcontainers mit echten Datenbank-Operationen
* **Fixtures:** Wiederverwendbare Testdaten in separaten Dateien/Klassen

---

## Ausführung

| Befehl | Beschreibung |
|--------|--------------|
| `mvn test` | Backend Unit Tests |
| `mvn verify` | Backend Unit + Integration Tests |
| `mvn test -Dtest=TarifServiceTest` | Einzelne Testklasse |
| `mvn clean compile test verify` | Alles (ohne E2E) |
