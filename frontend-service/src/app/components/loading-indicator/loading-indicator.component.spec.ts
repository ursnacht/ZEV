import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LoadingIndicatorComponent } from './loading-indicator.component';
import { TranslationService } from '../../services/translation.service';

describe('LoadingIndicatorComponent', () => {
  let component: LoadingIndicatorComponent;
  let fixture: ComponentFixture<LoadingIndicatorComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LoadingIndicatorComponent],
      providers: [
        { provide: TranslationService, useValue: { translate: (k: string) => k } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(LoadingIndicatorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('initialization', () => {
    it('should show the default text LADE_DATEN', () => {
      const text = fixture.nativeElement.querySelector('.zev-loading__text');
      expect(text.textContent.trim()).toBe('LADE_DATEN');
    });

    it('should render a spinner hidden from screen readers', () => {
      const spinner = fixture.nativeElement.querySelector('.zev-spinner');
      expect(spinner).not.toBeNull();
      expect(spinner.getAttribute('aria-hidden')).toBe('true');
    });

    it('should announce itself as a status', () => {
      const box = fixture.nativeElement.querySelector('.zev-loading');
      expect(box.getAttribute('role')).toBe('status');
      expect(box.getAttribute('aria-live')).toBe('polite');
    });

    it('should not be inline by default', () => {
      const box = fixture.nativeElement.querySelector('.zev-loading');
      expect(box.classList.contains('zev-loading--inline')).toBe(false);
    });
  });

  describe('inputs', () => {
    it('should translate the given text key', () => {
      fixture.componentRef.setInput('text', 'STATISTIK_WIRD_BERECHNET');
      fixture.detectChanges();
      const text = fixture.nativeElement.querySelector('.zev-loading__text');
      expect(text.textContent.trim()).toBe('STATISTIK_WIRD_BERECHNET');
    });

    it('should switch to the inline variant', () => {
      fixture.componentRef.setInput('inline', true);
      fixture.detectChanges();
      const box = fixture.nativeElement.querySelector('.zev-loading');
      expect(box.classList.contains('zev-loading--inline')).toBe(true);
    });
  });
});
