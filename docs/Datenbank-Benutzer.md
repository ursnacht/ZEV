# Datenbank-Benutzer: nur lesend

Ein Benutzer, der die Anwendungsdaten **lesen**, aber nichts verändern kann — für Auswertungen,
DB-Tools oder einen MCP-Server.

Gilt lokal wie auf dem NAS (Hene). Auf dem NAS dieselben Befehle im SSH-Fenster, gegebenenfalls
mit `sudo docker`.

## Anlegen

Als Administrator im Postgres-Container — **demselben Benutzer, mit dem Flyway die Tabellen
anlegt** (`$POSTGRES_USER`). Warum das zählt, steht unter „Künftige Tabellen".

```powershell
docker exec -it postgres sh -c 'psql -U $POSTGRES_USER -d zev'
```

In `psql`:

```sql
CREATE ROLE zev_readonly LOGIN;
\password zev_readonly

GRANT CONNECT ON DATABASE zev TO zev_readonly;
GRANT USAGE ON SCHEMA zev TO zev_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA zev TO zev_readonly;

-- Auch Tabellen, die KÜNFTIGE Migrationen anlegen:
ALTER DEFAULT PRIVILEGES IN SCHEMA zev GRANT SELECT ON TABLES TO zev_readonly;

-- Zusätzliche Sicherung: jede Sitzung startet nur-lesend
ALTER ROLE zev_readonly SET default_transaction_read_only = on;
```

> **`\password` statt `PASSWORD '…'`.** `psql` fragt das Passwort verdeckt ab — es landet weder in
> der Befehlshistorie noch im Container-Log. Das Passwort gehört nicht ins Repository; wenn es
> irgendwo abgelegt werden soll, dann in einer `.env`-Datei, die nicht eingecheckt wird.

### Künftige Tabellen

`GRANT … ON ALL TABLES` gilt nur für die Tabellen, die es **jetzt** gibt. Ohne
`ALTER DEFAULT PRIVILEGES` wäre die Tabelle der nächsten Migration für `zev_readonly`
unsichtbar — mit einem `permission denied`, das nach einem Fehler in der Abfrage aussieht.

Die Voreinstellung gilt für Objekte **des Benutzers, der sie setzt**. Deshalb muss sie unter
`$POSTGRES_USER` laufen: Mit diesem Benutzer verbindet sich auch das Backend, und Flyway legt
damit die neuen Tabellen an.

### Schema `keycloak` — bewusst nicht

Freigegeben ist nur `zev`. Im Schema `keycloak` liegen Passwort-Hashes, Sitzungen und
Client-Geheimnisse; für Auswertungen braucht es das nicht. Falls doch nötig: dieselben drei
Zeilen (`USAGE`, `SELECT`, `DEFAULT PRIVILEGES`) mit `keycloak` statt `zev`.

## Anlegen mit HeidiSQL

Dieselben Befehle, mit einer Ausnahme: **`\password` ist ein Befehl von `psql`** und funktioniert
in HeidiSQL nicht. Das Passwort steht dort im SQL. Die Benutzerverwaltung von HeidiSQL hilft nicht
weiter — sie unterstützt nur MySQL/MariaDB, nicht PostgreSQL.

