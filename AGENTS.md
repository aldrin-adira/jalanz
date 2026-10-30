# Jalanz — Instructions for AI Coding Agents

Jalanz is a privacy-first navigation app for Malaysia (Android phone + Android Auto) with toll estimates, live
traffic and end-to-end encrypted live location sharing. The full specification is in `spec/`. These instructions
apply to every session, every agent and every tool.

## 1. Before doing anything
1. Read `spec/00-README.md`, `spec/09-git-workflow.md` and `spec/10-build-phases.md`.
2. Find the next work package (WP) in `spec/10-build-phases.md` that is not in the progress log (§6), unless the
   product owner names a WP. While the personal-use track is active (§3), follow its order in
   `spec/10-build-phases.md` §1.1 and skip the WPs it defers.
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
| Provider policy | Confirmed (2026-10-29) | Avoid products founded or based in Israel. **Testing period:** Oracle Cloud acceptable if used; Tailscale Funnel allowed for ad-hoc testing from the developer's PC. **Before public launch:** all user personal data (accounts, locations, shared trips) only on Malaysian-owned servers in Malaysia. Any new third-party service needs product-owner approval |
| Hosting | Confirmed (2026-10-29) | **Local first:** the Docker Compose stack runs on the developer's PC until WP 1.18. **Decided at WP 1.18** (before release 0.1.0): Oracle Always Free if it cooperates, otherwise a Malaysian-owned VPS. Keep the stack provider-neutral |
| Personal-use track | Confirmed (2026-10-30) | Built for the product owner's own use for now: the app calls free public services (OpenFreeMap, Photon, FOSSGIS Valhalla) instead of our server; no Docker until sharing (Phase 4). Build order and deferred WPs: `spec/10-build-phases.md` §1.1. Must be revisited before anyone else installs the app |
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

**Android** (`android/`, JDK 17+; SDK levels and module conventions in `android/build-logic`):
```sh
./gradlew assembleDebug testDebugUnitTest :core:toll:test lint   # what CI runs, plus the debug APK
./gradlew :app:installDebug                                       # install on a connected phone/emulator
```
**Pipelines** (`pipelines/`, Python 3.12+; details in `pipelines/README.md`):
```sh
python -m venv .venv && .venv/Scripts/python -m pip install -e '.[test]'   # once
.venv/Scripts/python -m pytest                                            # what CI runs
.venv/Scripts/python -m jalanz_pipelines.llm_toll crawl --highway 24      # crawl into pipelines/data/ (never committed)
```
**CI is manual-only for now** (owner decision 2026-10-30, personal-use track): `.github/workflows/ci.yml` runs only
when started by hand in GitHub Actions. Until it is switched back on, the local commands above stand in for the
"CI is green" item of the merge checklist (`spec/09-git-workflow.md` §8): run them before every merge and record
the results in the commit body. Switch CI back on before anyone else contributes or tests the app.

If Gradle fails with `PKIX path building failed` on a Windows PC whose HTTPS is inspected by security
software, run it with `JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStoreType=Windows-ROOT` (local only; never
commit it).

## 5. Finishing a work package
1. Run the checks in `spec/09-git-workflow.md` §7.1 and the merge checklist in §8.
2. Add the WP to the progress log below **in the same branch**.
3. Merge into `develop` with `--no-ff` and delete the branch.
4. Report: what was built, test results, `[ASSUMPTION]`/`[VERIFY]` items hit, spec changes, next WP.

## 6. Progress log

