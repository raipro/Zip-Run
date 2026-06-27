import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import SuggestionCard from './SuggestionCard'

const baseSuggestion = {
  id: 7,
  orderId: 'ORD-003',
  orderDescription: 'Pharma — Whitefield to Marathahalli',
  recommendedAgentId: 'AGT-002',
  recommendedAgentName: 'Rahul Verma',
  confidence: 0.9,
  reasoning: 'Rahul Verma has the fewest active orders, so the most spare capacity.',
  status: 'PENDING',
  triggerReason: 'AGENT_OFFLINE',
  strategyUsed: 'ai',
}

describe('SuggestionCard', () => {
  it('shows the order, recommended agent, confidence and verbatim reasoning', () => {
    render(<SuggestionCard suggestion={baseSuggestion} onAccept={() => {}} onReject={() => {}} />)
    expect(screen.getByText('ORD-003')).toBeInTheDocument()
    // Recommended agent id appears only in the recommendation line.
    expect(screen.getByText('(AGT-002)')).toBeInTheDocument()
    expect(screen.getByText('confidence 90%')).toBeInTheDocument()
    // Reasoning must appear exactly as the backend returned it.
    expect(screen.getByText(baseSuggestion.reasoning)).toBeInTheDocument()
  })

  it('renders the AGENTIC RE-PLAN badge for AGENT_OFFLINE', () => {
    render(<SuggestionCard suggestion={baseSuggestion} onAccept={() => {}} onReject={() => {}} />)
    expect(screen.getByText('AGENTIC RE-PLAN')).toBeInTheDocument()
  })

  it('renders the MANUAL badge for INITIAL', () => {
    render(
      <SuggestionCard
        suggestion={{ ...baseSuggestion, triggerReason: 'INITIAL' }}
        onAccept={() => {}}
        onReject={() => {}}
      />,
    )
    expect(screen.getByText('MANUAL')).toBeInTheDocument()
  })

  it('calls onAccept / onReject with the suggestion id', async () => {
    const onAccept = vi.fn()
    const onReject = vi.fn()
    render(<SuggestionCard suggestion={baseSuggestion} onAccept={onAccept} onReject={onReject} />)

    await userEvent.click(screen.getByRole('button', { name: 'Accept' }))
    await userEvent.click(screen.getByRole('button', { name: 'Reject' }))

    expect(onAccept).toHaveBeenCalledWith(7)
    expect(onReject).toHaveBeenCalledWith(7)
  })

  it('disables actions while busy', () => {
    render(<SuggestionCard suggestion={baseSuggestion} onAccept={() => {}} onReject={() => {}} busy />)
    expect(screen.getByRole('button', { name: 'Accept' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Reject' })).toBeDisabled()
  })
})
