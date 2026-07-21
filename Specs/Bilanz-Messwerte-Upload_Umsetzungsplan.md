# Bilanz-Messwerte-Upload – Umsetzungsplan

## Zusammenfassung
Der Messwerte-Upload wird um **Bilanz-Dateien** erweitert (Beispiel `docs/2026-06-Bilanz.csv`): eine Datei enthält Netzbezug und Rücklieferung des ZEV im 15-Min-Raster. Die KI erkennt die Bilanz-Datei am Dateinamen (Sentinel `BILANZ`, neues Flag `bilanz`); ein neuer Endpoint `POST /api/messwerte/upload-bilanz` bucht die Werte auf die Einheiten vom Typ `BEZUG`/`RUECKLIEFERUNG` (`total` signiert, `zev = 0`, `quelle = CSV`). Die Statistik-Vergleiche nutzen die Werte anschliessend **ohne** neuen Code.

> **Abweichung vom Standard-Template:** **Keine** neue Entity/Tabelle/Migration für Daten (Messwerte-Tabelle unverändert), **kein** neues Routing/Navigation (bestehende Upload-Seite/Controller). Nur eine Übersetzungs-Migration.

## Betroffene Komponenten

**Backend (geändert):**
- `dto/EinheitMatchResponseDTO.java` – neues Flag `bilanz` (Default `false`), inkl. Builder.
- `service/EinheitMatchingService.java` – System-Prompt um Bilanz-Abkürzung (`bilanz`) erweitern; Sentinel `BILANZ` parsen → `bilanz = true` (analog `KEINE`).
- `service/MesswerteService.java` – neue Methode `processBilanzCsvUpload(MultipartFile)`: Einheiten per Typ auflösen (`findFirstByTyp`), Header-Plausibilisierung, Parsing (Tag aus `category`, Slot fortlaufend `+15 min`), Messwerte je Einheit, Monats-Overwrite, `@CacheEvict("statistik")`.
- `controller/MesswerteController.java` – neuer Endpoint `POST /upload-bilanz` (`messwerte:write` + `MESSWERTE_UPLOAD`-Flag wie `/upload`).

**Backend (unverändert, genutzt):**
- `repository/EinheitRepository.java` – `findFirstByTyp(EinheitTyp)` existiert bereits.
- `entity/Messwerte.java`, `entity/Quelle.java` (`CSV`), `exception/FeatureDisabledException`, `GlobalExceptionHandler`.

**Frontend (geändert):**
- `services/einheit.service.ts` – `EinheitMatchResponse` um `bilanz` erweitern.
- `components/messwerte-upload/messwerte-upload.component.ts` – `UploadEntry.bilanz`; Match übernimmt `bilanz`; manuelles Markieren/Zurücksetzen; Readiness-Prüfung anpassen; Upload-Routing (Bilanz → `/upload-bilanz`, nur Datei).
- `components/messwerte-upload/messwerte-upload.component.html` – Bilanz-Label; Einheit-/Datumsauswahl bei Bilanz ausblenden; manueller Bilanz-Umschalter.
- Tests/Mocks: `messwerte-upload.component.spec.ts`, `einheit.service.spec.ts`.

**DB / i18n:**
- `db/migration/V84__Add_Bilanz_Upload_Translations.sql` – neue Keys (DE/EN, `ON CONFLICT DO NOTHING`). (Höchste bestehende Migration: `V83`.)

## Umsetzungsreihenfolge (Phasen)

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [ ] | 1. Match-DTO erweitern | `EinheitMatchResponseDTO` um `bilanz` (boolean, Default `false`) + Builder-Methode. |
| [ ] | 2. KI-Erkennung | `EinheitMatchingService`: System-Prompt um Bilanz-Abkürzung (`bilanz` → Bilanz-Datei) erweitern; Antwort-Sentinel `BILANZ` parsen → Response mit `bilanz = true` (ohne `einheitId`). Producer/Consumer-Zuordnung unverändert. |
| [ ] | 3. Bilanz-Parsing & Service | `MesswerteService.processBilanzCsvUpload(file, dateStr)`: `BEZUG`/`RUECKLIEFERUNG` per `findFirstByTyp` auflösen (fehlt eine → `IllegalArgumentException("BILANZ_EINHEIT_FEHLT")`); Header positions- **und** titelbasiert plausibilisieren (`Bezug`/`Rücklieferung`, sonst `BILANZ_CSV_UNGUELTIG`); Zeitstempel = `LocalDate.parse(dateStr)` (`00:00`) + `+15 min` je gespeicherter Zeile – **identisch zu `processCsvUpload`**, `category`-Spalte (`parts[0]`) **nicht** ausgewertet; gefüllte Spalte → Einheit, `total = Wert`, `zev = 0`, `quelle = CSV`; Monats-Overwrite beider Einheiten (analog `processCsvUpload`); `@CacheEvict("statistik")`. `org_id` aus `OrganizationContextService`. |
| [ ] | 4. Backend-Controller | `MesswerteController`: `POST /upload-bilanz` (`@RequestParam file`, `@RequestParam date`), `@PreAuthorize("hasAuthority('messwerte:write')")` + `MESSWERTE_UPLOAD`-Flag-Prüfung (→ `FeatureDisabledException`); ruft `processBilanzCsvUpload(file, date)`; `IllegalArgumentException` → HTTP 400 (via `GlobalExceptionHandler`). |
| [ ] | 5. Frontend-Service | `einheit.service.ts`: `EinheitMatchResponse` um `bilanz: boolean`. |
| [ ] | 6. Frontend-Upload-Komponente | `UploadEntry` um `bilanz: boolean`; `matchEinheitForEntry` setzt `bilanz`; manuelles Markieren/Zurücksetzen; `canImport`/Readiness: **alle** Einträge brauchen `date`, nur normale zusätzlich `einheitId`; Upload-Routing: Bilanz → `POST /upload-bilanz` (`file` + `date`), sonst wie bisher (`file` + `date` + `einheitId`). |
| [ ] | 7. Frontend-Template | `messwerte-upload.component.html`: Bilanz-Label/Badge (`UPLOAD_TYP_BILANZ`), **Einheit-Spalte** bei Bilanz ausblenden, manueller Bilanz-Umschalter. **Datumsfeld für alle Typen sichtbar** (editierbar, vorbefüllt aus Datei – wie Consumer/Producer; FR-4.5). |
| [x] | 9. Nachtrag Datum bei Bilanz (Vibe) | Datum bei Bilanz **gleich wie Consumer/Producer** (Frontend **und** Backend): Template zeigt das `<input type="date">` einheitlich; `extractDateFromFile` füllt es vor; `importAll` sendet `date` auch bei Bilanz; Backend `processBilanzCsvUpload(file, date)` nutzt `date + Index·15min` (kein `category`-Parsing). Fix für „Datum erscheint kurz und verschwindet". |
| [ ] | 8. Übersetzungen | `V84__Add_Bilanz_Upload_Translations.sql`: `UPLOAD_TYP_BILANZ`, `BILANZ_EINHEIT_FEHLT`, `BILANZ_UPLOAD_ERFOLG`, `BILANZ_CSV_UNGUELTIG` (DE/EN). |

