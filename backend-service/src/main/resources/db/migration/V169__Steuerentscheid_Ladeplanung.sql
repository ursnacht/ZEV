-- Ladeplanung: Die Plangroessen am Steuerentscheid (Specs/Ladeplanung.md, FR-5).
--
-- ERSTE STUFE IST DIE SCHATTENRECHNUNG (FR-1a): Die Regelkaskade entscheidet weiter, die
-- Merit-Order rechnet mit und ihr Ergebnis steht in ladeplan_batterieladung. Deshalb traegt
-- verfahren vorerst durchgehend REGEL.
--
-- ALLE SPALTEN NULLABLE: Sie fehlen bei jedem Entscheid aus der Zeit davor und bei jedem
-- Rueckfall. Ein NOT NULL waere hier nicht Strenge, sondern eine Luege ueber die Vergangenheit.

ALTER TABLE zev.steuerentscheid
    ADD COLUMN verfahren               VARCHAR(20),
    ADD COLUMN ladeplan_batterieladung VARCHAR(20),
    ADD COLUMN prognose_ueberschuss    NUMERIC(12,3),
    ADD COLUMN gti                     NUMERIC(8,2),
    ADD COLUMN prognose_faktor         NUMERIC(12,8),
    ADD COLUMN rang                    INTEGER,
    ADD COLUMN rang_benoetigt          INTEGER,
    ADD COLUMN kapazitaet_frei         NUMERIC(12,3);

COMMENT ON COLUMN zev.steuerentscheid.verfahren IS
    'MERIT_ORDER oder REGEL - welches Verfahren den GELTENDEN Entscheid fuer batterieladung und '
    'einspeisung gefaellt hat. In der Schattenrechnung durchgehend REGEL. Ohne diese Angabe truege '
    'die Spalte regel zwei verschiedene Bedeutungen und das Protokoll waere spaeter nicht lesbar.';

COMMENT ON COLUMN zev.steuerentscheid.ladeplan_batterieladung IS
    'Was die Merit-Order entschieden HAETTE: FREI oder GESPERRT. NULL, wenn sie nicht rechnen '
    'konnte - dann fehlte eine Voraussetzung (Specs/Ladeplanung.md, FR-4). Bestimmt den Entscheid '
    'in der Schattenrechnung NICHT.';

COMMENT ON COLUMN zev.steuerentscheid.prognose_ueberschuss IS
    'Erwarteter PV-Ueberschuss DIESES Intervalls in kWh: max(0, erwartete Erzeugung minus '
    'Lastprofil). Nicht der gemessene Ueberschuss - der steht in ueberschuss.';

COMMENT ON COLUMN zev.steuerentscheid.gti IS
    'Einstrahlung auf die Modulflaeche in W/m2, die dem Entscheid zugrunde lag. Festgehalten, weil '
    'zev.einstrahlungsprognose je Intervall nur die ZULETZT geholte Fassung haelt - der hier '
    'gespeicherte Wert ist der einzige, der den Entscheid erklaert.';

COMMENT ON COLUMN zev.steuerentscheid.prognose_faktor IS
    'Gelernter Umrechnungsfaktor von W/m2 auf kWh zum Zeitpunkt des Entscheids. ACHT '
    'Nachkommastellen, weil der Wert in der Groessenordnung 0.005 liegt und bei sechs nur vier '
    'signifikante Stellen blieben.';

COMMENT ON COLUMN zev.steuerentscheid.rang IS
    'Platz dieses Intervalls in der Merit-Order des Resttages, nach Einspeisepreis aufsteigend. '
    'Zusammen mit rang_benoetigt erklaert er den Entscheid vollstaendig: "Rang 34, gebraucht '
    'werden 12" ist nachpruefbar, ein blosses GESPERRT nicht.';

COMMENT ON COLUMN zev.steuerentscheid.rang_benoetigt IS
    'Wie viele Intervalle gebraucht wurden, um die freie Kapazitaet zu decken. Ein Intervall liegt '
    'im Plan, wenn rang <= rang_benoetigt.';

COMMENT ON COLUMN zev.steuerentscheid.kapazitaet_frei IS
    'Freie Batteriekapazitaet in kWh beim Entscheid: kapazitaet * (1 - soc/100).';

-- Der CHECK auf batterieladung laesst NULL ohnehin zu; die Schattenspalte braucht denselben
-- Wertebereich.
ALTER TABLE zev.steuerentscheid ADD CONSTRAINT ck_steuerentscheid_ladeplan_batterieladung
    CHECK (ladeplan_batterieladung IN ('FREI', 'GESPERRT'));

ALTER TABLE zev.steuerentscheid ADD CONSTRAINT ck_steuerentscheid_verfahren
    CHECK (verfahren IN ('MERIT_ORDER', 'REGEL'));

ALTER TABLE zev.steuerentscheid ADD CONSTRAINT ck_steuerentscheid_gti
    CHECK (gti >= 0);

-- WARUM LADEPLAN JETZT SCHON ERLAUBT WIRD, obwohl die Schattenrechnung den Wert nicht schreibt:
-- Beim spaeteren Umschalten faellt die Erweiterung sonst leicht unter den Tisch, und der erste
-- Merit-Order-Entscheid scheitert beim INSERT - im Job, also nachts und ohne dass jemand zusieht.
-- Genau so ist es bei SOC_TIEF beinahe passiert (V160). Eine erlaubte, aber ungenutzte Konstante
-- kostet nichts.
--
-- Vor dem Anlegen mit pg_get_constraintdef geprueft: Der Constraint zaehlt sechs Werte auf.
ALTER TABLE zev.steuerentscheid DROP CONSTRAINT ck_steuerentscheid_regel;

ALTER TABLE zev.steuerentscheid ADD CONSTRAINT ck_steuerentscheid_regel
    CHECK (regel IN ('PREIS_NEGATIV', 'SOC_TIEF', 'KEIN_UEBERSCHUSS', 'EINSPEISEN_LOHNT',
                     'WARTEN_AUF_TAL', 'LADEN', 'LADEPLAN'));
