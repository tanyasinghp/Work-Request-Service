import { useEffect, useRef } from 'react'
import { Hint, Panel } from '../components/ui'
import { cx, displayRaw, PROBLEM_STYLES } from '../components/styles'
import type { Problem, RawBatch } from '../types'

export function InputPanel({ input, problems, highlighted }: {
  input: RawBatch | null
  /** Problems revealed so far, shown as dots on their rows. */
  problems: Problem[]
  highlighted: number[]
}) {
  const rows = useRef(new Map<number, HTMLElement>())

  useEffect(() => {
    if (highlighted.length > 0) {
      rows.current.get(highlighted[0])?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
    }
  }, [highlighted])

  return (
    <Panel title="Input batch" meta={input ? `${input.length} elements` : undefined}>
      {!input ? (
        <Hint>The batch you send appears here, one row per element, exactly as received.</Hint>
      ) : input.length === 0 ? (
        <Hint>The batch is empty.</Hint>
      ) : (
        <ol className="divide-y divide-slate-100">
          {input.map((element, index) => {
            const isObject = typeof element === 'object' && element !== null && !Array.isArray(element)
            const fields = isObject ? (element as Record<string, unknown>) : null
            const rowProblems = problems.filter((problem) => problem.inputIndex === index)
            return (
              <li
                key={index}
                ref={(node) => {
                  if (node) rows.current.set(index, node)
                  else rows.current.delete(index)
                }}
                className={cx(
                  'grid grid-cols-[34px_minmax(0,1fr)_auto] items-start gap-2 px-4 py-2 transition-colors duration-300',
                  highlighted.includes(index) ? 'bg-indigo-50 shadow-[inset_3px_0_0_#6366f1]' : 'bg-white',
                )}
              >
                <span className="pt-px font-mono text-[11px] text-slate-400">#{index}</span>
                {fields ? (
                  <span className="min-w-0 font-mono text-[12px] leading-5 whitespace-pre text-slate-700">
                    <span className="font-semibold text-slate-900">{displayRaw(fields.requestId)}</span>
                    <span className="text-slate-400"> · </span>{displayRaw(fields.equipmentId)}
                    <span className="text-slate-400"> · </span>{displayRaw(fields.priority)}
                    <span className="block overflow-hidden text-ellipsis whitespace-pre text-slate-500">{displayRaw(fields.description)}</span>
                  </span>
                ) : (
                  <span className="font-mono text-[12px] text-slate-500 italic">{displayRaw(element)} (not a request object)</span>
                )}
                <span className="flex gap-1 pt-1.5">
                  {rowProblems.map((problem, problemIndex) => (
                    <span key={problemIndex} title={problem.message} className={cx('size-2 rounded-full', PROBLEM_STYLES[problem.code].dot)} />
                  ))}
                </span>
              </li>
            )
          })}
        </ol>
      )}
    </Panel>
  )
}
