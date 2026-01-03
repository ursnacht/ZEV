# Umsetzungsplan: Intelligenter Upload der Messwerte

## Zusammenfassung

Erweiterung des Messwerte-Uploads um eine KI-basierte automatische Erkennung der Einheit aus dem Dateinamen. Claude analysiert den Dateinamen und matcht ihn gegen die verfügbaren Einheiten. Dies reduziert Fehler bei der manuellen Auswahl und beschleunigt den Upload-Prozess.

---

## Betroffene Komponenten

### Backend (Neu)
| Datei | Beschreibung |
|-------|--------------|
| `backend-service/pom.xml` | Spring AI Anthropic Dependency hinzufügen |
| `backend-service/src/main/resources/application.yml` | Claude API-Key Konfiguration |
| `backend-service/src/main/java/ch/nacht/service/EinheitMatchingService.java` | Service für KI-basiertes Einheit-Matching |
| `backend-service/src/main/java/ch/nacht/controller/EinheitController.java` | Neuer Endpoint für Einheit-Matching |
| `backend-service/src/main/java/ch/nacht/dto/EinheitMatchRequestDTO.java` | Request-DTO |
| `backend-service/src/main/java/ch/nacht/dto/EinheitMatchResponseDTO.java` | Response-DTO |

### Design System (Neu)
| Datei | Beschreibung |
|-------|--------------|
| `design-system/src/components/spinner/spinner.css` | Neue Spinner-Komponente |
| `design-system/src/components/index.css` | Import für Spinner hinzufügen |

### Frontend (Änderung)
| Datei | Beschreibung |
|-------|--------------|
| `frontend-service/src/app/services/einheit.service.ts` | Neue Methode für Einheit-Matching |
| `frontend-service/src/app/components/messwerte-upload/messwerte-upload.component.ts` | KI-Matching bei Dateiauswahl aufrufen |
| `frontend-service/src/app/components/messwerte-upload/messwerte-upload.component.html` | Loading-Indikator und Matching-Feedback |

### Datenbank
| Datei | Beschreibung |
|-------|--------------|
| `backend-service/src/main/resources/db/migration/V32__Add_KI_Matching_Translations.sql` | i18n Keys |

---

## Phasen-Tabelle

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. Spring AI Setup | Dependency und Konfiguration in `pom.xml` und `application.yml` |
| [x] | 2. Backend DTOs | Request/Response DTOs für Einheit-Matching |
| [x] | 3. Backend Service | `EinheitMatchingService` mit Claude-Integration |
| [x] | 4. Backend Controller | Neuer Endpoint `POST /api/einheiten/match` |
| [x] | 5. Design System | Neue Spinner-Komponente in `design-system/src/components/spinner/` |
| [x] | 6. Frontend Service | `matchEinheitByFilename()` Methode in `EinheitService` |
| [x] | 7. Frontend Komponente | Upload-Komponente mit Design System Komponenten erweitern |
| [x] | 8. Übersetzungen | Flyway-Migration für neue i18n Keys |
| [x] | 9. Fehlerbehandlung | Backend-Exceptions im Frontend anzeigen |
| [x] | 10. Tests | Unit- und Integrationstests |

---

## Detaillierte Phasen

### Phase 1: Spring AI Setup

**Datei:** `backend-service/pom.xml`

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-anthropic-spring-boot-starter</artifactId>
    <version>1.0.0-M4</version>
</dependency>
```

**Datei:** `backend-service/src/main/resources/application.yml`

```yaml
spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY:sk-ant-api...}
      chat:
        options:
          model: claude-3-haiku-20240307
          max-tokens: 100
          temperature: 0.0
```

> **Hinweis:** Haiku-Modell für schnelle Antworten (<2s) und niedrige Kosten.

---

### Phase 2: Backend DTOs

**Datei:** `backend-service/src/main/java/ch/nacht/dto/EinheitMatchRequestDTO.java`

```java
public class EinheitMatchRequestDTO {
    @NotBlank
    private String filename;

    // Getter, Setter
}
```

**Datei:** `backend-service/src/main/java/ch/nacht/dto/EinheitMatchResponseDTO.java`

```java
public class EinheitMatchResponseDTO {
    private Long einheitId;
    private String einheitName;
    private double confidence;  // 0.0 - 1.0
    private boolean matched;
    private String message;     // Fehlermeldung falls nicht gematcht

