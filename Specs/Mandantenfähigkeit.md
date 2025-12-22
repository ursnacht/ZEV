# Mandantenfähigkeit

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Das System soll von mehreren Mandanten (mehrere ZEV) verwendet werden können.
* **Warum machen wir das:** So können wir mehr Benutzer auf das System bringen und Mehreinnahmen generieren.
* **Aktueller Stand:** Es gibt keine Mandanten.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?
### FR-1: Organizations
* In keycloak wird das Konzept der "Organizations" verwendet, um Mandaten zu abzubilden.
* Die Organisationen können wie folgt aus dem JWT geholt werden: `jwtToken.getTokenAttributes().get("organizations")`
* Vorteil: es muss nicht für jeden Mandanten ein Realm erstellt werden.
* Nachteil: proprietäres Feature von keycloak.
* Claims werden wie folgt im JWT abgelegt:
* ```{
  "alias": {
    "id": "c2c9ba74-de18-4491-9489-8185629edd93"
  }
}```

### FR-2: Ablauf / Flow
1. Wenn der User sich authentisieren muss, wird er wie bisher zu keycloak weitergeleitet.
2. Keycloak fügt die org_id (UUID) des Benutzers in das JWT. 
3. Im Backend kann die org_id entnommen werden, um so die Queries auf der Datenbank mit der org_id zu ergänzen.

### FR-3: User gehört mehreren Organisationen an 
* Ein Benutzer kann mehreren Organisationen angehören
* Falls eine Liste von org_id im JWT vorhanden ist, muss der Benutzer wählen können, mit welcher Organisation er arbeiten möchte.
  * So benötigt der User nicht mehrere Credentials.
* Die Organisationen können von keycloak wie folgt bezogen werden: 
  * `/admin/realms/{realm}/organizations`
  * `/admin/realms/{realm}/organizations/{id}`

### FR-4: Datenbankabfragen
* Die Tabellen in der Datenbank müssen mit einer neuen Spalte "org_id" ergänzt werden
  * einheit
  * messwerte
  * tarif
  * metriken
* Die Entitäten müssen mit einem @Filter ergänzt werden, der automatisch `org_id = :org_id` in die Queries einfügt.
* Es muss eine globale `@FilterDef` erstellt werden, die dann mehrfach verwendet wird.
* HandlerInterceptor: Hibernate Filter aktivieren

### FR-5: Metriken
* Die Metriken müssen mit dem Namen der Organisation ergänzt werden (tag `org`), so dass sie unterschieden werden können.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)
* [ ] Die org_id kann aus dem JWT gelesen werden
* [ ] Der Benutzer kann die Organisation, falls er mehreren angehört, nach der Authentisierung in keycloak in der Anwendung wählen, mit der er arbeiten möchte. 
* [ ] Es werden nur die Einheiten, Messwerte und Tarife der eigenen Organisation in der Anwendung angezeigt oder verwendet.
* [ ] Die Metriken enthalten ein zusätzliches Tag `org` mit dem Namen der Organisation


## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Die Datenbankqueries dürfen nicht wesentlich langsamer werden: Index erstellen oder ergänzen.

### NFR-2: Sicherheit
* Der Benutzer darf die org_id im JWT nicht fälschen können und so die Einheiten etc. einer anderen Organisation sehen.
* Der Admin-User ist in allen Organisationen enthalten.


## 5. Edge Cases & Fehlerbehandlung
* Wenn keine org_id im Token enthalten ist, darf der Benutzer in der Anwendung nichts machen können.
  * Es soll eine Fehlermeldung angezeigt werden
  * Der User wird ausgeloggt


## 6. Abgrenzung / Out of Scope
* Verwaltung von Organisationen und deren Benutzer. Dies wird in keycloak gemacht.


## 7. Offene Fragen
* Wenn ein Benutzer in mehreren Organisationen ist, liefert keycloak keine organizations im JWT.


## 8. Schritte
Gehe bei der Umsetzung in folgenden Schritten vor, die ich separat anfordern kann:
1. org_id(s) aus dem JWT lesen und loggen
2. Bei mehreren org_ids im JWT soll die erste verwendet werden. Wenn keine org_id enthalten ist, Benutzer ausloggen
3. Datenbank mit org_id erweitern (Spalte, Queries mit Filter)
4. Auswahl Organisation falls mehrere org_ids im JWT enthalten sind
   * Namen der Organisation aus keycloak beziehen
   * Name der Organisation im Menü neben dem Benutzernamen anzeigen
5. Metriken mit dem Namen der Organisation erweitern
