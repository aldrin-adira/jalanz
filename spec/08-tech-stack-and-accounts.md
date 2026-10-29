# 08 — Tech Stack (Weighted Scoring Model) & Accounts

Decides every tool and language needed to build the app for the **first 10 users**, with **cost as the deciding
factor**. Also specifies optional user accounts (FR-26). Interactive version of this model (adjustable weights and
scores): the "Malaysia Nav Stack WSM" page delivered with this pack. Prices checked 24 Oct 2026; `[VERIFY]` items
could not be confirmed.

## 1. Method

Weighted score = Σ(weight × score) ÷ Σ(weights). Scores 1–5 (5 = best). Ties: higher Cost score, then Fit, then
list order. A winning margin under 0.15 is a **close call** — revisit if priorities change.

| Criterion | Weight | Meaning |
|---|---|---|
| Cost | 40 | Free or very low at 10 users (see anchors) |
| Fit to spec | 20 | Covers the requirements in 02, 04, 06, 07 |
| Privacy & data control | 15 | Data can stay in Malaysia / under our control |
| Ease of building | 15 | Docs, popularity, how well AI coding tools know it |
| Room to grow | 10 | Low lock-in; works beyond 10 users |

Cost anchors: **5** free at 10 users, no meaningful catch · **4** free with a catch (limits, pauses, no commercial
use) or a one-off/very low fee · **3** ≈ RM10–50/month · **2** ≈ RM50–150/month · **1** > RM150/month.

## 2. Decisions (winners at default weights)

| Category | Choice | Score | Runner-up | Margin |
|---|---|---|---|---|
| Android app language & UI | **Kotlin + Jetpack Compose** | 5.00 | Flutter (Dart) (4.35) | 0.65 |
| Backend language & framework | **Kotlin + Ktor** | 4.75 | TypeScript + Node.js (Fastify) (4.50) | 0.25 |
| Server hosting | **Oracle Cloud Always Free, Malaysia West (Kulai)** | 4.50 | Home PC / laptop server (3.70) | 0.80 |
| Deployment & orchestration | **Docker Compose on one VM** | 4.80 | k3s (lightweight Kubernetes) (4.35) | 0.45 |
| HTTPS / reverse proxy | **Caddy** | 4.90 | NGINX + Certbot (4.70) | 0.20 |
| Main database | **PostgreSQL + PostGIS (self-hosted)** | 4.85 | Supabase Free (4.00) | 0.85 |
| Live-share cache | **Valkey (open-source Redis) self-hosted** | 5.00 | In-memory map inside Ktor (4.30) | 0.70 |
| Optional user accounts (Google / Microsoft / email) | **Firebase Authentication (Spark, free)** | 4.45 | Keycloak (self-hosted) (4.35) | 0.10 — close call |
| Email sender (only if auth is not Firebase) | **Brevo Free** | 4.60 | Resend Free (4.60) | 0.00 — close call |
| Routing engine | **Valhalla (self-hosted)** | 4.70 | GraphHopper (self-hosted) (4.55) | 0.15 |
| Map tiles | **Self-hosted PMTiles (Planetiler/Protomaps build)** | 4.70 | OpenFreeMap public instance (4.40) | 0.30 |
| Map renderer on Android & Android Auto | **MapLibre Native / MapLibre Compose** | 4.85 | Mapbox Maps SDK (3.25) | 1.60 |
| Place search engine | **Typesense (self-hosted)** | 4.75 | Meilisearch (self-hosted) (4.70) | 0.05 — close call |
| Live traffic data | **TomTom Traffic API free tier** | 4.15 | No live traffic (typical speeds only) (4.00) | 0.15 |
| Push to recipients' phones (app users) | **Firebase Cloud Messaging** | 4.60 | UnifiedPush / ntfy (self-hosted) (4.10) | 0.50 |
| Share web page | **TypeScript + MapLibre GL JS + Vite** | 5.00 | Kotlin/JS or Kotlin Wasm (4.10) | 0.90 |
| Data pipelines (toll crawler, POI build) | **Python + DuckDB** | 4.90 | Kotlin (JVM scripts) (4.40) | 0.50 |
| Analytics / data processing | **DuckDB over Parquet files** | 4.90 | ClickHouse (self-hosted) (4.50) | 0.40 |
| Source control & CI | **GitHub + GitHub Actions** | 4.60 | GitLab + GitLab CI (4.05) | 0.55 |
| Crash reporting | **ACRA (open source, reports to our server)** | 4.40 | Sentry free tier (4.20) | 0.20 |
| Getting the app to the 10 users | **Google Play internal testing** | 4.45 | Firebase App Distribution (3.85) | 0.60 |
| Domain for API and share links | **.com domain** | 4.30 | .my domain (4.05) | 0.25 |

