package dev.renkinProject.renkin.icon.creator

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Color
import android.graphics.Paint
import dev.renkinProject.renkin.extension.newArgbBitmap
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

const val SHADOW_BLUR_MIN = 0f
const val SHADOW_BLUR_MAX = 32f
const val SHADOW_DISTANCE_MIN = 0f
const val SHADOW_DISTANCE_MAX = 32f
const val SHADOW_OPACITY_MIN = 0f
const val SHADOW_OPACITY_MAX = 1f

internal object IconShadow {
    fun apply(
        source: Bitmap,
        blur: Float,
        distance: Float,
        angle: Float,
        allDirections: Boolean,
        color: Int,
        style: ColorizerStyle?,
        opacity: Float
    ): Bitmap {
        if (source.width <= 0 || source.height <= 0 || opacity <= 0f) return source

        val scale = maxOf(source.width, source.height) / 256f
        val blurPx = blur.coerceIn(SHADOW_BLUR_MIN, SHADOW_BLUR_MAX) * scale
        val distancePx = distance.coerceIn(SHADOW_DISTANCE_MIN, SHADOW_DISTANCE_MAX) * scale
        val radians = Math.toRadians(angle.toDouble())
        val offsetX = if (allDirections) 0f else sin(radians).toFloat() * distancePx
        val offsetY = if (allDirections) 0f else -cos(radians).toFloat() * distancePx
        val maskSource = if (allDirections && distancePx > 0f) {
            newArgbBitmap(source.width, source.height) { canvas ->
                canvas.scale(
                    (source.width + distancePx * 2f) / source.width,
                    (source.height + distancePx * 2f) / source.height,
                    source.width / 2f,
                    source.height / 2f
                )
                canvas.drawBitmap(source, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            }
        } else {
            source
        }
        val alphaOffset = IntArray(2)
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            if (blurPx > 0f) maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
        }
        val shadowMask = maskSource.extractAlpha(maskPaint, alphaOffset)
        if (maskSource !== source) maskSource.recycle()
        val effectiveStyle = style ?: ColorizerStyle(firstColor = color)
        val positionedMask = newArgbBitmap(source.width, source.height) { canvas ->
            canvas.drawBitmap(
                shadowMask,
                alphaOffset[0] + offsetX,
                alphaOffset[1] + offsetY,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    this.color = Color.WHITE
                }
            )
        }
        val colorLayer = newArgbBitmap(source.width, source.height) { canvas ->
            val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                this.color = effectiveStyle.firstColor
                shader = if (effectiveStyle.mode == ColorizerMode.GRADIENT) {
                    buildColorizerShader(
                        effectiveStyle.allGradientColors,
                        effectiveStyle.gradientType,
                        effectiveStyle.gradientAngle,
                        source.width,
                        source.height,
                        effectiveStyle.gradientPositions
                    )
                } else null
            }
            canvas.drawRect(0f, 0f, source.width.toFloat(), source.height.toFloat(), fill)
        }
        val pixelCount = source.width * source.height
        val maskPixels = IntArray(pixelCount)
        val colorPixels = IntArray(pixelCount)
        positionedMask.getPixels(maskPixels, 0, source.width, 0, 0, source.width, source.height)
        colorLayer.getPixels(colorPixels, 0, source.width, 0, 0, source.width, source.height)
        val opacityScale = opacity.coerceIn(0f, 1f)
        for (index in 0 until pixelCount) {
            val maskAlpha = Color.alpha(maskPixels[index])
            val layerColor = colorPixels[index]
            val resultAlpha = (
                maskAlpha * Color.alpha(layerColor) / 255f * opacityScale
                ).roundToInt()
            colorPixels[index] = Color.argb(
                resultAlpha,
                Color.red(layerColor),
                Color.green(layerColor),
                Color.blue(layerColor)
            )
        }
        val shadowLayer = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        shadowLayer.setPixels(colorPixels, 0, source.width, 0, 0, source.width, source.height)

        val result = newArgbBitmap(source.width, source.height) { canvas ->
            canvas.drawBitmap(shadowLayer, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.drawBitmap(source, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        shadowMask.recycle()
        positionedMask.recycle()
        colorLayer.recycle()
        shadowLayer.recycle()
        result.density = source.density
        return result
    }
}