| WP | Branch | Merged | Notes |
|---|---|---|---|
| 0.1 | `chore/misc-monorepo-skeleton` | 2026-10-29 | Folders, .gitignore, commit template and hook; plus Apache-2.0 licence and spec pack |
| 0.2 | `build/misc-ci-pipeline` | 2026-10-30 | `.github/workflows/ci.yml`: commit-message check on every commit; Android, backend, web-share, pipelines and infra jobs switch on automatically once each project's marker file exists (`gradlew`, `package.json`, `pyproject.toml`, `compose.yaml`). **Merged on local checks only** (product owner, 2026-10-30): first GitHub run was blocked by an account billing lock; confirm a green run on the next push |
| 0.3 | `build/NFR-11-android-skeleton` | 2026-10-30 | Gradle 9.8.2, AGP 9.5 (built-in Kotlin 2.4.21), Hilt, Compose; 19 modules from spec 06 §2 via `build-logic` conventions; compile/target SDK 37, minSdk 28 (still `[ASSUMPTION]`); placeholder screen in EN/BM verified on emulator API 36. Backup and device transfer disabled (NFR-02) |
| — | `docs/misc-personal-use-public-apis` | 2026-10-30 | Personal-use track: OpenFreeMap, Photon, FOSSGIS Valhalla instead of our server (spec 10 §1.1, 05 DS-15–17) |
| 1.1 | `feature/FR-06-toll-domain-model` | 2026-10-30 | `:core:toll`: `Sen`, `EffectivePeriod`/`effectiveAt` (overlaps throw), 04 §3 model types, `TollDataset` lookups (direction-specific rows win). 20 unit tests. Detection, fares and discounts are WP 1.3–1.5 |
| 1.2 | `test/F1-F10-toll-fixtures` | 2026-10-30 | `fixtures/toll/F1–F10.json` (19 cases, format in its README), runner `TollFixturesTest` + `FixtureFilesTest`, `TollEstimator` API stub. Cases are skipped until their WP adds them to `ENGINE_READY`. Spec 04 §5/§6 clarified (geometric fallback, LOW total = range max `[ASSUMPTION]`). F2c BSI fare is a placeholder |
| 1.3 | `feature/FR-06-plaza-detection` | 2026-10-30 | `PlazaDetector`: way-aware node matching + geometric fallback (≤ 15 m, bearing ≤ 45°, only for plazas with no node on the path). 9 tests incl. F1/F4/F6/F7/F9 paths |
| 1.4 | `feature/FR-06-open-and-closed-fares` | 2026-10-30 | `TollEstimator`: open fares, closed pairs per network, toggles, not-charged and missing-fare items, confidence/warnings. Fixtures F1, F3, F4, F6–F9 pass (13 of 19 cases) |
| 1.5 | `feature/FR-06-discounts-and-confidence` | 2026-10-30 | Discounts (scope, exclusions at the charging plaza, half-up/down/up rounding, no stacking), unknown-entry range (LOW, total = max), 30-day fare-age rule. **All F1–F10 fixtures pass** (68 tests). Toll engine done; next per spec 10 §1.1: map (1.14) |
| 1.14 | `feature/C-05-phone-map` | 2026-10-30 | Home screen is a MapLibre map (OpenFreeMap liberty/dark), info button → attribution screen (EN/BM). MapLibre's location permissions removed from the manifest until WP 2.1. Verified on the owner's phone and the emulator |
| 1.15 | `feature/FR-01-search-ui` | 2026-10-30 | Photon search client (Peninsular bbox, transit stops filtered), Room `jalanz.db` for Home/Work/recents, search screen + map pin. Emulator cannot reach Photon on the dev PC (HTTPS inspection); live search confirmed on the phone by the owner |
| 2.9 | `feature/FR-17-share-in` | 2026-10-30 | Pulled forward. `geo:` links and shared text/links with literal coordinates open as a pin; Google short links get an explanation (never expanded, 05 §2); long-press pins; named saved places; typed `lat, lon` in search |
| 1.16 | `feature/FR-05-route-preview-ui` | 2026-10-30 | Partial by design: FOSSGIS Valhalla routes (up to 3, avoid tolls via `exclude_tolls`), route lines on the map, From = Home or picked place. Toll amount, class picker and breakdown deferred until toll data (spec 10 §1.1). Live routes confirmed on the owner's phone (30 Oct 2026) |
| 1.7 | `feature/FR-06-llm-portal-crawler` | 2026-10-30 | `pipelines/`: polite crawler for the LLM calculator's JSON API on www.llm.gov.my (kadartol.llm.gov.my is unreachable), raw snapshots, parser, aliases, diff, 04 §4.3 rules 1/2/3/5/6, change sets + `jalanz-toll` CLI. 41 tests. Real crawl: F1 pair matches (1525 sen, 136.5 km); LEKAS and LDP crawled in full (50 requests). Data stays in `pipelines/data/`. Rules 4/7 wait for WP 1.8 and a second source |
