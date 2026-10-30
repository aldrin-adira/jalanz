"""Change sets: diff a crawl run against approved fares, validate, approve.

Follows 04 §4: parse the run's raw snapshots, match plaza names to our
plaza_id through aliases, diff against the fares currently in effect,
apply the 04 §4.3 validation rules, and route anything that changed or
warned to human review. Approving writes effective-dated rows and closes
the old ones (04 §4.2); history is never deleted.
"""

from __future__ import annotations

import datetime as dt
import json
from collections import defaultdict

from . import MYT, SOURCE_ID
from .aliases import normalise
from .parse import MAX_AMOUNT_SEN, PARSER_VERSION, ParseError, class_scheme, parse_options, parse_rates
from .store import Store

MAX_LISTED_PER_RULE = 200
SYMMETRY_SEN = 10
TRIANGLE_SEN = 50
CHANGE_RATIO = 0.20
NETWORK_CHANGE_SHARE = 0.25
NOT_CHECKED = [
    "4 distance vs OSM path: needs the plaza-to-OSM mapping (WP 1.8)",
    "7 cross-source: no second source (S2 concessionaire calculators) yet",
    "8 discount events: entered by a curator, not crawled",
]
KEY = ("highway_ref", "entry_ref", "exit_ref", "vehicle_class")


class ChangeSetError(Exception):
    pass


def _join(a: str, b: str, cols=KEY) -> str:
    return " AND ".join(f"{a}.{c} = {b}.{c}" for c in cols)


def insert_many(store: Store, table: str, rows: list[tuple], chunk: int = 500) -> None:
    """Multi-row INSERTs; much faster in DuckDB than executemany."""
    if not rows:
        return
    width = len(rows[0])
    one = "(" + ", ".join("?" * width) + ")"
    for i in range(0, len(rows), chunk):
        part = rows[i:i + chunk]
        store.con.execute(f"INSERT INTO {table} VALUES " + ", ".join([one] * len(part)),
                          [v for row in part for v in row])


def _now_iso() -> str:
    return dt.datetime.now(MYT).isoformat(timespec="seconds")


class _Issues:
    def __init__(self):
        self.errors: list[dict] = []
        self.warnings: list[dict] = []
        self.counts: dict[str, int] = defaultdict(int)

    def add(self, rule: str, message: str, error: bool = False) -> None:
        self.counts[rule] += 1
        target = self.errors if error else self.warnings
        if self.counts[rule] <= MAX_LISTED_PER_RULE:
            target.append({"rule": rule, "message": message})

    def to_json(self) -> str:
        return json.dumps({"errors": self.errors, "warnings": self.warnings, "counts": dict(self.counts)},
                          ensure_ascii=False)


def _read_run(store: Store, run_id: int, issues: _Issues):
    """Parses every stored response of a run (re-runnable without re-fetching)."""
    snaps = store.query_dicts(
        "SELECT * FROM source_snapshot WHERE run_id = ? ORDER BY snapshot_id", [run_id])
    if not snaps:
        raise ChangeSetError(f"run {run_id} has no snapshots")
    latest: dict[str, dict] = {}
    for s in snaps:
        if s["http_status"] == 200 or s["url"] not in latest:
            latest[s["url"]] = s
    hw_names, entry_names, exit_names = {}, defaultdict(dict), {}
    listed, entries = set(), set()
    rate_snaps = []
    for s in latest.values():
        if s["http_status"] != 200:
            issues.add("fetch", f"{s['url']}: {s['error']}")
            continue
        if s["kind"] == "toll_rate":
            rate_snaps.append(s)
            continue
        try:
            opts = parse_options(store.read_raw(s["object_key"]))
        except ParseError as e:
            issues.add("parse", f"{s['url']}: {e}")
            continue
        hw, entry = s["highway_ref"], s["entry_ref"]
        if s["kind"] == "highways":
            hw_names.update({o.ref: o.name for o in opts})
        elif s["kind"] == "plaza_enter":
            entry_names[hw].update({o.ref: o.name for o in opts})
        elif s["kind"] == "plaza_exit":
            entries.add((hw, entry))
            exit_names[(hw, entry)] = {o.ref: o.name for o in opts}
            listed.update({(hw, entry, o.ref) for o in opts} if opts else {(hw, entry, "")})
    observations = []
    for s in rate_snaps:
        hw, entry, exit_ = s["highway_ref"], s["entry_ref"], s["exit_ref"]
        try:
            table = parse_rates(store.read_raw(s["object_key"]))
        except ParseError as e:
            issues.add("parse", f"{s['url']}: {e}")
            continue
        system = "open" if exit_ == "" else "closed"
        entry_name = entry_names[hw].get(entry, entry)
        exit_name = exit_names.get((hw, entry), {}).get(exit_, exit_) if exit_ else ""
        for r in table.rates:
            observations.append((
                run_id, s["snapshot_id"], SOURCE_ID, s["retrieved_at"], hw, hw_names.get(hw, hw), system,
                class_scheme(hw), entry, entry_name, exit_, exit_name, r.vehicle_class, r.amount_sen,
                table.distance_km if system == "closed" else None))
    return observations, listed, entries


