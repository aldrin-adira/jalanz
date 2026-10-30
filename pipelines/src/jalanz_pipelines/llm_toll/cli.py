"""Command line for the LLM toll crawler and change-set review.

    jalanz-toll crawl --highway 1 --entry 212 --exit 134
    jalanz-toll change-set list
    jalanz-toll change-set show 3
    jalanz-toll change-set approve 3 --by owner [--effective-from 2026-11-01]
    jalanz-toll alias add 1 "SIMPANG PULAI" E1-SIMPANG-PULAI
    jalanz-toll lookup 1 "SIMPANG PULAI" JURU
    jalanz-toll export observations 3 out.csv

Data lives in --data-dir (default $JALANZ_DATA_DIR or ./data), outside Git.
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import os
import sys
from pathlib import Path

from . import MYT, SOURCE_ID
from .aliases import normalise
from .changeset import MAX_LISTED_PER_RULE, ChangeSetError, approve, build_change_set, get_change_set, reject
from .crawl import Scope, crawl
from .parse import parse_options
from .portal import DEFAULT_DELAY_S, PortalClient, api_path
from .store import Store


def _client(args) -> PortalClient:
    import truststore  # Windows/macOS certificate stores; HTTPS may be inspected locally

    truststore.inject_into_ssl()
    return PortalClient(delay_s=args.delay)


def _print_change_set(store: Store, cs_id: int, limit: int) -> None:
    cs = get_change_set(store, cs_id)
    s = cs["summary"]
    print(f"change set {cs_id}: {cs['status']} (run {cs['run_id']}, {s['run_status']}), created {cs['created_at']}")
    if cs["reviewed_by"]:
        print(f"  reviewed by {cs['reviewed_by']} at {cs['reviewed_at']}")
    print("  highways: " + ", ".join(s["highways"]))
    print("  " + ", ".join(f"{k} {v}" for k, v in s["counts"].items()))
    w = cs["warnings"]
    for kind in ("errors", "warnings"):
        if w[kind]:
            total = sum(w["counts"][r] for r in {i["rule"] for i in w[kind]})
            print(f"  {kind.upper()} ({total}, first {MAX_LISTED_PER_RULE} per rule stored):")
            for item in w[kind][:limit]:
                print(f"    [{item['rule']}] {item['message']}")
    print("  not checked: " + "; ".join(s["not_checked"]))
    rows = store.query(
        "SELECT change, highway_ref, entry_name, exit_name, vehicle_class, old_amount_sen, new_amount_sen, "
        "distance_km FROM change_item WHERE change_set_id = ? AND change <> 'unchanged' "
        "ORDER BY change, highway_ref, entry_name, exit_name, vehicle_class LIMIT ?", [cs_id, limit])
    if rows:
        print(f"  first {len(rows)} changes:")
    for change, hw, en, xn, c, old, new, km in rows:
        km = f", {km} km" if km is not None else ""
        print(f"    {change:8} hw {hw} {en} -> {xn or '(open)'} class {c}: {_rm(old)} -> {_rm(new)}{km}")


def _rm(sen) -> str:
    return "-" if sen is None else f"RM{sen // 100}.{sen % 100:02d}"


def _find_ref(names: dict[str, str], value: str) -> str:
    if value in names:
        return value
    wanted = normalise(value)
    for ref, name in names.items():
        if normalise(name) == wanted:
            return ref
    raise SystemExit(f"no plaza '{value}'")


def cmd_crawl(args, store: Store) -> int:
    scope = Scope(highways=None if args.all_highways else args.highway,
                  entries=set(args.entry) if args.entry else None,
                  exits=set(args.exit) if args.exit else None)
    if scope.highways is None and not args.all_highways:
        raise SystemExit("name at least one --highway, or pass --all-highways")
    result = crawl(_client(args), store, scope, resume_run=args.resume, max_requests=args.max_requests)
    print(f"run {result.run_id}: {result.status}, {result.requests_sent} requests")
    for f in result.failures[:20]:
        print(f"  failed: {f}")
    if result.status == "blocked":
        print("STOPPED: the portal refused us or robots.txt disallows the path. Ask LLM for permission.")
        return 2
    if not args.no_change_set:
        cs_id = build_change_set(store, result.run_id)
        _print_change_set(store, cs_id, 20)
    return 0


def cmd_highways(args, store: Store) -> int:
    client = _client(args)
    for o in parse_options(client.get(api_path("getHighway")).body):
        print(f"{o.ref:>4}  {o.name}")
    return 0


def cmd_change_set(args, store: Store) -> int:
    if args.action == "list":
        for cs_id, created, run_id, status, summary in store.query(
                "SELECT change_set_id, created_at, run_id, status, summary FROM change_set ORDER BY 1 DESC LIMIT 30"):
            print(f"{cs_id:>4}  {status:14} run {run_id:<4} {created}")
        return 0
    if args.action == "build":
        cs_id = build_change_set(store, args.id)
        _print_change_set(store, cs_id, args.limit)
        return 0
    if args.action == "show":
        _print_change_set(store, args.id, args.limit)
        return 0
    if not args.by:
        raise SystemExit("--by is required to approve or reject")
    try:
        if args.action == "approve":
            eff = None
            if args.effective_from:
                eff = dt.datetime.combine(dt.date.fromisoformat(args.effective_from), dt.time(), MYT)
            approve(store, args.id, args.by, eff)
        else:
            reject(store, args.id, args.by)
    except ChangeSetError as e:
        print(f"refused: {e}", file=sys.stderr)
        return 1
    _print_change_set(store, args.id, 5)
    return 0


def cmd_alias(args, store: Store) -> int:
    if args.action == "add":
        store.add_alias(args.highway, args.name, normalise(args.name), args.plaza_id)
        print(f"highway {args.highway}: '{args.name}' -> {args.plaza_id}")
    else:
        for hw, alias, plaza_id in store.query(
                "SELECT highway_ref, alias, plaza_id FROM plaza_alias WHERE source_id = ? ORDER BY 1, 2", [SOURCE_ID]):
            print(f"{hw:>4}  {alias:40} {plaza_id}")
    return 0


def cmd_lookup(args, store: Store) -> int:
    names = dict(store.query(
        "SELECT DISTINCT entry_ref, entry_name FROM fare_observation WHERE highway_ref = ?", [args.highway]))
    names.update(store.query(
        "SELECT DISTINCT exit_ref, exit_name FROM fare_observation WHERE highway_ref = ? AND exit_ref <> ''",
        [args.highway]))
    entry = _find_ref(names, args.entry)
    exit_ = _find_ref(names, args.exit) if args.exit else ""
    rows = store.query("""
        SELECT vehicle_class, amount_sen, distance_km, retrieved_at, entry_name, exit_name FROM fare_observation
        WHERE highway_ref = ? AND entry_ref = ? AND exit_ref = ?
          AND retrieved_at = (SELECT max(retrieved_at) FROM fare_observation
                              WHERE highway_ref = ? AND entry_ref = ? AND exit_ref = ?)
        ORDER BY vehicle_class""", [args.highway, entry, exit_] * 2)
    if not rows:
        print("not crawled yet")
        return 1
    print(f"{rows[0][4]} -> {rows[0][5] or '(open)'}, retrieved {rows[0][3]}, distance {rows[0][2]} km")
    for c, sen, *_ in rows:
        print(f"  class {c}: {sen} sen ({_rm(sen)})")
    return 0


def cmd_export(args, store: Store) -> int:
    if args.what == "observations":
        if args.run is None:
            raise SystemExit("export observations needs a run id")
        cur = store.con.execute("SELECT * FROM fare_observation WHERE run_id = ? ORDER BY highway_ref, entry_name, "
                                "exit_name, vehicle_class", [args.run])
    else:
        cur = store.con.execute("SELECT * FROM fare WHERE effective_to IS NULL ORDER BY highway_ref, entry_name, "
                                "exit_name, vehicle_class")
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow([d[0] for d in cur.description])
        n = 0
        for row in cur.fetchall():
            w.writerow(row)
            n += 1
    print(f"wrote {n} rows to {out}")
    return 0


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="jalanz-toll", description="LLM toll-rate crawler and change-set review")
    p.add_argument("--data-dir", default=os.environ.get("JALANZ_DATA_DIR", "data"))
    sub = p.add_subparsers(dest="command", required=True)

    c = sub.add_parser("crawl", help="fetch fares from the LLM calculator")
    c.add_argument("--highway", action="append", help="portal highway id (repeatable)")
    c.add_argument("--all-highways", action="store_true")
    c.add_argument("--entry", action="append", help="limit to these entry plaza ids")
    c.add_argument("--exit", action="append", help="limit to these exit plaza ids")
    c.add_argument("--delay", type=float, default=DEFAULT_DELAY_S, help="seconds between requests")
    c.add_argument("--max-requests", type=int)
    c.add_argument("--resume", type=int, metavar="RUN_ID")
    c.add_argument("--no-change-set", action="store_true")
    c.set_defaults(func=cmd_crawl)

    h = sub.add_parser("highways", help="list the portal's highways and ids")
    h.add_argument("--delay", type=float, default=DEFAULT_DELAY_S)
    h.set_defaults(func=cmd_highways)

    cs = sub.add_parser("change-set", help="build, review, approve or reject change sets")
    cs.add_argument("action", choices=["list", "build", "show", "approve", "reject"])
    cs.add_argument("id", type=int, nargs="?", help="change set id (run id for build)")
    cs.add_argument("--by", help="reviewer name")
    cs.add_argument("--effective-from", help="YYYY-MM-DD in MYT; default: crawl day, flagged inferred")
    cs.add_argument("--limit", type=int, default=40)
    cs.set_defaults(func=cmd_change_set)

    a = sub.add_parser("alias", help="map portal plaza names to our plaza_id")
    a.add_argument("action", choices=["add", "list"])
    a.add_argument("highway", nargs="?")
    a.add_argument("name", nargs="?")
    a.add_argument("plaza_id", nargs="?")
    a.set_defaults(func=cmd_alias)

    lk = sub.add_parser("lookup", help="show the latest crawled fare for a pair")
    lk.add_argument("highway")
    lk.add_argument("entry", help="plaza id or name")
    lk.add_argument("exit", nargs="?", help="omit for open-system plazas")
    lk.set_defaults(func=cmd_lookup)

    e = sub.add_parser("export", help="write CSV of a run's observations or the current fares")
    e.add_argument("what", choices=["observations", "current"])
    e.add_argument("run", type=int, nargs="?")
    e.add_argument("out")
    e.set_defaults(func=cmd_export)
    return p


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    if args.command == "change-set" and args.action != "list" and args.id is None:
        raise SystemExit("change-set needs an id")
    if args.command == "alias" and args.action == "add" and not all((args.highway, args.name, args.plaza_id)):
        raise SystemExit("alias add needs HIGHWAY NAME PLAZA_ID")
    store = Store(args.data_dir)
    try:
        return args.func(args, store)
    finally:
        store.close()


if __name__ == "__main__":
    sys.exit(main())
