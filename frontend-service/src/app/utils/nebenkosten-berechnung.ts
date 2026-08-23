import {
  NkAkonto,
  NkBerechnung,
  NkMieterAbrechnung,
  NkPosition,
  NkPositionsart,
  NkUmlageInfo,
  NkZeile,
  NkZusatz
} from '../models/nebenkosten.model';

/**
 * Sofortberechnung der Nebenkostenabrechnung in der Maske
 * (Specs/Nebenkosten/Abrechnung.md, FR-7).
 *
 * **Das ist eine Vorschau, keine Wahrheit.** Dieselben Regeln stehen im Backend
 * (`NkBerechnungService`) und sind dort verbindlich; nach dem Speichern zeigt die Maske dessen
 * Werte, nicht die hier gerechneten (Entscheid 2 des Umsetzungsplans). Der Zweck ist allein, dass
 * eine Änderung sofort sichtbar wird, ohne speichern zu müssen.
 *
 * Ein Unterschied bleibt bauartbedingt: Java rechnet mit `BigDecimal`, JavaScript mit
 * Gleitkomma. In seltenen Grenzfällen kann die Vorschau um einen Rappen von der Antwort des
 * Servers abweichen. Weil die Maske nach dem Speichern die Serverwerte lädt, fällt das im selben
 * Moment auf, statt unbemerkt zu bleiben.
 */

/** Miettage eines Mieters, wie sie der Server zuletzt geliefert hat. */
export interface NkMieterTage {
  mieterId: number;
  name: string;
  /** Miettage im Zeitraum, bereits mit der Zahl der Wohnungen multipliziert. */
  tage: number;
  ohneWohnung: boolean;
}

/**
 * Kaufmännisch runden, von Null weg — wie `RoundingMode.HALF_UP` im Backend.
 *
 * `Math.round` rundet bei negativen Zahlen zur nächsthöheren Zahl (−0.5 → −0) und wäre damit
 * nicht symmetrisch. Der Epsilon-Zuschlag fängt Binärdarstellungen wie `8.005 → 8.00499…` ab.
 */
export function runde(wert: number, stellen: number): number {
  const faktor = Math.pow(10, stellen);
  const skaliert = Math.abs(wert) * faktor;
  const gerundet = Math.round(skaliert + Number.EPSILON * skaliert);
  return (wert < 0 ? -gerundet : gerundet) / faktor;
}

/** Zahl oder 0, wenn nichts erfasst ist. */
function zahl(wert: number | null | undefined): number {
  return wert ?? 0;
}

/**
 * Rechnet die ganze Abrechnung.
 *
 * @param nenner Anzahl Wohnungen × Tage im Zeitraum
 * @param mieter Die abzurechnenden Mieter samt ihren Miettagen
 * @param positionen Allgemeine Positionen in der Reihenfolge der Liste
 * @param zusaetze Zusatzpositionen aller Mieter
 * @param akonto Akonto-Angaben je Mieter
 */
export function berechneVorschau(
  nenner: number,
  mieter: NkMieterTage[],
  positionen: NkPosition[],
  zusaetze: NkZusatz[],
  akonto: NkAkonto[]
): NkBerechnung {
  // Die Reihenfolge kommt aus der Listenposition - genau so vergibt sie das Backend beim
  // Speichern neu. Wer eine Zeile verschiebt, sieht die Wirkung auf die Zuschlaege sofort.
  const nummeriert = positionen.map((position, index) => ({ position, reihenfolge: index + 1 }));

  const umlagen: NkUmlageInfo[] = nummeriert
    .filter(eintrag => eintrag.position.art === NkPositionsart.UMLAGE
                    || eintrag.position.art === NkPositionsart.ANTEIL)
    .map(eintrag => ({
      positionId: umlageSchluessel(eintrag.position, eintrag.reihenfolge),
      bezeichnung: eintrag.position.bezeichnung,
      art: eintrag.position.art,
      totalbetrag: runde(zahl(eintrag.position.totalbetrag), 2),
      summeVerteilt: 0,
      nichtVerteilt: 0,
      rundungsdifferenz: 0,
      summeProzent: 0
    }));

  const bloecke: NkMieterAbrechnung[] = [];
  let summeTage = 0;

  for (const person of mieter) {
    summeTage += person.tage;
    bloecke.push(berechneMieter(person, nenner, nummeriert, zusaetze, akonto, umlagen));
  }

  for (const info of umlagen) {
    // Dieselbe Rechnung, nur mit anderer Bezugsgroesse: bei ANTEIL die Summe der Prozentsaetze,
    // bei UMLAGE der Zeitanteil. Was uebrig bleibt, heisst dort "Prozente fehlen", hier "Leerstand".
    const teil = info.art === NkPositionsart.ANTEIL
      ? info.summeProzent / 100
      : anteil(summeTage, nenner);
    const exaktVerteilbar = runde(info.totalbetrag * teil, 2);
    info.nichtVerteilt = runde(info.totalbetrag - exaktVerteilbar, 2);
    info.rundungsdifferenz = runde(exaktVerteilbar - info.summeVerteilt, 2);
  }

  return { nenner, summeTage, mieter: bloecke, umlagen };
}