1. **Als Administrator verbinden** — neue Sitzung, Netzwerktyp *PostgreSQL (TCP/IP)*:

   | Feld | lokal |
   |---|---|
   | Hostname / IP | `127.0.0.1` |
   | Port | `5432` |
   | Benutzer / Passwort | `POSTGRES_USER` / `POSTGRES_PASSWORD` aus der `.env` |
   | Datenbanken | `zev` |

   Es muss **dieser** Benutzer sein — die Voreinstellung für künftige Tabellen gilt nur für
   Tabellen des Benutzers, der sie setzt (siehe „Künftige Tabellen").

2. **Abfrage-Tab öffnen, einfügen, mit F9 ausführen:**

   ```sql
   CREATE ROLE zev_readonly LOGIN PASSWORD 'hier-das-passwort';

   GRANT CONNECT ON DATABASE zev TO zev_readonly;
   GRANT USAGE ON SCHEMA zev TO zev_readonly;
   GRANT SELECT ON ALL TABLES IN SCHEMA zev TO zev_readonly;
   ALTER DEFAULT PRIVILEGES IN SCHEMA zev GRANT SELECT ON TABLES TO zev_readonly;
   ALTER ROLE zev_readonly SET default_transaction_read_only = on;
   ```

   > **Das Passwort steht danach im Abfrageverlauf von HeidiSQL.** Der Server protokolliert es
   > standardmässig nicht, HeidiSQL merkt sich aber ausgeführte Abfragen. Entweder den Eintrag im
   > Verlauf löschen, oder zunächst einen Platzhalter setzen und das Passwort anschliessend einmal in
   > `psql` mit `\password zev_readonly` ändern.

3. **Prüfen** in einer **zweiten** Sitzung mit Benutzer `zev_readonly` — die beiden Abfragen aus
   „Prüfen" unten. In der Datenbankliste links erscheint das Schema `keycloak`, keine Tabelle darin
   lässt sich öffnen. So ist es gewollt.

### HeidiSQL auf das NAS

HeidiSQL bringt einen SSH-Tunnel mit (Reiter *SSH-Tunnel*): Pfad zu `plink.exe`, NAS-Host,
SSH-Benutzer, lokaler Port z. B. `15432`.

> **Unter *Hostname / IP* (Reiter *Einstellungen*) gehört die IP des Postgres-Containers**, nicht
> `127.0.0.1`. HeidiSQL löst diese Adresse auf der NAS-Seite auf — und `127.0.0.1` wäre dort die
> PostgreSQL von DSM, nicht die ZEV-Datenbank (siehe „Zugriff mit einem DB-Tool"). Die
> Container-IP:
>
> ```sh
> sudo docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}' postgres
> ```

## Prüfen

```powershell
docker exec -it postgres psql -U zev_readonly -d zev -c "SELECT count(*) FROM zev.messwerte"
docker exec -it postgres psql -U zev_readonly -d zev -c "DELETE FROM zev.messwerte WHERE false"
```

* Die erste muss eine Zahl liefern.
* Die zweite muss mit `cannot execute DELETE in a read-only transaction` abbrechen. Das
  `WHERE false` sorgt dafür, dass sie selbst bei falsch gesetzten Rechten nichts löschen würde.

Innerhalb des Containers fragt `psql` je nach Konfiguration nicht nach dem Passwort; über das
Netzwerk (Port 5432) schon.

## Zugriff mit einem DB-Tool

**Lokal:** `localhost:5432`, Datenbank `zev`, Benutzer `zev_readonly`.

**NAS:** Über einen SSH-Tunnel — aber **nicht** auf `localhost:5432` des NAS: Dort läuft die
PostgreSQL-Instanz von DSM selbst, nicht die ZEV-Datenbank. Die Fehlermeldung
(`no pg_hba.conf entry … database zev`) sieht dann nach einem Rechteproblem aus, obwohl man
schlicht auf der falschen Datenbank landet. Stattdessen auf die IP des Containers tunneln:

```sh
ssh <user>@<nas-host> "sudo docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}' postgres"
ssh -N -L 15432:<container-ip>:5432 <user>@<nas-host>
```

Das DB-Tool dann auf `localhost:15432`. Lokal bewusst ein anderer Port als 5432, damit die
lokale Datenbank nicht verwechselt wird. Die Container-IP kann sich beim Neuaufsetzen des Stacks
ändern.

## Passwort ändern

```sql
\password zev_readonly
```

## Nach einem Restore

Der **Benutzer** ist nicht im Backup — er gehört zum Datenbankserver, nicht zur Datenbank. Die
**Rechte** auf Schema und Tabellen und die Voreinstellung für künftige Tabellen sind dagegen Teil
des Backups und kommen mit zurück (`docs/Datenbank-Backup.md`).

* **Restore auf demselben Server:** nichts zu tun.
* **Frischer Server** (z. B. NAS neu aufgesetzt): **vor** dem Restore `CREATE ROLE zev_readonly
  LOGIN;` und `\password zev_readonly` ausführen. Den Rest bringt das Backup mit.

> **Fehlt der Benutzer, scheitert der ganze Restore** — nicht nur die Rechtezuweisung.
> `scripts/db-restore.*` spielt das Backup in **einer** Transaktion zurück, und die
> Zuweisung an einen unbekannten Benutzer ist ein Fehler. Die laufende Datenbank bleibt dabei
> unverändert; der Restore muss nach dem Anlegen des Benutzers nur wiederholt werden.
> `-NurPruefen` zeigt das gefahrlos vorher.

## Entfernen

```sql
REVOKE ALL ON ALL TABLES IN SCHEMA zev FROM zev_readonly;
REVOKE USAGE ON SCHEMA zev FROM zev_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA zev REVOKE SELECT ON TABLES FROM zev_readonly;
REVOKE CONNECT ON DATABASE zev FROM zev_readonly;
DROP ROLE zev_readonly;
```

`DROP ROLE` scheitert, solange noch Rechte oder Voreinstellungen auf den Benutzer verweisen —
deshalb zuerst die `REVOKE`-Zeilen.
