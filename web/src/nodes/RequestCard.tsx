import { motion } from 'framer-motion'
import { EscalatedTag, PriorityPill } from '../components/ui'
import { cx, formatInstant, PRIORITY_STYLES } from '../components/styles'
import type { AcceptedRequest } from '../types'

export function RequestCard({ request, position, escalatedTag, selected, onSelect }: {
  request: AcceptedRequest
  position: number
  escalatedTag: string | null
  selected: boolean
  onSelect: () => void
}) {
  return (
    <motion.button
      type="button"
      layout
      initial={{ opacity: 0, x: -24 }}
      animate={{ opacity: 1, x: 0 }}
      transition={{ delay: 0.08 * position, duration: 0.35, ease: 'easeOut' }}
      onClick={onSelect}
      className={cx(
        'grid w-full grid-cols-[28px_minmax(0,1fr)_auto] items-center gap-x-3 rounded-lg border border-l-4 bg-white px-3 py-2.5 text-left transition',
        PRIORITY_STYLES[request.priority].bar,
        selected ? 'border-indigo-400 bg-indigo-50/40 ring-2 ring-indigo-100' : 'border-slate-200 hover:border-slate-300 hover:bg-slate-50',
      )}
    >
      <span className="font-mono text-[12px] text-slate-400 tabular-nums">{position + 1}</span>
      <span className="min-w-0">
        <span className="flex items-center gap-2">
          <span className="font-mono text-[13px] font-semibold text-slate-900">{request.requestId}</span>
          <span className="truncate text-[12px] text-slate-500">{request.equipmentId}</span>
        </span>
        <span className="mt-0.5 block truncate text-[13px] text-slate-700">{request.description}</span>
      </span>
      <span className="flex flex-col items-end gap-1">
        <PriorityPill priority={request.priority} />
        {escalatedTag ? <EscalatedTag label={escalatedTag} /> : <span className="text-[11px] text-slate-400">{formatInstant(request.requestedAt)}</span>}
      </span>
    </motion.button>
  )
}
