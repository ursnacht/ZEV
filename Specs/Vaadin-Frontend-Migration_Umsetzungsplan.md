# Umsetzungsplan: Vaadin Frontend-Migration (Angular → Vaadin)

## Zusammenfassung

Das Angular 21 SPA-Frontend (`frontend-service`) wird vollständig durch Vaadin Server-Side-Rendering ersetzt. Vaadin wird direkt in den `backend-service` integriert, sodass die Views die bestehenden `@Service`-Klassen direkt aufrufen – ohne HTTP-Roundtrip. Der `frontend-service` (Docker/Nginx) wird stillgelegt und aus `docker-compose.yml` entfernt.

---

## Kritische Vorabklärung: Vaadin + Spring Boot 4

**Spring Boot 4.0.1 (Spring Framework 7) ist inkompatibel mit Vaadin 24 LTS (Spring Boot 3.x).**

| Vaadin Version | Spring Boot | Status |
|---|---|---|
| Vaadin 24 LTS | Spring Boot 3.x | Stabil – **nicht** kompatibel |
| Vaadin 25+ | Spring Boot 4 / Spring Framework 7 | Kompatibel – Stabilitätsstatus vor Start prüfen |

→ **Phase 0 (Vorabklärung) muss vor Phase 1 abgeschlossen sein.**

---

## Betroffene Komponenten

### Neue Dateien

| Typ | Datei |
|-----|-------|
| Vaadin MainLayout | `backend-service/src/main/java/ch/nacht/vaadin/MainLayout.java` |
| Vaadin SecurityConfig | `backend-service/src/main/java/ch/nacht/vaadin/config/VaadinSecurityConfig.java` |
| Vaadin TranslationProvider | `backend-service/src/main/java/ch/nacht/vaadin/util/TranslationProvider.java` |
| Vaadin QuarterSelector | `backend-service/src/main/java/ch/nacht/vaadin/components/QuarterSelector.java` |
| View: Startseite | `backend-service/src/main/java/ch/nacht/vaadin/views/StartseiteView.java` |
| View: Einheiten | `backend-service/src/main/java/ch/nacht/vaadin/views/EinheitenView.java` |
| Dialog: Einheit | `backend-service/src/main/java/ch/nacht/vaadin/views/EinheitFormDialog.java` |
| View: Tarife | `backend-service/src/main/java/ch/nacht/vaadin/views/TarifeView.java` |
| Dialog: Tarif | `backend-service/src/main/java/ch/nacht/vaadin/views/TarifFormDialog.java` |
| View: Mieter | `backend-service/src/main/java/ch/nacht/vaadin/views/MieterView.java` |
| Dialog: Mieter | `backend-service/src/main/java/ch/nacht/vaadin/views/MieterFormDialog.java` |
| View: Einstellungen | `backend-service/src/main/java/ch/nacht/vaadin/views/EinstellungenView.java` |
| View: Messwerte-Upload | `backend-service/src/main/java/ch/nacht/vaadin/views/UploadView.java` |
| View: Solar-Berechnung | `backend-service/src/main/java/ch/nacht/vaadin/views/SolarCalculationView.java` |
| View: Statistik | `backend-service/src/main/java/ch/nacht/vaadin/views/StatistikView.java` |
| View: Rechnungen | `backend-service/src/main/java/ch/nacht/vaadin/views/RechnungenView.java` |
| View: Messwerte-Chart | `backend-service/src/main/java/ch/nacht/vaadin/views/ChartView.java` |
| View: Übersetzungen | `backend-service/src/main/java/ch/nacht/vaadin/views/TranslationEditorView.java` |
| CSS-Theme | `backend-service/src/main/resources/vaadin/themes/zev/theme.json` |
| CSS-Theme Tokens | `backend-service/src/main/resources/vaadin/themes/zev/styles.css` |

### Geänderte Dateien

| Typ | Datei | Änderung |
|-----|-------|----------|
| Root POM | `pom.xml` | Vaadin BOM hinzufügen |
| Backend POM | `backend-service/pom.xml` | `vaadin-spring-boot-starter` Dependency; `vaadin-maven-plugin` |
| Security | `backend-service/src/main/java/ch/nacht/config/SecurityConfig.java` | Auf Vaadin + Authorization Code Flow umstellen |
| Docker Compose | `docker-compose.yml` | `frontend-service` entfernen; ggf. Port-Anpassung |
| Root POM Module | `pom.xml` | `frontend-service` und `design-system` aus `<modules>` entfernen |

