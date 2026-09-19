package dev.renkinProject.renkin.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GallerySelectionTest {

    @Test
    fun toggle_entersAndLeavesSelectionMode() {
        val selected = GallerySelection().toggle("first")

        assertTrue(selected.active)
        assertEquals(setOf("first"), selected.paths)
        assertFalse(selected.toggle("first").active)
    }

    @Test
    fun toggle_preservesOtherSelections() {
        val selection = GallerySelection(setOf("first", "second"))

        assertEquals(setOf("second"), selection.toggle("first").paths)
        assertEquals(setOf("first", "second", "third"), selection.toggle("third").paths)
    }

    @Test
    fun toggleAll_selectsAvailablePathsThenClearsThem() {
        val available = setOf("first", "second")
        val allSelected = GallerySelection(setOf("first")).toggleAll(available)

        assertEquals(available, allSelected.paths)
        assertFalse(allSelected.toggleAll(available).active)
    }
}
