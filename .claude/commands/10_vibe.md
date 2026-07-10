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
    - **Design System zuerst prüfen** (siehe Block unten) – kein UI-CSS ohne diese Prüfung.

## 🎨 Design System – zwingend beachten (bei UI-Änderungen)
* **Kein neues UI-CSS ohne vorherige Prüfung des Design Systems.** Bestehende Klassen wiederverwenden, wiederverwendbare Styles gehören ins Design System – Details, Klassenliste und Regeln stehen in `Specs/generell.md` (Abschnitt „Design System"). Dort nachschlagen, nicht hier duplizieren.

## ⚠️ Datenbank-Migrationen (Flyway) – zwingend beachten
* **Ändere NIEMALS ein bestehendes Migrationsskript** (auch keines, das du in derselben Session erstellt hast), bevor du nicht via MCP-Server `zev-db` geprüft hast, ob es bereits ausgeführt wurde:
  `SELECT version, success FROM zev.flyway_schema_history WHERE version = '<NR>';`
* Wurde die Migration bereits ausgeführt (Treffer in `flyway_schema_history`), lege **immer eine neue Migration** mit der nächsthöheren Versionsnummer an – auch für kleine Ergänzungen wie zusätzliche Übersetzungs-Keys.
* Neue Übersetzungen immer mit `ON CONFLICT (key) DO NOTHING`.
* Prüfe die höchste vergebene Versionsnummer, bevor du eine neue Migration anlegst (Dateien in `backend-service/src/main/resources/db/migration/`).

## Output
* Ergänze die bestehende Anforderungsdatei: `Specs/[Feature-Name].md`
* Ergänze den bestehenden Umsetzungsplan (falls vorhanden): `Specs/[Feature-Name]_Umsetzungsplan.md`
* Weiche nicht von der Struktur des Templates `Specs/SPEC.md` ab
* Implementiere die neuen Anforderungen im Anwendungscode
* Passe die bestehenden Tests falls nötig an

## Referenz
* `Specs/[Feature-Name].md` - das bestehende und zu ergänzende Anforderungsdokument
* `Specs/[Feature-Name]_Umsetzungsplan.md` - der zu ergänzende Umsetzungsplan (falls vorhanden) 
* `Specs/SPEC.md` – Template-Struktur
* `Specs/generell.md` – Allgemeine Projektanforderungen (i18n, Design System, Multi-Tenancy)
* `Specs/Übersetzungsverwaltung.md` – Beispiel einer vollständigen Spec