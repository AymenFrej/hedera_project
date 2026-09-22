# Contributing

This is a five-person monorepo. Keep changes focused on the vertical you own and add tests alongside the feature.

## Branches

Use one of these patterns:

- `feature/accounts-*`
- `feature/payments-*`
- `feature/tokens-*`
- `feature/audit-*`
- `feature/policies-*`

Examples: `feature/accounts-create`, `feature/payments-transfer`, `feature/tokens-mint`, `feature/audit-hcs`, `feature/policies-limits`.

## Flow

```text
main
  ↑
Pull Request
  ↑
feature branch
```

Before opening a PR, run `npm run build` in `frontend` and `mvn test` in `backend`. Do not commit `.env`, private keys, build output, or generated dependencies. Keep shared contract changes backward compatible where possible.
