### gPlugK

## Verbindung via USB
* Verbinden mit USB-Kabel (achten, dass es ein Datenkabel ist, evtl. vorher kurz gPlug an Ladegrät aufladen, 
  wenn PC nicht genug Strom für Verbindungsaufbau liefert)
* Gerätemanager öffnen 
* Unter `Anschlüsse (COM & LPT)` sollte ein `USB Serial Device (COM7)` o.ä. auftauchen, 
  **sonst Taste Res auf dem gPlugK kurz drücken**
* Im Browser: https://tasmota.github.io/install/
  * Port wählen (COM7) und `Connect` klicken. Evtl. muss Port noch einmal selektiert werden.
  * `Open Console` klicken
  * Access Point starten mit Befehl: `WiFiConfig 2`
  * Handy verbinden mit SSID `gPlugK`
    * Im Browser auf Handy 192.168.4.1 öffnen
    * SSID wählen, Passwort eingeben
  * `ssid` zeigt die WiFi-Konfiguration: \
     `10:52:26.348 MQT: gPlug/2/TEST/stat/RESULT = {"SSId1":"DiLegno 2GHz","SSId2":"Glue_public"}`
  * Mit dem Befehl `Ap 1` oder `Ap 2` die entsprechende ssid wählen

## Tasmota Befehle
> https://tasmota.github.io/docs/Commands/