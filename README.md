# ZEV - Zusammenschluss zum Eigenverbrauch

Solar power distribution application for managing fair allocation of solar energy among consumers in a ZEV (self-consumption community).

## Prerequisites

- Java 8+
- Maven 3.6+
- Docker & Docker Compose

## Build & Test

```bash
cd app
mvn clean compile test
```

## Database Setup

Start PostgreSQL:

```bash
docker-compose up -d
```

Run Flyway migrations:

```bash
cd app
mvn flyway:migrate
```

## Project Structure

```
app/
├── src/main/java/ch/nacht/
│   └── SolarDistribution.java    # Fair solar power distribution algorithm
├── src/test/java/ch/nacht/
│   └── SolarDistributionTest.java
└── src/main/resources/db/migration/
    └── V1__create_schema_zev.sql
```

## Algorithm

The `SolarDistribution` class implements a fair distribution algorithm:

- **Case A**: If solar production >= total consumption, each consumer gets their full demand
- **Case B**: If solar production < total consumption, power is distributed proportionally among consumers
