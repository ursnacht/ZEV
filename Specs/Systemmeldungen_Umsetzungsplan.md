# Umsetzungsplan: Systemmeldungen

## Zusammenfassung

Eine mandantenfähige, benutzer­sichtbare **Systemmeldungen**-Seite, die Betriebsfehler zusätzlich zum Log persistent anzeigt (Level, Kategorie, erstmals/zuletzt aufgetreten, Meldung, Zähler, Erledigt). Erste Fehlerquelle ist das **Bilanzmodell** (`BILANZMODELL_KEINE_BILANZDATEN`). Wiederkehrende Fehler werden nach `meldung_key` **dedupliziert** (Zähler + Zeitstempel-Update), bei erfolgreichem Folgelauf **automatisch erledigt** (Auto-Resolve) und nach einer Frist **bereinigt** (Retention). Liste, Filter (Erledigt/Kategorie/Level), Sortierung und Paginierung laufen **serverseitig** (analog Datenbank-Ansicht). Lesen für alle Fachrollen (`systemmeldungen:read`), Verwalten (erledigt/löschen) nur Admin (`systemmeldungen:manage`).

---

## Betroffene Komponenten

| Typ | Datei | Änderungsart |
|-----|-------|--------------|
| DB Migration | `backend-service/.../db/migration/V86__Create_Systemmeldung_Table.sql` | Neu |
| DB Migration | `backend-service/.../db/migration/V87__Add_Systemmeldungen_Translations.sql` | Neu |
| Backend Enum | `backend-service/src/main/java/ch/nacht/entity/MeldungLevel.java` | Neu |
| Backend Entity | `backend-service/src/main/java/ch/nacht/entity/Systemmeldung.java` | Neu |
| Backend Repository | `backend-service/src/main/java/ch/nacht/repository/SystemmeldungRepository.java` | Neu |
| Backend Service | `backend-service/src/main/java/ch/nacht/service/SystemmeldungService.java` | Neu |
| Backend Cleanup-Job | `backend-service/src/main/java/ch/nacht/service/SystemmeldungCleanupJob.java` | Neu |
| Backend Controller | `backend-service/src/main/java/ch/nacht/controller/SystemmeldungController.java` | Neu |
| Backend DTO | `backend-service/src/main/java/ch/nacht/dto/SystemmeldungDTO.java` (+ Listen-/Slice-Wrapper) | Neu |
| Backend Service | `backend-service/src/main/java/ch/nacht/service/MesswerteService.java` | Änderung (Bilanz-Integration) |
| Backend Service | `backend-service/src/main/java/ch/nacht/service/ZaehlerAggregationService.java` | Änderung (Bilanz-Integration) |
| Backend Config | `backend-service/src/main/resources/application.yml` | Änderung (Retention-Frist + Cron) |
| Keycloak | `keycloak/realms/zev-realm.json` | Änderung (Permissions + Composite Roles) |
| Spec | `Specs/Berechtigungen.md` | Änderung (Matrix ergänzen) |
| Frontend Model | `frontend-service/src/app/models/systemmeldung.model.ts` | Neu |
| Frontend Service | `frontend-service/src/app/services/systemmeldung.service.ts` | Neu |
| Frontend Component | `frontend-service/src/app/components/systemmeldungen/systemmeldungen.component.{ts,html,css}` | Neu |
| Frontend Routing | `frontend-service/src/app/app.routes.ts` | Änderung |
| Frontend Navigation | `frontend-service/src/app/app.component.html` | Änderung |

---

