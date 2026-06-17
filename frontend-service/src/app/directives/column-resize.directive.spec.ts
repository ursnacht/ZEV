import { Component, ViewChild, ElementRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ColumnResizeDirective } from './column-resize.directive';

@Component({
  standalone: true,
  imports: [ColumnResizeDirective],
  template: `
    <table #testTable class="zev-table" appColumnResize>
      <thead>
        <tr>
          <th>Column 1</th>
          <th>Column 2</th>
          <th>Column 3</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td>Data 1</td>
          <td>Data 2</td>
          <td>Data 3</td>
        </tr>
      </tbody>
    </table>
  `
})
class TestHostComponent {
  @ViewChild('testTable') tableRef!: ElementRef<HTMLTableElement>;
}

describe('ColumnResizeDirective', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let component: TestHostComponent;
  let tableElement: HTMLTableElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TestHostComponent]
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(TestHostComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    tableElement = fixture.nativeElement.querySelector('table');

    // Wait for setTimeout in directive's ngAfterViewInit
    return new Promise<void>(resolve => {
      setTimeout(() => {
        fixture.detectChanges();
        resolve();
      }, 10);
    });
  });

  it('should create the directive', () => {
    expect(tableElement).toBeTruthy();
  });

  describe('initialization', () => {
    it('should add resize handles to table headers', () => {
      const handles = tableElement.querySelectorAll('.zev-table__resize-handle');
      expect(handles.length).toBe(2); // 3 columns - 1 (last column has no handle)
    });

    it('should not add resize handle to last column', () => {
      const headerCells = tableElement.querySelectorAll('thead th');
      const lastHeader = headerCells[headerCells.length - 1];
      const handleInLastColumn = lastHeader.querySelector('.zev-table__resize-handle');

      expect(handleInLastColumn).toBeNull();
    });

    it('should add resize handles inside th elements', () => {
      const firstHeader = tableElement.querySelector('thead th');
      const handle = firstHeader?.querySelector('.zev-table__resize-handle');

      expect(handle).toBeTruthy();
      expect(handle?.parentElement).toBe(firstHeader as HTMLElement);
    });
  });

  describe('resize handle interaction', () => {
    let handle: HTMLElement;
    let firstTh: HTMLTableCellElement;

    beforeEach(() => {
      handle = tableElement.querySelector('.zev-table__resize-handle') as HTMLElement;
      firstTh = tableElement.querySelector('thead th') as HTMLTableCellElement;
    });

    it('should prevent click from propagating', () => {
      const clickEvent = new MouseEvent('click', { bubbles: true });
      vi.spyOn(clickEvent, 'stopPropagation');

      handle.dispatchEvent(clickEvent);

      expect(clickEvent.stopPropagation).toHaveBeenCalled();
    });

    it('should auto-fit column on double-click', () => {
      const dblClickEvent = new MouseEvent('dblclick', { bubbles: true, cancelable: true });
      vi.spyOn(dblClickEvent, 'preventDefault');
      vi.spyOn(dblClickEvent, 'stopPropagation');

      // Get initial width
      const initialWidth = firstTh.style.width;

      handle.dispatchEvent(dblClickEvent);

      expect(dblClickEvent.preventDefault).toHaveBeenCalled();
      expect(dblClickEvent.stopPropagation).toHaveBeenCalled();

      // After auto-fit, width should be set
      expect(firstTh.style.width).toBeTruthy();
      expect(firstTh.style.minWidth).toBeTruthy();
    });

    it('should add resizing class to table on mousedown', () => {
      const mouseDownEvent = new MouseEvent('mousedown', {
        bubbles: true,
        cancelable: true,
        clientX: 100
      });

      handle.dispatchEvent(mouseDownEvent);

      expect(tableElement.classList.contains('zev-table--resizing')).toBe(true);
    });

    it('should add active class to handle on mousedown', () => {
      const mouseDownEvent = new MouseEvent('mousedown', {
        bubbles: true,
        cancelable: true,
        clientX: 100
      });

      handle.dispatchEvent(mouseDownEvent);

      expect(handle.classList.contains('zev-table__resize-handle--active')).toBe(true);
    });

    it('should remove resizing class on mouseup', () => {
      const mouseDownEvent = new MouseEvent('mousedown', {
        bubbles: true,
        cancelable: true,
        clientX: 100
      });
      handle.dispatchEvent(mouseDownEvent);

      expect(tableElement.classList.contains('zev-table--resizing')).toBe(true);

      const mouseUpEvent = new MouseEvent('mouseup', { bubbles: true });
      document.dispatchEvent(mouseUpEvent);

      expect(tableElement.classList.contains('zev-table--resizing')).toBe(false);
    });

    it('should remove active class from handle on mouseup', () => {
      const mouseDownEvent = new MouseEvent('mousedown', {
        bubbles: true,
        cancelable: true,
        clientX: 100
      });
      handle.dispatchEvent(mouseDownEvent);

      expect(handle.classList.contains('zev-table__resize-handle--active')).toBe(true);

      const mouseUpEvent = new MouseEvent('mouseup', { bubbles: true });
      document.dispatchEvent(mouseUpEvent);

      expect(handle.classList.contains('zev-table__resize-handle--active')).toBe(false);
    });
  });

  describe('cleanup', () => {
    it('should remove handles on destroy', () => {
      let handles = tableElement.querySelectorAll('.zev-table__resize-handle');
      expect(handles.length).toBe(2);

      fixture.destroy();

      handles = tableElement.querySelectorAll('.zev-table__resize-handle');
      expect(handles.length).toBe(0);
    });
  });
});

describe('ColumnResizeDirective with single column', () => {
  @Component({
    standalone: true,
    imports: [ColumnResizeDirective],
    template: `
      <table class="zev-table" appColumnResize>
        <thead>
          <tr>
            <th>Only Column</th>
          </tr>
        </thead>
      </table>
    `
  })
  class SingleColumnTestComponent {}

  let fixture: ComponentFixture<SingleColumnTestComponent>;
  let tableElement: HTMLTableElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SingleColumnTestComponent]
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(SingleColumnTestComponent);
    fixture.detectChanges();
    tableElement = fixture.nativeElement.querySelector('table');

    return new Promise<void>(resolve => {
      setTimeout(() => {
        fixture.detectChanges();
        resolve();
      }, 10);
    });
  });

  it('should not add any resize handles for single column table', () => {
    const handles = tableElement.querySelectorAll('.zev-table__resize-handle');
    expect(handles.length).toBe(0);
  });
});

describe('ColumnResizeDirective with empty table', () => {
  @Component({
    standalone: true,
    imports: [ColumnResizeDirective],
    template: `
      <table class="zev-table" appColumnResize>
        <thead>
          <tr></tr>
        </thead>
      </table>
    `
  })
  class EmptyTableTestComponent {}

  let fixture: ComponentFixture<EmptyTableTestComponent>;
  let tableElement: HTMLTableElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmptyTableTestComponent]
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(EmptyTableTestComponent);
    fixture.detectChanges();
    tableElement = fixture.nativeElement.querySelector('table');

    return new Promise<void>(resolve => {
      setTimeout(() => {
        fixture.detectChanges();
        resolve();
      }, 10);
    });
  });

  it('should handle empty table gracefully', () => {
    const handles = tableElement.querySelectorAll('.zev-table__resize-handle');
    expect(handles.length).toBe(0);
  });
});
