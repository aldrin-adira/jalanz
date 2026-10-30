from decimal import Decimal

import pytest

from jalanz_pipelines.llm_toll.aliases import normalise
from jalanz_pipelines.llm_toll.parse import ParseError, class_scheme, parse_options, parse_rates, rm_to_sen

from conftest import FIXTURES


def test_closed_fare_matches_fixture_F1():
    """PLUS Simpang Pulai -> Juru, as in fixtures/toll/F1.json (04 §9)."""
    table = parse_rates((FIXTURES / "rate_closed_F1.json").read_bytes())
    assert {r.vehicle_class: r.amount_sen for r in table.rates} == {
        "1": 1525, "2": 2790, "3": 3710, "4": 763, "5": 1140}
    assert table.distance_km == Decimal("136.5")


def test_open_fare_has_no_distance():
    table = parse_rates((FIXTURES / "rate_open.json").read_bytes())
    assert [r.amount_sen for r in table.rates] == [210, 420, 630, 110, 160]
    assert table.distance_km is None


def test_options_drop_placeholder_and_keep_string_ids():
    opts = parse_options((FIXTURES / "plaza_enter_trimmed.json").read_bytes())
    assert [(o.ref, o.name) for o in opts] == [
        ("010", "PENCHALA"), ("134", "JURU"), ("211", "IPOH(S)"), ("212", "SIMPANG PULAI")]


def test_html_error_page_is_a_parse_error():
    with pytest.raises(ParseError, match="not JSON"):
        parse_rates((FIXTURES / "error.html").read_bytes())


@pytest.mark.parametrize("body", [b"{}", b"[]", b'[{"rate": 1}]'])
def test_unexpected_rate_shapes_are_parse_errors(body):
    with pytest.raises(ParseError):
        parse_rates(body)


def test_rm_to_sen_is_exact():
    assert rm_to_sen(Decimal("27.9")) == 2790
    assert rm_to_sen(Decimal("0.1")) == 10
    assert rm_to_sen(3) == 300
    with pytest.raises(ParseError):
        rm_to_sen(Decimal("1.005"))


def test_penang_bridge_has_its_own_class_scheme():
    assert class_scheme("5") == "PENANG_BRIDGE"
    assert class_scheme("1") == "LLM_1_5"


@pytest.mark.parametrize("raw, expected", [
    ("IPOH(S)", "IPOH (S)"),
    ("Bukit  Tambun (U)", "BUKIT TAMBUN (U)"),
    ("PLAZA TOL JURU", "JURU"),
    ("Sungai Besi – Ulu Kelang", "SUNGAI BESI-ULU KELANG"),
    ("BANDAR CASSIA ( PLAZA A BCS )", "BANDAR CASSIA (PLAZA A BCS)"),
])
def test_normalise(raw, expected):
    assert normalise(raw) == expected
