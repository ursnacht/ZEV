# Feature-Flag-Upload – Umsetzungsplan

> Kombinierter Plan für die Feature-Flag-**Infrastruktur** (`Specs/FeatureFlag.md`) und deren erste konkrete **Anwendung** auf den Messdatenupload (`Specs/Feature-Flag-Upload.md`). Beide werden gemeinsam umgesetzt, da der Upload-Flag ohne die Infrastruktur nicht lauffähig ist.

## Zusammenfassung
Es wird eine leichtgewichtige, selbst gehostete Feature-Flag-Lösung eingeführt, mit der einzelne Features pro Mandant (`org_id`) zur Laufzeit ein-/ausgeschaltet werden können. Die bekannten Flags werden zentral im Enum `FeatureFlag` deklariert (Key, globaler Default, Beschreibungs-Übersetzungs-Key); mandantenspezifische Überschreibungen werden in einer neuen JSONB-Spalte `feature_flags` auf `zev.organisation` gespeichert und via Caffeine gecacht. Als erste Anwendung wird der Messdatenupload über das Flag `MESSWERTE_UPLOAD` (Default `true`) gesteuert: Bei inaktivem Flag wird der Menüeintrag ausgeblendet, die Route `/upload` per Guard gesperrt und der Endpunkt `POST /api/messwerte/upload` mit HTTP 403 abgelehnt. Die Verwaltung der Flags erfolgt integriert in der bestehenden Seite `/einstellungen` (nur `zev_admin`).

## Betroffene Komponenten

### Neu (Backend)
| Datei | Zweck |
|-------|-------|
| `backend-service/src/main/resources/db/migration/V67__Add_FeatureFlags_Column.sql` | JSONB-Spalte `feature_flags` auf `zev.organisation` |
| `backend-service/src/main/resources/db/migration/V68__Add_FeatureFlag_Translations.sql` | Übersetzungen (Abschnittstitel, Beschreibungs-Keys, Fehlermeldung) |
| `backend-service/src/main/java/ch/nacht/entity/FeatureFlag.java` | Enum: Flag-Registry (Key, Default, Beschreibungs-Key) |
| `backend-service/src/main/java/ch/nacht/service/FeatureFlagService.java` | Effektive Auflösung, Persistierung der Überschreibungen, Caching |
| `backend-service/src/main/java/ch/nacht/controller/FeatureFlagController.java` | REST-Endpunkte unter `/api/feature-flags` |
| `backend-service/src/main/java/ch/nacht/dto/FeatureFlagDTO.java` | Admin-Sicht: key, beschreibungKey, defaultWert, effektiv, quelle |
| `backend-service/src/main/java/ch/nacht/dto/FeatureFlagUpdateDTO.java` | Request-Body für `PUT` (`{ "enabled": boolean }`) |
| `backend-service/src/main/java/ch/nacht/exception/FeatureDisabledException.java` | Wird bei gesperrtem Upload geworfen → 403 (via `GlobalExceptionHandler`) |

### Geändert (Backend)
| Datei | Änderung |
|-------|----------|
| `backend-service/src/main/java/ch/nacht/entity/Organisation.java` | Neue JSONB-Property `featureFlags` (`Map<String,Boolean>` als JSON) |
| `backend-service/src/main/java/ch/nacht/config/CacheConfig.java` | Cache `featureFlags` ergänzen |
| `backend-service/src/main/java/ch/nacht/controller/MesswerteController.java` | `uploadCsv`: Flag-Prüfung `MESSWERTE_UPLOAD` → 403 bei inaktiv |
| `backend-service/src/main/java/ch/nacht/exception/GlobalExceptionHandler.java` | Mapping `FeatureDisabledException` → HTTP 403 |

### Neu (Frontend)
| Datei | Zweck |
|-------|-------|
| `frontend-service/src/app/models/feature-flag.model.ts` | Interfaces (effektive Map + Admin-DTO) |
| `frontend-service/src/app/services/feature-flag.service.ts` | Laden/Halten der Flags, `isEnabled('KEY')`, Admin-Calls |
| `frontend-service/src/app/directives/feature-flag.directive.ts` | Struktur-Direktive `*appFeature="'KEY'"` |
| `frontend-service/src/app/guards/feature-flag.guard.ts` | Route-Guard: Redirect bei inaktivem Flag |

