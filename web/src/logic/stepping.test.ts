import { describe, expect, it } from 'vitest'
import { SAMPLE_RESULT } from './fixtures'
import {
  canStep,
  FLOW_NODES,
  isQueueRevealed,
  LAST_STEP,
  nextStep,
  nodeCounts,
  nodeStatus,
  revealedProblems,
} from './stepping'

describe('stage stepping', () => {
  it('advances one node per step and stops at the queue', () => {
    const visited = [0]
    let step = 0
    while (canStep(step, true)) {
      step = nextStep(step)
      visited.push(step)
    }
    expect(visited).toEqual([0, 1, 2, 3, 4])
    expect(FLOW_NODES[LAST_STEP].id).toBe('queue')
    expect(nextStep(LAST_STEP)).toBe(LAST_STEP)
  })

  it('cannot step before a batch has been processed', () => {
    expect(canStep(0, false)).toBe(false)
  })

  it('marks earlier nodes done, the reached node current, later nodes waiting', () => {
    expect([0, 1, 2, 3, 4].map((index) => nodeStatus(index, 2)))
      .toEqual(['done', 'done', 'current', 'waiting', 'waiting'])
  })

  it('shows in → out counts only for reached nodes', () => {
    expect(nodeCounts(1, SAMPLE_RESULT, 0)).toBeNull()
    expect(nodeCounts(0, SAMPLE_RESULT, 0)).toEqual({ in: 13, out: 13, removed: 0 })
    expect(nodeCounts(1, SAMPLE_RESULT, 1)).toEqual({ in: 13, out: 10, removed: 3 })
    expect(nodeCounts(2, SAMPLE_RESULT, 4)).toEqual({ in: 10, out: 6, removed: 4 })
    expect(nodeCounts(3, SAMPLE_RESULT, 4)).toEqual({ in: 6, out: 6, removed: 0 })
    expect(nodeCounts(4, SAMPLE_RESULT, 4)).toEqual({ in: 6, out: 6, removed: 0 })
  })

  it('reveals each problem once the stage that found it is reached', () => {
    expect(revealedProblems(SAMPLE_RESULT, 0)).toHaveLength(0)
    expect(revealedProblems(SAMPLE_RESULT, 1).map((problem) => problem.code))
      .toEqual(['MISSING_FIELD', 'MISSING_FIELD', 'MISSING_FIELD'])
    expect(revealedProblems(SAMPLE_RESULT, 2)).toHaveLength(7)
  })

  it('reveals the queue only at the last step', () => {
    expect(isQueueRevealed(3)).toBe(false)
    expect(isQueueRevealed(LAST_STEP)).toBe(true)
  })
})
