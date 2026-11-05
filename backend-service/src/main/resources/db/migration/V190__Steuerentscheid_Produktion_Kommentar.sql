-- Einspeisesteuerung: Produktion mit Vorzeichen statt als Betrag (Specs/Einspeisesteuerung.md, FR-2, Anhang A.3)
COMMENT ON COLUMN zev.steuerentscheid.produktion IS
    'Netto-Erzeugung der PRODUCER im Intervall in kWh (-Summe total); negativ, wenn der Wechselrichter netto bezieht, z.B. beim Nachladen der Batterie aus dem Netz';
