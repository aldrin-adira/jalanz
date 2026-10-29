# 09 — Git Workflow & Commit Standard

Rules for every person or AI agent committing to this project. Words in **MUST / MUST NOT / SHOULD** are binding.
When a rule here conflicts with a habit or tool default, this file wins.

## 1. Summary for agents (read this first)

1. Never work directly on `main` or `develop`. Every change starts on a **typed branch** cut from `develop`
   (or from `main` for a hotfix).
2. Commit after **every completed unit of work**: a test written or run, a feature step implemented, a bug fixed,
   a refactor, a dependency or config change, a data change, a doc change (§5).
3. Every commit message has a **typed header, a detailed body and trailers** (§6). One-line messages are rejected.
4. Each commit is **one logical change** and builds; tests pass unless the commit is an explicitly marked
   test-first commit (§5.3).
5. Merge a branch only when the **merge checklist** passes (§8). Use `--no-ff` merges; never rewrite shared history.

## 2. Repository layout (monorepo)

```text
/android        Gradle project: :app, :car, :core:*, :feature:*  (06 §2)
/backend        Ktor API, share relay, sync/accounts             (03, 07, 08 §7)
/web-share      TypeScript share page                            (07 §7.1)
/pipelines      Python + DuckDB data jobs (toll crawler, POI build)
/infra          Docker Compose, Caddyfile, deployment scripts    (08 §6)
/fixtures       Toll fixtures F1–F10 (04 §9), GPX test drives, search test list
/spec           This specification pack (00–09)
```

## 3. Long-lived branches

| Branch | Purpose | Who writes to it |
|---|---|---|
| `main` | Exactly what the testers have. Every commit on `main` is a tagged release or hotfix. | Merges from `release/*` and `hotfix/*` only |
| `develop` | Integration branch. Always builds and passes all automated tests. | Merges from work branches only |

Both are protected: no direct commits, no force-push, no deletion. Configure branch protection/rulesets on the
Git host where the plan allows it `[VERIFY]` (availability for private repos depends on the GitHub plan); if not
available, agents must still follow these rules.

## 4. Work branches

### 4.1 Types
| Prefix | Use for | Cut from | Merges into |
|---|---|---|---|
| `feature/` | New user-facing or system capability | `develop` | `develop` |
| `fix/` | Bug fix found during development or testing | `develop` | `develop` |
| `test/` | Adding or improving tests only (unit, instrumented, fixtures, DHU checklists) | `develop` | `develop` |
| `refactor/` | Code change with no behaviour change | `develop` | `develop` |
| `perf/` | Performance or battery/data-usage improvement | `develop` | `develop` |
| `data/` | Toll dataset, fixtures data, POI/search test list, map style data | `develop` | `develop` |
| `docs/` | Spec pack, READMEs, comments-only changes | `develop` | `develop` |
| `build/` | Gradle, dependencies, Docker images, CI workflows | `develop` | `develop` |
| `chore/` | Housekeeping that fits nothing above (renames, `.gitignore`, licences) | `develop` | `develop` |
| `spike/` | Throwaway experiment (e.g., Phase 0 renderer spike) | `develop` | **Never merged** — findings go into `/spec` via a `docs/` branch |
| `release/` | Preparing a version for testers | `develop` | `main`, then back into `develop` |
| `hotfix/` | Urgent fix for what testers have | `main` | `main` and `develop` |

Choosing the type: pick by the **main purpose** of the work. If a task needs two purposes (e.g., a feature that
first needs a refactor), do them on **two branches**, refactor first.

### 4.2 Naming
```
<type>/<ref>-<short-kebab-description>
```
- `<ref>` is the spec ID the work serves: `FR-06`, `NFR-11`, `AA-07`, `SH-03`, `F8`, `C-08`, or an issue number
  `#42`. Use `misc` only for `chore/` and `build/` work with no requirement.
- Lowercase description, words separated by hyphens, at most 5 words.
- Releases and hotfixes use versions: `release/0.3.0`, `hotfix/0.3.1-share-link-expiry`.

Examples: `feature/FR-06-closed-toll-pairs`, `fix/AA-05-voice-after-stop-navigation`,
`test/F2-festive-discount-fixtures`, `data/FR-06-plus-rates-2026-10`, `spike/C-01-maplibre-car-surface`.

