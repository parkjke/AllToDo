package kr.alltodo.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import androidx.core.content.ContextCompat
import kr.alltodo.R
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

object PinImageManager {
    private const val TAG = "PinImageManager"
    private const val HEADER_SIGNATURE = "ALLTODO_V6" // [FIX] V6: iOS Style Badge Alignment
    private const val TARGET_WIDTH_DP = 40 // Standard Pin Width
    private const val TARGET_HEIGHT_DP = 50 // Standard Pin Height

    // Cache in memory
    private val bitmapCache = mutableMapOf<Int, Bitmap>()

    // Mapping: Drawable Res ID -> Basename (No extension)
    private val managedPins = mapOf(
        R.drawable.pin_current to "pin_current_v1",
        R.drawable.pin_history to "pin_history_v1",
        R.drawable.pin_todo_ready to "pin_todo_ready_v1",
        R.drawable.pin_todo_done to "pin_todo_done_v1",
        R.drawable.pin_receive_ready to "pin_receive_ready_v1",
        R.drawable.pin_receive_done to "pin_receive_done_v1"
    )

    fun initialize(context: Context) {
        val pinsDir = File(context.filesDir, "pins")
        if (!pinsDir.exists()) {
            pinsDir.mkdirs()
        }

        val density = context.resources.displayMetrics.density
        // Suffix ensures specific bitmap for this screen density
        val densitySuffix = "_d$density.png" 

        managedPins.forEach { (resId, basename) ->
            val filename = "$basename$densitySuffix"
            val file = File(pinsDir, filename)
            var bitmap: Bitmap? = null

            if (file.exists()) {
                bitmap = loadBitmapWithParity(file)
            }
            if (bitmap == null) {
                bitmap = createBitmapFromVector(context, resId)
                if (bitmap != null) {
                    saveBitmapWithParity(file, bitmap)
                }
            }
            if (bitmap != null) {
                bitmapCache[resId] = bitmap
            }
        }
    }

    fun clearCacheAndRebuild(context: Context) {
        bitmapCache.clear()
        val pinsDir = File(context.filesDir, "pins")
        if (pinsDir.exists()) {
            pinsDir.listFiles()?.forEach { it.delete() }
        }
        initialize(context)
    }

    fun getPinBitmap(resId: Int): Bitmap? {
        return bitmapCache[resId]
    }

    private fun loadBitmapWithParity(file: File): Bitmap? {
        try {
            FileInputStream(file).use { fis ->
                val headerBytes = ByteArray(HEADER_SIGNATURE.length)
                val read = fis.read(headerBytes)
                if (read != HEADER_SIGNATURE.length) return null

                val signature = String(headerBytes, StandardCharsets.UTF_8)
                if (signature != HEADER_SIGNATURE) return null

                return BitmapFactory.decodeStream(fis)
            }
        } catch (e: Exception) {
            return null
        }
    }

    private fun saveBitmapWithParity(file: File, bitmap: Bitmap) {
        try {
            FileOutputStream(file).use { fos ->
                fos.write(HEADER_SIGNATURE.toByteArray(StandardCharsets.UTF_8))
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
        } catch (e: Exception) {
        }
    }

    fun getPinList(): List<Pair<Int, String>> {
        return managedPins.toList()
    }

    fun createBitmapFromVector(context: Context, resId: Int, densityMultiplier: Float? = null): Bitmap? {
        try {
            val drawable = ContextCompat.getDrawable(context, resId) ?: return null
            
            val density = densityMultiplier ?: context.resources.displayMetrics.density
            val w = (TARGET_WIDTH_DP * density).toInt()
            val h = (TARGET_HEIGHT_DP * density).toInt()
            
            val intrW = drawable.intrinsicWidth
            val intrH = drawable.intrinsicHeight
            val finalW: Int
            val finalH: Int
            
            if (intrW > 0 && intrH > 0) {
                 val aspect = intrW.toFloat() / intrH.toFloat()
                 if (aspect > 1) { // Wide
                     finalW = w
                     finalH = (w / aspect).toInt()
                 } else { // Tall
                     finalH = h
                     finalW = (h * aspect).toInt()
                 }
            } else {
                finalW = w
                finalH = h
            }

            val bitmap = Bitmap.createBitmap(finalW, finalH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return bitmap
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Creates a Cluster Pin with Badge.
     * Refined for iOS Aesthetic: Top-right placement with crisp rendering.
     */
    fun createClusterPin(context: Context, resId: Int, count: Int, badgeColor: Int, scale: Float): Bitmap? {
        val density = context.resources.displayMetrics.density
        
        // Base Size (40x50 dp) * Scale
        val pinW = (TARGET_WIDTH_DP * density * scale).toInt()
        val pinH = (TARGET_HEIGHT_DP * density * scale).toInt()
        
        // Padding for Badge (Badge can overflow the 40x50 box)
        val badgeRadius = 10f * density * scale
        val padding = (badgeRadius * 1.2f).toInt() 
        
        val sizeW = pinW + padding
        val sizeH = pinH + padding
        
        val bitmap = Bitmap.createBitmap(sizeW, sizeH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // 1. Base Icon
        val baseBitmap = getPinBitmap(resId) ?: createBitmapFromVector(context, resId)
        if (baseBitmap != null) {
            val srcRect = Rect(0, 0, baseBitmap.width, baseBitmap.height)
            // Draw pin at left-bottom of the expanded canvas
            val destRect = Rect(0, padding, pinW, pinH + padding)
            canvas.drawBitmap(baseBitmap, srcRect, destRect, null)
        } else {
            return null
        }
        
        // 2. Badge (iOS Style: Top-Right of the Pin)
        if (count > 0) {
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.FILL
            }
            
            val cx = pinW.toFloat() - (2 * density * scale) 
            val cy = padding.toFloat() + (2 * density * scale) 
            
            // A. Badge Border
            paint.color = android.graphics.Color.WHITE
            canvas.drawCircle(cx, cy, badgeRadius + (1.5f * density * scale), paint)
            
            // B. Badge Fill
            paint.color = badgeColor
            canvas.drawCircle(cx, cy, badgeRadius, paint)
            
            // C. Text
            paint.color = android.graphics.Color.WHITE
            paint.textSize = 10f * density * scale
            paint.textAlign = android.graphics.Paint.Align.CENTER
            paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            
            val text = if (count > 99) "99+" else count.toString()
            val bounds = android.graphics.Rect()
            paint.getTextBounds(text, 0, text.length, bounds)
            val yOff = bounds.height().toFloat() / 2f - bounds.bottom
            
            canvas.drawText(text, cx, cy + yOff, paint)
        }
        
        return bitmap
    }
}
