# Umsetzungsplan: Tarifverwaltung – Grundgebühr

## Zusammenfassung

Einführung des neuen Tariftyps `GRUNDGEBUEHR` (monatlicher Festpreis pro Stromzähler). Die Grundgebühr ergänzt die bestehenden kWh-basierten Tariftypen ZEV und VNB und wird pro Einheit (Consumer **und** Producer) in Anzahl ganzer Kalendermonate berechnet. Produzenten erhalten dabei erstmals eigene Rechnungen mit ausschliesslich Grundgebühr-Positionen.

---

## Betroffene Komponenten

| Typ | Datei | Änderungsart |
|-----|-------|--------------|
| Backend Enum | `backend-service/src/main/java/ch/nacht/entity/TarifTyp.java` | Änderung |
| Backend DTO | `backend-service/src/main/java/ch/nacht/dto/TarifZeileDTO.java` | Änderung |
| Backend Service | `backend-service/src/main/java/ch/nacht/service/RechnungService.java` | Änderung |
| Backend Report | `backend-service/src/main/resources/reports/rechnung.jrxml` | Änderung |
| Frontend Enum/Model | `frontend-service/src/app/models/tarif.model.ts` | Änderung |
| Frontend Component | `frontend-service/src/app/components/tarif-form/tarif-form.component.ts` | Änderung |
| Frontend Component | `frontend-service/src/app/components/rechnungen/rechnungen.component.ts` | Änderung |
| Frontend Template | `frontend-service/src/app/components/rechnungen/rechnungen.component.html` | Änderung |
| DB Migration | `backend-service/src/main/resources/db/migration/V49__Add_Grundgebuehr_Translations.sql` | Neu |

> **Keine DB-Migration für TarifTyp nötig:** Die Spalte `tariftyp` in `zev.tarif` ist `VARCHAR`, kein DB-Enum. Der neue Wert `GRUNDGEBUEHR` wird automatisch akzeptiert.

---

## Phasen-Tabelle

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [ ] | 1. Backend – TarifTyp Enum | `GRUNDGEBUEHR` als neuen Wert in `TarifTyp.java` ergänzen |
| [ ] | 2. Backend – TarifZeileDTO | Feld `mengeneinheit` (String) hinzufügen; wird für PDF-Zeile benötigt (kWh vs. Monate) |
| [ ] | 3. Backend – RechnungService | Grundgebühr-Berechnung (volle Monate) + PRODUCER-Support in `berechneRechnungen` |
| [ ] | 4. Backend – rechnung.jrxml | Statisches `CHF / kWh` durch dynamisches `$F{mengeneinheit}` ersetzen; Field deklarieren |
| [ ] | 5. Frontend – tarif.model.ts | `GRUNDGEBUEHR` zum `TarifTyp`-Enum ergänzen |
| [ ] | 6. Frontend – TarifFormComponent | `GRUNDGEBUEHR`-Option im Tariftyp-Dropdown ergänzen |
| [ ] | 7. Frontend – RechnungenComponent | Alle Einheiten (CONSUMER + PRODUCER) laden und mit Typ-Label anzeigen |
| [ ] | 8. Übersetzungen | Flyway-Migration V49 für neue Translation-Keys |

---

## Detailbeschreibung der Phasen

### Phase 1: Backend – TarifTyp Enum

**Datei:** `backend-service/src/main/java/ch/nacht/entity/TarifTyp.java`

```java
public enum TarifTyp {
    ZEV,
    VNB,
    GRUNDGEBUEHR
}
```

---

### Phase 2: Backend – TarifZeileDTO

**Datei:** `backend-service/src/main/java/ch/nacht/dto/TarifZeileDTO.java`

Neues Feld `mengeneinheit` (String) ergänzen:
- Für ZEV/VNB-Zeilen: `"kWh"`
- Für GRUNDGEBUEHR-Zeilen: `"Monate"`

**Konstruktor anpassen:**
```java
public TarifZeileDTO(String bezeichnung, LocalDate von, LocalDate bis,
                     double menge, double preis, double betrag,
                     TarifTyp typ, String mengeneinheit) { ... }
```

**Bestehende Aufrufe in `RechnungService.berechneTarifZeilen` anpassen:**
```java
new TarifZeileDTO(
    tarif.getBezeichnung(), effectiveVon, effectiveBis,
    menge, preis, betrag, typ, "kWh"
)
```

---

### Phase 3: Backend – RechnungService

