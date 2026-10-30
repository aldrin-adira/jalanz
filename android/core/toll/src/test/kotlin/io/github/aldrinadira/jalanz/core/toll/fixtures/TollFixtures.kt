package io.github.aldrinadira.jalanz.core.toll.fixtures

import io.github.aldrinadira.jalanz.core.toll.ClassAssignment
import io.github.aldrinadira.jalanz.core.toll.ClassMapping
import io.github.aldrinadira.jalanz.core.toll.ClassSchemeId
import io.github.aldrinadira.jalanz.core.toll.ClosedFare
import io.github.aldrinadira.jalanz.core.toll.Confidence
import io.github.aldrinadira.jalanz.core.toll.DiscountAmount
import io.github.aldrinadira.jalanz.core.toll.DiscountEvent
import io.github.aldrinadira.jalanz.core.toll.DiscountEventId
import io.github.aldrinadira.jalanz.core.toll.DiscountRounding
import io.github.aldrinadira.jalanz.core.toll.DiscountScope
import io.github.aldrinadira.jalanz.core.toll.DiscountScopeKind
import io.github.aldrinadira.jalanz.core.toll.EffectivePeriod
import io.github.aldrinadira.jalanz.core.toll.GeoPoint
import io.github.aldrinadira.jalanz.core.toll.HighwayId
import io.github.aldrinadira.jalanz.core.toll.HighwaySystemPeriod
import io.github.aldrinadira.jalanz.core.toll.NetworkId
import io.github.aldrinadira.jalanz.core.toll.OpenFare
import io.github.aldrinadira.jalanz.core.toll.OsmNodeId
import io.github.aldrinadira.jalanz.core.toll.OsmWayId
import io.github.aldrinadira.jalanz.core.toll.PathPoint
import io.github.aldrinadira.jalanz.core.toll.Plaza
import io.github.aldrinadira.jalanz.core.toll.PlazaId
import io.github.aldrinadira.jalanz.core.toll.PlazaKind
import io.github.aldrinadira.jalanz.core.toll.PlazaOsmRef
import io.github.aldrinadira.jalanz.core.toll.PlazaRole
import io.github.aldrinadira.jalanz.core.toll.Role
import io.github.aldrinadira.jalanz.core.toll.RoutePath
import io.github.aldrinadira.jalanz.core.toll.Sen
import io.github.aldrinadira.jalanz.core.toll.SourceId
import io.github.aldrinadira.jalanz.core.toll.SystemType
import io.github.aldrinadira.jalanz.core.toll.TollDataset
import io.github.aldrinadira.jalanz.core.toll.TollEstimate
import io.github.aldrinadira.jalanz.core.toll.TollItem
import io.github.aldrinadira.jalanz.core.toll.VehicleClass
import io.github.aldrinadira.jalanz.core.toll.VehicleProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.time.Instant
import java.time.OffsetDateTime

// Loads /fixtures/toll/*.json (format: fixtures/toll/README.md) into the domain model and expected results.

data class TollFixture(
    val id: String,
    val kind: String,
    val retrievedAt: String?,
    val source: String?,
    val dataset: TollDataset,
    val cases: List<FixtureCase>,
)

data class FixtureCase(
    val id: String,
    val profile: VehicleProfile,
    val now: Instant,
    val path: RoutePath,
    val expected: ExpectedEstimate,
) {
    /** Shown as the test name in reports. */
    override fun toString() = id
}

data class ExpectedEstimate(
    val totalSen: Long,
    val rangeSen: List<Long>?,
    val confidence: Confidence,
    val items: List<ExpectedItem>,
)

/** The fields both engines must agree on (04 §9 acceptance), in a comparable form. */
data class ExpectedItem(
    val type: String,
    val plazaId: String? = null,
    val networkId: String? = null,
    val entry: String? = null,
    val exit: String? = null,
    val chargedSen: Long?,
    val rangeSen: List<Long>? = null,
    val discountEventId: String? = null,
)

object TollFixtures {
    /** Set by core/toll/build.gradle.kts so tests find the repo-level fixtures folder. */
    val directory: File = File(
        System.getProperty("jalanz.tollFixtures") ?: error("system property jalanz.tollFixtures is not set"),
    )

    fun loadAll(): List<TollFixture> =
        directory.listFiles { f -> f.extension == "json" }.orEmpty()
            .sortedBy { it.nameWithoutExtension.removePrefix("F").toIntOrNull() ?: Int.MAX_VALUE }
            .map { load(it) }

