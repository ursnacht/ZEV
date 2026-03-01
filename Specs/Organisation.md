# Organisation als eigene DB-Tabelle (Mandanten-Entkopplung von Keycloak-UUID)

## 1. Ziel & Kontext - Warum wird das Feature benötigt?

* **Was soll erreicht werden:** Eine neue Tabelle `zev.organisation` wird als interner Ankerpunkt für jeden Mandanten eingeführt. Alle bestehenden Tabellen, die aktuell direkt die Keycloak-UUID als `org_id UUID` speichern, werden auf einen Fremdschlüssel (`org_id BIGINT`) zu dieser neuen Tabelle umgestellt.
* **Warum machen wir das:** Die `org_id` in allen Datentabellen ist aktuell direkt die UUID der Keycloak-Organisation. Falls diese UUID in Keycloak ändert (z.B. durch Realm-Neuerstellung, Migration auf eine andere Keycloak-Instanz oder versehentliches Löschen/Neuanlegen einer Organisation), stimmt die UUID im JWT nicht mehr mit den gespeicherten `org_id`-Werten überein. Der Mandant verliert damit Zugriff auf alle seine Daten. Mit einer eigenen `organisation`-Tabelle ist die Keycloak-UUID entkoppelt: bei einer UUID-Änderung muss nur ein einzelner Datensatz angepasst werden — alle Daten bleiben korrekt verknüpft.
* **Aktueller Stand:** Sechs Tabellen (`einheit`, `messwerte`, `tarif`, `metriken`, `mieter`, `einstellungen`) speichern direkt `org_id UUID`. Der `OrganizationInterceptor` extrahiert die UUID aus dem JWT-Token und übergibt sie als Filterparameter. Der Hibernate-Filter `orgFilter` ist auf `java.util.UUID` typisiert.

---

## 2. Funktionale Anforderungen (FR)

### FR-1: Neue Tabelle `zev.organisation`

Eine neue Tabelle wird angelegt mit:
- `id BIGINT` — interner Primärschlüssel (Surrogatschlüssel, technische ID)
- `keycloak_org_id UUID NOT NULL UNIQUE` — die Keycloak-Organisations-UUID (kann aktualisiert werden)
- `name VARCHAR(255)` — der Alias/Name der Organisation aus dem Keycloak-JWT (`organizations`-Claim)
- `erstellt_am TIMESTAMP NOT NULL` — Erstellungszeitpunkt

### FR-2: Umstellung der bestehenden Tabellen

Alle sechs betroffenen Tabellen werden so migriert, dass `org_id` vom Typ `UUID` auf `BIGINT` (FK auf `organisation.id`) wechselt:

| Tabelle | Bisherige Spalte | Neue Spalte |
|---------|-----------------|-------------|
| `zev.einheit` | `org_id UUID NOT NULL` | `org_id BIGINT NOT NULL` → FK auf `zev.organisation.id` |
| `zev.messwerte` | `org_id UUID NOT NULL` | `org_id BIGINT NOT NULL` → FK auf `zev.organisation.id` |
| `zev.tarif` | `org_id UUID NOT NULL` | `org_id BIGINT NOT NULL` → FK auf `zev.organisation.id` |
| `zev.metriken` | `org_id UUID NOT NULL` | `org_id BIGINT NOT NULL` → FK auf `zev.organisation.id` |
| `zev.mieter` | `org_id UUID NOT NULL` | `org_id BIGINT NOT NULL` → FK auf `zev.organisation.id` |
| `zev.einstellungen` | `org_id UUID NOT NULL UNIQUE` | `org_id BIGINT NOT NULL UNIQUE` → FK auf `zev.organisation.id` |

### FR-3: Datenmigration

Bestehende Datensätze müssen korrekt migriert werden:
1. Für jede distinct `org_id UUID` in den bestehenden Tabellen wird ein Eintrag in `zev.organisation` erzeugt (`keycloak_org_id` = die bisherige UUID, `name` = UUID als String, da der Name nicht bekannt ist).
2. Die `org_id`-Spalten in allen sechs Tabellen werden auf den neuen `BIGINT`-Fremdschlüssel umgeschrieben.
3. Die Datenmigration erfolgt vollständig via Flyway (kein manueller Schritt).

### FR-4: Auto-Provisioning bei erstem Login

Wenn ein Benutzer sich einloggt und dessen Keycloak-Organisations-UUID noch nicht in `zev.organisation` vorhanden ist, wird die Organisation automatisch angelegt (Auto-Provisioning). Der Organisationsname aus dem JWT-`organizations`-Claim (Alias) wird als Name gespeichert.