### Gelöschte Module

| Modul | Aktion |
|-------|--------|
| `frontend-service/` | Verzeichnis entfernen |
| `design-system/` | Verzeichnis entfernen (wenn nicht mehr referenziert) |

---

## Phasen-Tabelle

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [ ] | 0. Vaadin-Versionsentscheid | Vaadin 25 GA-Readiness prüfen; Charting-Entscheidung treffen |
| [ ] | 1. Maven-Setup | `vaadin-spring-boot-starter` + `vaadin-maven-plugin` in `backend-service/pom.xml`; Vaadin BOM in Root-`pom.xml` |
| [ ] | 2. Security-Umbau | `SecurityConfig` auf Vaadin + Keycloak Authorization Code Flow umstellen (statt Bearer Token Resource Server) |
| [ ] | 3. CSS-Theme | Lumo-Theme in `backend-service/src/main/resources/vaadin/themes/zev/`; Mapping der `tokens.css` CSS-Variablen auf Lumo-Properties |
| [ ] | 4. MainLayout + Navigation | `MainLayout.java` mit `AppLayout`, `Drawer`, Menüeinträgen, Sprachumschaltung (DE/EN), Logout; Rollensichtbarkeit via `AuthenticationContext` |
| [ ] | 5. Multi-Tenancy-Infrastruktur | `BeforeEnterObserver`-Mixin oder Basis-Klasse für Views: `HibernateFilterService.enableOrgFilter()` vor jedem Laden |
| [ ] | 6. i18n-Infrastruktur | `TranslationProvider` wrapping `TranslationService`; `UI.getCurrent().setLocale()` für Sprachumschaltung; Session-persistente Sprache |
| [ ] | 7. Startseite | `StartseiteView` (`@Route("")`), `@RolesAllowed({"zev", "zev_admin"})`; Begrüssungstext + Bild |
| [ ] | 8. Einheitenverwaltung | `EinheitenView` mit `Grid`; `EinheitFormDialog` (Vaadin `Dialog` + `Binder`); Kebab via `GridContextMenu`; Rollen |
| [ ] | 9. Tarifverwaltung | `TarifeView` + `TarifFormDialog`; Datum-Validierung Schweizer Format; Kopieren-Aktion |
| [ ] | 10. Mieterverwaltung | `MieterView` + `MieterFormDialog`; Überlappungs-Validierung via Service; Einheit-Dropdown (nur CONSUMER) |
| [ ] | 11. Einstellungen | `EinstellungenView` mit `FormLayout` + `Binder`; direkter Aufruf von `EinstellungenService` |
| [ ] | 12. Übersetzungen | `TranslationEditorView` mit `Grid`; Inline-`Dialog` für Bearbeiten/Neu/Löschen |
| [ ] | 13. Solar-Berechnung | `SolarCalculationView`; Quartal-Selector (`QuarterSelector`); Ergebnis-`Grid` aus `SolarDistributionService` |
| [ ] | 14. Statistik | `StatistikView`; Quartal-Selector; Übersichtsbereich; Monatstabelle mit Farbmarkierungen; PDF-Download via `StatistikPdfService` |
| [ ] | 15. Rechnungen | `RechnungenView`; Quartal-Selector + Consumer-Checkboxen; `ProgressBar` bei Generierung; PDF-Download pro Rechnung; Tarif-Validierung |
| [ ] | 16. Messwerte-Upload | `UploadView`; Vaadin `Upload`-Komponente; KI-Erkennung via `EinheitMatchingService`; Timeout 2s + Fallback; Einheit-Dropdown |
| [ ] | 17. Messwerte-Chart | `ChartView`; Quartal-Selector + Einheitenauswahl; Chart-Rendering (Entscheidung aus Phase 0) |
| [ ] | 18. Docker/Deployment | `frontend-service` aus `docker-compose.yml` entfernen; Root `pom.xml` Module anpassen; Produktions-Build `mvn package -Pproduction` verifizieren |
| [ ] | 19. E2E-Tests | Playwright-Tests neu schreiben für kritische Flows (Login, Einheiten-CRUD, Statistik, Rechnungen-Generierung) |
| [ ] | 20. Alte Frontend-Module löschen | `frontend-service/` und `design-system/` aus Repository entfernen |

