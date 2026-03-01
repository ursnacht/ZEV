# Akzeptanzkriterien-Check: Einstellungen-Merger in Organisation-Tabelle

## Ergebnis: 10/10 Kriterien erfüllt

| # | Kriterium | Status | Nachweis |
|---|-----------|--------|----------|
| 1 | Tabelle `zev.einstellungen` existiert nach Migration nicht mehr | **OK** | DB-Query auf `information_schema.tables` gibt leeres Resultat; `V47` droppt die Tabelle (Z. 11) |
| 2 | `zev.organisation` besitzt Spalte `konfiguration JSONB` (nullable) | **OK** | DB: `column_name=konfiguration`, `data_type=jsonb`, `is_nullable=YES` |
| 3 | Bestehende Einstellungs-Daten korrekt in `organisation.konfiguration` migriert | **OK** | DB: Organisation „Mut13" (id=1) hat vollständige konfiguration (iban, steller, zahlungsfrist) |
| 4 | `GET /api/einstellungen` → `200 OK` mit korrekten Daten | **OK** | `EinstellungenController:33-42` gibt `ResponseEntity.ok()` wenn Service-Resultat != null; Test `getEinstellungen_Found_ReturnsOk` grün |
| 5 | `GET /api/einstellungen` → `204 No Content` wenn `konfiguration IS NULL` | **OK** | `EinstellungenController:36-39`: `if (einstellungen == null) return noContent()`; Service filtert mit `.filter(org -> org.getKonfiguration() != null)`; Test `getEinstellungen_NotConfigured_Returns204NoContent` grün |
| 6 | `PUT /api/einstellungen` speichert korrekt in `organisation.konfiguration` | **OK** | `EinstellungenService:81-82`: `org.setKonfiguration(toJson(...))` + `organisationRepository.save(org)`; Test `saveEinstellungen_Speichert_Konfiguration` grün |
| 7 | `Einstellungen.java` und `EinstellungenRepository.java` existieren nicht mehr | **OK** | File-Check: beide Pfade mit „No such file or directory"; Grep auf Imports ergibt keine Treffer im Main-Code |
| 8 | `EinstellungenService` verwendet `OrganisationRepository`, nicht `EinstellungenRepository` | **OK** | `EinstellungenService.java:25`: `private final OrganisationRepository organisationRepository`; kein Import auf EinstellungenRepository |
| 9 | Alle Backend-Unit-Tests laufen grün (`mvn test`) | **OK** | `mvn test`: 242 Tests, Failures: 0, Errors: 0 — BUILD SUCCESS |
| 10 | Frontend verhält sich identisch — keine sichtbaren Änderungen | **OK** | Controller-API unverändert (`/api/einstellungen`, gleiche DTOs, gleiche Status-Codes); keine Frontend-Dateien geändert |

---

## Erfüllte Kriterien

- [x] Die Tabelle `zev.einstellungen` existiert nach der Migration nicht mehr.
- [x] Die Tabelle `zev.organisation` besitzt die Spalte `konfiguration JSONB` (nullable).
- [x] Bestehende Einstellungs-Daten sind nach der Migration korrekt in `organisation.konfiguration` vorhanden — keine Daten gehen verloren.
- [x] `GET /api/einstellungen` liefert `200 OK` mit korrekten Daten für einen Mandanten, dessen Einstellungen konfiguriert sind.
- [x] `GET /api/einstellungen` liefert `204 No Content` für einen Mandanten ohne Einstellungen (`konfiguration IS NULL`).
- [x] `PUT /api/einstellungen` speichert die Einstellungen korrekt in `organisation.konfiguration`.
- [x] Die Klassen `Einstellungen.java` und `EinstellungenRepository.java` existieren nicht mehr im Backend.
- [x] `EinstellungenService` verwendet `OrganisationRepository`, nicht `EinstellungenRepository`.
- [x] Alle Backend-Unit-Tests laufen grün (`mvn test`).
- [x] Das Frontend verhält sich identisch wie vor dem Merge — keine sichtbaren Änderungen.

## Nicht erfüllte Kriterien

*Keine.*

---

## Zusätzliche Befunde (NFR & Edge Cases)

| Aspekt | Status | Nachweis |
|--------|--------|----------|
| **NFR-2 Sicherheit**: `/api/einstellungen` auf `zev_admin` beschränkt | OK | `EinstellungenController:17`: `@PreAuthorize("hasRole('zev_admin')")` |
| **NFR-2 Mandantentrennung**: `getCurrentOrgId()` für Isolation | OK | `EinstellungenService:42,75`: `organizationContextService.getCurrentOrgId()` in beiden Lese-/Schreibmethoden; Test `getEinstellungen_VerwendetCurrentOrgId` + `saveEinstellungen_VerwendetCurrentOrgId` grün |
| **NFR-3 Kompatibilität**: API-Pfade und DTOs unverändert | OK | `EinstellungenController.java` + `EinstellungenDTO.java` unmodifiziert |
| **Edge Case**: ungültiges JSON → `IllegalArgumentException` | OK | `EinstellungenService:114-116`; Test `getEinstellungen_UngueltigesJson_ThrowsIllegalArgumentException` grün |
| **Edge Case**: Organisation nicht in DB → `IllegalStateException` in `saveEinstellungen` | OK | `EinstellungenService:78-79`: `orElseThrow(() -> new IllegalStateException(...))`; Test `saveEinstellungen_OrganisationNichtGefunden_ThrowsIllegalStateException` grün |
| **Integrationstests**: `konfiguration`-Feld in DB persistierbar und aktualisierbar | OK | `OrganisationRepositoryIT`: `konfiguration_WirdGespeichertUndGeladen`, `konfiguration_IstNullbar`, `konfiguration_WirdAktualisiert` — alle grün |
