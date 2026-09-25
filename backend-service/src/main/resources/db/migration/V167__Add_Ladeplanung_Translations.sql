-- Ladeplanung: Einstrahlungsprognose in Chart, Tabelle und Einstellungen (Specs/Ladeplanung.md)

INSERT INTO zev.translation (key, deutsch, englisch) VALUES

('LADEPLANUNG_EINSTRAHLUNG',
 'Einstrahlung',
 'Irradiance'),

('LADEPLANUNG_PROGNOSE',
 'Erwartete Erzeugung',
 'Expected generation'),

('LADEPLANUNG_BREITENGRAD',
 'Breitengrad',
 'Latitude'),

('LADEPLANUNG_LAENGENGRAD',
 'Längengrad',
 'Longitude'),

('LADEPLANUNG_AZIMUT',
 'Ausrichtung (0 = Süd, −90 = Ost, 90 = West)',
 'Orientation (0 = south, −90 = east, 90 = west)'),

('LADEPLANUNG_NEIGUNG',
 'Neigung (0 = flach, 90 = senkrecht)',
 'Tilt (0 = flat, 90 = vertical)'),

('LADEPLANUNG_HISTORIE_TAGE',
 'Tage für den Umrechnungsfaktor',
 'Days for the conversion factor'),

('LADEPLANUNG_STANDORT_HINWEIS',
 'Breitengrad und Längengrad der Anlage, aus der Karte abgelesen. Ohne diese Angaben wird keine Einstrahlungsprognose abgerufen.',
 'Latitude and longitude of the installation, read off a map. Without them no irradiance forecast is fetched.'),

('LADEPLANUNG_AZIMUT_HINWEIS',
 'Achtung, nicht die meteorologische Zählweise: 0 bedeutet Süd, −90 Ost, 90 West. Eine Anlage nach Süden trägt also 0, nicht 180.',
 'Note, not the meteorological convention: 0 means south, −90 east, 90 west. A south-facing installation is 0, not 180.'),

('LADEPLANUNG_FAKTOR_HINWEIS',
 'Über so viele Tage wird gelernt, wie viele Kilowattstunden die Anlage je W/m² Einstrahlung erzeugt. Der Faktor enthält damit Verschattung, Verschmutzung und Alterung, ohne dass sie erfasst werden müssen. Vorgabe 28 Tage.',
 'The conversion from W/m² irradiance to kilowatt-hours is learned over this many days. The factor thus covers shading, soiling and ageing without anyone recording them. Default 28 days.'),

('LADEPLANUNG_PROGNOSE_FEHLER',
 'Die Einstrahlungsprognose konnte nicht abgerufen werden',
 'Could not fetch the irradiance forecast'),

('LADEPLANUNG_QUELLE',
 'Wetterdaten von Open-Meteo.com (CC BY 4.0), Modell MeteoSchweiz ICON-CH1',
 'Weather data from Open-Meteo.com (CC BY 4.0), model MeteoSwiss ICON-CH1')

ON CONFLICT (key) DO NOTHING;

-- Ueberschrift des neuen Abschnitts auf der Lizenzseite. Getrennte Anweisung, damit sie auch dann
-- greift, wenn die Schluessel oben schon existieren (ON CONFLICT DO NOTHING wirkt je Zeile).
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('LIZENZEN_DATENQUELLEN', 'Datenquellen', 'Data sources')
ON CONFLICT (key) DO NOTHING;
