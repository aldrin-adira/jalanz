# Jalanz

A privacy-first navigation app for Malaysia, on Android phones and **Android Auto**:
local place search, turn-by-turn guidance in Bahasa Malaysia and English, live traffic,
end-to-end encrypted live location sharing while driving, and **accurate toll estimates**
built from published toll fare tables.

> **Status:** Phase 0 (foundations). Not ready for use. See `spec/10-build-phases.md`.

## Repository layout

| Folder | Contents |
|---|---|
| `spec/` | The full specification pack (start with `spec/00-README.md`) |
| `android/` | Android phone app + Android Auto (Kotlin, Jetpack Compose) |
| `backend/` | Kotlin + Ktor API and live-share relay |
| `web-share/` | Share page for people following a drive (TypeScript) |
| `pipelines/` | Python data jobs (toll crawler, place index) |
| `infra/` | Docker Compose and server configuration |
| `fixtures/` | Shared test data (toll fixtures, search test list, GPX drives) |

## Contributing

All work follows `spec/09-git-workflow.md`: typed branches cut from `develop`, detailed commit
messages, and `--no-ff` merges. After cloning, enable the commit template and message check:

```sh
git config commit.template .gitmessage
git config core.hooksPath .githooks
```

AI coding agents: read `AGENTS.md` first.

## Licence

Apache License 2.0 — see `LICENSE` and `NOTICE` (map data © OpenStreetMap contributors).
