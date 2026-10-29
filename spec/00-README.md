# Jalanz — Specification Pack

> **Purpose of this pack:** a self-contained, AI-readable specification for building a privacy-respecting
> **Android-only** navigation app for Malaysia, on the phone **and Android Auto**, with (1) good local place
> search, (2) live-traffic-aware routing, (3) end-to-end-encrypted live location/ETA sharing that works
> **while driving** and (4) an always-up-to-date **toll estimator** built from publicly available toll fare tables.
>
> **Prepared:** 23 Oct 2026; updated 24 Oct 2026 for Android-only + Android Auto + drive-time sharing (Asia/Kuala_Lumpur). Facts about laws, prices, products and toll rates
> change — every time-sensitive fact is marked with the date it was checked. Re-verify before relying on it.

---

## Project identity

| Item | Value |
|---|---|
| App name | **Jalanz** (from Malay *jalan*: road / to go) |
| Display name on phone and Android Auto | Jalanz |
| Package ID (applicationId) | **`io.github.aldrinadira.jalanz`** (proposed; hyphens are not allowed in package IDs, so `aldrin-adira` becomes `aldrinadira`). Product owner confirms before WP 0.3; **permanent** once testers install it. Keep it even if a domain is bought later. |
| Repository | https://github.com/aldrin-adira/jalanz (public) |
| Licence | **Apache-2.0** (`LICENSE`, `NOTICE` at repo root) |
| Testing server address | `jalanz.duckdns.org` — registered 29 Oct 2026 (11 §6). Planned upgrade: `jalanz.eu.org` before Phase 4 |
| Kotlin package root | same as the package ID |

Use "Jalanz" in all user-facing text, notifications, the share page and voice prompts. Do not invent other names.

### Open source rules (public repository)
- Code, spec, fixtures and the share page are public under Apache-2.0.
- **Never commit:** secrets (signing keystore, DuckDNS token, TomTom key, server passwords, `.env`,
  `google-services.json`), the crawled LLM toll dataset (until LLM grants permission; crawler code and fixtures are
  fine), testers' drive logs, GPS traces or real share links.
- Security flaws are reported privately per `SECURITY.md`.

## How an AI agent should use this pack

1. Read the files **in order**. Later files reference IDs defined in earlier ones.
2. Treat requirement IDs (`FR-xx`, `NFR-xx`), evidence IDs (`E-xx`), and component IDs (`C-xx`) as stable keys.
   When you write code, put the relevant ID in commit messages, test names and code comments.
3. When a statement is tagged `[ASSUMPTION]`, it was **not** backed by research — validate it before depending on it.
4. When a statement is tagged `[VERIFY]`, it was true when written but is likely to change (prices, laws, URLs).
5. The **toll estimator** (`04-toll-estimator.md`) has precise data models, algorithms and test fixtures.
   Build it test-first from the fixtures in §9 of that file.
6. Never call Google Maps Platform APIs in this product (see `05-data-sources-and-licensing.md` §2).
7. Build in the order given by `10-build-phases.md`, one work package at a time.
8. Follow `09-git-workflow.md` for every branch, commit and merge. Never commit directly to `main` or `develop`.

## File index

| File | What it contains | Read when |
|---|---|---|
| `00-README.md` | This index, glossary, key decisions | First |
| `01-research-findings.md` | Peer-reviewed 2020s evidence + market signals, graded, each with a design implication | Before changing requirements |
| `02-user-requirements.md` | Personas, functional & non-functional requirements with priority and traceability to evidence | Before designing features |
| `03-system-architecture.md` | Components, diagrams (Mermaid), APIs, data flows, deployment | Before writing code |
| `04-toll-estimator.md` | Toll domain model, data-freshness pipeline, route→toll algorithm, edge cases, test fixtures | Before building toll features |
| `05-data-sources-and-licensing.md` | Every external data source, licence, refresh cadence, legal constraints | Before adding any data source |
| `06-android-auto.md` | Android module layout, drive-session service, Android Auto screens/templates, car map rendering, car tests | Before writing app or car code |
| `07-location-sharing.md` | Live sharing while driving: trusted contacts, standing links, E2E encryption, car controls, safety rules, API, tests | Before building sharing |
| `08-tech-stack-and-accounts.md` | Weighted scoring model of every tool/language (cost-first, 10 users), chosen stack, cost, single-VM deployment, optional accounts | Before setting up projects or servers |
| `09-git-workflow.md` | Branch types and naming, when to commit, detailed commit message format, merge checklist, release flow, commit-msg hook | Before the first commit, and whenever committing or merging |
| `10-build-phases.md` | Phase 0 + MVPs 1–8: what testers get each phase, work packages (one branch each), exit criteria, requirement→phase matrix, ready-made AI prompts | Before starting any build work; pick the next work package here |
| `11-zero-cost-testing.md` | RM0 testing edition: free distribution (Firebase App Distribution / sideload), free DuckDNS address, Android Auto unknown-sources setup, tester guide, free-tier guardrails | During the testing period, before following any paid step in 08–10 |
| `12-competitor-analysis.md` | What LAPAN Nav and Peta Maps do with data and privacy, where this spec is stronger, and the ideas adopted (A1–A10) | When changing privacy, sharing, traffic or search design |

