# Solinteg MHT — Modbus-Register

**Referenznotiz, keine Spec.** Sie hält fest, was über die Modbus-Schnittstelle des Wechselrichters
bekannt ist — die Register, die das Gateway heute liest, und die, mit denen sich Batterieladung und
Einspeisung sperren liessen. **Umgesetzt ist davon nur das Lesen.**

## Quelle und Geltung

| | |
|---|---|
| Dokument | Solinteg Hybrid Inverter MODBUS RTU Protocol, Version 00.02 |
| Fundstelle | <https://eshop.helion.cz/user/related_files/solinteg_modbus_protocol_mht-25-50.pdf> |
| Modelle | MHT-25K-100 … MHT-50K-100 — die Anlage (**MHT-30K-100**) steht in der Modellliste |
| Anschluss hier | Modbus **TCP**, Port 502, Unit-ID 255 |

> **Das Dokument beschreibt Modbus RTU, gelesen wird über TCP.** Die Registeradressen sind dieselben,
> die Rahmung ist es nicht. Funktionscodes: `0x03` lesen, `0x06` ein Register schreiben,
> `0x10` mehrere schreiben.

> **Registeradressen stehen im Dokument dezimal, in `pi-gateway/config.example.yaml` hexadezimal.**
> 31108 = `0x7984`, 31110 = `0x7986`, 33000 = `0x80E8`. Ein Zahlendreher hier liefert stumm
> plausible Zahlen aus einem anderen Register — beim Übertragen umrechnen, nicht abschreiben.

## Was heute gelesen wird

| Register | Bedeutung | Typ | Einheit | Faktor |
|---|---|---|---|---|
| 31108/31109 | Total Battery **Charging** Energy | U32 | kWh | 10 |
| 31110/31111 | Total Battery **Discharging** Energy | U32 | kWh | 10 |
| 33000 | **SOC** | U16 | % | 100 |

Konfiguriert in der Zähler-Liste des Gateways (`register.bezug` = Ladung, `register.einspeisung` =
Entladung, `zustand.soc`), `skalierung: 0.1` bzw. `0.01`.

### Warum Ladung und Entladung nur 0.1-kWh-Schritte zeigen

Der **Faktor 10** ist die Auflösung des Geräts: Die kumulative Energie steht als ganze Zahl in
Zehntel-Kilowattstunden. Jeder Zählerstand ist damit ein Vielfaches von 0.1 kWh, und die Differenz
zweier Stände — also die Menge eines 15-Minuten-Intervalls — ebenfalls. Bei einer Viertelstunde
entspricht das einer Auflösung von 0.4 kW.

Das ist **keine** Rundung in unserem Code: Das Gateway rundet auf vier Stellen
(`publisher.py`, `_KWH_DECIMALS = 4`), die Spalte ist `NUMERIC(12,3)`, die Anzeige formatiert mit
`toFixed(3)`.

**Ein feineres Energieregister gibt es nicht** — auch die Tageswerte (31003 Daily Battery Charging,
31004 Daily Battery Discharging) tragen Faktor 10. Feiner ginge nur über die **Momentanleistung**:

| Register | Bedeutung | Typ | Einheit | Faktor |
|---|---|---|---|---|
| 30258/30259 | `Battery_P` — Batterieleistung | **I32** | kW | **1000** |

Das ist Watt-Auflösung, vorzeichenbehaftet. Daraus eine Energiemenge zu gewinnen hiesse aber, über
die Zeit zu **integrieren** — ein anderes Messprinzip als das Zählerdifferenz-Verfahren, auf dem
die ganze Anwendung beruht, und anfällig für jede ausgefallene Abfrage. Nicht empfohlen, nur der
Vollständigkeit halber notiert.

### Weitere lesbare Grössen (nicht genutzt)

33001 SOH (U16, %, ×100) — der nächste Kandidat für `Zustandsgroesse`, siehe `Specs/Gerätezustand.md`.
33002 BMS Status, 33003 BMS Pack Temperature.

## Wie sich sperren liesse

