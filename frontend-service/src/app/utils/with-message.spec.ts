import { WithMessage } from './with-message';

class Testling extends WithMessage {}

describe('WithMessage', () => {
  let testling: Testling;

  beforeEach(() => {
    vi.useFakeTimers();
    testling = new Testling();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('showMessage', () => {
    it('should show a success message and remove it after five seconds', () => {
      testling.showMessage('Gespeichert', 'success');
      expect(testling.message).toBe('Gespeichert');
      expect(testling.messageType).toBe('success');

      vi.advanceTimersByTime(4999);
      expect(testling.message).toBe('Gespeichert');

      vi.advanceTimersByTime(1);
      expect(testling.message).toBe('');
      expect(testling.messageType).toBe('');
    });

    it('should keep an error message until it is dismissed', () => {
      testling.showMessage('Fehlgeschlagen', 'error');

      vi.advanceTimersByTime(60_000);

      expect(testling.message).toBe('Fehlgeschlagen');
      expect(testling.messageType).toBe('error');
    });

    it('should give a second success message its own five seconds', () => {
      // Der Timer der ersten Meldung darf die zweite nicht mitloeschen: Wer innert fuenf Sekunden
      // zweimal etwas ausloeste, sah die zweite Meldung sonst nur kurz.
      testling.showMessage('Erste', 'success');
      vi.advanceTimersByTime(3000);
      testling.showMessage('Zweite', 'success');

      vi.advanceTimersByTime(2000);
      expect(testling.message).toBe('Zweite');

      vi.advanceTimersByTime(2999);
      expect(testling.message).toBe('Zweite');

      vi.advanceTimersByTime(1);
      expect(testling.message).toBe('');
    });

    it('should not let an earlier success timer remove a later error message', () => {
      // Der eigentliche Schaden: Eine Fehlermeldung, die auf einen Erfolg folgte, verschwand von
      // selbst - obwohl sie stehen bleiben soll, bis sie weggeklickt wird.
      testling.showMessage('Gespeichert', 'success');
      vi.advanceTimersByTime(1000);
      testling.showMessage('Fehlgeschlagen', 'error');

      vi.advanceTimersByTime(60_000);

      expect(testling.message).toBe('Fehlgeschlagen');
      expect(testling.messageType).toBe('error');
    });
  });

  describe('dismissMessage', () => {
    it('should remove the message at once', () => {
      testling.showMessage('Fehlgeschlagen', 'error');

      testling.dismissMessage();

      expect(testling.message).toBe('');
      expect(testling.messageType).toBe('');
    });

    it('should cancel the pending timer so it cannot remove a later message', () => {
      testling.showMessage('Gespeichert', 'success');
      testling.dismissMessage();
      testling.showMessage('Fehlgeschlagen', 'error');

      vi.advanceTimersByTime(60_000);

      expect(testling.message).toBe('Fehlgeschlagen');
    });
  });
});