### 4.3 Size and lifetime
- SHOULD stay under ~400 changed lines (excluding generated files and fixtures) and under 3 working days.
- If bigger, split by sub-requirement (e.g., `feature/FR-06-open-plaza-fares` then
  `feature/FR-06-closed-toll-pairs`). Hide unfinished user-facing work behind a feature flag rather than holding a
  giant branch.

## 5. When to commit

### 5.1 Commit triggers (MUST commit when any of these is reached)
| Trigger | Typical type |
|---|---|
| A feature step is implemented and its tests pass (e.g., a screen, an endpoint, an algorithm stage) | `feat` |
| A bug is fixed and a test proving the fix passes | `fix` |
| New tests are written, or an existing test suite is run after changes and results recorded | `test` |
| A refactor is complete and all tests still pass | `refactor` |
| A performance change is measured | `perf` |
| Dependencies, build scripts, Docker or CI changed | `build` / `ci` |
| Data files changed (toll rates, fixtures, test lists) | `data` |
| Spec or documentation changed | `docs` |
| Formatting/lint only | `style` |
| End of a working session, even mid-task | `wip` (work branches only; see §5.4) |

### 5.2 One logical change
- A commit contains exactly one of the changes above. Do not mix a refactor and a feature, or a fix and an
  unrelated formatting pass.
- Stage files deliberately (`git add <paths>` or `git add -p`). **Do not** use `git add -A` / `git add .` without first
  reviewing `git status` and `git diff --staged`.

### 5.3 Green-build rule
- Every commit MUST compile and pass existing tests for the touched modules.
- Exception — **test-first commit**: on a work branch you may commit new tests that fail because the feature is
  not built yet. The body MUST state `Status: failing (expected) — implemented in the next commit`. The very next
  commit makes them pass. Never merge while such a commit is the branch tip.

### 5.4 WIP commits
- Allowed only on work branches, only to save progress at the end of a session.
- Header: `wip(<scope>): <what is in progress>`; body says what works, what doesn't, and the next step.
- Before merging, the branch owner MAY squash WIP commits into the next real commit with an interactive rebase
  **only if the branch has never been pulled by anyone else**. Otherwise leave them.

## 6. Commit message format

Based on Conventional Commits, with a mandatory detailed body.

```
<type>(<scope>): <summary>

What:
- <what changed, as concrete facts: files, classes, endpoints, data>

Why:
- <the reason: requirement, bug cause, measurement, decision>

How:
- <approach and notable decisions; alternatives rejected if relevant>

Testing:
- <exact commands run and results, manual checks done, or "not applicable" with reason>

Refs: <spec IDs and sections, issue numbers>
[Spec: <file § section> if behaviour follows a spec section]
[Status: failing (expected) — <reason>]          (test-first commits only)
[BREAKING CHANGE: <what breaks and how to migrate>]
[<attribution trailer required by the committing AI tool, if any>]
```

### 6.1 Header rules
- `type` ∈ `feat, fix, test, refactor, perf, data, docs, build, ci, style, chore, wip, revert`.
- `scope` = the module or area touched: `app, car, drive, location, guidance, routing-client, toll, share, map,
  search, data, account, privacy, designsystem, api, relay, web-share, pipeline, infra, fixtures, spec`.
  Multiple modules: pick the main one; list the rest in the body.
- `summary`: imperative mood ("add", "fix", not "added"), lowercase start, no full stop, header ≤ 72 characters.
- Use `!` after the scope for breaking changes: `feat(api)!: ...` plus a `BREAKING CHANGE:` trailer.

### 6.2 Body rules
- The four sections **What / Why / How / Testing** are required for every type except `style` and `wip`
  (those need What and Testing only). Write `- not applicable: <reason>` rather than deleting a section.
- Wrap lines at 72 characters. Use bullet points.
- **Testing** must name exact commands and results, e.g. `./gradlew :core:toll:test — 42 passed, 0 failed`.
  For manual checks, name the checklist ID (e.g., `AA-04 on DHU: pass`).
- Never paste secrets, tokens, real user data or real coordinates of users into messages.

### 6.3 Examples

