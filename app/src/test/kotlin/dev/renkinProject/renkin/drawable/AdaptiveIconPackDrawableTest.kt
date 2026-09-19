package dev.renkinProject.renkin.drawable

import android.graphics.Bitmap
import android.graphics.Color
import dev.renkinProject.renkin.extension.getBytes
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdaptiveIconPackDrawableTest {

    private fun layer(color: Int): ByteArray = Bitmap.createBitmap(
        AdaptiveIconPackDrawable.LAYER_SIZE,
        AdaptiveIconPackDrawable.LAYER_SIZE,
        Bitmap.Config.ARGB_8888
    ).apply { eraseColor(color) }.getBytes(Bitmap.CompressFormat.PNG, 100)

    @Test
    fun damagedLayerDecodesAsTransparentInsteadOfCrashing() {
        val icon = AdaptiveIconPackDrawable(byteArrayOf(1, 2, 3), layer(Color.BLUE))

        val foreground = icon.foreground

        assertEquals(AdaptiveIconPackDrawable.LAYER_SIZE, foreground.width)
        assertEquals(Color.TRANSPARENT, foreground.getPixel(0, 0))
        assertEquals(Color.BLUE, icon.background.getPixel(0, 0))
    }

    @Test
    fun withForegroundKeepsMonochromeUnlessANewOneIsGiven() {
        val icon = AdaptiveIconPackDrawable(layer(Color.RED), layer(Color.BLUE), layer(Color.WHITE))
        val replacement = Bitmap.createBitmap(
            AdaptiveIconPackDrawable.LAYER_SIZE,
            AdaptiveIconPackDrawable.LAYER_SIZE,
            Bitmap.Config.ARGB_8888
        ).apply { eraseColor(Color.GREEN) }

        val kept = icon.withForeground(replacement)
        val swapped = icon.withForeground(replacement, replacement)

        assertEquals(Color.GREEN, kept.foreground.getPixel(0, 0))
        assertEquals(Color.WHITE, checkNotNull(kept.monochrome).getPixel(0, 0))
        assertEquals(Color.GREEN, checkNotNull(swapped.monochrome).getPixel(0, 0))
    }
}
