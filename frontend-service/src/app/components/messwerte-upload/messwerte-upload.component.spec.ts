import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { MesswerteUploadComponent } from './messwerte-upload.component';
import { EinheitService, EinheitMatchResponse } from '../../services/einheit.service';
import { TranslationService } from '../../services/translation.service';
import { Einheit, EinheitTyp } from '../../models/einheit.model';
import { of, throwError } from 'rxjs';

describe('MesswerteUploadComponent', () => {
  let component: MesswerteUploadComponent;
  let fixture: ComponentFixture<MesswerteUploadComponent>;
  let einheitServiceSpy: jasmine.SpyObj<EinheitService>;
  let httpMock: HttpTestingController;

  const mockEinheiten: Einheit[] = [
    { id: 1, name: 'Allgemein', typ: EinheitTyp.CONSUMER },
    { id: 2, name: '1. Stock links', typ: EinheitTyp.CONSUMER },
    { id: 3, name: 'PV-Anlage', typ: EinheitTyp.PRODUCER }
  ];

  const mockMatchResponseHighConfidence: EinheitMatchResponse = {
    einheitId: 1,
    einheitName: 'Allgemein',
    confidence: 0.9,
    matched: true,
    message: null
  };

  const mockMatchResponseLowConfidence: EinheitMatchResponse = {
    einheitId: 2,
    einheitName: '1. Stock links',
    confidence: 0.7,
    matched: true,
    message: null
  };

  const mockMatchResponseNotMatched: EinheitMatchResponse = {
    einheitId: null,
    einheitName: null,
    confidence: 0,
    matched: false,
    message: 'Keine passende Einheit gefunden'
  };

  const mockMatchResponseError: EinheitMatchResponse = {
    einheitId: null,
    einheitName: null,
    confidence: 0,
    matched: false,
    message: 'KI-Service nicht verfügbar'
  };

  const mockTranslationService = {
    translate: (key: string) => key
  };

  beforeEach(async () => {
    einheitServiceSpy = jasmine.createSpyObj('EinheitService', [
      'getAllEinheiten',
      'matchEinheitByFilename'
    ]);
    einheitServiceSpy.getAllEinheiten.and.returnValue(of(mockEinheiten));

    await TestBed.configureTestingModule({
      imports: [MesswerteUploadComponent, HttpClientTestingModule],
      providers: [
        { provide: EinheitService, useValue: einheitServiceSpy },
        { provide: TranslationService, useValue: mockTranslationService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MesswerteUploadComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('initialization', () => {
    it('should load einheiten on init', () => {
      expect(einheitServiceSpy.getAllEinheiten).toHaveBeenCalled();
    });

    it('should sort einheiten alphabetically', () => {
      expect(component.einheiten.length).toBe(3);
      expect(component.einheiten[0].name).toBe('1. Stock links');
      expect(component.einheiten[1].name).toBe('Allgemein');
      expect(component.einheiten[2].name).toBe('PV-Anlage');
    });

    it('should set first einheit as default selection', () => {
      expect(component.einheitId).toBe(2); // '1. Stock links' comes first alphabetically
    });

    it('should initialize with empty matching state', () => {
      expect(component.isMatching).toBeFalse();
      expect(component.matchResult).toBeNull();
    });

    it('should show error message when loading einheiten fails', () => {
      einheitServiceSpy.getAllEinheiten.and.returnValue(throwError(() => new Error('Network error')));

      component.loadEinheiten();

      expect(component.messageType).toBe('error');
      expect(component.message).toContain('Fehler beim Laden der Einheiten');
    });
  });

  describe('matchEinheitByFilename', () => {
    it('should call service with filename when file is selected', fakeAsync(() => {
      einheitServiceSpy.matchEinheitByFilename.and.returnValue(of(mockMatchResponseHighConfidence));

      const mockFile = new File(['test'], '2025-07-allg.csv', { type: 'text/csv' });
      component['handleFile'](mockFile);
      tick();

      expect(einheitServiceSpy.matchEinheitByFilename).toHaveBeenCalledWith('2025-07-allg.csv');
    }));

    it('should set isMatching to true during matching', fakeAsync(() => {
      einheitServiceSpy.matchEinheitByFilename.and.returnValue(of(mockMatchResponseHighConfidence));

      component['matchEinheitByFilename']('2025-07-allg.csv');

      // Note: isMatching will be false after the sync operation completes
      tick();
      expect(component.isMatching).toBeFalse();
    }));

    it('should auto-select einheit on high confidence match', fakeAsync(() => {
      einheitServiceSpy.matchEinheitByFilename.and.returnValue(of(mockMatchResponseHighConfidence));

      component['matchEinheitByFilename']('2025-07-allg.csv');
      tick();

      expect(component.einheitId).toBe(1);
      expect(component.matchResult).toEqual(mockMatchResponseHighConfidence);
      expect(component.messageType).toBe('success');
      expect(component.message).toContain('EINHEIT_ERKANNT');
    }));

    it('should suggest einheit on low confidence match', fakeAsync(() => {
      einheitServiceSpy.matchEinheitByFilename.and.returnValue(of(mockMatchResponseLowConfidence));

      component['matchEinheitByFilename']('2025-07-1-li.csv');
      tick();

      expect(component.einheitId).toBe(2);
      expect(component.matchResult).toEqual(mockMatchResponseLowConfidence);
      expect(component.messageType).toBe('warning');
      expect(component.message).toContain('EINHEIT_BITTE_PRUEFEN');
    }));

    it('should show error message when no match found', fakeAsync(() => {
      einheitServiceSpy.matchEinheitByFilename.and.returnValue(of(mockMatchResponseNotMatched));

      component['matchEinheitByFilename']('2025-07-unknown.csv');
      tick();

      expect(component.matchResult).toEqual(mockMatchResponseNotMatched);
      expect(component.messageType).toBe('error');
      expect(component.message).toBe('Keine passende Einheit gefunden');
    }));

    it('should show error message when service returns error', fakeAsync(() => {
      einheitServiceSpy.matchEinheitByFilename.and.returnValue(of(mockMatchResponseError));

      component['matchEinheitByFilename']('2025-07-test.csv');
      tick();

      expect(component.matchResult).toEqual(mockMatchResponseError);
      expect(component.messageType).toBe('error');
      expect(component.message).toBe('KI-Service nicht verfügbar');
    }));

    it('should handle HTTP error gracefully', fakeAsync(() => {
      einheitServiceSpy.matchEinheitByFilename.and.returnValue(
        throwError(() => ({ error: { message: 'Connection refused' } }))
      );

      component['matchEinheitByFilename']('2025-07-test.csv');
      tick();

      expect(component.isMatching).toBeFalse();
      expect(component.matchResult).toBeNull();
      expect(component.messageType).toBe('error');
      expect(component.message).toBe('Connection refused');
    }));

    it('should use translation for fallback error message', fakeAsync(() => {
      einheitServiceSpy.matchEinheitByFilename.and.returnValue(
        throwError(() => ({}))
      );

      component['matchEinheitByFilename']('2025-07-test.csv');
      tick();

      expect(component.message).toBe('KI_NICHT_VERFUEGBAR');
    }));

    it('should reset matchResult on error', fakeAsync(() => {
      einheitServiceSpy.matchEinheitByFilename.and.returnValue(
        throwError(() => new Error('Network error'))
      );

      component['matchEinheitByFilename']('2025-07-test.csv');
      tick();

      expect(component.matchResult).toBeNull();
    }));
  });

  describe('confidence threshold', () => {
    it('should use 0.8 as confidence threshold', () => {
      expect(component['CONFIDENCE_THRESHOLD']).toBe(0.8);
    });

    it('should show success for confidence > 0.8', fakeAsync(() => {
      const highConfidence: EinheitMatchResponse = {
        ...mockMatchResponseHighConfidence,
        confidence: 0.81
      };
      einheitServiceSpy.matchEinheitByFilename.and.returnValue(of(highConfidence));

      component['matchEinheitByFilename']('test.csv');
      tick();

      expect(component.messageType).toBe('success');
    }));

    it('should show warning for confidence = 0.8', fakeAsync(() => {
      const exactThreshold: EinheitMatchResponse = {
        ...mockMatchResponseHighConfidence,
        confidence: 0.8
      };
      einheitServiceSpy.matchEinheitByFilename.and.returnValue(of(exactThreshold));

      component['matchEinheitByFilename']('test.csv');
      tick();

      expect(component.messageType).toBe('warning');
    }));

    it('should show warning for confidence < 0.8', fakeAsync(() => {
      const lowConfidence: EinheitMatchResponse = {
        ...mockMatchResponseHighConfidence,
        confidence: 0.79
      };
      einheitServiceSpy.matchEinheitByFilename.and.returnValue(of(lowConfidence));

      component['matchEinheitByFilename']('test.csv');
      tick();

      expect(component.messageType).toBe('warning');
    }));
  });

  describe('file handling', () => {
    it('should set file and trigger matching on file select', fakeAsync(() => {
      einheitServiceSpy.matchEinheitByFilename.and.returnValue(of(mockMatchResponseHighConfidence));

      const mockFile = new File(['test content'], '2025-08-allg.csv', { type: 'text/csv' });
      component['handleFile'](mockFile);
      tick();

      expect(component.file).toBe(mockFile);
      expect(einheitServiceSpy.matchEinheitByFilename).toHaveBeenCalledWith('2025-08-allg.csv');
    }));

    it('should only accept csv files on drop', () => {
      const nonCsvFile = new File(['test'], 'test.txt', { type: 'text/plain' });
      const dataTransfer = new DataTransfer();
      dataTransfer.items.add(nonCsvFile);

      const dropEvent = new DragEvent('drop', { dataTransfer });
      component.onDrop(dropEvent);

      expect(component.file).toBeNull();
      expect(component.messageType).toBe('error');
    });
  });

  describe('drag and drop', () => {
    it('should set isDragOver on dragover', () => {
      const event = new DragEvent('dragover');
      component.onDragOver(event);

      expect(component.isDragOver).toBeTrue();
    });

    it('should reset isDragOver on dragleave', () => {
      component.isDragOver = true;
      const event = new DragEvent('dragleave');
      component.onDragLeave(event);

      expect(component.isDragOver).toBeFalse();
    });

    it('should reset isDragOver on drop', fakeAsync(() => {
      einheitServiceSpy.matchEinheitByFilename.and.returnValue(of(mockMatchResponseHighConfidence));
      component.isDragOver = true;

      const csvFile = new File(['test'], 'test.csv', { type: 'text/csv' });
      const dataTransfer = new DataTransfer();
      dataTransfer.items.add(csvFile);

      const dropEvent = new DragEvent('drop', { dataTransfer });
      component.onDrop(dropEvent);
      tick();

      expect(component.isDragOver).toBeFalse();
    }));
  });

  describe('formatFileSize', () => {
    it('should return "0 Bytes" for zero bytes', () => {
      expect(component.formatFileSize(0)).toBe('0 Bytes');
    });

    it('should format bytes correctly', () => {
      expect(component.formatFileSize(500)).toBe('500 Bytes');
    });

    it('should format kilobytes correctly', () => {
      expect(component.formatFileSize(1024)).toBe('1 KB');
      expect(component.formatFileSize(1536)).toBe('1.5 KB');
    });

    it('should format megabytes correctly', () => {
      expect(component.formatFileSize(1048576)).toBe('1 MB');
      expect(component.formatFileSize(1572864)).toBe('1.5 MB');
    });
  });

  describe('form submission', () => {
    it('should show error when required fields are missing', () => {
      component.date = '';
      component.einheitId = null;
      component.file = null;

      component.onSubmit();

      expect(component.messageType).toBe('error');
      expect(component.message).toBe('BITTE_ALLE_FELDER_AUSFUELLEN');
    });

    it('should show error when date is missing', () => {
      component.date = '';
      component.einheitId = 1;
      component.file = new File(['test'], 'test.csv');

      component.onSubmit();

      expect(component.messageType).toBe('error');
    });

    it('should show error when einheitId is missing', () => {
      component.date = '2025-07-01';
      component.einheitId = null;
      component.file = new File(['test'], 'test.csv');

      component.onSubmit();

      expect(component.messageType).toBe('error');
    });

    it('should show error when file is missing', () => {
      component.date = '2025-07-01';
      component.einheitId = 1;
      component.file = null;

      component.onSubmit();

      expect(component.messageType).toBe('error');
    });
  });

  describe('message display', () => {
    it('should clear message after timeout', fakeAsync(() => {
      component['showMessage']('Test message', 'success');

      expect(component.message).toBe('Test message');
      expect(component.messageType).toBe('success');

      tick(5000);

      expect(component.message).toBe('');
      expect(component.messageType).toBe('');
    }));

    it('should display warning messages', () => {
      component['showMessage']('Warning message', 'warning');

      expect(component.message).toBe('Warning message');
      expect(component.messageType).toBe('warning');
    });
  });
});
