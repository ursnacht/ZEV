#!/bin/sh
# Erzeugt die Mosquitto-passwd beim Container-Start aus einer Umgebungsvariablen
# und startet anschliessend den Original-Entrypoint des eclipse-mosquitto-Images.
# So liegen keine Broker-Credentials im Image oder im Repo (nur gehashte Eintraege
# entstehen zur Laufzeit im Container).
#
# MOSQUITTO_USERS: durch Whitespace getrennte "user:passwort"-Paare, z.B.
#   MOSQUITTO_USERS="zev-backend:geheim1 pi-org42:geheim2"
# Einschraenkung: Passwoerter duerfen keinen Whitespace enthalten (Wort-Splitting).
# Ein Doppelpunkt im Passwort ist erlaubt (nur der erste trennt user/passwort).
set -eu

PASSWD_FILE=/mosquitto/config/passwd

if [ -n "${MOSQUITTO_USERS:-}" ]; then
    : > "$PASSWD_FILE"
    for entry in $MOSQUITTO_USERS; do
        user=${entry%%:*}
        pass=${entry#*:}
        if [ "$user" = "$entry" ] || [ -z "$user" ] || [ -z "$pass" ]; then
            echo "mosquitto-entrypoint: ungueltiger MOSQUITTO_USERS-Eintrag (erwartet user:passwort): '$entry'" >&2
            exit 1
        fi
        printf '%s:%s\n' "$user" "$pass" >> "$PASSWD_FILE"
    done
    # Klartext-Zeilen in-place zu PBKDF2-Hashes umschreiben.
    mosquitto_passwd -U "$PASSWD_FILE"
    # mosquitto liest die passwd NACH drop_privileges (als User 'mosquitto', nicht root)
    # -> Eigentum muss 'mosquitto' sein (root-owned waere unlesbar). Der Basis-Entrypoint
    # chownt /mosquitto ohnehin rekursiv auf mosquitto; wir setzen es explizit + 0700
    # (killt die "world readable"-Warnung; eine "owner is not root"-Warnung bleibt hier
    # prinzipbedingt bestehen und ist harmlos).
    chown mosquitto:mosquitto "$PASSWD_FILE"
    chmod 0700 "$PASSWD_FILE"
    echo "mosquitto-entrypoint: passwd aus MOSQUITTO_USERS generiert." >&2
else
    echo "mosquitto-entrypoint: MOSQUITTO_USERS nicht gesetzt -> keine passwd erzeugt;" \
         "Broker lehnt bei 'allow_anonymous false' alle Logins ab." >&2
fi

# Original-Entrypoint des Basis-Images uebernehmen (rechtekorrektur + exec mosquitto).
exec /docker-entrypoint.sh "$@"
