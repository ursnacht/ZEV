# Erstelle Anforderungsdokument aus dem Code (Reverse Engineering)

Erstelle aus dem bestehenden Code eines Features ein vollständiges Anforderungsdokument (Spec), das den **Ist-Stand** beschreibt.

## Input

$ARGUMENTS

* **Feature-Name** (erster Parameter): z.B. `Übersetzungsverwaltung` → erstellt `Specs/[Feature-Name].md`
* **Hinweise** (optional, Rest): Einstiegspunkte oder Abgrenzung, z.B. Route, Controller- oder Komponentenname (`/translations`, `TranslationController`), und was nur verwiesen werden soll
* Falls kein Feature-Name angegeben: nachfragen.

---

## Sub-Agent Ausführung

> **Als Sub-Agent:** Überspringe diesen Abschnitt und fahre direkt mit **Vorgehen** fort. Analysiere NUR:
> 1. Den tatsächlich vorhandenen Code (Backend, Frontend, Migrationen, Tests) und das DB-Schema (`zev-db`-MCP, nur lesend)
> 2. Die Template-Struktur `Specs/SPEC.md` und die Konventionen aus `Specs/generell.md`
> 3. Bestehende Specs in `Specs/` – für Abhängigkeiten (Abschnitt 6) und für Verhalten, das sie bereits beschreiben (Phase 2)
>
> **Beschreibe nur, was der Code tatsächlich tut.** Keine Wunsch-Features, keine Annahmen über
> beabsichtigtes Verhalten. Was sich aus dem Code nicht eindeutig ergibt, gehört in **8. Offene Fragen**.

Starte einen neuen Sub-Agenten mit dem `Agent`-Tool:

- **description:** `"Anforderungen reverse: [Feature-Name]"`
- **prompt:**

```
Du erstellst aus dem bestehenden Code ein Anforderungsdokument, das den Ist-Stand beschreibt.
Feature: [Feature-Name]
Hinweise: [Hinweise oder "keine"]

Lies: .claude/commands/98_anforderungen-reverse.md
Fahre ab Abschnitt "Vorgehen" fort.

Leitplanken: nicht committen/stagen/pushen, Umgebung nicht veraendern (Docker/Keycloak),
Datenbank nur lesen - siehe CLAUDE.md. Code NICHT aendern - einzige erlaubte Datei ist
Specs/[Feature-Name].md.
```

- **Hinweis:** Ersetze `[Feature-Name]` und `[Hinweise]` im `prompt` mit den tatsächlichen Werten aus `$ARGUMENTS`.
- **Vorher prüfen (Haupt-Agent):** Existiert `Specs/[Feature-Name].md` bereits, **nicht überschreiben** — nachfragen, ob die bestehende Spec ersetzt, unter anderem Namen angelegt oder abgebrochen werden soll.
- **Nach Abschluss:** Den Bericht des Sub-Agenten (Phase 6) im Chat ausgeben, inklusive Ergebnis der Belegprüfung.

---

## Vorgehen

### Phase 1: Feature im Code lokalisieren
Ausgehend vom Feature-Namen und den Hinweisen alle zugehörigen Dateien finden (Glob/Grep):
1. **Frontend-Einstieg:** Route in `frontend-service/src/app/app.routes.ts`; Menüeintrag in
   `frontend-service/src/app/components/navigation/navigation.component.html` **und**
   `navigation.component.ts` (Submenü-Einträge wie `adminEintraege` stehen im TS)
2. **Komponenten:** `frontend-service/src/app/components/**` (TS, HTML, CSS), Models in `models/`, Services in `services/`
3. **Backend:** Controller → Service → Repository → Entity/DTO in `backend-service/src/main/java/ch/nacht/`
4. **Datenbank:** Flyway-Migrationen in `backend-service/src/main/resources/db/migration/` (Tabellen, Constraints, Übersetzungs-Inserts)
5. **Tests:** `*Test.java`, `*IT.java`, `*.spec.ts`, `frontend-service/tests/*.spec.ts` — sie zeigen erwartetes Verhalten und Edge Cases

Ist die Zuordnung mehrdeutig (z.B. Code wird von mehreren Features genutzt), die Abgrenzung unter **7. Abgrenzung** festhalten.

