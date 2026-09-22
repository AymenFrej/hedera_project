# Architecture

The repository is a modular monolith with feature ownership aligned to business capabilities. A feature contains its agent, HTTP controller, service, repository, entity/DTO types, tests, and Hedera gateway. Shared agent models live under `shared` and should stay capability-neutral.

The current gateway implementations are mocks. A production adapter should implement the same interface, validate configuration, emit an `AuditEvent`, and add module-level tests without leaking SDK types through the API.
