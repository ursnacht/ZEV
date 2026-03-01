# Umsetzungsplan: Einstellungen-Merger in Organisation-Tabelle

## Zusammenfassung

Die separate Tabelle `zev.einstellungen` wird aufgelöst und die `konfiguration`-Spalte (JSONB) direkt in `zev.organisation` verschoben. Da die Beziehung 1:1 ist, ist die eigene Tabelle konzeptuell überflüssig. Die API (`GET/PUT /api/einstellungen`) und das Frontend bleiben vollständig unverändert — es ist ein reines Backend-/DB-Refactoring.

---

## Betroffene Komponenten

| Typ | Datei | Änderungsart |
|-----|-------|--------------|
| DB Migration | `backend-service/src/main/resources/db/migration/V47__Merge_Einstellungen_Into_Organisation.sql` | Neu |
| Backend Entity | `backend-service/src/main/java/ch/nacht/entity/Organisation.java` | Änderung (+`konfiguration`-Feld) |
| Backend Entity | `backend-service/src/main/java/ch/nacht/entity/Einstellungen.java` | **Löschen** |
| Backend Repository | `backend-service/src/main/java/ch/nacht/repository/EinstellungenRepository.java` | **Löschen** |
| Backend Service | `backend-service/src/main/java/ch/nacht/service/EinstellungenService.java` | Änderung (Umbau auf `OrganisationRepository`) |
| Backend Controller | `backend-service/src/main/java/ch/nacht/controller/EinstellungenController.java` | Unverändert |
| Backend DTO | `backend-service/src/main/java/ch/nacht/dto/EinstellungenDTO.java` | Unverändert |
| Backend DTO | `backend-service/src/main/java/ch/nacht/dto/RechnungKonfigurationDTO.java` | Unverändert |
| Test | `backend-service/src/test/java/ch/nacht/service/EinstellungenServiceTest.java` | Änderung (Mock-Umbau) |
| Test | `backend-service/src/test/java/ch/nacht/controller/EinstellungenControllerTest.java` | Prüfen / ggf. leichte Bereinigung |
| Test | `backend-service/src/test/java/ch/nacht/repository/OrganisationRepositoryIT.java` | Änderung (+`konfiguration`-Testfall) |
| Test | `backend-service/src/test/java/ch/nacht/service/OrganisationServiceTest.java` | Änderung (+`konfiguration`-Testfall) |

---

## Phasen-Tabelle

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. DB-Migration | Flyway V47: `konfiguration` nach `organisation`, `einstellungen`-Tabelle droppen |
| [x] | 2. Organisation Entity | `konfiguration`-Feld (JSONB) in `Organisation.java` ergänzen |
| [x] | 3. EinstellungenService umbauen | `EinstellungenRepository` durch `OrganisationRepository` ersetzen, Logik vereinfachen |
| [x] | 4. Einstellungen-Artefakte löschen | `Einstellungen.java` und `EinstellungenRepository.java` löschen |
| [x] | 5. EinstellungenServiceTest anpassen | Mock-Umbau: `EinstellungenRepository` → `OrganisationRepository` |
| [x] | 6. OrganisationRepositoryIT ergänzen | Integrationstest für `konfiguration`-Feld hinzufügen |
| [x] | 7. OrganisationServiceTest ergänzen | Sicherstellen, dass `findOrCreate` `konfiguration` nicht überschreibt |
| [x] | 8. EinstellungenControllerTest prüfen | Sicherstellen, dass kein Bezug auf `EinstellungenRepository` vorhanden ist |

---

## Detailbeschreibung der Phasen

### Phase 1: DB-Migration

**Datei:** `backend-service/src/main/resources/db/migration/V47__Merge_Einstellungen_Into_Organisation.sql`

```sql
-- Schritt 1: konfiguration-Spalte zur Organisation-Tabelle hinzufügen
ALTER TABLE zev.organisation ADD COLUMN konfiguration JSONB;

-- Schritt 2: Bestehende Einstellungs-Daten migrieren
UPDATE zev.organisation o
SET konfiguration = e.konfiguration
FROM zev.einstellungen e
WHERE e.org_id = o.id;

-- Schritt 3: einstellungen-Tabelle und Sequenz löschen
DROP TABLE zev.einstellungen;
DROP SEQUENCE IF EXISTS zev.einstellungen_seq;
```

### Phase 2: Organisation Entity

**Datei:** `backend-service/src/main/java/ch/nacht/entity/Organisation.java`

Neues Feld ergänzen:

```java
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "konfiguration", columnDefinition = "jsonb")
private String konfiguration;

// Getter + Setter:
public String getKonfiguration() { return konfiguration; }
public void setKonfiguration(String konfiguration) { this.konfiguration = konfiguration; }
```

### Phase 3: EinstellungenService umbauen