def build_change_set(store: Store, run_id: int) -> int:
    """Builds (or rebuilds) the change set for a crawl run and returns its id."""
    issues = _Issues()
    observations, listed, entries = _read_run(store, run_id, issues)
    con = store.con
    con.execute("DELETE FROM fare_observation WHERE run_id = ?", [run_id])
    insert_many(store, "fare_observation", observations)
    cs_id = store.next_id("seq_change_set")

    con.execute("CREATE OR REPLACE TEMP TABLE run_listed (highway_ref TEXT, entry_ref TEXT, exit_ref TEXT)")
    con.execute("CREATE OR REPLACE TEMP TABLE run_entries (highway_ref TEXT, entry_ref TEXT)")
    insert_many(store, "run_listed", sorted(listed))
    insert_many(store, "run_entries", sorted(entries))

    con.execute(f"""
        INSERT INTO change_item
        SELECT ?, CASE WHEN f.amount_sen IS NULL THEN 'added'
                       WHEN f.amount_sen <> o.amount_sen
                         OR f.distance_km IS DISTINCT FROM o.distance_km THEN 'changed'
                       ELSE 'unchanged' END,
               o.highway_ref, o.highway_name, o.system_type, o.class_scheme, o.entry_ref, o.exit_ref,
               o.entry_name, o.exit_name, NULL, NULL, o.vehicle_class, f.amount_sen, o.amount_sen,
               o.distance_km, o.snapshot_id, o.retrieved_at
        FROM fare_observation o
        LEFT JOIN fare f ON f.source_id = o.source_id AND f.effective_to IS NULL AND {_join('f', 'o')}
        WHERE o.run_id = ?""", [cs_id, run_id])
    pair = ("highway_ref", "entry_ref", "exit_ref")
    con.execute(f"""
        INSERT INTO change_item
        SELECT ?, 'removed', f.highway_ref, f.highway_name, f.system_type, f.class_scheme, f.entry_ref,
               f.exit_ref, f.entry_name, f.exit_name, NULL, NULL, f.vehicle_class, f.amount_sen, NULL,
               f.distance_km, NULL, NULL
        FROM fare f JOIN run_entries e ON {_join('f', 'e', ('highway_ref', 'entry_ref'))}
        WHERE f.source_id = ? AND f.effective_to IS NULL AND (
          NOT EXISTS (SELECT 1 FROM run_listed l WHERE {_join('l', 'f', pair)})
          OR (EXISTS (SELECT 1 FROM fare_observation o WHERE o.run_id = ? AND {_join('o', 'f', pair)})
              AND NOT EXISTS (SELECT 1 FROM fare_observation o WHERE o.run_id = ? AND {_join('o', 'f')})))
        """, [cs_id, SOURCE_ID, run_id, run_id])

    unmatched = _match_aliases(store, cs_id)
    for hw, name in unmatched:
        issues.add("unmatched_plaza", f"highway {hw}: no plaza_id for '{name}'")
    _validate(store, run_id, cs_id, issues)

    counts = dict(store.query(
        "SELECT change, count(*) FROM change_item WHERE change_set_id = ? GROUP BY change", [cs_id]))
    run = store.query_dicts("SELECT * FROM crawl_run WHERE run_id = ?", [run_id])[0]
    summary = {
        "run_id": run_id, "run_status": run["status"], "parser_version": PARSER_VERSION,
        "highways": sorted({o[4] for o in observations}),
        "counts": {k: counts.get(k, 0) for k in ("added", "changed", "removed", "unchanged")},
        "effective_from_inferred": True, "not_checked": NOT_CHECKED,
    }
    quiet = (not issues.errors and not issues.warnings and run["status"] == "complete"
             and not any(summary["counts"][k] for k in ("added", "changed", "removed")))
    status = "auto_confirmed" if quiet else "pending"
    con.execute("INSERT INTO change_set VALUES (?, ?, ?, ?, ?, ?, ?, NULL, NULL)",
                [cs_id, _now_iso(), SOURCE_ID, run_id, status, json.dumps(summary), issues.to_json()])
    if status == "auto_confirmed":
        _mark_verified(store, cs_id)
    return cs_id


