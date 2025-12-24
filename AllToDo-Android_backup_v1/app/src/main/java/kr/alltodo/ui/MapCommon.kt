package kr.alltodo.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kr.alltodo.data.TodoItem
import kr.alltodo.data.UserLog

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
    
    fun getPinResId(): Int {
        return when (this) {
            is Todo -> {
                if (item.source != "local") {
                    // Receive
                    if (item.completed) kr.alltodo.R.drawable.pin_receive_done
                    else kr.alltodo.R.drawable.pin_receive_ready
                } else {
                    // Local Todo
                    if (item.completed) kr.alltodo.R.drawable.pin_todo_done
                    else kr.alltodo.R.drawable.pin_todo_ready
                }
            }
            is History -> kr.alltodo.R.drawable.pin_history
            is CurrentLocation -> kr.alltodo.R.drawable.pin_current
        }
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

// Cache for Diamond Pins
private val diamondBitmapCache = android.util.LruCache<String, Bitmap>(50)

fun generateDiamondPin(color: Int, count: Int): Bitmap? {
    val key = "$color-$count"
    val cached = diamondBitmapCache.get(key)
    if (cached != null) return cached

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

    diamondBitmapCache.put(key, bitmap)
    return bitmap
}

// [FIX] Bitmap Cache to prevent UI Freeze
private val bitmapCache = android.util.LruCache<String, Bitmap>(100) // Cache last 100 icons

// [FIX] Use Provider Check or Scale if needed, but for now cache uses Scale.
fun getCachedClusterBitmap(context: android.content.Context, count: Int, baseResId: Int, badgeColor: Int, scale: Float = 1.0f): com.google.android.gms.maps.model.BitmapDescriptor {
    val key = "$count-$baseResId-$badgeColor-$scale"
    val cached = bitmapCache.get(key)
    
    val bitmap = if (cached != null) {
        cached
    } else {
        // Create new based on Scale
        val newBitmap = PinImageManager.createClusterPin(context, baseResId, count, badgeColor, scale) ?: return com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker()
        bitmapCache.put(key, newBitmap)
        newBitmap
    }
    
    return com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap)
}

// [NEW] Google Pin (Standard)
fun createGooglePinBitmap(context: android.content.Context, count: Int, baseResId: Int, badgeColor: Int): Bitmap {
    return PinImageManager.createClusterPin(context, baseResId, count, badgeColor, 1.0f) ?: createRedDotBitmap()
}

// [NEW] Kakao Pin (Uses Pre-scaled Bitmap Resource, No Runtime Image Scaling)
fun createKakaoPinBitmap(context: android.content.Context, count: Int, baseResId: Int, badgeColor: Int): Bitmap {
    // val scale = 0.85f // [ROLLBACK INFO] Old value
    val scale = 0.7f // [NEW] Adjusted to 0.7 as requested
    
    return PinImageManager.createClusterPin(context, baseResId, count, badgeColor, scale) ?: createRedDotBitmap()
}