**Nicht umgesetzt.** Die Einspeisesteuerung läuft im Trockenlauf; einen Schreibpfad zur Anlage gibt
es nicht (`Specs/Einspeisesteuerung.md`, §7). Diese Notiz sagt nur, was möglich wäre.

### Weg A — EMS_BattCtrlMode

Zuerst die Betriebsart, **Register 50000** (U16, als High-/Low-Byte gelesen):

| Wert | Modus | gültige Register |
|---|---|---|
| `0x0101` | General Mode (Werkseinstellung) | — |
| `0x0102` | Economic Mode | 53006–53048 (Weg B) |
| `0x0301` | EMS_ACCtrlMode | 50202–50206 |
| `0x0303` | **EMS_BattCtrlMode** | **50207–50211** |

Dann die beiden Sperren über zwei **unabhängige** Register:

| Register | Bedeutung | Typ | Einheit | Faktor | Sperre |
|---|---|---|---|---|---|
| **50207** | Battery Power Scheduling (P_bat) | I16 | kW | 100 | `0` → Batterie lädt und entlädt nicht |
| **50208** | AC Power UP Setting (P_upLimit) | I16 | kW | 100 | `0` → keine Einspeisung ins Netz |
| 50209 | AC Power Lower Setting | I16 | kW | 100 | Untergrenze = höchster Netzbezug (negativ) |
| 50210 | Priority of Power Output | U16 | — | 1 | 0 = PV zuerst, 1 = Batterie zuerst |

**Vorzeichen** (Appendix des Dokuments): `P_bat < 0` = laden, `P_bat > 0` = entladen;
`P_inv > 0` = Einspeisung, `P_inv < 0` = Netzbezug. **Skalierung:** 1000 W → Registerwert `100`.

Das trifft die zwei Zustände, die die Steuerung ohnehin führt: `batterieladung` auf 50207,
`einspeisung` auf 50208, getrennt schaltbar.

### Weg B — Economic Mode mit Zeitfenstern

`50000 = 0x0102`, dann bis zu **sechs** Perioden ab 53006:

| Register | Bedeutung |
|---|---|
| 53006 | Period Enable Flag — Bit 0–5 für Periode 1–6 |
| 53007 | 0 = NONE, 1 = charge, 2 = discharge |
| 53008 | Battery Charge By: 0 = nur PV, 1 = PV + Netz |
| 53010 | Power Limit, 0.0–100.0 % (Faktor 1000) |
| 53012 / 53013 | Start-/Stoppzeit — High-Byte Stunde, Low-Byte Minute |

Periode 2–6 liegen auf 53014ff., 53021ff., 53028ff., 53035ff., 53042ff. mit gleichem Aufbau.

**Sechs Perioden gegen 96 Viertelstunden** — das klingt zu wenig, ist es aber oft nicht: Die
Blockbildung dafür existiert schon. `bloecke()` in `einspeisesteuerung.component.ts` fasst
zusammenhängende gesperrte Intervalle zu Blöcken zusammen, und an einem typischen Tag sind das
wenige. Der eigentliche Vorteil: **Der Wechselrichter führt den Plan selbst aus** und ist nicht
darauf angewiesen, dass der Pi lebt.

### Weg C — BMS-Bitfeld (nicht gangbar)

`53508` Bit 10 (**Charge Command**, 1 = Enable, 0 = Disable) neben Bit 8 (On-grid Discharge) wäre
genau die gesuchte Semantik: Laden verbieten, Entladen erlauben. Der ganze Block 53500–53523 trägt
im Datenblatt aber den Vermerk **„Only for BMS access to EMS“** — diese Register schreibt ein
externes BMS an das EMS. Bei einer Batterie mit eigenem BMS am Wechselrichter ist das kein Weg.

## Welcher Weg trägt die Zustände der Steuerung

„Ladung gesperrt“ soll nur das **Laden** verhindern — das Entladen muss weiterlaufen, sonst deckt
die Batterie den Hausbedarf nicht mehr. **Einen Schalter dafür gibt es nicht:** Das Protokoll kennt
weder ein Ladeverbot noch eine SOC-**Obergrenze** (52502–52505 sind untere Entladegrenzen).