### FR-5: Anpassung Interceptor und Context-Service

- `OrganizationInterceptor`: Nach Extraktion der Keycloak-UUID → Lookup in `organisation`-Tabelle anhand `keycloak_org_id` → interne `id` (BIGINT) in `OrganizationContextService` setzen.
- `OrganizationContextService`: Speichert neu `Long currentOrgId` und `List<Long> availableOrgIds` (statt UUID).

### FR-6: Keycloak-UUID aktualisieren (Admin-Funktion)

Falls sich die UUID einer Organisation in Keycloak ändert, muss es möglich sein, den Wert `keycloak_org_id` in der `organisation`-Tabelle zu aktualisieren, ohne dass Daten verloren gehen. Dies geschieht über einen Backend-Endpunkt, der nur für System-Administratoren zugänglich ist (z.B. via Datenbank oder geschützter API).

---

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

* [ ] Die Tabelle `zev.organisation` existiert mit den Spalten `id`, `keycloak_org_id`, `name`, `erstellt_am`.
* [ ] Alle sechs Datentabellen haben `org_id BIGINT NOT NULL` mit korrektem FK auf `organisation.id`.
* [ ] Bestehende Datensätze sind nach der Migration korrekt mit der neuen `organisation.id` verknüpft — keine Daten gehen verloren.
* [ ] Ein Login mit einem bekannten Testbenutzer (Organisation bereits in DB) funktioniert korrekt; Daten werden mandantenspezifisch angezeigt.
* [ ] Ein erster Login mit einer neuen Keycloak-Organisation (noch nicht in DB) legt automatisch einen Eintrag in `zev.organisation` an (Auto-Provisioning).
* [ ] Nach dem Update von `keycloak_org_id` für eine Organisation (UUID geändert) hat der Benutzer wieder vollen Zugriff auf alle bisherigen Daten — ohne Datenmigration in den Datentabellen.
* [ ] Der `orgFilter` von Hibernate funktioniert weiterhin korrekt (BIGINT-Filterparameter statt UUID).
* [ ] Das Keycloak-`organizations`-Claim bleibt die alleinige Quelle für die Keycloak-UUID; die interne `id` wird ausschliesslich intern verwendet.

---

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Der Lookup der Organisation anhand `keycloak_org_id` erfolgt bei jedem Request. Die Spalte `keycloak_org_id` hat einen eindeutigen Index → O(1)-Lookup.
* Da das Ergebnis des Lookups pro Request einmalig benötigt wird, kann es im `OrganizationContextService` (Request-Scope) gecacht werden — kein zusätzlicher Caffeine-Cache nötig.

### NFR-2: Sicherheit
* Der Endpunkt zum Aktualisieren von `keycloak_org_id` ist ausschliesslich für System-Administratoren zugänglich (nicht über `zev_admin`-Rolle, sondern über direkten DB-Zugriff oder separaten Admin-Endpunkt mit Sonderschutz).
* Auto-Provisioning darf nur bei valider Keycloak-Organisation aus dem JWT erfolgen; direkte API-Aufrufe ohne JWT werden weiterhin abgelehnt.

### NFR-3: Kompatibilität
* Der Spaltenname `org_id` bleibt in allen Tabellen erhalten — nur der Typ ändert sich von `UUID` zu `BIGINT`. Dadurch bleibt die Hibernate-Filter-Bedingung `org_id = :orgId` syntaktisch unverändert.
* Das Externe-API-Verhalten (REST-Responses) ändert sich nicht; `org_id` wird nach aussen nicht exponiert.
* Alle bestehenden Daten bleiben vollständig erhalten.

---

## 5. Edge Cases & Fehlerbehandlung

* **Organisation nicht in DB und Auto-Provisioning schlägt fehl:** Request wird mit 403 `NO_ORGANIZATION` abgewiesen (wie heute bei fehlendem `organizations`-Claim).
* **Gleichzeitige erste Logins derselben neuen Organisation (Race Condition):** Unique Constraint auf `keycloak_org_id` verhindert doppeltes Anlegen; der zweite Insert schlägt fehl → Retry oder `INSERT ... ON CONFLICT DO NOTHING` mit anschliessendem Select.
* **Mehrere Organisationen im JWT:** Derzeit wird nur die erste Organisation verwendet (bestehendes Verhalten). Wenn mehrere Organisationen im JWT vorhanden sind, wird für jede der Lookup/Provisioning durchgeführt; die erste bleibt aktiv.
* **`keycloak_org_id` im JWT ist keine gültige UUID:** Wie heute → `IllegalArgumentException` → Request wird abgewiesen.
* **`org_id` in Datentabelle zeigt auf nicht existierende Organisation:** Darf durch FK-Constraint nicht vorkommen. Alte Testdaten ohne Migration würden zur Verletzung des FK führen → wird durch die Flyway-Migration verhindert.
* **DB nicht erreichbar beim Lookup:** Spring-Exception wird vom `GlobalExceptionHandler` als 500 zurückgegeben.