Notes on close calls:
- **Accounts:** Firebase Authentication wins by 0.10 over self-hosted Keycloak. Firebase is free, has Google, Microsoft
  and passwordless email sign-in built in, and sends the sign-in emails itself — but identity data (email, name) is
  held by Google outside Malaysia. Raising the privacy weight to 19 (taking 4 from cost) makes Keycloak win. Only
  identity data is involved: favourites, contacts and all location data stay on our Malaysian server (§4).
- **Search:** Typesense beats Meilisearch by 0.05; either is fine.
- **Email sender:** Brevo and Resend tie; only needed if Firebase is not used. Brevo chosen because its free limit
  (300/day) is confirmed.
- **Hosting:** the model's runner-up is a home PC (scores well on cost only). The realistic paid fallback if Oracle
  capacity is unavailable is a Malaysian VPS with 8 GB RAM (≈ RM80–190/month listed plans).

## 3. Full scores

### Android app language & UI

| Option | Cost | Fit to spec | Privacy & data control | Ease of building | Room to grow | Weighted | Cost at 10 users | Note |
|---|---|---|---|---|---|---|---|---|
| **Kotlin + Jetpack Compose** | 5 | 5 | 5 | 5 | 5 | 5.00 | Free | Native; required for Android Auto Car App Library anyway (D8). |
| Flutter (Dart) | 5 | 3 | 5 | 4 | 4 | 4.35 | Free | Android Auto part would still be native Kotlin, so two languages. |
| React Native (TypeScript) | 5 | 2 | 5 | 4 | 4 | 4.15 | Free | Car app and map-on-car-surface need native code. |

### Backend language & framework

| Option | Cost | Fit to spec | Privacy & data control | Ease of building | Room to grow | Weighted | Cost at 10 users | Note |
|---|---|---|---|---|---|---|---|---|
| **Kotlin + Ktor** | 5 | 5 | 5 | 4 | 4 | 4.75 | Free | Runs the same Kotlin toll library as the app; light on memory. |
| Kotlin + Spring Boot | 4 | 5 | 5 | 4 | 5 | 4.45 | Free, needs more RAM | Shares toll library but heavier; may need a bigger server. |
| TypeScript + Node.js (Fastify) | 5 | 3 | 5 | 5 | 4 | 4.50 | Free | Cannot reuse the Kotlin toll library; two toll engines to keep in sync. |
| Go | 5 | 3 | 5 | 3 | 4 | 4.20 | Free | Fast and small, but no shared toll code. |

### Server hosting

