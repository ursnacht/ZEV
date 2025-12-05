import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';

export interface Translation {
    key: string;
    deutsch: string;
    englisch: string;
}

@Injectable({
    providedIn: 'root'
})
export class TranslationService {
    private apiUrl = 'http://localhost:8080/api/translations';
    private translations: Record<string, Record<string, string>> = { de: {}, en: {} };

    currentLang = signal<'de' | 'en'>('de');
    translationsLoaded = new BehaviorSubject<boolean>(false);

    constructor(private http: HttpClient) {
        this.loadTranslations();
    }

    loadTranslations() {
        this.http.get<Record<string, Record<string, string>>>(this.apiUrl).subscribe({
            next: (data) => {
                this.translations = data;
                this.translationsLoaded.next(true);
            },
            error: (err) => console.error('Failed to load translations', err)
        });
    }

    setLanguage(lang: 'de' | 'en') {
        this.currentLang.set(lang);
    }

    translate(key: string): string {
        const lang = this.currentLang();
        return this.translations[lang]?.[key] || key;
    }

    // Admin methods
    getAllTranslations(): Observable<Translation[]> {
        return this.http.get<Translation[]>(`${this.apiUrl}/list`);
    }

    saveTranslation(translation: Translation): Observable<Translation> {
        if (translation.key) {
            return this.http.put<Translation>(`${this.apiUrl}/${translation.key}`, translation);
        }
        return this.http.post<Translation>(this.apiUrl, translation);
    }

    createTranslation(translation: Translation): Observable<Translation> {
        return this.http.post<Translation>(this.apiUrl, translation);
    }

    deleteTranslation(key: string): Observable<void> {
        return this.http.delete<void>(`${this.apiUrl}/${key}`).pipe(
            tap(() => this.loadTranslations())
        );
    }
}
