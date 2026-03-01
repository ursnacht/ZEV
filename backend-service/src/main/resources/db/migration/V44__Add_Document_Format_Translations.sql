-- Document Format Translations
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('DOKUMENT_FORMAT', 'Dokumentformat', 'Document Format'),
('FORMAT_PDF', 'PDF', 'PDF'),
('FORMAT_PDF_BESCHREIBUNG', 'Druckfertiges PDF-Dokument', 'Print-ready PDF document'),
('FORMAT_WORD', 'Word (.docx)', 'Word (.docx)'),
('FORMAT_WORD_BESCHREIBUNG', 'Bearbeitbares Word-Dokument', 'Editable Word document'),
('PDF_EXPORTIEREN', 'Als PDF exportieren', 'Export as PDF')
ON CONFLICT (key) DO NOTHING;