### Geändert (Frontend)
| Datei | Änderung |
|-------|----------|
| `frontend-service/src/app/app.routes.ts` | Route `/upload`: `FeatureFlagGuard` + `data: { featureFlag: 'MESSWERTE_UPLOAD' }` |
| `frontend-service/src/app/components/navigation/navigation.component.html` | Upload-Menüeintrag mit `*appFeature="'MESSWERTE_UPLOAD'"` |
| `frontend-service/src/app/components/einstellungen/einstellungen.component.ts` | Feature-Flag-Liste laden + toggeln/zurücksetzen |
| `frontend-service/src/app/components/einstellungen/einstellungen.component.html` | Neuer Abschnitt „Feature-Flags" (Tabelle mit Toggles) |

## Phasen-Tabelle

| Status | Phase                          | Beschreibung                                                                                                                        |
|--------|--------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
|  [x]   | 1. DB-Migration (Spalte)       | `V67__Add_FeatureFlags_Column.sql`: `ALTER TABLE zev.organisation ADD COLUMN feature_flags jsonb` (nullable, Default NULL)          |
|  [x]   | 2. Entity-Erweiterung          | `Organisation.java`: Property `featureFlags` (`@JdbcTypeCode(SqlTypes.JSON)`, `columnDefinition = "jsonb"`) + Getter/Setter          |
|  [x]   | 3. Flag-Registry (Enum)        | `FeatureFlag.java`: Enum mit `MESSWERTE_UPLOAD(true, "FEATURE_FLAG_MESSWERTE_UPLOAD")`; Felder `defaultEnabled`, `beschreibungKey`   |
|  [x]   | 4. DTOs                        | `FeatureFlagDTO` (key, beschreibungKey, defaultWert, effektiv, quelle DEFAULT/OVERRIDE) + `FeatureFlagUpdateDTO` (`enabled`)          |
|  [x]   | 5. Cache-Config                | `CacheConfig.java`: Cache `featureFlags` zum `CaffeineCacheManager` ergänzen (bestehende TTL 15 Min / max 100 gilt)                  |
|  [x]   | 6. Backend-Service             | `FeatureFlagService`: effektive Flags (`getEffectiveFlags`), Admin-Sicht, `setOverride`/`removeOverride`; `@Cacheable`/`@CacheEvict` |
|  [x]   | 7. Backend-Controller          | `FeatureFlagController`: `GET /api/feature-flags` (zev), `GET /admin`, `PUT/{key}`, `DELETE/{key}` (zev_admin); unbekannter Key → 400 |
|  [x]   | 8. Upload-Gate + Exception     | `FeatureDisabledException` + `GlobalExceptionHandler`→403; `MesswerteController#uploadCsv` prüft `MESSWERTE_UPLOAD`                   |
|  [x]   | 9. Frontend Model + Service    | `feature-flag.model.ts`, `feature-flag.service.ts` (`load()`, `isEnabled()`, `getAdminFlags()`, `setFlag()`, `resetFlag()`)          |
|  [x]   | 10. Frontend App-Init          | Abweichung: kein `provideAppInitializer`. Analog `TranslationService` im Service-Konstruktor geladen + reaktives Signal (siehe Annahmen) |
|  [x]   | 11. Struktur-Direktive         | `feature-flag.directive.ts`: `*appFeature="'KEY'"` (Signal-Input + `effect()`, reagiert auf Laden der Flags)                         |
|  [x]   | 12. Route-Guard                | `feature-flag.guard.ts`: liest `route.data['featureFlag']`, lädt Flags, Redirect auf `/startseite` bei inaktivem Flag                |
|  [x]   | 13. Routing                    | `app.routes.ts`: `/upload` um `FeatureFlagGuard` + `data.featureFlag` erweitert (bestehender `AuthGuard`/roles bleibt)               |
|  [x]   | 14. Navigation                 | `navigation.component.html`: Upload-`<li>` mit `*appFeature="'MESSWERTE_UPLOAD'"` umschlossen                                        |
|  [x]   | 15. Einstellungen-Integration  | `einstellungen.component.*`: Abschnitt „Feature-Flags" mit Toggle-Liste (Design System `zev-table`/`zev-checkbox`/`zev-status`)      |
|  [x]   | 16. Übersetzungen              | `V68__Add_FeatureFlag_Translations.sql`: DE/EN, `ON CONFLICT (key) DO NOTHING`                                                       |

