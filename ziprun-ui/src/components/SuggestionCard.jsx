import { confidencePct, triggerBadge } from '../lib/format'

/**
 * One pending reassignment, shown inline with its AI recommendation: the recommended agent,
 * confidence, the reasoning verbatim, and Accept/Reject controls. The trigger badge marks
 * agentic re-plans (AGENT_OFFLINE) vs manual requests.
 */
export default function SuggestionCard({ suggestion, onAccept, onReject, busy }) {
  const badge = triggerBadge(suggestion.triggerReason)
  return (
    <article className="card">
      <header className="card-head">
        <div className="order">
          <span className="order-id">{suggestion.orderId}</span>
          <span className="order-desc">{suggestion.orderDescription}</span>
        </div>
        <span className={badge.className}>{badge.label}</span>
      </header>

      <div className="reco">
        <span className="reco-label">Recommend</span>
        <span className="reco-agent">
          {suggestion.recommendedAgentName} <span className="muted">({suggestion.recommendedAgentId})</span>
        </span>
        <span className="reco-meta">confidence {confidencePct(suggestion.confidence)}</span>
        {suggestion.strategyUsed && <span className="reco-meta strategy">{suggestion.strategyUsed}</span>}
      </div>

      {/* AI reasoning, displayed verbatim — this is what ops reads. */}
      <p className="reasoning">{suggestion.reasoning}</p>

      <footer className="actions">
        <button className="btn btn-accept" disabled={busy} onClick={() => onAccept(suggestion.id)}>
          Accept
        </button>
        <button className="btn btn-reject" disabled={busy} onClick={() => onReject(suggestion.id)}>
          Reject
        </button>
      </footer>
    </article>
  )
}