    fun load(file: File): TollFixture {
        val root = Json.parseToJsonElement(file.readText()).jsonObject
        val dataset = root.obj("dataset")
        val cases = mutableListOf<FixtureCase>()
        for (case in root.array("cases").map { it.jsonObject }) {
            val pathJson = case.getValue("path")
            val path = if (pathJson is JsonPrimitive) {
                val ref = pathJson.content.removePrefix("same-as:")
                cases.first { it.id == ref }.path
            } else {
                RoutePath(pathJson.jsonArray.map { pathPoint(it.jsonObject) })
            }
            cases += FixtureCase(
                id = case.str("id"),
                profile = VehicleProfile(case.str("vehicle_profile")),
                now = case.time("now"),
                path = path,
                expected = expected(case.obj("expected")),
            )
        }
        return TollFixture(
            id = root.str("id"),
            kind = root.str("kind"),
            retrievedAt = root.strOrNull("retrieved_at"),
            source = root.strOrNull("source"),
            dataset = dataset(dataset),
            cases = cases,
        )
    }

    private fun dataset(d: JsonObject) = TollDataset(
        version = d.str("version"),
        plazas = d.array("plazas").map { it.jsonObject }.map {
            Plaza(
                id = PlazaId(it.str("id")),
                highwayId = HighwayId(it.str("highway_id")),
                nameMs = it.str("name_ms"),
                nameEn = it.strOrNull("name_en"),
                kind = PlazaKind.valueOf(it.str("kind").uppercase()),
                classScheme = ClassSchemeId(it.str("class_scheme")),
                location = GeoPoint(it.double("lat"), it.double("lon")),
                active = EffectivePeriod(it.time("active_from"), it.timeOrNull("active_to")),
            )
        },
        osmRefs = d.array("osm_refs").map { it.jsonObject }.map {
            PlazaOsmRef(
                plazaId = PlazaId(it.str("plaza_id")),
                osmNodeId = OsmNodeId(it.long("node_id")),
                inWayId = it.longOrNull("in_way_id")?.let(::OsmWayId),
                outWayId = it.longOrNull("out_way_id")?.let(::OsmWayId),
                bearingDeg = it.doubleOrNull("bearing_deg"),
            )
        },
        roles = d.array("roles").map { it.jsonObject }.map {
            PlazaRole(
                plazaId = PlazaId(it.str("plaza_id")),
                inWayId = it.longOrNull("in_way_id")?.let(::OsmWayId),
                role = Role.valueOf(it.str("role").uppercase()),
                networkId = it.strOrNull("network_id")?.let(::NetworkId),
                period = it.period(),
            )
        },
        openFares = d.array("open_fares").map { it.jsonObject }.map {
            OpenFare(
                plazaId = PlazaId(it.str("plaza_id")),
                inWayId = it.longOrNull("in_way_id")?.let(::OsmWayId),
                vehicleClass = VehicleClass(it.str("vehicle_class")),
                amount = Sen(it.long("amount_sen")),
                period = it.period(),
                sourceId = SourceId(it.str("source_id")),
                verifiedAt = it.time("verified_at"),
            )
        },
        closedFares = d.array("closed_fares").map { it.jsonObject }.map {
            ClosedFare(
                networkId = NetworkId(it.str("network_id")),
                entryPlazaId = PlazaId(it.str("entry")),
                exitPlazaId = PlazaId(it.str("exit")),
                vehicleClass = VehicleClass(it.str("vehicle_class")),
                amount = Sen(it.long("amount_sen")),
                distanceKm = it.doubleOrNull("distance_km"),
                period = it.period(),
                sourceId = SourceId(it.str("source_id")),
                verifiedAt = it.time("verified_at"),
            )
        },
        systemPeriods = d.array("system_periods").map { it.jsonObject }.map {
            HighwaySystemPeriod(
                highwayId = HighwayId(it.str("highway_id")),
                systemType = SystemType.valueOf(it.str("system_type").uppercase()),
                networkId = it.strOrNull("network_id")?.let(::NetworkId),
                period = it.period(),
            )
        },
        discountEvents = d.array("discount_events").map { it.jsonObject }.map(::discountEvent),
        classMappings = d.array("class_mappings").map { it.jsonObject }.map {
            val cls = it.strOrNull("vehicle_class")
            ClassMapping(
                profile = VehicleProfile(it.str("profile")),
                scheme = ClassSchemeId(it.str("class_scheme")),
                assignment = if (cls == null) ClassAssignment.NotCharged else ClassAssignment.Charged(VehicleClass(cls)),
            )
        },
    )

