# Mosquitto-Broker mit gebackener Config, aber OHNE eingebackene passwd:
# Die Zugangsdaten werden beim Container-Start aus der Umgebung (MOSQUITTO_USERS)
# generiert -> keine Secrets im Image oder im Repo.
# Funktioniert fuer Dev (docker-compose-mqtt.yml) wie NAS (eigene MOSQUITTO_USERS in .env).
FROM eclipse-mosquitto:2
COPY mosquitto.conf /mosquitto/config/mosquitto.conf
COPY docker-entrypoint.sh /zev-entrypoint.sh
RUN chmod +x /zev-entrypoint.sh
ENTRYPOINT ["/zev-entrypoint.sh"]
# CMD MUSS hier explizit gesetzt werden: Ein ENTRYPOINT-Override im abgeleiteten Image
# löscht das vom Basis-Image geerbte CMD. Ohne diese Zeile bekäme /docker-entrypoint.sh
# keine Argumente und beendet sich mit Code 0 (mosquitto startet nie).
CMD ["/usr/sbin/mosquitto", "-c", "/mosquitto/config/mosquitto.conf"]
