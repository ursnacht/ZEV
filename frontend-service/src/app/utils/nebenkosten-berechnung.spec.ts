import {
  NkAkonto,
  NkPosition,
  NkPositionsart,
  NkZusatz
} from '../models/nebenkosten.model';
import { Mengeneinheit } from '../models/tarif.model';
import { NkMieterTage, berechneVorschau, runde, umlageSchluessel } from './nebenkosten-berechnung';

/**
 * Tests der clientseitigen Sofortberechnung (Specs/Nebenkosten/Abrechnung.md, FR-2 bis FR-4).
 *
 * <p>Die Regeln stehen zwangsläufig zweimal — in Java und hier. Verbindlich ist das Backend; diese
 * Tests halten die Vorschau daran fest. Deshalb sind die **beiden Referenzbeispiele der Spec**
 * dieselben wie in `NkBerechnungServiceTest`: Läuft eine der beiden Seiten auseinander, fällt es
 * an derselben Zahl auf.
 */
describe('nebenkosten-berechnung', () => {

  const JAHR_TAGE = 365;

  function mieter(mieterId: number, tage: number): NkMieterTage {
    return { mieterId, name: 'Mieter ' + mieterId, tage, ohneWohnung: tage === 0 };
  }

  function umlage(id: number, bezeichnung: string, totalbetrag: number,
                  gesamtmenge: number | null = null): NkPosition {
    return {
      id, art: NkPositionsart.UMLAGE, bezeichnung,
      einheit: Mengeneinheit.M3, totalbetrag, gesamtmenge,
      betragProEinheit: null, prozentsatz: null, verbraeuche: []
    };
  }

  function umlagePerson(id: number, bezeichnung: string, totalbetrag: number,
                       gesamtmenge: number | null = null): NkPosition {
    return {
      id, art: NkPositionsart.UMLAGE_PERSON, bezeichnung,
      einheit: Mengeneinheit.CHF, totalbetrag, gesamtmenge,
      betragProEinheit: null, prozentsatz: null, verbraeuche: []
    };
  }

  function verbrauch(id: number, bezeichnung: string, betragProEinheit: number,
                     mengen: { mieterId: number; menge: number | null }[] = []): NkPosition {
    return {
      id, art: NkPositionsart.VERBRAUCH, bezeichnung,
      einheit: Mengeneinheit.M3, totalbetrag: null, gesamtmenge: null,
      betragProEinheit, prozentsatz: null, verbraeuche: mengen
    };
  }

  function anteil(id: number, bezeichnung: string, totalbetrag: number,
                  prozente: { mieterId: number; menge: number | null }[] = []): NkPosition {
    return {
      id, art: NkPositionsart.ANTEIL, bezeichnung,
      einheit: null, totalbetrag, gesamtmenge: null,
      betragProEinheit: null, prozentsatz: null, verbraeuche: prozente
    };
  }

  function zuschlag(id: number, bezeichnung: string, prozentsatz: number): NkPosition {
    return {
      id, art: NkPositionsart.ZUSCHLAG, bezeichnung,
      einheit: null, totalbetrag: null, gesamtmenge: null,
      betragProEinheit: null, prozentsatz, verbraeuche: []
    };
  }

  function zusatz(id: number, mieterId: number, bezeichnung: string,
                  menge: number, betragProEinheit: number, reihenfolge?: number): NkZusatz {
    return {
      id, mieterId, reihenfolge, bezeichnung,
      einheit: Mengeneinheit.STUECK, menge, betragProEinheit
    };
  }

  function akonto(mieterId: number, anzahlMonate: number, betragProMonat: number,
                  korrektur = 0): NkAkonto {
    return { id: 1, mieterId, anzahlMonate, betragProMonat, korrektur };
  }

  // ==================== Referenzbeispiel der Spec (FR-2) ====================

  describe('Referenzbeispiel FR-2', () => {
    it('should leave the vacancy share undistributed', () => {
      // 9 Wohnungen, 365 Tage, Allgemeinstrom 900.00; Wohnung 5 steht 90 Tage leer.
      // Nenner 3285, voller Mieter 100.00, Summe verteilt 875.34, nicht verteilt 24.66.
      const nenner = 9 * JAHR_TAGE;
      const mieterListe: NkMieterTage[] = [];
      for (let i = 1; i <= 8; i++) {
        mieterListe.push(mieter(i, JAHR_TAGE));
      }
      mieterListe.push(mieter(9, JAHR_TAGE - 90));

      const result = berechneVorschau(
        nenner, mieterListe, [umlage(10, 'Allgemeinstrom', 900)], [], []);

      expect(result.mieter[0].zeilen[0].betrag).toBe(100);
      expect(result.umlagen[0].summeVerteilt).toBe(875.34);
      expect(result.umlagen[0].nichtVerteilt).toBe(24.66);
      expect(result.umlagen[0].rundungsdifferenz).toBe(0);
    });

    it('should not charge the remaining tenants more for a vacancy', () => {
      // Der Kern des Entscheids: Leerstand geht zu Lasten des Eigentuemers.
      const nenner = 9 * JAHR_TAGE;
      const voll = Array.from({ length: 9 }, (_, i) => mieter(i + 1, JAHR_TAGE));
      const mitLeerstand = [...voll.slice(0, 8), mieter(9, JAHR_TAGE - 90)];

      const ohne = berechneVorschau(nenner, voll, [umlage(10, 'Strom', 900)], [], []);
      const mit = berechneVorschau(nenner, mitLeerstand, [umlage(10, 'Strom', 900)], [], []);

      expect(ohne.mieter[0].zeilen[0].betrag).toBe(100);
      expect(mit.mieter[0].zeilen[0].betrag).toBe(100);
    });
  });

  // ==================== Umlage (FR-2) ====================

  describe('Umlage', () => {
    it('should distribute proportionally to the rental days', () => {
      const result = berechneVorschau(
        2 * JAHR_TAGE, [mieter(1, JAHR_TAGE), mieter(2, JAHR_TAGE)],
        [umlage(10, 'Strom', 1000)], [], []);

      expect(result.mieter[0].zeilen[0].betrag).toBe(500);
      expect(result.mieter[1].zeilen[0].betrag).toBe(500);
    });

    it('should give a tenant with two apartments a double share', () => {
      // Zwei Wohnungen heisst doppelte Tage - so liefert sie der Server.
      const result = berechneVorschau(
        3 * JAHR_TAGE, [mieter(1, 2 * JAHR_TAGE), mieter(2, JAHR_TAGE)],
        [umlage(10, 'Strom', 900)], [], []);

      expect(result.mieter[0].zeilen[0].betrag).toBe(600);
      expect(result.mieter[1].zeilen[0].betrag).toBe(300);
    });

    it('should distribute the total quantity when one is recorded', () => {
      const result = berechneVorschau(
        2 * JAHR_TAGE, [mieter(1, JAHR_TAGE), mieter(2, JAHR_TAGE)],
        [umlage(10, 'Wasser', 1000, 500)], [], []);

      expect(result.mieter[0].zeilen[0].menge).toBe(250);
    });

    it('should leave the quantity empty when none is recorded', () => {
      const result = berechneVorschau(
        JAHR_TAGE, [mieter(1, JAHR_TAGE)], [umlage(10, 'Strom', 100)], [], []);

      expect(result.mieter[0].zeilen[0].menge).toBeUndefined();
    });

    it('should charge nothing to a tenant without an apartment', () => {
      const result = berechneVorschau(
        JAHR_TAGE, [mieter(1, JAHR_TAGE), mieter(2, 0)],
        [umlage(10, 'Strom', 100)], [], []);

      expect(result.mieter[1].zeilen[0].betrag).toBe(0);
      expect(result.mieter[1].ohneWohnung).toBe(true);
    });

    it('should not divide by zero when the denominator is zero', () => {
      const result = berechneVorschau(0, [mieter(1, 0)], [umlage(10, 'Strom', 900)], [], []);

      expect(result.mieter[0].zeilen[0].betrag).toBe(0);
      expect(result.umlagen[0].nichtVerteilt).toBe(900);
    });
  });

  // ==================== Verbrauch und Zusatz (FR-2 / FR-3) ====================

  describe('Verbrauch', () => {
    it('should multiply the recorded quantity by the price per unit', () => {
      const result = berechneVorschau(
        JAHR_TAGE, [mieter(1, JAHR_TAGE)],
        [verbrauch(10, 'Warmwasser', 3.5, [{ mieterId: 1, menge: 12.5 }])], [], []);

      expect(result.mieter[0].zeilen[0].betrag).toBe(43.75);
    });

    it('should treat a missing quantity as zero and not as an error', () => {
      // Sonst liesse sich eine halb erfasste Abrechnung nicht zwischenspeichern.
      const result = berechneVorschau(
        JAHR_TAGE, [mieter(1, JAHR_TAGE)], [verbrauch(10, 'Warmwasser', 3.5)], [], []);

      expect(result.mieter[0].zeilen[0].menge).toBeUndefined();
      expect(result.mieter[0].zeilen[0].betrag).toBe(0);
    });

    it('should keep the quantities of different tenants apart', () => {
      const result = berechneVorschau(
        2 * JAHR_TAGE, [mieter(1, JAHR_TAGE), mieter(2, JAHR_TAGE)],
        [verbrauch(10, 'Warmwasser', 2, [
          { mieterId: 1, menge: 10 }, { mieterId: 2, menge: 30 }])], [], []);

      expect(result.mieter[0].zeilen[0].betrag).toBe(20);
      expect(result.mieter[1].zeilen[0].betrag).toBe(60);
    });
  });

  describe('Zusatzpositionen', () => {
    it('should only appear in the block of their own tenant', () => {
      const result = berechneVorschau(
        2 * JAHR_TAGE, [mieter(1, JAHR_TAGE), mieter(2, JAHR_TAGE)],
        [], [zusatz(50, 1, 'Sauna', 3, 10)], []);

      expect(result.mieter[0].zeilen.length).toBe(1);
      expect(result.mieter[0].zeilen[0].betrag).toBe(30);
      expect(result.mieter[1].zeilen.length).toBe(0);
    });

    it('should be recognisable by its zusatzId', () => {
      // Daran unterscheidet die Maske bearbeitbare von berechneten Zeilen.
      const result = berechneVorschau(
        JAHR_TAGE, [mieter(1, JAHR_TAGE)], [], [zusatz(50, 1, 'Sauna', 3, 10)], []);

      expect(result.mieter[0].zeilen[0].zusatzId).toBe(50);
      expect(result.mieter[0].zeilen[0].positionId).toBeUndefined();
    });
  });

  // ==================== Zuschlagskaskade (FR-2) ====================

  describe('Zuschlag', () => {
    it('should calculate on the sum of the lines before it', () => {
      const result = berechneVorschau(
        JAHR_TAGE, [mieter(1, JAHR_TAGE)],
        [umlage(10, 'Strom', 100), zuschlag(11, 'Verwaltung', 10)], [], []);

      expect(result.mieter[0].zeilen[1].betrag).toBe(10);
      expect(result.mieter[0].kostentotal).toBe(110);
    });

    it('should include an earlier surcharge in a later one', () => {
      // Kaskadierend: 100 -> +10% = 10 -> +10% auf 110 = 11.
      const result = berechneVorschau(
        JAHR_TAGE, [mieter(1, JAHR_TAGE)],
        [umlage(10, 'Strom', 100), zuschlag(11, 'Verwaltung', 10), zuschlag(12, 'Weiteres', 10)],
        [], []);

      expect(result.mieter[0].zeilen[2].betrag).toBe(11);
      expect(result.mieter[0].kostentotal).toBe(121);
    });

    it('should include additional lines of the tenant in its basis', () => {
      const result = berechneVorschau(
        JAHR_TAGE, [mieter(1, JAHR_TAGE)],
        [umlage(10, 'Strom', 100), zuschlag(11, 'Verwaltung', 10)],
        [zusatz(50, 1, 'Sauna', 1, 100, 1)], []);

      // Die Zusatzzeile traegt Reihenfolge 1, der Zuschlag 2 - also 200 x 10%.
      expect(result.mieter[0].zeilen[2].betrag).toBe(20);
    });

    it('should put the general position first on an equal order number', () => {
      // Gleichstandsregel - sonst waere das Ergebnis nicht reproduzierbar.
      const result = berechneVorschau(
        JAHR_TAGE, [mieter(1, JAHR_TAGE)],
        [umlage(10, 'Strom', 100)], [zusatz(50, 1, 'Sauna', 1, 50, 1)], []);

      expect(result.mieter[0].zeilen[0].bezeichnung).toBe('Strom');
      expect(result.mieter[0].zeilen[1].bezeichnung).toBe('Sauna');
    });

    it('should change the result when the positions are reordered', () => {
      const vorher = berechneVorschau(
        JAHR_TAGE, [mieter(1, JAHR_TAGE)],
        [umlage(10, 'Strom', 100), zuschlag(11, 'Verwaltung', 10)], [], []);
      const nachher = berechneVorschau(
        JAHR_TAGE, [mieter(1, JAHR_TAGE)],
        [zuschlag(11, 'Verwaltung', 10), umlage(10, 'Strom', 100)], [], []);

      expect(vorher.mieter[0].kostentotal).toBe(110);
      // Vor der Umlage gibt es nichts, worauf der Zuschlag rechnen koennte.
      expect(nachher.mieter[0].kostentotal).toBe(100);
    });
  });

  // ==================== Anteil (FR-2) ====================

  describe('Anteil', () => {
    it('should distribute the total by the percentage per tenant', () => {
      const result = berechneVorschau(
        2 * JAHR_TAGE, [mieter(1, JAHR_TAGE), mieter(2, JAHR_TAGE)],
        [anteil(10, 'Heizkosten', 2400, [
          { mieterId: 1, menge: 60 }, { mieterId: 2, menge: 40 }])], [], []);

      expect(result.mieter[0].zeilen[0].betrag).toBe(1440);
      expect(result.mieter[1].zeilen[0].betrag).toBe(960);
      expect(result.umlagen[0].summeProzent).toBe(100);
      expect(result.umlagen[0].nichtVerteilt).toBe(0);
    });

    it('should leave the rest undistributed below 100 percent', () => {
      const result = berechneVorschau(
        2 * JAHR_TAGE, [mieter(1, JAHR_TAGE), mieter(2, JAHR_TAGE)],
        [anteil(10, 'Heizkosten', 1000, [{ mieterId: 1, menge: 30 }])], [], []);

      expect(result.umlagen[0].summeProzent).toBe(30);
      expect(result.umlagen[0].summeVerteilt).toBe(300);
      expect(result.umlagen[0].nichtVerteilt).toBe(700);
    });

    it('should be independent of the rental days', () => {
      // Anders als die Umlage: Der Schluessel kommt von aussen.
      const result = berechneVorschau(
        9 * JAHR_TAGE, [mieter(1, JAHR_TAGE)],
        [anteil(10, 'Heizkosten', 1000, [{ mieterId: 1, menge: 100 }])], [], []);

      expect(result.mieter[0].zeilen[0].betrag).toBe(1000);
      expect(result.umlagen[0].nichtVerteilt).toBe(0);
    });
  });

  // ==================== Akonto und Saldo (FR-4) ====================

  describe('Akonto und Saldo', () => {
    it('should calculate the total as months times amount plus correction', () => {
      const result = berechneVorschau(
        JAHR_TAGE, [mieter(1, JAHR_TAGE)], [umlage(10, 'Strom', 1200)], [],
        [akonto(1, 12, 100, 50)]);

      expect(result.mieter[0].akontoTotal).toBe(1250);
    });

    it('should allow a negative correction', () => {
      const result = berechneVorschau(
        JAHR_TAGE, [mieter(1, JAHR_TAGE)], [], [], [akonto(1, 12, 100, -200)]);

      expect(result.mieter[0].akontoTotal).toBe(1000);
    });

    it('should report a positive balance as an amount due', () => {
      const result = berechneVorschau(
        JAHR_TAGE, [mieter(1, JAHR_TAGE)], [umlage(10, 'Strom', 1500)], [],
        [akonto(1, 12, 100)]);

      expect(result.mieter[0].kostentotal).toBe(1500);
      expect(result.mieter[0].saldo).toBe(300);
    });

    it('should report a negative balance as a credit', () => {
      const result = berechneVorschau(
        JAHR_TAGE, [mieter(1, JAHR_TAGE)], [umlage(10, 'Strom', 900)], [],
        [akonto(1, 12, 100)]);

      expect(result.mieter[0].saldo).toBe(-300);
    });

    it('should treat a tenant without prepayment data as zero', () => {
      const result = berechneVorschau(
        JAHR_TAGE, [mieter(1, JAHR_TAGE)], [umlage(10, 'Strom', 900)], [], []);

      expect(result.mieter[0].akontoTotal).toBe(0);
      expect(result.mieter[0].saldo).toBe(900);
    });
  });

  // ==================== Rundung (FR-5) ====================

  describe('Rundung', () => {
    it('should round each line to one rappen, away from zero', () => {
      expect(runde(8.005, 2)).toBe(8.01);
      expect(runde(2.675, 2)).toBe(2.68);
      expect(runde(-2.675, 2)).toBe(-2.68);
    });

    it('should report the rounding difference separately from the vacancy share', () => {
      // Drei Mieter zu je einem Drittel von 100.00: 33.33 x 3 = 99.99, ein Rappen bleibt.
      const result = berechneVorschau(
        3 * JAHR_TAGE,
        [mieter(1, JAHR_TAGE), mieter(2, JAHR_TAGE), mieter(3, JAHR_TAGE)],
        [umlage(10, 'Strom', 100)], [], []);

      expect(result.umlagen[0].summeVerteilt).toBe(99.99);
      expect(result.umlagen[0].nichtVerteilt).toBe(0);
      expect(result.umlagen[0].rundungsdifferenz).toBe(0.01);
    });

    it('should not compensate the rounding difference on any tenant', () => {
      const result = berechneVorschau(
        3 * JAHR_TAGE,
        [mieter(1, JAHR_TAGE), mieter(2, JAHR_TAGE), mieter(3, JAHR_TAGE)],
        [umlage(10, 'Strom', 100)], [], []);

      expect(result.mieter.map(m => m.zeilen[0].betrag)).toEqual([33.33, 33.33, 33.33]);
    });
  });

  // ==================== Kontrollzahlen und Schluessel ====================

  describe('umlageSchluessel', () => {
    it('should use the database id of a saved position', () => {
      expect(umlageSchluessel(umlage(10, 'Strom', 100), 1)).toBe(10);
    });

    it('should fall back to the negated order for an unsaved position', () => {
      // Negativ, damit der Ersatzschluessel nie mit einer echten ID zusammenfaellt.
      const neu = { ...umlage(10, 'Strom', 100), id: undefined };
      expect(umlageSchluessel(neu, 2)).toBe(-2);
    });

    it('should let an unsaved position find its own control figures', () => {
      const neu = { ...umlage(10, 'Strom', 100), id: undefined };

      const result = berechneVorschau(JAHR_TAGE, [mieter(1, JAHR_TAGE)], [neu], [], []);

      expect(result.umlagen[0].summeVerteilt).toBe(100);
    });
  });

  describe('Randfaelle', () => {
    it('should return empty blocks without tenants', () => {
      const result = berechneVorschau(JAHR_TAGE, [], [umlage(10, 'Strom', 900)], [], []);

      expect(result.mieter.length).toBe(0);
      expect(result.summeTage).toBe(0);
      expect(result.umlagen[0].nichtVerteilt).toBe(900);
    });

    it('should handle an empty position list', () => {
      const result = berechneVorschau(JAHR_TAGE, [mieter(1, JAHR_TAGE)], [], [], []);

      expect(result.mieter[0].zeilen.length).toBe(0);
      expect(result.mieter[0].kostentotal).toBe(0);
    });

    it('should sum the rental days of all tenants', () => {
      // Grundlage der Pruefung "Summe Tage <= Nenner" im Backend.
      const result = berechneVorschau(
        3 * JAHR_TAGE, [mieter(1, JAHR_TAGE), mieter(2, 200)], [], [], []);

      expect(result.summeTage).toBe(JAHR_TAGE + 200);
    });
  });

  describe('Umlage pro Person', () => {

    it('should match the per-apartment allocation when nothing is recorded', () => {
      // Der eigentliche Schutz: Vorschlag "Personen = Wohnungen" plus Vorgabe "1 Person je Mieter"
      // muss dieselben Betraege ergeben - sonst aendert eine bestehende Abrechnung ihre Zahlen.
      const nenner = 2 * JAHR_TAGE;
      const mieterListe = [mieter(1, JAHR_TAGE), mieter(2, JAHR_TAGE)];

      const result = berechneVorschau(
        nenner, mieterListe,
        [umlage(10, 'Strom', 1000), umlagePerson(11, 'Gruenabfuhr', 1000)],
        [], [], nenner, []);

      const block = result.mieter[0];
      expect(block.zeilen[1].betrag).toBe(block.zeilen[0].betrag);
      expect(block.zeilen[1].betrag).toBe(500);
    });

    it('should distribute by heads', () => {
      const nennerPerson = 5 * JAHR_TAGE;

      const result = berechneVorschau(
        2 * JAHR_TAGE, [mieter(1, JAHR_TAGE), mieter(2, JAHR_TAGE)],
        [umlagePerson(11, 'Gruenabfuhr', 1000)], [], [],
        nennerPerson,
        [{ mieterId: 1, anzahlPersonen: 3 }, { mieterId: 2, anzahlPersonen: 2 }]);

      expect(result.mieter[0].zeilen[0].betrag).toBe(600);
      expect(result.mieter[1].zeilen[0].betrag).toBe(400);
      expect(result.nennerPerson).toBe(nennerPerson);
      expect(result.summePersonenTage).toBe(nennerPerson);
    });

    it('should expose personenTage as tage times persons', () => {
      const result = berechneVorschau(
        2 * JAHR_TAGE, [mieter(1, JAHR_TAGE), mieter(2, JAHR_TAGE)],
        [], [], [], 5 * JAHR_TAGE, [{ mieterId: 1, anzahlPersonen: 3 }]);

      expect(result.mieter[0].anzahlPersonen).toBe(3);
      expect(result.mieter[0].personenTage).toBe(3 * JAHR_TAGE);
      // Ohne Eintrag gilt die Vorgabe 1.
      expect(result.mieter[1].anzahlPersonen).toBe(1);
      expect(result.mieter[1].personenTage).toBe(JAHR_TAGE);
    });

    it('should leave a share undistributed when fewer heads are recorded', () => {
      const result = berechneVorschau(
        2 * JAHR_TAGE, [mieter(1, JAHR_TAGE), mieter(2, JAHR_TAGE)],
        [umlagePerson(11, 'Gruenabfuhr', 1000)], [], [],
        5 * JAHR_TAGE,
        [{ mieterId: 1, anzahlPersonen: 3 }, { mieterId: 2, anzahlPersonen: 1 }]);

      expect(result.umlagen[0].summeVerteilt).toBe(800);
      expect(result.umlagen[0].nichtVerteilt).toBe(200);
    });

    it('should distribute the quantity as well', () => {
      const result = berechneVorschau(
        2 * JAHR_TAGE, [mieter(1, JAHR_TAGE), mieter(2, JAHR_TAGE)],
        [umlagePerson(11, 'Gruenabfuhr', 800, 40)], [], [],
        4 * JAHR_TAGE,
        [{ mieterId: 1, anzahlPersonen: 3 }, { mieterId: 2, anzahlPersonen: 1 }]);

      expect(result.mieter[0].zeilen[0].menge).toBe(30);
      expect(result.mieter[1].zeilen[0].menge).toBe(10);
    });

    it('should yield 0 instead of dividing by zero', () => {
      const result = berechneVorschau(
        2 * JAHR_TAGE, [mieter(1, JAHR_TAGE)],
        [umlagePerson(11, 'Gruenabfuhr', 1000)], [], [], 0, []);

      expect(result.mieter[0].zeilen[0].betrag).toBe(0);
    });
  });
});
