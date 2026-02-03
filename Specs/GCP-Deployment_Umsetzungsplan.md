# GCP-Deployment Umsetzungsplan

## Zusammenfassung

Die ZEV-Anwendung soll von der lokalen Docker-Compose-Umgebung in die Google Cloud Platform (GCP) migriert werden. Das Deployment umfasst alle Komponenten: Frontend, Backend, Admin-Service, Keycloak und PostgreSQL-Datenbank. Cloud Run wird als serverlose Container-Plattform verwendet, Cloud SQL für die Datenbank.

## Architektur-Übersicht

```
                    ┌──────────────────────────────────────┐
                    │       Cloud Load Balancer            │
                    │   (HTTPS, SSL-Zertifikat, Domain)    │
                    └──────────────────┬───────────────────┘
                                       │
         ┌─────────────────────────────┼─────────────────────────────┐
         │                             │                             │
         ▼                             ▼                             ▼
┌─────────────────┐         ┌─────────────────┐         ┌─────────────────┐
│   Cloud Run     │         │   Cloud Run     │         │   Cloud Run     │
│   (Frontend)    │         │   (Backend)     │         │   (Keycloak)    │
│   Port 8080     │         │   Port 8090     │         │   Port 9000     │
└─────────────────┘         └────────┬────────┘         └────────┬────────┘
                                     │                           │
                                     ▼                           │
                            ┌─────────────────┐                  │
                            │   Cloud Run     │                  │
                            │ (Admin-Service) │                  │
                            │   Port 8081     │                  │
                            └────────┬────────┘                  │
                                     │                           │
                                     └───────────┬───────────────┘
                                                 ▼
                                     ┌─────────────────────┐
                                     │     Cloud SQL       │
                                     │   (PostgreSQL 16)   │
                                     │   Private IP        │
                                     └─────────────────────┘
                                                 │
                    ┌────────────────────────────┼────────────────────────────┐
                    │                            │                            │
                    ▼                            ▼                            ▼
           ┌───────────────┐          ┌─────────────────┐          ┌─────────────────┐
           │ Secret Manager│          │ Artifact        │          │ Cloud           │
           │ (Credentials) │          │ Registry        │          │ Monitoring      │
           └───────────────┘          │ (Docker Images) │          └─────────────────┘
                                      └─────────────────┘
```

## Betroffene Komponenten

### Neue Dateien (zu erstellen)

| Datei | Beschreibung |
|-------|--------------|
| `gcp/cloudbuild.yaml` | Cloud Build CI/CD Pipeline |
| `gcp/terraform/main.tf` | Terraform Hauptkonfiguration |
| `gcp/terraform/variables.tf` | Terraform Variablen |
| `gcp/terraform/outputs.tf` | Terraform Outputs |
| `gcp/terraform/cloud-run.tf` | Cloud Run Services |
| `gcp/terraform/cloud-sql.tf` | Cloud SQL Instanz |
| `gcp/terraform/networking.tf` | VPC, Serverless Connector |
| `gcp/terraform/secrets.tf` | Secret Manager Konfiguration |
| `gcp/terraform/iam.tf` | IAM Rollen und Service Accounts |
| `backend-service/src/main/resources/application-gcp.yml` | GCP-spezifische Spring Config |
| `.github/workflows/deploy-gcp.yml` | GitHub Actions Deployment |

### Anzupassende Dateien

| Datei | Änderung |
|-------|----------|
| `backend-service/Dockerfile` | Multi-Stage Build für optimierte Images |
| `frontend-service/Dockerfile` | Multi-Stage Build |
| `admin-service/Dockerfile` | Multi-Stage Build |
| `keycloak/realms/zev-realm.json` | Produktions-URLs für Redirects |
| `frontend-service/src/environments/` | GCP Environment-Konfiguration |

