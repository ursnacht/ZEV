/**
 * Global Vitest setup, executed after the application polyfills and the Angular
 * TestBed have been initialized by the `@angular/build:unit-test` builder.
 *
 * Purpose: provide jsdom shims for browser APIs that jsdom does not implement
 * but that the application code touches in tests (PDF/Blob download).
 */

// jsdom implements neither URL.createObjectURL nor revokeObjectURL.
// Define no-op shims so that production code paths (and vi.spyOn on them) work.
if (typeof URL.createObjectURL !== 'function') {
  URL.createObjectURL = () => 'blob:mock';
}
if (typeof URL.revokeObjectURL !== 'function') {
  URL.revokeObjectURL = () => {};
}

// jsdom implements neither DataTransfer nor DragEvent (used by the CSV drag & drop upload).
if (typeof (globalThis as any).DataTransfer === 'undefined') {
  class DataTransferShim {
    private readonly _files: File[] = [];
    readonly items = { add: (file: File): void => { this._files.push(file); } };
    get files(): FileList {
      const files = this._files;
      const list: Record<number, File> & Partial<FileList> = {
        length: files.length,
        item: (index: number) => files[index] ?? null,
      };
      files.forEach((file, index) => { list[index] = file; });
      (list as any)[Symbol.iterator] = function* () { yield* files; };
      return list as unknown as FileList;
    }
  }
  (globalThis as any).DataTransfer = DataTransferShim;
}
if (typeof (globalThis as any).DragEvent === 'undefined') {
  class DragEventShim extends Event {
    readonly dataTransfer: DataTransfer | null;
    constructor(type: string, init?: { dataTransfer?: DataTransfer } & EventInit) {
      super(type, init);
      this.dataTransfer = init?.dataTransfer ?? null;
    }
  }
  (globalThis as any).DragEvent = DragEventShim;
}
