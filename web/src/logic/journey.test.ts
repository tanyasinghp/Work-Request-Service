import { describe, expect, it } from 'vitest'
import { SAMPLE_BATCH, SAMPLE_RESULT } from './fixtures'
import { inputIndexesFor, journeyFor } from './journey'

describe('card journey', () => {
  it('tells the escalated request its whole story', () => {
    const req101 = SAMPLE_RESULT.accepted[0]

    expect(journeyFor(req101, SAMPLE_RESULT, SAMPLE_BATCH)).toEqual([
      'Received at input #0',
      'Passed validation',
      'Kept as the earliest of 3; merged REQ-107, REQ-108',
      'Escalated Medium → Critical from REQ-108',
      'Queue position 1 of 6',
    ])
  })

  it('keeps a plain request short', () => {
    const req112 = SAMPLE_RESULT.accepted[5]

    expect(journeyFor(req112, SAMPLE_RESULT, SAMPLE_BATCH)).toEqual([
      'Received at input #12',
      'Passed validation',
      'Queue position 6 of 6',
    ])
  })

  it('finds every input position of an id and skips non-objects', () => {
    expect(inputIndexesFor('REQ-109', SAMPLE_BATCH)).toEqual([8, 9])
    expect(inputIndexesFor('REQ-1', [null, 42, { requestId: 'REQ-1' }])).toEqual([2])
  })
})
