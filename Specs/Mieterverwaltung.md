# Mieterverwaltung

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Die Mieter sollen unabhängig von der Einheit verwaltet und einer Einheit zugeordnet werden können.
* **Warum machen wir das:** Bei einem Mieterwechsel innerhalb eines Quartals müssen beide Mieter einer Einheit eine Quartalsrechnung erhalten.
* **Aktueller Stand:** Da keine Mietdauer angegeben werden kann, ist nur ein Mieter pro Quartal in einer Einheit möglich.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?
### FR-1: Ablauf / Flow
1. Neue Seite aus dem Menü erreichbar: Mieterverwaltung
2. Das System erlaubt die Verwaltung von Mietern mit folgenden Attributen:
   * Name
   * Strasse
   * PLZ
   * Ort
   * Mietbeginn (Datum), Pflichtattribut
   * Mietende (Datum), optionales Attribut
   * Einheit (Auswahl mit einer Dropdown-Liste). Speichert nur die ID der Einheit.
3. Das System kann für mehrere Mieter einer Einheit während eines Quartals (Mieterwechsel innerhalb des Quartals) eine Rechnung erstellen
4. In der Rechnung wird maximal die Zeitspanne (Mietbeinn, Mietende) berücksichtigt, in der der Mieter auch tatsächlich Mieter war.
5. Wenn das Mietende fehlt, gilt die Miete bis zum Quartalsende. 

### FR-2: Persistierung
* Das System speichert die Mieter in der Datenbank
* Das Attribut "mietername" wird nicht mehr benötigt und soll aus der Einheit entfernt werden. 

### FR-3: Layout
* Vorlage für Layout: Tarifverwaltung
* Mieter ohne Mietende ("aktueller Mieter") werden in der Spalte "Mietende" mit dem Design-System-Badge `zev-status--success` ("Aktuell") dargestellt.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)
* [x] Die Mieterverwaltung ist aus dem Menü mit der Rolle "zev_admin" aufrufbar
* [x] Mieter können erfasst, bearbeitet und gelöscht werden (Kebabmenü)
* [x] Falls "Mietende" erfasst ist, muss dieses nach dem "Mietbeginn" liegen
* [x] Die Zeitspannen von mehreren Mietern der gleichen Einheiten dürfen sich nicht überschneiden.
* [x] Nur der Mieter mit dem jüngsten Mietbeginn einer Einheit darf kein Mietende haben (aktueller Mieter). Die andern Mieter müssen ein Mietend haben.
* [x] Bei einem Mieterwechsel innerhalb eines Quartals, muss für jeden Mieter eine Quartalsrechnung erstellt werden.
* [ ] `id` und `orgId` im Body von `POST /api/mieter` werden nicht übernommen. (Test: MieterControllerTest#createMieter_IdUndOrgIdImBody_WerdenIgnoriert)
* [ ] Speichern mit der ID eines Mieters, der nicht zum eigenen Mandanten gehört, wird abgewiesen; der fremde Mieter bleibt unverändert. (Test: MieterServiceTest#saveMieter_IdNichtImEigenenMandanten_ThrowsException; Test: FremdeIdBeimSpeichernIT#saveMieter_MitFremderId_WirdAbgewiesenUndFremderMieterBleibtUnveraendert)
* [ ] Beim Ändern bleibt der Mieter im eigenen Mandanten, auch wenn der Body eine andere `orgId` trägt. (Test: MieterServiceTest#saveMieter_Update_UebernimmtOrgIdVomBestehendenMieter; Test: FremdeIdBeimSpeichernIT#saveMieter_EigenerMieterMitFremderOrgId_BleibtImEigenenMandanten)
* [ ] Ein Mieter lässt sich nur Einheiten des eigenen Mandanten zuordnen; sonst wird das Speichern abgewiesen („Einheit nicht gefunden“). (Test: MieterServiceTest#saveMieter_EinheitNichtImEigenenMandanten_ThrowsException; Test: FremdeIdBeimSpeichernIT#saveMieter_MitFremderEinheit_WirdAbgewiesen)


## 3. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* -

### NFR-2: Sicherheit
* Für die Mieterverwaltung ist die Rolle "zev_admin" notwendig.
* `id` und `orgId` aus dem Request-Body übernimmt der Server nicht (`@JsonProperty(access = READ_ONLY)`); `PUT` nimmt die ID aus dem Pfad. Beim Ändern lädt der Service den bestehenden Mieter unter dem Mandantenfilter – fehlt er, wird abgewiesen („Mieter nicht gefunden“) – und übernimmt dessen `org_id`. Die zugeordneten Einheiten müssen dem eigenen Mandanten gehören (Hintergrund: Anhang A.1).
 

### NFR-3: Kompatibilität
* -

## 4. Edge Cases & Fehlerbehandlung
* -

## 5. Abgrenzung / Out of Scope
* Leerstände sind möglich, d.h. es kann Lücken geben

## 6. Offene Fragen
* -

## Anhang A – Entscheidungen und Verlauf

### A.1 Mitgeschickte `id`, `orgId` und fremde Einheiten (NFR-2, 18.10.2026)
Gefunden beim Security-Check zur Einheitenverwaltung (OWASP A01, `Specs/Einheitenverwaltung.md`, Anhang A.4). `saveMieter` dient für Anlegen und Ändern und setzte `org_id` nur bei `id == null`; sonst kam sie aus dem Request-Body. Folgen: Ein `POST` mit fremder `id` und `orgId` überschrieb einen fremden Mieter (`save` → `merge` lädt ungefiltert), ein `PUT` mit fremder `orgId` schob einen eigenen Mieter in einen anderen Mandanten. Zudem wurden die `einheitIds` ungeprüft übernommen, ein Mieter liess sich so einer fremden Einheit zuordnen. `FremdeIdBeimSpeichernIT` war gegen den alten Code rot (der Test zur fremden Einheit kam nach der ersten Gegenprobe dazu). Behoben durch die Regel in NFR-2.
