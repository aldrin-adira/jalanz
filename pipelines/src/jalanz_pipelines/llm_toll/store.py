"""Local DuckDB store and raw snapshot files for the toll crawler.

Layout under the data directory (never committed, see pipelines/.gitignore):
    toll.duckdb          runs, snapshots, observations, change sets, fares
    raw/ab/abcd....json  raw responses, content-addressed by SHA-256

Tables follow 04 §3 where they overlap (source_snapshot, change_set, fares
with effective_from/effective_to). Rows are keyed by the portal's own refs
(highway, entry, exit); our plaza_id comes from plaza_alias. Timestamps are
ISO-8601 strings in Asia/Kuala_Lumpur (+08:00).
"""

from __future__ import annotations

import hashlib
from pathlib import Path

import duckdb

from . import SOURCE_ID
from .parse import PARSER_VERSION
from .portal import Fetched

SCHEMA = """
CREATE SEQUENCE IF NOT EXISTS seq_run;
CREATE SEQUENCE IF NOT EXISTS seq_snapshot;
CREATE SEQUENCE IF NOT EXISTS seq_change_set;
CREATE TABLE IF NOT EXISTS crawl_run (
  run_id BIGINT, source_id TEXT, started_at TEXT, finished_at TEXT,
  status TEXT, scope TEXT, note TEXT);
CREATE TABLE IF NOT EXISTS source_snapshot (
  snapshot_id BIGINT, run_id BIGINT, source_id TEXT, kind TEXT, url TEXT,
  highway_ref TEXT, entry_ref TEXT, exit_ref TEXT, retrieved_at TEXT,
  http_status INTEGER, content_sha256 TEXT, object_key TEXT,
  parser_version TEXT, error TEXT);
CREATE TABLE IF NOT EXISTS plaza_alias (
  source_id TEXT, highway_ref TEXT, alias_norm TEXT, alias TEXT, plaza_id TEXT);
CREATE TABLE IF NOT EXISTS fare_observation (
  run_id BIGINT, snapshot_id BIGINT, source_id TEXT, retrieved_at TEXT,
  highway_ref TEXT, highway_name TEXT, system_type TEXT, class_scheme TEXT,
  entry_ref TEXT, entry_name TEXT, exit_ref TEXT, exit_name TEXT,
  vehicle_class TEXT, amount_sen INTEGER, distance_km DECIMAL(7,2));
CREATE TABLE IF NOT EXISTS change_set (
  change_set_id BIGINT, created_at TEXT, source_id TEXT, run_id BIGINT,
  status TEXT, summary TEXT, warnings TEXT, reviewed_by TEXT,
  reviewed_at TEXT);
CREATE TABLE IF NOT EXISTS change_item (
  change_set_id BIGINT, change TEXT, highway_ref TEXT, highway_name TEXT,
  system_type TEXT, class_scheme TEXT, entry_ref TEXT, exit_ref TEXT,
  entry_name TEXT, exit_name TEXT, entry_plaza_id TEXT, exit_plaza_id TEXT,
  vehicle_class TEXT, old_amount_sen INTEGER, new_amount_sen INTEGER,
  distance_km DECIMAL(7,2), snapshot_id BIGINT, retrieved_at TEXT);
CREATE TABLE IF NOT EXISTS fare (
  source_id TEXT, highway_ref TEXT, highway_name TEXT, system_type TEXT,
  class_scheme TEXT, entry_ref TEXT, exit_ref TEXT, entry_name TEXT,
  exit_name TEXT, vehicle_class TEXT, amount_sen INTEGER,
  distance_km DECIMAL(7,2), effective_from TEXT, effective_to TEXT,
  effective_from_inferred BOOLEAN, snapshot_id BIGINT, retrieved_at TEXT,
  verified_at TEXT, change_set_id BIGINT);
"""


class Store:
    def __init__(self, data_dir: Path | str):
        self.data_dir = Path(data_dir)
        self.raw_dir = self.data_dir / "raw"
        self.raw_dir.mkdir(parents=True, exist_ok=True)
        self.con = duckdb.connect(str(self.data_dir / "toll.duckdb"))
        self.con.execute(SCHEMA)

    def close(self) -> None:
        self.con.close()

    def query(self, sql: str, params: list | tuple = ()) -> list[tuple]:
        return self.con.execute(sql, params).fetchall()

    def query_dicts(self, sql: str, params: list | tuple = ()) -> list[dict]:
        cur = self.con.execute(sql, params)
        cols = [d[0] for d in cur.description]
        return [dict(zip(cols, row)) for row in cur.fetchall()]

    def next_id(self, sequence: str) -> int:
        return self.query(f"SELECT nextval('{sequence}')")[0][0]

    # Runs and snapshots

    def start_run(self, scope: str, started_at: str) -> int:
        run_id = self.next_id("seq_run")
        self.con.execute(
            "INSERT INTO crawl_run VALUES (?, ?, ?, NULL, 'running', ?, NULL)",
            [run_id, SOURCE_ID, started_at, scope])
        return run_id

    def finish_run(self, run_id: int, status: str, finished_at: str, note: str | None = None) -> None:
        self.con.execute(
            "UPDATE crawl_run SET status = ?, finished_at = ?, note = ? WHERE run_id = ?",
            [status, finished_at, note, run_id])

    def save_snapshot(self, run_id: int, kind: str, url: str, refs: tuple[str, str, str],
                      fetched: Fetched | None, retrieved_at: str, error: str | None = None) -> int:
        sha = key = None
        status = None
        if fetched is not None:
            status = fetched.status
            sha = hashlib.sha256(fetched.body).hexdigest()
            key = f"raw/{sha[:2]}/{sha}.json"
            path = self.data_dir / key
            if not path.exists():
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(fetched.body)
        snapshot_id = self.next_id("seq_snapshot")
        self.con.execute(
            "INSERT INTO source_snapshot VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            [snapshot_id, run_id, SOURCE_ID, kind, url, *refs, retrieved_at, status, sha, key,
             PARSER_VERSION, error])
        return snapshot_id

    def find_ok_snapshot(self, run_id: int, url: str) -> dict | None:
        rows = self.query_dicts(
            "SELECT * FROM source_snapshot WHERE run_id = ? AND url = ? AND http_status = 200 "
            "ORDER BY snapshot_id DESC LIMIT 1", [run_id, url])
        return rows[0] if rows else None

    def read_raw(self, object_key: str) -> bytes:
        return (self.data_dir / object_key).read_bytes()

    # Aliases

    def add_alias(self, highway_ref: str, alias: str, alias_norm: str, plaza_id: str) -> None:
        self.con.execute(
            "DELETE FROM plaza_alias WHERE source_id = ? AND highway_ref = ? AND alias_norm = ?",
            [SOURCE_ID, highway_ref, alias_norm])
        self.con.execute("INSERT INTO plaza_alias VALUES (?, ?, ?, ?, ?)",
                         [SOURCE_ID, highway_ref, alias_norm, alias, plaza_id])

    def alias_map(self) -> dict[tuple[str, str], str]:
        rows = self.query("SELECT highway_ref, alias_norm, plaza_id FROM plaza_alias WHERE source_id = ?",
                          [SOURCE_ID])
        return {(h, n): p for h, n, p in rows}
