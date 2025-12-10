package com.example.alltodo.data

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object TrajectoryCompressor {

    fun onlineCompress(
        points: List<IntCoordinate>,
        minDistMeters: Double = 3.0,
        angleThreshDegrees: Double = 10.0
    ): List<IntCoordinate> {
        if (points.isEmpty()) return emptyList()
        if (points.size <= 2) return points

        val compressed = ArrayList<IntCoordinate>()
        compressed.add(points[0])
        var lastKept = points[0]

        for (i in 1 until points.size - 1) {
            val current = points[i]
            val distance = distanceBetween(lastKept, current)

            if (distance >= minDistMeters) {
                val nextPoint = points[i + 1]
                val bearing1 = bearingBetween(lastKept, current)
                val bearing2 = bearingBetween(current, nextPoint)
                var angleDiff = abs(bearing1 - bearing2)
                if (angleDiff > 180) {
                    angleDiff = 360 - angleDiff
                }

                if (angleDiff >= angleThreshDegrees) {
                    compressed.add(current)
                    lastKept = current
                }
            }
        }
        compressed.add(points.last())
        return compressed
    }

    private fun distanceBetween(p1: IntCoordinate, p2: IntCoordinate): Double {
        val (lat1, lng1) = p1.toDoublePair()
        val (lat2, lng2) = p2.toDoublePair()
        
        val R = 6371000.0 // Earth radius in meters
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lng2 - lng1)

        val a = sin(deltaPhi / 2) * sin(deltaPhi / 2) +
                cos(phi1) * cos(phi2) *
                sin(deltaLambda / 2) * sin(deltaLambda / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return R * c
    }

    private fun bearingBetween(p1: IntCoordinate, p2: IntCoordinate): Double {
        val (lat1, lng1) = p1.toDoublePair()
        val (lat2, lng2) = p2.toDoublePair()

        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lng2 - lng1)

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) -
                sin(phi1) * cos(phi2) * cos(deltaLambda)

        val theta = atan2(y, x)
        return (Math.toDegrees(theta) + 360) % 360
    }
}
