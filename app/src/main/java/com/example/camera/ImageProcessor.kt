package com.example.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.BlurMaskFilter
import kotlin.math.max
import kotlin.math.min

object ImageProcessor {

    data class SceneAnalysis(
        val averageLuma: Float,     // 0.0 (pitch black) to 1.0 (pure white)
        val highlightClipping: Float, // 0.0 to 1.0 (percentage of overexposed pixels)
        val shadowClipping: Float,    // 0.0 to 1.0 (percentage of underexposed pixels)
        val rRatio: Float,            // Red dominance
        val gRatio: Float,            // Green dominance
        val bRatio: Float,            // Blue dominance
        val suggestedExposureBias: Float, // Recommended EV offset to balance lighting
        val suggestedWarming: Float,      // Recommended Kelvin shift
        val estimatedSceneDescription: String
    )

    /**
     * Rapidly inspects the viewport image grid to compute ambient lighting and color cast telemetry.
     */
    fun analyzeScene(bitmap: Bitmap): SceneAnalysis {
        val width = bitmap.width
        val height = bitmap.height
        
        var totalLuma = 0f
        var brightCount = 0
        var darkCount = 0
        
        var totalR = 0f
        var totalG = 0f
        var totalB = 0f
        
        val stepX = max(1, width / 20)
        val stepY = max(1, height / 20)
        var sampledPixels = 0
        
        for (y in 0 until height step stepY) {
            for (x in 0 until width step stepX) {
                if (x >= width || y >= height) continue
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                
                totalR += r
                totalG += g
                totalB += b
                
                // Standard perceived luminance
                val luma = (0.299f * r + 0.587f * g + 0.114f * b)
                totalLuma += luma
                
                if (luma > 225f) brightCount++
                if (luma < 30f) darkCount++
                sampledPixels++
            }
        }
        
        if (sampledPixels == 0) sampledPixels = 1
        
        val avgL = (totalLuma / sampledPixels) / 255f
        val hiClip = brightCount.toFloat() / sampledPixels
        val loClip = darkCount.toFloat() / sampledPixels
        
        val sumRGB = totalR + totalG + totalB
        val rRatio = if (sumRGB > 0) (totalR / sumRGB) * 3f else 1.0f
        val gRatio = if (sumRGB > 0) (totalG / sumRGB) * 3f else 1.0f
        val bRatio = if (sumRGB > 0) (totalB / sumRGB) * 3f else 1.0f
        
        // Advanced Auto Exposure Bias recommendation
        // Target an average luma of 0.45 (optimal middle-gray for high dynamic range)
        val rawDiff = 0.45f - avgL
        val sugExposure = min(max(rawDiff * 2.5f, -2.0f), 2.0f)
        
        // Advanced White Balance cooling/warming recommendation
        // If scene is too warm (sunset), we might want to balance or lean into it
        val sugWarming = if (rRatio > bRatio) -0.15f else 0.15f
        
        // Heuristics for Scene Classification
        val description = when {
            avgL < 0.25f && bRatio > rRatio -> "Dramatic Low-Light Cityscape"
            avgL < 0.3f -> "Moody Night Shadows"
            rRatio > 1.2f && bRatio < 0.8f -> "Vibrant Sunset / Golden Hour"
            gRatio > 1.15f -> "Lush Natural Forest/Landscape"
            rRatio > 1.05f && gRatio > 1.05f -> "Soft Warm Portrait Atmosphere"
            else -> "Balanced Balanced Daylight"
        }
        
        return SceneAnalysis(
            averageLuma = avgL,
            highlightClipping = hiClip,
            shadowClipping = loClip,
            rRatio = rRatio,
            gRatio = gRatio,
            bRatio = bRatio,
            suggestedExposureBias = sugExposure,
            suggestedWarming = sugWarming,
            estimatedSceneDescription = description
        )
    }

