"""Parsers for the LLM calculator JSON responses.

Response shapes seen on 30 Oct 2026 (UTF-8 with a byte-order mark):
- getHighway, getPlazaEnter/{hw}, getPlazaExit/{hw}/{entry}:
  ``[{"id": "", "name": "Sila Pilih ..."}, {"id": 1, "name": "..."}, ...]``.
  The first row is the select placeholder. IDs are ints or strings with
  leading zeros ("010"), so we keep them as strings.
- getTollRate/{hw}/{entry}/{exit | null}:
  ``[{"transCode": 1, "rate": 15.25, "distance": 136.5}, ...]``, rate in RM.
  Only the first row carries the distance (others 0 or null). An exit list
  with no real rows means an open-system plaza, priced with exit ``null``.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from decimal import Decimal

PARSER_VERSION = "llm-calculator-json/1"

# Highway 5 (Penang Bridge) uses its own class codes in the portal's script.
PENANG_BRIDGE_HIGHWAY_REF = "5"
MAX_AMOUNT_SEN = 50_000  # 04 §4.3 rule 1


class ParseError(ValueError):
    pass


@dataclass(frozen=True)
class Option:
    ref: str
    name: str


@dataclass(frozen=True)
class Rate:
    vehicle_class: str
    amount_sen: int


@dataclass(frozen=True)
class RateTable:
    rates: tuple[Rate, ...]
    distance_km: Decimal | None


def _load(body: bytes):
    try:
        return json.loads(body.decode("utf-8-sig"), parse_float=Decimal)
    except (UnicodeDecodeError, json.JSONDecodeError) as e:
        snippet = body[:80].decode("utf-8", "replace").strip()
        raise ParseError(f"not JSON ({e.__class__.__name__}): {snippet!r}") from e


def parse_options(body: bytes) -> list[Option]:
    """Parses a highway or plaza list, dropping the placeholder row."""
    data = _load(body)
    if not isinstance(data, list):
        raise ParseError("expected a list of options")
    out = []
    for row in data:
        if not isinstance(row, dict) or "id" not in row or "name" not in row:
            raise ParseError(f"unexpected option row: {row!r}")
        ref = str(row["id"]).strip()
        if ref:
            out.append(Option(ref, " ".join(str(row["name"]).split())))
    return out


def rm_to_sen(rm: Decimal | int) -> int:
    sen = Decimal(rm) * 100
    if sen != sen.to_integral_value():
        raise ParseError(f"fare {rm} RM is not a whole number of sen")
    return int(sen)


def parse_rates(body: bytes) -> RateTable:
    data = _load(body)
    if not isinstance(data, list) or not data:
        raise ParseError("expected a non-empty list of rates")
    rates, distance = [], None
    for row in data:
        try:
            code, rate = row["transCode"], row["rate"]
        except (TypeError, KeyError) as e:
            raise ParseError(f"unexpected rate row: {row!r}") from e
        if rate is None:
            continue
        rates.append(Rate(str(code), rm_to_sen(rate)))
        d = row.get("distance")
        if distance is None and d not in (None, 0):
            distance = Decimal(d)
    return RateTable(tuple(rates), distance)


def class_scheme(highway_ref: str) -> str:
    return "PENANG_BRIDGE" if highway_ref == PENANG_BRIDGE_HIGHWAY_REF else "LLM_1_5"
