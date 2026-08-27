import { speichereBlob } from './file-utils';

/**
 * Tests des Datei-Downloads im Browser.
 *
 * Die Funktion ist kurz, aber jeder ihrer vier Schritte ist einzeln vergessbar — und ein
 * vergessener fällt nicht auf: Ohne `download`-Attribut öffnet der Browser das PDF statt es zu
 * speichern, und ohne `revokeObjectURL` hält er das Blob bis zum Verlassen der Seite im
 * Speicher. Beides sieht in der Oberfläche nach Erfolg aus.
 *
 * Der Klick wird am **Prototyp** abgefangen statt über einen Stub auf
 * `document.createElement`: Ein Stub auf der DOM-Fabrik liefert Angulars Rendering denselben
 * Knoten zurück und bricht mit `HierarchyRequestError` — nicht im eigenen Test, sondern in den
 * nachfolgenden.
 */
describe('speichereBlob', () => {
  let geklickt: { download: string; href: string }[];

  beforeEach(() => {
    geklickt = [];
    vi.spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(function (this: HTMLAnchorElement) {
        geklickt.push({ download: this.download, href: this.href });
      });
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:abc');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
  });

  it('should create an object url for the blob', () => {
    const blob = new Blob(['%PDF']);

    speichereBlob(blob, 'rechnung.pdf');

    expect(URL.createObjectURL).toHaveBeenCalledWith(blob);
  });

  it('should click a link once', () => {
    speichereBlob(new Blob(['%PDF']), 'rechnung.pdf');

    expect(geklickt.length).toBe(1);
  });

  it('should set the given filename as download attribute', () => {
    speichereBlob(new Blob(['%PDF']), 'Nebenkosten_2026_Max_Muster.pdf');

    expect(geklickt[0].download).toBe('Nebenkosten_2026_Max_Muster.pdf');
  });

  it('should point the link at the object url', () => {
    speichereBlob(new Blob(['%PDF']), 'rechnung.pdf');

    expect(geklickt[0].href).toBe('blob:abc');
  });

  it('should release the object url again', () => {
    speichereBlob(new Blob(['%PDF']), 'rechnung.pdf');

    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:abc');
  });

  it('should not leave the link in the document', () => {
    speichereBlob(new Blob(['%PDF']), 'rechnung.pdf');

    // Der Anker wird nie eingehängt; ein zurückgelassener wäre ein Leck, das mit jedem
    // Download wächst.
    expect(document.querySelectorAll('a[download]').length).toBe(0);
  });
});
