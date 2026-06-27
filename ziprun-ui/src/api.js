// Thin API client for the ZipRun backend. Base URL is overridable via VITE_API_BASE_URL.
const BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

async function request(path, options) {
  const res = await fetch(`${BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  if (!res.ok) {
    // Surface the backend's structured error message when present.
    let message = `${res.status} ${res.statusText}`
    try {
      const body = await res.json()
      if (body?.message) message = body.message
    } catch {
      /* non-JSON error body — keep the status line */
    }
    throw new Error(message)
  }
  const text = await res.text()
  return text ? JSON.parse(text) : null
}

export const api = {
  getPendingSuggestions: () => request('/suggestions?status=PENDING'),
  getAgents: () => request('/agents'),
  getActiveStrategy: () => request('/config/routing-strategy'),
  decideSuggestion: (id, status) =>
    request(`/suggestions/${id}`, { method: 'PATCH', body: JSON.stringify({ status }) }),
  setAgentStatus: (id, status) =>
    request(`/agents/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status }) }),
}
