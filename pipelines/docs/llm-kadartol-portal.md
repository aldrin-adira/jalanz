# LLM toll-rate portal (kadartol.llm.gov.my) — how it serves data

Investigated 30 Oct 2026 for WP 1.7 (source DS-04, spec 05 §1 and §3). Re-check before relying on it.

## Summary
The old portal host is unreachable, but the **same toll calculator lives on the LLM home page**
(`https://www.llm.gov.my/`, panel "Kalkulator Tol") and is backed by a small **JSON API**. The crawler uses that
API. The archived WebForms page below is kept for reference only.

## Live calculator on www.llm.gov.my (what the crawler uses, checked 30 Oct 2026)
The home page script `assets/js/landing.min.js` calls these paths with plain `GET` requests (relative to
`https://www.llm.gov.my`). No login, no CAPTCHA, no API key, no cookie needed (the server sets a PHP session
cookie that the calls do not use). Server: IIS + CodeIgniter.

| Call | Returns |
|---|---|
| `/Calculator/getHighway` | `[{"id": "", "name": "Sila Pilih Lebuhraya"}, {"id": 1, "name": "Lebuhraya Utara-Selatan (PLUS)"}, ...]`, 33 highways |
| `/Calculator/getPlazaEnter/{highway}` | entry plazas, e.g. `{"id": 212, "name": "SIMPANG PULAI"}` |
| `/Calculator/getPlazaExit/{highway}/{entry}` | exit plazas for that entry; **only the placeholder row = open-system plaza** |
| `/Calculator/getTollRate/{highway}/{entry}/{exit}` | `[{"transCode": 1, "rate": 15.25, "distance": 136.5}, {"transCode": 2, "rate": 27.9, "distance": 0}, ...]` |
| `/Calculator/getTollRate/{highway}/{entry}/null` | open-system fare, `distance` null |

- Bodies are UTF-8 JSON **with a byte-order mark**; the first list row is a "Sila Pilih ..." placeholder.
- IDs are numbers or zero-padded strings (LDP: `"010"`, `"020"`), so they are kept as strings.
- `rate` is RM as a JSON number; `transCode` is the class (1–5). Only the first row carries the distance.
- The script uses a different class legend for highway id 5 (Penang Bridge, codes 1–7), so those rows get class
  scheme `PENANG_BRIDGE` `[VERIFY]` the codes when Penang Bridge is mapped.
- A malformed path (e.g. a missing segment) returns an HTML CodeIgniter error page with status 200; the parser
  rejects anything that is not the expected JSON.
- Check against fixture F1: `getTollRate/1/212/134` (PLUS, Simpang Pulai → Juru) = RM 15.25 / 27.90 / 37.10 /
  7.63 / 11.40 and 136.5 km, exactly fixture F1.
- `www.llm.gov.my/robots.txt` disallows `/index.php/*`, `/search`, `/corporate_info/`, `/doc/` and spam patterns
  (`*pg*`, `*slot*`), not `/Calculator/`. The site has a privacy policy, a copyright notice and a disclaimer, but no
  terms forbidding automated access. The reCAPTCHA on the home page belongs to the (now disabled) CCTV panel, not
  the calculator. Still no open-data licence (05 DS-04 `[VERIFY]`).
- Quirks seen: LEKAS lists Ampangan as a pair with itself (RM2.30, no distance), a flat-fare plaza inside a closed
  highway; some closed pairs are not symmetric (LEKAS class 2 Pajam → Mantin RM2.27, reverse RM2.77).

## Reachability of the old host (30 Oct 2026)
- `kadartol.llm.gov.my` is a CNAME for `llmhaqs01.llm.gov.my` (110.159.225.160).
- From the developer's PC in Malaysia, TCP connections to ports 80 and 443 **time out**; from an overseas
  fetcher the connection is **refused**. `www.llm.gov.my` (same /24 network) answers normally, so the portal host
  itself appears to be down or firewalled. The LLM home page no longer links to the portal.
