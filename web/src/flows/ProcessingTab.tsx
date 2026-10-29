import { AnimatePresence, motion } from 'framer-motion'
import { useCallback, useMemo, useState } from 'react'
import bundledSample from '../data/sampleBatch.json'
import { Button, Spinner } from '../components/ui'
import { escalationsByKeptId } from '../logic/escalation'
import { inputIndexesFor } from '../logic/journey'
import { canStep, FLOW_NODES, isQueueRevealed, LAST_STEP, revealedProblems } from '../logic/stepping'
import { DetailPanel } from '../panels/DetailPanel'
import { EditBatchPanel } from '../panels/EditBatchPanel'
import { InputPanel } from '../panels/InputPanel'
import { OutputQueue } from '../panels/OutputQueue'
import { ProblemsLane } from '../panels/ProblemsLane'
import { ProcessingFlow } from './ProcessingFlow'
import { useProcessing } from './useProcessing'

export function ProcessingTab() {
  const state = useProcessing()
  const { status, error, input, result, step, running, comparison, selection, setSelection } = state
  const [editing, setEditing] = useState(false)

  const busy = status === 'loading'
  const revealed = useMemo(() => (result ? revealedProblems(result, step) : []), [result, step])
  const escalations = useMemo(() => escalationsByKeptId(result?.problems ?? []), [result])

  const highlighted = useMemo(() => {
    if (!result || !input || !selection) return []
    if (selection.kind === 'problem') return [result.problems[selection.index]?.inputIndex].filter((index) => index !== undefined)
    if (selection.kind === 'request') return inputIndexesFor(selection.requestId, input)
    return []
  }, [result, input, selection])

  const closeEditor = useCallback(() => setEditing(false), [])
  const submitEdited = useCallback((batch: unknown) => {
    setEditing(false)
    void state.process(batch)
  }, [state])

  return (
    <div className="grid h-full grid-rows-[auto_auto_auto_minmax(0,1fr)] gap-3 px-6 py-4">
      <div className="flex items-center justify-between gap-4">
        <div className="flex items-center gap-2">
          <Button variant="primary" onClick={() => void state.loadSample()} disabled={busy}>Load sample</Button>
          <Button onClick={() => setEditing(true)} disabled={busy}>Edit batch</Button>
          <span className="mx-2 h-6 w-px bg-slate-200" />
          <Button onClick={state.stepOnce} disabled={busy || !canStep(step, result !== null)}>Step ▶</Button>
          <Button onClick={() => void state.runAll()} disabled={busy || !result || running}>Run all</Button>
          <Button onClick={() => void state.shuffleAndRerun()} disabled={busy || !result || running}>Shuffle &amp; re-run</Button>
        </div>
        <div className="flex items-center gap-3 text-[13px] text-slate-500">
          {busy && <span className="flex items-center gap-2"><Spinner /> Processing…</span>}
          <AnimatePresence mode="wait">
            {comparison && !busy && (
              <motion.span
                key={comparison}
                initial={{ opacity: 0, scale: 0.92 }}
                animate={{ opacity: 1, scale: 1 }}
                exit={{ opacity: 0 }}
                title="Accepted queue and stage trace compared with the previous run"
                className={comparison === 'identical'
                  ? 'rounded-full bg-emerald-50 px-3 py-1 text-[12px] font-semibold text-emerald-700 ring-1 ring-emerald-200'
                  : 'rounded-full bg-rose-50 px-3 py-1 text-[12px] font-semibold text-rose-700 ring-1 ring-rose-200'}
              >
                {comparison === 'identical' ? '✓ Output identical' : '✕ Output differs'}
              </motion.span>
            )}
          </AnimatePresence>
          {result && !busy && <span>Step {step} of {LAST_STEP} · {FLOW_NODES[step].label}</span>}
        </div>
      </div>

      <section className="relative h-[200px] rounded-xl border border-slate-200 bg-white">
        <ProcessingFlow
          result={result}
          step={step}
          selectedNodeId={selection?.kind === 'stage' ? selection.nodeId : null}
          onSelectNode={(nodeId) => setSelection({ kind: 'stage', nodeId })}
        />
        {status === 'error' && error && (
          <div role="alert" className="absolute inset-x-4 top-3 flex items-center justify-between gap-4 rounded-lg bg-rose-50 px-4 py-2.5 text-[13px] text-rose-800 ring-1 ring-rose-200">
            <span>{error}</span>
            <Button onClick={() => void (input ? state.process(input) : state.loadSample())}>Retry</Button>
          </div>
        )}
        {status === 'idle' && (
          <div className="pointer-events-none absolute inset-x-0 bottom-3 text-center text-[13px] text-slate-500">
            Load the sample batch, or paste your own, to watch it move through validate → deduplicate → prioritize.
          </div>
        )}
      </section>

      <ProblemsLane
        problems={revealed}
        allProblems={result?.problems ?? []}
        hasResult={result !== null}
        selectedProblemIndex={selection?.kind === 'problem' ? selection.index : null}
        onSelectProblem={(index) => setSelection({ kind: 'problem', index })}
      />

      <div className="grid min-h-0 grid-cols-[minmax(0,1fr)_minmax(0,1.3fr)_minmax(0,1fr)] gap-3">
        <InputPanel input={input} problems={revealed} highlighted={highlighted} />
        <OutputQueue
          accepted={result?.accepted ?? null}
          revealed={isQueueRevealed(step)}
          escalations={escalations}
          selectedRequestId={selection?.kind === 'request' ? selection.requestId : null}
          onSelectRequest={(requestId) => setSelection({ kind: 'request', requestId })}
        />
        <DetailPanel selection={selection} result={result} input={input} step={step} />
      </div>

      <AnimatePresence>
        {editing && (
          <EditBatchPanel
            initialText={JSON.stringify(input ?? bundledSample, null, 2)}
            onClose={closeEditor}
            onSubmit={submitEdited}
          />
        )}
      </AnimatePresence>
    </div>
  )
}
