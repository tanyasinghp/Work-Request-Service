import type { Problem, ProblemCode } from '../types'

/** Lane order: validation problems first, then deduplication, matching the flow left to right. */
export const PROBLEM_CODES: readonly ProblemCode[] = [
  'NULL_REQUEST',
  'MISSING_FIELD',
  'INVALID_VALUE',
  'CONFLICTING_REQUEST_ID',
  'DUPLICATE',
]

export const PROBLEM_LABELS: Record<ProblemCode, string> = {
  NULL_REQUEST: 'Null request',
  MISSING_FIELD: 'Missing field',
  INVALID_VALUE: 'Invalid value',
  CONFLICTING_REQUEST_ID: 'Conflicting ID',
  DUPLICATE: 'Duplicate',
}

export interface ProblemGroup {
  code: ProblemCode
  label: string
  problems: Problem[]
}

/** Non-empty groups in lane order; within a group, by input position then field. */
export function groupProblemsByCode(problems: readonly Problem[]): ProblemGroup[] {
  return PROBLEM_CODES
    .map((code) => ({
      code,
      label: PROBLEM_LABELS[code],
      problems: problems
        .filter((problem) => problem.code === code)
        .sort((a, b) => a.inputIndex - b.inputIndex || (a.field ?? '').localeCompare(b.field ?? '')),
    }))
    .filter((group) => group.problems.length > 0)
}

/** e.g. "missing field ×3 · conflicting ID ×2 · duplicate ×2" */
export function summarizeGroups(groups: readonly ProblemGroup[]): string {
  return groups
    .map((group) => `${group.label.charAt(0).toLowerCase()}${group.label.slice(1)} ×${group.problems.length}`)
    .join(' · ')
}