**Datei:** `backend-service/src/main/java/ch/nacht/service/RechnungService.java`

#### 3a: Neue Methode `berechneGrundgebuehrZeilen`

Berechnet Grundgebühr-Zeilen pro Tarif. Gezählt werden **vollständige Kalendermonate** im Überschneidungszeitraum von Tarifgültigkeit und Rechnungsperiode.

**Definition: vollständiger Kalendermonat** = erster und letzter Tag des Kalendermonats liegen beide im effektiven Zeitraum.

```java
private double berechneGrundgebuehrZeilen(RechnungDTO rechnung,
                                           LocalDate von, LocalDate bis,
                                           List<Tarif> tarife) {
    double total = 0.0;

    for (Tarif tarif : tarife) {
        LocalDate effVon = tarif.getGueltigVon().isBefore(von) ? von : tarif.getGueltigVon();
        LocalDate effBis = tarif.getGueltigBis().isAfter(bis) ? bis : tarif.getGueltigBis();

        int monate = zaehleVolleMonate(effVon, effBis);
        if (monate <= 0) continue;

        double preis = tarif.getPreis().doubleValue();
        double betrag = monate * preis;

        rechnung.addTarifZeile(new TarifZeileDTO(
            tarif.getBezeichnung(), effVon, effBis,
            monate, preis, betrag, TarifTyp.GRUNDGEBUEHR, "Monate"
        ));
        total += betrag;
    }
    return total;
}

/**
 * Zählt vollständige Kalendermonate im Zeitraum [von, bis] (inklusiv).
 * Ein Monat gilt als vollständig, wenn sein erster und letzter Tag im Zeitraum liegen.
 */
private int zaehleVolleMonate(LocalDate von, LocalDate bis) {
    int count = 0;
    LocalDate monthStart = von.withDayOfMonth(1);
    while (!monthStart.isAfter(bis)) {
        LocalDate monthEnd = monthStart.withDayOfMonth(
            monthStart.getMonth().length(java.time.Year.isLeap(monthStart.getYear()))
        );
        if (!monthStart.isBefore(von) && !monthEnd.isAfter(bis)) {
            count++;
        }
        monthStart = monthStart.plusMonths(1);
    }
    return count;
}
```

#### 3b: `berechneRechnung` um Grundgebühr erweitern

In der bestehenden Methode `berechneRechnung` nach den ZEV/VNB-Zeilen:
```java
// GRUNDGEBUEHR (optional - kein Fehler wenn kein Tarif vorhanden)
List<Tarif> grundgebuehrTarife = tarifService.getTarifeForZeitraum(TarifTyp.GRUNDGEBUEHR, von, bis);
if (!grundgebuehrTarife.isEmpty()) {
    totalBetrag += berechneGrundgebuehrZeilen(rechnung, von, bis, grundgebuehrTarife);
}
```

#### 3c: `berechneRechnungen` – PRODUCER-Support

Aktuell: nur CONSUMER-Einheiten verarbeitet. Neu: PRODUCER-Einheiten erhalten ausschliesslich Grundgebühr-Positionen.

```java
for (Long einheitId : einheitIds) {
    einheitRepository.findById(einheitId).ifPresent(einheit -> {
        if (einheit.getTyp() == EinheitTyp.CONSUMER) {
            // Bestehende Logik unverändert
            List<Mieter> mieter = mieterService.getMieterForQuartal(einheitId, von, bis);
            if (mieter.isEmpty()) {
                rechnungen.add(berechneRechnung(einheit, null, von, bis));
            } else {
                for (Mieter m : mieter) {
                    LocalDate effVon = ...;
                    LocalDate effBis = ...;
                    rechnungen.add(berechneRechnung(einheit, m, effVon, effBis));
                }
            }
        } else if (einheit.getTyp() == EinheitTyp.PRODUCER) {
            // Nur Grundgebühr für Produzenten
            RechnungDTO rechnung = berechneProduzentenRechnung(einheit, von, bis);
            if (!rechnung.getTarifZeilen().isEmpty()) {
                rechnungen.add(rechnung);
            }
        }
    });
}
```

#### 3d: Neue Methode `berechneProduzentenRechnung`

