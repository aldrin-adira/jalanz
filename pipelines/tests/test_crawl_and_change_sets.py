import datetime as dt

import pytest

from jalanz_pipelines.llm_toll import MYT
from jalanz_pipelines.llm_toll.changeset import ChangeSetError, approve, build_change_set, get_change_set, reject
from jalanz_pipelines.llm_toll.crawl import Scope, crawl

from conftest import FakeSession, closed_fares, network_error


def run(store, make_client, session, **scope):
    result = crawl(make_client(session), store, Scope(**scope), log=lambda _: None)
    return result, build_change_set(store, result.run_id)


def current(store, hw="90"):
    return {(e, x, c): a for e, x, c, a in store.query(
        "SELECT entry_ref, exit_ref, vehicle_class, amount_sen FROM fare "
        "WHERE effective_to IS NULL AND highway_ref = ?", [hw])}


def rules(cs):
    return {w["rule"] for w in cs["warnings"]["warnings"]}


def test_crawl_fetches_closed_and_open_highways(store, make_client):
    session = FakeSession()
    result, cs_id = run(store, make_client, session, highways=["90", "91"])
    assert result.status == "complete"
    # robots, highways, 2 entry lists, 3 + 2 exit lists, 6 closed + 2 open fares
    assert result.requests_sent == 1 + 1 + 2 + 5 + 8
    assert "/Calculator/getTollRate/91/010/null" in session.calls
    rows = store.query_dicts("SELECT * FROM fare_observation WHERE run_id = ? AND entry_ref = '1' "
                             "AND exit_ref = '3' AND vehicle_class = '1'", [result.run_id])
    assert rows[0]["amount_sen"] == 1200 and float(rows[0]["distance_km"]) == 10.5
    assert rows[0]["entry_name"] == "ALPHA" and rows[0]["exit_name"] == "CHARLIE"
    assert rows[0]["source_id"] == "llm_kadartol" and rows[0]["retrieved_at"].endswith("+08:00")
    cs = get_change_set(store, cs_id)
    assert cs["status"] == "pending"
    assert cs["summary"]["counts"] == {"added": 40, "changed": 0, "removed": 0, "unchanged": 0}


def test_raw_snapshots_are_stored_and_reparsable(store, make_client):
    result, first = run(store, make_client, FakeSession(), highways=["90"])
    snaps = store.query("SELECT count(*), count(DISTINCT content_sha256) FROM source_snapshot "
                        "WHERE run_id = ? AND http_status = 200", [result.run_id])[0]
    assert snaps[0] == 1 + 1 + 3 + 6
    again = build_change_set(store, result.run_id)  # no network needed
    assert get_change_set(store, again)["summary"]["counts"] == get_change_set(store, first)["summary"]["counts"]


def test_scope_limits_entries_and_exits(store, make_client):
    session = FakeSession()
    result, _ = run(store, make_client, session, highways=["90"], entries={"1"}, exits={"3"})
    assert [c for c in session.calls if "getTollRate" in c] == ["/Calculator/getTollRate/90/1/3"]


def test_approve_then_unchanged_crawl_is_auto_confirmed(store, make_client):
    _, cs1 = run(store, make_client, FakeSession(), highways=["90"])
    approve(store, cs1, "owner")
    fares = current(store)
    assert fares[("1", "3", "1")] == 1200 and len(fares) == 30
    row = store.query_dicts("SELECT * FROM fare WHERE entry_ref = '1' AND exit_ref = '3' AND vehicle_class = '1'")[0]
    assert row["effective_from"].endswith("T00:00:00+08:00") and row["effective_from_inferred"]
    store.con.execute("UPDATE fare SET verified_at = '2000-01-01T00:00:00+08:00'")
    for name in ("ALPHA", "BRAVO", "CHARLIE"):  # unmatched names would route it to review
        store.add_alias("90", name, name, f"E90-{name}")

    _, cs2 = run(store, make_client, FakeSession(), highways=["90"])
    cs = get_change_set(store, cs2)
    assert cs["status"] == "auto_confirmed"
    assert cs["summary"]["counts"]["unchanged"] == 30
    assert store.query("SELECT min(verified_at) FROM fare")[0][0] > "2026"


def test_changed_fare_closes_old_row_and_warns_on_big_change(store, make_client):
    _, cs1 = run(store, make_client, FakeSession(), highways=["90"])
    approve(store, cs1, "owner", dt.datetime(2020, 1, 1, tzinfo=MYT))
    _, cs2 = run(store, make_client, FakeSession(closed_fares({("1", "2"): 650})), highways=["90"])
    cs = get_change_set(store, cs2)
    assert cs["status"] == "pending"
    assert cs["summary"]["counts"]["changed"] == 5
    assert {"5_change_magnitude", "2_symmetry"} <= rules(cs)
    approve(store, cs2, "owner", dt.datetime(2026, 11, 1, tzinfo=MYT))
    history = store.query("SELECT amount_sen, effective_from, effective_to, effective_from_inferred FROM fare "
                          "WHERE entry_ref = '1' AND exit_ref = '2' AND vehicle_class = '1' ORDER BY effective_from")
    assert history == [(500, "2020-01-01T00:00:00+08:00", "2026-11-01T00:00:00+08:00", False),
                       (650, "2026-11-01T00:00:00+08:00", None, False)]


