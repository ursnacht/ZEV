import { vi, type Mock } from 'vitest';

/**
 * Vitest equivalent of Jasmine's `SpyObj<T>`: every method of `T` becomes a
 * Vitest `Mock`, so `.mockReturnValue()`, `.mockImplementation()` etc. are available.
 */
export type SpyObj<T> = {
  [K in keyof T]: T[K] extends (...args: any[]) => any ? Mock<T[K]> : T[K];
};

/**
 * Drop-in replacement for `jasmine.createSpyObj`: returns an object whose listed
 * methods are `vi.fn()` spies.
 *
 * @param _baseName Ignored (kept for call-site compatibility with the Jasmine API).
 * @param methodNames Names of the methods to stub.
 */
export function createSpyObj<T>(_baseName: string, methodNames: (keyof T)[]): SpyObj<T> {
  const obj = {} as SpyObj<T>;
  for (const name of methodNames) {
    obj[name] = vi.fn() as SpyObj<T>[typeof name];
  }
  return obj;
}
