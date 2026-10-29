# Jalanz — Instructions for AI Coding Agents

Jalanz is a privacy-first navigation app for Malaysia (Android phone + Android Auto) with toll estimates, live
traffic and end-to-end encrypted live location sharing. The full specification is in `spec/`. These instructions
apply to every session, every agent and every tool.

## 1. Before doing anything
1. Read `spec/00-README.md`, `spec/09-git-workflow.md` and `spec/10-build-phases.md`.
2. Find the next work package (WP) in `spec/10-build-phases.md` that is not in the progress log (§6), unless the
   product owner names a WP.
3. Read **only** the spec sections listed for that WP (plus any they reference). Do not load the whole pack.
4. During testing, apply the overrides in `spec/11-zero-cost-testing.md` §2.

## 2. Hard rules
- **One WP at a time, one branch per WP**, named exactly as in `spec/10-build-phases.md`, cut from `develop`.
  Never commit directly to `main` or `develop`. Never force-push them.
- **Commit after every completed unit of work** with the full message format in `spec/09-git-workflow.md` §6
  (What / Why / How / Testing / Refs). The `.githooks/commit-msg` hook enforces it. Add any attribution trailer
  your tool requires.
- **Never commit secrets:** signing keystores, `.env` files, the DuckDNS token, TomTom or other API keys, server
  passwords, `google-services.json`. Use environment variables or GitHub Actions secrets.
- **Never commit data that isn't ours to publish:** the crawled LLM toll dataset (crawler code and fixtures are
  fine), testers' drive logs, GPS traces of real people, real share links.
- **No Google Maps Platform APIs** (spec 05 §2). **No paid services or new API keys** without the product owner's
  approval (spec 11 is RM0).
- **Privacy by default:** no analytics/ads SDKs, location only inside the user-started foreground service, no
  `ACCESS_BACKGROUND_LOCATION`, no logging of search/route contents (NFR-13), sharing stays end-to-end encrypted.
- **Conventions:** money in integer sen; time zone `Asia/Kuala_Lumpur`; Bahasa Malaysia and English strings for
  everything user-facing; app name "Jalanz" only.
- **Scope:** do not build anything listed under the current phase's "Not in this phase". Stub behind an interface
  and note it in the commit body instead.
- **When unsure, stop and ask.** If the spec is ambiguous, contradicts itself, or a `[VERIFY]` item blocks you,
  report it rather than guessing. If you change behaviour, update the relevant spec file in the same branch.

## 3. Decisions

| Decision | Status | Value |
|---|---|---|
| App name | Confirmed | Jalanz |
| Repository | Confirmed | https://github.com/aldrin-adira/jalanz (public) |
| Licence | Confirmed | Apache-2.0 |
| Testing budget | Confirmed | RM0 — `spec/11-zero-cost-testing.md` |
| Testing server address | Confirmed | `jalanz.duckdns.org` (upgrade to `jalanz.eu.org` planned before Phase 4) |
| Package ID | Confirmed (2026-10-29) | `io.github.aldrinadira.jalanz` (permanent once installed) |
| Coverage | Confirmed (2026-10-29) | **Peninsular Malaysia only** for now (search, routing, tolls, map packs). Sabah/Sarawak later |
| Provider policy | Confirmed (2026-10-29) | Avoid products founded or based in Israel. **Testing period:** Oracle Cloud accepted. **Before public launch:** all user personal data (accounts, locations, shared trips) only on Malaysian-owned servers in Malaysia. Any new third-party service needs product-owner approval |
| Hosting | Confirmed for testing (2026-10-29) | Oracle Cloud Always Free, Malaysia West (spec 08 §6, spec 11). Keep everything in Docker Compose so it can move to a Malaysian-owned VPS before public launch (spec 10 §13) |
| Sign-in provider | **Open — needed before Phase 6** | Firebase (spec 08 default) is under review; alternatives: email-only sign-in in our own backend, or Keycloak |
| Test phones and head units | **Open — needed before Phase 3** | — |

Do not decide an **Open** item yourself. Ask the product owner when a WP depends on it.

## 4. Setup after cloning
```sh
git config commit.template .gitmessage
git config core.hooksPath .githooks
```
Build and test commands per area are in `spec/09-git-workflow.md` §7.1. Add the real commands here as each
project is created (WP 0.3 Android, WP 0.4 backend).

## 5. Finishing a work package
1. Run the checks in `spec/09-git-workflow.md` §7.1 and the merge checklist in §8.
2. Add the WP to the progress log below **in the same branch**.
3. Merge into `develop` with `--no-ff` and delete the branch.
4. Report: what was built, test results, `[ASSUMPTION]`/`[VERIFY]` items hit, spec changes, next WP.

## 6. Progress log

| WP | Branch | Merged | Notes |
|---|---|---|---|
| 0.1 | `chore/misc-monorepo-skeleton` | 2026-10-29 | Folders, .gitignore, commit template and hook; plus Apache-2.0 licence and spec pack |
