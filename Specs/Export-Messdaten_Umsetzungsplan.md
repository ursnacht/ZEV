# Umsetzungsplan: Export-Messdaten

## Zusammenfassung

In der Statistik-Seite bekommt jede **Consumer**-Zeile der Monatstabelle „Summen pro Einheit" einen Button **„Download CSV"**, der die **15-Minuten-Messwerte** dieser Einheit für den Monat als CSV herunterlädt (Spalten: Datum+Zeit, Energiebezug Total kWh, Anteil Bezug aus ZEV kWh; Spaltentitel mit Monatstotal). Rein additiv: neuer Backend-Endpoint `GET /api/statistik/export/csv` (analog PDF-Export), Erweiterung von `StatistikService`/`statistik.component` und neue Übersetzungs-Keys — **keine** neue Tabelle/Entity.

---

## Betroffene Komponenten

| Typ | Datei | Änderungsart |
|-----|-------|--------------|
| Backend Service | `backend-service/src/main/java/ch/nacht/service/StatistikService.java` | Änderung (CSV-Erzeugung, `TranslationService` injizieren) |
| Backend Controller | `backend-service/src/main/java/ch/nacht/controller/StatistikController.java` | Änderung (Endpoint `GET /export/csv`) |
| Frontend Service | `frontend-service/src/app/services/statistik.service.ts` | Änderung (`exportCsv(...)` → Blob) |
| Frontend Component | `frontend-service/src/app/components/statistik/statistik.component.ts` | Änderung (Download-Handler, Consumer-/Leer-Prüfung) |
| Frontend Template | `frontend-service/src/app/components/statistik/statistik.component.html` | Änderung (Button je Consumer-Zeile) |
| DB Migration | `backend-service/src/main/resources/db/migration/V88__Add_Export_Messdaten_Translations.sql` | Neu (nur Übersetzungen) |

> **Nicht betroffen:** kein neues Entity/Repository (Wiederverwendung `MesswerteRepository.findByEinheitAndZeitBetween`), keine Route/Navigation (innerhalb bestehender `/statistik`-Seite), kein Schema-Change.

---

## Phasen-Tabelle

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [ ] | 1. Backend-Service (CSV) | `StatistikService.exportMesswerteCsv(einheitId, von, bis, sprache)`: org-expliziter Einheiten-Check, 15-Min-Werte laden, auf 3 NKS runden, Header (übersetzte Titel + Monatstotal aus gerundeten Werten), Datenzeilen, `byte[]`/String |
| [ ] | 2. Backend-Controller | `GET /api/statistik/export/csv?einheitId&von&bis&sprache` → `text/csv`, `Content-Disposition: attachment`, Dateiname bereinigt; `von > bis` → 400 (Klasse hat bereits `@PreAuthorize('statistik:read')`) |
| [ ] | 3. Frontend-Service | `statistik.service.ts`: `exportCsv(einheitId, von, bis, sprache): Observable<Blob>` |
| [ ] | 4. Frontend-Component | Button „Download CSV" je **CONSUMER**-Zeile in „Summen pro Einheit"; deaktiviert bei `summeTotal === 0`; Download-Handler (Blob → Link), Sprache via `getCurrentLanguage()` |
| [ ] | 5. Übersetzungen | `V88` – `DOWNLOAD_CSV` + Spaltentitel-Keys + Fehlermeldung (DE/EN) |

---

## Detailbeschreibung

### Phase 1: Backend-Service `StatistikService.exportMesswerteCsv(...)`
- **Dependency:** `TranslationService` injizieren (bislang nicht vorhanden); Titel je `sprache` über `getTranslationByKey(key)` + `getDeutsch()`/`getEnglisch()` auflösen (analog `StatistikPdfService.loadTranslations`).
- **Org-Check (Sicherheit):** `hibernateFilterService.enableOrgFilter()`; Einheit laden und **explizit** `einheit.getOrgId().equals(organizationContextService.getCurrentOrgId())` prüfen (da `findById` den `orgFilter` nicht anwendet) → sonst `IllegalArgumentException`/404. Nur `EinheitTyp.CONSUMER` zulässig.
- **Daten:** `messwerteRepository.findByEinheitAndZeitBetween(einheit, von.atStartOfDay(), bis.atTime(23,59,59))`, aufsteigend nach `zeit`.
- **Rundung/Format:** jede `total`/`zev` auf **3 NKS** runden (`BigDecimal`, HALF_UP). **Monatstotal = Summe der gerundeten Intervallwerte** (nicht separate Aggregat-Query) → Header == Summe der CSV-Zeilen exakt. Zahlen mit Punkt-Dezimaltrenner (`String.format(Locale.US, "%.3f", …)`).
- **CSV:** Feldtrenner `,`; Zeitstempel `dd.MM.yyyy HH:mm` (`DateTimeFormatter`). Kopfzeile: `<Titel1>,<Titel2> (<TotalTotal>),<Titel3> (<TotalZev>)`. Header-Titel bei Bedarf CSV-escapen (falls Übersetzung ein Komma enthält → in `"` einschliessen).
- **Rückgabe:** CSV als `byte[]` (UTF-8) oder `String`.

