# Einzahlungsschein als Text - Umsetzungsplan

## Übersicht

| Aspekt | Details |
|--------|---------|
| **Spezifikation** | [Einzahlungsschein.md](Einzahlungsschein.md) |
| **Betroffene Datei** | `backend-service/src/main/java/ch/nacht/service/RechnungPdfService.java` |
| **Bibliotheken** | `net.codecrete.qrbill` (bestehend), `OpenHTMLToPDF` (bestehend) |
| **Geschätzter Aufwand** | Klein-Mittel (hauptsächlich HTML/CSS Layout) |

---

## Phasen-Übersicht

| Phase | Beschreibung | Status |
|-------|--------------|--------|
| 1 | [QR-Code isoliert generieren](#phase-1-qr-code-isoliert-generieren) | ✅ Abgeschlossen |
| 2 | [HTML/CSS Layout für Einzahlungsschein](#phase-2-htmlcss-layout-für-einzahlungsschein) | ✅ Abgeschlossen |
| 3 | [Integration in RechnungPdfService](#phase-3-integration-in-rechnungpdfservice) | ✅ Abgeschlossen |
| 4 | [Tests und Validierung](#phase-4-tests-und-validierung) | ⬜ Offen |

**Legende:** ⬜ Offen | 🔄 In Arbeit | ✅ Abgeschlossen

---

## Phase 1: QR-Code isoliert generieren

**Ziel:** Nur den QR-Code als PNG generieren (ohne umgebenden Text)

### Aufgaben

| Nr | Aufgabe | Status |
|----|---------|--------|
| 1.1 | `generateQrBillPng()` Methode anpassen: `OutputSize.QR_CODE_ONLY` statt `QR_BILL_ONLY` | ✅ |
| 1.2 | QR-Code Grösse auf 46x46mm setzen (gemäss SIX-Standard) | ✅ |
| 1.3 | Methode umbenennen zu `generateQrCodePng()` | ✅ |

### Code-Änderung

```java
// Vorher (Zeile 279-280):
bill.getFormat().setOutputSize(OutputSize.QR_BILL_ONLY);
bill.getFormat().setGraphicsFormat(GraphicsFormat.PNG);

// Nachher:
bill.getFormat().setOutputSize(OutputSize.QR_CODE_ONLY);
bill.getFormat().setGraphicsFormat(GraphicsFormat.PNG);
```

---

## Phase 2: HTML/CSS Layout für Einzahlungsschein

**Ziel:** Swiss QR-Bill Layout in HTML/CSS umsetzen

### Aufgaben

| Nr | Aufgabe | Status |
|----|---------|--------|
| 2.1 | CSS-Styles für Einzahlungsschein definieren (Empfangsschein + Zahlteil) | ✅ |
| 2.2 | Empfangsschein (links, 62mm) als HTML-Struktur | ✅ |
| 2.3 | Zahlteil (rechts, 148mm) als HTML-Struktur | ✅ |
| 2.4 | QR-Code Bild positionieren (rechts oben im Zahlteil) | ✅ |
| 2.5 | Schriftgrössen gemäss Standard (11pt Titel, 10pt Werte, 8pt IBAN) | ✅ |
| 2.6 | Trennlinie/Perforierung zwischen Empfangsschein und Zahlteil | ✅ |
| 2.7 | Eckmarkierungen für Betragsfeld (optional, als CSS-Border) | ✅ |

### Layout-Struktur

```html
<div class="qr-bill">
  <!-- Empfangsschein (links) -->
  <div class="empfangsschein">
    <div class="title">Empfangsschein</div>
    <div class="section">
      <div class="heading">Konto / Zahlbar an</div>
      <div class="value iban">[IBAN formatiert]</div>
      <div class="value">[Empfänger Name]</div>
      <div class="value">[Empfänger Adresse]</div>
    </div>
    <div class="section">
      <div class="heading">Zahlbar durch</div>
      <div class="value">[Zahler Name]</div>
      <div class="value">[Zahler Adresse]</div>
    </div>
    <div class="amount-section">
      <div class="currency">Währung<br/>CHF</div>
      <div class="amount">Betrag<br/>[Betrag]</div>
    </div>
    <div class="acceptance">Annahmestelle</div>
  </div>

  <!-- Zahlteil (rechts) -->
  <div class="zahlteil">
    <div class="title">Zahlteil</div>
    <div class="qr-code">
      <img src="data:image/png;base64,[QR-Code]"/>
    </div>
    <div class="amount-section">
      <div class="currency">Währung<br/>CHF</div>
      <div class="amount">Betrag<br/>[Betrag]</div>
    </div>
    <div class="details">
      <div class="section">
        <div class="heading">Konto / Zahlbar an</div>
        <div class="value iban">[IBAN formatiert]</div>
        <div class="value">[Empfänger Name]</div>
        <div class="value">[Empfänger Adresse]</div>
      </div>
      <div class="section">
        <div class="heading">Zusätzliche Informationen</div>
        <div class="value">[Mitteilung]</div>
      </div>
      <div class="section">
        <div class="heading">Zahlbar durch</div>
        <div class="value">[Zahler Name]</div>
        <div class="value">[Zahler Adresse]</div>
      </div>
    </div>
  </div>
</div>
```

### CSS-Dimensionen (gemäss SIX-Standard)

| Element | Breite | Höhe |
|---------|--------|------|
| Gesamter Einzahlungsschein | 210mm | 105mm |
| Empfangsschein | 62mm | 105mm |
| Zahlteil | 148mm | 105mm |
| QR-Code | 46mm | 46mm |
| Schweizer Kreuz im QR | 7mm | 7mm |

---

## Phase 3: Integration in RechnungPdfService

**Ziel:** Neue HTML-Struktur in die bestehende PDF-Generierung einbauen

### Aufgaben

| Nr | Aufgabe | Status |
|----|---------|--------|
| 3.1 | Neue Methode `buildQrBillHtml(RechnungDTO rechnung)` erstellen | ✅ |
| 3.2 | IBAN-Formatierung (Gruppen à 4 Zeichen) implementieren | ✅ |
| 3.3 | Betrag-Formatierung (mit Leerzeichen: "1 234.50") | ✅ |
| 3.4 | `buildHtml()` Methode anpassen: neues QR-Bill HTML einbinden | ✅ |
| 3.5 | Alte `<img class="qr-image">` Einbettung entfernen | ✅ |
| 3.6 | CSS-Styles für QR-Bill in den Style-Block einfügen | ✅ |
| 3.7 | Flyway-Migration für QR-Bill Übersetzungen erstellen | ✅ |

### Hilfsmethoden

```java
/**
 * Formatiert IBAN in Vierergruppen: CH12 3456 7890 1234 5678 9
 */
private String formatIban(String iban) {
    String clean = iban.replace(" ", "");
    StringBuilder formatted = new StringBuilder();
    for (int i = 0; i < clean.length(); i++) {
        if (i > 0 && i % 4 == 0) {
            formatted.append(" ");
        }
        formatted.append(clean.charAt(i));
    }
    return formatted.toString();
}

/**
 * Formatiert Betrag mit Leerzeichen als Tausendertrennzeichen
 */
private String formatBetragQrBill(double value) {
    // Format: 1 234.50
    java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.00");
    java.text.DecimalFormatSymbols symbols = new java.text.DecimalFormatSymbols();
    symbols.setGroupingSeparator(' ');
    symbols.setDecimalSeparator('.');
    df.setDecimalFormatSymbols(symbols);
    return df.format(value);
}
```

---

## Phase 4: Tests und Validierung

**Ziel:** Sicherstellen, dass der Einzahlungsschein korrekt funktioniert

### Aufgaben

| Nr | Aufgabe | Status |
|----|---------|--------|
| 4.1 | Bestehende Unit-Tests ausführen (`RechnungServiceTest`) | ⬜ |
| 4.2 | Manuell PDF generieren und IBAN-Selektion testen | ⬜ |
| 4.3 | Manuell PDF generieren und Betrag-Selektion testen | ⬜ |
| 4.4 | QR-Code mit E-Banking App scannen und validieren | ⬜ |
| 4.5 | Layout-Prüfung: Abstände und Schriftgrössen | ⬜ |
| 4.6 | Test mit langem Namen/Adresse (Umbruch) | ⬜ |
| 4.7 | Test ohne Zahler (leeres "Zahlbar durch" Feld) | ⬜ |

### Testfälle

| Test | Erwartetes Ergebnis |
|------|---------------------|
| IBAN kopieren | `CH93 0076 2011 6238 5295 7` (formatiert) |
| Betrag kopieren | `123.45` oder `1 234.50` |
| QR-Code scannen | E-Banking erkennt alle Daten korrekt |
| Langer Name | Text wird umgebrochen, max. 70 Zeichen pro Zeile |

---

## Dateien

| Datei | Änderung |
|-------|----------|
| `RechnungPdfService.java` | Hauptänderungen (QR-Code, HTML-Layout) |
| `RechnungServiceTest.java` | Ggf. erweitern |

---

## Referenzen

- [Swiss QR-Bill Spezifikation (SIX)](https://www.six-group.com/de/products-services/banking-services/payment-standardization/standards/qr-bill.html)
- [qrbill-generator Dokumentation](https://github.com/manuelbl/SwissQRBill)
- [Style Guide für QR-Rechnung (PDF)](https://www.six-group.com/dam/download/banking-services/standardization/qr-bill/style-guide-qr-bill-de.pdf)
