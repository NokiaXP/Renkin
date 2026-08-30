package dev.renkinProject.renkin.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutTest {

    @Test
    fun narrowWindowKeepsSinglePaneLayout() {
        val layout = horizontalPaneLayout(
            availableWidth = 600.dp,
            preferredLeadingWidth = 360.dp,
            minimumLeadingWidth = 320.dp,
            minimumTrailingWidth = 360.dp
        )

        assertNull(layout)
    }

    @Test
    fun mediumWindowPreservesBothContentMinimums() {
        val layout = horizontalPaneLayout(
            availableWidth = 720.dp,
            preferredLeadingWidth = 360.dp,
            minimumLeadingWidth = 320.dp,
            minimumTrailingWidth = 360.dp
        )!!

        assertEquals(320.dp, layout.leadingWidth)
        assertEquals(399.dp, layout.trailingWidth)
        assertFalse(layout.avoidsHinge)
    }

    @Test
    fun largeWindowCapsLeadingPaneAtItsPreferredWidth() {
        val layout = horizontalPaneLayout(
            availableWidth = 1_000.dp,
            preferredLeadingWidth = 360.dp,
            minimumLeadingWidth = 320.dp,
            minimumTrailingWidth = 360.dp
        )!!

        assertEquals(360.dp, layout.leadingWidth)
        assertEquals(639.dp, layout.trailingWidth)
    }

    @Test
    fun separatingHingeDefinesBothPaneWidths() {
        val layout = horizontalPaneLayout(
            availableWidth = 900.dp,
            preferredLeadingWidth = 360.dp,
            minimumLeadingWidth = 320.dp,
            minimumTrailingWidth = 360.dp,
            separatingVerticalHinge = VerticalHingeBounds(420.dp, 440.dp)
        )!!

        assertEquals(420.dp, layout.leadingWidth)
        assertEquals(20.dp, layout.separatorWidth)
        assertEquals(460.dp, layout.trailingWidth)
        assertTrue(layout.avoidsHinge)
    }

    @Test
    fun unsuitableSeparatingHingeFallsBackToSinglePane() {
        val layout = horizontalPaneLayout(
            availableWidth = 900.dp,
            preferredLeadingWidth = 360.dp,
            minimumLeadingWidth = 320.dp,
            minimumTrailingWidth = 360.dp,
            separatingVerticalHinge = VerticalHingeBounds(280.dp, 300.dp)
        )

        assertNull(layout)
    }

    @Test
    fun nestedEditorUsesItsOwnAvailableWidth() {
        val narrowLayout = horizontalPaneLayout(
            availableWidth = 500.dp,
            preferredLeadingWidth = 260.dp,
            minimumLeadingWidth = 220.dp,
            minimumTrailingWidth = 320.dp,
            dividerWidth = 16.dp
        )
        val wideLayout = horizontalPaneLayout(
            availableWidth = 600.dp,
            preferredLeadingWidth = 260.dp,
            minimumLeadingWidth = 220.dp,
            minimumTrailingWidth = 320.dp,
            dividerWidth = 16.dp
        )

        assertNull(narrowLayout)
        assertTrue(wideLayout != null)
        assertTrue(wideLayout!!.leadingWidth >= 220.dp)
        assertTrue(wideLayout.trailingWidth >= 320.dp)
    }
}
