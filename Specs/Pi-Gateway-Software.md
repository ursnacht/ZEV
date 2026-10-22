# Pi-Gateway-Software

## 1. Ziel & Kontext - Warum wird das Feature benötigt?

* **Was soll erreicht werden:** Eine Software auf dem **Raspberry Pi** am Zählerstandort liest die Stromzähler aus und **publiziert deren absolute Zählerstände** per MQTT an den Broker. Sie ist das **Publisher-Gegenstück** zum Backend-Subscriber aus `Specs/MQTT-Integration.md`.
* **Warum machen wir das:**
  - Automatisierte, zeitnahe Datenerfassung statt manuellem CSV-Upload bzw. dem früheren File/SFTP-Weg.
  - Klar getrennte Verantwortung: der MQTT-Broker verteilt nur Nachrichten, das **Auslesen der Zähler ist Aufgabe dieses separaten Prozesses** (siehe Rollentrennung in `docs/Netzwerk-Topologie-Hene.md`).
* **Ausgangslage:**
  - Bei Hene liest der Pi **15 Wago-Zähler** (über das Kommunikationsmodul WAGO 879-9000), den **BKW-Bilanzzähler** (über WhatWatt Go) und die **Batterie** (am Hybrid-Wechselrichter) aus – alle per **Modbus TCP**. Vor MQTT stellte er die Daten per **SFTP** bereit; dieser Weg ist abgelöst (`docs/Netzwerk-Topologie-Hene.md`).
  - In `Specs/MQTT-Integration.md` ist diese Pi-Software bewusst **Out of Scope** – dieses Dokument beschreibt sie.
  - Topologie: Die Software publiziert direkt an den Broker auf dem NAS (Variante B). Ein lokaler Broker + Bridge (Variante C) ist für sie **nicht erforderlich**, da absolute Zählerstände verlusttolerant sind (siehe unten).
  - Die **gPlugK im Nachbarhaus** liest diese Software **nicht**: Sie publizieren selbst (`Specs/MQTT-Integration.md`, FR-10). Der Mosquitto, der ihre Meldungen auf dem Pi per Bridge an den NAS weiterleitet, ist Betrieb, nicht Teil dieser Software.