---

## Phasen-Detail

### Phase 0: Vaadin-Versionsentscheid

Vor jeder Umsetzung klären:

1. **Vaadin-Version**: Ist Vaadin 25.x (Spring Boot 4 / Spring Framework 7) produktionsreif (GA)? Falls nicht: Downgrade von Spring Boot 4 auf 3.x evaluieren oder warten.
2. **Charting**: Entscheidung zwischen:
   - `vaadin-charts` Add-on (kommerziell, ~1200 EUR/Jahr)
   - Chart.js via Lit-basiertem Custom Web Component (Open Source)
   - JFreeChart (Server-Side PNG, kein JS, einfachste Option)
3. **Rollout-Strategie**: Big Bang (alle Views auf einmal) oder modul-weise (Angular parallel bis alle Views fertig)?

### Phase 1: Maven-Setup

**`pom.xml` (Root):**
```xml
<properties>
  <vaadin.version>25.x.x</vaadin.version>
</properties>
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.vaadin</groupId>
      <artifactId>vaadin-bom</artifactId>
      <version>${vaadin.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

**`backend-service/pom.xml`:**
```xml
<dependency>
  <groupId>com.vaadin</groupId>
  <artifactId>vaadin-spring-boot-starter</artifactId>
</dependency>
```

Plugin:
```xml
<plugin>
  <groupId>com.vaadin</groupId>
  <artifactId>vaadin-maven-plugin</artifactId>
  <version>${vaadin.version}</version>
  <executions>
    <execution>
      <goals><goal>prepare-frontend</goal></goals>
    </execution>
  </executions>
</plugin>
```

Produktions-Profil:
```xml
<profiles>
  <profile>
    <id>production</id>
    <dependencies>
      <dependency>
        <groupId>com.vaadin</groupId>
        <artifactId>vaadin-core</artifactId>
        <exclusions>
          <exclusion>
            <groupId>com.vaadin</groupId>
            <artifactId>vaadin-dev</artifactId>
          </exclusion>
        </exclusions>
      </dependency>
    </dependencies>
    <build>
      <plugins>
        <plugin>
          <groupId>com.vaadin</groupId>
          <artifactId>vaadin-maven-plugin</artifactId>
          <executions>
            <execution>
              <goals><goal>build-frontend</goal></goals>
            </execution>
          </executions>
        </plugin>
      </plugins>
    </build>
  </profile>
</profiles>
```

### Phase 2: Security-Umbau

**Datei:** `backend-service/src/main/java/ch/nacht/config/SecurityConfig.java`

Aktuell: OAuth2 Resource Server (Bearer Token für REST-API)
Neu: Vaadin Security + Keycloak Authorization Code Flow für Browser-Sessions, REST-API behält Bearer Token

```java
@EnableWebSecurity
@Configuration
public class SecurityConfig extends VaadinWebSecurity {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        // REST API: weiterhin Bearer Token
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/**", "/ping", "/actuator/**").permitAll());

        // Vaadin: Authorization Code Flow
        super.configure(http);
        setLoginView(http, LoginView.class);

        // Keycloak OAuth2 Login
        http.oauth2Login(Customizer.withDefaults());
    }
}
```

**Hinweis:** `VaadinWebSecurity` ist die Vaadin-spezifische Basis-Klasse. Die genaue API hängt von der Vaadin-Version ab – mit Vaadin 25 + Spring Boot 4 verifizieren.

### Phase 3: CSS-Theme

**`backend-service/src/main/resources/vaadin/themes/zev/theme.json`:**
```json
{
  "parent": "lumo",
  "documentCss": ["styles.css"]
}
```

**`backend-service/src/main/resources/vaadin/themes/zev/styles.css`:**
Mapping der bestehenden `tokens.css` auf Lumo-Properties:
```css
/* Mapping ZEV Design Tokens → Lumo */
html {
  --lumo-primary-color: #4CAF50;           /* --color-primary */
  --lumo-primary-color-50pct: rgba(76, 175, 80, 0.5);
  --lumo-primary-text-color: #388E3C;      /* --color-primary-dark */
  --lumo-error-color: #f44336;             /* --color-danger */
  --lumo-success-color: #4CAF50;           /* --color-primary */
  --lumo-base-color: #ffffff;              /* --color-white */
  --lumo-body-text-color: #333;            /* --color-gray-800 */
  --lumo-font-family: Arial, sans-serif;   /* --font-family-primary */
  --lumo-font-size-m: 14px;               /* --font-size-base */
}
```

Annotation im MainLayout: `@Theme("zev")`

### Phase 4: MainLayout + Navigation

**`ch.nacht.vaadin.MainLayout`:**

```java
@Layout
@Theme("zev")
public class MainLayout extends AppLayout implements BeforeEnterObserver {

