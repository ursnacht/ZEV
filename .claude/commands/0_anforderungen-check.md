# Prüfe das Anforderungsdokument

Erstelle einen strukturierten Bericht über Vollständigkeit, Korrektheit und Umsetzbarkeit eines Anforderungsdokuments.

## Input

* **Feature-Name**: $ARGUMENTS (z.B. `Debitorkontrolle`) → liest `Specs/[Feature-Name].md`  
  Falls nicht angegeben: aus dem Konversations-Kontext ableiten (z.B. wenn zuvor `/0_anforderungen` ausgeführt wurde); nur wenn unklar: nachfragen.

---

## Vorgehen

### Phase 1: Dokument lesen
1. Lies `Specs/[Feature-Name].md`
2. Lies `Specs/SPEC.md` (Template-Struktur) zum Abgleich
3. Lies `Specs/generell.md` (allgemeine Anforderungen)

### Phase 2: Kontext recherchieren
4. Lies verwandte Specs aus `/Specs/` (über Abhängigkeiten in Abschnitt 6)
5. Prüfe den **bestehenden Code** auf Widersprüche und Machbarkeit:
   - Relevante Entities / DTOs (z.B. sind genannte FKs nullable?)
   - Bestehende Services (z.B. können die genannten Felder wirklich geliefert werden?)
   - Bestehende Controller (z.B. verändert die Spec eine bestehende API?)

### Phase 3: Systematische Prüfung

**Struktur:**
- Sind alle 8 Abschnitte des Templates vorhanden?
- Sind keine Abschnitte leer oder nur mit Platzhaltern befüllt?

**Akzeptanzkriterien:**
- Haben alle Kriterien eine Checkbox (`[ ]`)?
- Sind sie konkret und testbar (nicht "soll funktionieren", sondern prüfbares Verhalten)?
- Decken sie alle FR-Anforderungen ab?
- Decken sie NFR-2 (Sicherheit/Rolle) ab?
- Sind Edge Cases aus Abschnitt 5 als testbare AK abgebildet?

**Sicherheit (NFR-2):**
- Ist die Rolle explizit genannt (`zev` oder `zev_admin`)?
- Ist `@PreAuthorize` / `AuthGuard` erwähnt?

**Multi-Tenancy:**
- Wird `org_id` in neuen Tabellen gefordert?
- Wird serverseitiges Setzen (nicht vom Client) spezifiziert?

**i18n:**
- Sind alle UI-Texte via `TranslationService` gefordert?
- Sind neue Translations als Abhängigkeit vermerkt?

**Persistierung:**
- Sind alle Spalten mit Typ, Pflicht/Optional und Constraints spezifiziert?
- Sind FK-Constraints vollständig (ON DELETE Verhalten)?
- Sind Unique-Constraints definiert (falls Upsert oder Duplikateprüfung nötig)?
- Ist Flyway-Migration erwähnt?

**Edge Cases:**
- Mindestens abgedeckt: leere Liste, Netzwerkfehler, ungültige Eingaben
- Verhalten bei referenziellen Abhängigkeiten (Löschen, Kaskadierung)?

**Widersprüche mit bestehendem Code:**
- Stimmen FK-Felder (nullable/not-null) mit dem Code überein?
- Stimmen API-Felder mit den DTOs überein?
- Sind neue Constraints mit bestehenden Daten kompatibel?

**Offene Fragen:**
- Sind noch unbeantwortete Fragen vorhanden, die die Umsetzung blockieren?

---

## Output

Gib den Bericht **im Chat** aus (keine Datei erstellen, Spec nicht verändern).

```markdown
# Anforderungs-Review: [Feature-Name]

## Ergebnis: [Anzahl Befunde] Befunde ([K] kritisch, [M] minor)

### Kritische Inkonsistenzen
> Blockieren die Umsetzung oder führen zu falschem Verhalten

| # | Problem | Abschnitt | Empfehlung |
|---|---------|-----------|------------|
| 1 | Beschreibung... | FR-2 | Empfehlung... |

### Kleinere Lücken
> Sollten vor der Umsetzung geklärt werden

| # | Problem | Abschnitt | Empfehlung |
|---|---------|-----------|------------|
| 1 | Beschreibung... | AK | Empfehlung... |

### Offene Fragen (falls vorhanden)
* Frage 1
* Frage 2

## Fazit
[Bereit zur Umsetzung / Korrekturbedarf / Klärungsbedarf]
```

Falls keine Befunde: "Keine Befunde — Spec ist vollständig und umsetzungsbereit."

---

## Hinweise

* **Konservativ bewerten:** Im Zweifel als Befund aufführen
* **Nachweis liefern:** Für Code-Widersprüche die konkrete Datei und Zeile angeben
* **Keine Korrekturen vornehmen:** Nur berichten, nicht ändern — Korrekturen macht der User selbst
* Diese Analyse ist **READ-ONLY** — keine Spec-Datei wird verändert

---

## Referenz
* `Specs/SPEC.md` - Template-Struktur
* `Specs/generell.md` - Allgemeine Projektanforderungen (i18n, Design System, Multi-Tenancy)
* `Specs/Tarifverwaltung.md` - Beispiel einer vollständigen Spec
