# Claude Code Präsentation

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Eine Marp-Präsentation über Claude Code am Beispiel des ZEV-Projekts erstellen, die als Schulungs- und Einführungsmaterial dient.
* **Warum machen wir das:** Neue Teammitglieder und Stakeholder sollen verstehen, wie Claude Code funktioniert und wie es im ZEV-Projekt eingesetzt wird.
* **Aktueller Stand:** Es existiert keine Dokumentation in Präsentationsform.

## 2. Funktionale Anforderungen (FR) - Was soll in der Präsentation enthalten sein?

### FR-1: Inhaltliche Struktur
Die Präsentation muss folgende Themen abdecken:

1. **Was ist Claude Code?**
   - Definition und Überblick
   - Kernfähigkeiten (CLI-Tool, Code-Verständnis, Ausführung)

2. **Was kann Claude Code?**
   - Verstehen (Codebase analysieren, Architektur erklären)
   - Planen (Features designen, Umsetzungspläne erstellen)
   - Implementieren (Code schreiben, Tests erstellen)
   - Validieren (Tests ausführen, Builds prüfen)

3. **Bausteine von Claude Code**
   - CLAUDE.md (Projektkontext)
   - Specs/generell.md (Allgemeine Anforderungen)
   - Specs/SPEC.md (Template für neue Anforderungen)
   - MCP Server (Datenbankzugriff)
   - Skills/Commands (Workflows)

4. **Wozu werden die Commands eingesetzt?** 
   - Code soll nach meinen Vorgaben erzeugt werden (deterministischer Code)
   - Dienen als Vorlage-Dateien für Programmcode
   - Referenzen auf existierende Dateien als Muster
   - Explizite Namenskonventionen
   - Strukturvorgaben
   - Weg von Vibe Coding zur strukturierten Software Entwicklung

5. **Deterministischer Code** entsteht durch
   - Konkrete Vorlagen statt abstrakter Beschreibungen
   - Referenzen auf existierende Dateien als Muster
   - Explizite Naming-Konventionen
   - Strukturvorgaben (Import-Reihenfolge, Methodenreihenfolge)

6. **Welche Commands gibt es?**
    - Liste alle commands in .claude/commands/*.md auf

7. **Wie werden die Commands eingesetzt?**
   - Workflow von Spec zur Implementierung inkl. Test
   - Sequentieller Ablauf der Skills
   - Einfach zu verwenden

8. **Wozu dient Specs/SPEC.md?**
   - Template-Struktur erklären
   - Vorteile strukturierter Anforderungen auflisten
   - System in kleine Teilbereiche/-module aufteilen

9. **Praxisbeispiel**
   - Am Beispiel von `Specs/SpaltenbreiteVeränderbar.md` zeigen
   - Zeigen, wie die Anforderungen mit den Commands umgesetzt werden

### FR-2: Technisches Format
* Format: Marp (Markdown-basiert)
* Export-Möglichkeiten: PPTX, PDF, HTML
* Dateiname: `docs/claude-code-praesentation.md`

### FR-3: Layout & Design
* Verwende die Farben von ZEV/design-system
* Code-Blöcke mit Java-Syntax-Highlighting in heller Farbe mit gutem Kontrast für Präsentation
* Tabellen für strukturierte Informationen
* Diagramme wo sinnvoll (ASCII/Mermaid)

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)
* [ ] Präsentation enthält alle 9 Hauptthemen aus FR-1
* [ ] Präsentation ist im Marp-Format erstellt
* [ ] Präsentation kann zu PPTX exportiert werden
* [ ] Jede Folie hat einen klaren Fokus (ein Thema pro Folie)
* [ ] Code-Beispiele sind aus dem ZEV-Projekt
* [ ] Praxisbeispiel zeigt den kompletten Workflow
* [ ] Präsentation endet mit Zusammenfassung und Ressourcen

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Lesbarkeit
* Schriftgrösse ausreichend für Präsentation (min. 18pt für Text)
* Schriftgrösse für Folientitel nicht allzu gross (h1.font-size: 1.6em)
* Maximal 6-8 Bullet Points pro Folie
* Kontrastreiche Farben für gute Lesbarkeit

### NFR-2: Konsistenz
* Einheitliches Design über alle Folien
* Gleiche Terminologie wie in CLAUDE.md und Specs

### NFR-3: Wartbarkeit
* Markdown-Format für einfache Aktualisierung
* Keine externen Bilder (nur ASCII-Art/Mermaid)

## 5. Edge Cases & Fehlerbehandlung
* Falls Marp CLI nicht installiert: Anleitung zur Installation bereitstellen
* Falls Export fehlschlägt: Alternative Export-Optionen dokumentieren

## 6. Abgrenzung / Out of Scope
* Keine Video-Integration
* Keine Animationen (nur statische Folien)
* Keine Speaker Notes
* Keine interaktiven Elemente

## 7. Offene Fragen
* Soll die Präsentation auch auf Englisch verfügbar sein?
* Sollen Screenshots der Anwendung eingefügt werden?

## 8. Referenzen
* Spec-Template: `Specs/SPEC.md`
* Spec-Beispiel: `Specs/SpaltenbreiteVeränderbar.md`
* Projektdokumentation: `CLAUDE.md`
* Commands: `.claude/commands/*.md`
