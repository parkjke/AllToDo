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
    private const val HEADER_SIGNATURE = "ALLTODO_V7_DYN" // [FIX] V7: Dynamic Composition
    private const val TARGET_WIDTH_DP = 40 // Standard Pin Width
    private const val TARGET_HEIGHT_DP = 50 // Standard Pin Height

    // Cache in memory
    private val bitmapCache = mutableMapOf<String, Bitmap>()

    fun clearCache(context: Context) {
        bitmapCache.clear()
        val pinsDir = File(context.filesDir, "pins")
        if (pinsDir.exists()) {
            pinsDir.listFiles()?.forEach { it.delete() }
        }
    }

    // New Dynamic Composition Function
    fun fetchCompositePin(
        context: Context,
        shieldResId: Int,
        markResId: Int,
        count: Int = 0,
        badgeColor: Int = 0,
        scale: Float = 1.0f
    ): Bitmap? {
        val density = context.resources.displayMetrics.density
        // Cache Key including all parameters
        val cacheKey = "comp_${shieldResId}_${markResId}_${count}_${badgeColor}_${scale}_d$density"
        
        // 1. Memory Cache Check
        if (bitmapCache.containsKey(cacheKey)) {
            return bitmapCache[cacheKey]
        }

        // 2. Disk Cache Check
        val pinsDir = File(context.filesDir, "pins")
        if (!pinsDir.exists()) pinsDir.mkdirs()
        val file = File(pinsDir, "$cacheKey.png")
        
        var bitmap: Bitmap? = null
        if (file.exists()) {
            bitmap = loadBitmapWithParity(file)
            if (bitmap != null) {
                bitmapCache[cacheKey] = bitmap
                return bitmap
            }
        }

        // 3. Generate New Composite Bitmap
        bitmap = createCompositeBitmap(context, shieldResId, markResId, count, badgeColor, scale)
        
        if (bitmap != null) {
            bitmapCache[cacheKey] = bitmap
            saveBitmapWithParity(file, bitmap)
        }
        
        return bitmap
    }

    private fun createCompositeBitmap(
        context: Context,
        shieldResId: Int,
        markResId: Int,
        count: Int,
        badgeColor: Int,
        scale: Float
    ): Bitmap? {
        val density = context.resources.displayMetrics.density
        
        // Base Size (40x50 dp) * Scale
        val pinW = (TARGET_WIDTH_DP * density * scale).toInt()
        val pinH = (TARGET_HEIGHT_DP * density * scale).toInt()
        
        // Padding for Badge (Badge is top-right, similar to iOS logic)
        // iOS BadgeSize is 20pt. Here we approximate relative to density.
        val badgeRadius = 10f * density * scale
        val borderThickness = 1.5f * density * scale
        val totalBadgeRadius = badgeRadius + borderThickness
        
        // Use safe padding to avoid clipping
        val padding = (totalBadgeRadius * 1.5f).toInt()
        
        val sizeW = pinW + padding
        val sizeH = pinH + padding
        
        val bitmap = Bitmap.createBitmap(sizeW, sizeH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // 1. Draw Shield (Base)
        // Shield is drawn at bottom-left of the canvas area allocated for the pin,
        // but since we have padding, we offset strictly by padding for Y if we want it aligned top-ish?
        // Let's stick to the previous coordinate system:
        // Pin is 40x50. Padded canvas is larger.
        // We place the Pin at (0, padding).
        val destRect = Rect(0, padding, pinW, pinH + padding)
        
        val shieldDrawable = ContextCompat.getDrawable(context, shieldResId) ?: return null
        shieldDrawable.setBounds(destRect.left, destRect.top, destRect.right, destRect.bottom)
        shieldDrawable.draw(canvas)
        
        // 2. Draw Mark (Foreground)
        // Logic from iOS:
        // markTargetHeight = size.height * 0.58
        // markY = (size.height * 0.32) - (markTargetHeight / 2)
        // NOTE: iOS 'markY' is relative to Top-Left of Shield Image.
        
        val markDrawable = ContextCompat.getDrawable(context, markResId)
        if (markDrawable != null) {
            val markIntrW = markDrawable.intrinsicWidth.toFloat()
            val markIntrH = markDrawable.intrinsicHeight.toFloat()
            
            if (markIntrW > 0 && markIntrH > 0) {
                val targetHeight = pinH * 0.58f
                val markScaleFactor = targetHeight / markIntrH
                val targetWidth = markIntrW * markScaleFactor
                
                val markX = (pinW - targetWidth) / 2f
                // 32% from top of Shield
                val markCenterY = pinH * 0.32f
                val markY = markCenterY - (targetHeight / 2f)
                
                // Adjust for canvas padding
                val fnX = markX
                val fnY = markY + padding
                
                val markRect = Rect(
                    fnX.toInt(),
                    fnY.toInt(),
                    (fnX + targetWidth).toInt(),
                    (fnY + targetHeight).toInt()
                )
                
                markDrawable.setBounds(markRect.left, markRect.top, markRect.right, markRect.bottom)
                markDrawable.draw(canvas)
            }
        }
        
        // 3. Draw Badge (Top-Right)
        if (count > 0) {
            val badgePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.FILL
            }
            
            // Center of Badge: Top-Right corner of the Shield frame
            // Overlap slightly for visual connection
            val overlap = 3 * density * scale 
            // Shield Top-Right is at (pinW, padding)
            val cx = pinW.toFloat() - overlap
            val cy = padding.toFloat() + overlap
            
            // A. Badge Border
            badgePaint.color = android.graphics.Color.WHITE
            canvas.drawCircle(cx, cy, totalBadgeRadius, badgePaint)
            
            // B. Badge Fill
            badgePaint.color = badgeColor
            canvas.drawCircle(cx, cy, badgeRadius, badgePaint)
            
            // C. Text
            val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
            
            val calculatedSize = 10f * density * scale
            val minSize = 9f * density
            textPaint.textSize = if (calculatedSize < minSize) minSize else calculatedSize
            
            val text = if (count > 99) "99+" else count.toString()
            val bounds = android.graphics.Rect()
            textPaint.getTextBounds(text, 0, text.length, bounds)
            val yOff = bounds.height() / 2f - bounds.bottom
            
            canvas.drawText(text, cx, cy + yOff, textPaint)
        }
        
        return bitmap
    }
    
    // Helpers
    private fun loadBitmapWithParity(file: File): Bitmap? {
        try {
            FileInputStream(file).use { fis ->
                val headerBytes = ByteArray(HEADER_SIGNATURE.length)
                val read = fis.read(headerBytes)
                if (read != HEADER_SIGNATURE.length) return null
                if (String(headerBytes, StandardCharsets.UTF_8) != HEADER_SIGNATURE) return null
                return BitmapFactory.decodeStream(fis)
            }
        } catch (e: Exception) { return null }
    }

    private fun saveBitmapWithParity(file: File, bitmap: Bitmap) {
        try {
            FileOutputStream(file).use { fos ->
                fos.write(HEADER_SIGNATURE.toByteArray(StandardCharsets.UTF_8))
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
        } catch (e: Exception) {}
    }
    
    // Utility to get ResID by name
    fun getResourceId(context: Context, name: String): Int {
         return context.resources.getIdentifier(name, "drawable", context.packageName)
    }
}
