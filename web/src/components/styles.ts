import type { Priority, ProblemCode } from '../types'

/** Joins class names, skipping falsy ones. */
export function cx(...classes: (string | false | null | undefined)[]): string {
  return classes.filter(Boolean).join(' ')
}

// Full class strings (not built from fragments) so Tailwind can find them in the source.
export const PRIORITY_STYLES: Record<Priority, { bar: string; pill: string; dot: string }> = {
  LOW: { bar: 'border-l-slate-400', pill: 'bg-slate-100 text-slate-700 ring-slate-300', dot: 'bg-slate-400' },
  MEDIUM: { bar: 'border-l-blue-500', pill: 'bg-blue-50 text-blue-700 ring-blue-200', dot: 'bg-blue-500' },
  HIGH: { bar: 'border-l-amber-500', pill: 'bg-amber-50 text-amber-800 ring-amber-200', dot: 'bg-amber-500' },
  CRITICAL: { bar: 'border-l-red-600', pill: 'bg-red-50 text-red-700 ring-red-200', dot: 'bg-red-600' },
}

export const PROBLEM_STYLES: Record<ProblemCode, { chip: string; dot: string }> = {
  NULL_REQUEST: { chip: 'bg-slate-50 text-slate-700 ring-slate-200', dot: 'bg-slate-500' },
  MISSING_FIELD: { chip: 'bg-orange-50 text-orange-800 ring-orange-200', dot: 'bg-orange-500' },
  INVALID_VALUE: { chip: 'bg-violet-50 text-violet-800 ring-violet-200', dot: 'bg-violet-500' },
  CONFLICTING_REQUEST_ID: { chip: 'bg-rose-50 text-rose-800 ring-rose-200', dot: 'bg-rose-500' },
  DUPLICATE: { chip: 'bg-teal-50 text-teal-800 ring-teal-200', dot: 'bg-teal-500' },
}

const TIME_FORMAT = new Intl.DateTimeFormat('en-GB', {
  day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', timeZone: 'UTC',
})

/** "20 Oct, 08:15 UTC": times are Instants, so they're shown in UTC, never the viewer's zone. */
export function formatInstant(iso: string): string {
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? iso : `${TIME_FORMAT.format(date)} UTC`
}

/** Shows a raw input value so that blanks, nulls and wrong types stay visible. */
export function displayRaw(value: unknown): string {
  if (value === undefined) return '—'
  if (typeof value === 'string') return value.trim() === '' ? JSON.stringify(value) : value
  return JSON.stringify(value)
}
