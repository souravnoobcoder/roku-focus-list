package com.rokufocus

import androidx.compose.foundation.gestures.Orientation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RokuFocusEscapeTest {

    @Test
    fun defaultLetsFocusLeaveEveryEdge() {
        val escape = RokuFocusEscape()
        assertTrue(escape.start && escape.end && escape.up && escape.down)
        assertEquals(RokuFocusEscape.All, escape)
        assertEquals(RokuFocusEscape.All, DefaultRokuFocusConfig.focusEscape)
    }

    @Test
    fun presetsCoverTheCommonLayouts() {
        assertEquals(RokuFocusEscape(false, false, false, false), RokuFocusEscape.None)
        assertEquals(RokuFocusEscape(true, true, false, false), RokuFocusEscape.Horizontal)
        assertEquals(RokuFocusEscape(false, false, true, true), RokuFocusEscape.Vertical)
    }

    @Test
    fun horizontalPressesConsultTheStartAndEndEdges() {
        // Escape toward a navigation pane on the start edge only.
        val escape = RokuFocusEscape(start = true, end = false, up = false, down = false)
        assertTrue(escape.allowsLeaving(Orientation.Horizontal, forward = false))
        assertFalse(escape.allowsLeaving(Orientation.Horizontal, forward = true))
    }

    @Test
    fun verticalPressesConsultTheUpAndDownEdges() {
        val escape = RokuFocusEscape(start = false, end = false, up = false, down = true)
        assertFalse(escape.allowsLeaving(Orientation.Vertical, forward = false))
        assertTrue(escape.allowsLeaving(Orientation.Vertical, forward = true))
    }

    @Test
    fun deprecatedAllOrNothingFlagMapsToEveryEdge() {
        @Suppress("DEPRECATION")
        val open = RokuFocusConfig(allowFocusEscape = true)
        assertEquals(RokuFocusEscape.All, open.focusEscape)

        @Suppress("DEPRECATION")
        val trapped = RokuFocusConfig(allowFocusEscape = false)
        assertEquals(RokuFocusEscape.None, trapped.focusEscape)

        @Suppress("DEPRECATION")
        val readBack = open.allowFocusEscape
        assertTrue(readBack)
    }

    @Test
    fun deprecatedFlagKeepsTheOtherSettings() {
        @Suppress("DEPRECATION")
        val config = RokuFocusConfig(wrapAround = true, keyRepeatDelayMs = 10L, allowFocusEscape = false)
        assertTrue(config.wrapAround)
        assertEquals(10L, config.keyRepeatDelayMs)
        assertEquals(RokuFocusEscape.None, config.focusEscape)
    }
}
