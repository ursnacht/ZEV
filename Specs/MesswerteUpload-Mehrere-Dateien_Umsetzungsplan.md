# Umsetzungsplan: MesswerteUpload – Mehrere Dateien gleichzeitig importieren

## Zusammenfassung

Die `MesswerteUploadComponent` wird überarbeitet, sodass mehrere CSV-Dateien gleichzeitig gedroppt werden können. Die Dateien erscheinen in einem Staging-Bereich (Tabelle) mit automatisch erkannter Einheit (KI) und Datum (aus Dateiinhalt), beides manuell anpassbar. Ein "Importieren"-Button sendet alle Dateien nacheinander an den bestehenden Backend-Endpunkt. Das Backend bleibt unverändert.

---

## Betroffene Komponenten

| Typ | Datei | Änderungsart |
|-----|-------|--------------|
| Frontend Component | `frontend-service/src/app/components/messwerte-upload/messwerte-upload.component.ts` | Änderung |
| Frontend Component | `frontend-service/src/app/components/messwerte-upload/messwerte-upload.component.html` | Änderung |
| Frontend Component | `frontend-service/src/app/components/messwerte-upload/messwerte-upload.component.css` | Änderung |
| DB Migration | `backend-service/src/main/resources/db/migration/V58__Add_MultiUpload_Translations.sql` | Neu |

---

## Phasen-Tabelle

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. Übersetzungen | Flyway-Migration V58 für neue i18n-Keys |
| [x] | 2. Component TypeScript | `messwerte-upload.component.ts` – `UploadEntry`-Interface + vollständige Überarbeitung |
| [x] | 3. Component Template | `messwerte-upload.component.html` – Dropzone + Staging-Tabelle + Import-Button |
| [x] | 4. Component CSS | `messwerte-upload.component.css` – Status-Spalte und Zeilenfarben für Tabelle |

---

## Detailbeschreibung der Phasen

### Phase 1: Übersetzungen

**Datei:** `backend-service/src/main/resources/db/migration/V58__Add_MultiUpload_Translations.sql`

```sql
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('DATEIEN_AUSWAEHLEN',      'Dateien auswählen',                          'Select files'),
('MEHRERE_DATEIEN_DROPPEN', 'Eine oder mehrere CSV-Dateien hier ablegen', 'Drop one or more CSV files here'),
('DATEI_BEREITS_IN_LISTE',  'Datei ist bereits in der Liste',             'File is already in the list'),
('ALLE_IMPORTIEREN',        'Alle importieren',                           'Import all'),
('IMPORTIERE',              'Importiere...',                              'Importing...'),
('DATEIEN_IMPORT_ERGEBNIS', '{success} von {total} Dateien erfolgreich importiert', '{success} of {total} files imported successfully'),
('KEINE_DATEIEN',           'Keine Dateien ausgewählt',                   'No files selected'),
('DATEI_ENTFERNEN',         'Datei entfernen',                            'Remove file'),
('STATUS_WARTEND',          'Wartend',                                    'Pending'),
('STATUS_ERKENNE',          'Erkenne...',                                  'Detecting...'),
('STATUS_IMPORTIERT',       'Importiert',                                 'Imported'),
('STATUS_FEHLER',           'Fehler',                                     'Error')
ON CONFLICT (key) DO NOTHING;
```

---

### Phase 2: Component TypeScript

**Datei:** `frontend-service/src/app/components/messwerte-upload/messwerte-upload.component.ts`

Die bestehende Einzeldatei-Logik wird durch eine Staging-Liste (`UploadEntry[]`) ersetzt.

**Neues Interface `UploadEntry`** (im selben File, oberhalb der Klasse):

```typescript
export type UploadStatus = 'pending' | 'matching' | 'ready' | 'uploading' | 'done' | 'error';

export interface UploadEntry {
  file: File;
  einheitId: number | null;
  date: string;
  status: UploadStatus;
  errorMessage: string | null;
  matchConfidence: number | null; // 0.0–1.0, null = noch nicht gematcht
}
```

**Neue Properties der Komponente** (ersetzen `file`, `date`, `einheitId`, `isMatching`, `matchResult`):

