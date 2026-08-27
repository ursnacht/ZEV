/**
 * Speichern einer heruntergeladenen Datei im Browser.
 *
 * Der Ablauf (Objekt-URL, unsichtbarer Anker, Klick, URL freigeben) steht im Projekt bereits an
 * drei Stellen; diese Funktion ist die vierte Verwendung und deshalb ausgelagert. Die bestehenden
 * drei bleiben vorerst, wie sie sind — sie umzustellen gehört nicht zu diesem Feature.
 */
export function speichereBlob(blob: Blob, filename: string): void {
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  window.URL.revokeObjectURL(url);
}
