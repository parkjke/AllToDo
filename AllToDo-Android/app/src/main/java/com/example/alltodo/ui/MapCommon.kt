package com.example.alltodo.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.example.alltodo.data.TodoItem
import com.example.alltodo.data.UserLog

sealed class UnifiedItem {
    abstract val latitude: Double
    abstract val longitude: Double
    abstract val timestamp: Long

    data class Todo(val item: TodoItem) : UnifiedItem() {
        override val latitude get() = item.latitude ?: 0.0
        override val longitude get() = item.longitude ?: 0.0
        override val timestamp get() = item.createdAt
    }

    data class History(val log: UserLog) : UnifiedItem() {
        override val latitude get() = log.latitude
        override val longitude get() = log.longitude
        override val timestamp get() = log.startTime
    }

    data class CurrentLocation(val lat: Double, val lon: Double) : UnifiedItem() {
        override val latitude get() = lat
        override val longitude get() = lon
        override val timestamp get() = System.currentTimeMillis()
    }
}

data class PinCluster(
    val latitude: Double,
    val longitude: Double,
    val items: List<UnifiedItem>,
    val type: String = "mixed",
    val hasMixed: Boolean = false
)

fun createRedDotBitmap(): Bitmap {
    val size = 40
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint()
    paint.color = android.graphics.Color.RED
    paint.isAntiAlias = true
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    return bitmap
}

fun generateDiamondPin(color: Int, count: Int): Bitmap? {
    val width = 100
    val height = 120
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        setColor(color)
    }

    val path = android.graphics.Path()
    path.moveTo(width / 2f, 0f)
    path.lineTo(width.toFloat(), height * 0.4f)
    path.lineTo(width / 2f, height.toFloat())
    path.lineTo(0f, height * 0.4f)
    path.close()

    canvas.drawPath(path, paint)

    if (count > 1) {
        paint.color = android.graphics.Color.WHITE
        val cx = width / 2f
        val cy = height * 0.4f
        val r = width / 4f
        canvas.drawCircle(cx, cy, r, paint)

        paint.color = color
        paint.textSize = 30f
        paint.textAlign = Paint.Align.CENTER
        val txt = if (count > 9) "9+" else count.toString()
        val bounds = android.graphics.Rect()
        paint.getTextBounds(txt, 0, txt.length, bounds)
        canvas.drawText(txt, cx, cy - bounds.exactCenterY(), paint)
    } else {
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(width / 2f, height * 0.4f, width / 8f, paint)
    }

    return bitmap
}
