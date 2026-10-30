"""Crawl orchestration: highways -> entry plazas -> exit plazas -> fares.

Requests per highway: 1 entry list, 1 exit list per entry, then 1 fare per
(entry, exit) pair, or 1 per entry on open highways. PLUS alone is several
thousand pairs, so runs can be limited (--entry/--exit/--max-requests) and
resumed: a resumed run reuses responses it already stored.
"""

from __future__ import annotations

import datetime as dt
import json
from dataclasses import dataclass, field
from typing import Callable

from . import MYT
from .parse import ParseError, parse_options
from .portal import PortalBlocked, PortalClient, PortalError, api_path
from .store import Store


@dataclass
class Scope:
    highways: list[str] | None = None  # None = every highway the portal lists
    entries: set[str] | None = None
    exits: set[str] | None = None

    def to_json(self) -> str:
        return json.dumps({k: sorted(v) if v is not None else None
                           for k, v in vars(self).items()})


class BudgetExhausted(Exception):
    pass


@dataclass
class CrawlResult:
    run_id: int
    status: str  # complete | partial | blocked | budget
    requests_sent: int
    failures: list[str] = field(default_factory=list)


def _iso(t: dt.datetime) -> str:
    return t.astimezone(MYT).isoformat(timespec="seconds")


class _Crawler:
    def __init__(self, client: PortalClient, store: Store, run_id: int, resume: bool,
                 max_requests: int | None, log: Callable[[str], None]):
        self.client, self.store, self.run_id = client, store, run_id
        self.resume, self.max_requests, self.log = resume, max_requests, log
        self.failures: list[str] = []

    def fetch(self, kind: str, path: str, hw: str = "", entry: str = "", exit_: str = "") -> bytes | None:
        url = self.client.base_url + path
        if self.resume:
            snap = self.store.find_ok_snapshot(self.run_id, url)
            if snap:
                return self.store.read_raw(snap["object_key"])
        if self.max_requests is not None and self.client.requests_sent >= self.max_requests:
            raise BudgetExhausted()
        refs = (hw, entry, exit_)
        try:
            fetched = self.client.get(path)
        except PortalBlocked as e:
            self.store.save_snapshot(self.run_id, kind, url, refs, None, _iso(dt.datetime.now(MYT)), str(e))
            raise
        except PortalError as e:
            self.store.save_snapshot(self.run_id, kind, url, refs, None, _iso(dt.datetime.now(MYT)), str(e))
            self.failures.append(f"{url}: {e}")
            return None
        error = None if fetched.status == 200 else f"HTTP {fetched.status}"
        self.store.save_snapshot(self.run_id, kind, url, refs, fetched, _iso(fetched.retrieved_at), error)
        if error:
            self.failures.append(f"{url}: {error}")
            return None
        return fetched.body

    def options(self, kind: str, path: str, hw: str = "", entry: str = "") -> list | None:
        body = self.fetch(kind, path, hw, entry)
        if body is None:
            return None
        try:
            return parse_options(body)
        except ParseError as e:
            self.failures.append(f"{path}: {e}")
            return None

    def run(self, scope: Scope) -> None:
        highways = self.options("highways", api_path("getHighway"))
        if highways is None:
            raise PortalError("could not read the highway list")
        known = {h.ref: h.name for h in highways}
        wanted = scope.highways if scope.highways is not None else list(known)
        for hw in wanted:
            if hw not in known:
                self.failures.append(f"highway {hw} is not listed by the portal")
                continue
            self.log(f"highway {hw}: {known[hw]}")
            self.crawl_highway(hw, scope)

    def crawl_highway(self, hw: str, scope: Scope) -> None:
        entries = self.options("plaza_enter", api_path("getPlazaEnter", hw), hw)
        for entry in entries or []:
            if scope.entries is not None and entry.ref not in scope.entries:
                continue
            exits = self.options("plaza_exit", api_path("getPlazaExit", hw, entry.ref), hw, entry.ref)
            if exits is None:
                continue
            if not exits:  # open-system plaza: the portal prices it with exit "null"
                self.fetch("toll_rate", api_path("getTollRate", hw, entry.ref, "null"), hw, entry.ref)
                continue
            for ex in exits:
                if scope.exits is not None and ex.ref not in scope.exits:
                    continue
                self.fetch("toll_rate", api_path("getTollRate", hw, entry.ref, ex.ref), hw, entry.ref, ex.ref)
            self.log(f"  {entry.name}: {len(exits)} exits")


def crawl(client: PortalClient, store: Store, scope: Scope, *, resume_run: int | None = None,
          max_requests: int | None = None, log: Callable[[str], None] = print) -> CrawlResult:
    now = _iso(dt.datetime.now(MYT))
    run_id = resume_run if resume_run is not None else store.start_run(scope.to_json(), now)
    crawler = _Crawler(client, store, run_id, resume_run is not None, max_requests, log)
    status, note = "complete", None
    try:
        crawler.run(scope)
    except BudgetExhausted:
        status, note = "budget", f"stopped after {client.requests_sent} requests; resume with --resume {run_id}"
    except PortalBlocked as e:
        status, note = "blocked", str(e)
    except PortalError as e:
        status, note = "partial", str(e)
    if status == "complete" and crawler.failures:
        status = "partial"
    if crawler.failures and note is None:
        note = f"{len(crawler.failures)} failed requests"
    store.finish_run(run_id, status, _iso(dt.datetime.now(MYT)), note)
    return CrawlResult(run_id, status, client.requests_sent, crawler.failures)