def test_removed_pair_is_closed_on_approval(store, make_client):
    _, cs1 = run(store, make_client, FakeSession(), highways=["90"])
    approve(store, cs1, "owner", dt.datetime(2020, 1, 1, tzinfo=MYT))
    fares = closed_fares()
    del fares[("1", "3")]
    _, cs2 = run(store, make_client, FakeSession(fares), highways=["90"])
    assert get_change_set(store, cs2)["summary"]["counts"]["removed"] == 5
    approve(store, cs2, "owner")
    assert ("1", "3", "1") not in current(store)


def test_limited_crawl_does_not_remove_pairs_outside_its_scope(store, make_client):
    _, cs1 = run(store, make_client, FakeSession(), highways=["90"])
    approve(store, cs1, "owner", dt.datetime(2020, 1, 1, tzinfo=MYT))
    _, cs2 = run(store, make_client, FakeSession(), highways=["90"], entries={"1"}, exits={"2"})
    assert get_change_set(store, cs2)["summary"]["counts"] == {
        "added": 0, "changed": 0, "removed": 0, "unchanged": 5}


def test_triangle_and_class_order_warnings(store, make_client):
    fares = closed_fares({("1", "3"): 1300, ("3", "1"): 1300})  # > 500 + 700 + 50
    session = FakeSession(fares)
    _, cs_id = run(store, make_client, session, highways=["90"])
    assert "3_triangle" in rules(get_change_set(store, cs_id))

    store2_session = FakeSession()
    store2_session.responses["/Calculator/getTollRate/90/1/2"] = [(200, (
        b'[{"transCode":1,"rate":5.0,"distance":10},{"transCode":2,"rate":4.0,"distance":0},'
        b'{"transCode":3,"rate":6.0,"distance":0}]'))]
    _, cs_id = run(store, make_client, store2_session, highways=["90"])
    assert "6_class_order" in rules(get_change_set(store, cs_id))


def test_amount_out_of_range_is_a_hard_error(store, make_client):
    session = FakeSession(closed_fares({("1", "2"): 50_000, ("2", "1"): 50_000}))
    _, cs_id = run(store, make_client, session, highways=["90"])
    cs = get_change_set(store, cs_id)
    assert cs["warnings"]["errors"][0]["rule"] == "1_amount_range"
    with pytest.raises(ChangeSetError, match="hard errors"):
        approve(store, cs_id, "owner")
    assert reject(store, cs_id, "owner")["status"] == "rejected"


def test_unmatched_plaza_names_warn_until_aliases_exist(store, make_client):
    _, cs_id = run(store, make_client, FakeSession(), highways=["91"])
    assert "unmatched_plaza" in rules(get_change_set(store, cs_id))
    from jalanz_pipelines.llm_toll.aliases import normalise
    store.add_alias("91", "DELTA", normalise("DELTA"), "E99-DELTA")
    store.add_alias("91", "Echo(U)", normalise("Echo(U)"), "E99-ECHO-U")
    cs2 = build_change_set(store, get_change_set(store, cs_id)["run_id"])
    assert "unmatched_plaza" not in rules(get_change_set(store, cs2))
    ids = {r[0] for r in store.query("SELECT entry_plaza_id FROM change_item WHERE change_set_id = ?", [cs2])}
    assert ids == {"E99-DELTA", "E99-ECHO-U"}


def test_stale_change_set_is_refused(store, make_client):
    _, cs1 = run(store, make_client, FakeSession(), highways=["90"])
    _, cs2 = run(store, make_client, FakeSession(), highways=["90"])
    approve(store, cs1, "owner")
    with pytest.raises(ChangeSetError, match="no longer match"):
        approve(store, cs2, "owner")


def test_failed_fetch_makes_run_partial_and_is_not_a_removal(store, make_client):
    _, cs1 = run(store, make_client, FakeSession(), highways=["90"])
    approve(store, cs1, "owner", dt.datetime(2020, 1, 1, tzinfo=MYT))
    session = FakeSession()
    session.responses["/Calculator/getTollRate/90/1/3"] = [network_error()] * 4
    result, cs2 = run(store, make_client, session, highways=["90"])
    cs = get_change_set(store, cs2)
    assert result.status == "partial" and cs["status"] == "pending"
    assert "fetch" in rules(cs)
    assert cs["summary"]["counts"]["removed"] == 0


def test_blocked_crawl_stops_immediately(store, make_client):
    session = FakeSession()
    session.responses["/Calculator/getPlazaEnter/90"] = [(403, b"")]
    result = crawl(make_client(session), store, Scope(highways=["90", "91"]), log=lambda _: None)
    assert result.status == "blocked"
    assert not any("91" in c for c in session.calls)


def test_budget_stops_and_resume_reuses_stored_responses(store, make_client):
    first = FakeSession()
    result = crawl(make_client(first), store, Scope(highways=["90"]), max_requests=5, log=lambda _: None)
    assert result.status == "budget"
    second = FakeSession()
    resumed = crawl(make_client(second), store, Scope(highways=["90"]), resume_run=result.run_id,
                    log=lambda _: None)
    assert resumed.status == "complete" and resumed.run_id == result.run_id
    assert "/Calculator/getHighway" not in second.calls
    assert len([c for c in first.calls + second.calls if "getTollRate" in c]) == 6
