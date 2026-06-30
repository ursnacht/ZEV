# Umsetzungsplan: Organisation (Mandanten-Entkopplung von Keycloak-UUID)

## Zusammenfassung

Einführung einer internen Tabelle `zev.organisation` als Ankerpunkt für jeden Mandanten. Alle sechs Datentabellen werden von `org_id UUID` (direkte Keycloak-UUID) auf `org_id BIGINT` (FK auf `organisation.id`) umgestellt, sodass eine Änderung der Keycloak-UUID nur einen einzelnen Datensatz betrifft statt alle Daten zu entkoppeln. Beim Login erfolgt ein Lookup/Auto-Provisioning der Organisation; optional wird der Anzeigename (`displayName`) aus dem JWT gelesen und im Navbar-Titel dargestellt (FR-7).

---

## Betroffene Komponenten

### Datenbank (Flyway)
| Datei | Art | Inhalt |
|-------|-----|--------|
| `db/migration/V45__Create_Organisation_Table.sql` | neu | Sequence + Tabelle `zev.organisation` (`id`, `keycloak_org_id` UNIQUE, `name`, `erstellt_am`) inkl. Index |
| `db/migration/V46__Migrate_Org_Id_To_Organisation_FK.sql` | neu | Distinct-UUIDs nach `organisation` übernehmen; `org_id` der 6 Tabellen von UUID → BIGINT (FK) migrieren |
| `db/migration/V47__Merge_Einstellungen_Into_Organisation.sql` | neu | Einstellungen-Konfiguration in `organisation.konfiguration` (jsonb) überführen |

### Backend
| Datei | Art | Änderung |
|-------|-----|----------|
| `entity/Organisation.java` | neu | Entity (`id`, `keycloakOrgId`, `name`, `erstelltAm`, `konfiguration` jsonb); **kein** `@Filter` |
| `repository/OrganisationRepository.java` | neu | `findByKeycloakOrgId(UUID)` o.ä. |
| `service/OrganisationService.java` | neu | `findOrCreate(UUID, alias)` (Auto-Provisioning, Upsert) |
| `entity/package-info.java` | ändern | `@ParamDef(type = UUID.class)` → `Long.class` |
| `entity/Einheit/Messwerte/Tarif/Metrik/Mieter/Einstellungen.java` | ändern | `UUID orgId` → `Long orgId` |
| `service/OrganizationContextService.java` | ändern | `currentOrgId`/`availableOrgIds` als `Long`; `currentOrgName` |
| `service/HibernateFilterService.java` | ändern | Filter-Parametertyp `UUID` → `Long` |
| `config/OrganizationInterceptor.java` | ändern | Keycloak-UUID → DB-Lookup/Provisioning → interne `Long`-ID; `extractDisplayName(...)` (FR-7) |
| `repository/EinstellungenRepository.java`, `MetrikRepository.java` | ändern | `findBy…OrgId(UUID)` → `Long` |
| `service/*Service.java` (Einheit, Messwerte, Tarif, Mieter, Metrics, Einstellungen) | ändern | `getCurrentOrgId()` liefert `Long` |

### Frontend
| Datei | Art | Änderung |
|-------|-----|----------|
| `components/navigation/navigation.component.ts` | ändern | Claim `organization` (Singular) + `displayName`; Feld `organizationName` (FR-7) |
| `components/navigation/navigation.component.html` | ändern | Org-Name im Titel mit Trennsequenz `" - "`; Benutzername ohne Org-Bezug (FR-7) |

### Konfiguration / Doku
| Datei | Art | Änderung |
|-------|-----|----------|
| Keycloak | konfig | Mapper „Organization Membership" mit „Add organization id" + „Add organization attributes"; Org-Attribut `displayName` |
| `docs/Anleitung-keycloak.md` | ändern | Mapper-Optionen + Abschnitt `displayName` |

---