    private final AuthenticationContext authContext;
    private final TranslationProvider translations;

    public MainLayout(AuthenticationContext authContext, TranslationProvider translations) {
        // Navbar Header: Hamburger + App-Titel
        DrawerToggle toggle = new DrawerToggle();
        H1 title = new H1("ZEV");
        addToNavbar(toggle, title);

        // Drawer: Menüeinträge
        VerticalLayout drawer = buildDrawer();
        addToDrawer(drawer);

        // Drawer initial aufgeklappt
        setDrawerOpened(true);
    }

    private VerticalLayout buildDrawer() {
        VerticalLayout layout = new VerticalLayout();

        // Menüeinträge mit Rollen-Check
        addMenuLink(layout, "STARTSEITE", "/", "zev", "zev_admin");
        addMenuLink(layout, "UPLOAD", "/upload", "zev", "zev_admin");
        addMenuLink(layout, "EINHEITEN", "/einheiten", "zev", "zev_admin");
        addMenuLink(layout, "SOLAR_BERECHNUNG", "/solar-calculation", "zev", "zev_admin");
        addMenuLink(layout, "MESSWERTE_CHART", "/chart", "zev", "zev_admin");
        addMenuLink(layout, "STATISTIK", "/statistik", "zev", "zev_admin");
        addMenuLink(layout, "RECHNUNGEN", "/rechnungen", "zev_admin");
        addMenuLink(layout, "TARIFE", "/tarife", "zev_admin");
        addMenuLink(layout, "MIETER", "/mieter", "zev_admin");
        addMenuLink(layout, "EINSTELLUNGEN", "/einstellungen", "zev_admin");
        addMenuLink(layout, "UEBERSETZUNGEN", "/translations", "zev_admin");

        // Sprachumschaltung DE/EN
        // Logout
        return layout;
    }
}
```

### Phase 5: Multi-Tenancy-Infrastruktur

Jede View implementiert `BeforeEnterObserver` oder erbt von einer Basisklasse:

```java
public abstract class SecuredView extends VerticalLayout implements BeforeEnterObserver {

    @Autowired
    private HibernateFilterService hibernateFilterService;

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        hibernateFilterService.enableOrgFilter();
    }
}
```

Alle Views erben von `SecuredView`. So ist der Org-Filter garantiert aktiv.

### Phase 6: i18n-Infrastruktur

**`ch.nacht.vaadin.util.TranslationProvider`:**

```java
@Component
public class TranslationProvider implements I18NProvider {

    private final TranslationService translationService;

    @Override
    public List<Locale> getProvidedLocales() {
        return List.of(Locale.GERMAN, Locale.ENGLISH);
    }

    @Override
    public String getTranslation(String key, Locale locale, Object... params) {
        // locale.getLanguage() == "de" → deutsch, "en" → englisch
        return translationService.translate(key, locale.getLanguage());
    }
}
```

Vaadin erkennt `I18NProvider`-Implementierungen automatisch via Spring.

Sprachumschaltung in MainLayout:
```java
Button deLang = new Button("DE", e -> {
    UI.getCurrent().setLocale(Locale.GERMAN);
    UI.getCurrent().getPage().reload();
});
```

### Phase 7–17: Views

Alle Views nach diesem Muster:

```java
@Route(value = "einheiten", layout = MainLayout.class)
@RolesAllowed({"zev", "zev_admin"})
@SpringComponent
@UIScope
public class EinheitenView extends SecuredView {

