package com.rokufocus

import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Navigation state for [RokuLazyColumn]. Holds the selected row index,
 * key-repeat timing, and acceleration counter.
 */
@Stable
internal class RokuColumnNavState(
    initialRowIndex: Int,
    rowCount: Int
) {
    var selectedRowIndex by mutableIntStateOf(initialRowIndex.coerceIn(0, maxOf(0, rowCount - 1)))
    var consecutivePresses by mutableIntStateOf(0)
    var lastKeyTime by mutableLongStateOf(0L)
}

/**
 * Key handler for [RokuLazyColumn]. Handles D-pad navigation across both axes:
 * UP/DOWN moves between rows, LEFT/RIGHT delegates to the active row's state.
 */
internal fun Modifier.rokuColumnKeyHandler(
    rows: List<RokuColumnRowConfig>,
    navState: RokuColumnNavState,
    config: RokuFocusConfig,
    onItemSelected: ((rowIndex: Int, itemIndex: Int) -> Unit)?,
    onItemClicked: ((rowIndex: Int, itemIndex: Int) -> Unit)?
): Modifier = onPreviewKeyEvent { keyEvent ->
    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

    val now = SystemClock.uptimeMillis()
    if (now - navState.lastKeyTime > 300) navState.consecutivePresses = 0

    val effectiveDelay = if (config.keyRepeatAccelAfter > 0 &&
        navState.consecutivePresses >= config.keyRepeatAccelAfter
    ) config.keyRepeatFastDelayMs else config.keyRepeatDelayMs

    val selectedRowIndex = navState.selectedRowIndex
    val activeRow = rows.getOrNull(selectedRowIndex) ?: return@onPreviewKeyEvent false
    val activeState = activeRow.state

    when (keyEvent.key) {
        // ── Vertical: move between rows ──
        Key.DirectionUp -> {
            if (now - navState.lastKeyTime < effectiveDelay) return@onPreviewKeyEvent true
            if (selectedRowIndex > 0) {
                navState.selectedRowIndex = (selectedRowIndex - 1).coerceAtLeast(0)
                navState.lastKeyTime = now
                navState.consecutivePresses++
                onItemSelected?.invoke(navState.selectedRowIndex, rows[navState.selectedRowIndex].state.selectedIndex)
                true
            } else {
                !config.allowFocusEscape
            }
        }

        Key.DirectionDown -> {
            if (now - navState.lastKeyTime < effectiveDelay) return@onPreviewKeyEvent true
            if (selectedRowIndex < rows.size - 1) {
                navState.selectedRowIndex = (selectedRowIndex + 1).coerceAtMost(rows.size - 1)
                navState.lastKeyTime = now
                navState.consecutivePresses++
                onItemSelected?.invoke(navState.selectedRowIndex, rows[navState.selectedRowIndex].state.selectedIndex)
                true
            } else {
                !config.allowFocusEscape
            }
        }

        // ── Horizontal: delegate to active row's state ──
        Key.DirectionRight -> {
            if (now - navState.lastKeyTime < effectiveDelay) return@onPreviewKeyEvent true
            if (config.wrapAround && !activeState.canScrollForward && activeState.itemCount > 1) {
                activeState.scrollTo(0)
                navState.lastKeyTime = now
                navState.consecutivePresses++
                onItemSelected?.invoke(selectedRowIndex, activeState.selectedIndex)
                true
            } else if (activeState.moveNext()) {
                navState.lastKeyTime = now
                navState.consecutivePresses++
                onItemSelected?.invoke(selectedRowIndex, activeState.selectedIndex)
                true
            } else {
                !config.allowFocusEscape
            }
        }

        Key.DirectionLeft -> {
            if (now - navState.lastKeyTime < effectiveDelay) return@onPreviewKeyEvent true
            if (config.wrapAround && !activeState.canScrollBackward && activeState.itemCount > 1) {
                activeState.scrollTo(activeState.itemCount - 1)
                navState.lastKeyTime = now
                navState.consecutivePresses++
                onItemSelected?.invoke(selectedRowIndex, activeState.selectedIndex)
                true
            } else if (activeState.movePrevious()) {
                navState.lastKeyTime = now
                navState.consecutivePresses++
                onItemSelected?.invoke(selectedRowIndex, activeState.selectedIndex)
                true
            } else {
                !config.allowFocusEscape
            }
        }

        Key.Enter, Key.DirectionCenter, Key.NumPadEnter -> {
            onItemClicked?.invoke(selectedRowIndex, activeState.selectedIndex)
            true
        }

        else -> false
    }
}
