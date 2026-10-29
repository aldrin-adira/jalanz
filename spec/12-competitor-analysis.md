# 12 — Competitor Analysis: LAPAN Nav & Peta Maps

What two new Malaysian navigation apps do with data and privacy, learned **only from their public pages**
(websites, privacy notices, provider lists, news coverage). No decompiling or probing of their apps. Checked
29 Oct 2026; both are in early or beta stages and will change.

The ideas adopted into this spec are listed in §5 with the IDs they changed.

## 1. The two apps at a glance

| | LAPAN Nav (lapan-nav.com) | Peta Maps (peta.my) |
|---|---|---|
| Builder | Independent Malaysian developer; launched Oct 2026 as a TuJu alternative | Malaysian team; beta (privacy policy effective 28 Oct 2026) |
| Platform | Web app (PWA) in Chrome/Safari; an Android app on Google Play is mentioned in its privacy notice | Native iPhone and Android betas (TestFlight / Google Play) |
| Android Auto | Not mentioned | **Yes**, plus Apple CarPlay |
| Account | Optional (email or Google) | Sign in with Apple or Google |
| Map tiles | OpenFreeMap (Hungary, via Cloudflare) + Mapbox | Own OSM-based map files hosted on Cloudflare |
| Search | Mapbox + OSM services (Photon, Nominatim, Overpass) + own index of Overture, Foursquare OS Places, GeoNames, Wikidata, Web Data Commons, Malaysian government open data | Own search on OpenStreetMap + Overture Maps + Wikidata |
| Routing | Car: Mapbox (US). Motorcycle/bicycle/walking: self-hosted Valhalla in Singapore | Own routing service (AWS Malaysia region) |
| Live traffic | TomTom (per news report) + anonymous speed data from users | Only from users driving with the app |
| Tolls | Toll fares page; news described manual input | Estimated from published rates for 205 plazas; some show "price unknown" |
| Fuel prices | Not stated | data.gov.my |
| Voice | Google Cloud Text-to-Speech (sentences cached and reused); voice reports via Google Gemini (US) | English and Bahasa Melayu guidance (engine not stated) |
| Hosting | Google Cloud Singapore; sign-in, maps and email in the US; states data is "not stored in Israel" but that Google support staff worldwide, including in Israel, may access it for maintenance | AWS Asia Pacific (Malaysia) for database, search, routing; Cloudflare for website, accounts, traffic service, photos, map files |

## 2. Privacy techniques worth learning from

### 2.1 Peta Maps
1. **Unlinkable traffic uploads.** Single-use tokens are issued with **blind signatures**, so the server can't
   connect a token to the upload that spends it. The traffic service gets no account, device or trip ID and doesn't
   log IP addresses.
2. **Trimming.** Uploads skip the **start and end of each trip** and **areas near saved places**.
3. **Short retention.** Raw pieces deleted within 24 h; matched road segments and times kept 48 h; then only
   per-road averages. Speeds shown only **when several pieces agree** (a k-threshold).
4. **Privacy zones in trip sharing.** Shared position hidden within ~500 m of the start point and of saved Home and
   Work; **"Our server never receives where your Home or Work is."** Arrival-only sharing by default; map position is
   opt-in per trip.
5. **Sharing ends on its own:** at arrival, when stopped, **30 min after the last update**, or after 6 h. Only the
   latest position is stored.
6. **Search and routing pass-through:** requests aren't saved or logged; results cached ≤ 10 min under a key not
   linked to the user.
7. **Account minimisation:** stores a **keyed hash** of the Apple/Google user ID and hashed sign-in tokens.
8. **Contributions:** reviews/photos under a short contributor name; **faces and number plates blurred on the
   phone before upload**; road reports show one rounded location for 1 hour.
9. **Budget guardrail:** daily per-user quota counters keep search and routing costs bounded.
10. Saved and recent places kept **on the phone only**.

Weak points: traffic sharing is **on by default** (opt-out); Cloudflare sees users' IP addresses and the map areas
they view; the account server runs outside Malaysia; Mailchimp (US) for emails; no DPO yet.

### 2.2 LAPAN Nav
1. **Random per-trip ID** for speed data; **trip start and end never sent**; samples kept **1 hour**.
2. **Aggregates without time:** only per-road, per-direction counts for **each quarter-hour of the week**, with no
   ID, trip or date — a privacy-friendly "typical traffic" model.
3. **Server-side proxying:** OSM search requests are sent from LAPAN's servers, so third parties never see the
   user's IP address; only the text and a ~5 km area.
4. **Missing-place mining:** nightly, searches with no match **typed by at least 3 drivers** are looked up in
   Wikidata/Nominatim, with no location, name or email.
5. **Driving-safety extras:** keyboard lock while moving, speed-limit display, rest reminders, one-tap 999 call.
6. **Transparency pages:** a "Who we work with" page listing, per provider, **what it receives and where**; in-app
   "Download my data" and "Delete my account".
7. **Shared TTS cache:** each spoken sentence is generated once and reused for everyone (only the sentence text
   leaves the server).
