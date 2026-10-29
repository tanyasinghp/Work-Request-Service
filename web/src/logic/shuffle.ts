import type { BatchResult } from '../types'

/** Small seeded generator (mulberry32) so tests and demos can repeat a shuffle exactly. */
export function seededRandom(seed: number): () => number {
  let state = seed >>> 0
  return () => {
    state = (state + 0x6d2b79f5) >>> 0
    let t = state
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

/** Fisher-Yates on a copy; the input is never modified. */
export function shuffled<T>(items: readonly T[], random: () => number = Math.random): T[] {
  const copy = [...items]
  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1))
    ;[copy[i], copy[j]] = [copy[j], copy[i]]
  }
  return copy
}

/**
 * Same queue and same trace. Problems are left out on purpose: their inputIndex moves with
 * the input order, so they differ after a shuffle even when the processing was identical.
 */
export function sameOutput(previous: BatchResult, current: BatchResult): boolean {
  return deepEqual(previous.accepted, current.accepted) && deepEqual(previous.trace, current.trace)
}

export function deepEqual(a: unknown, b: unknown): boolean {
  if (a === b) return true
  if (typeof a !== 'object' || typeof b !== 'object' || a === null || b === null) return false
  if (Array.isArray(a) !== Array.isArray(b)) return false
  if (Array.isArray(a) && Array.isArray(b)) {
    return a.length === b.length && a.every((item, index) => deepEqual(item, b[index]))
  }
  const aEntries = Object.entries(a)
  const bRecord = b as Record<string, unknown>
  return aEntries.length === Object.keys(bRecord).length
    && aEntries.every(([key, value]) => key in bRecord && deepEqual(value, bRecord[key]))
}