## Validierungen

### Backend
* **Unbekannter Flag-Key** bei `PUT`/`DELETE /api/feature-flags/{key}` → HTTP 400 (Key nicht im `FeatureFlag`-Enum).
* **Request-Body `PUT`**: `enabled` ist Pflichtfeld vom Typ `boolean` (`@NotNull`); fehlend/ungültig → HTTP 400.
* **Upload-Gate**: `MesswerteController#uploadCsv` prüft `featureFlagService.isEnabled(MESSWERTE_UPLOAD)`; inaktiv → `FeatureDisabledException` → HTTP 403 mit übersetzbarer Meldung (`FEATURE_FLAG_DEAKTIVIERT`).
* **Mandantentrennung**: `org_id` ausschliesslich aus `OrganizationContextService` (JWT), nie aus Request/Body.
* **Autorisierung**: `GET /api/feature-flags` = `zev`; `GET /admin`, `PUT`, `DELETE` = `zev_admin` (`@PreAuthorize`).
* **Effektiver Wert**: Override (falls vorhanden) → sonst Enum-Default → sonst `false`.
* **Robustheit**: NULL/leere/korrupte `feature_flags`-Spalte → ERROR loggen, auf Defaults zurückfallen (kein 500).

### Frontend
* **Toggle-Zustand** spiegelt den effektiven Wert je Flag (inkl. Quelle DEFAULT/OVERRIDE).
* **`isEnabled('KEY')`** liefert bei unbekanntem/nicht geladenem Key konservativ `false`.
* **Ladefehler** der Flags (Netzwerk) → `zev-message--error` (bleibt bis dismissed), App bleibt nutzbar; Upload-Menü/-Route konservativ aus.
* **Guard**: bei inaktivem Flag Redirect auf `/startseite` (kein leerer Screen).
* **Erfolg** beim Speichern/Zurücksetzen → `zev-message--success` (Auto-Dismiss nach 5s).

## Offene Punkte / Annahmen
* **Migrationsnummern V67/V68**: via `zev-db` MCP verifiziert – höchste angewendete Migration ist `V66`, keine fehlgeschlagenen Migrationen, Spalte `feature_flags` existiert noch nicht → V67/V68 sind frei und kollisionsfrei.
* **HTTP-Status gesperrter Upload**: Annahme **403** (Forbidden). Alternativen 409/404 wurden verworfen (Spec Abschnitt 8).
* **Umfang des Upload-Gates**: Annahme – nur `POST /api/messwerte/upload` wird gesperrt; abhängige Aktionen (`calculate-distribution`) bleiben offen (Spec Abschnitt 8).
* **App-Init-Mechanismus (umgesetzt)**: Statt `provideAppInitializer` folgt der `FeatureFlagService` dem bestehenden Projekt-Muster des `TranslationService`: Laden im Service-Konstruktor (nach Keycloak-Login, lazy bei erster Injection) und reaktiver `signal`-Zustand. Das vermeidet das Token-Timing-Problem eines App-Initializers und lässt Navigation (`*appFeature`) automatisch aktualisieren, sobald die Flags geladen sind. `app.config.ts` bleibt unverändert.
* **Verwaltungs-UI**: integriert in `/einstellungen` (keine eigene Route `/feature-flags`, kein separater Menüeintrag) – entschieden, in beiden Specs eingearbeitet.
* **`feature_flags` vs. `konfiguration`**: bewusst getrennte Spalten; `EinstellungenService` und die `konfiguration`-Spalte bleiben unangetastet.
* **Cache-Invalidierung über Instanzen**: Einzelinstanz angenommen; lokales `@CacheEvict` + 15-Min-TTL als Backstop genügen (keine cluster-weite Invalidierung).
* **Statische `@ConditionalOnProperty`-Schalter** (`features.*`): nicht Teil dieser Umsetzung (nur bei Bedarf, deploy-fest) – Out of Scope für den Upload.
