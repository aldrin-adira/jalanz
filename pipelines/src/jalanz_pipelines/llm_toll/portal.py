"""Polite HTTP access to the LLM toll calculator (spec 05 §3).

Every request is checked against robots.txt, sent with an identifying
User-Agent and spaced at least ``delay_s`` seconds apart. Server errors are
retried with exponential back-off; 403/429 stop the crawl, because they may
mean we are not welcome and a human must ask for permission instead.
"""

from __future__ import annotations

import datetime as dt
import re
import time
from dataclasses import dataclass
from typing import Callable
from urllib.parse import quote

import requests

from . import MYT

USER_AGENT = "Jalanz/0.0.1 (+https://github.com/aldrin-adira/jalanz)"
ROBOTS_TOKEN = "jalanz"
BASE_URL = "https://www.llm.gov.my"
DEFAULT_DELAY_S = 3.0
TIMEOUT_S = 30


class PortalError(Exception):
    """A request failed after all retries."""


class PortalBlocked(PortalError):
    """robots.txt disallows the path, or the server refused us (403/429)."""


class RobotsRules:
    """robots.txt matcher with Google-style ``*`` and ``$`` wildcards.

    Python's urllib.robotparser treats ``*`` inside paths literally, and the
    LLM robots.txt uses wildcards (``Disallow: *pg*``), so we match ourselves.
    """

    def __init__(self, rules: list[tuple[bool, str]]):
        self._rules = [(allow, p, _pattern_regex(p)) for allow, p in rules if p]

    @classmethod
    def parse(cls, text: str, token: str = ROBOTS_TOKEN) -> "RobotsRules":
        groups: list[tuple[list[str], list[tuple[bool, str]]]] = []
        agents: list[str] = []
        rules: list[tuple[bool, str]] = []
        last_was_agent = False
        for raw in text.splitlines():
            line = raw.split("#", 1)[0].strip()
            if ":" not in line:
                continue
            field, value = (s.strip() for s in line.split(":", 1))
            field = field.lower()
            if field == "user-agent":
                if not last_was_agent and agents:
                    groups.append((agents, rules))
                    agents, rules = [], []
                agents.append(value.lower())
                last_was_agent = True
            elif field in ("allow", "disallow"):
                rules.append((field == "allow", value))
                last_was_agent = False
            else:
                last_was_agent = False
        if agents:
            groups.append((agents, rules))
        token = token.lower()
        named = [r for a, r in groups if any(x != "*" and x in token for x in a)]
        if named:
            return cls([rule for r in named for rule in r])
        return cls([rule for a, r in groups if "*" in a for rule in r])

    def allowed(self, path: str) -> bool:
        best: tuple[int, bool] | None = None
        for allow, pattern, regex in self._rules:
            if regex.match(path):
                key = (len(pattern), allow)
                if best is None or key > best:
                    best = key
        return True if best is None else best[1]


def _pattern_regex(pattern: str) -> re.Pattern[str]:
    if not pattern.startswith(("/", "*")):
        pattern = "/" + pattern
    anchored = pattern.endswith("$")
    body = re.escape(pattern.rstrip("$")).replace(r"\*", ".*")
    return re.compile(body + ("$" if anchored else ""))


@dataclass(frozen=True)
class Fetched:
    url: str
    status: int
    body: bytes
    retrieved_at: dt.datetime


class PortalClient:
    """Fetches calculator API paths politely. One instance per crawl."""

    def __init__(
        self,
        session: requests.Session | None = None,
        base_url: str = BASE_URL,
        delay_s: float = DEFAULT_DELAY_S,
        max_retries: int = 3,
        clock: Callable[[], float] = time.monotonic,
        sleep: Callable[[float], None] = time.sleep,
        now: Callable[[], dt.datetime] = lambda: dt.datetime.now(MYT),
    ):
        self.session = session or requests.Session()
        self.session.headers["User-Agent"] = USER_AGENT
        self.base_url = base_url.rstrip("/")
        self.delay_s = delay_s
        self.max_retries = max_retries
        self._clock, self._sleep, self._now = clock, sleep, now
        self._last_request: float | None = None
        self._robots: RobotsRules | None = None
        self.requests_sent = 0

    def _wait_turn(self) -> None:
        if self._last_request is not None:
            remaining = self.delay_s - (self._clock() - self._last_request)
            if remaining > 0:
                self._sleep(remaining)
        self._last_request = self._clock()

    def _send(self, url: str) -> requests.Response:
        self._wait_turn()
        self.requests_sent += 1
        return self.session.get(url, timeout=TIMEOUT_S)

    def robots(self) -> RobotsRules:
        if self._robots is None:
            resp = self._send(self.base_url + "/robots.txt")
            if resp.status_code in (401, 403):
                raise PortalBlocked(f"robots.txt returned {resp.status_code}")
            # No robots.txt (4xx) means everything is allowed; 5xx means don't crawl.
            if resp.status_code >= 500:
                raise PortalError(f"robots.txt returned {resp.status_code}")
            self._robots = RobotsRules.parse(resp.text if resp.ok else "")
        return self._robots

    def get(self, path: str) -> Fetched:
        if not self.robots().allowed(path):
            raise PortalBlocked(f"robots.txt disallows {path}")
        url = self.base_url + path
        for attempt in range(self.max_retries + 1):
            try:
                resp = self._send(url)
            except requests.RequestException as e:
                error = f"{type(e).__name__}: {e}"
            else:
                if resp.status_code in (403, 429):
                    raise PortalBlocked(f"{url} returned {resp.status_code}")
                if resp.status_code < 500:
                    return Fetched(url, resp.status_code, resp.content, self._now())
                error = f"HTTP {resp.status_code}"
            if attempt < self.max_retries:
                self._sleep(self.delay_s * 3 ** (attempt + 1))
        raise PortalError(f"{url} failed after {self.max_retries + 1} tries: {error}")


def api_path(endpoint: str, *parts: str) -> str:
    """Builds a calculator API path, e.g. ``/Calculator/getPlazaExit/1/212``."""
    return "/Calculator/" + "/".join([endpoint, *(quote(p, safe="") for p in parts)])
