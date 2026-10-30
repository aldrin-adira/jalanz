package io.github.aldrinadira.jalanz.core.toll

import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot

enum class DetectionMethod { NODE, GEOMETRIC }

/** The route passes plaza [plazaId], arriving on [inWayId], at time [at] (04 §5). */
data class PlazaCrossing(
    val plazaId: PlazaId,
    val inWayId: OsmWayId?,
    val at: Instant,
    val method: DetectionMethod,
)

/**
 * Finds the toll plazas a route passes (04 §5), in path order.
 *
 * 1. Node match: a path point on a plaza's OSM node, arriving on the ref's `in_way_id` (or any way if null).
 *    Way-aware, so a mainline passing a ramp plaza is not charged (F6).
 * 2. Geometric fallback, only for plazas none of whose nodes are on the path: within [MAX_DISTANCE_M] of the
 *    path and heading within [MAX_BEARING_DIFF_DEG] of a known plaza bearing (distance only if none is known).
 *    Callers lower confidence for these (F9).
 */
class PlazaDetector(private val dataset: TollDataset) {
    private val refsByNode = dataset.osmRefs.groupBy { it.osmNodeId }
    private val refsByPlaza = dataset.osmRefs.groupBy { it.plazaId }

    fun detect(path: RoutePath): List<PlazaCrossing> {
        val found = nodeCrossings(path) + geometricCrossings(path)
        return found.sortedBy { it.position }
            .distinctBy { it.position to it.crossing.plazaId }
            .map { it.crossing }
    }

    private fun nodeCrossings(path: RoutePath): List<Positioned> = buildList {
        for ((i, point) in path.points.withIndex()) {
            val node = point.nodeId ?: continue
            for (ref in refsByNode[node].orEmpty()) {
                if (ref.inWayId != null && ref.inWayId != point.wayId) continue
                if (!isActive(ref.plazaId, point.at)) continue
                add(Positioned(i.toDouble(), PlazaCrossing(ref.plazaId, point.wayId, point.at, DetectionMethod.NODE)))
            }
        }
    }

    private fun geometricCrossings(path: RoutePath): List<Positioned> {
        val points = path.points
        if (points.size < 2) return emptyList()
        val nodesOnPath = points.mapNotNullTo(HashSet()) { it.nodeId }
        val box = BoundingBox.around(points.map { it.location }, marginM = PREFILTER_MARGIN_M)

        return dataset.plazas.mapNotNull { plaza ->
            val refs = refsByPlaza[plaza.id].orEmpty()
            if (refs.any { it.osmNodeId in nodesOnPath }) return@mapNotNull null
            if (!box.contains(plaza.location)) return@mapNotNull null

            val nearest = nearestSegment(points, plaza.location)
            if (nearest.distanceM > MAX_DISTANCE_M) return@mapNotNull null
            val bearings = refs.mapNotNull { it.bearingDeg }
            if (bearings.isNotEmpty() && bearings.none { angleDiff(it, nearest.bearingDeg) <= MAX_BEARING_DIFF_DEG }) {
                return@mapNotNull null
            }
            val from = points[nearest.index]
            val to = points[nearest.index + 1]
            val at = from.at + Duration.between(from.at, to.at).multipliedBy((nearest.fraction * 1000).toLong()).dividedBy(1000)
            if (!plaza.active.contains(at)) return@mapNotNull null
            Positioned(nearest.index + nearest.fraction, PlazaCrossing(plaza.id, to.wayId, at, DetectionMethod.GEOMETRIC))
        }
    }

    private fun isActive(plazaId: PlazaId, t: Instant) = dataset.plaza(plazaId)?.active?.contains(t) == true

    private data class Positioned(val position: Double, val crossing: PlazaCrossing)

    companion object {
        const val MAX_DISTANCE_M = 15.0
        const val MAX_BEARING_DIFF_DEG = 45.0
        private const val PREFILTER_MARGIN_M = 30.0
    }
}

// Small-distance geometry in a local flat projection around the plaza: accurate to well under a metre at the
// 15 m scale that matters here.

private const val EARTH_RADIUS_M = 6_371_008.8
private const val METRES_PER_DEGREE = EARTH_RADIUS_M * Math.PI / 180

private data class Nearest(val index: Int, val fraction: Double, val distanceM: Double, val bearingDeg: Double)

private fun nearestSegment(points: List<PathPoint>, target: GeoPoint): Nearest {
    val cosLat = cos(Math.toRadians(target.lat))
    fun local(p: GeoPoint) = Pair(
        (p.lon - target.lon) * METRES_PER_DEGREE * cosLat,
        (p.lat - target.lat) * METRES_PER_DEGREE,
    )
    var best: Nearest? = null
    for (i in 0 until points.size - 1) {
        val (ax, ay) = local(points[i].location)
        val (bx, by) = local(points[i + 1].location)
        val dx = bx - ax
        val dy = by - ay
        val lengthSq = dx * dx + dy * dy
        val t = if (lengthSq == 0.0) 0.0 else ((-ax * dx - ay * dy) / lengthSq).coerceIn(0.0, 1.0)
        val distance = hypot(ax + t * dx, ay + t * dy)
        if (best == null || distance < best.distanceM) {
            val bearing = (Math.toDegrees(atan2(dx, dy)) + 360) % 360
            best = Nearest(i, t, distance, bearing)
        }
    }
    return best!!
}

private fun angleDiff(a: Double, b: Double): Double {
    val d = abs(a - b) % 360
    return if (d > 180) 360 - d else d
}

private data class BoundingBox(val minLat: Double, val maxLat: Double, val minLon: Double, val maxLon: Double) {
    fun contains(p: GeoPoint) = p.lat in minLat..maxLat && p.lon in minLon..maxLon

    companion object {
        fun around(points: List<GeoPoint>, marginM: Double): BoundingBox {
            val minLat = points.minOf { it.lat }
            val maxLat = points.maxOf { it.lat }
            val latMargin = marginM / METRES_PER_DEGREE
            val lonMargin = marginM / (METRES_PER_DEGREE * cos(Math.toRadians(maxOf(abs(minLat), abs(maxLat)))))
            return BoundingBox(
                minLat - latMargin, maxLat + latMargin,
                points.minOf { it.lon } - lonMargin, points.maxOf { it.lon } + lonMargin,
            )
        }
    }
}
