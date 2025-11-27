# Gemini Code Analysis Report

## Project Overview

This project is a web application for managing a "Zusammenschluss zum Eigenverbrauch" (ZEV), which is a system for distributing locally generated solar power among multiple consumers.

The application is structured as a multi-module project with a Java Spring Boot backend and an Angular frontend.

## Backend Service (`backend-service`)

The backend is a Spring Boot application responsible for the core business logic and data management.

*   **Technologies:** Spring Boot, Spring Data JPA, Flyway, PostgreSQL.
*   **API Endpoints:**
    *   `EinheitController.java`: Manages consumer units (`Einheit`). It provides REST endpoints for CRUD operations on these units.
    *   `MesswerteController.java`: Handles the import of energy meter readings (`Messwerte`) from CSV files and likely triggers the solar power distribution calculation. Key methods are `uploadCsv` and `calculateDistribution`.
    *   `PingController.java`: A simple controller to check if the service is running.
*   **Data Model & Persistence:**
    *   JPA entities (`Einheit.java`, `Messwerte.java`) define the data model.
    *   Spring Data repositories (`EinheitRepository.java`, `MesswerteRepository.java`) are used for database interaction.
    *   The database schema is managed by Flyway, with migration scripts located in `src/main/resources/db/migration`.
*   **Core Logic:**
    *   The `SolarDistribution.java` class seems to contain the primary business logic for calculating the distribution of solar energy.

## Frontend Service (`frontend-service`)

The frontend is an Angular application that provides the user interface for interacting with the backend.

*   **Technologies:** Angular, TypeScript.
*   **Key Components (inferred):**
    *   Components for listing and editing `Einheiten`.
    *   A component for uploading `Messwerte` CSV files.
    *   Components for visualizing the energy data, possibly including charts.
*   **Interaction with Backend:** The frontend communicates with the backend via the REST APIs to fetch and manipulate data.

## Key Files

*   `backend-service/src/main/java/ch/nacht/controller/MesswerteController.java`: Entry point for data import and calculation.
*   `backend-service/src/main/java/ch/nacht/SolarDistribution.java`: Core business logic for solar distribution.
*   `backend-service/src/main/resources/db/migration`: Database schema definitions.
*   `frontend-service/src/app`: Angular frontend source code.

## Summary

The application allows users to manage consumer units, upload their energy consumption data, and then calculate how the generated solar power is distributed among them. The backend handles the data processing and calculations, while the frontend provides the user interface for these operations. To fully understand the application, a deeper analysis of the frontend components and the `SolarDistribution.java` class is recommended.
