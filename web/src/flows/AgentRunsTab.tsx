import { useState, type ReactNode } from 'react'
import agentRunsJson from '../data/agentRuns.json'
import { Hint, Panel } from '../components/ui'
import { cx } from '../components/styles'
import {
  AGENT_STAGES, isPlaceholderData, parseAgentRuns, passedFirstTry, runOutcome, type AgentRun, type AgentStageId,
} from '../logic/agentRuns'
import { AgentFlow } from './AgentFlow'

// Read once: the file is bundled at build time, and the pipeline replaces it between builds.
const loaded: { runs: AgentRun[] } | { error: string } = (() => {
  try {
    return { runs: parseAgentRuns(agentRunsJson) }
  } catch (error) {
    return { error: error instanceof Error ? error.message : 'agentRuns.json could not be read' }
  }
})()

export function AgentRunsTab() {
  const runs = 'runs' in loaded ? loaded.runs : []
  const [runId, setRunId] = useState(runs[0]?.id ?? null)
  const [stage, setStage] = useState<AgentStageId>('gate')
  const run = runs.find((candidate) => candidate.id === runId) ?? runs[0]

  if ('error' in loaded) {
    return <div className="p-6"><Banner tone="error">{loaded.error}</Banner></div>
  }
  if (!run) {
    return <div className="p-6"><Hint>No agent runs yet. Run the agent pipeline to export some to src/data/agentRuns.json.</Hint></div>
  }

  return (
    <div className="grid h-full grid-rows-[auto_auto_minmax(0,1fr)] gap-3 px-6 py-4">
      <div className="flex flex-wrap items-center gap-2">
        {runs.map((candidate) => (
          <button
            key={candidate.id}
            type="button"
            onClick={() => setRunId(candidate.id)}
            className={cx(
              'flex h-9 items-center gap-2 rounded-lg px-3 text-[13px] font-medium ring-1 transition',
              candidate.id === run.id ? 'bg-slate-900 text-white ring-slate-900' : 'bg-white text-slate-700 ring-slate-300 hover:ring-slate-400',
            )}
          >
            <span className={cx('size-2 rounded-full', runOutcome(candidate) === 'passed' ? 'bg-emerald-500' : 'bg-rose-500')} />
            {candidate.title}
            <span className={cx('text-[11px] font-normal', candidate.id === run.id ? 'text-slate-300' : 'text-slate-500')}>
              {outcomeLabel(candidate)}
            </span>
          </button>
        ))}
        {isPlaceholderData(runs) && (
          <Banner tone="warning">Placeholder data. The agent pipeline will replace agentRuns.json with real runs.</Banner>
        )}
      </div>

      <section className="h-[240px] rounded-xl border border-slate-200 bg-white">
        <AgentFlow run={run} selectedStage={stage} onSelectStage={setStage} />
      </section>

      <div className="grid min-h-0 grid-cols-[minmax(0,2fr)_minmax(0,1fr)] gap-3">
        <Panel title={AGENT_STAGES.find((candidate) => candidate.id === stage)?.label ?? ''} meta="click a stage to switch">
          <StageDetail run={run} stage={stage} />
        </Panel>
        <Panel title="Run summary">
          <dl className="grid grid-cols-[130px_minmax(0,1fr)] gap-x-3 gap-y-2 p-4 text-[13px]">
            <Term label="Outcome">{runOutcome(run) === 'passed' ? 'Gate passed' : `Stopped at ${run.stoppedAt ?? 'the gate'}`}</Term>
            <Term label="First-try pass">{passedFirstTry(run) ? 'Yes' : 'No'}</Term>
            <Term label="Attempts">{run.implement.attempts.length}</Term>
            <Term label="Diff size">
              {run.implement.attempts.map((attempt) => `${attempt.diffLines} lines`).join(' → ') || '—'}
            </Term>
            <Term label="Manual fixes">
              {run.summary.reviewed === false ? 'not reviewed yet' : run.summary.manualFixes.length}
            </Term>
            {run.branch && <Term label="Branch"><code className="font-mono text-[12px]">{run.branch}</code></Term>}
            {run.llm?.backend && <Term label="LLM">{run.llm.backend} ({(run.llm.models ?? []).join(', ') || 'model unknown'})</Term>}
            {run.durationSeconds != null && (
              <Term label="Time">
                {Math.round(run.durationSeconds / 60)} min{run.costUsd != null ? ` · $${run.costUsd.toFixed(2)}` : ''}
              </Term>
            )}
          </dl>
        </Panel>
      </div>
    </div>
  )
}

