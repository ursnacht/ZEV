import { Component, OnInit, ChangeDetectionStrategy, VERSION } from '@angular/core';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { IconComponent } from '../icon/icon.component';
import { VersionService } from '../../services/version.service';

@Component({
  selector: 'app-startseite',
  standalone: true,
  imports: [TranslatePipe, IconComponent],
  templateUrl: './startseite.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrls: ['./startseite.component.css']
})
export class StartseiteComponent implements OnInit {
  schemaVersion: string | null = null;
  buildTime: string | null = null;
  springBootVersion: string | null = null;

  /**
   * Angular-Version des ausgelieferten Frontends - direkt aus `@angular/core`, ohne Backend-Aufruf
   * (Specs/Startseite.md, FR-4.4). Sie steht darum auch dann, wenn `/api/version` scheitert.
   */
  readonly angularVersion = VERSION.full;

  constructor(private versionService: VersionService) {}

  ngOnInit(): void {
    this.versionService.getVersion().subscribe({
      next: (version) => {
        this.schemaVersion = version.schemaVersion;
        this.buildTime = version.buildTime;
        this.springBootVersion = version.springBootVersion;
      },
      error: () => {
        // Rein informativ: Die Backend-Teile entfallen, die Angular-Version bleibt stehen - keine
        // Fehlermeldung (Specs/Startseite.md, FR-4.6)
        this.schemaVersion = null;
        this.buildTime = null;
        this.springBootVersion = null;
      }
    });
  }
}