function berechneMieter(
  person: NkMieterTage,
  nenner: number,
  nummeriert: { position: NkPosition; reihenfolge: number }[],
  zusaetze: NkZusatz[],
  akonto: NkAkonto[],
  umlagen: NkUmlageInfo[]
): NkMieterAbrechnung {
  const eigeneZusaetze = zusaetze
    .filter(z => z.mieterId === person.mieterId)
    .map((z, index) => ({ zusatz: z, reihenfolge: z.reihenfolge ?? index + 1 }));

  // Ein Zuschlag rechnet auf die Summe aller Zeilen davor - allgemeine wie mieterspezifische.
  // Bei Gleichstand kommt die allgemeine Position zuerst, damit das Ergebnis eindeutig ist.
  type Quelle =
    | { art: 'position'; reihenfolge: number; position: NkPosition }
    | { art: 'zusatz'; reihenfolge: number; zusatz: NkZusatz };

  const quellen: Quelle[] = [
    ...nummeriert.map(e => ({ art: 'position' as const, reihenfolge: e.reihenfolge, position: e.position })),
    ...eigeneZusaetze.map(e => ({ art: 'zusatz' as const, reihenfolge: e.reihenfolge, zusatz: e.zusatz }))
  ];
  quellen.sort((a, b) =>
    a.reihenfolge !== b.reihenfolge
      ? a.reihenfolge - b.reihenfolge
      : (a.art === 'position' ? 0 : 1) - (b.art === 'position' ? 0 : 1)
  );

  const zeilen: NkZeile[] = [];
  let laufendeSumme = 0;

  for (const quelle of quellen) {
    const zeile = quelle.art === 'position'
      ? zeileAusPosition(quelle.position, quelle.reihenfolge, person, nenner, laufendeSumme, umlagen)
      : zeileAusZusatz(quelle.zusatz, quelle.reihenfolge);
    laufendeSumme = runde(laufendeSumme + zeile.betrag, 2);
    zeilen.push(zeile);
  }

  const eigenesAkonto = akonto.find(a => a.mieterId === person.mieterId);
  const monate = zahl(eigenesAkonto?.anzahlMonate);
  const proMonat = zahl(eigenesAkonto?.betragProMonat);
  const korrektur = zahl(eigenesAkonto?.korrektur);
  const akontoTotal = runde(monate * proMonat + korrektur, 2);

  return {
    mieterId: person.mieterId,
    name: person.name,
    tage: person.tage,
    ohneWohnung: person.ohneWohnung,
    zeilen,
    kostentotal: laufendeSumme,
    akontoAnzahlMonate: monate,
    akontoBetragProMonat: proMonat,
    akontoKorrektur: korrektur,
    akontoTotal,
    saldo: runde(laufendeSumme - akontoTotal, 2)
  };
}

