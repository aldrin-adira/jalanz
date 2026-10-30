import pytest

from jalanz_pipelines.llm_toll.portal import (
    USER_AGENT, PortalBlocked, PortalError, RobotsRules, api_path,
)

from conftest import FakeSession, network_error

LLM_ROBOTS = """User-agent: Googlebot
Disallow:

User-agent: *
Disallow: /application/
Disallow: /index.php/*
Disallow: /search

# Block spam URLs only
Disallow: /pg-slot
Disallow: *pg*
Disallow: *slot*
"""


def test_robots_allows_calculator_and_blocks_listed_paths():
    rules = RobotsRules.parse(LLM_ROBOTS)
    assert rules.allowed("/Calculator/getHighway")
    assert rules.allowed("/Calculator/getTollRate/1/212/134")
    assert not rules.allowed("/index.php/awam/get_data_ajax")
    assert not rules.allowed("/search")
    assert not rules.allowed("/assets/pgslot.html")  # wildcard in the middle


def test_robots_named_group_wins_and_allow_beats_shorter_disallow():
    text = "User-agent: *\nDisallow: /\n\nUser-agent: Jalanz\nDisallow: /Calculator/\nAllow: /Calculator/getHighway$\n"
    rules = RobotsRules.parse(text)
    assert rules.allowed("/Calculator/getHighway")
    assert not rules.allowed("/Calculator/getHighwayX")
    assert not rules.allowed("/Calculator/getPlazaEnter/1")
    assert rules.allowed("/about")  # only the Jalanz group applies


def test_empty_robots_allows_everything():
    assert RobotsRules.parse("").allowed("/anything")


def test_client_identifies_itself_and_spaces_requests(make_client):
    session = FakeSession()
    client = make_client(session)
    client.get(api_path("getHighway"))
    client.get(api_path("getPlazaEnter", "90"))
    assert session.headers["User-Agent"] == USER_AGENT
    assert session.calls == ["/robots.txt", "/Calculator/getHighway", "/Calculator/getPlazaEnter/90"]
    assert client.sleeps == [3.0, 3.0]  # before each request after the first


def test_client_refuses_paths_disallowed_by_robots(make_client):
    client = make_client(FakeSession(robots="User-agent: *\nDisallow: /Calculator/\n"))
    with pytest.raises(PortalBlocked):
        client.get(api_path("getHighway"))


def test_client_retries_server_errors_with_backoff(make_client):
    session = FakeSession()
    session.responses["/Calculator/getHighway"] = [(503, b""), network_error()]
    client = make_client(session)
    fetched = client.get(api_path("getHighway"))
    assert fetched.status == 200
    assert client.sleeps == [3.0, 9.0, 3.0, 27.0, 3.0]  # turn, back-off 1, turn, back-off 2, turn


def test_client_gives_up_after_retries(make_client):
    session = FakeSession()
    session.responses["/Calculator/getHighway"] = [(500, b"")] * 4
    with pytest.raises(PortalError):
        make_client(session).get(api_path("getHighway"))


@pytest.mark.parametrize("status", [403, 429])
def test_client_stops_when_refused(make_client, status):
    session = FakeSession()
    session.responses["/Calculator/getHighway"] = [(status, b"")]
    with pytest.raises(PortalBlocked):
        make_client(session).get(api_path("getHighway"))


def test_api_path_quotes_segments():
    assert api_path("getTollRate", "4", "010", "null") == "/Calculator/getTollRate/4/010/null"
    assert api_path("getPlazaExit", "1", "a/b") == "/Calculator/getPlazaExit/1/a%2Fb"
