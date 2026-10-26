import { Component, ChangeDetectionStrategy } from '@angular/core';
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
    changeDetection: ChangeDetectionStrategy.Eager,
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
    changeDetection: ChangeDetectionStrategy.Eager,
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

  describe('nach oben aufklappen', () => {
    /** Knopf an Position top..bottom (rechter Rand `right`), Menue `hoehe` x `breite`, Fenster `fenster` hoch. */
    function lage(top: number, bottom: number, hoehe: number, fenster: number,
                  right = 500, breite = 160): void {
      const host: HTMLElement = fixture.nativeElement;
      const knopf = host.querySelector('.zev-kebab-button') as HTMLElement;
      const menue = host.querySelector('.zev-kebab-menu') as HTMLElement;
      vi.spyOn(knopf, 'getBoundingClientRect').mockReturnValue({ top, bottom, right } as DOMRect);
      Object.defineProperty(menue, 'offsetHeight', { configurable: true, value: hoehe });
      Object.defineProperty(menue, 'offsetWidth', { configurable: true, value: breite });
      Object.defineProperty(window, 'innerHeight', { configurable: true, value: fenster });
    }

    it('should open downwards when there is room below', () => {
      lage(100, 120, 150, 800);
      oeffne();
      expect(menu().classList.contains('zev-kebab-menu--up')).toBe(false);
    });

    it('should open upwards near the bottom of the window', () => {
      lage(700, 720, 150, 800);
      oeffne();
      expect(menu().classList.contains('zev-kebab-menu--up')).toBe(true);
    });

    it('should stay downwards when there is even less room above', () => {
      lage(40, 60, 150, 160);
      oeffne();
      expect(menu().classList.contains('zev-kebab-menu--up')).toBe(false);
    });

    /**
     * Specs/Messpunkt-Empfang.md, Anhang A.2: In einer kurzen Tabelle in `.zev-table-scroll`
     * passte das absolut positionierte Menue weder nach unten noch nach oben, der oberste Eintrag
     * war abgeschnitten. Fest positioniert zaehlt nur noch das Fenster - ein abschneidender
     * Container aendert die Richtung nicht.
     */
    it('should ignore a clipping container and open downwards when the window has room', () => {
      lage(250, 270, 150, 800);
      const panel = document.createElement('div');
      panel.style.overflow = 'auto';
      const host: HTMLElement = fixture.nativeElement;
      host.parentElement!.insertBefore(panel, host);
      panel.appendChild(host);
      vi.spyOn(panel, 'getBoundingClientRect').mockReturnValue({ top: 200, bottom: 300 } as DOMRect);

      oeffne();

      expect(menu().classList.contains('zev-kebab-menu--up')).toBe(false);
      expect(menu().style.top).toBe('270px');
      panel.remove();
    });
  });

  describe('feste Lage im Fenster', () => {
    function lage(top: number, bottom: number, right: number, hoehe: number, breite: number): void {
      const host: HTMLElement = fixture.nativeElement;
      const knopf = host.querySelector('.zev-kebab-button') as HTMLElement;
      const menue = host.querySelector('.zev-kebab-menu') as HTMLElement;
      vi.spyOn(knopf, 'getBoundingClientRect').mockReturnValue({ top, bottom, right } as DOMRect);
      Object.defineProperty(menue, 'offsetHeight', { configurable: true, value: hoehe });
      Object.defineProperty(menue, 'offsetWidth', { configurable: true, value: breite });
      Object.defineProperty(window, 'innerHeight', { configurable: true, value: 800 });
    }

    it('should be positioned fixed', () => {
      expect(menu().classList.contains('zev-kebab-menu--fixed')).toBe(true);
    });

    it('should place the menu right-aligned below the button', () => {
      lage(100, 132, 600, 150, 160);
      oeffne();
      expect(menu().style.top).toBe('132px');
      expect(menu().style.left).toBe('440px');
    });

    it('should place the menu above the button when opening upwards', () => {
      lage(700, 732, 600, 150, 160);
      oeffne();
      expect(menu().style.top).toBe('550px');
    });

    it('should never leave the window on the left', () => {
      lage(100, 132, 50, 150, 160);
      oeffne();
      expect(menu().style.left).toBe('0px');
    });

    /**
     * Ein Scroll-Ereignis kommt oft erst nach dem Klick an (der Browser holt den Knopf ins Bild).
     * Schloss sich das Menue dabei, war es gleich nach dem Oeffnen wieder zu - es muss offen
     * bleiben und mit dem Knopf mitwandern.
     */
    it('should stay open on scroll and follow the button', () => {
      lage(100, 132, 600, 150, 160);
      oeffne();
      const knopf = kebabButton();
      vi.spyOn(knopf, 'getBoundingClientRect').mockReturnValue({ top: 40, bottom: 72, right: 600 } as DOMRect);

      window.dispatchEvent(new Event('scroll'));
      fixture.detectChanges();

      expect(istOffen()).toBe(true);
      expect(menu().style.top).toBe('72px');
    });

    it('should follow the button when a table inside the page scrolls', () => {
      lage(100, 132, 600, 150, 160);
      oeffne();
      vi.spyOn(kebabButton(), 'getBoundingClientRect').mockReturnValue({ top: 200, bottom: 232, right: 600 } as DOMRect);
      const ausserhalb = fixture.nativeElement.querySelector('.ausserhalb') as HTMLElement;

      ausserhalb.dispatchEvent(new Event('scroll'));
      fixture.detectChanges();

      expect(istOffen()).toBe(true);
      expect(menu().style.top).toBe('232px');
    });

    it('should follow the button on resize', () => {
      lage(100, 132, 600, 150, 160);
      oeffne();
      vi.spyOn(kebabButton(), 'getBoundingClientRect').mockReturnValue({ top: 100, bottom: 132, right: 400 } as DOMRect);

      window.dispatchEvent(new Event('resize'));
      fixture.detectChanges();

      expect(istOffen()).toBe(true);
      expect(menu().style.left).toBe('240px');
    });

    it('should remove the scroll listener on close', () => {
      const entfernt = vi.spyOn(window, 'removeEventListener');
      oeffne();
      kebabButton().click();
      fixture.detectChanges();
      expect(entfernt).toHaveBeenCalledWith('scroll', expect.any(Function), true);
      expect(entfernt).toHaveBeenCalledWith('resize', expect.any(Function));
    });
  });
});
