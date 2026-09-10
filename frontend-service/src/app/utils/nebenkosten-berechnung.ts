import {
  NkAkonto,
  NkBerechnung,
  NkMieterAbrechnung,
  NkPerson,
  NkPosition,
  NkPositionsart,
  NkPositionSumme,
  NkZeile,
  NkZusatz
} from '../models/nebenkosten.model';
import { Mengeneinheit } from '../models/tarif.model';

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

/** Personen je Wohnung, wenn nichts erfasst ist - wie `PERSONEN_VORGABE` im Backend. */
export const PERSONEN_VORGABE = 1;

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
 * @param nennerPerson Anzahl Personen × Tage im Zeitraum — Nenner der Umlage pro Person
 * @param personen Personenzahlen je Mieter; fehlt eine, gilt `PERSONEN_VORGABE`
 */
export function berechneVorschau(
  nenner: number,
  mieter: NkMieterTage[],
  positionen: NkPosition[],
  zusaetze: NkZusatz[],
  akonto: NkAkonto[],
  nennerPerson: number = 0,
  personen: NkPerson[] = []
): NkBerechnung {
  // Die Reihenfolge kommt aus der Listenposition - genau so vergibt sie das Backend beim
  // Speichern neu. Wer eine Zeile verschiebt, sieht die Wirkung auf die Zuschlaege sofort.
  const nummeriert = positionen.map((position, index) => ({ position, reihenfolge: index + 1 }));

  // Eine Zeile je Position, fuer JEDE Art (FR-10). Felder, die eine Art nicht kennt, bleiben
  // `null` - die Maske laesst die Zelle dann leer statt eine 0 zu behaupten.
  const summen: NkPositionSumme[] = nummeriert.map(eintrag => ({
    positionId: umlageSchluessel(eintrag.position, eintrag.reihenfolge),
    bezeichnung: eintrag.position.bezeichnung,
    art: eintrag.position.art,
    totalbetrag: verteilendeArt(eintrag.position.art)
      ? runde(zahl(eintrag.position.totalbetrag), 2) : null,
    summeMenge: null,
    // Bei ANTEIL steht in der Mengenspalte der Prozentsatz, bei ZUSCHLAG gibt es keine Menge.
    einheit: eintrag.position.art === NkPositionsart.ANTEIL
          || eintrag.position.art === NkPositionsart.ZUSCHLAG
      ? null : (eintrag.position.einheit ?? null),
    summeKosten: 0,
    nichtVerteilt: null,
    rundungsdifferenz: null,
    summeProzent: eintrag.position.art === NkPositionsart.ANTEIL ? 0 : null
  }));

  const bloecke: NkMieterAbrechnung[] = [];
  let summeTage = 0;
  let summePersonenTage = 0;

  for (const eintrag of mieter) {
    const anzahlPersonen = personen.find(x => x.mieterId === eintrag.mieterId)?.anzahlPersonen
      ?? PERSONEN_VORGABE;
    summeTage += eintrag.tage;
    summePersonenTage += eintrag.tage * anzahlPersonen;
    bloecke.push(berechneMieter(eintrag, nenner, nennerPerson, anzahlPersonen,
      nummeriert, zusaetze, akonto, summen));
  }

  for (const info of summen) {
    // Nur die verteilenden Arten haben einen Rest: Bei ANTEIL die Summe der Prozentsaetze, bei
    // UMLAGE der Zeitanteil, bei UMLAGE_PERSON der Personenanteil. Was uebrig bleibt, heisst dort
    // "Prozente fehlen", hier "Leerstand".
    if (!verteilendeArt(info.art)) {
      continue;
    }
    let teil: number;
    if (info.art === NkPositionsart.ANTEIL) {
      teil = zahl(info.summeProzent) / 100;
    } else if (info.art === NkPositionsart.UMLAGE_PERSON) {
      teil = anteil(summePersonenTage, nennerPerson);
    } else {
      teil = anteil(summeTage, nenner);
    }
    const total = zahl(info.totalbetrag);
    const exaktVerteilbar = runde(total * teil, 2);
    info.nichtVerteilt = runde(total - exaktVerteilbar, 2);
    info.rundungsdifferenz = runde(exaktVerteilbar - info.summeKosten, 2);
  }

  // Sammelzeile aller Zusatzpositionen - ohne sie waere die Summe kleiner als das Kostentotal
  // aller Mieter, denn die Mieterzeilen speisen sich aus zwei Quellen.
  const zusatzSumme = zusatzZeile(zusaetze);
  const positionSummen = zusatzSumme ? [...summen, zusatzSumme] : summen;

  return {
    nenner, summeTage, nennerPerson, summePersonenTage, mieter: bloecke, positionSummen,
    summeKosten: runde(positionSummen.reduce((s, z) => s + z.summeKosten, 0), 2)
  };
}

/** Verteilt die Art einen erfassten Gesamtbetrag auf die Mieter? */
function verteilendeArt(art: NkPositionsart | null | undefined): boolean {
  return art === NkPositionsart.UMLAGE
      || art === NkPositionsart.UMLAGE_PERSON
      || art === NkPositionsart.ANTEIL;
}

/**
 * Nimmt Menge und Betrag einer Mieterzeile in die Zusammenstellung ihrer Position auf.
 *
 * Eine **nicht erfasste** Menge laesst die Summe unangetastet: Sonst stuende bei einer
 * Verbrauchsposition, fuer die noch niemand etwas eingetragen hat, eine 0 - und die saehe aus wie
 * eine gemessene Null.
 */
