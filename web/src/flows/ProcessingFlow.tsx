import { Background, BackgroundVariant, ReactFlow, type Edge, type NodeTypes } from '@xyflow/react'
import { useMemo } from 'react'
import { FLOW_NODES, nodeCounts, nodeStatus, type FlowNodeId } from '../logic/stepping'
import { StageNode, type StageNodeType } from '../nodes/StageNode'
import type { BatchResult } from '../types'

const nodeTypes = { stage: StageNode } satisfies NodeTypes

const NODE_SPACING = 250

export function ProcessingFlow({ result, step, selectedNodeId, onSelectNode }: {
  result: BatchResult | null
  step: number
  selectedNodeId: FlowNodeId | null
  onSelectNode: (nodeId: FlowNodeId) => void
}) {
  const nodes = useMemo<StageNodeType[]>(() => FLOW_NODES.map((definition, index) => ({
    id: definition.id,
    type: 'stage',
    position: { x: index * NODE_SPACING, y: 0 },
    data: {
      label: definition.label,
      counts: result ? nodeCounts(index, result, step) : null,
      status: result ? nodeStatus(index, step) : 'waiting',
      selected: selectedNodeId === definition.id,
      endpoint: !definition.traceStage,
    },
  })), [result, step, selectedNodeId])

  const edges = useMemo<Edge[]>(() => FLOW_NODES.slice(1).map((definition, index) => {
    const reached = result !== null && index + 1 <= step
    return {
      id: `${FLOW_NODES[index].id}-${definition.id}`,
      source: FLOW_NODES[index].id,
      target: definition.id,
      type: 'smoothstep',
      // The edge into the node just reached carries the motion.
      animated: reached && index + 1 === step,
      style: reached
        ? { stroke: '#64748b', strokeWidth: 1.75 }
        : { stroke: '#cbd5e1', strokeWidth: 1.5, strokeDasharray: '5 5' },
    }
  }), [result, step])

  return (
    <ReactFlow
      nodes={nodes}
      edges={edges}
      nodeTypes={nodeTypes}
      onNodeClick={(_, node) => onSelectNode(node.id as FlowNodeId)}
      fitView
      fitViewOptions={{ padding: 0.12 }}
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
