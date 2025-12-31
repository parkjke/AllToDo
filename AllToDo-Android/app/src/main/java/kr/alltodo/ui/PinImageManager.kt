package kr.alltodo.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import kr.alltodo.R

object PinImageManager {
    private const val TAG = "PinImageManager"
    private const val TARGET_WIDTH_DP = 40
    private const val TARGET_HEIGHT_DP = 50

    // Memory Cache
    private val bitmapCache = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()

    fun clearCache() {
        bitmapCache.clear()
    }

    /**
     * Fetches a static map pin from resources and optionally draws a cluster badge on top.
     */
    fun fetchStaticPin(
        context: Context,
        pinId: String,
        count: Int = 0,
        badgeColor: Int = 0,
        scale: Float = 1.0f
    ): Bitmap? {
        val density = context.resources.displayMetrics.density
        val cacheKey = "pin_${pinId}_c${count}_s${scale}_d$density"
        
        if (bitmapCache.containsKey(cacheKey)) {
            val cached = bitmapCache[cacheKey]
            if (cached != null && !cached.isRecycled) {
                return cached
            }
        }

        return try {
            // 1. Resolve Resource ID
            val resName = "map_pin_$pinId"
            val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
             if (resId == 0) {
                 return null
             }

            // 2. Load Base Bitmap
            val options = BitmapFactory.Options().apply {
                inScaled = true // Ensure correct density scaling if needed
            }
            val baseBitmap = BitmapFactory.decodeResource(context.resources, resId, options) ?: return null

            // 3. Create Canvas for Scale & Badge
            val pinW = (TARGET_WIDTH_DP * density * scale).toInt().coerceAtLeast(1)
            val pinH = (TARGET_HEIGHT_DP * density * scale).toInt().coerceAtLeast(1)
            
            // Match iOS padding for anchor parity (20/51 = 0.392)
            val padding = if (count > 1) (11 * density * scale).toInt() else 0
            
            val totalW = (pinW + padding).coerceAtLeast(1)
            val totalH = (pinH + (if (count > 1) (5 * density * scale).toInt() else 0)).coerceAtLeast(1)
            
            val bitmap = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            // Draw Shield with Padding (Bottom-Left aligned relative to badge area)
            val destRect = Rect(0, padding, pinW, pinH + padding)
            canvas.drawBitmap(baseBitmap, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))

            // 4. Draw Badge if Count > 1
            if (count > 1) {
                val badgeRadius = 10f * density * scale
                val borderThickness = 1.5f * density * scale
                val totalBadgeRadius = badgeRadius + borderThickness
                
                val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                }
                
                // Overlap slightly with pin shoulder
                val overlap = 3 * density * scale
                val cx = pinW.toFloat() - overlap
                val cy = padding.toFloat() + overlap
                
                // A. Border
                badgePaint.color = android.graphics.Color.WHITE
                canvas.drawCircle(cx, cy, totalBadgeRadius, badgePaint)
                
                // B. Fill
                badgePaint.color = badgeColor
                canvas.drawCircle(cx, cy, badgeRadius, badgePaint)
                
                // C. Text
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textSize = (10f * density * scale).coerceAtLeast(9f * density)
                }
                
                val text = if (count > 99) "99+" else count.toString()
                val bounds = Rect()
                textPaint.getTextBounds(text, 0, text.length, bounds)
                val yOff = bounds.height() / 2f - bounds.bottom
                
                canvas.drawText(text, cx, cy + yOff, textPaint)
            }
            
            bitmapCache[cacheKey] = bitmap
            bitmapCache[cacheKey] = bitmap
            bitmap
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getResourceId(context: Context, name: String): Int {
         return context.resources.getIdentifier(name, "drawable", context.packageName)
    }

    /**
     * Creates a simple circular dot bitmap.
     */
    fun createDotBitmap(context: Context, color: Int, radiusDp: Float): Bitmap {
        val density = context.resources.displayMetrics.density
        val radiusPx = (radiusDp * density)
        val diameter = (radiusPx * 2).toInt()
        
        val bitmap = Bitmap.createBitmap(diameter, diameter, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        
        canvas.drawCircle(radiusPx, radiusPx, radiusPx, paint)
        return bitmap
    }
}
