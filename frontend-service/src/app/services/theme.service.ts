import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly STORAGE_KEY = 'zev-theme';
  private _isDarkMode = signal(false);

  readonly isDarkMode = this._isDarkMode.asReadonly();

  constructor() {
    this.initTheme();
  }

  private initTheme(): void {
    const stored = this.loadFromStorage();
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    const isDark = stored !== null ? stored === 'dark' : prefersDark;
    this.applyTheme(isDark);
  }

  toggleTheme(): void {
    this.applyTheme(!this._isDarkMode());
  }

  private applyTheme(dark: boolean): void {
    this._isDarkMode.set(dark);
    document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light');
    this.saveToStorage(dark ? 'dark' : 'light');
  }

  private loadFromStorage(): string | null {
    try {
      return localStorage.getItem(this.STORAGE_KEY);
    } catch {
      return null;
    }
  }

  private saveToStorage(theme: string): void {
    try {
      localStorage.setItem(this.STORAGE_KEY, theme);
    } catch {
      // localStorage nicht verfügbar – ignorieren
    }
  }
}
