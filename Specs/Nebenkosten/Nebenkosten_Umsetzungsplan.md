# Nebenkosten — Umsetzungsplan

## Zusammenfassung

Umgesetzt wird das **Grundgerüst** der Nebenkostenabrechnung gemäss
[`Nebenkosten.md`](./Nebenkosten.md): der Feature-Flag `NEBENKOSTENABRECHNUNG`, ein Menüpunkt
mit Untermenü (neue Fähigkeit im Design System), zwei leere Unterseiten und die Permission
`nebenkosten:manage`. Es entsteht **keine Fachlichkeit** — weder Tabelle noch Endpunkt.

Zwei Bausteine fehlen heute und sind der eigentliche Aufwand: Untermenüs in der Navigation und
eine Möglichkeit, im Template eine Permission abzufragen.

## Betroffene Komponenten

### Backend
| Datei | Änderung |
|---|---|
| `backend-service/src/main/java/ch/nacht/entity/FeatureFlag.java` | Enum-Wert `NEBENKOSTENABRECHNUNG(false, "FEATURE_FLAG_NEBENKOSTENABRECHNUNG")` |
| `backend-service/src/main/resources/db/migration/V116__Add_Nebenkosten_Translations.sql` | neu — Übersetzungen DE + EN |

### Design System
| Datei | Änderung |
|---|---|
| `design-system/src/components/navigation/navigation.css` | Untermenü-Klassen (`.zev-navbar__submenu`, `.zev-navbar__link--parent`, …) |

### Frontend
| Datei | Änderung |
|---|---|
| `frontend-service/src/app/guards/auth.guard.ts` | Prüflogik in eine exportierte Funktion ziehen; Verhalten unverändert |
| `frontend-service/src/app/utils/permissions.ts` | neu — gemeinsame `hasAnyPermission(...)` |
| `frontend-service/src/app/directives/permission.directive.ts` | neu — `*appPermission` |
| `frontend-service/src/app/components/nebenkosten-tarifpositionen/` | neu — Gerüstseite |
| `frontend-service/src/app/components/nebenkosten-abrechnung/` | neu — Gerüstseite |
| `frontend-service/src/app/app.routes.ts` | zwei Routen + Weiterleitung `/nebenkosten` |
| `frontend-service/src/app/components/navigation/navigation.component.{html,ts}` | Menüpunkt mit Untermenü |
| `frontend-service/src/app/components/icon/icons.ts` | Feather-Icon `chevron-down` ergänzen |
| `frontend-service/src/app/components/design-system-showcase/design-system-showcase.component.html` | Untermenü-Variante zeigen |

### Konfiguration und Dokumentation
| Datei | Änderung |
|---|---|
| `keycloak/realms/zev-realm.json` | Rolle `nebenkosten:manage` + Aufnahme in die `composite`-Listen |
| `Specs/Berechtigungen.md` | Permission-Matrix ergänzen |

## Umsetzungsreihenfolge (Phasen)