    // Getter, Setter, Builder
}
```

---

### Phase 3: Backend Service

**Datei:** `backend-service/src/main/java/ch/nacht/service/EinheitMatchingService.java`

```java
@Service
public class EinheitMatchingService {

    private final ChatClient chatClient;
    private final EinheitService einheitService;

    public EinheitMatchResponseDTO matchEinheitByFilename(String filename) {
        // 1. Alle verfügbaren Einheiten laden
        List<Einheit> einheiten = einheitService.getAllEinheiten();

        // 2. Prompt für Claude erstellen
        String prompt = buildPrompt(filename, einheiten);

        // 3. Claude API aufrufen
        String response = chatClient.prompt()
            .user(prompt)
            .call()
            .content();

        // 4. Response parsen und Einheit zurückgeben
        return parseResponse(response, einheiten);
    }

    private String buildPrompt(String filename, List<Einheit> einheiten) {
        StringBuilder sb = new StringBuilder();
        sb.append("Analysiere den Dateinamen und finde die passende Einheit.\n\n");
        sb.append("Dateiname: ").append(filename).append("\n\n");
        sb.append("Verfügbare Einheiten:\n");
        for (Einheit e : einheiten) {
            sb.append("- ID ").append(e.getId()).append(": ")
              .append(e.getName()).append(" (").append(e.getTyp()).append(")\n");
        }
        sb.append("\nAntwort NUR mit der ID der passenden Einheit, ");
        sb.append("oder 'KEINE' wenn keine passt. ");
        sb.append("Beispiele: '1' oder '42' oder 'KEINE'");
        return sb.toString();
    }
}
```

---

### Phase 4: Backend Controller

**Änderung in:** `backend-service/src/main/java/ch/nacht/controller/EinheitController.java`

```java
@PostMapping("/match")
public ResponseEntity<EinheitMatchResponseDTO> matchEinheit(
        @Valid @RequestBody EinheitMatchRequestDTO request) {
    try {
        EinheitMatchResponseDTO response =
            einheitMatchingService.matchEinheitByFilename(request.getFilename());
        return ResponseEntity.ok(response);
    } catch (Exception e) {
        log.error("Fehler beim Einheit-Matching: {}", e.getMessage());
        return ResponseEntity.ok(EinheitMatchResponseDTO.builder()
            .matched(false)
            .message("KI-Service nicht verfügbar")
            .build());
    }
}
```

---

### Phase 5: Design System - Spinner Komponente

**Datei:** `design-system/src/components/spinner/spinner.css`

```css
/**
 * Spinner Component
 * Loading indicator for async operations
 */

.zev-spinner {
  display: inline-block;
  width: 20px;
  height: 20px;
  border: 2px solid var(--color-gray-200);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: zev-spin 0.8s linear infinite;
}

.zev-spinner--small {
  width: 14px;
  height: 14px;
  border-width: 2px;
}

.zev-spinner--large {
  width: 32px;
  height: 32px;
  border-width: 3px;
}

@keyframes zev-spin {
  to {
    transform: rotate(360deg);
  }
}

/* Spinner with text */
.zev-spinner-container {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-sm);
  color: var(--color-gray-500);
  font-size: var(--font-size-sm);
}
```

**Änderung in:** `design-system/src/components/index.css`

```css
@import './spinner/spinner.css';
```

---

### Phase 6: Frontend Service

**Änderung in:** `frontend-service/src/app/services/einheit.service.ts`

```typescript
export interface EinheitMatchResponse {
  einheitId: number | null;
  einheitName: string | null;
  confidence: number;
  matched: boolean;
  message: string | null;
}

matchEinheitByFilename(filename: string): Observable<EinheitMatchResponse> {
  return this.http.post<EinheitMatchResponse>(
    `${this.apiUrl}/match`,
    { filename }
  );
}
```

---

### Phase 7: Frontend Komponente

**Änderung in:** `frontend-service/src/app/components/messwerte-upload/messwerte-upload.component.ts`

```typescript
// Neue Properties
isMatching = false;
matchResult: EinheitMatchResponse | null = null;

