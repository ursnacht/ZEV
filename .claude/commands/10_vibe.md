# Vibe → Anforderungsdokument + Umsetzung

Ergänze mit frei formulierten Anforderungen ein bestehendes Anforderungsdokument und setze die neuen Anforderungen direkt um.

## Input

$ARGUMENTS

* **Feature-Name** (erster Parameter): z.B. `Zahlungsverwaltung`
* **Anforderungen** (Rest): Neue oder angepasste Anforderungen, Änderungen oder Rahmenbedingungen zu einem bereits bestehenden Anforderungsdokument.

## Vorgehen
1. **Lies die Referenzdokumente** – `Specs/[Feature-Name].md` und `Specs/generell.md`.
2. **Verstehe die neuen Anforderungen** aus dem Input.
3. **Stelle Rückfragen** bei wesentlichen Unklarheiten, die die Umsetzung beeinflussen – BEVOR du weitermachst.
4. **Ergänze das Anforderungsdokument** mit den zusätzlichen Anforderungen.
    - Ergänze die Funktionalen Anforderungen, die Akzeptanzkriterien und die Nichtfunktionalen Anforderungen wo nötig.
5. **Setze die neuen Anforderungen um** gemäss den Vorgaben in `Specs/generell.md`.

## Output
* Ergänze die bestehende Anforderungsdatei: `Specs/[Feature-Name].md`
* Ergänze den bestehenden Umsetzungsplan (falls vorhanden): `Specs/[Feature-Name]_Umsetzungsplan.md`
* Weiche nicht von der Struktur des Templates `Specs/SPEC.md` ab
* Implementiere die neuen Anforderungen im Anwendungscode

## Referenz
* `Specs/[Feature-Name].md` - das bestehende und zu ergänzende Anforderungsdokument
* `Specs/[Feature-Name]_Umsetzungsplan.md` - der zu ergänzende Umsetzungsplan (falls vorhanden) 
* `Specs/SPEC.md` – Template-Struktur
* `Specs/generell.md` – Allgemeine Projektanforderungen (i18n, Design System, Multi-Tenancy)
