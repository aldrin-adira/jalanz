# 05 — Data Sources & Licensing

All entries checked 23 Oct 2026. Licences and terms change — `[VERIFY]` before release.

## 1. Source registry

| ID | Source | Used for | Licence / terms | Attribution required | Refresh |
|---|---|---|---|---|---|
| DS-01 | OpenStreetMap (Geofabrik extract covering Malaysia, Singapore, Brunei) | Roads, routing graph, base map, addresses, toll booth/gantry nodes | ODbL 1.0 | "© OpenStreetMap contributors" on map + attribution screen | Weekly |
| DS-02 | Overture Maps — places theme | POI search | Multi-licence per record: CDLA-Permissive-2.0 (most sources), **Apache-2.0** for Foursquare-sourced records, CC0-1.0 for AllThePlaces records | "© Overture Maps Foundation"; ship Apache-2.0 text + Foursquare NOTICE if Foursquare records are used | Monthly (Overture releases) |
| DS-03 | Foursquare Open Source Places (direct) | Optional extra POIs | Apache-2.0 | Licence + NOTICE | Monthly |
| DS-04 | LLM toll calculator: formerly kadartol.llm.gov.my, now the JSON API behind the "Kalkulator Tol" on www.llm.gov.my (`/Calculator/...`) | Official open fares and closed entry→exit fares by class, with distance | Government website — **no explicit open-data licence found** `[VERIFY]`; seek permission/data-sharing | Cite "Lembaga Lebuhraya Malaysia" as source in toll details | Nightly crawl |
| DS-05 | Concessionaire calculators (PLUS, LPT2, others) | Cross-check fares | Website terms of each operator `[VERIFY]` | Cite operator | Weekly |
| DS-06 | Official announcements (LLM, Works Ministry, Bernama) | Discounts, freezes, new plazas, effective dates | Facts are used, not text; link to source | Link in curator record | Daily watch |
| DS-07 | Commercial traffic provider (e.g., TomTom Traffic Flow/Incidents) | Live speeds, incidents | Commercial API terms; free tier for development (≈2,500 non-tile + 50,000 tile requests/day) `[VERIFY]`; check terms on caching, display and mixing with OSM maps | As required by provider | Live (1–5 min) |
| DS-09 | Push delivery: Firebase Cloud Messaging (recipients with the app) and browser Web Push services (share-page viewers) | Recipient notifications (FR-23) | Provider terms `[VERIFY]` | — | Event-driven |
| DS-10 | Android `TextToSpeech` voices (ms-MY, en-MY/en-GB) | Voice guidance | Device/engine terms | — | On device |
| DS-11 | Firebase Authentication | Optional sign-in (Google, Microsoft, email link) | Firebase terms; free tier | — | — |
| DS-12 | Oracle Cloud Infrastructure, Malaysia West (Kulai), Always Free | Hosting for the first 10 users | Oracle cloud terms; Always Free limits (08 §6) | — | — |
| DS-13 | data.gov.my fuel price data (weekly) | Fuel prices in FR-12 | Malaysian open government data terms `[VERIFY]` | Cite data.gov.my | Weekly |
| DS-14 | Wikidata | Place-name labels/aliases in BM/EN/ZH/TA for search | CC0 | — | Monthly |
| DS-15 | **OpenFreeMap** public vector tiles (`tiles.openfreemap.org`) — personal-use track (10 §1.1) | Phone base map (MapLibre style + tiles) until our own PMTiles exist | Free, no key, no request limit stated; tiles are OpenMapTiles schema built from OSM (ODbL) `[VERIFY]` current terms | "OpenFreeMap © OpenMapTiles Data from OpenStreetMap" on map + attribution screen | Weekly (theirs) |
| DS-16 | **Photon** public geocoder by komoot (`photon.komoot.io`) — personal-use track | Place search with search-as-you-type until our Typesense index exists | Free, fair use only, no key; heavy use not allowed `[VERIFY]`; results are OSM data (ODbL) | "© OpenStreetMap contributors" | Their OSM import |
| DS-17 | **Valhalla on the FOSSGIS server** (`valhalla1.openstreetmap.de`) — personal-use track | Routes, alternatives, `trace_attributes` OSM way IDs for toll detection, until our own Valhalla runs | FOSSGIS e.V. public service, fair use, no key, no SLA `[VERIFY]` usage policy; OSM data (ODbL) | "© OpenStreetMap contributors"; credit FOSSGIS on attribution screen | Their OSM import |
| DS-08 | User contributions (place edits, reports, opt-in probes) | Missing POIs, hazards, traffic | Your own terms of use; contributor licence grant to you; if merged into OSM-derived data, consider ODbL share-alike obligations | — | Continuous |