```java
private RechnungDTO berechneProduzentenRechnung(Einheit einheit, LocalDate von, LocalDate bis) {
    RechnungDTO rechnung = new RechnungDTO();
    rechnung.setEinheitId(einheit.getId());
    rechnung.setEinheitName(einheit.getName());
    rechnung.setMesspunkt(einheit.getMesspunkt());
    rechnung.setVon(von);
    rechnung.setBis(bis);
    rechnung.setErstellungsdatum(LocalDate.now());

    List<Tarif> tarife = tarifService.getTarifeForZeitraum(TarifTyp.GRUNDGEBUEHR, von, bis);
    double total = berechneGrundgebuehrZeilen(rechnung, von, bis, tarife);
    double endBetrag = RechnungService.roundTo5Rappen(total);
    rechnung.setTotalBetrag(total);
    rechnung.setRundung(endBetrag - total);
    rechnung.setEndBetrag(endBetrag);

    EinstellungenDTO einstellungen = einstellungenService.getEinstellungenOrThrow();
    RechnungKonfigurationDTO config = einstellungen.getRechnung();
    rechnung.setZahlungsfrist(config.getZahlungsfrist());
    rechnung.setIban(config.getIban());
    RechnungKonfigurationDTO.StellerDTO steller = config.getSteller();
    rechnung.setStellerName(steller.getName());
    rechnung.setStellerStrasse(steller.getStrasse());
    rechnung.setStellerPlzOrt(steller.getPlz() + " " + steller.getOrt());

    return rechnung;
}
```

#### 3e: Tarif-Validierung für Konsumenten einschränken

Die Methode `validateTarifAbdeckung` (für ZEV/VNB) soll nur aufgerufen werden, wenn CONSUMER-Einheiten im Request vorhanden sind:

```java
boolean hasConsumers = einheitIds.stream()
    .anyMatch(id -> einheitRepository.findById(id)
        .map(e -> e.getTyp() == EinheitTyp.CONSUMER)
        .orElse(false));

if (hasConsumers) {
    tarifService.validateTarifAbdeckung(von, bis);
}
```

> **Hinweis:** Performance-Optimierung möglich (Einheiten einmalig laden), aber für die Umsetzung reicht die einfachere Variante.

---

### Phase 4: Backend – rechnung.jrxml

**Datei:** `backend-service/src/main/resources/reports/rechnung.jrxml`

#### 4a: Field-Deklaration ergänzen

```xml
<field name="mengeneinheit" class="java.lang.String"/>
```

#### 4b: Statisches `CHF / kWh` durch dynamisches Feld ersetzen

Aktuell (staticText):
```xml
<element kind="staticText" ...>
    <text><![CDATA[CHF / kWh]]></text>
```

Neu (textField):
```xml
<element kind="textField" ...>
    <expression><![CDATA["CHF / " + $F{mengeneinheit}]]></expression>
```

> **Build-Hinweis:** Nach Änderung am JRXML muss `mvn compile` ausgeführt werden, damit das compilierte `.jasper`-File neu generiert wird.

---

### Phase 5: Frontend – tarif.model.ts

**Datei:** `frontend-service/src/app/models/tarif.model.ts`

```typescript
export enum TarifTyp {
  ZEV = 'ZEV',
  VNB = 'VNB',
  GRUNDGEBUEHR = 'GRUNDGEBUEHR'
}
```

---

### Phase 6: Frontend – TarifFormComponent

**Datei:** `frontend-service/src/app/components/tarif-form/tarif-form.component.ts`

```typescript
tarifTypOptions = [
  { value: TarifTyp.ZEV, label: 'ZEV (Solarstrom)' },
  { value: TarifTyp.VNB, label: 'VNB (Netzstrom)' },
  { value: TarifTyp.GRUNDGEBUEHR, label: 'Grundgebühr (CHF/Monat/Zähler)' }
];
```

> Alle Label-Texte im Template über `TranslatePipe` ausgeben – die hardcodierten Labels hier dienen nur als Fallback im `value`-Binding. Wenn das Template bereits `translate` verwendet, Labels durch Translation-Keys ersetzen.

---

### Phase 7: Frontend – RechnungenComponent

**Datei:** `frontend-service/src/app/components/rechnungen/rechnungen.component.ts`

#### 7a: Alle Einheiten laden (nicht nur CONSUMER)

```typescript
// Vorher: this.consumers = data.filter(...)
// Neu:
this.einheiten = data
  .sort((a, b) => {
    // Erst nach Typ (CONSUMER vor PRODUCER), dann nach Name
    if (a.typ !== b.typ) return a.typ === EinheitTyp.CONSUMER ? -1 : 1;
    return (a.name || '').localeCompare(b.name || '');
  });
```

