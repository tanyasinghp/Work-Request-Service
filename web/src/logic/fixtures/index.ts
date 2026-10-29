// Captured from the real API: POST /api/work-requests/batch with the 13-request sample.
import sampleBatch from '../../data/sampleBatch.json'
import type { BatchResult, RawBatch } from '../../types'
import sampleResultJson from './sampleResult.json'

export const SAMPLE_BATCH: RawBatch = sampleBatch
export const SAMPLE_RESULT = sampleResultJson as BatchResult

/** A deep copy, so a test can change it without affecting the others. */
export function sampleResult(): BatchResult {
  return structuredClone(SAMPLE_RESULT)
}
