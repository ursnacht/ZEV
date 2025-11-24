# ZEV - Zusammenschluss zum Eigenverbrauch

Solar power distribution application for managing fair allocation of solar energy among consumers in a ZEV (self-consumption community).

## Prerequisites

- Java 17+
- Maven 3.6+
- Docker & Docker Compose

## Architecture

Multi-module Maven project:
- **backend-service**: Spring Boot REST API (port 8080)
- **frontend-service**: Angular web application (port 4200)

## Build & Test

```bash
mvn clean compile test
```

## Database Setup

Start PostgreSQL:

```bash
docker-compose up -d postgres
```

Run Flyway migrations:

```bash
cd backend-service
mvn flyway:migrate
```

## Run Application

```bash
docker-compose up --build
```

Access:
- Frontend: http://localhost:4200
- Backend API: http://localhost:8080

## Project Structure

```
backend-service/        # REST API backend
├── src/main/java/ch/nacht/
│   ├── BackendServiceApplication.java
│   ├── SolarDistribution.java
│   ├── config/
│   │   └── WebConfig.java       # CORS configuration
│   ├── controller/
│   │   ├── EinheitController.java
│   │   ├── MesswerteController.java
│   │   └── PingController.java
│   ├── entity/
│   │   ├── Einheit.java
│   │   ├── EinheitTyp.java
│   │   └── Messwerte.java
│   └── repository/
│       ├── EinheitRepository.java
│       └── MesswerteRepository.java
└── src/main/resources/db/migration/

frontend-service/           # Angular web application
├── src/
│   ├── app/
│   │   ├── components/
│   │   │   ├── einheit-list/
│   │   │   ├── einheit-form/
│   │   │   ├── messwerte-upload/
│   │   │   └── navigation/
│   │   ├── models/
│   │   ├── services/
│   │   └── app.routes.ts
│   └── index.html
├── Dockerfile
└── nginx.conf
```

## Algorithm

The `SolarDistribution` class implements a fair distribution algorithm:

- **Case A**: If solar production >= total consumption, each consumer gets their full demand
- **Case B**: If solar production < total consumption, power is distributed proportionally among consumers
