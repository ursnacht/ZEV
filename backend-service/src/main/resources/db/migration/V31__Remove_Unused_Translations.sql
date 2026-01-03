-- Remove unused i18n keys

-- Keys replaced by QR_* versions (V28)
DELETE FROM zev.translation WHERE key = 'EMPFANGSSCHEIN';
DELETE FROM zev.translation WHERE key = 'KONTO_ZAHLBAR_AN';
DELETE FROM zev.translation WHERE key = 'ZAHLBAR_DURCH';
DELETE FROM zev.translation WHERE key = 'WAEHRUNG';
DELETE FROM zev.translation WHERE key = 'ZAHLTEIL';

-- Unused keys (never referenced in code)
DELETE FROM zev.translation WHERE key = 'ACTIONS';
DELETE FROM zev.translation WHERE key = 'RECHNUNGEN_GENERIEREN';
DELETE FROM zev.translation WHERE key = 'SEITE';
DELETE FROM zev.translation WHERE key = 'STATUS_FEHLEND';
DELETE FROM zev.translation WHERE key = 'STATUS_TEILWEISE';
DELETE FROM zev.translation WHERE key = 'STATUS_VOLLSTAENDIG';
DELETE FROM zev.translation WHERE key = 'VORHERIGER_MONAT';

-- Remove test keys created during E2E tests
DELETE FROM zev.translation WHERE key LIKE 'testkey_%';