## Phasen-Tabelle

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [ ] | **1. GCP-Projekt Setup** | GCP-Projekt erstellen, APIs aktivieren, Billing einrichten |
| [ ] | **2. Terraform Basis** | Terraform State Backend (GCS Bucket), Provider-Konfiguration |
| [ ] | **3. Netzwerk** | VPC, Serverless VPC Access Connector für Cloud SQL |
| [ ] | **4. Cloud SQL** | PostgreSQL-Instanz mit Private IP, Schemas erstellen |
| [ ] | **5. Secret Manager** | Secrets für DB-Passwort, Anthropic API Key, Keycloak Admin |
| [ ] | **6. Artifact Registry** | Docker Repository für Container-Images |
| [ ] | **7. Dockerfiles optimieren** | Multi-Stage Builds für alle Services |
| [ ] | **8. Backend Cloud Run** | Backend-Service deployen mit Cloud SQL Verbindung |
| [ ] | **9. Admin-Service Cloud Run** | Admin-Service deployen |
| [ ] | **10. Keycloak Cloud Run** | Keycloak mit persistenter DB deployen |
| [ ] | **11. Frontend Cloud Run** | Frontend deployen mit Umgebungsvariablen |
| [ ] | **12. Load Balancer** | HTTPS Load Balancer mit SSL-Zertifikat |
| [ ] | **13. DNS & Domain** | Domain konfigurieren, SSL-Zertifikat |
| [ ] | **14. CI/CD Pipeline** | GitHub Actions oder Cloud Build einrichten |
| [ ] | **15. Monitoring** | Cloud Monitoring, Logging, Alerting |
| [ ] | **16. Keycloak Konfiguration** | Realm für Produktion anpassen |
| [ ] | **17. Produktions-Test** | End-to-End Tests in GCP |

---

## Detaillierte Phasenbeschreibungen

### Phase 1: GCP-Projekt Setup

```bash
# Projekt erstellen
gcloud projects create zev-prod --name="ZEV Production"

# Projekt als aktiv setzen
gcloud config set project zev-prod

# Billing Account verknüpfen (ID aus Console)
gcloud billing projects link zev-prod --billing-account=XXXXXX-XXXXXX-XXXXXX

# Benötigte APIs aktivieren
gcloud services enable \
  run.googleapis.com \
  sqladmin.googleapis.com \
  secretmanager.googleapis.com \
  artifactregistry.googleapis.com \
  vpcaccess.googleapis.com \
  compute.googleapis.com \
  cloudbuild.googleapis.com \
  cloudresourcemanager.googleapis.com \
  servicenetworking.googleapis.com
```

### Phase 2: Terraform Basis

**Datei: `gcp/terraform/main.tf`**
```hcl
terraform {
  required_version = ">= 1.5.0"

  backend "gcs" {
    bucket = "zev-terraform-state"
    prefix = "terraform/state"
  }

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
}

# State Bucket (manuell erstellen oder via bootstrap)
resource "google_storage_bucket" "terraform_state" {
  name          = "${var.project_id}-terraform-state"
  location      = var.region
  force_destroy = false

  versioning {
    enabled = true
  }

  lifecycle_rule {
    condition {
      num_newer_versions = 5
    }
    action {
      type = "Delete"
    }
  }
}
```

**Datei: `gcp/terraform/variables.tf`**
```hcl
variable "project_id" {
  description = "GCP Project ID"
  type        = string
  default     = "zev-prod"
}

variable "region" {
  description = "GCP Region"
  type        = string
  default     = "europe-west6"  # Zürich
}

variable "zone" {
  description = "GCP Zone"
  type        = string
  default     = "europe-west6-a"
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "prod"
}

variable "domain" {
  description = "Domain for the application"
  type        = string
  default     = "zev.example.ch"
}
```

### Phase 3: Netzwerk

**Datei: `gcp/terraform/networking.tf`**
```hcl
# VPC Network
resource "google_compute_network" "main" {
  name                    = "zev-vpc"
  auto_create_subnetworks = false
}

# Subnet für Cloud Run
resource "google_compute_subnetwork" "main" {
  name          = "zev-subnet"
  ip_cidr_range = "10.0.0.0/24"
  region        = var.region
  network       = google_compute_network.main.id
}

# Private Service Access für Cloud SQL
resource "google_compute_global_address" "private_ip" {
  name          = "zev-private-ip"
  purpose       = "VPC_PEERING"
  address_type  = "INTERNAL"
  prefix_length = 16
  network       = google_compute_network.main.id
}

resource "google_service_networking_connection" "private_vpc_connection" {
  network                 = google_compute_network.main.id
  service                 = "servicenetworking.googleapis.com"
  reserved_peering_ranges = [google_compute_global_address.private_ip.name]
}

# Serverless VPC Access Connector (für Cloud Run -> Cloud SQL)
resource "google_vpc_access_connector" "connector" {
  name          = "zev-vpc-connector"
  region        = var.region
  ip_cidr_range = "10.8.0.0/28"
  network       = google_compute_network.main.name

  min_instances = 2
  max_instances = 3
}
```

