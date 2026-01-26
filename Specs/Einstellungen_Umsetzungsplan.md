# Umsetzungsplan: Einstellungen pro Mandant

## Zusammenfassung

Die Rechnungskonfiguration (Zahlungsfrist, IBAN, Rechnungssteller) wird von der statischen `application.yml` in die Datenbank verschoben. Jeder Mandant kann so seine Einstellungen eigenständig über eine neue Admin-Seite "Einstellungen" verwalten. Die Konfiguration wird als JSON-Struktur pro Mandant in einer neuen Tabelle `einstellungen` gespeichert.

---

## Betroffene Komponenten

### Backend (zu erstellen)
| Datei | Beschreibung |
|-------|--------------|
| `V40__Create_Einstellungen_Table.sql` | Flyway-Migration für neue Tabelle |
| `V41__Add_Einstellungen_Translations.sql` | Übersetzungen für Einstellungen-Seite |
| `entity/Einstellungen.java` | Entity für Einstellungen |
| `repository/EinstellungenRepository.java` | Repository für DB-Zugriff |
| `service/EinstellungenService.java` | Service mit Geschäftslogik |
| `controller/EinstellungenController.java` | REST-Controller |
| `dto/EinstellungenDTO.java` | DTO für API-Kommunikation |
| `dto/RechnungKonfigurationDTO.java` | DTO für Rechnungskonfiguration (JSON-Struktur) |

### Backend (zu ändern)
| Datei | Beschreibung |
|-------|--------------|
| `config/RechnungConfig.java` | Wird gelöscht (nicht mehr benötigt) |
| `service/RechnungService.java` | Verwendet neu `EinstellungenService` statt `RechnungConfig` |
| `application.yml` | `rechnung`-Block wird entfernt |

### Frontend (zu erstellen)
| Datei | Beschreibung |
|-------|--------------|
| `models/einstellungen.model.ts` | Model für Einstellungen |
| `services/einstellungen.service.ts` | Service für API-Calls |
| `components/einstellungen/einstellungen.component.ts` | Einstellungen-Seite |
| `components/einstellungen/einstellungen.component.html` | Template |
| `components/einstellungen/einstellungen.component.css` | Styles |

### Frontend (zu ändern)
| Datei | Beschreibung |
|-------|--------------|
| `app.routes.ts` | Neue Route `/einstellungen` |
| `components/navigation/navigation.component.html` | Neuer Menüpunkt vor "Übersetzungen" |

---

## Phasen-Tabelle

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. DB-Migration | `V40__Create_Einstellungen_Table.sql` - Neue Tabelle `einstellungen` mit `org_id` und `konfiguration` (JSONB) |
| [x] | 2. Backend-Entity | `Einstellungen.java` - Entity mit JSON-Konvertierung |
| [x] | 3. Backend-DTOs | `EinstellungenDTO.java` und `RechnungKonfigurationDTO.java` für die JSON-Struktur |
| [x] | 4. Backend-Repository | `EinstellungenRepository.java` mit `findByOrgId` |
| [x] | 5. Backend-Service | `EinstellungenService.java` mit CRUD-Operationen |
| [x] | 6. Backend-Controller | `EinstellungenController.java` - REST-Endpunkte GET/PUT |
| [x] | 7. Backend-Anpassung RechnungService | `RechnungService.java` verwendet neu `EinstellungenService` |
| [x] | 8. Backend-Bereinigung | `RechnungConfig.java` löschen, `application.yml` bereinigen |
| [x] | 9. Frontend-Model | `einstellungen.model.ts` |
| [x] | 10. Frontend-Service | `einstellungen.service.ts` |
| [x] | 11. Frontend-Komponente | `einstellungen.component.ts/html/css` |
| [x] | 12. Routing | Route `/einstellungen` in `app.routes.ts` |
| [x] | 13. Navigation | Menüpunkt "Einstellungen" vor "Übersetzungen" |
| [x] | 14. Übersetzungen | `V41__Add_Einstellungen_Translations.sql` |

---

