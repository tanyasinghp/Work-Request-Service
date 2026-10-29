/**
 * Agent pipeline runs as exported to src/data/agentRuns.json. The pipeline overwrites that
 * file; this module checks its shape so a bad export fails loudly instead of half-rendering.
 */

export interface AcceptanceCriterion {
  given: string
  when: string
  then: string
}

export interface GateAttempt {
  attempt: number
  passed: boolean
  /** Set when the attempt failed: the first failing test, e.g. "DuplicateResolverTest.keepsEarliest". */
  failingTest?: string
  output?: string
}

export interface AgentRun {
  id: string
  title: string
  placeholder?: boolean
  date: string
  story: string
  enrich: { criteria: AcceptanceCriterion[]; affectedClasses: string[]; outOfScope: string[] }
  implement: { attempts: { attempt: number; summary: string; diffLines: number }[] }
  writeTests: { testNames: string[] }
  gate: { attempts: GateAttempt[] }
  summary: { text: string; manualFixes: string[] }
}

export type RunOutcome = 'passed' | 'stopped'

export interface GateLoop {
  /** The failed attempt that sent the run back to Implement. */
  attempt: number
  failingTest: string
}

export const AGENT_STAGES = [
  { id: 'story', label: 'Story' },
  { id: 'enrich', label: 'Enrich' },
  { id: 'implement', label: 'Implement' },
  { id: 'writeTests', label: 'Write tests' },
  { id: 'gate', label: 'Gate' },
  { id: 'summary', label: 'PR summary' },
] as const

export type AgentStageId = (typeof AGENT_STAGES)[number]['id']

export function parseAgentRuns(data: unknown): AgentRun[] {
  const runs = (data as { runs?: unknown })?.runs
  if (!Array.isArray(runs)) {
    throw new Error('agentRuns.json must be an object with a "runs" array')
  }
  runs.forEach((run, index) => {
    const candidate = run as Partial<AgentRun>
    const missing = (['id', 'title', 'story', 'enrich', 'implement', 'writeTests', 'gate', 'summary'] as const)
      .filter((key) => candidate?.[key] === undefined)
    if (missing.length > 0) {
      throw new Error(`Run ${index} in agentRuns.json is missing: ${missing.join(', ')}`)
    }
    if (!Array.isArray(candidate.gate?.attempts) || candidate.gate.attempts.length === 0) {
      throw new Error(`Run ${index} in agentRuns.json has no gate attempts`)
    }
  })
  return runs as AgentRun[]
}

/** A run passes when its last gate attempt passed; a second failure stops it. */
export function runOutcome(run: AgentRun): RunOutcome {
  return run.gate.attempts[run.gate.attempts.length - 1].passed ? 'passed' : 'stopped'
}

/** Each failed attempt that was followed by a retry draws a red edge back to Implement. */
export function gateLoops(run: AgentRun): GateLoop[] {
  return run.gate.attempts
    .filter((attempt, index) => !attempt.passed && index < run.gate.attempts.length - 1)
    .map((attempt) => ({ attempt: attempt.attempt, failingTest: attempt.failingTest ?? 'unknown test' }))
}

export function passedFirstTry(run: AgentRun): boolean {
  return run.gate.attempts[0].passed
}
