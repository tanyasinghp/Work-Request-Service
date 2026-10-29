import type { ReactNode } from 'react'
import type { Priority } from '../types'
import { PRIORITY_LABELS } from '../logic/escalation'
import { cx, PRIORITY_STYLES } from './styles'

export function Button({ children, onClick, disabled, variant = 'secondary', title }: {
  children: ReactNode
  onClick: () => void
  disabled?: boolean
  variant?: 'primary' | 'secondary'
  title?: string
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      title={title}
      className={cx(
        'inline-flex h-9 items-center gap-2 rounded-lg px-3.5 text-[13px] font-medium transition',
        'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-500',
        'disabled:cursor-not-allowed disabled:opacity-40',
        variant === 'primary'
          ? 'bg-slate-900 text-white hover:bg-slate-700'
          : 'bg-white text-slate-700 ring-1 ring-slate-300 hover:bg-slate-50 hover:ring-slate-400',
      )}
    >
      {children}
    </button>
  )
}

export function PriorityPill({ priority }: { priority: Priority }) {
  return (
    <span className={cx('inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-[11px] font-semibold ring-1 ring-inset', PRIORITY_STYLES[priority].pill)}>
      <span className={cx('size-1.5 rounded-full', PRIORITY_STYLES[priority].dot)} />
      {PRIORITY_LABELS[priority]}
    </span>
  )
}

export function EscalatedTag({ label }: { label: string }) {
  return (
    <span className="inline-flex items-center gap-1 rounded-full bg-slate-800 px-2 py-0.5 text-[11px] font-medium text-white">
      <span aria-hidden>↑</span>
      {label}
    </span>
  )
}

export function Panel({ title, meta, children, className }: {
  title: string
  meta?: ReactNode
  children: ReactNode
  className?: string
}) {
  return (
    <section className={cx('flex min-h-0 flex-col rounded-xl border border-slate-200 bg-white', className)}>
      <header className="flex h-11 shrink-0 items-center justify-between border-b border-slate-100 px-4">
        <h2 className="text-[13px] font-semibold text-slate-800">{title}</h2>
        {meta && <div className="text-xs text-slate-500">{meta}</div>}
      </header>
      <div className="min-h-0 flex-1 overflow-y-auto">{children}</div>
    </section>
  )
}

export function Hint({ children }: { children: ReactNode }) {
  return <p className="px-4 py-6 text-center text-[13px] leading-relaxed text-slate-500">{children}</p>
}

export function Spinner() {
  return <span className="size-3.5 animate-spin rounded-full border-2 border-slate-300 border-t-slate-700" aria-hidden />
}