## Detailspezifikationen

### Phase 1: DB-Migration

**Datei:** `backend-service/src/main/resources/db/migration/V40__Create_Einstellungen_Table.sql`

```sql
CREATE SEQUENCE IF NOT EXISTS zev.einstellungen_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE zev.einstellungen (
    id BIGINT PRIMARY KEY DEFAULT nextval('zev.einstellungen_seq'),
    org_id UUID NOT NULL UNIQUE,
    konfiguration JSONB NOT NULL
);

CREATE INDEX idx_einstellungen_org_id ON zev.einstellungen (org_id);

COMMENT ON TABLE zev.einstellungen IS 'Mandantenspezifische Einstellungen';
COMMENT ON COLUMN zev.einstellungen.konfiguration IS 'JSON-Struktur mit Rechnungskonfiguration';
```

### Phase 2: Backend-Entity

**Datei:** `backend-service/src/main/java/ch/nacht/entity/Einstellungen.java`

```java
@Entity
@Table(name = "einstellungen", schema = "zev")
public class Einstellungen {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "einstellungen_seq")
    @SequenceGenerator(name = "einstellungen_seq", sequenceName = "zev.einstellungen_seq", allocationSize = 1)
    private Long id;

    @Column(name = "org_id", nullable = false, unique = true)
    private UUID orgId;

    @Column(name = "konfiguration", columnDefinition = "jsonb", nullable = false)
    @Convert(converter = RechnungKonfigurationConverter.class)
    private RechnungKonfigurationDTO konfiguration;

    // Getters, Setters
}
```

### Phase 3: Backend-DTOs

**Datei:** `backend-service/src/main/java/ch/nacht/dto/RechnungKonfigurationDTO.java`

```java
public class RechnungKonfigurationDTO {
    @NotBlank
    private String zahlungsfrist;

    @NotBlank
    @Pattern(regexp = "^CH[0-9]{2}\\s?([0-9]{4}\\s?){4}[0-9]{1}$")
    private String iban;

    @Valid
    @NotNull
    private StellerDTO steller;

    public static class StellerDTO {
        @NotBlank
        private String name;
        @NotBlank
        private String strasse;
        @NotBlank
        private String plz;
        @NotBlank
        private String ort;

        // Getters, Setters
    }
}
```

**Datei:** `backend-service/src/main/java/ch/nacht/dto/EinstellungenDTO.java`

```java
public class EinstellungenDTO {
    private Long id;
    private RechnungKonfigurationDTO rechnung;

    // Getters, Setters
}
```

### Phase 6: Backend-Controller

**Datei:** `backend-service/src/main/java/ch/nacht/controller/EinstellungenController.java`

```java
@RestController
@RequestMapping("/api/einstellungen")
@PreAuthorize("hasRole('zev_admin')")
public class EinstellungenController {

    @GetMapping
    public ResponseEntity<EinstellungenDTO> getEinstellungen() { ... }

    @PutMapping
    public ResponseEntity<EinstellungenDTO> saveEinstellungen(
        @Valid @RequestBody EinstellungenDTO dto) { ... }
}
```

### Phase 7: Backend-Anpassung RechnungService

Die Methode `berechneRechnung` in `RechnungService.java` muss angepasst werden:

```java
// Alt (RechnungConfig)
rechnung.setZahlungsfrist(rechnungConfig.getZahlungsfrist());
rechnung.setIban(rechnungConfig.getIban());
rechnung.setStellerName(rechnungConfig.getSteller().getName());
// ...

// Neu (EinstellungenService)
EinstellungenDTO einstellungen = einstellungenService.getEinstellungen();
RechnungKonfigurationDTO config = einstellungen.getRechnung();
rechnung.setZahlungsfrist(config.getZahlungsfrist());
rechnung.setIban(config.getIban());
rechnung.setStellerName(config.getSteller().getName());
// ...
```

### Phase 8: Backend-Bereinigung

1. **Löschen:** `backend-service/src/main/java/ch/nacht/config/RechnungConfig.java`
2. **Entfernen aus `application.yml`:**
   - Gesamter `rechnung:`-Block (Zeilen 92-103)