> **Tests** (Unit/Integration/E2E) werden separat mit `/3_backend-tests`, `/4_frontend-unit-tests`, `/5_e2e-tests` erstellt; hier nur die Anpassung bestehender Tests, damit der Build grün bleibt.

## Validierungen

### Backend (maßgeblich)
- **Feature-Gate:** `MESSWERTE_UPLOAD` aktiv, sonst `FeatureDisabledException` → HTTP 403 (wie `/upload`).
- **Bilanz-Einheiten vorhanden:** `BEZUG` **und** `RUECKLIEFERUNG` müssen im Mandanten existieren, sonst HTTP 400 `BILANZ_EINHEIT_FEHLT`, kein Speichern.
- **Header-Plausibilisierung:** Spalte 1 enthält `Bezug`, Spalte 2 `Rücklieferung` (case-insensitive), sonst HTTP 400 `BILANZ_CSV_UNGUELTIG`.
- **Leere/unparsbare Datei:** nur Kopfzeile oder kein parsbares Datum → HTTP 400 `BILANZ_CSV_UNGUELTIG`.
- **Zeilen-Robustheit (best-effort, wie Consumer-Upload):** Zeile mit beiden/keiner Spalte, nicht-numerischem Wert oder unparsbarem Datum → überspringen + Warn-Log; **keine** 96-Zeilen-Validierung.
- **Vorzeichen:** Wert verbatim aus der Datei (Bezug positiv, Rücklieferung negativ); `zev = 0`.
- **Mandanten-Isolation:** `orgFilter` aktiv, `org_id` aus dem Kontext (nie aus Datei/Request).
- **Overwrite:** Monat der Daten für beide Einheiten vor dem Insert löschen (idempotenter Re-Upload).

### Frontend
- Bilanz-Eintrag (`bilanz = true`) gilt ohne Einheit/Datum als „bereit"; normale Einträge benötigen weiterhin Einheit + Datum.
- Bilanz-Eintrag wird an `/api/messwerte/upload-bilanz` (nur Datei) gesendet, normale an `/api/messwerte/upload`.
- Manuelles Markieren als Bilanz überschreibt eine fehlende/fehlgeschlagene KI-Erkennung.
- Fehlermeldungen als `.zev-message--error`, Erfolg als `.zev-message--success` (i18n).

## Offene Punkte / Annahmen
- **Alle Spec-Fragen sind geklärt** (Bilanz-Messwerte-Upload.md §8): Erkennung am Dateinamen (Sentinel `BILANZ`); **Datum/Zeitstempel identisch zum Consumer-Upload** – GUI liest Startdatum aus der Datei, sendet es als `date`, Backend bildet `date + Index·15min`, keine 96-Validierung; Spalten positions- **und** titelbasiert; Monatswechsel/Overwrite wie Consumer-Upload; manueller Bilanz-Fallback; Feature-Flag + Frontend-Readiness analog bestehend.
- **Zeitstempel (revidiert, Vibe):** Bilanz nutzt denselben Pfad wie Consumer/Producer – `date`-Parameter (`00:00`) + fortlaufend `+15 min` je Zeile. Die `category`-Spalte wird backendseitig **nicht** ausgewertet (das GUI liest daraus nur den Vorbefüll-Wert des Datumsfelds). Kein Tages-Reset, keine Sonderlogik.
- **Overwrite-Monat:** Monat des `date`-Parameters (identisch Consumer-Upload); Zeilen jenseits der Monatsgrenze werden mit fortlaufendem Zeitstempel geschrieben, ohne gesonderte Mehr-Monats-Löschung.
- **KI-Prompt:** Die BEZUG/RUECKLIEFERUNG-Einheiten stehen bereits in der Einheiten-Liste des Prompts; die Ergänzung stellt sicher, dass die KI für „bilanz" den Sentinel `BILANZ` (nicht eine einzelne ID) liefert.
