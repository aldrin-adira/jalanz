# Security Policy

Jalanz handles location data and end-to-end encrypted live sharing, so security reports matter.

## Reporting a vulnerability

Please **do not open a public issue**. Report privately through GitHub:
**Security → Report a vulnerability** on this repository (GitHub private vulnerability reporting).

Include what you found, steps to reproduce, and the affected component (Android app, Android Auto, backend,
share relay, share page, pipelines).

## What to expect
- Acknowledgement within 7 days.
- A fix or mitigation plan agreed with you before any public disclosure.
- Credit in the release notes if you want it.

## Especially in scope
- Anything that lets the server, a third party or another viewer read shared locations, names or ETAs
  (`spec/07-location-sharing.md` §4).
- Ways to start, extend or view someone's sharing without their action.
- Leaks of location data into logs, crash reports or push payloads.
- Wrong toll amounts caused by tampered or injected data.

Jalanz is in early testing and has no bug bounty.