### Phase 4: Cloud SQL

**Datei: `gcp/terraform/cloud-sql.tf`**
```hcl
resource "google_sql_database_instance" "main" {
  name             = "zev-postgres"
  database_version = "POSTGRES_16"
  region           = var.region

  depends_on = [google_service_networking_connection.private_vpc_connection]

  settings {
    tier              = "db-custom-2-4096"  # 2 vCPU, 4GB RAM
    availability_type = "ZONAL"              # REGIONAL für HA
    disk_size         = 20
    disk_type         = "PD_SSD"

    ip_configuration {
      ipv4_enabled    = false
      private_network = google_compute_network.main.id
    }

    backup_configuration {
      enabled                        = true
      start_time                     = "03:00"
      point_in_time_recovery_enabled = true
      backup_retention_settings {
        retained_backups = 7
      }
    }

    maintenance_window {
      day  = 7  # Sunday
      hour = 4
    }

    database_flags {
      name  = "max_connections"
      value = "100"
    }
  }

  deletion_protection = true
}

# Datenbank
resource "google_sql_database" "zev" {
  name     = "zev"
  instance = google_sql_database_instance.main.name
}

# Datenbankbenutzer
resource "google_sql_user" "app" {
  name     = "zev-app"
  instance = google_sql_database_instance.main.name
  password = random_password.db_password.result
}

resource "random_password" "db_password" {
  length  = 32
  special = false
}

# Keycloak Schema wird via Flyway/Keycloak erstellt
```

### Phase 5: Secret Manager

**Datei: `gcp/terraform/secrets.tf`**
```hcl
# Datenbank-Passwort
resource "google_secret_manager_secret" "db_password" {
  secret_id = "zev-db-password"

  replication {
    auto {}
  }
}

resource "google_secret_manager_secret_version" "db_password" {
  secret      = google_secret_manager_secret.db_password.id
  secret_data = random_password.db_password.result
}

# Anthropic API Key
resource "google_secret_manager_secret" "anthropic_api_key" {
  secret_id = "anthropic-api-key"

  replication {
    auto {}
  }
}

# Keycloak Admin Passwort
resource "google_secret_manager_secret" "keycloak_admin_password" {
  secret_id = "keycloak-admin-password"

  replication {
    auto {}
  }
}

resource "google_secret_manager_secret_version" "keycloak_admin_password" {
  secret      = google_secret_manager_secret.keycloak_admin_password.id
  secret_data = random_password.keycloak_admin.result
}

resource "random_password" "keycloak_admin" {
  length  = 24
  special = true
}
```

### Phase 6: Artifact Registry

**Datei: `gcp/terraform/artifact-registry.tf`**
```hcl
resource "google_artifact_registry_repository" "docker" {
  location      = var.region
  repository_id = "zev-docker"
  description   = "Docker repository for ZEV application"
  format        = "DOCKER"

  cleanup_policies {
    id     = "keep-recent"
    action = "KEEP"

    most_recent_versions {
      keep_count = 10
    }
  }
}

output "docker_registry" {
  value = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.docker.repository_id}"
}
```

### Phase 7: Dockerfiles optimieren

**Datei: `backend-service/Dockerfile` (optimiert)**
```dockerfile
# Build Stage
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && \
    mvn clean package -DskipTests

# Runtime Stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /app/target/*.jar app.jar

# Cloud Run expects PORT env variable
ENV PORT=8090
EXPOSE ${PORT}

# JVM optimizations for containers
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
```

**Datei: `frontend-service/Dockerfile` (optimiert)**
```dockerfile
# Build Stage - Node for Angular
FROM node:20-alpine AS node-builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build -- --configuration=production

# Build Stage - Java for Spring
FROM eclipse-temurin:21-jdk-alpine AS java-builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
COPY --from=node-builder /app/dist ./src/main/resources/static
RUN apk add --no-cache maven && \
    mvn clean package -DskipTests

# Runtime Stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
COPY --from=java-builder /app/target/*.jar app.jar

ENV PORT=8080
EXPOSE ${PORT}

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
```

### Phase 8-11: Cloud Run Services

