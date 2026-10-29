import { useState } from 'react'
import { cx } from './components/styles'
import { ProcessingTab } from './flows/ProcessingTab'

type Tab = 'processing' | 'agents'

const TABS: { id: Tab; label: string }[] = [
  { id: 'processing', label: 'Processing' },
  { id: 'agents', label: 'Agent runs' },
]

export default function App() {
  const [tab, setTab] = useState<Tab>('processing')

  return (
    <div className="flex h-full flex-col">
      <header className="flex h-14 shrink-0 items-center justify-between border-b border-slate-200 bg-white px-6">
        <div className="flex items-baseline gap-3">
          <h1 className="text-[15px] font-semibold text-slate-900">Work Request Service</h1>
          <span className="text-[13px] text-slate-500">validate · deduplicate · prioritize</span>
        </div>
        <nav role="tablist" aria-label="Views" className="flex gap-1 rounded-lg bg-slate-100 p-1">
          {TABS.map(({ id, label }) => (
            <button
              key={id}
              type="button"
              role="tab"
              aria-selected={tab === id}
              onClick={() => setTab(id)}
              className={cx(
                'rounded-md px-3.5 py-1.5 text-[13px] font-medium transition',
                tab === id ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-600 hover:text-slate-900',
              )}
            >
              {label}
            </button>
          ))}
        </nav>
      </header>
      {/* Both tabs stay mounted so switching away doesn't lose a half-stepped batch. */}
      <main className={cx('min-h-0 flex-1', tab !== 'processing' && 'hidden')}>
        <ProcessingTab />
      </main>
      <main className={cx('min-h-0 flex-1', tab !== 'agents' && 'hidden')}>
        <p className="p-6 text-slate-500">Agent runs</p>
      </main>
    </div>
  )
}
