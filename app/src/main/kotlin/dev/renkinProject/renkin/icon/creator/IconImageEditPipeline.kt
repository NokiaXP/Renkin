package dev.renkinProject.renkin.icon.creator

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import dev.alembiconsProject.imagetracer.ImageTracer
import dev.alembiconsProject.tgCannyEdgeCompose.CannyEdgeDetector
import dev.alembiconsProject.tgCannyEdgeCompose.DetectionOptions
import dev.renkinProject.renkin.data.ImageEdit
import dev.renkinProject.renkin.drawable.BitmapIconDrawable
import dev.renkinProject.renkin.drawable.AdaptiveIconPackDrawable
import dev.renkinProject.renkin.drawable.MaterialYouPackEditState
import dev.renkinProject.renkin.drawable.IconPackDrawable
import dev.renkinProject.renkin.drawable.ImageVectorDrawable
import dev.renkinProject.renkin.drawable.InsetIconDrawable
import dev.renkinProject.renkin.drawable.toImageVectorDrawable
import dev.renkinProject.renkin.extension.changeBackgroundColor
import dev.renkinProject.renkin.extension.emptyLike
import dev.renkinProject.renkin.extension.newArgbBitmap
import dev.renkinProject.renkin.extension.removeBackground
import dev.renkinProject.renkin.vector.VectorEditor.Companion.editPaths
import dev.renkinProject.renkin.vector.VectorEditor.Companion.editPathColors
import dev.renkinProject.renkin.vector.VectorEditor.Companion.resizeAndCenter
import dev.renkinProject.renkin.vector.VectorEditor.Companion.setReferenceColorPaths

