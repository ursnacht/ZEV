# Umsetzungsplan: Mandantenfähigkeit

## Zusammenfassung

Das System wird mandantenfähig gemacht, indem Keycloak Organizations verwendet werden. Jeder Benutzer gehört einer oder mehreren Organisationen an. Die org_id wird aus dem JWT extrahiert und für die Filterung aller Datenbankabfragen verwendet.

## Akzeptanzkriterien

- [x] Die org_id kann aus dem JWT gelesen werden
- [ ] Der Benutzer kann die Organisation nach der Authentisierung wählen (falls mehrere)
- [x] Es werden nur Einheiten, Messwerte, Tarife und Metriken der eigenen Organisation angezeigt
- [ ] Die Metriken enthalten ein zusätzliches Tag `org` mit dem Organisationsnamen
- [x] Bestehende Daten werden zur Default-Organisation migriert
- [x] Der Organisationsname wird im Menü neben dem Benutzernamen angezeigt

## Annahmen & Klärungen

| Frage | Antwort |
|-------|---------|
| Format org_id | UUID (bestätigt) |
| Bestehende Daten | Migration mit Default-Org `c2c9ba74-de18-4491-9489-8185629edd93` |
| Admin-Zugriff | Muss Organisation wählen (kein globaler Zugriff) |
| Org-Auswahl UI | Eigene Seite nach Login |
| Keycloak Admin API | Service-Account mit Admin-Rechten |
| Passwort-Konfiguration | Umgebungsvariable `KEYCLOAK_CLIENT_SECRET` |
| Hibernate @Filter | Aktivierung via HandlerInterceptor |

## Abgrenzung / Out of Scope

- Verwaltung von Organisationen und Benutzern (erfolgt in Keycloak)
- Multi-Realm-Setup (nur Organizations innerhalb eines Realms)

---

## Phasen-Übersicht

| Phase | Beschreibung | Status |
|-------|--------------|--------|
| 1 | org_id(s) aus JWT lesen und loggen | ✅ Abgeschlossen |
| 2 | Fallback: Erste org_id verwenden, ohne org_id ausloggen | ✅ Abgeschlossen |
| 3 | Datenbank mit org_id erweitern (Spalte, Filter, Migration) | ✅ Abgeschlossen |
| 4 | Organisationsauswahl im Frontend | ⬜ Offen |
| 5 | Metriken mit Organisations-Tag erweitern | ⬜ Offen |

**Legende:** ⬜ Offen | 🔄 In Bearbeitung | ✅ Abgeschlossen

---

## Phase 1: org_id(s) aus JWT lesen und loggen

### Ziel
Die org_id(s) aus dem JWT-Token extrahieren und zur Verfügung stellen.

### Aufgaben
1. Service `OrganizationContextService` erstellen
2. org_id(s) aus JWT extrahieren: `jwtToken.getTokenAttributes().get("organizations")`
3. Aktuelle org_id im ThreadLocal oder RequestScope speichern
4. Logging der org_id bei jedem Request

### Betroffene Dateien
- `backend-service/src/main/java/ch/nacht/service/OrganizationContextService.java` (neu)
- `backend-service/src/main/java/ch/nacht/config/OrganizationInterceptor.java` (neu)
- `backend-service/src/main/java/ch/nacht/config/WebMvcConfig.java` (neu oder erweitern)

### Technische Details

**OrganizationContextService:**
```java
@Service
@RequestScope
public class OrganizationContextService {
    private UUID currentOrgId;
    private List<UUID> availableOrgIds;

    public UUID getCurrentOrgId() { return currentOrgId; }
    public void setCurrentOrgId(UUID orgId) { this.currentOrgId = orgId; }
    public List<UUID> getAvailableOrgIds() { return availableOrgIds; }
}
```

**JWT-Struktur (erwartet):**
```json
{
  "organizations": {
    "alias": {
      "id": "c2c9ba74-de18-4491-9489-8185629edd93"
    }
  }
}
```

---

## Phase 2: Fallback-Logik und Fehlerbehandlung

### Ziel
Bei mehreren org_ids die erste verwenden. Ohne org_id den Benutzer ausloggen.

### Aufgaben
1. OrganizationInterceptor erweitern: Prüfung auf vorhandene org_id
2. Exception `NoOrganizationException` erstellen
3. Frontend: Fehlerbehandlung und Redirect zu Logout
4. Logging bei fehlender org_id

