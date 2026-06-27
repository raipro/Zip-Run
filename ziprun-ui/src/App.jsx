import { useCallback, useEffect, useState } from 'react'
import { api } from './api'
import SuggestionCard from './components/SuggestionCard'
import AgentList from './components/AgentList'

const POLL_MS = 3000

export default function App() {
  const [suggestions, setSuggestions] = useState([])
  const [agents, setAgents] = useState([])
  const [strategy, setStrategy] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [lastUpdated, setLastUpdated] = useState(null)
  const [polling, setPolling] = useState(true)
  const [busyId, setBusyId] = useState(null)

  const load = useCallback(async ({ silent } = {}) => {
    if (!silent) setLoading(true)
    try {
      const [s, a, cfg] = await Promise.all([
        api.getPendingSuggestions(),
        api.getAgents(),
        api.getActiveStrategy().catch(() => null),
      ])
      setSuggestions(s)
      setAgents(a)
      setStrategy(cfg)
      setError(null)
      setLastUpdated(new Date())
    } catch (e) {
      setError(e.message || 'Failed to reach the ZipRun backend')
    } finally {
      if (!silent) setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  useEffect(() => {
    if (!polling) return undefined
    const timer = setInterval(() => load({ silent: true }), POLL_MS)
    return () => clearInterval(timer)
  }, [polling, load])

  const decide = async (id, status) => {
    setBusyId(id)
    try {
      await api.decideSuggestion(id, status)
      await load({ silent: true })
    } catch (e) {
      setError(e.message)
    } finally {
      setBusyId(null)
    }
  }

  const setOffline = async (agentId) => {
    setBusyId(agentId)
    try {
      await api.setAgentStatus(agentId, 'OFFLINE')
      await load({ silent: true })
      // Re-planning is async on the backend; a short follow-up poll lets the
      // first AGENT_OFFLINE suggestions land without waiting for the next tick.
      setTimeout(() => load({ silent: true }), 700)
    } catch (e) {
      setError(e.message)
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <h1>ZipRun · Ops</h1>
          <span className="subtitle">Reassignment engine</span>
        </div>
        <div className="controls">
          {strategy && (
            <span className="chip" title="Active routing strategy">
              strategy: <strong>{strategy.active}</strong>
            </span>
          )}
          {lastUpdated && <span className="muted updated">updated {lastUpdated.toLocaleTimeString()}</span>}
          <label className="poll-toggle">
            <input type="checkbox" checked={polling} onChange={(e) => setPolling(e.target.checked)} />
            auto-refresh
          </label>
          <button className="btn btn-small" onClick={() => load()} disabled={loading}>
            Refresh
          </button>
        </div>
      </header>

      {error && (
        <div className="error-banner" role="alert">
          ⚠ {error}
        </div>
      )}

      <main className="grid">
        <section className="panel">
          <div className="panel-head">
            <h2>Reassignment queue</h2>
            <span className="count">{suggestions.length}</span>
          </div>

          {loading && suggestions.length === 0 ? (
            <p className="empty">Loading…</p>
          ) : suggestions.length === 0 ? (
            <p className="empty">
              No pending reassignments. Take an agent offline to see the agentic loop queue suggestions.
            </p>
          ) : (
            <div className="cards">
              {suggestions.map((s) => (
                <SuggestionCard
                  key={s.id}
                  suggestion={s}
                  busy={busyId === s.id}
                  onAccept={(id) => decide(id, 'ACCEPTED')}
                  onReject={(id) => decide(id, 'REJECTED')}
                />
              ))}
            </div>
          )}
        </section>

        <section className="panel">
          <div className="panel-head">
            <h2>Agents</h2>
            <span className="count">{agents.length}</span>
          </div>
          {loading && agents.length === 0 ? (
            <p className="empty">Loading…</p>
          ) : (
            <AgentList agents={agents} onSetOffline={setOffline} busyId={busyId} />
          )}
        </section>
      </main>
    </div>
  )
}
