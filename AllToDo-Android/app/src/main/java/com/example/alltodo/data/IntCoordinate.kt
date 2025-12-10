package com.example.alltodo.data

import kotlin.math.roundToInt

data class IntCoordinate(
    val lat: Int,
    val lng: Int
) {
    fun toDoublePair(): Pair<Double, Double> {
        return Pair(lat / SCALE.toDouble(), lng / SCALE.toDouble())
    }

    companion object {
        const val SCALE = 100_000

        fun fromDouble(lat: Double, lng: Double): IntCoordinate {
            return IntCoordinate(
                (lat * SCALE).roundToInt(),
                (lng * SCALE).roundToInt()
            )
        }
    }
}
