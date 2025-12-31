package kr.alltodo.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kr.alltodo.data.TodoItem
import kr.alltodo.data.UserLog

sealed class UnifiedItem {
    abstract val intLat: Int
    abstract val intLng: Int
    abstract val timestamp: Long
    
    val latitude: Double get() = intLat / 100_000.0
    val longitude: Double get() = intLng / 100_000.0
    
    // [NEW] Static Bitmap PIN ID
    open val pinId: String get() = "10"

    data class Todo(val item: TodoItem) : UnifiedItem() {
        override val intLat get() = item.int_lat ?: 0
        override val intLng get() = item.int_long ?: 0
        override val timestamp get() = item.created_at
        
        override val pinId: String
            get() {
                 if (item.source != "local") {
                     return "20" // Server
                 }
                 // Local
                 return if (item.completed) "12" else "10"
            }
    }

    data class History(val item: TodoItem) : UnifiedItem() {
        override val intLat get() = item.int_lat ?: 0
        override val intLng get() = item.int_long ?: 0
        override val timestamp get() = item.begin_time ?: item.created_at
        
        override val pinId get() = "01"
    }

    data class CurrentLocation(val lat: Double, val lon: Double) : UnifiedItem() {
        override val intLat get() = (lat * 100_000).toInt()
        override val intLng get() = (lon * 100_000).toInt()
        override val timestamp get() = System.currentTimeMillis()
        
        override val pinId get() = "00"
    }
}

data class PinClusterItem(
    val intLat: Int,
    val intLng: Int,
    val count: Int,
    val items: List<UnifiedItem>
) {
    val latitude: Double get() = intLat / 100_000.0
    val longitude: Double get() = intLng / 100_000.0
}

// Legacy PinCluster (to be removed if unused)
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

// [FIX] Bitmap Cache to prevent UI Freeze
private val bitmapCache = android.util.LruCache<String, Bitmap>(100) // Cache last 100 icons

// [FIX] Update signature for Static Bitmaps
fun getCachedClusterBitmap(
    context: android.content.Context, 
    count: Int, 
    pinId: String,
    badgeColor: Int, 
    scale: Float = 1.0f
): com.google.android.gms.maps.model.BitmapDescriptor {
    val key = "cluster-$count-$pinId-$badgeColor-$scale"
    val cached = bitmapCache.get(key)
    
    val bitmap = if (cached != null) {
        cached
    } else {
        // Create new based on Scale
        val newBitmap = PinImageManager.fetchStaticPin(context, pinId, count, badgeColor, scale) 
            ?: createRedDotBitmap()
        bitmapCache.put(key, newBitmap)
        newBitmap
    }
    
    return com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap)
}

// [NEW] Google Pin (Standard)
fun createGooglePinBitmap(context: android.content.Context, count: Int, pinId: String, badgeColor: Int): Bitmap {
    return PinImageManager.fetchStaticPin(context, pinId, count, badgeColor, 1.0f) ?: createRedDotBitmap()
}

// [NEW] Kakao Pin
fun createKakaoPinBitmap(context: android.content.Context, count: Int, pinId: String, badgeColor: Int): Bitmap {
    val scale = 0.7f // Adjusted for Kakao consistency
    return PinImageManager.fetchStaticPin(context, pinId, count, badgeColor, scale) ?: createRedDotBitmap()
}

// [NEW] Naver Pin
fun createNaverPinBitmap(context: android.content.Context, count: Int, pinId: String, badgeColor: Int): Bitmap {
    val scale = 1.0f 
    return PinImageManager.fetchStaticPin(context, pinId, count, badgeColor, scale) ?: createRedDotBitmap()
}
