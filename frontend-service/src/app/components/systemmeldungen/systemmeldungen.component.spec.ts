import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import Keycloak from 'keycloak-js';
import { createSpyObj, SpyObj } from '../../../testing/spy';
import { fakeAsync, tick } from '../../../testing/fake-async';
import { SystemmeldungenComponent } from './systemmeldungen.component';
import { SystemmeldungService } from '../../services/systemmeldung.service';
import { TranslationService } from '../../services/translation.service';
import { Systemmeldung, SystemmeldungSeite } from '../../models/systemmeldung.model';

/**
 * Tests der Systemmeldungen-Seite (`Specs/Systemmeldungen.md`).
 *
 * Filter, Sortierung und Paginierung laufen **serverseitig** (FR-1.3/1.4/1.12) — die Komponente
 * übersetzt also den Zustand der Maske in eine Abfrage. Genau dort liegt der Schwerpunkt: Der
 * dreiwertige Erledigt-Filter, das Zurücksetzen auf Seite 0 bei jedem Filter- oder
 * Sortierwechsel, und die Wächter der Seitennavigation.
 */
describe('SystemmeldungenComponent', () => {
  let component: SystemmeldungenComponent;
  let fixture: ComponentFixture<SystemmeldungenComponent>;
  let systemmeldungServiceSpy: SpyObj<SystemmeldungService>;
  let translationServiceSpy: SpyObj<TranslationService>;
  let keycloakSpy: SpyObj<Keycloak>;

  const offeneMeldung: Systemmeldung = {
    id: 7,
    level: 'ERROR',
    kategorie: 'SYSTEMMELDUNG_KATEGORIE_BILANZMODELL',
    meldungKey: 'BILANZMODELL_KEINE_BILANZDATEN',
    parameter: '15.01.2024 10:15',
    erstmalsAufgetreten: '2024-01-15T10:15:00',
    zuletztAufgetreten: '2024-01-16T10:15:00',
    erledigt: false,
    erledigtAm: null,
    erledigtAutomatisch: false,
    zaehler: 3
  };

  const erledigteMeldung: Systemmeldung = {
    ...offeneMeldung, id: 8, erledigt: true, erledigtAm: '2024-02-01T09:00:00'
  };

  const seite = (items: Systemmeldung[], hatMehr = false): SystemmeldungSeite =>
    ({ items, hatMehr, page: 0 });

  const aufbauen = (canManage = true) => {
    keycloakSpy.hasRealmRole.mockReturnValue(canManage);
    fixture = TestBed.createComponent(SystemmeldungenComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  beforeEach(async () => {
    systemmeldungServiceSpy = createSpyObj<SystemmeldungService>('SystemmeldungService', [
      'getSeite', 'getKategorien', 'setErledigt', 'deleteSystemmeldung', 'deleteErledigte'
    ]);
    translationServiceSpy = createSpyObj<TranslationService>('TranslationService', ['translate']);
    keycloakSpy = createSpyObj<Keycloak>('Keycloak', ['hasRealmRole']);

    systemmeldungServiceSpy.getSeite.mockReturnValue(of(seite([offeneMeldung])));
    systemmeldungServiceSpy.getKategorien.mockReturnValue(
      of(['SYSTEMMELDUNG_KATEGORIE_BILANZMODELL', 'SYSTEMMELDUNG_KATEGORIE_MQTT']));
    systemmeldungServiceSpy.setErledigt.mockReturnValue(of(offeneMeldung));
    systemmeldungServiceSpy.deleteSystemmeldung.mockReturnValue(of(void 0));
    systemmeldungServiceSpy.deleteErledigte.mockReturnValue(of({ anzahl: 4 }));
    translationServiceSpy.translate.mockImplementation((key: string) => key);
    keycloakSpy.hasRealmRole.mockReturnValue(true);

    await TestBed.configureTestingModule({
      imports: [SystemmeldungenComponent],
      providers: [
        { provide: SystemmeldungService, useValue: systemmeldungServiceSpy },
        { provide: TranslationService, useValue: translationServiceSpy },
        { provide: Keycloak, useValue: keycloakSpy }
      ]
    }).compileComponents();
  });

  // ==================== Initialisierung ====================

  it('should load the list and the categories on init', () => {
    aufbauen();

    expect(systemmeldungServiceSpy.getSeite).toHaveBeenCalled();
    expect(systemmeldungServiceSpy.getKategorien).toHaveBeenCalled();
    expect(component.meldungen.length).toBe(1);
    expect(component.kategorien.length).toBe(2);
  });

  /** Default ist „Offene" (FR-1.3) — die Seite soll mit dem zeigen aufgehen, was zu tun ist. */
  it('should default to the open filter and newest first', () => {
    aufbauen();

    expect(component.erledigtFilter).toBe('OFFENE');
    expect(component.sortSpalte).toBe('zuletztAufgetreten');
    expect(component.sortRichtung).toBe('DESC');
    expect(systemmeldungServiceSpy.getSeite).toHaveBeenCalledWith(
      expect.objectContaining({ erledigt: false, page: 0, sortRichtung: 'DESC' }));
  });

  it('should keep the page usable when loading the categories fails', () => {
    // Die Filter-Optionen sind Beiwerk; ihr Ausfall darf die Liste nicht verhindern.
    systemmeldungServiceSpy.getKategorien.mockReturnValue(throwError(() => new Error('kaputt')));

    aufbauen();

    expect(component.kategorien).toEqual([]);
    expect(component.meldungen.length).toBe(1);
    expect(component.message).toBe('');
  });

  it('should clear the list and report an error when loading fails', () => {
    systemmeldungServiceSpy.getSeite.mockReturnValue(throwError(() => new Error('kaputt')));

    aufbauen();

    expect(component.meldungen).toEqual([]);
    expect(component.hatMehr).toBe(false);
    expect(component.loading).toBe(false);
    expect(component.message).toBe('SYSTEMMELDUNGEN_FEHLER');
    expect(component.messageType).toBe('error');
  });

  // ==================== Der dreiwertige Erledigt-Filter ====================

  /**
   * „Alle" muss `undefined` ergeben und nicht `false`.
   *
   * Das ist der Fehler, der hier am leichtesten passiert: Mit `false` zeigte „Alle" nur die
   * offenen Meldungen — und die erledigten wären unerreichbar, obwohl die Auswahl sie verspricht.
   */
  it('should send undefined for the "Alle" filter', () => {
    aufbauen();
    systemmeldungServiceSpy.getSeite.mockClear();

    component.erledigtFilter = 'ALLE';
    component.onFilterChange();

    expect(systemmeldungServiceSpy.getSeite).toHaveBeenCalledWith(
      expect.objectContaining({ erledigt: undefined }));
  });

  it('should send true for the "Erledigte" filter', () => {
    aufbauen();
    systemmeldungServiceSpy.getSeite.mockClear();

    component.erledigtFilter = 'ERLEDIGTE';
    component.onFilterChange();

    expect(systemmeldungServiceSpy.getSeite).toHaveBeenCalledWith(
      expect.objectContaining({ erledigt: true }));
  });

  it('should turn the empty category and level selections into undefined', () => {
    aufbauen();
    systemmeldungServiceSpy.getSeite.mockClear();

    component.kategorieFilter = '';
    component.levelFilter = '';
    component.onFilterChange();

    expect(systemmeldungServiceSpy.getSeite).toHaveBeenCalledWith(
      expect.objectContaining({ kategorie: undefined, level: undefined }));
  });

  it('should pass a selected category and level through', () => {
    aufbauen();
    systemmeldungServiceSpy.getSeite.mockClear();

    component.kategorieFilter = 'SYSTEMMELDUNG_KATEGORIE_MQTT';
    component.levelFilter = 'WARN';
    component.onFilterChange();

    expect(systemmeldungServiceSpy.getSeite).toHaveBeenCalledWith(
      expect.objectContaining({
        kategorie: 'SYSTEMMELDUNG_KATEGORIE_MQTT', level: 'WARN'
      }));
  });

  /** Ein Filterwechsel setzt auf Seite 0 zurück (FR-1.12) — sonst zeigte Seite 3 eines neuen
   *  Filters womöglich nichts, und die Liste wirkte leer. */
  it('should reset to page 0 when the filter changes', () => {
    aufbauen();
    component.page = 3;

    component.onFilterChange();

    expect(component.page).toBe(0);
    expect(systemmeldungServiceSpy.getSeite).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 0 }));
  });

  // ==================== Sortierung ====================

  it('should sort ascending when a new column is chosen', () => {
    aufbauen();

    component.onSort('level');

    expect(component.sortSpalte).toBe('level');
    expect(component.sortRichtung).toBe('ASC');
  });

  it('should toggle the direction when the same column is chosen again', () => {
    aufbauen();

    component.onSort('level');
    expect(component.sortRichtung).toBe('ASC');
    component.onSort('level');
    expect(component.sortRichtung).toBe('DESC');
    component.onSort('level');
    expect(component.sortRichtung).toBe('ASC');
  });

  it('should reset to page 0 when the sort changes', () => {
    aufbauen();
    component.page = 2;

    component.onSort('zaehler');

    expect(component.page).toBe(0);
  });

  // ==================== Erledigt umschalten ====================

  it('should toggle an open entry to resolved and report success', () => {
    aufbauen();

    component.onToggleErledigt(offeneMeldung);

    expect(systemmeldungServiceSpy.setErledigt).toHaveBeenCalledWith(7, true);
    expect(component.message).toBe('SYSTEMMELDUNG_ERLEDIGT');
    expect(component.messageType).toBe('success');
  });

  it('should reopen a resolved entry and report the matching message', () => {
    aufbauen();

    component.onToggleErledigt(erledigteMeldung);

    expect(systemmeldungServiceSpy.setErledigt).toHaveBeenCalledWith(8, false);
    expect(component.message).toBe('SYSTEMMELDUNG_WIEDER_OFFEN');
  });

  it('should reload the list after toggling', () => {
    aufbauen();
    systemmeldungServiceSpy.getSeite.mockClear();

    component.onToggleErledigt(offeneMeldung);

    expect(systemmeldungServiceSpy.getSeite).toHaveBeenCalled();
  });

  /**
   * Der Reopen-Konflikt aus dem Backend muss <b>wörtlich</b> angezeigt werden.
   *
   * Fiele die Komponente hier auf die generische Fehlermeldung zurück, erfährt der Benutzer nicht,
   * dass schon ein offener Eintrag desselben Keys existiert — und versucht es wieder und wieder.
   */
  it('should show the server key when reopening is rejected', () => {
    systemmeldungServiceSpy.setErledigt.mockReturnValue(
      throwError(() => ({ error: { error: 'SYSTEMMELDUNG_REOPEN_KONFLIKT' } })));
    aufbauen();

    component.onToggleErledigt(erledigteMeldung);

    expect(component.message).toBe('SYSTEMMELDUNG_REOPEN_KONFLIKT');
    expect(component.messageType).toBe('error');
  });

  it('should fall back to the generic message when the error carries no key', () => {
    systemmeldungServiceSpy.setErledigt.mockReturnValue(throwError(() => ({})));
    aufbauen();

    component.onToggleErledigt(offeneMeldung);

    expect(component.message).toBe('SYSTEMMELDUNGEN_FEHLER');
  });

  // ==================== Löschen ====================

  it('should delete a single entry and reload', () => {
    aufbauen();
    systemmeldungServiceSpy.getSeite.mockClear();

    component.onDelete(7);

    expect(systemmeldungServiceSpy.deleteSystemmeldung).toHaveBeenCalledWith(7);
    expect(component.message).toBe('SYSTEMMELDUNG_GELOESCHT');
    expect(systemmeldungServiceSpy.getSeite).toHaveBeenCalled();
  });

  it('should report an error when deleting fails', () => {
    systemmeldungServiceSpy.deleteSystemmeldung.mockReturnValue(
      throwError(() => new Error('kaputt')));
    aufbauen();

    component.onDelete(7);

    expect(component.message).toBe('SYSTEMMELDUNGEN_FEHLER');
    expect(component.messageType).toBe('error');
  });

  it('should route the kebab delete action to the delete call', () => {
    aufbauen();

    component.onMenuAction('delete', offeneMeldung);

    expect(systemmeldungServiceSpy.deleteSystemmeldung).toHaveBeenCalledWith(7);
  });

  it('should ignore an unknown kebab action', () => {
    aufbauen();

    component.onMenuAction('edit', offeneMeldung);

    expect(systemmeldungServiceSpy.deleteSystemmeldung).not.toHaveBeenCalled();
  });

  // ==================== Aufräumen: alle erledigten löschen ====================

  /**
   * Ohne Bestätigung passiert nichts (FR-1.6a).
   *
   * Die Aktion löscht alle erledigten Meldungen des Mandanten und ist nicht umkehrbar — der
   * Abbruch der Rückfrage muss deshalb wirklich abbrechen.
   */
  it('should do nothing when the confirmation is declined', () => {
    aufbauen();
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);

    component.onDeleteErledigte();

    expect(confirmSpy).toHaveBeenCalled();
    expect(systemmeldungServiceSpy.deleteErledigte).not.toHaveBeenCalled();
    confirmSpy.mockRestore();
  });

  it('should delete all resolved entries after confirmation and report the count', () => {
    aufbauen();
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);

    component.onDeleteErledigte();

    expect(systemmeldungServiceSpy.deleteErledigte).toHaveBeenCalled();
    expect(component.message).toContain('4');
    expect(component.messageType).toBe('success');
    confirmSpy.mockRestore();
  });

  it('should return to page 0 after cleaning up', () => {
    aufbauen();
    component.page = 2;
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);

    component.onDeleteErledigte();

    expect(component.page).toBe(0);
    confirmSpy.mockRestore();
  });

  it('should report an error when cleaning up fails', () => {
    systemmeldungServiceSpy.deleteErledigte.mockReturnValue(throwError(() => new Error('kaputt')));
    aufbauen();
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);

    component.onDeleteErledigte();

    expect(component.message).toBe('SYSTEMMELDUNGEN_FEHLER');
    expect(component.messageType).toBe('error');
    confirmSpy.mockRestore();
  });

  // ==================== Seitennavigation ====================

  it('should not go back before the first page', () => {
    aufbauen();
    systemmeldungServiceSpy.getSeite.mockClear();

    component.onVorherigeSeite();

    expect(component.page).toBe(0);
    expect(systemmeldungServiceSpy.getSeite).not.toHaveBeenCalled();
  });

  it('should go back one page', () => {
    aufbauen();
    component.page = 2;

    component.onVorherigeSeite();

    expect(component.page).toBe(1);
  });

  /** Ohne `hatMehr` gibt es keine nächste Seite — der Server sagt das, nicht ein Gesamt-Count. */
  it('should not advance when the server reports no more entries', () => {
    aufbauen();
    component.hatMehr = false;
    systemmeldungServiceSpy.getSeite.mockClear();

    component.onNaechsteSeite();

    expect(component.page).toBe(0);
    expect(systemmeldungServiceSpy.getSeite).not.toHaveBeenCalled();
  });

  it('should advance when the server reports more entries', () => {
    systemmeldungServiceSpy.getSeite.mockReturnValue(of(seite([offeneMeldung], true)));
    aufbauen();

    component.onNaechsteSeite();

    expect(component.page).toBe(1);
  });

  // ==================== Berechtigung und Darstellung ====================

  it('should allow managing with systemmeldungen:manage', () => {
    aufbauen(true);

    expect(component.canManage).toBe(true);
    expect(keycloakSpy.hasRealmRole).toHaveBeenCalledWith('systemmeldungen:manage');
  });

  it('should not allow managing without the permission', () => {
    aufbauen(false);

    expect(component.canManage).toBe(false);
  });

  it('should map each level to its status class', () => {
    aufbauen();

    expect(component.levelClass('ERROR')).toBe('zev-status--error');
    expect(component.levelClass('WARN')).toBe('zev-status--warning');
    expect(component.levelClass('INFO')).toBe('zev-status--info');
  });

  // ==================== Meldungsverhalten ====================

  it('should auto-dismiss a success message after 5 seconds', fakeAsync(() => {
    aufbauen();
    component.onDelete(7);
    expect(component.message).toBe('SYSTEMMELDUNG_GELOESCHT');

    tick(5000);

    expect(component.message).toBe('');
  }));

  it('should keep an error message until it is dismissed', fakeAsync(() => {
    systemmeldungServiceSpy.deleteSystemmeldung.mockReturnValue(
      throwError(() => new Error('kaputt')));
    aufbauen();
    component.onDelete(7);

    tick(10000);
    expect(component.message).toBe('SYSTEMMELDUNGEN_FEHLER');

    component.dismissMessage();
    expect(component.message).toBe('');
  }));
});
