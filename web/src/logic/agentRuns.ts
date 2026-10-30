/**
 * Agent pipeline runs as exported to src/data/agentRuns.json by agent-pipeline/export_runs.py.
 * The export overwrites that file; this module checks its shape so a bad export fails loudly
 * instead of half-rendering.
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
  /** Set when the diff never reached the gate: a guardrail rejected it or it didn't apply. */
  rejectedBeforeGate?: string
  output?: string
}

export interface AgentRun {
  id: string
  title: string
  placeholder?: boolean
  date: string
  story: string
  outcome?: 'passed' | 'stopped' | 'error'
  stoppedAt?: string | null
  branch?: string | null
  llm?: { backend?: string | null; models?: string[] }
  durationSeconds?: number | null
  costUsd?: number | null
  enrich: { criteria: AcceptanceCriterion[]; affectedClasses: string[]; outOfScope: string[] }
  implement: { attempts: { attempt: number; summary: string; diffLines: number }[] }
  writeTests: { testNames: string[] }
  gate: { attempts: GateAttempt[] }
  summary: {
    text: string
    manualFixes: string[]
    /** False until a human has reviewed the agent branch; then manualFixes is the review's verdict. */
    reviewed?: boolean
    reviewNotes?: string[]
  }
}

export type RunOutcome = 'passed' | 'stopped'

export interface GateLoop {
  /** The failed attempt that sent the run back to Implement. */
  attempt: number
  label: string
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
    if (!Array.isArray(candidate.gate?.attempts)) {
      throw new Error(`Run ${index} in agentRuns.json has no gate attempts list`)
    }
  })
  return runs as AgentRun[]
}

/** A run passes when its last gate attempt passed. No attempts means it stopped earlier. */
export function runOutcome(run: AgentRun): RunOutcome {
  const attempts = run.gate.attempts
  return attempts.length > 0 && attempts[attempts.length - 1].passed ? 'passed' : 'stopped'
}

/** Each failed attempt that was followed by a retry draws a red edge back to Implement. */
export function gateLoops(run: AgentRun): GateLoop[] {
  return run.gate.attempts
    .filter((attempt, index) => !attempt.passed && index < run.gate.attempts.length - 1)
    .map((attempt) => ({
      attempt: attempt.attempt,
      label: attempt.rejectedBeforeGate
        ? `attempt ${attempt.attempt} rejected before the gate`
        : `attempt ${attempt.attempt} failed: ${attempt.failingTest ?? 'unknown test'}`,
    }))
}

export function passedFirstTry(run: AgentRun): boolean {
  return run.gate.attempts[0]?.passed ?? false
}

/** True only while the shipped file still holds the placeholder rather than real runs. */
export function isPlaceholderData(runs: readonly AgentRun[]): boolean {
  return runs.length > 0 && runs.every((run) => run.placeholder === true)
}