    private final EinheitService einheitService;
    private final Grid<Einheit> grid;

    public EinheitenView(EinheitService einheitService) {
        this.einheitService = einheitService;
        this.grid = new Grid<>(Einheit.class, false);
        buildGrid();
        add(buildToolbar(), grid);
        loadData();
    }

    private void buildGrid() {
        grid.addColumn(Einheit::getName).setHeader(getTranslation("NAME"));
        grid.addColumn(e -> e.getTyp().name()).setHeader(getTranslation("TYP"));
        grid.addColumn(Einheit::getMesspunkt).setHeader(getTranslation("MESSPUNKT"));

        // Kebab via GridContextMenu oder Button-Spalte
        grid.addComponentColumn(einheit -> {
            MenuBar menu = new MenuBar();
            menu.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE);
            MenuItem actions = menu.addItem("⋮");
            actions.getSubMenu().addItem(getTranslation("BEARBEITEN"),
                e -> openForm(einheit));
            actions.getSubMenu().addItem(getTranslation("LOESCHEN"),
                e -> confirmDelete(einheit));
            return menu;
        }).setHeader(getTranslation("AKTIONEN"));
    }

    private void loadData() {
        grid.setItems(einheitService.getAllEinheiten());
    }
}
```

**CRUD-Dialog-Pattern (Binder):**
```java
public class EinheitFormDialog extends Dialog {

    private final Binder<Einheit> binder = new Binder<>(Einheit.class);