**Datei: `gcp/terraform/cloud-run.tf`**
```hcl
# Service Account für Cloud Run
resource "google_service_account" "cloud_run" {
  account_id   = "zev-cloud-run"
  display_name = "ZEV Cloud Run Service Account"
}

# IAM: Cloud SQL Client
resource "google_project_iam_member" "cloud_run_sql" {
  project = var.project_id
  role    = "roles/cloudsql.client"
  member  = "serviceAccount:${google_service_account.cloud_run.email}"
}

# IAM: Secret Manager Access
resource "google_project_iam_member" "cloud_run_secrets" {
  project = var.project_id
  role    = "roles/secretmanager.secretAccessor"
  member  = "serviceAccount:${google_service_account.cloud_run.email}"
}

# Backend Service
resource "google_cloud_run_v2_service" "backend" {
  name     = "zev-backend"
  location = var.region

  template {
    service_account = google_service_account.cloud_run.email

    scaling {
      min_instance_count = 0
      max_instance_count = 10
    }

    vpc_access {
      connector = google_vpc_access_connector.connector.id
      egress    = "PRIVATE_RANGES_ONLY"
    }

    containers {
      image = "${var.region}-docker.pkg.dev/${var.project_id}/zev-docker/backend:latest"

      ports {
        container_port = 8090
      }

      resources {
        limits = {
          cpu    = "1"
          memory = "1Gi"
        }
      }

      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = "gcp"
      }

      env {
        name  = "SPRING_DATASOURCE_URL"
        value = "jdbc:postgresql://${google_sql_database_instance.main.private_ip_address}:5432/zev"
      }

      env {
        name  = "SPRING_DATASOURCE_USERNAME"
        value = google_sql_user.app.name
      }

      env {
        name = "SPRING_DATASOURCE_PASSWORD"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.db_password.secret_id
            version = "latest"
          }
        }
      }

      env {
        name = "ANTHROPIC_API_KEY"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.anthropic_api_key.secret_id
            version = "latest"
          }
        }
      }

      env {
        name  = "SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI"
        value = "https://${var.domain}/auth/realms/zev"
      }

      startup_probe {
        http_get {
          path = "/actuator/health"
          port = 8090
        }
        initial_delay_seconds = 10
        period_seconds        = 10
        failure_threshold     = 3
      }

      liveness_probe {
        http_get {
          path = "/actuator/health/liveness"
          port = 8090
        }
        period_seconds = 30
      }
    }
  }

  traffic {
    percent = 100
    type    = "TRAFFIC_TARGET_ALLOCATION_TYPE_LATEST"
  }
}

# Admin Service
resource "google_cloud_run_v2_service" "admin" {
  name     = "zev-admin"
  location = var.region

  template {
    service_account = google_service_account.cloud_run.email

    scaling {
      min_instance_count = 0
      max_instance_count = 2
    }

    containers {
      image = "${var.region}-docker.pkg.dev/${var.project_id}/zev-docker/admin:latest"

      ports {
        container_port = 8081
      }

      resources {
        limits = {
          cpu    = "0.5"
          memory = "512Mi"
        }
      }
    }
  }
}

# Keycloak Service
resource "google_cloud_run_v2_service" "keycloak" {
  name     = "zev-keycloak"
  location = var.region

  template {
    service_account = google_service_account.cloud_run.email

    scaling {
      min_instance_count = 1  # Always on for auth
      max_instance_count = 3
    }

    vpc_access {
      connector = google_vpc_access_connector.connector.id
      egress    = "PRIVATE_RANGES_ONLY"
    }

    containers {
      image = "quay.io/keycloak/keycloak:latest"

      ports {
        container_port = 8080
      }

      resources {
        limits = {
          cpu    = "1"
          memory = "1Gi"
        }
      }

      args = ["start", "--optimized"]

      env {
        name  = "KC_HTTP_PORT"
        value = "8080"
      }

      env {
        name  = "KC_PROXY"
        value = "edge"
      }

      env {
        name  = "KC_HOSTNAME"
        value = "${var.domain}/auth"
      }

      env {
        name  = "KC_DB"
        value = "postgres"
      }

      env {
        name  = "KC_DB_URL"
        value = "jdbc:postgresql://${google_sql_database_instance.main.private_ip_address}:5432/zev"
      }

      env {
        name  = "KC_DB_SCHEMA"
        value = "keycloak"
      }

      env {
        name  = "KC_DB_USERNAME"
        value = google_sql_user.app.name
      }

      env {
        name = "KC_DB_PASSWORD"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.db_password.secret_id
            version = "latest"
          }
        }
      }

      env {
        name  = "KEYCLOAK_ADMIN"
        value = "admin"
      }

      env {
        name = "KEYCLOAK_ADMIN_PASSWORD"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.keycloak_admin_password.secret_id
            version = "latest"
          }
        }
      }
    }
  }
}

# Frontend Service
resource "google_cloud_run_v2_service" "frontend" {
  name     = "zev-frontend"
  location = var.region

  template {
    service_account = google_service_account.cloud_run.email

    scaling {
      min_instance_count = 0
      max_instance_count = 10
    }

    containers {
      image = "${var.region}-docker.pkg.dev/${var.project_id}/zev-docker/frontend:latest"

      ports {
        container_port = 8080
      }

      resources {
        limits = {
          cpu    = "0.5"
          memory = "512Mi"
        }
      }
    }
  }

  traffic {
    percent = 100
    type    = "TRAFFIC_TARGET_ALLOCATION_TYPE_LATEST"
  }
}

# Allow unauthenticated access to frontend
resource "google_cloud_run_service_iam_member" "frontend_public" {
  location = google_cloud_run_v2_service.frontend.location
  service  = google_cloud_run_v2_service.frontend.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}
```