## Umsetzungsreihenfolge

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. DB-Migration: Tabelle | `V45` – Sequence + Tabelle `zev.organisation` mit `keycloak_org_id` (UNIQUE) und Index |
| [x] | 2. DB-Migration: Datenmigration | `V46` – Distinct `org_id`-UUIDs nach `organisation` übernehmen; `org_id` der 6 Tabellen UUID → BIGINT (FK auf `organisation.id`), inkl. UNIQUE bei `einstellungen` |
| [x] | 3. DB-Migration: Einstellungen-Merge | `V47` – Konfiguration nach `organisation.konfiguration` (jsonb) überführen |
| [x] | 4. Backend-Entity + Repository | `Organisation.java`, `OrganisationRepository.java`; `org_id`-Felder der 6 Entities `UUID` → `Long`; `@ParamDef` auf `Long` |
| [x] | 5. Backend-Service | `OrganisationService.findOrCreate(UUID, alias)` (Auto-Provisioning/Upsert); `HibernateFilterService` Parametertyp `Long` |
| [x] | 6. Context-Service | `OrganizationContextService`: `Long currentOrgId`, `List<Long> availableOrgIds`, `currentOrgName` |
| [x] | 7. Interceptor | `OrganizationInterceptor`: Keycloak-UUID extrahieren → Lookup/Provisioning → interne `Long`-ID setzen; 403 `NO_ORGANIZATION` bei fehlendem Claim |
| [x] | 8. Repositories/Services anpassen | Alle `findBy…OrgId`- und `getCurrentOrgId()`-Aufrufe auf `Long` umstellen |
| [ ] | 9. FR-7: Keycloak-Mapper | Optionen „Add organization id" + „Add organization attributes" aktivieren; Org-Attribut `displayName` hinterlegen |
| [x] | 10. FR-7: Backend-Anzeigename | `extractDisplayName(orgDetails, alias)` – `displayName[0]` lesen, sonst Alias-Fallback; via `setCurrentOrgName(...)` |
| [x] | 11. FR-7: Frontend-Anzeigename | `NavigationComponent` liest Claim `organization` (Singular) + `displayName`; Org-Name im Navbar-Titel (`ZEV Management - <Name>`), Benutzername ohne Org |
| [x] | 12. Doku | `docs/Anleitung-keycloak.md`: Mapper-Optionen + `displayName`-Abschnitt |
| [ ] | 13. FR-6: Admin-Update `keycloak_org_id` | Geschützter Mechanismus zum Aktualisieren der UUID ohne Datenverlust (z.B. DB / Sonder-Endpunkt) |
| [ ] | 14. Verifikation | `mvn -pl backend-service test-compile`/`verify`; Frontend `npm run build`; Login bekannter + neuer Org; UUID-Wechsel-Szenario |

> Status: Phasen 1–8, 10–12 sind bereits implementiert (Migrationen V45–V47, `Organisation`-Entity, `OrganizationContextService` mit `Long`, Interceptor inkl. `displayName`). Offen: Keycloak-Mapper-Konfiguration (9), Admin-Update-Mechanismus (13), abschliessende Verifikation (14).

---

## Validierungen

### Datenbank / Migration
* `keycloak_org_id` ist `NOT NULL` und `UNIQUE` → verhindert doppelte Organisationen.
* FK `org_id → organisation.id` auf allen sechs Tabellen `NOT NULL`; bei `einstellungen` zusätzlich `UNIQUE`.
* Datenmigration darf keine verwaisten `org_id` erzeugen (FK-Constraint erzwingt Vollständigkeit).

### Backend
* **Auto-Provisioning** nur bei valider Keycloak-Organisation aus dem JWT; Requests ohne gültiges JWT werden abgelehnt.
* Fehlt der `organization`-Claim oder ist keine Organisation auflösbar → 403 `NO_ORGANIZATION` (`GlobalExceptionHandler`).
* Ungültige Org-UUID im JWT → `IllegalArgumentException` → Request abgewiesen.
* Race Condition bei erstem Login derselben Org → Upsert (`ON CONFLICT (keycloak_org_id)`).
* **FR-7 `displayName`:** nur gültig als nicht-leerer String im ersten Listenelement; `isBlank()` → Fallback auf Alias.

### Frontend (FR-7)
* `organization`-Claim (Singular); `displayName[0]` mit `.trim()`, sonst Alias.
* Kein Claim / keine Aliase → `organizationName = null`, Titel zeigt nur `ZEV Management`.
* Token-Parsing in `try/catch`; Fehler werden geloggt, brechen die Navbar nicht.

---

## Offene Punkte / Annahmen

* **Auto-Provisioning (Spec-Frage):** opt-in — neue Keycloak-Organisationen werden beim ersten Login automatisch angelegt (entspricht bisherigem Verhalten).
* **Org-Name aktualisieren (Spec-Frage):** Bei jedem Login Upsert `INSERT … ON CONFLICT (keycloak_org_id) DO UPDATE SET name = EXCLUDED.name`.
* **`displayName` persistieren (Spec-Frage):** Nein — `displayName` wird nur zur Laufzeit aus dem JWT gelesen/angezeigt; in `organisation.name` bleibt der Alias. Kein zusätzlicher Sync.
* **`organisation` unterliegt nicht dem `orgFilter`** — technische Tabelle, nicht mandantenspezifisch.
* **Mehrere Organisationen im JWT:** erste Organisation bleibt aktiv (bestehendes Verhalten); kein UI-Mandantenwechsel (Out of Scope laut Spec §7).
* **FR-6 (Admin-Update der UUID):** konkreter Mechanismus (DB-Direktzugriff vs. geschützter Endpunkt) noch offen; in Spec §2/§4 als System-Admin-Funktion beschrieben.
* **Attribut-Struktur bestätigt:** `displayName` liegt direkt unter dem Alias (neben `id`), als Array — verifiziert am realen Token (`"Mut13": { "displayName": ["Mutachstrasse 13"], "id": "…" }`).
