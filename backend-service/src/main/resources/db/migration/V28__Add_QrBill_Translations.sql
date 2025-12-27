-- Translations for Swiss QR-Bill payment slip
INSERT INTO translation (key, deutsch, englisch) VALUES
    ('QR_EMPFANGSSCHEIN', 'Empfangsschein', 'Receipt'),
    ('QR_ZAHLTEIL', 'Zahlteil', 'Payment part'),
    ('QR_KONTO_ZAHLBAR_AN', 'Konto / Zahlbar an', 'Account / Payable to'),
    ('QR_ZAHLBAR_DURCH', 'Zahlbar durch', 'Payable by'),
    ('QR_WAEHRUNG', 'Währung', 'Currency'),
    ('QR_BETRAG', 'Betrag', 'Amount'),
    ('QR_ANNAHMESTELLE', 'Annahmestelle', 'Acceptance point'),
    ('QR_ZUSAETZLICHE_INFOS', 'Zusätzliche Informationen', 'Additional information')
ON CONFLICT (key) DO NOTHING;
