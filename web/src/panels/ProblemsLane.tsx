import { AnimatePresence, motion } from 'framer-motion'
import { cx, PROBLEM_STYLES } from '../components/styles'
import { groupProblemsByCode, summarizeGroups } from '../logic/problems'
import type { Problem } from '../types'

export function ProblemsLane({ problems, allProblems, hasResult, selectedProblemIndex, onSelectProblem }: {
  /** Only the problems found by stages reached so far. */
  problems: Problem[]
  /** The full list, so a selection index means the same problem at every step. */
  allProblems: Problem[]
  hasResult: boolean
  selectedProblemIndex: number | null
  onSelectProblem: (index: number) => void
}) {
  const groups = groupProblemsByCode(problems)

  return (
    <section className="rounded-xl border border-slate-200 bg-white">
      <header className="flex h-10 items-center justify-between border-b border-slate-100 px-4">
        <h2 className="text-[13px] font-semibold text-slate-800">Problems</h2>
        <span className="text-xs text-slate-500">{groups.length > 0 ? summarizeGroups(groups) : ''}</span>
      </header>
      <div className="flex h-[132px] gap-3 overflow-x-auto px-4 py-3">
        {groups.length === 0 && (
          <p className="self-center text-[13px] text-slate-500">
            {hasResult ? 'No problems found by the stages reached so far.' : 'Problems found by each stage collect here, grouped by code.'}
          </p>
        )}
        <AnimatePresence initial={false}>
          {groups.map((group) => (
            <motion.div
              key={group.code}
              layout
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0 }}
              className="flex w-[264px] shrink-0 flex-col rounded-lg bg-slate-50/70 p-2 ring-1 ring-slate-200"
            >
              <div className="mb-1.5 flex items-center gap-2 px-1 text-[12px] font-semibold text-slate-700">
                <span className={cx('size-2 rounded-full', PROBLEM_STYLES[group.code].dot)} />
                {group.label}
                <span className="ml-auto font-mono font-normal text-slate-500">×{group.problems.length}</span>
              </div>
              <div className="flex min-h-0 flex-wrap content-start gap-1 overflow-y-auto">
                {group.problems.map((problem) => {
                  const index = allProblems.indexOf(problem)
                  return (
                    <button
                      key={index}
                      type="button"
                      onClick={() => onSelectProblem(index)}
                      title={problem.message}
                      className={cx(
                        'rounded-md px-1.5 py-0.5 font-mono text-[11px] ring-1 ring-inset transition',
                        PROBLEM_STYLES[group.code].chip,
                        selectedProblemIndex === index && 'outline-2 outline-offset-1 outline-indigo-500',
                      )}
                    >
                      #{problem.inputIndex} {problem.requestId ?? 'no id'}{problem.field ? ` · ${problem.field}` : ''}
                    </button>
                  )
                })}
              </div>
            </motion.div>
          ))}
        </AnimatePresence>
      </div>
    </section>
  )
}