## Key decisions (summary)

| # | Decision | Why | Detail |
|---|---|---|---|
| D1 | Open map stack: OpenStreetMap roads + Overture/Foursquare open places, rendered with MapLibre | Licences allow commercial use and mixing; avoids lock-in | 03 §3, 05 |
| D2 | **No Google Maps Platform content** (Places, Directions, Geocoding) | Google's service terms forbid using that content with a non-Google map | 05 §2 |
| D3 | Routing engine: Valhalla (self-hosted) | Open source, toll-aware costing, map-matching, traffic-speed support | 03 §4 |
| D4 | Live traffic via a pluggable provider adapter (start with a commercial free tier), later blended with opt-in user probe data | No open real-time traffic feed exists for Malaysia | 03 §6 |
| D5 | Toll fares sourced from the official LLM toll-rate portal, cross-checked with concessionaire calculators, stored with **effective dates** in a reviewed, versioned dataset | Rates, discounts and even toll-system types change over time | 04 |
| D6 | Core navigation works **without an account** | Login walls were a top complaint about a competing local app (E-M2) | 02 FR-09 |
| D7 | Privacy-by-design, Malaysian hosting, PDPA (as amended 2024) compliance | Users' awareness of location-data collection is low; sovereignty is now a national concern | 02 NFR-02, 03 §9 |
| D8 | **Android only**, native Kotlin + Jetpack Compose | Product owner decision; one platform done well; lets app and server share the Kotlin toll library | 06 §1 |
| D9 | **Android Auto** as a Car App Library *navigation* app; phone and car are two views of one drive session | Car support is expected (E-M2, E-M3); single state avoids phone/car mismatch | 06 §3–4 |
| D10 | Live sharing is **end-to-end encrypted**, uses reusable per-contact links, and is controllable from the car in ≤ 2 taps | Recipients need no app; server never sees location; no SMS/contacts permissions needed | 07 |
| D11 | Stack chosen by a cost-first weighted scoring model for 10 users: one Oracle Always Free VM in Malaysia West running Docker Compose (Ktor, Valhalla, Typesense, PostGIS, Valkey, Caddy) | Cost is the deciding factor; ≈ US$25 one-time + ≈ RM40–70/year | 08 |
| D12 | **Optional** accounts via Firebase Authentication: Google, Microsoft or email link; synced data stays in Malaysia | Free; no passwords; core app still works without sign-in | 08 §7 |
| D13 | **Testing period costs RM0**: sideloaded APK via Firebase App Distribution, Android Auto "Unknown sources", DuckDNS subdomain; Play and a domain only at public launch | No paid items needed for 10 testers; free limited-distribution account covers developer verification | 11 |

## Glossary

| Term | Meaning |
|---|---|
| LLM | Lembaga Lebuhraya Malaysia (Malaysian Highway Authority) — regulator of tolled highways |
| Concessionaire | Private company operating a tolled highway under a concession (e.g., PLUS Malaysia Berhad) |
| Open toll system | Flat fare charged at a plaza regardless of distance |
| Closed toll system | Entry plaza recorded, fare charged at exit based on the entry→exit pair (distance-based) |
| Toll network | A set of plazas sharing one closed-system fare matrix; may span several highways/operators |
| Vehicle class | LLM classes 1–5 (1 = private car; 2 = two-axle commercial, excl. buses; 3 = three+ axles; 4 = taxi; 5 = bus). Some plazas (e.g., Penang Bridge) use their own classification incl. motorcycles |
| MLFF / SLFF | Multi-/Single-Lane Free Flow barrier-less tolling (gantries + ANPR/RFID), being phased in |
| ANPR | Automatic Number Plate Recognition |
| R&R | Rest and Service area on a highway |
| sen | 1/100 of a Ringgit (RM). **All money is stored as integer sen.** |
| POI | Point of interest (a searchable place) |
| PDPA | Personal Data Protection Act 2010 (Malaysia), amended 2024 |
| Probe data | Anonymised speed/position samples from devices, used to compute traffic |
| Car App Library | `androidx.car.app` — Google's library for Android Auto apps; the car host draws templates, the app draws the map surface |
| DHU | Desktop Head Unit — Google's Android Auto emulator for testing |
| FGS | Android foreground service; ours (`DriveSessionService`) has type `location` |
| Standing link | A permanent share link for one trusted contact that goes live whenever the driver shares with them |

## Conventions for code generated from this pack

- Time zone: `Asia/Kuala_Lumpur` for all user-facing times and for toll effective dates.
- Language: Kotlin everywhere on Android; the toll engine is a pure-Kotlin library shared by app and server.
- Money: integer `sen`; never floating point.
- Coordinates: WGS84, `[lon, lat]` order in GeoJSON, `(lat, lon)` only in UI strings.
- Languages: Bahasa Malaysia (`ms`) and English (`en`) are first-class; Chinese (`zh`) and Tamil (`ta`) for place-name aliases.
- Every dataset row that came from outside carries `source_id` and `retrieved_at`.
