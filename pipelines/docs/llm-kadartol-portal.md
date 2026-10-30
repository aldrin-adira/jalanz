# LLM toll-rate portal (kadartol.llm.gov.my) — how it serves data

Investigated 30 Oct 2026 for WP 1.7 (source DS-04, spec 05 §1 and §3). Re-check before relying on it.

## Reachability (30 Oct 2026)
- `kadartol.llm.gov.my` is a CNAME for `llmhaqs01.llm.gov.my` (110.159.225.160).
- From the developer's PC in Malaysia, TCP connections to ports 80 and 443 **time out**; from an overseas
  fetcher the connection is **refused**. `www.llm.gov.my` (same /24 network) answers normally, so the portal host
  itself appears to be down or firewalled. The LLM home page no longer links to the portal.
- The newest Internet Archive capture of the portal is from **9 March 2024**. Nothing newer is archived, so the
  portal may have been offline for a long time `[VERIFY]` (ask LLM's Bahagian Pengawasan Tol, 03-8738 3067).

## Rules for automated access
- `robots.txt`: **404** in the archived captures (2015), i.e. no crawl rules. The crawler fetches it again before
  every run and stops if it disallows us.
- No login, no CAPTCHA, no terms-of-use text on the page (2022–2024 captures). The footer only gives an enquiry
  phone number. `www.llm.gov.my` uses reCAPTCHA on its own forms, but not on this portal. If either appears on the
  portal, the crawler stops (spec 05 §3).
- No open-data licence (spec 05 DS-04 `[VERIFY]`): fine for the owner's personal use; the crawled data is never
  committed.

## Page structure (archived 2022-11 to 2024-03)
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

## Unknowns `[VERIFY]` when the portal is back
- How open-system highways (e.g. LDP, BESRAYA) are shown: same entry/exit form, or a single plaza? The parser
  treats a result whose entry and exit names are equal (or that has no ` - `) as an **open fare** `[ASSUMPTION]`.
- What the page shows for a pair with no fare (empty table, message, or error page). The parser treats a missing
  result table as "no fare".
- Whether the plaza IDs are stable between portal updates (the crawler keys fares by these IDs and reports a
  rename when the name for an ID changes).

## How the crawler reads it
See `pipelines/README.md`. In short: `GET /robots.txt` → `GET /` → for each highway, one postback to load its
plazas → one POST per entry/exit pair, at least 3 s apart, User-Agent
`Jalanz/0.0.1 (+https://github.com/aldrin-adira/jalanz)`. Every response is stored gzipped (raw snapshot) outside
the repository before it is parsed.
