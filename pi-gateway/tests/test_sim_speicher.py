"""Simulator im Speicher-Modus: Ladezustand passend zur Lade-/Entladephase
(Specs/Gerätezustand.md, §3 Pi-Gateway und §6).

Ausführen (im Verzeichnis ``pi-gateway``): ``python -m unittest discover -s tests -t .``
"""

from __future__ import annotations

import random
import unittest

from gateway.models import MeterConfig, RegisterSpec
from gateway.readers.sim_reader import SimReader


def _speicher(messpunkt: str = "Batterie-Hene") -> SimReader:
    return SimReader(MeterConfig(
        messpunkt=messpunkt,
        protokoll="sim",
        register_bezug=RegisterSpec(addr=0),
        register_einspeisung=RegisterSpec(addr=0),
    ))


class SimSpeicherSocTest(unittest.TestCase):

    def setUp(self) -> None:
        # Fester Seed: Phasenlaengen und Schritte sind zufaellig, der Test soll es nicht sein.
        random.seed(4711)

    def test_read_VieleLesevorgaenge_SocFolgtDerPhaseUndBleibtImBereich(self) -> None:
        """Steigt die Ladung, steigt der SOC (oder steht an 100); steigt die Entladung, faellt er
        (oder steht an 0). Nie beides zugleich, nie ausserhalb 0-100. Ein unabhaengig
        gewuerfelter SOC widerspraeche den Zaehlerstaenden derselben Nachricht."""
        reader = _speicher()
        vorher = reader.read()
        ladend = entladend = 0

        for _ in range(500):
            stand = reader.read()
            soc, soc_vorher = stand.zustand["soc"], vorher.zustand["soc"]
            laedt = stand.zaehlerstand_bezug > vorher.zaehlerstand_bezug
            entlaedt = stand.zaehlerstand_einspeisung > vorher.zaehlerstand_einspeisung

            self.assertNotEqual(laedt, entlaedt, "Ein Speicher laedt ODER entlaedt")
            self.assertGreaterEqual(soc, 0.0)
            self.assertLessEqual(soc, 100.0)
            if laedt:
                ladend += 1
                self.assertTrue(soc > soc_vorher or soc == 100.0,
                                f"Ladephase, aber SOC {soc_vorher} -> {soc}")
            else:
                entladend += 1
                self.assertTrue(soc < soc_vorher or soc == 0.0,
                                f"Entladephase, aber SOC {soc_vorher} -> {soc}")
            vorher = stand

        # Beide Phasen kamen vor - sonst prueft die Schleife nur eine Richtung
        self.assertGreater(ladend, 50)
        self.assertGreater(entladend, 50)

    def test_read_KeinSpeicher_KeinSoc(self) -> None:
        """Der Ladezustand kommt nur vom Speicher; an einem Zaehler verwuerfe ihn das Backend."""
        self.assertIsNone(_speicher("Wohnung-1").read().zustand)


if __name__ == "__main__":
    unittest.main()
