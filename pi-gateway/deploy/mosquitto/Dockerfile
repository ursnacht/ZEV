# Dev-Broker mit gebackener Config + passwd (vermeidet Bind-Mount-Rechteprobleme:
# COPY erzeugt world-lesbare 0644-Dateien, die der mosquitto-User lesen kann).
# NUR für lokale Entwicklung/Simulation – nicht für Produktion.
FROM eclipse-mosquitto:2
COPY mosquitto.conf /mosquitto/config/mosquitto.conf
COPY passwd /mosquitto/config/passwd
