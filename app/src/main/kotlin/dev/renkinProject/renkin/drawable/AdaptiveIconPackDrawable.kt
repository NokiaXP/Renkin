package dev.renkinProject.renkin.drawable

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.Gravity
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import dev.renkinProject.renkin.extension.newArgbBitmap
import dev.renkinProject.renkin.extension.getBytes
import dev.renkinProject.renkin.extension.scaleFromCenter
import dev.renkinProject.renkin.icon.creator.ColorizerStyle
import dev.renkinProject.renkin.icon.parser.AdaptiveIconPayload

data class MaterialYouPackEditState(
    val selectedScheme: Int,
    val customForeground: ColorizerStyle,
    val customBackground: ColorizerStyle,
    val strokeScale: Float
) {
    companion object {
        val DEFAULT_FOREGROUND = ColorizerStyle(firstColor = android.graphics.Color.WHITE)
        val DEFAULT_BACKGROUND = ColorizerStyle(firstColor = android.graphics.Color.BLACK)
    }
}

class AdaptiveIconPackDrawable internal constructor(
    internal val foregroundPng: ByteArray,
    internal val backgroundPng: ByteArray,
    internal val monochromePng: ByteArray? = null,
    val materialYouEditState: MaterialYouPackEditState? = null,
    internal val originalForegroundPng: ByteArray? = null,
    internal val originalBackgroundPng: ByteArray? = null
) : IconPackDrawable() {
    private constructor(
        foreground: Bitmap,
        background: Bitmap,
        monochrome: Bitmap? = null,
        materialYouEditState: MaterialYouPackEditState? = null,
        originalForegroundPng: ByteArray? = null,
        originalBackgroundPng: ByteArray? = null
    ) : this(
        encodeLayer(foreground),
        encodeLayer(background),
        monochrome?.let(::encodeLayer),
        materialYouEditState,
        originalForegroundPng,
        originalBackgroundPng
    )

    // A pack can contain thousands of icons; retain compressed layers, not three large pixel buffers.
    val foreground: Bitmap get() = decodeLayer(foregroundPng)
    val background: Bitmap get() = decodeLayer(backgroundPng)
    val monochrome: Bitmap? get() = monochromePng?.let(::decodeLayer)
    val hasMonochrome: Boolean get() = monochromePng != null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    // The stored bitmaps are complete 108dp adaptive layers. Launchers zoom through their outer
    // safe-zone padding, while an ordinary Compose BitmapPainter does not. Keep the export raster
    // untouched, but apply the same 108/72 preview compensation used by BitmapIconDrawable so a
    // freshly picked layered icon does not look tiny and then jump larger after a modifier.
    private val rendered by lazy { render(PREVIEW_SIZE) }
    private val preview by lazy { rendered.scaleFromCenter(ADAPTIVE_ICON_SCALE) }

    override fun isAdaptiveIcon(): Boolean = true
    override fun toBitmap(): Bitmap = rendered
    override fun toModifierBitmap(size: Int): Bitmap = render(size)
    override fun previewBitmap(): Bitmap = preview
    override fun toBrowserPreviewBitmap(): Bitmap = preview
    override fun toDbString(): String = AdaptiveIconPayload.encode(this)
    override fun getIntrinsicWidth(): Int = PREVIEW_SIZE
    override fun getIntrinsicHeight(): Int = PREVIEW_SIZE

    @Composable
    override fun getPainter(): Painter = BitmapPainter(preview.asImageBitmap())

    override fun draw(canvas: Canvas) {
        canvas.drawBitmap(preview, null, bounds, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    // A null monochrome keeps the current layer: only transforms that move the foreground pass one.
    fun withForeground(foreground: Bitmap, monochrome: Bitmap? = null): AdaptiveIconPackDrawable =
        AdaptiveIconPackDrawable(
            encodeLayer(foreground),
            backgroundPng,
            monochrome?.let(::encodeLayer) ?: monochromePng,
            materialYouEditState,
            originalForegroundPng,
            originalBackgroundPng
        )

    fun withMaterialYouLayers(
        foreground: Bitmap,
        background: Bitmap,
        state: MaterialYouPackEditState,
        originalForegroundPng: ByteArray? = this.originalForegroundPng ?: foregroundPng,
        originalBackgroundPng: ByteArray? = this.originalBackgroundPng ?: backgroundPng
    ): AdaptiveIconPackDrawable = AdaptiveIconPackDrawable(
        encodeLayer(foreground),
        encodeLayer(background),
        monochromePng,
        state,
        originalForegroundPng,
        originalBackgroundPng
    )

    fun restoreOriginalMaterialYouLayers(state: MaterialYouPackEditState): AdaptiveIconPackDrawable {
        val foreground = originalForegroundPng ?: foregroundPng
        val background = originalBackgroundPng ?: backgroundPng
        return AdaptiveIconPackDrawable(
            foreground, background, monochromePng, state,
            originalForegroundPng = null, originalBackgroundPng = null
        )
    }

    private fun render(size: Int): Bitmap = newArgbBitmap(size, size) { canvas ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AdaptiveIconDrawable(layerDrawable(background), layerDrawable(foreground)).apply {
                setBounds(0, 0, size, size)
                draw(canvas)
            }
        } else {
            val extent = size.toFloat()
            canvas.clipPath(Path().apply { addOval(0f, 0f, extent, extent, Path.Direction.CW) })
            val inset = extent * (ADAPTIVE_ICON_SCALE - 1f) / 2f
            val target = RectF(-inset, -inset, extent + inset, extent + inset)
            canvas.drawBitmap(background, null, target, paint)
            canvas.drawBitmap(foreground, null, target, paint)
        }
    }

    companion object {
        const val PREVIEW_SIZE = 500
        const val LAYER_SIZE = 750

        fun capture(
            icon: AdaptiveIconDrawable,
            monochrome: Drawable? = null,
            materialYouEditState: MaterialYouPackEditState? = null,
            originalForegroundPng: ByteArray? = null,
            originalBackgroundPng: ByteArray? = null
        ): AdaptiveIconPackDrawable =
            AdaptiveIconPackDrawable(
                captureLayer(icon.foreground),
                captureLayer(icon.background),
                monochrome?.let(::captureLayer),
                materialYouEditState,
                originalForegroundPng,
                originalBackgroundPng
            )

        // Capture the whole 108dp layer, including its own inset, before Android applies 72dp masking.
        fun captureLayer(layer: Drawable): Bitmap {
            val oldBounds = Rect(layer.bounds)
            return try {
                newArgbBitmap(LAYER_SIZE, LAYER_SIZE) { canvas ->
                    layer.setBounds(0, 0, LAYER_SIZE, LAYER_SIZE)
                    layer.draw(canvas)
                }.apply { density = Bitmap.DENSITY_NONE }
            } finally {
                layer.bounds = oldBounds
            }
        }

        private fun layerDrawable(bitmap: Bitmap): BitmapDrawable = BitmapDrawable(null, bitmap).apply {
            gravity = Gravity.FILL
            isFilterBitmap = true
        }

        private fun encodeLayer(layer: Bitmap): ByteArray = layer.getBytes(Bitmap.CompressFormat.PNG, 100)

        // Payloads are only header-checked on load (a full decode per layer made startup slow), so a
        // damaged layer renders as transparent instead of crashing the list.
        private fun decodeLayer(png: ByteArray): Bitmap {
            val layer = BitmapFactory.decodeByteArray(png, 0, png.size)
                ?: Bitmap.createBitmap(LAYER_SIZE, LAYER_SIZE, Bitmap.Config.ARGB_8888)
            return layer.apply { density = Bitmap.DENSITY_NONE }
        }
    }
}
