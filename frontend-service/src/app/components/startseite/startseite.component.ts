import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { IconComponent } from '../icon/icon.component';
import { VersionService } from '../../services/version.service';

@Component({
  selector: 'app-startseite',
  standalone: true,
  imports: [TranslatePipe, IconComponent],
  templateUrl: './startseite.component.html',
  styleUrls: ['./startseite.component.css']
})
export class StartseiteComponent implements OnInit {
  schemaVersion: string | null = null;
  buildTime: string | null = null;

  constructor(private versionService: VersionService) {}

  ngOnInit(): void {
    this.versionService.getVersion().subscribe({
      next: (version) => {
        this.schemaVersion = version.schemaVersion;
        this.buildTime = version.buildTime;
      },
      error: () => {
        // Version/Build sind rein informativ – bei Fehlern keine Anzeige, keine Fehlermeldung
        this.schemaVersion = null;
        this.buildTime = null;
      }
    });
  }
}