### Mixing licences — notes
- Joining CDLA-Permissive-2.0 data with OSM is permitted, but the resulting database may need to carry ODbL if
  it is a derivative database (per Overture's own guidance). Keep the POI index as a **separate database** from
  the OSM-derived routing/map data where practical, and get legal advice before redistributing combined data.
- Overture lets you filter to CDLA-only records by excluding `source = Foursquare` if Apache-2.0 obligations
  are unwanted.

## 2. Google Maps Platform — not used (decision D2)
Google's service-specific terms state that content from the Places, Directions, Geocoding, Distance Matrix and
Roads APIs must not be used in conjunction with a non-Google map. Because this app renders an OSM/MapLibre map,
**do not** use those APIs.
- Allowed instead: accepting a user-shared `geo:` URI or plain coordinates (FR-17).
- Parsing Google Maps share links: only extract coordinates that appear literally in a link the user pastes;
  do not call Google endpoints to resolve short links. `[VERIFY]` with legal review before shipping.

## 3. Toll data acquisition etiquette (DS-04, DS-05)
- Identify the crawler with a contact email in the User-Agent.
- ≤1 request/second, nightly, with exponential back-off on errors.
- Respect `robots.txt` and site terms; stop and seek permission if disallowed.
- Store raw snapshots so parsing can be re-run without re-fetching.
- **Portal status (30 Oct 2026):** `kadartol.llm.gov.my` did not accept connections (timeout from Malaysia,
  refused from abroad); the newest archived copy is from 9 Mar 2024 (an ASP.NET form). The same calculator is now
  served on `www.llm.gov.my` as a JSON API (`/Calculator/getHighway`, `getPlazaEnter`, `getPlazaExit`,
  `getTollRate`); its `robots.txt` allows `/Calculator/`, no login, no CAPTCHA, no terms forbidding automated access.
  The crawler (WP 1.7) uses that API. Details: `pipelines/docs/llm-kadartol-portal.md` `[VERIFY]` before release.
- The crawler waits at least 3 s between requests (stricter than 1/s) and identifies itself as
  `Jalanz/0.0.1 (+https://github.com/aldrin-adira/jalanz)`; the project URL is the contact.
- Long-term: request a machine-readable feed from LLM (the portal already exposes highway → plaza → fare +
  distance, so the data exists in structured form).

## 4. Data that must NOT be collected
- Raw location history tied to an identity (NFR-02).
- Contacts, call logs, advertising IDs.
- Location data for analytics/advertising SDKs.
- Push payloads containing location, names or ETAs (07 §7.2).
- Permissions not needed in v1: `ACCESS_BACKGROUND_LOCATION`, SMS, call log, contacts (06 §3.3, 07 §8).

## 5. Attribution screen (NFR-10) — required text blocks
1. "Map data © OpenStreetMap contributors, available under the Open Database License (ODbL)."
2. "Places data © Overture Maps Foundation (CDLA-Permissive-2.0)" + per-source notices.
3. "Includes data from Foursquare Open Source Places © Foursquare Labs, Inc., Apache License 2.0" (if used).
4. "Toll rates sourced from Lembaga Lebuhraya Malaysia and highway operators; estimates only."
5. Traffic provider attribution as required.
6. Personal-use track (DS-15–17): "Map tiles by OpenFreeMap © OpenMapTiles, built from OpenStreetMap data." Add Photon and FOSSGIS lines when search and routing land. Shown in `:feature:settings` `AttributionScreen` since WP 1.14.

## 6. Legal context checklist (Malaysia) `[VERIFY]`
- PDPA 2010 as amended by the Personal Data Protection (Amendment) Act 2024: data protection officer
  appointment, mandatory data-breach notification (guidance indicates notifying the Commissioner within 72 hours
  of becoming aware), right to data portability, higher penalties (up to RM1 million), direct obligations on
  data processors. These provisions came into force in stages during 2025.
- Government guidance on navigation apps: civil servants were barred from Waze (Oct 2026) on data-security
  grounds; data residency and transparency are therefore product features, not just compliance items.
- Road-safety rules on phone use while driving apply; design for hands-free use (NFR-03).

## 7. Sources consulted (non-academic, for facts in 03–05)
- LLM toll-rate portal: http://kadartol.llm.gov.my/
- LPT2 toll calculator (network spans operators): https://www.lpt2.com.my/
- Toll system types & classes: https://www.carsome.my/news/?p=19937 ; Prolintas class definitions via
  https://www.thevibes.com/articles/news/74679/
- Christmas 2025 discount & exclusions: https://skreact.siakapkeli.my/articles/llm-jangka-2-6-juta-kenderaan-sehari-di-lebuh-raya-utama-sempena-hari-krismas
- 2026 toll freeze: https://www.nst.com.my/amp/news/nation/2025/12/1341045/llm-ensure-high-service-quality-toll-rates-stay-unchanged
- MLFF status: https://www.thestar.com.my/news/nation/2026/09/30/more-time-needed-to-implement-mlff-system-says-works-minister ;
  https://theedgemalaysia.com/node/817189
- Closed→open conversion example: https://en.wikipedia.org/wiki/Kajang%E2%80%93Seremban_Highway
- De-tolled stretch example: https://www.malaymail.com/news/malaysia/2018/05/31/salak-highway-and-sungai-besi-kl-stretch-to-be-non-toll-gazetted-stretches/1637142
- OSM toll tagging: https://wiki.openstreetmap.org/wiki/Key:toll ; https://wiki.openstreetmap.org/Tag:barrier=toll_booth
- Overture places licensing: https://docs.overturemaps.org/guides/places ; https://docs.overturemaps.org/blog/2025/09/24/release-notes/
- Google Maps Platform service terms: https://cloud.google.com/maps-platform/terms/maps-service-terms/index-20240522
- TomTom pricing/free tier: https://docs.tomtom.com/pricing
- PDPA amendments: https://insightplus.bakermckenzie.com/bm/data-technology/malaysia-personal-data-protection-amendment-act-2024-to-come-into-force ;
  https://www.roedl.com/en/insights/malaysia-personal-data-protection-amendment-act-2024/
- Android Auto navigation apps: https://developer.android.com/training/cars/apps/navigation ;
  draw maps: https://developer.android.com/training/cars/apps/library/draw-maps ;
  quality guidelines: https://developers.google.com/cars/design/create-apps/ux-requirements/templated-apps ;
  Car App Library releases: https://developer.android.com/jetpack/androidx/releases/car-app
- MapLibre Compose surface presentation: https://maplibre.org/maplibre-compose/presentation/
- Ferrostar: https://docs.stadiamaps.com/sdks/ferrostar/
- Play background location policy: https://support.google.com/googleplay/android-developer/answer/9799150
- Foreground service types/changes: https://developer.android.com/develop/background-work/services/fgs/changes
- Oracle Always Free resources: https://docs.oracle.com/en-us/iaas/Content/FreeTier/resourceref.htm ; regions: https://www.oracle.com/cloud/cloud-regions/
- Firebase Microsoft sign-in on Android: https://firebase.google.com/docs/auth/android/microsoft-oauth
- OpenFreeMap: https://openfreemap.org
- Google Play fee and new-account testing rule: https://www.testerscommunity.com/blog/how-much-does-it-cost-to-publish-an-app-on-google-play
- Waze ban & TuJu: https://bernama.com/en/general/news.php?id=2623504 ;
  https://soyacincau.com/2026/10/22/malaysia-urge-malaysians-ditch-waze-for-tuju-map-navigation-review/ ;
  https://www.thestar.com.my/news/nation/2026/10/22/most-users-game-for-tuju-if-it-works-well
