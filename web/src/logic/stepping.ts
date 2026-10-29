import type { BatchResult, Problem, ProblemCode, StageTrace } from '../types'

export type FlowNodeId = 'input' | 'validate' | 'deduplicate' | 'prioritize' | 'queue'

export interface FlowNodeDefinition {
  id: FlowNodeId
  label: string
  /** The backend trace entry this node shows; input and queue have none. */
  traceStage?: 'validate' | 'deduplicate' | 'prioritize'
}

export const FLOW_NODES: readonly FlowNodeDefinition[] = [
  { id: 'input', label: 'Input' },
  { id: 'validate', label: 'Validate', traceStage: 'validate' },
  { id: 'deduplicate', label: 'Deduplicate', traceStage: 'deduplicate' },
  { id: 'prioritize', label: 'Prioritize', traceStage: 'prioritize' },
  { id: 'queue', label: 'Queue' },
]

/** Step n means nodes 0..n are revealed. Step 0 shows only the input. */
export const FIRST_STEP = 0
export const LAST_STEP = FLOW_NODES.length - 1

export type NodeStatus = 'waiting' | 'current' | 'done'

export function nextStep(step: number): number {
  return Math.min(step + 1, LAST_STEP)
}

export function canStep(step: number, hasResult: boolean): boolean {
  return hasResult && step < LAST_STEP
}

export function nodeStatus(nodeIndex: number, step: number): NodeStatus {
  if (nodeIndex < step) return 'done'
  if (nodeIndex === step) return 'current'
  return 'waiting'
}

export interface NodeCounts {
  in: number
  out: number
  removed: number
}

export function traceFor(result: BatchResult, stage: string): StageTrace | undefined {
  return result.trace.find((entry) => entry.stage === stage)
}

/** Counts for a node, or null while the node hasn't been reached yet. */
export function nodeCounts(nodeIndex: number, result: BatchResult, step: number): NodeCounts | null {
  if (nodeIndex > step) return null
  const node = FLOW_NODES[nodeIndex]
  if (node.id === 'input') {
    const received = result.trace[0]?.in ?? 0
    return { in: received, out: received, removed: 0 }
  }
  if (node.id === 'queue') {
    return { in: result.accepted.length, out: result.accepted.length, removed: 0 }
  }
  const trace = node.traceStage ? traceFor(result, node.traceStage) : undefined
  if (!trace) return null
  return { in: trace.in, out: trace.out, removed: trace.in - trace.out }
}

const STAGE_OF_CODE: Record<ProblemCode, 'validate' | 'deduplicate'> = {
  NULL_REQUEST: 'validate',
  MISSING_FIELD: 'validate',
  INVALID_VALUE: 'validate',
  CONFLICTING_REQUEST_ID: 'deduplicate',
  DUPLICATE: 'deduplicate',
}

export function stageOfProblem(problem: Problem): 'validate' | 'deduplicate' {
  return STAGE_OF_CODE[problem.code]
}

/** Problems appear in the lane only once the stage that found them has been reached. */
export function revealedProblems(result: BatchResult, step: number): Problem[] {
  const reached = new Set(FLOW_NODES.slice(0, step + 1).map((node) => node.id))
  return result.problems.filter((problem) => reached.has(stageOfProblem(problem)))
}

export function isQueueRevealed(step: number): boolean {
  return step >= LAST_STEP
}
