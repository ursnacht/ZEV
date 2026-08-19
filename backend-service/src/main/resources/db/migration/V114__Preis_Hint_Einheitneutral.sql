-- Der Hinweis unter dem Preisfeld behauptete fest "pro kWh". Das stimmte schon fuer die
-- Grundgebuehr nicht (CHF pro Monat) und mit dem Tariftyp ZUSATZ erst recht nicht.
-- Die Bezugsgroesse steht jetzt im Label des Felds ("Preis (CHF/Stück)"), der Hinweis
-- beschraenkt sich deshalb auf das Zahlenformat.
UPDATE zev.translation
   SET deutsch  = 'Bis zu fünf Nachkommastellen (z.B. 0.20000)',
       englisch = 'Up to five decimal places (e.g. 0.20000)'
 WHERE key = 'PREIS_HINT';
