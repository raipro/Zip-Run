# ZipRun UI — Ops Interface

Minimal ops console for the ZipRun reassignment engine (T-5). Built with **React + Vite**.

The job of this UI is to make the agentic loop visible: when an agent goes offline, the
backend queues reassignment suggestions, and they appear here — each flagged with an
**AGENTIC RE-PLAN** badge — for ops to Accept or Reject.

## Run

The backend must be running first (default `http://localhost:8080`):

```bash
npm install
npm run dev          # http://localhost:5173
```

Backend base URL is overridable: `VITE_API_BASE_URL=http://host:port npm run dev`.

## What it shows (floor)

- **Reassignment queue** — pending suggestions, each with the recommended agent, confidence,
  the AI reasoning **verbatim**, and Accept/Reject (→ `PATCH /suggestions/{id}`).
- **Re-plan badge** — `AGENT_OFFLINE` suggestions are visually distinct from manual (`INITIAL`)
  ones; this is what makes the agentic loop visible.
- **Agent roster** — each agent's load and status (`AVAILABLE` / `BUSY` / `OFFLINE`) as a
  colour-coded pill, plus a **Set offline** action that triggers the agentic loop from the UI.
- **Auto-refresh** (3s polling, toggleable) + manual **Refresh**, with loading and error states.

## Demo path

1. `npm run dev`, open http://localhost:5173.
2. Hit **Set offline** on a BUSY agent (e.g. Priya Sharma / AGT-001).
3. Within a poll cycle, that agent's orders appear in the queue badged **AGENTIC RE-PLAN**.
4. **Accept** one — the order is reassigned and the card clears.

## Test / build

```bash
npm test             # Vitest (format helpers + SuggestionCard)
npm run build
```