> **Designentscheidung – absolute Zählerstände statt Deltas:** Der Pi überträgt die **absoluten (kumulativen) Zählerstände**, **nicht** berechnete Deltas. Die Delta-/Intervall-Bildung erfolgt im **Backend** (`Specs/MQTT-Integration.md`, FR-6). Vorteil: **verlusttolerant** — geht eine Nachricht verloren, bleibt die Gesamtsumme korrekt (die Differenz zweier empfangener Stände deckt die Lücke), es sinkt nur kurz die Auflösung. Dadurch ist der Pi **weitgehend zustandslos** (keine „letzter Stand"-Persistenz nötig) und QoS/Pufferung entspannen sich deutlich.

> Dieses Dokument beschreibt **Pi-seitige Software** (eigenes Repo/Artefakt), **nicht** das ZEV-Backend. App-Rollen (`zev_user`/`zev_admin`), i18n und die DB-Mandantenfähigkeit gelten hier **nicht** direkt; die Mandantentrennung erfolgt über Topic-`org_id` + Broker-ACL (siehe NFR-2).

## 2. Funktionale Anforderungen (FR)

### FR-1: Zähler auslesen
1. **Wago via Modbus TCP (bei Hene 15 Geräte, Liste beliebig erweiterbar — siehe FR-5):** periodisches Lesen der relevanten Register — die **Wirkenergie**-Zählerstände in **kWh** (Bezug/Einspeisung), **nicht** Leistung (kW) oder Blind-/Scheinenergie; pro Gerät Host/Port/Register/Unit-ID konfigurierbar. Die Wago hängen per seriellem Modbus (RTU) am **Kommunikationsmodul WAGO 879-9000** (RTU→TCP, siehe `docs/Netzwerk-Topologie-Hene.md`): gemeinsame Modul-IP (`host`/`port`), Unterscheidung über die **Unit-ID**.
2. **Weitere Modbus-TCP-Geräte**, je mit eigener `host`:
   * **BKW-Bilanzzähler via WhatWatt Go:** Bezug (OBIS 1.8.0) und Rücklieferung (OBIS 2.8.0); Registerkarte und Firmware-Voraussetzung in `docs/Netzwerk-Topologie-Hene.md`, Abschnitt „WhatWatt Go“. Die Einheiten `BEZUG`/`RUECKLIEFERUNG` teilen sich den Messpunkt (`Specs/Bilanzmesspunkt.md`).
   * **Batterie am Hybrid-Wechselrichter:** Lade- und Entladeenergie als `bezug`/`einspeisung` (`Specs/Batteriespeicher.md`), optional der Ladezustand als Zustandswert (`Specs/Gerätezustand.md`); Beispiel in `pi-gateway/config.example.yaml`.
3. Jede physische Quelle ist einer **Einheit** über deren **`messpunkt`** zugeordnet (Konfiguration, kein Rückgriff auf interne DB-IDs).
4. Lese-/Publish-Intervall konfigurierbar (Default z.B. 1–5 min).
5. **Lese-Timeout konfigurierbar (Nachtrag):** Die Wartezeit auf die Antwort eines Zählers ist konfigurierbar, **global** über `read_timeout` (Default **5 s**) und je Zähler über `zaehler[].read_timeout` überschreibbar (gleicher Schlüsselname, nur enger gültig) (z.B. ein einzelner langsamer Slave am Hub). Angabe als Zahl (Sekunden) oder Kurzform (`"8s"`, `"1m"`); Werte ≤ 0 sowie ungültige Angaben führen zu einem `ConfigError` **beim Start** (kein stiller Fallback). Der **wirksame globale Wert** wird **beim Start** in der Start-Log-Zeile ausgegeben (neben `org_id`, Zähleranzahl, Intervall, Broker), damit im Betrieb ohne Blick in die `config.yaml` erkennbar ist, welcher Timeout gilt – auch der stillschweigende Default. Zähler mit eigenem `read_timeout` werden zusätzlich in einer eigenen Zeile genannt, damit der globale Wert nicht als für alle Zähler gültig missverstanden wird (die Reader selbst loggen ihren Wert nur auf `DEBUG`).
6. **Lese-Versuche / Retry (Nachtrag):** Ein fehlgeschlagener Read wird **wiederholt**, bevor der Zähler im Zyklus übersprungen wird. Anzahl über `read_attempts` konfigurierbar (**inklusive** Erstversuch, Default **2** = ein Wiederholversuch; `1` = kein Retry). Gilt **global**, nicht je Zähler — die beobachteten Fehler treffen wechselnde Zähler.
   * **Begründung:** Am eingesetzten Kommunikationsmodul **WAGO 879-9000** ist **kein RTU-Response-Timeout einstellbar** (nur Baudrate/Parität der RS485-Seite, s. `docs/Netzwerk-Topologie-Hene.md`). Sporadische `exception_code=11` lassen sich daher nur client-seitig kompensieren.
   * **Unschädlich:** Es werden **absolute** Zählerstände publiziert – ein doppelt gelesener Stand kann nichts verfälschen (keine Delta-Bildung auf dem Pi, FR-2).
   * **Ablauf:** zwischen zwei Versuchen **0,5 s** Pause; jeder Versuch baut die Verbindung neu auf. Fehlgeschlagene Zwischenversuche werden auf `INFO` geloggt (mit Versuchszähler), der endgültige Fehlschlag auf `WARN`.
   * **Als fehlgeschlagener Read gelten auch Exceptions der Modbus-Bibliothek** (`ModbusException`, z. B. `ModbusIOException` bei einer abgeschnittenen Antwort: „Unable to decode frame … byte_count 4 > length of packet 3“). Der Reader übersetzt sie in einen `ReadError`; der Retry greift wie bei einer Fehler-Response.
     > Es ist derselbe sporadische Fehler der RTU-Strecke, nur meldet ihn pymodbus als Exception statt als Fehler-Response. Beobachtet am Speicher („Batterie-Hene“).
   * **Kein Retry** bei unerwarteten Exceptions (Programmierfehler) – dort ist ein zweiter Versuch aussichtslos.
   * **Zeitbudget:** `read_timeout × read_attempts` (+ Pausen) je Zähler muss deutlich unter dem `publish_interval` bleiben; ungültige Angaben (`0`, negativ, nicht-ganzzahlig, Boolean) führen zu `ConfigError` **beim Start**. Der wirksame Wert steht in der Start-Log-Zeile.
7. **`register.einspeisung` optional (Nachtrag):** **Konsumenten** speisen nicht ein – dort ist der Block `register.einspeisung` **weglassbar**. Fehlt er, wird das Register **nicht gelesen** und im Payload **`zaehlerstandEinspeisung: 0`** publiziert.
   * **Nutzen:** ein Modbus-Zugriff weniger je Zyklus und Zähler – halbiert die Anfragen für reine Konsumenten und entlastet damit die RS485-Strecke (relevant angesichts der sporadischen `exception_code=11`, s. FR-1.6).
   * **Payload-Vertrag unverändert:** `zaehlerstandEinspeisung` bleibt ein **Pflichtfeld** (`MQTT-Integration.md`, FR-3) und wird mit `0` gefüllt – das Feld wird **nicht** weggelassen. Das Backend bleibt unberührt.
   * **`register.bezug` bleibt Pflicht**; ein **vorhandener, aber fehlerhafter** `einspeisung`-Block führt weiterhin zu `ConfigError` **beim Start** – ein Tippfehler (z.B. `adr` statt `addr`) darf nicht stillschweigend zu 0 werden.
   * **Sichtbarkeit:** Beim Start wird je betroffenem Zähler eine `INFO`-Zeile geloggt, damit ein *versehentlich* fehlender Block nicht unbemerkt 0 liefert.
   * Grund für den grosszügigen Default: An einem **RTU→TCP-Hub** wird die Anfrage seriell bis zum Slave durchgereicht und erst danach beantwortet — das dauert deutlich länger als bei direkt angebundenen TCP-Geräten.
   * **Abgrenzung:** Das Timeout wirkt nur, wenn der Hub **gar nicht** antwortet. Antwortet er mit einer **Modbus-Exception** (z.B. `code 11` = *Gateway target device failed to respond*), ist die Anfrage bereits beantwortet — ein höheres Client-Timeout ändert daran **nichts**; Ursache und Abhilfe liegen dann auf der RTU-Strecke bzw. in der Hub-Konfiguration (siehe `docs/Netzwerk-Topologie-Hene.md`, Abschnitt „Modbus-Diagnose"). Die Fehlermeldung des Readers unterscheidet beide Fälle und nennt den Timeout-Hinweis nur bei ausbleibender Antwort.

> **Manuelles Auslesen / Diagnose (CLI):** Zum Prüfen von Verkabelung/Registern
> eignet sich `mbpoll` (`sudo apt install mbpoll`). „Adresse 1" = Modbus
> **Unit-/Slave-ID** (die Geräte-**IP** ist bei Modbus TCP zusätzlich nötig).
> ```bash
> # Modbus TCP, Unit-ID 1, 10 Holding-Register ab Register 1, einmalig:
> mbpoll -a 1 -t 4 -r 1 -c 10 -1 <zaehler-ip> -p 502
> mbpoll -t 4:float -r 0x6016 -c 1 -1 192.168.10.164 -p 502 -a 16
> mbpoll -t 4:hex -r 0x4000 -c 2 -1 192.168.10.164 -p 502 -a 16 -0 # Seriennummer auslesen
> # 32-bit-Float-Werte (ggf. -B fuer Big-Endian-Words):
> mbpoll -a 1 -t 4:float -r 1 -c 4 -1 <zaehler-ip>
> # Modbus RTU (serielle Leitung) statt TCP:
> mbpoll -m rtu -a 1 -b 9600 -P none -t 4 -r 1 -c 10 -1 /dev/ttyUSB0
> ```
> `-a` Unit-ID, `-t 4`/`3` Holding/Input Register, `-r` Start, `-c` Anzahl,
> `-1` One-Shot. Konkrete Register/Skalierung stammen aus der Zähler-Doku
> (siehe Offene Frage „Register-Mapping Wago"). WhatWatt Go: zusätzlich per
> REST-API (`curl`, siehe Topologie-Doku). Alternativen: `mbtget`, kurzes
> `pymodbus`-Skript.

### FR-2: Zählerstände übertragen (keine Delta-Bildung auf dem Pi)
1. Die Software publiziert die **gelesenen absoluten Zählerstände** unverändert — **keine** Delta-Berechnung, **keine** Persistenz eines „letzten Standes" (Pi ist zustandslos).
2. **Reset/Überlauf/Zählertausch** werden **nicht** auf dem Pi behandelt; der Rohstand wird publiziert, das Backend erkennt einen Rücksprung bei der Differenzbildung (`MQTT-Integration.md`, FR-6).
3. Nur **unvollständige/fehlgeschlagene Reads** werden verworfen (nicht publiziert), damit keine Scheinwerte entstehen.

### FR-3: MQTT-Publish (Vertrag gemäss `MQTT-Integration.md`)
1. **Topic:** `zev/{orgId}/{messpunkt}/messwert` (exakt wie Backend-Subscription `zev/+/+/messwert`).
2. **Payload (JSON, absolute Stände):**
   ```json
   { "timestamp": "2026-06-19T14:30:00+02:00", "zaehlerstandBezug": 12345.678, "zaehlerstandEinspeisung": 4321.000,
     "seriennummer": "WAGO-8791234" }
   ```
   `timestamp` in **lokaler Zeit mit UTC-Offset (ISO 8601)** (z. B. `+02:00`; eindeutig, kein DST-Problem), Stände in kWh (kumulativ, ≥ 0). Voraussetzung: korrekte lokale Zeitzone + NTP auf dem Pi (FR-6.3).
   `seriennummer` ist **optional** und wird nur publiziert, wenn im jeweiligen `zaehler`-Eintrag konfiguriert (Zählertausch-Erkennung, s. FR-3.5).
   `zustand` ist ebenfalls **optional**: Momentanwerte wie der Ladezustand, gelesen aus den `zustand`-Registern des Eintrags (`Specs/Gerätezustand.md`); ohne solche Register fehlt das Feld, es wird **nie als leeres Objekt** gesendet (`MQTT-Integration.md`, FR-3).
3. QoS **0 oder 1** genügt (Verlusttoleranz durch absolute Stände); stabile Client-ID.
4. Die **Pflichtfelder** von Topic/Payload müssen exakt zum Backend-Vertrag passen; **additive optionale Felder** sind erlaubt (das Backend toleriert unbekannte Felder). Änderungen nur abgestimmt mit `MQTT-Integration.md`.
5. **Seriennummer (Zählertausch-Erkennung):** Je `zaehler`-Eintrag optional `seriennummer` (String, max. 64 Zeichen), einmalig am Gerät abgelesen. Der Publisher übernimmt den Wert unverändert in den Payload. **Beim Zählertausch ist dieser Wert zu aktualisieren – er ist das Signal**, an dem das Backend den Tausch erkennt (`Specs/Zaehlertausch-Erkennung.md`, Betrieb: `docs/Zaehlertausch.md`). Überlänge → Startfehler (`ConfigError`), damit ein Konfigurationsfehler sofort auffällt.

### FR-4: Übertragung & (optionale) Pufferung
1. Bei nicht erreichbarem Broker/VPN ist ein **Nachrichtenverlust unkritisch** — der nächste erfolgreich übertragene Stand schliesst die Lücke (Backend bildet die Differenz).
2. **Pufferung ist optional** (nur falls auch die *zeitliche Auflösung* während Ausfällen erhalten bleiben soll): entweder ein lokaler Mosquitto + Bridge (Variante C) oder eine einfache in-process/Disk-Queue. **Kein** zwingender Store-and-Forward.
3. Ohne Pufferung reicht ein direkter Publish an den NAS-Broker (Variante B).

### FR-5: Konfiguration
1. **Flexible Zähler-Liste:** Die Zähler werden als **Liste beliebiger Länge** konfiguriert — typisch **10–20 oder mehr**, keine feste Anzahl im Code. Jeder Eintrag beschreibt einen Zähler vollständig (Protokoll, Host/IP, Port, Unit-ID, Register-Mapping, `messpunkt`).
2. **Neuer Zähler = nur ein Konfigurations-Eintrag** (kein Code-Change, kein Rebuild).
3. Weitere Parameter über Konfigurationsdatei/Env: `org_id`, Broker-URL/Credentials, Lese-/Publish-Intervall, optionale Puffer-Optionen.
4. **Keine Secrets im Code/Repo** (Broker-Passwort etc. via Env/gesicherte Datei).

> **Beispiel-Konfiguration (Ausschnitt: 3 der 15 Wago-Zähler):**
> ```yaml
> # config.yaml auf dem Pi (Secrets via Env)
> org_id: 42
> publish_interval: 5m
> read_timeout: 5s                  # Lese-Timeout je Zähler (Default 5s), unten überschreibbar
> read_attempts: 2                  # Lese-Versuche inkl. Erstversuch (Default 2, 1 = kein Retry)
> broker:
>   url: tcp://nas.local:1883        # bzw. tls://nas.local:8883
>   username: ${MQTT_USERNAME}
>   password: ${MQTT_PASSWORD}
>   qos: 1
>
> # Beliebig viele Zähler — hier 3 Wago hinter EINEM Kommunikationsmodul WAGO 879-9000:
> # gleiche host/port (Modul-IP), unterschiedliche unit_id (Modbus-Slave-Adresse). Weitere anhängen.
> zaehler:
>   - messpunkt: "ID742-Wohnung-1"
>     seriennummer: "WAGO-8791234"    # optional; beim Zählertausch aktualisieren (FR-3.5)
>     protokoll: modbus-tcp
>     host: 192.168.10.164            # IP des Kommunikationsmoduls (für alle Wago gleich)
>     port: 502
>     unit_id: 1                      # Slave-Adresse dieses Zählers am Hub
>     # read_timeout: 8s              # optional: überschreibt den globalen Wert nur für diesen Zähler
>     register:                       # Wirkenergie in kWh (Beispielwerte!)
>       bezug:       { addr: 600C, typ: float32, wortfolge: big, skalierung: 1.0 }   # OBIS 1.8.0
>       einspeisung: { addr: 6018, typ: float32, wortfolge: big, skalierung: 1.0 }   # OBIS 2.8.0
>   # Konsument ohne Einspeisung: 'einspeisung' weglassen -> Register wird nicht gelesen,
>   # publiziert wird 0 (FR-1.7).
>   - messpunkt: "ID742-Wohnung-2"
>     protokoll: modbus-tcp
>     host: 192.168.10.164
>     port: 502
>     unit_id: 2
>     register:
>       bezug:       { addr: 600C, typ: float32, wortfolge: big, skalierung: 1.0 }
>   - messpunkt: "ID742-Allgemein"
>     protokoll: modbus-tcp
>     host: 192.168.10.164
>     port: 502
>     unit_id: 3
>     register:
>       bezug:       { addr: 600C, typ: float32, wortfolge: big, skalierung: 1.0 }
>       einspeisung: { addr: 6018, typ: float32, wortfolge: big, skalierung: 1.0 }
>
> # BKW (WhatWatt Go) und Batterie (Wechselrichter): weitere Einträge mit eigener host (FR-1.2).
> ```
> Die Wago hängen per seriellem Modbus (RTU) am **Kommunikationsmodul WAGO 879-9000** (siehe `docs/Netzwerk-Topologie-Hene.md`); daher **gleiche `host`/`port` (Modul-IP), verschiedene `unit_id`**. Die Register-`addr`/`typ`/`skalierung` sind **beispielhaft** und müssen dem Wago-Datenblatt entsprechen (Diagnose per `mbpoll`, siehe FR-1). Der Skalierungs-/OBIS-Bezug ist unter „Register-Mapping Wago" (Offene Fragen) beschrieben.

### FR-6: Betrieb & Resilienz
1. Läuft als **`systemd`-Service** mit Autostart nach Reboot/Stromausfall.
2. Automatischer **Reconnect** (Broker/Modbus) mit Backoff.
3. **NTP-Zeitsynchronisation** vorausgesetzt (korrekte Zeitstempel sind abrechnungskritisch).
4. Strukturiertes Logging mit Rotation; ein lokaler Puffer (falls genutzt) **nicht** auf die SD-Karte (Wear) — USB-SSD.

### FR-7: Monitoring / Heartbeat
1. „Letzter erfolgreicher Read"- und „letzter erfolgreicher Publish"-Zeitstempel exponieren (z.B. Logfile, lokaler Statusendpunkt oder eigenes MQTT-Status-Topic), damit stiller Ausfall erkennbar ist (vgl. `docs/Netzwerk-Topologie-Hene.md`, Punkt „Monitoring gegen stillen Ausfall").

### FR-8: Publisher-Simulator (Test ohne Hardware)
1. **`protokoll: sim`** erzeugt synthetische, monoton steigende **absolute** Zählerstände, statt einen Zähler zu lesen, und publiziert sie über den echten Publish-Pfad (FR-3). Damit lässt sich der ganze Weg `Simulator → Broker → Backend-Ingest → zaehler_rohdaten → Aggregation` ohne Pi und Hardware prüfen. Vorlage: `pi-gateway/config.sim.example.yaml`.
2. Der **Messpunkt-Name** bestimmt das Verhalten: `producer` (Einspeisung überwiegt), `bilanz` (beide Register), `bezug` bzw. `rücklieferung` (nur ein Register), `speicher`/`batterie` (wechselnde Lade- und Entladephasen samt Ladezustand `soc`), sonst Consumer. Einzelheiten: `pi-gateway/README.md`, Abschnitt „Publisher-Simulator“.
3. **gPlugK simulieren (`protokoll: sim-gplug`):** dieselben Modi, aber als Meldung eines gPlugK nach `Specs/MQTT-Integration.md`, FR-10:
   * Topic `gPlug/{org_id}/{messpunkt}/tele/SENSOR`, **QoS 0** (wie Tasmota).
   * Payload `{"Time": "<lokale Zeit ohne Offset, ohne Sekundenbruchteile>", "z": {...}}`. In `z`: `SMid` (nur mit konfigurierter `seriennummer`), `Pi`/`Po` (kW), `Ei`/`Eo` (kWh, 3 Nachkommastellen) und die Tarifregister `Ei1`/`Ei2`/`Eo1`/`Eo2` (alles im Hochtarif: `Ei1 = Ei`, `Ei2 = 0`).
   * `Pi`/`Po` sind die mittlere Leistung seit dem letzten Lesen; die erste Meldung trägt 0.
   * Kein `zustand` – ein gPlug kennt keinen Ladezustand, auch nicht bei einem Speicher-Namen.
   > Damit lässt sich der gPlug-Ingest des Backends prüfen, bevor ein echter gPlug angeschlossen ist, und später jederzeit ohne Gerät wieder. Echte gPlugs publizieren selbst (Abschnitt 7); `sim-gplug` ist nur im Simulator sinnvoll.
4. Simulator-Zähler (`sim`, `sim-gplug`) brauchen weder `host` noch `register`.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

* [ ] Der Dienst liest **alle konfigurierten Zähler** (beliebige Anzahl; bei Hene 15 Wago, BKW über WhatWatt und Batterie, alle Modbus TCP) im konfigurierten Intervall aus.
* [ ] Ein zusätzlicher Zähler lässt sich **allein per Konfigurations-Eintrag** ergänzen (kein Code-Change/Rebuild).
* [ ] Ohne `read_timeout` in der Config gilt der Default **5 s**; ein gesetzter Wert (Zahl oder `"8s"`/`"1m"`) wird für **alle** Zähler übernommen.
* [ ] `zaehler[].read_timeout` überschreibt den globalen Wert **nur für diesen Zähler**; die übrigen behalten `read_timeout`.
* [ ] Ungültige Timeout-Angaben (`0`, negativ, nicht-numerisch, Boolean) führen zu einem `ConfigError` **beim Start** — kein stiller Fallback auf den Default.
* [ ] Die **Start-Log-Zeile** nennt neben `org_id`, Zähleranzahl, Intervall und Broker auch das **wirksame globale `read_timeout`** (auch wenn es der Default ist).
* [ ] Sind für einzelne Zähler eigene `read_timeout`-Werte konfiguriert, werden **genau diese** Zähler beim Start zusätzlich mit ihrem Wert genannt; ohne Abweichung erscheint **keine** solche Zeile.
* [ ] Ein fehlgeschlagener Read wird gemäss `read_attempts` **wiederholt**; gelingt der zweite Versuch, wird der Messwert publiziert und der Zyklus zählt als Erfolg.
* [ ] Mit `read_attempts: 1` findet **kein** Wiederholversuch statt (bisheriges Verhalten); ohne Angabe gilt **2**.
* [x] Wirft die Modbus-Bibliothek beim Lesen eine Exception (z. B. `ModbusIOException` bei abgeschnittener Antwort), wird der Read wie ein Modbus-Fehler wiederholt; gelingt der zweite Versuch, wird der Stand publiziert (Test: test_modbus_reader.py › ModbusReaderTest › test_read_AbgeschnitteneAntwort_WirftReadError; test_modbus_reader.py › ReadWithRetryTest › test_readWithRetry_AbgeschnitteneAntwortDannErfolg_LiefertStand)
* [ ] Scheitern alle Versuche, wird der Zähler in diesem Zyklus übersprungen (`WARN` mit Anzahl der Versuche) – die übrigen Zähler laufen unbeeinflusst weiter.
* [ ] Ungültige `read_attempts` (`0`, negativ, nicht-ganzzahlig, Boolean, String) führen zu einem `ConfigError` **beim Start**.
* [ ] Die Start-Log-Zeile nennt die wirksame Anzahl **Lese-Versuche**.
* [ ] Fehlt `register.einspeisung`, wird dieses Register **nicht** gelesen (nur **ein** Modbus-Zugriff je Zyklus) und der Payload enthält `zaehlerstandEinspeisung: 0` – das Feld fehlt **nicht**.
* [ ] Ist `register.einspeisung` konfiguriert, verhält sich der Reader unverändert (zwei Zugriffe, echter Wert).
* [ ] Ein **vorhandener, aber fehlerhafter** `einspeisung`-Block (z.B. `addr` fehlt) führt zu `ConfigError` **beim Start**; ein fehlendes `register.bezug` ebenso.
* [ ] Für jeden Zähler ohne `einspeisung`-Register erscheint beim Start eine `INFO`-Zeile.
* [x] Bleibt eine Antwort aus, nennt die Fehlermeldung den konfigurierten Timeout-Wert und den Hinweis, `timeout` zu erhöhen; bei einer **Modbus-Exception-Response** (z.B. `code 11`) erscheint dieser Hinweis **nicht** (dort hilft das Timeout nicht). (Test: test_modbus_reader.py › TimeoutHinweisTest › test_read_KeineAntwort_NenntTimeoutUndHinweis, test_read_KeineAntwortVerbindungGeschlossen_NenntHinweis, test_read_ExceptionResponseCode11_OhneHinweis, test_read_AbgeschnitteneAntwort_OhneHinweis)
* [ ] Der Dienst publiziert die **absoluten Zählerstände** unverändert (keine Delta-Berechnung, keine „letzter Stand"-Persistenz).
* [ ] Publizierte Topic-/Payload-Struktur entspricht `MQTT-Integration.md`: Topic `zev/{orgId}/{messpunkt}/messwert`, Pflichtfelder `{timestamp,zaehlerstandBezug,zaehlerstandEinspeisung}` – plus die **optionalen** `seriennummer` und `zustand`, sofern konfiguriert.
* [ ] Ist im `zaehler`-Eintrag eine `seriennummer` gesetzt, erscheint sie unverändert im Payload; fehlt sie, wird das Feld **weggelassen** (Payload identisch zum bisherigen Vertrag).
* [ ] Bei Broker-/VPN-Ausfall entsteht **kein Datenverlust** in der Gesamtsumme (nächster übertragener Stand schliesst die Lücke); optionale Pufferung erhält zusätzlich die Auflösung.
* [ ] Unvollständige/fehlgeschlagene Reads werden verworfen (nicht publiziert).
* [ ] Dienst startet nach Reboot automatisch (`systemd`) und verbindet sich selbstständig neu.
* [ ] Zeitstempel sind lokale Zeit mit korrektem UTC-Offset (NTP + Zeitzone); driftende Uhr wird als Problem erkennbar.
* [ ] Keine Secrets im Code/Repo; Broker-Zugangsdaten via Env/Config.
* [ ] „Letzter erfolgreicher Read/Publish" ist auslesbar (Heartbeat).
* [x] `protokoll: sim-gplug` wird ohne `host` und `register` akzeptiert und erzeugt einen Simulator-Reader (FR-8.3, FR-8.4). (Test: test_sim_gplug.py › SimGplugConfigTest › test_loadConfig_SimGplugOhneHostUndRegister_Gueltig; test_sim_gplug.py › SimGplugReaderTest › test_factory_SimGplug_ErzeugtSimReader)
* [x] Eine `sim-gplug`-Messung wird auf `gPlug/{org_id}/{messpunkt}/tele/SENSOR` mit QoS 0 publiziert; `sim`- und Modbus-Zähler publizieren unverändert auf `zev/…/messwert` mit der QoS des Brokers. (Test: test_sim_gplug.py › GplugPublishTest › test_publish_Gplug_TopicSensorUndQos0, test_publish_Zev_UnveraendertTopicMesswertUndBrokerQos; test_sim_gplug.py › SimGplugReaderTest › test_read_Sim_MeldungBleibtZev)
* [x] Die gPlug-Meldung trägt `Time` als lokale Zeit ohne Offset und ohne Sekundenbruchteile, `z.Ei`/`z.Eo` in kWh mit 3 Nachkommastellen, `z.SMid` nur mit konfigurierter Seriennummer, dazu `Pi`/`Po` und Tarifregister, deren Summe `Ei` bzw. `Eo` ergibt. (Test: test_sim_gplug.py › GplugPayloadTest › test_payload_TimeLokalOhneOffsetOhneMikrosekunden, test_payload_ZaehlerstaendeInKwhMitDreiStellen, test_payload_TarifregisterSummierenSich, test_payload_OhneSeriennummer_KeinSMid)
* [x] `Pi`/`Po` sind die mittlere Leistung seit dem letzten Lesen, die erste Meldung trägt 0; ein `sim-gplug`-Zähler meldet keinen Ladezustand. (Test: test_sim_gplug.py › SimGplugReaderTest › test_read_ErsteMeldung_LeistungNull, test_read_FolgeMeldung_LeistungAusZuwachs, test_read_SimGplug_MeldungGplugOhneZustand)

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance / Ressourcen
* Läuft schlank auf einem Raspberry Pi (arm64); ein Lesezyklus über alle Zähler deutlich innerhalb des Intervalls.

### NFR-2: Sicherheit & Mandantentrennung
* **Keine ZEV-App-Rollen** (`zev_user`/`zev_admin`) — dies ist Maschinen-zu-Broker-Kommunikation. Authentisierung am **MQTT-Broker** (Username/Passwort); **ACL** stellt sicher, dass der Pi nur in den Topic-Teilbaum seiner **`org_id`** publizieren darf (Mandantentrennung, konsistent zu `MQTT-Integration.md`).
* **TLS (Port 8883)** empfohlen, zusätzlich zum **VPN**-Tunnel (Defense-in-Depth).
* Broker-Ports nur über den VPN-Pfad erreichbar; keine Inbound-Exposition am Zählerstandort.
* Zugriff auf das lokale Netzsegment der Zähler minimieren (Modbus TCP ist unverschlüsselt/ohne Auth — vgl. Segmentierungs-Hinweis der Topologie-Doku).

### NFR-3: Zuverlässigkeit
* **Verlusttoleranz durch absolute Zählerstände** — eine verlorene Nachricht führt nicht zu Datenverlust; daher genügt QoS 0/1 und **keine** zwingende Pufferung.
* Duplikate sind backend-seitig idempotent (Unique `einheit_id`+`zeit`).
* Selbstheilung: Reconnect für Broker und Modbus.

### NFR-4: Kompatibilität
* Payload/Topic-Vertrag mit `MQTT-Integration.md` bleibt stabil; Änderungen nur koordiniert.
* Läuft auf Raspberry Pi OS (arm64); Deployment ohne Build auf dem Pi bevorzugt (siehe Sprachwahl, Offene Fragen).

## 5. Edge Cases & Fehlerbehandlung
| Szenario | Verhalten |
|----------|-----------|
| Zähler (Modbus) nicht erreichbar | Loggen, diesen Zyklus überspringen, Retry (nächster Stand schliesst die Lücke) |
| Teilweiser/fehlgeschlagener Read | Messwert dieser Quelle verwerfen, nicht publizieren |
| Abgeschnittene Antwort (`ModbusIOException` der Bibliothek) | Wie ein fehlgeschlagener Read: Retry gemäss `read_attempts`, danach überspringen (FR-1.6) |
| Zählerüberlauf / Rücksprung / Zählerwechsel | Rohstand wird publiziert; Erkennung/Behandlung im **Backend** (Differenz < 0) |
| Broker/VPN nicht erreichbar | Verlust unkritisch; optional puffern (Variante C / Queue) für Auflösung |
| Puffer voll (falls Pufferung aktiv) | Definiertes Verhalten (z.B. älteste verwerfen + WARN) |
| Uhr driftet / kein NTP | WARN; Zeitstempel bleiben lokale Zeit mit Offset; Betreiber-Alarm |
| Doppelter Publish (nach Reconnect) | Backend behandelt idempotent (Unique-Constraint) |
| Prozessabsturz/Neustart | `systemd` startet neu; kein „letzter Stand" nötig (zustandslos) |
| `register.einspeisung` fehlt (Konsument) | Register wird **nicht gelesen**, `zaehlerstandEinspeisung: 0` publiziert; `INFO` beim Start (FR-1.7) |
| `register.einspeisung` **nachträglich entfernt**, obwohl der Zähler bisher echte Werte lieferte | Der Stand fällt einmalig auf 0 → das Backend sieht ein **negatives Delta** und nullt es per Reset-Guard (`MQTT-Integration.md`). Kein Datenschaden, aber die Einspeisung dieses Intervalls fehlt – nur bei Zählern entfernen, die tatsächlich nicht einspeisen |

## 6. Abhängigkeiten & betroffene Funktionalität
* **Vertrag/Backend:** `Specs/MQTT-Integration.md` (Topic, Payload, QoS, Delta-Bildung/Aggregation) — **maßgeblich**.
* **Topologie/Broker:** `docs/Netzwerk-Topologie-Hene.md` (Broker-Setup NAS/Pi, Varianten A/B/C, VPN, SD-Wear, Monitoring).
* **Hardware/Protokolle:** Wago über Kommunikationsmodul WAGO 879-9000, BKW-Zähler über WhatWatt Go, Hybrid-Wechselrichter (alle Modbus TCP), Mosquitto-Broker, VPN (WireGuard).
* **Voraussetzungen:** Einheiten inkl. `messpunkt` sind im ZEV-System angelegt; `org_id` bekannt; VPN eingerichtet.
* **Kein** ZEV-App-Code betroffen (separates Pi-Artefakt); keine DB-/i18n-/Frontend-Änderung.

## 7. Abgrenzung / Out of Scope
* **Delta-/Intervall-Bildung und Aggregation** → **Backend** (`MQTT-Integration.md`, FR-6). Der Pi liefert nur absolute Stände.
* **Backend-Ingestion** (MQTT-Subscriber, Rohdaten-Tabelle) → `MQTT-Integration.md`.
* **Broker-Installation/Betrieb** (NAS/Pi) → `docs/Netzwerk-Topologie-Hene.md`.
* **VPN-Einrichtung**, Netzwerk-Segmentierung der Zähler.
* **Bidirektionale Steuerung** (Befehle an Zähler), automatische Geräteregistrierung.
* **Keine UI**; Konfiguration per Datei/Env.
* **gPlugK im Nachbarhaus:** publizieren selbst (`Specs/MQTT-Integration.md`, FR-10); die Mosquitto-Bridge auf dem Pi, die ihre Meldungen weiterleitet, ist Betrieb (`docs/Netzwerk-Topologie-Hene.md`).

## 8. Offene Fragen
* [ ] **Technologie/Sprache:** Empfehlung **Python** (`pymodbus`, `paho-mqtt`, HTTP-Client) für schnelle Umsetzung; **Go** als Alternative (ein statisches arm64-Binary, minimaler Footprint, einfachstes Deployment). Java auf dem Pi eher nicht (JVM-Overhead). → Python
* [ ] **gPlug-Schnittstelle (BKW):** hinfällig – der BKW-Zähler wird über WhatWatt Go per Modbus TCP gelesen, die gPlugs im Nachbarhaus publizieren selbst (Anhang B.1).
* [ ] **Optionale Pufferung:** überhaupt nötig (nur für Auflösungserhalt bei Ausfällen)? Falls ja: Variante C (lokaler Broker + Bridge) oder in-process Queue? → keine Pufferung
* [ ] **Welche Register/Stände** werden publiziert (Bezug/Einspeisung, ggf. ZEV-Register) und wie im Backend auf `total`/`zev` gemappt? (Spiegelt Offene Frage in `MQTT-Integration.md`.) → **Wirkenergie** (Active Energy, kumulativ in kWh): **Bezug = OBIS 1.8.0** → `zaehlerstandBezug`, **Einspeisung = OBIS 2.8.0** → `zaehlerstandEinspeisung`. **Keine** Wirk-/Blind-/Scheinleistung (kW/kvar/kVA) und keine Blind-/Scheinenergie. Das Mapping der Stände auf `messwerte` ist entschieden (`MQTT-Integration.md` FR-6.4): `total = ΔBezug − ΔEinspeisung` (vorzeichenbehaftet), `zev = 0` (Sentinel), `zev_calculated` via Solarverteilung. Für den Pi ändert sich dadurch **nichts** — er publiziert weiterhin nur die absoluten Bezug-/Einspeisung-Stände.
* [ ] **`org_id` im Topic:** internes `org_id` (BIGINT) vs. Keycloak-Alias/UUID — konsistent zu `MQTT-Integration.md` festlegen. → interne org_id
* [ ] **Register-Mapping Wago:** konkrete Modbus-Register/Skalierung je Zählertyp; `messpunkt`↔Gerät-Zuordnung. → Semantik geklärt: **Wirkenergie kWh** (OBIS 1.8.0 Bezug / 2.8.0 Einspeisung, s. o.). Die **konkreten Modbus-Registeradressen + Skalierung** je Wago-Typ stammen aus dem Gerätedatenblatt (Diagnose per `mbpoll`); `messpunkt`↔Gerät-Zuordnung per Config.
* [ ] **Publish-Intervall:** wie oft absolute Stände senden (bestimmt die erreichbare zeitliche Auflösung)? → konfigurierbar. Wir beginnen mit 5 Minuten.

## Anhang A: systemd-Registrierung (Raspberry Pi OS)

Anleitung, um das Python-Skript als `systemd`-Dienst mit Autostart zu betreiben
(erfüllt FR-6.1/6.2). Annahmen: Raspberry Pi OS (arm64, systemd), Code liegt in
`/opt/pi-gateway`, Ausführung als dedizierter, unprivilegierter Nutzer in einem
virtuellen Environment (venv). Secrets kommen **nicht** ins Unit-File, sondern in
eine root-only Env-Datei (FR-5.4).

### A.1 Dienst-Nutzer und Verzeichnisse anlegen

```bash
# Dedizierter Systemnutzer ohne Login und ohne Home
sudo useradd --system --no-create-home --shell /usr/sbin/nologin pigw

# Code- und Konfig-Verzeichnis
sudo mkdir -p /opt/pi-gateway
sudo chown -R pigw:pigw /opt/pi-gateway
# ... Code nach /opt/pi-gateway kopieren (git clone / rsync / Release-Tarball) ...
```

### A.2 Virtuelles Environment und Abhängigkeiten

```bash
sudo -u pigw python3 -m venv /opt/pi-gateway/.venv
sudo -u pigw /opt/pi-gateway/.venv/bin/pip install --upgrade pip
sudo -u pigw /opt/pi-gateway/.venv/bin/pip install -r /opt/pi-gateway/requirements.txt
```

### A.3 Konfiguration und Secrets

```bash
# Anwendungs-Konfiguration (ohne Secrets, siehe FR-5)
sudo install -o pigw -g pigw -m 640 config.yaml /opt/pi-gateway/config.yaml

# Broker-Zugangsdaten als root-only Env-Datei (NICHT ins Unit-File!)
sudo tee /etc/pi-gateway.env >/dev/null <<'EOF'
MQTT_USERNAME=pi-org42
MQTT_PASSWORD=<broker-passwort>
EOF
sudo chmod 600 /etc/pi-gateway.env
sudo chown root:root /etc/pi-gateway.env
```

### A.4 systemd-Unit anlegen

Datei `/etc/systemd/system/pi-gateway.service` (im Repo als `deploy/pi-gateway.service` versioniert):

```ini
[Unit]
Description=ZEV Pi-Gateway (Zähler auslesen und per MQTT publizieren)
Documentation=https://github.com/ursnacht/ZEV/blob/main/Specs/Pi-Gateway-Software.md
# Netzwerk (und ggf. VPN) muss vor dem Start verfügbar sein:
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=pigw
Group=pigw
WorkingDirectory=/opt/pi-gateway
# Secrets aus root-only Env-Datei laden:
EnvironmentFile=/etc/pi-gateway.env
ExecStart=/opt/pi-gateway/.venv/bin/python -m gateway.main --config /opt/pi-gateway/config.yaml
# Automatischer Neustart nach Absturz/Reboot (FR-6.1/6.2):
Restart=always
RestartSec=10
# Logs an journald (kein eigenes Logfile-Management nötig):
StandardOutput=journal
StandardError=journal
SyslogIdentifier=pi-gateway

# Härtung (optional, empfohlen):
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
PrivateTmp=true
ReadWritePaths=/opt/pi-gateway

[Install]
WantedBy=multi-user.target
```

> **Hinweis Pufferung/USB-SSD:** Wird später doch eine lokale Puffer-/Statusdatei
> genutzt, deren Pfad (auf USB-SSD, **nicht** SD-Karte — FR-6.4) unter
> `ReadWritePaths=` ergänzen.

### A.5 Aktivieren, starten, prüfen

```bash
# Unit-Datei aus dem Repo installieren (oder direkt anlegen)
sudo install -m 644 deploy/pi-gateway.service /etc/systemd/system/pi-gateway.service

sudo systemctl daemon-reload
sudo systemctl enable pi-gateway.service     # Autostart nach Reboot
sudo systemctl start  pi-gateway.service     # sofort starten

# Status und Logs
systemctl status pi-gateway.service
journalctl -u pi-gateway.service -f          # Live-Log (Ctrl+C beendet)
journalctl -u pi-gateway.service --since "1 hour ago"
```

### A.6 Update / Neustart / Deaktivieren

```bash
# Nach Code-Update:
sudo systemctl restart pi-gateway.service

# Nach Änderung der Unit-Datei zusätzlich:
sudo systemctl daemon-reload && sudo systemctl restart pi-gateway.service

# Dienst dauerhaft abschalten:
sudo systemctl disable --now pi-gateway.service
```

### Updaten
```bash
# 0) Dienst stoppen (Datei nicht "unter den Füssen" austauschen)
sudo systemctl stop pi-gateway.service

# 1) ZIP nach /tmp entpacken (ergibt /tmp/pi-gw-update/pi-gateway/...)
cd /home/pi
unzip -o zev-pi-gateway.zip -d /tmp/pi-gw-update

# 2) Code nach /opt/pi-gateway kopieren.
#    Plain cp -> vorhandene config.yaml/.venv bleiben erhalten (nicht im ZIP, werden nicht überschrieben).
sudo cp -r /tmp/pi-gw-update/pi-gateway/. /opt/pi-gateway/

# 3) Eigentümer korrigieren (cp hat als root kopiert)
sudo chown -R pigw:pigw /opt/pi-gateway

# 4) Abhängigkeiten im venv aktualisieren  <-- WICHTIG bei pymodbus-Fix!
#    requirements.txt ist jetzt <3.10 -> pip stellt die kompatible Version sicher.
sudo -u pigw /opt/pi-gateway/.venv/bin/pip install -r /opt/pi-gateway/requirements.txt

# 5) Starten + Logs prüfen
sudo systemctl start pi-gateway.service
journalctl -u pi-gateway.service -f
```


> **Zeitsynchronisation & Zeitzone (FR-6.3):** Korrekte Zeitstempel sind
> abrechnungskritisch. Sicherstellen, dass NTP läuft **und die lokale Zeitzone gesetzt ist**
> (`timedatectl status` → „System clock synchronized: yes“ und korrekte „Time zone“;
> ggf. `sudo timedatectl set-ntp true` bzw. `sudo timedatectl set-timezone Europe/Zurich`).
> Der publizierte Zeitstempel trägt den lokalen UTC-Offset.

## Anhang B – Verlauf

### B.1 Topologie Hene bereinigt (Abschnitt 1, FR-1, FR-5, 20.10.2026)
* Abschnitt 1 („Aktueller Stand“) lautete: „Der Pi liest die Zähler physisch bereits aus (**3× Wago via Modbus TCP**, **1× BKW via gPlug**) und stellt Daten heute per **SFTP** bereit“, „Es existiert **noch keine** MQTT-Publisher-Software“ und „Ziel-Topologie: Broker auf dem NAS (Variante B)“. Stand laut User: 15 Wago, BKW-Bilanz über WhatWatt Go, Batterie per Modbus am Wechselrichter; SFTP durch MQTT abgelöst.
* FR-1.2 lautete: „**BKW via gPlug:** Auslesen über die gPlug-Schnittstelle (Protokoll siehe Offene Fragen).“ Die offene Frage dazu: „**gPlug-Schnittstelle (BKW):** Modbus, HTTP/REST oder proprietär? Bestimmt Libs und Aufwand. → damit noch warten. Kommt später als Erweiterung.“ Der BKW-Zähler hängt inzwischen an einem WhatWatt Go (Modbus TCP, ein gewöhnlicher `modbus-tcp`-Eintrag). Die gPlugK im Nachbarhaus publizieren selbst und brauchen keinen Reader (`Specs/MQTT-Integration.md`, FR-10). Ein gPlug-Reader in dieser Software entfällt damit.
* Das Beispiel in FR-5 hiess „3 Wago-Zähler; ohne gPlug — folgt später“, nannte den „Modbus-TCP-Hub“ mit `host: 192.168.10.10` und endete mit „Später (Erweiterung): BKW als weiterer Eintrag mit `protokoll: gplug`“. Laut Geräte-Inventar der Topologie-Doku ist die Modul-IP `192.168.10.164`.
* FR-6.2, NFR-3, das erste Akzeptanzkriterium, der Edge Case „Zähler nicht erreichbar“ und Abschnitt 6 nannten gPlug als Protokoll dieser Software.

### B.2 Feld `zustand` im Payload nachgetragen (FR-3, 20.10.2026)
FR-3.2 nannte nur `seriennummer` als optionales Feld, das Akzeptanzkriterium zur Topic-/Payload-Struktur lautete „… – plus das **optionale** `seriennummer`, sofern konfiguriert.“ Das Feld `zustand` kam mit `Specs/Gerätezustand.md` in Konfiguration und Publisher dazu (`gateway/config.py`, `gateway/publisher.py`) und im Vertrag `MQTT-Integration.md`, FR-3; diese Spec war nicht nachgeführt. Der Checkbox-Status des Kriteriums bleibt.

### B.3 Simulator in die Spec aufgenommen, gPlug-Modus (FR-8, 22.10.2026)
Der Publisher-Simulator (`protokoll: sim`) war bisher nur in `pi-gateway/README.md` beschrieben. Mit dem gPlug-Modus (`protokoll: sim-gplug`) ist er als FR-8 in die Spec aufgenommen: Der User wollte das Auslesen und Speichern von gPlug-Meldungen testen können, bevor ein echter gPlug angeschlossen und eine echte Meldung mitgeschnitten ist (`Specs/MQTT-Integration.md`, FR-10, Anhang A.9). FR-8.1 und FR-8.2 beschreiben das bestehende Verhalten.

### B.4 Timeout-Hinweis bei ausbleibender Antwort (FR-1.5, 22.10.2026)
Beim Akzeptanzkriterien-Check vom 22.10.2026 fiel auf: Der Hinweis „keine Antwort innerhalb … – ggf. `read_timeout` erhöhen“ stand nur im Zweig für eine **Fehler-Response** (`response.isError()`). pymodbus 3.8 meldet eine ausbleibende Antwort aber nicht mehr als Response, sondern **wirft** eine `ModbusIOException` („No response received after 3 retries …“). Sie landete im Zweig für Exceptions der Bibliothek (FR-1.6) – ohne Hinweis. Bei einem echten Timeout erschien er so nie. Seither nennt auch dieser Zweig den Hinweis, wenn die Exception eine ausbleibende Antwort meldet; eine abgeschnittene Antwort und eine Exception-Response (code 11) bleiben ohne Hinweis. Die Exception-Response wird am Typ (`ExceptionResponse`) erkannt statt am Text von `str(response)`.