function merke(summe: NkPositionSumme | undefined,
               menge: number | null | undefined,
               betrag: number): void {
  if (!summe) {
    return;
  }
  summe.summeKosten = runde(summe.summeKosten + betrag, 2);
  if (menge != null) {
    summe.summeMenge = runde(zahl(summe.summeMenge) + menge, 3);
  }
}

/**
 * Sammelzeile aller Zusatzpositionen; `undefined`, wenn es keine gibt.
 *
 * Die Mengeneinheit bleibt leer, sobald die Zusatzpositionen **verschiedene** Einheiten mischen -
 * und dann auch die Menge: „2 Stueck plus 3 m³" ist keine Menge, sondern zwei. Die Kosten bleiben
 * in jedem Fall summierbar, denn Franken sind Franken.
 */
function zusatzZeile(zusaetze: NkZusatz[]): NkPositionSumme | undefined {
  if (zusaetze.length === 0) {
    return undefined;
  }

  let kosten = 0;
  let menge = 0;
  let einheit: Mengeneinheit | null = null;
  let einheitlich = true;

  for (const z of zusaetze) {
    kosten = runde(kosten + runde(zahl(z.menge) * zahl(z.betragProEinheit), 2), 2);
    menge += zahl(z.menge);
    if (einheit === null) {
      einheit = z.einheit ?? null;
    } else if (einheit !== z.einheit) {
      einheitlich = false;
    }
  }

  return {
    positionId: null,
    art: null,
    totalbetrag: null,
    summeMenge: einheitlich ? runde(menge, 3) : null,
    einheit: einheitlich ? einheit : null,
    summeKosten: kosten,
    nichtVerteilt: null,
    rundungsdifferenz: null,
    summeProzent: null,
    zusatz: true
  };
}

function berechneMieter(
  person: NkMieterTage,
  nenner: number,
  nennerPerson: number,
  anzahlPersonen: number,
  nummeriert: { position: NkPosition; reihenfolge: number }[],
  zusaetze: NkZusatz[],
  akonto: NkAkonto[],
  summen: NkPositionSumme[]
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
      ? zeileAusPosition(quelle.position, quelle.reihenfolge, person, nenner,
          person.tage * anzahlPersonen, nennerPerson, laufendeSumme, summen)
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
    anzahlPersonen,
    personenTage: person.tage * anzahlPersonen,
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
  personenTage: number,
  nennerPerson: number,
  laufendeSumme: number,
  summen: NkPositionSumme[]
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
    // Beide Umlagen rechnen identisch - nur der Verteilschluessel unterscheidet sich.
    case NkPositionsart.UMLAGE:
    case NkPositionsart.UMLAGE_PERSON: {
      const teil = position.art === NkPositionsart.UMLAGE_PERSON
        ? anteil(personenTage, nennerPerson)
        : anteil(person.tage, nenner);
      if (position.gesamtmenge !== null && position.gesamtmenge !== undefined) {
        zeile.menge = runde(position.gesamtmenge * teil, 3);
      }
      zeile.betrag = runde(zahl(position.totalbetrag) * teil, 2);
      // Bezugsgroesse und Prozentsatz fuer die Rechnung - in der Maske unsichtbar, aber die
      // Vorschau soll dieselbe Zeile beschreiben wie das Backend.
      zeile.bezugsbetrag = runde(zahl(position.totalbetrag), 2);
      zeile.prozentsatz = runde(teil * 100, 3);

      merke(summen.find(u => u.positionId === umlageSchluessel(position, reihenfolge)),
        zeile.menge, zeile.betrag);
      break;
    }
    case NkPositionsart.VERBRAUCH: {
      const erfasst = position.verbraeuche.find(v => v.mieterId === person.mieterId);
      zeile.menge = erfasst?.menge ?? undefined;
      zeile.betragProEinheit = position.betragProEinheit ?? undefined;
      zeile.betrag = runde(zahl(erfasst?.menge) * zahl(position.betragProEinheit), 2);
      merke(summen.find(u => u.positionId === umlageSchluessel(position, reihenfolge)),
        erfasst?.menge, zeile.betrag);
      break;
    }
    case NkPositionsart.ANTEIL: {
      // Der Prozentsatz je Mieter steht dort, wo bei VERBRAUCH die Menge steht.
      const erfasst = position.verbraeuche.find(v => v.mieterId === person.mieterId);
      zeile.prozentsatz = erfasst?.menge ?? undefined;
      zeile.bezugsbetrag = runde(zahl(position.totalbetrag), 2);
      zeile.betrag = runde(zahl(position.totalbetrag) * zahl(erfasst?.menge) / 100, 2);

      const info = summen.find(u => u.positionId === umlageSchluessel(position, reihenfolge));
      if (info) {
        // Bei ANTEIL ist die Bezugsgroesse der Prozentsatz, nicht eine Menge.
        merke(info, null, zeile.betrag);
        info.summeProzent = runde(zahl(info.summeProzent) + zahl(erfasst?.menge), 3);
      }
      break;
    }
    case NkPositionsart.ZUSCHLAG: {
      zeile.prozentsatz = position.prozentsatz ?? undefined;
      // Zwischentotal der Zeilen davor - die Groesse, auf der der Zuschlag rechnet.
      zeile.bezugsbetrag = runde(laufendeSumme, 2);
      zeile.betrag = runde(laufendeSumme * zahl(position.prozentsatz) / 100, 2);
      merke(summen.find(u => u.positionId === umlageSchluessel(position, reihenfolge)),
        null, zeile.betrag);
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
 * `NkZeile.positionId` und `NkPositionSumme.positionId` liefert. So passt die Zuordnung vor **und**
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