### Phase 12-13: Load Balancer & Domain

**Datei: `gcp/terraform/load-balancer.tf`**
```hcl
# SSL Certificate (managed by Google)
resource "google_compute_managed_ssl_certificate" "main" {
  name = "zev-ssl-cert"

  managed {
    domains = [var.domain]
  }
}

# External IP
resource "google_compute_global_address" "main" {
  name = "zev-external-ip"
}

# URL Map für Routing
resource "google_compute_url_map" "main" {
  name            = "zev-url-map"
  default_service = google_compute_backend_service.frontend.id

  host_rule {
    hosts        = [var.domain]
    path_matcher = "main"
  }

  path_matcher {
    name            = "main"
    default_service = google_compute_backend_service.frontend.id

    path_rule {
      paths   = ["/api/*"]
      service = google_compute_backend_service.backend.id
    }

    path_rule {
      paths   = ["/auth/*"]
      service = google_compute_backend_service.keycloak.id
    }

    path_rule {
      paths   = ["/admin/*"]
      service = google_compute_backend_service.admin.id
    }
  }
}

# Backend Services (Serverless NEGs)
resource "google_compute_region_network_endpoint_group" "frontend" {
  name                  = "zev-frontend-neg"
  network_endpoint_type = "SERVERLESS"
  region                = var.region

  cloud_run {
    service = google_cloud_run_v2_service.frontend.name
  }
}

resource "google_compute_backend_service" "frontend" {
  name                  = "zev-frontend-backend"
  protocol              = "HTTP"
  load_balancing_scheme = "EXTERNAL_MANAGED"

  backend {
    group = google_compute_region_network_endpoint_group.frontend.id
  }
}

# Ähnlich für backend, keycloak, admin...

# HTTPS Proxy
resource "google_compute_target_https_proxy" "main" {
  name             = "zev-https-proxy"
  url_map          = google_compute_url_map.main.id
  ssl_certificates = [google_compute_managed_ssl_certificate.main.id]
}

# Forwarding Rule
resource "google_compute_global_forwarding_rule" "main" {
  name                  = "zev-https-forwarding"
  ip_protocol           = "TCP"
  load_balancing_scheme = "EXTERNAL_MANAGED"
  port_range            = "443"
  target                = google_compute_target_https_proxy.main.id
  ip_address            = google_compute_global_address.main.id
}

# HTTP to HTTPS redirect
resource "google_compute_url_map" "http_redirect" {
  name = "zev-http-redirect"

  default_url_redirect {
    https_redirect = true
    strip_query    = false
  }
}

resource "google_compute_target_http_proxy" "redirect" {
  name    = "zev-http-proxy"
  url_map = google_compute_url_map.http_redirect.id
}

resource "google_compute_global_forwarding_rule" "http" {
  name                  = "zev-http-forwarding"
  ip_protocol           = "TCP"
  load_balancing_scheme = "EXTERNAL_MANAGED"
  port_range            = "80"
  target                = google_compute_target_http_proxy.redirect.id
  ip_address            = google_compute_global_address.main.id
}

output "external_ip" {
  value = google_compute_global_address.main.address
}
```

### Phase 14: CI/CD Pipeline

