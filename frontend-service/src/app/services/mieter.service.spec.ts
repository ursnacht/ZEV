import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { MieterService } from './mieter.service';
import { Mieter } from '../models/mieter.model';

describe('MieterService', () => {
  let service: MieterService;
  let httpMock: HttpTestingController;
  const apiUrl = 'http://localhost:8090/api/mieter';

  const mockMieter: Mieter = {
    id: 1,
    name: 'Max Muster',
    strasse: 'Musterstr. 1',
    plz: '8000',
    ort: 'Zürich',
    mietbeginn: '2024-01-01',
    einheitId: 1
  };

  const mockMieterList: Mieter[] = [
    mockMieter,
    {
      id: 2,
      name: 'Anna Test',
      strasse: 'Testweg 2',
      plz: '3000',
      ort: 'Bern',
      mietbeginn: '2024-06-01',
      mietende: '2025-05-31',
      einheitId: 3
    }
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [MieterService]
    });
    service = TestBed.inject(MieterService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getAllMieter', () => {
    it('should return all mieter', () => {
      service.getAllMieter().subscribe(mieter => {
        expect(mieter.length).toBe(2);
        expect(mieter).toEqual(mockMieterList);
      });

      const req = httpMock.expectOne(apiUrl);
      expect(req.request.method).toBe('GET');
      req.flush(mockMieterList);
    });

    it('should return empty array when no mieter exist', () => {
      service.getAllMieter().subscribe(mieter => {
        expect(mieter.length).toBe(0);
        expect(mieter).toEqual([]);
      });

      const req = httpMock.expectOne(apiUrl);
      req.flush([]);
    });
  });

  describe('getMieterById', () => {
    it('should return a single mieter by id', () => {
      const mieterId = 1;

      service.getMieterById(mieterId).subscribe(mieter => {
        expect(mieter).toEqual(mockMieter);
        expect(mieter.id).toBe(mieterId);
      });

      const req = httpMock.expectOne(`${apiUrl}/${mieterId}`);
      expect(req.request.method).toBe('GET');
      req.flush(mockMieter);
    });
  });

  describe('createMieter', () => {
    it('should create a new mieter', () => {
      const newMieter: Mieter = {
        name: 'Neuer Mieter',
        strasse: 'Neue Str. 1',
        plz: '4000',
        ort: 'Basel',
        mietbeginn: '2025-01-01',
        einheitId: 2
      };

      const createdMieter = { ...newMieter, id: 3 };

      service.createMieter(newMieter).subscribe(mieter => {
        expect(mieter).toEqual(createdMieter);
        expect(mieter.id).toBe(3);
      });

      const req = httpMock.expectOne(apiUrl);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(newMieter);
      req.flush(createdMieter);
    });
  });

  describe('updateMieter', () => {
    it('should update an existing mieter', () => {
      const updatedMieter: Mieter = {
        ...mockMieter,
        name: 'Max Muster Updated'
      };

      service.updateMieter(mockMieter.id!, updatedMieter).subscribe(mieter => {
        expect(mieter).toEqual(updatedMieter);
        expect(mieter.name).toBe('Max Muster Updated');
      });

      const req = httpMock.expectOne(`${apiUrl}/${mockMieter.id}`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(updatedMieter);
      req.flush(updatedMieter);
    });
  });

  describe('deleteMieter', () => {
    it('should delete a mieter', () => {
      const mieterId = 1;

      service.deleteMieter(mieterId).subscribe(response => {
        expect(response).toBeNull();
      });

      const req = httpMock.expectOne(`${apiUrl}/${mieterId}`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });
  });
});