- Property `consumers` → umbenennen in `einheiten` (inkl. Template-Referenzen)
- `allSelected()` / `someSelected()` / `onSelectAllToggle()` auf `einheiten` anpassen

#### 7b: Template anpassen

- Sektion-Label von `KONSUMENTEN_WAEHLEN` → `EINHEITEN_WAEHLEN`
- Jede Zeile zeigt Typ-Label in eckigen Klammern: `[Konsument]` / `[Produzent]`
- Verwendung der vorhandenen Translation-Keys `KONSUMENT` / `PRODUZENT`

```html
<!-- Template-Ausschnitt -->
<label>
  <input type="checkbox" [checked]="selectedEinheitIds.has(e.id!)"
         (change)="onEinheitToggle(e.id!)">
  {{ e.name }} [{{ (e.typ === 'CONSUMER' ? 'KONSUMENT' : 'PRODUZENT') | translate }}]
</label>
```

---

### Phase 8: DB Migration – Übersetzungen

**Datei:** `backend-service/src/main/resources/db/migration/V49__Add_Grundgebuehr_Translations.sql`

```sql
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
-- Neuer Tariftyp
('TARIFTYP_GRUNDGEBUEHR',      'Grundgebühr (CHF/Monat/Zähler)',        'Basic Fee (CHF/month/meter)'),
('TARIFTYP_HINT_GRUNDGEBUEHR', 'Monatlicher Festpreis pro Stromzähler', 'Monthly fixed price per electricity meter'),

-- PDF-Einheit für Grundgebühr
('MONATE',                     'Monate',                                 'Months'),

-- Rechnungsformular (Einheiten statt nur Konsumenten)
('EINHEITEN_WAEHLEN',          'Einheiten auswählen',                   'Select Units'),
('KEINE_EINHEITEN_VORHANDEN',  'Keine Einheiten vorhanden',             'No units available')

ON CONFLICT (key) DO NOTHING;
```

---

## Validierungen

### Frontend-Validierungen
1. **Tarifformular – unveränderter Bestand:** Preis > 0, alle Pflichtfelder, Datum von ≤ Datum bis
2. **Rechnungsformular:** mind. eine Einheit ausgewählt (bestehend; Logik bleibt unverändert)

### Backend-Validierungen
1. **TarifService.saveTarif:** Keine überlappenden Tarife desselben Typs (bestehend; gilt auch für GRUNDGEBUEHR automatisch)
2. **RechnungService:** ZEV/VNB-Validierung (`validateTarifAbdeckung`) nur wenn CONSUMER-Einheiten im Request – kein Fehler bei reiner PRODUCER-Selektion
3. **GRUNDGEBUEHR ist optional:** Kein Fehler, wenn kein Tarif vorhanden; Grundgebühr-Zeilen werden einfach nicht hinzugefügt
4. **Volle Monate:** Rechnungszeitraum < 1 Monat → 0 Monate → keine GRUNDGEBUEHR-Zeile

---

## Offene Punkte / Annahmen

1. **Annahme:** `PREIS_HINT` im Tarifformular ("CHF pro kWh") passt nicht für GRUNDGEBUEHR. Da der Hint statisch ist, bleibt er vorerst unverändert – der User sieht den neuen Tariftyp-Label und den Dropdown-Eintrag als ausreichende Kontextualisierung. Falls gewünscht, kann ein typ-spezifischer Hint in einer Folge-Phase ergänzt werden.
2. **Annahme:** Das `.jasper`-File wird via Maven-Build neu kompiliert. Die `jrxml`-Datei ist Source of Truth; das `.jasper`-File wird **nicht** manuell gepflegt.
3. **Annahme:** Produzenten-Rechnungen ohne Grundgebühr (leere tarifZeilen) werden **nicht** generiert (defensive Filterung in `berechneRechnungen`).
4. **Annahme:** Da GRUNDGEBUEHR in der Quartals-/Jahresvalidierung optional ist, wird `validateTarifAbdeckung` **nicht** für GRUNDGEBUEHR aufgerufen – konsistent mit Spec-Entscheid.
5. **Annahme:** Die `mengeneinheit` für kWh-Zeilen wird direkt im bestehenden `berechneTarifZeilen`-Aufruf als Literal `"kWh"` gesetzt (kein separater Translation-Key nötig, da das PDF-Layout bereits `CHF / kWh` als bekannte Einheit zeigt).
