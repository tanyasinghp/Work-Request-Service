import { Handle, Position, type Node, type NodeProps } from '@xyflow/react'
import { cx } from '../components/styles'

export type AgentStageTone = 'neutral' | 'passed' | 'stopped' | 'muted'

export type AgentStageNodeData = {
  label: string
  subtitle: string
  tone: AgentStageTone
  selected: boolean
}

export type AgentStageNodeType = Node<AgentStageNodeData, 'agentStage'>

const TONE_DOT: Record<AgentStageTone, string> = {
  neutral: 'bg-slate-400',
  passed: 'bg-emerald-500',
  stopped: 'bg-rose-500',
  muted: 'bg-slate-200',
}

export function AgentStageNode({ data }: NodeProps<AgentStageNodeType>) {
  const { label, subtitle, tone, selected } = data
  return (
    <div
      className={cx(
        'w-[176px] cursor-pointer rounded-xl border bg-white px-4 py-3 transition',
        selected ? 'border-indigo-500 ring-3 ring-indigo-100' : 'border-slate-200 hover:border-slate-300',
        tone === 'muted' && !selected && 'opacity-55',
      )}
    >
      <Handle id="in" type="target" position={Position.Left} />
      <Handle id="out" type="source" position={Position.Right} />
      {/* Bottom handles carry the red "retry" loop from Gate back to Implement. */}
      <Handle id="loop-in" type="target" position={Position.Bottom} />
      <Handle id="loop-out" type="source" position={Position.Bottom} />
      <div className="flex items-center justify-between">
        <span className="text-[13px] font-semibold text-slate-800">{label}</span>
        <span className={cx('size-2 rounded-full', TONE_DOT[tone])} />
      </div>
      <p className="mt-1 truncate text-[12px] text-slate-500">{subtitle}</p>
    </div>
  )
}