def _match_aliases(store: Store, cs_id: int) -> list[tuple[str, str]]:
    aliases = store.alias_map()
    names = store.query(
        "SELECT DISTINCT highway_ref, entry_name FROM change_item WHERE change_set_id = ? "
        "UNION SELECT DISTINCT highway_ref, exit_name FROM change_item WHERE change_set_id = ? AND exit_ref <> ''",
        [cs_id, cs_id])
    mapping, unmatched = [], []
    for hw, name in names:
        plaza_id = aliases.get((hw, normalise(name)))
        if plaza_id:
            mapping.append((hw, name, plaza_id))
        else:
            unmatched.append((hw, name))
    store.con.execute("CREATE OR REPLACE TEMP TABLE name_map (highway_ref TEXT, name TEXT, plaza_id TEXT)")
    insert_many(store, "name_map", mapping)
    for col in ("entry", "exit"):
        store.con.execute(f"""
            UPDATE change_item SET {col}_plaza_id = m.plaza_id FROM name_map m
            WHERE change_item.change_set_id = ? AND m.highway_ref = change_item.highway_ref
              AND m.name = change_item.{col}_name""", [cs_id])
    return sorted(unmatched)


def _validate(store: Store, run_id: int, cs_id: int, issues: _Issues) -> None:
    q = store.query
    obs = "(SELECT * FROM fare_observation WHERE run_id = ? AND system_type = 'closed')"
    # Rule 1: hard error.
    for hw, en, xn, c, amt in q(
            "SELECT highway_ref, entry_name, exit_name, vehicle_class, amount_sen FROM fare_observation "
            "WHERE run_id = ? AND (amount_sen < 0 OR amount_sen >= ?)", [run_id, MAX_AMOUNT_SEN]):
        issues.add("1_amount_range", f"highway {hw} {en} -> {xn or '(open)'} class {c}: {amt} sen", error=True)
    # Rule 2: closed matrix symmetry.
    for hw, en, xn, c, ab, ba in q(f"""
            SELECT a.highway_ref, a.entry_name, a.exit_name, a.vehicle_class, a.amount_sen, b.amount_sen
            FROM {obs} a JOIN {obs} b ON a.highway_ref = b.highway_ref AND a.entry_ref = b.exit_ref
              AND a.exit_ref = b.entry_ref AND a.vehicle_class = b.vehicle_class
            WHERE a.entry_ref < a.exit_ref AND abs(a.amount_sen - b.amount_sen) > ?
            ORDER BY 1, 2, 3, 4""", [run_id, run_id, SYMMETRY_SEN]):
        issues.add("2_symmetry", f"highway {hw} class {c}: {en} -> {xn} {ab} sen, reverse {ba} sen")
    # Rule 3: triangle.
    for hw, c, an, bn, cn, ac, ab, bc in q(f"""
            SELECT ac.highway_ref, ac.vehicle_class, ac.entry_name, ab.exit_name, ac.exit_name,
                   ac.amount_sen, ab.amount_sen, bc.amount_sen
            FROM {obs} ac
            JOIN {obs} ab ON ab.highway_ref = ac.highway_ref AND ab.vehicle_class = ac.vehicle_class
              AND ab.entry_ref = ac.entry_ref AND ab.exit_ref <> ac.exit_ref
            JOIN {obs} bc ON bc.highway_ref = ac.highway_ref AND bc.vehicle_class = ac.vehicle_class
              AND bc.entry_ref = ab.exit_ref AND bc.exit_ref = ac.exit_ref
            WHERE ac.amount_sen > ab.amount_sen + bc.amount_sen + ?
            ORDER BY 1, 2, 3, 4, 5""", [run_id, run_id, run_id, TRIANGLE_SEN]):
        issues.add("3_triangle", f"highway {hw} class {c}: {an} -> {cn} {ac} sen > "
                                 f"{an} -> {bn} {ab} + {bn} -> {cn} {bc} sen")
    # Rule 5: change magnitude, per fare and per network (highway).
    for hw, en, xn, c, old, new in q(
            "SELECT highway_ref, entry_name, exit_name, vehicle_class, old_amount_sen, new_amount_sen "
            "FROM change_item WHERE change_set_id = ? AND change = 'changed' AND old_amount_sen <> new_amount_sen "
            "AND (old_amount_sen = 0 OR abs(new_amount_sen - old_amount_sen) > ? * old_amount_sen) "
            "ORDER BY 1, 2, 3, 4", [cs_id, CHANGE_RATIO]):
        issues.add("5_change_magnitude", f"highway {hw} {en} -> {xn or '(open)'} class {c}: {old} -> {new} sen")
    for hw, changed, total in q("""
            SELECT f.highway_ref, count(DISTINCT CASE WHEN i.change IN ('changed', 'removed')
                                       THEN f.entry_ref || '>' || f.exit_ref END),
                   count(DISTINCT f.entry_ref || '>' || f.exit_ref)
            FROM fare f LEFT JOIN change_item i ON i.change_set_id = ? AND i.change IN ('changed', 'removed')
              AND i.highway_ref = f.highway_ref AND i.entry_ref = f.entry_ref AND i.exit_ref = f.exit_ref
              AND i.vehicle_class = f.vehicle_class
            WHERE f.source_id = ? AND f.effective_to IS NULL GROUP BY 1 ORDER BY 1""", [cs_id, SOURCE_ID]):
        if total and changed > NETWORK_CHANGE_SHARE * total:
            issues.add("5_network_share", f"highway {hw}: {changed} of {total} pairs changed")
    # Rule 6: class ordering 1 <= 2 <= 3 on the standard LLM scheme.
    for hw, en, xn, c1, c2, c3 in q("""
            SELECT highway_ref, entry_name, exit_name,
                   max(amount_sen) FILTER (WHERE vehicle_class = '1'),
                   max(amount_sen) FILTER (WHERE vehicle_class = '2'),
                   max(amount_sen) FILTER (WHERE vehicle_class = '3')
            FROM fare_observation WHERE run_id = ? AND class_scheme = 'LLM_1_5'
            GROUP BY 1, 2, 3, entry_ref, exit_ref
            HAVING max(amount_sen) FILTER (WHERE vehicle_class = '2') < max(amount_sen) FILTER (WHERE vehicle_class = '1')
                OR max(amount_sen) FILTER (WHERE vehicle_class = '3') < max(amount_sen) FILTER (WHERE vehicle_class = '2')
            ORDER BY 1, 2, 3""", [run_id]):
        issues.add("6_class_order", f"highway {hw} {en} -> {xn or '(open)'}: class 1/2/3 = {c1}/{c2}/{c3} sen")


