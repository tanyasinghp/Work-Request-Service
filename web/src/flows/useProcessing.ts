import { useCallback, useRef, useState } from 'react'
import { ApiError, fetchSample, processBatch } from '../api/client'
import { sameOutput, shuffled } from '../logic/shuffle'
import { FIRST_STEP, LAST_STEP, nextStep, type FlowNodeId } from '../logic/stepping'
import type { BatchResult, RawBatch } from '../types'

export type Selection =
  | { kind: 'stage'; nodeId: FlowNodeId }
  | { kind: 'request'; requestId: string }
  | { kind: 'problem'; index: number }
  | null

export type Status = 'idle' | 'loading' | 'ready' | 'error'

const STEP_DELAY_MS = 650

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

/**
 * Processing tab state. The backend processes each batch in one call; the UI then reveals
 * the stages one step at a time from the response, so nothing is re-computed here.
 */
export function useProcessing() {
  const [status, setStatus] = useState<Status>('idle')
  const [error, setError] = useState<string | null>(null)
  const [input, setInput] = useState<RawBatch | null>(null)
  const [result, setResult] = useState<BatchResult | null>(null)
  const [step, setStep] = useState(FIRST_STEP)
  const [running, setRunning] = useState(false)
  const [comparison, setComparison] = useState<'identical' | 'different' | null>(null)
  const [selection, setSelection] = useState<Selection>(null)
  // Bumped by every new action so a stale "Run all" or a slow response can't overwrite newer state.
  const runToken = useRef(0)

  const process = useCallback(async (batch: unknown, options: { compareWith?: BatchResult } = {}) => {
    const token = ++runToken.current
    setRunning(false)
    setStatus('loading')
    setError(null)
    try {
      const processed = await processBatch(batch)
      if (token !== runToken.current) return
      setInput(Array.isArray(batch) ? batch : [batch])
      setResult(processed)
      setSelection(null)
      if (options.compareWith) {
        setComparison(sameOutput(options.compareWith, processed) ? 'identical' : 'different')
        setStep(LAST_STEP)
      } else {
        setComparison(null)
        setStep(FIRST_STEP)
      }
      setStatus('ready')
    } catch (failure) {
      if (token !== runToken.current) return
      setError(failure instanceof ApiError ? failure.message : 'Something went wrong while processing the batch.')
      setStatus('error')
    }
  }, [])

  const loadSample = useCallback(async () => {
    const token = ++runToken.current
    setStatus('loading')
    setError(null)
    try {
      const sample = await fetchSample()
      if (token !== runToken.current) return
      await process(sample)
    } catch (failure) {
      if (token !== runToken.current) return
      setError(failure instanceof ApiError ? failure.message : 'Could not load the sample batch.')
      setStatus('error')
    }
  }, [process])

  const stepOnce = useCallback(() => {
    runToken.current++
    setRunning(false)
    setStep((current) => nextStep(current))
  }, [])

  const runAll = useCallback(async () => {
    if (!result) return
    const token = ++runToken.current
    setRunning(true)
    let current = step >= LAST_STEP ? FIRST_STEP : step
    setStep(current)
    while (current < LAST_STEP) {
      await delay(STEP_DELAY_MS)
      if (token !== runToken.current) return
      current = nextStep(current)
      setStep(current)
    }
    setRunning(false)
  }, [result, step])

  const shuffleAndRerun = useCallback(() => {
    if (!input || !result) return
    return process(shuffled(input), { compareWith: result })
  }, [input, result, process])

  return {
    status, error, input, result, step, running, comparison, selection,
    setSelection, loadSample, process, stepOnce, runAll, shuffleAndRerun,
  }
}
