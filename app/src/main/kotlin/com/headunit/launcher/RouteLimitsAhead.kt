package com.headunit.launcher

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Posted speed limits along the route ahead, read from OpenStreetMap's roads, for when the route itself came without them
 * (Mapbox often has none for UK roads). They place the sign on the map where the limit changes, like Sygic does, and the small
 * "next limit" sign by the speedometer.
 */
object RouteLimitsAhead {
    /** The point [off] metres along [r]. */
    fun pointAt(r: Route, off: Double): LL? {
        if (r.pts.isEmpty()) return null
        if (r.pts.size < 2 || r.cum.size != r.pts.size) return r.pts.first()
        val o = off.coerceIn(0.0, r.cum.last())
        var lo = 0; var hi = r.cum.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (r.cum[mid] <= o) lo = mid else hi = mid - 1
        }
        val i = min(lo, r.pts.size - 2)
        val seg = r.cum[i + 1] - r.cum[i]
        val t = if (seg > 0) ((o - r.cum[i]) / seg).coerceIn(0.0, 1.0) else 0.0
        val a = r.pts[i]; val b = r.pts[i + 1]
        return LL(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
    }

    /** The route's points from [from] to [to] metres along it, at least [gap] metres apart (for a small Overpass request). */
    fun lineBetween(r: Route, from: Double, to: Double, gap: Double = 40.0): List<LL> {
        val out = ArrayList<LL>()
        pointAt(r, from)?.let { out += it }
        var last = from
        for (i in r.pts.indices) {
            if (i >= r.cum.size) break
            val c = r.cum[i]
            if (c <= from || c >= to) continue
            if (c - last >= gap) { out += r.pts[i]; last = c }
        }
        pointAt(r, to)?.let { out += it }
        return out
    }

    /**
     * The posted limits from [from] to [to] metres along [r], sampled every [step] metres against [ways]. Only limits that are
     * really posted (not guessed from the kind of road) count, and a stretch shorter than [minLen] is dropped as a junction blip.
     */
    fun sections(r: Route, from: Double, to: Double, ways: List<Overpass.Way>, country: String, step: Double = 20.0, minLen: Double = 45.0): List<LimitSec> {
        if (ways.isEmpty() || to <= from) return emptyList()
        val raw = ArrayList<LimitSec>()
        var off = from
        while (off <= to) {
            val p = pointAt(r, off)
            val a = pointAt(r, max(0.0, off - 12.0)); val b = pointAt(r, min(r.len, off + 12.0))
            if (p != null && a != null && b != null && Geo.dist(a, b) > 1.0) {
                val lim = Overpass.matchLimit(ways, p, Geo.bearing(a, b), country)
                if (lim != null && !lim.estimated && lim.sure && lim.ms > 2.0) {
                    val s0 = max(from, off - step / 2); val s1 = min(to, off + step / 2)
                    val last = raw.lastOrNull()
                    if (last != null && abs(last.ms - lim.ms) < 0.05 && abs(last.end - s0) < 1.0) raw[raw.size - 1] = LimitSec(last.off, s1, lim.ms)
                    else raw += LimitSec(s0, s1, lim.ms)
                }
            }
            off += step
        }
        // drop short blips (a side road matched at a junction), then join what's left back up
        val out = ArrayList<LimitSec>()
        for (s in raw) {
            if (s.end - s.off < minLen) continue
            val last = out.lastOrNull()
            if (last != null && abs(last.ms - s.ms) < 0.05 && s.off - last.end < minLen + step) out[out.size - 1] = LimitSec(last.off, s.end, s.ms)
            else out += s
        }
        return out
    }

    /** How much of [from]..[to] the sections [secs] cover, in metres. */
    fun covered(secs: List<LimitSec>, from: Double, to: Double): Double {
        var c = 0.0
        for (s in secs) {
            if (s.end <= from) continue
            if (s.off >= to) break
            c += min(s.end, to) - max(s.off, from)
        }
        return c
    }

    /** True when [off] lies inside one of [secs]. */
    fun inside(secs: List<LimitSec>, off: Double): Boolean = secs.any { off >= it.off && off < it.end }

    /**
     * The first change ahead in [secs] from [at], no further than [ahead] metres: (limit in m/s, metres to it), or null. [cur] is the
     * limit now (m/s, null when unknown); [skip] leaves out starts the route's own limits already cover.
     */
    fun nextChange(secs: List<LimitSec>, at: Double, ahead: Double, cur: Double?, skip: (Double) -> Boolean = { false }): Pair<Double, Double>? {
        var c = cur
        for (sc in secs) {
            if (sc.off <= at) { if (sc.end > at) c = sc.ms; continue }
            if (sc.off - at > ahead) break
            if (sc.ms < 2.0 || skip(sc.off)) continue
            if (c == null || abs(sc.ms - c) > 0.5) return sc.ms to (sc.off - at)
        }
        return null
    }
}