    private fun discountEvent(e: JsonObject) = DiscountEvent(
        id = DiscountEventId(e.str("id")),
        name = e.str("name"),
        window = EffectivePeriod(e.time("starts_at"), e.time("ends_at")),
        amount = when (val kind = e.str("kind")) {
            "percent_off" -> DiscountAmount.PercentOff(e.long("percent_bp").toInt())
            "fixed_amount_off" -> DiscountAmount.FixedAmountOff(Sen(e.long("amount_sen")))
            "free" -> DiscountAmount.Free
            else -> error("unknown discount kind $kind")
        },
        rounding = DiscountRounding.valueOf(e.str("rounding").uppercase()),
        scopes = e.array("scopes").map { it.jsonObject }.map {
            DiscountScope(
                kind = DiscountScopeKind.valueOf(it.str("kind").uppercase()),
                scopeId = it.strOrNull("id"),
                classScheme = it.strOrNull("class_scheme")?.let(::ClassSchemeId),
                vehicleClass = it.strOrNull("vehicle_class")?.let(::VehicleClass),
            )
        },
        excludedPlazas = e.array("excluded_plazas").map { PlazaId(it.jsonPrimitive.content) }.toSet(),
        sourceUrl = e.str("source_url"),
    )

    private fun pathPoint(p: JsonObject) = PathPoint(
        nodeId = p.longOrNull("node_id")?.let(::OsmNodeId),
        wayId = OsmWayId(p.long("way_id")),
        location = GeoPoint(p.double("lat"), p.double("lon")),
        at = p.time("at"),
    )

    private fun expected(e: JsonObject) = ExpectedEstimate(
        totalSen = e.long("total_sen"),
        rangeSen = e.longListOrNull("range_sen"),
        confidence = Confidence.valueOf(e.str("confidence")),
        items = e.array("items").map { it.jsonObject }.map {
            ExpectedItem(
                type = it.str("type"),
                plazaId = it.strOrNull("plaza_id"),
                networkId = it.strOrNull("network_id"),
                entry = it.strOrNull("entry"),
                exit = it.strOrNull("exit"),
                chargedSen = it.longOrNull("charged_sen"),
                rangeSen = it.longListOrNull("range_sen"),
                discountEventId = it.strOrNull("discount_event_id"),
            )
        },
    )
}

/** Projects an engine result onto the fields fixtures assert, for a readable diff on failure. */
fun TollEstimate.toExpectedForm() = ExpectedEstimate(
    totalSen = total.value,
    rangeSen = range?.let { listOf(it.min.value, it.max.value) },
    confidence = confidence,
    items = items.map { item ->
        when (item) {
            is TollItem.Open -> ExpectedItem(
                type = "open", plazaId = item.plazaId.value,
                chargedSen = item.charged.value, discountEventId = item.discountEventId?.value,
            )
            is TollItem.ClosedPair -> ExpectedItem(
                type = "closed_pair", networkId = item.networkId.value, entry = item.entry.value,
                exit = item.exit.value, chargedSen = item.charged.value, discountEventId = item.discountEventId?.value,
            )
            is TollItem.NotCharged -> ExpectedItem(type = "not_charged", plazaId = item.plazaId.value, chargedSen = 0)
            is TollItem.MissingFare -> ExpectedItem(
                type = "missing_fare", plazaId = item.plazaId.value, entry = item.entry?.value, chargedSen = null,
            )
            is TollItem.ClosedUnknownEntry -> ExpectedItem(
                type = "closed_unknown_entry", networkId = item.networkId.value, exit = item.exit.value,
                chargedSen = null, rangeSen = listOf(item.range.min.value, item.range.max.value),
            )
        }
    },
)

private fun JsonObject.value(key: String): JsonElement? = this[key]?.takeUnless { it is JsonNull }
private fun JsonObject.obj(key: String) = getValue(key).jsonObject
private fun JsonObject.array(key: String): JsonArray = value(key)?.jsonArray ?: JsonArray(emptyList())
private fun JsonObject.strOrNull(key: String) = value(key)?.jsonPrimitive?.contentOrNull
private fun JsonObject.str(key: String) = strOrNull(key) ?: error("missing \"$key\" in $this")
private fun JsonObject.longOrNull(key: String) = value(key)?.jsonPrimitive?.longOrNull
private fun JsonObject.long(key: String) = longOrNull(key) ?: error("missing number \"$key\" in $this")
private fun JsonObject.doubleOrNull(key: String) = value(key)?.jsonPrimitive?.doubleOrNull
private fun JsonObject.double(key: String) = doubleOrNull(key) ?: error("missing number \"$key\" in $this")
private fun JsonObject.longListOrNull(key: String) = value(key)?.jsonArray?.map { it.jsonPrimitive.longOrNull!! }
private fun JsonObject.timeOrNull(key: String) = strOrNull(key)?.let { OffsetDateTime.parse(it).toInstant() }
private fun JsonObject.time(key: String) = timeOrNull(key) ?: error("missing time \"$key\" in $this")
private fun JsonObject.period() = EffectivePeriod(time("effective_from"), timeOrNull("effective_to"))
