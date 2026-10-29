import { describe, expect, it } from 'vitest'
import { sampleResult, SAMPLE_BATCH } from './fixtures'
import { deepEqual, sameOutput, seededRandom, shuffled } from './shuffle'

describe('shuffle', () => {
  it('returns a permutation and leaves the input untouched', () => {
    const before = JSON.stringify(SAMPLE_BATCH)

    const result = shuffled(SAMPLE_BATCH, seededRandom(42))

    expect(JSON.stringify(SAMPLE_BATCH)).toBe(before)
    expect(result).toHaveLength(SAMPLE_BATCH.length)
    expect(new Set(result)).toEqual(new Set(SAMPLE_BATCH))
    expect(result).not.toEqual(SAMPLE_BATCH)
  })

  it('repeats exactly for the same seed', () => {
    expect(shuffled(SAMPLE_BATCH, seededRandom(7))).toEqual(shuffled(SAMPLE_BATCH, seededRandom(7)))
  })
})

describe('shuffle comparison', () => {
  it('treats identical queue and trace as the same output', () => {
    expect(sameOutput(sampleResult(), sampleResult())).toBe(true)
  })

  it('ignores problems, whose input indices move with the order', () => {
    const shuffledRun = sampleResult()
    shuffledRun.problems = shuffledRun.problems.map((problem, index) => ({ ...problem, inputIndex: 100 + index })).reverse()

    expect(sameOutput(sampleResult(), shuffledRun)).toBe(true)
  })

  it('notices a different queue order', () => {
    const changed = sampleResult()
    changed.accepted.reverse()

    expect(sameOutput(sampleResult(), changed)).toBe(false)
  })

  it('notices a different priority on one card', () => {
    const changed = sampleResult()
    changed.accepted[0].priority = 'MEDIUM'

    expect(sameOutput(sampleResult(), changed)).toBe(false)
  })

  it('notices a different trace note', () => {
    const changed = sampleResult()
    changed.trace[1].notes = [...changed.trace[1].notes].reverse()

    expect(sameOutput(sampleResult(), changed)).toBe(false)
  })
})

describe('deepEqual', () => {
  it('compares structure, not identity', () => {
    expect(deepEqual({ a: [1, { b: null }] }, { a: [1, { b: null }] })).toBe(true)
    expect(deepEqual({ a: 1 }, { a: 1, b: undefined })).toBe(false)
    expect(deepEqual([1, 2], { 0: 1, 1: 2 })).toBe(false)
    expect(deepEqual(null, {})).toBe(false)
  })
})
