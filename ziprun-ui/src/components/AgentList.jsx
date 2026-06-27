import { agentStatusClass } from '../lib/format'

/**
 * The agent roster: id, name, current load, and a clearly-differentiated status pill.
 * The "Set offline" action triggers the agentic re-planning loop straight from the UI.
 */
export default function AgentList({ agents, onSetOffline, busyId }) {
  return (
    <div className="agents">
      {agents.map((agent) => (
        <div className="agent-row" key={agent.id}>
          <div className="agent-name">
            <strong>{agent.name}</strong>
            <span className="muted">{agent.id}</span>
          </div>
          <span className="agent-load">{agent.activeOrderCount} active</span>
          <span className={agentStatusClass(agent.status)}>{agent.status}</span>
          <button
            className="btn btn-small"
            disabled={agent.status === 'OFFLINE' || busyId === agent.id}
            onClick={() => onSetOffline(agent.id)}
            title="Take this agent offline to trigger the agentic re-plan"
          >
            Set offline
          </button>
        </div>
      ))}
    </div>
  )
}
