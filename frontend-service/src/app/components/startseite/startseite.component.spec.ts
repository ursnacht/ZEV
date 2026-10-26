import { createSpyObj, SpyObj } from '../../../testing/spy';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { VERSION } from '@angular/core';
import { of, throwError } from 'rxjs';
import { StartseiteComponent } from './startseite.component';
import { VersionInfo, VersionService } from '../../services/version.service';
import { TranslationService } from '../../services/translation.service';

/** Versionszeile der Startseite (Specs/Startseite.md, FR-4). */
describe('StartseiteComponent', () => {
  let fixture: ComponentFixture<StartseiteComponent>;
  let versionServiceSpy: SpyObj<VersionService>;

  const vollstaendig: VersionInfo = {
    schemaVersion: '188', buildTime: '26.10.2026 10:09', springBootVersion: '4.1.2'
  };

  async function erzeuge(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [StartseiteComponent],
      providers: [
        { provide: VersionService, useValue: versionServiceSpy },
        { provide: TranslationService, useValue: { translate: (k: string) => k } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(StartseiteComponent);
    fixture.detectChanges();
  }

  /** Die Versionszeile als Text, Leerraum zusammengezogen. */
  const zeile = (): string =>
    (fixture.nativeElement.querySelector('.startseite__version')?.textContent ?? '').replace(/\s+/g, ' ').trim();

  beforeEach(() => {
    versionServiceSpy = createSpyObj<VersionService>('VersionService', ['getVersion']);
  });

  it('should show all four parts in order, separated by a middle dot', async () => {
    versionServiceSpy.getVersion.mockReturnValue(of(vollstaendig));
    await erzeuge();

    expect(zeile()).toBe(
      `Spring Boot 4.1.2 · Angular ${VERSION.full} · DB_SCHEMA_VERSION: V188 · BUILD_DATUM: 26.10.2026 10:09`);
  });

  it('should show the Spring Boot version delivered by the backend', async () => {
    versionServiceSpy.getVersion.mockReturnValue(of(vollstaendig));
    await erzeuge();

    expect(zeile()).toContain('Spring Boot 4.1.2');
  });

  it('should show the Angular version of the delivered frontend', async () => {
    versionServiceSpy.getVersion.mockReturnValue(of(vollstaendig));
    await erzeuge();

    expect(fixture.componentInstance.angularVersion).toBe(VERSION.full);
    expect(zeile()).toContain(`Angular ${VERSION.full}`);
  });

  it('should omit missing parts without a stray separator', async () => {
    versionServiceSpy.getVersion.mockReturnValue(of({ schemaVersion: '188', buildTime: null, springBootVersion: null }));
    await erzeuge();

    expect(zeile()).toBe(`Angular ${VERSION.full} · DB_SCHEMA_VERSION: V188`);
  });

  it('should not leave a separator at either end when only the build time is missing', async () => {
    versionServiceSpy.getVersion.mockReturnValue(of({ schemaVersion: '188', buildTime: null, springBootVersion: '4.1.2' }));
    await erzeuge();

    expect(zeile()).toBe(`Spring Boot 4.1.2 · Angular ${VERSION.full} · DB_SCHEMA_VERSION: V188`);
  });

  it('should keep only the Angular version when the version call fails, without an error message', async () => {
    versionServiceSpy.getVersion.mockReturnValue(throwError(() => new Error('500')));
    await erzeuge();

    expect(zeile()).toBe(`Angular ${VERSION.full}`);
    expect(fixture.nativeElement.querySelector('.zev-message')).toBeNull();
  });
});