Die Reihenfolge ist so gewählt, dass jede Phase für sich lauffähig ist und die sichtbare
Verdrahtung zuletzt kommt — vorher gibt es nichts zu sehen, was in einem halben Zustand
verwirren könnte.

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [ ] | 1. Feature-Flag (Backend) | Enum-Wert in `FeatureFlag.java` ergänzen. Danach erscheint der Flag in der Verwaltung unter `/einstellungen` — noch ohne Wirkung. |
| [ ] | 2. Übersetzungen | `V116__Add_Nebenkosten_Translations.sql` mit `NEBENKOSTEN`, `NK_TARIFPOSITIONEN`, `NK_ABRECHNUNG`, `NK_NOCH_NICHT_VERFUEGBAR`, `FEATURE_FLAG_NEBENKOSTENABRECHNUNG` — je **deutsch und englisch**, Abschluss mit `ON CONFLICT (key) DO NOTHING`. |
| [ ] | 3. Keycloak-Rolle (Realm-JSON) | `nebenkosten:manage` als Realm-Rolle in `zev-realm.json` und in die `composite`-Listen der vorgesehenen Fachrollen. Wirkt nur bei Neuinstallation; fuer die laufende Instanz siehe Phase 13. |
| [ ] | 4. Berechtigungsmatrix | `Specs/Berechtigungen.md` um `nebenkosten:manage` ergänzen. |
| [ ] | 5. Permission-Prüflogik extrahieren | Die Rollenauswertung aus `auth.guard.ts:30-40` in `utils/permissions.ts` als `hasAnyPermission(rollen, permissions)` herausziehen; der Guard ruft sie auf. **Kein Verhaltenswechsel** — die bestehenden Guard-Tests müssen unverändert grün bleiben. |
| [ ] | 6. Direktive `*appPermission` | Nach dem Vorbild von `directives/feature-flag.directive.ts` (Signal + `effect`), Rollenquelle `inject(Keycloak)` → `realmAccess?.roles` und `resourceAccess`, Prüfung über `hasAnyPermission` aus Phase 5. |
| [ ] | 7. Icon `chevron-down` | Eine Zeile in `icons.ts`: `'chevron-down': '<polyline points="6 9 12 15 18 9"></polyline>'` (Feather, MIT — passt zum bestehenden `chevron-right`). Der Showcase listet Icons über `Object.keys(ICONS)` und zeigt es damit automatisch. |
| [ ] | 8. Design System: Untermenü | Klassen für Elterneintrag, aufklappbare Liste und Zustand in `navigation.css`; die Optik des Auf-/Zuklappens über die bestehenden `.zev-collapsible`-Klassen. Der Indikator nutzt `chevron-down` und wird im geöffneten Zustand per CSS um 180° gedreht (erlaubt eine Übergangsanimation mit einem einzigen Icon). `npm run build` im Design System. |
| [ ] | 9. Gerüstseiten | Zwei Komponenten mit `zev-container`, `<h1>` samt `app-icon` und `zev-message--info`. Kein Service, kein HTTP-Aufruf. |
| [ ] | 10. Routing | Zwei Routen mit `[AuthGuard, FeatureFlagGuard]` und `data: { permissions: ['nebenkosten:manage'], featureFlag: 'NEBENKOSTENABRECHNUNG' }`, dazu `{ path: 'nebenkosten', redirectTo: '/nebenkosten/abrechnung', pathMatch: 'full' }`. |
| [ ] | 11. Navigation | Menüpunkt „Nebenkosten" mit `*appFeature` **und** `*appPermission`, Untereinträge, Aufklapp-Zustand in der Komponente, aufgeklappt wenn eine NK-Route aktiv ist. |
| [ ] | 12. Design-System-Showcase | Untermenü-Variante im Showcase ergänzen. |
| [ ] | 13. Keycloak (Betreiber) | **Kein Code.** Rolle `nebenkosten:manage` in der laufenden Instanz anlegen, den Fachrollen zuweisen, neu anmelden. **Vorher sind die Akzeptanzkriterien nicht prüfbar.** |

### Validierung nach den Phasen
* Backend: `cd backend-service && mvn compile -q`
* Frontend: `cd frontend-service && npx ng build --configuration=development`
* Design System: `cd design-system && npm run build`
* Nach Phase 5: `npm test -- --include=src/app/guards/auth.guard.spec.ts` muss unverändert grün sein.

## Validierungen

Diese Ausbaustufe nimmt **keine Benutzereingaben** entgegen — es gibt keine Formulare, keine
Felder und keine Persistierung. Zu prüfen sind ausschliesslich Zugriffsbedingungen:

### Frontend
| Regel | Ort | Verhalten bei Verstoss |
|---|---|---|
| Feature-Flag aktiv | `FeatureFlagGuard` (`route.data.featureFlag`) | Weiterleitung auf `/startseite` |
| Permission `nebenkosten:manage` | `AuthGuard` (`route.data.permissions`) | Weiterleitung auf `/startseite` (über `parseUrl('/')` und die Leerpfad-Route) |
| Flag **und** Permission | `*appFeature` + `*appPermission` am Menüpunkt | Menüpunkt wird nicht gerendert |
| `/nebenkosten` ohne Unterpfad | Route-Weiterleitung | Weiterleitung auf `/nebenkosten/abrechnung` |

