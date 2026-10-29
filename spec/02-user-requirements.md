# 02 — User Requirements

Priorities use MoSCoW (**M**ust / **S**hould / **C**ould / **W**on't-for-now) for the first public release (v1).
Every requirement traces to evidence in `01-research-findings.md` (`E-xx`) or is marked `[STAKEHOLDER]`
(asked for by the product owner) or `[ASSUMPTION]` (needs validation).

**Platform:** Android phones and Android Auto only (D8, D9). No iOS, no Apple CarPlay.

## 1. Personas

| ID | Persona | Context | Primary needs |
|---|---|---|---|
| P-1 | **Klang Valley commuter** | Daily car commute, multiple tolled urban highways, heavy congestion | Fastest route with live traffic; daily toll cost; reroute explanations |
| P-2 | **Balik kampung / long-distance driver** | Festive trips on PLUS/LPT, family in car | Toll total before departure incl. festive discounts; R&R and fuel/EV stops; plan departure time |
| P-3 | **E-hailing / delivery driver** | Many short trips, time pressure, often motorcycle (delivery) | Accurate ETA, fast search, multi-stop, voice-first, low data/battery use |
| P-4 | **Privacy-conscious switcher** | Leaving Waze/Google for sovereignty or privacy reasons | Clear data practices, no account needed, works as well as what they left |
| P-5 | **Older / occasional driver** | Less tech-confident | Simple mode, large text, clear voice in BM or English |

## 2. Functional requirements

### Search & places
| ID | Requirement | Pri | Acceptance criteria | Trace |
|---|---|---|---|---|
| FR-01 | Search places by name, address, coordinates or Plus Code, in BM and English, typo-tolerant, with local aliases (e.g., "KLCC", "Mid", "Pavi") and Wikidata names in BM/EN/ZH/TA | M | ≥90% top-3 hit rate on the Malaysian POI test list (see 03 §5.3) incl. small attractions; results <500 ms p95 online | E-02, E-17, E-M3, `[STAKEHOLDER]` (missing places in other apps) |
| FR-02 | "Place missing?" flow: user adds/corrects a place (pin + name + category), moderated before public; plus nightly missing-place mining of unmatched searches typed on ≥ 3 distinct devices, with no location or identity (12 §5 A6) | S | Submission in ≤3 taps after a failed search; moderation queue; contributor sees status | E-17, E-M3 |
| FR-17 | Share-in: open shared location pins/links (WhatsApp location, geo: URIs, coordinates in text) directly as a destination; import favourites from a file | S | geo: URIs and plain coordinates always parsed; other link formats per 05 §2 | E-07 |

### Routing & guidance
| ID | Requirement | Pri | Acceptance criteria | Trace |
|---|---|---|---|---|
| FR-03 | Turn-by-turn voice guidance in BM and English with lane guidance on highways; configurable announcement timing (early/normal) and map style (2D/3D) | M | Announcements for every manoeuvre; highway exits announced ≥1 km ahead at highway speed; no raw markup ever shown | E-01, E-03, E-M2 |
| FR-04 | Live-traffic-aware routing, ETA and automatic rerouting | M | ETA error ≤15% median on benchmark trips; reroute offered when saving ≥ configurable threshold (default 5 min) | E-02, E-M2, E-M3 |
| FR-05 | Up to 3 route alternatives, each showing ETA, distance and **toll cost**; "avoid tolls" option; plain-language reason for each suggestion/reroute ("saves 12 min, costs RM3.40 more") | M | Every alternative has toll field (or explicit "unknown"); reason text present for every reroute | E-03, E-04, E-05, E-M5 |
| FR-15 | Plan ahead: choose departure time → predicted ETA and toll (incl. scheduled discounts); multi-stop trips (≥5 stops) | S | Toll for a future date uses fares effective on that date | E-14, E-M6 |

### Tolls
| ID | Requirement | Pri | Acceptance criteria | Trace |
|---|---|---|---|---|
| FR-06 | Toll estimate per route by vehicle class (1–5, plus plaza-specific classes e.g. motorcycles at Penang Bridge), with per-plaza breakdown, "rates as of" date, confidence level and active discounts | M | Matches official calculator on all fixtures in 04 §9; shows data date; confidence shown when < HIGH | E-15, E-M2, E-M5, E-M6, `[STAKEHOLDER]` |
| FR-19 | Trip toll log: after a trip, show the tolls actually passed (from the travelled path), optional monthly total | C | Uses map-matched trace, computed on-device | `[STAKEHOLDER]`, E-10 (on-device for privacy) |

### Sharing
| ID | Requirement | Pri | Acceptance criteria | Trace |
|---|---|---|---|---|
| FR-07 | Live location + ETA sharing **while driving**; recipient needs no app (web page); auto-expires on arrival or after a chosen time; sharer can stop anytime. Full design in `07-location-sharing.md` | M | SH-01–SH-06 in 07 §11 pass; updates ≤10 s p95; ends ≤2.5 min after arrival | `[STAKEHOLDER]` (core reason for this project) |
| FR-20 | Trusted contacts with reusable **standing links** sent once (e.g., via WhatsApp); no contacts/SMS permission | M | Link set-up ≤4 taps when parked; idle link shows "Not sharing right now" | `[STAKEHOLDER]`, E-07 (low switching effort) |
| FR-21 | Start/stop sharing from **Android Auto** and from the phone's navigation notification in ≤2 taps, no typing | M | AA-07 in 06 §8; SH-03 | E-01, E-12, E-13 (distraction), `[STAKEHOLDER]` |
| FR-22 | Auto-share rules (by destination or trip length), announced by voice with 5 s cancel | S | SH-09 | `[STAKEHOLDER]` |
| FR-23 | Recipient notifications: started, ≥10 min late, arrived (Web Push / app push), with no plaintext location sent to the server | S | Notification text built on recipient device | `[STAKEHOLDER]`, E-10 |
| FR-24 | Precision choice per session: exact, approximate (~1 km), ETA only; shared position always hidden inside on-device privacy zones (~500 m around trip start and saved Home/Work, 07 §5.1) | S | SH-07 | E-10, E-C1 |
| FR-25 | Safety safeguards: always-visible indicator, mandatory expiry (max 12 h), no remote start/extend, monthly rule review, "stop all sharing" | M | 07 §8 checklist passes review | E-10, `[ASSUMPTION]` anti-stalking need |
| FR-27 | Driving-safety extras: speed-limit display from OSM `maxspeed` with over-limit warning, rest reminder after long continuous driving (default 2 h `[ASSUMPTION]`), one-tap emergency call **999** from the navigation screen | S | Speed limit shown where OSM has data; reminder fires in GPX replay; 999 button opens the dialer | 12 §2.2 (LAPAN), E-01 |

### Driving context
| ID | Requirement | Pri | Acceptance criteria | Trace |
|---|---|---|---|---|
| FR-08 | **Android Auto** navigation app (Car App Library): home/favourites, search, route preview with tolls, turn-by-turn with lanes, reroute alerts, sharing controls, dark mode; instrument cluster in Phase 7 (10 §11). Design in `06-android-auto.md` | M | AA-01–AA-10 in 06 §8 pass; passes Android Auto quality review | E-M2, E-M3 (competitor lacks car support) |
| FR-09 | Core navigation (search, route, guide, toll, share) works **without an account** | M | Fresh install → first route in <60 s, no sign-up screen | E-07, E-M2 |
| FR-10 | Offline region download (Peninsular, Sabah, Sarawak) for map display, search and routing; toll estimates offline from cached dataset | S | Airplane-mode route + toll on downloaded region | `[ASSUMPTION]` rural/East Malaysia coverage gaps |
| FR-11 | Crowd reports: accidents, hazards, police, flood/closed road; time-decaying and confirm/deny voting | C | Reports expire unless confirmed; abuse-rate limits | E-02 (real-time info valued) |
| FR-12 | Along-route highway info: R&R, petrol stations (with current fuel prices from data.gov.my), EV chargers, with facilities | S | Shown in route preview and as voice prompt before each R&R | E-M2, E-M6 |
| FR-13 | Vehicle profiles: car, motorcycle (respects motorcycle restrictions; voice-first rider mode with minimal map), taxi/e-hailing (class 4), commercial (class 2/3) | S (motorcycle) | Profile changes routing restrictions and toll class | E-06, E-11, E-13, E-14 |
| FR-14 | Simple mode: large text, fewer controls, slower/clearer voice | S | Usable with system font scale 200% | E-09 |
| FR-16 | Onboarding that explains live traffic, toll estimates and privacy choices in ≤4 screens (skippable) | S | Shown once; replayable from settings | E-05, E-10 |
| FR-26 | **Optional** account: sign in with Google, Microsoft or email link to sync favourites, settings, trusted contacts (keys only via passphrase-encrypted vault) and opt-in toll log across phones; delete account in app and on the web | S | AC-01–AC-06 in 08 §7.4 | `[STAKEHOLDER]`, E-07 |
| FR-18 | Privacy dashboard: what is collected, where it is stored, who it is shared with (named), toggle probe sharing, in-app "Download my data" and delete data | M | Every toggle effective immediately; export as JSON | E-10, E-M3, NFR-02 |

## 3. Non-functional requirements

| ID | Requirement | Measure | Trace |
|---|---|---|---|
| NFR-01 | Positional accuracy & responsiveness | Map-matched position lag <1 s; reroute computed <2 s p95 online | E-02 |
| NFR-02 | Privacy & legal: PDPA 2010 as amended 2024 (DPO appointed, breach notification process, data-portability support), data minimisation, probe sharing **opt-in**, raw traces never stored server-side beyond 24 h, servers hosted in Malaysia `[VERIFY]`; sole exception: sign-in identity (email, name) for optional accounts is held by Firebase, disclosed at sign-in (08 §7.3) | Annual privacy review; DPIA before launch | E-10, E-C1, E-M1, 05 §6 |
| NFR-03 | Driver-distraction limits: no free-text typing while vehicle speed > 10 km/h (voice search offered instead); max 2 taps for any in-drive action; high-contrast night mode | UI test checklist | E-01, E-12, E-13 |
| NFR-04 | Battery & data: ≤8%/hour battery during guidance on a mid-range Android; ≤5 MB/hour mobile data with live traffic | Device lab measurement | E-03 (heavy users), `[ASSUMPTION]` targets |
| NFR-05 | Availability & graceful degradation: if traffic provider fails, route without traffic and say so; if toll service fails, use on-device dataset | 99.5% monthly API availability | E-02 |
| NFR-06 | Toll data freshness: official rate changes reflected ≤48 h after publication; every estimate shows dataset date | Monitored by pipeline SLOs (04 §4.5) | E-04, E-M6 |
| NFR-07 | Transparency: public list of all third-party data providers stating, per provider, what it receives and in which country; no third-party SDKs that collect location for advertising | Release checklist | E-M3, E-10 |
| NFR-08 | Usability & accessibility: follow E-08 checklist; screen-reader labels; BM/EN parity | SUS score ≥75 in testing with 15+ Malaysian drivers | E-08, E-09 |
| NFR-09 | Localisation: BM and EN UI and voice at launch; ZH/TA place aliases in search | 100% string coverage | E-M4 |
| NFR-10 | Licensing compliance: attribution screen for OSM (ODbL), Overture (CDLA-Permissive-2.0), Foursquare (Apache-2.0 + NOTICE) | Legal checklist | 05 §1 |
| NFR-11 | Platform: Android phone app, minSdk 28 `[ASSUMPTION]`, latest targetSdk; Android Auto on hosts with Car App API level ≥7; no `ACCESS_BACKGROUND_LOCATION` in v1 (location only inside the user-started foreground service) | Manifest/permission audit per release | 06 §1, §3.3 |
| NFR-12 | Sharing confidentiality: location, names and ETAs end-to-end encrypted; server stores ciphertext only (latest frame, no history) | SH-01, SH-11 | E-10, E-M1, 07 §4 |
| NFR-13 | Request privacy: search and routing request contents are never logged; caches keyed without user identity and expire ≤ 10 min; per-device daily quotas keep paid/free-tier usage bounded | Log audit (SH-01 style scan) | 12 §2.1 (Peta) |

## 4. Out of scope for v1 (Won't, for now)
- Public transport routing.
- In-app toll payment (await MLFF/payment ecosystem; E-M7).
- Social feeds, gamification (E-17 suggests information quality matters more).
- Google Maps Platform features of any kind (05 §2).
- iOS app and Apple CarPlay (D8).
- Android Automotive OS (cars with Google built in) — possible later with the same Car App Library code (06 §1).
- Sending SMS or WhatsApp messages automatically (07 §7.2).

## 5. Open questions for the product owner
1. Is v1 Peninsular only, or national (Sabah/Sarawak have few tolls but need maps)?
2. Is this a non-commercial student/portfolio project or intended for public release? (Affects traffic-provider
   costs, DPO obligations and licensing choices.)
3. Are the safeguards in 07 §8 enough, or should a standing link also require the recipient to confirm once before it works?
4. Which phones and head units will be used for testing (affects minSdk and real-car test plan, 06 §7)?
5. Is Google holding sign-in identity acceptable, or should accounts move to self-hosted Keycloak (the close runner-up in 08 §2)?
