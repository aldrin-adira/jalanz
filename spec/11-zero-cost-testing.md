# 11 — Zero-Cost Testing Edition (RM0)

A version of the plan for the **testing period with up to 10 testers** in which **nothing is paid**: no Play
developer fee, no domain, no paid APIs. It overrides the sections listed in §2. Everything else in the spec stays
the same. Use this edition until a public launch is decided (§9).

Facts checked 29 Oct 2026. `[VERIFY]` items could not be confirmed.

## 1. Cost

| Item | Testing edition | Main plan (08 §5) |
|---|---|---|
| Server: Oracle Cloud Always Free, Malaysia West (Kulai) | RM0 | RM0 |
| Optional accounts: Firebase Authentication (Spark plan) | RM0 | RM0 |
| Push: Firebase Cloud Messaging + Web Push | RM0 | RM0 |
| Live traffic: TomTom free allowance, hard-capped in code (§7) | RM0 | RM0 |
| Code, CI, releases: GitHub Free + GitHub Actions | RM0 | RM0 |
| App distribution: **Firebase App Distribution** (or GitHub Releases) | RM0 | US$25 Play fee |
| Domain: **free DuckDNS subdomain** (`<name>.duckdns.org`) | RM0 | ≈ RM40–70/year |
| App signing key: self-generated keystore | RM0 | RM0 |
| Android developer verification: free **limited distribution** account (≤ 20 devices), only when Malaysia requires it | RM0 | — |
| **Total** | **RM0** | ≈ US$25 + RM40–70/year |

Not counted: your own phone, computer, car/head unit, mobile data, and any paid AI coding tool you choose to use.

## 2. What this edition overrides

| Spec section | Main plan | Testing edition |
|---|---|---|
| 08 §2 "Getting the app to the 10 users" | Google Play internal testing | Firebase App Distribution (primary), GitHub Releases + Obtainium (alternative) — §4 |
| 08 §2 Domain | `.com` domain | DuckDNS free subdomain — §6 |
| 08 §5 Cost | ≈ US$25 + domain | RM0 — §1 |
| 06 §7 Play review | Android Auto quality review on Play | Not needed for sideloaded apps. Still follow 06 §4 rules so a later Play review passes |
| 06 §4.3 `onAutoDriveEnabled` | Required for Play review | Optional (useful for demos); keep it if time allows |
| 09 §8.2 Releases | Release bundle to Play internal testing | Signed APK to Firebase App Distribution + GitHub Release — §5 |
| 10 Phase 0 WP 0.5 | Domain + HTTPS | DuckDNS + HTTPS — §6 |
| 10 release WPs (1.18, 2.14, 3.11, 4.10, 5.10, 6.9, 7.10, 8.6) | Play upload; 3.11 includes Android Auto review | Run §5 pipeline; no Play review |
| 10 §13 Public launch tasks | Play production access | Still applies — see §9 |

## 3. Android developer verification (sideloading rules)

- Google is introducing **developer verification** for apps installed outside Play on certified Android devices.
  It starts **30 Sep 2026 in Brazil, Indonesia, Singapore and Thailand** and expands **globally during 2027**.
  Malaysia is not in the first wave, so sideloading works without verification for now.
- When it reaches Malaysia, register a **limited distribution** account: **no fee, no government ID**, for
  students and hobbyists, distributing to **up to 20 devices**. Enough for 10 testers plus your own devices.
- Installing over USB with `adb` remains allowed for developers' own devices. Google also offers an "advanced
  flow" for users to install unverified apps after a one-time setup.
- Watch for the Malaysia date (WP-level reminder in §8) and register before it applies `[VERIFY]` exact date.

## 4. Distribution

### 4.1 Primary: Firebase App Distribution
- Free; testers are invited by email, install via a link or the Firebase **App Tester** app, and get notified of
  new versions. Keeps the source code private. `[VERIFY]` current tester and storage limits.
- Uses the same Firebase project as Auth and FCM.

### 4.2 Alternative: GitHub Releases + Obtainium
- Attach the signed APK to each GitHub Release. Testers use **Obtainium** (free, open source) to watch the
  releases and update.
- Needs a **public** repository, or each tester added as a collaborator with a personal access token in Obtainium.

### 4.3 App signing (both options)
- Generate one release keystore locally (`keytool`). The same key must sign every future version, or testers
  must uninstall to update — and Play would later need it too.
- Store it as a base64 GitHub Actions secret plus an offline backup (two places). Never commit it (09 §10).
- Package ID is the one chosen for the main plan; do not change it later.

## 5. Release pipeline (replaces Play upload)

Triggered by pushing tag `v0.N.0` after `release/0.N.0` is merged to `main` (09 §8.1):

```text
GitHub Actions (free):
1. Check out the tag; run all checks in 09 §7.1.
2. Decode keystore secret; build signed release APK (./gradlew :app:assembleRelease).
3. Upload APK to Firebase App Distribution, group "testers", with release notes
   generated from commit types since the previous tag.
4. Create a GitHub Release for the tag with the same notes (and the APK if using §4.2).
5. Post the version and checksum in the release notes.
```

## 6. Server address without a paid domain