function StageDetail({ run, stage }: { run: AgentRun; stage: AgentStageId }) {
  switch (stage) {
    case 'story':
      return <Body><p>{run.story}</p></Body>
    case 'enrich':
      return (
        <Body>
          <ul className="space-y-2">
            {run.enrich.criteria.map((criterion, index) => (
              <li key={index} className="rounded-md bg-slate-50 px-3 py-2 ring-1 ring-slate-100">
                <b className="font-semibold">Given</b> {criterion.given}, <b className="font-semibold">when</b> {criterion.when}, <b className="font-semibold">then</b> {criterion.then}.
              </li>
            ))}
          </ul>
          <List title="Affected classes" items={run.enrich.affectedClasses} mono />
          <List title="Out of scope" items={run.enrich.outOfScope} />
        </Body>
      )
    case 'implement':
      return (
        <Body>
          {run.implement.attempts.map((attempt) => (
            <p key={attempt.attempt}><b className="font-semibold">Attempt {attempt.attempt}</b> · {attempt.diffLines} diff lines — {attempt.summary}</p>
          ))}
        </Body>
      )
    case 'writeTests':
      return <Body><p>Written from the acceptance criteria only, without seeing the diff.</p><List title="Tests" items={run.writeTests.testNames} mono /></Body>
    case 'gate':
      return (
        <Body>
          {run.gate.attempts.map((attempt) => (
            <div key={attempt.attempt} className="space-y-1">
              <p className={attempt.passed ? 'text-emerald-700' : 'text-rose-700'}>
                <b className="font-semibold">Attempt {attempt.attempt}:</b>{' '}
                {attempt.passed ? 'all tests green'
                  : attempt.rejectedBeforeGate ? `rejected before the gate — ${attempt.rejectedBeforeGate}`
                    : `failed — ${attempt.failingTest ?? 'unknown test'}`}
              </p>
              {attempt.output && <pre className="overflow-x-auto rounded-md bg-slate-900 px-3 py-2 font-mono text-[12px] text-slate-100">{attempt.output}</pre>}
            </div>
          ))}
          {run.gate.attempts.length === 0 && <p>This run stopped before the gate.</p>}
        </Body>
      )
    case 'summary':
      return (
        <Body>
          {/* The PR description is Markdown; shown as written rather than rendered. */}
          <p className="whitespace-pre-wrap">{run.summary.text}</p>
          {run.summary.reviewed === false
            ? <p className="text-slate-500">Not reviewed yet.</p>
            : <List title="Manual fixes (from review)" items={run.summary.manualFixes.length > 0 ? run.summary.manualFixes : ['None needed.']} />}
          <List title="Review notes" items={run.summary.reviewNotes ?? []} />
        </Body>
      )
  }
}

/** Short label that tells apart several runs of the same story. */
function outcomeLabel(run: AgentRun): string {
  if (runOutcome(run) === 'passed') return 'passed'
  return run.stoppedAt ? `stopped at ${run.stoppedAt}` : 'stopped'
}

function Body({ children }: { children: ReactNode }) {
  return <div className="space-y-3 p-4 text-[13px] leading-relaxed text-slate-700">{children}</div>
}

function List({ title, items, mono }: { title: string; items: string[]; mono?: boolean }) {
  if (items.length === 0) return null
  return (
    <div>
      <h4 className="mb-1 text-[11px] font-semibold tracking-wide text-slate-500 uppercase">{title}</h4>
      <ul className={cx('list-inside list-disc', mono && 'font-mono text-[12px]')}>
        {items.map((item) => <li key={item}>{item}</li>)}
      </ul>
    </div>
  )
}

function Term({ label, children }: { label: string; children: ReactNode }) {
  return (
    <>
      <dt className="text-slate-500">{label}</dt>
      <dd className="text-slate-800">{children}</dd>
    </>
  )
}

function Banner({ tone, children }: { tone: 'warning' | 'error'; children: ReactNode }) {
  return (
    <span role={tone === 'error' ? 'alert' : 'note'} className={cx(
      'rounded-lg px-3 py-1.5 text-[12px] font-medium ring-1',
      tone === 'warning' ? 'bg-amber-50 text-amber-800 ring-amber-200' : 'bg-rose-50 text-rose-800 ring-rose-200',
    )}>
      {children}
    </span>
  )
}
