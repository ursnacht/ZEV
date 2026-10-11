-- Preis 0 ist zulaessig (Specs/Tarifverwaltung.md, FR-5): kostenlose Leistung, die trotzdem auf der
-- Rechnung steht. Die Meldung "Preis muss groesser als 0 sein" (V23) stimmt nicht mehr.
UPDATE zev.translation
   SET deutsch = 'Preis ist erforderlich und darf nicht negativ sein',
       englisch = 'Price is required and must not be negative'
 WHERE key = 'PREIS_IST_ERFORDERLICH';
