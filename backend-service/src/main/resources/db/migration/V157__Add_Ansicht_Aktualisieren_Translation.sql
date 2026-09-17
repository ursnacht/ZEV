-- Einspeisesteuerung: Schaltflaeche "Aktualisieren" (Specs/Einspeisesteuerung.md, FR-5)
--
-- WARUM EIN EIGENER KEY und nicht der vorhandene 'AKTUALISIEREN': Der traegt englisch "Update"
-- und ist in fuenf Formularen das Submit-Label ("Aktualisieren" statt "Erstellen", z.B.
-- tarif-form.component.html:90). Auf einer Schaltflaeche, die nur neu laedt, hiesse das im
-- englischen UI "Update" - also "Daten aendern", das Gegenteil dessen, was der Knopf tut.
-- Deutsch sind beide Texte gleich; erst die Uebersetzung trennt sie.
--
-- KEIN Praefix STEUERUNG_: Der Text ist nicht an dieses Feature gebunden. Dieselbe Schaltflaeche
-- passt auf jede Ansicht, die sich nachladen laesst.
--
-- 'HEUTE' wird NICHT neu angelegt - der Key existiert seit V65 (Debitor-Schnellaktion) mit
-- genau dieser Bedeutung: Datum auf den heutigen Tag setzen.

INSERT INTO zev.translation (key, deutsch, englisch) VALUES

('ANSICHT_AKTUALISIEREN',
 'Aktualisieren',
 'Refresh')

ON CONFLICT (key) DO NOTHING;