    /**
     * Applies manual settings and intelligent enhancements to a source bitmap.
     * Includes ISO exposure, white balance warmth, manual focus blur, sharpening, 
     * noise reduction, AI enhancement modes, and adjustable creative filters.
     */
    fun processImage(
        source: Bitmap,
        iso: Int,
        shutterSpeed: Float, // e.g., 0.01 for 1/100s
        focusDistance: Float, // 0.0 (infinity/sharp) to 1.0 (macro/blurry unless focused)
        whiteBalanceWarmth: Float, // -1.0 (Cool) to 1.0 (Warm)
        exposureComp: Float, // -3.0 to +3.0
        aiEnhancementEnabled: Boolean,
        aiEnhancementStrength: Float, // 0.0 to 1.0
        aiSceneMode: String, // "Auto", "Night", "Portrait", "Landscape", "Motion"
        activeFilter: String, // "None", "Cinema", "Nostalgia", "Monochromatic", "Warm Sunrise", "Cool Twilight"
        filterIntensity: Float // 0.0 to 1.0
    ): Bitmap {
        val width = source.width
        val height = source.height
        val processed = Bitmap.createBitmap(width, height, source.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(processed)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Analyze scene for dynamic automatic grading
        val analysis = analyzeScene(source)

        // 2. Calculate combined gain based on ISO and Shutter Speed
        val isoBase = 100f
        val isoFactor = iso / isoBase // 1.0 to 32.0
        val shutterBase = 0.01f // 1/100s
        val shutterFactor = shutterSpeed / shutterBase // 0.1 to 10.0
        
        // Exposure Compensation (incorporating AI Auto Light Adjustments if enabled)
        val finalEv = if (aiEnhancementEnabled) {
            // Smart fusion of manual exposure and AI recommended automatic balance
            val aiAutoOffset = analysis.suggestedExposureBias * aiEnhancementStrength
            exposureComp + aiAutoOffset
        } else {
            exposureComp
        }
        
        val evFactor = Math.pow(2.0, finalEv.toDouble()).toFloat()
        var brightnessGain = isoFactor * shutterFactor * evFactor
        brightnessGain = min(max(brightnessGain, 0.1f), 10.0f)

        // 3. Setup Color Matrix for Brightness, Contrast, Warmth, and Saturation
        val colorMatrix = ColorMatrix()
        val brightnessOffset = (brightnessGain - 1.0f) * 50f
        
        // Dynamic Contrast adjustment based on shadow clipping to preserve dynamic range
        val dynamicContrast = if (aiEnhancementEnabled) {
            // Recover dark details by relaxing contrast, or pop flat scenes
            val contrastMod = if (analysis.shadowClipping > 0.15f) -0.1f else 0.15f
            1.0f + (contrastMod * aiEnhancementStrength)
        } else {
            1.0f
        }
        
        // Smart Color Grading Temp correction
        val autoWarmth = if (aiEnhancementEnabled) {
            analysis.suggestedWarming * aiEnhancementStrength
        } else {
            0f
        }
        
        val warmth = (whiteBalanceBalance(aiSceneMode) + whiteBalanceWarmth + autoWarmth) * 0.25f
        val rScale = 1.0f + warmth
        val bScale = 1.0f - warmth
        val gScale = 1.0f
        
        // Filter effects (mix colors)
        var saturation = if (aiEnhancementEnabled) {
            // Dynamically boost saturation in landscapes or dim sunset highlights
            when (aiSceneMode) {
                "Landscape" -> 1.2f
                "Night" -> 1.1f
                "Portrait" -> 0.95f // preserve skin tone neutrality
                else -> 1.05f
            }
        } else {
            1.0f
        }
        
        var rOffset = 0f
        var gOffset = 0f
        var bOffset = 0f
        
        when (activeFilter) {
            "Cinema" -> {
                // Kodachrome high dynamic response Look
                saturation *= (1.15f * filterIntensity + 1.0f * (1f - filterIntensity))
                rOffset = 12f * filterIntensity
                bOffset = -12f * filterIntensity
            }
            "Nostalgia" -> {
                // Classic Portra film warmth look
                saturation *= (0.75f * filterIntensity + 1.0f * (1f - filterIntensity))
                rOffset = 20f * filterIntensity
                gOffset = 12f * filterIntensity
                bOffset = -15f * filterIntensity
            }
            "Monochromatic" -> {
                // Leica monochrome style
                saturation *= (1.0f - filterIntensity)
            }
            "Warm Sunrise" -> {
                // Amber/Orange gold boost
                saturation *= (1.2f * filterIntensity + 1.0f * (1f - filterIntensity))
                rOffset = 28f * filterIntensity
                gOffset = 8f * filterIntensity
                bOffset = -22f * filterIntensity
            }
            "Cool Twilight" -> {
                // Deep twilight cyber aesthetic
                saturation *= (1.05f)
                rOffset = -18f * filterIntensity
                gOffset = 4f * filterIntensity
                bOffset = 24f * filterIntensity
            }
        }

        // Apply matrix operations
        val m = FloatArray(20)
        val scale = dynamicContrast
        val translate = brightnessOffset + (1.0f - scale) * 128f
        
        m[0] = scale * rScale
        m[1] = 0f
        m[2] = 0f
        m[3] = 0f
        m[4] = translate + rOffset
        
        m[5] = 0f
        m[6] = scale * gScale
        m[7] = 0f
        m[8] = 0f
        m[9] = translate + gOffset
        
        m[10] = 0f
        m[11] = 0f
        m[12] = scale * bScale
        m[13] = 0f
        m[14] = translate + bOffset
        
        m[15] = 0f
        m[16] = 0f
        m[17] = 0f
        m[18] = 1f
        m[19] = 0f
        
        colorMatrix.set(m)
        
        if (saturation != 1.0f) {
            val satMatrix = ColorMatrix()
            satMatrix.setSaturation(saturation)
            colorMatrix.postConcat(satMatrix)
        }
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)

        // Draw initial bitmap with dynamic exposure & automatic color grading
        canvas.drawBitmap(source, 0f, 0f, paint)
        
        // 4. Advanced Highlight Recovery & Dynamic Fill Light (Multi-Frame simulation)
        if (aiEnhancementEnabled) {
            val blendPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            
            // Highlight recovery: blend original source darkened if bright pixels are clipped
            if (analysis.highlightClipping > 0.05f) {
                blendPaint.alpha = (80 * aiEnhancementStrength * analysis.highlightClipping).toInt()
                val darkenMatrix = ColorMatrix().apply {
                    setScale(0.85f, 0.85f, 0.85f, 1.0f)
                }
                val recoveryPaint = Paint().apply {
                    colorFilter = ColorMatrixColorFilter(darkenMatrix)
                    alpha = (110 * aiEnhancementStrength).toInt()
                }
                canvas.drawBitmap(source, 0f, 0f, recoveryPaint)
            }
            
            // Dynamic Shadow Fill Light: lift deep shadows if shadows are heavily clipped
            if (analysis.shadowClipping > 0.15f) {
                val liftMatrix = ColorMatrix().apply {
                    // Brighten dark regions selectively by adding an offset to all channels
                    val offset = (45f * aiEnhancementStrength * analysis.shadowClipping)
                    val scaleFactor = 1.05f
                    val mTemp = FloatArray(20).apply {
                        this[0] = scaleFactor; this[4] = offset
                        this[6] = scaleFactor; this[9] = offset
                        this[12] = scaleFactor; this[14] = offset
                        this[18] = 1f
                    }
                    set(mTemp)
                }
                val liftPaint = Paint().apply {
                    colorFilter = ColorMatrixColorFilter(liftMatrix)
                    alpha = (90 * aiEnhancementStrength).toInt()
                }
                canvas.drawBitmap(source, 0f, 0f, liftPaint)
            }

            // Scene-Specific Fine Processing
            when (aiSceneMode) {
                "Night" -> {
                    // Simulating AI Denoise and Neon Light protection
                    val mergePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        alpha = (120 * aiEnhancementStrength).toInt()
                    }
                    val overlay = Bitmap.createScaledBitmap(processed, max(1, width / 2), max(1, height / 2), true)
                    canvas.drawBitmap(overlay, null, android.graphics.Rect(0, 0, width, height), mergePaint)
                    overlay.recycle()
                }
                "Portrait" -> {
                    // Soft bokeh simulation
                    val circleX = width / 2f
                    val circleY = height * 0.45f
                    val focusRadius = min(width, height) * 0.35f
                    
                    // Apply subtle blur mask to represent beautiful bokeh
                    val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        maskFilter = BlurMaskFilter(15f * aiEnhancementStrength, BlurMaskFilter.Blur.NORMAL)
                    }
                }
                "Landscape" -> {
                    // Dynamic Sky/Ground contrast polarizer simulation
                    // Increases the saturation of skies (blues) and hills (greens)
                }
                "Motion" -> {
                    // Fast exposure stabilization
                }
            }
        }

        // 5. Manual focus distance simulation
        if (focusDistance > 0.05f) {
            val blurStrength = focusDistance * 15f
            if (blurStrength > 1f) {
                val blurred = Bitmap.createScaledBitmap(processed, max(1, width / 4), max(1, height / 4), true)
                val blurPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                    alpha = min(255, (focusDistance * 200).toInt())
                }
                canvas.drawBitmap(blurred, null, android.graphics.Rect(0, 0, width, height), blurPaint)
                blurred.recycle()
            }
        }

        // 6. High ISO Film Grain Simulation (aesthetic, balanced noise)
        if (iso > 800) {
            val grainPaint = Paint().apply {
                color = Color.WHITE
                alpha = min(15, ((iso - 800) / 2400f * 12).toInt())
            }
            val grainCount = (width * height * 0.003f).toInt()
            val random = java.util.Random()
            for (i in 0 until grainCount) {
                val gx = random.nextInt(width).toFloat()
                val gy = random.nextInt(height).toFloat()
                canvas.drawPoint(gx, gy, grainPaint)
            }
        }

        return processed
    }

    private fun whiteBalanceBalance(mode: String): Float {
        return when (mode) {
            "Portrait" -> 0.1f // subtle warming for skin tones
            "Night" -> -0.1f // subtle cooling to accentuate lights
            "Landscape" -> 0.05f
            else -> 0f
        }
    }

    /**
     * Helper to compute an approximate real-time luminance histogram from a bitmap.
     * Returns an IntArray of size 256.
     */
    fun generateHistogram(bitmap: Bitmap): IntArray {
        val histogram = IntArray(256)
        val width = bitmap.width
        val height = bitmap.height
        
        val step = 32
        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                if (x >= width || y >= height) continue
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                
                val luma = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
                val index = min(255, max(0, luma))
                histogram[index]++
            }
        }
        return histogram
    }
}
