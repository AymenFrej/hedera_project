# Development guide

Run PostgreSQL with `docker compose up -d postgres`, then run the backend and frontend independently. The UI mock data is intentionally local so a feature can be developed while its API is still being designed. Replace a module's mock JSON with an API client only when the contract is stable.

Keep agent planning and execution explicit. Start with `AgentRequest`, produce an `AgentPlan`, apply policy/approval checks, then execute through a module gateway. This starter does not perform any of those real operations yet.
