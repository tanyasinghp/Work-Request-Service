import type { Priority, Problem } from '../types'

export interface Escalation {
  from: Priority
  to: Priority
  /** The dropped duplicate whose priority raised the kept request. */
  byRequestId: string | null
}

export const PRIORITY_LABELS: Record<Priority, string> = {
  LOW: 'Low',
  MEDIUM: 'Medium',
  HIGH: 'High',
  CRITICAL: 'Critical',
}

/**
 * Escalations keyed by the kept request's id, read only from the structured
 * escalatedFrom/escalatedTo fields. Message text is never parsed.
 */
export function escalationsByKeptId(problems: readonly Problem[]): Map<string, Escalation> {
  const escalations = new Map<string, Escalation>()
  for (const problem of problems) {
    if (problem.code !== 'DUPLICATE' || !problem.escalatedFrom || !problem.escalatedTo || !problem.relatedRequestId) {
      continue
    }
    escalations.set(problem.relatedRequestId, {
      from: problem.escalatedFrom,
      to: problem.escalatedTo,
      byRequestId: problem.requestId,
    })
  }
  return escalations
}

/** The card tag, e.g. "escalated from Medium", or null when the request wasn't escalated. */
export function escalatedTag(escalation: Escalation | undefined): string | null {
  return escalation ? `escalated from ${PRIORITY_LABELS[escalation.from]}` : null
}
