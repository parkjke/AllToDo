package com.example.alltodo.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect // [NEW]
import android.graphics.RectF // [NEW]
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.alltodo.R
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

object PinImageManager {
    private const val TAG = "PinImageManager"
    private const val HEADER_SIGNATURE = "ALLTODO_V1" // 10 bytes Parity/Header
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

            // 1. Check File & Parity
            if (file.exists()) {
                bitmap = loadBitmapWithParity(file)
            }

            // 2. If invalid or missing, Create from SVG
            if (bitmap == null) {
                Log.d(TAG, "Creating pin bitmap from SVG ($density x): $filename")
                bitmap = createBitmapFromVector(context, resId)
                if (bitmap != null) {
                    saveBitmapWithParity(file, bitmap)
                }
            }

            // 3. Load into Memory
            if (bitmap != null) {
                bitmapCache[resId] = bitmap
            } else {
                Log.e(TAG, "Failed to initialize pin: $filename")
            }
        }
        
        Log.d(TAG, "PinImageManager Initialized for density $density. Cached ${bitmapCache.size} pins.")
    }

    fun clearCacheAndRebuild(context: Context) {
        bitmapCache.clear()
        val pinsDir = File(context.filesDir, "pins")
        if (pinsDir.exists()) {
            pinsDir.listFiles()?.forEach { it.delete() }
        }
        Log.d(TAG, "Cache cleared. Rebuilding...")
        initialize(context)
    }

    fun getPinBitmap(resId: Int): Bitmap? {
        return bitmapCache[resId]
    }

    private fun loadBitmapWithParity(file: File): Bitmap? {
        try {
            FileInputStream(file).use { fis ->
                // Check Parity Header
                val headerBytes = ByteArray(HEADER_SIGNATURE.length)
                val read = fis.read(headerBytes)
                if (read != HEADER_SIGNATURE.length) return null

                val signature = String(headerBytes, StandardCharsets.UTF_8)
                if (signature != HEADER_SIGNATURE) {
                    Log.w(TAG, "Parity Mismatch for ${file.name}: $signature != $HEADER_SIGNATURE")
                    return null
                }

                // Decode remaining stream as PNG
                return BitmapFactory.decodeStream(fis)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading pin file: ${file.name}", e)
            return null
        }
    }

    private fun saveBitmapWithParity(file: File, bitmap: Bitmap) {
        try {
            FileOutputStream(file).use { fos ->
                // Write Header (Parity)
                fos.write(HEADER_SIGNATURE.toByteArray(StandardCharsets.UTF_8))
                
                // Write PNG Data
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving pin file: ${file.name}", e)
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
            val h = (TARGET_HEIGHT_DP * density).toInt() // Or preserve aspect ratio
            
            // Calculate Aspect Ratio preserver
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
            e.printStackTrace()
            return null
        }
    }


    /**
     * Creates a Pin with a Badge (Shield Pin) for Clustering.
     * Matches iOS Design: Canvas 50x60, Pin 40x50, Badge Overhang.
     */
    fun createShieldPin(context: Context, resId: Int, count: Int): Bitmap? {
        val density = context.resources.displayMetrics.density
        
        // Target Sizes in DP
        val canvasWidthDp = 50f
        val canvasHeightDp = 60f
        val pinWidthDp = 40f
        val pinHeightDp = 50f
        val badgeSizeDp = 20f
        
        // Pixel Sizes
        val w = (canvasWidthDp * density).toInt()
        val h = (canvasHeightDp * density).toInt()
        val pinW = (pinWidthDp * density).toInt()
        val pinH = (pinHeightDp * density).toInt()
        val badgeSize = (badgeSizeDp * density).toInt()
        
        // 1. Base Bitmap
        val baseBitmap = getPinBitmap(resId) ?: createBitmapFromVector(context, resId) ?: return null
        
        // 2. Create Canvas
        val resultBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        
        // 3. Draw Pin (Offset y by 10dp to make room for top badge if needed, 
        //    but iOS logic says Pin is 40x50. Canvas 50x60.
        //    Let's align Pin to Bottom-Left or Center?
        //    iOS: Anchor(0.4, 1.0). Visual Pin sits at (0, 10) in 50x60 canvas?
        //    If pin is 40x50, and we want overhang top-right.
        //    Pin Rect: (0, h - pinH, pinW, h) -> Bottom-Left alignment (0, 10) in 50x60 space.
        //    Badge Center: Top-Right of Pin.
        
        val pinLeft = 0f
        val pinTop = (10f * density) // 10dp down
        val pinRect = Rect(0, 0, resultBitmap.width, resultBitmap.height) // Dest rect?
        
        // Scale base bitmap to pinW x pinH
        val destRect = RectF(pinLeft, pinTop, pinLeft + pinW, pinTop + pinH)
        canvas.drawBitmap(baseBitmap, null, destRect, null)
        
        // 4. Draw Badge (Red Circle)
        // Position: Top-Right of the Pin (approx).
        // Pin Top-Right is (pinW, pinTop).
        // Badge Center should be around there.
        // Let's verify iOS logic: Badge overhangs.
        val badgeCenterX = (pinW - 5 * density) // Slight overlap
        val badgeCenterY = (pinTop + 5 * density) 
        
        // Adjust for "Overhang": Badge should be fully visible?
        // Check Canvas width: 50dp. Pin width 40dp. 10dp extra on right.
        // So badge can go up to x=50dp.
        // Badge Radius = 10dp.
        // Center x=40dp means right edge is 50dp. Perfect.
        val badgeRadius = badgeSize / 2f
        val finalBadgeCenterX = (pinW) // Edge of pin
        val finalBadgeCenterY = (pinTop) // Top of pin
        
        // Draw Red Circle
        val paint = android.graphics.Paint()
        paint.color = android.graphics.Color.RED
        paint.isAntiAlias = true
        paint.style = android.graphics.Paint.Style.FILL
        
        // Draw Shadow? Optional.
        // canvas.drawCircle(finalBadgeCenterX, finalBadgeCenterY, badgeRadius, paint)
        
        // Better: Draw slightly shifted to utilize the extra space
        // Center at (40dp, 10dp)?
        // 40dp * density
        val cx = (40f * density)
        val cy = (10f * density)
        
        canvas.drawCircle(cx, cy, badgeRadius, paint)
        
        // 5. Draw Text
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 12f * density
        paint.textAlign = android.graphics.Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        
        // Vertical Center Text
        val fontMetrics = paint.fontMetrics
        val baseline = cy - (fontMetrics.descent + fontMetrics.ascent) / 2
        
        val text = if (count > 99) "99+" else count.toString()
        canvas.drawText(text, cx, baseline, paint)
        
        return resultBitmap
    }
}