| Option | Cost | Fit to spec | Privacy & data control | Ease of building | Room to grow | Weighted | Cost at 10 users | Note |
|---|---|---|---|---|---|---|---|---|
| **Oracle Cloud Always Free, Malaysia West (Kulai)** | 5 | 5 | 5 | 3 | 3 | 4.50 | RM0 (Arm VM: 2 OCPU, 12 GB RAM, 200 GB disk) | Malaysian region. Catches: 'out of host capacity' at sign-up; idle free VMs can be reclaimed (unlikely with this stack's memory use) [VERIFY]. |
| Malaysian VPS, 8 GB RAM | 2 | 5 | 5 | 4 | 4 | 3.55 | About RM80-190/month (listed plans) | Simple and local; the fallback if Oracle capacity is unavailable. |
| AWS Asia Pacific (Malaysia) region | 2 | 5 | 5 | 4 | 5 | 3.65 | Paid after credits [VERIFY] | Malaysian region, polished tooling, but not free long-term. |
| Home PC / laptop server | 5 | 2 | 5 | 3 | 1 | 3.70 | RM0 + electricity | Share links and APIs go down when the PC sleeps; needs port forwarding. |

### Deployment & orchestration

| Option | Cost | Fit to spec | Privacy & data control | Ease of building | Room to grow | Weighted | Cost at 10 users | Note |
|---|---|---|---|---|---|---|---|---|
| **Docker Compose on one VM** | 5 | 5 | 5 | 5 | 3 | 4.80 | Free | Right size for 10 users; one file describes the whole stack. |
| k3s (lightweight Kubernetes) | 5 | 4 | 5 | 2 | 5 | 4.35 | Free | Overkill at 10 users; easier later scaling. |

### HTTPS / reverse proxy

| Option | Cost | Fit to spec | Privacy & data control | Ease of building | Room to grow | Weighted | Cost at 10 users | Note |
|---|---|---|---|---|---|---|---|---|
| **Caddy** | 5 | 5 | 5 | 5 | 4 | 4.90 | Free | Automatic HTTPS certificates; also serves PMTiles map files. |
| NGINX + Certbot | 5 | 5 | 5 | 3 | 5 | 4.70 | Free | More manual setup. |
| Cloudflare Tunnel | 5 | 4 | 2 | 4 | 3 | 4.00 | Free | Traffic passes through Cloudflare; WebSocket limits to check. |

### Main database

| Option | Cost | Fit to spec | Privacy & data control | Ease of building | Room to grow | Weighted | Cost at 10 users | Note |
|---|---|---|---|---|---|---|---|---|
| **PostgreSQL + PostGIS (self-hosted)** | 5 | 5 | 5 | 4 | 5 | 4.85 | Free | Toll tables, share sessions, synced user data; stays on our Malaysian VM. |
| Supabase Free | 4 | 4 | 3 | 5 | 4 | 4.00 | RM0; 500 MB; pauses after 1 week inactive | Easy, but pauses and data is hosted outside our VM. |
| Neon Free | 4 | 4 | 3 | 4 | 4 | 3.85 | RM0 with limits [VERIFY] | Serverless Postgres; region outside Malaysia [VERIFY]. |

### Live-share cache

| Option | Cost | Fit to spec | Privacy & data control | Ease of building | Room to grow | Weighted | Cost at 10 users | Note |
|---|---|---|---|---|---|---|---|---|
| **Valkey (open-source Redis) self-hosted** | 5 | 5 | 5 | 5 | 5 | 5.00 | Free | Holds latest encrypted share frame with expiry. |
| Upstash Redis free tier | 4 | 4 | 2 | 5 | 3 | 3.75 | RM0 with request limits [VERIFY] | Hosted outside Malaysia. |
| In-memory map inside Ktor | 5 | 3 | 5 | 5 | 2 | 4.30 | Free | Fine for 10 users, but loses state on restart. |

### Optional user accounts (Google / Microsoft / email)

| Option | Cost | Fit to spec | Privacy & data control | Ease of building | Room to grow | Weighted | Cost at 10 users | Note |
|---|---|---|---|---|---|---|---|---|
| **Firebase Authentication (Spark, free)** | 5 | 5 | 2 | 5 | 4 | 4.45 | RM0 (free tier covers up to 50k monthly users for these sign-in types) | Google, Microsoft and passwordless email link built in, and Firebase sends the emails. Identity data (email, name) is held by Google outside Malaysia. |
| Keycloak (self-hosted) | 5 | 4 | 5 | 2 | 5 | 4.35 | Free; needs ~1.25 GB RAM + an email sender | All data on our VM; Google and Microsoft via identity brokering; more complex to run. |
| Own auth in Ktor (Credential Manager + MSAL + email code) | 5 | 4 | 5 | 2 | 4 | 4.25 | Free + email sender | Full control but easy to get security wrong. |
| Supabase Auth | 4 | 4 | 3 | 4 | 4 | 3.85 | RM0; built-in email is rate-limited, needs custom SMTP | Google, Azure (Microsoft) and magic link supported. |

### Email sender (only if auth is not Firebase)

| Option | Cost | Fit to spec | Privacy & data control | Ease of building | Room to grow | Weighted | Cost at 10 users | Note |
|---|---|---|---|---|---|---|---|---|
| **Brevo Free** | 5 | 5 | 3 | 5 | 4 | 4.60 | RM0, up to 300 emails/day | Plenty for 10 users. |
| Resend Free | 5 | 5 | 3 | 5 | 4 | 4.60 | RM0 with monthly cap [VERIFY] | Developer-friendly API. |
| Self-hosted mail server | 5 | 2 | 5 | 1 | 2 | 3.50 | Free | Sign-in emails likely to land in spam. |

### Routing engine

| Option | Cost | Fit to spec | Privacy & data control | Ease of building | Room to grow | Weighted | Cost at 10 users | Note |
|---|---|---|---|---|---|---|---|---|
| **Valhalla (self-hosted)** | 5 | 5 | 5 | 3 | 5 | 4.70 | Free; ~2 GB RAM for Malaysia [ASSUMPTION] | Toll avoidance, map matching, traffic speeds (D3). |
| GraphHopper (self-hosted) | 5 | 4 | 5 | 4 | 4 | 4.55 | Free (open-source core) | Good docs; toll handling needs custom work. |
| OSRM (self-hosted) | 5 | 3 | 5 | 3 | 4 | 4.20 | Free | Very fast, but weak for live traffic changes. |
| Stadia Maps hosted (free plan) | 4 | 4 | 2 | 5 | 2 | 3.65 | RM0, 200k credits/month, no commercial use | Fine for testing only; would need a paid plan to launch. |

### Map tiles

| Option | Cost | Fit to spec | Privacy & data control | Ease of building | Room to grow | Weighted | Cost at 10 users | Note |
|---|---|---|---|---|---|---|---|---|
| **Self-hosted PMTiles (Planetiler/Protomaps build)** | 5 | 5 | 5 | 3 | 5 | 4.70 | Free; one static file served by Caddy | No third party sees which areas users look at. |
| OpenFreeMap public instance | 5 | 4 | 3 | 5 | 4 | 4.40 | RM0, no key, commercial use allowed | No SLA; tile requests go to a third party. Can be self-hosted later (MIT). |
| Stadia Maps free tiles | 4 | 4 | 2 | 5 | 3 | 3.75 | RM0, no commercial use | Testing only. |

### Map renderer on Android & Android Auto

| Option | Cost | Fit to spec | Privacy & data control | Ease of building | Room to grow | Weighted | Cost at 10 users | Note |
|---|---|---|---|---|---|---|---|---|
| **MapLibre Native / MapLibre Compose** | 5 | 5 | 5 | 4 | 5 | 4.85 | Free (open source) | Can draw on the Android Auto surface (06 §5). |
| Mapbox Maps SDK | 3 | 4 | 2 | 5 | 2 | 3.25 | Free tier then paid [VERIFY] | Has an Android Auto extension, but proprietary terms. |
| Google Maps SDK | 4 | 1 | 2 | 5 | 2 | 3.05 | Free tier then paid | Conflicts with decision D2 (no Google Maps content). |

### Place search engine

| Option | Cost | Fit to spec | Privacy & data control | Ease of building | Room to grow | Weighted | Cost at 10 users | Note |
|---|---|---|---|---|---|---|---|---|
| **Typesense (self-hosted)** | 5 | 5 | 5 | 4 | 4 | 4.75 | Free; RAM grows with POI count | Typo-tolerant + geo search (C-04). |
| Meilisearch (self-hosted) | 5 | 4 | 5 | 5 | 4 | 4.70 | Free | Very easy; geo search supported. |
| PostgreSQL full-text + pg_trgm | 5 | 3 | 5 | 4 | 4 | 4.35 | Free (no extra service) | Saves RAM, weaker ranking and typo handling. |

### Live traffic data

| Option | Cost | Fit to spec | Privacy & data control | Ease of building | Room to grow | Weighted | Cost at 10 users | Note |
|---|---|---|---|---|---|---|---|---|
| **TomTom Traffic API free tier** | 5 | 4 | 3 | 4 | 3 | 4.15 | RM0 within free allowance [VERIFY new 2026 pricing] | Covers Malaysia; only road-segment requests leave our server, no user identity. |
| No live traffic (typical speeds only) | 5 | 1 | 5 | 5 | 3 | 4.00 | Free | Fails FR-04 (live traffic is a Must). |
| Own probe data from users | 5 | 1 | 4 | 2 | 4 | 3.50 | Free | 10 users cannot produce useful traffic; backlog idea (10 §13). |

### Push to recipients' phones (app users)

| Option | Cost | Fit to spec | Privacy & data control | Ease of building | Room to grow | Weighted | Cost at 10 users | Note |
|---|---|---|---|---|---|---|---|---|
| **Firebase Cloud Messaging** | 5 | 5 | 3 | 5 | 4 | 4.60 | Free | Standard on Android; payloads carry no location (07 §7.2). Viewers in browsers use free Web Push. |
| UnifiedPush / ntfy (self-hosted) | 5 | 3 | 5 | 3 | 3 | 4.10 | Free | Recipients would need an extra distributor app. |

### Share web page

| Option | Cost | Fit to spec | Privacy & data control | Ease of building | Room to grow | Weighted | Cost at 10 users | Note |
|---|---|---|---|---|---|---|---|---|
| **TypeScript + MapLibre GL JS + Vite** | 5 | 5 | 5 | 5 | 5 | 5.00 | Free | Small static page; WebCrypto for decryption (07 §4). |
| Kotlin/JS or Kotlin Wasm | 5 | 3 | 5 | 3 | 3 | 4.10 | Free | One language, but heavier page and fewer examples. |

### Data pipelines (toll crawler, POI build)

| Option | Cost | Fit to spec | Privacy & data control | Ease of building | Room to grow | Weighted | Cost at 10 users | Note |
|---|---|---|---|---|---|---|---|---|
| **Python + DuckDB** | 5 | 5 | 5 | 5 | 4 | 4.90 | Free | Best scraping libraries; DuckDB reads Overture GeoParquet directly. |
| Kotlin (JVM scripts) | 5 | 4 | 5 | 3 | 4 | 4.40 | Free | Same language as backend; fewer data tools. |

### Analytics / data processing

| Option | Cost | Fit to spec | Privacy & data control | Ease of building | Room to grow | Weighted | Cost at 10 users | Note |
|---|---|---|---|---|---|---|---|---|
| **DuckDB over Parquet files** | 5 | 5 | 5 | 5 | 4 | 4.90 | Free | Warehouse-style queries with no warehouse service to run. |
| BigQuery free tier | 4 | 4 | 2 | 4 | 3 | 3.60 | RM0 within free quota | Data leaves Malaysia; overkill for 10 users. |
| ClickHouse (self-hosted) | 5 | 4 | 5 | 3 | 5 | 4.50 | Free; needs RAM | Only worth it at much larger scale. |

### Source control & CI

| Option | Cost | Fit to spec | Privacy & data control | Ease of building | Room to grow | Weighted | Cost at 10 users | Note |
|---|---|---|---|---|---|---|---|---|
| **GitHub + GitHub Actions** | 5 | 5 | 3 | 5 | 4 | 4.60 | Free plan; free CI minutes for private repos [VERIFY] | Builds APK, runs toll fixtures and tests. |
| GitLab + GitLab CI | 4 | 5 | 3 | 4 | 4 | 4.05 | Free plan with smaller CI allowance [VERIFY] | Good alternative. |
| Builds on own PC only | 5 | 2 | 5 | 3 | 2 | 3.80 | Free | No automatic checks. |

### Crash reporting

| Option | Cost | Fit to spec | Privacy & data control | Ease of building | Room to grow | Weighted | Cost at 10 users | Note |
|---|---|---|---|---|---|---|---|---|
| **ACRA (open source, reports to our server)** | 5 | 4 | 5 | 3 | 4 | 4.40 | Free | Reports stay on our VM; we control what is sent. |
| Sentry free tier | 4 | 5 | 3 | 5 | 4 | 4.20 | RM0 with event limits [VERIFY] | Best tooling; must strip coordinates. |
| Firebase Crashlytics | 5 | 4 | 2 | 5 | 3 | 4.15 | Free | Easy, but crash data held by Google. |

### Getting the app to the 10 users

| Option | Cost | Fit to spec | Privacy & data control | Ease of building | Room to grow | Weighted | Cost at 10 users | Note |
|---|---|---|---|---|---|---|---|---|
| **Google Play internal testing** | 4 | 5 | 4 | 5 | 5 | 4.45 | US$25 one-time developer fee | Up to 100 testers. Android Auto shows Play-installed apps normally. |
| Firebase App Distribution | 5 | 3 | 3 | 4 | 2 | 3.85 | Free | Each user must enable Android Auto developer 'Unknown sources'. |
| Sideload APK | 5 | 2 | 5 | 2 | 1 | 3.55 | Free | Same Android Auto problem, plus manual updates. |

### Domain for API and share links

| Option | Cost | Fit to spec | Privacy & data control | Ease of building | Room to grow | Weighted | Cost at 10 users | Note |
|---|---|---|---|---|---|---|---|---|
| **.com domain** | 4 | 5 | 4 | 4 | 5 | 4.30 | About RM40-70/year | Trustworthy-looking share links. |
| .my domain | 3 | 5 | 5 | 4 | 5 | 4.05 | About RM120/year (example price) | Local identity; registered under MYNIC rules. |
| Free subdomain (e.g. DuckDNS) | 5 | 3 | 4 | 4 | 2 | 4.00 | Free | Share links look suspicious when sent on WhatsApp. |

## 4. Languages and free tools

| Language | Used for |
|---|---|
| Kotlin | Android app, Android Auto, toll library, backend (Ktor) |
| SQL (PostgreSQL/PostGIS) | Database schema, toll queries |
| TypeScript | Share web page |
| Python | Toll crawler, POI and data pipelines |
| YAML | Docker Compose, GitHub Actions |
| Valhalla JSON config | Routing engine settings |

| Tool / data (no real alternative scored) | Purpose | Cost |
|---|---|---|
| Android Studio | IDE | Free |
| Android Auto Desktop Head Unit (DHU) | Car testing | Free |
| Git | Version control | Free |
| Docker | Containers | Free |
| Room (SQLite) + DataStore | On-device storage | Free |
| Hilt, Kotlin coroutines/Flow, WorkManager | Android libraries | Free |
| Android TextToSpeech (ms-MY, en) | Voice guidance | Free |
| Web Push (VAPID) | Viewer notifications | Free |
| OpenStreetMap (Geofabrik extract) | Road data | Free (ODbL) |
| Overture Maps places | Place data | Free (CDLA/Apache/CC0) |
| LLM toll-rate portal | Toll fares | Free (permission to be sought) |
| Uptime Kuma | Uptime alerts | Free (self-hosted) |

## 5. Cost for 10 users (recommended stack)

> During testing, use the RM0 edition in `11-zero-cost-testing.md`, which removes the Play fee and the domain.

| Item | Cost |
|---|---|
| Oracle Cloud Always Free in Malaysia West (Kulai): Arm VM 2 OCPU / 12 GB RAM, 200 GB block storage, 20 GB object storage for backups, 10 TB/month outbound | RM0 |
| Firebase Authentication (Google, Microsoft, email link) | RM0 |
| Firebase Cloud Messaging + Web Push | RM0 |
| TomTom Traffic API within the free allowance | RM0 `[VERIFY]` (new pricing from 1 Jul 2026) |
| GitHub Free + GitHub Actions | RM0 `[VERIFY]` free CI minutes |
| OpenStreetMap, Overture places, LLM toll portal data | RM0 |
| All open-source software (Valhalla, Typesense, PostgreSQL/PostGIS, Valkey, Caddy, MapLibre, DuckDB, ACRA) | RM0 |
| Google Play developer account | **US$25 one-time** |
| `.com` domain | **≈ RM40–70 per year** |
| **Total** | **≈ US$25 + RM40–70 in year one; ≈ RM40–70 per year after** |

Fallback if Oracle's free Arm capacity is unavailable in Malaysia West: a Malaysian VPS with 8 GB RAM,
≈ RM80–190/month (listed plans). Everything else stays the same.

Distribution note: new personal Play developer accounts need a closed test with at least 12 testers for 14 days
**before production access**. With 10 users, stay on the **internal testing** track (up to 100 testers), which is
not affected `[VERIFY]`.

## 6. Deployment for 10 users (replaces the multi-replica plan in 03 §10 for now)

One Oracle Arm (aarch64) VM running Ubuntu and **Docker Compose**:

| Service (container) | Role | Steady RAM `[ASSUMPTION]` |
|---|---|---|
| `caddy` | HTTPS (automatic certificates), serves PMTiles map file and the static share page | 0.1 GB |
| `api` (Kotlin + Ktor) | Route proxy + toll (`:core:toll`), share relay (07), sync and account endpoints (§7), crash-report intake (ACRA) | 0.5 GB |
| `valhalla` | Routing and map matching for the Malaysia/Singapore/Brunei extract | 2 GB |
| `typesense` | Place search | 1.5 GB |
| `postgres` (PostGIS) | Toll data, share sessions, synced user data | 1 GB |
| `valkey` | Latest encrypted share frames, traffic cache | 0.1 GB |
| `jobs` (Python + DuckDB, cron) | Nightly toll crawl; weekly OSM → Valhalla tiles, PMTiles, POI index rebuild | up to 4 GB at night |
| OS + headroom | | 1 GB |

Total steady ≈ 6.2 GB of 12 GB, plus the nightly build. Notes:
- All images must be available for **arm64** `[VERIFY]` (check Valhalla and PostGIS images especially; build your
  own if needed).
- Backups: nightly `pg_dump` to Oracle Object Storage (inside the 20 GB free allowance).
- Oracle may reclaim an Always Free VM only if, over 7 days, CPU, network **and** memory use are all under 20%;
  this stack keeps memory above that `[VERIFY]`. Upgrading the tenancy to Pay As You Go keeps Always Free
  resources free and is a further safeguard.
- Monitoring: Uptime Kuma (or any free external uptime check) on `/health`, share relay and Valhalla.

## 7. Optional user accounts (FR-26)

### 7.1 Rules
1. Accounts are **optional**. Every feature in FR-09 works without one.
2. Sign-in methods: **Google**, **Microsoft** (personal and work/school accounts), or **email** (passwordless link).
   No passwords, no phone/SMS sign-in (SMS costs money).
3. Sign-in happens on the **phone only**; Android Auto never shows sign-in screens.
4. One account per email: signing in with Google and later Microsoft using the same email links to the same
   account (Firebase account linking).
5. Users can delete their account in the app and from a web page; deletion removes the Firebase user and all
   server rows and revokes all share links `[VERIFY]` current Play account-deletion requirements.

### 7.2 What an account adds
| Synced | How | Server can read it? |
|---|---|---|
| Favourites (Home, Work, saved places), settings | `synced_item` rows | Yes (needed to sync; stored in Malaysia) |
| Trusted contacts' labels and link IDs | `synced_item` rows | Yes (labels only, e.g. "Mum") |
| Trusted contacts' keys `Kc` | Encrypted **key vault** blob, key derived from a sync passphrase (Argon2id) | **No** — if no passphrase is set, keys stay on the phone and standing links must be re-sent on a new phone |
| Trip toll log (FR-19), opt-in | `synced_item` rows: date, plazas, amounts — no GPS traces | Yes |
| Location history, live positions | Never synced | — |

### 7.3 Implementation
- **Android:** Firebase Authentication SDK. Google via Credential Manager "Sign in with Google" → Firebase
  credential; Microsoft via Firebase `OAuthProvider("microsoft.com")`; email via Firebase email-link sign-in
  (Firebase sends the email, so no separate email service is needed).
- **Backend (Ktor):** verify the Firebase ID token (JWT signed by Google keys; check issuer, audience = project ID,
  expiry) on each request; `user_id` = HMAC of the Firebase `uid` with a server secret. Guests keep using the anonymous device token.
- **Guest → account:** on first sign-in, upload local favourites/contacts and merge (newest `updated_at` wins per item).
- **Privacy notice at sign-in (PDPA):** "Your name and email are stored by Google Firebase outside Malaysia to let
  you sign in. Your places, contacts and trips are stored on our servers in Malaysia." `[VERIFY]` cross-border
  transfer wording with PDPA guidance.

```sql
CREATE TABLE user_account (
  user_id      TEXT PRIMARY KEY,          -- HMAC-SHA256(server secret, Firebase uid); raw uid never stored (12 §5 A8)
  created_at   TIMESTAMPTZ NOT NULL,
  last_seen_at TIMESTAMPTZ,
  deleted_at   TIMESTAMPTZ
);
CREATE TABLE synced_item (
  user_id    TEXT REFERENCES user_account ON DELETE CASCADE,
  kind       TEXT NOT NULL CHECK (kind IN ('favourite','setting','trusted_contact','toll_log')),
  item_id    TEXT NOT NULL,
  payload    JSONB NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  deleted    BOOLEAN NOT NULL DEFAULT FALSE,
  PRIMARY KEY (user_id, kind, item_id)
);
CREATE TABLE key_vault (
  user_id    TEXT PRIMARY KEY REFERENCES user_account ON DELETE CASCADE,
  ciphertext BYTEA NOT NULL,              -- AES-256-GCM, key from Argon2id(passphrase)
  kdf_params JSONB NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);
```

| Method & path | Purpose | Auth |
|---|---|---|
| `GET /v1/me` | Account status | Firebase ID token |
| `GET /v1/sync?since=` · `PUT /v1/sync/items` | Pull/push synced items | Firebase ID token |
| `GET /v1/sync/vault` · `PUT /v1/sync/vault` | Encrypted key vault | Firebase ID token |
| `DELETE /v1/me` | Delete account and all data | Firebase ID token |

### 7.4 Acceptance tests
| ID | Test | Pass condition |
|---|---|---|
| AC-01 | Fresh install, no sign-in | Search, route, toll, navigation, Android Auto and sharing all work |
| AC-02 | Sign in with Google, Microsoft, email link | Each succeeds on a real device; same email links to one account |
| AC-03 | Guest data then sign in | Favourites and contacts uploaded, nothing duplicated |
| AC-04 | Second phone, same account + passphrase | Favourites and trusted contacts (with working standing links) restored |
| AC-05 | Server data audit | No `Kc` keys or location traces in plaintext |
| AC-06 | Delete account | Firebase user and all rows gone; standing links stop working within 1 min |
