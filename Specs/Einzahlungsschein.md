# Einzahlungsschein als Text

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Der Einzahlungsschein soll als Text auf den Rechnungen ausgegeben werden, damit IBAN, Betrag und Mitteilung selektierbar/kopierbar sind.
* **Warum machen wir das:** Der Rechnungsempfänger kann so falls nötig IBAN-Nummer oder andere Angaben auf dem PDF selektieren und kopieren - z.B. für manuelle Zahlungseingabe.
* **Aktueller Stand:** Der ganze Einzahlungsschein wird als PNG-Bild in die Rechnungen eingefügt (`RechnungPdfService.java`, Zeile 280: `GraphicsFormat.PNG`). Nichts ist selektierbar.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Ablauf / Flow
1. Der Ablauf bleibt unverändert
2. `RechnungPdfService.generatePdf()` generiert weiterhin das PDF
3. Die QR-Bill Sektion wird neu als HTML mit selektierbarem Text gerendert

### FR-2: Persistierung
* Die Rechnungen werden wie bisher nicht persistiert.

### FR-3: Layout (Swiss QR-Bill Standard)

Der Einzahlungsschein besteht aus zwei Teilen gemäss SIX-Standard:

```
┌────────────────────────────────────────────────────────────────────────────┐
│                                                                            │
│  ┌─────────────────────┐  ┌─────────────────────────────────────────────┐  │
│  │  EMPFANGSSCHEIN     │  │  ZAHLTEIL        Konto / Zahlbar an         │  │
│  │   (62mm breit)      │  │   (148mm breit)  [IBAN]                     │  │
│  │                     │  │                  [Name]                     │  │
│  │  Konto / Zahlbar an │  │  ┌────────────┐  [Adressen]                 │  │
│  │  [IBAN]             │  │  │  QR-Code   │                             │  │
│  │  [Name]             │  │  │  (46x46mm) │  Zusätzliche Informationen  │  │
│  │  [Adresse]          │  │  │            │  [Mitteilung]               │  │
│  │                     │  │  │            │                             │  │
│  │  Zahlbar durch      │  │  │            │  Zahlbar durch              │  │
│  │  [Name]             │  │  └────────────┘  [Name]                     │  │
│  │  [Adresse]          │  │                  [Adresse]                  │  │
│  │                     │  │                                             │  │
│  │  Währung  Betrag    │  │  Währung  Betrag                            │  │
│  │  CHF      [Betrag]  │  │  CHF      [Betrag]                          │  │ 
│  └─────────────────────┘  └─────────────────────────────────────────────┘  │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

**Elemente:**

| Element | Darstellung | Selektierbar |
|---------|-------------|--------------|
| QR-Code | Bild (PNG) | Nein |
| IBAN | Text | **Ja** |
| Betrag | Text | **Ja** |
| Empfänger (Name, Adresse) | Text | **Ja** |
| Zahler (Name, Adresse) | Text | **Ja** |
| Mitteilung | Text | **Ja** |
| Schweizer Kreuz im QR | Teil des QR-Bildes | Nein |
| Ecken/Rahmen Betrag | Optional als Bild oder CSS | Nein |

**Schriftart:** Arial oder Liberation Sans, verschiedene Grössen gemäss Standard:
- Überschriften: 11pt, fett
- Werte: 10pt
- IBAN: 8pt (wegen Länge)

### FR-4: QR-Code Generierung
* Nur der QR-Code selbst wird als Bild generiert (nicht der ganze Einzahlungsschein)
* Die `qrbill`-Bibliothek unterstützt dies mit `OutputSize.QR_CODE_ONLY`
* Grösse: 46x46mm gemäss Standard

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)
* [ ] IBAN kann auf dem Rechnungs-PDF selektiert und kopiert werden
* [ ] Betrag kann selektiert und kopiert werden
* [ ] Mitteilung (Zusätzliche Informationen) kann selektiert werden
* [ ] Empfänger-Adresse kann selektiert werden
* [ ] Zahler-Adresse kann selektiert werden
* [ ] Der Einzahlungsschein kann in E-Banking hochgeladen werden und wird akzeptiert (QR-Code lesbar)
* [ ] Layout entspricht dem Swiss QR-Bill Standard (SIX)
* [ ] Bestehende Unit-Tests laufen weiterhin durch

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Die Performance spielt bei der Rechnungserstellung keine grosse Rolle.

### NFR-2: Sicherheit
* Die Sicherheit bleibt unverändert.

### NFR-3: Kompatibilität
* Der Einzahlungsschein entspricht den schweizerischen Normen (Swiss QR-Bill gemäss SIX).
* Referenz: https://www.six-group.com/de/products-services/banking-services/payment-standardization/standards/qr-bill.html

### NFR-4: Bibliothek
* Die bestehende `net.codecrete.qrbill` Bibliothek wird weiterhin verwendet
* Änderung: `OutputSize.QR_CODE_ONLY` statt `OutputSize.QR_BILL_ONLY`
* Das Layout wird in HTML/CSS umgesetzt (OpenHTMLToPDF bleibt)
* Da OpenHTMLToPDF kein Flexbox unterstützt, muss das Layout mit HTML-Tabellen umgesetzt werden.

## 5. Edge Cases & Fehlerbehandlung
* Fehlerbehandlung bleibt unverändert
* Bei sehr langen Namen/Adressen: Text wird gemäss Standard gekürzt (max. 70 Zeichen pro Zeile)
* Bei fehlendem Zahler (Debtor): "Zahlbar durch"-Feld zeigt leeren Rahmen

## 6. Abgrenzung / Out of Scope
* Keine Änderung am Rechnungs-Inhalt (nur Einzahlungsschein-Teil)
* Keine Änderung an der QR-Bill Datenstruktur
* Keine Unterstützung für SCOR oder QRR Referenzen (bleibt bei unstrukturierter Mitteilung)

## 7. Offene Fragen
* ~~Wahl der Bibliothek~~ → Bestehende `net.codecrete.qrbill` + OpenHTMLToPDF