// Confidence-Schwellenwert: nur bei >80% automatisch setzen
private readonly CONFIDENCE_THRESHOLD = 0.8;

private handleFile(file: File): void {
  this.file = file;
  this.extractDateFromFile(file);
  this.matchEinheitByFilename(file.name);  // NEU
}

private matchEinheitByFilename(filename: string): void {
  this.isMatching = true;
  this.matchResult = null;

  this.einheitService.matchEinheitByFilename(filename).subscribe({
    next: (result) => {
      this.isMatching = false;
      this.matchResult = result;

      // Nur bei Confidence >80% automatisch setzen
      if (result.matched && result.einheitId && result.confidence > this.CONFIDENCE_THRESHOLD) {
        this.einheitId = result.einheitId;
        this.showMessage(
          this.translationService.translate('EINHEIT_ERKANNT') + ': ' + result.einheitName,
          'success'
        );
      } else if (result.matched && result.confidence <= this.CONFIDENCE_THRESHOLD) {
        // Match gefunden aber Confidence zu niedrig - Benutzer muss wählen
        this.showMessage(
          this.translationService.translate('EINHEIT_BITTE_PRUEFEN'),
          'warning'
        );
      } else {
        this.showMessage(
          this.translationService.translate('EINHEIT_NICHT_ERKANNT'),
          'error'
        );
      }
    },
    error: (error) => {
      this.isMatching = false;
      this.showMessage(
        this.translationService.translate('KI_NICHT_VERFUEGBAR'),
        'error'
      );
    }
  });
}
```

**Änderung in:** `frontend-service/src/app/components/messwerte-upload/messwerte-upload.component.html`

Verwendet Design System Komponenten (`zev-spinner`, `zev-status`):

```html
<!-- Unter der Drop-Zone, vor Einheit-Select -->
<div *ngIf="isMatching" class="zev-spinner-container">
  <span class="zev-spinner"></span>
  {{ 'EINHEIT_WIRD_ERKANNT' | translate }}...
</div>

<!-- Einheit-Select mit Matching-Status -->
<div class="zev-form-group">
  <label for="einheit">
    {{ 'EINHEIT' | translate }}:
    <span *ngIf="matchResult?.matched && matchResult?.confidence > 0.8"
          class="zev-status zev-status--success">
      {{ 'AUTOMATISCH_ERKANNT' | translate }}
    </span>
    <span *ngIf="matchResult?.matched && matchResult?.confidence <= 0.8"
          class="zev-status zev-status--warning">
      {{ 'BITTE_PRUEFEN' | translate }}
    </span>
  </label>
  <!-- ... bestehendes select ... -->
</div>
```

---

### Phase 8: Übersetzungen

**Datei:** `backend-service/src/main/resources/db/migration/V32__Add_KI_Matching_Translations.sql`

```sql
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('EINHEIT_ERKANNT', 'Einheit automatisch erkannt', 'Unit automatically detected'),
('EINHEIT_NICHT_ERKANNT', 'Einheit konnte nicht automatisch erkannt werden', 'Unit could not be automatically detected'),
('EINHEIT_WIRD_ERKANNT', 'Einheit wird erkannt', 'Detecting unit'),
('EINHEIT_BITTE_PRUEFEN', 'Bitte Einheit prüfen', 'Please verify unit'),
('KI_NICHT_VERFUEGBAR', 'KI-Service nicht verfügbar', 'AI service not available'),
('AUTOMATISCH_ERKANNT', 'Automatisch erkannt', 'Auto-detected'),
('BITTE_PRUEFEN', 'Bitte prüfen', 'Please verify')
ON CONFLICT (key) DO NOTHING;
```

---

### Phase 9: Fehlerbehandlung

Verbesserung der Fehlerbehandlung, damit Backend-Exceptions (z.B. ungültiger API-Key) im Frontend angezeigt werden.

**Änderung in:** `backend-service/src/main/java/ch/nacht/service/EinheitMatchingService.java`

Neue Methode `extractErrorMessage()` für benutzerfreundliche Fehlermeldungen:

```java
private String extractErrorMessage(Exception e) {
    String message = e.getMessage();
    if (message == null) {
        return "KI-Service nicht verfügbar";
    }

    String lowerMessage = message.toLowerCase();
    if (lowerMessage.contains("invalid api key") || lowerMessage.contains("authentication")) {
        return "Ungültiger API-Key für KI-Service";
    }
    if (lowerMessage.contains("rate limit") || lowerMessage.contains("too many requests")) {
        return "KI-Service überlastet, bitte später versuchen";
    }
    if (lowerMessage.contains("timeout") || lowerMessage.contains("timed out")) {
        return "KI-Service antwortet nicht (Timeout)";
    }
    if (lowerMessage.contains("connection") || lowerMessage.contains("unreachable")) {
        return "KI-Service nicht erreichbar";
    }

    return "KI-Service nicht verfügbar: " + message;
}
```

**Änderung in:** `frontend-service/src/app/components/messwerte-upload/messwerte-upload.component.ts`

Fehlermeldungen aus dem Response anzeigen:

```typescript
// Bei matched === false mit message
} else if (!result.matched && result.message) {
  this.showMessage(result.message, 'error');
}

