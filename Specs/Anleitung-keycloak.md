## Schritte in Keycloak:

1. Organizations-Feature aktivieren (in docker-compose mit --features=organization)
2. Organization erstellen:
   - Keycloak Admin Console öffnen: http://localhost:9000
   - Realm "zev" auswählen
   - Menü: "Organizations" → "Create organization"
   - Name eingeben (z.B. "ZEV Musterstrasse")
   - Die generierte ID notieren (oder die Default-UUID c2c9ba74-de18-4491-9489-8185629edd93 verwenden falls möglich)
     - c2c9ba74-de18-4491-9489-8185629edd93 
3. Benutzer zur Organization hinzufügen:
   - Organization öffnen → Tab "Members"
   - Benutzer hinzufügen (z.B. testuser)
4. Client Scope für Organizations konfigurieren:
   - Menü: "Client scopes" → "organization" (sollte bereits existieren)
   - Falls nicht: Neuen Scope "organization" erstellen
   - Mapper hinzufügen: "Organization Membership"
5. Client Scope dem Client zuweisen:
   - Menü: "Clients" → Euren Client auswählen (z.B. "zev-frontend")
   - Tab "Client scopes" → "Add client scope"
   - "organization" hinzufügen (als "Default")

#### JWT-Debugger
* https://www.jwt.io/

## Service-Account

Ein Service-Account in Keycloak ist kein klassischer Benutzer mit Passwort, sondern ein Client, 
der sich selbst gegenüber Keycloak authentifizieren kann (Machine-to-Machine). 
Das ist ideal für dein Spring-Boot-Backend, um im Hintergrund die Admin-API aufzurufen.

### Hier ist die Schritt-für-Schritt-Anleitung:

1. #### Client erstellen
   1. Logge dich in die Admin-Konsole ein
   2. Gehe zu Clients -> Create client
   3. Client type: OpenID Connect
   4. Client ID: z. B. zev-backend
   5. Klicke auf Next

2. #### Service Accounts aktivieren (**Wichtig!**)
   In den **"Capability config"** Einstellungen musst du folgende Schalter umlegen:
   1. **Client Authentication:** Auf **On** stellen (Dadurch wird der Client "confidential" und erhält ein Secret).
   2. **Authorization:** Kann meist auf "Off" bleiben, außer du nutzt Fine-Grained Authorization.
   3. **Authentication flow:** Deaktiviere "Standard flow" (Browser Login) und aktiviere "Service accounts roles".
   4. Klicke auf **Save**

3. #### Berechtigungen zuweisen (Roles)
   Da dein Service-Account Organisationen oder Benutzer verwalten soll, braucht er Admin-Rechte.
   1. Klicke im Client auf den Reiter **Service account roles**
   2. Klicke auf **Assign role**
   3. Wähle im Dropdown "Filter by client" den Client `realm-management` aus.
   4. Wähle die benötigten Rollen aus:
      * `view-users` (zum Lesen von Usern/Orgas)
      * `manage-users` (zum Erstellen/Bearbeiten)
      * `query-realms` (oft nötig für globale Abfragen)
   5. Klicke auf **Assign**

4. #### Client Secret abrufen
   Damit dein Spring-Boot-Service sich anmelden kann, benötigt er das Secret.
   1. Gehe im Client auf den Reiter **Credentials**
   2. Kopiere den Wert bei **Client Secret**

5. ##### Den Token in der Anwendung abrufen
   Dein Backend kann nun mit der Client ID und dem Secret einen Token holen, ohne dass ein menschlicher Benutzer involviert ist.

   **Beispiel mit `curl`:**

    ```Bash
    curl.exe -X POST "http://localhost:9000/realms/zev/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=client_credentials" \
    -d "client_id=zev-backend" \
    -d "client_secret=<dein-client-secret>"
    ```
    Das Ergebnis ist ein JWT, das im `sub`-Feld die ID des Service-Accounts trägt 
    (Keycloak erstellt im Hintergrund einen versteckten User namens `service-account-zev-backend`).
    
### Integration in Spring Boot (Admin Client)
  Wenn du den KeycloakBuilder nutzt, sieht die Konfiguration für den Service-Account so aus:

```Java
Keycloak keycloak = KeycloakBuilder.builder()
    .serverUrl("http://localhost:9000")
    .realm("zev")
    .grantType(OAuth2Constants.CLIENT_CREDENTIALS) // Wichtig: Client Credentials!
    .clientId("zev-backend")
    .clientSecret("dein-client-secret")
    .build();
```

### Warum das besser ist als ein Admin-User:
  * **Kein Passwort-Ablauf:** Du musst kein User-Passwort rotieren.
  * **Sicherheit:** Falls das Secret kompromittiert wird, kannst du es im Keycloak-Panel sofort invalidieren oder neu generieren.
  * **Trennung:** Du siehst in den Logs genau, dass der Service die Änderung vorgenommen hat und nicht ein Administrator.
