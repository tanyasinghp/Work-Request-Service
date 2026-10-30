import { describe, expect, it } from 'vitest'
import shippedRuns from '../data/agentRuns.json'
import {
  gateLoops,
  isPlaceholderData,
  parseAgentRuns,
  passedFirstTry,
  runOutcome,
  type AgentRun,
} from './agentRuns'
import placeholderRuns from './fixtures/placeholderRuns.json'

// The original placeholder, kept as a fixture: the shipped file now holds real runs.
const [placeholder] = parseAgentRuns(placeholderRuns)

function withGate(attempts: AgentRun['gate']['attempts']): AgentRun {
  return { ...placeholder, placeholder: undefined, gate: { attempts } }
}

describe('agent runs', () => {
  it('parses the shipped agentRuns.json, whatever runs it holds', () => {
    expect(() => parseAgentRuns(shippedRuns)).not.toThrow()
  })

  it('recognises the placeholder file, and only that, as placeholder data', () => {
    expect(isPlaceholderData(parseAgentRuns(placeholderRuns))).toBe(true)
    expect(isPlaceholderData([withGate([{ attempt: 1, passed: true }])])).toBe(false)
    expect(isPlaceholderData([])).toBe(false)
  })

  it('draws a loop back to Implement for a failed attempt that was retried', () => {
    expect(gateLoops(placeholder)).toEqual([
      { attempt: 1, label: 'attempt 1 failed: Placeholder.existingTestBrokenByAttempt1' },
    ])
    expect(runOutcome(placeholder)).toBe('passed')
    expect(passedFirstTry(placeholder)).toBe(false)
  })

  it('draws no loop for a first-try pass', () => {
    const run = withGate([{ attempt: 1, passed: true }])

    expect(gateLoops(run)).toEqual([])
    expect(passedFirstTry(run)).toBe(true)
  })

  it('stops after a second failure, with one loop for the retried attempt', () => {
    const run = withGate([
      { attempt: 1, passed: false, failingTest: 'A.first' },
      { attempt: 2, passed: false, failingTest: 'A.second' },
    ])

    expect(runOutcome(run)).toBe('stopped')
    expect(gateLoops(run)).toEqual([{ attempt: 1, label: 'attempt 1 failed: A.first' }])
  })

  it('labels a loop for an attempt the guardrails rejected before the gate', () => {
    const run = withGate([
      { attempt: 1, passed: false, rejectedBeforeGate: 'no file changes found in the diff' },
      { attempt: 2, passed: false, failingTest: 'OrderIndependenceTest.covers' },
    ])

    expect(gateLoops(run)).toEqual([{ attempt: 1, label: 'attempt 1 rejected before the gate' }])
  })

  it('treats a run that never reached the gate as stopped', () => {
    const run = withGate([])

    expect(runOutcome(run)).toBe('stopped')
    expect(passedFirstTry(run)).toBe(false)
    expect(gateLoops(run)).toEqual([])
  })

  it('rejects an export with the wrong shape', () => {
    expect(() => parseAgentRuns([])).toThrow('"runs" array')
    expect(() => parseAgentRuns({ runs: [{ id: 'x' }] })).toThrow('missing: title, story')
    expect(() => parseAgentRuns({ runs: [{ ...placeholder, gate: {} }] })).toThrow('no gate attempts list')
  })
})