**Datei: `.github/workflows/deploy-gcp.yml`**
```yaml
name: Deploy to GCP

on:
  push:
    branches: [main]
  workflow_dispatch:

env:
  PROJECT_ID: zev-prod
  REGION: europe-west6
  REGISTRY: europe-west6-docker.pkg.dev

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest

    permissions:
      contents: read
      id-token: write

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Authenticate to Google Cloud
        uses: google-github-actions/auth@v2
        with:
          workload_identity_provider: ${{ secrets.WIF_PROVIDER }}
          service_account: ${{ secrets.WIF_SERVICE_ACCOUNT }}

      - name: Set up Cloud SDK
        uses: google-github-actions/setup-gcloud@v2

      - name: Configure Docker
        run: gcloud auth configure-docker ${{ env.REGISTRY }}

      - name: Set up Java
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
          cache: maven

      - name: Set up Node.js
        uses: actions/setup-node@v4
        with:
          node-version: 20
          cache: npm
          cache-dependency-path: frontend-service/package-lock.json

      # Build Design System
      - name: Build Design System
        run: |
          cd design-system
          npm ci
          npm run build

      # Build and push Backend
      - name: Build Backend
        run: |
          cd backend-service
          mvn clean package -DskipTests
          docker build -t ${{ env.REGISTRY }}/${{ env.PROJECT_ID }}/zev-docker/backend:${{ github.sha }} .
          docker push ${{ env.REGISTRY }}/${{ env.PROJECT_ID }}/zev-docker/backend:${{ github.sha }}

      # Build and push Frontend
      - name: Build Frontend
        run: |
          cd frontend-service
          npm ci
          npm run build -- --configuration=production
          mvn clean package -DskipTests
          docker build -t ${{ env.REGISTRY }}/${{ env.PROJECT_ID }}/zev-docker/frontend:${{ github.sha }} .
          docker push ${{ env.REGISTRY }}/${{ env.PROJECT_ID }}/zev-docker/frontend:${{ github.sha }}

      # Build and push Admin Service
      - name: Build Admin Service
        run: |
          cd admin-service
          mvn clean package -DskipTests
          docker build -t ${{ env.REGISTRY }}/${{ env.PROJECT_ID }}/zev-docker/admin:${{ github.sha }} .
          docker push ${{ env.REGISTRY }}/${{ env.PROJECT_ID }}/zev-docker/admin:${{ github.sha }}

      # Deploy to Cloud Run
      - name: Deploy Backend
        run: |
          gcloud run deploy zev-backend \
            --image ${{ env.REGISTRY }}/${{ env.PROJECT_ID }}/zev-docker/backend:${{ github.sha }} \
            --region ${{ env.REGION }}

      - name: Deploy Frontend
        run: |
          gcloud run deploy zev-frontend \
            --image ${{ env.REGISTRY }}/${{ env.PROJECT_ID }}/zev-docker/frontend:${{ github.sha }} \
            --region ${{ env.REGION }}

      - name: Deploy Admin Service
        run: |
          gcloud run deploy zev-admin \
            --image ${{ env.REGISTRY }}/${{ env.PROJECT_ID }}/zev-docker/admin:${{ github.sha }} \
            --region ${{ env.REGION }}
```

### Phase 15: Monitoring

**Datei: `gcp/terraform/monitoring.tf`**
```hcl
# Uptime Check für Frontend
resource "google_monitoring_uptime_check_config" "frontend" {
  display_name = "ZEV Frontend Uptime"
  timeout      = "10s"
  period       = "60s"

  http_check {
    path         = "/"
    port         = 443
    use_ssl      = true
    validate_ssl = true
  }

  monitored_resource {
    type = "uptime_url"
    labels = {
      project_id = var.project_id
      host       = var.domain
    }
  }
}

# Alert Policy
resource "google_monitoring_alert_policy" "uptime" {
  display_name = "ZEV Uptime Alert"
  combiner     = "OR"

  conditions {
    display_name = "Uptime Check Failed"

    condition_threshold {
      filter          = "resource.type = \"uptime_url\" AND metric.type = \"monitoring.googleapis.com/uptime_check/check_passed\""
      duration        = "300s"
      comparison      = "COMPARISON_LT"
      threshold_value = 1

      aggregations {
        alignment_period   = "60s"
        per_series_aligner = "ALIGN_NEXT_OLDER"
      }
    }
  }

  notification_channels = [google_monitoring_notification_channel.email.id]
}

resource "google_monitoring_notification_channel" "email" {
  display_name = "ZEV Admin Email"
  type         = "email"

  labels = {
    email_address = "admin@example.ch"
  }
}
```

