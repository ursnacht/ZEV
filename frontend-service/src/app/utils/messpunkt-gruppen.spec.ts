import { Einheit, EinheitTyp } from '../models/einheit.model';
import { messpunktGruppen } from './messpunkt-gruppen';

describe('Messpunkt-Gruppen Utils', () => {
  const bezug: Einheit = { id: 1, name: 'Bezug von BKW', typ: EinheitTyp.BEZUG, messpunkt: 'Bilanz' };
  const ruecklieferung: Einheit = {
    id: 2, name: 'Rücklieferung an BKW', typ: EinheitTyp.RUECKLIEFERUNG, messpunkt: 'Bilanz'
  };
  const wohnung: Einheit = { id: 3, name: 'Wohnung A', typ: EinheitTyp.CONSUMER, messpunkt: 'CH-1' };
  const ohneMesspunkt: Einheit = { id: 4, name: 'Wohnung B', typ: EinheitTyp.CONSUMER };

  describe('messpunktGruppen', () => {
    it('should group two einheiten sharing a messpunkt', () => {
      expect(messpunktGruppen([bezug, ruecklieferung, wohnung]))
        .toEqual([{ messpunkt: 'Bilanz', einheiten: [bezug, ruecklieferung] }]);
    });

    it('should not build a group for a messpunkt used only once', () => {
      expect(messpunktGruppen([bezug, wohnung])).toEqual([]);
    });

    it('should ignore einheiten without a messpunkt', () => {
      // Zwei Einheiten OHNE Messpunkt teilen keinen - `undefined` und '' sind kein Schluessel.
      const leer: Einheit = { id: 5, name: 'Wohnung C', typ: EinheitTyp.CONSUMER, messpunkt: '  ' };
      expect(messpunktGruppen([ohneMesspunkt, { ...ohneMesspunkt, id: 6 }, leer, { ...leer, id: 7 }]))
        .toEqual([]);
    });

    it('should compare the messpunkt without surrounding whitespace but case-sensitively', () => {
      const mitLeerraum: Einheit = { ...ruecklieferung, messpunkt: ' Bilanz ' };
      const andereSchreibweise: Einheit = { ...wohnung, messpunkt: 'bilanz' };

      const gruppen = messpunktGruppen([bezug, mitLeerraum, andereSchreibweise]);

      expect(gruppen.length).toBe(1);
      expect(gruppen[0].messpunkt).toBe('Bilanz');
      expect(gruppen[0].einheiten).toEqual([bezug, mitLeerraum]);
    });

    it('should keep the input order inside a group and sort groups by messpunkt', () => {
      const a: Einheit = { id: 10, name: 'Z', typ: EinheitTyp.BEZUG, messpunkt: 'Zaehler' };
      const b: Einheit = { id: 11, name: 'A', typ: EinheitTyp.RUECKLIEFERUNG, messpunkt: 'Zaehler' };

      const gruppen = messpunktGruppen([a, b, ruecklieferung, bezug]);

      expect(gruppen.map(g => g.messpunkt)).toEqual(['Bilanz', 'Zaehler']);
      expect(gruppen[0].einheiten).toEqual([ruecklieferung, bezug]);
      expect(gruppen[1].einheiten).toEqual([a, b]);
    });

    it('should put more than two einheiten of one messpunkt into one group', () => {
      const dritte: Einheit = { id: 8, name: 'Dritte', typ: EinheitTyp.CONSUMER, messpunkt: 'Bilanz' };

      expect(messpunktGruppen([bezug, ruecklieferung, dritte]))
        .toEqual([{ messpunkt: 'Bilanz', einheiten: [bezug, ruecklieferung, dritte] }]);
    });

    it('should return an empty list for no einheiten', () => {
      expect(messpunktGruppen([])).toEqual([]);
    });
  });
});