1. `jalanz.duckdns.org` is registered (29 Oct 2026). Point it to the Oracle VM's public IP in the DuckDNS dashboard
   (the IP is fixed, so no updater script is needed). Use a **reserved** public IP in Oracle if available so the
   address survives VM re-creation. The DuckDNS token is a secret: server `.env` or CI secret only.
2. Open ports **80 and 443** in two places: the Oracle VCN security list **and** the VM's firewall
   (Oracle's Ubuntu images block them by default in `iptables`). This is the most common setup mistake.
3. Caddy gets free Let's Encrypt HTTPS certificates for the subdomain automatically.
4. All URLs in the spec that say `<domain>` / `<share-domain>` use the DuckDNS name: API, map tiles, share page
   (`https://jalanz.duckdns.org/c/{link_id}#k=...`), privacy policy page.
5. Downsides: share links look less trustworthy in WhatsApp; DuckDNS has no uptime guarantee; DNS filters such as
   NextDNS can block dynamic-DNS names. If the name changes, standing links break.
6. Planned upgrade: apply for the free domain `jalanz.eu.org` (manual approval, can take weeks) and host its DNS on
   deSEC. Switch before Phase 4 so standing share links use the final address. (deSEC's own `dedyn.io` names were
   suspended for new registrations on 29 Oct 2026.)

## 7. Staying at RM0 (guardrails)

| Service | Guardrail |
|---|---|
| Oracle Cloud | Stay on the Free Tier account (don't upgrade to Pay As You Go during testing); create only Always Free shapes; check the cost dashboard monthly. Keep memory use above 20 % so the free VM isn't reclaimed as idle (08 §6). |
| Firebase | Stay on the Spark plan (no billing account, so charges are impossible). Use only Google, Microsoft and email-link sign-in — no SMS/phone sign-in. |
| TomTom | Restrict the key to our server; enforce a **daily request cap in code** below the free allowance; cache per road segment; when the cap is hit, route with typical speeds and show "no live traffic" (NFR-05). `[VERIFY]` the current free allowance under TomTom's July 2026 pricing. |
| GitHub | Keep CI jobs lean (path-filtered, cached Gradle) to stay inside free Actions minutes `[VERIFY]` for private repos, or make the repo public. |
| Data | OSM, Overture and LLM portal data are free; follow 05 §3 crawl etiquette. |
| Avoid | Anything that asks for a card "just in case" (paid map APIs, paid email services, paid crash tools). |

### 7.1 Diagnostic drive logs (testing only)
- Opt-in per tester, off by default. Records route, GPS, speed and app events for debugging guidance and tolls.
- The first and last ~500 m and privacy zones are trimmed on the phone before upload; uploaded when the drive ends.
- Stored on our Malaysian VM, deleted after **30 days**; never used for traffic. Adopted from LAPAN (12 §2.2).

## 8. Tester onboarding guide (send this to each tester)

**One-time setup on your Android phone**
1. Open the invite email from Firebase App Distribution and follow the link (or install the **App Tester** app it
   suggests).
2. When Android asks, allow installing apps from that source ("Install unknown apps").
3. If Play Protect warns "unknown developer", tap **More details → Install anyway**. This is expected for test apps.
4. Open the app, allow **location** ("While using the app") and **notifications**.

**One-time setup for Android Auto**
5. Open **Android Auto settings** on the phone (Settings → Connected devices → Android Auto, or the Android Auto app).
6. Scroll to **Version** and tap it about 10 times until developer settings unlock; confirm.
7. Open the menu (⋮) → **Developer settings** → turn on **Unknown sources**.
8. Connect to your car. The app should appear in the Android Auto launcher. If it doesn't, customise the launcher
   in Android Auto settings and enable it.

**After updates**
- New versions arrive through App Tester. If the app disappears from the car after an Android Auto update, repeat
  step 7 — the setting sometimes resets.

**If the app can't connect**
- If you use NextDNS, AdGuard or another DNS filter, allow `jalanz.duckdns.org` or turn off blocking of
  "dynamic DNS hostnames".

**Reporting problems**
- Use the in-app "Send feedback" (crash reports are sent automatically without your location).

## 9. Limitations and when to switch to the main plan

| Limitation | Effect |
|---|---|
| Max 20 devices once verification applies (limited distribution account) | Fine for 10 testers; not for public use |
| Each tester must enable Android Auto developer settings | One-time friction; the setting can reset |
| Updates not automatic like Play (needs App Tester/Obtainium) | Testers must accept updates |
| DuckDNS subdomain | Less trustworthy links; no SLA |
| No Play review | Quality issues found only at launch time — keep following 06 §4 rules now |

Switch to the main plan (08 §5, 10 §13 public launch tasks) when going beyond ~20 devices or launching publicly:
pay the US$25 Play fee, buy a domain, migrate share links (old DuckDNS standing links should redirect for a while),
and submit for Android Auto review.

## Sources
- Android developer verification: https://support.google.com/android-developer-console/answer/16561738
- Malaysia timing and limited distribution accounts: https://productnation.co/my/31699/android-developer-verification-asean-malaysia-IjA0/
- Android Auto sideloading (unknown sources): https://www.androidauthority.com/sideload-apps-on-android-auto-3681820/ ;
  https://support.magicearth.com/support/solutions/articles/205000048537
- Settings resetting after updates: https://aaadapp.netlify.app/blog/android-auto-apps-disappeared