### Betroffene Dateien
- `backend-service/src/main/java/ch/nacht/config/OrganizationInterceptor.java`
- `backend-service/src/main/java/ch/nacht/exception/NoOrganizationException.java` (neu)
- `backend-service/src/main/java/ch/nacht/controller/GlobalExceptionHandler.java` (erweitern)
- `frontend-service/src/app/services/auth.service.ts` (erweitern)

### Technische Details

**OrganizationInterceptor:**
```java
@Component
public class OrganizationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            Map<String, Object> orgs = jwt.getToken().getClaim("organizations");
            if (orgs == null || orgs.isEmpty()) {
                throw new NoOrganizationException("Keine Organisation im Token");
            }
            // Erste org_id verwenden
            UUID orgId = UUID.fromString(orgs.keySet().iterator().next());
            organizationContextService.setCurrentOrgId(orgId);

            // Hibernate Filter aktivieren
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("orgFilter").setParameter("orgId", orgId);
        }
        return true;
    }
}
```

---

## Phase 3: Datenbank mit org_id erweitern ✅

### Ziel
Alle relevanten Tabellen mit org_id erweitern und Queries automatisch filtern.

### Aufgaben
1. ✅ Flyway-Migration: Spalte `org_id` zu Tabellen hinzufügen
2. ✅ Flyway-Migration: Bestehende Daten mit Default-Org aktualisieren
3. ✅ Flyway-Migration: Indizes für org_id erstellen
4. ✅ Entities erweitern: `orgId` Feld + `@Filter` Annotation
5. ✅ HibernateFilterService: Filter in Service-Methoden aktivieren
6. ✅ Unique Constraint für Metriken anpassen (name + org_id)
7. ✅ Frontend: Organisation im Menü anzeigen

### Erstellte/Geänderte Dateien

**Backend - Migrations:**
- `V26__Add_Org_Id_To_Tables.sql` - org_id Spalte + Migration bestehender Daten
- `V27__Update_Metriken_Unique_Constraint.sql` - Unique Constraint auf (name, org_id)

**Backend - Entities:**
- `Einheit.java` - org_id Feld + @Filter
- `Messwerte.java` - org_id Feld + @Filter
- `Tarif.java` - org_id Feld + @Filter
- `Metrik.java` - org_id Feld + @Filter + UniqueConstraint(name, org_id)
- `entity/package-info.java` - Zentrale @FilterDef Definition

**Backend - Services:**
- `HibernateFilterService.java` (neu) - Aktiviert Hibernate orgFilter
- `EinheitService.java` - Filter-Aktivierung + org_id bei Erstellung
- `MesswerteService.java` - Filter-Aktivierung + org_id bei Erstellung
- `TarifService.java` - Filter-Aktivierung + org_id bei Erstellung
- `StatistikService.java` - Filter-Aktivierung
- `RechnungService.java` - Filter-Aktivierung
- `MetricsService.java` - Filter-Aktivierung + org_id bei Erstellung

**Backend - Config:**
- `pom.xml` - spring-boot-starter-aop Dependency hinzugefügt

**Frontend:**
- `navigation.component.ts` - Organisations-Alias aus JWT extrahieren
- `navigation.component.html` - Anzeige: "Benutzername, Org-Alias"

### Technische Details

**HibernateFilterService:**
```java
@Service
public class HibernateFilterService {
    public void enableOrgFilter() {
        UUID orgId = organizationContextService.getCurrentOrgId();
        if (orgId != null) {
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("orgFilter").setParameter("orgId", orgId);
        }
    }
}
```

**Service-Pattern:**
```java
@Transactional(readOnly = true)
public List<Einheit> getAllEinheiten() {
    hibernateFilterService.enableOrgFilter();
    return einheitRepository.findAllByOrderByNameAsc();
}
```

**Frontend - Organisations-Anzeige:**
```typescript
private extractOrganization(): void {
    const token = this.keycloak.tokenParsed;
    if (token?.['organizations']) {
        const aliases = Object.keys(token['organizations']);
        this.organizationAlias = aliases[0];
    }
}

get userDisplayName(): string {
    return this.organizationAlias
        ? `${this.userName}, ${this.organizationAlias}`
        : this.userName;
}
```

---

## Phase 4: Organisationsauswahl im Frontend

### Ziel
Der Benutzer kann nach dem Login die Organisation wählen (falls mehrere vorhanden).