### Phase 2: Bestehende Specs abgrenzen
Teile des Verhaltens sind oft schon in anderen Specs beschrieben. Sie werden **verwiesen, nicht neu beschrieben** —
zwei Fassungen desselben Verhaltens laufen auseinander.
1. Spec-Verweise im Feature-Code ermitteln:
   `grep -rhoE "Specs/[^ ,)\"']+\.md[^\"']{0,20}" <gefundene Dateien aus Phase 1> | sort | uniq -c`
2. Specs, die den Feature-Namen bzw. die Hauptentität im Titel oder in Abschnitt 6 nennen, querlesen.
3. Pro Verhalten entscheiden: **eigenes Verhalten** dieses Features → in FR beschreiben; **gehört einer anderen Spec**
   → nur Verweis (`siehe Specs/Ladestationen.md, FR-3`) in FR bzw. Abschnitt 6, und in **7. Abgrenzung** aufführen.
4. Vom Aufrufer genannte Abgrenzungen (Hinweise) haben Vorrang.

### Phase 3: Verhalten extrahieren
| Spec-Abschnitt | Quelle im Code |
|----------------|----------------|
| FR (Abläufe) | Template-Events, Komponenten-Methoden, Service-Aufrufe, Controller-Endpoints |
| Datenmodell & Persistierung | **Ist-Schema über den `zev-db`-MCP** (`information_schema.columns`, `pg_get_constraintdef` für CHECK-/FK-/Unique-Constraints); Entity (`@Column`, `nullable`, `@Filter`). Migrationen nur als Quelle nennen – über mehrere `ALTER`/`RENAME` hinweg ist der Stand daraus schwer zu lesen |
| API-Endpunkte | `@*Mapping`, `@PreAuthorize`, Request-/Response-Typen, `ResponseEntity`-Status-Codes |
| Validierungen | Bean-Validation im DTO/Entity, Service-Prüfungen, Form-Validatoren im Frontend |
| Übersetzungen (i18n) | Verwendete Keys (`\| translate`, `translationService.translate`); Texte **aus `zev.translation`** (MCP) – spätere Migrationen können sie per `UPDATE` geändert haben |
| Layout & Navigation | Route, Menüeintrag (HTML und TS), Design-System-Klassen im Template |
| Sicherheit (NFR-2) | `@PreAuthorize("hasAuthority('<permission>')")` (Backend), `AuthGuard` + `data.permissions` (Frontend); Fachrollen aus `Specs/Berechtigungen.md` |
| Multi-Tenancy | `org_id`, `@Filter`, `enableOrgFilter()` — oder begründet `n/a` |
| Edge Cases | Error-Handler, leere Zustände im Template, Exception-Handling, Testfälle |