**Datei:** `backend-service/src/main/java/ch/nacht/service/EinstellungenService.java`

Änderungen:
- Dependency: `EinstellungenRepository` → `OrganisationRepository`
- `getEinstellungen()`: `organisationRepository.findById(orgId)`, gibt `null` bei `konfiguration == null`
- `saveEinstellungen(dto)`: lädt `Organisation` per `findById`, setzt `konfiguration`, speichert — kein Create/Update-Branch mehr nötig
- `EinstellungenDTO.id` = `organisation.id`

```java
@Service
public class EinstellungenService {

    private final OrganisationRepository organisationRepository;
    private final OrganizationContextService organizationContextService;

    public EinstellungenService(OrganisationRepository organisationRepository,
                                OrganizationContextService organizationContextService) {
        this.organisationRepository = organisationRepository;
        this.organizationContextService = organizationContextService;
    }

    @Transactional(readOnly = true)
    public EinstellungenDTO getEinstellungen() {
        Long orgId = organizationContextService.getCurrentOrgId();
        return organisationRepository.findById(orgId)
            .filter(org -> org.getKonfiguration() != null)
            .map(this::toDTO)
            .orElse(null);
    }

    @Transactional(readOnly = true)
    public EinstellungenDTO getEinstellungenOrThrow() {
        EinstellungenDTO dto = getEinstellungen();
        if (dto == null) {
            throw new IllegalStateException(
                "Einstellungen sind noch nicht konfiguriert. Bitte zuerst die Einstellungen erfassen.");
        }
        return dto;
    }

    @Transactional
    public EinstellungenDTO saveEinstellungen(EinstellungenDTO dto) {
        Long orgId = organizationContextService.getCurrentOrgId();
        Organisation org = organisationRepository.findById(orgId)
            .orElseThrow(() -> new IllegalStateException("Organisation nicht gefunden: " + orgId));
        org.setKonfiguration(toJson(dto.getRechnung()));
        Organisation saved = organisationRepository.save(org);
        return toDTO(saved);
    }

    private EinstellungenDTO toDTO(Organisation org) {
        RechnungKonfigurationDTO konfiguration = fromJson(org.getKonfiguration());
        return new EinstellungenDTO(org.getId(), konfiguration);
    }

    // toJson / fromJson bleiben unverändert
}
```

### Phase 4: Einstellungen-Artefakte löschen

Folgende Dateien werden **gelöscht** (kein Ersatz):
- `backend-service/src/main/java/ch/nacht/entity/Einstellungen.java`
- `backend-service/src/main/java/ch/nacht/repository/EinstellungenRepository.java`

### Phase 5: EinstellungenServiceTest anpassen

**Datei:** `backend-service/src/test/java/ch/nacht/service/EinstellungenServiceTest.java`

Änderungen:
- `@Mock EinstellungenRepository` → `@Mock OrganisationRepository organisationRepository`
- Testdaten: statt `new Einstellungen(orgId, json)` nun `new Organisation()` mit `id` und `konfiguration`
- Alle `einstellungenRepository.findByOrgId(...)` → `organisationRepository.findById(...)`
- Testfälle für "NewSettings_Creates" / "ExistingSettings_Updates" vereinfachen — es gibt nur noch ein Update
- Neuer Testfall: `saveEinstellungen_OrganisationNotFound_ThrowsIllegalStateException`

Testfall-Übersicht nach Refactoring:

| Testfall | Beschreibung |
|----------|-------------|
| `getEinstellungen_KonfigurationVorhanden_ReturnsDTO` | Lädt Organisation mit gesetzter `konfiguration` |
| `getEinstellungen_KonfigurationNull_ReturnsNull` | Organisation vorhanden, `konfiguration == null` |
| `getEinstellungen_OrganisationNichtGefunden_ReturnsNull` | `findById` liefert `empty` |
| `getEinstellungenOrThrow_Vorhanden_ReturnsDTO` | Normalpfad |
| `getEinstellungenOrThrow_NichtKonfiguriert_ThrowsException` | Wirft `IllegalStateException` |
| `saveEinstellungen_Speichert_Konfiguration` | Setzt `konfiguration` und speichert |
| `saveEinstellungen_OrganisationNichtGefunden_ThrowsException` | `findById` liefert `empty` → Exception |
| `getEinstellungen_UngueltigesJson_ThrowsIllegalArgumentException` | JSON-Fehlerbehandlung |
| `getEinstellungen_VerwendetCurrentOrgId` | Multi-Tenancy-Isolation |

### Phase 6: OrganisationRepositoryIT ergänzen

**Datei:** `backend-service/src/test/java/ch/nacht/repository/OrganisationRepositoryIT.java`

Neuen Testfall hinzufügen:

```java
@Test
void konfiguration_WirdGespeichertUndGeladen() {
    Organisation org = new Organisation();
    org.setKeycloakOrgId(UUID.randomUUID());
    org.setName("Test Org");
    org.setErstelltAm(LocalDateTime.now());
    org.setKonfiguration("{\"zahlungsfrist\":\"30 Tage\"}");
    Organisation saved = organisationRepository.save(org);

    Organisation loaded = organisationRepository.findById(saved.getId()).orElseThrow();
    assertThat(loaded.getKonfiguration()).contains("30 Tage");
}

@Test
void konfiguration_IstNullbar() {
    Organisation org = new Organisation();
    org.setKeycloakOrgId(UUID.randomUUID());
    org.setName("Org ohne Einstellungen");
    org.setErstelltAm(LocalDateTime.now());
    // konfiguration nicht gesetzt
    Organisation saved = organisationRepository.save(org);

    Organisation loaded = organisationRepository.findById(saved.getId()).orElseThrow();
    assertThat(loaded.getKonfiguration()).isNull();
}
```

### Phase 7: OrganisationServiceTest ergänzen

**Datei:** `backend-service/src/test/java/ch/nacht/service/OrganisationServiceTest.java`

Neuen Testfall hinzufügen, der sicherstellt, dass `findOrCreate` beim Name-Update die `konfiguration` nicht überschreibt:

```java
@Test
void findOrCreate_NameUpdate_UeberschreibtKonfigurationNicht() {
    Organisation existing = new Organisation();
    existing.setId(1L);
    existing.setKeycloakOrgId(testUuid);
    existing.setName("Alter Name");
    existing.setErstelltAm(LocalDateTime.now());
    existing.setKonfiguration("{\"zahlungsfrist\":\"30 Tage\"}");

    when(organisationRepository.findByKeycloakOrgId(testUuid))
        .thenReturn(Optional.of(existing));
    when(organisationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Organisation result = organisationService.findOrCreate(testUuid, "Neuer Name");

    assertThat(result.getName()).isEqualTo("Neuer Name");
    assertThat(result.getKonfiguration()).isEqualTo("{\"zahlungsfrist\":\"30 Tage\"}");
}
```

### Phase 8: EinstellungenControllerTest prüfen

**Datei:** `backend-service/src/test/java/ch/nacht/controller/EinstellungenControllerTest.java`

Der Controller-Test verwendet `@MockitoBean EinstellungenService` — das Interface des Service ändert sich nicht. Es ist lediglich zu prüfen:
- Kein direkter Import auf `EinstellungenRepository` oder `Einstellungen`-Entity
- Kein `@MockitoBean EinstellungenRepository` (wird nicht verwendet)
- Alle bestehenden Tests laufen weiterhin unverändert durch

---

## Validierungen

### Backend-Validierungen (unverändert)

| Feld | Regel | Fehler |
|------|-------|--------|
| `rechnung` | `@NotNull` | 400 Bad Request |
| `rechnung.zahlungsfrist` | `@NotBlank` | 400 Bad Request |
| `rechnung.iban` | `@NotBlank` + Pattern `CH\d{2}...` | 400 Bad Request |
| `rechnung.steller` | `@NotNull` | 400 Bad Request |
| `rechnung.steller.name` | `@NotBlank` | 400 Bad Request |
| `rechnung.steller.strasse` | `@NotBlank` | 400 Bad Request |
| `rechnung.steller.plz` | `@NotBlank` | 400 Bad Request |
| `rechnung.steller.ort` | `@NotBlank` | 400 Bad Request |

---

## Offene Punkte / Annahmen

1. **Annahme:** `konfiguration` bleibt nullable in `organisation`. Der `null`-Zustand entspricht "noch nicht konfiguriert" und wird vom Controller korrekt als `204 No Content` zurückgegeben.
2. **Annahme:** Die `id` im `EinstellungenDTO` wird im Frontend nicht ausgewertet (nur `rechnung`-Felder). Die `id` zeigt neu auf `organisation.id` statt auf `einstellungen.id` — für das Frontend ist das transparent.
3. **Annahme:** Da Auto-Provisioning bei jedem Login erfolgt, ist `findById(orgId)` in `saveEinstellungen` immer erfolgreich. Die `orElseThrow`-Exception ist nur eine Sicherheitsnetz-Behandlung.
4. **Annahme:** Keine Änderungen an `RechnungPdfService`, `StatistikPdfService` o.ä. — diese rufen `einstellungenService.getEinstellungenOrThrow()` auf und werden durch den Refactoring nicht berührt.
5. **Kein ArchUnit-Impact:** Die Klassen `Einstellungen` und `EinstellungenRepository` werden gelöscht — zu prüfen, ob der `ArchitectureTest` explizite Referenzen auf diese Klassen enthält.
