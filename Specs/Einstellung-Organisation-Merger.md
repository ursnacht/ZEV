# Einstellungen-Merger in Organisation-Tabelle

## 1. Ziel & Kontext - Warum wird das Feature benötigt?

* **Was soll erreicht werden:** Die Tabelle `zev.einstellungen` wird aufgelöst. Die `konfiguration`-Spalte (JSONB) wird direkt in die Tabelle `zev.organisation` verschoben.
* **Warum machen wir das:** Die Tabelle `einstellungen` hat eine 1:1-Beziehung zu `organisation` (Unique-FK auf `org_id`). Diese Redundanz ist unnötig — die Konfiguration ist ein Attribut der Organisation, nicht eine eigene Entität. Durch die Zusammenführung entfällt ein Join, der Code wird einfacher, und die konzeptuelle Integrität wird verbessert.
* **Aktueller Stand:**
  * `zev.einstellungen` (`id`, `org_id BIGINT UNIQUE FK → organisation.id`, `konfiguration JSONB`)
  * `zev.organisation` (`id`, `keycloak_org_id UUID`, `name`, `erstellt_am`)
  * Backend: `Einstellungen`-Entity, `EinstellungenRepository`, `EinstellungenService` (liest/schreibt `konfiguration` via `einstellungenRepository.findByOrgId`)
  * API: `GET /api/einstellungen` und `PUT /api/einstellungen` (unverändert behalten)
  * Frontend: greift ausschliesslich via `/api/einstellungen` zu — keine Änderungen nötig

---

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Datenbankschema

1. Neue Spalte `konfiguration JSONB` wird in `zev.organisation` ergänzt (nullable, da nicht jede Organisation zwingend Einstellungen hat).
2. Die bestehenden Einstellungs-Datensätze werden per Flyway-Migration aus `zev.einstellungen` in `zev.organisation.konfiguration` übertragen.
3. Die Tabelle `zev.einstellungen` sowie ihre Sequenz `zev.einstellungen_seq` werden gelöscht.

### FR-2: Backend — Entfernung der Einstellungen-Artefakte

* `entity/Einstellungen.java` wird gelöscht.
* `repository/EinstellungenRepository.java` wird gelöscht.
* `EinstellungenService.java` wird angepasst: verwendet neu `OrganisationRepository.findById(orgId)` statt `EinstellungenRepository.findByOrgId(orgId)`. Liest und schreibt `konfiguration` direkt am `Organisation`-Objekt.
* `Organisation.java` erhält das neue Feld `konfiguration` (String, JSONB-Mapping).
* `EinstellungenController.java` bleibt unverändert (gleiche API).
* `EinstellungenDTO.java` bleibt unverändert; das Feld `id` enthält neu die `organisation.id`.

### FR-3: Backend — EinstellungenService-Logik nach dem Merge

* `getEinstellungen()`: lädt `Organisation` per `organisationRepository.findById(orgId)`. Falls kein Datensatz oder `konfiguration == null` → gibt `null` zurück (204-Verhalten bleibt).
* `getEinstellungenOrThrow()`: wie bisher, wirft `IllegalStateException` wenn `konfiguration == null`.
* `saveEinstellungen(dto)`: lädt `Organisation`, setzt `konfiguration` (JSON), speichert via `organisationRepository.save(org)`. Da die Organisation immer existiert (Auto-Provisioning), entfällt die Create-vs-Update-Logik für Einstellungen — es wird immer geupdated. `EinstellungenDTO.id` gibt neu die `organisation.id` zurück.

### FR-4: Tests

* `EinstellungenServiceTest.java`: wird angepasst — Mock von `EinstellungenRepository` durch Mock von `OrganisationRepository` ersetzen. Testfälle für Create-vs-Update-Logik entfallen oder werden angepasst (kein separates Create mehr).
* `EinstellungenControllerTest.java`: keine inhaltlichen Änderungen (Service-Interface bleibt gleich), lediglich das entfernte `EinstellungenRepository` als `@MockitoBean` entfernen falls vorhanden.
* `OrganisationRepositoryIT.java`: Integrationstest für das neue `konfiguration`-Feld ergänzen.
* `OrganisationServiceTest.java`: prüft, dass `findOrCreate` die `konfiguration` nicht überschreibt (Feld bleibt beim Update unberührt).

---

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

* [ ] Die Tabelle `zev.einstellungen` existiert nach der Migration nicht mehr.
* [ ] Die Tabelle `zev.organisation` besitzt die Spalte `konfiguration JSONB` (nullable).
* [ ] Bestehende Einstellungs-Daten sind nach der Migration korrekt in `organisation.konfiguration` vorhanden — keine Daten gehen verloren.
* [ ] `GET /api/einstellungen` liefert `200 OK` mit korrekten Daten für einen Mandanten, dessen Einstellungen konfiguriert sind.
* [ ] `GET /api/einstellungen` liefert `204 No Content` für einen Mandanten ohne Einstellungen (`konfiguration IS NULL`).
* [ ] `PUT /api/einstellungen` speichert die Einstellungen korrekt in `organisation.konfiguration`.
* [ ] Die Klassen `Einstellungen.java` und `EinstellungenRepository.java` existieren nicht mehr im Backend.
* [ ] `EinstellungenService` verwendet `OrganisationRepository`, nicht `EinstellungenRepository`.
* [ ] Alle Backend-Unit-Tests laufen grün (`mvn test`).
* [ ] Das Frontend verhält sich identisch wie vor dem Merge — keine sichtbaren Änderungen.

