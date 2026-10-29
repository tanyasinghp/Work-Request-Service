import { motion } from 'framer-motion'
import { useEffect, useMemo, useState } from 'react'
import { Button } from '../components/ui'
import { cx } from '../components/styles'

type Parsed = { ok: true; value: unknown; summary: string } | { ok: false; summary: string }

function parse(text: string): Parsed {
  try {
    const value: unknown = JSON.parse(text)
    return Array.isArray(value)
      ? { ok: true, value, summary: `Valid JSON array of ${value.length} element${value.length === 1 ? '' : 's'}` }
      // Still sendable: seeing the API's 400 for a non-array body is part of the demo.
      : { ok: true, value, summary: 'Valid JSON, but not an array; the API will reject it with a 400' }
  } catch (error) {
    return { ok: false, summary: error instanceof Error ? error.message : 'Not valid JSON' }
  }
}

/** Slide-over JSON editor. Mounted only while open, so it starts from the current batch each time. */
export function EditBatchPanel({ initialText, onClose, onSubmit }: {
  initialText: string
  onClose: () => void
  onSubmit: (batch: unknown) => void
}) {
  const [text, setText] = useState(initialText)
  const parsed = useMemo(() => parse(text), [text])

  useEffect(() => {
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', closeOnEscape)
    return () => window.removeEventListener('keydown', closeOnEscape)
  }, [onClose])

  return (
    <>
      <motion.div
        className="fixed inset-0 z-40 bg-slate-900/20"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        onClick={onClose}
      />
      <motion.aside
        role="dialog"
        aria-modal="true"
        aria-label="Edit batch"
        className="fixed inset-y-0 right-0 z-50 flex w-[600px] flex-col border-l border-slate-200 bg-white shadow-xl"
        initial={{ x: 620 }}
        animate={{ x: 0 }}
        exit={{ x: 620 }}
        transition={{ type: 'spring', stiffness: 380, damping: 38 }}
      >
        <header className="flex h-14 items-center justify-between border-b border-slate-100 px-5">
          <div>
            <h2 className="text-[14px] font-semibold text-slate-900">Edit batch</h2>
            <p className="text-[12px] text-slate-500">A JSON array of work requests. Break things on purpose.</p>
          </div>
          <button type="button" onClick={onClose} className="rounded-md px-2 py-1 text-slate-500 hover:bg-slate-100" aria-label="Close">✕</button>
        </header>
        <textarea
          value={text}
          onChange={(event) => setText(event.target.value)}
          spellCheck={false}
          autoFocus
          className="min-h-0 flex-1 resize-none bg-slate-50 p-5 font-mono text-[12.5px] leading-5 text-slate-800 outline-none"
        />
        <footer className="flex items-center justify-between gap-3 border-t border-slate-100 px-5 py-3">
          <span className={cx('truncate text-[12px]', parsed.ok ? 'text-slate-500' : 'text-rose-600')}>{parsed.summary}</span>
          <div className="flex shrink-0 gap-2">
            <Button onClick={onClose}>Cancel</Button>
            <Button variant="primary" disabled={!parsed.ok} onClick={() => parsed.ok && onSubmit(parsed.value)}>
              Process batch
            </Button>
          </div>
        </footer>
      </motion.aside>
    </>
  )
}
