import type { ReactNode } from 'react'
import { EscalatedTag, Hint, Panel, PriorityPill } from '../components/ui'
import { cx, formatInstant, PROBLEM_STYLES } from '../components/styles'
import { escalatedTag, escalationsByKeptId, PRIORITY_LABELS } from '../logic/escalation'
import { journeyFor } from '../logic/journey'
import { PROBLEM_LABELS } from '../logic/problems'
import { FLOW_NODES, nodeCounts, traceFor } from '../logic/stepping'
import type { BatchResult, RawBatch } from '../types'
import type { Selection } from '../flows/useProcessing'

export function DetailPanel({ selection, result, input, step }: {
  selection: Selection
  result: BatchResult | null
  input: RawBatch | null
  step: number
}) {
  return (
    <Panel title="Details">
      {!result || !input || !selection ? (
        <Hint>Click a stage, a request card, or a problem to see what happened and why.</Hint>
      ) : selection.kind === 'stage' ? (
        <StageDetail nodeId={selection.nodeId} result={result} step={step} />
      ) : selection.kind === 'request' ? (
        <RequestDetail requestId={selection.requestId} result={result} input={input} />
      ) : (
        <ProblemDetail index={selection.index} result={result} />
      )}
    </Panel>
  )
}

function StageDetail({ nodeId, result, step }: { nodeId: string; result: BatchResult; step: number }) {
  const index = FLOW_NODES.findIndex((node) => node.id === nodeId)
  const node = FLOW_NODES[index]
  const counts = nodeCounts(index, result, step)
  if (!counts) {
    return <Hint>{node.label} hasn't run yet. Press Step to reach it.</Hint>
  }
  const notes = node.traceStage ? traceFor(result, node.traceStage)?.notes ?? [] : []
  return (
    <div className="space-y-4 p-4">
      <Heading title={node.label} subtitle={node.traceStage ? `${counts.in} in → ${counts.out} out` : `${counts.out} requests`} />
      {node.id === 'input' && <Text>The batch as received, before any rule is applied.</Text>}
      {node.id === 'queue' && <Text>Accepted requests, ordered by priority, then earliest request, then request id.</Text>}
      {node.traceStage && (notes.length === 0 ? <Text>Nothing to note: every request passed through unchanged.</Text> : (
        <ul className="space-y-1.5">
          {notes.map((note) => (
            <li key={note} className="rounded-md bg-slate-50 px-3 py-2 text-[13px] leading-snug text-slate-700 ring-1 ring-slate-100">{note}</li>
          ))}
        </ul>
      ))}
    </div>
  )
}

function RequestDetail({ requestId, result, input }: { requestId: string; result: BatchResult; input: RawBatch }) {
  const request = result.accepted.find((accepted) => accepted.requestId === requestId)
  if (!request) return <Hint>That request is no longer in the queue.</Hint>
  const tag = escalatedTag(escalationsByKeptId(result.problems).get(requestId))
  return (
    <div className="space-y-4 p-4">
      <Heading title={request.requestId} subtitle={request.equipmentId} />
      <div className="flex flex-wrap gap-2">
        <PriorityPill priority={request.priority} />
        {tag && <EscalatedTag label={tag} />}
      </div>
      <Fields rows={[
        ['Equipment', request.equipmentId],
        ['Requested', formatInstant(request.requestedAt)],
        ['Description', request.description],
      ]} />
      <div>
        <h4 className="mb-2 text-[11px] font-semibold tracking-wide text-slate-500 uppercase">Journey</h4>
        <ol className="relative space-y-2 border-l border-slate-200 pl-4">
          {journeyFor(request, result, input).map((line) => (
            <li key={line} className="relative text-[13px] text-slate-700">
              <span className="absolute top-1.5 -left-[21px] size-2 rounded-full bg-slate-400 ring-2 ring-white" />
              {line}
            </li>
          ))}
        </ol>
      </div>
    </div>
  )
}

function ProblemDetail({ index, result }: { index: number; result: BatchResult }) {
  const problem = result.problems[index]
  if (!problem) return <Hint>That problem is no longer in the result.</Hint>
  return (
    <div className="space-y-4 p-4">
      <Heading title={problem.requestId ?? 'Request without id'} subtitle={`Input #${problem.inputIndex}`} />
      <span className={cx('inline-block rounded-md px-2 py-0.5 text-[12px] font-semibold ring-1 ring-inset', PROBLEM_STYLES[problem.code].chip)}>
        {PROBLEM_LABELS[problem.code]}
      </span>
      <Text>{problem.message}</Text>
      <Fields rows={[
        ['Field', problem.field],
        ['Related request', problem.relatedRequestId],
        ['Escalation', problem.escalatedFrom && problem.escalatedTo
          ? `${PRIORITY_LABELS[problem.escalatedFrom]} → ${PRIORITY_LABELS[problem.escalatedTo]}`
          : null],
      ]} />
      <p className="text-[12px] text-slate-500">Its row is highlighted in the input batch.</p>
    </div>
  )
}

function Heading({ title, subtitle }: { title: string; subtitle: string }) {
  return (
    <div>
      <h3 className="font-mono text-[15px] font-semibold text-slate-900">{title}</h3>
      <p className="text-[12px] text-slate-500">{subtitle}</p>
    </div>
  )
}

function Text({ children }: { children: ReactNode }) {
  return <p className="text-[13px] leading-relaxed text-slate-700">{children}</p>
}

function Fields({ rows }: { rows: [string, string | null][] }) {
  const shown = rows.filter(([, value]) => value !== null)
  if (shown.length === 0) return null
  return (
    <dl className="grid grid-cols-[110px_minmax(0,1fr)] gap-x-3 gap-y-1.5 text-[13px]">
      {shown.map(([label, value]) => (
        <div key={label} className="contents">
          <dt className="text-slate-500">{label}</dt>
          <dd className="break-words text-slate-800">{value}</dd>
        </div>
      ))}
    </dl>
  )
}
