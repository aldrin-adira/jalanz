# pipelines

Python + DuckDB data jobs: LLM toll-rate crawler (WP 1.7), later the place index and map/routing builds.
Design: `spec/04-toll-estimator.md` §4. **Crawled toll data is never committed** (AGENTS.md §2): it lives in
`pipelines/data/` (ignored by Git) or wherever `--data-dir` / `$JALANZ_DATA_DIR` points.

## Setup and tests
```sh
cd pipelines
python -m venv .venv
.venv/Scripts/python -m pip install -e '.[test]'     # .venv/bin/python on Linux/macOS
.venv/Scripts/python -m pytest
```

## Where the LLM toll rates come from
`kadartol.llm.gov.my` (spec 05 DS-04) is unreachable today; the same calculator is a small JSON API on
`https://www.llm.gov.my/Calculator/...` (highways → entry plazas → exit plazas → fare per class + distance).
robots.txt allows it; no login or CAPTCHA. Full findings: [`docs/llm-kadartol-portal.md`](docs/llm-kadartol-portal.md).
A full PLUS crawl is several thousand requests (hours at one request every 3 s), so crawls can be limited and resumed.

## The crawler
Politeness (05 §3): User-Agent `Jalanz/0.0.1 (+https://github.com/aldrin-adira/jalanz)`, robots.txt checked first
(with wildcard support), at least 3 s between requests (`--delay`), back-off 9/27/81 s on server errors, and a
stop on 403/429 or a robots disallow (exit code 2: ask LLM for permission, do not work around it).

Flow (04 §4): fetch → raw snapshot (content-addressed in `data/raw/`, re-parsable) → parse → alias matching →
diff against fares in effect → validation (04 §4.3 rules 1, 2, 3, 5, 6; rules 4, 7, 8 need WP 1.8, a second source
or a curator) → change set. Nothing changed and no warnings = `auto_confirmed` (only `verified_at` moves);
anything else is `pending` until a human approves it. Approving writes effective-dated rows and closes old ones.

```sh
J=".venv/Scripts/python -m jalanz_pipelines.llm_toll"    # or the jalanz-toll script
$J highways                                              # portal highway ids
$J crawl --highway 1 --entry 212 --exit 134              # one pair (fixture F1)
$J crawl --highway 24 --highway 4                        # whole highways
$J crawl --all-highways --max-requests 2000              # stops early; continue with --resume RUN_ID
$J change-set list
$J change-set show 2
$J alias add 1 "SIMPANG PULAI" E1-SIMPANG-PULAI          # portal name -> our plaza_id (04 §3)
$J change-set build 2                                    # rebuild from stored responses, no network
$J change-set approve 3 --by owner [--effective-from 2026-11-01]
$J change-set reject 3 --by owner
$J lookup 1 "Simpang Pulai" Juru
$J export observations 2 data/exports/run2.csv           # parsed fares of a run
$J export current data/exports/current.csv               # approved fares in effect
```

Every fare row carries `source_id` (`llm_kadartol`), `retrieved_at` (MYT, +08:00), amounts in integer sen and the
distance in km. Rows are keyed by the portal's own ids; `plaza_alias` maps names to our `plaza_id`, so the plaza
table from WP 1.8 joins later without re-crawling.

Quirks seen on the real portal (Ampangan self-pair on LEKAS, asymmetric pairs) are listed in the docs file.
