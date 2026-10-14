# Anforderungsdokument konsolidieren

Bringe ein über viele Nachträge gewachsenes Anforderungsdokument auf einen lesbaren **aktuellen
Stand**, ohne Wissen zu verlieren.

## Input

$ARGUMENTS

* **Feature-Name**: (z.B. `Einspeisesteuerung`) → liest `Specs/[Feature-Name].md`
  Falls nicht angegeben: nachfragen. Kandidaten zeigt `wc -l Specs/*.md | sort -n | tail`.

## Warum

Specs wachsen mit jedem `/10_vibe` um Nachträge („geändert am …", „vorher …", „bis 14.10.2026 …").
Das Wissen darin ist wertvoll – die Begründungen haben schon mehrfach verhindert, dass ein gelöster
Fehler neu eingebaut wird –, aber es ist bei über 1000 Zeilen kaum noch zu finden. Wer wissen will,
**wie es heute ist**, muss die Geschichte mitlesen.

Ziel: **oben der aktuelle Stand, unten die Geschichte.** Gekürzt wird an der Form, nicht am Inhalt.

---

## Grundregeln

* **Nichts geht verloren.** Jede Anforderung, jedes Akzeptanzkriterium, jeder Edge Case und jede
  Begründung steht danach entweder im aktuellen Teil oder im Anhang. Im Zweifel in den Anhang, nicht
  weg.
* **Nummern bleiben stabil.** `FR-x`, `NFR-x` und Abschnittsnummern werden **nicht** umnummeriert,
  auch nicht bei Lücken – der Code verweist darauf (`// Specs/Einspeisesteuerung.md, FR-4`; allein
  auf diese Spec verweisen rund 50 Dateien). Ein entfallenes FR bleibt als Überschrift mit
  „Entfallen am …, siehe Anhang" stehen.
* **Fachlich ändert sich nichts.** Kein neues Verhalten, keine neuen Kriterien, keine
  „Verbesserungen". Fallen beim Lesen Widersprüche oder Lücken auf: **berichten, nicht lösen**.
* **Checkbox-Status bleibt.** `[ ]` wird nicht ohne Prüfung zu `[x]`. Dafür gibt es
  `/7_akzeptanzkriterien-check` – im Bericht vorschlagen, wenn viele offen sind.
* **Struktur nach `Specs/SPEC.md`** (8 Abschnitte), ergänzt um den Anhang am Ende.

---

## Vorgehen

### Phase 1: Bestand aufnehmen
1. Lies `Specs/[Feature-Name].md` vollständig, dazu `Specs/SPEC.md`.
2. Lies `Specs/[Feature-Name]_Umsetzungsplan.md` (falls vorhanden) – nur als Kontext, um Nachträge
   zeitlich einzuordnen.
3. Ermittle die Verweise aus dem Code:
   `grep -rnoE "Specs/[Feature-Name]\.md[^\"']{0,40}" backend-service/src frontend-service/src design-system/src`
   – diese Nummern und Abschnittsnamen müssen danach noch auffindbar sein.
4. Prüfe, dass die Spec im Git sauber ist (`git status Specs/[Feature-Name].md`). Ist sie geändert:
   abbrechen und den User fragen – sonst vermischt sich seine Änderung mit der Konsolidierung.

### Phase 2: Umbauen
Für jeden Abschnitt:

| Im Bestand | Im Ergebnis |
|---|---|
| Aktuell gültige Anforderung | bleibt im Abschnitt, **im Präsens**, ohne „neu seit …" |
| Ersetzte Fassung („vorher …", „bis 14.10.2026 …") | → Anhang, mit Datum und Verweis auf das FR |
| Kurze Begründung („Warum …", 1–3 Sätze) | bleibt als `>`-Block direkt bei der Anforderung |
| Lange Begründung, Fehlergeschichte, Messung, „belegt am …" | → Anhang; beim FR ein Satz Kern + „(Hintergrund: Anhang A.n)" |
| Mehrfach gesagte Regel | einmal an der passenden Stelle, sonst Verweis |
| Akzeptanzkriterien aus Nachträgen | in Abschnitt 3 unter der passenden Gruppe, Status unverändert |
| Ersetztes Akzeptanzkriterium | → Anhang (nicht stehen lassen – es wäre ein falscher Prüfauftrag) |
| Beantwortete offene Frage | Antwort in die Anforderung, Frage + Antwort in den Anhang |

**Anhang** am Ende, nach Abschnitt 8:
```markdown
## Anhang A – Entscheidungen und Verlauf

### A.1 <Thema> (FR-4, 14.10.2026)
Was vorher galt, was sich warum geändert hat, Belege.
```
Chronologisch oder nach FR gruppiert – je nachdem, was bei dieser Spec besser lesbar ist.

### Phase 3: Verlustprüfung (Sub-Agent)
Starte einen Sub-Agenten mit dem `Agent`-Tool – der Autor übersieht, was er selbst weggelassen hat:

- **description:** `"Konsolidierung prüfen: [Feature-Name]"`
- **prompt:**
```
Pruefe, ob bei der Konsolidierung von Specs/[Feature-Name].md Inhalt verloren ging.
Alte Fassung: `git show HEAD:Specs/[Feature-Name].md`, neue Fassung: die Datei im Arbeitsverzeichnis.

Gehe die ALTE Fassung Aussage fuer Aussage durch (jede Anforderung, jedes Akzeptanzkriterium mit
Checkbox-Status, jeder Edge Case, jede Begruendung, jede Zahl/jedes Datum) und finde sie in der
neuen wieder - im aktuellen Teil oder im Anhang. Pruefe zusaetzlich:
- Sind alle FR-/NFR-Nummern der alten Fassung noch als Ueberschrift vorhanden?
- Hat sich ein Checkbox-Status geaendert?
- Steht eine ERSETZTE Regel noch im aktuellen Teil (Widerspruch)?
- Behauptet der aktuelle Teil etwas, das in der alten Fassung nicht stand?

Berichte als Tabelle: Aussage (alt) | gefunden wo (neu) | Befund (OK / FEHLT / VERAENDERT / WIDERSPRUCH).
Nur OK-Zeilen weglassen, wenn es mehr als 30 sind - dann ihre Anzahl nennen.

Leitplanken: READ-ONLY. Nichts aendern, nicht committen/stagen/pushen - siehe CLAUDE.md.
```
Jeden Befund `FEHLT`/`VERAENDERT`/`WIDERSPRUCH` beheben und – bei mehr als einer Handvoll – die
Prüfung wiederholen.

### Phase 4: Bericht
Im Chat:
* Zeilen vorher / nachher, davon Anhang
* Was in den Anhang gewandert ist (Themen, nicht Zeilen)
* Ergebnis der Verlustprüfung (Anzahl geprüft, behobene Befunde)
* Aufgefallene Widersprüche oder Lücken, die **nicht** gelöst wurden (für den User)
* Anzahl offener Akzeptanzkriterien, ggf. Empfehlung `/7_akzeptanzkriterien-check`

## Output
* Überarbeitete `Specs/[Feature-Name].md` – nur diese Datei.
* Der Umsetzungsplan bleibt unverändert: Er ist ein Protokoll, seine Nachträge sind dort richtig.
* Kein Code, keine Tests, keine Migration.

## Leitplanken
* Nicht committen, stagen oder pushen ohne Freigabe des Users (CLAUDE.md).
* Eine Spec pro Aufruf – der Diff muss reviewbar bleiben.

## Referenz
* `Specs/SPEC.md` – Template-Struktur
* `.claude/commands/10_vibe.md` – erzeugt die Nachträge, die hier aufgeräumt werden