```typescript
entries: UploadEntry[] = [];
importing = false;
```

**Neue Methoden:**

```typescript
// Ersetzt onDrop / onFileChange
private addFiles(files: FileList | File[]): void {
  const fileArray = Array.from(files);
  const newFiles = fileArray.filter(f => {
    if (!f.name.endsWith('.csv')) {
      this.showMessage(`${f.name}: ${this.translationService.translate('NUR_CSV_DATEIEN')}`, 'error');
      return false;
    }
    if (this.entries.some(e => e.file.name === f.name)) {
      this.showMessage(
        `${f.name}: ${this.translationService.translate('DATEI_BEREITS_IN_LISTE')}`, 'error'
      );
      return false;
    }
    return true;
  });

  const newEntries: UploadEntry[] = newFiles.map(f => ({
    file: f,
    einheitId: this.einheiten.length > 0 ? (this.einheiten[0].id ?? null) : null,
    date: '',
    status: 'matching',
    errorMessage: null,
    matchConfidence: null,
  }));

  this.entries = [...this.entries, ...newEntries];

  // Datum und Einheit für alle neuen Einträge parallel ermitteln
  newEntries.forEach(entry => {
    this.extractDateFromFile(entry);
    this.matchEinheitForEntry(entry);
  });
}

private matchEinheitForEntry(entry: UploadEntry): void {
  this.einheitService.matchEinheitByFilename(entry.file.name).subscribe({
    next: (result) => {
      entry.matchConfidence = result.confidence;
      if (result.matched && result.einheitId) {
        entry.einheitId = result.einheitId;
      }
      entry.status = 'ready';
    },
    error: () => {
      entry.matchConfidence = null;
      entry.status = 'ready'; // Fehler beim Matching: manuell wählen
    }
  });
}

private extractDateFromFile(entry: UploadEntry): void {
  const reader = new FileReader();
  reader.onload = (e) => {
    const content = e.target?.result as string;
    if (content) {
      const lines = content.split('\n');
      if (lines.length >= 2) {
        const parsedDate = this.parseEnglishDate(lines[1].split(',')[0]);
        if (parsedDate) {
          entry.date = parsedDate;
        }
      }
    }
  };
  reader.readAsText(entry.file);
}

removeEntry(entry: UploadEntry): void {
  this.entries = this.entries.filter(e => e !== entry);
}

get isAnyMatching(): boolean {
  return this.entries.some(e => e.status === 'matching');
}

get canImport(): boolean {
  return (
    this.entries.length > 0 &&
    !this.importing &&
    !this.isAnyMatching &&
    this.entries.every(e => e.einheitId !== null && e.date !== '' && e.status !== 'uploading')
  );
}

// Ersetzt onSubmit()
importAll(): void {
  const toImport = this.entries.filter(e => e.status === 'ready' || e.status === 'error');
  if (toImport.length === 0) return;

  this.importing = true;
  let completed = 0;
  let successCount = 0;
  const total = toImport.length;

  const processNext = (index: number): void => {
    if (index >= toImport.length) {
      this.importing = false;
      this.entries = this.entries.filter(e => e.status !== 'done');
      this.showMessage(
        this.translationService.translate('DATEIEN_IMPORT_ERGEBNIS')
          .replace('{success}', String(successCount))
          .replace('{total}', String(total)),
        successCount === total ? 'success' : 'error'
      );
      return;
    }

    const entry = toImport[index];
    entry.status = 'uploading';

    const formData = new FormData();
    formData.append('date', entry.date);
    formData.append('einheitId', entry.einheitId!.toString());
    formData.append('file', entry.file);

    this.http.post<any>('http://localhost:8090/api/messwerte/upload', formData).subscribe({
      next: (response) => {
        if (response.status === 'success') {
          entry.status = 'done';
          successCount++;
        } else {
          entry.status = 'error';
          entry.errorMessage = response.message;
        }
        processNext(index + 1);
      },
      error: (error) => {
        entry.status = 'error';
        entry.errorMessage = error.message;
        processNext(index + 1);
      }
    });
  };

  processNext(0);
}
```

