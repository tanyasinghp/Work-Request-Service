import { Hint, Panel } from '../components/ui'
import { escalatedTag, type Escalation } from '../logic/escalation'
import { RequestCard } from '../nodes/RequestCard'
import type { AcceptedRequest } from '../types'

export function OutputQueue({ accepted, revealed, escalations, selectedRequestId, onSelectRequest }: {
  accepted: AcceptedRequest[] | null
  revealed: boolean
  escalations: Map<string, Escalation>
  selectedRequestId: string | null
  onSelectRequest: (requestId: string) => void
}) {
  return (
    <Panel title="Output queue" meta={accepted && revealed ? `${accepted.length} accepted · highest priority first` : undefined}>
      {!accepted ? (
        <Hint>Accepted requests line up here in the order they should be worked on.</Hint>
      ) : !revealed ? (
        <Hint>Step through to the queue (or press Run all) to see the prioritized requests.</Hint>
      ) : accepted.length === 0 ? (
        <Hint>No requests were accepted. Every element had a problem; see the Problems lane.</Hint>
      ) : (
        <div className="flex flex-col gap-2 p-3">
          {accepted.map((request, position) => (
            <RequestCard
              key={request.requestId}
              request={request}
              position={position}
              escalatedTag={escalatedTag(escalations.get(request.requestId))}
              selected={selectedRequestId === request.requestId}
              onSelect={() => onSelectRequest(request.requestId)}
            />
          ))}
        </div>
      )}
    </Panel>
  )
}
