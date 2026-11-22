export type MessageType = 'success' | 'error' | 'warning' | '';

/** So lange bleibt eine Erfolgsmeldung stehen, bevor sie von selbst verschwindet. */
const ERFOLG_SICHTBAR_MS = 5000;

export abstract class WithMessage {
  message = '';
  messageType: MessageType = '';

  /** Laufender Ausblend-Timer der letzten Erfolgsmeldung. */
  private ausblendTimer?: ReturnType<typeof setTimeout>;

  /**
   * Zeigt eine Meldung. Eine Erfolgsmeldung verschwindet nach 5 Sekunden von selbst, Fehler und
   * Warnungen bleiben, bis sie weggeklickt oder ersetzt werden.
   *
   * Der Timer einer **früheren** Erfolgsmeldung wird dabei abgebrochen. Sonst löschte er die
   * neue Meldung mit: Wer innert fünf Sekunden zweimal etwas auslöste, sah die zweite Meldung nur
   * kurz — und eine Fehlermeldung, die auf einen Erfolg folgte, verschwand von selbst.
   */
  showMessage(message: string, type: Exclude<MessageType, ''>): void {
    this.brecheAusblendenAb();
    this.message = message;
    this.messageType = type;
    if (type === 'success') {
      this.ausblendTimer = setTimeout(() => {
        this.message = '';
        this.messageType = '';
        this.ausblendTimer = undefined;
      }, ERFOLG_SICHTBAR_MS);
    }
  }

  dismissMessage(): void {
    this.brecheAusblendenAb();
    this.message = '';
    this.messageType = '';
  }

  private brecheAusblendenAb(): void {
    if (this.ausblendTimer !== undefined) {
      clearTimeout(this.ausblendTimer);
      this.ausblendTimer = undefined;
    }
  }
}
