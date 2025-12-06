# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ZEV (Zusammenschluss zum Eigenverbrauch) is a solar power distribution application for managing fair allocation of solar energy among consumers in a self-consumption community. Multi-module Maven project with Spring Boot backend, Angular frontend, and Keycloak authentication.

## Build & Test Commands

### Full Stack
```bash
# Build all modules with tests
mvn clean compile test

# Build with integration tests
mvn clean compile test verify

# Start entire stack (PostgreSQL, Keycloak, all services)
docker-compose up --build
```

### Backend Service
```bash
cd backend-service

# Unit tests only (Surefire)
mvn test

# Integration tests (Failsafe, uses TestContainers)
mvn verify

# Run single test class
mvn test -Dtest=EinheitServiceTest

# Run single test method
mvn test -Dtest=EinheitServiceTest#testGetAllEinheiten
```

### Frontend Service
```bash
cd frontend-service

npm install
npm start           # Dev server at localhost:4200
npm test            # Unit tests (Jasmine/Karma)
npm run e2e         # Playwright E2E tests
npm run e2e:ui      # Playwright interactive mode
```

### Design System
```bash
cd design-system

npm install
npm run build       # Build CSS + TypeScript
npm run watch       # Watch mode for development
```

## Architecture

```
┌─────────────────┐     ┌─────────────────┐
│ frontend-service│────▶│ backend-service │
│   (Angular 19)  │     │ (Spring Boot)   │
└────────┬────────┘     └────────┬────────┘
         │                       │
         ▼                       ▼
┌─────────────────┐     ┌─────────────────┐
│    Keycloak     │     │   PostgreSQL    │
│   (OAuth2/JWT)  │     │     (zev DB)    │
└─────────────────┘     └─────────────────┘
```

**Backend Layers:** Controller → Service → Repository → Entity

**Key Backend Components:**
- `EinheitController` - CRUD for units (consumers/producers)
- `MesswerteController` - Measurement data upload/retrieval
- `TranslationController` - i18n support
- `SolarDistribution.java` - Core fair distribution algorithm
- `SecurityConfig` - OAuth2 JWT validation with Keycloak

**Key Frontend Components:**
- `EinheitListComponent` / `EinheitFormComponent` - Unit management
- `MesswerteUploadComponent` - CSV upload
- `MesswerteChartComponent` - Chart visualization
- `SolarCalculationComponent` - Distribution calculator
- `TranslationEditorComponent` - Admin translation management

## Key Conventions

### Testing Strategy
- Unit tests: `*Test.java` (backend), `*.spec.ts` (frontend)
- Integration tests: `*IT.java` with TestContainers
- E2E tests: Playwright in `frontend-service/e2e/`
- Follow test pyramid: 70-80% unit, 15-20% integration, 5-10% E2E

### Database
- Flyway migrations in `backend-service/src/main/resources/db/migration/`
- Migration naming: `V[number]__[description].sql`
- Schema: `zev` (application), `keycloak` (identity)

### Internationalization
- All UI text via `TranslationService` (not hardcoded)
- Translations stored in database
- Use `TranslatePipe` in Angular templates

### Authentication
- Keycloak roles: `zev` (member), `zev_admin` (admin), `user` (basic)
- Backend: `@PreAuthorize` annotations for authorization
- Frontend: `AuthGuard` for route protection

### Design System
- Use `@zev/design-system` for UI components
- Design tokens in `design-system/src/tokens/`
- Components: Button, Navigation, Card

## Specifications

Feature specs are in `/Specs/`:
- `SPEC.md` - Template for new feature specifications
- `generell.md` - General requirements (i18n, design system, testing)
- `AutomatisierteTests.md` - Testing strategy and tool configuration

## Access Points (Docker)

- Frontend: http://localhost:4200
- Backend API: http://localhost:8090
- Keycloak: http://localhost:9000
- Admin Dashboard: http://localhost:8081

## Test Users (Keycloak)

- `testuser` / `testpassword` (zev_admin role)
- `user` / `password` (zev role)
