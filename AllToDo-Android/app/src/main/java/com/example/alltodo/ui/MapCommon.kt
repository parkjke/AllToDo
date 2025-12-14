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
    
    fun getPinResId(): Int {
        return when (this) {
            is Todo -> {
                if (item.source != "local") {
                    // Receive
                    if (item.completed) com.example.alltodo.R.drawable.pin_receive_done
                    else com.example.alltodo.R.drawable.pin_receive_ready
                } else {
                    // Local Todo
                    if (item.completed) com.example.alltodo.R.drawable.pin_todo_done
                    else com.example.alltodo.R.drawable.pin_todo_ready
                }
            }
            is History -> com.example.alltodo.R.drawable.pin_history
            is CurrentLocation -> com.example.alltodo.R.drawable.pin_current
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

fun getCachedClusterBitmap(context: android.content.Context, count: Int, baseResId: Int, badgeColor: Int): com.google.android.gms.maps.model.BitmapDescriptor {
    val key = "$count-$baseResId-$badgeColor"
    val cached = bitmapCache.get(key)
    
    val bitmap = if (cached != null) {
        cached
    } else {
        // Create new
        val newBitmap = createClusterBitmapInternal(context, count, baseResId, badgeColor)
        bitmapCache.put(key, newBitmap)
        newBitmap
    }
    
    return com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap)
}

// Previously named createClusterBitmap
private fun createClusterBitmapInternal(context: android.content.Context, count: Int, baseResId: Int, badgeColor: Int): Bitmap {
    // [FIX] Density-aware sizing to match PinImageManager (40dp x 50dp) + Padding for Badge Overhang
    val density = context.resources.displayMetrics.density
    val pinW = (40 * density).toInt()
    val pinH = (50 * density).toInt()
    val padding = (10 * density).toInt() // Extra space for overhang
    
    val sizeW = pinW + padding
    val sizeH = pinH + padding
    
    val bitmap = Bitmap.createBitmap(sizeW, sizeH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    
    // 1. Draw Base Icon (Shifted down/left to make room for top/right badge)
    val cachedBase = PinImageManager.getPinBitmap(baseResId)
    if (cachedBase != null) {
        val srcRect = android.graphics.Rect(0, 0, cachedBase.width, cachedBase.height)
        val dstRect = android.graphics.Rect(0, padding, pinW, pinH + padding)
        canvas.drawBitmap(cachedBase, srcRect, dstRect, null)
    } else {
        // Fallback: Create from Drawable
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, baseResId)
        drawable?.setBounds(0, padding, pinW, pinH + padding)
        drawable?.draw(canvas)
    }
    
    // 2. Draw Badge (Overhanging Top-Right)
    if (count > 0) {
        val paint = Paint().apply {
            isAntiAlias = true
            color = badgeColor
            style = Paint.Style.FILL
        }
        
        val badgeSize = 20f * density
        // Center on the top-right corner of the PIN image
        // Pin ends at x=pinW, y=padding.
        val cx = pinW.toFloat()
        val cy = padding.toFloat()
        
        // White Border
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(cx, cy, badgeSize/2 + 2 * density, paint) // Border 2dp
        
        // Color Bg
        paint.color = badgeColor
        canvas.drawCircle(cx, cy, badgeSize/2, paint)
        
        // Text
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 12f * density // 12sp equivalent
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        
        val text = if (count > 9) "9+" else count.toString()
        val bounds = android.graphics.Rect() // [FIX] Fully qualified name
        paint.getTextBounds(text, 0, text.length, bounds)
        val yOff = bounds.height().toFloat() / 2f // [FIX] Cast to Float
        
        canvas.drawText(text, cx, cy + yOff, paint)
    }
    
    return bitmap
}
