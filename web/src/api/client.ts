import type { BatchResult, RawBatch } from '../types'

// `??` rather than `||`: an empty VITE_API_URL deliberately means "same origin" (nginx proxies /api).
const API_URL = (import.meta.env.VITE_API_URL ?? 'http://localhost:8080').replace(/\/$/, '')

export const UNREACHABLE_MESSAGE = 'API not reachable — is the backend running?'

export class ApiError extends Error {
  readonly kind: 'unreachable' | 'rejected'

  constructor(message: string, kind: 'unreachable' | 'rejected') {
    super(message)
    this.kind = kind
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${API_URL}${path}`, init)
  } catch {
    throw new ApiError(UNREACHABLE_MESSAGE, 'unreachable')
  }
  // Behind the nginx proxy a stopped backend shows up as a gateway error, not a network error.
  if ([502, 503, 504].includes(response.status)) {
    throw new ApiError(UNREACHABLE_MESSAGE, 'unreachable')
  }
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as { message?: string } | null
    throw new ApiError(body?.message ?? `The API answered ${response.status}`, 'rejected')
  }
  return (await response.json()) as T
}

export function fetchSample(): Promise<RawBatch> {
  return request<RawBatch>('/api/work-requests/sample')
}

/** Sends any JSON value: the API, not the UI, decides what a valid batch is. */
export function processBatch(batch: unknown): Promise<BatchResult> {
  return request<BatchResult>('/api/work-requests/batch', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(batch),
  })
}