### Phase 16: Keycloak Konfiguration

Die `keycloak/realms/zev-realm.json` muss für Produktion angepasst werden:

```json
{
  "realm": "zev",
  "enabled": true,
  "clients": [
    {
      "clientId": "zev-frontend",
      "enabled": true,
      "publicClient": true,
      "redirectUris": [
        "https://zev.example.ch/*"
      ],
      "webOrigins": [
        "https://zev.example.ch"
      ],
      "baseUrl": "https://zev.example.ch"
    }
  ]
}
```

### Phase 17: Backend GCP Profile

**Datei: `backend-service/src/main/resources/application-gcp.yml`**
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 5
      minimum-idle: 2

  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_ISSUER_URI}
          jwk-set-uri: ${KEYCLOAK_JWK_URI}

# Cloud-optimized logging (JSON format for Cloud Logging)
logging:
  pattern:
    console: '{"timestamp":"%d{yyyy-MM-dd HH:mm:ss.SSS}","level":"%level","logger":"%logger","message":"%msg"}%n'
```

---

## Geschätzte Kosten (monatlich)

| Ressource | Konfiguration | Geschätzte Kosten |
|-----------|---------------|-------------------|
| Cloud Run (Backend) | 1 vCPU, 1GB, ~50% Auslastung | CHF 15-30 |
| Cloud Run (Frontend) | 0.5 vCPU, 512MB | CHF 5-15 |
| Cloud Run (Admin) | 0.5 vCPU, 512MB, minimal | CHF 2-5 |
| Cloud Run (Keycloak) | 1 vCPU, 1GB, always-on | CHF 30-50 |
| Cloud SQL (PostgreSQL) | db-custom-2-4096, 20GB SSD | CHF 50-80 |
| VPC Connector | 2 Instanzen | CHF 10-15 |
| Load Balancer | HTTPS mit SSL | CHF 20-25 |
| Secret Manager | 4 Secrets | CHF 1-2 |
| Artifact Registry | ~5GB | CHF 1-2 |
| Cloud Monitoring | Basic | CHF 0-10 |
| **Total** | | **CHF 135-235** |

---

## Offene Punkte / Annahmen

### Offene Punkte

| # | Frage | Status |
|---|-------|--------|
| 1 | Welche Domain soll verwendet werden? | Offen |
| 2 | Gibt es bereits ein GCP-Projekt/Billing Account? | Offen |
| 3 | Soll Keycloak selbst gehostet oder Google Identity Platform verwendet werden? | Annahme: Selbst gehostet |
| 4 | Welche Umgebungen werden benötigt (dev, staging, prod)? | Annahme: Nur prod |
| 5 | Wer soll Monitoring-Alerts erhalten? | Offen |
| 6 | Soll Cloud Armor (WAF/DDoS-Schutz) aktiviert werden? | Offen |
| 7 | Backup-Strategie für Cloud SQL - wie lange aufbewahren? | Annahme: 7 Tage |

### Annahmen

1. **Region:** `europe-west6` (Zürich) für minimale Latenz in der Schweiz
2. **Keycloak:** Selbst gehostet auf Cloud Run (nicht Identity Platform)
3. **CI/CD:** GitHub Actions (könnte auch Cloud Build sein)
4. **SSL:** Google-managed SSL-Zertifikat
5. **Skalierung:** Automatisch mit Cloud Run (0-10 Instanzen)
6. **Datenbank:** Einzelne Cloud SQL-Instanz (nicht HA) für Kostenoptimierung
7. **Terraform:** Für Infrastructure as Code

---

## Validierungen / Checkliste vor Go-Live

- [ ] SSL-Zertifikat aktiv und gültig
- [ ] Keycloak Realm korrekt konfiguriert
- [ ] Alle Secrets in Secret Manager
- [ ] Database-Migrationen erfolgreich
- [ ] Health-Checks funktionieren
- [ ] Monitoring-Alerts konfiguriert
- [ ] Backup-Job für Cloud SQL aktiv
- [ ] DNS-Eintrag zeigt auf Load Balancer IP
- [ ] CORS korrekt konfiguriert
- [ ] Produktions-Testuser erstellt
- [ ] Anthropic API Key in Secret Manager
