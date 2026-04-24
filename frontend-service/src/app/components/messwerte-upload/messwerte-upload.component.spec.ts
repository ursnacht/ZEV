import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { MesswerteUploadComponent, UploadEntry } from './messwerte-upload.component';
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

  const mockMatchResponse: EinheitMatchResponse = {
    einheitId: 1,
    einheitName: 'Allgemein',
    confidence: 0.9,
    matched: true,
    message: null
  };

  const mockTranslationService = {
    translate: (key: string) => key
  };

  function makeCsvFile(name = 'test.csv'): File {
    return new File(['date,value\nJan 01 2025,42'], name, { type: 'text/csv' });
  }

  function dropFiles(files: File[]): void {
    const dataTransfer = new DataTransfer();
    files.forEach(f => dataTransfer.items.add(f));
    const event = new DragEvent('drop', { dataTransfer });
    component.onDrop(event);
  }

  beforeEach(async () => {
    einheitServiceSpy = jasmine.createSpyObj('EinheitService', [
      'getAllEinheiten',
      'matchEinheitByFilename'
    ]);
    einheitServiceSpy.getAllEinheiten.and.returnValue(of(mockEinheiten));
    einheitServiceSpy.matchEinheitByFilename.and.returnValue(of(mockMatchResponse));

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
      expect(component.einheiten.length).toBe(3);
    });

    it('should sort einheiten alphabetically', () => {
      expect(component.einheiten[0].name).toBe('1. Stock links');
      expect(component.einheiten[1].name).toBe('Allgemein');
      expect(component.einheiten[2].name).toBe('PV-Anlage');
    });

    it('should start with empty entries list', () => {
      expect(component.entries.length).toBe(0);
    });

    it('should show error when loading einheiten fails', () => {
      einheitServiceSpy.getAllEinheiten.and.returnValue(throwError(() => new Error('Network error')));
      component.loadEinheiten();
      expect(component.messageType).toBe('error');
    });
  });

  describe('isAnyMatching', () => {
    it('should return false when entries is empty', () => {
      expect(component.isAnyMatching).toBeFalse();
    });

    it('should return true when at least one entry is matching', () => {
      component.entries = [
        { file: makeCsvFile(), einheitId: 1, date: '', status: 'ready', errorMessage: null, matchConfidence: null },
        { file: makeCsvFile('b.csv'), einheitId: 1, date: '', status: 'matching', errorMessage: null, matchConfidence: null }
      ];
      expect(component.isAnyMatching).toBeTrue();
    });

    it('should return false when all entries are ready', () => {
      component.entries = [
        { file: makeCsvFile(), einheitId: 1, date: '2025-01-01', status: 'ready', errorMessage: null, matchConfidence: null }
      ];
      expect(component.isAnyMatching).toBeFalse();
    });
  });

  describe('canImport', () => {
    it('should return false when entries is empty', () => {
      expect(component.canImport).toBeFalse();
    });

    it('should return false when any entry is matching', () => {
      component.entries = [
        { file: makeCsvFile(), einheitId: 1, date: '2025-01-01', status: 'matching', errorMessage: null, matchConfidence: null }
      ];
      expect(component.canImport).toBeFalse();
    });

    it('should return false when any entry has no einheitId', () => {
      component.entries = [
        { file: makeCsvFile(), einheitId: null, date: '2025-01-01', status: 'ready', errorMessage: null, matchConfidence: null }
      ];
      expect(component.canImport).toBeFalse();
    });

    it('should return false when any entry has no date', () => {
      component.entries = [
        { file: makeCsvFile(), einheitId: 1, date: '', status: 'ready', errorMessage: null, matchConfidence: null }
      ];
      expect(component.canImport).toBeFalse();
    });

    it('should return true when all entries are valid and ready', () => {
      component.entries = [
        { file: makeCsvFile(), einheitId: 1, date: '2025-01-01', status: 'ready', errorMessage: null, matchConfidence: null }
      ];
      expect(component.canImport).toBeTrue();
    });
  });

  describe('removeEntry', () => {
    it('should remove the specified entry from the list', () => {
      const entry: UploadEntry = { file: makeCsvFile(), einheitId: 1, date: '2025-01-01', status: 'ready', errorMessage: null, matchConfidence: null };
      component.entries = [entry];
      component.removeEntry(entry);
      expect(component.entries.length).toBe(0);
    });

    it('should only remove the specified entry', () => {
      const entry1: UploadEntry = { file: makeCsvFile('a.csv'), einheitId: 1, date: '2025-01-01', status: 'ready', errorMessage: null, matchConfidence: null };
      const entry2: UploadEntry = { file: makeCsvFile('b.csv'), einheitId: 2, date: '2025-01-01', status: 'ready', errorMessage: null, matchConfidence: null };
      component.entries = [entry1, entry2];
      component.removeEntry(entry1);
      expect(component.entries).toEqual([entry2]);
    });
  });

  describe('drag and drop', () => {
    it('should set isDragOver on dragover', () => {
      component.onDragOver(new DragEvent('dragover'));
      expect(component.isDragOver).toBeTrue();
    });

    it('should reset isDragOver on dragleave', () => {
      component.isDragOver = true;
      component.onDragLeave(new DragEvent('dragleave'));
      expect(component.isDragOver).toBeFalse();
    });

    it('should reset isDragOver on drop', fakeAsync(() => {
      component.isDragOver = true;
      dropFiles([makeCsvFile()]);
      tick();
      expect(component.isDragOver).toBeFalse();
    }));

    it('should add CSV file to entries on drop', fakeAsync(() => {
      dropFiles([makeCsvFile('upload.csv')]);
      tick();
      expect(component.entries.length).toBe(1);
      expect(component.entries[0].file.name).toBe('upload.csv');
    }));

    it('should reject non-CSV files on drop', () => {
      const txtFile = new File(['test'], 'test.txt', { type: 'text/plain' });
      dropFiles([txtFile]);
      expect(component.entries.length).toBe(0);
      expect(component.messageType).toBe('error');
    });

    it('should reject duplicate file names', fakeAsync(() => {
      dropFiles([makeCsvFile('dup.csv')]);
      tick();
      dropFiles([makeCsvFile('dup.csv')]);
      tick();
      expect(component.entries.length).toBe(1);
      expect(component.messageType).toBe('error');
    }));

    it('should call matchEinheitByFilename for each dropped CSV', fakeAsync(() => {
      dropFiles([makeCsvFile('upload.csv')]);
      tick();
      expect(einheitServiceSpy.matchEinheitByFilename).toHaveBeenCalledWith('upload.csv');
    }));

    it('should set entry status to ready after matching', fakeAsync(() => {
      dropFiles([makeCsvFile()]);
      tick();
      expect(component.entries[0].status).toBe('ready');
    }));

    it('should set entry status to ready even on match error', fakeAsync(() => {
      einheitServiceSpy.matchEinheitByFilename.and.returnValue(throwError(() => new Error('fail')));
      dropFiles([makeCsvFile()]);
      tick();
      expect(component.entries[0].status).toBe('ready');
    }));
  });

  describe('importAll', () => {
    it('should do nothing when no ready entries exist', () => {
      component.entries = [];
      component.importAll();
      expect(component.importing).toBeFalse();
    });

    it('should upload entries and show success message', fakeAsync(() => {
      component.entries = [
        { file: makeCsvFile(), einheitId: 1, date: '2025-01-01', status: 'ready', errorMessage: null, matchConfidence: null }
      ];
      component.importAll();

      const req = httpMock.expectOne('http://localhost:8090/api/messwerte/upload');
      req.flush({ status: 'success' });
      tick();

      expect(component.entries.length).toBe(0);
      expect(component.messageType).toBe('success');
    }));

    it('should set entry status to error on upload failure', fakeAsync(() => {
      component.entries = [
        { file: makeCsvFile(), einheitId: 1, date: '2025-01-01', status: 'ready', errorMessage: null, matchConfidence: null }
      ];
      component.importAll();

      const req = httpMock.expectOne('http://localhost:8090/api/messwerte/upload');
      req.flush({ status: 'error', message: 'Ungültige Datei' });
      tick();

      expect(component.messageType).toBe('error');
    }));

    it('should reset importing flag after completion', fakeAsync(() => {
      component.entries = [
        { file: makeCsvFile(), einheitId: 1, date: '2025-01-01', status: 'ready', errorMessage: null, matchConfidence: null }
      ];
      component.importAll();

      const req = httpMock.expectOne('http://localhost:8090/api/messwerte/upload');
      req.flush({ status: 'success' });
      tick();

      expect(component.importing).toBeFalse();
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
    });
  });

  describe('message display', () => {
    it('should clear success message after 5s', fakeAsync(() => {
      component['showMessage']('Test', 'success');
      expect(component.message).toBe('Test');
      tick(5000);
      expect(component.message).toBe('');
    }));

    it('should not auto-dismiss error messages', fakeAsync(() => {
      component['showMessage']('Fehler', 'error');
      tick(5000);
      expect(component.message).toBe('Fehler');
    }));

    it('should not auto-dismiss warning messages', fakeAsync(() => {
      component['showMessage']('Warnung', 'warning');
      tick(5000);
      expect(component.message).toBe('Warnung');
    }));
  });
});