Good — feature:
```
feat(toll): compute closed-system fares from entry/exit pairs

What:
- Add ClosedFareResolver to :core:toll that pairs closed_entry and
  closed_exit events per toll_network and looks up closed_fare rows
  effective at the exit time
- Add LOW-confidence range output when an exit has no known entry

Why:
- FR-06 requires per-plaza breakdown for closed highways such as PLUS
- 04 §6 defines transaction time as exit time [ASSUMPTION]

How:
- Track open entries in a map keyed by network_id so trips crossing
  two closed networks are handled independently (edge case 4)
- Rejected: summing per-segment rates, because the LLM portal only
  publishes pair fares

Testing:
- ./gradlew :core:toll:test — 37 passed, 0 failed
- Fixtures F1, F5, F7, F8 pass; F2a-d not yet implemented

Refs: FR-06, F1, F5, F7, F8
Spec: 04-toll-estimator.md §6
```

Good — fix:
```
fix(car): stop voice prompts when host calls onStopNavigation

What:
- NavSession now forwards onStopNavigation to DriveSessionService,
  which stops GuidanceEngine and releases audio focus

Why:
- AA-05 failed: our prompts kept playing after the user started
  another navigation app

How:
- Added StopGuidance command; sharing continues with state
  not_navigating as required by 06 §3.2

Testing:
- ./gradlew :car:testDebugUnitTest — 18 passed
- DHU manual check AA-05: pass (voice stops in < 1 s)

Refs: AA-05, FR-08
Spec: 06-android-auto.md §3.2, §4.3
```

Good — test:
```
test(toll): add Christmas 2025 festive discount fixtures

What:
- Add fixtures/toll/F2a-F2d.json covering discount, wrong class,
  excluded plaza and expired window

Why:
- Discount logic must match the real Dec 2025 announcement (04 §9)

How:
- Base fare taken from F1; rounding mode set to half_up_sen

Testing:
- ./gradlew :core:toll:test — 4 new tests fail as expected

Refs: F2a, F2b, F2c, F2d, FR-06
Status: failing (expected) — discount engine added in next commit
```

Bad (rejected):
```
update stuff
```
No type, no scope, no body, no testing, no references.

## 7. Day-to-day procedure for an agent

```text
1. git fetch origin && git status                         # clean tree required
2. git switch develop && git pull --ff-only
3. Decide branch type (§4.1) and ref (§4.2)
4. git switch -c <type>/<ref>-<desc>
5. Loop, for each small step of the task:
     a. make the change
     b. run the relevant checks (§7.1)
     c. review: git status ; git diff
     d. stage deliberately: git add <paths>  (or git add -p)
     e. git diff --staged   # confirm only this logical change, no secrets, no large binaries
     f. git commit          # full message per §6 (use the template in §10)
6. git push -u origin <branch>   # push at least once per working session
7. Before merge: git fetch && git rebase origin/develop
       (only if nobody else has pulled this branch; otherwise git merge origin/develop)
   Re-run all checks (§7.1) and commit any fixes with their own messages.
8. Run the merge checklist (§8). If it passes, open a pull request (§9) and merge with --no-ff.
9. Delete the merged branch locally and on the remote.
```

### 7.1 Checks to run before each commit (run those that apply to the touched area)
| Area | Command / check |
|---|---|
| Any Android module | `./gradlew :<module>:testDebugUnitTest` and `./gradlew :<module>:lint` |
| Toll engine | `./gradlew :core:toll:test` (must include fixtures F1–F10) |
| Car app | `./gradlew :car:testDebugUnitTest`; relevant DHU checklist items AA-01–AA-10 |
| Sharing | backend + Android share tests; relevant SH-xx checks |
| Backend | `./gradlew test` in `/backend` |
| Web share page | `npm run lint && npm test` in `/web-share` |
| Pipelines | `pytest` in `/pipelines` |
| Infra | `docker compose -f infra/compose.yaml config` |
| Spec/docs only | Links and IDs referenced exist (FR/NFR/AA/SH/AC/F IDs) |

## 8. When to merge (merge checklist)

Merge a work branch into `develop` **only when all are true**:
1. The branch's goal is complete: the acceptance criteria of its referenced IDs that are in scope are met, or a
   clearly bounded sub-part is complete and any incomplete user-facing work is behind a feature flag.
2. CI is green on the branch head; no commit at the tip is marked `Status: failing`.
3. Toll fixtures pass if anything under `:core:toll`, `/fixtures` or toll data changed.
4. The branch is up to date with `develop`.
5. Spec files are updated in the same branch if behaviour differs from the spec, or a `docs/` branch is opened
   and linked.
