import { describe, expect, it } from 'vitest'
import type { Problem } from '../types'
import { escalatedTag, escalationsByKeptId } from './escalation'
import { SAMPLE_RESULT } from './fixtures'

describe('escalated tag', () => {
  it('tags the sample request that was escalated, and only that one', () => {
    const escalations = escalationsByKeptId(SAMPLE_RESULT.problems)

    expect([...escalations.keys()]).toEqual(['REQ-101'])
    expect(escalations.get('REQ-101')).toEqual({ from: 'MEDIUM', to: 'CRITICAL', byRequestId: 'REQ-108' })
    expect(escalatedTag(escalations.get('REQ-101'))).toBe('escalated from Medium')
    expect(escalatedTag(escalations.get('REQ-102'))).toBeNull()
  })

  it('uses the structured fields, never the message text', () => {
    const messageOnly: Problem = {
      inputIndex: 7,
      requestId: 'REQ-108',
      code: 'DUPLICATE',
      field: null,
      relatedRequestId: 'REQ-101',
      escalatedFrom: null,
      escalatedTo: null,
      message: 'Duplicate of REQ-101; priority escalated MEDIUM to CRITICAL',
    }

    expect(escalationsByKeptId([messageOnly]).size).toBe(0)
  })

  it('ignores escalation fields on anything but a duplicate', () => {
    const odd: Problem = {
      inputIndex: 0, requestId: 'REQ-1', code: 'CONFLICTING_REQUEST_ID', field: null, relatedRequestId: 'REQ-2',
      escalatedFrom: 'LOW', escalatedTo: 'HIGH', message: 'x',
    }

    expect(escalationsByKeptId([odd]).size).toBe(0)
  })
})