---

## 6. Abhängigkeiten & betroffene Funktionalität

* **Voraussetzungen:** Alle bisherigen Flyway-Migrationen (bis V43) sind eingespielt.
* **Betroffener Code (Backend):**
  * `entity/package-info.java` — `@ParamDef(type = java.util.UUID.class)` → `@ParamDef(type = Long.class)`
  * `entity/Einheit.java`, `Messwerte.java`, `Tarif.java`, `Metrik.java`, `Mieter.java`, `Einstellungen.java` — `private UUID orgId` → `private Long orgId`
  * `service/OrganizationContextService.java` — `UUID` → `Long`
  * `service/HibernateFilterService.java` — Parametertyp `UUID` → `Long`
  * `config/OrganizationInterceptor.java` — Keycloak-UUID → DB-Lookup → interne `Long`-ID
  * `repository/EinstellungenRepository.java` — `findByOrgId(UUID)` → `findByOrgId(Long)`
  * `repository/MetrikRepository.java` — `findByNameAndOrgId(String, UUID)` → `findByNameAndOrgId(String, Long)`
  * `service/EinheitService.java`, `MesswerteService.java`, `TarifService.java`, `MieterService.java`, `MetricsService.java`, `EinstellungenService.java` — alle `getCurrentOrgId()` Aufrufe liefern neu `Long`
  * Neu: `entity/Organisation.java`, `repository/OrganisationRepository.java`, `service/OrganisationService.java`
* **Betroffener Code (Frontend):** Keiner — `org_id` wird vom Frontend nicht verwendet.
* **Datenmigration:** Ja, mehrere Flyway-Migrationen erforderlich (Details in Umsetzungsplan).

---

## 7. Abgrenzung / Out of Scope

* **Organisations-Verwaltung via UI:** Es gibt keine Benutzeroberfläche zum Anlegen, Bearbeiten oder Löschen von Organisationen — dies geschieht weiterhin in Keycloak. Nur `keycloak_org_id` kann bei Bedarf technisch aktualisiert werden.
* **Mandantenwechsel im Frontend:** Ein Benutzer mit mehreren Organisationen kann nicht über die UI zwischen Mandanten wechseln. Dies bleibt Out of Scope.
* **Organisations-spezifische Metadaten:** Weitere Felder (Adresse, Kontakt etc.) für die Organisation werden nicht hinzugefügt.
* **REST-Endpunkt zur Organisation-Verwaltung:** Kein öffentlicher CRUD-Controller für Organisationen — lediglich der interne Auto-Provisioning-Mechanismus.
* **Keycloak-Synchronisation:** Die Anwendung synchronisiert nicht aktiv mit Keycloak; die Keycloak-UUID wird nur beim Login aus dem JWT gelesen.

---

## 8. Offene Fragen

* **Auto-Provisioning: opt-in oder opt-out?** Soll eine neue Keycloak-Organisation automatisch in die DB aufgenommen werden (opt-in), oder soll eine manuelle Freischaltung erforderlich sein? Empfehlung: Auto-Provisioning, da dies dem bisherigen Verhalten am nächsten kommt.
* **Organisations-Name aktualisieren:** Soll der `name` in `zev.organisation` bei jedem Login aktualisiert werden (falls der Alias in Keycloak geändert wurde)? Empfehlung: Ja, mit `UPDATE organisation SET name = ? WHERE keycloak_org_id = ?` bei jedem Login.
* **`INSERT ... ON CONFLICT`-Strategie:** PostgreSQL-spezifisches `ON CONFLICT DO UPDATE` oder `ON CONFLICT DO NOTHING` + anschliessendes `SELECT`? Empfehlung: `INSERT ... ON CONFLICT (keycloak_org_id) DO UPDATE SET name = EXCLUDED.name` (Upsert).
* **Soll `organisation` dem `orgFilter` unterliegen?** Nein — die `organisation`-Tabelle ist technisch, nicht mandantenspezifisch, und wird ohne Filter abgefragt.
