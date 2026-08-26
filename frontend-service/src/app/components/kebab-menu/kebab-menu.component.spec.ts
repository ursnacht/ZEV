import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { KebabMenuComponent, KebabMenuItem } from './kebab-menu.component';
import { TranslationService } from '../../services/translation.service';

/**
 * Tests des Kebab-Menüs.
 *
 * Die Komponente steckt in jeder Liste des Projekts und war bisher nur über E2E abgedeckt. Ihr
 * heikler Teil sind die **globalen Listener**: Sie hängt beim Öffnen einen Klick- und einen
 * Tastatur-Listener an `document` und muss sie beim Schliessen, beim Auswählen eines Eintrags und
 * beim Zerstören wieder abräumen. Ein vergessener Listener wäre ein Leck, das mit jeder
 * Tabellenzeile wächst — und in einer Liste mit fünfzig Zeilen hängen fünfzig Menüs.
 *
 * Dazu kommt `ChangeDetectionStrategy.OnPush`: Der Weg über den Dokument-Klick läuft
 * ausserhalb von Angular und muss die Ansicht selbst zur Prüfung anmelden. Deshalb prüfen die
 * Tests hier am DOM und nicht nur am Feld `isOpen`.
 */
describe('KebabMenuComponent', () => {

  const items: KebabMenuItem[] = [
    { label: 'BEARBEITEN', action: 'edit', icon: 'edit-2' },
    { label: 'LOESCHEN', action: 'delete', danger: true, icon: 'trash-2' },
    { label: 'KOPIEREN', action: 'copy' }
  ];

  @Component({
    standalone: true,
    imports: [KebabMenuComponent],
    template: `
      <div class="ausserhalb">daneben</div>
      <app-kebab-menu [items]="items" (itemClick)="gewaehlt = gewaehlt.concat($event)"></app-kebab-menu>
    `
  })
  class HostComponent {
    items = items;
    gewaehlt: string[] = [];
  }

  let fixture: ComponentFixture<HostComponent>;
  let host: HostComponent;

  const menu = () => fixture.nativeElement.querySelector('.zev-kebab-menu') as HTMLElement;
  const kebabButton = () => fixture.nativeElement.querySelector('.zev-kebab-button') as HTMLElement;
  const eintraege = () => Array.from(
    fixture.nativeElement.querySelectorAll('.zev-kebab-menu__item')) as HTMLElement[];
  const istOffen = () => menu().classList.contains('zev-kebab-menu--open');

  /** Öffnet das Menü über die Schaltfläche — so, wie ein Benutzer es tut. */
  const oeffne = () => {
    kebabButton().click();
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [
        { provide: TranslationService, useValue: { translate: (k: string) => k } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(HostComponent);
    host = fixture.componentInstance;
    fixture.detectChanges();
  });

  // ==================== Darstellung ====================

  it('should render one entry per item', () => {
    expect(eintraege().length).toBe(3);
  });

  it('should start closed', () => {
    expect(istOffen()).toBe(false);
  });

  it('should mark a dangerous entry', () => {
    const [bearbeiten, loeschen] = eintraege();

    expect(loeschen.classList.contains('zev-kebab-menu__item--danger')).toBe(true);
    expect(bearbeiten.classList.contains('zev-kebab-menu__item--danger')).toBe(false);
  });

  it('should render an icon only where one is given', () => {
    const [bearbeiten, , kopieren] = eintraege();

    expect(bearbeiten.querySelector('app-icon')).toBeTruthy();
    expect(kopieren.querySelector('app-icon')).toBeNull();
  });

  it('should render an empty menu without items', () => {
    host.items = [];
    fixture.detectChanges();

    expect(eintraege().length).toBe(0);
    // Die Schaltflaeche bleibt - ob Eintraege da sind, entscheidet der Aufrufer.
    expect(kebabButton()).toBeTruthy();
  });

  // ==================== Öffnen und Schliessen ====================

  it('should open on the kebab button', () => {
    oeffne();

    expect(istOffen()).toBe(true);
  });

  it('should close on a second click of the kebab button', () => {
    oeffne();

    kebabButton().click();
    fixture.detectChanges();

    expect(istOffen()).toBe(false);
  });

  // ==================== Auswahl eines Eintrags ====================

  it('should emit the action of the chosen entry', () => {
    oeffne();

    eintraege()[1].click();
    fixture.detectChanges();

    expect(host.gewaehlt).toEqual(['delete']);
  });

  it('should close after choosing an entry', () => {
    // Sonst blieb das Menue ueber der Liste stehen, waehrend die Aktion schon laeuft.
    oeffne();

    eintraege()[0].click();
    fixture.detectChanges();

    expect(istOffen()).toBe(false);
  });

  it('should emit the action of each entry', () => {
    for (const index of [0, 1, 2]) {
      oeffne();
      eintraege()[index].click();
      fixture.detectChanges();
    }

    expect(host.gewaehlt).toEqual(['edit', 'delete', 'copy']);
  });

  // ==================== Klick daneben ====================

  it('should close on a click outside', () => {
    oeffne();

    fixture.nativeElement.querySelector('.ausserhalb').click();
    fixture.detectChanges();

    expect(istOffen()).toBe(false);
  });

  it('should stay open on a click inside the menu', () => {
    // Der Rand des Menues gehoert dazu; nur ein Klick DANEBEN schliesst.
    oeffne();

    menu().click();
    fixture.detectChanges();

    expect(istOffen()).toBe(true);
  });

  /**
   * Der Dokument-Listener hängt in der **Capture-Phase**.
   *
   * Das ist keine Kosmetik: Tabellenzeilen tragen eigene Klick-Handler, und einer davon mit
   * `stopPropagation()` würde ein in der Bubble-Phase gebundenes Schliessen verhindern — das Menü
   * blieb offen, während man längst etwas anderes anklickt.
   */
  it('should close even when the outside handler stops propagation', () => {
    oeffne();
    const daneben = fixture.nativeElement.querySelector('.ausserhalb') as HTMLElement;
    daneben.addEventListener('click', event => event.stopPropagation());

    daneben.click();
    fixture.detectChanges();

    expect(istOffen()).toBe(false);
  });

  // ==================== Tastatur ====================

  it('should close on Escape', () => {
    oeffne();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();

    expect(istOffen()).toBe(false);
  });

  it('should ignore other keys', () => {
    oeffne();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'a' }));
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    fixture.detectChanges();

    expect(istOffen()).toBe(true);
  });

  // ==================== Globale Listener ====================

  /**
   * Solange das Menü zu ist, hängt kein Listener am Dokument.
   *
   * In einer Liste mit fünfzig Zeilen hängen fünfzig Menüs. Würden alle dauerhaft lauschen, liefe
   * bei jedem Klick irgendwo auf der Seite fünfzigmal Code — und `close()` müsste den Zustand für
   * jedes einzelne prüfen.
   */
  it('should not listen on the document while closed', () => {
    const spy = vi.spyOn(document, 'addEventListener');

    // Frisch erzeugt und nicht geoeffnet: keine Listener.
    const neu = TestBed.createComponent(HostComponent);
    neu.detectChanges();

    expect(spy).not.toHaveBeenCalledWith('click', expect.anything(), true);
    spy.mockRestore();
  });

  it('should remove the document listeners on close', () => {
    const entfernt = vi.spyOn(document, 'removeEventListener');
    oeffne();

    kebabButton().click();
    fixture.detectChanges();

    expect(entfernt).toHaveBeenCalledWith('click', expect.anything(), true);
    expect(entfernt).toHaveBeenCalledWith('keydown', expect.anything());
    entfernt.mockRestore();
  });

  it('should remove the document listeners after choosing an entry', () => {
    const entfernt = vi.spyOn(document, 'removeEventListener');
    oeffne();

    eintraege()[0].click();
    fixture.detectChanges();

    expect(entfernt).toHaveBeenCalledWith('click', expect.anything(), true);
    entfernt.mockRestore();
  });

  /**
   * Beim Zerstören müssen die Listener weg — sonst hält ein zerstörtes Menü die Komponente am
   * Leben und ein späterer Klick liefe in eine Ansicht, die es nicht mehr gibt.
   */
  it('should remove the document listeners on destroy', () => {
    oeffne();
    const entfernt = vi.spyOn(document, 'removeEventListener');

    fixture.destroy();

    expect(entfernt).toHaveBeenCalledWith('click', expect.anything(), true);
    expect(entfernt).toHaveBeenCalledWith('keydown', expect.anything());
    entfernt.mockRestore();
  });

  it('should not react to a document click after being destroyed', () => {
    oeffne();
    fixture.destroy();

    // Wirft der Listener hier, war er nicht abgeraeumt.
    expect(() => {
      document.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    }).not.toThrow();
  });

  it('should tolerate being destroyed while closed', () => {
    expect(() => fixture.destroy()).not.toThrow();
  });

  // ==================== Mehrere Menüs nebeneinander ====================

  @Component({
    standalone: true,
    imports: [KebabMenuComponent],
    template: `
      <app-kebab-menu class="erstes" [items]="items"></app-kebab-menu>
      <app-kebab-menu class="zweites" [items]="items"></app-kebab-menu>
    `
  })
  class ZweiMenuesHost {
    items = items;
  }

  /**
   * Zwei Menüs in derselben Liste dürfen sich nicht gegenseitig offen halten.
   *
   * Das Öffnen des zweiten muss das erste schliessen — sonst stünden in einer Tabelle zwei
   * Menüs gleichzeitig offen, und der Benutzer sähe zwei Aktionslisten für verschiedene Zeilen.
   */
  it('should close the first menu when the second one opens', () => {
    const zwei = TestBed.createComponent(ZweiMenuesHost);
    zwei.detectChanges();

    const menues = Array.from(
      zwei.nativeElement.querySelectorAll('.zev-kebab-menu')) as HTMLElement[];
    const buttons = Array.from(
      zwei.nativeElement.querySelectorAll('.zev-kebab-button')) as HTMLElement[];

    buttons[0].click();
    zwei.detectChanges();
    expect(menues[0].classList.contains('zev-kebab-menu--open')).toBe(true);

    buttons[1].click();
    zwei.detectChanges();

    expect(menues[1].classList.contains('zev-kebab-menu--open')).toBe(true);
    expect(menues[0].classList.contains('zev-kebab-menu--open')).toBe(false);
  });
});
