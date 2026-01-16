import { Directive, ElementRef, AfterViewInit, OnDestroy, Renderer2 } from '@angular/core';

@Directive({
  selector: '[appColumnResize]',
  standalone: true
})
export class ColumnResizeDirective implements AfterViewInit, OnDestroy {
  private table: HTMLTableElement;
  private resizeHandles: HTMLElement[] = [];
  private isResizing = false;
  private currentHandle: HTMLElement | null = null;
  private currentTh: HTMLTableCellElement | null = null;
  private startX = 0;
  private startWidth = 0;

  private mouseMoveListener: (() => void) | null = null;
  private mouseUpListener: (() => void) | null = null;

  constructor(
    private el: ElementRef<HTMLTableElement>,
    private renderer: Renderer2
  ) {
    this.table = this.el.nativeElement;
  }

  ngAfterViewInit(): void {
    setTimeout(() => this.initResizeHandles(), 0);
  }

  ngOnDestroy(): void {
    this.removeGlobalListeners();
    this.resizeHandles.forEach(handle => handle.remove());
  }

  private initResizeHandles(): void {
    const headerCells = this.table.querySelectorAll('thead th');
    const cellCount = headerCells.length;

    headerCells.forEach((th, index) => {
      // Skip last column - no resize handle
      if (index === cellCount - 1) {
        return;
      }

      const handle = this.renderer.createElement('div');
      this.renderer.addClass(handle, 'zev-table__resize-handle');
      this.renderer.appendChild(th, handle);
      this.resizeHandles.push(handle);

      // Mousedown starts resize
      this.renderer.listen(handle, 'mousedown', (event: MouseEvent) => {
        this.onMouseDown(event, th as HTMLTableCellElement, handle);
      });

      // Auto-fit column on double-click
      this.renderer.listen(handle, 'dblclick', (event: MouseEvent) => {
        event.preventDefault();
        event.stopPropagation();
        this.autoFitColumn(th as HTMLTableCellElement, index);
      });

      // Prevent click from triggering sort
      this.renderer.listen(handle, 'click', (event: MouseEvent) => {
        event.stopPropagation();
      });
    });
  }

  private onMouseDown(event: MouseEvent, th: HTMLTableCellElement, handle: HTMLElement): void {
    event.preventDefault();
    event.stopPropagation();

    this.isResizing = true;
    this.currentHandle = handle;
    this.currentTh = th;
    this.startX = event.pageX;
    this.startWidth = th.offsetWidth;

    this.renderer.addClass(this.table, 'zev-table--resizing');
    this.renderer.addClass(handle, 'zev-table__resize-handle--active');

    this.addGlobalListeners();
  }

  private onMouseMove = (event: MouseEvent): void => {
    if (!this.isResizing || !this.currentTh) {
      return;
    }

    requestAnimationFrame(() => {
      if (!this.currentTh) return;

      const diff = event.pageX - this.startX;
      const newWidth = Math.max(this.getMinWidth(this.currentTh), this.startWidth + diff);

      this.renderer.setStyle(this.currentTh, 'width', `${newWidth}px`);
      this.renderer.setStyle(this.currentTh, 'min-width', `${newWidth}px`);
    });
  };

  private onMouseUp = (): void => {
    if (!this.isResizing) {
      return;
    }

    this.isResizing = false;
    this.renderer.removeClass(this.table, 'zev-table--resizing');

    if (this.currentHandle) {
      this.renderer.removeClass(this.currentHandle, 'zev-table__resize-handle--active');
    }

    this.currentHandle = null;
    this.currentTh = null;

    this.removeGlobalListeners();
  };

  private autoFitColumn(th: HTMLTableCellElement, columnIndex: number): void {
    // Get all cells in this column (header + body)
    const headerCell = th;
    const bodyCells = this.table.querySelectorAll(`tbody tr td:nth-child(${columnIndex + 1})`);

    // Calculate max width needed
    let maxWidth = this.measureContentWidth(headerCell);

    bodyCells.forEach(cell => {
      const cellWidth = this.measureContentWidth(cell as HTMLElement);
      maxWidth = Math.max(maxWidth, cellWidth);
    });

    // Apply the width
    this.renderer.setStyle(th, 'width', `${maxWidth}px`);
    this.renderer.setStyle(th, 'min-width', `${maxWidth}px`);
  }

  private measureContentWidth(cell: HTMLElement): number {
    // Check if cell contains an input element
    const input = cell.querySelector('input, textarea, select');
    if (input) {
      // For inputs, measure the value text width
      const inputEl = input as HTMLInputElement;
      const span = document.createElement('span');
      span.style.visibility = 'hidden';
      span.style.position = 'absolute';
      span.style.whiteSpace = 'nowrap';
      span.style.font = window.getComputedStyle(inputEl).font;
      span.textContent = inputEl.value || inputEl.placeholder || '';
      document.body.appendChild(span);
      const textWidth = span.offsetWidth;
      document.body.removeChild(span);

      // Add input padding/border
      const inputStyle = window.getComputedStyle(inputEl);
      const inputPadding = parseFloat(inputStyle.paddingLeft) + parseFloat(inputStyle.paddingRight);
      const inputBorder = parseFloat(inputStyle.borderLeftWidth) + parseFloat(inputStyle.borderRightWidth);

      return textWidth + inputPadding + inputBorder + 20; // Extra space for cell padding
    }

    // For regular text content
    const span = document.createElement('span');
    span.style.visibility = 'hidden';
    span.style.position = 'absolute';
    span.style.whiteSpace = 'nowrap';
    span.style.font = window.getComputedStyle(cell).font;
    span.textContent = cell.textContent || '';
    document.body.appendChild(span);
    const textWidth = span.offsetWidth;
    document.body.removeChild(span);

    // Add cell padding
    const computedStyle = window.getComputedStyle(cell);
    const paddingLeft = parseFloat(computedStyle.paddingLeft) || 0;
    const paddingRight = parseFloat(computedStyle.paddingRight) || 0;

    return Math.max(50, textWidth + paddingLeft + paddingRight + 10);
  }

  private getMinWidth(th: HTMLTableCellElement): number {
    // Calculate minimum width based on text content
    const span = document.createElement('span');
    span.style.visibility = 'hidden';
    span.style.position = 'absolute';
    span.style.whiteSpace = 'nowrap';
    span.style.font = window.getComputedStyle(th).font;
    span.textContent = th.textContent || '';
    document.body.appendChild(span);

    const textWidth = span.offsetWidth;
    document.body.removeChild(span);

    // Add padding and resize handle width
    const computedStyle = window.getComputedStyle(th);
    const paddingLeft = parseFloat(computedStyle.paddingLeft) || 0;
    const paddingRight = parseFloat(computedStyle.paddingRight) || 0;

    return Math.max(50, textWidth + paddingLeft + paddingRight + 10);
  }

  private addGlobalListeners(): void {
    this.mouseMoveListener = this.renderer.listen('document', 'mousemove', this.onMouseMove);
    this.mouseUpListener = this.renderer.listen('document', 'mouseup', this.onMouseUp);
  }

  private removeGlobalListeners(): void {
    if (this.mouseMoveListener) {
      this.mouseMoveListener();
      this.mouseMoveListener = null;
    }
    if (this.mouseUpListener) {
      this.mouseUpListener();
      this.mouseUpListener = null;
    }
  }
}