**Vermutete Fehler werden nicht zum Soll.** Wirkt ein Verhalten wie ein Fehler (z.B. Frontend validiert anders als
Backend, Fehler wird verschluckt, 500 statt 404), kommt es **nicht als FR** in die Spec, sondern in **8. Offene Fragen**
— mit beiden Lesarten („Ist: … / vermutlich gemeint: …"). Der User entscheidet, was gilt.

### Phase 4: Akzeptanzkriterien ableiten
* Aus jedem FR und jedem Edge Case **testbare** Kriterien formulieren (Checkbox `[ ]`)
* Bestehende Tests, die ein Kriterium prüfen, am Kriterium eintragen – Format nach `Specs/SPEC.md`, Abschnitt 3:
  `(Test: EinheitServiceTest#deleteEinheit_NotExists_ReturnsFalse)`, `(Test: einheit-list.component.spec.ts › onDelete › should …)`, `(E2E: einheiten.spec.ts › should …)`; mehrere mit `;` trennen
* **Checkboxen bleiben `[ ]`** – auch mit Test-Verweis. `[x]` setzt nur `/7_akzeptanzkriterien-check`, nachdem der Test ausgeführt wurde und genau dieses Kriterium prüft
* Permissions/403 und HTTP-Status-Codes explizit abdecken

### Phase 5: Lücken und Auffälligkeiten sammeln
Unter **8. Offene Fragen** festhalten (nicht stillschweigend „korrigieren"):
* Verhalten, das sich aus dem Code nicht eindeutig ergibt
* Vermutete Fehler (Phase 3) und Inkonsistenzen (z.B. Permission im `AuthGuard` ≠ `@PreAuthorize`)
* Verstösse gegen `Specs/generell.md` (hartcodierte Texte, fehlende `org_id`)
* Abschnitt 1 «Warum machen wir das» lässt sich aus Code meist nicht ableiten → Vermutung als solche kennzeichnen

### Phase 6: Belegprüfung (Sub-Agent) und Bericht
Beim Reverse Engineering besteht das Risiko nicht im Weglassen, sondern im **Behaupten**: Aussagen, die der Code
nicht hergibt. Der Autor sieht das nicht – starte einen Sub-Agenten mit dem `Agent`-Tool:

- **description:** `"Spec-Belege pruefen: [Feature-Name]"`
- **prompt:**
```
Pruefe Specs/[Feature-Name].md gegen den Code. Die Spec wurde aus dem Code rekonstruiert
und soll nur beschreiben, was der Code tut.

Gehe jede Aussage in Abschnitt 2 (FR, Datenmodell, API, Validierungen, i18n, Layout),
Abschnitt 4 (NFR) und Abschnitt 5 (Edge Cases) durch und pruefe sie an der genannten Quelle
bzw. im Code. Pruefe ausserdem jeden Test-Verweis in Abschnitt 3: existiert der Test, und
prueft er dieses Kriterium (nicht nur denselben Code)?

Berichte als Tabelle: Aussage | Quelle | Befund (BELEGT / NICHT BELEGT / ANDERS / VERWEIS FALSCH).
BELEGT-Zeilen weglassen, wenn es mehr als 30 sind - dann ihre Anzahl nennen.

Leitplanken: READ-ONLY. Nichts aendern, nicht committen/stagen/pushen - siehe CLAUDE.md.
```
Jeden Befund ausser `BELEGT` beheben (Aussage korrigieren, streichen oder in die Offenen Fragen verschieben) und –
bei mehr als einer Handvoll – die Prüfung wiederholen.

**Bericht** im Chat:
* Gefundene Dateien (gruppiert: Frontend, Backend, Migrationen, Tests)
* Anzahl FR, Akzeptanzkriterien (davon mit Test-Verweis), Offene Fragen
* An andere Specs verwiesene Teile (Phase 2)
* Ergebnis der Belegprüfung (geprüft, behobene Befunde)
* Empfehlung: `/0_anforderungen-check [Feature-Name]` (Struktur, Lücken), danach `/7_akzeptanzkriterien-check [Feature-Name]` (führt die Tests aus, setzt die Checkboxen)

---

## Output
* Erstelle die Datei: `Specs/[Feature-Name].md`
* Verwende exakt die Struktur des Templates `Specs/SPEC.md` (alle 8 Abschnitte, Tabellen in Abschnitt 2)
* Der Feature-Name als H1-Titel
* Die Spec beschreibt das **Soll** im Präsens – kein „Ist-Stand", „umgesetzt" o.ä. im Fliesstext (`Specs/SPEC.md`)
* Die Herkunft steht im Anhang (Konvention von `/15_anforderungen_konsolidieren`):
  ```markdown
  ## Anhang A – Entscheidungen und Verlauf

  ### A.1 Entstehung (<Datum>)
  Aus dem Code rekonstruiert (Stand Commit `<kurzer Hash>`). Verhalten, das andere Specs beschreiben, ist nur verwiesen (Abschnitt 7).
  ```
  Liefern Umsetzungspläne oder `git log` Begründungen für frühere Entscheidungen, als weitere `A.n` ergänzen – mit Quelle.

## Hinweise
* **Nachweis liefern:** Bei Datenmodell, API und Validierungen die Quelle angeben (Datei, ggf. Zeile), damit die Spec überprüfbar ist
* **Nichts erfinden:** Was nicht im Code steht, kommt nicht in FR/AK — sondern in die Offenen Fragen
* **Keine Code-Änderungen:** Auch offensichtliche Fehler nur dokumentieren (Phase 3)
* **Datenbank nur lesen:** `zev-db`-MCP für Schema und Übersetzungen; keine Schreibzugriffe

## Referenz
* `Specs/SPEC.md` - Template-Struktur
* `Specs/generell.md` - Allgemeine Projektanforderungen (i18n, Design System, Multi-Tenancy)
* `Specs/Berechtigungen.md` - Permissions und Fachrollen
* `Specs/Tarifverwaltung.md` - Beispiel einer vollständigen Spec mit Test-Verweisen und Anhang
* `.claude/commands/15_anforderungen_konsolidieren.md` - Anhang-Konvention, Prüfung per Sub-Agent
