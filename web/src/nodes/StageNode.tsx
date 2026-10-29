import { Handle, Position, type Node, type NodeProps } from '@xyflow/react'
import { motion } from 'framer-motion'
import { cx } from '../components/styles'
import type { NodeCounts, NodeStatus } from '../logic/stepping'

export type StageNodeData = {
  label: string
  counts: NodeCounts | null
  status: NodeStatus
  selected: boolean
  /** Input and Queue show a single count; stages show in → out. */
  endpoint: boolean
}

export type StageNodeType = Node<StageNodeData, 'stage'>

const MAX_DOTS = 24

export function StageNode({ data }: NodeProps<StageNodeType>) {
  const { label, counts, status, selected, endpoint } = data
  return (
    <div
      className={cx(
        'w-[188px] cursor-pointer rounded-xl border bg-white px-4 py-3 transition-[box-shadow,border-color,opacity] duration-300',
        selected ? 'border-indigo-500 ring-3 ring-indigo-100' : status === 'current' ? 'border-slate-400 shadow-md' : 'border-slate-200',
        status === 'waiting' && !selected && 'opacity-55',
      )}
    >
      <Handle type="target" position={Position.Left} />
      <Handle type="source" position={Position.Right} />

      <div className="flex items-center justify-between">
        <span className="text-[13px] font-semibold text-slate-800">{label}</span>
        <span
          className={cx(
            'size-2 rounded-full',
            status === 'done' && 'bg-emerald-500',
            status === 'current' && 'bg-indigo-500',
            status === 'waiting' && 'bg-slate-300',
          )}
          aria-label={status}
        />
      </div>

      {counts ? (
        <motion.div key={`${counts.in}:${counts.out}`} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3 }}>
          <div className="mt-1.5 font-mono text-[26px] leading-tight font-medium tabular-nums text-slate-900">
            {endpoint ? counts.out : (
              <>
                {counts.in}
                <span className="px-1 text-slate-300">→</span>
                {counts.out}
              </>
            )}
          </div>
          <div className="h-4 text-[11px] font-medium text-rose-600">
            {counts.removed > 0 ? `−${counts.removed} removed` : ''}
          </div>
          <div className="mt-1.5 flex h-[22px] flex-wrap content-start gap-[3px]">
            {Array.from({ length: Math.min(counts.out, MAX_DOTS) }, (_, index) => (
              <motion.span
                key={index}
                className="size-[7px] rounded-[2px] bg-slate-400"
                initial={{ opacity: 0, x: -10 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: 0.03 * index, duration: 0.25 }}
              />
            ))}
          </div>
        </motion.div>
      ) : (
        <>
          <div className="mt-1.5 font-mono text-[26px] leading-tight text-slate-300">—</div>
          <div className="h-[44px]" />
        </>
      )}
    </div>
  )
}
