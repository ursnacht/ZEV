# Dependencies-Check

Prüft mit dem Maven Versions-Plugin alle deklarierten Dependencies (und optional Plugins/Parent) gegen die Repositories und berichtet, welche neueren Versionen verfügbar sind.

## Input

* **Modul (optional)**: $ARGUMENTS (z.B. `backend-service`) → beschränkt die Prüfung auf ein Maven-Modul.
  Falls nicht angegeben: alle Module des Reactors prüfen (Root-`pom.xml`).

---

## Sub-Agent Ausführung

> **Als Sub-Agent:** Überspringe diesen Abschnitt und fahre direkt mit **Vorgehen** fort. Führe NUR die Versions-Prüfung aus und berichte das Ergebnis — nimm KEINE Code- oder POM-Änderungen vor.

Starte einen neuen Sub-Agenten mit dem `Agent`-Tool:

- **description:** `"Dependencies-Check"`
- **prompt:**

```
Du prüfst die Maven-Dependencies des Projekts auf neuere Versionen.
Modul (optional): [Modul]

Lies: .claude/commands/20_dependencies-check.md
Fahre ab Abschnitt "Vorgehen" fort.
```

- **Hinweis:** Ersetze `[Modul]` im `prompt` mit dem tatsächlichen Wert aus `$ARGUMENTS` (oder „alle Module", falls nicht angegeben).

---

## Vorgehen

### Phase 1: Versions-Plugin ausführen

1. **Dependency-Updates** (Kern der Prüfung) — `-DprocessDependencyManagement=false` blendet die vom Spring-Boot-Parent verwaltete BOM aus, sodass nur die **selbst deklarierten** Dependencies erscheinen:

   ```bash
   # Alle Module:
   mvn versions:display-dependency-updates -DprocessDependencyManagement=false

   # Einzelnes Modul:
   mvn -pl [Modul] versions:display-dependency-updates -DprocessDependencyManagement=false
   ```

2. **Plugin-Updates** (optional, für Build-Plugins wie surefire, failsafe, flyway-maven-plugin, jasperreports-maven-plugin):

   ```bash
   mvn versions:display-plugin-updates
   ```

3. **Parent-Updates** (optional, für `spring-boot-starter-parent`):

   ```bash
   mvn versions:display-parent-updates
   ```

> **Hinweis:** Die Ausgabe kann umfangreich sein. Auf die Zeilen mit `->` filtern (z.B. `| grep -E '\->'`) und Plugin-/Build-Warnungen (`WARNING`, `Unsafe`, `minimum version`) ausblenden.

### Phase 2: Befunde klassifizieren

Jeden gemeldeten Update-Vorschlag einordnen:

1. **Selbst gepinnt vs. Parent-verwaltet**
   - **Selbst gepinnt** = mit explizitem `<version>` oder eigener Property in einer `pom.xml` (z.B. `jasperreports`, `qrbill-generator`, `testcontainers`, `spring-ai-starter-model-anthropic`, `archunit-junit5`, `spring-boot-admin.version`). → Direkt bumpbar.
   - **Parent-verwaltet** = ohne `<version>` deklariert, Version kommt aus dem Spring-Boot-Parent-BOM (z.B. `caffeine`, `postgresql`, `jackson`, `micrometer`). → Nicht einzeln bumpen, kommt mit einem Spring-Boot-Upgrade.

2. **Stabil vs. Pre-Release**
   - **Pre-Releases überspringen**: Versionen mit `-M`, `-RC`, `-beta`, `-alpha`, `-rc`, `-SNAPSHOT` im Tag (z.B. `4.1.0-RC1`, `3.0.0-beta3`). Nur als Hinweis listen, nicht zum Bump empfehlen.
   - **Stabil**: reine Release-Versionen → Bump-Kandidaten.

3. **Update-Art**: Patch (z.B. 7.0.3 → 7.0.7), Minor (3.3.0 → 3.4.0) oder Major (11.x → 12.x). Major-Bumps separat behandeln (höheres Risiko, ggf. an Spring Boot gekoppelt).

> **Achtung:** Der Such-Index von `search.maven.org` kann veraltet sein. Das Versions-Plugin liest die echten `maven-metadata.xml` der Repositories — diesen Werten vertrauen.

### Phase 3: Ergebnis-Bericht

Gib den Bericht **im Chat** aus (keine Datei erstellen, keine POM-Änderung).

---

## Output

```markdown
# Dependencies-Check[: Modul]

## Selbst gepinnt — stabile Updates verfügbar
| Dependency | Aktuell | Neuste stabile | Art | Datei |
|------------|---------|----------------|-----|-------|
| net.sf.jasperreports:jasperreports | 7.0.3 | 7.0.7 | Patch | backend-service/pom.xml |

## Bereits aktuell
- gruppe:artefakt X.Y.Z (neuste stabile Version)

## Nur Pre-Releases verfügbar → überspringen
- gruppe:artefakt X.Y.Z → A.B.C-RC1

## Parent-verwaltet (kommt mit Spring-Boot-Bump)
- gruppe:artefakt X.Y.Z → X.Y.(Z+1)

## Plugins / Parent (falls geprüft)
- org.apache.maven.plugins:maven-surefire-plugin X → Y
- spring-boot-starter-parent X → Y

## Empfehlung
Risikoarme Bumps: [Liste]. Major/Pre-Releases separat behandeln.
```

Falls keine Updates: „Keine Updates — alle selbst gepinnten Dependencies sind aktuell."

---

## Wichtige Regeln

* **READ-ONLY** — dieser Command prüft und berichtet nur. Keine `pom.xml` und kein Code wird verändert.
* **Konservativ bewerten** — Pre-Releases und Major-Bumps nie als „sicher" empfehlen.
* **Nachweis liefern** — für jeden Bump-Kandidaten die `pom.xml` (und ggf. Zeile/Property) angeben.
* **Parent-verwaltete Deps nicht pinnen** — Updates dort über das Spring-Boot-Upgrade lösen, nicht durch Hinzufügen einer expliziten Version.

## Nach dem Bump (falls der User Updates anwenden lässt)

Verifizieren, dass nichts bricht:
* `mvn -pl backend-service test -Dtest=JasperTemplateCompileTest` — nach JasperReports-Bump (Templates müssen kompilieren).
* `mvn -pl backend-service test` — Backend-Unit-Tests nach jedem Backend-Dependency-Bump.
* CLAUDE.md (Abschnitt „Tech Stack Versions") an die neuen Versionen anpassen.

---

## Referenz

* CLAUDE.md — Abschnitt „Tech Stack Versions" (dokumentierte Soll-Versionen)
* `pom.xml` — Root: Parent-Version + zentrale Properties (`spring-boot-admin.version`)
* `backend-service/pom.xml` — die meisten selbst gepinnten Dependencies
* [Maven Versions Plugin](https://www.mojohaus.org/versions/versions-maven-plugin/)
