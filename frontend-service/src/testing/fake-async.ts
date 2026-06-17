import { vi } from 'vitest';

/**
 * Drop-in replacements for Angular's `fakeAsync`/`tick`, backed by Vitest's fake
 * timers. Angular's own `fakeAsync` relies on a zone.js ProxyZone that zone.js
 * does not install for the Vitest runner, so the synchronous timer-based tests
 * (e.g. message auto-dismiss via `setTimeout`) use these instead.
 *
 * Limitation: only timer-driven async is supported (setTimeout/setInterval), not
 * zone microtask draining. The migrated tests rely on synchronous RxJS (`of`),
 * so this is sufficient.
 */
export function fakeAsync<T extends (...args: any[]) => any>(
  fn: T,
): (...args: Parameters<T>) => ReturnType<T> {
  return function (this: unknown, ...args: Parameters<T>): ReturnType<T> {
    vi.useFakeTimers();
    try {
      return fn.apply(this, args);
    } finally {
      vi.useRealTimers();
    }
  };
}

/** Advances the (faked) clock by `millis` milliseconds, running due timers. */
export function tick(millis = 0): void {
  vi.advanceTimersByTime(millis);
}

/** Runs all currently pending timers. */
export function flush(): void {
  vi.runOnlyPendingTimers();
}