## Phasen-Tabelle

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. DB-Migration Tabelle | `V86` – `zev.systemmeldung` + Sequenz, UNIQUE-Teil-Index `(org_id, meldung_key) WHERE erledigt=false`, Index für Liste/Retention |
| [x] | 2. Backend-Enum + Entity | `MeldungLevel` (INFO/WARN/ERROR), `Systemmeldung` (mit `@Filter("orgFilter")`, `org_id` `Long`) |
| [x] | 3. Backend-Repository | Paginierte/gefilterte Liste (`Slice`), org-explizite Dedup-/Auto-Resolve-Queries, Retention-Delete |
| [x] | 4. Backend-Service | `SystemmeldungService`: `erfasse(...)` (Dedup, `REQUIRES_NEW`), `autoResolve(orgId, key)`, `getSeite(...)`, `setErledigt(...)`, `delete(...)`; zentrale Kategorie-/Keys als Konstanten |
| [x] | 5. Backend-Controller + DTO | `/api/systemmeldungen` (GET paginiert/sortiert/gefiltert, PUT erledigt-Toggle, DELETE); method-level `@PreAuthorize` read/manage |
| [x] | 6. Bilanzmodell-Integration | zentral in `MesswerteService.distributeBilanz`: Erfassen (org-explizit, `REQUIRES_NEW`) vor Abbruch, Auto-Resolve bei Erfolg → deckt manuellen + MQTT-Lauf ab (keine Änderung an Controller/Aggregation nötig) |
| [x] | 7. Retention Cleanup-Job | `SystemmeldungCleanupJob` `@Scheduled` (ohne `@Profile`) + `application.yml` (`systemmeldung.retention.tage/cron`) |
| [x] | 8. Berechtigungen | `systemmeldungen:read`/`:manage` in `zev-realm.json` (Composite Roles: read→zev_user, manage→org_admin) + `Berechtigungen.md`. **Realm-Reimport durch User** |
| [x] | 9. Frontend-Model + Service | `systemmeldung.model.ts`, `systemmeldung.service.ts` (paginierte/gefilterte Calls) |
| [x] | 10. Frontend-Komponente | List-Component: Filter (Erledigt/Kategorie/Level), serverseitige Sortierung, Paginierung, Kebab (löschen), Checkbox (erledigt), Level-Badge; neue DS-Styles `.zev-status--info`, `.zev-pagination`, `.zev-filter-row` |
| [x] | 11. Routing | Route `/systemmeldungen` mit `AuthGuard`, `data.permissions: ['systemmeldungen:read']` |
| [x] | 12. Navigation | Menü-Eintrag **nach „Statistik"**, Icon `file-text` |
| [x] | 13. Übersetzungen | `V87` – Menü/Titel, Spalten, Filter-Labels, Level-/Kategorie-Labels, Pagination, Aktionen (DE/EN) |

> **Reihenfolge-Hinweis:** Backend (1–7) vor Frontend (9–12). Phase 8 (Keycloak) wird vom User ausgeführt (Realm-Reimport/Umgebung).

---

## Detailbeschreibung ausgewählter Phasen

### Phase 1: DB-Migration `V86__Create_Systemmeldung_Table.sql`
```sql
CREATE SEQUENCE IF NOT EXISTS zev.systemmeldung_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE zev.systemmeldung (
    id                    BIGINT PRIMARY KEY DEFAULT nextval('zev.systemmeldung_seq'),
    org_id                BIGINT       NOT NULL,
    level                 VARCHAR(10)  NOT NULL,
    kategorie             VARCHAR(50)  NOT NULL,
    meldung_key           VARCHAR(100) NOT NULL,
    parameter             VARCHAR(500),
    erstmals_aufgetreten  TIMESTAMP    NOT NULL,
    zuletzt_aufgetreten   TIMESTAMP    NOT NULL,
    erledigt              BOOLEAN      NOT NULL DEFAULT FALSE,
    erledigt_am           TIMESTAMP,
    erledigt_automatisch  BOOLEAN      NOT NULL DEFAULT FALSE,
    zaehler               INTEGER      NOT NULL DEFAULT 1
);

-- Dedup-Invariante: max. ein OFFENER Eintrag je (org_id, meldung_key)
CREATE UNIQUE INDEX uk_systemmeldung_offen
    ON zev.systemmeldung (org_id, meldung_key) WHERE erledigt = FALSE;
-- Listenzugriff (mandantengefiltert, Default-Sort)
CREATE INDEX idx_systemmeldung_org_zuletzt ON zev.systemmeldung (org_id, zuletzt_aufgetreten DESC);
-- Retention
CREATE INDEX idx_systemmeldung_retention ON zev.systemmeldung (erledigt, erledigt_am);

COMMENT ON COLUMN zev.systemmeldung.level IS 'Schweregrad INFO/WARN/ERROR';
COMMENT ON COLUMN zev.systemmeldung.meldung_key IS 'Übersetzungs-Key des Fehlers';
COMMENT ON COLUMN zev.systemmeldung.zaehler IS 'Anzahl Vorkommen (Dedup)';
```

### Phase 2: Entity + Enum
* `MeldungLevel { INFO, WARN, ERROR }`.
* `Systemmeldung` analog `Tarif.java`: `@Entity @Table(name="systemmeldung", schema="zev")`, `@Filter(name="orgFilter", condition="org_id = :orgId")`, Sequenz-Generator `systemmeldung_seq`, `Long orgId`, `@Enumerated(EnumType.STRING) MeldungLevel level`, restliche Felder gem. Tabelle.

