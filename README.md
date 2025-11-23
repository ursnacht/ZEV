# ZEV - Zusammenschluss zum Eigenverbrauch

Solar power distribution application for managing fair allocation of solar energy among consumers in a ZEV (self-consumption community).

## Prerequisites

- Java 17+
- Maven 3.6+
- Docker & Docker Compose

## Build & Test

```bash
cd backend-service
mvn clean compile test
```

## Database Setup

Start PostgreSQL:

```bash
docker-compose up -d
```

Run Flyway migrations:

```bash
cd backend-service
mvn flyway:migrate
```

## Run Application

```bash
cd backend-service
mvn spring-boot:run
```

## Project Structure

```
backend-service/
├── src/main/java/ch/nacht/
│   ├── BackendServiceApplication.java    # Spring Boot main class
│   ├── SolarDistribution.java            # Fair solar power distribution algorithm
│   ├── entity/
│   │   └── Einheit.java                  # JPA entity
│   └── repository/
│       └── EinheitRepository.java        # Spring Data repository
├── src/test/java/ch/nacht/
│   └── SolarDistributionTest.java
└── src/main/resources/
    ├── application.properties
    └── db/migration/
        ├── V1__create_schema_zev.sql
        └── V2__create_table_einheit.sql
```

## Algorithm

The `SolarDistribution` class implements a fair distribution algorithm:

- **Case A**: If solar production >= total consumption, each consumer gets their full demand
- **Case B**: If solar production < total consumption, power is distributed proportionally among consumers
