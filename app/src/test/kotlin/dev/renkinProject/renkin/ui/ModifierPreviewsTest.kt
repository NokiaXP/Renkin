package dev.renkinProject.renkin.ui

import dev.renkinProject.renkin.data.ImageEdit
import dev.renkinProject.renkin.data.Source
import dev.renkinProject.renkin.data.TextType
import dev.renkinProject.renkin.icon.creator.ColorizerMode
import dev.renkinProject.renkin.icon.creator.ColorizerStyle
import dev.renkinProject.renkin.icon.creator.GenerationOptions
import dev.renkinProject.renkin.icon.creator.GradientType
import org.junit.Assert.assertEquals
import org.junit.Test

class ModifierPreviewsTest {

    @Test
    fun withShapeStyle_changesOnlyShapeBackground() {
        val original = options()
        val style = ColorizerStyle(
            mode = ColorizerMode.GRADIENT,
            gradientType = GradientType.RADIAL,
            firstColor = 0xFF123456.toInt(),
            gradientStops = listOf(0xFFABCDEF.toInt())
        )

        val updated = original.withShapeStyle(style)

        assertEquals(style.firstColor, updated.bgColor)
        assertEquals(style, updated.backgroundStyle)
        assertEquals(original.copy(bgColor = style.firstColor, backgroundStyle = style), updated)
    }

    private fun options() = GenerationOptions(
        primarySource = Source.APPLICATION_ICON,
        primaryImageEdit = ImageEdit.NONE,
        primaryTextType = TextType.FULL_NAME,
        primaryIconPack = "",
        color = 0xFF000000.toInt(),
        bgColor = 0xFFFFFFFF.toInt(),
        vector = false,
        materialYou = false,
        themed = false,
        override = true
    )
}