    public EinheitFormDialog(Einheit einheit, EinheitService service,
                              Runnable onSave) {
        TextField name = new TextField(getTranslation("NAME"));
        binder.forField(name)
            .withValidator(n -> n != null && !n.isBlank(), getTranslation("PFLICHTFELD"))
            .withValidator(n -> n.length() <= 30, getTranslation("NAME_MAX_30"))
            .bind(Einheit::getName, Einheit::setName);

        Select<EinheitTyp> typ = new Select<>();
        typ.setItems(EinheitTyp.values());
        binder.bind(typ, Einheit::getTyp, Einheit::setTyp);

        binder.setBean(einheit);

        Button save = new Button(getTranslation("SPEICHERN"), e -> {
            if (binder.validate().isOk()) {
                service.saveEinheit(einheit);
                Notification.show(getTranslation("GESPEICHERT"), 5000,
                    Notification.Position.BOTTOM_START);
                close();
                onSave.run();
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        add(new FormLayout(name, typ), new HorizontalLayout(save));
    }
}
```

### Phase 18: Docker/Deployment

**`docker-compose.yml`:** `frontend-service`-Block entfernen. Falls `prometheus.yml` den Frontend-Scrape-Endpoint referenziert: anpassen.

**`pom.xml` (Root):** `frontend-service` und `design-system` aus `<modules>` entfernen.

**Produktions-Build verifizieren:**
```bash
cd backend-service
mvn clean package -Pproduction
```

### Phase 19: E2E-Tests

Playwright-Tests in `backend-service/src/test/playwright/` (neues Verzeichnis) oder bestehendes `frontend-service/tests/` ersetzen.

Kritische Flows neu schreiben:
- Login via Keycloak
- Einheiten-CRUD
- Statistik PDF-Export
- Rechnungen-Generierung + Download
- Messwerte-Upload mit KI

### Phase 20: Alte Module löschen

Nach vollständiger Verifikation:
```bash
git rm -r frontend-service/
git rm -r design-system/
```

---

## Validierungen

### Vaadin Binder (Frontend-Äquivalent)

| Feld | Regel |
|------|-------|
| Einheit: Name | Pflichtfeld, max. 30 Zeichen |
| Einheit: Typ | Pflichtfeld (Dropdown Consumer/Producer) |
| Tarif: Bezeichnung | Pflichtfeld, max. 30 Zeichen |
| Tarif: Preis | Pflichtfeld, numerisch, max. 5 Nachkommastellen |
| Tarif: Gültig von/bis | Pflichtfeld, Datumsformat dd.MM.yyyy |
| Mieter: Name | Pflichtfeld |
| Mieter: Mietbeginn | Pflichtfeld |
| Mieter: Mietende | Optional; wenn gesetzt: nach Mietbeginn |
| Mieter: Einheit | Pflichtfeld (Dropdown, nur CONSUMER) |
| Einstellungen: alle Felder | Pflichtfelder |
| Einstellungen: IBAN | Format-Validierung |

### Backend-Validierungen (unverändert)

Die bestehenden `@Valid`, `@NotBlank`, `@Size` auf Entities sowie Service-Validierungen (Mieter-Überlappung, Tarif-Abdeckung) bleiben vollständig erhalten. Fehlermeldungen vom Backend werden via `catch`-Block in Views als `Notification` (rot, permanent) angezeigt.

```java
try {
    service.saveMieter(mieter);
    Notification.show(getTranslation("GESPEICHERT"), 5000, BOTTOM_START);
} catch (IllegalArgumentException e) {
    Notification notification = Notification.show(e.getMessage());
    notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
}
```

---

## Design-System Mapping

| Angular Design System | Vaadin Äquivalent |
|---|---|
| `.zev-table`, `KebabMenuComponent` | `Grid` + `MenuBar` (inline, tertiary) |
| `.zev-form-*`, `.zev-input` | `FormLayout`, `TextField`, `DatePicker`, `Select` |
| `.zev-button--primary` | `Button` + `ButtonVariant.LUMO_PRIMARY` |
| `.zev-button--secondary` | `Button` + `ButtonVariant.LUMO_CONTRAST` |
| `.zev-button--danger` | `Button` + `ButtonVariant.LUMO_ERROR` |
| `.zev-message--success` | `Notification` + `NotificationVariant.LUMO_SUCCESS` (5s) |
| `.zev-message--error` | `Notification` + `NotificationVariant.LUMO_ERROR` (permanent) |
| `.zev-spinner` | `ProgressBar` (indeterminate) |
| `.zev-collapsible` | `Details` |
| `.zev-drop-zone` | `Upload` |
| `QuarterSelectorComponent` | `QuarterSelector.java` (Custom Vaadin Component) |
| `NavigationComponent` (Hamburger) | `AppLayout` + `Drawer` |
| `TranslatePipe` | `getTranslation(key)` (via `HasI18N`) |

---

## Offene Punkte / Annahmen

1. **Vaadin 25 GA-Readiness (kritisch):** Vor Start verifizieren, ob Vaadin 25.x mit Spring Boot 4.0.1 produktionsreif ist. Falls nicht GA: entweder auf Spring Boot 3.x downgraden oder warten.

2. **`VaadinWebSecurity`-API in Spring Boot 4:** Die genaue Vaadin Security API für Spring Boot 4 / Spring Security 7 muss verifiziert werden – die bisherige `WebSecurityConfigurerAdapter`-basierte API existiert in Spring Security 7 nicht mehr.

3. **Charting-Entscheidung (Phase 0):** `vaadin-charts` (kommerziell) vs. JFreeChart (server-side PNG, einfachste Option) vs. Custom Lit Web Component für Chart.js.

4. **Annahme:** Vaadin läuft im selben Docker-Container wie `backend-service` auf Port 8090. Der Frontend-Port 4200 entfällt.

5. **Annahme:** Big-Bang-Strategie (kein paralleler Betrieb Angular + Vaadin). Bei Risikobedarf: modul-weise Migration mit Proxy-Setup möglich.

6. **Annahme:** REST-Controller bleiben vollständig bestehen (für externe Konsumenten und automatisierte Tests). Vaadin ruft Services direkt auf – kein HTTP-Roundtrip intern.

7. **Annahme:** Die bestehenden Backend-Unit- und Integrationstests (`*Test.java`, `*IT.java`) sind nicht betroffen und bleiben unverändert.

8. **Annahme:** Prometheus-Metriken kommen weiterhin aus dem `backend-service`-Actuator. Die `prometheus.yml` muss ggf. angepasst werden (kein separater Frontend-Endpunkt mehr).

9. **`@UIScope` vs. `@VaadinSessionScope`:** Views werden mit `@UIScope` annotiert (eine Instanz pro Browser-Tab). Daten werden bei `beforeEnter()` frisch geladen.

10. **CORS:** Nach Abschluss der Migration sind die `frontend-service`-Origins (`http://localhost:4200`) aus `SecurityConfig.corsConfigurationSource()` zu entfernen.
