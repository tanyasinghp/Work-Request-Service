import { describe, expect, it } from 'vitest'
import type { Problem } from '../types'
import { SAMPLE_RESULT } from './fixtures'
import { groupProblemsByCode, PROBLEM_CODES, summarizeGroups } from './problems'

function problem(overrides: Partial<Problem>): Problem {
  return {
    inputIndex: 0,
    requestId: 'REQ-1',
    code: 'MISSING_FIELD',
    field: null,
    relatedRequestId: null,
    escalatedFrom: null,
    escalatedTo: null,
    message: 'x',
    ...overrides,
  }
}

describe('grouping problems', () => {
  it('groups the sample by code in lane order', () => {
    const groups = groupProblemsByCode(SAMPLE_RESULT.problems)

    expect(groups.map((group) => [group.code, group.problems.length])).toEqual([
      ['MISSING_FIELD', 3],
      ['CONFLICTING_REQUEST_ID', 2],
      ['DUPLICATE', 2],
    ])
    expect(summarizeGroups(groups)).toBe('missing field ×3 · conflicting ID ×2 · duplicate ×2')
  })

  it('includes INVALID_VALUE as its own group, between missing fields and conflicts', () => {
    const groups = groupProblemsByCode([
      problem({ code: 'DUPLICATE', inputIndex: 1 }),
      problem({ code: 'INVALID_VALUE', inputIndex: 4, field: 'priority' }),
      problem({ code: 'MISSING_FIELD', inputIndex: 2 }),
      problem({ code: 'NULL_REQUEST', inputIndex: 3, requestId: null }),
    ])

    expect(groups.map((group) => group.code)).toEqual(['NULL_REQUEST', 'MISSING_FIELD', 'INVALID_VALUE', 'DUPLICATE'])
    expect(groups.find((group) => group.code === 'INVALID_VALUE')?.label).toBe('Invalid value')
    expect(PROBLEM_CODES).toHaveLength(5)
  })

  it('orders problems in a group by input position, then field', () => {
    const [group] = groupProblemsByCode([
      problem({ inputIndex: 5, field: 'requestedAt' }),
      problem({ inputIndex: 2, field: 'priority' }),
      problem({ inputIndex: 2, field: 'equipmentId' }),
    ])

    expect(group.problems.map((item) => [item.inputIndex, item.field]))
      .toEqual([[2, 'equipmentId'], [2, 'priority'], [5, 'requestedAt']])
  })

  it('returns no groups for no problems', () => {
    expect(groupProblemsByCode([])).toEqual([])
    expect(summarizeGroups([])).toBe('')
  })
})
