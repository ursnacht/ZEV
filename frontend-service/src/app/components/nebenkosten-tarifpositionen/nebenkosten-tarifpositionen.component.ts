import { Component, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { IconComponent } from '../icon/icon.component';

/**
 * Gerüstseite der Nebenkostenabrechnung (Specs/Nebenkosten/Nebenkosten.md, FR-4).
 *
 * Bewusst ohne Service und ohne HTTP-Aufruf: Die Fachlichkeit folgt mit einer eigenen Spec.
 * Die Seite existiert, damit Menüstruktur, Routing und Berechtigungen vollständig sind.
 */
@Component({
  selector: 'app-nebenkosten-tarifpositionen',
  standalone: true,
  imports: [CommonModule, TranslatePipe, IconComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './nebenkosten-tarifpositionen.component.html'
})
export class NebenkostenTarifpositionenComponent {
}
