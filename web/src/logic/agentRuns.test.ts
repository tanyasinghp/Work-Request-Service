import { describe, expect, it } from 'vitest'
import agentRunsJson from '../data/agentRuns.json'
import { gateLoops, parseAgentRuns, passedFirstTry, runOutcome, type AgentRun } from './agentRuns'

const [placeholder] = parseAgentRuns(agentRunsJson)

function withGate(attempts: AgentRun['gate']['attempts']): AgentRun {
  return { ...placeholder, gate: { attempts } }
}

describe('agent runs', () => {
  it('ships one clearly labelled placeholder run', () => {
    const runs = parseAgentRuns(agentRunsJson)

    expect(runs).toHaveLength(1)
    expect(runs[0].placeholder).toBe(true)
    expect(runs[0].title).toContain('PLACEHOLDER')
  })

  it('draws a loop back to Implement for a failed attempt that was retried', () => {
    expect(gateLoops(placeholder)).toEqual([{ attempt: 1, failingTest: 'Placeholder.existingTestBrokenByAttempt1' }])
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
    expect(gateLoops(run)).toEqual([{ attempt: 1, failingTest: 'A.first' }])
  })

  it('rejects an export with the wrong shape', () => {
    expect(() => parseAgentRuns([])).toThrow('"runs" array')
    expect(() => parseAgentRuns({ runs: [{ id: 'x' }] })).toThrow('missing: title, story')
    expect(() => parseAgentRuns({ runs: [{ ...placeholder, gate: { attempts: [] } }] })).toThrow('no gate attempts')
  })
})