**Weg A trifft zu viel.** `50207 = 0` legt die Batterie still, also auch das Entladen. Bei
`WARTEN_AUF_TAL` ist das doppelt teuer: Der Hausbedarf käme aus dem Netz, zum hohen Preis, den die
Regel gerade vermeiden will. Nur das Laden zu sperren hiesse, `50207` laufend auf den aktuellen
Hausbedarf nachzuführen — die Anwendung würde damit zum echten EMS, mit der Watchdog-Pflicht aus
Appendix 2 und einem Pi, an dem der Betrieb hängt.

**Weg B trifft es.** Ein Zeitfenster mit `53007 = 2` (discharge) lässt in der Sperrzeit entladen
statt laden — bei hohem Preis ohnehin das Gewünschte.

### Warum ein Fahrplan genügt

**Jede Sperre der Regel ist eine reine Preisentscheidung** (`Specs/Einspeisesteuerung.md`, FR-2):
`PREIS_NEGATIV`, `EINSPEISEN_LOHNT` und `WARTEN_AUF_TAL` werten nur Preise aus. Die beiden
übrigen Regeln brauchen Messwerte, setzen aber **keine** Sperre.

Der Schaltplan eines Tages steht damit fest, sobald die Preise vorliegen — es braucht keine
Reaktion auf Messwerte und also kein Schalten im 15-Minuten-Takt. Die Steuerung schriebe einmal
täglich einen **Fahrplan**. Der Gewinn ist nicht Bequemlichkeit, sondern das Ausfallverhalten:
**Der Wechselrichter führt den Plan selbst aus.** Fällt der Pi aus, läuft die Anlage weiter,
statt in einem Sperrzustand oder im Schutz zu hängen.

### Offen an Weg B

* **Was `discharge` in einer Periode genau bewirkt** — Entladung nur bis zum Hausbedarf oder auch
  ins Netz? Das Datenblatt sagt es nicht. `53010 Power Limit` begrenzt die Leistung, nicht das Ziel.
* **Ob sechs Perioden reichen.** An einem Tag mit zerstückelten Preisen könnten mehr Blöcke
  entstehen; dann müssten benachbarte zusammengefasst oder die kürzesten weggelassen werden.
* **Die Einspeisesperre** (`PREIS_NEGATIV`) deckt Weg B **nicht** ab: Die Zeitfenster steuern die
  Batterie, nicht die Netzeinspeisung. Dafür bliebe `50208` im EMS-Modus oder ein Export-Limit —
  womit ein Teil der Steuerung doch wieder am laufenden Betrieb des Pi hängt.

## Was vor einer Umsetzung zu klären wäre

**Der Watchdog.** Appendix 2 des Protokolls: Sind Smart Meter (25104–25110) und BMS (53500–53523)
über das EMS angebunden und das EMS schreibt die Daten nicht alle 1–30 s, löst der Wechselrichter
Schutz aus oder fällt auf Vorgabewerte zurück. Ein EMS-Modus ist also nichts zum
Einstellen-und-Vergessen. Ob das für diese Anlage gilt, hängt davon ab, wie Zähler und BMS
angeschlossen sind — das steht nicht im Protokoll.

**Der sichere Rückfall.** Fällt der Pi aus, während `50208 = 0` steht, speist die Anlage nicht mehr
ein, bis jemand es bemerkt. Weg B hat dieses Problem nicht: Der Plan liegt im Gerät.

**Schreiben ist etwas anderes als Lesen.** Bis heute liest das Gateway nur. Ein Schreibpfad zur
Anlage berührt Netzanschlussbedingungen und womöglich die Gewährleistung — eine Entscheidung, keine
Umsetzungsfrage.

**Die Ansteuerung wäre nicht mehr rückrechenbar.** Der Reiz des Trockenlaufs ist, dass sich jeder
Schwellwert nachträglich erproben lässt, weil nichts geschaltet wurde. Sobald geschaltet wird,
verändert die Steuerung die Messwerte, aus denen sie selbst gespeist wird.