### Phase 3: Repository (`SystemmeldungRepository extends JpaRepository<Systemmeldung, Long>`)
* **Liste (request-scoped, `orgFilter` aktiv):** `Slice<Systemmeldung> findByFilter(...)` – filterbar nach `erledigt`, `kategorie`, `level`, mit `Pageable` (Sort + Seite). `Slice` liefert `hasNext()` → `hatMehr` (ohne teuren Count). Umsetzung via abgeleiteten Methoden je Filterkombination **oder** `@Query` mit optionalen Parametern.
* **Org-explizit (Hintergrund, ohne Request):**
  * `Optional<Systemmeldung> findByOrgIdAndMeldungKeyAndErledigtFalse(Long orgId, String meldungKey)` – Dedup-Lookup.
  * `@Modifying @Query("UPDATE Systemmeldung s SET s.erledigt=true, s.erledigtAm=:jetzt, s.erledigtAutomatisch=true WHERE s.orgId=:orgId AND s.meldungKey=:key AND s.erledigt=false")` – **Auto-Resolve**.
* **Retention (mandantenübergreifend):** `@Modifying @Query("DELETE FROM Systemmeldung s WHERE s.erledigt=true AND s.erledigtAm < :cutoff")`.

### Phase 4: Service (`SystemmeldungService`)
* `erfasse(Long orgId, MeldungLevel level, String kategorieKey, String meldungKey, String parameter)` – **`@Transactional(propagation = REQUIRES_NEW)`**, org-explizit (`hibernateFilterService.enableOrgFilter(orgId)`), Dedup: existiert offener Eintrag (Key) → `zaehler++`, `zuletztAufgetreten=now`, `parameter=neu`; sonst Insert (`erstmals=zuletzt=now`, `zaehler=1`). Race → `DataIntegrityViolationException` (UNIQUE-Index) abfangen und auf Increment zurückfallen.
* `autoResolve(Long orgId, String meldungKey)` – bulk-Update offener Einträge → erledigt; org-explizit; Fehler werden geloggt, nicht propagiert.
* `getSeite(erledigtFilter, kategorie, level, page, size, sortSpalte, sortRichtung)` – `enableOrgFilter()` (request), `Slice` zurückgeben.
* `setErledigt(Long id, boolean erledigt)` – Toggle; beim **Wieder-Öffnen** `erledigtAm=null`, `erledigtAutomatisch=false`; **Ablehnen** (IllegalStateException → 409/400 mit Key), wenn bereits offener Eintrag gleichen `meldungKey` existiert.
* `delete(Long id): boolean`.
* Zeit über einen injizierbaren `Clock`/`LocalDateTime.now()` (Container-Zeitzone, konsistent mit übrigem Code).

### Phase 5: Controller + DTO
* `@RestController @RequestMapping("/api/systemmeldungen")`; **method-level** `@PreAuthorize` (read vs. manage unterschiedlich):
  * `GET` (Liste, Query-Params `erledigt`, `kategorie`, `level`, `page`, `size`, `sortSpalte`, `sortRichtung`) → `hasAuthority('systemmeldungen:read')`; Response `{ items: SystemmeldungDTO[], hatMehr, page }`.
  * `PUT /{id}/erledigt` (Body/Param `erledigt`) → `hasAuthority('systemmeldungen:manage')`.
  * `DELETE /{id}` → `hasAuthority('systemmeldungen:manage')` (204 / 404).
* Fehler (Reopen-Konflikt) → `IllegalStateException`/`IllegalArgumentException` → 400/409 mit Key (bestehendes `GlobalExceptionHandler`-Muster).

### Phase 6: Bilanzmodell-Integration
* **Manuell** (`MesswerteController.calculate-distribution` bzw. der Service-Aufrufer): im `catch`-Pfad des Bilanz-Abbruchs `systemmeldungService.erfasse(orgId, ERROR, "SYSTEMMELDUNG_KATEGORIE_BILANZMODELL", "BILANZMODELL_KEINE_BILANZDATEN", intervallInfo)`; im **Erfolgsfall** `autoResolve(orgId, "BILANZMODELL_KEINE_BILANZDATEN")`. `orgId` aus dem Request-Kontext.
* **Auto-Lauf** (`ZaehlerAggregationService`): im bestehenden `catch (IllegalStateException)` (ERROR-Log) zusätzlich `erfasse(org, ...)` **org-explizit**; im Erfolgs-Zweig (nach `calculateSolarDistributionForOrg`) `autoResolve(org, ...)`. Kein `getCurrentOrgId()`.
* Da `erfasse` `REQUIRES_NEW` nutzt, überlebt die Meldung den Rollback des Verteillaufs.

### Phase 7: Retention Cleanup-Job
* `SystemmeldungCleanupJob` mit `@Scheduled(cron = "${systemmeldung.retention.cron:0 0 3 * * *}")` (**ohne** `@Profile`), ruft `SystemmeldungService`/Repository-Delete mit `cutoff = now - retentionTage`.
* `application.yml`: `systemmeldung.retention.tage: 90`, `systemmeldung.retention.cron: "0 0 3 * * *"`.