// Bei HTTP-Fehlern
error: (error) => {
  const message = error.error?.message || error.message ||
    this.translationService.translate('KI_NICHT_VERFUEGBAR');
  this.showMessage(message, 'error');
}
```

---

## Validierungen

### Backend
| Validierung | Beschreibung |
|-------------|--------------|
| Dateiname nicht leer | `@NotBlank` auf `filename` |
| Timeout | Claude API Call max. 2 Sekunden |
| Graceful Degradation | Bei KI-Fehler: Manuelle Auswahl möglich |

### Frontend
| Validierung | Beschreibung |
|-------------|--------------|
| Confidence >80% | Einheit wird nur bei Confidence >80% automatisch gesetzt |
| Einheit auswählbar | Auch ohne KI-Match kann manuell gewählt werden |
| Loading-State | Während KI-Erkennung ist Upload-Button disabled |

---

## Offene Punkte / Annahmen

1. **Annahme:** Das Haiku-Modell (claude-3-haiku) ist ausreichend schnell und genau für diese Aufgabe
2. **Annahme:** Bei KI-Fehler wird die manuelle Auswahl wie bisher ermöglicht (Graceful Degradation)
3. **Umgesetzt:** Der API-Key wird via `.env`-Datei und Umgebungsvariable `ANTHROPIC_API_KEY` konfiguriert
4. **Annahme:** Keine Persistierung der Matching-Historie (jeder Match ist ein neuer API-Call)
5. **Entschieden:** Confidence-Schwellenwert >80% - nur dann wird die Einheit automatisch gesetzt
6. **Entschieden:** Benutzer bestätigt implizit durch Klick auf "Importieren" (keine zusätzliche Bestätigung)
7. **Umgesetzt:** Dateinamen-Format ist `YYYY-MM-<einheit>.csv` - der relevante Teil wird extrahiert

---

## Architektur-Übersicht

```
┌─────────────────────────────────────────────────────────────────┐
│                         Frontend                                │
│  ┌─────────────────┐    ┌─────────────────┐                     │
│  │ MesswerteUpload │───▶│ EinheitService  │                     │
│  │   Component     │    │ matchByFilename │                     │
│  └─────────────────┘    └────────┬────────┘                     │
└──────────────────────────────────┼──────────────────────────────┘
                                   │ HTTP POST /api/einheiten/match
                                   ▼
┌─────────────────────────────────────────────────────────────────┐
│                         Backend                                 │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────┐  │
│  │EinheitController│───▶│EinheitMatching  │───▶│ Spring AI   │  │
│  │   /match        │    │   Service       │    │ ChatClient  │  │
│  └─────────────────┘    └────────┬────────┘    └──────┬──────┘  │
│                                  │                    │         │
│                                  ▼                    │         │
│                         ┌─────────────────┐           │         │
│                         │ EinheitService  │           │         │
│                         │ getAllEinheiten │           │         │
│                         └─────────────────┘           │         │
└───────────────────────────────────────────────────────┼─────────┘
                                                        │
                                                        ▼
                                               ┌─────────────────┐
                                               │   Claude API    │
                                               │   (Anthropic)   │
                                               └─────────────────┘
```
