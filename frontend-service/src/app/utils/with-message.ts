export type MessageType = 'success' | 'error' | 'warning' | '';

export abstract class WithMessage {
  message = '';
  messageType: MessageType = '';

  showMessage(message: string, type: Exclude<MessageType, ''>): void {
    this.message = message;
    this.messageType = type;
    if (type === 'success') {
      setTimeout(() => { this.message = ''; this.messageType = ''; }, 5000);
    }
  }

  dismissMessage(): void {
    this.message = '';
    this.messageType = '';
  }
}
