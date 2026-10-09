# Datenbank-Backup und -Restore

Sichern und Zurückspielen der ZEV-Datenbank — lokal unter Windows (PowerShell) und auf dem NAS
(Hene, Shell). Die Skripte liegen in `scripts/`.

| Zweck | Windows | NAS |
|---|---|---|
| Backup | `scripts/db-backup.ps1` | `scripts/db-backup.sh` |
| Backup prüfen (verändert nichts) | `scripts/db-restore.ps1 -Datei X -NurPruefen` | `scripts/db-restore.sh --nur-pruefen X` |
| Restore | `scripts/db-restore.ps1 -Datei X` | `scripts/db-restore.sh X` |

## Was gesichert wird

Die Datenbank `zev` enthält **zwei Schemas**:

* `zev` — die Anwendungsdaten (Messwerte, Einheiten, Rechnungen, Steuerentscheide …)
* `keycloak` — Benutzer, Passwörter, Rollen, Realm

Ein Backup enthält **beides**. Ein Restore setzt deshalb auch die **Keycloak-Benutzer** auf den
Stand des Backups zurück.

## Backup

```powershell
.\scripts\db-backup.ps1 -Ziel D:\Backups\zev
```

Ergebnis: `zev-JJJJ-MM-TT_HHMM.dump` im komprimierten Format von PostgreSQL (`pg_dump -Fc`).

> **Nicht mit `>` umleiten.** Das Skript schreibt den Dump im Container und kopiert ihn mit
> `docker cp` heraus. Windows PowerShell 5.1 wandelt umgeleitete Programmausgabe in Text um — die
> Binärdatei wäre unbrauchbar, ohne dass ein Fehler erscheint. Wer die Befehle von Hand ausführt,
> sollte dasselbe tun.

## Backup prüfen

```powershell
.\scripts\db-restore.ps1 -Datei D:\Backups\zev\zev-2026-10-08_0930.dump -NurPruefen
```

Spielt das Backup in eine **separate** Datenbank `zev_test`, zeigt einige Zeilenzahlen und die
letzte Migrationsversion, und löscht `zev_test` wieder. **Die laufende Datenbank bleibt
unberührt.**

> **Ein Backup, das nie zurückgespielt wurde, ist ungeprüft.** Die Prüfung nach jedem neuen
> Backup ist billig und zeigt, ob die Datei lesbar und vollständig ist.

## Restore

```powershell
.\scripts\db-restore.ps1 -Datei D:\Backups\zev\zev-2026-10-08_0930.dump
```

Ablauf:

1. Das Backup wird in eine **neue** Datenbank `zev_neu` gespielt. Schlägt das fehl, ist nichts
   verändert.
2. Nachfrage — erst nach Eingabe von `RESTORE` geht es weiter. Dann werden `backend-service` und
   `keycloak` gestoppt.
3. Die Datenbanken werden umbenannt: `zev` → `zev_alt`, `zev_neu` → `zev`.
4. `keycloak` und `backend-service` werden wieder gestartet — auch nach einem Fehler.

Der bisherige Stand bleibt als **`zev_alt`** erhalten. Ein früheres `zev_alt` wird dabei gelöscht.

> **Warum nicht `pg_restore --clean` in die laufende Datenbank.** `--clean` löscht nur, was im
> Backup vorkommt. Bei einem **älteren** Backup blieben Tabellen neuerer Migrationen stehen
> (etwa `einstrahlungsprognose` aus V166), während die Migrationsliste auf den alten Stand
> zurückginge. Flyway versuchte diese Tabellen beim Start neu anzulegen und bräche ab. Eine frische
> Datenbank kennt das Problem nicht — und der Tausch über Umbenennen lässt sich rückgängig machen.

### Rückweg

Ist nach dem Restore etwas nicht in Ordnung, den vorherigen Stand zurückholen:

```powershell
docker stop backend-service keycloak
@'
SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'zev' AND pid <> pg_backend_pid();
ALTER DATABASE zev RENAME TO zev_verworfen;
ALTER DATABASE zev_alt RENAME TO zev;
'@ | docker exec -i postgres sh -c 'psql -v ON_ERROR_STOP=1 -U $POSTGRES_USER -d postgres'
docker start keycloak backend-service
```

Danach liegt der verworfene Stand als `zev_verworfen` vor und kann mit `dropdb` entfernt werden.

### Aufräumen

Läuft alles, kann `zev_alt` weg — es belegt so viel Platz wie die Datenbank selbst:

```powershell
docker exec postgres sh -c 'dropdb -U $POSTGRES_USER zev_alt'
```

> **Zusätzliche Datenbank-Benutzer** (z. B. `zev_readonly`, `docs/Datenbank-Benutzer.md`) sind
> **nicht** im Backup — sie gehören zum Server. Ihre Rechte aber schon. Auf einem **frischen**
> Server deshalb zuerst die Benutzer anlegen, sonst scheitert der Restore (er läuft in einer
> Transaktion, und die Rechtezuweisung an einen unbekannten Benutzer ist ein Fehler).

## Version des Backups und der Anwendung

Das Backup enthält auch die Liste der ausgeführten Migrationen (`flyway_schema_history`). Die
Prüfung zeigt die letzte Version an.

* **Älteres Backup, neuere Anwendung:** kein Problem — die fehlenden Migrationen laufen beim Start
  nach.
* **Neueres Backup, ältere Anwendung:** scheitert beim Start, weil die Anwendung die neueren
  Migrationen nicht kennt. Zuerst die Anwendung auf den passenden Stand bringen.

## Auf dem NAS (Hene)

```sh
ssh <user>@<nas-host>
cd /volume1/docker/zev
DOCKER="sudo docker" ./scripts/db-backup.sh /volume1/backup/zev
DOCKER="sudo docker" ./scripts/db-restore.sh --nur-pruefen /volume1/backup/zev/zev-2026-10-08_0930.dump
```

`DOCKER="sudo docker"` nur, wenn `docker` dort `sudo` braucht.

> **Das Backup vom NAS herunterkopieren**, z. B. mit `scp`. Ein Backup auf derselben Platte schützt
> nicht gegen deren Ausfall.

> **Zeilenenden:** Die `.sh`-Skripte müssen Unix-Zeilenenden haben, sonst bricht `sh` mit
> `\r: command not found` ab. `.gitattributes` legt das für `scripts/*.sh` fest; wer die Dateien
> aus einem Windows-Checkout von Hand aufs NAS kopiert, sollte das prüfen.

## Hinweise

* Die Skripte brauchen weder Passwort noch Benutzername — beides ist im Postgres-Container bereits
  gesetzt (`$POSTGRES_USER`).
* **Aufbewahrung:** Ein Löschen in der Datenbank entfernt Daten nicht aus alten Backups
  (`Specs/Datenaufbewahrung.md`, offene Frage zur Backup-Aufbewahrung).
