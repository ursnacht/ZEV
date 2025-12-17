# Metriken mit Prometheus und Grafana

## 1. Ziel & Kontext
*   Metriken dienen der Überwachung / Monitoring der Anwendung.
*   Mit Hilfe der Metriken können wir Fehlverhalten der Benutzer und der Anwendung feststellen und mit Grafana Alarmierung auslösen.
*   Aktuell ist lediglich Logging eingebaut.

## 2. Funktionale Anforderungen (Functional Requirements)
*   Als Administrator der Anwendung möchte ich erkennen, wenn bspw. zuviel CPU oder Memory gebraucht wird
*   Als Benutzer möchte ich (via Grafana) informiert werden, wenn ich vergessen habe Messdaten zu laden oder die Solarverteilung zu berechnen. 
*   Wenn ich Messdaten lade oder die Solarverteilung berechnen lasse, soll eine Metrik mit dem aktuellen Datum publiziert werden.

## 3. Technische Spezifikationen (Technical Specs)
*   Services "prometheus" und "grafana" in docker-compose.yml aufnehmen.
*   Metriken mit sprechenden Namen erstellen (in "Messdaten laden" und "Solarstromverteilung") und publizieren.
*   Zugriff auf die Metrik-Endpunkte für Prometheus zulassen.
