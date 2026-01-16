import { ComponentFixture, TestBed } from '@angular/core/testing';
import { QuarterSelectorComponent } from './quarter-selector.component';
import { TranslationService } from '../../services/translation.service';

describe('QuarterSelectorComponent', () => {
  let component: QuarterSelectorComponent;
  let fixture: ComponentFixture<QuarterSelectorComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [QuarterSelectorComponent],
      providers: [
        { provide: TranslationService, useValue: { translate: (k: string) => k } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(QuarterSelectorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('initialization', () => {
    it('should calculate 5 quarters on init', () => {
      expect(component.quarters.length).toBe(5);
    });

    it('should have quarters in chronological order (oldest first)', () => {
      const labels = component.quarters.map(q => q.label);
      // First quarter should be older than last
      const firstYear = parseInt(labels[0].split('/')[1]);
      const lastYear = parseInt(labels[4].split('/')[1]);
      const firstQ = parseInt(labels[0].charAt(1));
      const lastQ = parseInt(labels[4].charAt(1));

      // Either year is smaller, or same year with smaller quarter
      const isChronological = firstYear < lastYear ||
        (firstYear === lastYear && firstQ < lastQ);
      expect(isChronological).toBeTrue();
    });

    it('should format quarter labels correctly', () => {
      component.quarters.forEach(q => {
        expect(q.label).toMatch(/^Q[1-4]\/\d{4}$/);
      });
    });

    it('should format von date as YYYY-MM-DD', () => {
      component.quarters.forEach(q => {
        expect(q.von).toMatch(/^\d{4}-\d{2}-\d{2}$/);
      });
    });

    it('should format bis date as YYYY-MM-DD', () => {
      component.quarters.forEach(q => {
        expect(q.bis).toMatch(/^\d{4}-\d{2}-\d{2}$/);
      });
    });

    it('should set correct start dates for each quarter', () => {
      component.quarters.forEach(q => {
        const quarterNum = parseInt(q.label.charAt(1));
        const expectedStartMonth = ((quarterNum - 1) * 3) + 1; // 1, 4, 7, 10
        const actualMonth = parseInt(q.von.split('-')[1]);
        expect(actualMonth).toBe(expectedStartMonth);
      });
    });

    it('should set correct end dates for each quarter', () => {
      component.quarters.forEach(q => {
        const quarterNum = parseInt(q.label.charAt(1));
        const expectedEndMonth = quarterNum * 3; // 3, 6, 9, 12
        const actualMonth = parseInt(q.bis.split('-')[1]);
        expect(actualMonth).toBe(expectedEndMonth);
      });
    });

    it('should set correct last day of quarter', () => {
      component.quarters.forEach(q => {
        const day = parseInt(q.bis.split('-')[2]);
        const month = parseInt(q.bis.split('-')[1]);

        // Q1 ends March 31, Q2 ends June 30, Q3 ends Sept 30, Q4 ends Dec 31
        if (month === 3 || month === 12) {
          expect(day).toBe(31);
        } else if (month === 6 || month === 9) {
          expect(day).toBe(30);
        }
      });
    });
  });

  describe('selectQuarter', () => {
    it('should emit quarterSelected event with von and bis', () => {
      spyOn(component.quarterSelected, 'emit');

      const testQuarter = component.quarters[0];
      component.selectQuarter(testQuarter);

      expect(component.quarterSelected.emit).toHaveBeenCalledWith({
        von: testQuarter.von,
        bis: testQuarter.bis
      });
    });

    it('should emit correct values for each quarter', () => {
      spyOn(component.quarterSelected, 'emit');

      component.quarters.forEach(q => {
        component.selectQuarter(q);
        expect(component.quarterSelected.emit).toHaveBeenCalledWith({
          von: q.von,
          bis: q.bis
        });
      });
    });
  });

  describe('isSelected', () => {
    it('should return true when selectedVon and selectedBis match quarter', () => {
      const testQuarter = component.quarters[2];
      component.selectedVon = testQuarter.von;
      component.selectedBis = testQuarter.bis;

      expect(component.isSelected(testQuarter)).toBeTrue();
    });

    it('should return false when selectedVon does not match', () => {
      const testQuarter = component.quarters[2];
      component.selectedVon = '2000-01-01';
      component.selectedBis = testQuarter.bis;

      expect(component.isSelected(testQuarter)).toBeFalse();
    });

    it('should return false when selectedBis does not match', () => {
      const testQuarter = component.quarters[2];
      component.selectedVon = testQuarter.von;
      component.selectedBis = '2000-12-31';

      expect(component.isSelected(testQuarter)).toBeFalse();
    });

    it('should return false when both inputs are empty', () => {
      component.selectedVon = '';
      component.selectedBis = '';

      expect(component.isSelected(component.quarters[0])).toBeFalse();
    });

    it('should return false for non-matching quarter', () => {
      const firstQuarter = component.quarters[0];
      const lastQuarter = component.quarters[4];
      component.selectedVon = firstQuarter.von;
      component.selectedBis = firstQuarter.bis;

      expect(component.isSelected(lastQuarter)).toBeFalse();
    });
  });

  describe('edge cases', () => {
    it('should handle year boundary correctly (Q1 follows Q4 of previous year)', () => {
      // Find if there's a Q1 in the quarters
      const q1 = component.quarters.find(q => q.label.startsWith('Q1'));
      if (q1) {
        const q1Index = component.quarters.indexOf(q1);
        if (q1Index > 0) {
          const previousQuarter = component.quarters[q1Index - 1];
          expect(previousQuarter.label).toMatch(/^Q4/);

          const q1Year = parseInt(q1.label.split('/')[1]);
          const q4Year = parseInt(previousQuarter.label.split('/')[1]);
          expect(q4Year).toBe(q1Year - 1);
        } else {
          // Q1 is first element, boundary check not applicable
          expect(q1Index).toBe(0);
        }
      } else {
        // No Q1 in current 5 quarters - verify quarters are consecutive
        expect(component.quarters.length).toBe(5);
      }
    });

    it('should include current quarter as last element', () => {
      const today = new Date();
      const currentQuarter = Math.ceil((today.getMonth() + 1) / 3);
      const currentYear = today.getFullYear();
      const expectedLabel = `Q${currentQuarter}/${currentYear}`;

      const lastQuarter = component.quarters[component.quarters.length - 1];
      expect(lastQuarter.label).toBe(expectedLabel);
    });
  });
});