**Entfernte Properties/Methoden:** `file`, `date`, `einheitId`, `isMatching`, `matchResult`, `CONFIDENCE_THRESHOLD`, `handleFile()`, `matchEinheitByFilename()`, `extractDateFromFile()` (privatisiert + angepasst), `removeFile()`, `onSubmit()`, `resetForm()`.

---

### Phase 3: Component Template

**Datei:** `frontend-service/src/app/components/messwerte-upload/messwerte-upload.component.html`

Layout: Dropzone oben, Staging-Tabelle darunter, Import-Button ganz unten.

```html
<div class="zev-container">
  <h1><app-icon name="upload" size="lg"></app-icon> {{ 'ZEV_MESSWERTE_UPLOAD' | translate }}</h1>

  @if (message) {
    <div class="zev-message" [ngClass]="'zev-message--' + messageType">
      {{ message }}
    </div>
  }

  <!-- Dropzone -->
  <div
    class="zev-drop-zone"
    [class.zev-drop-zone--active]="isDragOver"
    (dragover)="onDragOver($event)"
    (dragleave)="onDragLeave($event)"
    (drop)="onDrop($event)"
    (click)="fileInput.click()">
    <input
      #fileInput
      type="file"
      class="zev-drop-zone__input"
      accept=".csv"
      multiple
      (change)="onFileChange($event)">
    <div class="zev-drop-zone__content">
      <div class="zev-drop-zone__icon">
        <!-- Upload SVG (wie bisher) -->
      </div>
      <p class="zev-drop-zone__text">{{ 'MEHRERE_DATEIEN_DROPPEN' | translate }}</p>
      <p class="zev-drop-zone__hint">{{ 'OR_CLICK_TO_SELECT' | translate }}</p>
    </div>
  </div>

  <!-- Staging-Tabelle -->
  @if (entries.length > 0) {
    <table class="zev-table">
      <thead>
        <tr>
          <th>{{ 'DATEINAME' | translate }}</th>
          <th>{{ 'EINHEIT' | translate }}</th>
          <th>{{ 'DATUM' | translate }}</th>
          <th>{{ 'STATUS' | translate }}</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        @for (entry of entries; track entry.file.name) {
          <tr [class.upload-row--done]="entry.status === 'done'"
              [class.upload-row--error]="entry.status === 'error'">

            <td class="upload-row__filename">{{ entry.file.name }}</td>

            <td>
              <select
                class="zev-select"
                [(ngModel)]="entry.einheitId"
                [disabled]="entry.status === 'matching' || entry.status === 'uploading' || entry.status === 'done'">
                @for (einheit of einheiten; track einheit.id) {
                  <option [value]="einheit.id">{{ einheit.name }} ({{ einheit.typ }})</option>
                }
              </select>
              @if (entry.status === 'matching') {
                <span class="zev-spinner-container">
                  <span class="zev-spinner zev-spinner--small"></span>
                </span>
              }
              @if (entry.matchConfidence !== null && entry.matchConfidence > 0.8 && entry.status === 'ready') {
                <span class="zev-status zev-status--success">{{ 'AUTOMATISCH_ERKANNT' | translate }}</span>
              }
              @if (entry.matchConfidence !== null && entry.matchConfidence <= 0.8 && entry.status === 'ready') {
                <span class="zev-status zev-status--warning">{{ 'BITTE_PRUEFEN' | translate }}</span>
              }
            </td>

            <td>
              <input
                type="date"
                class="zev-input"
                [(ngModel)]="entry.date"
                [disabled]="entry.status === 'uploading' || entry.status === 'done'">
            </td>

            <td>
              @switch (entry.status) {
                @case ('matching') {
                  <span class="zev-text--muted">{{ 'STATUS_ERKENNE' | translate }}</span>
                }
                @case ('uploading') {
                  <span class="zev-spinner-container">
                    <span class="zev-spinner zev-spinner--small"></span>
                    {{ 'IMPORTIERE' | translate }}
                  </span>
                }
                @case ('done') {
                  <span class="zev-status zev-status--success">{{ 'STATUS_IMPORTIERT' | translate }}</span>
                }
                @case ('error') {
                  <span class="zev-status zev-status--error" [title]="entry.errorMessage ?? ''">{{ 'STATUS_FEHLER' | translate }}</span>
                }
                @default {
                  <span class="zev-text--muted">{{ 'STATUS_WARTEND' | translate }}</span>
                }
              }
            </td>

            <td>
              @if (entry.status !== 'uploading' && entry.status !== 'done') {
                <button
                  type="button"
                  class="zev-button zev-button--secondary zev-button--compact"
                  (click)="removeEntry(entry)"
                  [title]="'DATEI_ENTFERNEN' | translate">
                  <app-icon name="x"></app-icon>
                </button>
              }
            </td>
          </tr>
        }
      </tbody>
    </table>

    <div class="zev-form-actions">
      <button
        type="button"
        class="zev-button zev-button--primary"
        (click)="importAll()"
        [disabled]="!canImport">
        <app-icon name="upload"></app-icon>
        {{ importing ? ('IMPORTIERE' | translate) : ('ALLE_IMPORTIEREN' | translate) }}
      </button>
    </div>
  }

  @if (entries.length === 0) {
    <p class="zev-empty-state">{{ 'KEINE_DATEIEN' | translate }}</p>
  }
</div>
```

