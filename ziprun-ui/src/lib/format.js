// Pure presentation helpers — kept separate so they're trivially unit-testable.

export function confidencePct(confidence) {
  return `${Math.round((confidence ?? 0) * 100)}%`
}

export function isAgenticReplan(suggestion) {
  return suggestion?.triggerReason === 'AGENT_OFFLINE'
}

/**
 * The re-plan badge — the single most important UI signal (it's what makes the agentic
 * loop visible). AGENT_OFFLINE suggestions are flagged distinctly from manually-requested ones.
 */
export function triggerBadge(triggerReason) {
  return triggerReason === 'AGENT_OFFLINE'
    ? { label: 'AGENTIC RE-PLAN', className: 'badge badge-replan' }
    : { label: 'MANUAL', className: 'badge badge-manual' }
}

export function agentStatusClass(status) {
  return `pill pill-${(status || '').toLowerCase()}`
}
