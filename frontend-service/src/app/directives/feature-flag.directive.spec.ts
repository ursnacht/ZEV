import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { createSpyObj, SpyObj } from '../../testing/spy';
import { FeatureFlagDirective } from './feature-flag.directive';
import { FeatureFlagService } from '../services/feature-flag.service';

@Component({
  standalone: true,
  imports: [FeatureFlagDirective],
  template: `<div class="flagged" *appFeature="'MESSWERTE_UPLOAD'">content</div>`
})
class HostComponent {}

describe('FeatureFlagDirective', () => {
  let featureFlagServiceSpy: SpyObj<FeatureFlagService>;
  let fixture: ComponentFixture<HostComponent>;

  beforeEach(async () => {
    featureFlagServiceSpy = createSpyObj<FeatureFlagService>('FeatureFlagService', [
      'isEnabled', 'load', 'getAdminFlags', 'setFlag', 'resetFlag'
    ]);

    await TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [
        { provide: FeatureFlagService, useValue: featureFlagServiceSpy }
      ]
    }).compileComponents();
  });

  it('should render the content when the flag is enabled', () => {
    featureFlagServiceSpy.isEnabled.mockReturnValue(true);

    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();

    const el = fixture.nativeElement.querySelector('.flagged');
    expect(el).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('content');
  });

  it('should not render the content when the flag is disabled', () => {
    featureFlagServiceSpy.isEnabled.mockReturnValue(false);

    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.flagged')).toBeNull();
  });

  it('should query the service with the configured flag key', () => {
    featureFlagServiceSpy.isEnabled.mockReturnValue(true);

    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();

    expect(featureFlagServiceSpy.isEnabled).toHaveBeenCalledWith('MESSWERTE_UPLOAD');
  });

  it('should update the view reactively when the flag state changes', () => {
    // isEnabled auf ein Signal stützen, damit der effect() der Direktive reaktiv reagiert.
    const enabled = signal(false);
    featureFlagServiceSpy.isEnabled.mockImplementation(() => enabled());

    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.flagged')).toBeNull();

    enabled.set(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.flagged')).toBeTruthy();

    enabled.set(false);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.flagged')).toBeNull();
  });
});
