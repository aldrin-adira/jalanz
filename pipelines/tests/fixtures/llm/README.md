# Trimmed LLM calculator responses for parser tests

Captured from https://www.llm.gov.my/Calculator/... on 30 Oct 2026 and cut down to a few rows.
Only tiny samples live here: the crawled dataset is never committed (AGENTS.md §2).

- `rate_closed_F1.json`: getTollRate/1/212/134 (PLUS, Simpang Pulai -> Juru), the fare in fixture F1.
- `rate_open.json`: getTollRate/4/010/null (LDP, open-system plaza).
- `plaza_enter_trimmed.json`: placeholder row plus four plazas, mixing int and zero-padded string ids.
- `error.html`: the HTML error page the server returns for a malformed path (trimmed).