### Backend
Keine. In dieser Ausbaustufe entstehen **keine** NK-Endpunkte.

> **Regel für die Folge-Specs:** Der erste NK-Endpunkt muss den Flag serverseitig prüfen und bei
> ausgeschaltetem Flag `403` liefern (`Nebenkosten.md`, FR-2). Ohne das ist der Flag reine
> Kosmetik — die API bliebe über einen HTTP-Client erreichbar.

## Offene Punkte / Annahmen

### Aus der Spec übernommen (beantwortet)
* Elterneintrag „Nebenkosten" ist **kein Navigationsziel**, nur Aufklapper.
* Menüpunkt hängt an Flag **und** Permission.
* Abweisungsziel einheitlich `/startseite`.
* Nur `nebenkosten:manage`; die Zuordnung zu den Fachrollen nimmt der Betreiber vor.

### Annahmen dieses Plans
* **Rollenquelle der Direktive:** `inject(Keycloak)` mit `realmAccess?.roles` und
  `resourceAccess`. Begründung: Die Navigation injiziert `Keycloak` bereits
  (`navigation.component.ts:26`), und `AuthGuardData.grantedRoles` speist sich aus derselben
  Quelle. Damit bleibt die Prüfung identisch zum Guard.
* **Icon des Menüpunkts:** `calculator` aus dem bestehenden Satz (`icons.ts`) — passend und
  bisher nicht belegt. Falls unerwünscht, ist es eine Zeile.
* **Aufklapp-Indikator:** `icons.ts` ist ein kuratierter Auszug aus **Feather Icons** (MIT,
  Kopfkommentar der Datei); `chevron-down` fehlt dort bisher nur, weil es niemand brauchte.
  Es wird ergänzt (Phase 7) statt `chevron-right` zu drehen — konsistent mit dem übrigen Satz.
  Für den geöffneten Zustand genügt eine CSS-Drehung um 180°; ein zusätzliches `chevron-up`
  wäre die Alternative, verhindert aber die Übergangsanimation.
* **Composite-Zuordnung im Realm-JSON:** Vorgeschlagen analog `rechnungen:manage`
  (`zev_user`, `org_admin`, `zev_admin`). Der Betreiber entscheidet; Phase 3 ist entsprechend
  anzupassen, bevor sie ausgeführt wird.
* **Untermenü im Hamburger-Menü:** Es wird dieselbe Mechanik verwendet wie in der breiten
  Ansicht, kein separater Modus.

### Bewusst nicht in diesem Plan
Alle offenen Fragen aus `Nebenkosten.md` Abschnitt 8 betreffen **Folge-Specs** und blockieren
diese Umsetzung nicht:
* Rechenarten (Quote / Menge / Prozent) und Betrag-statt-Menge → `NK-Tarifpositionen`
* Abrechnungsjahr 01.07.–30.06. konfigurierbar? → `NK-Tarifpositionen` / `NK-Abrechnung`
* Menüpunkte flächendeckend nach Berechtigung ausblenden → eigene Aufräumaufgabe
* Weitere Untereinträge → sobald bekannt

### Hinweise zur Prüfung
* **Feature-Flags sind mandantenweiter Zustand.** E2E-Tests, die den Flag umschalten, müssen
  serial und in einem einzigen Browser laufen — parallele Worker überlagern sich sonst.
* Die Akzeptanzkriterien zur Sichtbarkeit sind erst **nach Phase 13** prüfbar. Vorher verhält
  sich die Anwendung wie bei einem nicht umgesetzten Feature: Menüpunkt unsichtbar, Route
  abgewiesen — ohne Fehlermeldung.