### Phase 2: Backend-Controller `GET /export/csv`
- Analog `exportPdf`: Parameter `von`/`bis` (`@DateTimeFormat ISO.DATE`), `einheitId` (Long), `sprache` (`defaultValue = "de"`). `von.isAfter(bis)` → `400`.
- Response: `Content-Type: text/csv; charset=UTF-8`, `Content-Disposition: attachment; filename=verbrauch_<einheit>_<yyyy-MM>.csv` (Dateiname auf unbedenkliche Zeichen bereinigen; optional `filename*` RFC 5987 für Umlaute).
- Fehler (Einheit fremd/unbekannt/kein Consumer) → `400`/`404` (kein Fremd-Export).

### Phase 3: Frontend-Service
```typescript
exportCsv(einheitId: number, von: string, bis: string, sprache = 'de'): Observable<Blob> {
  const params = new HttpParams()
    .set('einheitId', einheitId).set('von', von).set('bis', bis).set('sprache', sprache);
  return this.http.get(`${this.apiUrl}/export/csv`, { params, responseType: 'blob' });
}
```

### Phase 4: Frontend-Component
- In der „Summen pro Einheit"-Tabelle je Zeile mit `einheit.einheitTyp === 'CONSUMER'` einen Button `.zev-button--secondary .zev-button--compact` + Icon `download`, Label `DOWNLOAD_CSV`.
- **Deaktiviert**, wenn `einheit.summeTotal === 0` (kein Messwert im Monat, Spec §5).
- Handler `onDownloadCsv(monat, einheit)`: `sprache = translationService.getCurrentLanguage()`; `statistikService.exportCsv(einheit.einheitId, monat.von, monat.bis, sprache)` → Blob per `URL.createObjectURL` + `link.download` (Dateiname aus `Content-Disposition` oder clientseitig gebildet), `subscribe({ next, error })`; Fehler → `.zev-message--error` (Key `EXPORT_CSV_FEHLER`).

### Phase 5: Übersetzungen `V88`
Keys (DE/EN), `ON CONFLICT (key) DO NOTHING`:
- `DOWNLOAD_CSV` = „Download CSV" / „Download CSV"
- `EXPORT_SPALTE_DATUM_ZEIT` = „Datum+Zeit" / „Date+Time"
- `EXPORT_SPALTE_ENERGIEBEZUG_TOTAL` = „Energiebezug Total kWh" / „Total energy consumption kWh"
- `EXPORT_SPALTE_ANTEIL_ZEV` = „Anteil Bezug aus ZEV kWh" / „Share from ZEV kWh"
- `EXPORT_CSV_FEHLER` = „CSV-Export fehlgeschlagen" / „CSV export failed"

---

## Validierungen

### Backend
1. **`von > bis`** → HTTP 400.
2. **Org-Check:** Einheit muss zur aktuellen `org_id` gehören (expliziter Vergleich, da `findById` den `orgFilter` umgeht) → sonst kein Export (400/404).
3. **Nur CONSUMER** exportierbar (Producer/Bilanz → abgelehnt).
4. **Rundung konsistent:** Header-Monatstotal = Summe der auf 3 NKS gerundeten Intervallwerte.
5. **Autorisierung:** `statistik:read` (Klassen-`@PreAuthorize` am `StatistikController`).

### Frontend
1. Button nur bei CONSUMER-Zeilen; **deaktiviert** bei `summeTotal === 0`.
2. Sprache aus `getCurrentLanguage()` an den Endpoint übergeben.
3. Fehler beim Download → `.zev-message--error` (übersetzt), kein stiller Fehlschlag.
4. Alle Texte via `TranslationService`.

---

## Offene Punkte / Annahmen

1. **Geklärt (Spec §8):** „Anteil aus ZEV" = `zev`; leerer Monat → Button deaktiviert; serverseitige Erzeugung; 3 NKS; Dateiname `verbrauch_<einheit>_<yyyy-MM>.csv`; UTF-8 ohne BOM, Feldtrenner Komma / Punkt-Dezimal; Spaltentitel gemäss UI-Sprache; Header aus derselben Rundungsbasis (Header == Summe der Zeilen).
2. **Annahme:** CSV wird als `byte[]`/String vollständig erzeugt (ein Monat × 15 Min ≤ ~2976 Zeilen → unkritisch, kein Streaming nötig).
3. **Annahme:** Dateiname-Bereinigung ersetzt Nicht-`[A-Za-z0-9._-]` durch `_`; optional `filename*` (RFC 5987) für korrekte Umlaute im Download-Namen.
4. **Annahme:** Endpoint am `StatistikController` (Permission `statistik:read`), obwohl Roh-15-Min-Daten sonst über `messwerte:read` (`/by-einheit`) laufen — beide Permissions haben alle Fachrollen, daher keine Rechte-Ausweitung.
5. **Flyway-Version `V88`:** höchste bestehende ist `V87`; nächste freie zum Umsetzungszeitpunkt via `zev-db` verifizieren.
6. **Tests** (Folge-Kommandos): Backend – CSV-Inhalt/Header/Rundung/Org-Check/`von>bis`; Frontend – Button-Sichtbarkeit (nur Consumer)/Deaktivierung (leerer Monat)/Download-Aufruf; E2E – Download in der Statistik.