---

### Phase 4: Component CSS

**Datei:** `frontend-service/src/app/components/messwerte-upload/messwerte-upload.component.css`

```css
.upload-row--done td {
  opacity: 0.5;
}

.upload-row--error td {
  background-color: var(--color-danger-50, #fef2f2);
}

.upload-row__filename {
  font-size: var(--font-size-sm);
  word-break: break-all;
  max-width: 200px;
}

/* Kompakter Select in Tabelle */
table .zev-select {
  padding: var(--spacing-xs) var(--spacing-sm);
  font-size: var(--font-size-sm);
}

/* Kompakter Date-Input in Tabelle */
table .zev-input {
  padding: var(--spacing-xs) var(--spacing-sm);
  font-size: var(--font-size-sm);
}

/* Staging-Tabelle Abstand zur Dropzone */
.zev-table {
  margin-top: var(--spacing-lg);
}
```

---

## Validierungen

### Frontend-Validierungen

| Validierung | Beschreibung |
|-------------|--------------|
| Nur CSV-Dateien | Dateien ohne `.csv`-Endung werden abgelehnt mit Fehlermeldung |
| Keine Duplikate | Datei mit gleichem Namen wird nicht zur Liste hinzugefügt |
| Import-Button | Nur aktiv wenn: mind. 1 Eintrag, kein Matching läuft, alle Einträge haben Einheit + Datum |
| Sequenzieller Import | Dateien werden nacheinander importiert (nicht parallel) |
| Fehlerhafte Einträge | Bleiben nach Importfehler in der Liste für erneuten Versuch |
| Erledigte Einträge | Werden nach erfolgreichem Import aus der Liste entfernt |

### Backend-Validierungen
Unverändert (bestehender Endpunkt `POST /api/messwerte/upload`).

---

## Offene Punkte / Annahmen

1. **Annahme:** Erfolgreich importierte Einträge werden nach dem Gesamtimport aus der Liste entfernt; fehlerhafte Einträge bleiben für einen erneuten Versuch.
2. **Annahme:** Import erfolgt sequenziell (eine Datei nach der anderen), nicht parallel – einfachere Fehlerbehandlung und keine Backend-Last.
3. **Annahme:** Kein Limit für die Anzahl Dateien.
4. **Annahme:** Die Dropzone bleibt sichtbar und nutzbar, solange kein Import läuft – neue Dateien können jederzeit hinzugefügt werden.
5. **Annahme:** Der `DATEINAME` Translation-Key existiert bereits (wird in anderen Tabellen verwendet); falls nicht, wird er in V58 ergänzt.
6. **Annahme:** `zev-status--error`-Klasse ist im Design System vorhanden (analog zu `zev-status--success`/`--warning`); falls nicht, wird sie dort ergänzt.