### Phase 10: Frontend-Komponente
* Aufbau analog `datenbank-ansicht` (Paginierung: `page`, `size=50`, `hatMehr`, Vorherige/Nächste) + Tarif-/Mieter-Liste (sortierbare Header, Kebab).
* Filter-Leiste: Erledigt (Alle/Offene/Erledigte, Default Offene), Kategorie (Alle + vorhandene), Level (Alle/INFO/WARN/ERROR). Filter-/Sortwechsel → `page=0`, neu laden.
* `subscribe({ next, error })`; Success-Meldung 5 s, Error bleibt; nach Mutation Liste neu laden.
* Erledigt-Checkbox + Kebab „Löschen" nur bei `systemmeldungen:manage` (via Keycloak-Rollencheck, analog `EinstellungenComponent.canManageFeatureFlags`).
* Level als `.zev-status`-Badge; Meldung = `translate(meldungKey)` + (parameter ? ` ${parameter}` : '').

---

## Validierungen

### Backend
1. **Dedup-Invariante:** max. ein offener Eintrag je (`org_id`, `meldung_key`) – DB-seitig per UNIQUE-Teil-Index + service-seitiger Lookup; Race → auf Increment zurückfallen.
2. **Reopen-Konflikt:** Wieder-Öffnen abgelehnt, wenn bereits offener Eintrag gleichen `meldung_key` existiert (verständliche Meldung).
3. **Org-explizit im Hintergrund:** Erfassen/Auto-Resolve aus dem MQTT-Auto-Lauf mit expliziter `orgId` + `enableOrgFilter(orgId)`, nie `getCurrentOrgId()` (sonst `NoOrganizationException`).
4. **Entkopplung:** `erfasse` in `REQUIRES_NEW`; Fehler beim Erfassen/Auto-Resolve werden geloggt, nicht propagiert – der auslösende Verteillauf bleibt unberührt.
5. **Retention:** nur `erledigt = true` **und** `erledigt_am < cutoff` werden gelöscht; offene nie.
6. **Autorisierung:** `GET` = `systemmeldungen:read`; `PUT`/`DELETE` = `systemmeldungen:manage` (`@PreAuthorize`).

### Frontend
1. Filter-Defaults: Erledigt=Offene, Kategorie=Alle, Level=Alle; Filter/Sort-Wechsel setzt Seite 0.
2. Verwalten-Elemente (Checkbox, Kebab „Löschen") nur bei `systemmeldungen:manage` sichtbar/aktiv.
3. Alle Texte via `TranslationService`/`TranslatePipe` (DE/EN); Meldung durch Konkatenation Key + Parameter.
4. Fehleranzeige `.zev-message--error`, Erfolg `.zev-message--success`.

---

## Offene Punkte / Annahmen

1. **Annahme (Spec §8 geklärt):** Dedup nur nach `meldung_key`; erledigt+Wiederauftreten → neuer Eintrag; Meldung als Key+Parameter (Konkatenation); zwei Zeitstempel; Kategorie-Attribut+Filter; Level (INFO/WARN/ERROR) als Spalte+Filter; Menü nach „Statistik", Icon `file-text`; Auto-Resolve; Retention (Default 90 Tage); serverseitige Paginierung+Sortierung.
2. **Level-Sortierung nach Schweregrad:** `level` ist `VARCHAR` → alphabetische Sortierung (ERROR<INFO<WARN) ≠ Schweregrad. Für „Level nach Schweregrad" (ERROR>WARN>INFO) wird bei der Umsetzung eine **Rang-Ableitung** genutzt (CASE-Order in der Query oder zusätzliche Sortier-Spalte `level_rang`). Empfehlung: CASE-Order, kein Extra-Feld.
3. **`Slice` statt `Page`:** bewusst `Slice` (nur `hasNext`/`hatMehr`, kein Count) – konsistent mit Datenbank-Ansicht und performanter.
4. **Kategorie-Auswahl im Filter:** ergibt sich aus vorhandenen `kategorie`-Werten des Mandanten (zu Beginn nur „Bilanzmodell"). Annahme: eigener leichter Endpoint oder Ableitung aus der aktuellen Liste; Detail bei Umsetzung.
5. **Keycloak (Phase 8):** Realm-Änderung/Reimport führt der User aus (Umgebungshoheit); Backend/Frontend prüfen die neuen Permissions erst nach Bereitstellung.
6. **Flyway-Versionen `V86`/`V87`:** höchste bestehende ist `V85`; nächste freie zum Umsetzungszeitpunkt via `zev-db` verifizieren.
7. **Tests:** über Folge-Kommandos (`3_backend-tests`, `4_frontend-unit-tests`, `5_e2e-tests`); Schwerpunkte: Dedup/Increment, Auto-Resolve, Reopen-Konflikt, Retention-Cutoff, org-explizites Erfassen im Auto-Lauf, Paginierung/Filter, Permission-Trennung read/manage.
