// Shapes returned by the backend (see backend api/dto). The UI only displays these;
// it never re-implements validation, deduplication or sorting.

export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export type ProblemCode =
  | 'NULL_REQUEST'
  | 'MISSING_FIELD'
  | 'INVALID_VALUE'
  | 'DUPLICATE'
  | 'CONFLICTING_REQUEST_ID'

export interface AcceptedRequest {
  requestId: string
  equipmentId: string
  priority: Priority
  requestedAt: string
  description: string
}

export interface Problem {
  inputIndex: number
  requestId: string | null
  code: ProblemCode
  field: string | null
  relatedRequestId: string | null
  escalatedFrom: Priority | null
  escalatedTo: Priority | null
  message: string
}

export interface StageTrace {
  stage: string
  in: number
  out: number
  notes: string[]
}

export interface BatchResult {
  accepted: AcceptedRequest[]
  problems: Problem[]
  trace: StageTrace[]
}

/** The batch exactly as sent: any JSON values, because broken input is the point of the demo. */
export type RawBatch = unknown[]