8. **Testing-period drive logs**, trimmed at start/end, deleted after 30 days.

Weak points: car routes, search text and the user's location go to **Mapbox in the US**; voice reports go to
**Google Gemini in the US**; feedback text goes to Gemini's free tier, which may use it to improve Google products;
data stored in Singapore and the US, not Malaysia; no Android Auto.

## 3. Where this spec is already stronger

| Area | LAPAN | Peta | This spec |
|---|---|---|---|
| Live location sharing | Server sees position (rounded to ~20 m) | Server stores latest position | **End-to-end encrypted**: server never sees position, names or ETAs (07 §4) |
| Data location | Singapore + US | Malaysia (AWS) + Cloudflare | Everything incl. map tiles on one VM in **Malaysia** (08 §6) |
| Routing for cars | Mapbox, US | Own | Own Valhalla, Malaysia; no third party sees trips |
| Toll engine | Manual / fares page | Published rates, 205 plazas | Effective-dated fares, closed-system pairing, festive discounts with exclusions, confidence + per-plaza breakdown, official cross-checks (04) |
| Traffic sharing consent | Optional | **On by default** | **Opt-in** (03 §6, NFR-02) |
| Account | Optional | Required to sign in | Optional (FR-09, FR-26) |
| Android Auto | No | Yes | Yes (06) |
| AI services receiving user content | Gemini (voice, feedback) | None stated | None |

## 4. Gaps where they are ahead (now adopted)

1. Unlinkable traffic contributions (blind-signature tokens) and a time-free typical-traffic model.
2. Privacy zones around trip start, Home and Work for shared positions, computed on the phone.
3. Stale-share auto-end (30 min without updates).
4. Driving-safety extras: speed-limit display, rest reminder, one-tap 999.
5. Fuel prices from data.gov.my and Wikidata names/aliases for search.
6. Missing-place mining with a 3-device threshold.
7. Search/routing requests not logged; per-user daily quotas as a cost guardrail.
8. Account stores only a keyed hash of the provider user ID.
9. A per-provider "what it receives and where" page and in-app "Download my data".
10. Opt-in, trimmed drive logs for the testing period.

## 5. Changes made to this spec

| # | Change | Where |
|---|---|---|
| A1 | Shared positions hidden inside **privacy zones** (~500 m around trip start and saved Home/Work), computed on the phone; zone locations never sent | 07 §5.1 (new), FR-24 |
| A2 | Share session ends **30 min after the last update** (in addition to arrival + 2 min and the 12 h cap) | 07 §6, §8 |
| A3 | Opt-in probe traffic design: blind-signature single-use tokens, no device/account/trip ID, no IP logging, trim trip start/end and privacy zones, raw ≤ 24 h, then per-road averages per quarter-hour of the week; show only when ≥ 5 independent pieces agree | 03 §6 |
| A4 | **FR-27** driving-safety extras: speed-limit display (OSM `maxspeed`), rest reminder after long driving, one-tap emergency call 999 | 02, 10 §11 (Phase 7) |
| A5 | **FR-12** extended with fuel prices from data.gov.my | 02, 05 |
| A6 | Search: Wikidata labels as BM/EN/ZH/TA aliases; missing-place mining (≥ 3 distinct devices, no location or identity) | 02 FR-01/FR-02, 05 |
| A7 | **NFR-13** no logging of search/routing request contents; caches keyed without user identity; per-device daily quotas | 02, 03 §9 |
| A8 | Account table stores a keyed hash (HMAC) of the Firebase UID, not the raw UID | 08 §7.3 |
| A9 | NFR-07 provider page must state, per provider, **what it receives and where**; FR-18 adds in-app "Download my data" | 02 |
| A10 | Testing edition: opt-in diagnostic drive logs, trimmed at start/end, deleted after 30 days | 11 §7 |

Not adopted, on purpose: sending user content to third-party AI services (LAPAN's Gemini use); traffic sharing on by
default (Peta); third-party CDNs that see users' IPs and map areas; mandatory sign-in.

## 6. Open question this raises

LAPAN's provider page discloses that **Google support staff worldwide, including in Israel, may access data** held
in Google Cloud for maintenance. Our optional accounts use **Firebase Authentication** (Google), which holds the
user's email and name. If avoiding any Israel-linked access is a hard requirement, switch accounts to **self-hosted
Keycloak** (runner-up in 08 §2, 0.10 behind) so identity data also stays on our Malaysian server. FCM (push) carries
no user content in our design (07 §7.2), so it is less affected.

## Sources
- LAPAN Nav: https://lapan-nav.com/ · privacy: https://lapan-nav.com/privacy · providers: https://lapan-nav.com/providers
- Peta Maps: https://peta.my/ · privacy: https://peta.my/privacy/
- News: https://www.lowyat.net/2026/408395/local-developers-creates-tuju-navigation-app-rival-lapan/ ;
  https://says.com/my/tech/malaysian-developer-launches-lapan-navigation-app-tuju-alternative ;
  https://www.malaysianow.com/news/2026/10/27/switching-out-waze-but-let-down-by-governments-tuju-try-this