- The newest Internet Archive capture of the portal is from **9 March 2024**. Nothing newer is archived, so the
  portal may have been offline for a long time `[VERIFY]` (ask LLM's Bahagian Pengawasan Tol, 03-8738 3067).

## Rules for automated access
- Old portal `robots.txt`: **404** in the archived captures (2015). The crawler reads the robots.txt of the host it
  actually uses (www.llm.gov.my) before every run and stops if it disallows us.
- Old portal: no login, no CAPTCHA, no terms-of-use text on the page (2022–2024 captures). The footer only gives an enquiry
  phone number. `www.llm.gov.my` uses reCAPTCHA on its own forms, but not on this portal. If either appears on the
  portal, the crawler stops (spec 05 §3).
- No open-data licence (spec 05 DS-04 `[VERIFY]`): fine for the owner's personal use; the crawled data is never
  committed.

## Old portal page structure (archived 2022-11 to 2024-03, for reference)
An ASP.NET WebForms page (`/` = `Default.aspx`) with full-page postbacks. `Sys.WebForms.PageRequestManager` is
initialised with no update panels, so every response is a complete HTML page. There is **no JSON endpoint**.

| Field | Meaning |
|---|---|
| `ctl00$MainContent$ASPxComboBox1` | Highway (`<select>`, 32 options in 2024, e.g. `1` = PLUS). `onchange` posts back with `__EVENTTARGET` = this field to reload the plaza lists |
| `ctl00$MainContent$ASPxComboBox2` | Entry plaza (`Pilih Plaza Masuk`), values are numeric plaza IDs (`212` = SIMPANG PULAI) |
| `ctl00$MainContent$ASPxComboBox3` | Exit plaza (`Pilih Plaza Keluar`), not filtered by the entry plaza |
| `ctl00$MainContent$btnSubmit` = `CARI` | Submit; the result is rendered in the same page |
| `__VIEWSTATE`, `__VIEWSTATEGENERATOR`, `__EVENTVALIDATION`, `__EVENTTARGET`, `__EVENTARGUMENT`, `__LASTFOCUS` | WebForms state; each POST must send back the values from the previous response |

The result is a table `#MainContent_grid_kadar_tol`:

```text
row 1  <th> route code span (E1) + highway name     "E1 LEBUHRAYA UTARA-SELATAN (PLUS)"
row 2  <th> "<ENTRY> - <EXIT>"                       "SIMPANG PULAI - JURU"
row 3  header KENDERAAN | KELAS | KADAR TOL
rows   class image | class 1..5 | "RM 15.25"
row    "JARAK 136.50 KM"
row    "PASTIKAN BAKI KAD TOUCH N GO & RFID ANDA MENCUKUPI"
```

The plain `GET /` already shows a default result (first highway, first entry, first exit). The 9 March 2024 capture
shows exactly fixture F1: PLUS, Simpang Pulai → Juru, classes 1–5 = RM 15.25 / 27.90 / 37.10 / 7.63 / 11.40,
136.50 km.

Plaza names carry direction hints such as `(U)` (utara, northbound) and `(S)` (selatan, southbound), with
inconsistent spacing (`BUKIT TAMBUN(S)` vs `BUKIT TAMBUN (U)`, `IPOH(U)`). The PLUS entry list includes plazas of
other highways in the same closed network (ELITE, NKVE, e.g. `PUTRA HEIGHTS`, `KLIA`).

## Open questions `[VERIFY]`
- Open-system highways: answered by the live API (empty exit list, fare with exit `null`).
- Whether the plaza IDs are stable between portal updates. Fares are keyed by these IDs; names go through aliases.
- What the API returns for a pair with no fare; the parser treats an empty or non-JSON body as a parse error,
  which becomes a review warning, never a fare.

## How the crawler reads it
See `pipelines/README.md`. In short: `GET /robots.txt` → `getHighway` → per highway `getPlazaEnter` → per entry
`getPlazaExit` → one `getTollRate` per pair (or one per open plaza), at least 3 s apart, User-Agent
`Jalanz/0.0.1 (+https://github.com/aldrin-adira/jalanz)`. Every response is stored as a raw snapshot under the data
directory, outside the repository, before it is parsed. A full PLUS crawl is several thousand requests (hours at
3 s), so runs can be limited and resumed.
