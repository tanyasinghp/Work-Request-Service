import type { AcceptedRequest, BatchResult, RawBatch } from '../types'
import { escalationsByKeptId, PRIORITY_LABELS } from './escalation'

/**
 * Positions in the raw input whose element carries this requestId. The API doesn't return
 * an index for accepted requests; after deduplication only exact repeats share an id, and
 * those are identical, so matching by id is unambiguous.
 */
export function inputIndexesFor(requestId: string, input: RawBatch): number[] {
  return input.flatMap((element, index) =>
    typeof element === 'object' && element !== null && (element as { requestId?: unknown }).requestId === requestId
      ? [index]
      : [],
  )
}

/** Plain-language steps a card took through the pipeline, built from the response only. */
export function journeyFor(request: AcceptedRequest, result: BatchResult, input: RawBatch): string[] {
  const id = request.requestId
  const steps: string[] = []

  const positions = inputIndexesFor(id, input)
  steps.push(positions.length > 0 ? `Received at input #${positions.join(', #')}` : 'Received')
  steps.push('Passed validation')

  const dropped = result.problems.filter((problem) => problem.code === 'DUPLICATE' && problem.relatedRequestId === id)
  const repeats = dropped.filter((problem) => problem.requestId === id)
  const duplicates = dropped.filter((problem) => problem.requestId !== id)
  if (duplicates.length > 0) {
    steps.push(`Kept as the earliest of ${duplicates.length + 1}; merged ${duplicates.map((problem) => problem.requestId).join(', ')}`)
  }
  if (repeats.length > 0) {
    steps.push(`Exact repeat${repeats.length > 1 ? 's' : ''} at input #${repeats.map((problem) => problem.inputIndex).join(', #')} dropped`)
  }

  const escalation = escalationsByKeptId(result.problems).get(id)
  if (escalation) {
    steps.push(`Escalated ${PRIORITY_LABELS[escalation.from]} → ${PRIORITY_LABELS[escalation.to]}`
      + (escalation.byRequestId ? ` from ${escalation.byRequestId}` : ''))
  }

  const position = result.accepted.findIndex((accepted) => accepted.requestId === id)
  steps.push(`Queue position ${position + 1} of ${result.accepted.length}`)
  return steps
}