---

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* `organisationRepository.findById(orgId)` ist ein Primary-Key-Lookup (O(1)). Kein Performance-Verlust gegenüber dem heutigen `findByOrgId`-Index-Lookup.

### NFR-2: Sicherheit
* Der API-Endpunkt `/api/einstellungen` bleibt auf Rolle `zev_admin` beschränkt (unverändert).
* Mandantentrennung: `OrganizationContextService.getCurrentOrgId()` stellt sicher, dass immer nur die eigene Organisation geladen wird.

### NFR-3: Kompatibilität
* Die REST-API (`GET /api/einstellungen`, `PUT /api/einstellungen`) bleibt vollständig kompatibel — gleiche Pfade, gleiche DTOs, gleiche HTTP-Status-Codes.
* `konfiguration` ist in `organisation` nullable. Bestehende Organisationen ohne Einstellungen behalten `NULL` bis zur ersten Konfiguration.
* Alle anderen Services, die `EinstellungenService.getEinstellungenOrThrow()` aufrufen (z.B. `RechnungPdfService`), müssen nicht angepasst werden.

---

## 5. Edge Cases & Fehlerbehandlung

* **Organisation nicht in DB:** Da Auto-Provisioning beim ersten Login erfolgt, ist die `organisation`-Zeile immer vorhanden wenn ein authentifizierter Request eintrifft. `findById` liefert immer einen Wert — kein `Optional.empty()`-Fall möglich. Falls doch (z.B. Datenfehler): `IllegalStateException` (gleich wie heute bei fehlender Konfiguration).
* **Konfiguration `null` (noch nie gespeichert):** Entspricht dem bisherigen "keine Einstellungen"-Zustand → `getEinstellungen()` gibt `null` zurück, Controller antwortet `204`.
* **Ungültiges JSON in `konfiguration`:** Gleiche Fehlerbehandlung wie bisher (`IllegalArgumentException` → 400 Bad Request via `GlobalExceptionHandler`).
* **Gleichzeitiges Speichern:** Da `saveEinstellungen` immer die bestehende `organisation`-Zeile updated (kein Insert), sind optimistische Locking-Konflikte möglich, aber werden durch Spring JPA transparent behandelt.
* **Datenmigration mit `konfiguration = NULL` in einstellungen:** Darf laut Datenbankschema (`NOT NULL` in `einstellungen`) nicht vorkommen — wird in Migration nicht gesondert behandelt.

---

## 6. Abhängigkeiten & betroffene Funktionalität

* **Voraussetzungen:** Flyway-Migration V46 (Organisation-FK-Migration) muss bereits eingespielt sein.
* **Betroffener Code (Backend):**
  * **Löschen:** `entity/Einstellungen.java`, `repository/EinstellungenRepository.java`
  * **Anpassen:** `entity/Organisation.java` (+`konfiguration`-Feld), `service/EinstellungenService.java` (Umbau auf `OrganisationRepository`)
  * **Unverändert:** `controller/EinstellungenController.java`, `dto/EinstellungenDTO.java`, `dto/RechnungKonfigurationDTO.java`
* **Betroffener Code (Tests):**
  * **Anpassen:** `service/EinstellungenServiceTest.java` (Mock-Umbau), `repository/OrganisationRepositoryIT.java` (neuer Testfall)
  * **Prüfen / leichte Anpassung:** `controller/EinstellungenControllerTest.java`
  * **Ergänzen:** `service/OrganisationServiceTest.java` (konfiguration-Feld bleibt beim Name-Update erhalten)
* **Betroffener Code (Frontend):** keiner
* **Datenmigration:** Ja — neue Flyway-Migration erforderlich (z.B. `V47__Merge_Einstellungen_Into_Organisation.sql`):
  1. `ALTER TABLE zev.organisation ADD COLUMN konfiguration JSONB;`
  2. `UPDATE zev.organisation SET konfiguration = e.konfiguration FROM zev.einstellungen e WHERE e.org_id = zev.organisation.id;`
  3. `DROP TABLE zev.einstellungen;`
  4. `DROP SEQUENCE IF EXISTS zev.einstellungen_seq;`

---

## 7. Abgrenzung / Out of Scope

* Keine Änderungen am Frontend.
* Kein Umbenennen des API-Endpunkts (bleibt `/api/einstellungen`).
* Keine Änderungen an anderen Services, die `EinstellungenService` konsumieren (`RechnungPdfService`, `RechnungService` etc.).
* Die `EinstellungenController`- und `EinstellungenDTO`-Klassen werden nicht umbenannt.
* Keine E2E-Test-Anpassungen (das Verhalten aus Sicht des Browsers ändert sich nicht).

---

## 8. Offene Fragen

* **`konfiguration` nullable oder mit Default?** Empfehlung: nullable (kein synthetischer Default-JSON), da der `null`-Zustand semantisch korrekt "nicht konfiguriert" bedeutet und bereits vom Controller korrekt als 204 behandelt wird.
* **`EinstellungenDTO.id` neu = `organisation.id`:** Wird die `id` im Frontend ausgewertet? Falls ja (z.B. für Caching), ändert sich der Wert durch die Migration. Prüfen: aktuell wird `id` im Frontend nicht ausgewertet (nur `rechnung`-Felder).