### Aufgaben
1. Backend: Endpoint für verfügbare Organisationen erstellen
2. Backend: Keycloak Admin Client für Organisationsnamen konfigurieren
3. Frontend: Organisationsauswahl-Seite erstellen
4. Frontend: Gewählte Organisation im Header anzeigen
5. Frontend: Route Guard für Org-Auswahl

### Betroffene Dateien

**Backend:**
- `backend-service/src/main/java/ch/nacht/controller/OrganizationController.java` (neu)
- `backend-service/src/main/java/ch/nacht/service/KeycloakAdminService.java` (neu)
- `backend-service/src/main/resources/application.yml` (erweitern)

**Frontend:**
- `frontend-service/src/app/pages/organization-select/` (neu)
- `frontend-service/src/app/services/organization.service.ts` (neu)
- `frontend-service/src/app/components/navigation/navigation.component.ts` (erweitern)
- `frontend-service/src/app/guards/organization.guard.ts` (neu)

### Keycloak Service-Account Konfiguration

```yaml
# application.yml
keycloak:
  admin:
    client-id: zev-backend-service
    client-secret: ${KEYCLOAK_CLIENT_SECRET}
    token-url: http://keycloak:9000/realms/zev/protocol/openid-connect/token
    api-url: http://keycloak:9000/admin/realms/zev
```

```yaml
# docker-compose.yml
backend-service:
  environment:
    - KEYCLOAK_CLIENT_SECRET=${KEYCLOAK_CLIENT_SECRET:-dev-secret}
```

### API-Endpoints

```
GET /api/organizations          - Liste der Organisationen des Benutzers
POST /api/organizations/select  - Organisation auswählen (setzt Session/Cookie)
GET /api/organizations/current  - Aktuelle Organisation
```

---

## Phase 5: Metriken mit Organisations-Tag erweitern

### Ziel
Alle Metriken erhalten ein zusätzliches Tag `org` mit dem Organisationsnamen.

### Aufgaben
1. MetricsService erweitern: org-Tag zu allen Gauges hinzufügen
2. Organisationsnamen cachen (nicht bei jedem Request Keycloak abfragen)
3. Grafana Dashboard anpassen: Filter nach Organisation

### Betroffene Dateien
- `backend-service/src/main/java/ch/nacht/service/MetricsService.java`
- `grafana/provisioning/dashboards/zev-dashboard.json`

### Technische Details

```java
// Gauge mit org-Tag registrieren
Gauge.builder(METRIC_MESSDATEN_UPLOAD_ZEITPUNKT, ...)
    .tag("einheit", einheitName)
    .tag("org", organizationName)  // NEU
    .register(meterRegistry);
```

### Prometheus-Metrik (Beispiel)

```
zev_messdaten_upload_letzter_zeitpunkt{einheit="1. Stock li", org="ZEV Musterstrasse"} 1734518400
```

---

## Risiken und Mitigationen

| Risiko | Wahrscheinlichkeit | Auswirkung | Mitigation |
|--------|-------------------|------------|------------|
| JWT-Struktur anders als erwartet | Mittel | Hoch | Zuerst JWT-Struktur in Phase 1 loggen und validieren |
| Hibernate Filter nicht aktiv | Mittel | Hoch | Unit-Tests für Filter, Logging |
| Performance durch zusätzliche Joins | Niedrig | Mittel | Indizes auf org_id |
| Keycloak Admin API nicht erreichbar | Niedrig | Mittel | Caching, Fallback auf org_id statt Name |

---

## Abhängigkeiten

```
Phase 1 (JWT lesen)
    ↓
Phase 2 (Fallback/Fehler)
    ↓
Phase 3 (Datenbank)
    ↓
Phase 4 (Frontend Auswahl)
    ↓
Phase 5 (Metriken)
```

---

## Testplan

| Test | Typ | Phase | Beschreibung |
|------|-----|-------|--------------|
| JWT org_id Extraktion | Unit | 1 | OrganizationContextService extrahiert org_id korrekt |
| Fehlende org_id | Unit | 2 | NoOrganizationException wird geworfen |
| Hibernate Filter | Integration | 3 | Nur Daten der eigenen Org werden geladen |
| Daten-Isolation | Integration | 3 | Org A sieht keine Daten von Org B |
| Org-Auswahl Flow | E2E | 4 | Benutzer mit 2 Orgs kann wählen |
| Metriken Tag | Unit | 5 | Metriken enthalten org-Tag |
