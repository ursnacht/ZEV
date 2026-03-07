# Erstelle Anforderungsdokument

Erstelle ein vollständiges Anforderungsdokument (Spec) aus den eingegebenen Anforderungen.

## Input
* Erster Parameter: Name des Features (z.B. `Zahlungsverwaltung`)
* Weitere Parameter / Freitext: Die rohen Anforderungen, Wünsche und Rahmenbedingungen

$ARGUMENTS

## Vorgehen
1. **Analysiere die Eingabe** - Lies und verstehe alle angegebenen Anforderungen
2. **Recherchiere den Kontext** - Prüfe bestehende Specs in `/Specs/` auf verwandte Features und Abhängigkeiten
3. **Prüfe den bestehenden Code** - Identifiziere bereits vorhandene Komponenten, die relevant sind
4. **Ergänze fehlende Abschnitte** - Leite aus den Anforderungen sinnvolle Akzeptanzkriterien, Edge Cases und NFR ab
5. **Stelle Rückfragen** bei wesentlichen Unklarheiten, die den Scope beeinflussen - BEVOR du das Dokument erstellst

## Output
* Erstelle eine neue Datei: `Specs/[FeatureName].md`
* Verwende exakt die Struktur des Templates `Specs/SPEC.md`
* Der Feature-Name als H1-Titel (erster Parameter)

## Hinweise
* **Akzeptanzkriterien** müssen konkret und testbar sein (mit Checkbox `[ ]`)
* **Sicherheit (NFR-2):** Rollen explizit nennen (`zev` oder `zev_admin`)
* **Multi-Tenancy:** Falls neue Daten gespeichert werden, auf Mandantenfähigkeit hinweisen (`org_id`)
* **i18n:** Alle UI-Texte müssen via `TranslationService` kommen - als Abhängigkeit vermerken
* **Edge Cases:** Mindestens leere Listen, Netzwerkfehler und ungültige Eingaben behandeln
* Abschnitte, für die keine Informationen vorliegen, mit sinnvollen Standardannahmen füllen und unter **8. Offene Fragen** vermerken

## Referenz
* `Specs/SPEC.md` - Template-Struktur
* `Specs/generell.md` - Allgemeine Projektanforderungen (i18n, Design System, Multi-Tenancy)
* `Specs/Tarifverwaltung.md` - Beispiel einer vollständigen Spec