function zeileAusPosition(
  position: NkPosition,
  reihenfolge: number,
  person: NkMieterTage,
  nenner: number,
  laufendeSumme: number,
  umlagen: NkUmlageInfo[]
): NkZeile {
  const zeile: NkZeile = {
    positionId: position.id,
    art: position.art,
    reihenfolge,
    bezeichnung: position.bezeichnung,
    einheit: position.einheit ?? undefined,
    betrag: 0
  };

  switch (position.art) {
    case NkPositionsart.UMLAGE: {
      const teil = anteil(person.tage, nenner);
      if (position.gesamtmenge !== null && position.gesamtmenge !== undefined) {
        zeile.menge = runde(position.gesamtmenge * teil, 3);
      }
      zeile.betrag = runde(zahl(position.totalbetrag) * teil, 2);

      const schluessel = umlageSchluessel(position, reihenfolge);
      const info = umlagen.find(u => u.positionId === schluessel);
      if (info) {
        info.summeVerteilt = runde(info.summeVerteilt + zeile.betrag, 2);
      }
      break;
    }
    case NkPositionsart.VERBRAUCH: {
      const erfasst = position.verbraeuche.find(v => v.mieterId === person.mieterId);
      zeile.menge = erfasst?.menge ?? undefined;
      zeile.betragProEinheit = position.betragProEinheit ?? undefined;
      zeile.betrag = runde(zahl(erfasst?.menge) * zahl(position.betragProEinheit), 2);
      break;
    }
    case NkPositionsart.ANTEIL: {
      // Der Prozentsatz je Mieter steht dort, wo bei VERBRAUCH die Menge steht.
      const erfasst = position.verbraeuche.find(v => v.mieterId === person.mieterId);
      zeile.prozentsatz = erfasst?.menge ?? undefined;
      zeile.betrag = runde(zahl(position.totalbetrag) * zahl(erfasst?.menge) / 100, 2);

      const info = umlagen.find(u => u.positionId === umlageSchluessel(position, reihenfolge));
      if (info) {
        info.summeVerteilt = runde(info.summeVerteilt + zeile.betrag, 2);
        info.summeProzent = runde(info.summeProzent + zahl(erfasst?.menge), 3);
      }
      break;
    }
    case NkPositionsart.ZUSCHLAG: {
      zeile.prozentsatz = position.prozentsatz ?? undefined;
      zeile.betrag = runde(laufendeSumme * zahl(position.prozentsatz) / 100, 2);
      break;
    }
  }
  return zeile;
}

function zeileAusZusatz(zusatz: NkZusatz, reihenfolge: number): NkZeile {
  return {
    zusatzId: zusatz.id,
    // Rechnet wie VERBRAUCH; unterschieden wird ueber zusatzId - so wie im Backend.
    art: NkPositionsart.VERBRAUCH,
    reihenfolge,
    bezeichnung: zusatz.bezeichnung,
    einheit: zusatz.einheit ?? undefined,
    menge: zusatz.menge ?? undefined,
    betragProEinheit: zusatz.betragProEinheit ?? undefined,
    betrag: runde(zahl(zusatz.menge) * zahl(zusatz.betragProEinheit), 2)
  };
}

/**
 * Schlüssel, über den eine Umlagezeile ihre Kontrollzahlen findet.
 *
 * Für eine gespeicherte Position ist das ihre **Datenbank-ID** — dieselbe, die der Server in
 * `NkZeile.positionId` und `NkUmlageInfo.positionId` liefert. So passt die Zuordnung vor **und**
 * nach der ersten clientseitigen Neuberechnung; vorher lief sie ins Leere, weil die Vorschau die
 * Reihenfolge als Schlüssel benutzte und der Server die ID.
 *
 * Eine noch nicht gespeicherte Position hat keine ID; sie bekommt die **negierte** Reihenfolge.
 * Negativ, damit der Ersatzschlüssel nie mit einer echten Datenbank-ID zusammenfällt.
 */
export function umlageSchluessel(position: NkPosition, reihenfolge: number): number {
  return position.id ?? -reihenfolge;
}

/** Zeitanteil `Tage / Nenner`; ein Nenner von 0 ergibt 0 statt einer Division durch 0. */
function anteil(tage: number, nenner: number): number {
  return nenner > 0 ? tage / nenner : 0;
}