3. **Hinweis:** Die `adresse`-Konfiguration wird laut Spezifikation nicht mehr benötigt, da die Adresse bei den Mietern konfiguriert ist

### Phase 11: Frontend-Komponente

**Struktur des Formulars:**
- Überschrift: "Einstellungen" mit Icon (settings/tool)
- Abschnitt "Rechnung":
  - Zahlungsfrist (Text-Input)
  - IBAN (Text-Input mit Validierung)
  - Rechnungssteller:
    - Name
    - Strasse
    - PLZ
    - Ort
- Speichern-Button

### Phase 13: Navigation

Der neue Menüpunkt "Einstellungen" wird **vor** "Übersetzungen" eingefügt (nach Zeile 70 in `navigation.component.html`):

```html
<li>
  <a routerLink="/einstellungen" routerLinkActive="zev-navbar__link--active" class="zev-navbar__link"
    (click)="closeMenu()">
    <app-icon name="settings"></app-icon>
    {{ 'EINSTELLUNGEN' | translate }}
  </a>
</li>
```

### Phase 14: Übersetzungen

**Datei:** `backend-service/src/main/resources/db/migration/V41__Add_Einstellungen_Translations.sql`

```sql
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('EINSTELLUNGEN', 'Einstellungen', 'Settings'),
('RECHNUNGSEINSTELLUNGEN', 'Rechnungseinstellungen', 'Invoice Settings'),
('ZAHLUNGSFRIST', 'Zahlungsfrist', 'Payment Terms'),
('IBAN', 'IBAN', 'IBAN'),
('RECHNUNGSSTELLER', 'Rechnungssteller', 'Invoice Issuer'),
('STRASSE', 'Strasse', 'Street'),
('PLZ', 'PLZ', 'Postal Code'),
('ORT', 'Ort', 'City'),
('EINSTELLUNGEN_GESPEICHERT', 'Einstellungen erfolgreich gespeichert', 'Settings saved successfully'),
('EINSTELLUNGEN_FEHLER', 'Fehler beim Speichern der Einstellungen', 'Error saving settings'),
('IBAN_UNGUELTIG', 'Bitte geben Sie eine gültige Schweizer IBAN ein', 'Please enter a valid Swiss IBAN')
ON CONFLICT (key) DO NOTHING;
```

---

## Validierungen

### Backend-Validierungen
| Feld | Validierung |
|------|-------------|
| zahlungsfrist | Pflichtfeld, nicht leer |
| iban | Pflichtfeld, Schweizer IBAN-Format (CH + 19 Ziffern) |
| steller.name | Pflichtfeld, nicht leer |
| steller.strasse | Pflichtfeld, nicht leer |
| steller.plz | Pflichtfeld, nicht leer |
| steller.ort | Pflichtfeld, nicht leer |

### Frontend-Validierungen
| Feld | Validierung |
|------|-------------|
| Alle Felder | Required-Validierung |
| IBAN | Pattern-Validierung für Schweizer IBAN |

---

## Offene Punkte / Annahmen

1. **Annahme:** Die `adresse`-Konfiguration aus `application.yml` wird nicht in die Einstellungen übernommen, da die Mieteradresse direkt bei den Mietern gespeichert ist (gemäss Spezifikation)
2. **Annahme:** Es gibt nur einen Einstellungs-Datensatz pro Mandant (keine Historie)
3. **Annahme:** Wenn noch keine Einstellungen existieren, wird beim ersten GET ein leeres Formular angezeigt. Der Admin muss die Daten initial erfassen, bevor Rechnungen generiert werden können
4. **Annahme:** Die Rechnungsgenerierung wirft einen Fehler, wenn keine Einstellungen für den Mandanten vorhanden sind
5. **Icon:** Laut Spezifikation soll "wenn möglich Zahnrad, sonst Tool" verwendet werden. Feather Icons hat `settings` (Zahnrad) - dieses wird verwendet
