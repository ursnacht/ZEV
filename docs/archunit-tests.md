# ArchUnit-Tests

ArchUnit-Tests zur Sicherstellung der Architektur-Konventionen im Backend-Service.

## Dependency

Die ArchUnit-Dependency wurde zur `backend-service/pom.xml` hinzugefügt:

```xml
<dependency>
  <groupId>com.tngtech.archunit</groupId>
  <artifactId>archunit-junit5</artifactId>
  <version>1.3.0</version>
  <scope>test</scope>
</dependency>
```

## Testklasse

**Pfad:** `backend-service/src/test/java/ch/nacht/architecture/ArchitectureTest.java`

| Kategorie | Tests |
|-----------|-------|
| **Schichten-Architektur** | Layered Architecture, Controller→Service, Repository-Isolation |
| **Namenskonventionen** | Controller, Service, Repository, DTO, Config |
| **Annotationen** | @RestController, @Service, @Entity, Repository als Interface |
| **Abhängigkeiten** | Entity-Isolation, DTO-Isolation, keine Zyklen zwischen Haupt-Schichten |
| **Spring-Regeln** | Repositories erben von JpaRepository |

## Geprüfte Architekturregeln

- Controller → Service → Repository ist strikt unidirektional
- Config als Querschnittsschicht (darf Services verwenden und von Services verwendet werden)
- DTOs dürfen Enums aus dem Entity-Package verwenden (EinheitTyp, TarifTyp)
- Innere Klassen (Builder, Adresse, Steller) werden bei Namenskonventionen ausgeschlossen

## Ausführen

```bash
cd backend-service
mvn test -Dtest=ArchitectureTest
```
