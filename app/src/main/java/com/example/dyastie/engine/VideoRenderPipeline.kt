package com.example.dyastie.engine

import android.graphics.*
import com.example.dyastie.model.*
import kotlin.math.cos
import kotlin.math.sin

object VideoRenderPipeline {

    fun renderFrame(
        baseBitmap: Bitmap?,
        outputWidth: Int,
        outputHeight: Int,
        clip: TimelineClip?,
        timelineTimeMs: Long,
        previewQualityScale: Float = 1.0f // 1.0 = Full, 0.5 = 1/2, 0.25 = 1/4
    ): Bitmap {
        val targetWidth = (outputWidth * previewQualityScale).toInt().coerceAtLeast(160)
        val targetHeight = (outputHeight * previewQualityScale).toInt().coerceAtLeast(90)

        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)

        if (clip == null || baseBitmap == null) {
            // Draw pro NLE empty slate / timecode card
            drawPlaceholderGrid(canvas, targetWidth, targetHeight, timelineTimeMs)
            return output
        }

        val transform = clip.transform
        val colorGrading = clip.colorGrading
        val effects = clip.effects.filter { it.isEnabled }
        val localTimeMs = (timelineTimeMs - clip.timelineStartMs).coerceAtLeast(0L)

        // 1. Interpolate Transform with Keyframes if present
        val animPosX = ClipKeyframe.interpolate(clip.keyframes, KeyframeProperty.POSITION_X, localTimeMs, transform.posX)
        val animPosY = ClipKeyframe.interpolate(clip.keyframes, KeyframeProperty.POSITION_Y, localTimeMs, transform.posY)
        val animScaleX = ClipKeyframe.interpolate(clip.keyframes, KeyframeProperty.SCALE, localTimeMs, transform.scaleX)
        val animScaleY = ClipKeyframe.interpolate(clip.keyframes, KeyframeProperty.SCALE, localTimeMs, transform.scaleY)
        val animRotation = ClipKeyframe.interpolate(clip.keyframes, KeyframeProperty.ROTATION, localTimeMs, transform.rotationDeg)
        val animOpacity = ClipKeyframe.interpolate(clip.keyframes, KeyframeProperty.OPACITY, localTimeMs, transform.opacity)

        canvas.save()

        val centerX = targetWidth / 2f + animPosX * (targetWidth / 1920f)
        val centerY = targetHeight / 2f + animPosY * (targetHeight / 1080f)

        var totalScaleX = animScaleX
        var totalScaleY = animScaleY
        var totalRotation = animRotation
        var shakeOffsetX = 0f
        var shakeOffsetY = 0f

        // 2. Evaluate Gaming Effects
        for (effect in effects) {
            when (effect.type) {
                EffectType.ZOOM -> {
                    totalScaleX *= (1f + 0.3f * effect.intensity)
                    totalScaleY *= (1f + 0.3f * effect.intensity)
                }
                EffectType.PUNCH_IN -> {
                    val progress = ((timelineTimeMs - clip.timelineStartMs) % 1000L) / 1000f
                    val punch = (1f - progress).coerceAtLeast(0f) * effect.intensity * 0.4f
                    totalScaleX += punch
                    totalScaleY += punch
                }
                EffectType.PUNCH_OUT -> {
                    val progress = ((timelineTimeMs - clip.timelineStartMs) % 1000L) / 1000f
                    val punch = (progress).coerceIn(0f, 1f) * effect.intensity * 0.3f
                    totalScaleX -= punch
                    totalScaleY -= punch
                }
                EffectType.SHAKE, EffectType.IMPACT -> {
                    val freq = 0.05f * effect.speed
                    val intensityPx = effect.intensity * 24f * (targetWidth / 1920f)
                    shakeOffsetX += (sin((timelineTimeMs * freq).toDouble()) * intensityPx).toFloat()
                    shakeOffsetY += (cos((timelineTimeMs * freq * 1.3).toDouble()) * intensityPx).toFloat()
                    if (effect.type == EffectType.IMPACT) {
                        totalScaleX *= 1.08f
                        totalScaleY *= 1.08f
                    }
                }
                EffectType.RGB_SPLIT, EffectType.GLITCH -> {
                    shakeOffsetX += (sin((timelineTimeMs * 0.08).toDouble()) * 8f * effect.intensity).toFloat()
                }
                else -> Unit
            }
        }

        canvas.translate(centerX + shakeOffsetX, centerY + shakeOffsetY)
        canvas.rotate(totalRotation)
        canvas.scale(totalScaleX, totalScaleY)

        // 3. Color Grading & Opacity
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.alpha = (transform.opacity.coerceIn(0f, 1f) * 255).toInt()

        val cm = ColorMatrix()
        // Saturation
        cm.setSaturation(colorGrading.saturation.coerceIn(0f, 2f))

        // Contrast & Brightness
        val contrast = colorGrading.contrast.coerceIn(0f, 2f)
        val brightnessShift = colorGrading.brightness * 255f
        val scaleMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, brightnessShift,
            0f, contrast, 0f, 0f, brightnessShift,
            0f, 0f, contrast, 0f, brightnessShift,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(scaleMatrix)