/** Applies source-independent image edits, followed by geometry/output adjustments. */
internal class IconImageEditPipeline(
    private val resources: Resources,
    private val options: GenerationOptions,
    private val adjustments: IconAdjustmentPipeline = IconAdjustmentPipeline(resources, options)
) {
    private val colorizeColor
        get() = if (options.colorizeInverse) invertArgb(options.color) else options.color

    fun apply(icon: IconPackDrawable, imageEdit: ImageEdit): IconPackDrawable {
        val source = if (icon is AdaptiveIconPackDrawable) restyleMaterialYouPackIcon(icon) else icon
        return adjustments.apply(applyEdit(source, imageEdit))
    }

    fun applyPrimary(icon: IconPackDrawable): IconPackDrawable =
        apply(icon, options.primaryImageEdit)

    internal fun applyEdit(icon: IconPackDrawable, imageEdit: ImageEdit): IconPackDrawable {
        if (imageEdit == ImageEdit.NONE) return icon

        if (icon is AdaptiveIconPackDrawable) {
            if (options.themed) {
                return applyEdit(BitmapIconDrawable(resources, icon.foreground), imageEdit)
            }
            if (imageEdit == ImageEdit.COLORIZE && options.colorizeLayers.isEmpty()) {
                val foreground = colorizeAdaptiveForeground(icon.foreground)
                return icon.withForeground(foreground)
            }
            return applyToBitmap(icon.toBitmap(), imageEdit, options.colorizeBlendMode)
        }

        if (imageEdit == ImageEdit.COLORIZE &&
            options.colorizerMode == ColorizerMode.SINGLE_COLOR &&
            !options.colorizeLighten &&
            !options.colorizeMonochrome
        ) {
            adjustments.modifierVector(icon)?.let { vector ->
                vector.root.setReferenceColorPaths(SolidColor(Color(colorizeColor)))
                vector.tintColor = Color.Unspecified
                return vector
            }
        }

        if (icon is ImageVectorDrawable) {
            val copy = ImageVectorDrawable(icon.toImageVector())
            return when (imageEdit) {
                ImageEdit.NONE -> icon
                ImageEdit.COLORIZE_SEGMENTS -> colorize(copy.toBitmap(), options.colorizeBlendMode)
                ImageEdit.COLORIZE -> {
                    if (options.colorizeLighten ||
                        options.colorizerMode == ColorizerMode.GRADIENT ||
                        options.colorizeMonochrome
                    ) {
                        colorize(copy.toBitmap(), options.colorizeBlendMode)
                    } else {
                        copy.root.setReferenceColorPaths(SolidColor(Color(colorizeColor)))
                        copy.tintColor = Color.Unspecified
                        copy
                    }
                }
                ImageEdit.PATH -> trace(copy.toBitmap())
                ImageEdit.EDGE -> detectEdges(copy.toBitmap())
                ImageEdit.REMOVE_BACKGROUND -> removeBackground(copy.toBitmap())
            }
        }

        val modified = applyToBitmap(icon.toBitmap(), imageEdit, options.colorizeBlendMode)
        return preserveBitmapPresentation(icon, modified)
    }

    internal fun applyToBitmap(
        bitmap: Bitmap,
        imageEdit: ImageEdit,
        mode: PorterDuff.Mode
    ): IconPackDrawable = when (imageEdit) {
        ImageEdit.NONE -> BitmapIconDrawable(resources, bitmap)
        ImageEdit.PATH -> trace(bitmap)
        ImageEdit.EDGE -> detectEdges(bitmap)
        ImageEdit.COLORIZE, ImageEdit.COLORIZE_SEGMENTS -> colorize(bitmap, mode)
        ImageEdit.REMOVE_BACKGROUND -> removeBackground(bitmap)
    }

    private fun colorizeAdaptiveForeground(source: Bitmap): Bitmap {
        if (options.colorizerMode != ColorizerMode.GRADIENT) {
            return colorize(source, options.colorizeBlendMode).toBitmap()
        }
        val gradient = requireNotNull(gradientPixels(options.colorizerStyle, source.width, source.height))
        val base = if (options.colorizeMonochrome) monochromeBitmap(source, options.colorizeInverse) else source
        val pixels = IntArray(source.width * source.height)
        base.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        val solid = options.colorizeBlendMode == PorterDuff.Mode.SRC_IN
        val lighten = options.colorizeBlendMode == PorterDuff.Mode.SCREEN
        for (index in pixels.indices) {
            val original = pixels[index]
            val tint = gradient[index]
            val tintAlpha = android.graphics.Color.alpha(tint) / 255f
            fun channel(shift: Int): Int {
                val value = (original ushr shift) and 255
                val color = (tint ushr shift) and 255
                return when {
                    solid -> color
                    lighten ->
                        (value + (255 - value) * tintAlpha * color / 255f).toInt()
                    else -> (value * (1f - tintAlpha + tintAlpha * color / 255f)).toInt()
                }
            }
            // Canvas MULTIPLY over a transparent layer would paint the entire gradient rectangle.
            val alpha = android.graphics.Color.alpha(original)
            val colored = android.graphics.Color.argb(
                if (solid) (alpha * tintAlpha).toInt() else alpha,
                channel(16), channel(8), channel(0)
            )
            pixels[index] = if (options.colorizeInverse && !options.colorizeMonochrome) invertArgb(colored) else colored
        }
        return Bitmap.createBitmap(pixels, source.width, source.height, Bitmap.Config.ARGB_8888).apply {
            density = source.density
        }
    }

    private fun restyleMaterialYouPackIcon(icon: AdaptiveIconPackDrawable): AdaptiveIconPackDrawable {
        if (icon.materialYouEditState == null) return icon
        val customForeground = options.materialYouPackCustomForeground ?: return icon
        val customBackground = options.materialYouPackCustomBackground ?: return icon
        val state = MaterialYouPackEditState(
            selectedScheme = options.materialYouPackSelectedScheme,
            customForeground = customForeground,
            customBackground = customBackground,
            strokeScale = options.materialYouPackStrokeScale
        )
        if (state.selectedScheme < 0) return icon.restoreOriginalMaterialYouLayers(state)

        val foregroundColor = options.materialYouPackForeground ?: return icon
        val backgroundColor = options.materialYouPackBackground ?: return icon
        val foreground = tintAlphaLayer(icon.foreground, foregroundColor)
        val background = newArgbBitmap(AdaptiveIconPackDrawable.LAYER_SIZE, AdaptiveIconPackDrawable.LAYER_SIZE) {
            it.drawColor(backgroundColor)
        }
        return icon.withMaterialYouLayers(foreground, background, state)
    }

    private fun tintAlphaLayer(source: Bitmap, color: Int): Bitmap =
        newArgbBitmap(source.width, source.height) { canvas ->
            canvas.drawBitmap(source, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.drawColor(color, PorterDuff.Mode.SRC_IN)
        }

    internal fun colorize(bitmap: Bitmap, mode: PorterDuff.Mode): IconPackDrawable {
        if (options.colorizeLayers.isNotEmpty()) {
            return BitmapIconDrawable(
                resources,
                addBackground(applySegmentLayers(bitmap, options.colorizeLayers))
            )
        }
        if (options.colorizerMode == ColorizerMode.GRADIENT) {
            return BitmapIconDrawable(resources, colorizeWithGradient(bitmap))
        }
        if (options.colorizeMonochrome) {
            return defaultBitmap(monochromeBitmap(bitmap, options.colorizeInverse))
        }
        return BitmapIconDrawable(resources, colorizeBitmap(bitmap, mode))
    }

    internal fun colorizeVector(vector: ImageVectorDrawable): IconPackDrawable {
        if (options.colorizeLighten) return colorize(vector.toBitmap(), options.colorizeBlendMode)
        vector.root.editPathColors(
            SolidColor(Color.Unspecified),
            SolidColor(Color(colorizeColor))
        )
        vector.tintColor = Color.Unspecified
        return vector
    }

    internal fun trace(bitmap: Bitmap): IconPackDrawable {
        val imageVector = ImageTracer.imageToVector(
            bitmap.asImageBitmap(),
            ImageTracer.TracingOptions().apply { numberOfColors = 8 }
        )
        val vector = imageVector.toImageVectorDrawable()
        recolorVectorStrokes(vector)
        vector.resizeAndCenter()
        return if (options.themed) vectorToInset(vector) else vector
    }

    private fun detectEdges(bitmap: Bitmap): IconPackDrawable {
        val detector = CannyEdgeDetector()
        detector.process(
            bitmap.asImageBitmap(),
            options.color,
            DetectionOptions().apply {
                lowThreshold = options.edgeLowThreshold
                highThreshold = options.edgeHighThreshold
                gaussianKernelRadius = options.edgeGaussianRadius
                contrastNormalized = options.edgeContrastNormalized
            }
        )
        return if (options.themed) {
            bitmapToInset(detector.edgesImage)
        } else {
            BitmapIconDrawable(resources, detector.edgesImage)
        }
    }

    private fun removeBackground(bitmap: Bitmap): IconPackDrawable {
        val cleaned = bitmap.removeMatchedBackground(
            targets = options.bgRemovalTargets,
            tolerance = options.bgRemovalTolerance
        )
        // Hand strokes come last: they are corrections to whatever the colour match decided, and
        // restoring reads from the untouched artwork rather than from the cleaned result.
        return defaultBitmap(
            cleaned.applyBackgroundBrush(
                original = bitmap,
                operations = options.backgroundBrushOperations
            )
        )
    }

    private fun colorizeBitmap(icon: Bitmap, mode: PorterDuff.Mode): Bitmap {
        val source = if (options.themed) {
            icon.emptyLike().also { scaled ->
                Canvas(scaled).apply {
                    scale(0.5f, 0.5f, icon.width * 0.5f, icon.height * 0.5f)
                    drawBitmap(icon, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
                }
            }
        } else {
            icon
        }
        val coloredIcon = if (mode == PorterDuff.Mode.SCREEN) {
            screenColorizeBitmap(source, options.color)
        } else {
            source.emptyLike().also { result ->
                Canvas(result).drawBitmap(
                    source,
                    0f,
                    0f,
                    Paint().apply { colorFilter = PorterDuffColorFilter(options.color, mode) }
                )
            }
        }
        if (source !== icon) source.recycle()
        val result = addBackground(coloredIcon)
        return if (options.colorizeInverse) invertBitmapColors(result) else result
    }

    private fun colorizeWithGradient(icon: Bitmap): Bitmap {
        val centerX = icon.width / 2f
        val centerY = icon.height / 2f
        val base = if (options.colorizeMonochrome) {
            monochromeBitmap(icon, options.colorizeInverse)
        } else {
            icon
        }
        val style = options.colorizerStyle
        val gradient = buildColorizerShader(
            style.allGradientColors,
            style.gradientType,
            style.gradientAngle,
            icon.width,
            icon.height,
            style.gradientPositions
        )
        val coloredIcon = icon.emptyLike()
        val canvas = Canvas(coloredIcon)
        val solidFill = options.colorizeFlat && !options.colorizeMonochrome
        val drawMask = {
            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                if (solidFill) xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            }
            if (options.themed) {
                canvas.save()
                canvas.scale(0.5f, 0.5f, centerX, centerY)
                canvas.drawBitmap(base, 0f, 0f, maskPaint)
                canvas.restore()
            } else {
                canvas.drawBitmap(base, 0f, 0f, maskPaint)
            }
        }
        val drawGradient = {
            canvas.drawRect(
                0f,
                0f,
                icon.width.toFloat(),
                icon.height.toFloat(),
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = gradient
                    if (!solidFill) {
                        xfermode = PorterDuffXfermode(
                            if (options.colorizeLighten) PorterDuff.Mode.SCREEN
                            else PorterDuff.Mode.MULTIPLY
                        )
                    }
                }
            )
        }
        if (solidFill) {
            drawGradient()
            drawMask()
        } else {
            drawMask()
            drawGradient()
            if (options.colorizeLighten) {
                val alphaMask = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                }
                if (options.themed) {
                    canvas.save()
                    canvas.scale(0.5f, 0.5f, centerX, centerY)
                    canvas.drawBitmap(base, 0f, 0f, alphaMask)
                    canvas.restore()
                } else {
                    canvas.drawBitmap(base, 0f, 0f, alphaMask)
                }
            }
        }
        val tinted = if (options.colorizeInverse && !options.colorizeMonochrome) {
            invertBitmapColors(coloredIcon)
        } else {
            coloredIcon
        }
        return addBackground(tinted)
    }

    private fun addBackground(image: Bitmap): Bitmap {
        if (!options.themed) return image
        val shader = options.backgroundShader(image.width, image.height)
            ?: return image.changeBackgroundColor(options.bgColor)
        val result = image.emptyLike()
        Canvas(result).apply {
            drawRect(
                0f,
                0f,
                image.width.toFloat(),
                image.height.toFloat(),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
            )
            drawBitmap(image, 0f, 0f, null)
        }
        image.recycle()
        return result
    }

    private fun defaultBitmap(bitmap: Bitmap): IconPackDrawable =
        if (options.themed) bitmapToInset(bitmap) else BitmapIconDrawable(resources, bitmap)

    private fun preserveBitmapPresentation(
        source: IconPackDrawable,
        modified: IconPackDrawable
    ): IconPackDrawable {
        val bitmapSource = source as? BitmapIconDrawable ?: return modified
        if (!bitmapSource.isAdaptiveIcon() && bitmapSource.previewScale == 1f) return modified
        return BitmapIconDrawable(
            resources,
            modified.toBitmap(),
            exportAsAdaptiveIcon = bitmapSource.isAdaptiveIcon(),
            previewScale = bitmapSource.previewScale
        )
    }

    private fun recolorVectorStrokes(vector: ImageVectorDrawable) {
        val stroke = vector.viewportHeight / 48
        vector.root.editPaths(
            stroke,
            SolidColor(Color.Unspecified),
            SolidColor(Color(options.color))
        )
        vector.tintColor = Color.Unspecified
    }

    private fun vectorToInset(vector: ImageVectorDrawable, scale: Float = 0.25f): InsetIconDrawable {
        val x = vector.viewportWidth * scale
        val y = vector.viewportHeight * scale
        return InsetIconDrawable(
            vector,
            Rect(x.toInt(), y.toInt(), x.toInt(), y.toInt()),
            RectF(scale, scale, scale, scale)
        )
    }

    private fun bitmapToInset(bitmap: Bitmap, scale: Float = 0.25f): InsetIconDrawable {
        val x = bitmap.width * scale
        val y = bitmap.height * scale
        return InsetIconDrawable(
            BitmapIconDrawable(resources, bitmap),
            Rect(x.toInt(), y.toInt(), x.toInt(), y.toInt()),
            RectF(scale, scale, scale, scale)
        )
    }
}

/** Zero tolerance deliberately leaves colour removal off so brush-only corrections are possible. */
internal fun Bitmap.removeMatchedBackground(targets: List<Int>, tolerance: Float): Bitmap = when {
    tolerance <= 0f -> this
    targets.isNotEmpty() -> removeSegmentColors(this, targets, tolerance)
    else -> removeBackground(tolerance)
}