6. No secrets, no large generated files (OSM `.pbf`, `.pmtiles`, Valhalla tiles, APKs, toll bundles).
7. Pull request description complete (§9); a human reviewer has approved if one is available (for AI-only work,
   the agent re-reads the full diff before merging).

Do **not** merge when: tests fail; the feature breaks an existing user flow; the branch is a `spike/`; review
comments are unresolved; or the change only makes sense together with another unmerged branch (merge them in
order, or combine the work).

### 8.1 Merge methods
| From → into | Method |
|---|---|
| work branch → `develop` | `git merge --no-ff` with message `merge(<type>): <branch name> — <one-line result>` |
| `develop` → `release/x.y.z` | create branch; only fixes, version bump and changelog on it |
| `release/x.y.z` → `main` | `--no-ff`, then tag `vX.Y.Z` (annotated) |
| `release/x.y.z` → `develop` | `--no-ff` to bring back release fixes |
| `hotfix/*` → `main` and `develop` | `--no-ff` into both; tag `vX.Y.(Z+1)` |

Never: squash-merge into `develop` (loses the detailed history), force-push `main`/`develop`, rebase a branch
others have pulled, or commit merge-conflict markers.

### 8.2 When to cut a release
Cut `release/x.y.z` when `develop` contains a coherent set of finished features the testers can use and all
phase exit criteria in `10-build-phases.md` pass. Versioning: Semantic Versioning (`0.MINOR.PATCH` until public
launch); Android `versionCode` increments by 1 on every release. Release builds go to the Play internal testing
track (08 §5).

## 9. Pull request description template

```
## Summary
<one paragraph: what this branch delivers>

## Requirements
- Refs: FR-.., NFR-.., AA-.., SH-.., F..
- Spec sections followed: <file §>

## Changes
- <bullets per module>

## Testing
- Automated: <commands and results>
- Manual: <DHU / device checks with IDs>

## Risks / follow-ups
- <known gaps, flags, VERIFY items touched>
```

## 10. Repository setup (do once)

`.gitmessage` (commit template; enable with `git config commit.template .gitmessage`):
```
<type>(<scope>): <summary, imperative, max 72 chars>

What:
- 

Why:
- 

How:
- 

Testing:
- 

Refs: 
```

`.githooks/commit-msg` (enable with `git config core.hooksPath .githooks`):
```sh
#!/bin/sh
# Rejects commit messages that break 09-git-workflow.md §6.
msg_file="$1"
header=$(head -n1 "$msg_file")
case "$header" in Merge*|merge\(*|Revert*) exit 0;; esac
echo "$header" | grep -Eq '^(feat|fix|test|refactor|perf|data|docs|build|ci|style|chore|wip|revert)\([a-z0-9-]+\)!?: [a-z].*[^.]$' || {
  echo "commit-msg: header must be '<type>(<scope>): <summary>' (see 09 §6.1)"; exit 1; }
[ ${#header} -le 72 ] || { echo "commit-msg: header longer than 72 characters (see 09 §6.1)"; exit 1; }
type=$(echo "$header" | sed -E 's/^([a-z]+).*/\1/')
body=$(sed -n '3,$p' "$msg_file" | grep -v '^#')
need="What: Testing:"
[ "$type" != "style" ] && [ "$type" != "wip" ] && need="What: Why: How: Testing:"
for s in $need; do
  echo "$body" | grep -q "^$s" || { echo "commit-msg: missing '$s' section (see 09 §6.2)"; exit 1; }
done
if [ "$type" != "wip" ] && [ "$type" != "style" ] && [ "$type" != "chore" ] && [ "$type" != "build" ] && [ "$type" != "ci" ]; then
  echo "$body" | grep -q '^Refs: .\+' || { echo "commit-msg: missing 'Refs:' trailer (see 09 §6)"; exit 1; }
fi
exit 0
```

`.gitignore` (minimum): `local.properties`, `*.keystore`, `*.jks`, `.env*`, `**/secrets/`, `*.pbf`, `*.pmtiles`,
`valhalla_tiles/`, `*.apk`, `*.aab`, `build/`, `.gradle/`, `node_modules/`, `__pycache__/`, `toll-bundle-*.sqlite*`.
API keys (TomTom, Firebase config for non-public values, signing keys) live in environment variables or the CI
secret store, never in Git.

CI (GitHub Actions) MUST run on every push to a work branch and on pull requests: the checks in §7.1 for changed
paths, plus the commit-message check above on every commit in the branch.