        // Temperature (Cool vs Warm)
        if (colorGrading.temperature != 0f) {
            val temp = colorGrading.temperature
            val tempMatrix = ColorMatrix(floatArrayOf(
                1f + temp * 0.2f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f - temp * 0.2f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.postConcat(tempMatrix)
        }

        paint.colorFilter = ColorMatrixColorFilter(cm)

        // Draw video bitmap centered
        val srcRect = Rect(0, 0, baseBitmap.width, baseBitmap.height)
        val halfW = targetWidth / 2f
        val halfH = targetHeight / 2f
        val dstRect = RectF(-halfW, -halfH, halfW, halfH)
        canvas.drawBitmap(baseBitmap, srcRect, dstRect, paint)

        canvas.restore()

        // 4. Overlays & Post-Process Effects (Flash, Vignette, Glitch Scanlines)
        for (effect in effects) {
            when (effect.type) {
                EffectType.FLASH -> {
                    val progress = ((timelineTimeMs - clip.timelineStartMs) % 800L) / 800f
                    val flashAlpha = ((1f - progress) * effect.intensity * 200).toInt().coerceIn(0, 255)
                    if (flashAlpha > 0) {
                        val flashPaint = Paint().apply {
                            color = Color.WHITE
                            alpha = flashAlpha
                        }
                        canvas.drawRect(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat(), flashPaint)
                    }
                }
                EffectType.VIGNETTE -> {
                    val rad = Math.hypot(targetWidth / 2.0, targetHeight / 2.0).toFloat()
                    val gradient = RadialGradient(
                        targetWidth / 2f, targetHeight / 2f, rad,
                        intArrayOf(Color.TRANSPARENT, Color.argb((effect.intensity * 220).toInt(), 0, 0, 0)),
                        floatArrayOf(0.4f, 1.0f),
                        Shader.TileMode.CLAMP
                    )
                    val vigPaint = Paint().apply { shader = gradient }
                    canvas.drawRect(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat(), vigPaint)
                }
                EffectType.GLITCH -> {
                    // Draw digital scanlines
                    val linePaint = Paint().apply {
                        color = Color.argb(40, 0, 255, 200)
                        strokeWidth = 2f
                    }
                    var y = (timelineTimeMs % 20).toFloat()
                    while (y < targetHeight) {
                        canvas.drawLine(0f, y, targetWidth.toFloat(), y, linePaint)
                        y += 12f
                    }
                }
                EffectType.RGB_SPLIT -> {
                    // Fast RGB fringe
                    val splitPaint = Paint().apply {
                        color = Color.argb((40 * effect.intensity).toInt(), 255, 0, 80)
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                    }
                    canvas.drawRect(4f, 0f, targetWidth.toFloat(), targetHeight.toFloat(), splitPaint)
                }
                else -> Unit
            }
        }

        // 5. Draw Text Overlay if present
        clip.textOverlay?.let { textOverlay ->
            drawTextOverlay(canvas, targetWidth, targetHeight, textOverlay)
        }

        return output
    }

    private fun drawTextOverlay(
        canvas: Canvas,
        width: Int,
        height: Int,
        textOverlay: TextOverlay
    ) {
        val scaledSp = textOverlay.fontSizeSp * (width / 1280f).coerceAtLeast(0.6f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = scaledSp
            typeface = if (textOverlay.isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textAlign = when (textOverlay.alignment) {
                0 -> Paint.Align.LEFT
                2 -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
        }

        val x = when (textOverlay.alignment) {
            0 -> width * 0.1f
            2 -> width * 0.9f
            else -> width * 0.5f
        }
        val y = height * 0.82f // Lower third by default

        // Optional Pill Background
        if (textOverlay.backgroundColor != 0L) {
            val bounds = Rect()
            paint.getTextBounds(textOverlay.text, 0, textOverlay.text.length, bounds)
            val bgPaint = Paint().apply { color = textOverlay.backgroundColor.toInt() }
            val padX = 20f
            val padY = 12f
            canvas.drawRoundRect(
                x - bounds.width() / 2f - padX,
                y - bounds.height() - padY,
                x + bounds.width() / 2f + padX,
                y + padY,
                16f, 16f, bgPaint
            )
        }

        // Stroke
        if (textOverlay.strokeWidth > 0f) {
            val strokePaint = Paint(paint).apply {
                style = Paint.Style.STROKE
                strokeWidth = textOverlay.strokeWidth * (width / 1280f).coerceAtLeast(1f)
                color = textOverlay.strokeColor.toInt()
            }
            canvas.drawText(textOverlay.text, x, y, strokePaint)
        }

        // Fill Text with Shadow
        paint.style = Paint.Style.FILL
        paint.color = textOverlay.textColor.toInt()
        paint.setShadowLayer(8f, 2f, 2f, textOverlay.shadowColor.toInt())
        canvas.drawText(textOverlay.text, x, y, paint)
    }

    private fun drawPlaceholderGrid(canvas: Canvas, width: Int, height: Int, timeMs: Long) {
        val gridPaint = Paint().apply {
            color = Color.DKGRAY
            strokeWidth = 1f
        }
        // Crosshairs
        canvas.drawLine(width / 2f, 0f, width / 2f, height.toFloat(), gridPaint)
        canvas.drawLine(0f, height / 2f, width.toFloat(), height / 2f, gridPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            textSize = 24f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("DYASTIE NLE MONITOR", width / 2f, height / 2f - 20, textPaint)

        val seconds = timeMs / 1000
        val millis = (timeMs % 1000) / 10
        val tcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 0, 210, 255)
            textSize = 20f
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(String.format("TC %02d:%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60, millis), width / 2f, height / 2f + 30, tcPaint)
    }
}
