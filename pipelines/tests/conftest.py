"""Shared test helpers: an in-memory fake of the LLM calculator API.

The fake network is made up (plazas ALPHA, BRAVO, CHARLIE); it is not
crawled data. Highway 90 is closed (entry -> exit fares), 91 is open.
"""

from __future__ import annotations

import json
from pathlib import Path

import pytest
import requests

from jalanz_pipelines.llm_toll.portal import PortalClient

FIXTURES = Path(__file__).parent / "fixtures" / "llm"
BASE = "https://portal.test"
ROBOTS = "User-agent: *\nDisallow: /index.php/*\nDisallow: *pg*\n"

PLAZAS_90 = {"1": "ALPHA", "2": "BRAVO", "3": "CHARLIE"}
PLAZAS_91 = {"010": "DELTA", "020": "ECHO (U)"}


def closed_fares(overrides: dict | None = None) -> dict:
    """Class 1 fares in sen per (entry, exit); other classes derived from it."""
    fares = {("1", "2"): 500, ("2", "1"): 500, ("2", "3"): 700, ("3", "2"): 700,
             ("1", "3"): 1200, ("3", "1"): 1200}
    fares.update(overrides or {})
    return fares


def _bom_json(data) -> bytes:
    return b"\xef\xbb\xbf" + json.dumps(data).encode()


def _options(placeholder: str, items: dict) -> bytes:
    return _bom_json([{"id": "", "name": placeholder}] + [{"id": k, "name": v} for k, v in items.items()])


def _rates(class1_sen: int, distance, multipliers=(1, 2, 3, 0.5, 1)) -> bytes:
    rows = []
    for i, m in enumerate(multipliers, start=1):
        sen = round(class1_sen * m)
        rows.append({"transCode": i, "rate": sen / 100, "distance": distance if i == 1 else 0})
    return _bom_json(rows)


class FakeResponse:
    def __init__(self, status: int, body: bytes):
        self.status_code, self.content = status, body
        self.ok = status < 400
        self.text = body.decode("utf-8-sig", "replace")


class FakeSession:
    """Serves the fake network; ``responses`` maps path -> status/body overrides."""

    def __init__(self, fares: dict | None = None, open_sen: dict | None = None, robots: str = ROBOTS):
        self.headers: dict[str, str] = {}
        self.calls: list[str] = []
        self.fares = closed_fares() if fares is None else fares
        self.open_sen = open_sen or {"010": 210, "020": 160}
        self.robots = robots
        self.responses: dict[str, list[tuple[int, bytes] | Exception]] = {}

    def get(self, url: str, timeout=None):
        path = url.removeprefix(BASE)
        self.calls.append(path)
        queued = self.responses.get(path)
        if queued:
            item = queued.pop(0)
            if isinstance(item, Exception):
                raise item
            return FakeResponse(*item)
        return FakeResponse(*self.route(path))

    def route(self, path: str) -> tuple[int, bytes]:
        if path == "/robots.txt":
            return 200, self.robots.encode()
        parts = path.split("/")[2:]  # after /Calculator/
        name, args = parts[0], parts[1:]
        if name == "getHighway":
            return 200, _options("Sila Pilih Lebuhraya", {90: "Lebuhraya Ujian Tertutup", 91: "Lebuhraya Ujian Terbuka"})
        if name == "getPlazaEnter":
            return 200, _options("Sila Pilih Plaza Masuk", PLAZAS_90 if args[0] == "90" else PLAZAS_91)
        if name == "getPlazaExit":
            if args[0] == "91":
                return 200, _options("Sila Pilih Plaza Keluar", {})
            exits = {x: PLAZAS_90[x] for (e, x) in self.fares if e == args[1]}
            return 200, _options("Sila Pilih Plaza Keluar", dict(sorted(exits.items())))
        if name == "getTollRate":
            hw, entry, exit_ = args
            if hw == "91":
                return 200, _rates(self.open_sen[entry], None)
            return 200, _rates(self.fares[(entry, exit_)], 10.5)
        return 404, b"not found"


@pytest.fixture
def make_client():
    def make(session: FakeSession) -> PortalClient:
        sleeps: list[float] = []
        client = PortalClient(session=session, base_url=BASE, delay_s=3.0,
                              clock=lambda: 0.0, sleep=sleeps.append)
        client.sleeps = sleeps
        return client
    return make


@pytest.fixture
def store(tmp_path):
    from jalanz_pipelines.llm_toll.store import Store
    s = Store(tmp_path / "data")
    yield s
    s.close()


def network_error() -> Exception:
    return requests.ConnectionError("connection reset")
