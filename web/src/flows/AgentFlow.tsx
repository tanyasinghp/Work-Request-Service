import { Background, BackgroundVariant, MarkerType, ReactFlow, type Edge, type NodeTypes } from '@xyflow/react'
import { useMemo } from 'react'
import { AGENT_STAGES, gateLoops, runOutcome, type AgentRun, type AgentStageId } from '../logic/agentRuns'
import { AgentStageNode, type AgentStageNodeType, type AgentStageTone } from '../nodes/AgentStageNode'

const nodeTypes = { agentStage: AgentStageNode } satisfies NodeTypes

const NODE_SPACING = 228
const FAILURE_RED = '#e11d48'

function plural(count: number, noun: string): string {
  return `${count} ${noun}${count === 1 ? '' : 's'}`
}

/** The stage a stopped run stopped in, from the pipeline's stoppedAt (e.g. "implement-2"). */
function stoppedStage(run: AgentRun): AgentStageId | null {
  const at = run.stoppedAt ?? ''
  if (at === 'enrich') return 'enrich'
  if (at.startsWith('implement')) return 'implement'
  if (at === 'write-tests') return 'writeTests'
  if (at === 'gate' || at === 'branch-check') return 'gate'
  return null
}

function reached(stage: AgentStageId, run: AgentRun): boolean {
  switch (stage) {
    case 'story':
      return true
    case 'enrich':
      return run.enrich.criteria.length > 0 || stoppedStage(run) === 'enrich'
    case 'implement':
      return run.implement.attempts.length > 0
    case 'writeTests':
      return run.writeTests.testNames.length > 0 || stoppedStage(run) === 'writeTests'
    case 'gate':
      return run.gate.attempts.length > 0
    case 'summary':
      return runOutcome(run) === 'passed'
  }
}

function describe(stage: AgentStageId, run: AgentRun): { subtitle: string; tone: AgentStageTone } {
  if (!reached(stage, run)) {
    return { subtitle: stage === 'summary' ? 'not produced' : 'not reached', tone: 'muted' }
  }
  const stopped = stoppedStage(run) === stage
  const attempts = run.gate.attempts
  switch (stage) {
    case 'story':
      return { subtitle: run.date, tone: 'neutral' }
    case 'enrich':
      return stopped
        ? { subtitle: 'criteria rejected · stopped', tone: 'stopped' }
        : { subtitle: `${run.enrich.criteria.length} ${run.enrich.criteria.length === 1 ? 'criterion' : 'criteria'}`,
            tone: 'neutral' }
    case 'implement':
      return { subtitle: plural(run.implement.attempts.length, 'attempt') + (stopped ? ' · stopped' : ''),
               tone: stopped ? 'stopped' : 'neutral' }
    case 'writeTests':
      return stopped
        ? { subtitle: 'tests rejected · stopped', tone: 'stopped' }
        : { subtitle: plural(run.writeTests.testNames.length, 'test'), tone: 'neutral' }
    case 'gate':
      return runOutcome(run) === 'passed'
        ? { subtitle: `passed on attempt ${attempts[attempts.length - 1].attempt}`, tone: 'passed' }
        : { subtitle: `failed ${attempts.length}× · stopped`, tone: 'stopped' }
    case 'summary':
      return { subtitle: 'ready for review', tone: 'neutral' }
  }
}

export function AgentFlow({ run, selectedStage, onSelectStage }: {
  run: AgentRun
  selectedStage: AgentStageId
  onSelectStage: (stage: AgentStageId) => void
}) {
  const nodes = useMemo<AgentStageNodeType[]>(() => AGENT_STAGES.map((stage, index) => ({
    id: stage.id,
    type: 'agentStage',
    position: { x: index * NODE_SPACING, y: 0 },
    data: { label: stage.label, ...describe(stage.id, run), selected: selectedStage === stage.id },
  })), [run, selectedStage])

  const edges = useMemo<Edge[]>(() => {
    const stopped = runOutcome(run) === 'stopped'
    const forward: Edge[] = AGENT_STAGES.slice(1).map((stage, index) => {
      const intoSummaryAfterStop = stage.id === 'summary' && stopped
      return {
        id: `${AGENT_STAGES[index].id}-${stage.id}`,
        source: AGENT_STAGES[index].id,
        target: stage.id,
        sourceHandle: 'out',
        targetHandle: 'in',
        type: 'smoothstep',
        style: intoSummaryAfterStop
          ? { stroke: '#cbd5e1', strokeDasharray: '5 5' }
          : { stroke: '#64748b', strokeWidth: 1.75 },
      }
    })
    const loops: Edge[] = gateLoops(run).map((loop, index) => ({
      id: `retry-${loop.attempt}`,
      source: 'gate',
      target: 'implement',
      sourceHandle: 'loop-out',
      targetHandle: 'loop-in',
      type: 'smoothstep',
      animated: true,
      pathOptions: { offset: 36 + index * 18 },
      label: loop.label,
      labelStyle: { fill: FAILURE_RED, fontSize: 11, fontWeight: 600 },
      labelBgPadding: [6, 3] as [number, number],
      labelBgBorderRadius: 4,
      style: { stroke: FAILURE_RED, strokeWidth: 1.75 },
      markerEnd: { type: MarkerType.ArrowClosed, color: FAILURE_RED, width: 16, height: 16 },
    }))
    return [...forward, ...loops]
  }, [run])

  return (
    <ReactFlow
      nodes={nodes}
      edges={edges}
      nodeTypes={nodeTypes}
      onNodeClick={(_, node) => onSelectStage(node.id as AgentStageId)}
      fitView
      fitViewOptions={{ padding: 0.14 }}
      nodesDraggable={false}
      nodesConnectable={false}
      elementsSelectable={false}
      panOnDrag={false}
      zoomOnScroll={false}
      zoomOnPinch={false}
      zoomOnDoubleClick={false}
      preventScrolling={false}
    >
      <Background variant={BackgroundVariant.Dots} gap={18} size={1} color="#e2e8f0" />
    </ReactFlow>
  )
}
