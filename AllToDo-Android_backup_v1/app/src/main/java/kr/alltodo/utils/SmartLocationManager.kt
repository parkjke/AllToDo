package kr.alltodo.utils

import android.location.Location
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow

object SmartLocationManager {
    // Precision: 100,000 => 5 decimal places => ~1.1 meters resolution
    private const val PRECISION = 100000.0
    private const val BASE_ZOOM = 18f
    // At Zoom 18, we want 10m threshold. 
    // 10m in degrees (Lat) ~= 10 / 111000 = 0.00009 deg
    // In Integer units (x 100,000) => 9 units.
    private const val BASE_THRESHOLD_UNITS = 9 

    data class IntLocation(val lat: Int, val lon: Int)

    fun toIntLocation(loc: Location): IntLocation {
        return IntLocation(
            (loc.latitude * PRECISION).toInt(),
            (loc.longitude * PRECISION).toInt()
        )
    }

    /**
     * Determines if the map/location should be updated based on distance moved and zoom level.
     * Rule: Update if moved > 10m (at Zoom 18).
     * Threshold scales with Zoom: Lower Zoom (Far) -> Larger Threshold.
     * Formula: Threshold = 10m * 2^(18 - Zoom)
     */
    fun shouldUpdate(lastLoc: IntLocation?, newLoc: Location, currentZoom: Float): Boolean {
        if (lastLoc == null) return true

        val newIntLoc = toIntLocation(newLoc)
        val deltaLat = abs(lastLoc.lat - newIntLoc.lat)
        val deltaLon = abs(lastLoc.lon - newIntLoc.lon)

        // Calculate dynamic threshold
        // e.g. Zoom 18 -> Factor 1 -> 9 units (10m)
        // e.g. Zoom 15 -> Factor 8 -> 72 units (80m)
        // e.g. Zoom 20 -> Factor 0.25 -> 2 units (2.2m)
        // We cap max calculation at generous bounds to prevent overflow or weirdness
        val factor = 2.0.pow((BASE_ZOOM - currentZoom).toDouble())
        val threshold = (BASE_THRESHOLD_UNITS * factor).toInt().coerceAtLeast(2) // Min 2 units (~2m)
        
        // Simple Box Check (Manhattan-ish) for speed
        return deltaLat > threshold || deltaLon > threshold
    }

    /**
     * Checks if distance is > 500km using simplified integer math.
     * 500km = 500,000m.
     * Units = 500,000m / 1.11m ~= 450,000 units.
     */
    fun isFar(loc1: Location, loc2: Location): Boolean {
        val p1 = toIntLocation(loc1)
        val p2 = toIntLocation(loc2)
        
        val dy = (p1.lat - p2.lat).toLong()
        
        // Correct longitude for latitude shrinking
        val avgLatRad = Math.toRadians((loc1.latitude + loc2.latitude) / 2.0)
        val dx = ((p1.lon - p2.lon) * cos(avgLatRad)).toLong()
        
        val distSq = dx*dx + dy*dy
        val limit = 450000L // ~500km in units
        return distSq > (limit * limit)
    }

    /**
     * Checks if user has moved beyond 1/4 of the screen width from the map center.
     * @param user User's location (Int)
     * @param center Map center location (Int)
     * @param spanLon Map's longitude span (width) in Int units (deg * 100,000)
     */
    fun needsCentering(user: IntLocation, center: IntLocation, spanLon: Int): Boolean {
        val deltaLon = abs(user.lon - center.lon)
        val threshold = spanLon / 4
        return deltaLon > threshold
    }

    // [NEW] Ensure Min Span for Bounds (Prevent Max Zoom on Single Point)
    fun ensureMinSpan(bounds: com.google.android.gms.maps.model.LatLngBounds, minSpan: Double): com.google.android.gms.maps.model.LatLngBounds {
        val center = bounds.center
        val ne = bounds.northeast
        val sw = bounds.southwest
        
        var dLat = ne.latitude - sw.latitude
        var dLon = ne.longitude - sw.longitude
        
        if (dLat < minSpan) dLat = minSpan
        if (dLon < minSpan) dLon = minSpan
        
        val newNe = com.google.android.gms.maps.model.LatLng(center.latitude + dLat / 2, center.longitude + dLon / 2)
        val newSw = com.google.android.gms.maps.model.LatLng(center.latitude - dLat / 2, center.longitude - dLon / 2)
        
        return com.google.android.gms.maps.model.LatLngBounds(newSw, newNe)
    }
}
