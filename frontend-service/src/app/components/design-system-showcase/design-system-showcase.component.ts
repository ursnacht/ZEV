import { Component } from '@angular/core';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { QuarterSelectorComponent } from '../quarter-selector/quarter-selector.component';
import { IconComponent } from '../icon/icon.component';
import { ICONS } from '../icon/icons';

@Component({
  selector: 'app-design-system-showcase',
  standalone: true,
  imports: [TranslatePipe, QuarterSelectorComponent, IconComponent],
  templateUrl: './design-system-showcase.component.html',
  styleUrl: './design-system-showcase.component.css'
})
export class DesignSystemShowcaseComponent {
  // Icon names for showcase
  iconNames: string[] = Object.keys(ICONS);
  collapsibleOpen = false;
  quarterDateFrom = '';
  quarterDateTo = '';
  dropZoneActive = false;
  dropZoneFile: File | null = null;

  toggleCollapsible(): void {
    this.collapsibleOpen = !this.collapsibleOpen;
  }

  onQuarterSelected(event: {von: string, bis: string}): void {
    this.quarterDateFrom = event.von;
    this.quarterDateTo = event.bis;
  }

  onDropZoneDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dropZoneActive = true;
  }

  onDropZoneDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.dropZoneActive = false;
  }

  onDropZoneDrop(event: DragEvent): void {
    event.preventDefault();
    this.dropZoneActive = false;
    const files = event.dataTransfer?.files;
    if (files && files.length > 0) {
      this.dropZoneFile = files[0];
    }
  }

  onDropZoneFileSelect(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.dropZoneFile = input.files[0];
    }
  }

  removeDropZoneFile(event: Event): void {
    event.stopPropagation();
    this.dropZoneFile = null;
  }

  formatFileSize(bytes: number): string {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  }
}
