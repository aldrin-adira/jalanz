import csv

import pytest

from jalanz_pipelines.llm_toll import cli
from jalanz_pipelines.llm_toll.changeset import build_change_set
from jalanz_pipelines.llm_toll.crawl import Scope, crawl
from jalanz_pipelines.llm_toll.store import Store

from conftest import FakeSession


def seed(data_dir, make_client) -> int:
    store = Store(data_dir)
    try:
        result = crawl(make_client(FakeSession()), store, Scope(highways=["90"]), log=lambda _: None)
        return build_change_set(store, result.run_id)
    finally:
        store.close()


def test_review_approve_lookup_and_export(tmp_path, make_client, capsys):
    data = tmp_path / "data"
    cs_id = seed(data, make_client)
    base = ["--data-dir", str(data)]

    assert cli.main(base + ["change-set", "show", str(cs_id)]) == 0
    out = capsys.readouterr().out
    assert "pending" in out and "added 30" in out and "unmatched_plaza" in out

    with pytest.raises(SystemExit, match="--by"):
        cli.main(base + ["change-set", "approve", str(cs_id)])
    assert cli.main(base + ["change-set", "approve", str(cs_id), "--by", "owner",
                            "--effective-from", "2026-11-01"]) == 0
    assert "approved" in capsys.readouterr().out

    assert cli.main(base + ["lookup", "90", "alpha", "CHARLIE"]) == 0
    out = capsys.readouterr().out
    assert "class 1: 1200 sen (RM12.00)" in out and "10.50 km" in out

    target = tmp_path / "out" / "current.csv"
    assert cli.main(base + ["export", "current", str(target)]) == 0
    with target.open(encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    assert len(rows) == 30 and rows[0]["effective_from"] == "2026-11-01T00:00:00+08:00"


def test_alias_add_and_list(tmp_path, capsys):
    base = ["--data-dir", str(tmp_path / "data")]
    assert cli.main(base + ["alias", "add", "1", "Simpang Pulai", "E1-SIMPANG-PULAI"]) == 0
    assert cli.main(base + ["alias", "list"]) == 0
    assert "E1-SIMPANG-PULAI" in capsys.readouterr().out


def test_second_reject_is_refused(tmp_path, make_client, capsys):
    data = tmp_path / "data"
    cs_id = seed(data, make_client)
    base = ["--data-dir", str(data), "change-set"]
    assert cli.main(base + ["reject", str(cs_id), "--by", "owner"]) == 0
    assert cli.main(base + ["reject", str(cs_id), "--by", "owner"]) == 1
