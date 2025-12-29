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
    
    // [NEW] Dynamic Composition Properties
    open val shieldName: String get() = "pin_shield_1x"
    open val markName: String get() = "pin_mark_10"

    data class Todo(val item: TodoItem) : UnifiedItem() {
        override val latitude get() = item.latitude ?: 0.0
        override val longitude get() = item.longitude ?: 0.0
        override val timestamp get() = item.created_at
        
        override val shieldName: String
            get() {
                 if (item.source != "local") {
                     // Server (20)
                     return "pin_shield_2x"
                 }
                 return "pin_shield_1x" // Local (10)
            }
            
        override val markName: String
            get() {
                 if (item.source != "local") {
                     return "pin_mark_20"
                 }
                 // Local
                 return if (item.completed) "pin_mark_12" else "pin_mark_10"
            }
    }

    data class History(val item: TodoItem) : UnifiedItem() {
        override val latitude get() = item.latitude ?: 0.0
        override val longitude get() = item.longitude ?: 0.0
        override val timestamp get() = item.begin_time ?: item.created_at
        
        override val shieldName get() = "pin_shield_0x"
        override val markName get() = "pin_mark_01"
    }

    data class CurrentLocation(val lat: Double, val lon: Double) : UnifiedItem() {
        override val latitude get() = lat
        override val longitude get() = lon
        override val timestamp get() = System.currentTimeMillis()
        
        override val shieldName get() = "pin_shield_0x"
        override val markName get() = "pin_mark_00"
    }
}

data class PinClusterItem(
    val latitude: Double,
    val longitude: Double,
    val count: Int,
    val items: List<UnifiedItem>
)

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

// [FIX] Update signature for Dynamic Composition
fun getCachedClusterBitmap(
    context: android.content.Context, 
    count: Int, 
    shieldResId: Int, 
    markResId: Int,
    badgeColor: Int, 
    scale: Float = 1.0f
): com.google.android.gms.maps.model.BitmapDescriptor {
    val key = "cluster-$count-$shieldResId-$markResId-$badgeColor-$scale"
    val cached = bitmapCache.get(key)
    
    val bitmap = if (cached != null) {
        cached
    } else {
        // Create new based on Scale
        val newBitmap = PinImageManager.fetchCompositePin(context, shieldResId, markResId, count, badgeColor, scale) 
            ?: createRedDotBitmap()
        bitmapCache.put(key, newBitmap)
        newBitmap
    }
    
    return com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap)
}

// [NEW] Google Pin (Standard)
// Takes ResIDs directly (resolved by Caller)
fun createGooglePinBitmap(context: android.content.Context, count: Int, shieldResId: Int, markResId: Int, badgeColor: Int): Bitmap {
    return PinImageManager.fetchCompositePin(context, shieldResId, markResId, count, badgeColor, 1.0f) ?: createRedDotBitmap()
}

// [NEW] Kakao Pin
fun createKakaoPinBitmap(context: android.content.Context, count: Int, shieldResId: Int, markResId: Int, badgeColor: Int): Bitmap {
    val scale = 0.7f // Adjusted
    return PinImageManager.fetchCompositePin(context, shieldResId, markResId, count, badgeColor, scale) ?: createRedDotBitmap()
}

// [NEW] Naver Pin
fun createNaverPinBitmap(context: android.content.Context, count: Int, shieldResId: Int, markResId: Int, badgeColor: Int): Bitmap {
    val scale = 1.0f 
    return PinImageManager.fetchCompositePin(context, shieldResId, markResId, count, badgeColor, scale) ?: createRedDotBitmap()
}