def _mark_verified(store: Store, cs_id: int) -> None:
    store.con.execute(f"""
        UPDATE fare SET verified_at = i.retrieved_at FROM change_item i
        WHERE i.change_set_id = ? AND i.change = 'unchanged' AND fare.source_id = ?
          AND fare.effective_to IS NULL AND {_join('fare', 'i')}""", [cs_id, SOURCE_ID])


def get_change_set(store: Store, cs_id: int) -> dict:
    rows = store.query_dicts("SELECT * FROM change_set WHERE change_set_id = ?", [cs_id])
    if not rows:
        raise ChangeSetError(f"change set {cs_id} not found")
    cs = rows[0]
    cs["summary"] = json.loads(cs["summary"])
    cs["warnings"] = json.loads(cs["warnings"])
    return cs


def _start_of_day(iso: str) -> dt.datetime:
    t = dt.datetime.fromisoformat(iso).astimezone(MYT)
    return t.replace(hour=0, minute=0, second=0, microsecond=0)


def approve(store: Store, cs_id: int, reviewer: str, effective_from: dt.datetime | None = None) -> dict:
    """Writes the change set into the effective-dated fare table.

    effective_from defaults to the start of the crawl day in MYT and is then
    flagged as inferred (04 §4.2); pass the announced date when known.
    """
    cs = get_change_set(store, cs_id)
    if cs["status"] != "pending":
        raise ChangeSetError(f"change set {cs_id} is {cs['status']}, not pending")
    if cs["warnings"]["errors"]:
        raise ChangeSetError(f"change set {cs_id} has hard errors; reject it and fix the source")
    run = store.query_dicts("SELECT started_at FROM crawl_run WHERE run_id = ?", [cs["run_id"]])[0]
    inferred = effective_from is None
    eff = (effective_from or _start_of_day(run["started_at"])).astimezone(MYT).isoformat(timespec="seconds")
    con, q = store.con, store.query
    current = f"fare.source_id = ? AND fare.effective_to IS NULL AND {_join('fare', 'i')}"
    con.begin()
    try:
        stale = q(f"""
            SELECT count(*) FROM change_item i LEFT JOIN fare ON {current}
            WHERE i.change_set_id = ? AND (
              (i.change IN ('changed', 'removed') AND fare.amount_sen IS DISTINCT FROM i.old_amount_sen)
              OR (i.change = 'added' AND fare.amount_sen IS NOT NULL))""", [SOURCE_ID, cs_id])[0][0]
        if stale:
            raise ChangeSetError(f"{stale} items no longer match the current fares; rebuild the change set")
        too_early = q(f"""
            SELECT count(*) FROM change_item i JOIN fare ON {current}
            WHERE i.change_set_id = ? AND i.change IN ('changed', 'removed') AND fare.effective_from >= ?""",
                      [SOURCE_ID, cs_id, eff])[0][0]
        if too_early:
            raise ChangeSetError(f"effective_from {eff} is not after {too_early} current rows; pass a later date")
        con.execute(f"""
            UPDATE fare SET effective_to = ? FROM change_item i
            WHERE i.change_set_id = ? AND i.change IN ('changed', 'removed') AND {current}""",
                    [eff, cs_id, SOURCE_ID])
        con.execute("""
            INSERT INTO fare
            SELECT ?, i.highway_ref, i.highway_name, i.system_type, i.class_scheme, i.entry_ref, i.exit_ref,
                   i.entry_name, i.exit_name, i.vehicle_class, i.new_amount_sen, i.distance_km, ?, NULL, ?,
                   i.snapshot_id, i.retrieved_at, i.retrieved_at, i.change_set_id
            FROM change_item i WHERE i.change_set_id = ? AND i.change IN ('added', 'changed')""",
                    [SOURCE_ID, eff, inferred, cs_id])
        _mark_verified(store, cs_id)
        con.execute("UPDATE change_set SET status = 'approved', reviewed_by = ?, reviewed_at = ? "
                    "WHERE change_set_id = ?", [reviewer, _now_iso(), cs_id])
        con.commit()
    except Exception:
        con.rollback()
        raise
    return get_change_set(store, cs_id)


def reject(store: Store, cs_id: int, reviewer: str) -> dict:
    cs = get_change_set(store, cs_id)
    if cs["status"] != "pending":
        raise ChangeSetError(f"change set {cs_id} is {cs['status']}, not pending")
    store.con.execute("UPDATE change_set SET status = 'rejected', reviewed_by = ?, reviewed_at = ? "
                      "WHERE change_set_id = ?", [reviewer, _now_iso(), cs_id])
    return get_change_set(store, cs_id)
