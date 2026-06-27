import { describe, it, expect } from 'vitest'
import { confidencePct, isAgenticReplan, triggerBadge, agentStatusClass } from './format'

describe('confidencePct', () => {
  it('renders a 0..1 confidence as a percentage', () => {
    expect(confidencePct(0.85)).toBe('85%')
    expect(confidencePct(1)).toBe('100%')
    expect(confidencePct(0)).toBe('0%')
  })
  it('handles missing confidence', () => {
    expect(confidencePct(undefined)).toBe('0%')
  })
})

describe('triggerBadge', () => {
  it('flags AGENT_OFFLINE as an agentic re-plan', () => {
    const b = triggerBadge('AGENT_OFFLINE')
    expect(b.label).toBe('AGENTIC RE-PLAN')
    expect(b.className).toContain('badge-replan')
  })
  it('flags INITIAL as manual', () => {
    const b = triggerBadge('INITIAL')
    expect(b.label).toBe('MANUAL')
    expect(b.className).toContain('badge-manual')
  })
})

describe('isAgenticReplan', () => {
  it('is true only for AGENT_OFFLINE', () => {
    expect(isAgenticReplan({ triggerReason: 'AGENT_OFFLINE' })).toBe(true)
    expect(isAgenticReplan({ triggerReason: 'INITIAL' })).toBe(false)
  })
})

describe('agentStatusClass', () => {
  it('maps status to a pill class', () => {
    expect(agentStatusClass('AVAILABLE')).toBe('pill pill-available')
    expect(agentStatusClass('OFFLINE')).toBe('pill pill-offline')
  })
})
