# Toll fixtures F1–F10

The contract for every toll engine (`spec/04-toll-estimator.md` §9). The app (`:core:toll`) and any future server
build must produce the same `total_sen`, `range_sen`, `confidence` and per-item `charged_sen` for every case.
Runner: `android/core/toll/src/test/.../TollFixturesTest.kt` (`./gradlew :core:toll:test`).

One file per fixture. Each file holds a small, self-contained toll dataset and one or more cases (F2 holds F2a–F2d).

## File format

```jsonc
{
  "id": "F1",
  "kind": "real",                 // "real" (fares from an official source) or "synthetic" (IDs start with TEST-)
  "description": "…",
  "retrieved_at": "…",            // real fixtures only: when the fares were read from the source
  "source": "…",                  // real fixtures only
  "notes": ["…"],                 // assumptions and placeholders, each stated plainly
  "dataset": {
    "version": "fixture-F1",
    "plazas":         [{ "id", "highway_id", "name_ms", "kind": "booth|gantry", "class_scheme", "lat", "lon", "active_from" }],
    "osm_refs":       [{ "plaza_id", "node_id", "in_way_id"?, "out_way_id"?, "bearing_deg"? }],
    "roles":          [{ "plaza_id", "in_way_id"?, "role", "network_id"?, "effective_from", "effective_to"? }],
    "open_fares":     [{ "plaza_id", "in_way_id"?, "vehicle_class", "amount_sen", "effective_from", "effective_to"?, "source_id", "verified_at" }],
    "closed_fares":   [{ "network_id", "entry", "exit", "vehicle_class", "amount_sen", "distance_km"?, "effective_from", "effective_to"?, "source_id", "verified_at" }],
    "system_periods": [{ "highway_id", "system_type": "open|closed|free", "network_id"?, "effective_from", "effective_to"? }],
    "discount_events":[{ "id", "name", "starts_at", "ends_at", "kind": "percent_off|fixed_amount_off|free",
                         "percent_bp"?, "amount_sen"?, "rounding": "half_up_sen|down_sen|up_sen",
                         "scopes": [{ "kind": "all|highway|network|plaza", "id"?, "class_scheme"?, "vehicle_class"? }],
                         "excluded_plazas": ["…"], "source_url" }],
    "class_mappings": [{ "profile", "class_scheme", "vehicle_class" }]   // vehicle_class null = not charged
  },
  "cases": [{
    "id": "F1-class1",
    "vehicle_profile": "car",
    "now": "…",                   // when the estimate is made (used for the verified_at age rule, F10)
    "path": [{ "node_id"?, "way_id", "lat", "lon", "at" }],
    "expected": {
      "total_sen": 1525,
      "range_sen": null,          // [min, max] when confidence is LOW
      "confidence": "HIGH|MEDIUM|LOW",
      "items": [{ "type": "open|closed_pair|closed_unknown_entry",
                  "plaza_id"?, "network_id"?, "entry"?, "exit"?,
                  "charged_sen", "range_sen"?, "discount_event_id"? }]
    }
  }]
}
```

### Conventions
- Times are ISO-8601 with the `+08:00` offset (Malaysia time). Money is integer sen. Periods are `[from, to)`.
- A path is the route as an ordered list of points. `way_id` is the OSM way travelled **to reach** the point.
  A plaza crossing is the triple (this point's `way_id`, its `node_id`, the next point's `way_id`) (04 §5).
  `at` is the predicted time at the point; fares and discounts use the time at the charging plaza.
- `"path": "same-as:<case id>"` reuses the path of an earlier case in the same file.
- OSM node and way IDs in these files are **synthetic**, even in real fixtures. Real IDs come from the plaza
  mapping (WP 1.8).
- Items appear in path order. `total_sen` is the sum of `charged_sen`; for LOW confidence with an unknown entry,
  the item has `charged_sen: null` and `range_sen`, and `total_sen` is the **upper end** of the route's range
  (04 §6, so a total never under-states the toll).
- Real fixtures must be re-checked against the official calculator before each release (04 §9 `[VERIFY]`).
