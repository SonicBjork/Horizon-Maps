package com.headunit.launcher
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RouteLimitsAheadTest {
    private fun route(points: List<LL>): Route {
        val cum = DoubleArray(points.size)
        for (i in 1 until points.size) cum[i] = cum[i - 1] + Geo.dist(points[i - 1], points[i])
        return Route(points, cum, emptyList(), cum.last(), 0.0, 0.0)
    }
    // a straight road north out of Bingley: 30 mph for the first 600 m, then 40 mph; a side road crosses at 300 m
    private val lon = -1.84
    private fun lat(m: Double) = 53.85 + m / 111320.0
    private val r = route((0..20).map { LL(lat(it * 50.0), lon) })
    private val ways = listOf(
        Overpass.Way(1, JSONObject().put("highway", "primary").put("maxspeed", "30 mph"), listOf(LL(lat(-50.0), lon), LL(lat(600.0), lon))),
        Overpass.Way(2, JSONObject().put("highway", "primary").put("maxspeed", "40 mph"), listOf(LL(lat(600.0), lon), LL(lat(1100.0), lon))),
        Overpass.Way(3, JSONObject().put("highway", "residential").put("maxspeed", "20 mph"), listOf(LL(lat(300.0), lon - 0.003), LL(lat(300.0), lon + 0.003))),
    )

    @Test fun findsTheChangeWhereTheRoadsMeet() {
        val secs = RouteLimitsAhead.sections(r, 0.0, r.len, ways, "GB")
        assertEquals(2, secs.size)
        assertEquals(30 * 0.44704, secs[0].ms, 0.01)
        assertEquals(40 * 0.44704, secs[1].ms, 0.01)
        assertEquals(600.0, secs[1].off, 25.0)
        val nx = RouteLimitsAhead.nextChange(secs, 200.0, 800.0, 30 * 0.44704)
        assertNotNull(nx)
        assertEquals(40 * 0.44704, nx!!.first, 0.01)
        assertEquals(400.0, nx.second, 25.0)
    }

    @Test fun noChangeBeyondTheWindowOrWhenTheSame() {
        val secs = RouteLimitsAhead.sections(r, 0.0, r.len, ways, "GB")
        assertNull(RouteLimitsAhead.nextChange(secs, 0.0, 300.0, 30 * 0.44704))
        assertNull(RouteLimitsAhead.nextChange(secs, 700.0, 800.0, 40 * 0.44704))
    }

    @Test fun guessedLimitsDoNotCount() {
        val unposted = listOf(Overpass.Way(9, JSONObject().put("highway", "primary"), listOf(LL(lat(-50.0), lon), LL(lat(1100.0), lon))))
        assertTrue(RouteLimitsAhead.sections(r, 0.0, r.len, unposted, "GB").isEmpty())
    }

    @Test fun skipsWhatTheRouteCovers() {
        val secs = RouteLimitsAhead.sections(r, 0.0, r.len, ways, "GB")
        val routeOwn = listOf(LimitSec(500.0, 1000.0, 40 * 0.44704))
        assertNull(RouteLimitsAhead.nextChange(secs, 200.0, 800.0, 30 * 0.44704) { RouteLimitsAhead.inside(routeOwn, it) })
        assertEquals(500.0, RouteLimitsAhead.covered(routeOwn, 0.0, 1000.0), 0.1)
    }

    @Test fun pointAtAndLine() {
        val p = RouteLimitsAhead.pointAt(r, 125.0)!!
        assertEquals(125.0, Geo.dist(r.pts[0], p), 0.5)
        val line = RouteLimitsAhead.lineBetween(r, 100.0, 400.0)
        assertTrue(line.size in 3..10)
    }
}
